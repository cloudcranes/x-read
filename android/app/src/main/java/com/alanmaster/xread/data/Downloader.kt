package com.alanmaster.xread.data

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

data class DownloadState(
    val running: Boolean = false,
    val bookId: String = "",
    val title: String = "",
    val done: Int = 0,
    val total: Int = 0,
    val message: String = "",
    val resumable: Boolean = false,
    val concurrency: Int = 0,
    val concurrencyMax: Int = AdaptiveConcurrency.DEFAULT_MAX,
    val skipped: Int = 0,
    val failed: Int = 0,
)

/**
 * 批量下载：目录 → 正文**自适应并发**抓取 → 落 OfflineRepo。
 *
 * 并发窗口由 [AdaptiveConcurrency]（AIMD）动态调整：连续成功则加一，失败则减半并退避，
 * 因此在服务端 `Transport` 串行节流或网络抖动时会自动收敛，不会撞出 `LOCAL_BUSY`。
 *
 * 续传不记账，以**磁盘上已存在的章节文件**为准：每次启动重拉目录，按 hasChapter 过滤缺失章节。
 * 任务状态另存 tasks.json，仅用于界面标注「未完成任务（可续传）」。
 */
class Downloader(
    private val api: Api,
    private val offline: OfflineRepo,
    private val scope: CoroutineScope,
    startConcurrency: Int = AdaptiveConcurrency.DEFAULT_START,
    /** 当前下载策略；由 ViewModel 在每次开始时同步进来。 */
    @Volatile var policy: DownloadPolicy = DownloadPolicy(),
    /** 网络是否计费；由 ViewModel 注入，便于单测替身。 */
    private val onUnmetered: () -> Boolean = { true },
) {
    // 记住上次收敛值作为起点；无效值（0）时用默认。
    private val window = AdaptiveConcurrency(
        start = offline.savedConcurrency().takeIf { it > 0 } ?: startConcurrency,
    )
    private val _state = MutableStateFlow(DownloadState())
    val state: StateFlow<DownloadState> = _state
    private var job: Job? = null
    /** 当前任务对应的书；收尾时用它算章节数，绝不读全局 state（会被新任务覆盖）。 */
    private var jobBookId: String = ""

    /** 未完成且本地已有章节的任务，用于界面提示续传。 */
    fun resumable(): List<OfflineRepo.DownloadTask> =
        offline.tasks()
            .filter { it.status != STATUS_DONE }
            .mapNotNull { t ->
                val local = offline.chapterCount(t.bookId)
                if (local <= 0) null else t.copy(done = local)
            }

    fun start(book: Book, maxChapters: Int) =
        launch(book, if (maxChapters > 0) 0 until maxChapters else null)

    /**
     * 只补目录里从 [from] 开始（0 基下标）的 [count] 章，[count] = 1 就是单章补缓存。
     *
     * 与整本下载共用同一套并发、预算与续传逻辑，区别只在待下集合：整本按
     * `maxChapters` 截断，这里按区间过滤。不做第二份循环。
     */
    fun startRange(book: Book, from: Int, count: Int) =
        launch(book, if (count <= 0 || from < 0) IntRange.EMPTY else from until (from + count))

    /**
     * 等到当前任务收尾。批量缓存按顺序跑时要它：下载器同一时刻只跑一本，
     * 排队方不能在上一本还在跑的时候 start（会被直接 return 丢掉）。
     */
    suspend fun awaitIdle() {
        job?.join()
    }

    fun resume(book: Book) {
        val max = offline.task(book.bookId)?.maxChapters ?: 0
        launch(book, if (max > 0) 0 until max else null)
    }

    /** [limit] 为 null 表示整本；否则只下这些目录下标。 */
    private fun launch(book: Book, limit: IntRange?) {
        // tasks.json 仍按「下到第几章」记账，续传才认得这次任务的范围
        val maxChapters = limit?.last?.plus(1) ?: 0
        if (job?.isActive == true) return
        // 仅 Wi-Fi：没网或移动网络下不起跑，直接给出可操作提示，而不是把失败摊进进度
        if (policy.wifiOnly && !onUnmetered()) {
            _state.value = DownloadState(false, book.bookId, book.title, 0, 0,
                "已设为仅 Wi-Fi 下载，当前网络不计费条件不满足")
            return
        }
        jobBookId = book.bookId
        // 必须落在 IO：putBook/saveTask/chapterCount 都是同步磁盘操作，
        // 旧实现用 viewModelScope(Main) 启动，1496 章目录的 fsync 直接卡主线程。
        job = scope.launch(Dispatchers.IO) download@{
            var total = 0
            try {
                _state.value = DownloadState(true, book.bookId, book.title, 0, 0, "取目录…",
                    concurrency = window.permits)
                val chapters = api.chapters(book.source, book.bookId)
                if (chapters.isEmpty()) {
                    _state.value = DownloadState(false, book.bookId, book.title, 0, 0, "目录为空或服务不可达")
                    return@download
                }
                offline.putBook(book, chapters)
                val slice = (limit ?: 0 until chapters.size).filter { it in chapters.indices }
                total = slice.size
                // 数千次 stat 不能在主线程做
                val pending = kotlinx.coroutines.withContext(Dispatchers.IO) {
                    // 只下未读：从阅读进度所在章开始；进度不存在则照常全下
                    // 区间下载是用户手动点的那几章，不再套「只下未读」
                    val readFrom = if (limit == null && policy.skipRead)
                        offline.progress(book.bookId)?.first?.let { cid ->
                            chapters.indexOfFirst { it.id == cid }
                        } ?: -1
                    else -1
                    slice.filter { i ->
                        val id = chapters[i].id
                        (readFrom < 0 || i >= readFrom) &&
                            (!offline.hasChapter(book.bookId, id) || offline.chapterNeedsRefresh(book.bookId, id))
                    }
                }
                val already = total - pending.size

                if (pending.isEmpty()) {
                    finish(book, maxChapters, already, total, 0, 0,
                        "已是最新 · 本地 ${offline.chapterCount(book.bookId)} 章")
                    return@download
                }

                offline.saveTask(taskOf(book, maxChapters, total, already, STATUS_RUNNING))
                _state.value = DownloadState(
                    true, book.bookId, book.title, already, total,
                    if (already > 0) "续传中（已跳过 $already 章）" else "开始下载",
                    concurrency = window.permits,
                )

                val gate = AdaptiveGate(window)
                val saved = AtomicInteger(already)
                val skipped = AtomicInteger(0)
                val failed = AtomicInteger(0)

                // 批量分块（优先走 full/batch 批量拉取接口，大幅降低请求频次与耗时）
                val chunks = pending.chunked(BATCH_CHUNK_SIZE)
                val cursor = AtomicInteger(0)
                val budgetHit = AtomicBoolean(false)
                val workers = minOf(chunks.size, AdaptiveConcurrency.DEFAULT_MAX * 2).coerceAtLeast(1)
                coroutineScope {
                    repeat(workers) {
                        launch(Dispatchers.IO) {
                            while (true) {
                                val chunkIdx = cursor.getAndIncrement()
                                if (chunkIdx >= chunks.size) break
                                if (!isActive) break
                                if (overBudget()) {
                                    budgetHit.set(true)
                                    break
                                }
                                val chunkIndices = chunks[chunkIdx]
                                val chunkChapters = chunkIndices.map { chapters[it] }
                                val chunkIds = chunkChapters.map { it.id }

                                gate.acquire()
                                var needBackoff = false
                                try {
                                    val batchMap = try {
                                        api.chaptersBatch(book.bookId, chunkIds)
                                    } catch (e: CancellationException) {
                                        throw e
                                    } catch (_: Exception) {
                                        emptyMap()
                                    }

                                    var batchSaved = 0
                                    val missing = mutableListOf<Chapter>()

                                    for (c in chunkChapters) {
                                        val text = batchMap[c.id]
                                        if (!text.isNullOrBlank() && offline.putChapter(book.bookId, c.id, c.title, text)) {
                                            batchSaved++
                                        } else {
                                            missing.add(c)
                                        }
                                    }

                                    if (batchSaved > 0) {
                                        gate.recordSuccess()
                                        val now = saved.addAndGet(batchSaved)
                                        publish(now, total, skipped, failed, "批量下载中")
                                    }

                                    // 对批量缺失或失败的章节逐一走单章重试逻辑
                                    for (c in missing) {
                                        if (!isActive || overBudget()) break
                                        when (fetchChapter(book, c)) {
                                            Outcome.OK -> {
                                                gate.recordSuccess()
                                                val now = saved.incrementAndGet()
                                                publish(now, total, skipped, failed, "下载中")
                                            }
                                            Outcome.SKIPPED -> {
                                                skipped.incrementAndGet()
                                                publish(saved.get(), total, skipped, failed,
                                                    "下载中（跳过 ${skipped.get()} 章上游缺失）")
                                            }
                                            Outcome.FAILED -> {
                                                gate.recordFailure()
                                                needBackoff = true
                                                failed.incrementAndGet()
                                                publish(saved.get(), total, skipped, failed,
                                                    "下载中（${failed.get()} 章失败，已降并发，可续传）")
                                            }
                                        }
                                    }
                                    persistWindow()
                                } finally {
                                    gate.release()
                                }
                                if (needBackoff) AdaptiveGate.backoff(gate)
                            }
                        }
                    }
                }
                val local = offline.chapterCount(book.bookId)
                val missing = failed.get()
                val skip = skipped.get()
                val note = buildString {
                    if (skip > 0) append("，跳过 $skip 章")
                    if (missing > 0) append("，$missing 章失败可续传")
                }
                if (budgetHit.get()) {
                    // 容量上限命中：不再继续，但已下的章节算数，任务标 partial 便于续传
                    offline.saveTask(taskOf(book, maxChapters, total, local, STATUS_PARTIAL, skip, missing))
                    _state.value = DownloadState(false, book.bookId, book.title, local, total,
                        "已达存储上限 · 本地 $local 章，可续传", resumable = true,
                        concurrency = window.permits, skipped = skip, failed = missing)
                    return@download
                }
                if (missing > 0) {
                    // 有章节没拿到：不能标 DONE，否则「可续传」列表会把它过滤掉。
                    offline.saveTask(taskOf(book, maxChapters, total, local, STATUS_PARTIAL, skip, missing))
                    _state.value = DownloadState(false, book.bookId, book.title, local, total,
                        "完成（不完整）· 本地 $local 章$note", resumable = true,
                        concurrency = window.permits, skipped = skip, failed = missing)
                } else {
                    finish(book, maxChapters, local, total, skip, 0,
                        "完成 · 本地 $local 章$note")
                }
            } catch (e: CancellationException) {
                val local = offline.chapterCount(book.bookId)
                offline.saveTask(taskOf(book, maxChapters, total, local, STATUS_PAUSED))
                _state.value = DownloadState(false, book.bookId, book.title, local, total,
                    "已取消 · 本地 $local 章，可续传", resumable = true, concurrency = window.permits)
                throw e
            } catch (e: Exception) {
                val local = offline.chapterCount(book.bookId)
                offline.saveTask(taskOf(book, maxChapters, total, local, STATUS_FAILED))
                _state.value = DownloadState(false, book.bookId, book.title, local, total,
                    e.message ?: "下载失败", resumable = local > 0, concurrency = window.permits)
            }
        }
    }

    private val budgetProbe = java.util.concurrent.atomic.AtomicInteger(0)

    /**
     * 是否已达离线库容量上限。整棵树 stat 代价随章数增长，因此每 25 章才真正测一次；
     * 其余调用直接返回 false，避免把下载变成 IO 密集。
     */
    private fun overBudget(): Boolean {
        val cap = policy.maxBytes
        if (cap <= 0) return false
        if (budgetProbe.incrementAndGet() % 25 != 1) return false
        return offline.sizeBytes() >= cap
    }

    private enum class Outcome { OK, SKIPPED, FAILED }

    /** 抓一章；瞬时失败重试一次，永久缺失直接跳过。 */
    private suspend fun fetchChapter(book: Book, chapter: Chapter): Outcome {
        var attempt = 0
        while (true) {
            attempt++
            try {
                val content = api.chapter(book.source, book.bookId, chapter.id)
                return if (offline.putChapter(book.bookId, chapter.id,
                        content.title.ifEmpty { chapter.title }, content.text, content.images)) Outcome.OK
                else if (attempt < 2) continue else Outcome.FAILED
            } catch (e: CancellationException) {
                throw e
            } catch (e: ApiException) {
                if (!e.transient) return Outcome.SKIPPED
                if (attempt >= 2) return Outcome.FAILED
            } catch (e: Exception) {
                if (attempt >= 2) return Outcome.FAILED
            }
        }
    }

    private fun publish(
        done: Int, total: Int,
        skipped: AtomicInteger, failed: AtomicInteger, message: String,
    ) {
        _state.value = _state.value.copy(
            done = done, total = total,
            concurrency = window.permits,
            skipped = skipped.get(), failed = failed.get(),
            message = message,
        )
    }

    private var persistedConcurrency = 0

    /** 窗口变化即落盘（只在值变动时写，避免每章一次 IO）。 */
    @Synchronized
    private fun persistWindow() {
        val current = window.permits
        if (current != persistedConcurrency) {
            persistedConcurrency = current
            offline.saveConcurrency(current)
        }
    }

    private fun finish(book: Book, maxChapters: Int, done: Int, total: Int, skipped: Int, failed: Int, message: String) {
        persistWindow()
        offline.saveTask(taskOf(book, maxChapters, total, done, STATUS_DONE, skipped, failed))
        _state.value = DownloadState(false, book.bookId, book.title, done, total, message,
            concurrency = window.permits, skipped = skipped, failed = failed)
    }

    private fun taskOf(
        book: Book, maxChapters: Int, total: Int, done: Int, status: String,
        skipped: Int = 0, failed: Int = 0,
    ) = OfflineRepo.DownloadTask(
        bookId = book.bookId, source = book.source, title = book.title, author = book.author,
        maxChapters = maxChapters, total = total, done = done, status = status,
        updated = System.currentTimeMillis(), skipped = skipped, failed = failed,
    )

    /**
     * 取消并**等待旧任务真正退场**。旧实现只把 running 置 false 就返回，
     * 旧协程随后的 CancellationException 分支会再写一次 state/tasks，
     * 把紧接着新起的任务状态覆盖掉。
     */
    /** 只有当前任务确实是这本书时才取消，避免删 A 书把 B 书的下载停掉。 */
    suspend fun cancelIfRunning(bookId: String) {
        if (job?.isActive == true && jobBookId == bookId) cancel()
    }

    suspend fun cancel() {
        persistWindow()
        val running = job
        _state.value = _state.value.copy(running = false, message = "已取消")
        if (running == null || !running.isActive) return
        // 注释一直写着「取消并等待旧任务真正退场」，实现却是 cancel 完就返回：
        // 旧协程的 CancellationException 收尾仍会写 state/tasks，把紧接着新起的任务
        // 覆盖成「已取消」。这里改成真正 join，并在 join 之后再收尾。
        running.cancelAndJoin()
        val bookId = jobBookId
        val local = if (bookId.isEmpty()) 0 else offline.chapterCount(bookId)
        _state.value = _state.value.copy(
            running = false,
            done = if (local > 0) local else _state.value.done,
            resumable = local > 0,
            message = "已取消 · 本地 $local 章，可续传",
        )
    }

    companion object {
        const val BATCH_CHUNK_SIZE = 50
        const val STATUS_RUNNING = "running"
        const val STATUS_PAUSED = "paused"
        const val STATUS_FAILED = "failed"
        /** 有章节没拿到，可续传。 */
        const val STATUS_PARTIAL = "partial"
        const val STATUS_DONE = "done"
    }
}
