package com.alanmaster.xread.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.ConcurrentHashMap

/**
 * 本地离线仓库：章节、进度、统计、替换规则、备份。私有目录，不需存储权限。
 *
 * root/exportDir 构造器对同模块（含单测）可见，生产代码请用 [of] 取进程级单例。
 */
class OfflineRepo internal constructor(private val root: File, val exportDir: File) {

    /** 兼容既有调用。生产代码请用 [of] 拿进程级单例。 */
    constructor(context: Context) : this(
        File(context.filesDir, "offline"),
        context.getExternalFilesDir(null) ?: context.filesDir,
    )

    private val books = File(root, "books")
    private val progressFile = File(root, "progress.json")
    private val statsFile = File(root, "stats.json")
    private val settingsFile = File(root, "settings.json")
    private val marksFile = File(root, "marks.json")
    private val tasksFile = File(root, "tasks.json")
    private val searchFile = File(root, "search.json")
    private val dramaRecentFile = File(root, "drama-recent.json")
    private val dramaFavFile = File(root, "drama-favorites.json")
    /** 书架整理结果（分组、入架时间、更新基线）在 library/shelf.json，备份要一并带上。 */
    private val shelfFile = File(root.parentFile ?: root, "library/shelf.json")

    /**
     * 最近一次自愈动作的说明（数据文件损坏而被另存时置位）。
     * UI 可读它告知用户，而不是静默把数据换成空。
     */
    @Volatile
    var lastWarning: String? = null
        private set

    init {
        runCatching { books.mkdirs() }
    }

    /* ---------- 读写内核 ---------- */

    /**
     * 读取并解析 JSON 对象。
     *
     * - 文件不存在 → 空对象（首次使用）
     * - 文件损坏 → 先另存为 `*.corrupt-<ts>`，再返回空对象，并置 [lastWarning]
     *
     * 绝不因为一次读取失败就把原数据直接覆盖掉：坏文件一定留在磁盘上可回收。
     */
    private fun loadObject(f: File): JSONObject {
        if (!f.isFile) return JSONObject()
        val text = try {
            f.readText()
        } catch (e: Exception) {
            quarantine(f, "${f.name} 读取失败")
            return JSONObject()
        }
        if (text.isBlank()) return JSONObject()
        return try {
            JSONObject(text)
        } catch (e: Exception) {
            quarantine(f, "${f.name} 内容损坏")
            JSONObject()
        }
    }

    private fun quarantine(f: File, reason: String) {
        val aside = File(f.parentFile, "${f.name}.corrupt-${System.currentTimeMillis()}")
        val moved = runCatching { f.renameTo(aside) }.getOrDefault(false)
        lastWarning = if (moved) "$reason，原文件已另存为 ${aside.name}" else "$reason，原文件保留未动"
    }

    /**
     * 原子写：同目录唯一临时文件 → fsync → rename 覆盖。
     *
     * 不使用「rename 失败就直写目标」的退路——那会在写到一半失败时留下截断文件，
     * 而截断文件正是上一次数据被清空的成因。失败时目标文件保持原样。
     */
    private fun write(f: File, text: String): Boolean = synchronized(lockFor(f)) {
        val dir = f.parentFile ?: return false
        if (!dir.isDirectory && !dir.mkdirs()) return false
        var tmp: File? = null
        try {
            tmp = File.createTempFile("${f.name}.", ".tmp", dir)
            FileOutputStream(tmp).use { out ->
                out.write(text.toByteArray(Charsets.UTF_8))
                out.flush()
                runCatching { out.fd.sync() }
            }
            if (tmp.renameTo(f)) return true
            // rename 在个别文件系统上失败时，用带 ATOMIC_MOVE 的语义再试一次；
            // 再失败也不直写目标，宁可报错让上层知道这次没存上。
            runCatching {
                java.nio.file.Files.move(
                    tmp.toPath(), f.toPath(),
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                    java.nio.file.StandardCopyOption.ATOMIC_MOVE,
                )
            }.isSuccess
        } catch (e: Exception) {
            false
        } finally {
            tmp?.let { if (it.exists()) it.delete() }
        }
    }

    /**
     * 读-改-写整体加锁。只锁 [write] 是不够的：两个写者可以同时读到同一份旧内容，
     * 各改各的再先后覆盖，必然丢键（替换规则或并发值其一消失）。
     */
    private fun mutate(f: File, block: (JSONObject) -> Unit): Boolean = synchronized(lockFor(f)) {
        val obj = loadObject(f)
        block(obj)
        write(f, obj.toString())
    }


    private fun bookDir(bookId: String) = File(books, safe(bookId))

    /* ---------- 书目与章节 ---------- */

    fun putBook(book: Book, chapters: List<Chapter>) = runCatching {
        val meta = JSONObject()
            .put("bookId", book.bookId)
            .put("title", book.title)
            .put("author", book.author)
            .put("coverUrl", book.coverUrl)
            .put("updated", System.currentTimeMillis())
        val arr = JSONArray()
        chapters.forEach { arr.put(JSONObject().put("id", it.id).put("title", it.title)) }
        meta.put("chapters", arr)
        write(File(bookDir(book.bookId), "meta.json"), meta.toString())
    }.getOrDefault(false)

    /**
     * 写入一章正文。
     *
     * [auto] 标记这一章是「阅读时自动预取」写下的：只有这类章节会被
     * [trimAutoChapters] 在超出窗口时清掉。用户手动下载的章节永不自动删除。
     */
    fun putChapter(
        bookId: String,
        chapterId: String,
        title: String,
        content: String,
        images: List<String> = emptyList(),
        auto: Boolean = false,
    ): Boolean {
        if (content.isBlank() && images.isEmpty()) return false
        val obj = JSONObject().put("chapterId", chapterId).put("title", title)
            .put("content", content).put("saved", System.currentTimeMillis())
            .put("fmt", CHAPTER_FMT)
        if (images.isNotEmpty()) {
            val arr = org.json.JSONArray()
            images.forEach { arr.put(it) }
            obj.put("images", arr)
        }
        val ok = write(File(bookDir(bookId), "c_${safe(chapterId)}.json"), obj.toString())
        if (ok) {
            downloadedCache.remove(safe(bookId))
            markAuto(bookId, chapterId, auto)
        }
        return ok
    }

    /* ---------- 自动预取标记 ---------- */

    /**
     * 每本书一个 `auto.json` 记「哪些章是预取写的」。
     *
     * 不把标记写进章节 JSON：那要为了判来源把整章正文读出来，939 章就是几十 MB 解析。
     * 小索引文件读一次只要几百字节，删除与判断都便宜。
     */
    private fun autoFile(bookId: String) = File(bookDir(bookId), "auto.json")

    fun autoChapters(bookId: String): Set<String> = runCatching {
        val arr = loadObject(autoFile(bookId)).optJSONArray("ids") ?: return emptySet()
        (0 until arr.length())
            .map { arr.optString(it) }
            .filterTo(HashSet()) { it.isNotEmpty() }
    }.getOrDefault(emptySet())

    private fun markAuto(bookId: String, chapterId: String, auto: Boolean) {
        runCatching {
        val current = autoChapters(bookId)
        if (auto) {
            if (chapterId in current) return@runCatching
            write(autoFile(bookId), JSONObject()
                .put("ids", JSONArray((current + chapterId).toList())).toString())
        } else {
            if (chapterId !in current) return@runCatching
            val left = current - chapterId
            if (left.isEmpty()) autoFile(bookId).delete()
            else write(autoFile(bookId), JSONObject()
                .put("ids", JSONArray(left.toList())).toString())
        }
        }
    }

