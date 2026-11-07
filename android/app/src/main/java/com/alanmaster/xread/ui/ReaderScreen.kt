package com.alanmaster.xread.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.WindowManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.automirrored.filled.LibraryBooks
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDrawerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextIndent
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import coil.compose.AsyncImage
import coil.request.ImageRequest
import java.io.File
import com.alanmaster.xread.R
import com.alanmaster.xread.data.BookHotReview
import com.alanmaster.xread.data.BookHotReviewPage
import com.alanmaster.xread.data.Chapter
import com.alanmaster.xread.data.ChapterContent
import com.alanmaster.xread.data.FindHighlight
import com.alanmaster.xread.data.Mark
import com.alanmaster.xread.data.ParagraphBubble
import com.alanmaster.xread.data.ReaderPrefs
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * 阅读页（v2「纸间」）。
 *
 * 设计原则：**正文是主角，控件全部让位。**
 *  - 默认只有正文与一条常显的 2dp 进度线；顶栏 / 底栏 / 查找条都靠点按屏幕中部唤出，
 *    再次点按收起，正文的可视高度不因控件常驻而缩水。
 *  - 正文左右各 [ReaderMetrics.textGutter]（20dp），衬线体、用户字号/行距，
 *    **段间空行**而不是首行缩进——中文网络小说按「段」组织，空行分隔更符合阅读节奏。
 *  - 阅读底色只认 [ReaderPalette.of]（用户显式挑选的方案），不跟随应用外壳主题；
 *    顶栏、底栏、进度线、设置抽屉全部用 `palette.*` 着色，避免「白底黑字夹深色正文」。
 *
 * 连续阅读：多章正文接成**一条** LazyColumn，章与章首尾相接，滚过本章末尾就是下一章，
 *  滚回本章开头就接着上一章（两头都自动接），不换页、不清屏。每章占
 *  「页眉 1 项 + 段落 n 项」，列表下标 ↔ (章, 章内段落) 的换算全在 [ReaderFlow] 里；
 *  [ReaderProgress] 只保留单章旧口径，不参与跨章换算。
 */
