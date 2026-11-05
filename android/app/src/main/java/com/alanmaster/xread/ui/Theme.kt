package com.alanmaster.xread.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/* ================================================================== *
 * x-read 设计系统
 *
 * 三层，互不越界：
 *  1. 色板  应用外壳 [LightColors] / [DarkColors]；阅读页另用 [ReaderPalette]，
 *          因为阅读底色必须由用户显式挑选护眼方案，不能跟随系统。
 *  2. 字体  界面用无衬线，阅读正文用衬线（宋体）——中文长文本在宋体下更护眼。
 *  3. 尺度  [Space] 4dp 基网格、[Radius] 圆角、[AppTypography] 字号阶梯。
 * ================================================================== */

/* ------------------------------ 间距 ------------------------------ */

/** 4dp 基网格。页面留白只允许取这里的值，不要随手写 13.dp / 18.dp。 */
object Space {
    val xxs = 2.dp
    val xs = 4.dp
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
    val xl = 20.dp
    val xxl = 24.dp
    val huge = 32.dp

    /** 列表类页面的左右安全边距。v3 从 16dp 放宽到 20dp：刊物需要更松的气口。 */
    val gutter = 20.dp

    /** 悬浮底栏让出的高度：贴着屏幕底部浮着，列表末尾要留出这么多才不会被它压住。 */
    val navFloat = 80.dp

    /** 卡片内边距。 */
    val card = 12.dp

    /** 区块之间的纵向间距。 */
    val section = 28.dp
}

/* ------------------------------ 圆角 ------------------------------ */

/**
 * 圆角只有三档：**0 / 10 / 20**。
 *
 * v3「刊」的骨架是报纸编辑设计 + 数据表对齐纪律，卡片一律直角 + 发丝线，
 * 不用气泡感。只有封面（10dp，印刷品的圆角出血）和底部抽屉（20dp）例外。
 *
 * v2 曾有 8/14/18/24 四档，落地后同屏必然出现三档以上：
 * 芯片 8 + 封面 8 + 卡片 14 + 抽屉 24，视觉噪点多于信息。
 */
object Radius {
    /** 直角：卡片、输入框、芯片、按钮 —— 全部 0。 */
    val none = 0.dp

    /** 封面。 */
    val sm = 10.dp

    /** 底部抽屉顶部。 */
    val xl = 20.dp
}

/* ------------------------------ 阴影 ------------------------------ */

/**
 * 分层只有两级：**贴面** 与 **浮起**。
 *
 * 旧实现里同一个 `surfaceVariant` 同时当占位底、卡片底和分割线，
 * 导致层级全糊在一起；现在用 surface 系列分层、outline 系列画边。
 */
object Elevation {
    /** 贴面：卡片。 */
    val flat = 0.dp

    /** 浮起：浮层、抽屉、弹出菜单。 */
    val raised = 6.dp
}

/* ------------------------------ 字体 ------------------------------ */

/** 界面字体：无衬线，用于所有非正文文本。 */
val Sans = FontFamily.SansSerif

/**
 * 阅读字体：衬线。Android 上 `FontFamily.Serif` 映射到 Noto Serif，
 * 中文回退到思源宋体，横向笔画更细、白底/米黄底上的正视疲劳低于黑体。
 */
val Serif = FontFamily.Serif

private val LineTrim = LineHeightStyle(
    alignment = LineHeightStyle.Alignment.Center,
    trim = LineHeightStyle.Trim.None,
)

private fun style(
    family: FontFamily,
    size: Int,
    line: Int,
    weight: FontWeight = FontWeight.Normal,
    tracking: Double = 0.0,
) = TextStyle(
    fontFamily = family,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = line.sp,
    letterSpacing = tracking.sp,
    lineHeightStyle = LineTrim,
)

/**
 * 字号阶梯。
 *
 * 中文字形的字面框比西文满，同样的 pt 在中文里看着更大，
 * 所以这里的基准比 M3 默认值低一档；行高按中文行文取 1.5–1.75 倍。
 */