    /**
     * 清掉窗口外的预取章。
     *
     * [keep] 是本次要保留的章节编号（当前章前 2 章 ～ 后 N 章）。手动下载的章
     * 不在 auto 集合里，所以这里永远碰不到它们。单次最多删 [limit] 章，
     * 免得切一章就做几十次文件删除。
     */
    fun trimAutoChapters(bookId: String, keep: Set<String>, limit: Int = 50): Int {
        val auto = autoChapters(bookId)
        if (auto.isEmpty()) return 0
        val doomed = auto.filter { it !in keep }.take(limit)
        if (doomed.isEmpty()) return 0
        var removed = 0
        doomed.forEach { if (removeChapter(bookId, it)) removed++ }
        return removed
    }

    fun getChapter(bookId: String, chapterId: String): JSONObject? {
        val f = File(bookDir(bookId), "c_${safe(chapterId)}.json")
        if (!f.isFile) return null
        return runCatching { JSONObject(f.readText()) }.getOrNull()
    }

    fun hasChapter(bookId: String, chapterId: String) =
        File(bookDir(bookId), "c_${safe(chapterId)}.json").isFile

    /**
     * 已落盘章节的键集合。
     *
     * 939 行目录逐行 `File.isFile` 就是 939 次 stat，滚动时会掉帧；这里一次
     * listFiles 建集合，再按书缓存。写章节、删章节、删整本都会让缓存失效。
     */
    fun downloadedChapters(bookId: String): Set<String> {
        val key = safe(bookId)
        downloadedCache[key]?.let { return it }
        val set = bookDir(bookId).listFiles()
            ?.filter { it.name.startsWith("c_") && it.name.endsWith(".json") }
            ?.mapTo(HashSet()) { it.name.removePrefix("c_").removeSuffix(".json") }
            ?: HashSet()
        downloadedCache[key] = set
        return set
    }

    private val downloadedCache = ConcurrentHashMap<String, Set<String>>()

    /** 单章删除：整本 [removeBook] 太重，用户常常只想清掉某一章。 */
    fun removeChapter(bookId: String, chapterId: String): Boolean {
        val f = File(bookDir(bookId), "c_${safe(chapterId)}.json")
        val ok = !f.exists() || f.delete()
        if (ok) {
            downloadedCache.remove(safe(bookId))
            markAuto(bookId, chapterId, false)
        }
        return ok
    }

    /**
     * 已落盘、但正文是旧格式写下的（见 [CHAPTER_FMT]），需要重下。
     * 老版本的批量下载会把原生核心给的纯文本再过一遍 `Html.fromHtml`，
     * 换行被折叠成空格，整章只剩一段 —— 这类文件重下就恢复了。
     */
    fun chapterNeedsRefresh(bookId: String, chapterId: String): Boolean {
        val f = File(bookDir(bookId), "c_${safe(chapterId)}.json")
        if (!f.isFile) return false
        val fmt = runCatching { JSONObject(f.readText()).optInt("fmt", 1) }.getOrDefault(1)
        return fmt < CHAPTER_FMT
    }

    /** 只数已落盘的章节：写出中途的 `c_x.json.<rand>.tmp` 不算一章。 */
    fun chapterCount(bookId: String) =
        bookDir(bookId).listFiles()?.count { it.name.startsWith("c_") && it.name.endsWith(".json") } ?: 0

    fun offlineBooks(): List<Book> = runCatching {
        val dirs = books.listFiles() ?: return emptyList()
        dirs.mapNotNull { dir ->
            if (!dir.isDirectory) return@mapNotNull null
            val f = File(dir, "meta.json")
            if (!f.isFile) return@mapNotNull null
            val meta = runCatching { JSONObject(f.readText()) }.getOrNull() ?: return@mapNotNull null
            Book(
                bookId = meta.optString("bookId"),
                title = meta.optString("title"),
                author = meta.optString("author"),
                coverUrl = meta.optString("coverUrl"),
                chapterCount = chapterCount(meta.optString("bookId")),
            )
        }.sortedBy { it.title }
    }.getOrDefault(emptyList())

