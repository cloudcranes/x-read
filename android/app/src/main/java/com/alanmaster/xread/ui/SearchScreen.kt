package com.alanmaster.xread.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import com.alanmaster.xread.data.Book
import com.alanmaster.xread.data.ReadCountFormat
import com.alanmaster.xread.data.TextHit
import kotlinx.coroutines.delay

/**
 * 搜索页。
 *
 * 结构：**顶栏（标题 + 搜索动作）→ 48dp 搜索框 → 状态区**。
 *
 * 搜索框独立成行（不再塞进 TopAppBar 的 title 里）：
 * 顶栏里放输入框会让标题栏高度被撑到 64dp，且输入框宽度被返回键挤掉。
 *
 * 状态区三态分明：**历史**（还没搜过）、**结果**（搜到了）、**失败**（能重试）。
 * 旧实现把「没有结果」和「搜索失败」混成一句灰字，用户不知道是该换词还是该重试，
 * 现在错误态单独成块并带重试按钮。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    initialQuery: String,
    results: List<Book>,
    loading: Boolean,
    /** 是否已经搜过一次；false 时显示历史与提示，而不是「没有结果」。 */
    searched: Boolean = false,
    /** 搜索失败原因；非空时优先展示它，而不是「没有结果」。 */
    error: String = "",
    /** 本地保存的最近搜索词，新的在前。 */
    history: List<String> = emptyList(),
    /** 「大家在搜」的热词；只在搜索框获得焦点时露出。 */
    hotWords: List<String> = emptyList(),
    searchTabType: Int = 3,
    onSelectTabType: (Int, String) -> Unit = { _, _ -> },
    /** 搜索结果是否已经到底；true 时不再请求下一页。 */
    exhausted: Boolean = true,
    moreLoading: Boolean = false,
    onMore: () -> Unit = {},
    /** 有已下载的书时才露出「书库」tab：否则搜本地只会永远空手而归。 */
    libraryAvailable: Boolean = false,
    libraryHits: List<TextHit> = emptyList(),
    librarySearching: Boolean = false,
    libraryDone: Boolean = false,
    libraryScanned: Int = 0,
    libraryTotal: Int = 0,
    onLibrarySearch: (String) -> Unit = {},
    onLibraryOpen: (TextHit) -> Unit = {},
    onBack: () -> Unit,
    onSearch: (String) -> Unit,
    onPickHistory: (String) -> Unit = onSearch,
    onClearHistory: () -> Unit = {},
    onRemoveHistory: (String) -> Unit = {},
    onOpen: (Book) -> Unit,
) {
    // 以 initialQuery 为 key：从书架换关键词再进来时要跟着更新
    var query by remember(initialQuery) { mutableStateOf(initialQuery) }
    // 「大家在搜」只在搜索框被点进去时露出：平常它只是发现页的一块内容，
    // 摆在搜索页只会让人以为这些词是「我搜过的」。
    var fieldFocused by remember { mutableStateOf(false) }
    var libraryMode by rememberSaveable { mutableStateOf(false) }

    // 本地扫描是逐章读盘，按键即搜会让它反复被取消；300ms 静默后再跑一轮。
    LaunchedEffect(query, libraryMode) {
        if (!libraryMode) return@LaunchedEffect
        delay(300)
        onLibrarySearch(query)
    }

    val placeholder = "书名、作者、主角"

    Scaffold(
        topBar = {
            XReadTopBar(
                title = "搜索",
                onBack = onBack,
                actions = {
                    TextButton(
                        onClick = { onSearch(query) },
                        enabled = query.isNotBlank(),
                    ) {
                        Text("搜索", fontSize = 14.sp, color = MaterialTheme.colorScheme.primary)
                    }
                },
            )
        }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            CompactSearchField(
                value = query,
                onValueChange = { query = it },
                placeholder = placeholder,
                onImeSearch = { onSearch(query) },
                onFocusChange = { fieldFocused = it },
                modifier = Modifier.padding(horizontal = Space.gutter, vertical = Space.sm),
            )

            val searchTabs = listOf(
                "综合" to 1,
                "小说" to 3,
            )
            LazyRow(
                contentPadding = PaddingValues(horizontal = Space.gutter, vertical = Space.xs),
                horizontalArrangement = Arrangement.spacedBy(Space.sm),
            ) {
                if (libraryAvailable) {
                    item(key = "tab-library") {
                        SelectChip(
                            label = "书库",
                            selected = libraryMode,
                            onClick = { libraryMode = true },
                        )
                    }
                }
                items(searchTabs) { (title, type) ->
                    SelectChip(
                        label = title,
                        selected = !libraryMode && searchTabType == type,
                        onClick = {
                            libraryMode = false
                            onSelectTabType(type, query)
                        },
                    )
                }
            }

            when {
                libraryMode -> LibraryResults(
                    hits = libraryHits,
                    query = query,
                    searching = librarySearching,
                    done = libraryDone,
                    scanned = libraryScanned,
                    total = libraryTotal,
                    onOpen = onLibraryOpen,
                )
                loading -> Loading()
                error.isNotEmpty() -> SearchErrorState(error) { onSearch(query) }
                results.isNotEmpty() -> {
                    val sectionHint = when (searchTabType) {
                        1 -> "综合结果"
                        else -> "小说结果"
                    }
                    SectionHeader(sectionHint, "${results.size} 本", index = "RESULTS")
                    // 搜索结果是**行**不是网格：同名书很多（「十日终焉」能搜出十几本），
                    // 网格只放得下封面和书名，分不清哪本是要找的；行能多给简介与评分。
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(bottom = Space.xxl),
                    ) {
                        items(results, key = { it.bookId }) { book ->
                            // 有声书在综合 tab 里也混着，只能按条目自己判断
                            SearchResultRow(book, audio = book.isAudio, onClick = { onOpen(book) })
                            DividerLine()
                        }
                        item(key = "search-more") {
                            SearchMoreFooter(
                                count = results.size,
                                moreLoading = moreLoading,
                                exhausted = exhausted,
                                onMore = onMore,
                            )
                        }
                    }
                }
                searched -> EmptyHint(
                    text = "没有找到匹配的作品，换个关键词试试",
                    actionLabel = "清空重搜",
                    onAction = { query = ""; onSearch("") },
                )
                else -> {
                    if (history.isNotEmpty()) {
                        HistoryBlock(history, onPickHistory, onClearHistory, onRemoveHistory)
                    }
                    if (fieldFocused && hotWords.isNotEmpty()) {
                        HotWordsBlock(hotWords, onPickHistory)
                    } else {
                        EmptyHint("输入关键词后搜索")
                    }
                }
            }
        }
    }
}

