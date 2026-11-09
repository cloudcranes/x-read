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
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alanmaster.xread.data.Book
import com.alanmaster.xread.data.BookStatus
import com.alanmaster.xread.data.HomeBoard
import com.alanmaster.xread.data.ReadCountFormat

/** 自动续页上限：避免 LaunchedEffect 随列表增长反复拉取，把整页撑到上千本。 */
private const val MaxAutoLoadBooks = 120

/** 单个网格块的书本数：把每块高度压在 Compose Constraints 可编码范围内。 */
internal const val RecommendationChunkSize = 60

/**
 * 发现页（方案 B「主笔甄选流」）：只做小说。
 *
 * 一张「今日主笔力荐」折页大卡定调，热榜/新书/各榜单收成一条纯封面横滑带，
 * 推荐区走三列竖版海报流。数据仍来自 ranked / newBooks / boards / recommended，
 * 业务回调（onRank / onCategory / onAudio / onBoard / onMore / onOpen）一个不改。
 */
@Composable
fun DiscoverScreen(
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
    // 榜单板块默认全部收进「更多榜单」；展开后逐个露出完整榜单入口。
    var boardsExpanded by remember { mutableStateOf(false) }

    // 已收敛为纯小说阅读器：新书榜并入 01 横滑带，漫剧/短剧榜不再露出。
    val boardList = boards.filter {
        it.name != "新书榜" && !it.name.contains("漫剧") && !it.name.contains("短剧")
    }
    val hero = ranked.firstOrNull()
    // 01 横滑带：热榜魁首之外 + 新书 + 各榜单，按 bookId 去重，信息量足且不重复封面。
    val stripBooks = (ranked.drop(1) + newBooks + boardList.flatMap { it.books })
        .distinctBy { it.bookId }
        .take(20)

    Scaffold(
        topBar = { DiscoverHeader(onRefresh = onRefresh) },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(bottom = Space.navFloat),
        ) {
            /* -------------------- 搜索栏 -------------------- */
            item {
                SearchBarEntry(placeholder = "搜索书名、作者、主角…", onClick = onSearch)
            }

            /* -------------------- 今日主笔力荐 -------------------- */
            if (hero != null) {
                item { HeroFeatureCard(hero, onClick = { onOpen(hero) }) }
            }

            /* -------------------- 三色快捷入口 -------------------- */
            item {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Space.gutter, vertical = Space.xs),
                    horizontalArrangement = Arrangement.spacedBy(Space.sm),
                ) {
                    DiscoverTile(
                        title = "排行榜",
                        hint = "多维榜单",
                        icon = Icons.Filled.Leaderboard,
                        badgeColor = MaterialTheme.colorScheme.tertiaryContainer,
                        iconTint = rankGold,
                        onClick = onRank,
                        modifier = Modifier.weight(1f),
                    )
                    DiscoverTile(
                        title = "题材书库",
                        hint = "36 类细分",
                        icon = Icons.Filled.GridView,
                        badgeColor = MaterialTheme.colorScheme.secondaryContainer,
                        iconTint = MaterialTheme.colorScheme.secondary,
                        onClick = onCategory,
                        modifier = Modifier.weight(1f),
                    )
                    DiscoverTile(
                        title = "官方听书",
                        hint = "声临其境",
                        icon = Icons.Filled.Headphones,
                        badgeColor = MaterialTheme.colorScheme.primaryContainer,
                        iconTint = MaterialTheme.colorScheme.primary,
                        onClick = onAudio,
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            /* -------------------- 01 飙升与完本 -------------------- */
            if (stripBooks.isNotEmpty()) {
                item {
                    SectionHeader(
                        title = "飙升与完本",
                        hint = "读者热捧",
                        index = "01",
                        indexColor = MaterialTheme.colorScheme.primary,
                        action = "全部榜单" to onRank,
                    )
                }
                item { BookStrip(stripBooks, onOpen) }
            }

            /* -------------------- 更多榜单（默认折叠） -------------------- */
            if (boardsExpanded) {
                boardList.forEach { board ->
                    item {
                        SectionHeader(
                            title = board.name,
                            hint = "${board.books.size} 本",
                            action = "完整榜单" to { onBoard(board.algorithm) },
                        )
                    }
                    item { BookStrip(board.books.take(20), onOpen) }
                }
            } else if (boardList.isNotEmpty()) {
                item {
                    MoreBoardsEntry(
                        remaining = boardList.size,
                        onClick = { boardsExpanded = true },
                    )
                }
            }

            /* -------------------- 02 个性化淘书 -------------------- */
            if (recommended.isNotEmpty()) {
                item {
                    SectionHeader(
                        title = "个性化淘书",
                        hint = "下拉自动续页",
                        index = "02",
                        indexColor = MaterialTheme.colorScheme.primary,
                        action = "换一批" to onMore,
                    )
                }
                // 拆成固定大小的网格块：单块高度有上限，避免长列表把
                // LazyVerticalGrid 的 height() 推到 Constraints 无法编码的数值。
                val chunks = recommended.chunked(RecommendationChunkSize)
                chunks.forEach { chunk ->
                    item { RecommendationGrid(chunk, onOpen) }
                }
                item {
                    // 到达上限后只提示手动「换一批」，不再自动把列表拉到无限长。
                    val autoLoadCapReached = recommended.size >= MaxAutoLoadBooks
                    LaunchedEffect(recommended.size, moreLoading, exhausted, autoLoadCapReached) {
                        if (moreLoading || exhausted || autoLoadCapReached) return@LaunchedEffect
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
                            autoLoadCapReached -> Text(
                                "已展示 ${recommended.size} 本 · 点「换一批」继续",
                                fontSize = 12.sp,
                                color = ink3,
                            )
                            else -> Text(
                                "继续下滑，自动加载更多",
                                fontSize = 12.sp,
                                color = ink3,
                            )
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
private fun DiscoverHeader(onRefresh: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.background) {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .padding(start = Space.gutter, end = Space.xs, top = Space.lg, bottom = Space.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = "发现",
                    fontFamily = Serif,
                    fontSize = 24.sp,
                    lineHeight = 32.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Text(
                    text = "拾页精选 · 纯粹小说阅读",
                    fontSize = 11.sp,
                    lineHeight = 15.sp,
                    color = ink3,
                )
            }
            // 搜索已有整行入口，页头只保留刷新；显式 48dp 触控，图标仍是 22dp。
            IconButton(onClick = onRefresh, modifier = Modifier.size(48.dp)) {
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

/* --------------------------- 主笔力荐折页大卡 --------------------------- */

@Composable
private fun HeroFeatureCard(book: Book, onClick: () -> Unit) {
    val primary = MaterialTheme.colorScheme.primary
    val surface = MaterialTheme.colorScheme.surface
    val statusLabel = when (book.status) {
        BookStatus.Finished -> "完本"
        BookStatus.Serial -> "连载"
        BookStatus.Unknown -> null
    }
    val meta = listOfNotNull(
        book.author.takeIf { it.isNotBlank() },
        if (book.readCount > 0) "${ReadCountFormat.short(book.readCount)}人在读" else null,
        statusLabel,
    ).joinToString(" · ").ifBlank { "编辑甄选" }
    // 接口简介常带换行、&#10; 实体与【标签】，折成单行短引文，避免两行里大半行被空行吃掉。
    val quote = book.description
        .replace(Regex("&#(?:10|13|xa0|160);?|&nbsp;|<br\\s*/?>", RegexOption.IGNORE_CASE), " ")
        .replace(Regex("\\s+"), " ")
        .trim()
        .takeIf { it.isNotBlank() }
        ?: "翻开第一页，便舍不得合上。"

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Space.gutter, vertical = Space.sm)
            .heightIn(min = 132.dp)
            .clickable(onClick = onClick, role = Role.Button)
            .semantics(mergeDescendants = true) {
                contentDescription = "今日主笔力荐，${book.title}，点击查看详情"
            },
        shape = RoundedCornerShape(Radius.sm),
        color = surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Box {
            // 整卡先铺朱红，右侧内容再以宣纸白覆盖，天然留出 4dp 折页竖线。
            Box(Modifier.matchParentSize().background(primary))
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(start = 4.dp)
                    .background(surface)
                    .padding(horizontal = Space.md, vertical = Space.md),
            ) {
                Row(verticalAlignment = Alignment.Top) {
                    BookCover(
                        title = book.title,
                        coverUrl = book.coverUrl,
                        modifier = Modifier.width(68.dp).height(96.dp),
                    )
                    Spacer(Modifier.width(Space.md))
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = "今日主笔力荐",
                            fontSize = 10.sp,
                            lineHeight = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = primary,
                            modifier = Modifier
                                .clip(RoundedCornerShape(Radius.sm))
                                .background(MaterialTheme.colorScheme.primaryContainer)
                                .padding(horizontal = Space.sm, vertical = Space.xxs),
                        )
                        Spacer(Modifier.height(Space.xs))
                        Text(
                            text = "热榜魁首 · 编辑甄选",
                            fontSize = 10.sp,
                            lineHeight = 14.sp,
                            color = primary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Spacer(Modifier.height(Space.xxs))
                        Text(
                            text = book.title,
                            fontFamily = Serif,
                            fontSize = 15.sp,
                            lineHeight = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Spacer(Modifier.height(Space.xxs))
                        Text(
                            text = quote,
                            fontSize = 11.sp,
                            lineHeight = 16.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                Spacer(Modifier.height(Space.sm))
                Text(
                    text = meta,
                    fontSize = 10.sp,
                    lineHeight = 14.sp,
                    color = ink3,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/* --------------------------- 快捷入口卡 --------------------------- */

@Composable
private fun DiscoverTile(
    title: String,
    hint: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    badgeColor: Color = Color.Transparent,
    iconTint: Color? = null,
) {
    val tint = iconTint ?: MaterialTheme.colorScheme.primary
    Surface(
        modifier = modifier
            .heightIn(min = 58.dp)
            .clickable(onClick = onClick, role = Role.Button)
            .semantics(mergeDescendants = true) { contentDescription = "$title，$hint" },
        shape = RoundedCornerShape(Radius.sm),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = Space.sm, vertical = Space.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(Radius.sm))
                    .background(badgeColor),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp), tint = tint)
            }
            Spacer(Modifier.width(Space.sm))
            Column(Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = hint,
                    fontSize = 10.sp,
                    lineHeight = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/* --------------------------- 横向封面带 --------------------------- */

@Composable
private fun BookStrip(books: List<Book>, onOpen: (Book) -> Unit) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = Space.gutter),
        horizontalArrangement = Arrangement.spacedBy(Space.md),
    ) {
        itemsIndexed(books, key = { _, b -> b.bookId }) { _, book ->
            StripBookCard(book, onClick = { onOpen(book) })
        }
    }
}

/** 90×126 纯封面卡：书名两行 + 一行作者/题材，不铺大段文字。 */
@Composable
private fun StripBookCard(book: Book, onClick: () -> Unit) {
    Column(
        Modifier
            .width(90.dp)
            .clickable(onClick = onClick, role = Role.Button)
            .semantics(mergeDescendants = true) { contentDescription = "打开《${book.title}》" },
    ) {
        BookCover(
            title = book.title,
            coverUrl = book.coverUrl,
            modifier = Modifier.width(90.dp).height(126.dp),
        )
        Spacer(Modifier.height(Space.xs))
        Text(
            text = book.title,
            fontSize = 11.sp,
            lineHeight = 15.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = book.author.takeIf { it.isNotBlank() }
                ?: book.category.takeIf { it.isNotBlank() }
                ?: "小说",
            fontSize = 10.sp,
            lineHeight = 14.sp,
            color = ink3,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
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
                PosterCard(book, onClick = { onOpen(book) })
            }
        }
    }
}

/** 三列竖版海报：书名单行截断，封面按 0.70 宽高比撑满列宽。 */
@Composable
private fun PosterCard(book: Book, onClick: () -> Unit) {
    Column(
        Modifier
            .clickable(onClick = onClick, role = Role.Button)
            .semantics(mergeDescendants = true) { contentDescription = "打开《${book.title}》" },
    ) {
        BookCover(
            title = book.title,
            coverUrl = book.coverUrl,
            modifier = Modifier.fillMaxWidth().aspectRatio(0.70f),
        )
        Spacer(Modifier.height(Space.xs))
        Text(
            text = book.title,
            fontSize = 12.sp,
            lineHeight = 17.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = book.author.takeIf { it.isNotBlank() }
                ?: book.category.takeIf { it.isNotBlank() }
                ?: "小说",
            fontSize = 10.sp,
            lineHeight = 14.sp,
            color = ink3,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * 「更多榜单」入口：把榜单板块收一行，展开后逐个露出完整榜单入口。
 */
@Composable
private fun MoreBoardsEntry(remaining: Int, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clickable(onClick = onClick, role = Role.Button)
            .semantics(mergeDescendants = true) {
                contentDescription = "更多榜单，还有 $remaining 个"
            }
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
