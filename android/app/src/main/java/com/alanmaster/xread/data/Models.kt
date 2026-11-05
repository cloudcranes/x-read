package com.alanmaster.xread.data

import org.json.JSONArray
import org.json.JSONObject

data class Book(
    val source: String = "fanqie",
    val bookId: String,
    val title: String,
    val author: String = "",
    val authorId: String = "",
    val description: String = "",
    val coverUrl: String = "",
    val category: String = "",
    val score: String = "",
    val wordCount: Long = 0,
    val chapterCount: Int = 0,
    val latestChapterTitle: String = "",
    /** `creation_status`：**"0" 是完本，"1" 是连载**（实测口径，见 [BookStatus]）。 */
    val creationStatus: String = "",
    /** 在读人数。上游给字符串；没有就是 0，不猜。 */
    val readCount: Long = 0,
    /** 连载中已发章节数（`serial_count`）；完本时为 0 或缺失。 */
    val serialCount: Int = 0,
    /**
     * 上游 `book_type`：**"1" 是有声书**，"0" 是文字作品。
     *
     * 只有这一位能判定「这是不是有声书」：小说条目同样带 listen_count（甚至数值相近），
     * `have_stt_resource` 在小说节点上又经常缺失。综合 tab 会把小说与有声书混在一条列表里，
     * 靠 tab 猜会把有声书当小说打开。
     */
    val isAudio: Boolean = false,
) {
    val status: BookStatus get() = BookStatus.of(creationStatus)

    /**
     * 用 [fresh] 覆盖自己，但**只覆盖上游真正给了的值**。
     *
     * 番茄详情接口不是每次都给全字段：同一本书第二次打开可能少了 `word_number`
     * 或 `score`，直接替换会让详情页的字数从「320 万字」变成「—」、评分从 9.9 变成空。
     * 空串与 0 在这里一律视为「这次没给」，沿用上一份已知值；给了非空值就以新值为准
     * （章节数会增长、在读人数会变，这些是真实变化，不能锁死）。
     */
    fun mergeFrom(fresh: Book): Book = Book(
        source = fresh.source.ifBlank { source },
        bookId = fresh.bookId.ifBlank { bookId },
        title = fresh.title.ifBlank { title },
        author = fresh.author.ifBlank { author },
        authorId = fresh.authorId.ifBlank { authorId },
        description = fresh.description.ifBlank { description },
        coverUrl = fresh.coverUrl.ifBlank { coverUrl },
        category = fresh.category.ifBlank { category },
        score = fresh.score.ifBlank { score },
        wordCount = if (fresh.wordCount > 0) fresh.wordCount else wordCount,
        chapterCount = if (fresh.chapterCount > 0) fresh.chapterCount else chapterCount,
        latestChapterTitle = fresh.latestChapterTitle.ifBlank { latestChapterTitle },
        creationStatus = fresh.creationStatus.ifBlank { creationStatus },
        readCount = if (fresh.readCount > 0) fresh.readCount else readCount,
        serialCount = if (fresh.serialCount > 0) fresh.serialCount else serialCount,
        isAudio = fresh.isAudio || isAudio,
    )
}


/**
 * 连载状态。
 *
 * 口径来自实测而非文档：抓 40 条榜单样本交叉核对末章标题——
 * `creation_status = "0"` 的 7 条末章全是「完结 / 番外 / 大结局」，
 * `"1"` 的 33 条均无完结信号。所以 **0 = 完本，1 = 连载**。
 * 上游没有再给第三种值；未知一律按「连载」处理，避免把在更的书标成完本。
 */
enum class BookStatus {
    Serial,
    Finished,
    Unknown;

    companion object {
        fun of(raw: String): BookStatus = when (raw.trim()) {
            "0" -> Finished
            "1" -> Serial
            else -> Unknown
        }
    }
}