private data class FindHit(val chapterIndex: Int, val paragraphIndex: Int, val listIndex: Int)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderScreen(
    bookTitle: String,
    chapters: List<Chapter>,
    flow: List<ChapterContent> = emptyList(),
    currentChapterId: String = "",
    /** 是否正在请求正文；用于区分「加载中」与「确实失败」。 */
    loading: Boolean,
    /** 章节加载失败的原因；空串表示没有错误。 */
    error: String,
    onRetry: () -> Unit,
    flowLoading: Boolean = false,
    flowError: String = "",
    onAppendNext: () -> Unit = {},
    /** 章末既没缓存、请求也失败时的出路：先下后 5 章再续接。 */
    onCacheNext: () -> Unit = {},
    /** 正在往流头部接上一章。 */
    flowLoadingPrev: Boolean = false,
    /** 滚到流开头时接上一章；上面没有了由调用方自己停手。 */
    onPrependPrev: () -> Unit = {},
    /** 阅读器外观：背景、行距、翻页方式。字号在 [prefs] 里，避免两个来源打架。 */
    prefs: ReaderPrefs,
    /** 上次读到的段落下标；切章后据此复位。 */
    initialParagraph: Int = 0,
    scrollRequest: Int = 0,
    scrollChapterId: String = "",
    scrollParagraph: Int = -1,
    onBack: () -> Unit,
    onSelect: (String) -> Unit,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    /** 整份偏好回写：字号、行距、背景、翻页方式都从这里落盘。 */
    onPrefs: (ReaderPrefs) -> Unit,
    /** 滚动时回报当前章节与段落，作为阅读进度。 */
    onProgress: (String, Int) -> Unit = { _, _ -> },
    /** 听书状态与操作。 */
    ttsSpeaking: Boolean,
    ttsRate: Float,
    ttsMessage: String,
    /** 正在朗读的段落下标；-1 表示未在朗读。 */
    ttsParagraph: Int = -1,
    ttsChapterId: String = "",
    onTtsToggle: () -> Unit,
    onTtsRate: (Float) -> Unit,
    onTtsSleep: (Int) -> Unit,
    /** 按章索引的段评分布。 */
    bubblesByChapter: Map<String, List<ParagraphBubble>> = emptyMap(),
    /** 按章索引的书签与高亮。 */
    marksByChapter: Map<String, List<Mark>> = emptyMap(),
    /** (章节ID, 段落下标, 摘录文本, 类型) */
    onToggleMark: (String, Int, String, String) -> Unit = { _, _, _, _ -> },
    /** 这本书是否已经在书架上。 */
    inShelf: Boolean = false,
    /** 加入书架 / 移出书架。阅读页也能收书，不必退回详情页。 */
    onToggleShelf: () -> Unit = {},
    /** 右上角进书籍详情；从书架直接开读时，详情页没有别的入口。 */
    onOpenDetail: () -> Unit = {},
    /* ---------- 段评面板（点段末气泡打开） ---------- */
    /** 段评面板是否打开。 */
    paragraphCommentOpen: Boolean = false,
    /** 面板对应的段落下标（应用坐标系）；-1 表示未打开。 */
    paragraphCommentIndex: Int = -1,
    /** 已加载的段评条目。 */
    paragraphComments: List<BookHotReview> = emptyList(),
    /** 该段段评总数。 */
    paragraphCommentTotal: Int = 0,
    /** 段评加载中。 */
    paragraphCommentsLoading: Boolean = false,
    /** 段评面板错误文案；空串表示无错误。 */
    paragraphCommentError: String = "",
    /** 还有没有更多段评。 */
    paragraphCommentsHasMore: Boolean = false,
    /** 点段末气泡：打开该段的段评。 */
    onOpenParagraphComments: (String, Int) -> Unit = { _, _ -> },
    /** 关闭段评面板。 */
    onCloseParagraphComments: () -> Unit = {},
    /** 加载更多段评。 */
    onMoreParagraphComments: () -> Unit = {},
    /** 段评点赞/取消点赞。 */
    onToggleParagraphCommentDigg: (String) -> Unit = {},
) {
    val drawer = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val listState = rememberLazyGridState()
    val density = LocalDensity.current

    // 常亮：直接改窗口 View 的标志位，比往 Window 里塞 FLAG_KEEP_SCREEN_ON 少一层。
    // 离开阅读页必须还原，否则回来时别处也一直亮着。
    val readerView = LocalView.current
    DisposableEffect(prefs.keepAwake) {
        readerView.keepScreenOn = prefs.keepAwake
        onDispose { readerView.keepScreenOn = false }
    }

    // 屏内亮度：只改本页窗口，退出即交还系统。系统那档亮度过半是给白天用的，
    // 夜里关灯读书还得手动拉状态栏，读完再拉回来——这里省掉那一趟。
    val context = LocalContext.current
    DisposableEffect(prefs.brightness) {
        val window = context.findActivity()?.window
        if (window != null) {
            window.attributes = window.attributes.apply {
                screenBrightness = if (prefs.brightness > 0f) {
                    prefs.brightness
                } else {
                    WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
                }
            }
        }
        onDispose {
            window?.let {
                it.attributes = it.attributes.apply {
                    screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
                }
            }
        }
    }

    var findOpen by remember { mutableStateOf(false) }
    var findQuery by remember { mutableStateOf("") }
    var findHits by remember { mutableStateOf<List<FindHit>>(emptyList()) }
    var findCursor by remember { mutableStateOf(0) }
    var markTarget by remember { mutableStateOf<Triple<String, Int, String>?>(null) }
    var menuOpen by remember { mutableStateOf(false) }
    /** 控件层是否展开：默认全沉浸，点按中央才出现。 */
    var chromeVisible by remember { mutableStateOf(false) }
    /** 阅读设置抽屉。 */
    var settingsVisible by remember { mutableStateOf(false) }
    /**
     * 设置面板的底部抽屉状态。
     *
     * skipPartiallyExpanded = true：只留「展开/收起」两档。
     * material3 的半展开档是按面板高度过半屏才生成的，面板内容过半屏时
     * 半展开档会把下半截内容顶到屏幕外（滚也够不到）。
     * 所以这里不要那一档，改成自己封顶面板高度（见 ReaderSettingsSheet）。
     */
    val settingsSheetState = rememberModalBottomSheetState()
    /** 进入夜间前记住的主题；点「夜间」再点一次可原路返回。 */
    var themeBeforeNight by remember { mutableStateOf<String?>(null) }

    /**
     * 左下角常显时钟。
     *
     * 沉浸阅读最容易忘了时间，左下角挂个小钟（跟右下角百分比同一档字号）。
     * 每秒一跳、且每拍都按当前墙钟重算延迟：唤醒抖动不会累积，
     * 某一拍被吞掉最迟下一秒就补上，不会像分钟粒度那样整整慢一分钟。
     */
    var clockText by remember { mutableStateOf(ReaderClock.format(System.currentTimeMillis())) }
    LaunchedEffect(Unit) {
        while (true) {
            val now = System.currentTimeMillis()
            clockText = ReaderClock.format(now)
            delay(ReaderClock.millisToNextSecond(now))
        }
    }

    val systemDark = isSystemInDarkTheme()
    // 底色只由用户显式挑选（auto 才按系统深浅切夜色），不跟随外壳主题
    val palette = remember(prefs.theme, systemDark) { ReaderPalette.of(prefs.theme, systemDark) }

    val (visibleChapIdx, visibleParaIdx) = remember(listState.firstVisibleItemIndex, flow) {
        ReaderFlow.locate(flow, listState.firstVisibleItemIndex)
    }
    val visibleChapter = flow.getOrNull(visibleChapIdx)
    val activeChapterId = visibleChapter?.id ?: currentChapterId
    val activeChapter = visibleChapter ?: flow.firstOrNull { it.id == currentChapterId } ?: flow.firstOrNull()

    /**
     * 跨章章名浮现。
     *
     * 正文是连续滚动的，翻过章节边界时读者很难察觉「已经换章」；
     * 顶部浮一条章名两秒，比退出去看目录省事。首次进入不提示——那是自己点进来的那章。
     */
    var chapterToast by remember { mutableStateOf<String?>(null) }
    var toastChapterId by remember { mutableStateOf("") }
    LaunchedEffect(activeChapterId, activeChapter?.title) {
        if (activeChapterId.isEmpty()) return@LaunchedEffect
        if (toastChapterId == activeChapterId) return@LaunchedEffect
        val first = toastChapterId.isEmpty()
        toastChapterId = activeChapterId
        if (first) return@LaunchedEffect
        val no = chapters.indexOfFirst { it.id == activeChapterId }
        chapterToast = chapterToastText(no, activeChapter?.title.orEmpty())
        delay(1600)
        chapterToast = null
    }

    /**
     * 切章时先复位到上次位置，**再**开始上报进度。
     *
     * [flow] 会随「触底追加下一章」不断变长，而本 effect 的 key 不能跟着变——
     * 一变就会把读者拽回章首。但闭包捕获的是启动那一刻的 [flow]，如果 collect
     * 里直接读它，连读到第 10 章时仍在用「只有第 1 章」的老列表换算下标：
     * locate 把越界下标一律压到最后一章，于是永远上报打开时那一章，进度停在原地，
     * 退出重进就回到原处。所以必须经 [rememberUpdatedState] 读最新的 flow。
     */
    val liveFlow by rememberUpdatedState(flow)
    val liveOnProgress by rememberUpdatedState(onProgress)
    // key 只跟「跳章请求」和「流从空变成非空」：往上接一章会让 flow.first() 换人，
    // 若把它也算进 key，每接一章都会把读者拽回章首。
    LaunchedEffect(scrollRequest, flow.isEmpty()) {
        if (flow.isEmpty()) return@LaunchedEffect
        val targetChapId = if (scrollChapterId.isNotEmpty()) scrollChapterId else currentChapterId
        val chapIdx = flow.indexOfFirst { it.id == targetChapId }.coerceAtLeast(0)
        val targetPara = if (scrollParagraph >= 0) scrollParagraph else initialParagraph
        val targetIndex = if (targetPara <= 0) {
            ReaderFlow.headerOf(flow, chapIdx)
        } else {
            ReaderFlow.indexOf(flow, chapIdx, targetPara)
        }
        if (targetIndex in 0 until ReaderFlow.bodyItems(flow)) {
            listState.scrollToItem(targetIndex)
        }
        snapshotFlow { listState.firstVisibleItemIndex }.collect { index ->
            val current = liveFlow
            val (k, p) = ReaderFlow.locate(current, index)
            current.getOrNull(k)?.let { c ->
                liveOnProgress(c.id, p)
            }
        }
    }

    // 触底自动追加下一章
    val shouldLoadMore by remember(flow, flowLoading, flowError) {
        derivedStateOf {
            val total = listState.layoutInfo.totalItemsCount
            val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            total > 0 && lastVisible >= total - 4
        }
    }
    LaunchedEffect(shouldLoadMore, flowLoading, flowError) {
        if (shouldLoadMore && !flowLoading && flowError.isBlank()) {
            onAppendNext()
        }
    }

    /**
     * 自动滚动。
     *
     * 每帧推进一小段而不是按固定间隔滚一屏：步长小于一屏时，滚动是连续的，
     * 眼睛不用追着力跳。速度按 px/s 记，不足 1px 的余量累计到下一帧——
     * 「慢」档 14px/s 在 90Hz 上每帧才 0.15px，直接取整会一直是 0，等于不动。
     *
     * 任何触摸、翻页、浮层都会把它置成暂停：读者一旦动手，就是不想被动滚。
     *
     * 朗读期间一律不自动滚：朗读跟随自己会把该段滚到视野里，
     * 两套滚动同时开就是每帧互相拽，字会跳。
     */
    val autoSpeed = ReaderPrefs.AUTO_SPEEDS[prefs.autoScroll] ?: 0f
    val ttsRunning = ttsParagraph >= 0 && ttsChapterId.isNotEmpty()
    var autoPaused by remember { mutableStateOf(false) }
    LaunchedEffect(prefs.autoScroll) {
        // 关掉再打开时要能立刻滚，不能沿用上次的暂停态
        if (prefs.autoScroll == "off") autoPaused = false
    }
    LaunchedEffect(autoSpeed, autoPaused, flow, ttsRunning) {
        if (autoSpeed <= 0f || autoPaused || ttsRunning) return@LaunchedEffect
        var carry = 0f
        var last = 0L
        while (isActive) {
            val now = withFrameNanos { it }
            if (last != 0L) {
                carry += autoSpeed * ((now - last) / 1_000_000_000f)
                val step = carry.toInt()
                if (step > 0) {
                    carry -= step
                    if (!listState.canScrollForward) {
                        // 到章末就停：续章由上面的触底逻辑负责，这里不能自己拉章节
                        autoPaused = true
                        break
                    }
                    listState.scrollBy(step.toFloat())
                }
            }
            last = now
        }
    }

    /**
     * 触顶自动接上一章。
     *
     * 两个条件缺一不可：「第一个可见项就是流的第 0 项且贴到顶」，且「列表正在滚」。
     * 后一个条件专门用来区分「读者又往上拉」和「刚打开/刚接完一章」——只看前者的话，
     * 接完一章后布局还没重新量过的那个瞬间它仍然成立，会一路把整本书往前扒。
     * 接完一章后 LazyColumn 按 item key 把镜头钉在原来那一项上，它会变成第 N 项，
     * 条件自然不成立。
     */
    val atFlowStart by remember {
        derivedStateOf { listState.firstVisibleItemIndex <= 0 && listState.firstVisibleItemScrollOffset <= 0 }
    }
    val pullingAtStart by remember {
        derivedStateOf { atFlowStart && listState.isScrollInProgress }
    }
    LaunchedEffect(pullingAtStart, flowLoadingPrev, flow.size) {
        if (pullingAtStart && !flowLoadingPrev && flow.isNotEmpty()) {
            onPrependPrev()
        }
    }

    val firstChapterIndex = remember(chapters, flow) {
        val firstId = flow.firstOrNull()?.id.orEmpty()
        chapters.indexOfFirst { it.id == firstId }.coerceAtLeast(0)
    }
    val progress by remember(listState, flow, chapters.size, firstChapterIndex) {
        derivedStateOf {
            ReaderFlow.bookPercent(
                flow = flow,
                listIndex = listState.firstVisibleItemIndex,
                chaptersTotal = chapters.size,
                firstChapterIndex = firstChapterIndex,
            )
        }
    }

    val marksHere = marksByChapter[activeChapterId].orEmpty()
    val bookmarked = marksHere.any { it.paragraph == visibleParaIdx && it.kind == Mark.KIND_BOOKMARK }

    /** 命中行滚到可视区，但别贴着顶栏/查找条。 */
    fun revealHit(listIndex: Int) {
        scope.launch {
            listState.animateScrollToItem(listIndex)
            listState.animateScrollBy(-with(density) { 88.dp.toPx() })
        }
    }

    fun runFind(query: String, scrollToFirst: Boolean = true) {
        findQuery = query
        val q = query.trim()
        val chapIdx = flow.indexOfFirst { it.id == activeChapterId }
        if (q.isEmpty() || chapIdx < 0) {
            findHits = emptyList()
            findCursor = 0
            return
        }
        // 与界面上的「章内查找」一致：只查当前章
        val hits = flow[chapIdx].findHits(q).map { p ->
            FindHit(chapIdx, p, ReaderFlow.indexOf(flow, chapIdx, p))
        }
        findHits = hits
        findCursor = 0
        if (scrollToFirst) hits.firstOrNull()?.let { revealHit(it.listIndex) }
    }

    fun jump(delta: Int) {
        if (findHits.isEmpty()) return
        findCursor = ((findCursor + delta) % findHits.size + findHits.size) % findHits.size
        findHits.getOrNull(findCursor)?.let { hit -> revealHit(hit.listIndex) }
    }

    // 翻到另一章时按新章重算命中，但不把读者拽回第一处
    LaunchedEffect(activeChapterId) {
        if (findQuery.trim().isEmpty()) {
            findHits = emptyList()
            findCursor = 0
        } else {
            runFind(findQuery, scrollToFirst = false)
        }
    }

    // 抽屉打开时收起工具栏，避免两层控件叠在一起
    LaunchedEffect(settingsVisible) {
        if (settingsVisible) chromeVisible = false
    }

    fun toggleChrome() {
        chromeVisible = !chromeVisible
        if (!chromeVisible) findOpen = false
    }

    fun toggleFind() {
        findOpen = !findOpen
        if (!findOpen) runFind("")
    }

    val night = prefs.theme == "dark" || (prefs.theme == "auto" && systemDark)
    fun toggleNight() {
        if (night) {
            onPrefs(prefs.copy(theme = themeBeforeNight ?: "paper"))
            themeBeforeNight = null
        } else {
            themeBeforeNight = prefs.theme
            onPrefs(prefs.copy(theme = "dark"))
        }
    }

    ModalNavigationDrawer(
        drawerState = drawer,
        drawerContent = {
            ReaderTocDrawer(
                chapters = chapters,
                currentId = activeChapterId,
                palette = palette,
                onSelect = { id ->
                    scope.launch { drawer.close() }
                    onSelect(id)
                },
            )
        },
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(palette.background),
        ) {
            // ---------- 正文 ----------
            // 进度不再占位置：顶部条带撤掉了，只在右下角留一个百分比小字。
            when {
                flow.isEmpty() -> ReaderPlaceholder(
                    loading = loading,
                    error = error,
                    palette = palette,
                    onRetry = onRetry,
                )

                flow.all { it.paragraphs.isEmpty() && it.images.isEmpty() } -> ReaderEmpty(palette = palette, onRetry = onRetry)

                activeChapter?.isManga == true && prefs.mangaMode == "paged" -> MangaPagedReader(
                    chapter = activeChapter,
                    palette = palette,
                    onTapCenter = { toggleChrome() },
                    onPrevChapter = onPrev,
                    onNextChapter = onNext,
                )

                else -> ReaderBody(
                    flow = flow,
                    chapters = chapters,
                    bookTitle = bookTitle,
                    prefs = prefs,
                    palette = palette,
                    listState = listState,
                    findQuery = findQuery,
                    findActiveChapterId = findHits.getOrNull(findCursor)
                        ?.let { flow.getOrNull(it.chapterIndex)?.id }.orEmpty(),
                    findActiveParagraph = findHits.getOrNull(findCursor)?.paragraphIndex ?: -1,
                    bubblesByChapter = if (prefs.paragraphComments) bubblesByChapter else emptyMap(),
                    marksByChapter = marksByChapter,
                    ttsChapterId = ttsChapterId,
                    ttsParagraph = ttsParagraph,
                    flowLoading = flowLoading,
                    flowError = flowError,
                    onRetryAppend = onAppendNext,
        onCacheNext = onCacheNext,
                    onPrev = onPrev,
                    onNext = onNext,
                    onSelect = onSelect,
                    onTapCenter = { toggleChrome() },
                    onLongPress = { chapId, index, text -> markTarget = Triple(chapId, index, text) },
                    onOpenParagraphComments = onOpenParagraphComments,
                    onUserInteraction = { if (autoSpeed > 0f) autoPaused = true },
                )
            }

            // ---------- 顶部：跨章章名 ----------
            // 只在沉浸态出现：顶栏一出，章名已经在标题栏里了，再浮一层就是重复。
            AnimatedVisibility(
                visible = chapterToast != null && !chromeVisible && !settingsVisible,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(top = Space.md),
            ) {
                Surface(
                    shape = RoundedCornerShape(Radius.sm),
                    color = palette.background,
                    border = BorderStroke(1.dp, palette.rule),
                ) {
                    Text(
                        text = chapterToast.orEmpty(),
                        fontSize = 11.sp,
                        color = palette.accent,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = Space.md, vertical = 3.dp),
                    )
                }
            }

            // ---------- 右下角：常显百分比 ----------
            // 进度线撤了，只在右下角留一个百分比小字：位置最不碍事的地方。
            // 底栏/设置/段评浮层展开时收起，免得叠在一起。
            if (!chromeVisible && !settingsVisible && !paragraphCommentOpen) {
                Text(
                    text = "${(progress * 100).toInt()}%",
                    fontSize = 11.sp,
                    lineHeight = 15.sp,
                    color = palette.dim,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .navigationBarsPadding()
                        .padding(end = ReaderMetrics.textGutter, bottom = Space.sm),
                )
            }

            // ---------- 右下角：自动滚动状态 ----------
            // 只在开了自动滚动时出现；暂停后点一下继续，不必再进设置。
            if (autoSpeed > 0f && !chromeVisible && !settingsVisible && !paragraphCommentOpen) {
                Surface(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .navigationBarsPadding()
                        .padding(end = ReaderMetrics.textGutter, bottom = Space.xl)
                        .clip(RoundedCornerShape(Radius.sm))
                        .clickable { autoPaused = !autoPaused },
                    shape = RoundedCornerShape(Radius.sm),
                    color = palette.background,
                    border = BorderStroke(1.dp, palette.rule),
                ) {
                    Text(
                        text = if (autoPaused) "继续" else "自动",
                        fontSize = 11.sp,
                        color = if (autoPaused) palette.accent else palette.dim,
                        modifier = Modifier.padding(horizontal = Space.sm, vertical = 3.dp),
                    )
                }
            }

            // ---------- 左下角：常显时钟 ----------
            // 与右下角百分比对称：浮层展开时一起收起，不跟底栏抢位置。
            if (!chromeVisible && !settingsVisible && !paragraphCommentOpen) {
                Text(
                    text = clockText,
                    fontSize = 11.sp,
                    lineHeight = 15.sp,
                    color = palette.dim,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .navigationBarsPadding()
                        .padding(start = ReaderMetrics.textGutter, bottom = Space.sm),
                )
            }

            // ---------- 底部：底栏（显隐） ----------
            // 无 Scaffold 的手工布局必须自己吃导航栏 inset：否则整列会排到导航栏之下，
            // 底栏在真机上直接被裁出可见区（实测 [0,0][0,0]）。
            Column(
                Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = Space.xs),
            ) {
                AnimatedVisibility(
                    visible = chromeVisible && !settingsVisible,
                    enter = fadeIn() + slideInVertically { it },
                    exit = fadeOut() + slideOutVertically { it },
                ) {
                    ReaderBottomBar(
                        palette = palette,
                        ttsSpeaking = ttsSpeaking,
                        night = night,
                        onOpenToc = { scope.launch { drawer.open() } },
                        onOpenSettings = { settingsVisible = true },
                        onTtsToggle = onTtsToggle,
                        onToggleNight = { toggleNight() },
                    )
                }
            }

            // ---------- 顶部：顶栏（显隐）+ 查找条 ----------
            Column(
                Modifier.align(Alignment.TopCenter),
            ) {
                AnimatedVisibility(
                    visible = chromeVisible,
                    enter = fadeIn() + slideInVertically { -it },
                    exit = fadeOut() + slideOutVertically { -it },
                ) {
                    ReaderTopBar(
                        title = bookTitle.ifBlank { activeChapter?.title.orEmpty() },
                        bookmarked = bookmarked,
                        isManga = activeChapter?.isManga == true,
                        mangaMode = prefs.mangaMode,
                        onToggleMangaMode = {
                            val next = if (prefs.mangaMode == "vertical") "paged" else "vertical"
                            onPrefs(prefs.copy(mangaMode = next))
                        },
                        palette = palette,
                        menuOpen = menuOpen,
                        onMenuOpenChange = { menuOpen = it },
                        onBack = onBack,
                        onToggleBookmark = {
                            val text = activeChapter?.paragraphs?.getOrNull(visibleParaIdx).orEmpty()
                            if (text.isNotEmpty()) {
                                onToggleMark(activeChapterId, visibleParaIdx, text, Mark.KIND_BOOKMARK)
                            }
                        },
                        inShelf = inShelf,
                        onToggleShelf = onToggleShelf,
                        onOpenDetail = onOpenDetail,
                        onToggleFind = { toggleFind() },
                    )
                }
                AnimatedVisibility(
                    visible = findOpen && chromeVisible,
                    enter = fadeIn() + slideInVertically { -it },
                    exit = fadeOut() + slideOutVertically { -it },
                ) {
                    ReaderFindBar(
                        query = findQuery,
                        hits = findHits.map { it.listIndex },
                        cursor = findCursor,
                        palette = palette,
                        onQuery = { runFind(it) },
                        onJump = ::jump,
                        onClose = { findOpen = false; runFind("") },
                    )
                }
            }

            // ---------- 设置抽屉：可上下滑动的底部面板 ----------
            // 用 material3 的 ModalBottomSheet：面板高于半屏时自动停在半展开档，
            // 上下拖动可收起或铺满，不再像以前一打开就糊满整屏。
            if (settingsVisible) {
                ModalBottomSheet(
                    onDismissRequest = {
                        settingsVisible = false
                        chromeVisible = true
                    },
                    sheetState = settingsSheetState,
                    containerColor = palette.background,
                    contentColor = palette.text,
                    scrimColor = Color.Black.copy(alpha = 0.4f),
                    dragHandle = {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .padding(vertical = Space.sm),
                            contentAlignment = Alignment.Center,
                        ) {
                            Box(
                                Modifier
                                    .width(32.dp)
                                    .height(4.dp)
                                    .clip(CircleShape)
                                    .background(palette.rule),
                            )
                        }
                    },
                ) {
                    ReaderSettingsSheet(
                        prefs = prefs,
                        palette = palette,
                        selectedThemeKey = when (prefs.theme) {
                            "auto" -> if (systemDark) "dark" else "paper"
                            else -> prefs.theme
                        },
                        ttsSpeaking = ttsSpeaking,
                        ttsRate = ttsRate,
                        ttsMessage = ttsMessage,
                        onPrefs = onPrefs,
                        onTtsToggle = onTtsToggle,
                        onTtsRate = onTtsRate,
                        onTtsSleep = onTtsSleep,
                    )
                }
            }

            // ---------- 段评面板：点段末气泡打开 ----------
            // 遮罩和面板都用阅读底色，夜色阅读下不会弹出一块刺眼的白板。
            AnimatedVisibility(
                visible = paragraphCommentOpen,
                enter = fadeIn(),
                exit = fadeOut(),
            ) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.4f))
                        .pointerInput(Unit) {
                            detectTapGestures { onCloseParagraphComments() }
                        },
                )
            }
            AnimatedVisibility(
                visible = paragraphCommentOpen,
                enter = slideInVertically { it },
                exit = slideOutVertically { it },
                modifier = Modifier.align(Alignment.BottomCenter),
            ) {
                ParagraphCommentSheet(
                    index = paragraphCommentIndex,
                    comments = paragraphComments,
                    total = paragraphCommentTotal,
                    loading = paragraphCommentsLoading,
                    error = paragraphCommentError,
                    hasMore = paragraphCommentsHasMore,
                    palette = palette,
                    onMore = onMoreParagraphComments,
                    onClose = onCloseParagraphComments,
                    onToggleDigg = onToggleParagraphCommentDigg,
                )
            }
        }
    }

    markTarget?.let { (chapterId, index, text) ->
        // 对话框也跟随阅读底色，否则夜色阅读下会弹出一块刺眼的白板
        MaterialTheme(
            colorScheme = MaterialTheme.colorScheme.copy(
                surface = palette.background,
                surfaceContainerHigh = palette.background,
                onSurface = palette.text,
                onSurfaceVariant = palette.dim,
                primary = palette.accent,
            ),
        ) {
            AlertDialog(
                onDismissRequest = { markTarget = null },
                title = { Text("第 ${index + 1} 段", color = palette.title) },
                text = { Text(text.take(80), fontSize = 13.sp, color = palette.text) },
                confirmButton = {
                    TextButton(onClick = {
                        onToggleMark(chapterId, index, text, Mark.KIND_HIGHLIGHT)
                        markTarget = null
                    }) { Text("高亮", color = palette.accent) }
                },
                dismissButton = {
                    TextButton(onClick = {
                        onToggleMark(chapterId, index, text, Mark.KIND_BOOKMARK)
                        markTarget = null
                    }) { Text("加书签", color = palette.accent) }
                },
            )
        }
    }
}

