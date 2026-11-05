package com.alanmaster.xread.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Leaderboard
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.alanmaster.xread.data.Book
import com.alanmaster.xread.data.DramaSeries
import com.alanmaster.xread.data.HomeBoard

/**
 * 发现页：小说、漫画、短剧三大板块。
 */
@Composable
fun DiscoverScreen(
    section: Int = 0,
    onSelectSection: (Int) -> Unit = {},
    // 小说
    ranked: List<Book>,
    recommended: List<Book>,
    newBooks: List<Book>,
    boards: List<HomeBoard>,
    moreLoading: Boolean,
    feedError: String = "",
    exhausted: Boolean,
    loading: Boolean,
    onRefresh: () -> Unit,
    onSearch: () -> Unit,
    onRank: () -> Unit,
    onCategory: () -> Unit,
    onAudio: () -> Unit = {},
    onBoard: (Int) -> Unit,
    onMore: () -> Unit,
    onOpen: (Book) -> Unit,
    // 漫画
    mangaFeed: List<Book> = emptyList(),
    mangaLoading: Boolean = false,
    mangaMoreLoading: Boolean = false,
    mangaExhausted: Boolean = false,
    onMoreManga: () -> Unit = {},
    mangaCategory: String = "全部",
    onSelectMangaCategory: (String) -> Unit = {},
    motionComics: List<DramaSeries> = emptyList(),
    onOpenDrama: (DramaSeries) -> Unit = {},
    // 短剧
    dramaFeed: List<DramaSeries> = emptyList(),
    dramaLoading: Boolean = false,
    dramaMoreLoading: Boolean = false,
    dramaExhausted: Boolean = false,
    onMoreDrama: () -> Unit = {},
    dramaCategory: String = "全部",
    onSelectDramaCategory: (String) -> Unit = {},
    onSelectDrama: (DramaSeries) -> Unit = {},
    onOpenDramaDouyin: (DramaSeries?) -> Unit = {},
) {
    var moreRequested by remember { mutableStateOf(-1) }

    Scaffold(
        topBar = {
            DiscoverHeader(
                section = section,
                onSelectSection = onSelectSection,
                onSearch = onSearch,
                onRefresh = onRefresh,
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(bottom = Space.navFloat),
        ) {
            /* -------------------- 搜索栏 -------------------- */
            item {
                val placeholder = when (section) {
                    1 -> "搜索精彩漫画、条漫、同名改编…"
                    2 -> "搜索短剧、漫剧、爆款爽剧…"
                    else -> "搜索书名、作者、主角…"
                }
                SearchBarEntry(placeholder = placeholder, onClick = onSearch)
            }

            when (section) {
                0 -> {
                    /* ==================== 小说板块 ==================== */
                    item {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = Space.gutter, vertical = Space.xs),
                            horizontalArrangement = Arrangement.spacedBy(Space.sm),
                        ) {
                            DiscoverTile(
                                title = "排行榜",
                                hint = "实时榜单 · 完本",
                                icon = Icons.Filled.Leaderboard,
                                onClick = onRank,
                                modifier = Modifier.weight(1f),
                            )
                            DiscoverTile(
                                title = "题材分类",
                                hint = "全部题材 · 男频",
                                // 原来的 Icons.Filled.Category 画的是几块细矩形，20dp 下
                                // 几乎看不出是个图标；换「宫格」的 GridView，一眼就知道是分类。
                                icon = Icons.Filled.GridView,
                                onClick = onCategory,
                                modifier = Modifier.weight(1f),
                            )
                            DiscoverTile(
                                title = "官方听书",
                                hint = "原声畅听 · 选集",
                                icon = Icons.Filled.Headphones,
                                onClick = onAudio,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }

                    if (ranked.isNotEmpty()) {
                        item {
                            SectionHeader(
                                title = "番茄热榜",
                                hint = "实时",
                                index = "01",
                                action = "完整榜单" to onRank,
                            )
                        }
                        items(ranked.take(3), key = { "rank-${it.bookId}" }) { b ->
                            RankRow(
                                rank = ranked.indexOfFirst { it.bookId == b.bookId } + 1,
                                title = b.title,
                                coverUrl = b.coverUrl,
                                author = b.author,
                                category = b.category,
                                status = b.status,
                                readCount = b.readCount,
                                onClick = { onOpen(b) },
                            )
                            DividerLine()
                        }
                        item { BookStrip(ranked.drop(3).take(17), onOpen) }
                    }

                    if (newBooks.isNotEmpty()) {
                        item {
                            SectionHeader(
                                title = "新书速递",
                                hint = "刚上榜",
                                index = "02",
                                action = "更多" to onRank,
                            )
                        }
                        item { BookStrip(newBooks.take(20), onOpen) }
                    }

                    boards.filter { it.name != "新书榜" }.forEach { board ->
                        item {
                            SectionHeader(
                                title = board.name,
                                hint = "${board.books.size} 本",
                                action = "完整榜单" to { onBoard(board.algorithm) },
                            )
                        }
                        items(board.books.take(3), key = { "board-${board.algorithm}-${it.bookId}" }) { b ->
                            RankRow(
                                rank = board.books.indexOfFirst { it.bookId == b.bookId } + 1,
                                title = b.title,
                                coverUrl = b.coverUrl,
                                author = b.author,
                                category = b.category,
                                status = b.status,
                                readCount = b.readCount,
                                onClick = { onOpen(b) },
                            )
                            DividerLine()
                        }
                        item { BookStrip(board.books.drop(3).take(17), onOpen) }
                    }

                    if (recommended.isNotEmpty()) {
                        item {
                            SectionHeader("猜你喜欢", "${recommended.size} 本 · 继续下滑自动加载")
                        }
                        item {
                            RecommendationGrid(recommended, onOpen)
                        }
                        item {
                            LaunchedEffect(recommended.size, moreLoading, exhausted) {
                                if (moreLoading || exhausted) return@LaunchedEffect
                                if (moreRequested == recommended.size) return@LaunchedEffect
                                moreRequested = recommended.size
                                onMore()
                            }
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(Space.xl),
                                contentAlignment = Alignment.Center,
                            ) {
                                when {
                                    moreLoading -> Text(
                                        "正在取更多…",
                                        fontSize = 13.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    exhausted -> Text(
                                        "已经到底了 · 共 ${recommended.size} 本",
                                        fontSize = 13.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    else -> TextButton(onClick = onMore) {
                                        Text("换一批", fontWeight = FontWeight.SemiBold)
                                    }
                                }
                            }
                        }
                    }

                    if (loading && ranked.isEmpty() && recommended.isEmpty()) {
                        item { Loading() }
                    }
                    if (!loading && ranked.isEmpty() && recommended.isEmpty()) {
                        item {
                            EmptyHint(
                                text = feedError.ifEmpty { "暂时取不到推荐。点右上角刷新，或直接搜索书名。" },
                                actionLabel = "刷新",
                                onAction = onRefresh,
                            )
                        }
                    }
                }

                1 -> {
                    /* ==================== 漫画板块 ==================== */
                    item {
                        val mangaCategories = listOf("全部", "热血", "恋爱", "悬疑", "玄幻", "都市", "古风", "穿越", "搞笑")
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = Space.gutter, vertical = Space.xs),
                            horizontalArrangement = Arrangement.spacedBy(Space.sm),
                        ) {
                            items(mangaCategories) { cat ->
                                SelectChip(
                                    label = cat,
                                    selected = mangaCategory == cat,
                                    onClick = { onSelectMangaCategory(cat) },
                                )
                            }
                        }
                    }

                    if (motionComics.isNotEmpty()) {
                        item {
                            SectionHeader("动态漫剧", "${motionComics.size} 部精选 · 有声视听", index = "MOTION")
                        }
                        item {
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = Space.gutter),
                                horizontalArrangement = Arrangement.spacedBy(Space.md),
                            ) {
                                items(motionComics) { mc ->
                                    MotionComicCard(mc, onClick = { onOpenDrama(mc) })
                                }
                            }
                        }
                    }

                    item {
                        SectionHeader(
                            title = if (mangaCategory == "全部") "热门漫画" else "「$mangaCategory」漫画",
                            hint = if (mangaFeed.isNotEmpty()) "${mangaFeed.size} 部连载" else "精彩展陈",
                            index = "01",
                        )
                    }

                    if (mangaFeed.isNotEmpty()) {
                        item {
                            RecommendationGrid(mangaFeed, onOpen)
                        }
                        item {
                            FeedMoreFooter(
                                count = mangaFeed.size,
                                moreLoading = mangaMoreLoading,
                                exhausted = mangaExhausted,
                                onMore = onMoreManga,
                                unit = "部",
                                loadingHint = "正在取更多漫画…",
                            )
                        }
                    } else if (mangaLoading) {
                        item { Loading() }
                    } else {
                        item {
                            EmptyHint(
                                text = "暂无此类漫画推荐，请点击刷新重试",
                                actionLabel = "刷新",
                                onAction = { onSelectMangaCategory(mangaCategory) },
                            )
                        }
                    }
                }

                2 -> {
                    /* ==================== 短剧板块 ==================== */
                    item {
                        val dramaCategories = listOf("全部", "热播短剧", "动态漫剧", "战神逆袭", "甜宠言情", "玄幻爽剧")
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = Space.gutter, vertical = Space.xs),
                            horizontalArrangement = Arrangement.spacedBy(Space.sm),
                        ) {
                            items(dramaCategories) { cat ->
                                SelectChip(
                                    label = cat,
                                    selected = dramaCategory == cat,
                                    onClick = { onSelectDramaCategory(cat) },
                                )
                            }
                        }
                    }

                    val filteredDramas = when (dramaCategory) {
                        "全部" -> dramaFeed
                        "热播短剧" -> dramaFeed.filter { !it.isMotionComic }
                        "动态漫剧" -> dramaFeed.filter { it.isMotionComic }
                        else -> dramaFeed.filter {
                            it.title.contains(dramaCategory) ||
                            it.intro.contains(dramaCategory) ||
                            it.tags.any { tag -> tag.contains(dramaCategory) }
                        }.ifEmpty { dramaFeed }
                    }

                    if (filteredDramas.isNotEmpty()) {
                        item {
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = Space.gutter, vertical = Space.xs)
                                    .clickable { onOpenDramaDouyin(filteredDramas.firstOrNull()) },
                                shape = RoundedCornerShape(12.dp),
                                color = Color(0xFF1E1E1E),
                                border = BorderStroke(1.dp, Color(0xFFFF2C55).copy(alpha = 0.5f))
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(36.dp)
                                            .clip(CircleShape)
                                            .background(Color(0xFFFF2C55)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(Icons.Filled.PlayArrow, contentDescription = null, tint = Color.White, modifier = Modifier.size(22.dp))
                                    }
                                    Spacer(Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                            Text("抖音沉浸刷剧模式", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                            Surface(
                                                shape = RoundedCornerShape(4.dp),
                                                color = Color(0xFFFF2C55).copy(alpha = 0.25f),
                                            ) {
                                                Text("全屏沉浸", color = Color(0xFFFF2C55), fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp))
                                            }
                                        }
                                        Text("上下滑屏切集 · 双击红心 · 长按2倍速", color = Color.White.copy(alpha = 0.7f), fontSize = 11.sp)
                                    }
                                    Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = Color.White.copy(alpha = 0.6f))
                                }
                            }
                        }

                        item {
                            SectionHeader(
                                title = if (dramaCategory == "全部") "短剧精选" else "「$dramaCategory」精选",
                                hint = "${filteredDramas.size} 部 · 原生高清播放",
                                index = "THEATRE",
                            )
                        }
                        item {
                            LazyVerticalGrid(
                                columns = GridCells.Fixed(2),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(((filteredDramas.size + 1) / 2 * 270).dp),
                                contentPadding = PaddingValues(horizontal = Space.gutter),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                                userScrollEnabled = false,
                            ) {
                                gridItems(filteredDramas, key = { it.seriesId }) { item ->
                                    DramaCard(drama = item, onClick = { onSelectDrama(item) })
                                }
                            }
                        }
                        item {
                            FeedMoreFooter(
                                count = dramaFeed.size,
                                moreLoading = dramaMoreLoading,
                                exhausted = dramaExhausted,
                                onMore = onMoreDrama,
                                unit = "部",
                                loadingHint = "正在取更多短剧…",
                            )
                        }
                    } else if (dramaLoading) {
                        item { Loading() }
                    } else {
                        item {
                            EmptyHint(
                                text = "暂无相关短剧内容",
                                actionLabel = "刷新",
                                onAction = onRefresh,
                            )
                        }
                    }
                }
            }
        }
    }
}

