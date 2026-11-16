package com.alanmaster.xread.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.media.AudioManager
import android.os.Build
import android.os.SystemClock
import android.app.PictureInPictureParams
import android.util.Rational
import android.util.Log
import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
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
import androidx.compose.material.icons.filled.Settings
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
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
import androidx.compose.runtime.mutableFloatStateOf
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
import androidx.compose.ui.graphics.vector.ImageVector
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
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import coil.compose.AsyncImage
import com.alanmaster.xread.data.BookHotReview
import com.alanmaster.xread.data.CommentReply
import com.alanmaster.xread.data.DanmakuItem
import com.alanmaster.xread.data.DramaEpisode
import com.alanmaster.xread.data.DramaPlaySource
import com.alanmaster.xread.data.DramaProgress
import com.alanmaster.xread.data.DramaSeries
import com.alanmaster.xread.data.DramaSeriesGrouping
import com.alanmaster.xread.data.DramaSubscription
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.abs

/**
 * 短剧与漫剧剧场界面 剧场广场、剧集选集与视频播放、弹幕浮层与互动。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DramaScreen(
    feed: List<DramaSeries>,
    feedLoading: Boolean,
    progress: List<DramaProgress> = emptyList(),
    onContinueWatching: (DramaProgress) -> Unit = {},
    currentDrama: DramaSeries?,
    currentEpisode: DramaEpisode?,
    currentVideoSource: DramaPlaySource?,
    prefetchedEpisodeId: String = "",
    prefetchedVideoSource: DramaPlaySource? = null,
    videoLoading: Boolean,
    videoError: String,
    resumePositionMs: Long = 0L,
    onSaveDramaProgress: (DramaEpisode, Long, Long) -> Unit = { _, _, _ -> },
    danmakuList: List<DanmakuItem>,
    onBack: () -> Unit,
    onSelectDrama: (DramaSeries) -> Unit,
    onSelectEpisode: (DramaEpisode) -> Unit,
    onSendDanmaku: (String, Long) -> Unit,
    onLoadMoreDanmaku: (Long) -> Unit = {},
    onRefreshFeed: () -> Unit,
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
    commentReplyTarget: BookHotReview? = null,
    commentReplies: List<CommentReply> = emptyList(),
    commentRepliesLoading: Boolean = false,
    commentRepliesHasMore: Boolean = false,
    commentRepliesError: String = "",
    commentReplySending: Boolean = false,
    onLoadComments: () -> Unit = {},
    onLoadMoreComments: () -> Unit = {},
    onSendComment: (String) -> Unit = {},
    onToggleCommentDigg: (BookHotReview) -> Unit = {},
    onOpenCommentReplies: (BookHotReview) -> Unit = {},
    onLoadMoreCommentReplies: () -> Unit = {},
    onCloseCommentReplies: () -> Unit = {},
    onReplyComment: (String, CommentReply?) -> Unit = { _, _ -> },
    onShareDrama: () -> Unit = {},
    onSelectRelated: (DramaSeries) -> Unit = {},
    moreLoading: Boolean = false,
    feedExhausted: Boolean = true,
    onLoadMore: () -> Unit = {},
    onSearch: () -> Unit = {},
    onOpenMyDramas: () -> Unit = {},
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
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Space.gutter)
                        .padding(bottom = Space.xs),
                    horizontalArrangement = Arrangement.spacedBy(Space.sm),
                ) {
                    DramaQuickEntry(
                        title = "我的追剧",
                        subtitle = "订阅 · 收藏 · 记录",
                        modifier = Modifier.weight(1f),
                        onClick = onOpenMyDramas,
                    )
                    DramaQuickEntry(
                        title = "观看历史",
                        subtitle = "最近看过的短剧",
                        modifier = Modifier.weight(1f),
                        onClick = onOpenMyDramas,
                    )
                }
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
                            if (progress.isNotEmpty()) {
                                item(span = androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan.FullLine) {
                                    DramaContinueRail(progress = progress, onClick = onContinueWatching)
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
            prefetchedEpisodeId = prefetchedEpisodeId,
            prefetchedVideoSource = prefetchedVideoSource,
            resumePositionMs = resumePositionMs,
            onSaveDramaProgress = onSaveDramaProgress,
            loading = videoLoading,
            error = videoError,
            danmakuList = danmakuList,
            onBack = onBack,
            onSelectEpisode = onSelectEpisode,
            onSendDanmaku = onSendDanmaku,
            onLoadMoreDanmaku = onLoadMoreDanmaku,
            isFavorited = favorites.any { it.seriesId == drama.seriesId },
            onToggleFavorite = { onToggleFavorite(drama) },
            isSubscribed = DramaSeriesGrouping.isSubscribed(drama, subscriptions),
            onToggleSubscription = { onToggleSubscription(drama) },
            related = related,
            comments = comments,
            commentTotal = commentTotal,
            commentsLoading = commentsLoading,
            commentsHasMore = commentsHasMore,
            commentSending = commentSending,
            commentReplyTarget = commentReplyTarget,
            commentReplies = commentReplies,
            commentRepliesLoading = commentRepliesLoading,
            commentRepliesHasMore = commentRepliesHasMore,
            commentRepliesError = commentRepliesError,
            commentReplySending = commentReplySending,
            onLoadComments = onLoadComments,
            onLoadMoreComments = onLoadMoreComments,
            onSendComment = onSendComment,
            onToggleCommentDigg = onToggleCommentDigg,
            onOpenCommentReplies = onOpenCommentReplies,
            onLoadMoreCommentReplies = onLoadMoreCommentReplies,
            onCloseCommentReplies = onCloseCommentReplies,
            onReplyComment = onReplyComment,
            onShareDrama = onShareDrama,
            onSelectRelated = onSelectRelated,
        )
    }
}

/** 首页「继续观看」rail，只展示本地有进度的剧。 */
@Composable
private fun DramaContinueRail(
    progress: List<DramaProgress>,
    onClick: (DramaProgress) -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(top = Space.sm, bottom = Space.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("继续观看", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.weight(1f))
            Text("${progress.size} 部", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(Space.sm),
            contentPadding = PaddingValues(bottom = Space.sm),
        ) {
            items(progress, key = { it.seriesId }) { item ->
                Surface(
                    onClick = { onClick(item) },
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
                    modifier = Modifier.width(236.dp),
                ) {
                    Row(
                        Modifier.padding(8.dp),
                        horizontalArrangement = Arrangement.spacedBy(Space.sm),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        AsyncImage(
                            model = item.coverUrl,
                            contentDescription = item.seriesTitle,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(width = 58.dp, height = 76.dp)
                                .clip(RoundedCornerShape(8.dp)),
                        )
                        Column(Modifier.weight(1f)) {
                            Text(
                                item.seriesTitle
                                    .takeUnless { it.isBlank() || it == "短剧 ${item.seriesId}" }
                                    ?: "继续观看",
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                if (item.isFinished) "已看完 · 第 ${item.episodeIndex} 集" else "看到第 ${item.episodeIndex} 集",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.height(8.dp))
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .height(3.dp)
                                    .clip(RoundedCornerShape(2.dp))
                                    .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)),
                            ) {
                                Box(
                                    Modifier
                                        .fillMaxWidth(item.fraction.coerceAtLeast(0.02f))
                                        .fillMaxHeight()
                                        .background(MaterialTheme.colorScheme.primary),
                                )
                            }
                        }
                    }
                }
            }
        }
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

