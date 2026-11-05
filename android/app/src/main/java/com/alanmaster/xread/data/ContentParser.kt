package com.alanmaster.xread.data

import org.json.JSONArray
import org.json.JSONObject

/**
 * 图片地址统一升 HTTPS。
 *
 * App 关了明文流量（`res/xml/network_security_config.xml`），上游给的
 * `http://` 封面会被系统直接拦掉，Coil 那边不报错、图就是不出来。
 * 同一张图换 https 能取到，所以这里统一改写，不去动明文开关。
 */
internal fun normalizeImageUrl(url: String): String {
    val s = url.trim()
    return when {
        s.startsWith("//") -> "https:$s"
        s.startsWith("http://") -> "https://" + s.substring(7)
        else -> s
    }
}

/** Boundary between fqapp's upstream JSON and the native reader's stable models. */
object ContentParser {
    fun book(node: JSONObject): Book {
        val n = node.optJSONObject("book_info") ?: node
        return Book(
            bookId = n.str("book_id"),
            title = n.str("book_name", n.str("title")),
            author = n.str("author"),
            authorId = n.str("author_id"),
            description = n.str("abstract", n.str("book_abstract_v2", n.str("description"))),
            coverUrl = listOf("thumb_url", "cover_url", "expand_thumb_url", "audio_thumb_url_hd", "cover")
                .firstNotNullOfOrNull { n.str(it).takeIf(String::isNotBlank) }
                ?.let { normalizeImageUrl(it) }.orEmpty(),
            category = parseCategory(n),
            score = n.str("score"),
            wordCount = maxOf(
                n.long("word_number"),
                n.long("word_count"),
                // 榜单载荷给的是 last_chapter_title / serial_count 这一组，
                // 没有 word_number；字数缺了就让详情页显示「—」而不是 0。
                n.long("wordNumber"),
            ),
            chapterCount = maxOf(
                n.long("chapter_number"),
                n.long("chapter_count"),
                n.long("serial_count"),
                n.long("serialCount"),
            ).toInt(),
            latestChapterTitle = n.str("last_chapter_title", n.str("latest_chapter_title")),
            // creation_status："0" 完本 / "1" 连载（实测口径）
            creationStatus = n.str("creation_status", n.str("creationStatus")),
            readCount = maxOf(n.long("read_count"), n.long("readCount")),
            serialCount = n.long("serial_count", n.long("serialCount")).toInt(),
            // book_type："1" 有声书 / "0" 文字作品。只有它能区分二者，
            // 小说条目也带 listen_count，have_stt_resource 又常缺失。
            isAudio = n.str("book_type") == "1",
        )
    }

    /**
     * 分类名。
     *
     * 上游的 `category` 字段**不可靠**：它在榜单载荷里被当成社交证明位，
     * 实测出现过 `category = "116个老书虫在看"` 这种值（真分类在
     * `pure_category_tags = "都市日常,仕途"` 里）。
     * 所以优先级是：`pure_category_tags` 第一段 → `category`(仅当不像计数文案) → `tags` 第一段。
     *
     * 「不像计数文案」的判据：含「在看 / 人在读 / 书友 / 人读过 / 收藏」这类词，
     * 或整串是「数字 + 人在…」的形状。宁可不显示分类，也不把社交证明当分类印在榜单上。
     */
    private val COERCION_MARKERS = listOf("在看", "人在读", "人读过", "书友", "人在追", "收藏")

    fun parseCategory(n: JSONObject): String {
        n.str("pure_category_tags").split(',', '，').firstOrNull()?.trim()
            ?.takeIf { it.isNotEmpty() }?.let { return it }
        val raw = n.str("category").trim()
        if (raw.isNotEmpty() && COERCION_MARKERS.none { raw.contains(it) }) return raw
        n.str("tags").split(',', '，').firstOrNull()?.trim()
            ?.takeIf { it.isNotEmpty() }?.let { return it }
        return ""
    }

    fun walk(node: Any?, depth: Int = 0, visit: (JSONObject) -> Unit) {
        if (depth > 20) return
        when (node) {
            is JSONObject -> {
                visit(node)
                node.keys().forEach { key -> walk(node.opt(key), depth + 1, visit) }
            }
            is JSONArray -> for (i in 0 until node.length()) walk(node.opt(i), depth + 1, visit)
        }
    }

    fun books(node: Any?): List<Book> = books(node, preferListen = false)

    /**
     * 听书结果要显示「在听人数」而不是「在读人数」。
     *
     * 上游同一本书给两个数：read_count（在读）与 listen_count（在听），
     * 有声条目上两者不等（实测 read_count=8876 / listen_count=14849）。
     * 沿用 read_count 会把「1.4 万人在听」印成在读人数。
     */
    private fun books(node: Any?, preferListen: Boolean): List<Book> {
        val result = linkedMapOf<String, Book>()
        walk(node) { n ->
            if (n.str("book_id").isNotEmpty() && (n.str("book_name").isNotEmpty() || n.str("title").isNotEmpty())) {
                val parsed = book(n)
                // listen_count 可能在 book_info 里，也可能在外层，两处都看。
                val listen = maxOf(n.long("listen_count"), n.optJSONObject("book_info")?.long("listen_count") ?: 0L)
                // 有声书在任何 tab 里都按「在听人数」显示：综合 tab 会把小说和有声书混在一条列表里，
                // 只看 tab 听不出这条是不是有声书，只有 book_type 能判。
                val book = if ((preferListen || parsed.isAudio) && listen > 0) parsed.copy(readCount = listen) else parsed
                result.putIfAbsent(book.bookId, book)
            }
        }
        return result.values.toList()
    }


