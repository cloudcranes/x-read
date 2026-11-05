package com.alanmaster.xread.ui

import org.junit.Assert.assertEquals
import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 设计令牌守卫。
 *
 * v3「刊」把圆角收到三档（0 / 10 / 20）：卡片一律直角 + 发丝线，不用气泡感。
 * 这里把档位与网格钉成测试，防止有人随手加回 8/14/18 这类散值。
 */
class DesignTokensTest {
    @Test fun radiusHasExactlyThreeSteps() {
        val steps = listOf(Radius.none, Radius.sm, Radius.xl).map { it.value }
        assertEquals("圆角只允许 0 / 10 / 20 三档，实际 $steps", 3, steps.distinct().size)
        assertEquals("最小档必须是直角：$steps", 0f, steps.first())
        assertEquals("中间档是封面 10dp：$steps", 10f, steps[1])
        assertEquals("最大档是抽屉 20dp：$steps", 20f, steps[2])
    }

    @Test fun gutterIsTwentyDp() {
        assertEquals("页面边距 v3 定为 20dp", 20f, Space.gutter.value)
    }

    @Test fun spacingIsOnFourDpGrid() {
        // xxs=2dp 是唯一的半格例外：只用于图标内部的像素级偏移，不参与版面节奏。
        listOf(
            Space.xs, Space.sm, Space.md, Space.lg,
            Space.xl, Space.xxl, Space.huge, Space.gutter, Space.card, Space.section,
        ).forEach { step ->
            assertTrue("间距 ${step.value}dp 不在 4dp 网格上", step.value % 4f == 0f)
        }
    }

    /**
     * 三级文字必须过 AA。
     *
     * v3 把序号、单位、元信息全部退到 `Ink3`；如果它的对比度掉到 4.5 以下，
     * 整个「数字右对齐成列」的设计就不成立了（看不清就没法扫读）。
     */
    @Test fun ink3PassesAaOnPaperAndCard() {
        val paper = contrast(Ink3, Color(0xFFFBFAF7))
        val card = contrast(Ink3, Color(0xFFFFFFFF))
        assertTrue("Ink3 在纸白底上只有 $paper，低于 4.5", paper >= 4.5)
        assertTrue("Ink3 在白卡上只有 $card，低于 4.5", card >= 4.5)
    }

    /** 朱红主操作色：白字压在上面的对比度必须过 AA。 */
    @Test fun onPrimaryIsReadable() {
        val ratio = contrast(Color(0xFFB3341F), Color(0xFFFFFFFF))
        assertTrue("朱红底上的白字只有 $ratio", ratio >= 4.5)
    }

    /** 松绿（下降）与藏金（评分）在纸白底上也要过 AA。 */
    @Test fun accentColorsPassAaOnPaper() {
        listOf("DownGreen" to DownGreen, "RankGold" to RankGold).forEach { (name, color) ->
            val ratio = contrast(color, Color(0xFFFBFAF7))
            assertTrue("$name 在纸白底上只有 $ratio，低于 4.5", ratio >= 4.5)
        }
    }

    /** WCAG 对比度。颜色分量取自 Compose 的 [Color]，与真机渲染同源。 */
    private fun contrast(fg: Color, bg: Color): Double {
        fun luminance(c: Color): Double {
            fun channel(v: Float): Double {
                val d = v.toDouble()
                return if (d <= 0.03928) d / 12.92 else Math.pow((d + 0.055) / 1.055, 2.4)
            }
            return 0.2126 * channel(c.red) + 0.7152 * channel(c.green) + 0.0722 * channel(c.blue)
        }
        val a = luminance(fg)
        val b = luminance(bg)
        return (maxOf(a, b) + 0.05) / (minOf(a, b) + 0.05)
    }
}
