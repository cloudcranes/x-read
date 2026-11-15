package com.alanmaster.xread.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** 短剧订阅：订阅关系落在本机，剧集更新数按 feed 快照刷新。 */
class DramaSubscriptionTest {
    @get:Rule val temp = TemporaryFolder()

    private fun repo(): OfflineRepo =
        OfflineRepo(temp.newFolder("offline"), temp.newFolder("export"))

    private fun series(id: String, title: String = "剧 $id", episodes: Int = 42) = DramaSeries(
        seriesId = id,
        title = title,
        coverUrl = "https://example.com/$id.jpg",
        intro = "简介 $id",
        author = "作者 $id",
        episodeCount = episodes,
        contentType = 1,
    )

    @Test fun missingFileStartsEmpty() {
        assertTrue(repo().dramaSubscriptions().isEmpty())
    }

    @Test fun subscribeThenCancelRoundTrips() {
        val r = repo()
        r.setDramaSubscription(series("a"), true)
        assertTrue(r.isDramaSubscribed("a"))
        assertFalse(r.isDramaSubscribed("b"))
        assertEquals("剧 a", r.dramaSubscriptions().single().series.title)

        r.setDramaSubscription(series("a"), false)
        assertFalse(r.isDramaSubscribed("a"))
        assertTrue(r.dramaSubscriptions().isEmpty())
    }

    @Test fun refreshTracksNewEpisodesAndSeenClearsBadge() {
        val r = repo()
        r.setDramaSubscription(series("a", episodes = 10), true)
        val refreshed = r.refreshDramaSubscriptionEpisodes(listOf(series("a", episodes = 14)))
        assertEquals(14, refreshed.single().latestEpisodeCount)
        assertEquals(4, refreshed.single().newEpisodeCount)

        val seen = r.markDramaSubscriptionSeen(series("a", episodes = 14))
        assertEquals(0, seen.single().newEpisodeCount)
        assertEquals(14, seen.single().lastSeenEpisodeCount)
    }

    @Test fun backupCarriesSubscriptions() {
        val files = temp.newFolder("files")
        val export = temp.newFolder("export2")
        val origin = OfflineRepo(files.resolve("offline"), export)
        origin.setDramaSubscription(series("sub"), true)
        val backup = origin.exportBackup()
        assertTrue("备份文件没生成", backup != null && backup.isFile)
        assertTrue("备份未带短剧订阅", backup!!.readText().contains("dramaSubscriptions"))

        val restored = OfflineRepo(temp.newFolder("files2").resolve("offline"), export)
        assertTrue(restored.importBackup().startsWith("导入完成"))
        assertTrue("订阅没恢复", restored.isDramaSubscribed("sub"))
    }
}