    /** 搜索结果：首屏数据 + 翻页游标。 */
    data class SearchPage(
        val books: List<Book>,
        val hasMore: Boolean = false,
        /** 下一页的 offset；hasMore 为 true 时一定大于当前 offset。 */
        val nextOffset: Int = 0,
        /** 上游要求的回传串，通常就是 next_offset 的字符串形式。 */
        val passback: String = "",
    )

    fun search(payload: JSONObject, tabType: Int = 3): List<Book> = searchPage(payload, tabType).books

    /**
     * 解析一页搜索结果。
     *
     * 分页字段（has_more / next_offset / passback）挂在命中的那个 search_tabs 条目上，
     * 不在顶层 data 里 —— 其余 tab 的 has_more 恒为 false，抓错条目就会以为搜完了。
     */
    fun searchPage(payload: JSONObject, tabType: Int = 3): SearchPage {
        val tabs = payload.optJSONArray("search_tabs") ?: return SearchPage(books(payload.opt("data")))
        val all = (0 until tabs.length()).mapNotNull { tabs.optJSONObject(it) }
        val exact = all.firstOrNull { it.str("tab_type") == tabType.toString() }
        // 命中的 tab 可能整块为空（漫画/短剧在书籍 tab 之外经常没数据），此时退回书籍 tab：
        // 拿到的本来就是书籍结果，翻页游标也必须跟书籍 tab 走，否则页码会错位。
        // 但听书(2)与短剧(11)不回退：回退会把「有声书」悄悄换成「同名小说」，
        // 用户点开才发现根本不是刚搜到的那一本。
        val fallback = if (tabType != 3 && tabType != 2 && tabType != 11) {
            all.firstOrNull { it.str("tab_type") == "3" }
        } else null
        // 听书 tab 用「在听人数」，其它 tab 用「在读人数」。
        val preferListen = tabType == 2
        val entry = listOfNotNull(exact, fallback).firstOrNull { books(it, preferListen).isNotEmpty() }
            ?: return SearchPage(books(payload.opt("data"), preferListen))
        val found = books(entry, preferListen).distinctBy { it.bookId }
        val next = entry.optInt("next_offset", 0)
        return SearchPage(
            books = found,
            hasMore = entry.optBoolean("has_more", false) && next > 0,
            nextOffset = next,
            passback = entry.str("passback"),
        )
    }

    fun chapters(payload: JSONObject): List<Chapter> {
        val data = payload.optJSONObject("data") ?: payload
        val arr = data.optJSONArray("item_data_list") ?: return emptyList()
        val seen = hashSetOf<String>()
        return buildList {
            for (i in 0 until arr.length()) {
                val row = arr.optJSONObject(i) ?: continue
                val id = row.str("item_id")
                if (id.isEmpty() || !seen.add(id)) continue
                // version 是章节正文版本：段评接口（books/{id}/reviews + para_index）必需，
                // 缺了上游直接拒为 103001。目录里没给就留空串，不编造。
                add(Chapter(id, row.str("title", row.str("item_title")), row.str("volume_name"),
                    row.optBoolean("is_locked", false) || row.optBoolean("locked", false),
                    row.str("version")))
            }
        }
    }

    /**
     * 批量章节解析：兼容 chapters/full (data.item_infos 字典或数组)
     * 与 chapters/batch (data 字典)。
     * 返回 Map<chapterId, contentText>，其中 content 为解密正文。
     */
    fun batchChapters(payload: JSONObject): Map<String, String> {
        val data = payload.optJSONObject("data") ?: payload
        val result = mutableMapOf<String, String>()

        // 1. chapters/full: data.item_infos
        val itemInfos = data.opt("item_infos")
        if (itemInfos is JSONObject) {
            val keys = itemInfos.keys()
            while (keys.hasNext()) {
                val id = keys.next()
                val item = itemInfos.optJSONObject(id) ?: continue
                val text = item.str("content")
                if (text.isNotBlank()) {
                    result[id] = text
                }
            }
        } else if (itemInfos is org.json.JSONArray) {
            for (i in 0 until itemInfos.length()) {
                val item = itemInfos.optJSONObject(i) ?: continue
                val id = item.str("item_id").ifEmpty { item.str("id") }
                val text = item.str("content")
                if (id.isNotEmpty() && text.isNotBlank()) {
                    result[id] = text
                }
            }
        }

        // 2. chapters/batch: data 本身就是 { "<id>": { "content": "..." } }
        if (result.isEmpty()) {
            val keys = data.keys()
            while (keys.hasNext()) {
                val id = keys.next()
                if (id == "item_infos") continue
                val item = data.optJSONObject(id) ?: continue
                val text = item.str("content")
                if (text.isNotBlank()) {
                    result[id] = text
                }
            }
        }
        return result
    }