private val AppTypography = Typography(
    displayLarge = style(Sans, 44, 52),
    displayMedium = style(Sans, 36, 44),
    displaySmall = style(Sans, 30, 38),
    headlineLarge = style(Sans, 28, 36, FontWeight.Bold),
    headlineMedium = style(Sans, 24, 32, FontWeight.Bold),
    headlineSmall = style(Sans, 20, 28, FontWeight.Bold),
    titleLarge = style(Sans, 18, 26, FontWeight.Bold),
    titleMedium = style(Sans, 16, 24, FontWeight.SemiBold),
    titleSmall = style(Sans, 14, 20, FontWeight.SemiBold),
    bodyLarge = style(Sans, 15, 24),
    bodyMedium = style(Sans, 14, 21),
    bodySmall = style(Sans, 12, 18),
    labelLarge = style(Sans, 14, 20, FontWeight.Medium),
    labelMedium = style(Sans, 12, 16, FontWeight.Medium),
    labelSmall = style(Sans, 11, 15, FontWeight.Medium),
)

private val AppShapes = Shapes(
    extraSmall = androidx.compose.foundation.shape.RoundedCornerShape(Radius.none),
    small = androidx.compose.foundation.shape.RoundedCornerShape(Radius.none),
    medium = androidx.compose.foundation.shape.RoundedCornerShape(Radius.none),
    large = androidx.compose.foundation.shape.RoundedCornerShape(Radius.sm),
    extraLarge = androidx.compose.foundation.shape.RoundedCornerShape(Radius.xl),
)

/* ------------------------------ 色板 ------------------------------ */

/**
 * 浅色「纸白」：`#FBFAF7` 底 + `#16150F` 墨 + `#B3341F` 朱红主色。
 *
 * 与 v2 的宣纸米黄（`#FAF6F0` + 赭石 `#9C4A22`）的区别是**用途**：
 * 米黄赭石是一套仿古的阅读语言；榜单/搜索/详情是信息页，需要更高的明度分离
 * 才能让发丝线与数字立住。纸白比米黄冷一档，与阅读页的暖米黄内文形成
 * 「封面纸 / 内文纸」的对比。
 *
 * 全部前景色对 `background` 的对比度实测（WCAG 相对亮度）：
 *   onSurface 17.52:1 / onSurfaceVariant 6.71:1 / onSurfaceVariant 三级 5.19:1
 *   primary 5.87:1 / onPrimary 6.13:1 / secondary(松绿) 5.85:1 / tertiary(金) 4.83:1
 */
private val LightColors = lightColorScheme(
    /** 朱红：主操作、选中、排名上升。 */
    primary = Color(0xFFB3341F),
    onPrimary = Color.White,
    /** 朱红浅底：选中芯片、徽标。 */
    primaryContainer = Color(0xFFF8E6E1),
    onPrimaryContainer = Color(0xFFB3341F),
    inversePrimary = Color(0xFFE36A50),

    /** 松绿：排名下降、「已读」这类收敛状态。中文语境红涨绿跌。 */
    secondary = Color(0xFF3F6B52),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE4EDE6),
    onSecondaryContainer = Color(0xFF2C4A39),

    /** 藏金：评分、前三名序号。 */
    tertiary = Color(0xFF8A6A1F),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFBF4E2),
    onTertiaryContainer = Color(0xFF5E4813),

    background = Color(0xFFFBFAF7),
    onBackground = Color(0xFF16150F),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF16150F),
    /** 卡片内的次级面，用于骨架占位与内嵌块。 */
    surfaceVariant = Color(0xFFF4F2EC),
    /** 次要文字 6.71:1；三级文字另用 [Ink3]。 */
    onSurfaceVariant = Color(0xFF5C594F),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF8F7F4),
    surfaceContainer = Color(0xFFF4F2EC),
    surfaceContainerHigh = Color(0xFFEDEAE2),
    surfaceContainerHighest = Color(0xFFE6E2D8),
    surfaceTint = Color(0xFFB3341F),
    inverseSurface = Color(0xFF1F1D18),
    inverseOnSurface = Color(0xFFF8F7F4),

    /** 可交互边界（输入框描边）。 */
    outline = Color(0xFFD3CEC1),
    /** 发丝分隔线。 */
    outlineVariant = Color(0xFFE6E2D8),
    scrim = Color(0xFF16150F),
    error = Color(0xFFB3261E),
    onError = Color.White,
    errorContainer = Color(0xFFF9DEDC),
    onErrorContainer = Color(0xFF410E0B),
)

