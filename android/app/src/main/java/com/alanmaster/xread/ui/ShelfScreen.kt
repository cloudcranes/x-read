package com.alanmaster.xread.ui

import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.LocalLibrary
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Update
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.alanmaster.xread.data.Book
import com.alanmaster.xread.data.BookStatus
import com.alanmaster.xread.data.DramaSeries
import com.alanmaster.xread.data.ShelfGrouping
import com.alanmaster.xread.data.ShelfItem
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 书架页：用户的个人藏书、阅读进度、最近阅读与分组管理。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShelfScreen(
    shelf: List<ShelfItem>,
    loading: Boolean,
    shelfSort: String,
    shelfGroup: String,
    shelfGroups: List<String>,
    /** 按连载状态自动分好的组（连载中 / 已完结），只列有书的那些。 */
    builtinGroups: List<ShelfGrouping.Group> = emptyList(),
    shelfMediaType: String = "all",
    onSelectMediaType: (String) -> Unit = {},
    recentDramas: List<DramaSeries> = emptyList(),
    onOpenDrama: (DramaSeries) -> Unit = {},
    updates: Map<String, Int>,
    checkingUpdates: Boolean,
    onSort: (String) -> Unit,
    onGroupFilter: (String) -> Unit,
    onCheckUpdates: () -> Unit,
    onLongPressBook: (Book) -> Unit,
    onRefresh: () -> Unit,
    /**
     * 书架数据落库计数：每完成一次刷新 +1。
     *
     * 不用布尔标记：刷新快到同一帧完成时布尔翻转会被合帧吃掉，指示器就再也收不回来。
     */
    shelfRefreshDone: Int = 0,
    /** 下拉刷新的回调；默认跟 [onRefresh] 一样。 */
    onPullRefresh: () -> Unit = onRefresh,
    onSearch: () -> Unit,
    onSettings: () -> Unit,
    onDiscover: () -> Unit,
    /** 追剧空态的「去发现短剧」；未接线时退回 [onDiscover]。 */
    onDiscoverDrama: (() -> Unit)? = null,
    onOpen: (Book) -> Unit,
    onContinue: (Book) -> Unit = {},
    /** 短剧收藏与追剧记录：都落在 OfflineRepo，进程重启后仍在。 */
    dramaFavorites: List<DramaSeries> = emptyList(),
    onOpenFavoriteDrama: (DramaSeries) -> Unit = {},
    onToggleFavoriteDrama: (DramaSeries) -> Unit = {},
    /** 批量操作：多选模式下一次性处理选中的书。 */
    onBatchGroup: (List<Book>) -> Unit = {},
    onBatchRemove: (List<Book>) -> Unit = {},
    onBatchCache: (List<Book>) -> Unit = {},
    onBatchFinish: (List<Book>) -> Unit = {},
    shelfStatus: String = "all",
    onStatusFilter: (String) -> Unit = {},
) {
    val context = LocalContext.current
    val viewPrefs = remember {
        context.getSharedPreferences("shelf_view", Context.MODE_PRIVATE)
    }
    var listView by rememberSaveable { mutableStateOf(viewPrefs.getBoolean("list", false)) }

    /*
     * 多选。状态留在页面里而不是 ViewModel：这是纯粹的界面态，转屏丢失也无所谓
     * （真的转屏了，用户重新勾一下比把半截选择状态塞进 UiState 更省心）。
     */
    var selecting by rememberSaveable { mutableStateOf(false) }
    var selectedIds by remember { mutableStateOf(emptySet<String>()) }
    /** 长按菜单锚在哪一本上；null 表示没有菜单展开。 */
    var menuFor by remember { mutableStateOf<String?>(null) }
    val haptic = rememberHaptic()
    val selectedBooks = remember(selectedIds, shelf) {
        shelf.mapNotNull { it.book.takeIf { b -> b.bookId in selectedIds } }
    }
    val toggle: (Book) -> Unit = { b ->
        selectedIds = if (b.bookId in selectedIds) selectedIds - b.bookId else selectedIds + b.bookId
    }
    val exitSelection = {
        selecting = false
        selectedIds = emptySet<String>()
    }

    Scaffold(
        topBar = {
            if (selecting) {
                ShelfSelectionHeader(
                    count = selectedIds.size,
                    total = shelf.size,
                    onToggleAll = {
                        selectedIds = if (selectedIds.size >= shelf.size && shelf.isNotEmpty()) emptySet()
                        else shelf.mapTo(mutableSetOf()) { it.book.bookId }
                    },
                    onCancel = { exitSelection() },
                )
            } else {
                ShelfHeader(
                    count = if (shelfMediaType == "drama") 0 else shelf.size,
                    checkingUpdates = checkingUpdates,
                    onSearch = onSearch,
                    onCheckUpdates = onCheckUpdates,
                    onRefresh = onRefresh,
                    onSettings = onSettings,
                )
            }
        },
    ) { padding ->
        // material3 1.3 起有官方 PullToRefreshBox：手势、指示器、收起动画都在里面，
        // 不用再自己拼 Box + nestedScroll + 指示器。
        // 指示器由 isRefreshing 布尔驱动：拉到底它自己置 true，数据回来我们置 false。
        var pullRefreshing by remember { mutableStateOf(false) }
        // 数据回来了就收起指示器。挂刷新完成计数而不是布尔：刷新快到同一帧完成时，
        // 布尔翻转会被合帧吃掉，指示器就永远停在屏上收不回来。
        LaunchedEffect(shelfRefreshDone) {
            pullRefreshing = false
        }
        Box(
            Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
        PullToRefreshBox(
            isRefreshing = pullRefreshing,
            onRefresh = {
                pullRefreshing = true
                onPullRefresh()
            },
            modifier = Modifier.fillMaxSize(),
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = Space.navFloat),
            ) {
                /* -------------------- 板块与排序 -------------------- */
                item {
                    if (selecting) {
                        // 多选是模态的：排序/分组切换会让用户勾中的书从列表里消失，
                        // 所以这里直接不给入口，并写明原因。
                        Text(
                            text = "多选中 · 排序与查更新暂不可用",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(
                                horizontal = Space.gutter, vertical = Space.sm,
                            ),
                        )
                    } else ShelfToolbar(
                        mediaType = shelfMediaType,
                        sort = shelfSort,
                        group = shelfGroup,
                        groups = shelfGroups,
                        builtinGroups = builtinGroups,
                        onSelectMediaType = onSelectMediaType,
                        onSort = onSort,
                        onGroupFilter = onGroupFilter,
                        listView = listView,
                        onToggleView = {
                            listView = !listView
                            viewPrefs.edit().putBoolean("list", listView).apply()
                        },
                        // 追剧记录只有一种卡片样式、也不参与排序，按钮留着只会让人以为点坏了。
                        sortable = shelfMediaType != "drama",
                    )
                }

                /* -------------------- 个人标记 -------------------- */
                // 与媒体类型分两行：一行管「这是什么书」，一行管「我读到哪一步」，
                // 混在一行里 chip 会多到需要横向滚两次才看得全。
                if (!selecting && shelfMediaType != "drama") {
                    item(key = "status-filter") {
                        StatusFilterRow(selected = shelfStatus, onSelect = onStatusFilter)
                    }
                }

                if (shelfMediaType == "drama") {
                    /* -------------------- 短剧收藏 + 追剧记录 -------------------- */
                    // 以前这里只有「追剧记录」，而「收藏」的星标只写进 SharedPreferences，
                    // 全仓没有第二个地方读它——用户收藏完根本找不回来。现在收藏也落盘并列出。
                    if (dramaFavorites.isNotEmpty()) {
                        item {
                            ShelfSection(title = "我的收藏", hint = "${dramaFavorites.size} 部")
                        }
                        items(dramaFavorites, key = { "fav-drama-${it.seriesId}" }) { drama ->
                            RecentDramaCard(
                                drama = drama,
                                onClick = { onOpenFavoriteDrama(drama) },
                                onToggleFavorite = { onToggleFavoriteDrama(drama) },
                            )
                            DividerLine()
                        }
                    }
                    if (recentDramas.isNotEmpty()) {
                        item {
                            ShelfSection(
                                title = "追剧记录",
                                hint = if (recentDramas.isEmpty()) "" else "${recentDramas.size} 部",
                            )
                        }
                        items(recentDramas, key = { "recent-drama-${it.seriesId}" }) { drama ->
                            RecentDramaCard(
                                drama = drama,
                                onClick = { onOpenDrama(drama) },
                                onToggleFavorite = if (dramaFavorites.any { it.seriesId == drama.seriesId }) {
                                    { onToggleFavoriteDrama(drama) }
                                } else {
                                    null
                                },
                            )
                            DividerLine()
                        }
                    }
                    if (dramaFavorites.isEmpty() && recentDramas.isEmpty()) {
                        item {
                            // 追剧记录的空态不是「书架还是空的」——短剧收藏和书架无关，
                            // 所以只留「去发现」。
                            ShelfEmpty(
                                onDiscover = onDiscoverDrama ?: onDiscover,
                                title = "还没有追剧记录",
                                description = "去「发现」的短剧板块挑一部，播放过的短剧会出现在这里。",
                                primaryLabel = "去发现短剧",
                            )
                        }
                    }
                } else {
                    /* -------------------- 我的书架 -------------------- */
                    // 「在架 N 本 · 查更新」这一行删掉了：分类已经在上面那排 chip 里，
                    // 本数挪进了页头标题，查更新挪进了页头图标，书架直接接在工具栏下面。
                    if (shelf.isNotEmpty()) {
                        items(shelf, key = { "shelf-${it.book.bookId}" }) { item ->
                            val newChapters = updates[item.book.bookId]
                            val status = shelfStatus(item, newChapters)
                            val subStatus = shelfSubStatus(item)
                            val progress = shelfProgress(item)
                            val book = item.book
                            val checked = book.bookId in selectedIds
                            val continueReading: (() -> Unit)? =
                                if (progress == null) null else ({ onContinue(book) })
                            // 多选时点按改成勾选；退出的唯一方式是顶栏「取消」或执行完动作，
                            // 免得读到一半误触把选择清空。
                            val onClick: () -> Unit = {
                                if (selecting) {
                                    haptic.light()
                                    toggle(book)
                                } else {
                                    onOpen(book)
                                }
                            }
                            val onLongClick: () -> Unit = if (selecting) {
                                ({ haptic.light(); toggle(book) })
                            } else {
                                ({ haptic.medium(); menuFor = book.bookId })
                            }
                            Box {
                                if (listView) {
                                    ShelfBookRow(
                                        book = book,
                                        status = status,
                                        statusEmphasized = newChapters != null,
                                        subStatus = subStatus,
                                        meta = shelfSeriesMeta(book),
                                        readInfo = shelfReadInfo(item),
                                        progress = progress,
                                        onContinue = if (selecting) null else continueReading,
                                        onClick = onClick,
                                        onLongClick = onLongClick,
                                    )
                                    DividerLine()
                                } else {
                                    ShelfBookCard(
                                        book = book,
                                        modifier = Modifier.padding(
                                            horizontal = Space.gutter, vertical = Space.xs,
                                        ),
                                        status = status,
                                        statusEmphasized = newChapters != null,
                                        subStatus = shelfReadInfo(item),
                                        meta = shelfSeriesMeta(book),
                                        progress = progress,
                                        onContinue = if (selecting) null else continueReading,
                                        onClick = onClick,
                                        onLongClick = onLongClick,
                                    )
                                }
                                if (selecting) {
                                    SelectionMark(
                                        selected = checked,
                                        modifier = Modifier
                                            .align(Alignment.TopEnd)
                                            .padding(end = Space.gutter + Space.sm, top = Space.md),
                                    )
                                }
                                // 长按菜单：保持「长按 = 操作菜单」的习惯，菜单里再进多选。
                                DropdownMenu(
                                    expanded = menuFor == book.bookId,
                                    onDismissRequest = { menuFor = null },
                                ) {
                                    DropdownMenuItem(
                                        text = { Text("多选", fontSize = 13.sp) },
                                        onClick = {
                                            menuFor = null
                                            selecting = true
                                            selectedIds = setOf(book.bookId)
                                        },
                                    )
                                    DropdownMenuItem(
                                        text = { Text("设置分组", fontSize = 13.sp) },
                                        onClick = {
                                            menuFor = null
                                            onLongPressBook(book)
                                        },
                                    )
                                }
                            }
                        }
                    } else if (!loading) {
                        item { ShelfEmpty(onDiscover = onDiscover) }
                    } else {
                        item { Loading() }
                    }
                }
            }
        }
            if (selecting) {
                ShelfSelectionActions(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = Space.navFloat),
                    count = selectedBooks.size,
                    onGroup = { onBatchGroup(selectedBooks); exitSelection() },
                    onCache = { onBatchCache(selectedBooks); exitSelection() },
                    onFinish = { onBatchFinish(selectedBooks); exitSelection() },
                    onRemove = { onBatchRemove(selectedBooks); exitSelection() },
                )
            }
        }
    }
}

