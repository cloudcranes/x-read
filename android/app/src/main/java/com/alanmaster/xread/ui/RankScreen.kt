package com.alanmaster.xread.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alanmaster.xread.data.Book
import com.alanmaster.xread.data.ContentParser
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 榜单页（v3「刊」）。
 *
 * 结构：**页头（榜单 / 日期）→ 榜种标签条 → 分类芯片 → 统计条 → 榜行**。
 *
 * v3 的核心变化是**把后端给的数字摆出来**：每行右侧一列「在读人数」，
 * 用等宽数字右对齐，整屏可以竖着扫读比较。次级信息从「作者 · 分类 · N 章」
 * 换成「作者 · 分类 · 连载/完本」——章数对榜单读者没用，连载状态才有用。
 *
 * **不做的两件事**（上游确实没有，不能编）：
 *  1. **名次升降**：原生核心 `rank_data` 载荷里没有 `rankPosDiff`/`currentPos`，
 *     那是 Python 后端走官网接口才有的字段。所以不画 `↑1` / `↓1`。
 *  2. **分页**：同一载荷里 `has_more=false`、`next_offset=0`，一次给 30 本，客户端拿不到
 *     任何「还有下一页」的信号。更关键的是——**榜单不是稳定列表**：实测同一个 `offset`
 *     连取四次，30 本里两两只重叠 0~2 本，顺序也全不同；换个 `offset` 确实会换一批书，
 *     但那只是又一次随机抽样，不是「第 13~42 名」。所以照 `offset` 递增去翻，用户看到的
 *     会是重复与跳变，等于编造一个上游并不存在的分页。统计条只说「已加载 N 本」。
 *
 * 名次序号（1、2、3…）是**列表顺序**，这就是榜单的语义本身；
 * 它与上游的 `rank` 字段一致（同样是顺序位次），不是编造。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RankScreen(
    books: List<Book>,
    catalog: ContentParser.RankCatalog?,
    algorithm: Int,
    categoryId: String,
    loading: Boolean,
    onBack: () -> Unit,
    onPick: (Int, String) -> Unit,
    onOpen: (Book) -> Unit,
    // 滚动位置由调用方持有：从详情页退回来时还停在原来挑到的那一行，
    // 而不是从头再翻一遍。换榜种/分类时才回到顶部。
    listState: LazyListState = rememberLazyListState(),
) {
    var lastPick by rememberSaveable { mutableStateOf(algorithm to categoryId) }
    LaunchedEffect(algorithm, categoryId) {
        if (lastPick != algorithm to categoryId) {
            lastPick = algorithm to categoryId
            listState.scrollToItem(0)
        }
    }
    val tabs = catalog?.tabs.orEmpty()
    val categories = catalog?.categories.orEmpty()
    val currentTab = tabs.firstOrNull { it.algorithm == algorithm }?.name.orEmpty()
    val currentCategory = categories.firstOrNull { it.id == categoryId }?.name ?: "全部"
    // 上游分类有十几个，全部平铺要占三行，每次换榜都要在芯片堆里找。
    // 默认只铺一行（当前选中项一定在其中），要看全再点「全部 N」。
    var expandedCategories by rememberSaveable { mutableStateOf(false) }
    val categoryNames = categories.map { it.name }
    val shownCategories = visibleCategories(categoryNames, currentCategory, expandedCategories)

    Scaffold(
        topBar = {
            RankPageHead(
                title = if (currentTab.isEmpty()) "榜单" else currentTab,
                subtitle = currentCategory,
                onBack = onBack,
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            if (tabs.isNotEmpty()) {
                RankTabStrip(
                    tabs = tabs.map { it.name },
                    selected = currentTab,
                    onSelect = { name ->
                        tabs.firstOrNull { it.name == name }?.let { onPick(it.algorithm, categoryId) }
                    },
                )
            }
            if (categories.isNotEmpty()) {
                CategoryCapsules(
                    categories = shownCategories,
                    selected = currentCategory,
                    expanded = expandedCategories,
                    collapsible = categoryNames.size > CategoryRowLimit,
                    total = categoryNames.size,
                    onToggle = { expandedCategories = !expandedCategories },
                    onSelect = { name ->
                        categories.firstOrNull { it.name == name }?.let { onPick(algorithm, it.id) }
                    },
                )
            }
            // 统计条：只说真话——本页给了几本就是几本，没有分页就不提页码。
            if (books.isNotEmpty()) {
                RankStatStrip(count = books.size, category = currentCategory)
            }
            when {
                loading -> Loading()
                books.isEmpty() -> EmptyHint("该榜暂无数据")
                else -> LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = Space.xxl),
                ) {
                    itemsIndexed(books, key = { _, b -> b.bookId }) { index, book ->
                        RankRow(
                            rank = index + 1,
                            title = book.title,
                            coverUrl = book.coverUrl,
                            author = book.author,
                            // 选了具体分类就不逐行印书的标签：榜的分类来自上游
                            // `sub_info_list`，书行上的是 `pure_category_tags`，两套词表
                            // 对不上时会读成「这个榜挂错了书」。只有「全部」才显示书自己的标签。
                            category = if (categoryId == "0") book.category else "",
                            status = book.status,
                            readCount = book.readCount,
                            onClick = { onOpen(book) },
                        )
                        DividerLine()
                    }
                }
            }
        }
    }
}