/* ================================================================== *
 * 正文
 * ================================================================== */

@Composable
private fun ReaderPlaceholder(
    loading: Boolean,
    error: String,
    palette: ReaderPalette,
    onRetry: () -> Unit,
) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            if (loading) {
                CircularProgressIndicator(
                    color = palette.accent,
                    strokeWidth = 2.dp,
                    modifier = Modifier.size(22.dp),
                )
                Text(
                    "正在取正文…",
                    fontSize = 13.sp,
                    color = palette.dim,
                    modifier = Modifier.padding(top = Space.md),
                )
            } else {
                Text(
                    text = error.ifEmpty { "正文未加载" },
                    color = if (error.isNotEmpty()) palette.accent else palette.dim,
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = Space.huge),
                )
                TextButton(onClick = onRetry) {
                    Text("重试", fontSize = 14.sp, color = palette.accent)
                }
            }
        }
    }
}

@Composable
private fun ReaderEmpty(palette: ReaderPalette, onRetry: () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                "本章暂无正文",
                fontSize = 14.sp,
                color = palette.dim,
            )
            TextButton(onClick = onRetry) {
                Text("重试", fontSize = 14.sp, color = palette.accent)
            }
        }
    }
}

/**
 * 正文字体：四种都随 APK 一起打包（`res/font/`），不再依赖系统字体族或在线字体。
 *
 * 旧实现走系统族名（`FontFamily.Serif`、`Typeface.create("mitype")`）：MIUI 之外的机型没有
 * 「兰亭」，很多 ROM 也没有中文衬线，选完和「默认」长得一模一样。字体随包走以后，
 * 换机、断网、ROM 差异都不影响渲染。
 * 字体只用 GB2312 全字集子集化过（约 7600 字），超出范围的生僻字由系统字体兜底。
 */
private fun readerFontFamily(key: String): FontFamily = when (key) {
    "serif" -> FontFamily(Font(R.font.reader_serif))
    "sans" -> FontFamily(Font(R.font.reader_sans))
    "mono" -> FontFamily(Font(R.font.reader_mono))
    "kai" -> FontFamily(Font(R.font.reader_kai))
    else -> FontFamily.Default
}

/** 命中片段涂背景；当前命中加深，同章其余命中淡一点。 */
private fun AnnotatedString.Builder.appendHighlighted(
    text: String,
    query: String,
    palette: ReaderPalette,
    active: Boolean,
) {
    val ranges = FindHighlight.ranges(text, query)
    if (ranges.isEmpty()) {
        append(text)
        return
    }
    val bg = palette.accent.copy(alpha = if (active) 0.42f else 0.20f)
    var at = 0
    for (r in ranges) {
        if (r.first > at) append(text, at, r.first)
        withStyle(SpanStyle(background = bg, color = palette.title)) {
            append(text, r.first, r.last + 1)
        }
        at = r.last + 1
    }
    if (at < text.length) append(text, at, text.length)
}