/**
 * 三级文字与「持平」标记。
 *
 * 不能直接用 `onSurfaceVariant`：那个是 6.71:1，用作序号、单位、元信息时
 * 视线重量会和正文抢。`#6E6A5C` 在纸白上是 5.19:1、在白卡上是 5.41:1，
 * 刚好过 AA 又明显退后一层。
 */
val Ink3 = Color(0xFF6E6A5C)

/** 排名下降 / 收敛态。与 `secondary` 同值，给非 Material 组件直接用。 */
val DownGreen = Color(0xFF3F6B52)

/** 评分与前三名序号。 */
val RankGold = Color(0xFF8A6A1F)

/** 深色（夜灯）：近黑暖灰底，浅朱红主色，正文不用纯白以免眩光。 */
private val DarkColors = darkColorScheme(
    primary = Color(0xFFE36A50),
    onPrimary = Color(0xFF3D1108),
    primaryContainer = Color(0xFF5E2318),
    onPrimaryContainer = Color(0xFFFFDAD1),
    inversePrimary = Color(0xFFB3341F),

    secondary = Color(0xFFA8C7AE),
    onSecondary = Color(0xFF1B3527),
    secondaryContainer = Color(0xFF2E4C3A),
    onSecondaryContainer = Color(0xFFC5E5CB),

    tertiary = Color(0xFFE0C078),
    onTertiary = Color(0xFF3E2E04),
    tertiaryContainer = Color(0xFF5A450E),
    onTertiaryContainer = Color(0xFFFCDD93),

    background = Color(0xFF14120F),
    onBackground = Color(0xFFE8E1D6),
    surface = Color(0xFF1C1A16),
    onSurface = Color(0xFFE8E1D6),
    surfaceVariant = Color(0xFF26231E),
    onSurfaceVariant = Color(0xFFB5ABA0),
    surfaceContainerLowest = Color(0xFF100E0C),
    surfaceContainerLow = Color(0xFF1A1814),
    surfaceContainer = Color(0xFF211E1A),
    surfaceContainerHigh = Color(0xFF2B2822),
    surfaceContainerHighest = Color(0xFF363129),
    surfaceTint = Color(0xFFE36A50),
    inverseSurface = Color(0xFFE8E1D6),
    inverseOnSurface = Color(0xFF2A2621),

    outline = Color(0xFF3B352D),
    outlineVariant = Color(0xFF4A4239),
    scrim = Color(0xFF000000),
    error = Color(0xFFF2B8B5),
    onError = Color(0xFF601410),
    errorContainer = Color(0xFF8C1D18),
    onErrorContainer = Color(0xFFF9DEDC),
)

/** 三级文字在深色下的对应值（对 `#14120F` 为 7.24:1）。 */
val Ink3Dark = Color(0xFFA8A096)

/** 按当前主题取三级文字色。 */
val ink3: Color
    @Composable get() = if (isSystemInDarkTheme()) Ink3Dark else Ink3

/** 按当前主题取「评分/前三名」色。 */
val rankGold: Color
    @Composable get() = if (isSystemInDarkTheme()) Color(0xFFE0C078) else RankGold

/** 按当前主题取「下降」色。 */
val downGreen: Color
    @Composable get() = if (isSystemInDarkTheme()) Color(0xFFA8C7AE) else DownGreen

@Composable
fun XReadTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (dark) DarkColors else LightColors,
        typography = AppTypography,
        shapes = AppShapes,
        content = content,
    )
}