/* --------------------------- 页头 --------------------------- */

@Composable
private fun DiscoverHeader(
    section: Int,
    onSelectSection: (Int) -> Unit,
    onSearch: () -> Unit,
    onRefresh: () -> Unit,
) {
    val sections = listOf("小说", "漫画", "短剧")
    val subtitle = when (section) {
        1 -> "条漫展陈 · 精彩连载 · 翻页阅读"
        2 -> "热播剧场 · 动态漫剧 · 弹幕互动"
        else -> "精选好书 · 热门榜单 · 离线听书"
    }

    Surface(color = MaterialTheme.colorScheme.background) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(start = Space.gutter, end = Space.xs, top = Space.md, bottom = Space.xs)
        ) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(Space.lg),
                    verticalAlignment = Alignment.Bottom,
                ) {
                    sections.forEachIndexed { idx, title ->
                        val selected = section == idx
                        Column(
                            modifier = Modifier.clickable { onSelectSection(idx) },
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text(
                                text = title,
                                fontFamily = Serif,
                                fontSize = if (selected) 28.sp else 20.sp,
                                lineHeight = 36.sp,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                color = if (selected) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                            )
                            if (selected) {
                                Box(
                                    Modifier
                                        .padding(top = 4.dp)
                                        .width(20.dp)
                                        .height(2.5.dp)
                                        .background(MaterialTheme.colorScheme.primary)
                                )
                            } else {
                                Spacer(Modifier.height(6.5.dp))
                            }
                        }
                    }
                }
                IconButton(onClick = onSearch) {
                    Icon(Icons.Filled.Search, "搜索", Modifier.size(22.dp))
                }
                IconButton(onClick = onRefresh) {
                    Icon(Icons.Filled.Refresh, "刷新", Modifier.size(22.dp))
                }
            }
            Text(
                text = subtitle,
                fontSize = 12.sp,
                lineHeight = 17.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}

