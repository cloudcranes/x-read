package com.alanmaster.xread.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alanmaster.xread.data.Chapter
import com.alanmaster.xread.data.OfflineRepo
import kotlinx.coroutines.launch

/**
 * 章号直达：查询串是纯数字且落在 `1..size` 时，返回该章在**原始目录**里的下标。
 *
 * 上千章的目录里「滚到第 800 章」要滑几十屏，直接输章号是最短路径。返回 null
 * 有两种情况——不是纯数字（交给标题筛选），或者数字越界（那里没有章节）。
 */
internal fun chapterJumpIndex(query: String, size: Int): Int? {
    if (query.isEmpty() || !query.all { it.isDigit() }) return null
    val n = query.toIntOrNull() ?: return null
    return if (n in 1..size) n - 1 else null
}

/**
 * 目录页。
 *
 * 上千章的目录不能只靠滚动：打开时定位到当前章，过滤时回到顶部让首个命中进视野。
 * 当前章用主色 + 左侧竖条标出，不只靠文字颜色（色弱用户也要能分辨）。
 *
 * v2 调整：
 * - **正序 / 倒序**切换：本地状态，不动 MainActivity。倒序时章号跟着走，
 *   列表从最新一章开始——追更的人几乎总是从末尾开始找。
 * - 目录头部**吸顶**：书名 + 章数 + 排序切换始终可见，滚到第 800 章时
 *   还能确认自己看的哪本书。
 * - 行高 48dp，序号右对齐在弱色，当前章强调色 + 左侧 2dp 竖条。
 * - **章号直达**：搜索框里输纯数字（如 `800`）直接滚到第 800 章，不筛列表；
 *   输文字才按标题过滤。939 章的目录滑到第 800 章要几十屏，这是最短路径。
 * - 右下角悬浮「定位到当前章」胶囊按钮：定位过一次后，用户想回当前位置
 *   不必再滚几千行。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TocScreen(
    bookTitle: String,
    chapters: List<Chapter>,
    currentChapterId: String?,
    onBack: () -> Unit,
    onSelect: (String) -> Unit,
    /** 已缓存章节的键（[OfflineRepo.chapterKey]），由 ViewModel 一次扫盘得到。 */
    cached: Set<String> = emptySet(),
    /** 正在补缓存：未缓存的圆点用主色呼吸，表示「进行中」。 */
    downloading: Boolean = false,
    /** 长按菜单：缓存本章 / 缓存后 [CACHE_BATCH] 章。 */
    onCache: (Chapter, Int) -> Unit = { _, _ -> },
    onUncache: (Chapter) -> Unit = {},
    /** 点「补全」时把当前窗口补齐（前 2 章 ～ 后 N 章）。 */
    onFillWindow: (() -> Unit)? = null,
    /**
     * 目录刷新完成计数：每重拉一次目录 +1。
     *
     * 与书架下拉同一个理由——布尔在同一帧里翻转会被合帧吃掉，指示器就收不回来。
     */
    tocRefreshDone: Int = 0,
    /** 下拉重拉目录：追更的书常年在长，读者要知道「有没有新章」。 */
    onRefreshChapters: () -> Unit = {},
) {
    var query by remember { mutableStateOf("") }
    // 倒序 = 从最新一章往回排；默认正序（与上游目录顺序一致）
    var reversed by remember { mutableStateOf(false) }

    // 章号直达：纯数字直接滚到那一章，不筛列表——上下文的相邻章还在，
    // 读者能顺势继续翻；只有文字查询才过滤。
    val jump = chapterJumpIndex(query.trim(), chapters.size)
    val matched = remember(chapters, query, jump) {
        val q = query.trim()
        when {
            q.isEmpty() -> chapters
            jump != null -> chapters
            else -> chapters.filter { it.title.contains(q, ignoreCase = true) }
        }
    }
    // 倒序时把整个列表反过来；章号仍按**原始顺序**编号，翻转后不会串号
    val ordered = remember(matched, reversed) {
        if (reversed) matched.reversed().toList() else matched
    }
    // 章号 = 该章在全书里的序号（与筛选、排序无关），过滤后不能重排
    val ordinals = remember(chapters) {
        val map = HashMap<String, Int>(chapters.size)
        chapters.forEachIndexed { i, c -> map[c.id] = i + 1 }
        map
    }

    // 呼吸灯：只用于「下载中」的空心圆点，恒定成本，不随列表长度变化
    val haptic = rememberHaptic()
    val pulse by rememberInfiniteTransition(label = "toc-pulse").animateFloat(
        initialValue = 0.25f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(900, easing = LinearEasing), RepeatMode.Reverse),
        label = "toc-pulse-alpha",
    )
    var menuFor by remember { mutableStateOf<String?>(null) }

    // 已缓存计数只统计目录里真实存在的章：删书后残留的键不算
    val cachedCount = remember(cached, chapters) {
        chapters.count { OfflineRepo.chapterKey(it.id) in cached }
    }
    val listState = rememberLazyListState()
    // 悬浮「定位到当前章」是在 click 回调里滚动，需要显式作用域
    val scope = rememberCoroutineScope()
    // 打开目录时定位到当前章：上千章的目录每次从第 1 章滚起等于不可用。
    // 旧实现把 filtered.size 当 key，每输入一个字都重新定位回当前章：
    // 当前章命中就跳走、不命中就完全不滚，等于筛选时无法看到首个命中。
    LaunchedEffect(currentChapterId, chapters.size) {
        val target = currentChapterId ?: return@LaunchedEffect
        if (target.isEmpty() || chapters.isEmpty()) return@LaunchedEffect
        val idx = ordered.indexOfFirst { it.id == target }
        if (idx >= 0) listState.scrollToItem(idx)
    }
    // 查询变化后：章号直达滚到那一章，文字筛选回到顶部让首个命中进视野，
    // 清空则回到当前章——否则清完筛选会停在全书第 1 章，和上面的 CURRENT 对不上。
    LaunchedEffect(query, reversed) {
        val q = query.trim()
        val j = chapterJumpIndex(q, chapters.size)
        when {
            j != null -> listState.scrollToItem(if (reversed) chapters.size - 1 - j else j)
            q.isNotEmpty() -> listState.scrollToItem(0)
            else -> {
                val idx = ordered.indexOfFirst { it.id == currentChapterId }
                if (idx >= 0) listState.scrollToItem(idx)
            }
        }
    }
    // 切换排序方向后当前章可能跑到别处，重新定位一次，免得看起来像「章丢了」。
    // 章号直达生效时上面那条已经负责滚动，这里让路。
    LaunchedEffect(reversed) {
        if (chapterJumpIndex(query.trim(), chapters.size) != null) return@LaunchedEffect
        val target = currentChapterId ?: return@LaunchedEffect
        val idx = ordered.indexOfFirst { it.id == target }
        if (idx >= 0) listState.scrollToItem(idx)
    }

    Scaffold(
        topBar = {
            XReadTopBar(
                title = "目录",
                subtitle = bookTitle.ifBlank { "共 ${chapters.size} 章" },
                onBack = onBack,
            )
        }
    ) { padding ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            // 下拉 = 重拉目录。放在 LazyColumn 外层：空目录（新书还没章节）也得能下拉。
            var pullRefreshing by remember { mutableStateOf(false) }
            LaunchedEffect(tocRefreshDone) { pullRefreshing = false }
            PullToRefreshBox(
                isRefreshing = pullRefreshing,
                onRefresh = {
                    pullRefreshing = true
                    onRefreshChapters()
                },
                modifier = Modifier.fillMaxSize(),
            ) {
            Column(Modifier.fillMaxSize()) {
                // 工具行：章节总数 + 正序/倒序切换
                Row(
                    Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                        .padding(horizontal = Space.gutter),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "共 ${chapters.size} 章" +
                            if (query.isNotBlank() && jump == null) " · 匹配 ${ordered.size}" else "" +
                            if (cached.isNotEmpty()) " · 已缓存 $cachedCount" else "",
                        fontSize = 12.sp,
                        lineHeight = 17.sp,
                        color = ink3,
                        modifier = Modifier.weight(1f),
                    )
                    if (onFillWindow != null && cachedCount < chapters.size) {
                        TextButton(
                            onClick = onFillWindow,
                            contentPadding = PaddingValues(horizontal = Space.sm),
                        ) {
                            Text("补全", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                    OrderToggle(reversed = reversed, onToggle = { reversed = !reversed })
                }
                CompactSearchField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = "章号直达，或筛选章节标题",
                    modifier = Modifier.padding(horizontal = Space.gutter, vertical = Space.xs),
                )
                // 统计条：把筛选区与长列表切开，同时说明「当前在第几章」
                val currentOrdinal = ordinals[currentChapterId] ?: 0
                if (currentOrdinal > 0) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .padding(horizontal = Space.gutter, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "CURRENT",
                            fontSize = 10.sp,
                            lineHeight = 14.sp,
                            letterSpacing = 1.2.sp,
                            color = ink3,
                        )
                        Text(
                            text = "  第 $currentOrdinal 章",
                            fontSize = 11.sp,
                            lineHeight = 15.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Box(Modifier.weight(1f))
                        Text(
                            text = "$currentOrdinal / ${chapters.size}",
                            fontSize = 10.sp,
                            lineHeight = 14.sp,
                            color = ink3,
                        )
                    }
                }
                if (ordered.isEmpty()) {
                    EmptyHint("没有匹配的章节")
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = Space.huge),
                    ) {
                        itemsIndexed(ordered, key = { _, c -> c.id }) { _, c ->
                            val active = c.id == currentChapterId
                            val key = OfflineRepo.chapterKey(c.id)
                            TocRow(
                                title = c.title.ifEmpty { c.id },
                                ordinal = ordinals[c.id] ?: 0,
                                active = active,
                                cached = key in cached,
                                downloading = downloading,
                                pulse = pulse,
                                menuOpen = menuFor == c.id,
                                onClick = { onSelect(c.id) },
                                onLongClick = {
                                    haptic.medium()
                                    menuFor = c.id
                                },
                                onDismissMenu = { menuFor = null },
                                onCacheOne = { menuFor = null; onCache(c, 1) },
                                onCacheBatch = { menuFor = null; onCache(c, CACHE_BATCH) },
                                onUncache = { menuFor = null; onUncache(c) },
                            )
                        }
                    }
                }
            }

            // 悬浮「定位到当前章」：定位过一次之后，回到当前位置不必再滚几千行
            val currentIndex = ordered.indexOfFirst { it.id == currentChapterId }
            if (currentIndex >= 0 && currentIndex > 2) {
                LocateButton(
                    onClick = { scope.launch { listState.animateScrollToItem(currentIndex) } },
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(Space.gutter),
                )
            }
            }
        }
    }
}