    /**
     * 漫画章节图片列表解析：兼容 /api/v1/manga/{id} 返回的 { "images": [...] }
     * 以及内嵌在 data 结构中的 images 数组。
     */
    fun mangaImages(payload: JSONObject): List<String> {
        val data = payload.optJSONObject("data") ?: payload
        val arr = data.optJSONArray("images")
            ?: data.optJSONArray("pic_list")
            ?: data.optJSONArray("pictures")
            ?: payload.optJSONArray("images")
            ?: return emptyList()
        val list = mutableListOf<String>()
        for (i in 0 until arr.length()) {
            when (val raw = arr.opt(i)) {
                is String -> if (raw.isNotBlank()) list.add(raw)
                is JSONObject -> {
                    val url = raw.optString("url").takeIf(String::isNotBlank)
                        ?: raw.optString("path").takeIf(String::isNotBlank)
                        ?: raw.optString("uri").takeIf(String::isNotBlank)
                    if (!url.isNullOrBlank()) list.add(url)
                }
            }
        }
        return list
    }

    fun hotWords(payload: JSONObject): List<HotWord> {
        val result = linkedSetOf<String>()
        walk(payload.opt("data")) { n -> n.str("tag_title").takeIf(String::isNotBlank)?.let(result::add) }
        return result.map(::HotWord)
    }

    data class RankTab(val name: String, val algorithm: Int)
    data class RankCategory(val name: String, val id: String)
    data class RankCatalog(val id: String, val tabs: List<RankTab>, val categories: List<RankCategory>)

    /**
     * 首页两个原生区块：上游把「排行榜」与「猜你喜欢」都塞在 tab_item[0].cell_data 里，
     * 只能按 cell_name 认，不能按位置猜。
     */
    data class HomeFeed(
        val ranked: List<Book> = emptyList(),
        val recommended: List<Book> = emptyList(),
    ) {
        val isEmpty: Boolean get() = ranked.isEmpty() && recommended.isEmpty()
    }

    fun homeFeed(payload: JSONObject): HomeFeed {
        val tabs = (payload.optJSONObject("data") ?: payload).optJSONArray("tab_item") ?: return HomeFeed()
        val first = (0 until tabs.length()).mapNotNull { tabs.optJSONObject(it) }
            .firstOrNull { (it.optJSONArray("cell_data")?.length() ?: 0) > 0 } ?: return HomeFeed()
        val cells = first.optJSONArray("cell_data") ?: return HomeFeed()
        var ranked = emptyList<Book>()
        var recommended = emptyList<Book>()
        for (i in 0 until cells.length()) {
            val cell = cells.optJSONObject(i) ?: continue
            val name = cell.str("cell_name")
            val found = books(cell).distinctBy { it.bookId }
            when {
                name.contains("排行") && ranked.isEmpty() -> ranked = found
                name.contains("猜你喜欢") || name.contains("推荐") -> if (recommended.isEmpty()) recommended = found
            }
        }
        return HomeFeed(ranked, recommended)
    }

    /**
     * 听书榜：上游 `recommend/homepage` 里 tab_type=5（听书）那一格。
     *
     * 不能复用 [homeFeed]：它只认「第一个有 cell_data 的 tab」，再按 cell_name 分流；
     * 听书藏在 tab_item[4]，cell_name 是「爆款网文听听看」，两条规则都匹配不上。
     * 上游按 tab_index 决定回哪一格的 cell_data，所以 tab_index 必须跟着 tab_type 一起传。
     */
    fun audioFeed(payload: JSONObject): List<Book> {
        val tabs = (payload.optJSONObject("data") ?: payload).optJSONArray("tab_item") ?: return emptyList()
        for (i in 0 until tabs.length()) {
            val tab = tabs.optJSONObject(i) ?: continue
            if (tab.optInt("tab_type", -1) != 5) continue
            val cells = tab.optJSONArray("cell_data") ?: continue
            val result = linkedMapOf<String, Book>()
            walk(cells) { n ->
                // 先 parse 再取 id：有的条目把书裹在 book_info 里，
                // 直接读外层 book_id 会漏掉。
                val parsed = book(n)
                val id = parsed.bookId
                if (id.isEmpty() || result.containsKey(id)) return@walk
                if (parsed.title.isEmpty()) return@walk
                // 听书榜给的是「在听人数」（listen_count），不是「在读」；
                // 直接沿用 read_count 会把「22.5 万人在听」印成在读人数。
                // listen_count 可能在 book_info 里，也可能在它外层，两处都看。
                val listen = maxOf(n.long("listen_count"), n.optJSONObject("book_info")?.long("listen_count") ?: 0L)
                result[id] = if (listen > 0) parsed.copy(readCount = listen) else parsed
            }
            return result.values.toList()
        }
        return emptyList()
    }

    /**
     * 分类页：`data.down_category[]`，id 与榜单的 rank_sub_info_id 同源。
     *
     * 封面字段名上游换过：老结构给 `cover`，新结构（`category_tab_config` 那套）
     * 给 `pic_url`，两个都读，免得以后再变成空白格。
     */
    fun categories(payload: JSONObject): List<BookCategory> {
        val data = payload.optJSONObject("data") ?: payload
        val arr = data.optJSONArray("down_category") ?: return emptyList()
        return (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            val id = o.str("id")
            val name = o.str("name")
            if (id.isEmpty() || name.isEmpty()) return@mapNotNull null
            val cover = listOf("pic_url", "cover", "cover_url", "thumb_url")
                .firstNotNullOfOrNull { o.str(it).takeIf(String::isNotBlank) }.orEmpty()
            BookCategory(id, name, normalizeImageUrl(cover), o.long("book_num").toInt())
        }
    }

