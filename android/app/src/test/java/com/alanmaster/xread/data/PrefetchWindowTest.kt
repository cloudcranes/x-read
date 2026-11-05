package com.alanmaster.xread.data

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * v3.6「随身」的窗口逻辑：预取章有标记、手动章不受清理影响、上限不可越。
 */
class PrefetchWindowTest {

    @get:Rule
    val temp = TemporaryFolder()

    private fun repo(): OfflineRepo = OfflineRepo(File(temp.newFolder(), "offline"), temp.newFolder())

    private fun book(id: String = "1") = Book(source = "tomato", bookId = id, title = "书$id")

    @Test
    fun prefetchDefaultsToThreeChapters() {
        assertEquals(3, DownloadPolicy().prefetch)
        assertEquals(3, DownloadPolicy(prefetchChapters = 7).prefetch)
        assertEquals(0, DownloadPolicy(prefetchChapters = 0).prefetch)
        assertEquals(10, DownloadPolicy(prefetchChapters = 10).prefetch)
    }

    @Test
    fun prefetchSurvivesPolicyRoundTrip() {
        val root = File(temp.newFolder(), "offline")
        OfflineRepo(root, temp.newFolder()).saveDownloadPolicy(
            DownloadPolicy(wifiOnly = true, prefetchChapters = 5),
        )
        // 换一个实例重读盘：设置页改完必须重启后仍然生效
        val reloaded = OfflineRepo(root, temp.newFolder()).downloadPolicy()
        assertEquals(5, reloaded.prefetch)
        assertTrue(reloaded.wifiOnly)
    }

    @Test
    fun autoChaptersTrackOnlyPrefetchedOnes() {
        val r = repo()
        val b = book()
        r.putBook(b, listOf(Chapter("c1", "一"), Chapter("c2", "二"), Chapter("c3", "三")))
        r.putChapter(b.bookId, "c1", "一", "手动下载的正文")
        r.putChapter(b.bookId, "c2", "二", "预取来的正文", auto = true)
        assertEquals(setOf("c2"), r.autoChapters(b.bookId))
        assertEquals(setOf("c1", "c2"), r.downloadedChapters(b.bookId))
    }

    @Test
    fun trimKeepsWindowAndLeavesManualChapters() {
        val r = repo()
        val b = book()
        val chapters = (1..10).map { Chapter("c$it", "第 $it 章") }
        r.putBook(b, chapters)
        chapters.forEach { r.putChapter(b.bookId, it.id, it.title, "正文 ${it.id}", auto = true) }
        r.putChapter(b.bookId, "c9", "第 9 章", "读者自己下的")

        // 窗口 = 第 3~6 章（当前第 4 章，前 2 后 2）
        // c9 被读者重下过一次，已从 auto 集合里摘掉，所以只剩 5 个预取章在窗口外
        val removed = r.trimAutoChapters(b.bookId, setOf("c3", "c4", "c5", "c6"))
        assertEquals(5, removed)
        assertEquals(setOf("c3", "c4", "c5", "c6", "c9"), r.downloadedChapters(b.bookId))
        // 手动章从不在 auto 集合里，所以不会被当成预取章删掉
        assertFalse("c9" in r.autoChapters(b.bookId))
        assertTrue(r.hasChapter(b.bookId, "c9"))
    }

    @Test
    fun trimStopsAtLimitPerPass() {
        val r = repo()
        val b = book()
        val chapters = (1..60).map { Chapter("c$it", "第 $it 章") }
        r.putBook(b, chapters)
        chapters.forEach { r.putChapter(b.bookId, it.id, it.title, "正文 ${it.id}", auto = true) }
        val removed = r.trimAutoChapters(b.bookId, setOf("c1", "c2"))
        assertEquals(50, removed)
        // 剩下的下一次再清，避免切一章就删几十个文件卡住主线程
        assertEquals(8, r.trimAutoChapters(b.bookId, setOf("c1", "c2")))
    }

    @Test
    fun deletingAChapterClearsItsAutoEntry() {
        val r = repo()
        val b = book()
        r.putChapter(b.bookId, "c5", "五", "正文", auto = true)
        assertTrue(r.removeChapter(b.bookId, "c5"))
        assertTrue(r.autoChapters(b.bookId).isEmpty())
        assertFalse(r.hasChapter(b.bookId, "c5"))
    }

    @Test
    fun unknownPrefetchValueFallsBackAndIsRewrittenOnSave() {
        val root = File(temp.newFolder(), "offline")
        val r = OfflineRepo(root, temp.newFolder())
        r.saveDownloadPolicy(DownloadPolicy(prefetchChapters = 7))
        // 存的时候就把非法档位归一化，磁盘上不会留下 7
        assertEquals(3, OfflineRepo(root, temp.newFolder()).downloadPolicy().prefetch)
        assertEquals(3, DownloadPolicy(prefetchChapters = 7).prefetch)
    }
}
