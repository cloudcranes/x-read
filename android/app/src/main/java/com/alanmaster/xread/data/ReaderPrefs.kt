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
    )

    companion object {
        const val MIN_FONT = 14f
        const val MAX_FONT = 30f
        const val MIN_LINE = 1.2f
        const val MAX_LINE = 2.4f
        const val MIN_GAP = 0.2f
        const val MAX_GAP = 1.4f
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
    }
}
