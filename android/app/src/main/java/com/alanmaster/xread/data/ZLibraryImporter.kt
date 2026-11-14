package com.alanmaster.xread.data

/**
 * Z-Library 电子书导入：把 EAPI 搜到的书 + 下载到的 EPUB 转成本地书。
 *
 * 为什么不把它做成在线书源：z-library 只给整本文件下载，没有按章拉取的接口；
 * 而 x-read 的阅读器已经有一套完整的「目录 + 逐章纯文本」本地书体系
 * （[OfflineRepo.putBook] / [OfflineRepo.putChapter]），把 EPUB 拆成章节写进去，
 * 现有阅读器、进度、书签、段评位置全都能直接用，不用另造一套阅读链路。
 */
object ZLibraryImportMapper {
    /** 本地书架上的书源标识。番茄是 fanqie，本地导入的电子书统一用它。 */
    const val SOURCE = "zlibrary"

    /**
     * 本地 bookId：必须是纯数字（[LocalLibrary.put] 与 [Api.checkId] 都只认 `[0-9]{1,24}`）。
     *
     * z-library 的 id 本身就是数字串，直接用；上游偶尔给空或非数字时，用 hash 兜一个
     * 稳定的正数，保证同一本书重复导入落在同一条目录上（幂等覆盖，不会越导越多）。
     */
    fun localId(book: ZLibraryBook): String {
        val digits = book.id.filter { it in '0'..'9' }
        if (digits.isNotEmpty()) return digits.take(24)
        val seed = book.hash.ifBlank { book.title }
        return (seed.hashCode().toLong() and 0x7fffffffL).toString()
    }

    fun toBook(raw: ZLibraryBook, epub: EpubBook, coverUrl: String = ""): Book = Book(
        source = SOURCE,
        bookId = localId(raw),
        title = epub.title.ifBlank { raw.title },
        author = epub.author.ifBlank { raw.author },
        description = epub.description.ifBlank { plainDescription(raw.description) },
        coverUrl = coverUrl.ifBlank { raw.coverUrl },
        category = raw.categories.substringBefore(',').trim(),
        score = raw.qualityScore,
        chapterCount = epub.chapters.size,
        latestChapterTitle = epub.chapters.lastOrNull()?.title.orEmpty(),
        // 导入的是一本完整电子书，不是连载中的网文，按完本处理。
        creationStatus = "0",
    )

    fun toChapters(epub: EpubBook): List<Chapter> =
        epub.chapters.map { Chapter(id = it.id, title = it.title) }

    /**
     * 一章正文落盘格式：**一行一段**。
     *
     * [Paragraphs.split] 读的时候按 Unicode 行分隔符切段并丢空行，所以这里是它的逆操作。
     * 不用空行分段：正文里本来就可能出现空行，用 `\n` 单行分隔才不会读回多余的空白段。
     */
    fun chapterText(chapter: EpubChapter): String = chapter.paragraphs.joinToString("\n")

    /** 上游 description 是 HTML（`<b>` / `<br>` / `<p>` + 实体），详情页要纯文本。 */
    fun plainDescription(html: String): String {
        if (html.isBlank()) return ""
        val stripped = html
            .replace(Regex("(?is)<(script|style)[^>]*>.*?</\\1>"), " ")
            .replace(Regex("(?i)<br\\s*/?>"), "\n")
            .replace(Regex("(?i)</(p|div|h[1-6]|li|blockquote|tr)>"), "\n")
            .replace(Regex("<[^>]*>"), " ")
            .replace(Regex("[\\t\\u00A0 ]+"), " ")
        return Paragraphs.split(decodeEntities(stripped)).joinToString("\n")
    }

    private fun decodeEntities(text: String): String {
        var out = text
            .replace("&nbsp;", " ")
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace("&apos;", "'")
        out = Regex("&#(\\d{1,7});").replace(out) { m ->
            m.groupValues[1].toIntOrNull()?.let { code -> runCatching { String(Character.toChars(code)) }.getOrDefault(m.value) }
                ?: m.value
        }
        out = Regex("&#[xX]([0-9a-fA-F]{1,6});").replace(out) { m ->
            m.groupValues[1].toIntOrNull(16)?.let { code -> runCatching { String(Character.toChars(code)) }.getOrDefault(m.value) }
                ?: m.value
        }
        return out
    }
}

/** 真正执行导入：解析 EPUB → 写离线目录 → 进书架。所有方法都是阻塞 IO，调用方放 IO 调度器。 */
class ZLibraryImporter(private val offline: OfflineRepo, private val api: Api) {

    /**
     * @param bytes 已下载好的 EPUB 原始字节（下载与登录由 [ZLibraryClient] 负责）。
     * @return 落库后的书，可直接交给书架/详情页。
     */
    suspend fun import(raw: ZLibraryBook, bytes: ByteArray): Book {
        val epub = EpubParser.parse(bytes)
        val bookId = ZLibraryImportMapper.localId(raw)
        val coverUrl = epub.coverBytes
            ?.let { offline.putCover(bookId, it, epub.coverExtension) }
            .orEmpty()
        val book = ZLibraryImportMapper.toBook(raw, epub, coverUrl)

        check(offline.putBook(book, ZLibraryImportMapper.toChapters(epub))) { "电子书写入失败，请检查存储空间" }
        epub.chapters.forEach { chapter ->
            offline.putChapter(
                bookId = book.bookId,
                chapterId = chapter.id,
                title = chapter.title,
                content = ZLibraryImportMapper.chapterText(chapter),
            )
        }
        api.putLocalBook(book)
        return book
    }
}
