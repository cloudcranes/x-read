package com.alanmaster.xread.ui

import android.widget.VideoView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import coil.compose.AsyncImage
import com.alanmaster.xread.data.DanmakuItem
import com.alanmaster.xread.data.DramaEpisode
import com.alanmaster.xread.data.DramaPlaySource
import com.alanmaster.xread.data.DramaSeries
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * 短剧与漫剧剧场界面：剧场广场、剧集选集与视频播放、弹幕浮层与互动。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DramaScreen(
    feed: List<DramaSeries>,
    feedLoading: Boolean,
    currentDrama: DramaSeries?,
    currentEpisode: DramaEpisode?,
    currentVideoSource: DramaPlaySource?,
    videoLoading: Boolean,
    videoError: String,
    danmakuList: List<DanmakuItem>,
    onBack: () -> Unit,
    onSelectDrama: (DramaSeries) -> Unit,
    onSelectEpisode: (DramaEpisode) -> Unit,
    onSendDanmaku: (String, Long) -> Unit,
    onRefreshFeed: () -> Unit,
    onSwitchToDouyin: () -> Unit = {},
    favorites: List<DramaSeries> = emptyList(),
    onToggleFavorite: (DramaSeries) -> Unit = {},
) {
    if (currentDrama == null) {
        // ------------------ 剧场广场列表模式 ------------------
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text("短剧 · 漫剧剧场", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            Text("热播好剧 · 动态漫剧 · 弹幕互动", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.Filled.ArrowBack, contentDescription = "返回")
                        }
                    },
                    actions = {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = Color(0xFFFF2C55).copy(alpha = 0.15f),
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { onSwitchToDouyin() }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(Icons.Filled.Tv, contentDescription = null, tint = Color(0xFFFF2C55), modifier = Modifier.size(14.dp))
                                Text("抖音流", color = Color(0xFFFF2C55), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                        IconButton(onClick = onRefreshFeed) {
                            Icon(Icons.Filled.Refresh, contentDescription = "刷新")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                    )
                )
            }
        ) { padding ->
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                if (feedLoading && feed.isEmpty()) {
                    CircularProgressIndicator(Modifier.align(Alignment.Center))
                } else if (feed.isEmpty()) {
                    Column(
                        Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(Space.sm),
                    ) {
                        Icon(Icons.Filled.Tv, contentDescription = null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.outline)
                        Text("暂无剧场推荐内容", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        TextButton(onClick = onRefreshFeed) {
                            Text("点击重试")
                        }
                    }
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        contentPadding = PaddingValues(horizontal = Space.gutter, vertical = Space.sm),
                        horizontalArrangement = Arrangement.spacedBy(Space.sm),
                        verticalArrangement = Arrangement.spacedBy(Space.md),
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        items(feed, key = { it.seriesId }) { item ->
                            DramaCard(drama = item, onClick = { onSelectDrama(item) })
                        }
                    }
                }
            }
        }
    } else {
        // ------------------ 剧集播放与选集详情模式 ------------------
        DramaPlayerScreen(
            drama = currentDrama,
            episode = currentEpisode,
            source = currentVideoSource,
            loading = videoLoading,
            error = videoError,
            danmakuList = danmakuList,
            onBack = onBack,
            onSelectEpisode = onSelectEpisode,
            onSendDanmaku = onSendDanmaku,
            onSwitchToDouyin = onSwitchToDouyin,
            isFavorited = currentDrama?.let { d -> favorites.any { it.seriesId == d.seriesId } } == true,
            onToggleFavorite = { currentDrama?.let(onToggleFavorite) },
        )
    }
}