    /** 分享文案：`data.share_url` / `copy_link_text` / `sub_text` / `text`。 */
    fun shareInfo(payload: JSONObject): ShareInfo {
        val data = payload.optJSONObject("data") ?: payload
        return ShareInfo(
            text = data.str("text"),
            copyLinkText = data.str("copy_link_text"),
            subText = data.str("sub_text"),
            shareUrl = data.str("share_url"),
        )
    }

    fun rankCatalog(payload: JSONObject): RankCatalog? {
        var result: RankCatalog? = null
        walk(payload) { n ->
            if (result == null) {
                val raw = n.optJSONObject("rank_with_category_data")
                val id = n.str("cell_id_str", n.str("cell_id"))
                if (raw != null && id.isNotEmpty()) {
                    val arr = raw.optJSONArray("rank_algo_list") ?: JSONArray()
                    val tabs = (0 until arr.length()).mapNotNull { arr.optJSONObject(it) }
                        .filter { it.str("rank_name").isNotEmpty() && it.optInt("rank_algo", -1) >= 0 }
                        .map { RankTab(it.str("rank_name"), it.optInt("rank_algo")) }
                    val cats = raw.optJSONArray("sub_info_list") ?: JSONArray()
                    val categories = (0 until cats.length()).mapNotNull { cats.optJSONObject(it) }
                        .filter { it.str("info_name").isNotEmpty() }
                        .map { RankCategory(it.str("info_name"), it.str("info_id", "0")) }
                    result = RankCatalog(id, tabs, categories)
                }
            }
        }
        return result
    }
}

/** 书评 / 段评 / 作者页的解析。字段名以实测上游响应为准，缺失即降级为空值。 */
object SocialParser {

    /**
     * 书评广场：`data.comment[]`，每条自带 user_info 与 reply_list。
     *
     * 与 [reviews] 的关键差异：分页靠 offset（next_offset 回传），没有 cursor；
     * 也不接受 sort 参数——上游对 int 值直接报 "sort is not smart_hot"。
     */
    fun bookComments(payload: JSONObject): BookCommentPage {
        val data = payload.optJSONObject("data") ?: return BookCommentPage(emptyList(), 0, 0, "", 0, false)
        val rows = data.optJSONArray("comment") ?: return BookCommentPage(emptyList(), 0, 0, "", 0, false)
        var fallback = 0
        val items = (0 until rows.length()).mapNotNull { i ->
            val c = rows.optJSONObject(i) ?: return@mapNotNull null
            // text 是正文；content_type 只说明排版，缺正文就不要造一条空评论
            val text = Emoji.decode(c.str("text").trim())
            if (text.isEmpty()) return@mapNotNull null
            val info = c.optJSONObject("user_info") ?: JSONObject()
            val rawId = c.str("comment_id")
            val id = if (rawId.isNotEmpty()) rawId
                else "c${c.long("create_timestamp")}_${fallback++}"
            val replies = (c.optJSONArray("reply_list") ?: JSONArray()).let { arr ->
                (0 until arr.length()).mapNotNull reply@{ j ->
                    val r = arr.optJSONObject(j) ?: return@reply null
                    val rt = Emoji.decode(r.str("text").trim())
                    if (rt.isEmpty()) return@reply null
                    val ri = r.optJSONObject("user_info") ?: JSONObject()
                    val rid = r.str("reply_id").ifEmpty {
                        r.str("comment_id").ifEmpty { "${id}_r$j" }
                    }
                    CommentReply(
                        id = rid,
                        user = ri.str("user_name", "读者"),
                        avatar = ri.str("user_avatar"),
                        text = rt,
                        digg = r.optInt("digg_count", 0),
                        createdAt = r.long("create_timestamp"),
                        // author=1 是作者本人；user_info.is_author 也标了同样的意思
                        isAuthor = r.optInt("author", 0) == 1 || ri.optBoolean("is_author", false),
                        replyToUser = r.str("reply_to_user_name").ifEmpty { r.optJSONObject("reply_to_user")?.str("user_name").orEmpty() },
                        userLiked = r.optInt("user_digg", 0) == 1 || r.optBoolean("is_digg", false),
                    )
                }
            }
            BookComment(
                id = id,
                user = info.str("user_name", "读者"),
                avatar = info.str("user_avatar"),
                text = text,
                score = c.str("score"),
                digg = c.optInt("digg_count", 0),
                replyCount = c.optInt("reply_count", replies.size),
                createdAt = c.long("create_timestamp"),
                isAuthor = info.optBoolean("is_author", false),
                replies = replies,
                userLiked = c.optInt("user_digg", 0) == 1 || c.optBoolean("is_digg", false),
            )
        }
        val next = data.optInt("next_offset", 0)
        return BookCommentPage(
            items = items,
            total = data.optInt("comment_cnt", 0),
            raters = data.optInt("score_cnt", 0),
            context = data.str("context"),
            // next_offset 为 0 时没有下一页；此时保持 0，由 has_more 兜底
            nextOffset = next,
            hasMore = data.optBoolean("has_more", false) && next > 0,
        )
    }

