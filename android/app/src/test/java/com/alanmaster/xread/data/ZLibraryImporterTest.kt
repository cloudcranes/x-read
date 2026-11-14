package com.alanmaster.xread.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * ZLibraryImportMapper 的纯函数测试。
 *
 * 只测「把 EAPI 的书 + EPUB 解析结果映射成本地书」这一层：不碰网络、不碰
 * [OfflineRepo]（要 Context）、不碰文件系统，所以能在 JVM 单测里直接跑。
 */
class ZLibraryImporterTest {

    private fun raw(
        id: String = "6134915",
        hash: String = "c79ab3",
        title: String = "诡秘之主",
        author: String = "爱潜水的乌贼",
        description: String = "<b>蒸汽与机械的浪潮中</b><br>谁能触及非凡？",
        categories: String = "玄幻, 奇幻",
        qualityScore: String = "5.0",
        coverUrl: String = "https://example.com/cover.jpg",
    ) = ZLibraryBook(
        id = id,
        hash = hash,
        title = title,
        author = author,
        description = description,
        categories = categories,
        qualityScore = qualityScore,
        coverUrl = coverUrl,
    )

    private fun epub(
        title: String = "诡秘之主",
        author: String = "爱潜水的乌贼",
        description: String = "",
        chapters: List<EpubChapter> = listOf(
            EpubChapter("1", "第一章 绯红", listOf("段落一", "段落二")),
            EpubChapter("2", "第二章 杂技", listOf("段落三")),
        ),
    ) = EpubBook(title = title, author = author, description = description, chapters = chapters)

    @Test
    fun localIdKeepsPureDigits() {
        assertEquals("6134915", ZLibraryImportMapper.localId(raw(id = "6134915")))
    }

    @Test
    fun localIdStripsNonDigits() {
        assertEquals("123456", ZLibraryImportMapper.localId(raw(id = "abc123def456")))
    }

    @Test
    fun localIdIsStableWhenIdHasNoDigits() {
        val first = ZLibraryImportMapper.localId(raw(id = "", hash = "c79ab3"))
        val second = ZLibraryImportMapper.localId(raw(id = "", hash = "c79ab3"))
        assertEquals(first, second)
        assertTrue(first.matches(Regex("[0-9]{1,24}")))
    }

    @Test
    fun localIdAlwaysMatchesLocalLibraryRegex() {
        listOf("", "0", "6134915", "abc", "１２３").forEach { id ->
            val value = ZLibraryImportMapper.localId(raw(id = id))
            assertTrue("localId=$value for id=$id", value.matches(Regex("[0-9]{1,24}")))
        }
    }

    @Test
    fun toBookMapsCoreFields() {
        val book = ZLibraryImportMapper.toBook(raw(), epub())
        assertEquals("zlibrary", book.source)
        assertEquals("6134915", book.bookId)
        assertEquals("诡秘之主", book.title)
        assertEquals("爱潜水的乌贼", book.author)
        assertEquals(2, book.chapterCount)
        assertEquals("第二章 杂技", book.latestChapterTitle)
        assertEquals("0", book.creationStatus)
        assertEquals("玄幻", book.category)
        assertEquals("5.0", book.score)
    }

    @Test
    fun toBookPrefersEpubMetadataOverRaw() {
        val book = ZLibraryImportMapper.toBook(
            raw(title = "上游书名", author = "上游作者"),
            epub(title = "EPUB 书名", author = "EPUB 作者"),
        )
        assertEquals("EPUB 书名", book.title)
        assertEquals("EPUB 作者", book.author)
    }

    @Test
    fun toBookFallsBackToRawWhenEpubTitleBlank() {
        val book = ZLibraryImportMapper.toBook(raw(title = "上游书名", author = "上游作者"), epub(title = "", author = ""))
        assertEquals("上游书名", book.title)
        assertEquals("上游作者", book.author)
    }

    @Test
    fun toBookUsesCoverUrlWhenGiven() {
        val book = ZLibraryImportMapper.toBook(raw(), epub(), coverUrl = "file:///data/cover.jpg")
        assertEquals("file:///data/cover.jpg", book.coverUrl)
    }

    @Test
    fun toBookFallsBackToUpstreamCover() {
        val book = ZLibraryImportMapper.toBook(raw(coverUrl = "https://example.com/a.jpg"), epub())
        assertEquals("https://example.com/a.jpg", book.coverUrl)
    }

    @Test
    fun toBookUsesPlainDescriptionWhenEpubHasNone() {
        val book = ZLibraryImportMapper.toBook(raw(), epub())
        assertTrue(book.description.contains("蒸汽与机械的浪潮中"))
        assertTrue(!book.description.contains("<b>"))
    }

    @Test
    fun toChaptersKeepsIdsAndTitles() {
        val chapters = ZLibraryImportMapper.toChapters(epub())
        assertEquals(listOf("1", "2"), chapters.map { it.id })
        assertEquals(listOf("第一章 绯红", "第二章 杂技"), chapters.map { it.title })
    }

    @Test
    fun chapterTextJoinsParagraphsWithSingleNewlines() {
        val text = ZLibraryImportMapper.chapterText(EpubChapter("1", "第一章", listOf("段落一", "段落二", "段落三")))
        assertEquals("段落一\n段落二\n段落三", text)
    }

    @Test
    fun chapterTextRoundTripsThroughParagraphsSplit() {
        val paragraphs = listOf("第一段，有逗号。", "第二段 有空格", "第三段")
        val text = ZLibraryImportMapper.chapterText(EpubChapter("1", "第一章", paragraphs))
        assertEquals(paragraphs, Paragraphs.split(text))
    }

    @Test
    fun plainDescriptionStripsTagsAndDecodesEntities() {
        val text = ZLibraryImportMapper.plainDescription("<b>Tiny</b> &amp; <i>Changes</i><br>Remarkable Results")
        assertEquals("Tiny & Changes\nRemarkable Results", text)
    }

    @Test
    fun plainDescriptionDropsScriptAndStyle() {
        val text = ZLibraryImportMapper.plainDescription("<style>p{color:red}</style><script>alert(1)</script><p>正文</p>")
        assertEquals("正文", text)
    }

    @Test
    fun plainDescriptionDecodesNumericEntities() {
        assertEquals("A B", ZLibraryImportMapper.plainDescription("A&#32;B"))
        assertEquals("A B", ZLibraryImportMapper.plainDescription("A&#x20;B"))
    }

    @Test
    fun plainDescriptionReturnsEmptyForBlank() {
        assertEquals("", ZLibraryImportMapper.plainDescription(""))
        assertEquals("", ZLibraryImportMapper.plainDescription("   "))
    }

    @Test
    fun plainDescriptionKeepsParagraphBreaks() {
        val text = ZLibraryImportMapper.plainDescription("<p>第一段</p><p>第二段</p>")
        assertEquals("第一段\n第二段", text)
        assertEquals(listOf("第一段", "第二段"), Paragraphs.split(text))
    }
}
