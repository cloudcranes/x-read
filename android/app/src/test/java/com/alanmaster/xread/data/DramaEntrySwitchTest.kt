package com.alanmaster.xread.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * 短剧入口开关落盘。
 *
 * 上游播放地址解析接口（multi_video_model / video_model）经常整路径返回空包，
 * 入口露着只会让读者点进黑屏，所以默认收起；这个开关决定底栏、发现页、
 * 搜索 tab 与书架媒体类型里到底出不出现「短剧」。
 *
 * 开关只影响入口，不动数据：关掉再打开，收藏、订阅、追剧记录、进度都还在。
 */
class DramaEntrySwitchTest {
    @get:Rule val temp = TemporaryFolder()

    private fun repo(): OfflineRepo =
        OfflineRepo(temp.newFolder("offline"), temp.newFolder("export"))

    @Test fun defaultsToHidden() {
        assertFalse("短剧入口默认应收起", repo().dramaEnabled())
    }

    @Test fun switchRoundTrips() {
        val r = repo()
        r.saveDramaEnabled(true)
        assertTrue("打开后应落盘为 true", r.dramaEnabled())
        r.saveDramaEnabled(false)
        assertFalse("关掉后应落盘为 false", r.dramaEnabled())
    }

    @Test fun survivesNewRepoOnSameDir() {
        val root = temp.newFolder("offline")
        val export = temp.newFolder("export")
        OfflineRepo(root, export).saveDramaEnabled(true)
        // 新实例读同一个目录，等价于杀进程重启。
        assertTrue("重启后开关应保持", OfflineRepo(root, export).dramaEnabled())
    }

    @Test fun doesNotTouchOtherSettings() {
        val root = temp.newFolder("offline")
        val export = temp.newFolder("export")
        val r = OfflineRepo(root, export)
        r.saveNewChapterNotify(true)
        r.saveDramaEnabled(true)
        val again = OfflineRepo(root, export)
        assertTrue("开关写盘不应覆盖新章提醒", again.newChapterNotifyEnabled())
        assertTrue(again.dramaEnabled())
    }

    @Test fun hidingKeepsDramaData() {
        val root = temp.newFolder("offline")
        val export = temp.newFolder("export")
        val r = OfflineRepo(root, export)
        r.pushDramaRecent(
            DramaSeries(
                seriesId = "7680479504975793176",
                title = "穿越古代 我靠军火建立王朝",
                coverUrl = "https://example.com/a.jpg",
                intro = "",
                author = "",
                episodeCount = 80,
                contentType = 1,
            )
        )
        r.saveDramaEnabled(false)
        val again = OfflineRepo(root, export)
        assertFalse(again.dramaEnabled())
        assertTrue(
            "关掉入口不该删追剧记录",
            again.dramaRecent().any { it.seriesId == "7680479504975793176" },
        )
    }
}