    /**
     * 段评：data.data 是以段落序号为键的对象，值里有 count / hot / is_author_comment。
     * 只保留有评论的段落，按段落序号升序。
     *
     * `hot` / `is_author_comment` 上游给的是数字字符串（实测 `"0"` / `"1"`），
     * 不能用 `optBoolean` 读——它只认 `"true"` / `"false"`，会把 `"1"` 读成 false。
     */
    fun paragraphBubbles(payload: JSONObject): List<ParagraphBubble> {
        val inner = payload.optJSONObject("data")?.optJSONObject("data") ?: return emptyList()
        return inner.keys().asSequence()
            .mapNotNull { key -> key.toIntOrNull()?.let { key to it } }
            .mapNotNull { (key, index) ->
                val node = inner.optJSONObject(key) ?: return@mapNotNull null
                val count = node.optInt("count", 0)
                if (count <= 0) return@mapNotNull null
                ParagraphBubble(index, count, node.flag("hot"),
                    node.flag("is_author_comment"))
            }
            .sortedBy { it.index }
            .toList()
    }

    /**
     * 全书热评：`data.data_list[]`，每条把内容拆成 common / stat / expand 三组。
     *
     * 与老接口 [bookComments] 的差别在三点：
     *  1. 正文在 `comment.common.content.text`，不是平铺的 `text`；
     *  2. 用户名与头像在 `comment.common.user_info.base_info` 里，要再下一层；
     *  3. 翻页是 cursor（JSON 字符串），不是 offset——cursor 必须原样回传。
     *
     * 缺字段一律降级为空值：正文为空就丢掉这条（不造空评论），
     * 其余字段缺失只影响这一项的展示，不影响整页。
     */
    fun bookHotReviews(payload: JSONObject): BookHotReviewPage {
        val data = payload.optJSONObject("data") ?: return BookHotReviewPage()
        val rows = data.optJSONArray("data_list") ?: return BookHotReviewPage()
        var fallback = 0
        val items = (0 until rows.length()).mapNotNull { i ->
            val row = rows.optJSONObject(i) ?: return@mapNotNull null
            val comment = row.optJSONObject("comment") ?: return@mapNotNull null
            val common = comment.optJSONObject("common") ?: JSONObject()
            val text = Emoji.decode(common.optJSONObject("content")?.str("text").orEmpty().trim())
            if (text.isEmpty()) return@mapNotNull null
            val base = common.optJSONObject("user_info")?.optJSONObject("base_info") ?: JSONObject()
            val stat = comment.optJSONObject("stat") ?: JSONObject()
            val expand = comment.optJSONObject("expand") ?: JSONObject()
            val rawId = comment.str("comment_id").ifEmpty { common.str("comment_id") }
            val id = if (rawId.isNotEmpty()) rawId else "hr${common.long("create_timestamp")}_${fallback++}"
            BookHotReview(
                id = id,
                user = base.str("user_name", "读者"),
                avatar = base.str("user_avatar"),
                text = text,
                score = expand.str("score"),
                digg = stat.optInt("digg_count", 0),
                replyCount = stat.optInt("reply_count", 0),
                createdAt = common.long("create_timestamp"),
                userLiked = stat.optInt("user_digg", 0) == 1 || stat.optBoolean("is_digg", false),
            )
        }
        val info = data.optJSONObject("common_list_info") ?: JSONObject()
        return BookHotReviewPage(
            items = items,
            total = info.optInt("total", 0),
            hasMore = info.optBoolean("has_more", false),
            cursor = info.str("cursor"),
        )
    }

    /**
     * 单个段落的段评：`books/{id}/reviews` + `para_index` + `item_version`。
     *
     * 响应形状与 [bookHotReviews] **完全一致**（同一条上游链路，只是请求多带两个
     * 参数把结果收敛到一段），所以条目解析直接复用同一套规则，不另起一套。
     *
     * 唯一的区别在翻页游标：全书热评的 cursor 是 JSON 字符串
     * （`{"session_id":…,"offset":20}`），而段评这边上游给的是**数字字符串**
     * （形如 `"5"`）。它必须原样回传，不能转成 int 再拼回去——
     * 上游认的就是那个字符串。
     */
    fun paragraphComments(payload: JSONObject): ParagraphCommentPage {
        val data = payload.optJSONObject("data") ?: return ParagraphCommentPage()
        val rows = data.optJSONArray("data_list") ?: return ParagraphCommentPage()
        var fallback = 0
        val items = (0 until rows.length()).mapNotNull { i ->
            val row = rows.optJSONObject(i) ?: return@mapNotNull null
            val comment = row.optJSONObject("comment") ?: return@mapNotNull null
            val common = comment.optJSONObject("common") ?: JSONObject()
            val text = Emoji.decode(common.optJSONObject("content")?.str("text").orEmpty().trim())
            // 正文为空就丢掉这一条：宁可少一条，也不造一条空评论充数
            if (text.isEmpty()) return@mapNotNull null
            val base = common.optJSONObject("user_info")?.optJSONObject("base_info") ?: JSONObject()
            val stat = comment.optJSONObject("stat") ?: JSONObject()
            val expand = comment.optJSONObject("expand") ?: JSONObject()
            val rawId = comment.str("comment_id").ifEmpty { common.str("comment_id") }
            val id = if (rawId.isNotEmpty()) rawId else "pc${common.long("create_timestamp")}_${fallback++}"
            BookHotReview(
                id = id,
                user = base.str("user_name", "读者"),
                avatar = base.str("user_avatar"),
                text = text,
                score = expand.str("score"),
                digg = stat.optInt("digg_count", 0),
                replyCount = stat.optInt("reply_count", 0),
                createdAt = common.long("create_timestamp"),
                userLiked = stat.optInt("user_digg", 0) == 1 || stat.optBoolean("is_digg", false),
            )
        }
        val info = data.optJSONObject("common_list_info") ?: JSONObject()
        return ParagraphCommentPage(
            items = items,
            total = info.optInt("total", 0),
            hasMore = info.optBoolean("has_more", false),
            // cursor 原样留成字符串：上游要的就是这个数字字符串本身
            cursor = info.str("cursor"),
        )
    }

