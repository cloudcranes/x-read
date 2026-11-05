package com.alanmaster.xread.ui

import androidx.compose.ui.graphics.Color

/**
 * 阅读页配色。
 *
 * 纯数据映射，不依赖主题，避免深色主题下正文仍是白底。
 *
 * 四个方案的取色逻辑：
 *  - **米黄（paper）**：底色带黄绿，短波蓝光被抵消，正文用深褐而非纯黑，
 *    在暖光下最不刺眼，是长时间阅读的默认项。
 *  - **白（white）**：白底对 OLED 省电，但夜里偏刺眼。
 *  - **浅绿（mint）**：底色带绿，进一步削弱红光，适合白天强光下阅读。
 *  - **夜色（dark）**：底色用深灰蓝而非纯黑——纯黑与白字对比度过高，
 *    OLED 上还会出现「拖影」；深灰底配浅灰字对比度约 8:1，接近无障碍标准。
 */
data class ReaderPalette(
    val background: Color,
    val text: Color,
    val title: Color,
    /** 注释/气泡等次要信息的颜色。 */
    val dim: Color,
    /** 强调色：命中高亮、朗读中、段评热评。 */
    val accent: Color,
    /** 分割线与边框。 */
    val rule: Color,
    /** 高亮底色（叠加在背景上的半透明填充）。 */
    val highlight: Color,
) {
    companion object {
        /** 米黄：护眼首选。 */
        private val Paper = ReaderPalette(
            background = Color(0xFFF3EBD9),
            text = Color(0xFF2C2620),
            title = Color(0xFF1A1611),
            // dim 从 #7B6E5E 收到 #6B5F50：米黄底上的对比度 4.18:1 → 5.24:1，
            // 页眉、进度、段评计数这些 11–12sp 小字才过 AA。
            dim = Color(0xFF6B5F50),
            accent = Color(0xFF9C4A22),
            rule = Color(0xFFE0D5BE),
            highlight = Color(0x339C4A22),
        )

        /** 纯白：白天/OLED 省电。 */
        private val White = ReaderPalette(
            background = Color(0xFFFFFFFF),
            text = Color(0xFF1F1D1A),
            title = Color(0xFF0C0B0A),
            dim = Color(0xFF6E6252),
            accent = Color(0xFF9C4A22),
            rule = Color(0xFFE6E3DE),
            highlight = Color(0x339C4A22),
        )

        /** 浅绿：白天强光下用。 */
        private val Mint = ReaderPalette(
            background = Color(0xFFE4EEDE),
            text = Color(0xFF1E2820),
            title = Color(0xFF131C16),
            dim = Color(0xFF5F6B60),
            accent = Color(0xFF4A6B3F),
            rule = Color(0xFFD0DDD0),
            highlight = Color(0x334A6B3F),
        )

        /** 夜色：深灰蓝底 + 浅灰字，避免纯黑高对比。 */
        private val Night = ReaderPalette(
            background = Color(0xFF171A1E),
            text = Color(0xFFC8C7C2),
            title = Color(0xFFEBEAE5),
            dim = Color(0xFFB5ABA0),
            accent = Color(0xFFD9A273),
            rule = Color(0xFF2A2E34),
            highlight = Color(0x33D9A273),
        )

        /**
         * @param systemDark 「跟随系统」时按系统深浅决定用夜色还是米黄。
         *   不能把 auto 直接映到 Paper，否则系统深色下正文仍是白底。
         */
        fun of(theme: String, systemDark: Boolean = false): ReaderPalette = when (theme) {
            "white" -> White
            "mint" -> Mint
            "dark" -> Night
            "auto" -> if (systemDark) Night else Paper
            else -> Paper
        }

        /** 主题键 → 中文名，供设置面板与浮层使用。 */
        val LABELS = linkedMapOf(
            "auto" to "跟随系统",
            "paper" to "米黄",
            "white" to "白",
            "mint" to "浅绿",
            "dark" to "夜色",
        )

        /** 主题键 → 一句话说明。 */
        val HINTS = linkedMapOf(
            "auto" to "白天米黄，夜间夜色",
            "paper" to "暖黄底，最护眼",
            "white" to "纯白底，省电",
            "mint" to "浅绿底，弱蓝光",
            "dark" to "深色底，暗光阅读",
        )
    }
}