/**
 * 书库检索结果。
 *
 * 按书分组而不是按命中时间平铺：同一本书里的十几处命中，读者会想「这一章翻完再翻下一章」，
 * 打散之后每次跳转都要重新想一遍自己在哪本里。
 */
@Composable
private fun LibraryResults(
    hits: List<TextHit>,
    query: String,
    searching: Boolean,
    done: Boolean,
    scanned: Int,
    total: Int,
    onOpen: (TextHit) -> Unit,
) {
    if (hits.isEmpty()) {
        if (searching) {
            Column(
                Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                CircularProgressIndicator(
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp),
                )
                Text(
                    text = if (total > 0) "已扫描 $scanned / $total 章" else "正在读取书库",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = Space.sm),
                )
            }
            return
        }
        if (query.isBlank() || !done) {
            EmptyHint("输入关键词，在已下载的正文里查找")
            return
        }
        EmptyHint(
            text = "已下载的正文里没有「$query」\n目录页可以缓存更多章节后再搜。",
            actionLabel = "换个词",
            onAction = { },
        )
        return
    }

    val groups = remember(hits) { hits.groupBy { it.bookId } }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = Space.xxl),
    ) {
        item(key = "library-header") {
            SectionHeader(
                title = "书库结果",
                hint = if (searching) "${hits.size} 处 · 继续扫描中" else "${hits.size} 处",
                index = "LIBRARY",
            )
        }
        groups.forEach { (bookId, group) ->
            item(key = "book-$bookId") {
                Text(
                    text = "${group.first().bookTitle} · ${group.size} 处",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Space.gutter, vertical = Space.sm),
                )
            }
            items(group, key = { "${it.chapterId}-${it.paragraphIndex}" }) { hit ->
                TextHitRow(hit = hit, query = query, onClick = { onOpen(hit) })
                DividerLine()
            }
        }
    }
}

@Composable
private fun TextHitRow(hit: TextHit, query: String, onClick: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = Space.gutter, vertical = Space.sm),
    ) {
        Text(
            text = hit.chapterTitle.ifBlank { "第 ${hit.paragraphIndex + 1} 段" },
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = highlight(hit.snippet, query),
            fontSize = 13.sp,
            lineHeight = 20.sp,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 2.dp),
        )
    }
}

