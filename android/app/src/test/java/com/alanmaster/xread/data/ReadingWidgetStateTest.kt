package com.alanmaster.xread.data

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.util.Calendar

/**
 * 桌面「继续阅读」小组件的数据源。
 *
 * 小组件平时不显示，但一旦显示错（显示成另一本书、显示成昨天读的那本、
 * 章节号错位）用户很难排查，所以这里把挑记录的规则钉死。
 */
class ReadingWidgetStateTest {

    @get:Rule val temp = TemporaryFolder()

    private fun repo(): OfflineRepo =
        OfflineRepo(temp.newFolder("offline"), temp.newFolder("export"))

    private fun progress(
        bookId: String,
        chapterId: String,
        at: Long,
        chapterIndex: Int = -1,
        title: String = "书 $bookId",
        author: String = "作者 $bookId",
        source: String = "fanqie",
    ): JSONObject = JSONObject()
        .put("chapterId", chapterId)
        .put("offset", 0)
        .put("chapterIndex", chapterIndex)
        .put("at", at)
        .put("title", title)
        .put("author", author)
        .put("source", source)

    @Test fun emptyProgressHasNoState() {
        assertNull(ReadingWidgetStateParser.parse(JSONObject()))
    }

    @Test fun picksTheMostRecentlyReadBook() {
        val all = JSONObject()
            .put("old", progress("old", "c1", at = 1_000L))
            .put("new", progress("new", "c2", at = 9_000L))
        val state = ReadingWidgetStateParser.parse(all)
        assertEquals("new", state?.bookId)
        assertEquals(9_000L, state?.lastReadAt)
    }

    @Test fun skipsHalfWrittenRecords() {
        val all = JSONObject()
            .put("noChapter", JSONObject().put("at", 9_000L))
            .put("noTime", progress("noTime", "c1", at = 0L))
        assertNull(ReadingWidgetStateParser.parse(all))
    }

    @Test fun chapterLabelUsesOneBasedIndex() {
        val all = JSONObject().put("b", progress("b", "c1", at = 1L, chapterIndex = 11))
        assertEquals("第 12 章", ReadingWidgetStateParser.parse(all)?.chapterLabel)
    }

    @Test fun chapterLabelEmptyWhenIndexUnknown() {
        val all = JSONObject().put("b", progress("b", "c1", at = 1L, chapterIndex = -1))
        assertEquals("", ReadingWidgetStateParser.parse(all)?.chapterLabel)
    }

    @Test fun missingSourceFallsBackToFanqie() {
        val entry = progress("b", "c1", at = 1L).apply { remove("source") }
        val state = ReadingWidgetStateParser.parse(JSONObject().put("b", entry))
        assertEquals("fanqie", state?.source)
    }

    @Test fun repoExposesLastReadBook() {
        val r = repo()
        r.saveProgress("b1", "c1", offset = 10, chapterIndex = 0, book = Book("fanqie", "b1", "书一", "甲"))
        val state = r.readingWidgetState()
        assertEquals("b1", state?.bookId)
        assertEquals("书一", state?.title)
        assertEquals("第 1 章", state?.chapterLabel)
    }

    @Test fun relativeLabelCoversTodayYesterdayAndOlder() {
        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, 10)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        val today = cal.timeInMillis
        assertTrue(relativeReadLabel(today, today).startsWith("今天"))

        val yesterday = today - 24 * 60 * 60 * 1000L
        assertTrue(relativeReadLabel(yesterday, today).startsWith("昨天"))

        val threeDays = today - 3 * 24 * 60 * 60 * 1000L
        assertEquals("3天前", relativeReadLabel(threeDays, today))

        // 更早的落到具体日期，不去猜多少天前。
        val old = today - 40 * 24 * 60 * 60 * 1000L
        assertTrue(relativeReadLabel(old, today).contains("月"))

        assertEquals("", relativeReadLabel(0L, today))
    }
}