/**
 * 页头：栏目字 + 28sp 衬线大标题 + 返回。
 *
 * 栏目字用「RANKINGS · 10-29」这种刊物眉题，交代这是什么页面、什么时候的榜单。
 * 日期取本机当前日期：这是**打开页面**的时间，不是榜单统计时间
 * （上游没给统计时间，写了就是编造）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RankPageHead(
    title: String,
    subtitle: String,
    onBack: () -> Unit,
) {
    val today = SimpleDateFormat("MM-dd", Locale.US).format(Date())
    Surface(color = MaterialTheme.colorScheme.background) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = Space.xs, end = Space.gutter, top = Space.sm, bottom = Space.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "返回",
                    modifier = Modifier.width(20.dp).height(20.dp),
                )
            }
            Column(Modifier.weight(1f).padding(start = Space.xs)) {
                Text(
                    text = "RANKINGS · $today",
                    fontSize = 10.sp,
                    lineHeight = 14.sp,
                    letterSpacing = 1.6.sp,
                    color = ink3,
                )
                Text(
                    text = title,
                    fontFamily = Serif,
                    fontSize = 28.sp,
                    lineHeight = 36.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 1,
                )
            }
            Text(
                text = subtitle,
                fontSize = 12.sp,
                lineHeight = 18.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
    }
}

/**
 * 统计条：`N 本` + 当前分类。
 *
 * 用 `surfaceVariant` 铺一条通栏底，把筛选区和榜单正文切开，
 * 同时也给出「这一屏到底有多少本」的量感。
 */
@Composable
private fun RankStatStrip(count: Int, category: String) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = Space.gutter, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "TOTAL",
            fontSize = 10.sp,
            lineHeight = 14.sp,
            letterSpacing = 1.2.sp,
            color = ink3,
        )
        Text(
            text = "  $count 本",
            style = MaterialTheme.typography.labelMedium.copy(
                fontFeatureSettings = "tnum",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            ),
        )
        Box(Modifier.weight(1f))
        Text(
            text = category,
            fontSize = 10.sp,
            lineHeight = 14.sp,
            letterSpacing = 1.2.sp,
            color = ink3,
            maxLines = 1,
        )
    }
}

/** 收起时一行最多铺几个分类芯片（不含末尾那颗「全部 N」）。 */
internal const val CategoryRowLimit = 5

/**
 * 收起状态下实际要铺出来的分类。
 *
 * 当前选中的分类**必须**留在这一行里，否则用户看不出自己站在哪个分类下；
 * 前 [limit] 个里没有它就顶掉最后一个。分类总数不超过 `limit + 1` 时直接全铺——
 * 为了藏一个芯片还多出个「全部 N」按钮反而更绕。
 */
internal fun visibleCategories(
    all: List<String>,
    selected: String,
    expanded: Boolean,
    limit: Int = CategoryRowLimit,
): List<String> {
    if (expanded || all.size <= limit + 1) return all
    val head = all.take(limit)
    return if (selected in head || selected !in all) head else head.dropLast(1) + selected
}

/**
 * 榜种切换：下划线标签条。
 *
 * 不用胶囊芯片：榜种是页面级的分段（同一时刻只有一个生效），
 * 需要和下方的分类筛选在视觉上分层——标签条靠下划线，分类靠芯片。
 */
@Composable
private fun RankTabStrip(
    tabs: List<String>,
    selected: String,
    onSelect: (String) -> Unit,
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = Space.gutter),
        horizontalArrangement = Arrangement.spacedBy(Space.lg),
    ) {
        items(tabs) { name ->
            val on = name == selected
            Column(
                modifier = Modifier
                    .height(46.dp)
                    .clickable { onSelect(name) },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = name,
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                    fontWeight = if (on) FontWeight.Bold else FontWeight.Normal,
                    color = if (on) MaterialTheme.colorScheme.onSurface
                    else ink3,
                    maxLines = 1,
                )
                // 未选中时也占同样的 2dp 高度，切换时整行不会上下跳
                Box(
                    Modifier
                        .padding(top = Space.xs)
                        .width(20.dp)
                        .height(2.dp)
                        .background(
                            if (on) MaterialTheme.colorScheme.primary else Color.Transparent
                        ),
                )
            }
        }
    }
}

/**
 * 分类筛选：胶囊芯片，自动换行（上游的分类表有十几个，一行放不下）。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CategoryCapsules(
    categories: List<String>,
    selected: String,
    expanded: Boolean,
    collapsible: Boolean,
    total: Int,
    onToggle: () -> Unit,
    onSelect: (String) -> Unit,
) {
    FlowRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Space.gutter)
            .padding(top = Space.xs, bottom = Space.sm),
        horizontalArrangement = Arrangement.spacedBy(Space.sm),
        verticalArrangement = Arrangement.spacedBy(Space.sm),
    ) {
        categories.forEach { name ->
            SelectChip(
                label = name,
                selected = name == selected,
                onClick = { onSelect(name) },
            )
        }
        if (collapsible) {
            SelectChip(
                label = if (expanded) "收起" else "全部 $total",
                selected = false,
                onClick = onToggle,
            )
        }
    }
}