/** 命中词描底：扫读时要能一眼看到自己搜的那两个字落在哪。 */
@Composable
private fun highlight(text: String, query: String): AnnotatedString {
    val q = query.trim()
    if (q.isEmpty()) return AnnotatedString(text)
    val tint = MaterialTheme.colorScheme.primary
    return buildAnnotatedString {
        append(text)
        var from = 0
        while (from <= text.length - q.length) {
            val at = text.indexOf(q, from, ignoreCase = true)
            if (at < 0) break
            addStyle(
                SpanStyle(background = tint.copy(alpha = 0.18f), fontWeight = FontWeight.SemiBold),
                at,
                at + q.length,
            )
            from = at + q.length
        }
    }
}

/**
 * 统一搜索框：44dp 高、**直角**、左侧放大镜、聚焦时描边转墨色。
 *
 * v3 把输入框也改成直角：刊物语言里没有气泡输入框。
 * 聚焦色用 `onSurface`（墨）而不是朱红——朱红留给「主操作」，
 * 输入框聚焦只是状态，不该抢按钮的位置。
 *
 * 自绘而不是用 `OutlinedTextField`：M3 的输入框最小高度是 56dp，
 * 压到 48dp 会把提示文字挤掉；而这个框要出现在搜索页、分类页、目录页三处，
 * 自己画一份才能保证三处的高度、圆角、聚焦色完全一致。
 *
 * 放在本文件而不是 `Components.kt` 是因为这三个页面同属「发现 / 目录」这一组，
 * 共享组件文件由其它页面共用，不为这三处单独开一个公共入口。
 */
@Composable
internal fun CompactSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    /** 键盘「搜索」键的回调；为空则只更新文本。 */
    onImeSearch: (() -> Unit)? = null,
    /** 焦点变化回调；搜索页靠它决定要不要露出「大家在搜」。 */
    onFocusChange: (Boolean) -> Unit = {},
) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(Radius.none)
    val borderColor =
        if (focused) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(44.dp)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, borderColor, shape)
            .padding(start = Space.md, end = Space.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Filled.Search,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp),
        )
        Box(
            Modifier
                .weight(1f)
                .padding(start = Space.sm),
        ) {
            if (value.isEmpty()) {
                Text(
                    text = placeholder,
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                    color = ink3,
                    maxLines = 1,
                )
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium.copy(
                    color = MaterialTheme.colorScheme.onSurface,
                ),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { onImeSearch?.invoke() }),
                modifier = Modifier
                    .fillMaxWidth()
                    .onFocusChanged {
                        focused = it.isFocused
                        onFocusChange(it.isFocused)
                    },
            )
        }
        if (value.isNotEmpty()) {
            IconButton(onClick = { onValueChange("") }) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = "清空",
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

/**
 * 搜索结果行：封面 + 书名 + 作者/分类/状态 + 两行简介 + 右侧评分。
 *
 * 评分右对齐成列——搜索结果常常连着七八本，右边缘对齐才扫得动。
 * 简介两行是搜索页与榜单页的关键差异：搜同一本书名会出现大量同名作品，
 * 只有简介能帮用户分辨。
 *
 * 听书结果额外标一行「在听 N」：有声书的 readCount 存的是在听人数，
 * 标成「在读」会误导——同一个词在两个 tab 里含义不同。
 */
@Composable
private fun SearchResultRow(book: Book, audio: Boolean = false, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = Space.gutter, vertical = Space.sm),
        verticalAlignment = Alignment.Top,
    ) {
        BookCover(
            title = book.title,
            coverUrl = book.coverUrl,
            modifier = Modifier
                .width(52.dp)
                .aspectRatio(0.74f),
        )
        Column(
            Modifier
                .weight(1f)
                .padding(horizontal = Space.md),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = book.title,
                fontSize = 14.sp,
                lineHeight = 20.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = listOfNotNull(
                    book.author.takeIf { it.isNotBlank() },
                    book.category.takeIf { it.isNotBlank() },
                    statusLabel(book.status),
                ).joinToString(" · "),
                fontSize = 11.sp,
                lineHeight = 16.sp,
                color = ink3,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (audio && book.readCount > 0) {
                Text(
                    text = "在听 ${ReadCountFormat.short(book.readCount)}",
                    fontSize = 11.sp,
                    lineHeight = 16.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
            if (book.description.isNotBlank()) {
                Text(
                    text = book.description,
                    fontSize = 11.sp,
                    lineHeight = 17.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 1.dp),
                )
            }
        }
        if (book.score.isNotBlank()) {
            Text(
                text = book.score,
                fontFamily = Serif,
                fontSize = 14.sp,
                lineHeight = 20.sp,
                fontWeight = FontWeight.Bold,
                color = rankGold,
                maxLines = 1,
            )
        }
    }
}