/**
 * 目录里的一章。
 *
 * [version] 是章节正文版本（形如 `0e4a6f22…_1_e4bb696f…`）：
 * 段评接口（`books/{id}/reviews` + `para_index`）**必须**带上它，
 * 上游缺 book_id / item_version / para_index 任一项都会直接拒为
 * `103001 book_id, item_version, or para_index invalid`。
 * 目录 `item_data_list[].version` 与 `chapters/{id}` 的 `data.novel_data.version` 同源同值。
 */
data class Chapter(
    val id: String,
    val title: String,
    val volume: String = "",
    val locked: Boolean = false,
    /** 章节正文版本；段评请求必需，空串表示上游没给（此时读段评会失败）。 */
    val version: String = "",
)

/**
 * 应用段落下标与上游段评下标（`para_index`）之间的偏移。
 *
 * 依据（实测，47 章 + 750 余条评论全文比对）：
 *  - 上游 `chapters/{id}` 正文首行就是章节标题，`paragraphs_num` 不计它；
 *  - 本应用的 [ChapterContent.paragraphs] 第 0 段是那一行标题；
 *  - 所以同一段文字在两边的下标差 1：应用下标 = 上游下标 + 1。
 */
object ParagraphOffsets {
    /** 应用第 0 段是标题行，上游从正文段 0 开始数，偏移 1。 */
    const val UPSTREAM_TITLE_OFFSET = 1
}

data class ChapterContent(
    val id: String,
    val title: String,
    val paragraphs: List<String>,
    val previousId: String?,
    val nextId: String?,
    /** 章节正文版本（`data.novel_data.version`）；段评请求必需。 */
    val version: String = "",
    /**
     * 上游自己认的段落数（`data.paragraphs_num`）；0 表示上游没给。
     *
     * 它是段评下标换算的**权威校验**：段评气泡的键与 `para_index` 都在这个
     * 坐标系里（0-based、不计标题），而本应用的 [paragraphs] 第 0 段是章节标题行。
     * 只有当上游报的段落数恰好等于应用段落数减一时，两边才对得上；
     * 拿不到（0）或对不上时**不能猜**，段评一律不给气泡、不发请求。
     */
    val upstreamParagraphs: Int = 0,
    /** 漫画/图文章节图片路径列表（本机绝对路径或 URL）；非空时为漫画模式。 */
    val images: List<String> = emptyList(),
) {
    val isManga: Boolean get() = images.isNotEmpty()

    val text: String get() = paragraphs.joinToString("\n")

    /**
     * 应用段落下标 → 上游段评下标（`para_index`）；-1 表示换算不出来。
     *
     * 换算不出来的三种情形：上游没报段落数、应用段落数与上游对不上、
     * 下标越界（标题行本身没有段评，或超出正文末尾）。调用方据此不给气泡。
     */
    fun upstreamParagraphIndex(index: Int): Int {
        if (upstreamParagraphs <= 0) return -1
        // 正文可能把标题行算成第 0 段（差 1），也可能拆掉了标题行（相等）。
        val shift = paragraphs.size - upstreamParagraphs
        if (shift != 0 && shift != ParagraphOffsets.UPSTREAM_TITLE_OFFSET) return -1
        val upstream = index - shift
        if (upstream < 0 || upstream >= upstreamParagraphs) return -1
        return upstream
    }

    /** 章内查找：返回命中段落下标，保持顺序。纯函数，便于离线单测。 */
    fun findHits(query: String): List<Int> {
        val q = query.trim()
        if (q.isEmpty()) return emptyList()
        return paragraphs.mapIndexedNotNull { index, para -> if (para.contains(q)) index else null }
    }
}

data class HotWord(val word: String)

