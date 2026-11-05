package com.alanmaster.xread.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * 追剧记录落盘：书架「短剧」页在进程重启后要还能看到看过的剧。
 *
 * 之前这份列表只活在 LibraryViewModel 的内存里，杀进程即空，用户刚看的剧
 * 找不回来，只能去发现页重新翻。
 */
class DramaRecentTest {
    @get:Rule val temp = TemporaryFolder()

    private fun repo(): OfflineRepo =
        OfflineRepo(temp.newFolder("offline"), temp.newFolder("export"))

    private fun series(id: String, title: String = "剧 $id") = DramaSeries(
        seriesId = id,
        title = title,
        coverUrl = "https://example.com/$id.jpg",
        intro = "简介 $id",
        author = "作者 $id",
        episodeCount = 42,
        contentType = 1,
    )

    @Test fun missingFileStartsEmpty() {
        assertTrue(repo().dramaRecent().isEmpty())
    }

    @Test fun pushKeepsNewestFirst() {
        val r = repo()
        r.pushDramaRecent(series("a"))
        val list = r.pushDramaRecent(series("b"))
        assertEquals(listOf("b", "a"), list.map { it.seriesId })
        // 重新读一次，确认真的落盘了而不是只在返回值里
        assertEquals(listOf("b", "a"), r.dramaRecent().map { it.seriesId })
    }

    @Test fun pushingSameSeriesMovesItToFrontWithoutDuplicating() {
        val r = repo()
        r.pushDramaRecent(series("a"))
        r.pushDramaRecent(series("b"))
        val list = r.pushDramaRecent(series("a", title = "改名后的 A"))
        assertEquals(listOf("a", "b"), list.map { it.seriesId })
        assertEquals("改名后的 A", list.first().title)
    }

    @Test fun keepsSeriesFieldsNeededByShelfCard() {
        val r = repo()
        r.pushDramaRecent(series("a", title = "龙王赘婿").copy(contentType = 1004))
        val back = r.dramaRecent().single()
        assertEquals("龙王赘婿", back.title)
        assertEquals("https://example.com/a.jpg", back.coverUrl)
        assertEquals("简介 a", back.intro)
        assertEquals("作者 a", back.author)
        assertEquals(42, back.episodeCount)
        assertTrue(back.isMotionComic)
    }

    @Test fun emptySeriesIdIsIgnored() {
        val r = repo()
        r.pushDramaRecent(series("a"))
        r.pushDramaRecent(DramaSeries(seriesId = "", title = "空"))
        assertEquals(listOf("a"), r.dramaRecent().map { it.seriesId })
    }

    @Test fun corruptFileFallsBackToEmptyInsteadOfThrowing() {
        val root = temp.newFolder("offline-corrupt")
        File(root, "drama-recent.json").writeText("{ this is not json")
        val r = OfflineRepo(root, temp.newFolder("export-corrupt"))
        assertTrue(r.dramaRecent().isEmpty())
    }
}
