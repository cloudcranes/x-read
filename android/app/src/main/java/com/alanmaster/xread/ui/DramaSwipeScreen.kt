package com.alanmaster.xread.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.os.Build
import android.widget.VideoView
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import coil.compose.AsyncImage
import com.alanmaster.xread.data.DramaPlaySource
import com.alanmaster.xread.data.DramaSeries
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val SwipeRed = Color(0xFFFE2C55)

/**
 * 刷短剧界面：一屏一部剧，只播该剧第一集，上下滑动换剧。
 * 第一集播完自动进入该剧详情页并从第二集继续。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DramaSwipeScreen(
    feed: List<DramaSeries>,
    initialPage: Int = 0,
    onPageChanged: (Int) -> Unit = {},
    /** 已收藏的短剧，星标状态由它派生；收藏本身落盘在 OfflineRepo。 */
    favorites: List<DramaSeries> = emptyList(),
    onToggleFavorite: (DramaSeries) -> Unit = {},
    drama: DramaSeries?,
    currentEpisodeId: String?,
    currentVideoSource: DramaPlaySource?,
    videoLoading: Boolean,
    videoError: String,
    onBack: () -> Unit,
    onLoadFirstEpisode: (DramaSeries) -> Unit,
    onOpenDetailNextEpisode: (DramaSeries) -> Unit,
    /** 播放失败时重试当前这一集。 */
    onRetryVideo: () -> Unit = {},
    onLoadMore: () -> Unit = {},
    feedLoading: Boolean = false,
    feedError: String = "",
    onReloadFeed: () -> Unit = {},
) {
    if (feed.isEmpty()) {
        Box(Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
            if (feedLoading) {
                CircularProgressIndicator(color = SwipeRed)
            } else {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = feedError.ifBlank { "短剧暂时没能加载出来" },
                        color = Color(0xFFEAEAEA),
                        fontSize = 14.sp,
                        modifier = Modifier.padding(horizontal = 32.dp),
                    )
                    Spacer(Modifier.height(16.dp))
                    SecondaryButton(text = "重试", onClick = onReloadFeed)
                }
            }
        }
        return
    }

    val scope = rememberCoroutineScope()
    val hostContext = LocalContext.current
    val pagerState = rememberPagerState(
        initialPage = initialPage.coerceIn(0, maxOf(0, feed.lastIndex)),
        pageCount = { feed.size },
    )

    var isPlaying by remember { mutableStateOf(false) }
    var positionMs by remember { mutableLongStateOf(0L) }
    var durationMs by remember { mutableLongStateOf(0L) }
    var videoViewRef by remember { mutableStateOf<VideoView?>(null) }
    var playerSeriesId by remember { mutableStateOf("") }
    var handedOff by remember { mutableStateOf(false) }

    val heartScale = remember { Animatable(0f) }
    var showHeart by remember { mutableStateOf(false) }

    DisposableEffect(hostContext) {
        val window = hostContext.findHostActivity()?.window
        if (window == null) {
            onDispose { }
        } else {
            val controller = WindowCompat.getInsetsController(window, window.decorView)
            controller.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller.hide(WindowInsetsCompat.Type.systemBars())
            onDispose { controller.show(WindowInsetsCompat.Type.systemBars()) }
        }
    }

    // 首次进入自动加载第一部的第一集
    LaunchedEffect(feed.firstOrNull()?.seriesId) {
        val first = feed.firstOrNull() ?: return@LaunchedEffect
        if (drama?.seriesId != first.seriesId) onLoadFirstEpisode(first)
    }

    // 当前页变化：保证只播该剧的第一集
    LaunchedEffect(pagerState.currentPage, feed, drama?.seriesId, currentEpisodeId) {
        val target = feed.getOrNull(pagerState.currentPage) ?: return@LaunchedEffect
        val loadedForTarget = drama?.seriesId == target.seriesId
        val episodesOfTarget = if (loadedForTarget) drama?.episodes.orEmpty() else emptyList()
        val firstEpisodeId = episodesOfTarget.firstOrNull()?.episodeId
        val needFirstEpisode =
            loadedForTarget && episodesOfTarget.isNotEmpty() && currentEpisodeId != firstEpisodeId
        if (!loadedForTarget || needFirstEpisode) onLoadFirstEpisode(target)
    }

    fun burstHeart() {
        scope.launch {
            heartScale.snapTo(0f)
            showHeart = true
            heartScale.animateTo(1f, tween(220, easing = FastOutSlowInEasing))
            delay(260)
            heartScale.animateTo(0f, tween(180))
            showHeart = false
        }
    }

    // 上报当前页，便于从详情页返回时回到同一部剧
    LaunchedEffect(pagerState.currentPage) {
        onPageChanged(pagerState.currentPage)
    }

    // 滑到倒数第二部就预取更多短剧，保证连续下滑不断流
    LaunchedEffect(pagerState.currentPage, feed.size) {
        if (feed.isNotEmpty() && pagerState.currentPage >= feed.lastIndex - 1) onLoadMore()
    }

    // 进度刷新；顺带兜底判定播放结束（部分机型 setOnCompletionListener 不回调）
    LaunchedEffect(playerSeriesId, currentVideoSource) {
        handedOff = false
        while (true) {
            val vv = videoViewRef
            if (vv != null && playerSeriesId == drama?.seriesId) {
                runCatching {
                    positionMs = vv.currentPosition.toLong()
                    durationMs = vv.duration.toLong().coerceAtLeast(0L)
                }
                if (!handedOff && durationMs > 0L && positionMs >= durationMs - 800L) {
                    val series = feed.getOrNull(pagerState.currentPage)
                    if (series != null && series.seriesId == drama?.seriesId) {
                        handedOff = true
                        onOpenDetailNextEpisode(series)
                    }
                }
            }
            delay(400)
        }
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        VerticalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
        ) { page ->
            val series = feed.getOrNull(page) ?: return@VerticalPager
            val firstEpisodeId = drama?.episodes?.firstOrNull()?.episodeId
            val isFirstEpisode = firstEpisodeId == null || firstEpisodeId == currentEpisodeId
            val isActive = page == pagerState.currentPage &&
                series.seriesId == drama?.seriesId && isFirstEpisode
            // 星标直接读收藏列表：以前只从 SharedPreferences 回填，
            // 换个页面收藏就丢了，也没有任何地方能列出收藏过什么。
            val isFavorited = favorites.any { it.seriesId == series.seriesId }

            Box(Modifier.fillMaxSize().background(Color.Black)) {
                AsyncImage(
                    model = series.coverUrl,
                    contentDescription = series.title,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )

                if (isActive && currentVideoSource != null) {
                    androidx.compose.ui.viewinterop.AndroidView(
                        modifier = Modifier.fillMaxSize(),
                        factory = { ctx ->
                            VideoView(ctx).apply {
                                setOnPreparedListener { mp ->
                                    durationMs = mp.duration.toLong()
                                    mp.start()
                                    isPlaying = true
                                }
                                setOnCompletionListener {
                                    isPlaying = false
                                    if (!handedOff) {
                                        handedOff = true
                                        onOpenDetailNextEpisode(series)
                                    }
                                }
                                videoViewRef = this
                                playerSeriesId = series.seriesId
                            }
                        },
                        onRelease = { vv ->
                            if (videoViewRef === vv) videoViewRef = null
                            vv.stopPlayback()
                        },
                        update = { vv ->
                            val path =
                                if (currentVideoSource.isLocal) currentVideoSource.localFilePath
                                else currentVideoSource.videoUrl
                            if (path.isNotEmpty() && vv.tag != path) {
                                vv.tag = path
                                vv.setVideoPath(path)
                            }
                        },
                    )
                }

                // 点击暂停/继续，双击点赞
                Box(
                    Modifier.fillMaxSize().pointerInput(isActive) {
                        detectTapGestures(
                            onTap = {
                                if (isActive) {
                                    val vv = videoViewRef ?: return@detectTapGestures
                                    if (vv.isPlaying) {
                                        vv.pause()
                                        isPlaying = false
                                    } else {
                                        vv.start()
                                        isPlaying = true
                                    }
                                }
                            },
                            onDoubleTap = {
                                // 上游没有短剧点赞数（digg_cnt 恒为 0，也没有点赞写接口），
                                // 以前这里只改一个假数字。这里只保留红心动效，不假装点赞成功。
                                burstHeart()
                            },
                        )
                    },
                )

                if (!isPlaying && isActive && !videoLoading) {
                    Icon(
                        Icons.Filled.PlayArrow,
                        contentDescription = "播放",
                        tint = Color.White.copy(alpha = 0.85f),
                        modifier = Modifier.size(72.dp).align(Alignment.Center),
                    )
                }

                if (videoLoading && isActive) {
                    Box(
                        Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.3f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator(color = SwipeRed)
                    }
                }

                if (videoError.isNotEmpty() && isActive) {
                    Box(
                        Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.55f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        // 只显示一句报错，用户什么都做不了：抖音流那边有「重试播放」，
                        // 这边缺一个，用户只能退回上一页再回来。
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(videoError, color = Color.White, fontSize = 13.sp)
                            TextButton(onClick = onRetryVideo) {
                                Text("重试播放", color = SwipeRed)
                            }
                        }
                    }
                }

                if (showHeart && isActive) {
                    Icon(
                        Icons.Filled.Favorite,
                        contentDescription = null,
                        tint = SwipeRed,
                        modifier = Modifier
                            .align(Alignment.Center)
                            .size((110 * heartScale.value + 20).dp),
                    )
                }

                // 顶部返回与剧名
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回",
                            tint = Color.White,
                        )
                    }
                    Text(
                        series.title,
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    // 「1/19」贴着标题里的「共 104 集」时会被读成「第 1 集 / 共 19 集」，
                    // 但它是滑动的榜单序号（19 部剧），不是集数。加字把两个数分开。
                    Text(
                        "榜单 ${page + 1}/${feed.size}",
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 12.sp,
                    )
                }

                // 右侧操作栏
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 12.dp, bottom = 120.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    // 点赞原来在这里显示一个写死的「1.3 万」，上游根本没有短剧点赞数
                    // （digg_cnt 恒为 0，也没有点赞写接口），每部剧显示的都是同一个假数字。
                    // 现在只留收藏（真的落盘）和分享，点赞手势只剩双击红心。

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        IconButton(onClick = { onToggleFavorite(series) }) {
                            Icon(
                                Icons.Filled.Star,
                                contentDescription = "收藏",
                                tint = if (isFavorited) Color(0xFFFFC107) else Color.White,
                                modifier = Modifier.size(28.dp),
                            )
                        }
                        Text(
                            if (isFavorited) "已收藏" else "收藏",
                            color = Color.White,
                            fontSize = 11.sp,
                        )
                    }

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        IconButton(onClick = {
                            val text = "《${series.title}》${series.episodeCount} 集，来拾页一起看"
                            val intent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, text)
                            }
                            hostContext.startActivity(Intent.createChooser(intent, "分享短剧"))
                        }) {
                            Icon(
                                Icons.Filled.Share,
                                contentDescription = "分享",
                                tint = Color.White,
                                modifier = Modifier.size(26.dp),
                            )
                        }
                        Text("分享", color = Color.White, fontSize = 11.sp)
                    }

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        IconButton(onClick = { onOpenDetailNextEpisode(series) }) {
                            Icon(
                                Icons.Filled.Info,
                                contentDescription = "详情",
                                tint = Color.White,
                                modifier = Modifier.size(26.dp),
                            )
                        }
                        Text("详情", color = Color.White, fontSize = 11.sp)
                    }
                }

                // 底部信息
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth()
                        .background(
                            Brush.verticalGradient(
                                listOf(Color.Transparent, Color.Black.copy(alpha = 0.75f)),
                            ),
                        )
                        .padding(start = 16.dp, end = 90.dp, top = 40.dp, bottom = 28.dp),
                ) {
                    Text(
                        series.author.ifBlank { "拾页短剧" },
                        color = Color.White.copy(alpha = 0.85f),
                        fontSize = 13.sp,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        series.title,
                        color = Color.White,
                        fontSize = 19.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "共 ${series.episodeCount} 集 · 第 1 集播放完自动续播下一集",
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 12.sp,
                    )
                    if (series.intro.isNotBlank()) {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            series.intro,
                            color = Color.White.copy(alpha = 0.6f),
                            fontSize = 12.sp,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }

                    Spacer(Modifier.height(12.dp))
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(2.dp)
                            .clip(RoundedCornerShape(1.dp))
                            .background(Color.White.copy(alpha = 0.25f)),
                    ) {
                        val ratio = if (durationMs > 0) {
                            (positionMs.toFloat() / durationMs).coerceIn(0f, 1f)
                        } else 0f
                        Box(
                            Modifier
                                .fillMaxWidth(ratio)
                                .height(2.dp)
                                .background(Color.White),
                        )
                    }
                }
            }
        }
    }
}

private fun Context.findHostActivity(): Activity? {
    var ctx: Context = this
    while (ctx is ContextWrapper) {
        if (ctx is Activity) return ctx
        ctx = ctx.baseContext
    }
    return null
}