data class ShelfItem(
    val book: Book,
    val chapterId: String,
    val chapterOffset: Int,
    val updatedAt: String,
    /** 本地分组名；空串表示未分组。 */
    val group: String = "",
    /** 入架时间（毫秒）；0 表示未知。 */
    val addedAt: Long = 0,
    /** 上次已知的章节数，用于「有更新」提醒。 */
    val knownChapters: Int = 0,
    /** 上次阅读时间（毫秒，来自 progress.json 的 at）；0 表示从未读过。 */
    val lastReadAt: Long = 0,
    /** 已读到的章节下标（0 起，来自 progress.json 的 chapterIndex）；-1 表示未知。 */
    val readChapterIndex: Int = -1,
    /** 用户侧标记：想读 / 在读 / 读完。与 [Book.status]（上游连载/完本）是两回事。 */
    val status: String = STATUS_READING,
    /** 个人评分 0–5；0 表示没评。 */
    val rating: Int = 0,
    /** 个人标签，最多 [TAG_LIMIT] 个。 */
    val tags: List<String> = emptyList(),
) {
    companion object {
        const val STATUS_WANT = "want"
        const val STATUS_READING = "reading"
        const val STATUS_DONE = "done"
        val STATUSES = listOf(STATUS_WANT, STATUS_READING, STATUS_DONE)
        /** 顺序即界面顺序：想读 → 在读 → 读完。 */
        val STATUS_LABELS = linkedMapOf(
            STATUS_WANT to "想读",
            STATUS_READING to "在读",
            STATUS_DONE to "读完",
        )
        const val TAG_LIMIT = 6

        fun labelOf(status: String): String = STATUS_LABELS[status] ?: STATUS_LABELS.getValue(STATUS_READING)
    }
}

fun JSONObject.str(key: String, fallback: String = ""): String {
    if (!has(key) || isNull(key)) return fallback
    return optString(key, fallback)
}

/**
 * 上游的布尔标记：值可能是真布尔，也可能是数字字符串。
 *
 * 实测段评气泡的 `hot` 是字符串 `"0"` / `"1"`（不是布尔）。`JSONObject.optBoolean`
 * 只认 `"true"` / `"false"` 这种字符串，遇到 `"1"` 一律返回 false——用它读
 * 数字字符串的标记会把 `1` 误判成 false。这里自己解析，数字字符串按数值判真假。
 */
fun JSONObject.flag(key: String, fallback: Boolean = false): Boolean {
    if (!has(key) || isNull(key)) return fallback
    return when (val v = opt(key)) {
        is Boolean -> v
        is Number -> v.toInt() != 0
        is String -> when {
            v.isEmpty() -> fallback
            v == "0" -> false
            v == "1" -> true
            else -> v.equals("true", ignoreCase = true)
        }
        else -> fallback
    }
}

fun JSONObject.long(key: String, fallback: Long = 0L): Long {
    if (!has(key) || isNull(key)) return fallback
    return when (val v = opt(key)) {
        is Number -> v.toLong()
        is String -> v.toLongOrNull() ?: fallback
        else -> fallback
    }
}

fun JSONObject.book(): Book = Book(
    source = str("source", "fanqie"),
    bookId = str("book_id"),
    title = str("title"),
    author = str("author"),
    // shelf.json 里写的是 author_id；不读回来会让重启后的书架书丢掉「作者主页」入口
    authorId = str("author_id"),
    description = str("description"),
    coverUrl = str("cover_url"),
    category = str("category"),
    score = str("score"),
    wordCount = long("word_count"),
    chapterCount = long("chapter_count").toInt(),
    latestChapterTitle = str("latest_chapter_title"),
    creationStatus = str("creation_status"),
    readCount = long("read_count"),
    serialCount = long("serial_count").toInt(),
)

fun JSONArray.books(): List<Book> = buildList {
    for (i in 0 until length()) optJSONObject(i)?.let { add(it.book()) }
}

fun JSONArray.chapters(): List<Chapter> = buildList {
    for (i in 0 until length()) {
        val o = optJSONObject(i) ?: continue
        val id = o.str("chapter_id")
        if (id.isEmpty()) continue
        add(Chapter(id, o.str("title"), o.str("volume"), o.optBoolean("locked", false)))
    }
}