/** 搜索失败态：说明原因 + 一个可点的重试按钮。 */
@Composable
private fun SearchErrorState(message: String, onRetry: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(Space.xxl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = Icons.Filled.CloudOff,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(32.dp),
        )
        Text(
            text = "搜索失败",
            fontSize = 15.sp,
            fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(top = Space.md),
        )
        Text(
            text = message,
            fontSize = 12.sp,
            lineHeight = 18.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = Space.xs),
        )
        SecondaryButton(
            text = "重试",
            onClick = onRetry,
            modifier = Modifier.padding(top = Space.lg),
        )
    }
}

/** 最近搜索：一排可点的关键词 + 清空。 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HistoryBlock(
    history: List<String>,
    onPick: (String) -> Unit,
    onClear: () -> Unit,
    onRemove: (String) -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        SectionHeader(
            title = "最近搜索",
            index = "RECENT",
            action = "清空" to onClear,
        )
        FlowRow(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = Space.gutter),
            horizontalArrangement = Arrangement.spacedBy(Space.sm),
            verticalArrangement = Arrangement.spacedBy(Space.sm),
        ) {
            history.forEach { word ->
                HistoryChip(
                    label = word,
                    onClick = { onPick(word) },
                    onRemove = { onRemove(word) },
                )
            }
        }
    }
}

/**
 * 历史词条：点词条直接搜，点右侧叉只删这一条。
 *
 * 之前历史只有一个「清空」：想删掉某个不想被看见的词，只能把整段历史清掉。
 * 叉放在词条内部而不是长按菜单里——历史就一排词，长按是隐藏动作。
 */
@Composable
private fun HistoryChip(label: String, onClick: () -> Unit, onRemove: () -> Unit) {
    Row(
        Modifier
            .height(28.dp)
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(Radius.none)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .clickable(onClick = onClick)
                .padding(start = 10.dp, end = 2.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = label,
                fontSize = 12.sp,
                lineHeight = 16.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
        IconButton(
            onClick = onRemove,
            modifier = Modifier.size(28.dp),
        ) {
            Icon(
                imageVector = Icons.Filled.Close,
                contentDescription = "删除搜索记录「$label」",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(12.dp),
            )
        }
    }
}

/**
 * 「大家在搜」：只在搜索框被点进去时露出的热搜词。
 *
 * 平常它在发现页占一整块，搬进搜索页后也没必要一直挂着——没点搜索框时，
 * 用户看到的应该是自己搜过什么，而不是「这些词大家都在搜」。
 * 点了搜索框才把热搜词摊开，顺手也能当推荐词用。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HotWordsBlock(words: List<String>, onPick: (String) -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        SectionHeader(
            title = "大家在搜",
            index = "HOT",
        )
        FlowRow(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = Space.gutter),
            horizontalArrangement = Arrangement.spacedBy(Space.sm),
            verticalArrangement = Arrangement.spacedBy(Space.sm),
        ) {
            words.forEach { word ->
                SelectChip(
                    label = word,
                    selected = false,
                    onClick = { onPick(word) },
                )
            }
        }
    }
}

/**
 * 结果列表底部的翻页条。
 *
 * 不用「点击加载更多」按钮：搜索结果是一路往下看的，多一次点击就多一次打断。
 * 滚到底自动取下一页，同时留一行文案说明现在是什么状态，
 * 免得读者不知道是卡住了还是真没有了。
 */
@Composable
private fun SearchMoreFooter(
    count: Int,
    moreLoading: Boolean,
    exhausted: Boolean,
    onMore: () -> Unit,
) {
    LaunchedEffect(count, moreLoading, exhausted) {
        if (moreLoading || exhausted || count <= 0) return@LaunchedEffect
        onMore()
    }
    Box(
        Modifier
            .fillMaxWidth()
            .padding(vertical = Space.lg),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (moreLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(12.dp),
                    strokeWidth = 1.5.dp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = "正在加载",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = Space.xs),
                )
            } else {
                Text(
                    text = if (exhausted) "已加载全部 $count 本" else "继续下滑自动加载",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