    fun offlineChapters(bookId: String): List<Chapter> = runCatching {
        val f = File(bookDir(bookId), "meta.json")
        if (!f.isFile) return emptyList()
        val meta = runCatching { JSONObject(f.readText()) }.getOrNull() ?: return emptyList()
        val arr = meta.optJSONArray("chapters") ?: return emptyList()
        buildList {
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                add(Chapter(o.optString("id"), o.optString("title")))
            }
        }
    }.getOrDefault(emptyList())

    /** 下载任务：仅记状态与计数，实际续传以磁盘上已存在的章节文件为准。 */
    data class DownloadTask(
        val bookId: String,
        val source: String,
        val title: String,
        val author: String,
        val maxChapters: Int,
        val total: Int,
        val done: Int,
        val status: String,
        val updated: Long,
        /** 上游明确不提供的章节数（永久失败）。 */
        val skipped: Int = 0,
        /** 临时失败、下次可重试的章节数。 */
        val failed: Int = 0,
    )

    fun saveTask(task: DownloadTask) = mutate(tasksFile) { all ->
        all.put(task.bookId, JSONObject()
            .put("bookId", task.bookId)
            .put("source", task.source)
            .put("title", task.title)
            .put("author", task.author)
            .put("maxChapters", task.maxChapters)
            .put("total", task.total)
            .put("done", task.done)
            .put("skipped", task.skipped)
            .put("failed", task.failed)
            .put("status", task.status)
            .put("updated", task.updated))
    }

    private fun taskOf(o: JSONObject) = DownloadTask(
        bookId = o.optString("bookId"),
        source = o.optString("source"), title = o.optString("title"),
        author = o.optString("author"), maxChapters = o.optInt("maxChapters"),
        total = o.optInt("total"), done = o.optInt("done"),
        status = o.optString("status"), updated = o.optLong("updated"),
        skipped = o.optInt("skipped"), failed = o.optInt("failed"),
    )

    @Synchronized
    fun task(bookId: String): DownloadTask? {
        val o = loadObject(tasksFile).optJSONObject(bookId) ?: return null
        return taskOf(o)
    }

    /** 一次读盘取全部任务（旧实现每个 key 都重读一遍文件）。 */
    @Synchronized
    fun tasks(): List<DownloadTask> {
        val all = loadObject(tasksFile)
        return all.keys().asSequence()
            .mapNotNull { k -> all.optJSONObject(k)?.let { taskOf(it) } }
            .sortedByDescending { it.updated }
            .toList()
    }

    @Synchronized
    fun deleteTask(bookId: String) = mutate(tasksFile) { all -> all.remove(bookId) }

    fun removeBook(bookId: String): Boolean = synchronized(lockFor(bookDir(bookId))) {
        deleteTask(bookId)
        downloadedCache.remove(safe(bookId))
        bookDir(bookId).deleteRecursively()
    }

    fun sizeBytes(): Long {
        fun size(f: File): Long = if (f.isFile) f.length() else (f.listFiles()?.sumOf { size(it) } ?: 0L)
        return size(root)
    }

    /** 每本书的占用（按字节降序）。删除界面据此显示体积，而不是只给总数。 */
    fun bookSizes(): List<Pair<String, Long>> {
        fun size(f: File): Long = if (f.isFile) f.length() else (f.listFiles()?.sumOf { size(it) } ?: 0L)
        return (books.listFiles() ?: emptyArray())
            .filter { it.isDirectory }
            .map { it.name to size(it) }
            .sortedByDescending { it.second }
    }

    /**
     * 磁盘残留：原子写中断留下的 `*.tmp` 与隔离出来的 `*.corrupt-<ts>`。
     * 旧实现只在同进程的 finally 里删 tmp，进程被杀或掉电后就永远留着。
     */
    fun leftovers(): List<File> = buildList {
        fun scan(f: File) {
            if (f.isDirectory) { f.listFiles()?.forEach(::scan); return }
            val n = f.name
            if (n.endsWith(".tmp") || n.contains(".corrupt-")) add(f)
        }
        scan(root)
    }

    /** 清理残留，返回删除的文件数。 */
    fun cleanLeftovers(): Int = leftovers().count { runCatching { it.delete() }.getOrDefault(false) }

    /* ---------- 进度 ---------- */

    fun saveProgress(
        bookId: String,
        chapterId: String,
        offset: Int,
        chapterIndex: Int = -1,
        book: Book? = null,
    ) = mutate(progressFile) { all ->
        val previous = all.optJSONObject(bookId)
        val index = if (chapterIndex >= 0) chapterIndex else previous?.optInt("chapterIndex", -1) ?: -1
        val entry = JSONObject().put("chapterId", chapterId).put("offset", offset)
            .put("chapterIndex", index)
            .put("at", System.currentTimeMillis())
        // 顺手记下书目信息：冷启动要靠这条记录直接还原出 Book，不能只留一个 id
        // （从发现/榜单点进去读的书不一定在书架里，查不到标题就没法恢复）。
        if (book != null && book.title.isNotEmpty()) {
            entry.put("title", book.title).put("author", book.author)
                .put("coverUrl", book.coverUrl).put("source", book.source)
        } else {
            listOf("title", "author", "coverUrl", "source").forEach { k ->
                if (previous?.has(k) == true) entry.put(k, previous.opt(k))
            }
        }
        all.put(bookId, entry)
    }

    /**
     * 最近一次阅读记录：`progress.json` 里 `at` 最大的那条，返回 `书 id to 时间戳`。
     *
     * 冷启动恢复上次阅读位置用。只读盘一次，避免为找「最近一本」把每本书都读一遍。
     */
    @Synchronized
    fun lastSession(): Pair<Book, Long>? = runCatching {
        val all = loadObject(progressFile)
        all.keys().asSequence().mapNotNull { k ->
            val one = all.optJSONObject(k) ?: return@mapNotNull null
            val at = one.optLong("at")
            if (at <= 0L || one.optString("chapterId").isEmpty()) return@mapNotNull null
            Book(
                source = one.optString("source").ifEmpty { "fanqie" },
                bookId = k,
                title = one.optString("title"),
                author = one.optString("author"),
                coverUrl = one.optString("coverUrl"),
            ) to at
        }.maxByOrNull { it.second }
    }.getOrNull()

    /** 批量取已读章节下标；缺记录的书籍不出现在结果里。 */
    @Synchronized
    fun progressChapterIndexes(): Map<String, Int> = runCatching {
        val all = loadObject(progressFile)
        all.keys().asSequence().mapNotNull { k ->
            val i = all.optJSONObject(k)?.optInt("chapterIndex", -1) ?: -1
            if (i < 0) null else k to i
        }.toMap()
    }.getOrDefault(emptyMap())

    fun progress(bookId: String): Pair<String, Int>? = runCatching {
        val one = loadObject(progressFile).optJSONObject(bookId) ?: return null
        val cid = one.optString("chapterId")
        if (cid.isEmpty()) null else cid to one.optInt("offset")
    }.getOrNull()

    /** 上次阅读时间（毫秒）；0 表示没有记录。一次读盘批量取，避免书架逐本读文件。 */
    @Synchronized
    fun lastReadTimes(): Map<String, Long> = runCatching {
        val all = loadObject(progressFile)
        all.keys().asSequence().mapNotNull { k ->
            val at = all.optJSONObject(k)?.optLong("at") ?: return@mapNotNull null
            if (at <= 0L) null else k to at
        }.toMap()
    }.getOrDefault(emptyMap())

    /* ---------- 统计 ---------- */

    fun addStats(seconds: Int, words: Int, chapters: Int) = mutate(statsFile) { stats ->
        stats.put("seconds", stats.optLong("seconds") + seconds.coerceAtLeast(0))
        stats.put("words", stats.optLong("words") + words.coerceAtLeast(0))
        stats.put("chapters", stats.optLong("chapters") + chapters.coerceAtLeast(0))
        val days = stats.optJSONObject("days") ?: JSONObject()
        val today = GoalMath.dayKey()
        days.put(today, days.optLong(today) + seconds.coerceAtLeast(0))
        stats.put("days", days)
        if (!stats.has("since")) stats.put("since", System.currentTimeMillis())
    }

    /**
     * 用备份内容**覆盖**统计，而不是累加。
     *
     * [addStats] 的语义是「又读了一会儿」，备份导入的语义是「回到备份那一刻」。
     * 早期实现把导入接在 addStats 上，结果每导一次阅读时长就翻一倍（实测
     * 1654 秒的备份导入两次变成 4962 秒）。`days`/`since` 也必须一并恢复，
     * 否则只搬总数会让日粒度曲线失真。
     */
    fun restoreStats(backup: JSONObject): Boolean = mutate(statsFile) { stats ->
        stats.put("seconds", backup.optLong("seconds").coerceAtLeast(0))
        stats.put("words", backup.optLong("words").coerceAtLeast(0))
        stats.put("chapters", backup.optLong("chapters").coerceAtLeast(0))
        stats.put("days", backup.optJSONObject("days")?.let { JSONObject(it.toString()) } ?: JSONObject())
        if (backup.optLong("since") > 0) stats.put("since", backup.optLong("since"))
    }

    fun stats(): JSONObject = loadObject(statsFile).let { s ->
        if (s.length() == 0) JSONObject().put("seconds", 0).put("words", 0)
            .put("chapters", 0).put("days", JSONObject())
        else s
    }

    /* ---------- 并发窗口记忆 ---------- */

    /**
     * 上次收敛到的并发窗口，仅作下次的起点，仍受 AdaptiveConcurrency 上下界约束；
     * 换网络或换服务端后 AIMD 会自行再收敛。
     */
    fun savedConcurrency(): Int = runCatching {
        loadObject(settingsFile).optInt("concurrency", 0)
    }.getOrDefault(0)

    fun saveConcurrency(value: Int) = mutate(settingsFile) { s ->
        s.put("concurrency", value.coerceAtLeast(0))
    }

    /* ---------- 阅读器外观 ---------- */

    fun readerPrefs(): ReaderPrefs = runCatching {
        val o = loadObject(settingsFile).optJSONObject("reader") ?: return ReaderPrefs()
        ReaderPrefs(
            fontSize = o.optDouble("fontSize", 19.0).toFloat(),
            lineHeight = o.optDouble("lineHeight", 1.75).toFloat(),
            theme = o.optString("theme", "paper"),
            tapTurn = o.optBoolean("tapTurn", true),
            volumeTurn = o.optBoolean("volumeTurn", true),
            mangaMode = o.optString("mangaMode", "vertical"),
            font = o.optString("font", "default"),
            paragraphComments = o.optBoolean("paragraphComments", true),
            indent = o.optBoolean("indent", true),
            paragraphGap = o.optDouble("paragraphGap", 0.75).toFloat(),
            turnMode = o.optString("turnMode", "slide"),
            keepAwake = o.optBoolean("keepAwake", true),
            autoScroll = o.optString("autoScroll", "off"),
            margin = o.optDouble("margin", 20.0).toFloat(),
            letterSpacing = o.optDouble("letterSpacing", 0.0).toFloat(),
            hapticTurn = o.optBoolean("hapticTurn", true),
            brightness = o.optDouble("brightness", -1.0).toFloat(),
        ).normalized()
    }.getOrDefault(ReaderPrefs())

    fun saveReaderPrefs(prefs: ReaderPrefs) = mutate(settingsFile) { s ->
        val v = prefs.normalized()
        s.put("reader", JSONObject()
            .put("fontSize", v.fontSize.toDouble())
            .put("lineHeight", v.lineHeight.toDouble())
            .put("theme", v.theme)
            .put("tapTurn", v.tapTurn)
            .put("volumeTurn", v.volumeTurn)
            .put("mangaMode", v.mangaMode)
            .put("font", v.font)
            .put("paragraphComments", v.paragraphComments)
            .put("indent", v.indent)
            .put("paragraphGap", v.paragraphGap.toDouble())
            .put("turnMode", v.turnMode)
            .put("keepAwake", v.keepAwake)
            .put("autoScroll", v.autoScroll)
            .put("margin", v.margin.toDouble())
            .put("letterSpacing", v.letterSpacing.toDouble())
            .put("hapticTurn", v.hapticTurn)
            .put("brightness", v.brightness.toDouble()))
    }

    /* ---------- 新章提醒 ---------- */

    fun newChapterNotifyEnabled(): Boolean = runCatching {
        loadObject(settingsFile).optBoolean("newChapterNotify", false)
    }.getOrDefault(false)

    fun reminder(): ReadingReminder = runCatching {
        ReadingReminder.from(loadObject(settingsFile).optJSONObject("reminder"))
    }.getOrDefault(ReadingReminder())

    fun saveReminder(reminder: ReadingReminder) = mutate(settingsFile) { s ->
        s.put("reminder", reminder.normalized().toJson())
    }

    fun saveNewChapterNotify(enabled: Boolean) = mutate(settingsFile) { s ->
        s.put("newChapterNotify", enabled)
    }

    /* ---------- 热更新 ---------- */

    /**
     * 热更新总开关，默认开。
     *
     * 关了之后启动不自动检查，但设置里的「检查更新」仍可手动用——用户多半只是
     * 不想被后台流量打扰，而不是永远不升级。
     */
    fun hotUpdateEnabled(): Boolean = runCatching {
        loadObject(settingsFile).optBoolean("hotUpdate", true)
    }.getOrDefault(true)

    fun saveHotUpdateEnabled(enabled: Boolean) = mutate(settingsFile) { s ->
        s.put("hotUpdate", enabled)
    }

    fun updateSource(): String = runCatching {
        loadObject(settingsFile).optString("updateSource").ifBlank { AppUpdate.DEFAULT_SOURCE }
    }.getOrDefault(AppUpdate.DEFAULT_SOURCE)

    fun saveUpdateSource(source: String) = mutate(settingsFile) { s ->
        s.put("updateSource", source.trim())
    }

    /** GitHub 只读令牌；仓库私有，不带令牌拉 releases/latest 会 404。 */
    fun updateToken(): String = runCatching {
        loadObject(settingsFile).optString("updateToken")
    }.getOrDefault("")

    fun saveUpdateToken(token: String) = mutate(settingsFile) { s ->
        s.put("updateToken", token.trim())
    }

    /**
     * 下载加速通道的 key，见 [AppUpdate.MIRRORS]；空串表示直连。
     *
     * 读的时候也过一遍 [AppUpdate.mirrorOf]：手改过的 settings.json 里可能是
     * 已经下线的通道名，这时按直连处理，别让下载卡在一个拼不出来的 URL 上。
     */
    fun updateMirror(): String = runCatching {
        AppUpdate.mirrorOf(loadObject(settingsFile).optString("updateMirror")).key
    }.getOrDefault("")

    fun saveUpdateMirror(key: String) = mutate(settingsFile) { s ->
        s.put("updateMirror", AppUpdate.mirrorOf(key).key)
    }

    fun lastUpdateCheckAt(): Long = runCatching {
        loadObject(settingsFile).optLong("lastUpdateCheckAt", 0L)
    }.getOrDefault(0L)

    fun saveLastUpdateCheckAt(at: Long) = mutate(settingsFile) { s ->
        s.put("lastUpdateCheckAt", at)
    }

    /** 已下好、等用户点安装的包路径；装完或校验失败就清空。 */
    fun pendingUpdate(): String = runCatching {
        loadObject(settingsFile).optString("pendingUpdate")
    }.getOrDefault("")

    /** 缓存包对应的 versionCode；老数据没有这个键时返回 0。 */
    fun pendingUpdateCode(): Int = runCatching {
        loadObject(settingsFile).optInt("pendingUpdateCode", 0)
    }.getOrDefault(0)

    fun savePendingUpdate(path: String, versionCode: Int = 0) = mutate(settingsFile) { s ->
        s.put("pendingUpdate", path)
        s.put("pendingUpdateCode", versionCode)
    }

    /* ---------- 首启引导 ---------- */

    fun onboarded(): Boolean = runCatching {
        loadObject(settingsFile).optBoolean("onboarded", false)
    }.getOrDefault(false)

    fun saveOnboarded() = mutate(settingsFile) { s -> s.put("onboarded", true) }

    /**
     * 是否该显示首启引导。
     *
     * 不只看着 onboarded 标记：老用户升级上来没有标记，但书架里已经有书、书里已经有进度，
     * 这时弹三步教程只会挡路。只要本地有痕迹就直接当作已经引导过。
     */
    fun needsOnboarding(): Boolean = runCatching {
        if (onboarded()) return false
        loadObject(shelfFile).length() == 0 && loadObject(progressFile).length() == 0
    }.getOrDefault(false)

    /* ---------- 阅读目标 ---------- */

    fun readingGoal(): ReadingGoal = runCatching {
        val o = loadObject(settingsFile).optJSONObject("readingGoal") ?: return ReadingGoal()
        ReadingGoal(
            enabled = o.optBoolean("enabled", false),
            minutes = o.optInt("minutes", GoalMath.DEFAULT_MINUTES),
        )
    }.getOrDefault(ReadingGoal()).normalized()

    fun saveReadingGoal(goal: ReadingGoal) = mutate(settingsFile) { s ->
        val v = goal.normalized()
        s.put("readingGoal", JSONObject()
            .put("enabled", v.enabled)
            .put("minutes", v.minutes))
    }

    /* ---------- 下载策略 ---------- */

    fun downloadPolicy(): DownloadPolicy = runCatching {
        val o = loadObject(settingsFile).optJSONObject("download") ?: return DownloadPolicy()
        DownloadPolicy(
            wifiOnly = o.optBoolean("wifiOnly", false),
            maxBytes = o.optLong("maxBytes", 0L).coerceAtLeast(0L),
            skipRead = o.optBoolean("skipRead", false),
            prefetchChapters = o.optInt("prefetch", DownloadPolicy.DEFAULT_PREFETCH),
        )
    }.getOrDefault(DownloadPolicy())

    fun saveDownloadPolicy(policy: DownloadPolicy) = mutate(settingsFile) { s ->
        s.put("download", JSONObject()
            .put("wifiOnly", policy.wifiOnly)
            .put("maxBytes", policy.maxBytes.coerceAtLeast(0L))
            .put("skipRead", policy.skipRead)
            .put("prefetch", policy.prefetch))
    }

    /* ---------- 替换规则 ---------- */

    @Synchronized
    fun rules(): List<ReplaceRule> = runCatching {
        val arr = loadObject(settingsFile).optJSONArray("replaceRules") ?: return emptyList()
        buildList {
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val p = o.optString("pattern")
                if (p.isEmpty()) continue
                add(ReplaceRule(p, o.optString("replacement"), o.optBoolean("regex"),
                    o.optBoolean("enabled", true)))
            }
        }
    }.getOrDefault(emptyList())

    fun saveRules(list: List<ReplaceRule>) = mutate(settingsFile) { s ->
        val arr = JSONArray()
        list.forEach {
            arr.put(JSONObject().put("pattern", it.pattern).put("replacement", it.replacement)
                .put("regex", it.regex).put("enabled", it.enabled))
        }
        s.put("replaceRules", arr)
    }.also { rulesVersion++ }

    /**
     * 已编译的替换规则。TTS 按段落调用 [applyRules]，一章数百段；
     * 每次都重读 settings.json 再重编正则等于把整章变成几百次文件读 + 正则编译。
     * 规则只经 [saveRules] 变更，故按其版本号失效即可。
     */
    @Volatile
    private var compiledRules: List<Pair<ReplaceRule, Regex?>> = emptyList()

    @Volatile
    private var compiledRulesVersion = -1

    @Volatile
    private var rulesVersion = 0

    private fun compiled(): List<Pair<ReplaceRule, Regex?>> {
        val version = rulesVersion
        if (version == compiledRulesVersion) return compiledRules
        val built = rules().filter { it.enabled }.map { r ->
            r to if (r.regex) runCatching { Regex(r.pattern) }.getOrNull() else null
        }
        compiledRules = built
        compiledRulesVersion = version
        return built
    }

    fun applyRules(text: String): String {
        var out = text
        for ((rule, compiled) in compiled()) {
            out = runCatching {
                if (rule.regex) compiled?.replace(out, rule.replacement) ?: out
                else out.replace(rule.pattern, rule.replacement)
            }.getOrDefault(out)
        }
        return out
    }

    /**
     * 逐段套用替换规则。**必须先切段再替换**：
     * 替换文本里若带换行，对整篇正文替换会凭空多出段落，
     * 段评下标、书签与阅读进度都会整体错位。
     * 逐段替换保证段数不变，段号可以照旧对齐上游。
     */
    fun applyRules(paragraphs: List<String>): List<String> {
        if (paragraphs.isEmpty()) return paragraphs
        if (compiled().isEmpty()) return paragraphs
        return paragraphs.map { applyRules(it) }
    }

    /* ---------- 书签与书摘 ---------- */

    private fun markKey(bookId: String, chapterId: String) = "$bookId:$chapterId"

    fun marks(bookId: String, chapterId: String): List<Mark> = runCatching {
        val entry = loadObject(marksFile).optJSONObject(markKey(bookId, chapterId))
            ?: return emptyList()
        val items = entry.optJSONArray("items") ?: return emptyList()
        (0 until items.length()).mapNotNull { i ->
            val o = items.optJSONObject(i) ?: return@mapNotNull null
            markOf(o).takeIf { it.paragraph >= 0 && it.kind.isNotEmpty() }
        }
    }.getOrDefault(emptyList())

    /** 同段落同类型视为同一条；重复添加只更新时间与摘录。 */
    fun addMark(bookId: String, chapterId: String, chapterTitle: String, mark: Mark): Boolean =
        mutate(marksFile) { all ->
            val key = markKey(bookId, chapterId)
            val entry = all.optJSONObject(key) ?: JSONObject().put("items", JSONArray())
            // 上游章节名带尾随空格（如 "第1章 "），存盘前就 trim 掉，
            // 否则书摘页与导出的 Markdown 里会印出「【第1章 】」。
            entry.put("title", chapterTitle.trim())
            val items = entry.optJSONArray("items") ?: JSONArray().also { entry.put("items", it) }
            var replaced = false
            for (i in 0 until items.length()) {
                val o = items.optJSONObject(i) ?: continue
                if (o.optInt("i", -1) == mark.paragraph && o.optString("k") == mark.kind) {
                    items.put(i, markJson(mark))
                    replaced = true
                    break
                }
            }
            if (!replaced) items.put(markJson(mark))
            all.put(key, entry)
        }

    fun removeMark(bookId: String, chapterId: String, paragraph: Int, kind: String): Boolean =
        mutate(marksFile) { all ->
            val key = markKey(bookId, chapterId)
            val entry = all.optJSONObject(key) ?: return@mutate
            val items = entry.optJSONArray("items") ?: return@mutate
            val kept = JSONArray()
            for (i in 0 until items.length()) {
                val o = items.optJSONObject(i) ?: continue
                if (o.optInt("i", -1) == paragraph && o.optString("k") == kind) continue
                kept.put(o)
            }
            if (kept.length() == 0) all.remove(key) else entry.put("items", kept)
        }

    private fun markJson(mark: Mark) = JSONObject()
        .put("i", mark.paragraph).put("t", mark.text).put("k", mark.kind).put("at", mark.at)
        // 笔记与标签只在有值时落盘：老版本客户端读这份 marks.json 时不认识这两个键，
        // 但不认识也无害；空值不写则让「没写笔记」的条目与旧格式逐字节一致。
        .also { o ->
            if (mark.note.isNotBlank()) o.put("n", mark.note)
            if (mark.tags.isNotEmpty()) o.put("g", JSONArray(mark.tags))
        }

    private fun markOf(o: JSONObject) = Mark(
        paragraph = o.optInt("i", -1),
        text = o.optString("t"),
        kind = o.optString("k"),
        at = o.optLong("at"),
        note = o.optString("n"),
        tags = o.optJSONArray("g")?.let { arr ->
            (0 until arr.length()).mapNotNull { arr.optString(it).takeIf { t -> t.isNotEmpty() } }
        } ?: emptyList(),
    )

    /**
     * 写/改一条书摘的批注与标签。
     *
     * 按 (段落, 类型) 定位而不是按整条内容找：同一段落的高亮可能被重新摘过，
     * 文本变了但那条批注还是用户写的那条。找不到就返回 false，不凭空插入。
     */
    fun setMarkNote(
        bookId: String,
        chapterId: String,
        paragraph: Int,
        kind: String,
        note: String,
        tags: List<String>,
    ): Boolean {
        val clean = note.take(Mark.NOTE_LIMIT)
        val cleanTags = Mark.normalizeTags(tags)
        var hit = false
        mutate(marksFile) { all ->
            val entry = all.optJSONObject(markKey(bookId, chapterId)) ?: return@mutate
            val items = entry.optJSONArray("items") ?: return@mutate
            for (i in 0 until items.length()) {
                val o = items.optJSONObject(i) ?: continue
                if (o.optInt("i", -1) != paragraph || o.optString("k") != kind) continue
                if (clean.isBlank()) o.remove("n") else o.put("n", clean)
                if (cleanTags.isEmpty()) o.remove("g") else o.put("g", JSONArray(cleanTags))
                hit = true
                break
            }
        }
        return hit
    }

    /** 全部书摘，按时间倒序。 */
    fun allMarks(): List<MarkEntry> = runCatching {
        val all = loadObject(marksFile)
        val out = mutableListOf<MarkEntry>()
        all.keys().forEach { key ->
            val entry = all.optJSONObject(key) ?: return@forEach
            val sep = key.indexOf(':')
            if (sep <= 0) return@forEach
            val bookId = key.substring(0, sep)
            val chapterId = key.substring(sep + 1)
            // 老数据可能还留着带尾随空格的章节名，读的时候也 trim 一次
            val title = entry.optString("title").trim()
            val items = entry.optJSONArray("items") ?: return@forEach
            for (i in 0 until items.length()) {
                val o = items.optJSONObject(i) ?: continue
                out += MarkEntry(bookId, chapterId, title, markOf(o))
            }
        }
        out.filter { it.mark.paragraph >= 0 }.sortedByDescending { it.mark.at }
    }.getOrDefault(emptyList())

    /** 导出用的日期：只到天，跨年时补上年份。 */
    private fun stampFor(at: Long): String {
        if (at <= 0) return ""
        val fmt = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
        return fmt.format(java.util.Date(at))
    }

    /** 导出 Markdown 书摘到外部目录；无书摘时返回 null。 */
    fun exportMarks(bookTitles: Map<String, String> = emptyMap()): File? = runCatching {
        val entries = allMarks()
        if (entries.isEmpty()) return null
        val sb = StringBuilder("# 拾页书摘\n\n")
        entries.groupBy { it.bookId }.forEach { (bookId, group) ->
            sb.append("## ").append(bookTitles[bookId] ?: "作品 $bookId").append("\n\n")
            group.forEach { e ->
                // 有批注的条目改成引用块：原文与自己的话必须在视觉上分开，
                // 否则一年后翻回去会分不清哪句是书里写的。
                val quoted = e.mark.note.isNotBlank() || e.mark.tags.isNotEmpty()
                val body = e.mark.text.trim()
                if (quoted) {
                    sb.append("> ").append(body.replace("\n", "\n> ")).append("\n")
                    sb.append("— ")
                    if (e.chapterTitle.isNotEmpty()) sb.append("【").append(e.chapterTitle).append("】")
                    sb.append(" ").append(stampFor(e.mark.at)).append("\n")
                } else {
                    // 没有批注的条目保持旧格式：一行一条，读到哪儿都不会错位。
                    sb.append("- ")
                    if (e.chapterTitle.isNotEmpty()) sb.append("【").append(e.chapterTitle).append("】")
                    sb.append(body).append("\n")
                }
                if (e.mark.note.isNotBlank()) {
                    sb.append("\n笔记：").append(e.mark.note.trim().replace("\n", "\n  ")).append("\n")
                }
                if (e.mark.tags.isNotEmpty()) {
                    sb.append("\n标签：").append(e.mark.tags.joinToString(" ") { "#$it" }).append("\n")
                }
                if (quoted) sb.append("\n")
            }
            sb.append("\n")
        }
        val target = File(exportDir, "x-read-marks.md")
        if (!write(target, sb.toString())) return null
        target
    }.getOrNull()

    /**
     * 把已缓存的章节按目录顺序拼成 TXT 导出到外部目录。
     *
     * 顺序取自 meta.json 的目录，不用 listFiles 的字典序——后者会把
     * 「第10章」排到「第2章」前面。只写真正有正文的章节。
     */
    fun exportBookText(bookId: String): File? = runCatching {
        val chapters = offlineChapters(bookId)
        if (chapters.isEmpty()) return null
        val meta = File(bookDir(bookId), "meta.json")
            .takeIf { it.isFile }
            ?.let { runCatching { JSONObject(it.readText()) }.getOrNull() }
        val title = meta?.optString("title").orEmpty().ifEmpty { "作品 $bookId" }
        val author = meta?.optString("author").orEmpty()
        val bodies = chapters.mapNotNull { c ->
            val obj = getChapter(bookId, c.id) ?: return@mapNotNull null
            val body = obj.optString("content").trim()
            if (body.isEmpty()) return@mapNotNull null
            // 上游正文常把章节标题重复一遍作为首行，导出时去掉，否则 TXT 里标题出现两次
            val deduped = body.lineSequence().toList().let { lines ->
                if (c.title.isNotBlank() && lines.firstOrNull()?.trim() == c.title.trim())
                    lines.drop(1).joinToString("\n").trim()
                else body
            }
            if (deduped.isEmpty()) null else c.title to deduped
        }
        // 只有目录没有正文时不留空文件
        if (bodies.isEmpty()) return null
        val target = File(exportDir, safeFileName(title) + ".txt")
        target.bufferedWriter().use { w ->
            w.write(title)
            w.write("\n")
            if (author.isNotEmpty()) w.write("作者：$author\n")
            w.write("\n")
            bodies.forEach { (chapterTitle, body) ->
                w.write("\n")
                if (chapterTitle.isNotEmpty()) {
                    w.write(chapterTitle)
                    w.write("\n\n")
                }
                w.write(body)
                w.write("\n")
            }
        }
        target
    }.getOrNull()

    /** 文件名里只留字面量与空格，避免路径分隔符把导出写到别的目录去。 */
    private fun safeFileName(name: String): String =
        name.map { if (it.isLetterOrDigit() || it == ' ' || it == '-' || it == '_') it else '_' }
            .joinToString("").trim().ifEmpty { "book" }.take(60)

    /* ---------- 搜索历史 ---------- */

    /** 最近搜索词，新的在前。坏文件按空处理，不影响搜索本身。 */
    fun searchHistory(): List<String> = runCatching {
        val arr = loadObject(searchFile).optJSONArray("items") ?: return emptyList()
        (0 until arr.length()).mapNotNull { i ->
            arr.optString(i).trim().takeIf { it.isNotEmpty() }
        }.distinct().take(MAX_HISTORY)
    }.getOrDefault(emptyList())

    /** 记一次搜索并返回新列表：同词只留一条且提到最前。 */
    fun pushSearch(query: String): List<String> = runCatching {
        val q = query.trim()
        if (q.isEmpty()) return searchHistory()
        val merged = (listOf(q) + searchHistory().filter { it != q }).take(MAX_HISTORY)
        val arr = JSONArray()
        merged.forEach { arr.put(it) }
        write(searchFile, JSONObject().put("items", arr).toString())
        merged
    }.getOrDefault(emptyList())

    /** 删掉一条搜索历史并返回新列表：只动这一条，其余顺序不变。 */
    fun removeSearchHistory(query: String): List<String> = runCatching {
        val merged = searchHistory().filter { it != query.trim() }
        val arr = JSONArray()
        merged.forEach { arr.put(it) }
        if (!write(searchFile, JSONObject().put("items", arr).toString())) return searchHistory()
        merged
    }.getOrDefault(searchHistory())

    fun clearSearchHistory(): Boolean =
        write(searchFile, JSONObject().put("items", JSONArray()).toString())

    /* ---------- 追剧记录 ---------- */

    /**
     * 最近看过的短剧，新的在前。
     *
     * 之前这份列表只存在 ViewModel 的内存里，杀进程后书架「短剧」页永远回到空态，
     * 用户刚看过的剧找不回来。这里按搜索历史同样的方式落盘。
     */
    fun dramaRecent(): List<DramaSeries> = runCatching {
        val arr = loadObject(dramaRecentFile).optJSONArray("items") ?: return emptyList()
        buildList {
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val id = o.str("series_id")
                if (id.isEmpty()) continue
                add(
                    DramaSeries(
                        seriesId = id,
                        title = o.str("title"),
                        coverUrl = o.str("cover_url"),
                        intro = o.str("intro"),
                        author = o.str("author"),
                        episodeCount = o.long("episode_count").toInt(),
                        contentType = o.long("content_type", 1L).toInt(),
                    ),
                )
            }
        }.distinctBy { it.seriesId }.take(MAX_DRAMA_RECENT)
    }.getOrDefault(emptyList())

    /** 记一部刚看过的短剧并返回新列表：同一部只留一条且提到最前。 */
    fun pushDramaRecent(series: DramaSeries): List<DramaSeries> = runCatching {
        if (series.seriesId.isEmpty()) return dramaRecent()
        val merged = (listOf(series) + dramaRecent().filter { it.seriesId != series.seriesId })
            .take(MAX_DRAMA_RECENT)
        val arr = JSONArray()
        merged.forEach { s ->
            arr.put(
                JSONObject()
                    .put("series_id", s.seriesId)
                    .put("title", s.title)
                    .put("cover_url", s.coverUrl)
                    .put("intro", s.intro)
                    .put("author", s.author)
                    .put("episode_count", s.episodeCount)
                    .put("content_type", s.contentType),
            )
        }
        write(dramaRecentFile, JSONObject().put("items", arr).toString())
        merged
    }.getOrDefault(emptyList())

    /* ---------- 短剧收藏 ---------- */

    /**
     * 收藏的短剧，新的在前。
     *
     * 原来收藏只往 SharedPreferences 写一个布尔值，全仓没有任何地方读回来，
     * 用户点了星标却找不到「收藏的短剧」在哪里——等于收藏是个黑洞。
     * 这里和追剧记录用同一套落盘方式，书架短剧页可以直接列出来。
     */
    fun dramaFavorites(): List<DramaSeries> = runCatching {
        val arr = loadObject(dramaFavFile).optJSONArray("items") ?: return emptyList()
        buildList {
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val id = o.str("series_id")
                if (id.isEmpty()) continue
                add(
                    DramaSeries(
                        seriesId = id,
                        title = o.str("title"),
                        coverUrl = o.str("cover_url"),
                        intro = o.str("intro"),
                        author = o.str("author"),
                        episodeCount = o.long("episode_count").toInt(),
                        contentType = o.long("content_type", 1L).toInt(),
                    ),
                )
            }
        }.distinctBy { it.seriesId }.take(MAX_DRAMA_FAVORITES)
    }.getOrDefault(emptyList())

    fun isDramaFavorite(seriesId: String): Boolean =
        seriesId.isNotEmpty() && dramaFavorites().any { it.seriesId == seriesId }

    /** 收藏/取消收藏，返回收藏后的完整列表（新的在前）。 */
    fun setDramaFavorite(series: DramaSeries, favorite: Boolean): List<DramaSeries> = runCatching {
        if (series.seriesId.isEmpty()) return dramaFavorites()
        val rest = dramaFavorites().filter { it.seriesId != series.seriesId }
        val merged = if (favorite) (listOf(series) + rest).take(MAX_DRAMA_FAVORITES) else rest
        val arr = JSONArray()
        merged.forEach { s ->
            arr.put(
                JSONObject()
                    .put("series_id", s.seriesId)
                    .put("title", s.title)
                    .put("cover_url", s.coverUrl)
                    .put("intro", s.intro)
                    .put("author", s.author)
                    .put("episode_count", s.episodeCount)
                    .put("content_type", s.contentType),
            )
        }
        write(dramaFavFile, JSONObject().put("items", arr).toString())
        merged
    }.getOrDefault(emptyList())

    /* ---------- 备份 ---------- */

    fun exportBackup(): File? = runCatching {
        val target = File(exportDir, "x-read-backup.json")
        val body = backupJson() ?: return null
        if (!write(target, body)) return null
        target
    }.getOrNull()

    /**
     * 备份内容本身，不落盘。
     *
     * 自动备份要写进用户选的 SAF 目录、WebDAV 要直接 PUT，
     * 都不该先往应用外部目录写一份中间文件。
     */
    fun backupJson(): String? = runCatching {
        // version 3：marks 里带上了批注（n）与标签（g）。导入不校验版本，
        // 老备份缺这两个键时读出即空，所以向下兼容不需要迁移代码。
        val all = JSONObject().put("app", "x-read").put("version", 3)
            .put("exported", System.currentTimeMillis())
        val arr = JSONArray()
        books.listFiles()?.forEach { dir ->
            if (!dir.isDirectory) return@forEach
            val f = File(dir, "meta.json")
            if (!f.isFile) return@forEach
            runCatching { JSONObject(f.readText()) }.getOrNull()?.let { arr.put(it) }
        }
        all.put("books", arr)
        all.put("progress", loadObject(progressFile))
        all.put("stats", stats())
        val rulesArr = JSONArray()
        rules().forEach {
            rulesArr.put(JSONObject().put("pattern", it.pattern).put("replacement", it.replacement)
                .put("regex", it.regex).put("enabled", it.enabled))
        }
        all.put("replaceRules", rulesArr)
        // 不含书架的话，恢复后分组、入架时间、「有新章」基线全部丢失
        all.put("shelf", loadObject(shelfFile))
        // 书签与高亮同样是用户产出，备份里不能丢
        all.put("marks", loadObject(marksFile))
        // 追剧记录与短剧收藏也是用户自己点出来的产物：备份里不带上的话，
        // 换机恢复之后书架「短剧」页会干干净净，用户以为记录丢了。
        all.put("dramaRecent", loadObject(dramaRecentFile))
        all.put("dramaFavorites", loadObject(dramaFavFile))
        all.toString()
    }.getOrNull()

    /**
     * 导入备份。备份里 `stats.seconds` **本身就是秒**（addStats 以秒累加），
     * 早期实现又除了一次 1000，导致恢复后阅读时长为 0；这里保持秒的原义。
     * 统计走 [restoreStats] 覆盖而非累加，所以连续导入同一份备份结果不变。
     * 任一步写盘失败都如实返回失败，不谎报「导入完成」。
     */
    fun importBackup(): String {
        val target = File(exportDir, "x-read-backup.json")
        if (!target.isFile) return "未找到 ${target.absolutePath}"
        val all = runCatching { JSONObject(target.readText()) }.getOrNull() ?: return "备份无法解析"
        var booksN = 0
        var progressN = 0
        var failed = 0
        val metas = all.optJSONArray("books")
        if (metas != null) for (i in 0 until metas.length()) {
            val meta = metas.optJSONObject(i) ?: continue
            val id = meta.optString("bookId")
            if (id.isEmpty()) continue
            if (write(File(bookDir(id), "meta.json"), meta.toString())) booksN++ else failed++
        }
        val prog = all.optJSONObject("progress")
        if (prog != null) {
            val ok = mutate(progressFile) { mine ->
                prog.keys().forEach { k -> mine.put(k, prog.opt(k)); progressN++ }
            }
            if (!ok) failed++
        }
        all.optJSONObject("stats")?.let { st ->
            if (!restoreStats(st)) failed++
        }
        all.optJSONObject("shelf")?.let { backupShelf ->
            if (backupShelf.length() > 0) {
                val ok = mutate(shelfFile) { mine ->
                    backupShelf.keys().forEach { k ->
                        val entry = backupShelf.optJSONObject(k) ?: return@forEach
                        val existing = mine.optJSONObject(k)
                        if (existing == null) {
                            // 备份里有、本地没有：整条补回
                            mine.put(k, JSONObject(entry.toString()))
                        } else {
                            // 两边都有：只补整理字段，不覆盖本地已有分组
                            if (!existing.has("group") || existing.optString("group").isEmpty()) {
                                existing.put("group", entry.optString("group"))
                            }
                            if (!existing.has("added_at") || existing.optLong("added_at") <= 0) {
                                existing.put("added_at", entry.optLong("added_at"))
                            }
                            if (!existing.has("known_chapters") || existing.optInt("known_chapters") <= 0) {
                                existing.put("known_chapters", entry.optInt("known_chapters"))
                            }
                            // 个人标记同理：本地没设过才补，已经整理过的不被备份覆盖
                            if (!existing.has("status") || existing.optString("status").isEmpty()) {
                                existing.put("status", entry.optString("status"))
                            }
                            if (!existing.has("rating") || existing.optInt("rating") <= 0) {
                                existing.put("rating", entry.optInt("rating"))
                            }
                            if (existing.optJSONArray("tags") == null ||
                                (existing.optJSONArray("tags")?.length() ?: 0) == 0
                            ) {
                                entry.optJSONArray("tags")?.let { existing.put("tags", it) }
                            }
                            mine.put(k, existing)
                        }
                    }
                }
                if (!ok) failed++
            }
        }
        all.optJSONObject("marks")?.let { backupMarks ->
            if (backupMarks.length() > 0) {
                val ok = mutate(marksFile) { mine ->
                    backupMarks.keys().forEach { key ->
                        val entry = backupMarks.optJSONObject(key) ?: return@forEach
                        val items = entry.optJSONArray("items") ?: return@forEach
                        val existing = mine.optJSONObject(key) ?: JSONObject()
                            .put("items", JSONArray()).also { mine.put(key, it) }
                        if (entry.optString("title").isNotEmpty()) {
                            existing.put("title", entry.optString("title"))
                        }
                        val mineItems = existing.optJSONArray("items")
                            ?: JSONArray().also { existing.put("items", it) }
                        // 按 (段落, 类型) 去重后并入，导入不会把本地已有书摘抹掉
                        for (i in 0 until items.length()) {
                            val o = items.optJSONObject(i) ?: continue
                            var dup = false
                            for (j in 0 until mineItems.length()) {
                                val m = mineItems.optJSONObject(j) ?: continue
                                if (m.optInt("i", -1) == o.optInt("i", -2) &&
                                    m.optString("k") == o.optString("k")) { dup = true; break }
                            }
                            if (!dup) mineItems.put(JSONObject(o.toString()))
                        }
                        mine.put(key, existing)
                    }
                }
                if (!ok) failed++
            }
        }
        // 追剧记录与短剧收藏：按 series_id 去重并入，不覆盖本地已有的同 id 记录。
        listOf("dramaRecent" to dramaRecentFile, "dramaFavorites" to dramaFavFile).forEach { (key, file) ->
            // 导出时写的是 {"items":[…]}，不是裸数组——按对象取，别按 optJSONArray 取。
            val remote = all.optJSONObject(key)?.optJSONArray("items") ?: return@forEach
            val ok = mutate(file) { mine ->
                val items = mine.optJSONArray("items") ?: JSONArray().also { mine.put("items", it) }
                for (i in 0 until remote.length()) {
                    val o = remote.optJSONObject(i) ?: continue
                    val id = o.optString("series_id")
                    if (id.isEmpty()) continue
                    var dup = false
                    for (j in 0 until items.length()) {
                        if (items.optJSONObject(j)?.optString("series_id") == id) { dup = true; break }
                    }
                    if (!dup) items.put(JSONObject(o.toString()))
                }
            }
            if (!ok) failed++
        }
        all.optJSONArray("replaceRules")?.let { r ->
            val list = buildList {
                for (i in 0 until r.length()) {
                    val o = r.optJSONObject(i) ?: continue
                    add(ReplaceRule(o.optString("pattern"), o.optString("replacement"),
                        o.optBoolean("regex"), o.optBoolean("enabled", true)))
                }
            }
            if (!saveRules(list)) failed++
        }
        return if (failed > 0) "导入部分失败：书目 $booksN，进度 $progressN，$failed 项未写入"
        else "导入完成：书目 $booksN，进度 $progressN"
    }

    companion object {
        /**
         * 文件名用的安全键：非字母数字/下划线/短横一律换成 `_`。
         *
         * 目录页也要用它计算缓存标记的键，所以放在伴生对象里，保证「文件名」与
         * 「UI 比对的键」永远出自同一段逻辑。
         */
        fun safe(id: String) = id.map { c ->
            if (c.isLetterOrDigit() || c == '_' || c == '-') c else '_'
        }.joinToString("").ifEmpty { "unknown" }

        /** 章节文件名的键。目录页拿它和 [downloadedChapters] 比对，省掉逐行 stat。 */
        fun chapterKey(chapterId: String): String = safe(chapterId)

        /** 搜索历史最多保留多少条。 */
        private const val MAX_HISTORY = 12

        /** 追剧记录最多保留多少部。 */
        private const val MAX_DRAMA_RECENT = 20

        /** 收藏列表上限：再多也没人翻得到底。 */
        private const val MAX_DRAMA_FAVORITES = 100

        /**
         * 章节正文的落盘格式版本。
         *
         * 1（缺字段）是老的批量下载路径写下的：正文被 `Html.fromHtml` 折叠过，
         * 段落之间的换行变成空格，整章只剩一段。2 起正文保留原生核心给的换行。
         * [chapterNeedsRefresh] 用它挑出旧文件重下。
         */
        private const val CHAPTER_FMT = 2

        /**
         * 进程级单例。Api 与 MainActivity 曾各自 new 一个实例指向同一目录，
         * 而 `@Synchronized` 只锁实例，等于完全没有互斥。
         */
        @Volatile
        private var shared: OfflineRepo? = null

        fun of(context: Context): OfflineRepo {
            shared?.let { return it }
            return synchronized(this) {
                shared ?: OfflineRepo(context.applicationContext).also { shared = it }
            }
        }

        /** 同一目标文件的跨实例互斥锁（防御性：即使有人绕过 [of] 也不会互相截断）。 */
        private val fileLocks = ConcurrentHashMap<String, Any>()

        private fun lockFor(f: File): Any =
            fileLocks.getOrPut(f.absolutePath) { Any() }
    }
}

data class ReplaceRule(
    val pattern: String,
    val replacement: String,
    val regex: Boolean = false,
    val enabled: Boolean = true,
)