@Composable
private fun ReaderBody(
    flow: List<ChapterContent>,
    chapters: List<Chapter>,
    bookTitle: String,
    prefs: ReaderPrefs,
    palette: ReaderPalette,
    listState: LazyGridState,
    findQuery: String,
    findActiveChapterId: String,
    findActiveParagraph: Int,
    bubblesByChapter: Map<String, List<ParagraphBubble>>,
    marksByChapter: Map<String, List<Mark>>,
    ttsChapterId: String,
    ttsParagraph: Int,
    flowLoading: Boolean,
    flowError: String,
    onRetryAppend: () -> Unit,
    onCacheNext: () -> Unit,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onSelect: (String) -> Unit,
    onTapCenter: () -> Unit,
    onLongPress: (chapterId: String, paragraphIndex: Int, text: String) -> Unit,
    /** 点段末气泡：打开该段的段评面板。 */
    onOpenParagraphComments: (chapterId: String, paragraphIndex: Int) -> Unit,
    /** 读者动手了：自动滚动据此暂停，页内其它「被动滚动」逻辑不受影响。 */
    onUserInteraction: () -> Unit = {},
) {
    // 量气泡尺寸用：占位符的宽高只能是 sp/em，dp 尺寸得先量成像素再换算
    val textMeasurer = rememberTextMeasurer()
    val pageScope = rememberCoroutineScope()
    // 段间距按行高的倍数换算，系统字体放大时同步变大
    val paragraphGap = with(LocalDensity.current) {
        (prefs.fontSize * prefs.lineHeight * prefs.paragraphGap).sp.toDp()
    }
    // 段首缩进两个汉字：中文版式的默认排法，可在设置里关掉
    val bodyStyle = remember(prefs.indent) {
        TextStyle(textIndent = if (prefs.indent) TextIndent(firstLine = 2.em) else TextIndent.None)
    }
    // 导航栏有多高，正文底部就要多留多少，末段才滚得上来
    val navBarBottom = with(LocalDensity.current) {
        WindowInsets.navigationBars.getBottom(this).toDp()
    }

    // 朗读跟随：每读一段滚到该段
    LaunchedEffect(ttsChapterId, ttsParagraph) {
        if (ttsParagraph >= 0 && ttsChapterId.isNotEmpty()) {
            val k = flow.indexOfFirst { it.id == ttsChapterId }
            if (k >= 0) {
                val idx = ReaderFlow.indexOf(flow, k, ttsParagraph)
                listState.animateScrollToItem(idx)
            }
        }
    }

    // 仿真翻页进度：0 = 静止，0.5 = 旧页掀到一半（此刻换页），1 = 新页落回
    val simTurn = remember { Animatable(0f) }
    // 翻页方向：1 = 往前，-1 = 往回
    var turnDir by remember { mutableStateOf(1) }
    // 滑动翻页的横向位移（px）：拖拽时跟手，翻页时做进出场动画
    var slideX by remember { mutableFloatStateOf(0f) }
    // 驱动 slideX 的动画器：pointerInput 的受限作用域不能挂起，动画一律从这里发起
    val slideAnim = remember { Animatable(0f) }

    /** 把 slideX 从 [from] 动画到 [to]。 */
    suspend fun animateSlide(from: Float, to: Float, durMs: Int) {
        slideAnim.snapTo(from)
        slideAnim.animateTo(to, animationSpec = tween(durMs, easing = FastOutSlowInEasing)) {
            slideX = value
        }
    }

    /**
     * 落点：新页页首顶在内容区顶端。
     *
     * 三种方式只差过渡动画，落在哪一页都一样。
     * - `none` 直接切
     * - `slide` 旧页横向移出 → 换页 → 新页从另一侧移入
     * - `simulate` 旧页绕远侧边缘掀起（转 + 淡出）→ 换页 → 新页绕近侧边缘落回
     */
    suspend fun landOn(index: Int, dir: Int) {
        when (prefs.turnMode) {
            "none" -> listState.scrollToItem(index)
            "simulate" -> {
                turnDir = dir
                simTurn.animateTo(0.5f, tween(120, easing = FastOutSlowInEasing))
                listState.scrollToItem(index)
                simTurn.animateTo(1f, tween(130, easing = FastOutSlowInEasing))
                simTurn.snapTo(0f)
            }

            "slide" -> {
                val w = listState.layoutInfo.viewportSize.width.toFloat().coerceAtLeast(1f)
                animateSlide(slideX, -dir * w, 120)
                listState.scrollToItem(index)
                slideX = dir * w
                animateSlide(dir * w, 0f, 130)
            }

            else -> listState.animateScrollToItem(index)
        }
    }

    /** 页内补滚，只在「首段比一屏还高」这种推不动的情况下才会用到。 */
    suspend fun nudgeBy(px: Float) {
        if (prefs.turnMode == "none") listState.scrollBy(px)
        else listState.animateScrollBy(px)
    }

    /**
     * 翻页统一入口：点按两侧、横向拖拽与音量键都走这里，三态差异只体现在动画上。
     *
     * 前进落到「最后一个顶部仍在屏底之上的可见段」的段首；退页按可见段的典型高度
     * 回推一屏，落在段落起点。两种落点都不切在段落中间。
     */
    suspend fun turnPage(dir: Int) {
        val info = listState.layoutInfo
        val visible = info.visibleItemsInfo.map { TurnItem(it.index, it.offset.y, it.size.height) }
        if (dir > 0) {
            val plan = ReaderTurn.forward(
                visible = visible,
                fold = info.viewportEndOffset - info.afterContentPadding,
                firstIndex = listState.firstVisibleItemIndex,
            )
            when {
                plan != null -> landOn(plan.index, dir)
                listState.canScrollForward -> nudgeBy(listState.pagePx())
                else -> onNext()
            }
        } else {
            if (!listState.canScrollBackward) {
                onPrev()
                return
            }
            landOn(
                ReaderTurn.backTarget(visible, listState.firstVisibleItemIndex, info.viewportSize.height),
                dir,
            )
        }
    }

    val haptic = rememberHaptic()
    /** 翻页触感：可在设置里关掉；点按、滑动、音量键三种入口共用。 */
    fun turnHaptic() {
        if (prefs.hapticTurn) haptic.light()
    }

/**
     * 滑动松手：越过阈值翻一页，没过就弹回原位。
     *
     * 放在 pointerInput 之外是因为那里的协程作用域受限，挂起函数调不动。
     */
    fun onSwipeRelease(offset: Float, width: Float) {
        val dir = if (offset < 0f) 1 else -1
        pageScope.launch {
            if (kotlin.math.abs(offset) > width * 0.18f) {
                turnHaptic()
                turnPage(dir)
            } else {
                animateSlide(offset, 0f, 180)
            }
        }
    }

    // 音量键翻页：Activity 捕获按键后经 ReaderPager 转发过来
    LaunchedEffect(prefs.volumeTurn, prefs.turnMode) {
        if (!prefs.volumeTurn) return@LaunchedEffect
        ReaderPager.commands.collect { dir ->
            turnHaptic()
            turnPage(dir)
        }
    }

    /**
     * 正文手势只有这一处：点按按 x 分三段，长按按 y 反查段落下标。
     *
     * 不用逐段 `combinedClickable`：它会把 down 事件先消费掉，
     * 外层用于「切 chrome / 翻页」的手势就再也收不到点击——正文中部会变成死区。
     * 这里统一挂在列表上，长按位置经 `layoutInfo` 换算成段落，行为才一致。
     *
     * 「滑动」模式下额外识别横向拖拽：跟手位移用 [slideShift] 表示，
     * 越过阈值松手才翻页，否则回弹——点按仍走 x 分三段的旧逻辑。
     */
    val gestures = Modifier.pointerInput(prefs.tapTurn, prefs.turnMode, flow) {
        detectTapGestures(
            onTap = { offset ->
                val w = size.width.toFloat()
                val edge = w * (1f - ReaderMetrics.centerTapRatio) / 2f
                when {
                    // 到头/尾时 turnPage 自己会去翻章，这里只管把方向传下去
                    offset.x < edge -> when {
                        !prefs.tapTurn -> onTapCenter()
                        else -> {
                            onUserInteraction()
                            turnHaptic()
                            pageScope.launch { turnPage(-1) }
                        }
                    }

                    offset.x > w - edge -> when {
                        !prefs.tapTurn -> onTapCenter()
                        else -> {
                            onUserInteraction()
                            turnHaptic()
                            pageScope.launch { turnPage(1) }
                        }
                    }

                    else -> onTapCenter()
                }
            },
            onLongPress = { offset ->
                val item = listState.layoutInfo.visibleItemsInfo.firstOrNull {
                    offset.y >= it.offset.y && offset.y < it.offset.y + it.size.height
                }
                val index = item?.index ?: listState.firstVisibleItemIndex
                val (k, p) = ReaderFlow.locate(flow, index)
                val chapter = flow.getOrNull(k)
                val text = chapter?.paragraphs?.getOrNull(p).orEmpty()
                if (chapter != null && text.isNotEmpty()) {
                    onUserInteraction()
                    haptic.medium()
                    onLongPress(chapter.id, p, text)
                }
            },
        )
    }

    // 「滑动」模式的横向拖拽：仅在 turnMode == "slide" 时挂上
    val swipeModifier = if (prefs.turnMode == "slide") {
        Modifier.pointerInput(flow, prefs.turnMode) {
            val slop = viewConfiguration.touchSlop
            awaitEachGesture {
                // 整段手势都在 Initial 阶段接：LazyColumn 自带的竖向滚动是内层修饰符，
                // Main 阶段才轮到它。等它先滚起来，正文已经竖着挪了一点，再叠上横向位移，
                // 看着就是抖。这里抢在它前面把事件吃掉，它就滚不动了。
                val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                var dragging = false
                var originX = down.position.x
                var total = 0f
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                    if (!change.pressed) break
                    if (!dragging) {
                        val dx = change.position.x - down.position.x
                        val dy = change.position.y - down.position.y
                        // 还没到判定线，继续观察
                        if (abs(dx) < slop && abs(dy) < slop) continue
                        // 竖向意图明确，整段手势让给 LazyColumn
                        if (abs(dy) >= abs(dx)) break
                        // 横向意图：从此刻起跟手，位移从 0 重新算，接管瞬间不跳
                        dragging = true
                        originX = change.position.x
                        total = 0f
                    }
                    total += change.position.x - originX
                    change.consume()
                    slideX = total
                }
                if (dragging) onSwipeRelease(total, size.width.toFloat())
            }
        }
    } else {
        Modifier
    }

    val bodyFont = remember(prefs.font) { readerFontFamily(prefs.font) }

    // 宽屏双栏：平板/横屏下把一屏切成两列，段落按行优先顺序流下去。
    // 不用分页器：按高度切割必然切断段落，而这段文字的排版是「一段一块」，
    // 用两列网格既能双栏又保证每段完整落在同一格子里。
    // 条漫逐图铺满，不分栏。
    val wide = LocalConfiguration.current.screenWidthDp >= 720 && flow.none { it.isManga }

    LazyVerticalGrid(
        columns = GridCells.Fixed(if (wide) 2 else 1),
        state = listState,
        horizontalArrangement = Arrangement.spacedBy(if (wide) prefs.margin.dp * 1.6f else 0.dp),
        modifier = Modifier
            .fillMaxSize()
            // 仿真翻页时整页横向让开一点，再回弹；其余模式恒为 0
            .graphicsLayer {
                when (prefs.turnMode) {
                    "simulate" -> {
                        // p ≤ 0.5 旧页掀起，p > 0.5 新页落回；fold 是「掀开程度」0..1
                        val p = simTurn.value
                        val fold = if (p <= 0.5f) p * 2f else (1f - p) * 2f
                        // 往前翻：书脊在左，右边缘朝读者掀起；往回翻则左右互换
                        val hinge = if (turnDir > 0) 0f else 1f
                        transformOrigin = TransformOrigin(hinge, 0.5f)
                        rotationY = if (turnDir > 0) -92f * fold else 92f * fold
                        translationX = 0f
                        alpha = (1f - fold * 0.6f).coerceIn(0.25f, 1f)
                        cameraDistance = 12f * density
                    }

                    "slide" -> {
                        // 取整到整像素：图层偏移带小数时，文字会被双线性重采样，
                        // 拖动过程中笔画边缘一直在换 → 看着就是抖。
                        translationX = slideX.roundToInt().toFloat()
                        alpha = 1f
                    }

                    else -> {
                        translationX = 0f
                        alpha = 1f
                    }
                }
            }
            .then(gestures)
            .then(swipeModifier),
        contentPadding = PaddingValues(
            // 页边距可在设置里调窄/加宽：同一档字号，6.7 寸屏每行能多挤两三个字
            start = prefs.margin.dp,
            end = prefs.margin.dp,
            top = Space.sm,
            // 底部只留导航栏那一档：右下角那个百分比小字浮在导航栏之上的角上，
            // 正文末行留出的余量已经够它站。
            bottom = Space.lg + navBarBottom,
        ),
    ) {
        flow.forEachIndexed { k, chapter ->
            val bubbles = bubblesByChapter[chapter.id].orEmpty()
            val bubbleByIndex = bubbles.associateBy { it.index }
            val marks = marksByChapter[chapter.id].orEmpty()
            val marksByIndex = marks.groupBy { it.paragraph }
            // 页眉只印书名。早先这里把章名也拼了进去，而下面紧接着就是同一章的
            // 大标题，同一行字一屏里连出两次，看着像是标题和页眉粘在了一起。
            val header = bookTitle.takeIf { it.isNotBlank() }.orEmpty()

            item(key = "chapter-header-${chapter.id}", span = { GridItemSpan(maxLineSpan) }) {
                // 页眉画在标题项内部：不进索引，滚走即隐藏。
                // 必须包 Column：grid item 默认叠放，不包则页眉与章名重合。
                Column(Modifier.fillMaxWidth()) {
                    if (header.isNotEmpty()) {
                        Text(
                            text = header,
                            fontSize = 11.sp,
                            lineHeight = 16.sp,
                            letterSpacing = 0.08.em,
                            color = palette.dim,
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = Space.xs, bottom = Space.lg),
                        )
                    } else {
                        // 没有书名可印时不占那行留白，直接让章名顶上来
                        Spacer(Modifier.height(Space.md))
                    }
                    Text(
                        text = chapter.title,
                        fontFamily = Serif,
                        fontWeight = FontWeight.Bold,
                        fontSize = (prefs.fontSize + 4).sp,
                        lineHeight = ((prefs.fontSize + 4) * 1.45f).sp,
                        color = palette.title,
                    )
                    Spacer(Modifier.height(Space.md))
                    Box(
                        Modifier
                            .width(28.dp)
                            .height(2.dp)
                            .background(palette.rule),
                    )
                    Spacer(Modifier.height(Space.lg))
                }
            }

            if (chapter.isManga) {
                itemsIndexed(
                    items = chapter.images,
                    key = { imgIdx, _ -> "img-${chapter.id}-$imgIdx" },
                ) { imgIdx, imgPath ->
                    MangaImageItem(
                        imgPath = imgPath,
                        palette = palette,
                        index = imgIdx,
                        total = chapter.images.size,
                    )
                }
            } else {
                itemsIndexed(
                    items = chapter.paragraphs,
                    key = { p, _ -> "p-${chapter.id}-$p" },
                ) { p, text ->
                    val marksHere = marksByIndex[p].orEmpty()
                val highlighted = marksHere.any { it.kind == Mark.KIND_HIGHLIGHT }
                val bookmarkedHere = marksHere.any { it.kind == Mark.KIND_BOOKMARK }
                val speakingNow = chapter.id == ttsChapterId && p == ttsParagraph
                val hitActive = chapter.id == findActiveChapterId && p == findActiveParagraph
                val bubble = bubbleByIndex[p]
                val fill = when {
                    speakingNow -> palette.accent.copy(alpha = 0.16f)
                    highlighted -> palette.highlight
                    else -> Color.Transparent
                }

                Row(
                    Modifier
                        .fillMaxWidth()
                        .background(fill)
                        .padding(vertical = Space.xs),
                    verticalAlignment = Alignment.Top,
                ) {
                    Column(Modifier.weight(1f)) {
                        if (bookmarkedHere) {
                            Icon(
                                Icons.Filled.Bookmark,
                                contentDescription = "已加书签",
                                tint = palette.accent,
                                modifier = Modifier.size(12.dp),
                            )
                            Spacer(Modifier.height(Space.xxs))
                        }
                        if (bubble == null) {
                            Text(
                                text = buildAnnotatedString {
                                    appendHighlighted(text, findQuery, palette, hitActive)
                                },
                                fontFamily = bodyFont,
                                fontSize = prefs.fontSize.sp,
                                lineHeight = (prefs.fontSize * prefs.lineHeight).sp,
                                letterSpacing = prefs.letterSpacing.em,
                                style = bodyStyle,
                                color = palette.text,
                            )
                        } else {
                            // 段评气泡内联在段末。气泡是 inline content：占位符只负责在
                            // 行末留出一块矩形，真正画出来的是 children 里的气泡本体
                            // （Compose 的 ReplacementSpan 不绘制占位文本，alternateText
                            // 只给读屏用）。
                            //
                            // 占位符的宽高必须用 sp 或 em（dp 会抛 IllegalArgumentException），
                            // 所以这里先用 TextMeasurer 量出气泡的实际像素尺寸，再换算回 sp。
                            val bubbleId = "paragraph-bubble-${chapter.id}-$p"
                            Text(
                                text = buildAnnotatedString {
                                    appendHighlighted(text, findQuery, palette, hitActive)
                                    append(" ")
                                    appendInlineContent(bubbleId, "段评")
                                },
                                fontFamily = bodyFont,
                                fontSize = prefs.fontSize.sp,
                                lineHeight = (prefs.fontSize * prefs.lineHeight).sp,
                                letterSpacing = prefs.letterSpacing.em,
                                style = bodyStyle,
                                color = palette.text,
                                inlineContent = mapOf(
                                    bubbleId to InlineTextContent(
                                        placeholder = rememberBubblePlaceholder(bubble, textMeasurer),
                                        children = {
                                            InlineBubble(
                                                bubble = bubble,
                                                palette = palette,
                                                onClick = { onOpenParagraphComments(chapter.id, p) },
                                            )
                                        },
                                    ),
                                ),
                            )
                        }
                    }
                }
                // 段间空行：保留按「段」组织的阅读节奏
                Spacer(Modifier.height(paragraphGap))
            }
            }

            // 章末卡片：连续流里两章之间唯一的分界标记。
            // 之前章与章直接首尾相接，滚过去只看到下一章标题，没有「读完了」的停顿。
            val chapterIndex = chapters.indexOfFirst { it.id == chapter.id }
            val nextInList = chapters.getOrNull(chapterIndex + 1)
            val nextInFlow = flow.getOrNull(k + 1)
            item(key = "chapter-end-${chapter.id}", span = { GridItemSpan(maxLineSpan) }) {
                ChapterEndCard(
                    nextTitle = nextInList?.title ?: nextInFlow?.title.orEmpty(),
                    palette = palette,
                    onNext = {
                        when {
                            nextInFlow != null -> onSelect(nextInFlow.id)
                            nextInList != null -> onSelect(nextInList.id)
                            else -> onRetryAppend()
                        }
                    },
                )
            }
        }

        // 底部：加载中提示、失败重试、或手动上一章/下一章
        if (flowLoading) {
            item(key = "flow-loading", span = { GridItemSpan(maxLineSpan) }) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = Space.lg),
                    contentAlignment = Alignment.Center,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(
                            color = palette.accent,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(Modifier.width(Space.sm))
                        Text("正在加载下一章…", fontSize = 13.sp, color = palette.dim)
                    }
                }
            }
        } else if (flowError.isNotBlank()) {
            item(key = "flow-error", span = { GridItemSpan(maxLineSpan) }) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = Space.lg),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(flowError, fontSize = 13.sp, color = palette.dim)
                    Spacer(Modifier.height(Space.xs))
                    Row(horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                        TextButton(onClick = onRetryAppend) {
                            Text("点击重试", fontSize = 13.sp, color = palette.accent)
                        }
                        // 断网时重试大概率还是失败；先落盘后 5 章再续接，才是真正能走下去的动作
                        TextButton(onClick = onCacheNext) {
                            Text("缓存后 5 章", fontSize = 13.sp, color = palette.accent)
                        }
                    }
                }
            }
        }

        item(key = "flow-footer", span = { GridItemSpan(maxLineSpan) }) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = Space.xxl),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                TextButton(onClick = onPrev) {
                    Text("上一章", fontSize = 13.sp, color = palette.text)
                }
                TextButton(onClick = onNext) {
                    Text("下一章", fontSize = 13.sp, color = palette.text)
                }
            }
        }
    }
}