/** 一次「缓存后 N 章」的 N。 */
private const val CACHE_BATCH = 50

/**
 * 目录行：缓存圆点 + 章名 + 右侧章号。
 *
 * 当前章：主色文字 + 左侧 2dp 竖条 + 极浅的强调底色。
 * 三重信号（颜色 / 竖条 / 底色）任一失效也还能辨认。
 *
 * 圆点表达缓存状态，用**形状**而不只是颜色区分有无（色弱也分得出）：
 * 实心弱色 = 已缓存，空心 = 未缓存，主色呼吸 = 正在补。
 * 长按整行弹菜单，与书架的长按习惯一致，不额外加按钮占行宽。
 */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun TocRow(
    title: String,
    ordinal: Int,
    active: Boolean,
    cached: Boolean,
    downloading: Boolean,
    pulse: Float,
    menuOpen: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onDismissMenu: () -> Unit,
    onCacheOne: () -> Unit,
    onCacheBatch: () -> Unit,
    onUncache: () -> Unit,
) {
    Box {
        Row(
            Modifier
                .fillMaxWidth()
                .height(48.dp)
                .background(
                    if (active) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                    else Color.Transparent
                )
                .combinedClickable(onClick = onClick, onLongClick = onLongClick),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // 当前章用左侧竖条标出，不只靠文字颜色
            Box(
                Modifier
                    .padding(start = Space.gutter, end = Space.sm)
                    .width(2.dp)
                    .height(18.dp)
                    .clip(RoundedCornerShape(1.dp))
                    .background(
                        if (active) MaterialTheme.colorScheme.primary else Color.Transparent
                    ),
            )
            Text(
                text = title,
                fontSize = 14.sp,
                lineHeight = 20.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = if (active) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            CachedDot(cached = cached, downloading = downloading, pulse = pulse)
            if (ordinal > 0) {
                Text(
                    text = "$ordinal",
                    fontSize = 11.sp,
                    lineHeight = 15.sp,
                    color = if (active) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = Space.xs, end = Space.gutter),
                )
            }
        }
        DropdownMenu(expanded = menuOpen, onDismissRequest = onDismissMenu) {
            DropdownMenuItem(
                text = { Text("缓存本章", fontSize = 13.sp) },
                onClick = onCacheOne,
            )
            DropdownMenuItem(
                text = { Text("缓存后 $CACHE_BATCH 章", fontSize = 13.sp) },
                onClick = onCacheBatch,
            )
            // 没缓存就没有可删的，菜单项一起藏起来，避免点了没反应
            if (cached) {
                DropdownMenuItem(
                    text = { Text("删除本章缓存", fontSize = 13.sp) },
                    onClick = onUncache,
                )
            }
        }
    }
}

