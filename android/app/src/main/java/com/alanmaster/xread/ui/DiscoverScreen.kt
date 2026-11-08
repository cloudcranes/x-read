package com.alanmaster.xread.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Leaderboard
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alanmaster.xread.data.Book
import com.alanmaster.xread.data.HomeBoard

/** 榜单板块首屏默认展开的数量，其余收进「更多榜单」入口。 */
private const val BoardsPreviewCount = 2

/**
 * 发现页：只做小说，聚焦榜单、分类与新书。
 */
@Composable
fun DiscoverScreen(
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
) {
    var moreRequested by remember { mutableStateOf(-1) }
    // 榜单板块默认只展开前 2 个，其余收进「更多榜单」。首屏先看到热榜和一本榜单，
    // 剩下的一排标题对读者没有区分度，只是把滚动距离拉长。
    var boardsExpanded by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            DiscoverHeader(onRefresh = onRefresh)
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
                SearchBarEntry(placeholder = "搜索书名、作者 · 精选好书与榜单", onClick = onSearch)
            }

            // 只做小说：以下板块无条件渲染，不再随 section 切换。
                    /* ==================== 小说板块 ==================== */
                    item {
                        // 三宫格整行抬到 96dp：图标与标题横向对齐，hint 不再挤在标题下面换一行，
                        // 首屏从「三张小方块 + 一大片留白」变成一条可扫的快捷入口带。
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
                        }
                        item { DividerLine() }
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

                    val boardList = boards.filter { it.name != "新书榜" }
                    val shownBoards = if (boardsExpanded) boardList else boardList.take(BoardsPreviewCount)
                    shownBoards.forEach { board ->
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
                        }
                        item { DividerLine() }
                        item { BookStrip(board.books.drop(3).take(17), onOpen) }
                    }
                    if (!boardsExpanded && boardList.size > BoardsPreviewCount) {
                        item {
                            MoreBoardsEntry(
                                remaining = boardList.size - BoardsPreviewCount,
                                onClick = { boardsExpanded = true },
                            )
                        }
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
    }
}

/* --------------------------- 页头 --------------------------- */

@Composable
private fun DiscoverHeader(
    onRefresh: () -> Unit,
) {
    Surface(color = MaterialTheme.colorScheme.background) {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .padding(start = Space.gutter, end = Space.xs, top = Space.lg, bottom = Space.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "发现",
                fontFamily = Serif,
                fontSize = 24.sp,
                lineHeight = 32.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.weight(1f),
            )
            // 搜索已有整行入口，页头只保留刷新；避免重复图标挤占段落标题。
            IconButton(onClick = onRefresh) {
                Icon(Icons.Filled.Refresh, "刷新", Modifier.size(22.dp))
            }
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
            .heightIn(min = 48.dp)
            .clickable(onClick = onClick, role = Role.Button),
        shape = RoundedCornerShape(Radius.sm),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(
            Modifier
                .fillMaxHeight()
                .padding(horizontal = Space.md),
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
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
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
        modifier = modifier
            .heightIn(min = 96.dp)
            .clickable(onClick = onClick, role = Role.Button),
        shape = RoundedCornerShape(Radius.none),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = Space.sm, vertical = Space.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                icon,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Column(Modifier.padding(start = Space.sm)) {
                Text(
                    text = title,
                    fontSize = 14.sp,
                    lineHeight = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = hint,
                    fontSize = 11.sp,
                    lineHeight = 15.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = Space.xxs),
                )
            }
        }
    }
}

/* --------------------------- 动态漫剧卡片 --------------------------- */

/* --------------------------- 横向书籍带 --------------------------- */

@Composable
private fun BookStrip(books: List<Book>, onOpen: (Book) -> Unit) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = Space.gutter),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        itemsIndexed(books, key = { _, b -> b.bookId }) { _, book ->
            Box(Modifier.width(108.dp)) {
                BookCard(book, onClick = { onOpen(book) })
            }
        }
    }
}

/* --------------------------- 推荐网格 --------------------------- */

/** 卡片最小宽度：再窄书名会折成三行，读不出书名。 */
private val CardMinWidth = 104.dp

/** 卡片之间的横向间距。 */
private val CardSpacing = Space.md

/** 网格行间距。 */
private val CardRowSpacing = Space.xl

/** 封面之外的高度：书名两行 + 元信息一行 + 与封面的间隙。 */
private val CardTextHeight = 66.dp

/**
 * 按可用宽度算网格列数。
 *
 * LazyVerticalGrid 自己会按 Adaptive 决定列数，但外层是 LazyColumn，
 * 必须给出确定高度，所以这里得用同一套规则先算出来——两边算法不一致
 * 就会少算一行，最后一行直接被裁掉。
 */
internal fun recommendationColumns(
    availableWidth: Dp,
    minCardWidth: Dp = CardMinWidth,
    spacing: Dp = CardSpacing,
): Int = ((availableWidth + spacing) / (minCardWidth + spacing)).toInt().coerceAtLeast(2)

/** 单行高度：封面（按 0.70 的宽高比）+ 书名与元信息。 */
internal fun recommendationRowHeight(cardWidth: Dp): Dp = cardWidth / 0.70f + CardTextHeight

/** 网格总高度：行数 × 行高 + 行间空隙。 */
internal fun recommendationGridHeight(
    bookCount: Int,
    availableWidth: Dp,
    rowSpacing: Dp = CardRowSpacing,
): Dp {
    if (bookCount <= 0) return 0.dp
    val columns = recommendationColumns(availableWidth)
    val cardWidth = (availableWidth - CardSpacing * (columns - 1)) / columns
    val rows = (bookCount + columns - 1) / columns
    return recommendationRowHeight(cardWidth) * rows + rowSpacing * (rows - 1)
}

@Composable
private fun RecommendationGrid(books: List<Book>, onOpen: (Book) -> Unit) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val available = maxWidth - Space.gutter * 2
        LazyVerticalGrid(
            columns = GridCells.Fixed(recommendationColumns(available)),
            modifier = Modifier
                .fillMaxWidth()
                .height(recommendationGridHeight(books.size, available)),
            contentPadding = PaddingValues(horizontal = Space.gutter),
            horizontalArrangement = Arrangement.spacedBy(CardSpacing),
            verticalArrangement = Arrangement.spacedBy(CardRowSpacing),
            userScrollEnabled = false,
        ) {
            gridItems(books, key = { it.bookId }) { book ->
                BookCard(book, onClick = { onOpen(book) })
            }
        }
    }
}

/**
 * 「更多榜单」入口：把首屏之外的榜单收一行。
 *
 * 榜单板块每个都带三行排名加一条书带，三四个叠在一起读者根本滑不到
 * 「猜你喜欢」；这里默认只展开前两个，剩下的按需翻开。
 */
@Composable
private fun MoreBoardsEntry(remaining: Int, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = Space.gutter, vertical = Space.xl),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "更多榜单",
            fontFamily = Serif,
            fontSize = 16.sp,
            lineHeight = 22.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.width(Space.sm))
        Text(
            text = "还有 $remaining 个",
            fontSize = 11.sp,
            lineHeight = 15.sp,
            color = ink3,
        )
        Spacer(Modifier.weight(1f))
        Icon(
            Icons.Filled.ChevronRight,
            contentDescription = null,
            tint = ink3,
            modifier = Modifier.size(18.dp),
        )
    }
}