    /** 评论楼中楼回复列表解析（/comments/{id}/replies）。 */
    fun commentReplies(payload: JSONObject): CommentReplyPage {
        val data = payload.optJSONObject("data") ?: payload
        val commonList = data.optJSONObject("common_list_info") ?: JSONObject()
        val hasMore = commonList.optBoolean("has_more", false) || data.optBoolean("has_more", false)
        val cursor = commonList.optString("cursor").ifEmpty { data.optString("cursor") }
        val total = commonList.optInt("total", data.optInt("total", 0))

        val arr = data.optJSONArray("reply_list")
            ?: data.optJSONArray("reply")
            ?: data.optJSONArray("data_list")
            ?: data.optJSONArray("items")
            ?: payload.optJSONArray("reply_list")
            ?: return CommentReplyPage(emptyList(), hasMore, cursor, total)

        val list = mutableListOf<CommentReply>()
        for (i in 0 until arr.length()) {
            val r = arr.optJSONObject(i) ?: continue
            val replyObj = r.optJSONObject("reply") ?: r
            val common = replyObj.optJSONObject("common") ?: JSONObject()
            val text = Emoji.decode(replyObj.optString("text").trim().ifEmpty {
                common.optJSONObject("content")?.optString("text").orEmpty().trim()
            })
            if (text.isEmpty()) continue
            val id = replyObj.optString("reply_id").ifEmpty {
                replyObj.optString("comment_id").ifEmpty { common.optString("comment_id").ifEmpty { r.optString("id") } }
            }
            val userInfo = replyObj.optJSONObject("user_info")?.let { it.optJSONObject("base_info") ?: it }
                ?: common.optJSONObject("user_info")?.let { it.optJSONObject("base_info") ?: it }
                ?: replyObj.optJSONObject("user")
                ?: JSONObject()
            val user = userInfo.optString("user_name").ifEmpty { userInfo.optString("name", "读者") }
            val avatar = userInfo.optString("user_avatar").ifEmpty { userInfo.optString("avatar_url") }
            val isAuthor = replyObj.optInt("author", 0) == 1 || userInfo.optBoolean("is_author", false)
            val stat = replyObj.optJSONObject("stat") ?: JSONObject()
            val digg = replyObj.optInt("digg_count", stat.optInt("digg_count", 0))
            val createdAt = replyObj.optLong("create_timestamp", common.optLong("create_timestamp", 0L))
            val replyToUser = replyObj.optString("reply_to_user_name").ifEmpty {
                replyObj.optJSONObject("reply_to_user")?.optString("user_name").orEmpty()
            }
            val replyToReplyId = replyObj.optString("reply_to_reply_id")
            val userLiked = stat.optInt("user_digg", 0) == 1 || replyObj.optBoolean("is_digg", false)

            list.add(
                CommentReply(
                    id = id,
                    user = user,
                    avatar = avatar,
                    text = text,
                    digg = digg,
                    createdAt = createdAt,
                    isAuthor = isAuthor,
                    replyToUser = replyToUser,
                    replyToReplyId = replyToReplyId,
                    userLiked = userLiked,
                )
            )
        }
        return CommentReplyPage(
            items = list,
            hasMore = hasMore,
            cursor = cursor,
            total = if (total > 0) total else list.size,
        )
    }
}

/** 真人音频解析。 */
object AudioParser {
    /** data.audio_tones[]：每项给出有声版本的 abook_id。 */
    fun tones(payload: JSONObject): List<AudioTone> {
        val data = payload.optJSONObject("data") ?: return emptyList()
        val arr = data.optJSONArray("audio_tones") ?: return emptyList()
        return (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            val id = o.str("abook_id")
            if (id.isEmpty() || id == "0") null
            else AudioTone(id, o.str("title"), o.str("icon_url"))
        }
    }

    /**
     * data 是数组，每项含 main_url / backup_url / is_encrypt。
     * 只取第一段非空地址；加密标记如实带出，不假装可播。
     */
    fun chapter(chapterId: String, title: String, payload: JSONObject): AudioChapter? {
        val rows = payload.opt("data") as? JSONArray ?: return null
        for (i in 0 until rows.length()) {
            val row = rows.optJSONObject(i) ?: continue
            val main = row.str("main_url")
            val backup = row.str("backup_url")
            if (main.isEmpty() && backup.isEmpty()) continue
            return AudioChapter(
                chapterId = chapterId,
                title = title,
                url = main.ifEmpty { backup },
                backupUrl = if (main.isNotEmpty()) backup else "",
                // is_encrypt 用 flag 而不是 optBoolean：上游有时给字符串 "True"/"1"，
                // optBoolean 只认 "true"/"false"，数字字符串一律读成 false。
                encrypted = row.flag("is_encrypt", false),
                cachedUrl = row.str("cached_url"),
                cacheError = row.str("cache_error"),
            )
        }
        return null
    }
}

/** 听书逐句时间轴解析器（解析 /chapters/{id}/timeline 接口的 speech_text） */
object TimelineParser {
    private val LINE_REGEX = Regex("""^\[(\d+)[^\]]*\](?:<[^>]*>)?(.*)$""")