/** 短剧 tab 的追剧快捷入口，点开书架里的短剧分组。 */
@Composable
private fun DramaQuickEntry(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        modifier = modifier,
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 9.dp)) {
            Text(title, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
            Text(
                subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
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
    prefetchedEpisodeId: String = "",
    prefetchedVideoSource: DramaPlaySource? = null,
    loading: Boolean,
    error: String,
    resumePositionMs: Long = 0L,
    onSaveDramaProgress: (DramaEpisode, Long, Long) -> Unit = { _, _, _ -> },
    danmakuList: List<DanmakuItem>,
    onBack: () -> Unit,
    onSelectEpisode: (DramaEpisode) -> Unit,
    onSendDanmaku: (String, Long) -> Unit,
    onLoadMoreDanmaku: (Long) -> Unit = {},
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
    commentReplyTarget: BookHotReview? = null,
    commentReplies: List<CommentReply> = emptyList(),
    commentRepliesLoading: Boolean = false,
    commentRepliesHasMore: Boolean = false,
    commentRepliesError: String = "",
    commentReplySending: Boolean = false,
    onLoadComments: () -> Unit = {},
    onLoadMoreComments: () -> Unit = {},
    onSendComment: (String) -> Unit = {},
    onToggleCommentDigg: (BookHotReview) -> Unit = {},
    onOpenCommentReplies: (BookHotReview) -> Unit = {},
    onLoadMoreCommentReplies: () -> Unit = {},
    onCloseCommentReplies: () -> Unit = {},
    onReplyComment: (String, CommentReply?) -> Unit = { _, _ -> },
    onShareDrama: () -> Unit = {},
    onSelectRelated: (DramaSeries) -> Unit = {},
) {
    var videoViewRef by remember { mutableStateOf<DramaPlayerView?>(null) }
    var isPlaying by remember { mutableStateOf(false) }
    var currentPositionMs by remember { mutableLongStateOf(0L) }
    val danmakuPositionState = remember { mutableLongStateOf(0L) }
    var danmakuPositionMs by danmakuPositionState
    var durationMs by remember { mutableLongStateOf(0L) }
    val hostContext = LocalContext.current
    var showControls by remember { mutableStateOf(false) }
    var showDetails by remember { mutableStateOf(false) }
    var showMoreMenu by remember { mutableStateOf(false) }
    var playbackSpeed by remember { mutableFloatStateOf(1f) }
    var selectedVariantIndex by remember(source?.videoId) { mutableIntStateOf(-1) }
    var pendingSeekMs by remember { mutableLongStateOf(0L) }
    var consumedResumeMs by remember(drama.seriesId, episode?.episodeId) { mutableLongStateOf(resumePositionMs) }
    var danmakuEnabled by remember { mutableStateOf(true) }
    // 亮度与音量手势：亮度写回窗口，音量走系统媒体流，退出播放页时把亮度还给系统。
    val audioManager = remember(hostContext) {
        hostContext.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
    }
    var gestureHintKind by remember { mutableStateOf("") }
    var gestureHintLabel by remember { mutableStateOf("") }
    var gestureHintProgress by remember { mutableFloatStateOf(0f) }
    val danmakuPrefs = remember(hostContext) {
        hostContext.getSharedPreferences("drama_danmaku", Context.MODE_PRIVATE)
    }
    var danmakuFontSize by remember { mutableFloatStateOf(danmakuPrefs.getFloat("font_size", 16f)) }
    var danmakuOpacity by remember { mutableFloatStateOf(danmakuPrefs.getFloat("opacity", 0.9f)) }
    var danmakuLaneCount by remember { mutableIntStateOf(danmakuPrefs.getInt("lane_count", 4)) }
    var danmakuWindowMs by remember { mutableIntStateOf(danmakuPrefs.getInt("window_ms", 4000)) }
    var showDanmakuSettings by remember { mutableStateOf(false) }
    var inputText by remember { mutableStateOf("") }
    var showComments by remember { mutableStateOf(false) }
    var showEpisodePicker by remember(drama.seriesId) { mutableStateOf(false) }
    var introExpanded by remember(drama.seriesId) { mutableStateOf(false) }
    // AndroidView 的 factory 只执行一次，回调里要读最新的剧集列表与回调，不能捕获首帧闭包。
    val currentEpisodeState by rememberUpdatedState(episode)
    val currentDramaState by rememberUpdatedState(drama)
    val selectEpisodeState by rememberUpdatedState(onSelectEpisode)
    val prefetchedEpisodeState by rememberUpdatedState(
        drama.episodes.firstOrNull { it.episodeId == prefetchedEpisodeId }
    )
    val currentPlaybackSpeed by rememberUpdatedState(playbackSpeed)
    val saveProgressState by rememberUpdatedState(onSaveDramaProgress)
    val loadMoreDanmakuState by rememberUpdatedState(onLoadMoreDanmaku)
    val effectiveVideoPath = remember(source, selectedVariantIndex) {
        val variant = source?.variants?.getOrNull(selectedVariantIndex)
        when {
            variant != null && variant.url.isNotBlank() -> variant.url
            source == null -> ""
            source.isLocal -> source.localFilePath
            else -> source.videoUrl
        }
    }

    // 播放页默认沉浸式 进入时隐藏系统栏 点按画面才唤出控制层。
    DisposableEffect(hostContext) {
        val window = hostContext.findHostActivity()?.window
        if (window == null) {
            onDispose { }
        } else {
            val controller = WindowCompat.getInsetsController(window, window.decorView)
            controller.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            WindowCompat.setDecorFitsSystemWindows(window, false)
            controller.hide(WindowInsetsCompat.Type.systemBars())
            onDispose {
                controller.show(WindowInsetsCompat.Type.systemBars())
                WindowCompat.setDecorFitsSystemWindows(window, true)
                // 播放器只临时覆盖窗口亮度，离开时还原系统默认，避免影响其它页面。
                window.attributes = window.attributes.apply {
                    screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
                }
            }
        }
    }

    // 亮度/音量手势提示 1 秒后自动消失，不打断播放。
    LaunchedEffect(gestureHintKind, gestureHintLabel) {
        if (gestureHintKind.isNotEmpty()) {
            delay(1000)
            gestureHintKind = ""
        }
    }

    // 控制层自动收起 只在播放中且没有展开详情时生效。
    LaunchedEffect(showControls, isPlaying, showDetails, showComments, showEpisodePicker, showDanmakuSettings, showMoreMenu) {
        if (showControls && isPlaying && !showDetails && !showComments && !showEpisodePicker && !showDanmakuSettings && !showMoreMenu) {
            delay(3000)
            showControls = false
        }
    }

    // 倍速变化时同步到当前 Media3 播放器，避免只改变显示文字。
    LaunchedEffect(playbackSpeed) {
        videoViewRef?.setPlaybackSpeed(playbackSpeed)
    }

    // 从「相关推荐」切到另一部剧时，先把上一部剧的剧评弹层收起来，避免展示旧列表。
    LaunchedEffect(drama.seriesId) {
        showComments = false
        showEpisodePicker = false
        introExpanded = false
        showDetails = false
        showControls = false
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

    // 每 5 秒保存一次播放进度；暂停、切集和退出也立即保存。
    LaunchedEffect(videoViewRef, isPlaying, episode?.episodeId) {
        if (!isPlaying) {
            episode?.let { ep ->
                val vv = videoViewRef
                if (vv != null && vv.currentPosition > 0) {
                    saveProgressState(ep, vv.currentPosition.toLong(), vv.duration.toLong())
                }
            }
            return@LaunchedEffect
        }
        while (true) {
            delay(5_000)
            val vv = videoViewRef ?: continue
            val ep = currentEpisodeState ?: continue
            if (vv.currentPosition > 0) {
                saveProgressState(ep, vv.currentPosition.toLong(), vv.duration.toLong())
            }
        }
    }

    // 退出播放页时兜底保存一次，避免最后几秒进度丢失。
    DisposableEffect(Unit) {
        onDispose {
            val vv = videoViewRef
            val ep = currentEpisodeState
            if (vv != null && ep != null && vv.currentPosition > 0) {
                saveProgressState(ep, vv.currentPosition.toLong(), vv.duration.toLong())
            }
        }
    }

    // 弹幕按时间窗口续拉：首包覆盖前 180 秒，播放推进后继续取后续窗口。
    LaunchedEffect(videoViewRef, isPlaying, episode?.episodeId) {
        if (!isPlaying) return@LaunchedEffect
        var lastRequestedMs = 0L
        while (true) {
            delay(120_000)
            val vv = videoViewRef ?: continue
            val pos = vv.currentPosition.toLong()
            if (pos > lastRequestedMs + 60_000L) {
                lastRequestedMs = pos
                loadMoreDanmakuState(pos)
            }
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
        containerColor = Color.Black,
        contentColor = Color.White,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            /* -------------------- 视频播放区域 默认铺满 展开详情后回到 16:9 -------------------- */
            val videoModifier = if (showDetails) {
                Modifier.fillMaxWidth().aspectRatio(16f / 9f)
            } else {
                Modifier.fillMaxWidth().weight(1f)
            }
            Box(
                videoModifier
                    .background(Color.Black)
            ) {
                // 原生 VideoView 播放核心
                AndroidView(
                    // Media3 PlayerView 自己绘制视频，控制层仍由 Compose 叠加在上方。
                    modifier = Modifier.fillMaxSize(),
                    factory = { context ->
                        DramaPlayerView(context).apply {
                            isClickable = false
                            isFocusable = false
                            setOnTouchListener { _, _ -> false }
                            setOnPreparedListener { vv ->
                                durationMs = vv.duration.toLong()
                                Log.i("DramaPlayer", "prepared series=${drama.seriesId} episode=${episode?.episodeId} duration=${vv.duration}")
                                vv.setPlaybackSpeed(currentPlaybackSpeed)
                                val resume = maxOf(consumedResumeMs, pendingSeekMs)
                                if (resume > 0L) {
                                    runCatching { seekTo(resume.coerceAtMost(vv.duration.toLong()).toInt()); currentPositionMs = resume }
                                    pendingSeekMs = 0L
                                    consumedResumeMs = 0L
                                }
                                vv.start()
                                isPlaying = true
                            }
                            setOnErrorListener { _, what, extra ->
                                Log.e("DramaPlayer", "error episode=${currentEpisodeState?.episodeId} what=$what extra=$extra")
                                true
                            }
                            setOnCompletionListener { vv ->
                                Log.i("DramaPlayer", "completed episode=${currentEpisodeState?.episodeId}")
                                isPlaying = false
                                currentEpisodeState?.let { ep ->
                                    saveProgressState(ep, vv.duration.toLong(), vv.duration.toLong())
                                }
                                val list = currentDramaState.episodes
                                val index = list.indexOfFirst {
                                    it.episodeId == currentEpisodeState?.episodeId
                                }
                                val next = if (index >= 0) list.getOrNull(index + 1) else null
                                if (next != null) selectEpisodeState(next)
                            }
                            setOnMediaItemTransitionListener {
                                val currentIndex = currentDramaState.episodes.indexOfFirst {
                                    it.episodeId == currentEpisodeState?.episodeId
                                }
                                val next = currentDramaState.episodes.getOrNull(currentIndex + 1)
                                Log.i("DramaPlayer", "auto advanced to next episode=${next?.episodeId}")
                                // 预取队列切换后以当前剧集列表的下一集为准，不依赖
                                // prefetchedEpisodeId 的瞬时状态，避免自动切集后状态空转。
                                (next ?: prefetchedEpisodeState)?.let { selectEpisodeState(it) }
                            }
                            videoViewRef = this
                        }
                    },
                    update = { view ->
                        videoViewRef = view
                        if (source != null) {
                            val path = effectiveVideoPath
                            if (path.isNotEmpty() && view.tag != path) {
                                view.tag = path
                                view.setVideoPath(path)
                            }
                            val nextPath = prefetchedVideoSource?.let {
                                if (it.localFilePath.isNotBlank()) it.localFilePath else it.videoUrl
                            }
                            if (nextPath.isNullOrBlank()) view.clearNextVideo() else view.setNextVideoPath(nextPath)
                        }
                    },
                    onRelease = { view -> view.release() }
                )

                // 控制层显示时，空白区域点击收起；控件本身在更后层，优先命中。
                if (showControls && !showDetails) {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .pointerInput(Unit) {
                                detectTapGestures { showControls = false }
                            },
                    )
                }

                if (!showControls) {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .pointerInput(drama.seriesId, episode?.episodeId) {
                            if (showControls) return@pointerInput
                            val switchThreshold = size.height * 0.12f
                            val edgeZone = size.width * 0.28f
                            val longPressMs = viewConfiguration.longPressTimeoutMillis
                            awaitEachGesture {
                                val down = awaitFirstDown(requireUnconsumed = false)
                                val inBrightnessZone = down.position.x < edgeZone
                                val inVolumeZone = down.position.x > size.width - edgeZone
                                var lastX = down.position.x
                                var lastY = down.position.y
                                var totalX = 0f
                                var totalY = 0f
                                var dragging = false
                                var startBrightness = 0f
                                var startVolumeFraction = 0f
                                val maxVolume = audioManager?.getStreamMaxVolume(AudioManager.STREAM_MUSIC) ?: 1
                                while (true) {
                                    // 未拖动时用剩余长按时长做超时等待，手指静止也能触发 2 倍速。
                                    val event = if (!dragging) {
                                        val elapsed = SystemClock.uptimeMillis() - down.uptimeMillis
                                        withTimeoutOrNull((longPressMs - elapsed).coerceAtLeast(1L)) {
                                            awaitPointerEvent()
                                        }
                                    } else {
                                        awaitPointerEvent()
                                    }
                                    if (event == null) {
                                        videoViewRef?.setPlaybackSpeed(2f)
                                        gestureHintKind = "speed"
                                        gestureHintLabel = "2x 倍速播放中"
                                        gestureHintProgress = 1f
                                        // 等手指抬起再恢复，长按期间保持 2 倍速。
                                        waitForUpOrCancellation()
                                        videoViewRef?.setPlaybackSpeed(currentPlaybackSpeed)
                                        gestureHintKind = ""
                                        return@awaitEachGesture
                                    }
                                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                    if (change.changedToUpIgnoreConsumed()) break
                                    val dx = change.position.x - lastX
                                    val dy = change.position.y - lastY
                                    lastX = change.position.x
                                    lastY = change.position.y
                                    if (dx != 0f || dy != 0f) {
                                        totalX += dx
                                        totalY += dy
                                        if (!dragging && (abs(totalX) > 12.dp.toPx() || abs(totalY) > 12.dp.toPx())) {
                                            dragging = true
                                            startBrightness = currentWindowBrightness(hostContext)
                                            startVolumeFraction = ((audioManager?.getStreamVolume(AudioManager.STREAM_MUSIC) ?: 0).toFloat()) / maxVolume.coerceAtLeast(1)
                                        }
                                        if (dragging) change.consume()
                                    }
                                    // 左右边缘竖滑实时调亮度/音量，中间竖滑仍然切集
                                    if (dragging && abs(totalY) > abs(totalX) &&
                                        abs(totalY) > 12.dp.toPx() && (inBrightnessZone || inVolumeZone)
                                    ) {
                                        val deltaFraction = -totalY / size.height
                                        if (inBrightnessZone) {
                                            val target = (startBrightness + deltaFraction).coerceIn(0.02f, 1f)
                                            applyWindowBrightness(hostContext, target)
                                            gestureHintKind = "brightness"
                                            gestureHintLabel = "亮度 ${(target * 100).toInt()}%"
                                            gestureHintProgress = target
                                        } else {
                                            val target = (startVolumeFraction + deltaFraction).coerceIn(0f, 1f)
                                            val index = (target * maxVolume).toInt().coerceIn(0, maxVolume)
                                            audioManager?.setStreamVolume(AudioManager.STREAM_MUSIC, index, 0)
                                            gestureHintKind = "volume"
                                            gestureHintLabel = "音量 ${(target * 100).toInt()}%"
                                            gestureHintProgress = target
                                        }
                                    }
                                }
                                if (dragging) {
                                    if (abs(totalY) > abs(totalX) && abs(totalY) > switchThreshold &&
                                        !inBrightnessZone && !inVolumeZone
                                    ) {
                                        val currentIndex = drama.episodes.indexOfFirst { it.episodeId == episode?.episodeId }
                                        val target = when {
                                            episode == null -> drama.episodes.firstOrNull()
                                            currentIndex >= 0 && totalY < 0f -> drama.episodes.getOrNull(currentIndex - 1)
                                            currentIndex >= 0 -> drama.episodes.getOrNull(currentIndex + 1)
                                            else -> null
                                        }
                                        if (target != null) {
                                            onSelectEpisode(target)
                                            showControls = true
                                        }
                                    } else if (abs(totalX) > abs(totalY) && abs(totalX) > 80.dp.toPx()) {
                                        // 横滑快进/快退 10 秒，短剧用户不用精确拖进度条。
                                        val delta = if (totalX > 0f) 10_000L else -10_000L
                                        val max = durationMs.takeIf { it > 0 } ?: Long.MAX_VALUE
                                        val target = (currentPositionMs + delta).coerceIn(0L, max)
                                        videoViewRef?.seekTo(target.toInt())
                                        currentPositionMs = target
                                        showControls = true
                                    }
                                    return@awaitEachGesture
                                }
                                val second = withTimeoutOrNull(viewConfiguration.doubleTapTimeoutMillis) {
                                    awaitFirstDown(requireUnconsumed = false)
                                }
                                if (second != null) {
                                    waitForUpOrCancellation()
                                    val delta = if (down.position.x < size.width / 2f) -10_000L else 10_000L
                                    val max = durationMs.takeIf { it > 0 } ?: Long.MAX_VALUE
                                    val target = (currentPositionMs + delta).coerceIn(0L, max)
                                    videoViewRef?.seekTo(target.toInt())
                                    currentPositionMs = target
                                    showControls = true
                                } else {
                                    showControls = !showControls
                                }
                            }
                        }
                    )
                }

                // 亮度/音量/长按倍速的即时提示，只在手势进行时出现。
                if (gestureHintKind.isNotEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(
                            modifier = Modifier
                                .background(Color.Black.copy(alpha = 0.72f), RoundedCornerShape(14.dp))
                                .padding(horizontal = Space.md, vertical = Space.sm),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(Space.xs),
                        ) {
                            Text(gestureHintLabel, color = Color.White, fontSize = 14.sp)
                            if (gestureHintKind != "speed") {
                                Box(
                                    Modifier
                                        .width(140.dp)
                                        .height(4.dp)
                                        .background(Color.White.copy(alpha = 0.28f), RoundedCornerShape(2.dp))
                                ) {
                                    Box(
                                        Modifier
                                            .fillMaxWidth(gestureHintProgress.coerceIn(0f, 1f))
                                            .fillMaxHeight()
                                            .background(Color.White, RoundedCornerShape(2.dp))
                                    )
                                }
                            }
                        }
                    }
                }

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
                if (danmakuEnabled && !showDetails && danmakuList.isNotEmpty()) {
                    DanmakuFlowLayer(
                        positionMs = danmakuPositionState,
                        items = danmakuList,
                        modifier = Modifier.fillMaxSize(),
                        windowMs = danmakuWindowMs.toLong(),
                        laneCount = danmakuLaneCount,
                        topInset = if (showControls) 128.dp else 16.dp,
                        laneGap = (danmakuFontSize * 1.7f).dp,
                        fontSize = danmakuFontSize.sp,
                        textAlpha = danmakuOpacity,
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

                // ------------------ 顶部工具栏 抖音/红果式透明浮层 ------------------
                if (showControls) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(128.dp)
                            .align(Alignment.TopCenter)
                            .background(
                                Brush.verticalGradient(
                                    listOf(Color.Black.copy(alpha = 0.74f), Color.Transparent),
                                ),
                            ),
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 2.dp, end = 10.dp, top = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            IconButton(
                                onClick = onBack,
                                modifier = Modifier.size(40.dp),
                            ) {
                                Icon(
                                    Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "返回",
                                    tint = Color.White,
                                    modifier = Modifier.size(24.dp),
                                )
                            }
                            Text(
                                text = "第 ${episode?.episodeIndex ?: 1} 集",
                                color = Color.White,
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(start = 8.dp, end = 8.dp),
                            )
                            Text(
                                text = if (playbackSpeed % 1f == 0f) "${playbackSpeed.toInt()}.0x" else "${playbackSpeed}x",
                                color = Color.White.copy(alpha = 0.92f),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(50))
                                    .clickable {
                                        playbackSpeed = nextPlaybackSpeed(playbackSpeed)
                                        showControls = true
                                    }
                                    .padding(horizontal = 8.dp, vertical = 8.dp),
                            )
                            Box {
                                IconButton(
                                    onClick = { showMoreMenu = true },
                                    modifier = Modifier.size(38.dp),
                                ) {
                                    Icon(
                                        Icons.Filled.MoreVert,
                                        contentDescription = "更多",
                                        tint = Color.White,
                                        modifier = Modifier.size(22.dp),
                                    )
                                }
                                DropdownMenu(
                                    expanded = showMoreMenu,
                                    onDismissRequest = { showMoreMenu = false },
                                    containerColor = Color(0xFF1E1E1E),
                                ) {
                                    DropdownMenuItem(
                                        text = { Text(if (showDetails) "收起详情" else "查看详情", color = Color.White) },
                                        onClick = {
                                            showDetails = !showDetails
                                            showMoreMenu = false
                                            showControls = true
                                        },
                                    )
                                    val variants = source?.variants.orEmpty()
                                    if (variants.isNotEmpty()) {
                                        DropdownMenuItem(
                                            text = {
                                                val label = variants.getOrNull(selectedVariantIndex)?.name
                                                    ?: variants.first().name.ifBlank { "默认" }
                                                Text("清晰度 · $label", color = Color.White)
                                            },
                                            onClick = {
                                                pendingSeekMs = videoViewRef?.currentPosition?.toLong() ?: 0L
                                                selectedVariantIndex =
                                                    (selectedVariantIndex + 2).mod(variants.size) - 1
                                                showMoreMenu = false
                                                showControls = true
                                            },
                                        )
                                    }
                                    DropdownMenuItem(
                                        text = { Text("弹幕设置", color = Color.White) },
                                        onClick = {
                                            showDanmakuSettings = true
                                            showMoreMenu = false
                                            showControls = true
                                        },
                                    )
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                        DropdownMenuItem(
                                            text = { Text("画中画播放", color = Color.White) },
                                            onClick = {
                                                showMoreMenu = false
                                                val activity = hostContext.findHostActivity()
                                                activity?.enterPictureInPictureMode(
                                                    PictureInPictureParams.Builder()
                                                        .setAspectRatio(Rational(16, 9))
                                                        .build()
                                                )
                                            },
                                        )
                                    }
                                    DropdownMenuItem(
                                        text = { Text("分享", color = Color.White) },
                                        onClick = {
                                            showMoreMenu = false
                                            onShareDrama()
                                        },
                                    )
                                }
                            }
                        }
                    }
                }

                // ------------------ 右侧操作栏 抖音/红果式竖排 ------------------
                if (showControls && !showDetails) {
                    Column(
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .padding(end = 10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        PlayerActionButton(
                            icon = Icons.Filled.ChatBubbleOutline,
                            label = "剧评",
                            count = if (commentTotal > 0) formatActionCount(commentTotal) else "",
                            onClick = {
                                showComments = true
                                onLoadComments()
                            },
                        )
                        PlayerActionButton(
                            icon = if (isSubscribed) Icons.Filled.NotificationsActive else Icons.Filled.Notifications,
                            label = if (isSubscribed) "已订阅" else "订阅",
                            tint = if (isSubscribed) Color(0xFF4FC3F7) else Color.White,
                            onClick = onToggleSubscription,
                        )
                        PlayerActionButton(
                            icon = Icons.Filled.Star,
                            label = if (isFavorited) "已收藏" else "收藏",
                            tint = if (isFavorited) Color(0xFFFFC107) else Color.White,
                            onClick = onToggleFavorite,
                        )
                        PlayerActionButton(
                            icon = Icons.Filled.Share,
                            label = "分享",
                            onClick = onShareDrama,
                        )
                    }
                }

                // ------------------ 全屏播放底部信息与选集入口 ------------------
                if (showControls && !showDetails) {
                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .fillMaxWidth()
                            .padding(bottom = 64.dp),
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = Space.gutter),
                            verticalArrangement = Arrangement.spacedBy(3.dp),
                        ) {
                            Text(
                                text = drama.title,
                                color = Color.White,
                                fontSize = 17.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = "第 ${episode?.episodeIndex ?: 1} 集  " +
                                    (if (drama.episodeCount > 0 && drama.episodes.size >= drama.episodeCount) "已完结" else "连载中") +
                                    "  全 ${drama.episodes.size.coerceAtLeast(drama.episodeCount)} 集",
                                color = Color.White.copy(alpha = 0.62f),
                                fontSize = 12.sp,
                                maxLines = 1,
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        DramaPlayerEpisodeBar(
                            totalCount = drama.episodes.size.coerceAtLeast(drama.episodeCount),
                            completed = drama.episodeCount > 0 && drama.episodes.size >= drama.episodeCount,
                            onOpenEpisodePicker = { showEpisodePicker = true },
                        )
                    }
                }

                // ------------------ 播放控制覆盖层 ------------------
                // 详情首帧由封面上的播放入口负责；没有视频源时不要显示控制层，
                // 否则中间播放键会先拦截点击并把空 VideoView 置成“播放中”。
                // ------------------ 播放控制覆盖层 ------------------
                // 详情首帧由封面上的播放入口负责；没有视频源时不要显示控制层，
                // 否则中间播放键会先拦截点击并把空 VideoView 置成“播放中”。
                if (showControls && !(source == null && !loading && error.isEmpty())) {
                    Box(Modifier.fillMaxSize()) {
                        // 底部横条 播放键 进度条与弹幕开关
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .align(Alignment.BottomCenter)
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                                .clip(RoundedCornerShape(18.dp))
                                .background(Color.Black.copy(alpha = 0.32f))
                                .padding(horizontal = 8.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
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
                                modifier = Modifier.size(32.dp),
                            ) {
                                Icon(
                                    if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                    contentDescription = if (isPlaying) "暂停" else "播放",
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                            Text(
                                formatTime(currentPositionMs),
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White,
                            )
                            PlayerProgressBar(
                                value = if (durationMs > 0) (currentPositionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f,
                                onValueChange = { frac ->
                                    val target = (frac * durationMs).toLong()
                                    currentPositionMs = target
                                    videoViewRef?.seekTo(target.toInt())
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(horizontal = 8.dp),
                            )
                            Text(
                                formatTime(durationMs),
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White,
                            )
                            IconButton(
                                onClick = { danmakuEnabled = !danmakuEnabled },
                                modifier = Modifier.size(32.dp),
                            ) {
                                Icon(
                                    Icons.Filled.Subtitles,
                                    contentDescription = "弹幕开关",
                                    tint = if (danmakuEnabled) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.5f),
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                            IconButton(
                                onClick = {
                                    showDanmakuSettings = true
                                    showControls = true
                                },
                                modifier = Modifier.size(32.dp),
                            ) {
                                Icon(
                                    Icons.Filled.Settings,
                                    contentDescription = "弹幕设置",
                                    tint = Color.White.copy(alpha = 0.85f),
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        }
                    }
                }
            }

            if (showDetails) {
                XReadTheme(dark = true) {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .background(Color(0xFF14120F)),
                    ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = Space.xs),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                Surface(
                    onClick = {
                        showDetails = false
                        showControls = false
                    },
                    shape = RoundedCornerShape(50),
                    color = Color.White.copy(alpha = 0.12f),
                    contentColor = Color.White,
                ) {
                    Row(
                        Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Icon(
                            Icons.Filled.Tv,
                            contentDescription = null,
                            modifier = Modifier.size(15.dp),
                        )
                        Text("全屏观看", fontSize = 12.sp)
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
                }
            }
            }

            if (showComments && commentReplyTarget == null) {
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
                    onOpenReplies = onOpenCommentReplies,
                )
            }
            if (showComments && commentReplyTarget != null) {
                DramaCommentRepliesSheet(
                    target = commentReplyTarget,
                    replies = commentReplies,
                    loading = commentRepliesLoading,
                    hasMore = commentRepliesHasMore,
                    error = commentRepliesError,
                    sending = commentReplySending,
                    onBack = onCloseCommentReplies,
                    onLoadMore = onLoadMoreCommentReplies,
                    onReply = onReplyComment,
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
            if (showDanmakuSettings) {
                DanmakuSettingsSheet(
                    fontSize = danmakuFontSize,
                    opacity = danmakuOpacity,
                    laneCount = danmakuLaneCount,
                    windowMs = danmakuWindowMs,
                    onFontSize = {
                        danmakuFontSize = it
                        danmakuPrefs.edit().putFloat("font_size", it).apply()
                    },
                    onOpacity = {
                        danmakuOpacity = it
                        danmakuPrefs.edit().putFloat("opacity", it).apply()
                    },
                    onLaneCount = {
                        danmakuLaneCount = it
                        danmakuPrefs.edit().putInt("lane_count", it).apply()
                    },
                    onWindowMs = {
                        danmakuWindowMs = it
                        danmakuPrefs.edit().putInt("window_ms", it).apply()
                    },
                    onDismiss = { showDanmakuSettings = false },
                )
            }
        }
    }
}

/** 播放器底部的选集入口，固定在详情内容底部。 */
@Composable
private fun DramaPlayerEpisodeBar(
    totalCount: Int,
    completed: Boolean,
    onOpenEpisodePicker: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Space.gutter, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            onClick = onOpenEpisodePicker,
            modifier = Modifier
                .weight(1f)
                .height(52.dp),
            shape = RoundedCornerShape(18.dp),
            color = Color(0xFF2B2925),
            contentColor = Color.White,
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("选集", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.width(10.dp))
                Text(
                    if (completed) "已完结" else "连载中",
                    fontSize = 13.sp,
                    color = Color.White.copy(alpha = 0.6f),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    "全 $totalCount 集",
                    fontSize = 13.sp,
                    color = Color.White.copy(alpha = 0.6f),
                )
                Spacer(Modifier.weight(1f))
                Icon(
                    Icons.Filled.KeyboardArrowUp,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.8f),
                    modifier = Modifier.size(22.dp),
                )
            }
        }
        IconButton(
            onClick = onOpenEpisodePicker,
            modifier = Modifier
                .size(52.dp)
                .background(Color(0xFF2B2925), RoundedCornerShape(18.dp)),
        ) {
            Icon(
                Icons.Filled.GridView,
                contentDescription = "选集",
                tint = Color.White,
                modifier = Modifier.size(22.dp),
            )
        }
    }
}