/** 行尾 6dp 缓存标记：实心 = 已缓存，空心 = 未缓存，主色呼吸 = 正在补。 */
@Composable
private fun CachedDot(cached: Boolean, downloading: Boolean, pulse: Float) {
    val hollow = !cached && !downloading
    val color = when {
        cached -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
        downloading -> MaterialTheme.colorScheme.primary.copy(alpha = pulse)
        else -> MaterialTheme.colorScheme.outlineVariant
    }
    Box(
        Modifier
            .padding(end = Space.sm)
            .size(6.dp)
            .then(
                if (hollow) Modifier.border(1.dp, color, CircleShape)
                else Modifier.background(color, CircleShape)
            ),
    )
}

/** 正序 / 倒序切换：图标 + 文字，48dp 触控区。 */
@Composable
private fun OrderToggle(reversed: Boolean, onToggle: () -> Unit) {
    Row(
        modifier = Modifier
            .height(48.dp)
            .clip(RoundedCornerShape(Radius.sm))
            .clickable { onToggle() }
            .padding(horizontal = Space.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        androidx.compose.material3.Icon(
            imageVector = Icons.Filled.SwapVert,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(18.dp),
        )
        Text(
            text = if (reversed) "倒序" else "正序",
            fontSize = 13.sp,
            lineHeight = 18.sp,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = Space.xs),
        )
    }
}

/** 右下角悬浮胶囊：定位到当前章。 */
@Composable
private fun LocateButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .height(40.dp)
            .clip(RoundedCornerShape(Radius.none))
            .background(MaterialTheme.colorScheme.primary)
            .clickable { onClick() }
            .padding(horizontal = Space.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        androidx.compose.material3.Icon(
            imageVector = Icons.Filled.GpsFixed,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier.size(16.dp),
        )
        Text(
            text = "定位到当前章",
            fontSize = 13.sp,
            lineHeight = 18.sp,
            color = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier.padding(start = Space.xs),
        )
    }
}