/**
 * 章末卡片：`本章完` + 下一章标题 + 一个「下一章」按钮。
 *
 * 连续阅读把多章接成一条流，章末没有任何停顿，读者分不清「这章读完了」
 * 还是「作者断更了」；这张卡就是那条分界线，同时给一个不依赖手势的跳章入口。
 */
@Composable
private fun ChapterEndCard(
    nextTitle: String,
    palette: ReaderPalette,
    onNext: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(top = Space.xxl, bottom = Space.md),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .width(28.dp)
                .height(1.dp)
                .background(palette.rule),
        )
        Text(
            text = "本章完",
            fontSize = 11.sp,
            lineHeight = 16.sp,
            letterSpacing = 0.32.em,
            color = palette.dim,
            modifier = Modifier.padding(top = Space.md),
        )
        if (nextTitle.isNotBlank()) {
            Text(
                text = nextTitle,
                fontFamily = Serif,
                fontSize = 15.sp,
                lineHeight = 22.sp,
                color = palette.title,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = Space.xs, start = Space.lg, end = Space.lg),
                textAlign = TextAlign.Center,
            )
        }
        TextButton(onClick = onNext) {
            Text("下一章", fontSize = 13.sp, color = palette.accent)
        }
    }
}

/** 条漫模式图片项：自适应填满屏幕宽度、按原图比例自适应高度、右下角显示轻量半透明页码。 */
@Composable
private fun MangaImageItem(
    imgPath: String,
    palette: ReaderPalette,
    index: Int,
    total: Int,
) {
    val context = LocalContext.current
    val model = remember(imgPath) {
        if (imgPath.startsWith("/") || imgPath.startsWith("file://")) {
            File(imgPath.removePrefix("file://"))
        } else {
            imgPath
        }
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 1.dp)
            .background(palette.background),
        contentAlignment = Alignment.Center,
    ) {
        AsyncImage(
            model = ImageRequest.Builder(context)
                .data(model)
                .crossfade(true)
                .build(),
            contentDescription = "第 ${index + 1} 页",
            contentScale = ContentScale.FillWidth,
            modifier = Modifier.fillMaxWidth(),
        )
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(Space.xs)
                .clip(RoundedCornerShape(4.dp))
                .background(Color.Black.copy(alpha = 0.55f))
                .padding(horizontal = 6.dp, vertical = 2.dp),
        ) {
            Text(
                text = "${index + 1} / $total",
                fontSize = 10.sp,
                color = Color.White,
            )
        }
    }
}

/** 漫画翻页阅读器：左右横滑切页、轻点中心唤出菜单、底部常显章名与页码。 */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun MangaPagedReader(
    chapter: ChapterContent,
    palette: ReaderPalette,
    onTapCenter: () -> Unit,
    onPrevChapter: () -> Unit,
    onNextChapter: () -> Unit,
) {
    val images = chapter.images
    if (images.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("本章暂无图片", color = palette.dim)
        }
        return
    }
    val pagerState = rememberPagerState(pageCount = { images.size })
    val context = LocalContext.current

    Box(
        Modifier
            .fillMaxSize()
            .background(palette.background)
            .pointerInput(Unit) {
                detectTapGestures(onTap = { onTapCenter() })
            }
    ) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
        ) { page ->
            val imgPath = images[page]
            val model = remember(imgPath) {
                if (imgPath.startsWith("/") || imgPath.startsWith("file://")) {
                    File(imgPath.removePrefix("file://"))
                } else {
                    imgPath
                }
            }
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(model)
                        .crossfade(true)
                        .build(),
                    contentDescription = "第 ${page + 1} 页",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }

        // 底部居中半透明页码条与换章快捷入口
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = Space.md)
                .clip(RoundedCornerShape(Radius.sm))
                .background(Color.Black.copy(alpha = 0.65f))
                .padding(horizontal = Space.sm, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (pagerState.currentPage == 0) {
                Text(
                    text = "上一章",
                    fontSize = 11.sp,
                    color = Color.LightGray,
                    modifier = Modifier
                        .clickable(onClick = onPrevChapter)
                        .padding(horizontal = Space.xs, vertical = Space.xs),
                )
                Spacer(Modifier.width(Space.xs))
            }
            Text(
                text = "${chapter.title}  ${pagerState.currentPage + 1} / ${images.size}",
                fontSize = 11.sp,
                color = Color.White,
                modifier = Modifier.padding(horizontal = Space.xs),
            )
            if (pagerState.currentPage == images.size - 1) {
                Spacer(Modifier.width(Space.xs))
                Text(
                    text = "下一章",
                    fontSize = 11.sp,
                    color = Color.LightGray,
                    modifier = Modifier
                        .clickable(onClick = onNextChapter)
                        .padding(horizontal = Space.xs, vertical = Space.xs),
                )
            }
        }
    }
}

/* ================================================================== *
 * 段末气泡
 * ================================================================== */

/** 气泡图标尺寸。 */
private val BubbleIconSize = 11.dp

/** 图标与条数之间的间距。 */
private val BubbleIconGap = 3.dp

/** 气泡左右内边距。 */
private val BubblePaddingH = 5.dp

/**
 * 气泡上下内边距。
 *
 * 气泡是 inline content，占位符的高度会计进所在行的行高。留太多会把整段撑高，
 * 只留 1dp：气泡高度仍小于正文字体的 ascent+descent，行高基本不受影响。
 */
private val BubblePaddingV = 1.dp

/**
 * 气泡里条数文字的样式。
 *
 * 占位符测量与实际渲染必须用**同一个**样式，否则占位出来的矩形和气泡实际尺寸对不上，
 * 气泡会被裁掉。字体也要显式指定：渲染时 material3 的 Text 会把样式合进来，
 * 而测量用的是纯样式，两边字体不一致就会差出几个像素。
 */
private val BubbleCountStyle = TextStyle(
    fontFamily = Sans,
    fontSize = 11.sp,
    lineHeight = 13.sp,
)

/**
 * 段末的段评气泡：可点，点开该段的短评。
 *
 * 旧实现把它排在正文右缘的一条固定轨道上（整章留 22dp），既挤了正文宽度，
 * 又让人误以为那是一条装饰线。现在内联到段末，跟着句子走。
 *
 * `hot` 用强调色、其余用次要色；`authorCommented` 用实心气泡图标区分。
 * 文字与颜色双通道，读屏能拿到「段评 N 条，作者参与」。
 */
@Composable
private fun InlineBubble(
    bubble: ParagraphBubble,
    palette: ReaderPalette,
    onClick: () -> Unit,
) {
    val tint = if (bubble.hot) palette.accent else palette.dim
    val fill = if (bubble.hot) palette.accent.copy(alpha = 0.12f) else palette.rule.copy(alpha = 0.7f)
    val desc = buildString {
        append("段评 ${bubble.count} 条")
        if (bubble.authorCommented) append("，作者参与")
        append("，点开查看")
    }
    Row(
        Modifier
            // 撑满占位符留出的矩形：气泡的底色正好落在文字预留的那块空位上，
            // 点按范围也一并变大（Compose 的 clickable 不带最小触摸区）。
            .fillMaxSize()
            .clip(CircleShape)
            .background(fill)
            .clickable(onClick = onClick)
            .padding(horizontal = BubblePaddingH, vertical = BubblePaddingV)
            // 整个气泡读作一个节点：「段评 12 条，作者参与，点开查看」。
            // 不加 clearAndSetSemantics 的话读屏会把图标、条数、描述各念一遍。
            .clearAndSetSemantics {
                contentDescription = desc
                role = Role.Button
            },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            if (bubble.authorCommented) Icons.Filled.ChatBubble else Icons.Filled.ChatBubbleOutline,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(BubbleIconSize),
        )
        Spacer(Modifier.width(BubbleIconGap))
        Text(
            text = bubble.countLabel,
            style = BubbleCountStyle,
            color = tint,
            maxLines = 1,
        )    }
}

/** 气泡里的条数文案：过 99 就收成「99+」，免得把行末撑开。 */
private val ParagraphBubble.countLabel: String
    get() = if (count > 99) "99+" else count.toString()

/**
 * 段末气泡的占位符。
 *
 * Compose 的 inline content 用占位符在行末留出一块矩形，再把气泡画在那块矩形上。
 * 占位符的宽高**只能用 sp 或 em**（dp 会抛 IllegalArgumentException），
 * 而气泡的尺寸是 dp + 文本测量出来的——所以先量出气泡的像素尺寸，
 * 再按 `px = sp * fontScale * density` 换算回 sp。
 *
 * 换算式与 Compose 的 `PlaceholderSpan` 一致（它用 `fontScale * density` 作为 pxPerSp），
 * 所以占位出来的矩形与气泡实际尺寸对得上，气泡不会被裁掉。
 */
