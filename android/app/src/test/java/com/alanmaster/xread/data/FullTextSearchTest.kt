package com.alanmaster.xread.data

import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class FullTextSearchTest {

    @get:Rule val temp = TemporaryFolder()

    private fun repo(): OfflineRepo = OfflineRepo(File(temp.newFolder(), "offline"), temp.newFolder())

    private fun seed(r: OfflineRepo) {
        val book = Book(bookId = "1", title = "长夜")
        r.putBook(book, listOf(Chapter("c1", "第1章"), Chapter("c2", "第2章"), Chapter("c3", "第3章")))
        r.putChapter("1", "c1", "第1章", "第一段正文\n这里埋着一个关键词\n第三段正文")
        r.putChapter("1", "c2", "第2章", "第二章也提到关键词")
        // c3 只存在于目录、没有正文：不该被算进扫描范围
    }

    private fun hits(r: OfflineRepo, q: String): List<SearchProgress> =
        runBlocking { FullTextSearch.search(r, q).toList() }

    @Test fun findsParagraphsAcrossDownloadedChapters() {
        val r = repo()
        seed(r)
        val last = hits(r, "关键词").last()
        assertTrue(last.done)
        assertEquals(2, last.hits.size)
        assertEquals(2, last.total)
        assertEquals(2, last.scanned)
        assertEquals(listOf("c1", "c2"), last.hits.map { it.chapterId })
        assertEquals(1, last.hits.first().paragraphIndex)
        assertEquals("长夜", last.hits.first().bookTitle)
    }

    @Test fun ignoresChaptersThatAreOnlyInTheCatalog() {
        val r = repo()
        seed(r)
        val last = hits(r, "正文").last()
        // total 是「要扫多少章」，与关键词无关：目录里 3 章、真正落盘 2 章
        assertEquals(2, last.total)
        assertTrue(last.hits.all { it.chapterId == "c1" })
        assertTrue(last.hits.none { it.chapterId == "c3" })
    }

    @Test fun isCaseInsensitive() {
        val r = repo()
        r.putBook(Book(bookId = "1", title = "书"), listOf(Chapter("c1", "第1章")))
        r.putChapter("1", "c1", "第1章", "Hello World")
        val last = hits(r, "world").last()
        assertEquals(1, last.hits.size)
        assertTrue(last.hits.first().snippet.contains("World"))
    }

    @Test fun blankQueryProducesNothing() {
        val r = repo()
        seed(r)
        val all = hits(r, "   ")
        assertEquals(1, all.size)
        assertTrue(all.first().done)
        assertTrue(all.first().hits.isEmpty())
    }

    @Test fun stopsAtLimit() {
        val r = repo()
        r.putBook(Book(bookId = "1", title = "书"), listOf(Chapter("c1", "第1章")))
        val many = (1..250).joinToString("\n") { "第 $it 段都有命中词" }
        r.putChapter("1", "c1", "第1章", many)
        val last = hits(r, "命中词").last()
        assertEquals(FullTextSearch.LIMIT, last.hits.size)
        assertTrue(last.done)
    }

    @Test fun snippetKeepsContextAndEllipsis() {
        val text = "甲".repeat(60) + "关键词" + "乙".repeat(60)
        val snippet = FullTextSearch.snippetOf(text, "关键词")
        assertTrue(snippet.startsWith("…"))
        assertTrue(snippet.endsWith("…"))
        assertTrue(snippet.contains("关键词"))
        assertEquals(2 + 20 + 3 + 20, snippet.length)
    }

    @Test fun totalIsZeroWhenNothingDownloaded() {
        val r = repo()
        r.putBook(Book(bookId = "1", title = "书"), listOf(Chapter("c1", "第1章")))
        val last = hits(r, "任何词").last()
        assertEquals(0, last.total)
        assertEquals(0, last.scanned)
        assertTrue(last.done)
        assertFalse(last.hits.any())
    }
}
