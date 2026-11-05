package com.alanmaster.xread.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderPaletteTest {
    @Test fun autoFollowsSystemDarkness() {
        // 系统深色时必须换成夜色，否则「跟随系统」在深色下仍是白底
        assertEquals(ReaderPalette.of("dark"), ReaderPalette.of("auto", systemDark = true))
        assertEquals(ReaderPalette.of("paper"), ReaderPalette.of("auto", systemDark = false))
    }

    @Test fun explicitThemesIgnoreSystemDarkness() {
        assertEquals(ReaderPalette.of("white"), ReaderPalette.of("white", systemDark = true))
        assertEquals(ReaderPalette.of("mint"), ReaderPalette.of("mint", systemDark = true))
    }

    @Test fun unknownThemeFallsBackToPaper() {
        assertEquals(ReaderPalette.of("paper"), ReaderPalette.of("neon"))
    }

    /**
     * 每个阅读方案都必须自带完整的 7 个槽位。
     *
     * 旧实现只有 bg/text/title 三个槽位，弱信息只能去借应用色板的
     * `onSurfaceVariant`——那是从米黄应用底色推出来的，在浅绿/夜色底上不匹配。
     */
    @Test fun everyPaletteHasFullSlotSet() {
        listOf("paper", "white", "mint", "dark").forEach { key ->
            val p = ReaderPalette.of(key)
            listOf(
                p.background to p.text,
                p.background to p.dim,
                p.background to p.title,
                p.background to p.accent,
                p.background to p.rule,
            ).forEach { (bg, fg) ->
                // 前景不能与背景同色：同色 = 文字不可见
                assertTrue("$key 的前景色与背景色相同", bg != fg)
            }
        }
    }

    /**
     * 正文与背景的对比度必须够阅读。
     *
     * 旧夜色方案是 #121212 配 #C9C6C0，对比度约 11:1——纯黑底在 OLED 上过曝且有拖影。
     * 新方案换成深灰蓝底，目标是 8:1 上下，仍然远超 4.5:1 的正文标准。
     */
    @Test fun bodyTextContrastIsReadable() {
        listOf("paper", "white", "mint", "dark").forEach { key ->
            val p = ReaderPalette.of(key)
            val ratio = contrast(p.background, p.text)
            assertTrue("$key 的正文对比度只有 $ratio，低于 7:1", ratio >= 7.0)
        }
    }

    @Test fun everyThemeHasLabelAndHint() {
        listOf("paper", "white", "mint", "dark").forEach { key ->
            assertTrue(ReaderPalette.LABELS.containsKey(key))
            assertTrue(ReaderPalette.HINTS.containsKey(key))
        }
        assertEquals("跟随系统", ReaderPalette.LABELS["auto"])
    }

    /**
     * dim 槽（页眉、进度百分比、段评计数这些 11–12sp 小字）必须过 AA 4.5:1。
     *
     * 旧实现米黄底的 dim 是 #7B6E5E，只有 4.18:1——正文 19sp 看着够，
     * 小字在米黄纸上就糊成一团。四个方案现在分别是 5.24 / 5.95 / 4.68 / 7.73:1。
     */
    @Test fun dimSlotPassesAaContrast() {
        listOf("paper", "white", "mint", "dark").forEach { key ->
            val p = ReaderPalette.of(key)
            val ratio = contrast(p.background, p.dim)
            assertTrue("$key 的 dim 对比度只有 $ratio，低于 4.5:1", ratio >= 4.5)
        }
    }
}

/** WCAG 相对亮度。纯函数，便于单测。 */
private fun luminance(color: androidx.compose.ui.graphics.Color): Double {
    fun channel(c: Float): Double {
        val v = c.toDouble()
        return if (v <= 0.03928) v / 12.92 else Math.pow((v + 0.055) / 1.055, 2.4)
    }
    return 0.2126 * channel(color.red) +
        0.7152 * channel(color.green) +
        0.0722 * channel(color.blue)
}

/** WCAG 对比度，返回值 ≥ 1。 */
private fun contrast(a: androidx.compose.ui.graphics.Color, b: androidx.compose.ui.graphics.Color): Double {
    val la = luminance(a)
    val lb = luminance(b)
    val hi = maxOf(la, lb)
    val lo = minOf(la, lb)
    return (hi + 0.05) / (lo + 0.05)
}