/* --------------------------- 搜索栏入口 --------------------------- */

@Composable
private fun SearchBarEntry(placeholder: String, onClick: () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Space.gutter, vertical = Space.sm)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(Radius.none),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(
            Modifier.padding(horizontal = Space.md, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Filled.Search,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(Space.sm))
            Text(
                text = placeholder,
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/* --------------------------- 快捷入口卡 --------------------------- */

@Composable
private fun DiscoverTile(
    title: String,
    hint: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(Radius.none),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(Modifier.padding(vertical = Space.md, horizontal = Space.md)) {
            Icon(
                icon,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = title,
                fontSize = 15.sp,
                lineHeight = 21.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(top = Space.xs),
            )
            Text(
                text = hint,
                fontSize = 11.sp,
                lineHeight = 15.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/* --------------------------- 动态漫剧卡片 --------------------------- */

@Composable
private fun MotionComicCard(
    drama: DramaSeries,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .width(130.dp)
            .clickable(onClick = onClick),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(0.75f)
                .clip(RoundedCornerShape(4.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
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
                        .size(36.dp)
                        .align(Alignment.Center),
                    tint = MaterialTheme.colorScheme.outline,
                )
            }
            Surface(
                color = MaterialTheme.colorScheme.tertiary,
                shape = RoundedCornerShape(bottomEnd = 4.dp),
                modifier = Modifier.align(Alignment.TopStart),
            ) {
                Text(
                    text = "漫剧",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                )
            }
        }
        Text(
            text = drama.title,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 4.dp),
        )
        Text(
            text = if (drama.episodeCount > 0) "全 ${drama.episodeCount} 集" else "连载中",
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/* --------------------------- 横向书籍带 --------------------------- */

@Composable
private fun BookStrip(books: List<Book>, onOpen: (Book) -> Unit) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = Space.gutter),
        horizontalArrangement = Arrangement.spacedBy(Space.md),
    ) {
        itemsIndexed(books, key = { _, b -> b.bookId }) { _, book ->
            Box(Modifier.width(100.dp)) {
                BookCard(book, onClick = { onOpen(book) })
            }
        }
    }
}

/* --------------------------- 推荐网格 --------------------------- */

/* 下滑到底自动续页：与小说推荐流的「继续下滑自动加载」保持一致 */
@Composable
private fun FeedMoreFooter(
    count: Int,
    moreLoading: Boolean,
    exhausted: Boolean,
    onMore: () -> Unit,
    unit: String,
    loadingHint: String,
) {
    LaunchedEffect(count, moreLoading, exhausted) {
        if (moreLoading || exhausted || count <= 0) return@LaunchedEffect
        onMore()
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = Space.md),
        contentAlignment = Alignment.Center,
    ) {
        when {
            moreLoading -> Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(Space.sm))
                Text(loadingHint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
            }
            exhausted -> Text(
                text = "已加载全部 $count $unit",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline,
            )
            else -> Text(
                text = "继续下滑自动加载",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline,
            )
        }
    }
}

@Composable
private fun RecommendationGrid(books: List<Book>, onOpen: (Book) -> Unit) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(104.dp),
        modifier = Modifier
            .fillMaxWidth()
            .height(((books.size + 3) / 4 * 210).dp),
        contentPadding = PaddingValues(horizontal = Space.gutter),
        horizontalArrangement = Arrangement.spacedBy(Space.md),
        verticalArrangement = Arrangement.spacedBy(Space.lg),
        userScrollEnabled = false,
    ) {
        gridItems(books, key = { it.bookId }) { book ->
            BookCard(book, onClick = { onOpen(book) })
        }
    }
}