/* --------------------------- 多选 --------------------------- */

/**
 * 多选顶栏：替代页头，只保留「已选 N 本 / 全选 / 取消」。
 *
 * 多选期间不显示搜索、查更新、刷新、设置——这些都会改变列表内容，
 * 与「我已经勾好这几本」冲突。
 */
@Composable
private fun ShelfSelectionHeader(
    count: Int,
    total: Int,
    onToggleAll: () -> Unit,
    onCancel: () -> Unit,
) {
    Surface(color = MaterialTheme.colorScheme.background) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = Space.gutter, end = Space.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "已选 $count 本",
                fontFamily = Serif,
                fontSize = 22.sp,
                lineHeight = 28.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Spacer(Modifier.weight(1f))
            TextButton(onClick = onToggleAll, enabled = total > 0) {
                Text(
                    text = if (count >= total && total > 0) "取消全选" else "全选",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            IconButton(onClick = onCancel, modifier = Modifier.size(38.dp)) {
                Icon(Icons.Filled.Close, "退出多选", Modifier.size(20.dp))
            }
        }
    }
}

/** 多选底部动作条：分组 / 缓存 / 读完 / 移出。没有选中时全部禁用。 */
@Composable
private fun ShelfSelectionActions(
    count: Int,
    onGroup: () -> Unit,
    onCache: () -> Unit,
    onFinish: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val enabled = count > 0
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = Space.sm, vertical = Space.xs),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            SelectionAction("分组", Icons.Filled.Category, enabled, onGroup)
            SelectionAction("缓存", Icons.Filled.Download, enabled, onCache)
            SelectionAction("读完", Icons.Filled.Check, enabled, onFinish)
            SelectionAction("移出", Icons.Filled.DeleteOutline, enabled, onRemove, danger = true)
        }
    }
}

