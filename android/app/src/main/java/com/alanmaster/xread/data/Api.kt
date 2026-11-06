package com.alanmaster.xread.data

import android.content.Context
import android.text.Html
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap

class ApiException(message: String, val transient: Boolean = false) : Exception(message)

/** Native content repository: Kotlin -> JNI -> upstream. All user library data stays on phone. */
class Api(context: Context) {
    private val app = context.applicationContext
    // 必须与 ViewModel 共用同一实例：OfflineRepo 的锁是实例级，两个实例等于没有互斥。
    private val offline = OfflineRepo.of(app)
    private val library = LocalLibrary(File(app.filesDir, "library"))
    private val directoryCache = ConcurrentHashMap<String, List<Chapter>>()
    private val bookCache = ConcurrentHashMap<String, Book>()

    /** 当前是否处于 Wi-Fi/以太网。下载策略据此决定要不要动。 */
    fun onUnmeteredNetwork(): Boolean = runCatching {
        val cm = app.getSystemService(Context.CONNECTIVITY_SERVICE)
                as? android.net.ConnectivityManager ?: return true
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
        caps.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI) ||
            caps.hasTransport(android.net.NetworkCapabilities.TRANSPORT_ETHERNET)
    }.getOrDefault(true)

    private fun checkSource(source: String) { require(source == "fanqie") { "书源不存在" } }
    private fun checkId(id: String) { require(id.matches(Regex("[0-9]{1,24}"))) { "作品或章节编号格式错误" } }
    private suspend fun content(path: String, query: String = ""): JSONObject =
        NativeCore.request(app, "/api/v1/$path", query)

    suspend fun shelf(): List<ShelfItem> = withContext(Dispatchers.IO) {
        library.migrateDownloads(offline.offlineBooks())
        val progress = library.entries().associateBy { it.book.bookId }
        val lastRead = offline.lastReadTimes()
        val readIndexes = offline.progressChapterIndexes()
        library.books().map { b ->
            val p = offline.progress(b.bookId)
            val meta = progress[b.bookId]
            ShelfItem(
                book = b,
                chapterId = p?.first.orEmpty(),
                chapterOffset = p?.second ?: 0,
                updatedAt = "",
                group = meta?.group.orEmpty(),
                addedAt = meta?.addedAt ?: 0,
                knownChapters = meta?.knownChapters ?: b.chapterCount,
                lastReadAt = lastRead[b.bookId] ?: 0L,
                readChapterIndex = readIndexes[b.bookId] ?: -1,
            )
        }
    }

    /** 移动分组。 */
    suspend fun setGroup(bookId: String, group: String) = withContext(Dispatchers.IO) {
        checkId(bookId)
        require(group.length <= 24) { "分组名过长" }
        check(library.setGroup(bookId, group)) { "分组保存失败" }
    }

    suspend fun setBookStatus(bookId: String, status: String) = withContext(Dispatchers.IO) {
        checkId(bookId)
        check(library.setStatus(bookId, status)) { "状态保存失败" }
    }

    /**
     * 同步写状态，给阅读进度自动推进用。
     *
     * 走异步协程的话，读者翻到末章立刻退回书架时 [shelf] 可能先把旧值读回来，
     * 界面就停在「在读」。这里就在调用线程落盘（几 KB 的 shelf.json，跟进度落盘同量级）。
     */
    fun setBookStatusNow(bookId: String, status: String): Boolean = runCatching {
        checkId(bookId)
        library.setStatus(bookId, status)
    }.getOrDefault(false)

    suspend fun setBookRating(bookId: String, rating: Int) = withContext(Dispatchers.IO) {
        checkId(bookId)
        check(library.setRating(bookId, rating)) { "评分保存失败" }
    }

    suspend fun setBookTags(bookId: String, tags: List<String>) = withContext(Dispatchers.IO) {
        checkId(bookId)
        check(library.setTags(bookId, tags)) { "标签保存失败" }
    }

    /** 刷新在架书籍的章节数基线，并返回「有新章」的书目 id。 */
    suspend fun checkUpdates(items: List<ShelfItem>): Map<String, Int> = withContext(Dispatchers.IO) {
        val updated = linkedMapOf<String, Int>()
        items.forEach { item ->
            runCatching {
                val latest = book(item.book.source, item.book.bookId)
                val chapters = latest.chapterCount
                if (chapters > item.knownChapters && item.knownChapters > 0) {
                    updated[item.book.bookId] = chapters
                    // 章数变了，旧目录已经过期：不清掉的话 TOC 与下载都看不到新章
                    invalidateDirectory(item.book.bookId)
                }
                if (chapters > 0) library.markChapterBaseline(item.book.bookId, chapters)
                // 顺手把完本状态/在读人数等元数据刷新回书架，旧数据缺这些字段
                runCatching { library.put(latest) }
            }
        }
        updated
    }

    suspend fun addToShelf(source: String, bookId: String): Book {
        checkSource(source); checkId(bookId)
        val value = bookCache[bookId] ?: book(source, bookId)
        withContext(Dispatchers.IO) { library.put(value) }
        return value
    }

    suspend fun removeFromShelf(source: String, bookId: String) = withContext(Dispatchers.IO) {
        checkSource(source); checkId(bookId); library.remove(bookId)
    }

    /** 撤销移出：按备份条目写回本地书架，不重新拉书目（离线也能撤销）。 */
    suspend fun restoreToShelf(item: ShelfItem) = withContext(Dispatchers.IO) {
        library.restore(item)
    }

    suspend fun search(query: String, tabType: Int = 3): List<Book> =
        searchPage(query, tabType).books

    /**
     * 搜索一页。
     *
     * offset/passback 是上游的翻页游标：两者必须同时传，passback 通常等于 offset 的字符串，
     * 只给 offset 会让上游把这页当成第一页重发。
     */
    suspend fun searchPage(
        query: String,
        tabType: Int = 3,
        offset: Int = 0,
        passback: String = "",
    ): ContentParser.SearchPage {
        require(query.isNotBlank() && query.length <= 100) { "搜索词长度须为 1–100" }
        val pb = passback.ifEmpty { offset.toString() }
        val data = content(
            "search",
            "query=${enc(query.trim())}&tab_type=$tabType&count=$SEARCH_PAGE_SIZE&offset=$offset&passback=${enc(pb)}",
        )
        return ContentParser.searchPage(data, tabType).also { page ->
            page.books.onEach { bookCache[it.bookId] = it }
        }
    }

    /**
     * 短剧搜索。
     *
     * 只问 tab_type=11（短剧）。tab_type=2 是听书，结构完全不同
     * （book_data 而不是 video_data），DramaParser 在上面一条都解析不出来，
     * 拿它当兜底只会让「搜短剧」永远空着。听书有独立的 tab 和独立的解析。
     */
    suspend fun searchDrama(query: String): List<DramaSeries> {
        require(query.isNotBlank() && query.length <= 100) { "搜索词长度须为 1–100" }
        val term = enc(query.trim())
        return runCatching {
            DramaParser.dramaFeed(content("search", "query=$term&tab_type=11&count=$SEARCH_PAGE_SIZE&offset=0"))
        }.getOrDefault(emptyList())
    }

    suspend fun mangaFeed(query: String = "漫画", offset: Int = 0): List<Book> {
        val term = if (query.isBlank()) "漫画" else query.trim()
        val data = try {
            content("search", "query=${enc(term)}&tab_type=8&count=20&offset=$offset&passback=$offset")
        } catch (e: Exception) {
            content("search", "query=${enc(term)}&tab_type=3&count=20&offset=$offset&passback=$offset")
        }
        val books = ContentParser.search(data, tabType = 8).ifEmpty {
            ContentParser.search(data, tabType = 3)
        }
        return books.onEach { bookCache[it.bookId] = it }
    }

    suspend fun hotWords(): List<HotWord> = ContentParser.hotWords(content("search/hot"))

    /**
     * 首页：排行榜 + 猜你喜欢（同一份 recommend/homepage 载荷）。
     * offset 每页推进 10，实测每页约 27 本、连续 5 页仍几乎不重复，故用它做「换一批」。
     */
    suspend fun homeFeed(offset: Int = 0): ContentParser.HomeFeed {
        require(offset >= 0) { "offset 非法" }
        val feed = ContentParser.homeFeed(
            content("recommend/homepage", "tab_type=2&tab_index=0&offset=$offset"))
        (feed.ranked + feed.recommended).forEach { bookCache[it.bookId] = it }
        return feed
    }

    /**
     * 听书榜：recommend/homepage 的 tab_type=5（听书）那一格。
     * tab_index 必须与 tab_type 一起传——上游靠它决定回哪一格的 cell_data；
     * 少传会拿回推荐流的「爆款网文听听看」以外的格子。
     * 实测只有 offset=0 这一页，offset 再加就 SERVICE_ERROR。
     */
    suspend fun audioFeed(): List<Book> {
        val list = ContentParser.audioFeed(
            content("recommend/homepage", "tab_type=5&tab_index=5&offset=0"))
        list.forEach { bookCache[it.bookId] = it }
        return list
    }

    /** 新书速递：榜单目录里的「新书榜」。 */
    suspend fun newBooks(): List<Book> = board(algorithm = 108)

    suspend fun book(source: String, bookId: String): Book {
        checkSource(source); checkId(bookId)
        val data = content("books/$bookId").optJSONObject("data") ?: throw ApiException("书籍详情为空")
        val parsed = ContentParser.book(data)
        if (parsed.bookId != bookId) throw ApiException("返回的作品编号与请求不符")
        // 番茄的详情接口不是每次都给全字段：同一本书两次打开，第二次可能少回
        // word_number / score / read_count。缺少的项用缓存里已知的值兜住，
        // 否则详情页的字数会在「320 万字」和「—」之间来回跳。
        val result = bookCache[bookId]?.let { parsed.mergeFrom(it) } ?: parsed
        bookCache[bookId] = result
        return result
    }

    /** 丢弃某本书的目录缓存。检测到新章后必须调用，否则 TOC 与下载永远用旧目录。 */
    fun invalidateDirectory(bookId: String) { directoryCache.remove(bookId) }

    suspend fun chapters(source: String, bookId: String): List<Chapter> {
        checkSource(source); checkId(bookId)
        directoryCache[bookId]?.let { return it }
        val result = ContentParser.chapters(content("books/$bookId/directory"))
        if (result.isEmpty()) throw ApiException("目录暂不可用，请稍后重试", transient = true)
        directoryCache[bookId] = result
        return result
    }

    suspend fun chapter(source: String, bookId: String, chapterId: String): ChapterContent {
        checkSource(source); checkId(bookId); checkId(chapterId)
        val directory = chapters(source, bookId)
        val index = directory.indexOfFirst { it.id == chapterId }
        if (index < 0) throw ApiException("章节不属于当前作品")
        var mangaImgs = emptyList<String>()
        val payload = try {
            content("chapters/$chapterId")
        } catch (e: Exception) {
            null
        }
        val data = payload?.optJSONObject("data")
        val text = data?.str("content").orEmpty()
        if (text.contains("picInfos") || text.isBlank()) {
            mangaImgs = mangaImages(bookId, chapterId)
        }

        val paras = withContext(Dispatchers.Default) {
            if (mangaImgs.isNotEmpty()) {
                listOf(directory[index].title.ifEmpty { "漫画" })
            } else {
                if (text.isBlank()) emptyList()
                else {
                    // 只有真的看到标签才做 HTML 解析：上游有时给原始 HTML，
                    // 有时给已经带 \n 的纯文本（原生核心的 full 回退路径），
                    // 纯文本再过一遍 Html.fromHtml 会被折叠成空格，整章粘成一段。
                    val plain = Emoji.decode(
                        ChapterText.toPlain(text) { Html.fromHtml(it, Html.FROM_HTML_MODE_LEGACY).toString() }
                    )
                    Paragraphs.split(plain)
                }
            }
        }
        if (paras.isEmpty() && mangaImgs.isEmpty()) throw ApiException("章节正文为空")
        // 章节正文版本：段评请求必需。正文响应的 novel_data.version 与目录里的
        // item_data_list[].version 同源同值，两处都没有就留空（读段评会失败，
        // 但不能编一个版本号去骗上游）。
        val version = data?.optJSONObject("novel_data")?.str("version")
            ?.takeIf { it.isNotBlank() }
            ?: directory[index].version
        // 上游自己认的段落数（data.paragraphs_num）：段评下标换算的权威校验。
        // 上游首行是标题且不计它，本应用第 0 段恰是那行标题，所以两者差 1。
        // 拿不到就留 0，由 ChapterContent.upstreamParagraphIndex 判定「换算不出」。
        val upstreamParagraphs = data?.long("paragraphs_num", 0L)
            ?.takeIf { it in 1..Int.MAX_VALUE.toLong() }?.toInt() ?: 0
        return ChapterContent(chapterId, directory[index].title, paras,
            directory.getOrNull(index - 1)?.id, directory.getOrNull(index + 1)?.id, version,
            upstreamParagraphs, mangaImgs)
    }

    /**
     * 拉取漫画章节图片列表：调用 /api/v1/manga/{chapterId}?book_id={bookId}。
     * 自动将本地相对路径（如 /src/img_*.jpg）转换为当前应用沙箱内的绝对路径。
     */
    suspend fun mangaImages(bookId: String, chapterId: String): List<String> = withContext(Dispatchers.IO) {
        checkId(chapterId)
        val payload = try {
            content("manga/$chapterId", "book_id=$bookId")
        } catch (e: Exception) {
            return@withContext emptyList()
        }
        val rawList = ContentParser.mangaImages(payload)
        rawList.map { path ->
            if (path.startsWith("/src/")) NativeCore.localFile(app, path) else path
        }
    }

    /**
     * 批量拉取章节正文（优先走 chapters/full 握手批量解密，失败自动降级 chapters/batch）。
     * 返回 Map<chapterId, contentText>，缺失或空正文项已过滤。
     */
    suspend fun chaptersBatch(bookId: String, chapterIds: List<String>): Map<String, String> =
        withContext(Dispatchers.IO) {
            if (chapterIds.isEmpty()) return@withContext emptyMap()
            checkId(bookId)
            val query = "book_id=$bookId&item_ids=${chapterIds.joinToString(",")}"
            val payload = try {
                content("chapters/full", query)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                try {
                    content("chapters/batch", "item_ids=${chapterIds.joinToString(",")}")
                } catch (e2: CancellationException) {
                    throw e2
                } catch (_: Exception) {
                    throw e
                }
            }
            val raw = ContentParser.batchChapters(payload)
            raw.mapValues { (_, text) ->
                // chapters/full 返回时已经是带 \n 的纯文本（原生核心的
                // FullContentProcessor 干过活了），只有 chapters/batch 才是原始 HTML。
                // 对纯文本再跑一遍 Html.fromHtml，Android 会把换行当普通空白折叠成空格，
                // 整章粘成一段 —— 段落号与在线阅读、段评下标全部错位。
                val plain = ChapterText.toPlain(text) {
                    Html.fromHtml(it, Html.FROM_HTML_MODE_LEGACY).toString()
                }
                Emoji.decode(plain).trim()
            }
        }

    suspend fun related(source: String, bookId: String): List<Book> {
        checkSource(source); checkId(bookId)
        return ContentParser.books(content("books/$bookId/related")).onEach { bookCache[it.bookId] = it }
    }

    /** 榜单目录（榜种 + 分类）来自首页载荷，只取一次。 */
    private var rankCatalogCache: ContentParser.RankCatalog? = null

    suspend fun rankCatalog(): ContentParser.RankCatalog {
        rankCatalogCache?.let { if (it.tabs.isNotEmpty()) return it }
        val catalog = ContentParser.rankCatalog(content("recommend/homepage", "tab_type=2&tab_index=0"))
            ?: throw ApiException("上游暂未提供榜单目录")
        rankCatalogCache = catalog
        return catalog
    }

    /**
     * 任意榜种（推荐/完本/巅峰/新书/漫剧/短剧）× 分类。
     * 上游分类表不含可靠性别字段，故不伪造女频专属榜。
     */
    suspend fun board(algorithm: Int, categoryId: String = "0"): List<Book> {
        val catalog = rankCatalog()
        require(algorithm in catalog.tabs.map { it.algorithm }) { "榜种不在目录内" }
        require(categoryId.matches(Regex("[0-9]{1,24}"))) { "分类编号格式错误" }
        return ContentParser.books(
            content("rank/${enc(catalog.id)}", "algo_type=$algorithm&rank_sub_info_id=$categoryId"))
            .onEach { bookCache[it.bookId] = it }
    }

    /**
     * 书评广场：走 `books/{id}/comments` 老接口。
     *
     * 为什么不是 `books/{id}/reviews`：那条路要求 sort 传字符串枚举
     * （smart_hot / time_desc），而核心层的 body 把它序列化成 int，
     * 实测 sort=0 直接 400「sort is not smart_hot」——「最多赞」永远是空的。
     * 老接口不提 sort，默认就是最赞，还自带 reply_list 和内嵌评分。
     */
    suspend fun bookComments(bookId: String, offset: Int = 0, count: Int = 20): BookCommentPage {
        checkId(bookId)
        require(count in 1..50) { "书评条数非法" }
        require(offset >= 0) { "书评偏移量非法" }
        return SocialParser.bookComments(
            content("books/$bookId/comments", "count=$count&offset=$offset"))
    }

    /** 评论楼中楼回复列表：/comments/{id}/replies。 */
    suspend fun commentReplies(
        commentId: String,
        bookId: String,
        groupId: String = bookId,
        count: Int = 20,
        cursor: String = "",
    ): CommentReplyPage {
        val q = buildString {
            append("book_id=${enc(bookId)}&group_id=${enc(groupId)}&count=$count")
            if (cursor.isNotEmpty()) {
                append("&cursor=").append(enc(cursor))
            }
        }
        val resp = content("comments/$commentId/replies", q)
        return SocialParser.commentReplies(resp)
    }

    /** 评论/回复/短剧评论点赞与取消点赞：/comments/{id}/digg。 */
    suspend fun diggComment(
        commentId: String,
        liked: Boolean = true,
        targetType: Int = 1,
        bookId: String = "",
    ): Boolean {
        val q = buildString {
            append("liked=$liked&target_type=$targetType")
            if (bookId.isNotEmpty()) append("&book_id=${enc(bookId)}")
        }
        val resp = NativeCore.request(app, "/api/v1/comments/$commentId/digg", q, "POST", "")
        return resp.optInt("code", -1) == 0 || resp.optString("message") == "success" || resp.optJSONObject("data") != null
    }

    /** 分类清单。id 与榜单 rank_sub_info_id 同源，可直接喂给 [board]。 */
    suspend fun categories(): List<BookCategory> = ContentParser.categories(content("category"))

    /** 分享文案与落地页地址。 */
    suspend fun share(bookId: String): ShareInfo {
        checkId(bookId)
        return ContentParser.shareInfo(content("books/$bookId/share"))
    }

    /** 章内段评分布，用于正文段尾气泡。 */
    suspend fun paragraphBubbles(chapterId: String): List<ParagraphBubble> {
        checkId(chapterId)
        return SocialParser.paragraphBubbles(content("chapters/$chapterId/reviews"))
    }

    /**
     * 某个段落的段评正文：`books/{id}/reviews` + `para_index`。
     *
     * 这条路和 [bookHotReviews] 是**同一条上游链路**，只是多带
     * `para_index`（段落下标）与 `item_version`（章节正文版本）把结果收敛到一段。
     *
     * 三个不能省的参数：
     *  - `server_channel` **必须 38**：官方 presenter 里那个 43 也能拿到 code=0，
     *    但恒返回 total=0；39 只对部分书生效。38 是实测能出数的那个。
     *  - `item_version` **必须给**：上游缺 book_id / item_version / para_index 任一项
     *    就直接拒为 `103001 book_id, item_version, or para_index invalid`。
     *    版本来自目录的 `item_data_list[].version` 或 `chapters/{id}` 的
     *    `data.novel_data.version`（两者同源同值）。
     *  - `group_id` 是**章节 id**（段评挂在章节上），不是书 id；
     *    `business_param.book_id` 才放书 id。
     *
     * 翻页：`common_list_info.cursor` 是数字字符串（形如 `"5"`），
     * **原样回传**，上游认的是这个字符串本身。
     */
    suspend fun paragraphComments(
        bookId: String,
        chapterId: String,
        itemVersion: String,
        paraIndex: Int,
        count: Int = 20,
        cursor: String = "",
    ): ParagraphCommentPage {
        checkId(bookId)
        checkId(chapterId)
        require(itemVersion.isNotBlank()) { "章节版本缺失，无法读取段评" }
        require(paraIndex >= 0) { "段落下标非法" }
        require(count in 1..50) { "段评条数非法" }
        val query = buildString {
            append("book_id=").append(bookId)
            append("&group_id=").append(chapterId)
            // group_type=15：段落（NovelItemComment）容器；写错会拿不到段评
            append("&group_type=15")
            append("&comment_source=2")
            append("&comment_type=1")
            // 38 是实测唯一稳定出数的渠道，见上方说明
            append("&server_channel=38")
            append("&para_index=").append(paraIndex)
            append("&item_version=").append(enc(itemVersion))
            append("&count=").append(count)
            if (cursor.isNotBlank()) {
                append("&cursor=").append(enc(cursor))
            }
        }
        return SocialParser.paragraphComments(content("books/$bookId/reviews", query))
    }

    /**
     * 全书热评：`books/{id}/reviews`。
     *
     * 与 [bookComments] 走的是两条不同的上游链路：
     *  - 老接口 `books/{id}/comments` 自带 reply_list、offset 分页，但只是老式书评；
     *  - 这条 `books/{id}/reviews` 给的是官方热评（评分、点赞、回复数齐全），
     *    代价是翻页靠 cursor。
     *
     * **path 里不能带 source 段**（上游只有 `books/{id}/reviews` 这一种形状），
     * count 只能作为独立 query 参数传。cursor 是上游给的 JSON 字符串，
     * 翻页时原样回传，不要自己拼 offset。
     */
    suspend fun bookHotReviews(
        bookId: String,
        count: Int = 20,
        cursor: String = "",
    ): BookHotReviewPage {
        checkId(bookId)
        require(count in 1..50) { "书评条数非法" }
        val query = buildString {
            append("count=").append(count)
            if (cursor.isNotBlank()) {
                // cursor 本身是 JSON（含引号与花括号），必须整体编码后再拼
                append("&cursor=").append(enc(cursor))
            }
        }
        return SocialParser.bookHotReviews(content("books/$bookId/reviews", query))
    }

    /** 作者主页 + 作品。作者 id 形如 2_69149…（_ 分隔段），故不走 checkId。 */
    suspend fun author(authorId: String): AuthorProfile {
        require(authorId.matches(Regex("[0-9_]{1,40}"))) { "作者编号格式错误" }
        val data = content("authors/$authorId").optJSONObject("data") ?: throw ApiException("作者信息为空")
        val works = ContentParser.books(data.optJSONArray("author_book_info") ?: JSONObject.NULL)
            .onEach { bookCache[it.bookId] = it }
        return AuthorProfile(
            authorId = authorId,
            name = data.str("user_name"),
            avatar = data.str("expand_user_avatar", data.str("user_avatar")),
            description = data.str("author_desc", data.str("description")),
            fans = data.long("fans_num"),
            works = works,
        )
    }

    /**
     * 真人音频：先取该书的音频目录（章节标题），再按章节 id 取播放地址。
     * 不走 audio/play —— 实测它对当前设备身份恒返回 `invalid aid`，而上游章节接口能直接给出 CDN 地址。
     */
    /**
     * 有声版本清单。**必须**先用小说 id 取 tones 拿 abook_id；
     * 拿小说 id 直接打 audio/books/… 会得到 NO_THIS_TONE。
     */
    suspend fun audioTones(bookId: String): List<AudioTone> {
        checkId(bookId)
        return AudioParser.tones(content("books/$bookId/tones"))
    }

    suspend fun audioChapters(bookId: String, abookId: String): List<Chapter> {
        checkId(bookId); checkId(abookId)
        // 音频章节清单取自有声版本的目录，标题与小说目录可能不一致
        return ContentParser.chapters(content("books/$abookId/directory"))
    }

    /**
     * 真人音频。加密流交由 core 下载并 CENC 解密后落 /src（cache=1），
     * 否则系统播放器只能播到加密数据。cacheBase 用于把 /src 路径拼成本机可读 URL。
     */
    suspend fun audioChapter(abookId: String, chapterId: String, title: String): AudioChapter {
        checkId(abookId); checkId(chapterId)
        val payload = content("audio/books/$abookId/chapters/$chapterId", "cache=1")
        val chapter = AudioParser.chapter(chapterId, title, payload)
            ?: throw ApiException("该章暂无音频地址", transient = false)
        // /src/xxx 是 core 数据目录下的相对路径，播放前换成绝对路径
        return chapter.copy(cachedUrl = NativeCore.localFile(app, chapter.cachedUrl))
    }

    /** 获取听书逐句时间轴字幕。 */
    suspend fun timeline(chapterId: String, toneId: String = "99"): ChapterTimeline =
        withContext(Dispatchers.IO) {
            checkId(chapterId)
            val payload = try {
                content("chapters/$chapterId/timeline", "tone_id=$toneId")
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                JSONObject()
            }
            TimelineParser.parse(chapterId, payload)
        }

    /** 短剧 / 漫剧推荐流：频道 tab 聚合 + 频道内 offset 分页，保证能一直下滑出新剧 */
    suspend fun dramaFeed(cellId: String = "", maxCells: Int = 10, offset: Int = 0): List<DramaSeries> =
        withContext(Dispatchers.IO) {
            val list = mutableListOf<DramaSeries>()
            val seen = mutableSetOf<String>()
            fun absorb(items: List<DramaSeries>) {
                for (item in items) {
                    if (seen.add(item.seriesId)) list.add(item)
                }
            }
            if (cellId.isNotEmpty()) {
                val payload = try {
                    content("recommend/series-feed", "cell_id=${enc(cellId)}")
                } catch (_: Exception) { JSONObject() }
                absorb(DramaParser.dramaFeed(payload))
            }
            val tabs = if (maxCells <= 1) intArrayOf(16) else intArrayOf(16, 17, 19, 20, 21)
            for (tab in tabs) {
                if (list.size >= FEED_TARGET) break
                val payload = try {
                    content("recommend/channels", "tab_type=$tab&last_tab_type=-1&offset=$offset")
                } catch (_: Exception) { JSONObject() }
                absorb(DramaParser.dramaFeed(payload))
            }
            if (list.isEmpty()) {
                // tab_type=11 才是短剧；用 2（听书）搜，短剧一条都解析不出来
                for (kw in listOf("热播", "短剧", "重生")) {
                    val searchPayload = try {
                        content("search", "query=${enc(kw)}&tab_type=11")
                    } catch (_: Exception) { JSONObject() }
                    val results = DramaParser.dramaFeed(searchPayload)
                    if (results.isNotEmpty()) {
                        list.addAll(results)
                        break
                    }
                }
            }
            list
        }

    /** 短剧 / 漫剧详情与选集 */
    suspend fun dramaDetail(seriesId: String, isMotionComic: Boolean = false): DramaSeries = withContext(Dispatchers.IO) {
        checkId(seriesId)
        val payload = try {
            if (isMotionComic) content("manga/videos/$seriesId")
            else content("series/$seriesId")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            try {
                if (isMotionComic) content("series/$seriesId") else content("manga/videos/$seriesId")
            } catch (_: Exception) {
                throw e
            }
        }
        DramaParser.dramaSeries(seriesId, payload)
    }

    /** 短剧单集视频播放源获取与本地解密路径解析 */
    suspend fun dramaEpisodePlay(videoId: String): DramaPlaySource = withContext(Dispatchers.IO) {
        checkId(videoId)
        val payload = content("videos/$videoId")
        val rawUrl = payload.optString("video_url")
        val localPath = if (rawUrl.startsWith("/src/")) {
            NativeCore.localFile(app, rawUrl)
        } else ""
        DramaPlaySource(
            videoId = videoId,
            videoUrl = rawUrl,
            localFilePath = localPath,
            durationMs = payload.optLong("duration_ms", 0L),
        )
    }

    /**
     * 短剧弹幕获取。
     *
     * 上游按 180 秒固定窗口切片，且同一个请求有四成左右会落到空窗
     * （返回 code=0 但 data_list 为空），所以这里重试到拿到数据为止。
     */
    suspend fun dramaDanmaku(
        seriesId: String,
        videoId: String,
        offsetMs: Long = 0L,
        durationSeconds: Int = 0,
    ): List<DanmakuItem> = withContext(Dispatchers.IO) {
        checkId(seriesId); checkId(videoId)
        // 上游只在时长不短于 180 秒时给整窗；给太大反而落到空窗。
        val duration = if (durationSeconds > 0) durationSeconds.coerceIn(180, 600) else 0
        val q = buildString {
            append("series_id=").append(enc(seriesId))
            append("&start_offset_time=").append(offsetMs)
            if (duration > 0) append("&playlet_item_duration=").append(duration)
        }
        var items: List<DanmakuItem> = emptyList()
        loop@ for (attempt in 0 until 3) {
            val payload = try {
                content("videos/$videoId/danmaku", q)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                JSONObject()
            }
            items = DramaParser.danmakuList(payload)
            if (items.isNotEmpty()) break@loop
            if (attempt < 2) delay(300L)
        }
        items
    }

    /**
     * 发送短剧弹幕。
     *
     * 上游要番茄登录态：没有账号 cookie 时返回 code=103006「用户未登录」。
     * 所以这里只认 code 为成功，失败就把上游原话带出去，
     * 别让调用方以为弹幕发出去了。
     */
    suspend fun sendDramaDanmaku(
        seriesId: String,
        videoId: String,
        text: String,
        offsetMs: Long,
    ): Boolean = withContext(Dispatchers.IO) {
        val trimmed = text.trim()
        require(trimmed.isNotEmpty()) { "弹幕内容不能为空" }
        checkId(seriesId); checkId(videoId)
        val q = buildString {
            append("text=").append(enc(trimmed))
            append("&series_id=").append(enc(seriesId))
            append("&offset=").append(offsetMs)
        }
        val resp = NativeCore.request(app, "/api/v1/videos/$videoId/danmaku/add", q, "POST", "")
        val code = resp.optInt("code", -1)
        val message = resp.optString("message")
        if (code != 0 && code != 200) {
            throw ApiException(message.ifEmpty { "弹幕发送失败（$code）" })
        }
        message == "success" || resp.optJSONObject("data") != null
    }

    suspend fun saveProgress(source: String, bookId: String, chapterId: String, offset: Int) = withContext(Dispatchers.IO) {
        checkSource(source); checkId(bookId); checkId(chapterId)
        check(offline.saveProgress(bookId, chapterId, offset)) { "阅读进度保存失败" }
    }

    companion object {
        /** 短剧推荐流累计到该数量即停止继续拉取 cell */
        private const val FEED_TARGET = 30

        /** 搜索每页条数；上游 count 超过 30 会被悄悄截断，20 是与 web 端一致的安全值 */
        const val SEARCH_PAGE_SIZE = 20

        fun enc(value: String): String = URLEncoder.encode(value, "UTF-8")
    }
}