data class AuthorProfile(
    val authorId: String,
    val name: String,
    val avatar: String,
    val description: String,
    val fans: Long,
    val works: List<Book>,
)

/** 段评气泡：段落序号 → 条数与是否热评。 */
data class ParagraphBubble(val index: Int, val count: Int, val hot: Boolean, val authorCommented: Boolean)

/**
 * 某一段落的段评一页。
 *
 * 走的是全书热评同一条上游链路（`books/{id}/reviews`），只是多带了
 * `para_index` + `item_version`，把结果收敛到单个段落。
 * 条目模型复用 [BookHotReview]：响应形状完全一致（内容在 `common.content.text`，
 * 用户名在 `common.user_info.base_info`，点赞/回复数在 `stat`，评分在 `expand.score`），
 * 没有理由再造一个字段相同的类型。
 *
 * [cursor] 是上游给的**数字字符串**（形如 `"5"`），翻页时**原样回传**，
 * 不能转成 int 再拼回去——上游认的是字符串。
 */
data class ParagraphCommentPage(
    val items: List<BookHotReview> = emptyList(),
    /** 该段落的段评总数（`common_list_info.total`）。 */
    val total: Int = 0,
    /** 还有没有下一页（`common_list_info.has_more`）。 */
    val hasMore: Boolean = false,
    /** 翻页游标（`common_list_info.cursor`），原样回传。 */
    val cursor: String = "",
)

/**
 * 书评广场条目，来自 `books/{id}/comments`（老接口）。
 *
 * 与 `books/{id}/reviews` 的区别：老接口用 offset 分页、自带 reply_list、
 * 且不接受 sort 枚举参数（新接口传 int 会被上游拒为 "sort is not smart_hot"）。
 */
data class BookComment(
    val id: String,
    val user: String,
    val avatar: String,
    val text: String,
    /** 十分制评分（"8"）；空串表示未打分。 */
    val score: String,
    val digg: Int,
    val replyCount: Int,
    val createdAt: Long,
    /** 评论者是否本书作者。 */
    val isAuthor: Boolean = false,
    /** 上游内嵌的前几条回复或楼中楼完整加载后的回复列表。 */
    val replies: List<CommentReply> = emptyList(),
    val userLiked: Boolean = false,
    val repliesLoaded: Boolean = false,
    val repliesLoading: Boolean = false,
    val repliesHasMore: Boolean = false,
    val repliesCursor: String = "",
)

data class CommentReply(
    val id: String,
    val user: String,
    val avatar: String,
    val text: String,
    val digg: Int,
    val createdAt: Long,
    val isAuthor: Boolean,
    val replyToUser: String = "",
    val replyToReplyId: String = "",
    val userLiked: Boolean = false,
)

data class CommentReplyPage(
    val items: List<CommentReply> = emptyList(),
    val hasMore: Boolean = false,
    val cursor: String = "",
    val total: Int = 0,
)

data class BookCommentPage(
    val items: List<BookComment>,
    /** 评论条数（comment_cnt）。 */
    val total: Int,
    /** 点评人数（score_cnt），与 total 不是一个口径。 */
    val raters: Int,
    /** 上游的现成文案，如「19.5万人点评」。 */
    val context: String,
    val nextOffset: Int,
    val hasMore: Boolean,
)

/**
 * 全书热评条目，来自 `books/{id}/reviews`。
 *
 * 与老接口 [BookComment]（`books/{id}/comments`）不是同一条链路：
 * 这条的上游响应把内容拆成 `common` / `stat` / `expand` 三组，
 * 用户名与头像还要再往下一层 `user_info.base_info` 里找；
 * 翻页靠 cursor 而不是 offset。
 */
data class BookHotReview(
    val id: String,
    val user: String,
    val avatar: String,
    val text: String,
    /** 十分制评分（"9.9"）；空串表示未打分。 */
    val score: String = "",
    val digg: Int = 0,
    val replyCount: Int = 0,
    val createdAt: Long = 0,
    val userLiked: Boolean = false,
)

