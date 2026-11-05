package com.alanmaster.xread.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.media.MediaPlayer
import android.os.Build
import android.widget.VideoView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FitScreen
import androidx.compose.material.icons.filled.FormatListNumbered
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material.icons.filled.SubtitlesOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.compose.ui.viewinterop.AndroidView
import coil.compose.AsyncImage
import com.alanmaster.xread.data.DanmakuItem
import com.alanmaster.xread.data.DramaEpisode
import com.alanmaster.xread.data.DramaPlaySource
import com.alanmaster.xread.data.DramaSeries
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private val DouyinRed = Color(0xFFFF2C55)
private val DouyinDarkSurface = Color(0xFF161616)

/** 双击点赞飘心动画数据 */
private data class HeartBubble(
    val id: Long,
    val x: Float,
    val y: Float,
)

/**
 * 抖音模式短剧全屏沉浸播放界面：
 * 1. 上下滑动切集 (VerticalPager)。
 * 2. 视频全屏铺满播放、单机播放/暂停、双击浮心点赞、长按2倍速快进。
 * 3. 右侧互动操作栏：封面关注、点赞、选集弹窗、弹幕开关、倍速切换、经典模式切换。
 * 4. 底部沉浸信息：@剧名、集数标题、简介展开、底部细进度条与快捷选集条。
 * 5. 选集抽屉 (ModalBottomSheet)：支持直接切换集数与一键换剧。
 */