    fun parse(chapterId: String, payload: JSONObject): ChapterTimeline {
        val data = payload.optJSONObject("data") ?: return ChapterTimeline(chapterId)
        val raw = data.optString("speech_text", "")
        if (raw.isBlank()) return ChapterTimeline(chapterId)

        val rawList = mutableListOf<TimelineSentence>()
        for (line in raw.lineSequence()) {
            val trimmed = line.trim()
            if (trimmed.isEmpty()) continue
            val match = LINE_REGEX.find(trimmed) ?: continue
            val startMs = match.groupValues[1].toIntOrNull() ?: continue
            val text = match.groupValues[2].trim()
            if (text.isNotEmpty()) {
                rawList.add(TimelineSentence(startMs = startMs, endMs = startMs, text = text))
            }
        }
        val sentences = rawList.mapIndexed { idx, item ->
            val nextStart = rawList.getOrNull(idx + 1)?.startMs
            val endMs = if (nextStart != null && nextStart > item.startMs) nextStart else item.startMs + 5000
            item.copy(endMs = endMs)
        }
        return ChapterTimeline(chapterId, sentences)
    }
}

/** 短剧与漫剧解析器 */
object DramaParser {

    fun parseCover(node: JSONObject): String {
        val keys = listOf(
            "series_cover",
            "series_cover_url",
            "cover",
            "cover_url",
            "thumb_url",
            "thumb_uri",
            "series_cover_uri",
            "expand_thumb_url",
            "audio_thumb_url_hd",
            "vertical_cover",
            "horizontal_cover",
            "poster",
            "poster_url",
            "image_url",
            "pic_url",
        )
        for (k in keys) {
            val v = node.opt(k) ?: continue
            val raw = when (v) {
                is String -> v.trim()
                is JSONObject -> {
                    val arr = v.optJSONArray("url_list") ?: v.optJSONArray("urls")
                    var found = ""
                    if (arr != null) {
                        for (i in 0 until arr.length()) {
                            val u = arr.optString(i).trim()
                            if (u.isNotEmpty() && !u.startsWith("{") && !u.startsWith("[")) {
                                found = u
                                break
                            }
                        }
                    }
                    found.ifEmpty {
                        v.optString("url").ifEmpty {
                            v.optString("src").ifEmpty {
                                v.optString("uri").ifEmpty {
                                    v.optString("cover_url")
                                }
                            }
                        }.trim()
                    }
                }
                is org.json.JSONArray -> {
                    var found = ""
                    for (i in 0 until v.length()) {
                        val item = v.optString(i).trim()
                        if (item.isNotEmpty() && !item.startsWith("{") && !item.startsWith("[")) {
                            found = item
                            break
                        }
                    }
                    found
                }
                else -> ""
            }
            if (raw.isNotEmpty() && !raw.startsWith("{") && !raw.startsWith("[")) {
                return normalizeUrl(raw)
            }
        }
        for (sub in listOf("video_data", "series_data", "book_info", "base_info")) {
            val child = node.optJSONObject(sub) ?: continue
            val c = parseCover(child)
            if (c.isNotEmpty()) return c
        }
        return ""
    }

    private fun normalizeUrl(url: String): String {
        var s = url.trim()
        if (s.startsWith("//")) {
            s = "https:$s"
        } else if (s.startsWith("http://")) {
            s = "https://" + s.substring(7)
        } else if (!s.startsWith("https://") && !s.contains("://")) {
            s = "https://p3-novel.byteimg.com/origin/" + s.trimStart('/')
        }
        return s
    }

