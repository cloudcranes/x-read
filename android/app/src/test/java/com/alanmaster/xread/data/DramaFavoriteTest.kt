package com.alanmaster.xread.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * 短剧收藏落盘：星标点下去之后必须找得回来。
 *
 * 旧实现只把布尔值写进 SharedPreferences，全仓没有任何地方读它，
 * 用户收藏完在界面上再也看不到这部剧——收藏等于黑洞。
 */
class DramaFavoriteTest {
    @get:Rule val temp = TemporaryFolder()

    private fun repo(): OfflineRepo =
        OfflineRepo(temp.newFolder("offline"), temp.newFolder("export"))

    private fun series(id: String, title: String = "剧 $id", contentType: Int = 1) = DramaSeries(
        seriesId = id,
        title = title,
        coverUrl = "https://example.com/$id.jpg",
        intro = "简介 $id",
        author = "作者 $id",
        episodeCount = 42,
        contentType = contentType,
    )

    @Test fun missingFileStartsEmpty() {
        assertTrue(repo().dramaFavorites().isEmpty())
    }

    @Test fun favoriteThenCancelRoundTrips() {
        val r = repo()
        r.setDramaFavorite(series("a"), true)
        assertTrue(r.isDramaFavorite("a"))
        assertFalse(r.isDramaFavorite("b"))

        r.setDramaFavorite(series("a"), false)
        assertFalse(r.isDramaFavorite("a"))
        assertTrue(r.dramaFavorites().isEmpty())
    }

    @Test fun newestFavoriteComesFirstWithoutDuplicates() {
        val r = repo()
        r.setDramaFavorite(series("a"), true)
        val list = r.setDramaFavorite(series("b"), true)
        assertEquals(listOf("b", "a"), list.map { it.seriesId })
        // 再收藏一次同一部：不产生重复项，也不把它挤到后面
        val again = r.setDramaFavorite(series("a", title = "改名后"), true)
        assertEquals(listOf("a", "b"), again.map { it.seriesId })
        assertEquals("改名后", again.first().title)
    }

    @Test fun keepsFieldsNeededByShelfCard() {
        val r = repo()
        r.setDramaFavorite(series("m", contentType = 1004), true)
        val saved = r.dramaFavorites().single()
        assertEquals("https://example.com/m.jpg", saved.coverUrl)
        assertEquals("作者 m", saved.author)
        assertEquals(42, saved.episodeCount)
        assertTrue(saved.isMotionComic)
    }

    @Test fun emptySeriesIdIsIgnored() {
        val r = repo()
        r.setDramaFavorite(series(""), true)
        assertTrue(r.dramaFavorites().isEmpty())
    }

    @Test fun backupCarriesDramaRecords() {
        val files = temp.newFolder("files")
        val export = temp.newFolder("export2")
        val origin = OfflineRepo(files.resolve("offline"), export)
        origin.setDramaFavorite(series("fav"), true)
        origin.pushDramaRecent(series("recent"))
        val backup = origin.exportBackup()
        assertTrue("备份文件没生成", backup != null && backup.isFile)
        val text = backup!!.readText()
        assertTrue("备份未带短剧收藏", text.contains("dramaFavorites"))
        assertTrue("备份未带追剧记录", text.contains("dramaRecent"))
        assertTrue("备份未带收藏的剧", text.contains("fav"))
        assertTrue("备份未带看过的剧", text.contains("recent"))

        // 换一份干净的数据目录恢复，收藏与追剧记录都要回来
        val restoredFiles = temp.newFolder("files2")
        val restored = OfflineRepo(restoredFiles.resolve("offline"), export)
        assertTrue(restored.importBackup().startsWith("导入完成"))
        assertTrue("收藏没恢复", restored.isDramaFavorite("fav"))
        assertEquals(listOf("recent"), restored.dramaRecent().map { it.seriesId })
    }

    @Test fun corruptFileFallsBackToEmptyInsteadOfThrowing() {
        val r = repo()
        r.setDramaFavorite(series("a"), true)
        val file = temp.root.resolve("offline").resolve("drama-favorites.json")
        file.writeText("{ not json")
        assertTrue(r.dramaFavorites().isEmpty())
    }
}