@Composable
private fun rememberBubblePlaceholder(
    bubble: ParagraphBubble,
    measurer: TextMeasurer,
): Placeholder {
    val density = LocalDensity.current
    val label = bubble.countLabel
    return remember(label, bubble.authorCommented, measurer, density) {
        // 气泡里的文字宽度会随条数变化，用与气泡完全相同的样式量一次
        val text = measurer.measure(label, BubbleCountStyle, maxLines = 1)
        val iconPx = with(density) { BubbleIconSize.toPx() }
        val gapPx = with(density) { BubbleIconGap.toPx() }
        val padHPx = with(density) { BubblePaddingH.toPx() }
        val padVPx = with(density) { BubblePaddingV.toPx() }
        val widthPx = text.size.width + iconPx + gapPx + padHPx * 2
        val heightPx = max(text.size.height.toFloat(), iconPx) + padVPx * 2
        Placeholder(
            // 多留 1px：占位符的取整（ceilToInt）与这里的换算可能差一点点，
            // 不留余量的话气泡的圆角会被裁掉一像素。
            width = (widthPx + 1f).pxToSp(density),
            height = (heightPx + 1f).pxToSp(density),
            placeholderVerticalAlign = PlaceholderVerticalAlign.Center,
        )
    }
}

/**
 * 像素 → 占位符用的 sp。
 *
 * `PlaceholderSpan` 用 `fontScale * density` 作为「1 sp 等于多少像素」，
 * 换算必须与它一致，否则占位出来的矩形和气泡实际尺寸对不上，气泡会被裁掉。
 */
private fun Float.pxToSp(density: Density): TextUnit =
    (this / (density.fontScale * density.density)).sp

/* ================================================================== *
 * 段评面板
 * ================================================================== */

/**
 * 段评面板：某一段的短评列表。
 *
 * 失败只显示在面板里，**不碰正文**——读者正在看书，一条评论读不出来
 * 不该打断阅读。加载失败时正文照常可读、面板照常可关。
 */
@Composable
private fun ParagraphCommentSheet(
    index: Int,
    comments: List<BookHotReview>,
    total: Int,
    loading: Boolean,
    error: String,
    hasMore: Boolean,
    palette: ReaderPalette,
    onMore: () -> Unit,
    onClose: () -> Unit,
    onToggleDigg: (String) -> Unit = {},
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(topStart = Radius.xl, topEnd = Radius.xl),
        color = palette.background,
        shadowElevation = Elevation.raised,
    ) {
        Column(
            Modifier
                // 面板最多占屏幕 60%，剩下的留给正文，读者随时能看到自己评的是哪一段
                .fillMaxHeight(0.6f)
                .navigationBarsPadding(),
        ) {            // 拖拽条
            Box(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = Space.sm),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    Modifier
                        .width(32.dp)
                        .height(4.dp)
                        .clip(CircleShape)
                        .background(palette.rule),
                )
            }
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Space.gutter, vertical = Space.xs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = "第 ${index + 1} 段 · 段评",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = palette.title,
                    )
                    if (total > 0) {
                        Text(
                            text = "共 $total 条",
                            fontSize = 11.sp,
                            lineHeight = 15.sp,
                            color = palette.dim,
                        )
                    }
                }
                IconButton(onClick = onClose) {
                    Icon(
                        Icons.Filled.Close,
                        contentDescription = "关闭段评",
                        tint = palette.dim,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(palette.rule),
            )

            when {
                // 错误优先于空态：读不到评论要说清楚原因，不能只给一句「暂无」
                error.isNotEmpty() -> Box(
                    Modifier
                        .fillMaxWidth()
                        .padding(Space.xxl),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = error,
                        fontSize = 13.sp,
                        color = palette.accent,
                        textAlign = TextAlign.Center,
                    )
                }

                comments.isEmpty() && loading -> Box(
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = Space.huge),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(
                        color = palette.accent,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(22.dp),
                    )
                }

                comments.isEmpty() -> Box(
                    Modifier
                        .fillMaxWidth()
                        .padding(Space.xxl),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "这一段还没有段评",
                        fontSize = 13.sp,
                        color = palette.dim,
                    )
                }

                else -> Column(
                    Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                ) {
                    comments.forEach { comment ->
                        ParagraphCommentRow(
                            comment = comment,
                            palette = palette,
                            onToggleDigg = { onToggleDigg(comment.id) },
                        )
                    }
                    if (hasMore) {
                        TextButton(
                            onClick = onMore,
                            enabled = !loading,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = Space.xs),
                        ) {
                            Text(
                                text = if (loading) "加载中…" else "加载更多",
                                fontSize = 13.sp,
                                color = palette.accent,
                            )
                        }
                    }
                }
            }
        }
    }
}

/** 一条段评：头像 + 用户名 + 时间 + 正文 + 点赞/回复数。 */
@Composable
private fun ParagraphCommentRow(
    comment: BookHotReview,
    palette: ReaderPalette,
    onToggleDigg: () -> Unit = {},
) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = Space.gutter, vertical = Space.md),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ParagraphCommentAvatar(url = comment.avatar, name = comment.user, palette = palette)
            Spacer(Modifier.width(Space.sm))
            Column(Modifier.weight(1f)) {
                Text(
                    text = comment.user,
                    fontSize = 13.sp,
                    lineHeight = 17.sp,
                    color = palette.title,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val whenText = readerRelativeTime(comment.createdAt)
                if (whenText.isNotEmpty()) {
                    Text(
                        text = whenText,
                        fontSize = 11.sp,
                        lineHeight = 15.sp,
                        color = palette.dim,
                    )
                }
            }
            if (comment.score.isNotBlank()) {
                Text(
                    text = comment.score,
                    fontSize = 11.sp,
                    lineHeight = 15.sp,
                    color = palette.accent,
                )
            }
        }
        Text(
            text = comment.text,
            fontSize = 14.sp,
            lineHeight = 21.sp,
            color = palette.text,
            modifier = Modifier.padding(top = Space.xs),
        )
        Row(
            Modifier.padding(top = Space.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.clickable(onClick = onToggleDigg),
            ) {
                Icon(
                    Icons.Filled.ThumbUp,
                    contentDescription = null,
                    tint = if (comment.userLiked) palette.accent else palette.dim,
                    modifier = Modifier.size(12.dp),
                )
                Spacer(Modifier.width(3.dp))
                Text(
                    text = comment.digg.toString(),
                    fontSize = 11.sp,
                    lineHeight = 15.sp,
                    color = if (comment.userLiked) palette.accent else palette.dim,
                )
            }
            if (comment.replyCount > 0) {
                Spacer(Modifier.width(Space.sm))
                Icon(
                    Icons.Filled.ChatBubbleOutline,
                    contentDescription = null,
                    tint = palette.dim,
                    modifier = Modifier.size(12.dp),
                )
                Spacer(Modifier.width(3.dp))
                Text(
                    text = comment.replyCount.toString(),
                    fontSize = 11.sp,
                    lineHeight = 15.sp,
                    color = palette.dim,
                )
            }
        }
    }
    Box(
        Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(palette.rule),
    )
}

/** 段评头像：圆形；没有头像就用用户名首字。 */
@Composable
private fun ParagraphCommentAvatar(url: String, name: String, palette: ReaderPalette) {
    Box(
        Modifier
            .size(28.dp)
            .clip(CircleShape)
            // 头像底色用发丝线的深一档：跟着阅读底色走，夜色下不会是一块白饼
            .background(palette.rule),
        contentAlignment = Alignment.Center,
    ) {
        if (url.isNotBlank()) {
            AsyncImage(
                model = url,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Text(
                text = name.take(1),
                fontSize = 12.sp,
                color = palette.dim,
            )
        }
    }
}

/**
 * 段评时间：相对时间。
 *
 * 上游给的是**秒级**时间戳（实测 create_timestamp 形如 1783234303），
 * 口径与 [relativeTime] 一致。
 */
private fun readerRelativeTime(epochSeconds: Long): String {
    if (epochSeconds <= 0) return ""
    val minutes = (System.currentTimeMillis() / 1000 - epochSeconds) / 60
    return when {
        minutes < 1 -> "刚刚"
        minutes < 60 -> "${minutes} 分钟前"
        minutes < 60 * 24 -> "${minutes / 60} 小时前"
        minutes < 60 * 24 * 30 -> "${minutes / (60 * 24)} 天前"
        else -> "${minutes / (60 * 24 * 30)} 个月前"
    }
}

/* ================================================================== *
 * 顶栏 / 查找条
 * ================================================================== */

@Composable
private fun ReaderTopBar(
    title: String,
    bookmarked: Boolean,
    isManga: Boolean = false,
    mangaMode: String = "vertical",
    onToggleMangaMode: () -> Unit = {},
    palette: ReaderPalette,
    menuOpen: Boolean,
    onMenuOpenChange: (Boolean) -> Unit,
    onBack: () -> Unit,
    onToggleBookmark: () -> Unit,
    inShelf: Boolean = false,
    onToggleShelf: () -> Unit = {},
    onOpenDetail: () -> Unit = {},
    onToggleFind: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(palette.background.copy(alpha = 0.97f))
            .consumeTaps()
            .padding(horizontal = Space.xs, vertical = Space.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "返回",
                tint = palette.text,
                modifier = Modifier.size(22.dp),
            )
        }
        Text(
            text = title,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            color = palette.title,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = Space.sm),
        )
        if (isManga) {
            TextButton(
                onClick = onToggleMangaMode,
                contentPadding = PaddingValues(horizontal = Space.xs, vertical = 0.dp),
            ) {
                Text(
                    text = if (mangaMode == "vertical") "条漫" else "翻页",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = palette.accent,
                )
            }
        }
        IconButton(onClick = onOpenDetail) {
            Icon(
                Icons.Outlined.Info,
                contentDescription = "书籍详情",
                tint = palette.dim,
                modifier = Modifier.size(20.dp),
            )
        }
        IconButton(onClick = onToggleBookmark) {
            Icon(
                Icons.Filled.Bookmark,
                contentDescription = if (bookmarked) "取消书签" else "加书签",
                tint = if (bookmarked) palette.accent else palette.dim,
                modifier = Modifier.size(20.dp),
            )
        }
        Box {
            IconButton(onClick = { onMenuOpenChange(true) }) {
                Icon(
                    Icons.Filled.MoreVert,
                    contentDescription = "更多",
                    tint = palette.text,
                    modifier = Modifier.size(22.dp),
                )
            }
            // 菜单浮在阅读底色上：容器色也换到 palette，否则夜色阅读会弹出一块浅色面板
            MaterialTheme(
                colorScheme = MaterialTheme.colorScheme.copy(
                    surface = palette.background,
                    surfaceContainer = palette.background,
                    surfaceContainerLow = palette.background,
                    surfaceContainerHigh = palette.background,
                    onSurface = palette.text,
                    onSurfaceVariant = palette.dim,
                ),
            ) {
                DropdownMenu(
                    expanded = menuOpen,
                    onDismissRequest = { onMenuOpenChange(false) },
                ) {
                    if (isManga) {
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = if (mangaMode == "vertical") "切换为翻页模式" else "切换为条漫模式",
                                    fontSize = 13.sp,
                                    color = palette.text,
                                )
                            },
                            onClick = {
                                onMenuOpenChange(false)
                                onToggleMangaMode()
                            },
                        )
                    }
                    // 阅读页也能收书：从「发现」点开一本书直接读、读着读着想留着，
                    // 以前只能退回详情页才加得上，这里补一个入口。
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = if (inShelf) "移出书架" else "加入书架",
                                fontSize = 13.sp,
                                color = palette.text,
                            )
                        },
                        leadingIcon = {
                            Icon(
                                Icons.AutoMirrored.Filled.LibraryBooks,
                                contentDescription = null,
                                tint = palette.dim,
                                modifier = Modifier.size(18.dp),
                            )
                        },
                        onClick = {
                            onMenuOpenChange(false)
                            onToggleShelf()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("章内查找", fontSize = 13.sp, color = palette.text) },
                        leadingIcon = {
                            Icon(
                                Icons.Filled.Search,
                                contentDescription = null,
                                tint = palette.dim,
                                modifier = Modifier.size(18.dp),
                            )
                        },
                        onClick = {
                            onMenuOpenChange(false)
                            onToggleFind()
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun ReaderFindBar(
    query: String,
    hits: List<Int>,
    cursor: Int,
    palette: ReaderPalette,
    onQuery: (String) -> Unit,
    onJump: (Int) -> Unit,
    onClose: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(palette.background.copy(alpha = 0.97f))
            .consumeTaps()
            .padding(start = Space.md, end = Space.xs, top = Space.xs, bottom = Space.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OutlinedTextField(
            value = query,
            onValueChange = onQuery,
            placeholder = { Text("本章查找", fontSize = 13.sp, color = palette.dim) },
            singleLine = true,
            shape = RoundedCornerShape(Radius.sm),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = palette.text,
                unfocusedTextColor = palette.text,
                focusedBorderColor = palette.accent,
                unfocusedBorderColor = palette.rule,
                cursorColor = palette.accent,
            ),
            modifier = Modifier.weight(1f),
        )
        Text(
            text = when {
                hits.isEmpty() -> if (query.isBlank()) "0" else "无匹配"
                else -> "${cursor + 1}/${hits.size}"
            },
            fontSize = 11.sp,
            color = palette.dim,
            modifier = Modifier.padding(horizontal = Space.xs),
        )
        IconButton(onClick = { onJump(-1) }, enabled = hits.isNotEmpty()) {
            Icon(
                Icons.Filled.KeyboardArrowUp,
                contentDescription = "上一处",
                tint = if (hits.isEmpty()) palette.rule else palette.text,
            )
        }
        IconButton(onClick = { onJump(1) }, enabled = hits.isNotEmpty()) {
            Icon(
                Icons.Filled.KeyboardArrowDown,
                contentDescription = "下一处",
                tint = if (hits.isEmpty()) palette.rule else palette.text,
            )
        }
        IconButton(onClick = onClose) {
            Icon(
                Icons.Filled.Close,
                contentDescription = "关闭查找",
                tint = palette.dim,
            )
        }
    }
}