/** 广场单部剧集卡片 */
@Composable
internal fun DramaCard(
    drama: DramaSeries,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(0.72f)
                .clip(RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
        ) {
            if (drama.coverUrl.isNotEmpty()) {
                AsyncImage(
                    model = drama.coverUrl,
                    contentDescription = drama.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Icon(
                    Icons.Filled.Movie,
                    contentDescription = null,
                    modifier = Modifier
                        .size(40.dp)
                        .align(Alignment.Center),
                    tint = MaterialTheme.colorScheme.outline,
                )
            }

            // 标签（短剧 / 漫剧）
            Surface(
                color = if (drama.isMotionComic) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary,
                shape = RoundedCornerShape(bottomEnd = 6.dp),
                modifier = Modifier.align(Alignment.TopStart),
            ) {
                Text(
                    text = if (drama.isMotionComic) "漫剧" else "短剧",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                )
            }

            // 底部集数遮罩
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Transparent, Color.Black.copy(alpha = 0.7f))
                        )
                    )
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(
                    text = if (drama.episodeCount > 0) "全 ${drama.episodeCount} 集" else "连载中",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White,
                )
            }
        }

        Column(
            modifier = Modifier.padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = drama.title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (drama.intro.isNotEmpty()) {
                Text(
                    text = drama.intro,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** 播放与选集详情页 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DramaPlayerScreen(
    drama: DramaSeries,
    episode: DramaEpisode?,
    source: DramaPlaySource?,
    loading: Boolean,
    error: String,
    danmakuList: List<DanmakuItem>,
    onBack: () -> Unit,
    onSelectEpisode: (DramaEpisode) -> Unit,
    onSendDanmaku: (String, Long) -> Unit,
    onSwitchToDouyin: () -> Unit = {},
    isFavorited: Boolean = false,
    onToggleFavorite: () -> Unit = {},
) {
    var videoViewRef by remember { mutableStateOf<VideoView?>(null) }
    var isPlaying by remember { mutableStateOf(false) }
    var currentPositionMs by remember { mutableLongStateOf(0L) }
    var durationMs by remember { mutableLongStateOf(0L) }
    var showControls by remember { mutableStateOf(true) }
    var danmakuEnabled by remember { mutableStateOf(true) }
    var inputText by remember { mutableStateOf("") }

    // 播放位置轮询更新
    LaunchedEffect(videoViewRef, isPlaying) {
        while (true) {
            videoViewRef?.let { vv ->
                if (vv.isPlaying) {
                    currentPositionMs = vv.currentPosition.toLong()
                    val dur = vv.duration.toLong()
                    if (dur > 0) durationMs = dur
                }
            }
            delay(250)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(drama.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(episode?.title ?: "选集播放", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    // 收藏：另两个播放页都有，这里补上，不然在这个剧场页收藏不了这部剧。
                    IconButton(
                        onClick = { onToggleFavorite() },
                    ) {
                        Icon(
                            Icons.Filled.Star,
                            contentDescription = if (isFavorited) "取消收藏" else "收藏",
                            tint = if (isFavorited) Color(0xFFFFC107) else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFFFF2C55).copy(alpha = 0.15f),
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { onSwitchToDouyin() }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(Icons.Filled.Tv, contentDescription = null, tint = Color(0xFFFF2C55), modifier = Modifier.size(14.dp))
                            Text("抖音全屏", color = Color(0xFFFF2C55), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                )
            )
        }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            /* -------------------- 视频播放区域 (16:9) -------------------- */
            Box(
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .background(Color.Black)
                    .clickable { showControls = !showControls }
            ) {
                // 原生 VideoView 播放核心
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { context ->
                        VideoView(context).apply {
                            setOnPreparedListener { mp ->
                                durationMs = mp.duration.toLong()
                                mp.start()
                                isPlaying = true
                            }
                            setOnCompletionListener {
                                isPlaying = false
                            }
                            videoViewRef = this
                        }
                    },
                    update = { view ->
                        videoViewRef = view
                        if (source != null) {
                            val path = if (source.isLocal) source.localFilePath else source.videoUrl
                            if (path.isNotEmpty() && view.tag != path) {
                                view.tag = path
                                view.setVideoPath(path)
                            }
                        }
                    }
                )

                // ------------------ 弹幕浮层 ------------------
                if (danmakuEnabled && danmakuList.isNotEmpty()) {
                    DanmakuOverlay(
                        currentPositionMs = currentPositionMs,
                        danmakuList = danmakuList,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                // ------------------ 加载与错误提示 ------------------
                if (loading) {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.5f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator(color = Color.White)
                    }
                } else if (error.isNotEmpty()) {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.8f))
                            .padding(Space.md),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(Space.sm),
                        ) {
                            Text(error, style = MaterialTheme.typography.bodyMedium, color = Color.White)
                            TextButton(onClick = { episode?.let(onSelectEpisode) }) {
                                Text("重试播放", color = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }

                // ------------------ 播放控制覆盖层 ------------------
                if (showControls) {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.35f))
                    ) {
                        // 中间播放/暂停按钮
                        IconButton(
                            onClick = {
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
                            modifier = Modifier
                                .size(56.dp)
                                .align(Alignment.Center)
                                .background(Color.Black.copy(alpha = 0.6f), CircleShape)
                        ) {
                            Icon(
                                if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                contentDescription = if (isPlaying) "暂停" else "播放",
                                tint = Color.White,
                                modifier = Modifier.size(36.dp),
                            )
                        }

                        // 底部横条：进度条与弹幕开关
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .align(Alignment.BottomCenter)
                                .padding(horizontal = Space.sm, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                formatTime(currentPositionMs),
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White,
                            )
                            Slider(
                                value = if (durationMs > 0) (currentPositionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f,
                                onValueChange = { frac ->
                                    val target = (frac * durationMs).toLong()
                                    currentPositionMs = target
                                    videoViewRef?.seekTo(target.toInt())
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(horizontal = 8.dp),
                                colors = SliderDefaults.colors(
                                    thumbColor = MaterialTheme.colorScheme.primary,
                                    activeTrackColor = MaterialTheme.colorScheme.primary,
                                    inactiveTrackColor = Color.White.copy(alpha = 0.3f),
                                )
                            )
                            Text(
                                formatTime(durationMs),
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White,
                            )
                            IconButton(onClick = { danmakuEnabled = !danmakuEnabled }) {
                                Icon(
                                    Icons.Filled.Subtitles,
                                    contentDescription = "弹幕开关",
                                    tint = if (danmakuEnabled) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.5f),
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                        }
                    }
                }
            }

            /* -------------------- 弹幕输入条 -------------------- */
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Space.gutter, vertical = Space.xs),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Space.sm),
            ) {
                Box(
                    Modifier
                        .weight(1f)
                        .height(36.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                        .padding(horizontal = 12.dp),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    if (inputText.isEmpty()) {
                        Text("发条弹幕互动一下...", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                    }
                    BasicTextField(
                        value = inputText,
                        onValueChange = { inputText = it },
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurface),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                        keyboardActions = KeyboardActions(onSend = {
                            if (inputText.isNotBlank()) {
                                onSendDanmaku(inputText, currentPositionMs)
                                inputText = ""
                            }
                        }),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                IconButton(
                    onClick = {
                        if (inputText.isNotBlank()) {
                            onSendDanmaku(inputText, currentPositionMs)
                            inputText = ""
                        }
                    },
                    modifier = Modifier.size(36.dp),
                ) {
                    Icon(Icons.Filled.Send, contentDescription = "发送", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                }
            }

            /* -------------------- 选集与详情区域 -------------------- */
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentPadding = PaddingValues(bottom = Space.xl),
            ) {
                // 选集标题
                item {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = Space.gutter, vertical = Space.sm),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text("选集", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                        Text(
                            text = "共 ${drama.episodes.size.coerceAtLeast(drama.episodeCount)} 集",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                // 选集横滑条
                item {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = Space.gutter),
                        horizontalArrangement = Arrangement.spacedBy(Space.sm),
                    ) {
                        items(drama.episodes, key = { it.episodeId }) { ep ->
                            val isCurrent = ep.episodeId == episode?.episodeId
                            EpisodeChip(
                                episode = ep,
                                isSelected = isCurrent,
                                onClick = { onSelectEpisode(ep) }
                            )
                        }
                    }
                }

                // 剧集简介卡片
                item {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = Space.gutter, vertical = Space.md)
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
                            .padding(Space.sm),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(drama.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Medium)
                        if (drama.author.isNotEmpty()) {
                            Text("主创/作者：${drama.author}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                        }
                        if (drama.intro.isNotEmpty()) {
                            Text(
                                drama.intro,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}

/** 选集胶囊按钮 */
@Composable
private fun EpisodeChip(
    episode: DramaEpisode,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(8.dp),
        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        border = if (isSelected) androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary) else null,
        modifier = Modifier.height(44.dp),
    ) {
        Box(
            modifier = Modifier.padding(horizontal = 14.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "${episode.episodeIndex}",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

/** 弹幕动态浮层渲染 */
@Composable
private fun DanmakuOverlay(
    currentPositionMs: Long,
    danmakuList: List<DanmakuItem>,
    modifier: Modifier = Modifier,
) {
    // 筛选当前时间前 4 秒至当前时间的弹幕进行渲染（每条弹幕飞 4 秒）
    val activeDanmakus = remember(currentPositionMs, danmakuList) {
        danmakuList.filter {
            currentPositionMs >= it.offsetMs && currentPositionMs <= it.offsetMs + 4000L
        }
    }

    Box(modifier = modifier) {
        activeDanmakus.forEachIndexed { index, item ->
            // 进度 0.0 ~ 1.0 从右侧 1.0f 飞到左侧 -0.2f
            val progress = ((currentPositionMs - item.offsetMs).toFloat() / 4000f).coerceIn(0f, 1f)
            val trackIndex = (index % 3) // 3 条弹幕轨道避免重叠
            val yOffset = 16.dp + (trackIndex * 26).dp

            Box(
                Modifier
                    .fillMaxWidth()
                    .padding(top = yOffset)
            ) {
                Text(
                    text = item.text,
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = Color.White,
                        fontWeight = FontWeight.Medium,
                    ),
                    modifier = Modifier
                        .offset {
                            // 从全屏右侧往左飞
                            val screenWidth = 1000 // 估算轨道像素宽度
                            val x = (screenWidth * (1f - progress * 1.3f)).roundToInt()
                            IntOffset(x, 0)
                        }
                        .background(Color.Black.copy(alpha = 0.4f), RoundedCornerShape(4.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
        }
    }
}

private fun formatTime(ms: Long): String {
    val totalSec = (ms / 1000).coerceAtLeast(0)
    val min = totalSec / 60
    val sec = totalSec % 60
    // 固定 Locale.US：默认 Locale 在 ar/fa/bn 下会把数字换成别的记数系统，
    // 时长看起来就像乱码。
    return String.format(java.util.Locale.US, "%02d:%02d", min, sec)
}
