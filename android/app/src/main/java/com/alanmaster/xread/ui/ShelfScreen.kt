package com.alanmaster.xread.ui

import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.LocalLibrary
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alanmaster.xread.data.Book
import com.alanmaster.xread.data.BookStatus
import com.alanmaster.xread.data.ShelfGrouping
import com.alanmaster.xread.data.ShelfItem

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
    /** 现算的自动分组（待读 / 连载中 / 已完结），只列有书的那些。 */
    builtinGroups: List<ShelfGrouping.Group> = emptyList(),
    shelfMediaType: String = "all",
    onSelectMediaType: (String) -> Unit = {},
    updates: Map<String, Int>,
    /** 整轮刷新是否在跑（重读书架 + 查新章）；页头图标转圈并防连点。 */
    refreshing: Boolean,
    onSort: (String) -> Unit,
    onGroupFilter: (String) -> Unit,
    /**
     * 刷新书架：重读条目再查新章。页头图标与下拉手势共用这一个入口——
     * 只重读本机 shelf.json 没有意义，用户真正想知道的是「有没有新章」。
     */
    onRefresh: () -> Unit,
    onLongPressBook: (Book) -> Unit,
    /**
     * 书架数据落库计数：每完成一次刷新 +1。
     *
     * 不用布尔标记：刷新快到同一帧完成时布尔翻转会被合帧吃掉，指示器就再也收不回来。
     */
    shelfRefreshDone: Int = 0,
    onSearch: () -> Unit,
    onDiscover: () -> Unit,
    /** 追剧空态的「去发现短剧」；未接线时退回 [onDiscover]。 */
    onOpen: (Book) -> Unit,
    /** 长按菜单里的「书籍详情」：书架点按是继续读，详情要显式选。 */
    onDetail: (Book) -> Unit = {},
    onContinue: (Book) -> Unit = {},
    /** 短剧收藏与追剧记录：都落在 OfflineRepo，进程重启后仍在。 */
    /** 批量操作：多选模式下一次性处理选中的书。 */
    onBatchGroup: (List<Book>) -> Unit = {},
    onBatchRemove: (List<Book>) -> Unit = {},
    onBatchCache: (List<Book>) -> Unit = {},
    onBatchFinish: (List<Book>) -> Unit = {},
    /** 改个人标记（想读 / 在读 / 读完）。长按面板里的三态芯片走这条。 */
    onBatchStatus: (List<Book>, String) -> Unit = { _, _ -> },
    shelfStatus: String = ShelfItem.STATUS_READING,
    onStatusFilter: (String) -> Unit = {},
) {
    val context = LocalContext.current
    val viewPrefs = remember {
        context.getSharedPreferences("shelf_view", Context.MODE_PRIVATE)
    }
    var listView by rememberSaveable { mutableStateOf(viewPrefs.getBoolean("list", false)) }
    // 大屏（平板 / 折叠屏展开）上单列卡片只有一行字，横向空间全浪费了。
    // 列表视图保持单列——它的信息密度本来就是按整行设计的。
    val columns = shelfColumns(
        listView = listView,
        widthDp = LocalConfiguration.current.screenWidthDp,
    )

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
                    count = shelf.size,
                    refreshing = refreshing,
                    onSearch = onSearch,
                    onRefresh = onRefresh,
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
                onRefresh()
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
                        sort = shelfSort,
                        group = shelfGroup,
                        groups = shelfGroups,
                        builtinGroups = builtinGroups,
                        onSort = onSort,
                        onGroupFilter = onGroupFilter,
                        listView = listView,
                        onToggleView = {
                            listView = !listView
                            viewPrefs.edit().putBoolean("list", listView).apply()
                        },
                        sortable = true,
                    )
                }

                /* -------------------- 个人标记 -------------------- */
                // 与媒体类型分两行：一行管「这是什么书」，一行管「我读到哪一步」，
                // 混在一行里 chip 会多到需要横向滚两次才看得全。
                if (!selecting) {
                    item(key = "status-filter") {
                        StatusFilterRow(selected = shelfStatus, onSelect = onStatusFilter)
                    }
                }

                    /* -------------------- 我的书架 -------------------- */
                    // 「在架 N 本 · 查更新」这一行删掉了：分类已经在上面那排 chip 里，
                    // 本数挪进了页头标题，刷新（含查新章）挪进了页头图标，书架直接接在工具栏下面。
                    if (shelf.isNotEmpty()) {
                        if (columns == 2) {
                            // 宽屏两列：卡片宽度交给 weight，间距交给 Row 的 spacedBy，
                            // 每行左右只留页面留白，中间不再各加一份 gutter。
                            items(shelf.chunked(columns), key = { "shelf-row-${it.first().book.bookId}" }) { row ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = Space.gutter),
                                    horizontalArrangement = Arrangement.spacedBy(Space.sm),
                                ) {
                                    row.forEach { item ->
                                        ShelfBookCell(
                                            item = item,
                                            listView = false,
                                            modifier = Modifier.weight(1f),
                                            newChapters = updates[item.book.bookId],
                                            selecting = selecting,
                                            checked = item.book.bookId in selectedIds,
                                            cardPadding = PaddingValues(vertical = Space.xs),
                                            markPadding = PaddingValues(end = Space.sm, top = Space.md),
                                            onOpen = onOpen,
                                            onContinue = onContinue,
                                            onToggle = { book -> haptic.light(); toggle(book) },
                                            onLongPress = { book -> haptic.medium(); menuFor = book.bookId },
                                        )
                                    }
                                    // 单数本时补一个空位，最后一张卡不会被拉成整行宽。
                                    if (row.size == 1) Spacer(Modifier.weight(1f))
                                }
                            }
                        } else {
                            items(shelf, key = { "shelf-${it.book.bookId}" }) { item ->
                                ShelfBookCell(
                                    item = item,
                                    listView = listView,
                                    newChapters = updates[item.book.bookId],
                                    selecting = selecting,
                                    checked = item.book.bookId in selectedIds,
                                    cardPadding = PaddingValues(
                                        horizontal = Space.gutter, vertical = Space.xs,
                                    ),
                                    markPadding = PaddingValues(
                                        end = Space.gutter + Space.sm, top = Space.md,
                                    ),
                                    onOpen = onOpen,
                                    onContinue = onContinue,
                                    onToggle = { book -> haptic.light(); toggle(book) },
                                    onLongPress = { book -> haptic.medium(); menuFor = book.bookId },
                                )
                            }
                        }
                    } else if (!loading) {
                        // 书架默认停在「在读」，架上有书但一本都不符合筛选时，
                        // 说「书架还是空的」会让人以为书没了。这里一键清掉三层筛选。
                        val filtered = shelfStatus != "all" || shelfGroup.isNotBlank() ||
                            shelfMediaType != "all"
                        if (filtered) {
                            item {
                                ShelfEmpty(
                                    onDiscover = {
                                        onStatusFilter("all")
                                        onGroupFilter("")
                                        onSelectMediaType("all")
                                    },
                                    title = "这一栏还没有书",
                                    description = "当前筛选条件下没有书，换成「全部」就能看到整架。",
                                    primaryLabel = "看全部",
                                )
                            }
                        } else {
                            item { ShelfEmpty(onDiscover = onDiscover) }
                        }
                    } else {
                        item { Loading() }
                    }
            }
        }
            // 长按菜单挂在页面根部而不是列表项里：列表项会随滚动回收，
            // 面板一旦跟着 item 走，滑一下就会自己消失。
            val actionItem = shelf.firstOrNull { it.book.bookId == menuFor }
            if (actionItem != null) {
                ModalBottomSheet(
                    onDismissRequest = { menuFor = null },
                    sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
                    containerColor = MaterialTheme.colorScheme.surface,
                ) {
                    ShelfBookActionsSheet(
                        item = actionItem,
                        onDetail = { menuFor = null; onDetail(actionItem.book) },
                        onFinish = { menuFor = null; onBatchFinish(listOf(actionItem.book)) },
                        onSetStatus = { status ->
                            menuFor = null
                            onBatchStatus(listOf(actionItem.book), status)
                        },
                        onGroup = { menuFor = null; onLongPressBook(actionItem.book) },
                        onMultiSelect = {
                            menuFor = null
                            selectedIds = setOf(actionItem.book.bookId)
                            selecting = true
                        },
                        onRemove = { menuFor = null; onBatchRemove(listOf(actionItem.book)) },
                    )
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

/* --------------------------- 长按操作面板 --------------------------- */

/**
 * 长按一本书弹出的操作面板。
 *
 * 顶部是这本书本身（封面 + 书名 + 一行元信息），下面才是动作——先确认「我对谁操作」，
 * 再选做什么，比一列没有上下文的文字安全。动作按「常用 → 管理 → 危险」分组，
 * 每条都有图标、主文案和一句副文案：只写「设置分组」四个字没人知道分组是干什么用的。
 */
@Composable
private fun ShelfBookActionsSheet(
    item: ShelfItem,
    onDetail: () -> Unit,
    onFinish: () -> Unit,
    onSetStatus: (String) -> Unit,
    onGroup: () -> Unit,
    onMultiSelect: () -> Unit,
    onRemove: () -> Unit,
) {
    val book = item.book
    val meta = listOfNotNull(
        book.author.takeIf { it.isNotBlank() },
        shelfSeriesMeta(book),
        shelfReadInfo(item).takeIf { it != "未阅读" },
    ).joinToString(" · ")

    Column(
        Modifier
            .fillMaxWidth()
            .padding(bottom = Space.xl),
    ) {
        // 头部：这本书。封面用统一的 BookCover，没有封面时退回书名首字。
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = Space.gutter, vertical = Space.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BookCover(
                title = book.title,
                coverUrl = book.coverUrl,
                modifier = Modifier
                    .width(44.dp)
                    .aspectRatio(0.72f),
            )
            Column(Modifier.padding(start = Space.md).weight(1f)) {
                Text(
                    text = book.title,
                    fontSize = 15.sp,
                    lineHeight = 20.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (meta.isNotBlank()) {
                    Text(
                        text = meta,
                        fontSize = 12.sp,
                        lineHeight = 17.sp,
                        color = ink3,
                        modifier = Modifier.padding(top = 2.dp),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        DividerLine()

        // 个人标记：想读 / 在读 / 读完。三态直接摆在面板上，比藏进二级菜单少一次点击，
        // 也是「手动移入待读」唯一的入口——详情页里同样的三个芯片，学一次就够。
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = Space.gutter, vertical = Space.md),
            verticalArrangement = Arrangement.spacedBy(Space.xs),
        ) {
            Text(
                text = "我的标记",
                fontSize = 11.sp,
                lineHeight = 16.sp,
                color = ink3,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(Space.xs)) {
                ShelfItem.STATUS_LABELS.forEach { (key, label) ->
                    SelectChip(
                        label = label,
                        selected = item.status == key,
                        onClick = { onSetStatus(key) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
        DividerLine()
        ShelfActionRow(
            icon = Icons.Filled.Info,
            label = "书籍详情",
            hint = "简介、章节列表与我的标记",
            onClick = onDetail,
        )
        ShelfActionRow(
            icon = Icons.Filled.Check,
            label = "标记读完",
            hint = "把阅读进度推到最后一章",
            onClick = onFinish,
        )
        DividerLine()

        // 管理动作：分组与多选属于整理书架，不是阅读本身，所以单独成组。
        ShelfActionRow(
            icon = Icons.Filled.Category,
            label = "设置分组",
            hint = "把这本书收进自定义分组",
            onClick = onGroup,
        )
        ShelfActionRow(
            icon = Icons.Filled.Checklist,
            label = "多选",
            hint = "勾选多本后批量处理",
            onClick = onMultiSelect,
        )
        DividerLine()

        // 危险动作：移出书架会给一次撤销窗口，但仍要和上面的动作拉开距离。
        ShelfActionRow(
            icon = Icons.Filled.DeleteOutline,
            label = "移出书架",
            hint = "可从底部提示里撤销",
            danger = true,
            onClick = onRemove,
        )
    }
}

/**
 * 面板里的一行动作：左图标、右主副文案。
 *
 * 点击范围铺满整行（48dp 高），不仅仅是有字的地方——这个面板单手拇指操作，
 * 行内留白也算命中区。
 */
@Composable
private fun ShelfActionRow(
    icon: ImageVector,
    label: String,
    hint: String,
    danger: Boolean = false,
    onClick: () -> Unit,
) {
    val tint = if (danger) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = Space.gutter, vertical = Space.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (danger) tint else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp),
        )
        Column(Modifier.padding(start = Space.md)) {
            Text(text = label, fontSize = 14.sp, color = tint)
            Text(
                text = hint,
                fontSize = 11.sp,
                lineHeight = 16.sp,
                color = if (danger) MaterialTheme.colorScheme.error.copy(alpha = 0.75f) else ink3,
                modifier = Modifier.padding(top = 1.dp),
            )
        }
    }
}

/* --------------------------- 多选 --------------------------- */

/**
 * 多选顶栏：替代页头，只保留「已选 N 本 / 全选 / 取消」。
 *
 * 多选期间不显示搜索、刷新——这些都会改变列表内容，
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

/* --------------------------- 页头 --------------------------- */

@Composable
private fun ShelfHeader(
    count: Int,
    refreshing: Boolean,
    onSearch: () -> Unit,
    onRefresh: () -> Unit,
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
            // 刷新 = 重读书架 + 查新章，与下拉手势同一件事，所以页头只留一个图标。
            // 跑的时候转圈并禁用，避免连点叠出多轮请求。
            IconButton(
                onClick = onRefresh,
                enabled = !refreshing,
                modifier = Modifier.size(38.dp),
            ) {
                if (refreshing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                    )
                } else {
                    Icon(Icons.Filled.Refresh, "刷新书架", Modifier.size(22.dp))
                }
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
    // 上方类型筛选和这里的状态筛选都带「全部」，两排红底 chip 会撞成上下两块红砖。
    // 状态改等宽文字 tab：选中只留朱红字 + 底部短线；整格 48dp 可点，选中态走 selectable 语义。
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = Space.gutter)
            .height(48.dp)
            .selectableGroup(),
    ) {
        options.forEach { (key, label) ->
            val active = selected == key
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .selectable(selected = active, role = Role.Tab, onClick = { onSelect(key) }),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = label,
                    fontSize = 13.sp,
                    fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (active) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
                Box(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .width(20.dp)
                        .height(2.dp)
                        .background(if (active) MaterialTheme.colorScheme.primary else Color.Transparent),
                )
            }
        }
    }
}

@Composable
private fun ShelfToolbar(
    sort: String,
    group: String,
    groups: List<String>,
    builtinGroups: List<ShelfGrouping.Group>,
    onSort: (String) -> Unit,
    onGroupFilter: (String) -> Unit,
    listView: Boolean,
    onToggleView: () -> Unit,
    // 短剧 tab 下面是「追剧记录」，既不排序也不分卡片/列表视图。
    // 原来这两个按钮照样显示但点了没反应，用户会以为排序坏了。
    sortable: Boolean = true,
) {
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
        // 纯小说阅读器无需再在书架左上角显示「全部/小说」类型下拉，右侧视图切换与排序直接靠右对齐。
        Spacer(Modifier.weight(1f))
        if (sortable) {
            IconButton(onClick = onToggleView, modifier = Modifier.size(34.dp)) {
                Icon(
                    imageVector = if (listView) Icons.Filled.GridView else Icons.AutoMirrored.Filled.ViewList,
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
                            text = "自动分组",
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

/* --------------------------- 列数 --------------------------- */

/**
 * 书架卡片视图的列数。
 *
 * 阅读页用 720dp 作为「这是块平板」的判断，书架沿用同一个阈值，
 * 免得同一个设备上阅读页两栏、书架却还是单列。列表视图永远是单列：
 * 它的信息密度按整行设计，劈成两半每行只剩几个字。
 */
internal fun shelfColumns(listView: Boolean, widthDp: Int): Int =
    if (!listView && widthDp >= 720) 2 else 1

/* --------------------------- 单本书 --------------------------- */

/**
 * 一本书的渲染单元：列表视图是纯文字行，卡片视图是带封面的横排卡。
 *
 * 抽成独立 composable 是为了让单列与宽屏两列共用同一套点击/多选/长按语义——
 * 早先两处各写一遍，多选时「点按＝勾选」这条规则在某一处漏掉就会变成打开书。
 */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun ShelfBookCell(
    item: ShelfItem,
    listView: Boolean,
    newChapters: Int?,
    selecting: Boolean,
    checked: Boolean,
    cardPadding: PaddingValues,
    markPadding: PaddingValues,
    modifier: Modifier = Modifier,
    onOpen: (Book) -> Unit,
    onContinue: (Book) -> Unit,
    onToggle: (Book) -> Unit,
    onLongPress: (Book) -> Unit,
) {
    val book = item.book
    val status = shelfStatus(item, newChapters)
    val progress = shelfProgress(item)
    val continueReading: (() -> Unit)? =
        if (progress == null) null else ({ onContinue(book) })
    // 多选时点按改成勾选；退出的唯一方式是顶栏「取消」或执行完动作，
    // 免得读到一半误触把选择清空。
    val onClick: () -> Unit = { if (selecting) onToggle(book) else onOpen(book) }
    val onLongClick: () -> Unit =
        if (selecting) ({ onToggle(book) }) else ({ onLongPress(book) })

    Box(modifier) {
        if (listView) {
            ShelfBookRow(
                book = book,
                status = status,
                statusEmphasized = newChapters != null,
                subStatus = shelfSubStatus(item),
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
                modifier = Modifier.padding(cardPadding),
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
                    .padding(markPadding),
            )
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