/* ================================================================== *
 * 底栏 / 进度线
 * ================================================================== */

@Composable
private fun ReaderBottomBar(
    palette: ReaderPalette,
    ttsSpeaking: Boolean,
    night: Boolean,
    onOpenToc: () -> Unit,
    onOpenSettings: () -> Unit,
    onTtsToggle: () -> Unit,
    onToggleNight: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(palette.background.copy(alpha = 0.97f))
            .consumeTaps()
            .padding(horizontal = Space.sm, vertical = Space.sm),
        horizontalArrangement = Arrangement.spacedBy(Space.xs),
    ) {
        // 文案与顺序由 ReaderMetrics.bottomActions 决定（有单测钉住）
        ReaderMetrics.bottomActions.forEach { action ->
            val active = (action == "听书" && ttsSpeaking) || (action == "夜间" && night)
            val tint = if (active) palette.accent else palette.text
            val label = if (action == "听书" && ttsSpeaking) "暂停" else action
            val icon = when (action) {
                "目录" -> Icons.Filled.Menu
                "设置" -> Icons.Filled.Settings
                "听书" -> if (ttsSpeaking) Icons.Filled.Pause else Icons.Filled.Headphones
                else -> Icons.Filled.DarkMode
            }
            val onClick = when (action) {
                "目录" -> onOpenToc
                "设置" -> onOpenSettings
                "听书" -> onTtsToggle
                else -> onToggleNight
            }
            Column(
                Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp)
                    .clip(RoundedCornerShape(Radius.sm))
                    .clickable(onClick = onClick)
                    .padding(vertical = Space.xs),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Icon(
                    icon,
                    contentDescription = label,
                    tint = tint,
                    modifier = Modifier.size(24.dp),
                )
                Text(
                    text = label,
                    fontSize = 11.sp,
                    lineHeight = 15.sp,
                    color = if (active) palette.accent else palette.dim,
                    maxLines = 1,
                )
            }
        }
    }
}

/* ================================================================== *
 * 目录抽屉
 * ================================================================== */

