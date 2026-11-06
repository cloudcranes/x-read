package com.alanmaster.xread.data

/**
 * 阅读器外观与翻页偏好。纯数据，无 Android 依赖，便于单测与落盘。
 *
 * 旧实现把字号存在 `remember` 里，退出阅读页就回到 19sp；背景/行距则完全没有。
 */
data class ReaderPrefs(
    val fontSize: Float = 19f,
    val lineHeight: Float = 1.75f,
    /** paper | white | mint | dark */
    val theme: String = "paper",
    /** 点按左右三分之一区域翻页 */
    val tapTurn: Boolean = true,
    /** 音量键翻页 */
    val volumeTurn: Boolean = true,
    /** 漫画浏览模式：vertical(条漫连续流) | paged(横向翻页) */
    val mangaMode: String = "vertical",
    /** 正文字体：default | serif | sans | kai | mono（都随 APK 打包，见 res/font） */
    val font: String = "default",
    /** 正文段评气泡与段评入口 */
    val paragraphComments: Boolean = true,
    /** 段首缩进两个汉字 */
    val indent: Boolean = true,
    /** 段间距，单位是行高：紧凑 0.45 / 标准 0.75 / 宽松 1.05 */
    val paragraphGap: Float = 0.75f,
    /** 翻页方式：simulate(仿真) | slide(滑动) | none(无动画) */
    val turnMode: String = "slide",
    /** 阅读时保持屏幕常亮。默认开：看两页就黑屏是最常见的打断。 */
    val keepAwake: Boolean = true,
    /** 自动滚动速度：off | slow | normal | fast，单位见 AUTO_SPEEDS */
    val autoScroll: String = "off",
    /** 正文左右留白，单位 dp，见 MARGINS。窄屏一行字太少、大屏一行太长，都靠它调。 */
    val margin: Float = 20f,
    /** 正文额外字距，单位 em，见 TRACKINGS。0 表示按字体自带间距。 */
    val letterSpacing: Float = 0f,
    /** 翻页时轻震一下。系统关掉触感或开着 TalkBack 时不生效。 */
    val hapticTurn: Boolean = true,
    /**
     * 阅读页屏内亮度：-1 跟随系统，其余 0.1–1.0。
     *
     * 只改本页窗口的亮度，退出阅读页即还原；写系统设置需要权限，也不该动全局。
     */
    val brightness: Float = -1f,
) {
    fun normalized(): ReaderPrefs = copy(
        fontSize = fontSize.coerceIn(MIN_FONT, MAX_FONT),
        lineHeight = lineHeight.coerceIn(MIN_LINE, MAX_LINE),
        theme = if (theme in THEMES) theme else "paper",
        mangaMode = if (mangaMode in MANGA_MODES) mangaMode else "vertical",
        // 旧版「兰亭」走 MIUI 私有字体族，换机就失效；现在换成随包自带的「楷体」。
        font = when {
            font == "type" -> "kai"
            font in FONTS -> font
            else -> "default"
        },
        paragraphGap = paragraphGap.coerceIn(MIN_GAP, MAX_GAP),
        turnMode = if (turnMode in TURN_MODES) turnMode else "slide",
        autoScroll = if (autoScroll in AUTO_SCROLLS) autoScroll else "off",
        margin = margin.coerceIn(MIN_MARGIN, MAX_MARGIN),
        letterSpacing = letterSpacing.coerceIn(MIN_TRACKING, MAX_TRACKING),
        // 负值一律当「跟随系统」，免得残缺数据把屏幕压成黑的
        brightness = if (brightness < 0f) -1f else brightness.coerceIn(MIN_BRIGHT, MAX_BRIGHT),
    )

    companion object {
        const val MIN_FONT = 14f
        const val MAX_FONT = 30f
        const val MIN_LINE = 1.2f
        const val MAX_LINE = 2.4f
        const val MIN_GAP = 0.2f
        const val MAX_GAP = 1.4f
        const val MIN_MARGIN = 16f
        const val MAX_MARGIN = 26f
        const val MIN_TRACKING = 0f
        const val MAX_TRACKING = 0.06f
        const val MIN_BRIGHT = 0.1f
        const val MAX_BRIGHT = 1f
        val THEMES = setOf("auto", "paper", "white", "mint", "dark")
        val MANGA_MODES = setOf("vertical", "paged")
        val FONTS = setOf("default", "serif", "sans", "kai", "mono")
        val TURN_MODES = setOf("simulate", "slide", "none")

        /**
         * 自动滚动速度表，单位 px/s。
         *
         * 按「每秒几行」定标再换算：常速 26px/s 在 1.75 行距、19sp 正文下约等于
         * 每 1.9 秒一行，接近默读速度；慢档留给需要停顿的段落，快档给扫榜。
         */
        val AUTO_SPEEDS = linkedMapOf(
            "off" to 0f,
            "slow" to 14f,
            "normal" to 26f,
            "fast" to 42f,
        )
        val AUTO_SCROLLS = AUTO_SPEEDS.keys

        // linkedMapOf：设置面板按插入顺序渲染
        val AUTO_LABELS = linkedMapOf(
            "off" to "关",
            "slow" to "慢",
            "normal" to "常速",
            "fast" to "快",
        )
        // linkedMapOf：设置面板按插入顺序渲染
        val TURN_LABELS = linkedMapOf(
            "simulate" to "仿真",
            "slide" to "滑动",
            "none" to "无动画",
        )
        // linkedMapOf：设置面板按插入顺序渲染
        val FONT_LABELS = linkedMapOf(
            "default" to "默认",
            "serif" to "宋体",
            "sans" to "黑体",
            "kai" to "楷体",
            "mono" to "等宽",
        )
        // linkedMapOf：设置面板按插入顺序渲染，「跟随系统」排在最前
        val THEME_LABELS = linkedMapOf(
            "auto" to "跟随系统",
            "paper" to "米黄",
            "white" to "白",
            "mint" to "浅绿",
            "dark" to "夜色",
        )

        /**
         * 页边距三档，单位 dp。
         *
         * 20dp 是旧版写死的值：19sp 正文在 6.1 寸屏上每行 18 字左右，
         * 窄一档给 6.7 寸以上的大屏多挤几个字，宽一档给单手握持留出拇指区。
         */
        val MARGINS = listOf(16f, 20f, 26f)
        val MARGIN_LABELS = listOf("窄", "标准", "宽")

        /** 字距三档，单位 em。中文宋体默认偏紧，加 0.02em 就有明显气口。 */
        val TRACKINGS = listOf(0f, 0.02f, 0.05f)
        val TRACKING_LABELS = listOf("默认", "微松", "加宽")

        /** 屏内亮度四档：-1 跟随系统，其余为窗口亮度。 */
        val BRIGHTNESSES = listOf(-1f, 0.25f, 0.55f, 1f)
        val BRIGHTNESS_LABELS = listOf("跟随", "暗", "中", "亮")
    }
}
