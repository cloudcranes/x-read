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

    @Test fun pendingUpdatesIgnoreAlreadySeenAndNotified() {
        val r = repo()
        r.setDramaSubscription(series("a", episodes = 10), true)
        // 订阅那一刻没有任何「新集」。
        assertTrue(r.pendingDramaUpdates().isEmpty())

        r.refreshDramaSubscriptionEpisodes(listOf(series("a", episodes = 14)))
        val pending = r.pendingDramaUpdates()
        assertEquals(1, pending.size)
        assertEquals(4, pending.single().newEpisodes)

        // 发过通知后水位抬到 14，再问一次就不该重复提醒。
        val marked = r.markDramaUpdatesNotified(listOf("a"))
        assertEquals(14, marked.single().notifiedEpisodeCount)
        assertTrue(r.pendingDramaUpdates().isEmpty())

        // 再涨 3 集，只提醒这 3 集。
        r.refreshDramaSubscriptionEpisodes(listOf(series("a", episodes = 17)))
        assertEquals(3, r.pendingDramaUpdates().single().newEpisodes)
    }

    @Test fun seenSeriesDoesNotProduceNotice() {
        val r = repo()
        r.setDramaSubscription(series("a", episodes = 10), true)
        r.refreshDramaSubscriptionEpisodes(listOf(series("a", episodes = 20)))
        assertEquals(10, r.pendingDramaUpdates().single().newEpisodes)

        // 打开详情页 = 用户已经看到这些，不该再推。
        r.markDramaSubscriptionSeen(series("a", episodes = 20))
        assertTrue(r.pendingDramaUpdates().isEmpty())
    }

    @Test fun notifiedWatermarkSurvivesRoundTrip() {
        val dir = temp.newFolder("offline-wm")
        val export = temp.newFolder("export-wm")
        val first = OfflineRepo(dir, export)
        first.setDramaSubscription(series("a", episodes = 10), true)
        first.refreshDramaSubscriptionEpisodes(listOf(series("a", episodes = 12)))
        first.markDramaUpdatesNotified(listOf("a"))

        // 换一个实例重新读盘：水位必须还在，否则每次冷启动都会重复弹同一条通知。
        val second = OfflineRepo(dir, export)
        assertEquals(12, second.dramaSubscriptions().single().notifiedEpisodeCount)
        assertTrue(second.pendingDramaUpdates().isEmpty())
    }

    @Test fun seasonSuffixIsRemovedForGrouping() {
        assertEquals("问鼎仙途踏碎仙凡", DramaSeriesGrouping.displayTitle("问鼎仙途踏碎仙凡第三季"))
        assertEquals("问鼎仙途踏碎仙凡", DramaSeriesGrouping.displayTitle("问鼎仙途踏碎仙凡"))
        assertEquals(
            DramaSeriesGrouping.groupKey("问鼎仙途踏碎仙凡"),
            DramaSeriesGrouping.groupKey("问鼎仙途踏碎仙凡第二季"),
        )
    }

    @Test fun subscribingOneSeasonAddsWholeGroupAndCancelRemovesIt() {
        val r = repo()
        val second = series("s2", "问鼎仙途踏碎仙凡第二季", 900)
        val third = series("s3", "问鼎仙途踏碎仙凡第三季", 1000)
        val first = series("s1", "问鼎仙途踏碎仙凡", 800)
        val stored = r.setDramaSubscriptionGroup(third, listOf(first, second), true)
        assertEquals(3, stored.size)
        assertEquals(1, DramaSeriesGrouping.group(stored).size)
        assertTrue(r.isDramaSubscribed(second))

        val removed = r.setDramaSubscriptionGroup(second, listOf(first, third), false)
        assertTrue(removed.isEmpty())
        assertFalse(r.isDramaSubscribed(third))
    }

    @Test fun refreshAddsNewSeasonToExistingGroup() {
        val r = repo()
        val first = series("s1", "问鼎仙途踏碎仙凡", 800)
        r.setDramaSubscriptionGroup(first, emptyList(), true)
        val second = series("s2", "问鼎仙途踏碎仙凡第二季", 900)
        val refreshed = r.refreshDramaSubscriptionEpisodes(listOf(first, second))
        assertEquals(2, refreshed.size)
        assertEquals(1, DramaSeriesGrouping.group(refreshed).size)
        assertEquals(0, DramaSeriesGrouping.group(refreshed).single().newEpisodeCount)
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