@Composable
private fun SelectionAction(
    label: String,
    icon: ImageVector,
    enabled: Boolean,
    onClick: () -> Unit,
    danger: Boolean = false,
) {
    val tint = when {
        !enabled -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
        danger -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.onSurface
    }
    Column(
        Modifier
            .clip(RoundedCornerShape(Radius.sm))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = Space.md, vertical = Space.sm),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
        Text(
            text = label,
            fontSize = 11.sp,
            color = tint,
            modifier = Modifier.padding(top = 2.dp),
        )
    }
}

/* --------------------------- 分区标题 --------------------------- */

@Composable
private fun ShelfSection(
    title: String,
    hint: String,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = Space.gutter, end = Space.gutter, top = Space.md, bottom = Space.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            fontFamily = Serif,
            fontSize = 17.sp,
            lineHeight = 24.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.weight(1f, fill = false),
        )
        Spacer(Modifier.weight(1f))
        if (hint.isNotBlank()) {
            Text(hint, fontSize = 11.sp, color = ink3)
        }
    }
}

/* --------------------------- 页头 --------------------------- */

@Composable
private fun ShelfHeader(
    count: Int,
    checkingUpdates: Boolean,
    onSearch: () -> Unit,
    onCheckUpdates: () -> Unit,
    onRefresh: () -> Unit,
    onSettings: () -> Unit,
) {
    Surface(color = MaterialTheme.colorScheme.background) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = Space.gutter, end = Space.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "书架",
                fontFamily = Serif,
                fontSize = 22.sp,
                lineHeight = 28.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
            )
            if (count > 0) {
                Text(
                    text = "  $count 本",
                    fontSize = 11.sp,
                    lineHeight = 16.sp,
                    color = ink3,
                    modifier = Modifier.align(Alignment.CenterVertically),
                )
            }
            Spacer(Modifier.weight(1f))
            IconButton(onClick = onSearch) {
                Icon(Icons.Filled.Search, "搜索", Modifier.size(22.dp))
            }
            // 「查更新」原来占着书架顶部整行，挪到页头当图标：逐本比对章节数，
            // 正在查的时候转圈，避免连点。
            IconButton(
                onClick = onCheckUpdates,
                enabled = !checkingUpdates,
                modifier = Modifier.size(38.dp),
            ) {
                if (checkingUpdates) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                    )
                } else {
                    Icon(Icons.Filled.Update, "查更新", Modifier.size(22.dp))
                }
            }
            IconButton(onClick = onRefresh, modifier = Modifier.size(38.dp)) {
                Icon(Icons.Filled.Refresh, "刷新", Modifier.size(22.dp))
            }
            IconButton(onClick = onSettings, modifier = Modifier.size(38.dp)) {
                Icon(Icons.Filled.Settings, "设置", Modifier.size(22.dp))
            }
        }
    }
}