@Composable
private fun ReaderTocDrawer(
    chapters: List<Chapter>,
    currentId: String?,
    palette: ReaderPalette,
    onSelect: (String) -> Unit,
) {
    ModalDrawerSheet(
        drawerContainerColor = palette.background,
        drawerContentColor = palette.text,
    ) {
        Column(Modifier.fillMaxHeight()) {
            Text(
                "目录 · ${chapters.size} 章",
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = palette.title,
                modifier = Modifier.padding(
                    start = Space.gutter,
                    end = Space.gutter,
                    top = Space.lg,
                    bottom = Space.sm,
                ),
            )
            // 抽屉目录打开即定位当前章：上千章时从头滚起等于不可用。
            // 另外给一个输入框：纯数字直接滚到那一章（937 章滑到第 800 章要几十屏），
            // 输中文则回到按标题筛选。这里的输入框没有复用 CompactSearchField——
            // 它取的是 App 主题色，在夜色阅读配色下会亮成一块白板。
            var tocQuery by remember { mutableStateOf("") }
            val tocJump = chapterJumpIndex(tocQuery.trim(), chapters.size)
            val tocShown = remember(chapters, tocQuery, tocJump) {
                val q = tocQuery.trim()
                when {
                    q.isEmpty() || tocJump != null -> chapters
                    else -> chapters.filter { it.title.contains(q, ignoreCase = true) }
                }
            }
            val tocState = rememberLazyListState()
            LaunchedEffect(currentId, chapters.size, tocQuery) {
                val q = tocQuery.trim()
                val target = when {
                    q.isNotEmpty() -> tocJump ?: 0
                    else -> currentId?.let { id -> chapters.indexOfFirst { it.id == id } } ?: -1
                }
                if (target >= 0) tocState.scrollToItem(target)
            }
            TocJumpField(
                value = tocQuery,
                onValueChange = { tocQuery = it },
                palette = palette,
                modifier = Modifier.padding(
                    start = Space.gutter,
                    end = Space.gutter,
                    bottom = Space.sm,
                ),
            )
            if (tocQuery.isNotBlank() && tocJump == null && tocShown.isEmpty()) {
                Text(
                    text = "没有匹配的章节",
                    fontSize = 12.sp,
                    lineHeight = 18.sp,
                    color = palette.dim,
                    modifier = Modifier.padding(horizontal = Space.gutter, vertical = Space.sm),
                )
            }
            // state 必须接上：不接的话 scrollToItem 作用在一个没人渲染的
            // LazyListState 上，它永远等不到布局，目录打开就停在第 1 章。
            LazyColumn(state = tocState, modifier = Modifier.weight(1f)) {
                itemsIndexed(tocShown, key = { _, c -> c.id }) { _, c ->
                    val active = c.id == currentId
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .clickable { onSelect(c.id) }
                            .padding(horizontal = Space.gutter, vertical = Space.sm),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = c.title.ifEmpty { c.id },
                            fontSize = 13.sp,
                            lineHeight = 19.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = if (active) palette.accent else palette.text,
                            modifier = Modifier.weight(1f),
                        )
                        if (active) {
                            Box(
                                Modifier
                                    .width(3.dp)
                                    .height(14.dp)
                                    .background(palette.accent),
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * 目录抽屉里的输入框：纯数字当章号直达，其余当标题筛选。
 *
 * 用 [ReaderPalette] 而不是 App 主题色——夜色配色下主题色会给出一块亮白输入框。
 */
@Composable
private fun TocJumpField(
    value: String,
    onValueChange: (String) -> Unit,
    palette: ReaderPalette,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(40.dp)
            .border(1.dp, palette.rule)
            .padding(start = Space.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.weight(1f)) {
            if (value.isEmpty()) {
                Text(
                    text = "输入章号直达，或输入标题筛选",
                    fontSize = 12.sp,
                    lineHeight = 18.sp,
                    color = palette.dim,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = MaterialTheme.typography.bodySmall.copy(color = palette.text),
                cursorBrush = SolidColor(palette.accent),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        // BasicTextField 没有自带清空：输了三位章号再想筛标题得删三次，给个叉。
        if (value.isNotEmpty()) {
            IconButton(onClick = { onValueChange("") }, modifier = Modifier.size(36.dp)) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = "清空",
                    tint = palette.dim,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    }
}

/* ================================================================== *
 * 阅读设置抽屉
 * ================================================================== */
@Composable
private fun ReaderSettingsSheet(
    prefs: ReaderPrefs,
    palette: ReaderPalette,
    selectedThemeKey: String,
    ttsSpeaking: Boolean,
    ttsRate: Float,
    ttsMessage: String,
    onPrefs: (ReaderPrefs) -> Unit,
    onTtsToggle: () -> Unit,
    onTtsRate: (Float) -> Unit,
    onTtsSleep: (Int) -> Unit,
) {
    val lineValues = listOf(1.5f, 1.75f, 2.0f)
    val lineLabels = listOf("紧凑", "标准", "宽松")
    // 旧值可能是 1.6 之类：取最接近的一档做选中态，不擅自改数据
    val lineIndex = lineValues.indices.minByOrNull { abs(lineValues[it] - prefs.lineHeight) } ?: 1
    val gapValues = listOf(0.45f, 0.75f, 1.05f)
    val gapLabels = listOf("紧凑", "标准", "宽松")
    val gapIndex = gapValues.indices.minByOrNull { abs(gapValues[it] - prefs.paragraphGap) } ?: 1
    val rateValues = listOf(1f, 1.5f, 2f)
    val rateLabels = listOf("1x", "1.5x", "2x")
    val rateIndex = rateValues.indices.minByOrNull { abs(rateValues[it] - ttsRate) } ?: 0

    // ModalBottomSheet 自带 Surface 外壳和拖拽条，这里只放内容。
    // 高度封顶：设置项再多也只占屏幕下面一块，正文还看得见。
    val maxPanelHeight = (LocalConfiguration.current.screenHeightDp * 0.72f).dp
    Column(
        Modifier
            .heightIn(max = maxPanelHeight)
            .verticalScroll(rememberScrollState())
            // ModalBottomSheet 的窗口自己已经避开了系统栏，这里不再加导航栏内边距，
            // 否则底部会多出一段空隙。留一点底距纯粹是为了让最后一行不贴着边。
            .padding(bottom = Space.xxl),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = Space.gutter),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "阅读设置",
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = palette.title,
                modifier = Modifier.weight(1f),
            )
        }

        // 1. 字号：14–30sp（数据模型的上限，不缩窄），步进 1
        ReaderSheetRow(label = "字号", palette = palette) {
            ReaderStepButton(
                text = "A-",
                enabled = prefs.fontSize > ReaderPrefs.MIN_FONT,
                palette = palette,
            ) {
                onPrefs(
                    prefs.copy(
                    fontSize = (prefs.fontSize - 1f)
                        .coerceIn(ReaderPrefs.MIN_FONT, ReaderPrefs.MAX_FONT),
                ),
                )
            }
            Slider(
                value = prefs.fontSize,
                onValueChange = { v ->
                    onPrefs(
                    prefs.copy(
                    fontSize = v.roundToInt().toFloat()
                        .coerceIn(ReaderPrefs.MIN_FONT, ReaderPrefs.MAX_FONT),
                ),
                )
            },
                valueRange = ReaderPrefs.MIN_FONT..ReaderPrefs.MAX_FONT,
                steps = (ReaderPrefs.MAX_FONT - ReaderPrefs.MIN_FONT).toInt() - 1,
                colors = SliderDefaults.colors(
                thumbColor = palette.accent,
                activeTrackColor = palette.accent,
                inactiveTrackColor = palette.rule,
                activeTickColor = Color.Transparent,
                inactiveTickColor = Color.Transparent,
            ),
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = Space.xs),
            )
            Text(
                text = prefs.fontSize.toInt().toString(),
                fontSize = 11.sp,
                color = palette.dim,
                modifier = Modifier.width(20.dp),
            )
            ReaderStepButton(
                text = "A+",
                enabled = prefs.fontSize < ReaderPrefs.MAX_FONT,
                palette = palette,
            ) {
                onPrefs(
                    prefs.copy(
                    fontSize = (prefs.fontSize + 1f)
                        .coerceIn(ReaderPrefs.MIN_FONT, ReaderPrefs.MAX_FONT),
                ),
                )
            }
        }

        // 2. 字体：系统族名，缺失时回落默认；选项顺序即显示顺序
        ReaderSheetRow(label = "字体", palette = palette) {
            ReaderSegmented(
                options = ReaderPrefs.FONT_LABELS.values.toList(),
                selected = ReaderPrefs.FONT_LABELS[prefs.font]
                ?: ReaderPrefs.FONT_LABELS.getValue("default"),
                palette = palette,
                modifier = Modifier.weight(1f),
                onSelect = { label ->
                    val key = ReaderPrefs.FONT_LABELS.entries
                        .firstOrNull { it.value == label }?.key
                    if (key != null) onPrefs(prefs.copy(font = key))
            },
            )
        }

        // 3. 行距：三档，旧值取最近一档
        ReaderSheetRow(label = "行距", palette = palette) {
            ReaderSegmented(
                options = lineLabels,
                selected = lineLabels[lineIndex],
                palette = palette,
                modifier = Modifier.weight(1f),
                onSelect = { label ->
                    val idx = lineLabels.indexOf(label)
                    if (idx >= 0) onPrefs(prefs.copy(lineHeight = lineValues[idx]))
            },
            )
        }

        // 3b. 段间距：与行距分开，正文节奏更可控
        ReaderSheetRow(label = "段距", palette = palette) {
            ReaderSegmented(
                options = gapLabels,
                selected = gapLabels[gapIndex],
                palette = palette,
                modifier = Modifier.weight(1f),
                onSelect = { label ->
                    val idx = gapLabels.indexOf(label)
                    if (idx >= 0) onPrefs(prefs.copy(paragraphGap = gapValues[idx]))
            },
            )
        }

        // 3c. 页边距：同一档字号下，行宽决定了每行几个字
        ReaderSheetRow(label = "边距", palette = palette) {
            val idx = ReaderPrefs.MARGINS.indices
                .minByOrNull { abs(ReaderPrefs.MARGINS[it] - prefs.margin) } ?: 1
            ReaderSegmented(
                options = ReaderPrefs.MARGIN_LABELS,
                selected = ReaderPrefs.MARGIN_LABELS[idx],
                palette = palette,
                modifier = Modifier.weight(1f),
                onSelect = { label ->
                    val i = ReaderPrefs.MARGIN_LABELS.indexOf(label)
                    if (i >= 0) onPrefs(prefs.copy(margin = ReaderPrefs.MARGINS[i]))
                },
            )
        }

        // 3d. 字距：宋体默认偏紧，加一点气口长文更好读
        ReaderSheetRow(label = "字距", palette = palette) {
            val idx = ReaderPrefs.TRACKINGS.indices
                .minByOrNull { abs(ReaderPrefs.TRACKINGS[it] - prefs.letterSpacing) } ?: 0
            ReaderSegmented(
                options = ReaderPrefs.TRACKING_LABELS,
                selected = ReaderPrefs.TRACKING_LABELS[idx],
                palette = palette,
                modifier = Modifier.weight(1f),
                onSelect = { label ->
                    val i = ReaderPrefs.TRACKING_LABELS.indexOf(label)
                    if (i >= 0) onPrefs(prefs.copy(letterSpacing = ReaderPrefs.TRACKINGS[i]))
                },
            )
        }

        // 4. 背景：用户显式挑选，色板用各方案自己的底色与正文色
        ReaderSheetRow(label = "背景", palette = palette) {
            Row(
                Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(Space.xs),
            ) {
                listOf("paper", "white", "mint", "dark").forEach { key ->
                    val p = ReaderPalette.of(key)
                    PaletteSwatch(
                        label = ReaderPalette.LABELS[key] ?: key,
                        background = p.background,
                        foreground = p.text,
                        selected = selectedThemeKey == key,
                        onClick = { onPrefs(prefs.copy(theme = key)) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }

        // 4b. 屏内亮度：只压本页窗口，退出阅读页还原系统亮度
        ReaderSheetRow(label = "亮度", palette = palette) {
            val idx = ReaderPrefs.BRIGHTNESSES.indices
                .minByOrNull { abs(ReaderPrefs.BRIGHTNESSES[it] - prefs.brightness) } ?: 0
            ReaderSegmented(
                options = ReaderPrefs.BRIGHTNESS_LABELS,
                selected = ReaderPrefs.BRIGHTNESS_LABELS[idx],
                palette = palette,
                modifier = Modifier.weight(1f),
                onSelect = { label ->
                    val i = ReaderPrefs.BRIGHTNESS_LABELS.indexOf(label)
                    if (i >= 0) onPrefs(prefs.copy(brightness = ReaderPrefs.BRIGHTNESSES[i]))
                },
            )
        }

        // 5. 翻页：先选「怎么动」，再管「哪些手势能动」
        ReaderSheetRow(label = "翻页", palette = palette) {
            ReaderSegmented(
                options = ReaderPrefs.TURN_LABELS.values.toList(),
                selected = ReaderPrefs.TURN_LABELS[prefs.turnMode]
                ?: ReaderPrefs.TURN_LABELS.getValue("slide"),
                palette = palette,
                modifier = Modifier.weight(1f),
                onSelect = { label ->
                    val key = ReaderPrefs.TURN_LABELS.entries.firstOrNull { it.value == label }?.key
                    if (key != null) onPrefs(prefs.copy(turnMode = key))
            },
            )
        }
        ReaderToggleRow(
            label = "点按左右翻页",
            checked = prefs.tapTurn,
            palette = palette,
        ) { onPrefs(prefs.copy(tapTurn = it)) }
        ReaderToggleRow(
            label = "音量键翻页",
            checked = prefs.volumeTurn,
            palette = palette,
        ) { onPrefs(prefs.copy(volumeTurn = it)) }
        ReaderToggleRow(
            label = "翻页触感",
            checked = prefs.hapticTurn,
            palette = palette,
        ) { onPrefs(prefs.copy(hapticTurn = it)) }
        ReaderToggleRow(
            label = "段首缩进",
            checked = prefs.indent,
            palette = palette,
        ) { onPrefs(prefs.copy(indent = it)) }
        ReaderToggleRow(
            label = "显示段评",
            checked = prefs.paragraphComments,
            palette = palette,
        ) { onPrefs(prefs.copy(paragraphComments = it)) }

        // 自动滚动与常亮成对出现：这两项都是为「放下手读到睡着」准备的
        ReaderSheetRow(label = "自动滚动", palette = palette) {
            ReaderSegmented(
                options = ReaderPrefs.AUTO_LABELS.values.toList(),
                selected = ReaderPrefs.AUTO_LABELS[prefs.autoScroll] ?: "关",
                palette = palette,
                modifier = Modifier.weight(1f),
                onSelect = { label ->
                    val key = ReaderPrefs.AUTO_LABELS.entries.firstOrNull { it.value == label }?.key
                    if (key != null) onPrefs(prefs.copy(autoScroll = key))
                },
            )
        }
        ReaderToggleRow(
            label = "保持屏幕常亮",
            checked = prefs.keepAwake,
            palette = palette,
        ) { onPrefs(prefs.copy(keepAwake = it)) }

        // 6. 听书：说明 + 受控开关（状态直接反映 ttsSpeaking）
        ReaderSheetRow(label = "听书", palette = palette) {
            Text(
                text = ttsMessage.ifBlank { "后台朗读本章，锁屏可继续" },
                fontSize = 12.sp,
                lineHeight = 18.sp,
                color = palette.dim,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .weight(1f)
                    .padding(end = Space.sm),
            )
            Switch(
                checked = ttsSpeaking,
                onCheckedChange = { onTtsToggle() },
                colors = readerSwitchColors(palette),
            )
        }
        ReaderSheetRow(label = "倍速", palette = palette) {
            ReaderSegmented(
                options = rateLabels,
                selected = rateLabels[rateIndex],
                palette = palette,
                modifier = Modifier.weight(1f),
                onSelect = { label ->
                    val idx = rateLabels.indexOf(label)
                    if (idx >= 0) onTtsRate(rateValues[idx])
            },
            )
        }
        ReaderSheetRow(label = "定时", palette = palette) {
            ReaderChip(
                text = "15 分",
                palette = palette,
                modifier = Modifier.weight(1f),
                onClick = { onTtsSleep(15) },
            )
            Spacer(Modifier.width(Space.sm))
            ReaderChip(
                text = "取消定时",
                palette = palette,
                modifier = Modifier.weight(1f),
                onClick = { onTtsSleep(0) },
            )
        }
    }
}

@Composable
private fun readerSwitchColors(palette: ReaderPalette) = SwitchDefaults.colors(
    checkedThumbColor = palette.background,
    checkedTrackColor = palette.accent,
    checkedBorderColor = palette.accent,
    uncheckedThumbColor = palette.dim,
    uncheckedTrackColor = palette.background,
    uncheckedBorderColor = palette.rule,
)

/** 设置行：左 13sp 标签（固定 52dp 列），右控件。整行高 ≥48dp。 */
@Composable
private fun ReaderSheetRow(
    label: String,
    palette: ReaderPalette,
    content: @Composable RowScope.() -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .padding(horizontal = Space.gutter, vertical = Space.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            fontSize = 13.sp,
            color = palette.text,
            modifier = Modifier.width(52.dp),
        )
        content()
    }
}

@Composable
private fun ReaderToggleRow(
    label: String,
    checked: Boolean,
    palette: ReaderPalette,
    onChange: (Boolean) -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clickable { onChange(!checked) }
            .padding(horizontal = Space.gutter, vertical = Space.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            fontSize = 13.sp,
            color = palette.text,
            modifier = Modifier.weight(1f),
        )
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = readerSwitchColors(palette),
        )
    }
}

/** 分段控制：抽屉内需要 palette 着色与 48dp 触控高度，故不复用外壳版本。 */
@Composable
private fun ReaderSegmented(
    options: List<String>,
    selected: String,
    palette: ReaderPalette,
    modifier: Modifier = Modifier,
    onSelect: (String) -> Unit,
) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
        options.forEach { label ->
            val on = label == selected
            Box(
                Modifier
                    .weight(1f)
                    .height(48.dp)
                    .clip(RoundedCornerShape(Radius.sm))
                    .background(if (on) palette.accent.copy(alpha = 0.12f) else Color.Transparent)
                    .border(
                        1.dp,
                        if (on) palette.accent else palette.rule,
                        RoundedCornerShape(Radius.sm),
                    )
                    .clickable { onSelect(label) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = label,
                    fontSize = 12.sp,
                    color = if (on) palette.accent else palette.dim,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun ReaderStepButton(
    text: String,
    enabled: Boolean,
    palette: ReaderPalette,
    onClick: () -> Unit,
) {
    Box(
        Modifier
            .size(48.dp)
            .clip(RoundedCornerShape(Radius.sm))
            .background(if (enabled) palette.rule.copy(alpha = 0.6f) else Color.Transparent)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            fontSize = 14.sp,
            color = if (enabled) palette.text else palette.dim,
        )
    }
}

@Composable
private fun ReaderChip(
    text: String,
    palette: ReaderPalette,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Box(
        modifier
            .height(48.dp)
            .clip(RoundedCornerShape(Radius.sm))
            .border(1.dp, palette.rule, RoundedCornerShape(Radius.sm))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            fontSize = 12.sp,
            color = palette.text,
            maxLines = 1,
        )
    }
}

/* ================================================================== *
 * 小工具
 * ================================================================== */

/** 控件条吃掉落在自身上的点击，避免穿透到正文的「点按切 chrome / 翻页」手势。 */
private fun Modifier.consumeTaps(): Modifier = pointerInput(Unit) {
    detectTapGestures { }
}

/** 从包装 Context 中取出宿主 Activity，用于改本页窗口亮度。 */
/**
 * 跨章浮现的章名。上游标题自带「第3章」前缀，剥掉再拼，
 * 否则就成了「第 3 章 · 第3章 写日记的人」。
 */
internal fun chapterToastText(chapterIndex: Int, title: String): String? {
    val prefix = chapterHeadingPattern.find(title.trim())
    val name = if (prefix == null) {
        title.trim()
    } else {
        title.trim().substring(prefix.range.last + 1).trimStart('：', ':', '·', '、', '，', ' ', '　').trim()
    }
    return when {
        name.isEmpty() -> if (chapterIndex >= 0) "第 ${chapterIndex + 1} 章" else null
        chapterIndex >= 0 -> "第 ${chapterIndex + 1} 章 · $name"
        else -> name
    }
}

private val chapterHeadingPattern = Regex("^第\\s*[0-9零一二三四五六七八九十百千]+\\s*章")

private fun Context.findActivity(): Activity? {
    var ctx: Context = this
    while (ctx is ContextWrapper) {
        if (ctx is Activity) return ctx
        ctx = ctx.baseContext
    }
    return null
}

/** 一屏高度即「一页」，留 15% 重叠避免段首被切掉。 */
private fun LazyGridState.pagePx(): Float =
    (layoutInfo.viewportSize.height * 0.85f).coerceAtLeast(200f)

/**
 * 列表下标 → 阅读段落下标。纯函数，便于单测。
 *
 * 列表第 0 项是章标题、末项是翻页导航，故正文下标要减一；
 * 再按实际段落数夹取，短章或切章瞬间的旧下标都不会越界。
 */
internal object ReaderProgress {
    fun paragraphOf(listIndex: Int, paragraphs: Int): Int {
        if (paragraphs <= 0) return 0
        return (listIndex - 1).coerceIn(0, paragraphs - 1)
    }
}