@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun DramaDouyinScreen(
    drama: DramaSeries?,
    currentEpisode: DramaEpisode?,
    currentVideoSource: DramaPlaySource?,
    videoLoading: Boolean,
    videoError: String,
    danmakuList: List<DanmakuItem>,
    feed: List<DramaSeries>,
    /** 已收藏的短剧，星标状态由它派生；收藏本身落盘在 OfflineRepo。 */
    favorites: List<DramaSeries> = emptyList(),
    onToggleFavorite: (DramaSeries) -> Unit = {},
    onBack: () -> Unit,
    onSelectEpisode: (DramaEpisode) -> Unit,
    onSelectDrama: (DramaSeries) -> Unit,
    onSendDanmaku: (String, Long) -> Unit,
    onSwitchToClassic: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val episodes = drama?.episodes.orEmpty()

    // 默认剧集兜底：若暂未选剧但推荐流有内容，自动载入首部剧
    LaunchedEffect(drama, feed) {
        if (drama == null && feed.isNotEmpty()) {
            onSelectDrama(feed.first())
        }
    }

    // 选集弹窗抽屉控制
    var showEpisodeSheet by remember { mutableStateOf(false) }

    // 自动连播开关
    var autoNextEnabled by remember { mutableStateOf(true) }

    // 弹幕开启状态
    var danmakuEnabled by remember { mutableStateOf(true) }

    // 播放倍速：1.0x, 1.25x, 1.5x, 2.0x
    var playbackSpeed by remember { mutableFloatStateOf(1.0f) }

    // 点赞：上游没有短剧点赞数（digg_cnt 恒为 0，也没有点赞写接口），
    // 原来这里显示一个写死的「12.9w」，每部剧都一样。只保留双击红心这个手势反馈。

    // 收藏状态：读 ViewModel 里的收藏列表，收藏与取消都走 onToggleFavorite 落盘。
    val hostContext = LocalContext.current
    val isFavorited = drama?.seriesId?.let { id -> favorites.any { it.seriesId == id } } ?: false

    // 沉浸式全屏：进入抖音模式隐藏系统栏，离开时恢复
    DisposableEffect(hostContext) {
        val window = hostContext.findActivity()?.window
        if (window == null) {
            onDispose { }
        } else {
            val controller = WindowCompat.getInsetsController(window, window.decorView)
            controller.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller.hide(WindowInsetsCompat.Type.systemBars())
            onDispose {
                controller.show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }

    // 双击飘心列表
    val heartBubbles = remember { mutableStateListOf<HeartBubble>() }

    // 剧集简介是否展开
    var introExpanded by remember { mutableStateOf(false) }

    // 弹幕输入文本
    var danmakuInputText by remember { mutableStateOf("") }

    // 播放位置与总时长
    var currentPositionMs by remember { mutableLongStateOf(0L) }
    var durationMs by remember { mutableLongStateOf(0L) }
    var isScrubbing by remember { mutableStateOf(false) }
    var scrubPositionMs by remember { mutableLongStateOf(0L) }

    // 播放控制引用与播放状态
    var videoViewRef by remember { mutableStateOf<VideoView?>(null) }
    var mediaPlayerRef by remember { mutableStateOf<MediaPlayer?>(null) }
    var isPlaying by remember { mutableStateOf(false) }
    var isFastForwarding by remember { mutableStateOf(false) }

    // 调节倍速辅助方法
    val applySpeed: (Float) -> Unit = { speed ->
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                mediaPlayerRef?.let { mp ->
                    mp.playbackParams = mp.playbackParams.setSpeed(speed)
                }
            } catch (_: Exception) {}
        }
    }

    // 计算当前集在剧集列表中的索引
    val currentEpisodeIndex = remember(drama?.seriesId, currentEpisode?.episodeId, episodes) {
        val idx = episodes.indexOfFirst { it.episodeId == currentEpisode?.episodeId }
        if (idx >= 0) idx else 0
    }

    // 下一部剧：当前剧滑到末尾后继续上滑，直接切入推荐流里的下一部短剧
    val nextDrama = remember(feed, drama?.seriesId) {
        if (feed.size <= 1) {
            null
        } else {
            val idx = feed.indexOfFirst { it.seriesId == drama?.seriesId }
            if (idx < 0) feed.first() else feed[(idx + 1) % feed.size]
        }
    }
    val nextDramaPage = if (nextDrama != null && episodes.isNotEmpty()) 1 else 0

    // VerticalPager 状态（末尾追加一页「下一部剧」过渡页）
    val pagerState = rememberPagerState(
        initialPage = currentEpisodeIndex,
        pageCount = { if (episodes.isNotEmpty()) episodes.size + nextDramaPage else 1 }
    )

    // 当外部选中集变化时同步 Pager
    LaunchedEffect(currentEpisodeIndex) {
        if (pagerState.currentPage != currentEpisodeIndex && currentEpisodeIndex in episodes.indices) {
            pagerState.scrollToPage(currentEpisodeIndex)
        }
    }

    // 当 Pager 滑动到新页面时触发集数加载与播放；滑到末尾过渡页则换下一部剧
    LaunchedEffect(pagerState, episodes, nextDrama) {
        snapshotFlow { pagerState.currentPage }.collect { page ->
            if (page in episodes.indices) {
                val targetEp = episodes[page]
                if (targetEp.episodeId != currentEpisode?.episodeId) {
                    onSelectEpisode(targetEp)
                }
            } else if (page >= episodes.size && nextDrama != null) {
                onSelectDrama(nextDrama)
            }
        }
    }

    // 播放进度高频轮询更新
    LaunchedEffect(videoViewRef, isPlaying) {
        while (true) {
            videoViewRef?.let { vv ->
                if (vv.isPlaying && !isScrubbing) {
                    currentPositionMs = vv.currentPosition.toLong()
                    val dur = vv.duration.toLong()
                    if (dur > 0) durationMs = dur
                }
            }
            delay(200)
        }
    }

    // 根容器：黑色背景全屏沉浸
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        if (episodes.isEmpty()) {
            // 数据加载中或空提示
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                if (videoLoading) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        CircularProgressIndicator(color = DouyinRed)
                        Text("剧目加载中...", color = Color.White.copy(alpha = 0.8f), fontSize = 14.sp)
                    }
                } else {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(drama?.title ?: "暂无剧目", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        TextButton(onClick = { drama?.let { onSelectDrama(it) } }) {
                            Text("点击重试", color = DouyinRed)
                        }
                    }
                }
            }
        } else {
            // 核心纵向滑动翻页容器
            VerticalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
                key = { page -> episodes.getOrNull(page)?.episodeId ?: page },
            ) { page ->
                if (page >= episodes.size) {
                    DouyinNextDramaPreview(
                        nextDrama = nextDrama,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                val ep = episodes.getOrNull(page)
                val isCurrentPage = page == pagerState.currentPage

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(Unit) {
                            detectTapGestures(
                                onTap = {
                                    // 单击：播放 / 暂停切换
                                    videoViewRef?.let { vv ->
                                        if (vv.isPlaying) {
                                            vv.pause()
                                            isPlaying = false
                                        } else {
                                            vv.start()
                                            isPlaying = true
                                        }
                                    }
                                },
                                onDoubleTap = { offset ->
                                    // 双击：抖音式红心爆破动效
                                    val bubble = HeartBubble(System.currentTimeMillis(), offset.x, offset.y)
                                    heartBubbles.add(bubble)
                                    scope.launch {
                                        delay(900)
                                        heartBubbles.remove(bubble)
                                    }
                                },
                                onPress = {
                                    // 长按触发 2.0x 倍速快进，松开恢复
                                    isFastForwarding = true
                                    applySpeed(2.0f)
                                    try {
                                        awaitRelease()
                                    } finally {
                                        isFastForwarding = false
                                        applySpeed(playbackSpeed)
                                    }
                                }
                            )
                        }
                ) {
                    // 1. 封面底图（未缓冲好或滑动预览时平滑兜底）
                    AsyncImage(
                        model = ep?.coverUrl?.ifEmpty { drama?.coverUrl }.orEmpty(),
                        contentDescription = ep?.title,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                    )

                    // 2. 原生全屏视频播放层（仅当是当前活动页面时挂载）
                    if (isCurrentPage && currentVideoSource != null) {
                        AndroidView(
                            modifier = Modifier.fillMaxSize(),
                            factory = { ctx ->
                                VideoView(ctx).apply {
                                    setOnPreparedListener { mp ->
                                        mediaPlayerRef = mp
                                        durationMs = mp.duration.toLong()
                                        mp.isLooping = !autoNextEnabled
                                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && playbackSpeed != 1.0f) {
                                            try {
                                                mp.playbackParams = mp.playbackParams.setSpeed(playbackSpeed)
                                            } catch (_: Exception) {}
                                        }
                                        mp.start()
                                        isPlaying = true
                                    }
                                    setOnCompletionListener {
                                        isPlaying = false
                                        if (autoNextEnabled) {
                                            if (pagerState.currentPage + 1 < episodes.size) {
                                                scope.launch {
                                                    pagerState.animateScrollToPage(pagerState.currentPage + 1)
                                                }
                                            } else if (nextDramaPage > 0) {
                                                // 最后一集播完，滑向过渡页，由 snapshotFlow 自动切下一部剧
                                                scope.launch {
                                                    pagerState.animateScrollToPage(episodes.size)
                                                }
                                            }
                                        }
                                    }
                                    videoViewRef = this
                                }
                            },
                            update = { vv ->
                                videoViewRef = vv
                                val path = if (currentVideoSource.isLocal) currentVideoSource.localFilePath else currentVideoSource.videoUrl
                                if (path.isNotEmpty() && vv.tag != path) {
                                    vv.tag = path
                                    vv.setVideoPath(path)
                                }
                            }
                        )
                    }

                    // 3. 加载中或错误状态遮罩
                    if (videoLoading && isCurrentPage) {
                        Box(
                            Modifier
                                .fillMaxSize()
                                .background(Color.Black.copy(alpha = 0.4f)),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(color = DouyinRed, modifier = Modifier.size(44.dp))
                        }
                    } else if (videoError.isNotEmpty() && isCurrentPage) {
                        Box(
                            Modifier
                                .fillMaxSize()
                                .background(Color.Black.copy(alpha = 0.65f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(videoError, color = Color.White, fontSize = 14.sp)
                                TextButton(onClick = { ep?.let { onSelectEpisode(it) } }) {
                                    Text("重试播放", color = DouyinRed)
                                }
                            }
                        }
                    }
                }
                }
            }
        }

        // 4. 浮动弹幕呈现层（屏幕上半区飘动）
        if (danmakuEnabled && danmakuList.isNotEmpty()) {
            DouyinDanmakuOverlay(
                items = danmakuList,
                currentPositionMs = currentPositionMs,
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.42f)
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(top = 56.dp)
            )
        }

        // 5. 暂停时屏幕中央半透明大播放图标
        AnimatedVisibility(
            visible = !isPlaying && !videoLoading && videoError.isEmpty(),
            enter = fadeIn() + scaleIn(initialScale = 0.8f),
            exit = fadeOut() + scaleOut(targetScale = 0.8f),
            modifier = Modifier.align(Alignment.Center)
        ) {
            Box(
                modifier = Modifier
                    .size(76.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.45f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Filled.PlayArrow,
                    contentDescription = "播放",
                    tint = Color.White.copy(alpha = 0.9f),
                    modifier = Modifier.size(48.dp)
                )
            }
        }

        // 6. 长按2倍速快进提示徽章
        AnimatedVisibility(
            visible = isFastForwarding,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(top = 64.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = Color.Black.copy(alpha = 0.65f),
                border = androidx.compose.foundation.BorderStroke(1.dp, DouyinRed.copy(alpha = 0.8f))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = null, tint = DouyinRed, modifier = Modifier.size(16.dp))
                    Text("2.0X 快进中", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        // 7. 双击红心飘动爆破层
        heartBubbles.forEach { bubble ->
            DouyinHeartPop(bubble = bubble)
        }

        // 8. 顶部悬浮导航与状态栏 (Back / 剧名 / 连播 / 传统模式)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 返回键
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.4f))
                    .clickable { onBack() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "返回",
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(Modifier.width(10.dp))

            // 剧集标题指示
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = drama?.title ?: "短剧沉浸剧场",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = currentEpisode?.title ?: "第 ${currentEpisodeIndex + 1} 集",
                    color = Color.White.copy(alpha = 0.75f),
                    fontSize = 12.sp,
                    maxLines = 1
                )
            }

            // 自动连播开关按钮
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(14.dp))
                    .background(if (autoNextEnabled) DouyinRed.copy(alpha = 0.85f) else Color.Black.copy(alpha = 0.45f))
                    .clickable { autoNextEnabled = !autoNextEnabled }
                    .padding(horizontal = 10.dp, vertical = 5.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Icon(Icons.Filled.Repeat, contentDescription = null, tint = Color.White, modifier = Modifier.size(13.dp))
                    Text(if (autoNextEnabled) "连播开" else "连播关", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                }
            }

            Spacer(Modifier.width(8.dp))

            // 切换传统 16:9 播放界面
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.4f))
                    .clickable { onSwitchToClassic() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Filled.FitScreen,
                    contentDescription = "切换常规模式",
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        // 9. 右侧垂直悬浮操作栏 (头像、点赞、选集、弹幕、倍速)
        Column(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 12.dp, bottom = 95.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 剧集封面小头像。
            // 原来头像下面挂了一个写着「追剧」的红色加号，但那个加号没有 onClick：
            // 点下去什么都不会发生，而 App 里也确实没有「关注」这个功能
            // （上游只给 followed_cnt，没有关注写接口）。假的入口比没有入口更误导，
            // 所以把加号删掉，只留下真的能落盘的「收藏」。
            AsyncImage(
                model = drama?.coverUrl.orEmpty(),
                contentDescription = "海报",
                modifier = Modifier
                    .size(46.dp)
                    .clip(CircleShape)
                    .border(1.5.dp, Color.White, CircleShape),
                contentScale = ContentScale.Crop
            )

            Spacer(Modifier.height(4.dp))

            // 收藏按钮（本地持久化）
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                IconButton(
                    onClick = { drama?.let(onToggleFavorite) },
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        Icons.Filled.Star,
                        contentDescription = "收藏",
                        tint = if (isFavorited) Color(0xFFFFC107) else Color.White,
                        modifier = Modifier.size(30.dp)
                    )
                }
                Text(
                    if (isFavorited) "已收藏" else "收藏",
                    color = Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium
                )
            }

            // 分享按钮（拉起系统分享面板）
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                IconButton(
                    onClick = {
                        val shareText = "《${drama?.title ?: "短剧"}》第 ${currentEpisodeIndex + 1} 集" +
                            (currentEpisode?.title?.takeIf { it.isNotBlank() }?.let { " · $it" } ?: "") +
                            "，来拾页一起看"
                        val intent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, shareText)
                        }
                        hostContext.startActivity(Intent.createChooser(intent, "分享短剧"))
                    },
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        Icons.Filled.Share,
                        contentDescription = "分享",
                        tint = Color.White,
                        modifier = Modifier.size(28.dp)
                    )
                }
                Text("分享", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Medium)
            }

            // 选集按钮 (唤起抽屉)
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                IconButton(
                    onClick = { showEpisodeSheet = true },
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        Icons.Filled.FormatListNumbered,
                        contentDescription = "选集",
                        tint = Color.White,
                        modifier = Modifier.size(30.dp)
                    )
                }
                Text("选集", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Medium)
            }

            // 弹幕开关按钮
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                IconButton(
                    onClick = { danmakuEnabled = !danmakuEnabled },
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        if (danmakuEnabled) Icons.Filled.Subtitles else Icons.Filled.SubtitlesOff,
                        contentDescription = "弹幕开关",
                        tint = if (danmakuEnabled) DouyinRed else Color.White.copy(alpha = 0.7f),
                        modifier = Modifier.size(28.dp)
                    )
                }
                Text("弹幕", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Medium)
            }

            // 倍速切换按钮
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.45f))
                        .border(1.dp, Color.White.copy(alpha = 0.6f), CircleShape)
                        .clickable {
                            playbackSpeed = when (playbackSpeed) {
                                1.0f -> 1.25f
                                1.25f -> 1.5f
                                1.5f -> 2.0f
                                else -> 1.0f
                            }
                            applySpeed(playbackSpeed)
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "${playbackSpeed}x",
                        color = if (playbackSpeed != 1.0f) DouyinRed else Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(Modifier.height(2.dp))
                Text("倍速", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Medium)
            }
        }

        // 10. 底部半透明遮罩与信息栏（剧名、标题、简介、选集快捷胶囊条、播放进度条）
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            Color.Black.copy(alpha = 0.6f),
                            Color.Black.copy(alpha = 0.95f),
                        )
                    )
                )
                .padding(bottom = 6.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // @剧名
                Text(
                    text = "@${drama?.title ?: "短剧"}",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                // 集数标题
                Text(
                    text = currentEpisode?.title ?: "第 ${currentEpisodeIndex + 1} 集",
                    color = Color.White.copy(alpha = 0.92f),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1
                )

                // 剧集简介展开/折叠
                if (!drama?.intro.isNullOrBlank()) {
                    Text(
                        text = drama?.intro.orEmpty(),
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 12.sp,
                        maxLines = if (introExpanded) 5 else 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.clickable { introExpanded = !introExpanded }
                    )
                }

                Spacer(Modifier.height(4.dp))

                // 快捷选集胶囊条（点击亦可快速展开选集抽屉）
                Row(
                    modifier = Modifier
                        .fillMaxWidth(0.85f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.White.copy(alpha = 0.15f))
                        .clickable { showEpisodeSheet = true }
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Filled.Movie, contentDescription = null, tint = DouyinRed, modifier = Modifier.size(15.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "选集 · 第 ${currentEpisodeIndex + 1} / ${if ((drama?.episodeCount ?: 0) > 0) drama?.episodeCount else episodes.size} 集 · ${if (drama?.episodeCount == episodes.size && episodes.isNotEmpty()) "已完结" else "连载中"}",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.weight(1f)
                    )
                    Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = Color.White.copy(alpha = 0.7f), modifier = Modifier.size(16.dp))
                }

                // 快捷弹幕输入条
                Row(
                    modifier = Modifier
                        .fillMaxWidth(0.92f)
                        .padding(top = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(30.dp)
                            .clip(RoundedCornerShape(15.dp))
                            .background(Color.White.copy(alpha = 0.12f))
                            .padding(horizontal = 12.dp),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        if (danmakuInputText.isEmpty()) {
                            Text("发条弹幕互动一下...", color = Color.White.copy(alpha = 0.5f), fontSize = 12.sp)
                        }
                        BasicTextField(
                            value = danmakuInputText,
                            onValueChange = { danmakuInputText = it },
                            singleLine = true,
                            textStyle = TextStyle(color = Color.White, fontSize = 12.sp),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                            keyboardActions = KeyboardActions(onSend = {
                                if (danmakuInputText.isNotBlank()) {
                                    onSendDanmaku(danmakuInputText, currentPositionMs)
                                    danmakuInputText = ""
                                }
                            }),
                            cursorBrush = SolidColor(DouyinRed),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    if (danmakuInputText.isNotBlank()) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(15.dp))
                                .background(DouyinRed)
                                .clickable {
                                    onSendDanmaku(danmakuInputText, currentPositionMs)
                                    danmakuInputText = ""
                                }
                                .padding(horizontal = 10.dp, vertical = 5.dp)
                        ) {
                            Text("发送", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // 拖动快进时的中央时间指示气泡
            AnimatedVisibility(
                visible = isScrubbing,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.align(Alignment.CenterHorizontally)
            ) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color.Black.copy(alpha = 0.8f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.4f))
                ) {
                    Text(
                        text = "${formatTime(scrubPositionMs)} / ${formatTime(durationMs)}",
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
            }

            // 底部细长滑动进度条
            val currentMs = if (isScrubbing) scrubPositionMs else currentPositionMs
            val progressFraction = if (durationMs > 0) (currentMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f) else 0f

            Slider(
                value = progressFraction,
                onValueChange = { frac ->
                    isScrubbing = true
                    scrubPositionMs = (frac * durationMs).toLong()
                },
                onValueChangeFinished = {
                    videoViewRef?.seekTo(scrubPositionMs.toInt())
                    currentPositionMs = scrubPositionMs
                    isScrubbing = false
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(18.dp)
                    .padding(horizontal = 8.dp),
                colors = SliderDefaults.colors(
                    thumbColor = DouyinRed,
                    activeTrackColor = DouyinRed,
                    inactiveTrackColor = Color.White.copy(alpha = 0.25f),
                )
            )
        }

        // 11. 选集底部抽屉 ModalBottomSheet
        if (showEpisodeSheet) {
            ModalBottomSheet(
                onDismissRequest = { showEpisodeSheet = false },
                sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
                containerColor = DouyinDarkSurface,
                scrimColor = Color.Black.copy(alpha = 0.6f)
            ) {
                DouyinEpisodePickerSheet(
                    drama = drama,
                    episodes = episodes,
                    currentEpisodeIndex = currentEpisodeIndex,
                    feed = feed,
                    onSelectEpisode = { targetEp, targetIdx ->
                        showEpisodeSheet = false
                        onSelectEpisode(targetEp)
                        scope.launch {
                            pagerState.scrollToPage(targetIdx)
                        }
                    },
                    onSelectAnotherDrama = { newDrama ->
                        showEpisodeSheet = false
                        onSelectDrama(newDrama)
                    },
                    onClose = { showEpisodeSheet = false }
                )
            }
        }
    }
}

/** 选集抽屉内容：支持本剧选集列表与快速一键换剧 */
/** 当前剧最后一集之后上滑出现的过渡页：展示下一部短剧并自动切入 */
@Composable
private fun DouyinNextDramaPreview(
    nextDrama: DramaSeries?,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.background(Color.Black),
        contentAlignment = Alignment.Center,
    ) {
        if (nextDrama == null) {
            CircularProgressIndicator(color = DouyinRed)
            return@Box
        }
        AsyncImage(
            model = nextDrama.coverUrl,
            contentDescription = nextDrama.title,
            modifier = Modifier
                .fillMaxSize()
                .alpha(0.45f),
            contentScale = ContentScale.Crop,
        )
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(horizontal = 32.dp),
        ) {
            CircularProgressIndicator(color = DouyinRed)
            Text("即将播放下一部", color = Color.White.copy(alpha = 0.7f), fontSize = 13.sp)
            Text(
                nextDrama.title,
                color = Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                "共 ${nextDrama.episodeCount} 集 · 上滑继续",
                color = Color.White.copy(alpha = 0.65f),
                fontSize = 12.sp,
            )
        }
    }
}

