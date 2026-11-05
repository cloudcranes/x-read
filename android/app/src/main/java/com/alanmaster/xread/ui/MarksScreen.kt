package com.alanmaster.xread.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Highlight
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alanmaster.xread.data.Mark
import com.alanmaster.xread.data.MarkEntry
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val FILTER_ALL = "all"
private const val FILTER_BOOKMARK = "bookmark"
private const val FILTER_HIGHLIGHT = "highlight"
private const val FILTER_NOTED = "noted"

/**
 * 书摘页：跨书列出全部书签与高亮，可跳回原文、写批注、删除、导出 Markdown。
 *
 * 点击整条仍是「跳回原文」——这是这个页面最高频的动作，不该为了写笔记把它降级。
 * 写批注走行内的「笔记」按钮，弹底部面板，300ms 防抖实时落盘：
 * 面板里再放一个「跳到原文」，等于两条路都留着。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MarksScreen(
    marks: List<MarkEntry>,
    bookTitles: Map<String, String>,
    onBack: () -> Unit,
    onOpen: (MarkEntry) -> Unit,
    onDelete: (MarkEntry) -> Unit,
    onShare: (MarkEntry) -> Unit,
    onExport: () -> Unit,
    /**
     * 空态「去阅读」的落点。为 null 时退回 [onBack]（回到工具页，那里列着已下载的书）。
     *
     * 做成可选参数而不是必填，是为了不改动 `MainActivity.kt` 的调用。
     */
    onGoRead: (() -> Unit)? = null,
    onSaveNote: (MarkEntry, String, List<String>) -> Unit = { _, _, _ -> },
) {
    var filter by rememberSaveable { mutableStateOf(FILTER_ALL) }
    var bookFilter by rememberSaveable { mutableStateOf("") }
    var editing by remember { mutableStateOf<MarkEntry?>(null) }

    val filterOptions = listOf(
        "全部" to FILTER_ALL,
        "书签" to FILTER_BOOKMARK,
        "高亮" to FILTER_HIGHLIGHT,
        "有笔记" to FILTER_NOTED,
    )
    val books = remember(marks, bookTitles) {
        marks.map { it.bookId to (bookTitles[it.bookId] ?: it.bookId) }.distinct()
            .sortedBy { it.second }
    }
    val filtered = remember(marks, filter, bookFilter) {
        marks.filter { e ->
            (bookFilter.isEmpty() || e.bookId == bookFilter) && when (filter) {
                FILTER_BOOKMARK -> e.mark.kind == Mark.KIND_BOOKMARK
                FILTER_HIGHLIGHT -> e.mark.kind == Mark.KIND_HIGHLIGHT
                FILTER_NOTED -> e.mark.hasNote
                else -> true
            }
        }
    }

    Scaffold(
        topBar = {
            XReadTopBar(
                title = "书摘",
                subtitle = when {
                    marks.isEmpty() -> "还没有书签或高亮"
                    filtered.size == marks.size -> "共 ${marks.size} 条"
                    else -> "筛选出 ${filtered.size} / ${marks.size} 条"
                },
                onBack = onBack,
                actions = {
                    if (marks.isNotEmpty()) {
                        TextButton(onClick = onExport) {
                            Text("导出", fontSize = 13.sp, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                },
            )
        },
    ) { padding ->
        if (marks.isEmpty()) {
            EmptyHint(
                text = "正文里长按段落可高亮或加书签；\n阅读页顶栏的书签按钮可给当前段落加书签。",
                actionLabel = "去阅读",
                onAction = onGoRead ?: onBack,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
            )
            return@Scaffold
        }
        Column(Modifier.fillMaxSize().padding(padding)) {
            FilterRow(
                options = filterOptions,
                selected = filter,
                onSelect = { filter = it },
                books = books,
                bookFilter = bookFilter,
                onBookFilter = { bookFilter = it },
            )
            if (filtered.isEmpty()) {
                EmptyHint(
                    text = if (filter == FILTER_NOTED) "还没有写过批注。\n打开任意一条书摘，点行内的「笔记」就能写。"
                    else "当前筛选下没有条目。",
                    actionLabel = "看全部",
                    onAction = { filter = FILTER_ALL; bookFilter = "" },
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                LazyColumn(
                    Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = Space.gutter, end = Space.gutter,
                        top = Space.sm, bottom = Space.xxl,
                    ),
                    verticalArrangement = Arrangement.spacedBy(Space.sm),
                ) {
                    items(
                        filtered,
                        key = { "${it.bookId}-${it.chapterId}-${it.mark.paragraph}-${it.mark.kind}" },
                    ) { e ->
                        MarkRow(
                            entry = e,
                            bookTitle = bookTitles[e.bookId] ?: e.bookId,
                            onOpen = { onOpen(e) },
                            onEditNote = { editing = e },
                            onShare = { onShare(e) },
                            onDelete = { onDelete(e) },
                        )
                    }
                }
            }
        }
    }

    editing?.let { entry ->
        NoteSheet(
            entry = entry,
            bookTitle = bookTitles[entry.bookId] ?: entry.bookId,
            knownTags = remember(marks) {
                marks.flatMap { it.mark.tags }.distinct().take(12)
            },
            onSave = { note, tags -> onSaveNote(entry, note, tags) },
            onOpenText = { editing = null; onOpen(entry) },
            onDismiss = { editing = null },
        )
    }
}

/** 筛选条：类型用芯片，书目用下拉——书目可能几十本，芯片铺不下。 */
@Composable
private fun FilterRow(
    options: List<Pair<String, String>>,
    selected: String,
    onSelect: (String) -> Unit,
    books: List<Pair<String, String>>,
    bookFilter: String,
    onBookFilter: (String) -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = Space.gutter, vertical = Space.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LazyRow(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(Space.xs)) {
            items(options) { (label, value) ->
                SelectChip(label = label, selected = selected == value, onClick = { onSelect(value) })
            }
        }
        if (books.size > 1) {
            Box {
                TextButton(onClick = { menuOpen = true }) {
                    val label = books.firstOrNull { it.first == bookFilter }?.second ?: "全部书目"
                    Text(
                        text = if (label.length > 8) label.take(8) + "…" else label,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text("全部书目", fontSize = 13.sp) },
                        onClick = { menuOpen = false; onBookFilter("") },
                    )
                    books.forEach { (id, title) ->
                        DropdownMenuItem(
                            text = { Text(title, fontSize = 13.sp) },
                            onClick = { menuOpen = false; onBookFilter(id) },
                        )
                    }
                }
            }
        }
    }
}

/**
 * 一条书摘。
 *
 * 类型用图标 + 文字双通道标出（不只靠颜色）：书签是 Bookmark 图标，
 * 高亮是 Highlight 图标，右侧跟一个小标签。
 */
@Composable
private fun MarkRow(
    entry: MarkEntry,
    bookTitle: String,
    onOpen: () -> Unit,
    onEditNote: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit,
) {
    val isBookmark = entry.mark.kind == Mark.KIND_BOOKMARK
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen),
        shape = RoundedCornerShape(Radius.none),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(Modifier.padding(Space.card)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = if (isBookmark) Icons.Filled.Bookmark else Icons.Filled.Highlight,
                    contentDescription = null,
                    tint = if (isBookmark) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.size(16.dp),
                )
                Text(
                    text = if (isBookmark) "书签" else "高亮",
                    fontSize = 11.sp,
                    lineHeight = 15.sp,
                    fontWeight = FontWeight.Medium,
                    color = if (isBookmark) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.padding(start = Space.xs),
                )
                Text(
                    text = entry.chapterTitle.ifEmpty { "第 ${entry.mark.paragraph + 1} 段" },
                    fontSize = 11.sp,
                    lineHeight = 15.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = Space.sm),
                )
            }
            Text(
                text = entry.mark.text.ifBlank { "（第 ${entry.mark.paragraph + 1} 段）" },
                fontSize = 14.sp,
                lineHeight = 21.sp,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = Space.sm),
            )
            if (entry.mark.note.isNotBlank()) {
                Text(
                    text = entry.mark.note.trim(),
                    fontSize = 13.sp,
                    lineHeight = 19.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = Space.sm)
                        .background(
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                            RoundedCornerShape(Radius.none),
                        )
                        .padding(horizontal = Space.sm, vertical = Space.xs),
                )
            }
            if (entry.mark.tags.isNotEmpty()) {
                Text(
                    text = entry.mark.tags.joinToString(" ") { "#$it" },
                    fontSize = 11.sp,
                    lineHeight = 16.sp,
                    color = MaterialTheme.colorScheme.secondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = Space.xs),
                )
            }
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(top = Space.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "$bookTitle · ${stamp(entry.mark.at)}",
                    fontSize = 11.sp,
                    lineHeight = 16.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                MarkIconAction(
                    icon = Icons.Filled.EditNote,
                    label = if (entry.mark.hasNote) "改笔记" else "写笔记",
                    onClick = onEditNote,
                )
                MarkIconAction(
                    icon = Icons.Filled.Share,
                    label = "分享",
                    onClick = onShare,
                )
                MarkIconAction(
                    icon = Icons.Filled.DeleteOutline,
                    label = "删除",
                    onClick = onDelete,
                )
            }
        }
    }
}

