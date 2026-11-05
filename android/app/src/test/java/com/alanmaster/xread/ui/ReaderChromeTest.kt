package com.alanmaster.xread.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 阅读页 chrome 与正文版心的结构守卫。
 *
 * v2 把阅读页的坐标从「散落在各处的常量」收敛成 [ReaderMetrics]：
 *  - 正文左右边距 20dp（v1 是 24dp）
 *  - 中央 60% 区域点按才切换 chrome（v1 是 40%）
 *  - 底栏四动作：目录 / 设置 / 听书 / 夜间
 *
 * 这些值散在 Compose 代码里很容易被下一个改动悄悄改掉，
 * 所以用纯函数 + 常量把边界钉住。
 */
class ReaderChromeTest {
    @Test fun textGutterIsTwentyDp() {
        assertEquals(20f, ReaderMetrics.textGutter.value, 0.01f)
    }

    @Test fun centerTapRatioIsSixtyPercent() {
        assertEquals(0.6f, ReaderMetrics.centerTapRatio, 0.01f)
    }

    @Test fun bottomBarHasFourActions() {
        assertEquals(4, ReaderMetrics.bottomActions.size)
        assertEquals(listOf("目录", "设置", "听书", "夜间"), ReaderMetrics.bottomActions)
    }

    /** 中央点按区间的左右边界必须各占屏宽 20%。 */
    @Test fun centerTapRangeCoversSixtyPercent() {
        val width = 1080f
        val left = width * (1f - ReaderMetrics.centerTapRatio) / 2f
        val right = width - left
        assertEquals(0.2f, left / width, 0.001f)
        assertEquals(0.8f, right / width, 0.001f)
        assertTrue("中央区必须宽于左右翻页区", (right - left) > width / 3f)
    }
}
