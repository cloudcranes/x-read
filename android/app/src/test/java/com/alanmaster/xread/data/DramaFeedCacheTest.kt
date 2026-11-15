package com.alanmaster.xread.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/** 短剧媒体墙缓存：冷启动先有内容，网络刷新在后台补。 */
class DramaFeedCacheTest {
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
        tags = listOf("热播", "逆袭"),
    )

    @Test fun missingFileStartsEmpty() {
        assertTrue(repo().dramaFeedCache().isEmpty())
    }

    @Test fun saveAndLoadKeepsFeedFields() {
        val r = repo()
        r.saveDramaFeed(listOf(series("a", "龙王赘婿").copy(contentType = 1004)))
        val back = r.dramaFeedCache().single()
        assertEquals("龙王赘婿", back.title)
        assertEquals("https://example.com/a.jpg", back.coverUrl)
        assertEquals("简介 a", back.intro)
        assertEquals("作者 a", back.author)
        assertEquals(42, back.episodeCount)
        assertTrue(back.isMotionComic)
        assertEquals(listOf("热播", "逆袭"), back.tags)
    }

    @Test fun saveDeduplicatesBySeriesId() {
        val r = repo()
        r.saveDramaFeed(listOf(series("a", "旧标题"), series("a", "新标题"), series("b")))
        assertEquals(listOf("a", "b"), r.dramaFeedCache().map { it.seriesId })
        assertEquals("旧标题", r.dramaFeedCache().first().title)
    }

    @Test fun corruptFileFallsBackToEmptyInsteadOfThrowing() {
        val root = temp.newFolder("offline-corrupt")
        File(root, "drama-feed.json").writeText("{ this is not json")
        val r = OfflineRepo(root, temp.newFolder("export-corrupt"))
        assertTrue(r.dramaFeedCache().isEmpty())
    }
}