/**
 * 批注面板。
 *
 * 输入即存（300ms 防抖）：批注是随手写的东西，没有「保存」这个心理动作；
 * 弹层被划掉时不该丢字。真正落盘失败由 ViewModel 报错。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NoteSheet(
    entry: MarkEntry,
    bookTitle: String,
    knownTags: List<String>,
    onSave: (String, List<String>) -> Unit,
    onOpenText: () -> Unit,
    onDismiss: () -> Unit,
) {
    var note by remember(entry) { mutableStateOf(entry.mark.note) }
    var tagText by remember(entry) {
        mutableStateOf(entry.mark.tags.joinToString(" ") { "#$it" })
    }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    LaunchedEffect(note, tagText) {
        val tags = Mark.splitTags(tagText)
        if (note == entry.mark.note && tags == entry.mark.tags) return@LaunchedEffect
        delay(300)
        onSave(note, tags)
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .heightIn(max = 560.dp)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Space.gutter)
                .padding(bottom = Space.xxl),
            verticalArrangement = Arrangement.spacedBy(Space.sm),
        ) {
            Text(
                text = "批注 · ${entry.chapterTitle.ifEmpty { "第 ${entry.mark.paragraph + 1} 段" }}",
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = "$bookTitle · ${stamp(entry.mark.at)}",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                shape = RoundedCornerShape(Radius.none),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = entry.mark.text.ifBlank { "（第 ${entry.mark.paragraph + 1} 段）" },
                    fontSize = 13.sp,
                    lineHeight = 20.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 6,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(Space.sm),
                )
            }
            OutlinedTextField(
                value = note,
                onValueChange = { if (it.length <= Mark.NOTE_LIMIT) note = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("笔记", fontSize = 12.sp) },
                placeholder = { Text("这段为什么重要？和谁有关？", fontSize = 13.sp) },
                minLines = 4,
                maxLines = 8,
                supportingText = {
                    Text("${note.length} / ${Mark.NOTE_LIMIT}", fontSize = 10.sp)
                },
            )
            OutlinedTextField(
                value = tagText,
                onValueChange = { tagText = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("标签", fontSize = 12.sp) },
                placeholder = { Text("#人物 #伏笔", fontSize = 13.sp) },
                singleLine = true,
                supportingText = {
                    Text("空格或逗号分隔，最多 ${Mark.TAG_LIMIT} 个", fontSize = 10.sp)
                },
            )
            if (knownTags.isNotEmpty()) {
                Text(
                    text = "常用标签",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                LazyRow(horizontalArrangement = Arrangement.spacedBy(Space.xs)) {
                    items(knownTags) { tag ->
                        val on = Mark.splitTags(tagText).contains(tag)
                        SelectChip(
                            label = "#$tag",
                            selected = on,
                            onClick = {
                                val current = Mark.splitTags(tagText).toMutableList()
                                if (on) current.remove(tag) else current.add(tag)
                                tagText = current.joinToString(" ") { "#$it" }
                            },
                        )
                    }
                }
            }
            Row(
                Modifier.fillMaxWidth().padding(top = Space.xs),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Space.sm),
            ) {
                TextButton(onClick = onOpenText) {
                    Text("跳到原文", fontSize = 13.sp)
                }
                Box(Modifier.weight(1f))
                Button(onClick = onDismiss) {
                    Text("完成", fontSize = 13.sp)
                }
            }
        }
    }
}

/** 行内图标动作：20dp 图标 + 40dp 视觉，触控区撑到 48dp。 */
@Composable
private fun MarkIconAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
) {
    IconButton(
        onClick = onClick,
        modifier = Modifier
            .size(48.dp)
            .padding(4.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp),
        )
    }
}

private fun stamp(at: Long): String =
    if (at <= 0) "" else SimpleDateFormat("MM-dd HH:mm", Locale.US).format(Date(at))