@Composable
private fun DouyinEpisodePickerSheet(
    drama: DramaSeries?,
    episodes: List<DramaEpisode>,
    currentEpisodeIndex: Int,
    feed: List<DramaSeries>,
    onSelectEpisode: (DramaEpisode, Int) -> Unit,
    onSelectAnotherDrama: (DramaSeries) -> Unit,
    onClose: () -> Unit,
) {
    var selectedTab by remember { mutableIntStateOf(0) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight(0.68f)
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        // 抽屉头部
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    drama?.title ?: "短剧剧场",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    "全 ${episodes.size} 集 · 当前播放第 ${currentEpisodeIndex + 1} 集",
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 12.sp
                )
            }
            IconButton(onClick = onClose) {
                Icon(Icons.Filled.Close, contentDescription = "关闭", tint = Color.White.copy(alpha = 0.8f))
            }
        }

        Spacer(Modifier.height(8.dp))

        // 标签切换：本剧选集 / 换一部剧
        TabRow(
            selectedTabIndex = selectedTab,
            containerColor = Color.Transparent,
            contentColor = Color.White,
            indicator = { tabPositions ->
                TabRowDefaults.SecondaryIndicator(
                    modifier = Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                    color = DouyinRed
                )
            }
        ) {
            Tab(
                selected = selectedTab == 0,
                onClick = { selectedTab = 0 },
                text = { Text("剧集列表 (${episodes.size})", fontWeight = FontWeight.SemiBold) }
            )
            Tab(
                selected = selectedTab == 1,
                onClick = { selectedTab = 1 },
                text = { Text("换一部剧 (${feed.size})", fontWeight = FontWeight.SemiBold) }
            )
        }

        Spacer(Modifier.height(12.dp))

        if (selectedTab == 0) {
            // 剧集网格选择
            LazyVerticalGrid(
                columns = GridCells.Fixed(5),
                contentPadding = PaddingValues(bottom = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                itemsIndexed(episodes) { index, ep ->
                    val isPlayingNow = index == currentEpisodeIndex
                    Box(
                        modifier = Modifier
                            .aspectRatio(1.1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(
                                if (isPlayingNow) DouyinRed.copy(alpha = 0.9f)
                                else Color.White.copy(alpha = 0.08f)
                            )
                            .border(
                                width = 1.dp,
                                color = if (isPlayingNow) DouyinRed else Color.Transparent,
                                shape = RoundedCornerShape(8.dp)
                            )
                            .clickable { onSelectEpisode(ep, index) },
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "${index + 1}",
                                color = Color.White,
                                fontWeight = if (isPlayingNow) FontWeight.Bold else FontWeight.Normal,
                                fontSize = 16.sp
                            )
                            if (isPlayingNow) {
                                Text(
                                    text = "播放中",
                                    color = Color.White.copy(alpha = 0.9f),
                                    fontSize = 9.sp
                                )
                            }
                        }
                    }
                }
            }
        } else {
            // 换一部剧推荐列表
            LazyColumn(
                contentPadding = PaddingValues(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(feed.filterNot { it.seriesId == drama?.seriesId }) { item ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color.White.copy(alpha = 0.06f))
                            .clickable { onSelectAnotherDrama(item) }
                            .padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AsyncImage(
                            model = item.coverUrl,
                            contentDescription = item.title,
                            modifier = Modifier
                                .size(width = 54.dp, height = 74.dp)
                                .clip(RoundedCornerShape(6.dp)),
                            contentScale = ContentScale.Crop
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(item.title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(item.intro.ifEmpty { "全 ${item.episodeCount} 集" }, color = Color.White.copy(alpha = 0.65f), fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                        Spacer(Modifier.width(8.dp))
                        Text("播放 >", color = DouyinRed, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

/** 抖音风格双击点赞红心缩放爆炸动效组件 */
@Composable
private fun DouyinHeartPop(bubble: HeartBubble) {
    val scale = remember { Animatable(0.4f) }
    val alpha = remember { Animatable(1f) }

    LaunchedEffect(bubble.id) {
        launch {
            scale.animateTo(1.3f, tween(300, easing = FastOutSlowInEasing))
            scale.animateTo(1.1f, tween(150))
        }
        launch {
            delay(400)
            alpha.animateTo(0f, tween(400))
        }
    }

    Icon(
        imageVector = Icons.Filled.Favorite,
        contentDescription = null,
        tint = DouyinRed,
        modifier = Modifier
            .offset { IntOffset(bubble.x.roundToInt() - 40, bubble.y.roundToInt() - 40) }
            .size(80.dp)
            .scale(scale.value)
            .alpha(alpha.value)
    )
}

/** 弹幕流动层 */
@Composable
private fun DouyinDanmakuOverlay(
    items: List<DanmakuItem>,
    currentPositionMs: Long,
    modifier: Modifier = Modifier,
) {
    val visibleDanmaku = remember(items, currentPositionMs) {
        items.filter {
            val delta = it.offsetMs - currentPositionMs
            delta in -1000..5000
        }.take(8)
    }

    Box(modifier = modifier) {
        visibleDanmaku.forEachIndexed { idx, danmaku ->
            val laneY = (idx % 4) * 26
            val timeDelta = (danmaku.offsetMs - currentPositionMs).coerceIn(-1000L, 5000L)
            val progress = 1f - (timeDelta + 1000f) / 6000f
            val offsetX = (360 - progress * 480).dp

            Text(
                text = danmaku.text,
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier
                    .offset(x = offsetX, y = laneY.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.Black.copy(alpha = 0.45f))
                    .padding(horizontal = 8.dp, vertical = 2.dp)
            )
        }
    }
}

/** 时间格式化 mm:ss */
private fun formatTime(ms: Long): String {
    val totalSec = (ms / 1000).coerceAtLeast(0)
    val m = totalSec / 60
    val s = totalSec % 60
    // 固定 Locale.US：默认 Locale 在 ar/fa/bn 下会把数字换成别的记数系统，
    // 时长看起来就像乱码。
    return String.format(java.util.Locale.US, "%02d:%02d", m, s)
}

/** 从包装 Context 中取出宿主 Activity，用于控制系统栏 */
private fun Context.findActivity(): Activity? {
    var ctx: Context = this
    while (ctx is ContextWrapper) {
        if (ctx is Activity) return ctx
        ctx = ctx.baseContext
    }
    return null
}