    fun dramaSeries(seriesId: String, payload: JSONObject): DramaSeries {
        val data = payload.optJSONObject("data") ?: payload
        var title = data.str("series_title").ifEmpty {
            data.str("series_name").ifEmpty { data.str("title").ifEmpty { data.str("name") } }
        }
        var intro = data.str("series_intro").ifEmpty { data.str("intro").ifEmpty { data.str("abstract") } }
        var cover = parseCover(data)
        var author = data.str("author").ifEmpty { data.str("author_name") }
        val contentType = data.optInt("content_type", data.optInt("pseries_type", 1))
        var count = data.optInt(
            "episode_cnt",
            data.optInt("series_cnt", data.optInt("total_episode", data.optInt("episode_count", 0)))
        )
        if (title.isEmpty() || intro.isEmpty() || cover.isEmpty() || author.isEmpty()) {
            ContentParser.walk(data) { node ->
                if (title.isEmpty()) {
                    title = node.str("series_title").ifEmpty { node.str("series_name") }
                }
                if (intro.isEmpty()) {
                    intro = node.str("series_intro").ifEmpty { node.str("intro") }
                }
                if (cover.isEmpty()) cover = parseCover(node)
                if (author.isEmpty()) author = node.str("author").ifEmpty { node.str("author_name") }
                if (count <= 0) {
                    count = node.optInt(
                        "episode_cnt",
                        node.optInt("series_cnt", node.optInt("total_episode", 0))
                    )
                }
            }
        }

        val arr = data.optJSONArray("item_data_list")
            ?: data.optJSONArray("episodes")
            ?: data.optJSONArray("video_list")
            ?: data.optJSONArray("video_data")
        val episodes = mutableListOf<DramaEpisode>()
        if (arr != null) {
            for (i in 0 until arr.length()) {
                val row = arr.optJSONObject(i) ?: continue
                val epId = row.str("item_id").ifEmpty { row.str("video_id").ifEmpty { row.str("vid") } }
                if (epId.isEmpty()) continue
                val epTitle = row.str("title").ifEmpty { row.str("item_title").ifEmpty { "第 ${i + 1} 集" } }
                val epCover = parseCover(row)
                val epDuration = row.optInt("duration", row.optInt("item_duration", 0))
                val epIndex = row.optInt("episode_index", i + 1)
                episodes.add(DramaEpisode(
                    episodeId = epId,
                    seriesId = seriesId,
                    episodeIndex = epIndex,
                    title = epTitle,
                    coverUrl = epCover.ifEmpty { cover },
                    durationSeconds = epDuration,
                ))
            }
        }
        if (episodes.isEmpty()) {
            val seen = mutableSetOf<String>()
            ContentParser.walk(data) { row ->
                val epId = row.str("vid").ifEmpty {
                    row.str("video_id").ifEmpty { row.str("item_id") }
                }
                if (epId.isNotEmpty() && seen.add(epId)) {
                    val epTitle = row.str("title").ifEmpty {
                        row.str("item_title").ifEmpty { "第 ${episodes.size + 1} 集" }
                    }
                    episodes.add(DramaEpisode(
                        episodeId = epId,
                        seriesId = seriesId,
                        episodeIndex = row.optInt("vid_index", row.optInt("episode_index", episodes.size + 1)),
                        title = epTitle,
                        coverUrl = parseCover(row).ifEmpty { cover },
                        durationSeconds = row.optInt("duration", 0),
                    ))
                }
            }
        }
        val epCount = if (count > 0) count else episodes.size
        return DramaSeries(
            seriesId = seriesId,
            title = title.ifEmpty { "短剧 $seriesId" },
            coverUrl = cover,
            intro = intro,
            author = author,
            episodeCount = epCount,
            contentType = contentType,
            episodes = episodes,
        )
    }

    fun dramaFeed(payload: JSONObject): List<DramaSeries> {
        val list = mutableListOf<DramaSeries>()
        val seen = mutableSetOf<String>()
        ContentParser.walk(payload) { node ->
            val sid = node.str("series_id").ifEmpty { node.str("album_id") }
            val title = node.str("series_title").ifEmpty { node.str("title").ifEmpty { node.str("name").ifEmpty { node.str("book_name") } } }
            if (sid.isNotEmpty() && title.isNotEmpty() && seen.add(sid)) {
                val cover = parseCover(node)
                val intro = node.str("series_intro").ifEmpty { node.str("intro").ifEmpty { node.str("abstract") } }
                val author = node.str("author").ifEmpty { node.str("author_name") }
                val cnt = node.optInt("episode_cnt", node.optInt("total_episode", 0))
                val cType = node.optInt("content_type", 1)
                list.add(DramaSeries(
                    seriesId = sid,
                    title = title,
                    coverUrl = cover,
                    intro = intro,
                    author = author,
                    episodeCount = cnt,
                    contentType = cType,
                ))
            }
        }
        return list
    }

    /**
     * 官方短剧弹幕列表。
     *
     * 上游真实载荷是嵌套的：文本在 comment.common.content.text，
     * 编号在 comment.comment_id，时间偏移在 comment.expand.offset_time。
     * 早期载荷是扁平的（comment_id / text / start_offset_time），继续兼容。
     */
    fun danmakuList(payload: JSONObject): List<DanmakuItem> {
        val list = mutableListOf<DanmakuItem>()
        val seen = mutableSetOf<String>()
        ContentParser.walk(payload) { node ->
            // 外层条目包着 comment；直接走到 comment 时用它自己。
            val c = node.optJSONObject("comment") ?: node
            val id = c.str("comment_id").ifEmpty { c.str("id") }
            val text = c.str("text").ifEmpty {
                c.optJSONObject("content")?.str("text").orEmpty().ifEmpty {
                    c.optJSONObject("common")?.optJSONObject("content")?.str("text").orEmpty()
                }
            }
            if (id.isNotEmpty() && text.isNotEmpty() && seen.add(id)) {
                val expand = c.optJSONObject("expand")
                val business = c.optJSONObject("business_param")
                val offset = when {
                    c.has("start_offset_time") -> c.long("start_offset_time")
                    c.has("offset_time") -> c.long("offset_time")
                    c.has("offset") -> c.long("offset")
                    expand != null && expand.has("offset_time") -> expand.long("offset_time")
                    business != null && business.has("start_offset_time") -> business.long("start_offset_time")
                    else -> 0L
                }
                val info = c.optJSONObject("user_info")
                    ?: c.optJSONObject("common")?.optJSONObject("user_info")
                val userName = c.str("user_name").ifEmpty {
                    info?.str("user_name")?.ifEmpty { info.optJSONObject("base_info")?.str("user_name").orEmpty() }.orEmpty()
                }
                val avatar = c.str("avatar_url").ifEmpty {
                    info?.str("avatar_url")?.ifEmpty { info.optJSONObject("base_info")?.str("avatar_url").orEmpty() }.orEmpty()
                }
                list.add(DanmakuItem(
                    id = id,
                    text = text,
                    offsetMs = offset,
                    userName = userName,
                    userAvatar = avatar,
                ))
            }
        }
        return list.sortedBy { it.offsetMs }
    }
}