/* --------------------------- 板块与排序 --------------------------- */

@Composable
private fun StatusFilterRow(selected: String, onSelect: (String) -> Unit) {
    val options = remember {
        listOf("all" to "全部", ShelfItem.STATUS_WANT to "想读", ShelfItem.STATUS_READING to "在读", ShelfItem.STATUS_DONE to "读完")
    }
    LazyRow(
        contentPadding = PaddingValues(horizontal = Space.gutter, vertical = Space.xs),
        horizontalArrangement = Arrangement.spacedBy(Space.xs),
    ) {
        items(options.size) { i ->
            val (key, label) = options[i]
            SelectChip(label = label, selected = selected == key, onClick = { onSelect(key) })
        }
    }
}

@Composable
private fun ShelfToolbar(
    mediaType: String,
    sort: String,
    group: String,
    groups: List<String>,
    builtinGroups: List<ShelfGrouping.Group>,
    onSelectMediaType: (String) -> Unit,
    onSort: (String) -> Unit,
    onGroupFilter: (String) -> Unit,
    listView: Boolean,
    onToggleView: () -> Unit,
    // 短剧 tab 下面是「追剧记录」，既不排序也不分卡片/列表视图。
    // 原来这两个按钮照样显示但点了没反应，用户会以为排序坏了。
    sortable: Boolean = true,
) {
    val mediaTabs = listOf("全部" to "all", "小说" to "novel", "漫画" to "manga", "短剧" to "drama")
    val sortOptions = listOf("最近" to "recent", "入架" to "added", "书名" to "title")
    val menuOpen = remember { mutableStateOf(false) }
    val activeGroup = if (sort == "group" && group.isNotBlank()) ShelfGrouping.labelOf(group) else ""
    val sortLabel = when (sort) {
        "added" -> "入架"
        "title" -> "书名"
        "group" -> activeGroup.ifBlank { "分组" }
        else -> "最近"
    }
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = Space.gutter, end = Space.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LazyRow(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(Space.sm),
        ) {
            items(mediaTabs) { (label, key) ->
                SelectChip(
                    label = label,
                    selected = mediaType == key,
                    onClick = { onSelectMediaType(key) },
                )
            }
        }
        if (sortable) {
            IconButton(onClick = onToggleView, modifier = Modifier.size(34.dp)) {
                Icon(
                    imageVector = if (listView) Icons.Filled.GridView else Icons.Filled.ViewList,
                    contentDescription = if (listView) "切换卡片视图" else "切换列表视图",
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Box {
                TextButton(
                    onClick = { menuOpen.value = true },
                    contentPadding = PaddingValues(horizontal = Space.sm),
                ) {
                    Text(
                        text = sortLabel,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Icon(
                        imageVector = Icons.Filled.ArrowDropDown,
                        contentDescription = "排序",
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                DropdownMenu(
                    expanded = menuOpen.value,
                    onDismissRequest = { menuOpen.value = false },
                ) {
                    sortOptions.forEach { (label, key) ->
                        DropdownMenuItem(
                            text = { Text(label, fontSize = 13.sp) },
                            trailingIcon = if (sort == key) {
                                { Icon(Icons.Filled.Check, null, Modifier.size(16.dp)) }
                            } else null,
                            onClick = {
                                onSort(key)
                                menuOpen.value = false
                            },
                        )
                    }
                    if (groups.isNotEmpty() || builtinGroups.isNotEmpty()) {
                        DividerLine()
                        DropdownMenuItem(
                            text = { Text("全部分组", fontSize = 13.sp) },
                            trailingIcon = if (sort == "group" && group.isBlank()) {
                                { Icon(Icons.Filled.Check, null, Modifier.size(16.dp)) }
                            } else null,
                            onClick = {
                                onGroupFilter("")
                                onSort("group")
                                menuOpen.value = false
                            },
                        )
                        groups.forEach { g ->
                            DropdownMenuItem(
                                text = { Text(g, fontSize = 13.sp) },
                                trailingIcon = if (sort == "group" && group == g) {
                                    { Icon(Icons.Filled.Check, null, Modifier.size(16.dp)) }
                                } else null,
                                onClick = {
                                    onGroupFilter(g)
                                    onSort("group")
                                    menuOpen.value = false
                                },
                            )
                        }
                    }
                    // 自动分组排在手工分组之后：它们是现算的，改不了也删不掉，
                    // 放在上面会让人以为能编辑。
                    if (builtinGroups.isNotEmpty()) {
                        DividerLine()
                        Text(
                            text = "按连载状态",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 16.dp, top = Space.sm, bottom = Space.xs),
                        )
                        builtinGroups.forEach { g ->
                            DropdownMenuItem(
                                text = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(g.label, fontSize = 13.sp)
                                        Spacer(Modifier.width(Space.sm))
                                        Text(
                                            text = "${g.count}",
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                },
                                trailingIcon = if (sort == "group" && group == g.key) {
                                    { Icon(Icons.Filled.Check, null, Modifier.size(16.dp)) }
                                } else null,
                                onClick = {
                                    onGroupFilter(g.key)
                                    onSort("group")
                                    menuOpen.value = false
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

/* --------------------------- 列表行 --------------------------- */

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun ShelfBookRow(
    book: Book,
    status: String?,
    statusEmphasized: Boolean,
    subStatus: String?,
    meta: String?,
    readInfo: String?,
    progress: Float? = null,
    onContinue: (() -> Unit)? = null,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
) {
    val info = listOfNotNull(
        book.author.takeIf { it.isNotBlank() },
        meta?.takeIf { it.isNotBlank() },
        status?.takeIf { it.isNotBlank() },
    ).joinToString(" · ")
    Column(
        Modifier
            .fillMaxWidth()
            .then(
                if (onLongClick == null) Modifier.clickable(onClick = onClick)
                else Modifier.combinedClickable(onClick = onClick, onLongClick = onLongClick)
            )
            .padding(horizontal = Space.gutter, vertical = Space.md),
    ) {
        Text(
            text = book.title,
            fontSize = 15.sp,
            lineHeight = 20.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (info.isNotEmpty()) {
            Text(
                text = info,
                fontSize = 12.sp,
                lineHeight = 17.sp,
                fontWeight = if (statusEmphasized) FontWeight.SemiBold else FontWeight.Normal,
                color = if (statusEmphasized) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        if (!readInfo.isNullOrBlank() || !subStatus.isNullOrBlank()) {
            Row(
                Modifier.fillMaxWidth().padding(top = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = readInfo.orEmpty(),
                    fontSize = 12.sp,
                    lineHeight = 17.sp,
                    fontWeight = if (statusEmphasized) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (statusEmphasized) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (!subStatus.isNullOrBlank()) {
                    Text(
                        text = subStatus,
                        fontSize = 11.sp,
                        color = ink3,
                        maxLines = 1,
                    )
                }
                if (onContinue != null) {
                    Text(
                        text = "继续",
                        modifier = Modifier
                            .clickable(onClick = onContinue)
                            .padding(start = Space.sm, top = Space.xxs, bottom = Space.xxs),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
        if (progress != null) {
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = Space.xs)
                    .height(2.dp)
                    .clip(RoundedCornerShape(1.dp)),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.outlineVariant,
            )
        }
    }
}

/* --------------------------- 空态 --------------------------- */

@Composable
private fun ShelfEmpty(
    onDiscover: () -> Unit,
    title: String = "书架还是空的",
    description: String = "去「发现」浏览热门榜单，把想看的书加进来。",
    primaryLabel: String = "去发现",
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Space.gutter, vertical = Space.sm),
        shape = RoundedCornerShape(Radius.none),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(vertical = Space.xl, horizontal = Space.lg),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                Icons.Filled.LocalLibrary,
                contentDescription = null,
                modifier = Modifier.size(40.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = title,
                fontSize = 15.sp,
                lineHeight = 22.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(top = Space.sm),
            )
            Text(
                text = description,
                fontSize = 12.sp,
                lineHeight = 19.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = Space.xs),
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(Space.sm),
                modifier = Modifier.padding(top = Space.xs),
            ) {
                TextButton(onClick = onDiscover) {
                    Text(primaryLabel, color = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
}

/* --------------------------- 书架卡片状态 --------------------------- */

/** 「完结/连载 · 共 N 章」，未知的部分直接省略。 */
private fun shelfSeriesMeta(book: Book): String? {
    val state = when (book.status) {
        BookStatus.Finished -> "完结"
        BookStatus.Serial -> "连载"
        BookStatus.Unknown -> ""
    }
    val total = if (book.chapterCount > 0) "共 ${book.chapterCount} 章" else ""
    return listOf(state, total).filter { it.isNotBlank() }.joinToString(" · ").ifBlank { null }
}

/** 阅读进度 0..1；章节总数未知时用最近一次查更新拿到的章数。 */
private fun shelfProgress(item: ShelfItem): Float? {
    val index = item.readChapterIndex
    val total = if (item.book.chapterCount > 0) item.book.chapterCount else item.knownChapters
    if (index < 0 || total <= 0) return null
    return ((index + 1).toFloat() / total).coerceIn(0f, 1f)
}

/** 「已读至第 K 章 · M 章未读」/「未阅读」/「已读完」。 */
private fun shelfReadInfo(item: ShelfItem): String? {
    val total = if (item.book.chapterCount > 0) item.book.chapterCount else item.knownChapters
    val index = item.readChapterIndex
    if (index < 0) {
        return if (item.chapterId.isNotBlank() || item.lastReadAt > 0) "在阅读" else "未阅读"
    }
    val readTo = "已读至第 ${index + 1} 章"
    if (total > 0 && index >= total - 1) return "已读完"
    val left = if (total > 0) (total - index - 1).coerceAtLeast(0) else 0
    return if (left > 0) "$readTo · $left 章未读" else readTo
}

private fun shelfStatus(item: ShelfItem, newChapters: Int?): String? = when {
    newChapters != null && newChapters > 0 -> "↑ 新至 $newChapters 章"
    item.group.isNotBlank() -> item.group
    else -> null
}

private fun shelfSubStatus(item: ShelfItem): String? = when {
    item.chapterId.isNotBlank() && item.chapterOffset > 0 -> "本章第 ${item.chapterOffset + 1} 段"
    item.chapterId.isNotBlank() -> "刚打开"
    else -> null
}

/* --------------------------- 相对时间 --------------------------- */

private fun relativeTime(at: Long): String {
    if (at <= 0) return ""
    val diff = System.currentTimeMillis() - at
    return when {
        diff < 60_000L -> "刚刚"
        diff < 3_600_000L -> "${diff / 60_000L} 分钟前"
        diff < 86_400_000L -> "${diff / 3_600_000L} 小时前"
        diff < 7 * 86_400_000L -> "${diff / 86_400_000L} 天前"
        else -> SimpleDateFormat("MM-dd", Locale.US).format(Date(at))
    }
}

@Composable
private fun RecentDramaCard(
    drama: DramaSeries,
    onClick: () -> Unit,
    onToggleFavorite: (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = Space.gutter, vertical = Space.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .width(56.dp)
                .aspectRatio(0.72f)
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
                        .size(24.dp)
                        .align(Alignment.Center),
                    tint = MaterialTheme.colorScheme.outline,
                )
            }
        }
        Spacer(Modifier.width(Space.md))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = drama.title,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (drama.isMotionComic) {
                    Spacer(Modifier.width(6.dp))
                    Surface(
                        color = MaterialTheme.colorScheme.tertiary,
                        shape = RoundedCornerShape(2.dp),
                    ) {
                        Text(
                            text = "漫剧",
                            fontSize = 10.sp,
                            color = Color.White,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                        )
                    }
                }
            }
            Text(
                text = if (drama.episodeCount > 0) "全 ${drama.episodeCount} 集" else "连载中",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp),
            )
            if (drama.intro.isNotBlank()) {
                Text(
                    text = drama.intro,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.outline,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
        Spacer(Modifier.width(Space.sm))
        if (onToggleFavorite != null) {
            IconButton(onClick = onToggleFavorite, modifier = Modifier.size(36.dp)) {
                Icon(
                    Icons.Filled.Star,
                    contentDescription = "取消收藏",
                    tint = Color(0xFFFFC107),
                    modifier = Modifier.size(20.dp),
                )
            }
        }
        OutlinedButton(
            onClick = onClick,
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
            shape = RoundedCornerShape(Radius.none),
        ) {
            Text("播放", fontSize = 12.sp)
        }
    }
}
