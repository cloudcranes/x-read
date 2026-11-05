package com.alanmaster.xread.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * 目录缓存标记：圆点是「磁盘上有什么」的投影，不能和实际落盘产生歧义。
 *
 * 三个容易错的地方——缓存没失效（刚下完仍显示空心）、键名映射和文件名不一致
 * （939 行全是空心）、删书后残留旧集合（换书还亮着）。
 */
class TocCacheTest {
    @get:Rule val temp = TemporaryFolder()

    private fun repo(): OfflineRepo =
        OfflineRepo(temp.newFolder("offline"), temp.newFolder("export"))

    @Test fun downloadedChaptersListsSavedChapterKeys() {
        val r = repo()
        r.putChapter("b1", "1", "第1章", "正文一")
        r.putChapter("b1", "2", "第2章", "正文二")
        assertEquals(setOf("1", "2"), r.downloadedChapters("b1"))
    }

    @Test fun downloadedChaptersIsEmptyBeforeAnyDownload() {
        assertTrue(repo().downloadedChapters("b1").isEmpty())
    }

    @Test fun cacheRefreshesAfterPutChapter() {
        val r = repo()
        // 先读一次把空集合放进缓存，再写章节——缓存必须失效
        assertTrue(r.downloadedChapters("b1").isEmpty())
        r.putChapter("b1", "7", "第7章", "正文")
        assertTrue("7" in r.downloadedChapters("b1"))
    }

    @Test fun removeChapterDropsOnlyThatKey() {
        val r = repo()
        r.putChapter("b1", "1", "第1章", "正文一")
        r.putChapter("b1", "2", "第2章", "正文二")
        assertTrue(r.removeChapter("b1", "1"))
        assertEquals(setOf("2"), r.downloadedChapters("b1"))
    }

    @Test fun removeChapterOnMissingFileIsHarmless() {
        val r = repo()
        assertTrue(r.removeChapter("b1", "404"))
        assertTrue(r.downloadedChapters("b1").isEmpty())
    }

    @Test fun removingWholeBookClearsCache() {
        val r = repo()
        r.putChapter("b1", "1", "第1章", "正文")
        assertEquals(1, r.downloadedChapters("b1").size)
        r.removeBook("b1")
        assertTrue(r.downloadedChapters("b1").isEmpty())
    }

    @Test fun chapterKeyMatchesStoredFileName() {
        val r = repo()
        val id = "12:34/56"
        r.putChapter("b1", id, "第1章", "正文")
        val keys = r.downloadedChapters("b1")
        assertEquals(setOf(OfflineRepo.chapterKey(id)), keys)
        assertFalse("键里不该残留路径分隔符", OfflineRepo.chapterKey(id).contains('/'))
    }

    @Test fun chaptersOfDifferentBooksDoNotLeak() {
        val r = repo()
        r.putChapter("b1", "1", "第1章", "正文一")
        assertTrue(r.downloadedChapters("b2").isEmpty())
    }
}