/** 弹幕设置 字号、显示行数、不透明度与滚动速度。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DanmakuSettingsSheet(
    fontSize: Float,
    opacity: Float,
    laneCount: Int,
    windowMs: Int,
    onFontSize: (Float) -> Unit,
    onOpacity: (Float) -> Unit,
    onLaneCount: (Int) -> Unit,
    onWindowMs: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Space.gutter)
                .padding(bottom = Space.xl),
            verticalArrangement = Arrangement.spacedBy(Space.md),
        ) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("弹幕设置", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                TextButton(onClick = onDismiss) { Text("完成") }
            }
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(88.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF111111)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "这是一条弹幕预览",
                    color = Color.White.copy(alpha = opacity),
                    fontSize = fontSize.sp,
                    fontWeight = FontWeight.Medium,
                )
            }
            DanmakuSettingRow(
                title = "字号",
                options = listOf("小" to 14f, "标准" to 16f, "大" to 18f, "特大" to 20f),
                selected = fontSize,
                onSelect = onFontSize,
            )
            DanmakuSettingRow(
                title = "显示行数",
                options = listOf("3 行" to 3, "4 行" to 4, "5 行" to 5),
                selected = laneCount,
                onSelect = onLaneCount,
            )
            DanmakuSettingRow(
                title = "不透明度",
                options = listOf("60%" to 0.6f, "80%" to 0.8f, "100%" to 1f),
                selected = opacity,
                onSelect = onOpacity,
            )
            DanmakuSettingRow(
                title = "滚动速度",
                options = listOf("慢" to 5200, "标准" to 4000, "快" to 3000),
                selected = windowMs,
                onSelect = onWindowMs,
            )
        }
    }
}

@Composable
private fun <T> DanmakuSettingRow(
    title: String,
    options: List<Pair<String, T>>,
    selected: T,
    onSelect: (T) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            options.forEach { (label, value) ->
                val active = value == selected
                Surface(
                    onClick = { onSelect(value) },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(9.dp),
                    color = if (active) {
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                    },
                    contentColor = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                ) {
                    Box(
                        Modifier.padding(vertical = 9.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            label,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                        )
                    }
                }
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
    onOpenReplies: (BookHotReview) -> Unit,
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
                                    TextButton(onClick = { onOpenReplies(comment) }) {
                                        Text(
                                            if (comment.replyCount > 0) "${comment.replyCount} 回复" else "回复",
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

/** 剧评楼中楼 支持查看回复、回复某条子回复与继续翻页。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DramaCommentRepliesSheet(
    target: BookHotReview,
    replies: List<CommentReply>,
    loading: Boolean,
    hasMore: Boolean,
    error: String,
    sending: Boolean,
    onBack: () -> Unit,
    onLoadMore: () -> Unit,
    onReply: (String, CommentReply?) -> Unit,
) {
    var input by remember { mutableStateOf("") }
    var replyTo by remember { mutableStateOf<CommentReply?>(null) }
    LaunchedEffect(target.id) {
        input = ""
        replyTo = null
    }
    ModalBottomSheet(onDismissRequest = onBack) {
        Column(
            Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.72f)
                .padding(horizontal = Space.gutter),
        ) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onBack) { Text("返回") }
                Column(Modifier.weight(1f)) {
                    Text(
                        if (replyTo != null) "回复 @${replyTo?.user.orEmpty()}" else "回复 @${target.user}",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        "${target.replyCount} 条回复",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.height(Space.xs))
            if (loading && replies.isEmpty()) {
                Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (replies.isEmpty()) {
                Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                    Text("还没有回复", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    verticalArrangement = Arrangement.spacedBy(Space.sm),
                    contentPadding = PaddingValues(vertical = Space.sm),
                ) {
                    itemsIndexed(replies) { _, reply ->
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .clickable { replyTo = reply },
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    Modifier
                                        .size(28.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.surfaceVariant),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    if (reply.avatar.isNotEmpty()) {
                                        AsyncImage(
                                            model = reply.avatar,
                                            contentDescription = null,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.fillMaxSize(),
                                        )
                                    } else {
                                        Text(
                                            reply.user.take(1).ifEmpty { "友" },
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                                Spacer(Modifier.width(Space.sm))
                                Text(
                                    reply.user.ifEmpty { "匿名用户" },
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                if (reply.isAuthor) {
                                    Text("作者", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                                }
                            }
                            if (reply.replyToUser.isNotBlank()) {
                                Text(
                                    "回复 @${reply.replyToUser}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.outline,
                                    modifier = Modifier.padding(start = 36.dp, top = 2.dp),
                                )
                            }
                            Text(
                                reply.text,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(start = 36.dp, top = 2.dp),
                            )
                            Row(Modifier.padding(start = 36.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    "赞 ${formatCount(reply.digg)}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.outline,
                                )
                                TextButton(onClick = { replyTo = reply }) {
                                    Text("回复", style = MaterialTheme.typography.labelSmall)
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
            if (error.isNotBlank()) {
                Text(error, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
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
                        Text(
                            if (replyTo != null) "回复 @${replyTo?.user.orEmpty()}" else "写回复",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline,
                        )
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
                            onReply(text, replyTo)
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
        var query by remember { mutableStateOf("") }
        var rangeStart by remember(drama.seriesId) { mutableIntStateOf(0) }
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
            // 长剧集快速定位：搜索集数或标题 + 每 100 集分段，避免在几百集里盲滑。
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Space.gutter)
                    .height(38.dp),
            ) {
                Row(
                    Modifier.padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Space.xs),
                ) {
                    Icon(
                        Icons.Filled.Search,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp),
                    )
                    BasicTextField(
                        value = query,
                        onValueChange = { query = it },
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurface),
                        decorationBox = { inner ->
                            if (query.isEmpty()) {
                                Text("搜索集数 / 标题", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            inner()
                        },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            val total = drama.episodes.size.coerceAtLeast(drama.episodeCount)
            if (total > 100) {
                val ranges = remember(total) {
                    buildList {
                        add(0 to "全部")
                        var start = 1
                        while (start <= total) {
                            val end = (start + 99).coerceAtMost(total)
                            add(start to "$start-$end")
                            start += 100
                        }
                    }
                }
                LazyRow(
                    contentPadding = PaddingValues(horizontal = Space.gutter),
                    horizontalArrangement = Arrangement.spacedBy(Space.xs),
                    modifier = Modifier.padding(top = Space.xs),
                ) {
                    items(ranges, key = { it.first }) { (start, label) ->
                        DramaCategoryChip(
                            label = label,
                            selected = rangeStart == start,
                            onClick = { rangeStart = start },
                        )
                    }
                }
            }
            val filtered = remember(drama.episodes, query, rangeStart) {
                drama.episodes.filter { ep ->
                    val inRange = rangeStart == 0 || ep.episodeIndex in rangeStart until (rangeStart + 100)
                    val q = query.trim()
                    inRange && (q.isEmpty() || ep.episodeIndex.toString().contains(q) ||
                        ep.title.contains(q, ignoreCase = true))
                }
            }
            if (filtered.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("没有匹配的集数", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                val currentIndex = filtered.indexOfFirst { it.episodeId == currentEpisodeId }
                val gridState = rememberLazyGridState(initialFirstVisibleItemIndex = (currentIndex - 2).coerceAtLeast(0))
                LazyVerticalGrid(
                    state = gridState,
                    columns = GridCells.Fixed(5),
                    contentPadding = PaddingValues(horizontal = Space.gutter, vertical = Space.sm),
                    horizontalArrangement = Arrangement.spacedBy(Space.sm),
                    verticalArrangement = Arrangement.spacedBy(Space.sm),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    gridItems(filtered, key = { it.episodeId }) { ep ->
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PlayerProgressBar(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    Slider(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.height(28.dp),
        thumb = {
            Box(
                Modifier
                    .size(14.dp)
                    .background(Color.White, CircleShape)
                    .border(2.dp, MaterialTheme.colorScheme.primary, CircleShape),
            )
        },
        track = { state ->
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(RoundedCornerShape(50))
                    .background(Color.White.copy(alpha = 0.2f)),
            ) {
                Box(
                    Modifier
                        .fillMaxWidth(state.value)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(50))
                        .background(
                            Brush.horizontalGradient(
                                listOf(
                                    MaterialTheme.colorScheme.primary,
                                    Color(0xFFFF9E45),
                                ),
                            ),
                        ),
                )
            }
        },
    )
}

@Composable
private fun PlayerActionButton(
    icon: ImageVector,
    label: String,
    tint: Color = Color.White,
    count: String = "",
    onClick: () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(1.dp),
    ) {
        IconButton(onClick = onClick, modifier = Modifier.size(42.dp)) {
            Icon(
                icon,
                contentDescription = label,
                tint = tint,
                modifier = Modifier.size(27.dp),
            )
        }
        Text(label, color = Color.White.copy(alpha = 0.94f), fontSize = 11.sp)
        if (count.isNotEmpty()) {
            Text(count, color = Color.White.copy(alpha = 0.72f), fontSize = 10.sp)
        }
    }
}

private fun nextPlaybackSpeed(speed: Float): Float = when {
    speed < 0.9f -> 1f
    speed < 1.2f -> 1.25f
    speed < 1.4f -> 1.5f
    speed < 1.8f -> 2f
    else -> 0.75f
}

private fun formatActionCount(count: Int): String =
    if (count >= 10000) String.format(java.util.Locale.US, "%.1f万", count / 10000f) else count.toString()

private fun formatTime(ms: Long): String {
    val totalSec = (ms / 1000).coerceAtLeast(0)
    val min = totalSec / 60
    val sec = totalSec % 60
    // 固定 Locale.US 默认 Locale 在 ar/fa/bn 下会把数字换成别的记数系统，
    // 时长看起来就像乱码。
    return String.format(java.util.Locale.US, "%02d:%02d", min, sec)
}

/** 读取当前窗口亮度；系统默认 -1 时按 0.5 估算，手势从中间开始调。 */
private fun currentWindowBrightness(context: Context): Float {
    val window = context.findHostActivity()?.window ?: return 0.5f
    val value = window.attributes.screenBrightness
    return if (value < 0f) 0.5f else value
}

/** 把亮度写回窗口，不改系统设置，退出播放页后由调用方还原。 */
private fun applyWindowBrightness(context: Context, value: Float) {
    val window = context.findHostActivity()?.window ?: return
    val attrs = window.attributes
    attrs.screenBrightness = value.coerceIn(0.02f, 1f)
    window.attributes = attrs
}

private fun Context.findHostActivity(): Activity? {
    var ctx: Context = this
    while (ctx is ContextWrapper) {
        if (ctx is Activity) return ctx
        ctx = ctx.baseContext
    }
    return null
}
