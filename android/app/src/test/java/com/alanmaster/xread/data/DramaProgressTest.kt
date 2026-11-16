package com.alanmaster.xread.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * 短剧断点续播：同一部剧只保留一条进度，按最近观看排序。
 */
class DramaProgressTest {
    @get:Rule val temp = TemporaryFolder()

    private fun repo(): OfflineRepo =
        OfflineRepo(temp.newFolder("offline"), temp.newFolder("export"))

    private fun progress(
        seriesId: String,
        episodeIndex: Int = 1,
        positionMs: Long = 30_000L,
        updatedAt: Long = System.currentTimeMillis(),
    ) = DramaProgress(
        seriesId = seriesId,
        seriesTitle = "剧 $seriesId",
        coverUrl = "https://example.com/$seriesId.jpg",
        episodeId = "$seriesId-$episodeIndex",
        episodeIndex = episodeIndex,
        positionMs = positionMs,
        durationMs = 100_000L,
        updatedAt = updatedAt,
    )

    @Test fun missingFileStartsEmpty() {
        assertTrue(repo().dramaProgress().isEmpty())
    }

    @Test fun sameSeriesUpsertsToNewestPosition() {
        val r = repo()
        r.saveDramaProgress(progress("a", positionMs = 10_000L))
        val list = r.saveDramaProgress(progress("a", episodeIndex = 2, positionMs = 40_000L))
        assertEquals(1, list.size)
        assertEquals(2, list.single().episodeIndex)
        assertEquals(40_000L, list.single().positionMs)
        assertEquals(2, r.dramaProgress().single().episodeIndex)
    }

    @Test fun newestSeriesComesFirst() {
        val r = repo()
        val now = 1_700_000_000_000L
        r.saveDramaProgress(progress("a", updatedAt = now))
        val list = r.saveDramaProgress(progress("b", updatedAt = now + 1_000L))
        assertEquals(listOf("b", "a"), list.map { it.seriesId })
    }

    @Test fun progressKeepsNeededRailFields() {
        val r = repo()
        r.saveDramaProgress(progress("a", episodeIndex = 7, positionMs = 50_000L))
        val back = r.dramaProgress().single()
        assertEquals("剧 a", back.seriesTitle)
        assertEquals("https://example.com/a.jpg", back.coverUrl)
        assertEquals("a-7", back.episodeId)
        assertEquals(0.5f, back.fraction, 0.001f)
    }

    @Test fun finishedAtNinetyFivePercent() {
        assertTrue(progress("a", positionMs = 95_000L).isFinished)
    }

    @Test fun corruptFileFallsBackToEmpty() {
        val root = temp.newFolder("offline-corrupt")
        java.io.File(root, "drama-progress.json").writeText("{ not json")
        val r = OfflineRepo(root, temp.newFolder("export-corrupt"))
        assertTrue(r.dramaProgress().isEmpty())
    }
}