/**
 * 全书热评的一页。
 *
 * [cursor] 是上游给的游标（一个 JSON 字符串，形如 `{"session_id":"…","offset":20}`），
 * 翻页时**原样回传**，不能自己拼 offset。
 */
data class BookHotReviewPage(
    val items: List<BookHotReview> = emptyList(),
    /** 全书书评总数（`common_list_info.total`）。 */
    val total: Int = 0,
    val hasMore: Boolean = false,
    val cursor: String = "",
)

/** 分类页条目，来自 `category` 的 down_category。id 与榜单 rank_sub_info_id 同源。 */
data class BookCategory(val id: String, val name: String, val cover: String, val bookCount: Int)

/** 分享文案，来自 `books/{id}/share`。 */
data class ShareInfo(
    val text: String,
    val copyLinkText: String,
    val subText: String,
    val shareUrl: String,
) {
    val hasContent: Boolean get() = shareUrl.isNotBlank() || copyLinkText.isNotBlank()
}

/** 有声版本（音色）：abook_id 才是音频接口要的书 id。 */
data class AudioTone(val abookId: String, val title: String, val iconUrl: String = "")

/** 真人音频章节：core 直接给出可播放的 CDN 地址。 */
data class AudioChapter(
    val chapterId: String,
    val title: String,
    val url: String,
    val backupUrl: String = "",
    val encrypted: Boolean = false,
    /** core 解密并缓存后的本地路径（/src/xxx.m4a）；为空表示未解密。 */
    val cachedUrl: String = "",
    val cacheError: String = "",
) {
    /**
     * 要不要提示「加密音频可能播不了」。
     *
     * 只有拿不到本地解密副本才提示：core 拿到 cache=1 会自己下载并 CENC 解密到
     * /src，解密成功时照样能播，这时候再挂警告就是虚假告警。
     */
    val needsDecryptWarning: Boolean get() = encrypted && cachedUrl.isEmpty()
}

/** 听书逐句时间轴单句条目 */
data class TimelineSentence(
    val startMs: Int,
    val endMs: Int,
    val text: String,
)

/** 听书章节逐句时间轴集合 */
data class ChapterTimeline(
    val chapterId: String,
    val sentences: List<TimelineSentence> = emptyList(),
) {
    /** 根据当前播放进度（毫秒）反查当前句子下标 */
    fun findCurrentIndex(positionMs: Int): Int {
        if (sentences.isEmpty()) return -1
        for (i in sentences.indices) {
            val s = sentences[i]
            if (positionMs in s.startMs until s.endMs) return i
        }
        if (positionMs >= sentences.last().startMs) return sentences.lastIndex
        return -1
    }
}

/** 短剧 / 漫剧剧集信息 */
data class DramaSeries(
    val seriesId: String,
    val title: String,
    val coverUrl: String = "",
    val intro: String = "",
    val author: String = "",
    val episodeCount: Int = 0,
    val contentType: Int = 1, // 1: 短剧, 1004: 漫剧
    val episodes: List<DramaEpisode> = emptyList(),
    val tags: List<String> = emptyList(),
) {
    val isMotionComic: Boolean get() = contentType == 1004
}

/** 单集元数据 */
data class DramaEpisode(
    val episodeId: String, // item_id or video_id
    val seriesId: String,
    val episodeIndex: Int, // 1, 2, 3...
    val title: String = "",
    val coverUrl: String = "",
    val durationSeconds: Int = 0,
)

/** 播放视频源 */
data class DramaPlaySource(
    val videoId: String,
    val videoUrl: String,
    val localFilePath: String = "",
    val durationMs: Long = 0L,
) {
    val isLocal: Boolean get() = localFilePath.isNotBlank()
}

/** 弹幕条目 */
data class DanmakuItem(
    val id: String,
    val text: String,
    val offsetMs: Long,
    val userName: String = "",
    val userAvatar: String = "",
)

