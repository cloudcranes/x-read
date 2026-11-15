package com.alanmaster.xread.ui

import android.widget.VideoView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.foundation.lazy.staggeredgrid.items as staggeredItems
import androidx.compose.material.icons.filled.Search
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
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
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.SolidColor
import coil.request.ImageRequest
import coil.imageLoader
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import coil.compose.AsyncImage
import com.alanmaster.xread.data.BookHotReview
import com.alanmaster.xread.data.DanmakuItem
import com.alanmaster.xread.data.DramaEpisode
import com.alanmaster.xread.data.DramaPlaySource
import com.alanmaster.xread.data.DramaSeries
import com.alanmaster.xread.data.DramaSubscription
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 短剧与漫剧剧场界面 剧场广场、剧集选集与视频播放、弹幕浮层与互动。
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
    subscriptions: List<DramaSubscription> = emptyList(),
    onToggleSubscription: (DramaSeries) -> Unit = {},
    related: List<DramaSeries> = emptyList(),
    comments: List<BookHotReview> = emptyList(),
    commentTotal: Int = 0,
    commentsLoading: Boolean = false,
    commentsHasMore: Boolean = false,
    commentSending: Boolean = false,
    onLoadComments: () -> Unit = {},
    onLoadMoreComments: () -> Unit = {},
    onSendComment: (String) -> Unit = {},
    onToggleCommentDigg: (BookHotReview) -> Unit = {},
    onSelectRelated: (DramaSeries) -> Unit = {},
    moreLoading: Boolean = false,
    feedExhausted: Boolean = true,
    onLoadMore: () -> Unit = {},
    onSearch: () -> Unit = {},
) {
    if (currentDrama == null) {
        // ------------------ 剧场广场列表模式 ------------------
        val categories = remember(feed) {
            buildList {
                add("推荐")
                add("热播")
                if (feed.any { !it.isMotionComic }) add("短剧")
                if (feed.any { it.isMotionComic }) add("漫剧")
                if (feed.any { it.episodeCount >= 300 }) add("长篇")
                if (feed.any { it.episodeCount in 1..299 }) add("短篇")
                feed.flatMap { it.tags }
                    .filter { it.isNotBlank() && it.length in 2..6 }
                    .groupingBy { it }
                    .eachCount()
                    .entries
                    .sortedByDescending { it.value }
                    .take(8)
                    .forEach { if (!contains(it.key)) add(it.key) }
            }
        }
        var selectedCategory by remember { mutableStateOf(categories.first()) }
        LaunchedEffect(categories) {
            if (selectedCategory !in categories) selectedCategory = categories.first()
        }
        val visibleFeed = remember(feed, selectedCategory) {
            when (selectedCategory) {
                "推荐" -> feed
                "热播" -> feed.sortedByDescending { it.episodeCount }
                "短剧" -> feed.filter { !it.isMotionComic }
                "漫剧" -> feed.filter { it.isMotionComic }
                "长篇" -> feed.filter { it.episodeCount >= 300 }
                "短篇" -> feed.filter { it.episodeCount in 1..299 }
                else -> feed.filter { item ->
                    item.tags.any { it.contains(selectedCategory) } ||
                        item.title.contains(selectedCategory) ||
                        item.intro.contains(selectedCategory)
                }
            }
        }
        val context = LocalContext.current
        LaunchedEffect(visibleFeed.take(8)) {
            val loader = context.imageLoader
            visibleFeed.take(8).forEach { drama ->
                if (drama.coverUrl.isNotBlank()) {
                    loader.enqueue(ImageRequest.Builder(context).data(drama.coverUrl).build())
                }
            }
        }
        val gridState = rememberLazyStaggeredGridState()
        val shouldLoadMore by remember {
            derivedStateOf {
                val info = gridState.layoutInfo
                val last = info.visibleItemsInfo.lastOrNull()?.index ?: return@derivedStateOf false
                last >= info.totalItemsCount - 4
            }
        }
        LaunchedEffect(shouldLoadMore, feedExhausted, moreLoading) {
            if (shouldLoadMore && !feedExhausted && !moreLoading) onLoadMore()
        }
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text("短剧", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                            Text(
                                if (feed.isNotEmpty()) "共 ${feed.size} 部" else "",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
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
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                DramaSearchEntry(onClick = onSearch)
                LazyRow(
                    contentPadding = PaddingValues(horizontal = Space.gutter),
                    horizontalArrangement = Arrangement.spacedBy(Space.xs),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = Space.sm),
                ) {
                    items(categories, key = { it }) { category ->
                        DramaCategoryChip(
                            label = category,
                            selected = category == selectedCategory,
                            onClick = { selectedCategory = category },
                        )
                    }
                }

                when {
                    feedLoading && feed.isEmpty() -> {
                        DramaHomeSkeleton()
                    }

                    feed.isEmpty() -> {
                        Column(
                            Modifier.fillMaxSize(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                        ) {
                            Icon(Icons.Filled.Tv, contentDescription = null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.outline)
                            Text("暂无短剧", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            TextButton(onClick = onRefreshFeed) {
                                Text("重试")
                            }
                        }
                    }

                    else -> {
                        val hero = visibleFeed.firstOrNull()
                        LazyVerticalStaggeredGrid(
                            state = gridState,
                            columns = StaggeredGridCells.Fixed(2),
                            contentPadding = PaddingValues(horizontal = Space.gutter, vertical = Space.sm),
                            horizontalArrangement = Arrangement.spacedBy(Space.sm),
                            verticalItemSpacing = Space.md,
                            modifier = Modifier.weight(1f),
                        ) {
                            if (hero != null) {
                                item(span = androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan.FullLine) {
                                    DramaHeroBanner(drama = hero, onClick = { onSelectDrama(hero) })
                                }
                            }
                            staggeredItems(visibleFeed.drop(1), key = { it.seriesId }) { item ->
                                DramaCard(
                                    drama = item,
                                    onClick = { onSelectDrama(item) },
                                    coverAspectRatio = dramaCoverAspectRatio(item.seriesId),
                                )
                            }
                            item(span = androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan.FullLine) {
                                Box(
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = Space.md),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    if (moreLoading) {
                                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.xs)) {
                                            CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                                            Text("加载中", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    } else if (feedExhausted) {
                                        Text("共 ${visibleFeed.size} 部", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    } else {
                                        Text("继续上滑", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    } else {
        // ------------------ 剧集播放与选集详情模式 ------------------
        // else 分支里 currentDrama 已被收窄为非空，这里再取一份局部值，
        // 后面就不用写 ?. 了。
        val drama = currentDrama
        DramaPlayerScreen(
            drama = drama,
            episode = currentEpisode,
            source = currentVideoSource,
            loading = videoLoading,
            error = videoError,
            danmakuList = danmakuList,
            onBack = onBack,
            onSelectEpisode = onSelectEpisode,
            onSendDanmaku = onSendDanmaku,
            onSwitchToDouyin = onSwitchToDouyin,
            isFavorited = favorites.any { it.seriesId == drama.seriesId },
            onToggleFavorite = { onToggleFavorite(drama) },
            isSubscribed = subscriptions.any { it.series.seriesId == drama.seriesId },
            onToggleSubscription = { onToggleSubscription(drama) },
            related = related,
            comments = comments,
            commentTotal = commentTotal,
            commentsLoading = commentsLoading,
            commentsHasMore = commentsHasMore,
            commentSending = commentSending,
            onLoadComments = onLoadComments,
            onLoadMoreComments = onLoadMoreComments,
            onSendComment = onSendComment,
            onToggleCommentDigg = onToggleCommentDigg,
            onSelectRelated = onSelectRelated,
        )
    }
}

/** 红果式搜索入口 点击进入搜索页。 */
@Composable
private fun DramaHomeSkeleton() {
    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = Space.gutter),
        verticalArrangement = Arrangement.spacedBy(Space.md),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(168.dp)
                .background(
                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                    RoundedCornerShape(12.dp),
                ),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
            repeat(2) {
                Box(
                    Modifier
                        .weight(1f)
                        .height(220.dp)
                        .background(
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                            RoundedCornerShape(10.dp),
                        ),
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
            repeat(2) {
                Box(
                    Modifier
                        .weight(1f)
                        .height(180.dp)
                        .background(
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                            RoundedCornerShape(10.dp),
                        ),
                )
            }
        }
        Text(
            text = "加载中",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.align(Alignment.CenterHorizontally),
        )
    }
}

@Composable
private fun DramaSearchEntry(onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(21.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Space.gutter, vertical = Space.sm)
            .height(42.dp)
            .clip(RoundedCornerShape(21.dp))
            .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Space.xs),
        ) {
            Icon(Icons.Filled.Search, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
            Text("搜索短剧", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** 红果式频道 chip。 */
@Composable
private fun DramaCategoryChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(50),
        color = if (selected) Color(0xFFFF2C55) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .clickable(onClick = onClick),
    ) {
        Text(
            text = label,
            fontSize = 12.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            color = if (selected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
        )
    }
}

/** 红果式焦点大图 首部剧集作为推荐位。 */
@Composable
private fun DramaHeroBanner(drama: DramaSeries, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(168.dp)
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        if (drama.coverUrl.isNotEmpty()) {
            AsyncImage(
                model = drama.coverUrl,
                contentDescription = drama.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Transparent, Color.Black.copy(alpha = 0.15f), Color.Black.copy(alpha = 0.86f))
                    )
                )
        )
        Surface(
            color = Color(0xFFFF2C55),
            shape = RoundedCornerShape(bottomEnd = 10.dp),
            modifier = Modifier.align(Alignment.TopStart),
        ) {
            Text(
                "推荐",
                color = Color.White,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
            )
        }
        Column(
            Modifier
                .align(Alignment.BottomStart)
                .padding(horizontal = 14.dp, vertical = 12.dp)
                .padding(end = 112.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = drama.title,
                color = Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (drama.episodeCount > 0) {
                    Text(
                        "全 ${drama.episodeCount} 集",
                        color = Color.White.copy(alpha = 0.82f),
                        fontSize = 12.sp,
                    )
                }
                Text(
                    if (drama.isMotionComic) "动态漫剧" else "热播短剧",
                    color = Color.White.copy(alpha = 0.82f),
                    fontSize = 12.sp,
                )
            }
        }
        Surface(
            color = Color(0xFFFF2C55),
            shape = RoundedCornerShape(50),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(14.dp),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Icon(Icons.Filled.PlayArrow, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                Text("播放", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

/** 瀑布流封面比例 按 seriesId 稳定散列到 0.62~0.82，避免相邻卡片高度一致。 */
private fun dramaCoverAspectRatio(seriesId: String): Float {
    val bucket = (seriesId.hashCode() and 0x7fffffff) % 4
    return when (bucket) {
        0 -> 0.62f
        1 -> 0.72f
        2 -> 0.78f
        else -> 0.84f
    }
}

/** 广场单部剧集卡片 红果式海报卡，标题与集数放在海报下方。 */
@Composable
internal fun DramaCard(
    drama: DramaSeries,
    onClick: () -> Unit,
    coverAspectRatio: Float = 0.72f,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(coverAspectRatio)
                .clip(RoundedCornerShape(10.dp))
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

            Surface(
                color = if (drama.isMotionComic) Color(0xFF6C5CE7) else Color(0xFFFF2C55),
                shape = RoundedCornerShape(bottomEnd = 8.dp),
                modifier = Modifier.align(Alignment.TopStart),
            ) {
                Text(
                    text = if (drama.isMotionComic) "漫剧" else "短剧",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White,
                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                )
            }
        }

        Text(
            text = drama.title,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 6.dp, start = 2.dp, end = 2.dp),
        )
        Text(
            text = if (drama.episodeCount > 0) "全 ${drama.episodeCount} 集" else "连载中",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            modifier = Modifier.padding(top = 2.dp, start = 2.dp, end = 2.dp, bottom = 2.dp),
        )
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
    isSubscribed: Boolean = false,
    onToggleSubscription: () -> Unit = {},
    related: List<DramaSeries> = emptyList(),
    comments: List<BookHotReview> = emptyList(),
    commentTotal: Int = 0,
    commentsLoading: Boolean = false,
    commentsHasMore: Boolean = false,
    commentSending: Boolean = false,
    onLoadComments: () -> Unit = {},
    onLoadMoreComments: () -> Unit = {},
    onSendComment: (String) -> Unit = {},
    onToggleCommentDigg: (BookHotReview) -> Unit = {},
    onSelectRelated: (DramaSeries) -> Unit = {},
) {
    var videoViewRef by remember { mutableStateOf<VideoView?>(null) }
    var isPlaying by remember { mutableStateOf(false) }
    var currentPositionMs by remember { mutableLongStateOf(0L) }
    val danmakuPositionState = remember { mutableLongStateOf(0L) }
    var danmakuPositionMs by danmakuPositionState
    var durationMs by remember { mutableLongStateOf(0L) }
    var showControls by remember { mutableStateOf(true) }
    var danmakuEnabled by remember { mutableStateOf(true) }
    var inputText by remember { mutableStateOf("") }
    var showComments by remember { mutableStateOf(false) }
    var showEpisodePicker by remember(drama.seriesId) { mutableStateOf(false) }
    var introExpanded by remember(drama.seriesId) { mutableStateOf(false) }
    // AndroidView 的 factory 只执行一次，回调里要读最新的剧集列表与回调，不能捕获首帧闭包。
    val currentEpisodeState by rememberUpdatedState(episode)
    val currentDramaState by rememberUpdatedState(drama)
    val selectEpisodeState by rememberUpdatedState(onSelectEpisode)

    // 从「相关推荐」切到另一部剧时，先把上一部剧的剧评弹层收起来，避免展示旧列表。
    LaunchedEffect(drama.seriesId) {
        showComments = false
        showEpisodePicker = false
        introExpanded = false
    }

    // 播放位置轮询更新（仅用于时间/进度条）
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

    // 弹幕用逐帧时钟 每帧只更新一个 State，DanmakuFlowLayer 在 graphicsLayer 内读取，
    // 不触发整页重组，弹幕位移才能跟满刷新率。
    LaunchedEffect(videoViewRef, danmakuEnabled, danmakuList.isNotEmpty()) {
        if (!danmakuEnabled || danmakuList.isEmpty()) return@LaunchedEffect
        while (true) {
            withFrameNanos {
                videoViewRef?.let { vv ->
                    if (vv.isPlaying) danmakuPositionMs = vv.currentPosition.toLong()
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(drama.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(episode?.title ?: "选集", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        showComments = true
                        onLoadComments()
                    }) {
                        Icon(Icons.Filled.ChatBubbleOutline, contentDescription = "剧评")
                    }
                    // 订阅 上游没有关注写接口，订阅关系落在本机，书架「我的订阅」可查。
                    IconButton(onClick = { onToggleSubscription() }) {
                        Icon(
                            if (isSubscribed) Icons.Filled.NotificationsActive else Icons.Filled.Notifications,
                            contentDescription = if (isSubscribed) "取消订阅" else "订阅",
                            tint = if (isSubscribed) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    // 收藏 另两个播放页都有，这里补上，不然在这个剧场页收藏不了这部剧。
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
                            Text("全屏", color = Color(0xFFFF2C55), fontSize = 12.sp, fontWeight = FontWeight.Bold)
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
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onTap = { showControls = !showControls },
                            onDoubleTap = { offset ->
                                val delta = if (offset.x < size.width / 2f) -10_000L else 10_000L
                                val max = durationMs.takeIf { it > 0 } ?: Long.MAX_VALUE
                                val target = (currentPositionMs + delta).coerceIn(0L, max)
                                videoViewRef?.seekTo(target.toInt())
                                currentPositionMs = target
                                showControls = true
                            },
                        )
                    }
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
                                val list = currentDramaState.episodes
                                val index = list.indexOfFirst {
                                    it.episodeId == currentEpisodeState?.episodeId
                                }
                                val next = if (index >= 0) list.getOrNull(index + 1) else null
                                if (next != null) selectEpisodeState(next)
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

                // 未点播放前先展示封面，不自动起播；点封面或播放键才加载第一集。
                if (source == null && !loading && error.isEmpty()) {
                    if (drama.coverUrl.isNotEmpty()) {
                        AsyncImage(
                            model = drama.coverUrl,
                            contentDescription = drama.title,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                    Box(
                        Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.45f))
                    )
                    Column(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(Space.sm),
                    ) {
                        IconButton(
                            onClick = { drama.episodes.firstOrNull()?.let(onSelectEpisode) },
                            modifier = Modifier
                                .size(64.dp)
                                .background(Color.Black.copy(alpha = 0.55f), CircleShape),
                        ) {
                            Icon(
                                Icons.Filled.PlayArrow,
                                contentDescription = "播放第一集",
                                tint = Color.White,
                                modifier = Modifier.size(40.dp),
                            )
                        }
                        Text(
                            if (drama.episodes.isEmpty()) "暂无剧集" else "播放",
                            style = MaterialTheme.typography.labelMedium,
                            color = Color.White,
                        )
                    }
                }

                // ------------------ 弹幕浮层 ------------------
                if (danmakuEnabled && danmakuList.isNotEmpty()) {
                    DanmakuFlowLayer(
                        positionMs = danmakuPositionState,
                        items = danmakuList,
                        modifier = Modifier.fillMaxSize(),
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
                                Text("重试", color = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }

                // ------------------ 播放控制覆盖层 ------------------
                // 详情首帧由封面上的播放入口负责；没有视频源时不要显示控制层，
                // 否则中间播放键会先拦截点击并把空 VideoView 置成“播放中”。
                if (showControls && !(source == null && !loading && error.isEmpty())) {
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

                        // 底部横条 进度条与弹幕开关
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
                        Text("发弹幕", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
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
                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "发送", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
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
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "共 ${drama.episodes.size.coerceAtLeast(drama.episodeCount)} 集" +
                                    if (drama.episodes.size > 1) " 自动续播" else "",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            if (drama.episodes.size > 8) {
                                TextButton(onClick = { showEpisodePicker = true }, contentPadding = PaddingValues(horizontal = Space.sm)) {
                                    Text("全部", style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
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
                            Text("作者 ${drama.author}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                        }
                        if (drama.intro.isNotEmpty()) {
                            Text(
                                drama.intro,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = if (introExpanded) Int.MAX_VALUE else 3,
                                overflow = TextOverflow.Ellipsis,
                            )
                            if (drama.intro.length > 80) {
                                Text(
                                    if (introExpanded) "收起" else "展开全部",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .clickable { introExpanded = !introExpanded }
                                        .padding(vertical = 2.dp),
                                )
                            }
                        }
                    }
                }

                // 剧评入口
                item {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = Space.gutter)
                            .clip(RoundedCornerShape(8.dp))
                            .clickable {
                                showComments = true
                                onLoadComments()
                            }
                            .padding(horizontal = Space.sm, vertical = Space.sm),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text("剧评", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (commentTotal > 0) {
                                Text(
                                    "${formatCount(commentTotal)} 条",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Icon(
                                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }

                // 相关推荐
                if (related.isNotEmpty()) {
                    item {
                        Text(
                            "相关推荐",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = Space.gutter, vertical = Space.sm),
                        )
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = Space.gutter),
                            horizontalArrangement = Arrangement.spacedBy(Space.sm),
                        ) {
                            items(related, key = { it.seriesId }) { item ->
                                Box(Modifier.width(120.dp)) {
                                    DramaCard(drama = item, onClick = { onSelectRelated(item) })
                                }
                            }
                        }
                    }
                }
            }

            if (showComments) {
                DramaCommentsSheet(
                    comments = comments,
                    total = commentTotal,
                    loading = commentsLoading,
                    hasMore = commentsHasMore,
                    sending = commentSending,
                    onDismiss = { showComments = false },
                    onLoadMore = onLoadMoreComments,
                    onSend = onSendComment,
                    onToggleDigg = onToggleCommentDigg,
                )
            }
            if (showEpisodePicker) {
                EpisodePickerSheet(
                    drama = drama,
                    currentEpisodeId = episode?.episodeId,
                    onSelect = {
                        showEpisodePicker = false
                        onSelectEpisode(it)
                    },
                    onDismiss = { showEpisodePicker = false },
                )
            }
        }
    }
}

/** 短剧剧评弹层 列表、点赞与发评。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DramaCommentsSheet(
    comments: List<BookHotReview>,
    total: Int,
    loading: Boolean,
    hasMore: Boolean,
    sending: Boolean,
    onDismiss: () -> Unit,
    onLoadMore: () -> Unit,
    onSend: (String) -> Unit,
    onToggleDigg: (BookHotReview) -> Unit,
) {
    var input by remember { mutableStateOf("") }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.72f)
                .padding(horizontal = Space.gutter),
        ) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("剧评", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    if (total > 0) "${formatCount(total)} 条" else "",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(Space.xs))
            if (loading && comments.isEmpty()) {
                Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (comments.isEmpty()) {
                Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                    Text("暂无剧评", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    verticalArrangement = Arrangement.spacedBy(Space.sm),
                    contentPadding = PaddingValues(vertical = Space.sm),
                ) {
                    itemsIndexed(comments) { _, comment ->
                        Row(Modifier.fillMaxWidth()) {
                            Box(
                                Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.surfaceVariant),
                                contentAlignment = Alignment.Center,
                            ) {
                                if (comment.avatar.isNotEmpty()) {
                                    AsyncImage(
                                        model = comment.avatar,
                                        contentDescription = null,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize(),
                                    )
                                } else {
                                    Text(
                                        comment.user.take(1).ifEmpty { "友" },
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                            Spacer(Modifier.width(Space.sm))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    comment.user.ifEmpty { "匿名用户" },
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Text(comment.text, style = MaterialTheme.typography.bodySmall)
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    TextButton(onClick = { onToggleDigg(comment) }) {
                                        Text(
                                            if (comment.userLiked) "已赞 ${formatCount(comment.digg)}" else "赞 ${formatCount(comment.digg)}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = if (comment.userLiked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                    if (comment.replyCount > 0) {
                                        Text(
                                            "${comment.replyCount} 回复",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.outline,
                                        )
                                    }
                                }
                            }
                        }
                    }
                    if (hasMore) {
                        item {
                            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                                TextButton(onClick = onLoadMore) { Text("加载更多") }
                            }
                        }
                    }
                }
            }
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = Space.sm),
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
                    if (input.isEmpty()) {
                        Text("写剧评", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                    }
                    BasicTextField(
                        value = input,
                        onValueChange = { input = it },
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurface),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                TextButton(
                    enabled = input.isNotBlank() && !sending,
                    onClick = {
                        val text = input.trim()
                        if (text.isNotEmpty()) {
                            onSend(text)
                            input = ""
                        }
                    },
                ) { Text(if (sending) "发送中" else "发送") }
            }
        }
    }
}

/** 选集全量面板 一屏查看全部集数，避免横向滑动找集。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EpisodePickerSheet(
    drama: DramaSeries,
    currentEpisodeId: String?,
    onSelect: (DramaEpisode) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.72f)
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Space.gutter, vertical = Space.sm),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("选集", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(
                    "共 ${drama.episodes.size.coerceAtLeast(drama.episodeCount)} 集",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            val currentIndex = drama.episodes.indexOfFirst { it.episodeId == currentEpisodeId }
            val gridState = rememberLazyGridState(initialFirstVisibleItemIndex = (currentIndex - 2).coerceAtLeast(0))
            LazyVerticalGrid(
                state = gridState,
                columns = GridCells.Fixed(5),
                contentPadding = PaddingValues(horizontal = Space.gutter, vertical = Space.sm),
                horizontalArrangement = Arrangement.spacedBy(Space.sm),
                verticalArrangement = Arrangement.spacedBy(Space.sm),
                modifier = Modifier.fillMaxSize(),
            ) {
                gridItems(drama.episodes, key = { it.episodeId }) { ep ->
                    val selected = ep.episodeId == currentEpisodeId
                    Surface(
                        onClick = { onSelect(ep) },
                        shape = RoundedCornerShape(8.dp),
                        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        border = if (selected) androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary) else null,
                        modifier = Modifier.height(44.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                "${ep.episodeIndex}",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
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

private fun formatTime(ms: Long): String {
    val totalSec = (ms / 1000).coerceAtLeast(0)
    val min = totalSec / 60
    val sec = totalSec % 60
    // 固定 Locale.US 默认 Locale 在 ar/fa/bn 下会把数字换成别的记数系统，
    // 时长看起来就像乱码。
    return String.format(java.util.Locale.US, "%02d:%02d", min, sec)
}
