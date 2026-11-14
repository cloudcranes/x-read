package com.alanmaster.xread.ui

import com.alanmaster.xread.data.BackupUiState

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Downloading
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Highlight
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SettingsBackupRestore
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alanmaster.xread.data.Book
import com.alanmaster.xread.data.DownloadPolicy
import com.alanmaster.xread.data.DownloadState
import com.alanmaster.xread.data.GoalMath
import com.alanmaster.xread.data.OfflineRepo
import com.alanmaster.xread.data.ReadingGoal
import com.alanmaster.xread.data.ReadingReminder
import com.alanmaster.xread.data.ReplaceRule
import com.alanmaster.xread.data.ZLibraryBook
import com.alanmaster.xread.data.ZLibrarySession
import org.json.JSONObject

/** 字节数转人类可读；工具页与书架提示共用。 */
internal fun formatBytes(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val kb = bytes / 1024.0
    if (kb < 1024) return String.format(java.util.Locale.US, "%.0f KB", kb)
    val mb = kb / 1024.0
    if (mb < 1024) return String.format(java.util.Locale.US, "%.1f MB", mb)
    return String.format(java.util.Locale.US, "%.2f GB", mb / 1024.0)
}

/** 「我的」页分组：总览只放入口，具体区块按组进入独立子页。 */
enum class ToolsPage { Overview, Downloads, Reading, Content, Backup, ZLibrary }

@Composable
private fun ToolsOverview(onPage: (ToolsPage) -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = Space.gutter, vertical = Space.md),
        verticalArrangement = Arrangement.spacedBy(Space.sm),
    ) {
        ToolsEntryCard(
            icon = Icons.Filled.Download,
            title = "下载与离线",
            hint = "下载状态、未完成任务、下载策略、本地已下载、存储与残留",
            onClick = { onPage(ToolsPage.Downloads) },
        )
        ToolsEntryCard(
            icon = Icons.Filled.Bookmark,
            title = "阅读管理",
            hint = "阅读目标、阅读统计、阅读回顾、每日提醒",
            onClick = { onPage(ToolsPage.Reading) },
        )
        ToolsEntryCard(
            icon = Icons.AutoMirrored.Filled.VolumeUp,
            title = "内容与朗读",
            hint = "正文替换、书摘、朗读本章",
            onClick = { onPage(ToolsPage.Content) },
        )
        ToolsEntryCard(
            icon = Icons.Filled.SettingsBackupRestore,
            title = "备份与恢复",
            hint = "导入导出、自动备份、WebDAV 同步",
            onClick = { onPage(ToolsPage.Backup) },
        )
    }
}

@Composable
private fun ToolsEntryCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    hint: String,
    onClick: () -> Unit,
) {
    Surface(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 72.dp)
            .clickable(onClick = onClick, role = Role.Button),
        shape = RoundedCornerShape(Radius.none),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = Space.card, vertical = Space.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(22.dp),
            )
            Column(
                Modifier
                    .weight(1f)
                    .padding(start = Space.md, end = Space.md),
            ) {
                Text(
                    text = title,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = hint,
                    fontSize = 12.sp,
                    lineHeight = 17.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = Space.xs),
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

/**
 * 「我的」（工具与离线）页：总览 + 4 个分组子页。
 *
 * 总览只显示 4 个入口，点进去才是各自独立子页，避免全部堆在一屏；
 * 每个区块仍是一张**白面 + 1dp 发丝线 + 0 阴影**的卡片，顺序固定：
 * 下载状态 / 未完成任务 / 下载策略 / 本地已下载 / 存储与残留 /
 * 阅读目标 / 阅读统计 / 每日提醒 / 正文替换 / 书摘 / 备份与恢复 / 朗读。
 *
 * 「未完成任务」只在有可续传任务时出现，「阅读统计」只在有阅读记录时出现；
 * 「阅读目标」始终出现——它同时是开启目标的入口，藏起来就等于没有入口；
 * 两块的序号由 [sectionIndex] 在进入 LazyColumn 前一次算好，子页沿用全局序号，
 * 滚动不会改变，也不会漏号重排。
 *
 * 旧实现是一长串裸 SectionTitle + 按钮，开关行与说明文字挤在同一层，
 * 层级全靠字号区分；现在卡片负责分区，开关行文字 13sp、说明 11–12sp，
 * 触发行高 ≥48dp。
 */
@Composable
fun ToolsScreen(
    offlineBooks: List<Book>,
    resumable: List<OfflineRepo.DownloadTask>,
    download: DownloadState,
    cacheQueueSize: Int = 0,
    stats: JSONObject,
    rules: List<ReplaceRule>,
    storageTotal: Long = 0,
    bookSizes: Map<String, Long> = emptyMap(),
    leftoverCount: Int = 0,
    leftoverBytes: Long = 0,
    onCleanLeftovers: () -> Unit = {},
    onOpenMarks: () -> Unit = {},
    onOpenReport: () -> Unit = {},
    policy: DownloadPolicy = DownloadPolicy(),
    onPolicy: (DownloadPolicy) -> Unit = {},
    goal: ReadingGoal = ReadingGoal(),
    todaySeconds: Long = 0,
    onGoal: (ReadingGoal) -> Unit = {},
    reminder: ReadingReminder = ReadingReminder(),
    onReminder: (ReadingReminder) -> Unit = {},
    notificationAllowed: Boolean = true,
    onRequestNotification: () -> Unit = {},
    newChapterNotify: Boolean = false,
    onNewChapterNotify: (Boolean) -> Unit = {},
    ttsRunning: Boolean,
    onBack: (() -> Unit)? = null,
    page: ToolsPage = ToolsPage.Overview,
    onPage: (ToolsPage) -> Unit = {},
    onSettings: () -> Unit = {},
    zlibResults: List<ZLibraryBook> = emptyList(),
    zlibLoading: Boolean = false,
    zlibMoreLoading: Boolean = false,
    zlibHasNext: Boolean = false,
    zlibError: String = "",
    zlibQuery: String = "",
    zlibSession: ZLibrarySession? = null,
    zlibLoginLoading: Boolean = false,
    zlibImporting: String = "",
    onZlibSearch: (String) -> Unit = {},
    onZlibLoadMore: () -> Unit = {},
    onZlibLogin: (String, String) -> Unit = { _, _ -> },
    onZlibLogout: () -> Unit = {},
    onZlibImport: (ZLibraryBook) -> Unit = {},
    onZlibPickLocal: () -> Unit = {},
    onOpen: (Book) -> Unit,
    onExportBook: (Book) -> Unit,
    onDelete: (Book) -> Unit,
    onCancelDownload: () -> Unit,
    onResume: (OfflineRepo.DownloadTask) -> Unit,
    onSaveRules: (List<ReplaceRule>) -> Unit,
    onExport: () -> Unit,
    onImport: () -> Unit,
    backup: BackupUiState = BackupUiState(),
    onPickBackupDir: () -> Unit = {},
    onBackupInterval: (Int) -> Unit = {},
    onBackupNow: () -> Unit = {},
    onDavSave: (String, String, String, Boolean) -> Unit = { _, _, _, _ -> },
    onDavUpload: () -> Unit = {},
    onSpeak: () -> Unit,
    onStopSpeak: () -> Unit,
) {
    // 删除本地书、清空规则、导入备份都不可撤销，先二次确认
    var confirmDelete by remember { mutableStateOf<Book?>(null) }
    var confirmClear by remember { mutableStateOf(false) }
    var confirmImport by remember { mutableStateOf(false) }
    var remindTimeOpen by remember { mutableStateOf(false) }
    var ruleText by remember(rules) {
        mutableStateOf(rules.joinToString("\n") {
            (if (it.regex) "/${it.pattern}/" else it.pattern) + " => " + it.replacement
        })
    }

    // 阅读统计整块是条件渲染的（没有任何阅读记录时整块不出现），
    // 所以「这页有几块」必须在进 LazyColumn 之前就定下来。
    val hasStats = stats.optLong("seconds") > 0 ||
            stats.optLong("words") > 0 ||
            stats.optLong("chapters") > 0 ||
            (stats.optJSONObject("days")?.length() ?: 0) > 0

    // 分区序号必须**稳定**：曾经写成 LazyColumn 作用域里的 `var sectionNo` +
    // `nextIndex()` 自增，而 item 的内容会随滚动反复重组，同一个计数器被反复
    // 自增——滚一屏再滚回去，「下载状态」的序号就从 01 变成 16。
    // 现在按固定的渲染顺序一次性算好，滚多远都不会变。
    val sectionIndex: Map<String, String> = remember(resumable.isEmpty(), hasStats) {
        val order = buildList {
            add("下载状态")
            if (resumable.isNotEmpty()) add("未完成任务")
            add("下载策略")
            add("本地已下载")
            add("存储与残留")
            add("阅读目标")
            if (hasStats) add("阅读统计")
            add("每日提醒")
            add("正文替换")
            add("书摘")
            add("备份与恢复")
            add("朗读")
        }
        order.withIndex().associate { (i, key) -> key to (i + 1).toString().padStart(2, '0') }
    }

    val pageTitle = when (page) {
        ToolsPage.Overview -> "离线与工具"
        ToolsPage.Downloads -> "下载与离线"
        ToolsPage.Reading -> "阅读管理"
        ToolsPage.Content -> "内容与朗读"
        ToolsPage.Backup -> "备份与恢复"
        ToolsPage.ZLibrary -> "Z-Library 电子书"
    }
    val pageSubtitle = when (page) {
        ToolsPage.Overview -> "本地书架、下载、备份与朗读"
        ToolsPage.Downloads -> "下载状态、策略、本地书籍与存储"
        ToolsPage.Reading -> "目标、统计、回顾与每日提醒"
        ToolsPage.Content -> "正文替换、书摘与朗读"
        ToolsPage.Backup -> "导入导出、自动备份与 WebDAV"
        ToolsPage.ZLibrary -> if (zlibSession == null) "未登录 · 只能搜索" else "已登录"
    }
    val backAction: (() -> Unit)? = if (page == ToolsPage.Overview) {
        onBack
    } else {
        { onPage(ToolsPage.Overview) }
    }
    Scaffold(
        topBar = {
            XReadTopBar(
                title = pageTitle,
                subtitle = pageSubtitle,
                onBack = backAction,
                actions = {
                    IconButton(onClick = onSettings) {
                        Icon(Icons.Filled.Settings, "设置")
                    }
                },
            )
        },
    ) { padding ->
        if (page == ToolsPage.ZLibrary) {
            Box(Modifier.fillMaxSize().padding(padding)) {
                ZLibrarySection(
                    results = zlibResults,
                    loading = zlibLoading,
                    moreLoading = zlibMoreLoading,
                    hasNext = zlibHasNext,
                    error = zlibError,
                    query = zlibQuery,
                    session = zlibSession,
                    loginLoading = zlibLoginLoading,
                    importing = zlibImporting,
                    onSearch = onZlibSearch,
                    onLoadMore = onZlibLoadMore,
                    onLogin = onZlibLogin,
                    onLogout = onZlibLogout,
                    onImport = onZlibImport,
                    onPickLocal = onZlibPickLocal,
                )
            }
        } else LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(bottom = Space.navFloat),
        ) {

            if (page == ToolsPage.Overview) {
                item { ToolsOverview(onPage) }
            }
            if (page == ToolsPage.Downloads) {

            /* -------------------- 下载状态 -------------------- */
            item {
                SectionHeader("下载状态", index = sectionIndex.getValue("下载状态"))
                ToolCard {
                    val head = if (download.message.isEmpty()) "空闲" else download.message
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = if (download.running) Icons.Filled.Downloading
                            else Icons.Filled.Download,
                            contentDescription = null,
                            tint = if (download.running) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp),
                        )
                        Text(
                            text = head,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .weight(1f)
                                .padding(start = Space.sm),
                        )
                    }
                    if (download.total > 0) {
                        Text(
                            // 拼接顺序有坑：原来的写法是
                            //   "并发 x/y" + if (失败>0) " · 失败 N" else "" + if (跳过>0) …
                            // if 是表达式，else 分支吃掉后面整串，
                            // 于是「有失败章」时「跳过 N」永远不显示。
                            text = "${download.done}/${download.total} 章 · " +
                                    "并发 ${download.concurrency}/${download.concurrencyMax}" +
                                    (if (download.failed > 0) " · 失败 ${download.failed}" else "") +
                                    (if (download.skipped > 0) " · 跳过 ${download.skipped}" else ""),
                            fontSize = 11.sp,
                            lineHeight = 17.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = Space.xs),
                        )
                        ProgressHairline(
                            progress = download.done.toFloat() / download.total,
                            showLabel = true,
                            modifier = Modifier.padding(top = Space.sm),
                        )
                    }
                    if (cacheQueueSize > 0) {
                        Text(
                            text = "后面还有 $cacheQueueSize 本排队",
                            fontSize = 11.sp,
                            lineHeight = 17.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = Space.xs),
                        )
                    }
                    if (download.running && download.total > 0) {
                        SecondaryButton(
                            text = "取消下载",
                            onClick = onCancelDownload,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = Space.md),
                        )
                    }
                }
            }

            /* -------------------- 未完成任务 -------------------- */
            if (resumable.isNotEmpty()) {
                item {
                    SectionHeader("未完成任务", hint = "${resumable.size} 项可续传", index = sectionIndex.getValue("未完成任务"))
                }
                items(resumable) { task ->
                    ToolCard {
                        Row(
                            Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    text = task.title.ifEmpty { task.bookId },
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    // done 已经含在 done/total 里，再写一次「本地 N 章」是三重复读
                                    text = "${task.done}/${task.total} 章 · ${statusLabel(task.status)}",
                                    fontSize = 11.sp,
                                    lineHeight = 17.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            SecondaryButton(
                                text = "续传",
                                onClick = { onResume(task) },
                                modifier = Modifier
                                    .padding(start = Space.sm)
                                    .width(88.dp),
                            )
                        }
                    }
                }
            }

            /* -------------------- 下载策略 -------------------- */
            item {
                SectionHeader("下载策略", index = sectionIndex.getValue("下载策略"))
                ToolCard {
                    SwitchRow(
                        title = "仅 Wi-Fi 下载",
                        description = "移动网络下不自动开始或续传，避免偷跑流量。",
                        checked = policy.wifiOnly,
                        onCheckedChange = { onPolicy(policy.copy(wifiOnly = it)) },
                    )
                    ToolDivider()
                    SwitchRow(
                        title = "只下未读",
                        description = "从阅读进度之后开始，跳过已读章节。",
                        checked = policy.skipRead,
                        onCheckedChange = { onPolicy(policy.copy(skipRead = it)) },
                    )
                    ToolDivider()
                    Text(
                        text = "阅读时预取",
                        fontSize = 14.sp,
                        lineHeight = 20.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(top = Space.xs),
                    )
                    Text(
                        text = "翻到一章时，顺手把后面几章缓到本地；关掉就只按手动下载走。",
                        fontSize = 11.sp,
                        lineHeight = 17.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                    SegmentedRow(
                        options = DownloadPolicy.PREFETCH_CHOICES.map { if (it == 0) "关" else "$it" },
                        selected = policy.prefetch.let { if (it == 0) "关" else "$it" },
                        onSelect = { label ->
                            onPolicy(policy.copy(prefetchChapters = label.toIntOrNull() ?: 0))
                        },
                        modifier = Modifier.padding(top = Space.sm),
                    )
                    Text(
                        text = "后 N 章 · 当前章之前保留 2 章，窗口外的预取章会自动清掉。",
                        fontSize = 11.sp,
                        lineHeight = 17.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = Space.xs),
                    )
                    ToolDivider()
                    SwitchRow(
                        title = "每天检查新章并通知",
                        description = "每天一次系统通知，提示追更作品的新章节。",
                        checked = newChapterNotify,
                        onCheckedChange = onNewChapterNotify,
                    )
                    ToolDivider()
                    Text(
                        text = "存储上限",
                        fontSize = 14.sp,
                        lineHeight = 20.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(top = Space.xs),
                    )
                    Text(
                        text = "离线库总占用超过上限后不再自动下载。",
                        fontSize = 11.sp,
                        lineHeight = 17.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                    SegmentedRow(
                        options = listOf("不限", "512MB", "1GB", "2GB"),
                        selected = storageLimitLabel(policy.maxBytes),
                        onSelect = { label ->
                            onPolicy(policy.copy(maxBytes = storageLimitBytes(label)))
                        },
                        modifier = Modifier.padding(top = Space.sm),
                    )
                }
            }

            /* -------------------- 本地已下载 -------------------- */
            item {
                SectionHeader("本地已下载", hint = if (offlineBooks.isEmpty()) "" else "${offlineBooks.size} 本", index = sectionIndex.getValue("本地已下载"))
            }
            if (offlineBooks.isEmpty()) {
                item {
                    ToolCard {
                        Text(
                            // 详情页没有裸露的「下载」按钮：入口收在右上角「更多」里，
                            // 旧文案会让人在详情页到处找一个不存在的按钮。
                            text = "尚无缓存。打开一本书，点右上角「更多」→「下载全书」或「下载前 20 章」。",
                            fontSize = 12.sp,
                            lineHeight = 18.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            } else {
                items(offlineBooks) { b ->
                    ToolCard {
                        Row(
                            Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    text = b.title,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                val size = bookSizes[b.bookId] ?: 0L
                                Text(
                                    text = buildString {
                                        append("${b.chapterCount} 章")
                                        if (b.author.isNotBlank()) append(" · ${b.author}")
                                        if (size > 0) append(" · ${formatBytes(size)}")
                                    },
                                    fontSize = 11.sp,
                                    lineHeight = 17.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            Row(
                                Modifier.padding(start = Space.sm),
                                horizontalArrangement = Arrangement.spacedBy(Space.xs),
                            ) {
                                IconAction(
                                    icon = Icons.Filled.Bookmark,
                                    label = "阅读",
                                    onClick = { onOpen(b) },
                                )
                                IconAction(
                                    icon = Icons.Filled.FileDownload,
                                    label = "导出",
                                    onClick = { onExportBook(b) },
                                )
                                IconAction(
                                    icon = Icons.Filled.DeleteOutline,
                                    label = "删除",
                                    onClick = { confirmDelete = b },
                                )
                            }
                        }
                    }
                }
            }

            /* -------------------- 存储与残留 -------------------- */
            item {
                SectionHeader("存储与残留", index = sectionIndex.getValue("存储与残留"))
                ToolCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Filled.Storage,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp),
                        )
                        Text(
                            text = "离线库合计",
                            fontSize = 14.sp,
                            lineHeight = 20.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier
                                .weight(1f)
                                .padding(start = Space.sm),
                        )
                        Text(
                            text = formatBytes(storageTotal),
                            fontSize = 14.sp,
                            lineHeight = 20.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                    // 存储上限被设定时，条形表示占用比例；未设上限时条形为空，只报绝对值。
                    val limit = policy.maxBytes
                    ProgressHairline(
                        progress = if (limit > 0) storageTotal.toFloat() / limit.toFloat() else 0f,
                        showLabel = true,
                        label = if (limit > 0)
                            "${formatBytes(storageTotal)} / ${formatBytes(limit)}"
                        else formatBytes(storageTotal),
                        modifier = Modifier.padding(top = Space.sm),
                    )
                    Text(
                        text = if (leftoverCount > 0)
                            "残留文件 $leftoverCount 个 · ${formatBytes(leftoverBytes)}（原子写中断与损坏隔离留下）"
                        else "无残留文件",
                        fontSize = 11.sp,
                        lineHeight = 17.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = Space.sm),
                    )
                    if (leftoverCount > 0) {
                        SecondaryButton(
                            text = "清理残留",
                            onClick = onCleanLeftovers,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = Space.md),
                        )
                    }
                }
            }

            }
            if (page == ToolsPage.Reading) {

            /* -------------------- 阅读目标 -------------------- */
            item {
                val normalized = goal.normalized()
                val targetSecs = GoalMath.goalSeconds(normalized)
                val ratio = if (targetSecs > 0) {
                    (todaySeconds.toFloat() / targetSecs.toFloat()).coerceIn(0f, 1f)
                } else 0f
                // 连续天数用 stats 快照里的 days 现算；快照进页面时刚刷过。
                val streak = GoalMath.streak(stats.optJSONObject("days"), normalized, GoalMath.dayKey())
                val metToday = targetSecs > 0 && todaySeconds >= targetSecs
                val remainMin = ((targetSecs - todaySeconds).coerceAtLeast(0L) + 59L) / 60L
                val hint = when {
                    metToday && streak > 0 -> "连续 $streak 天达标"
                    metToday -> "今日已达标"
                    streak > 0 -> "已连续 $streak 天 · 再读 $remainMin 分钟保持"
                    else -> "再读 $remainMin 分钟达标"
                }
                SectionHeader("阅读目标", index = sectionIndex.getValue("阅读目标"))
                ToolCard {
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                text = "每天 ${normalized.minutes} 分钟",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                            )
                            Text(
                                text = "按自然日结算，连续天数从阅读记录现算，换目标会重算历史",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = Space.xxs),
                            )
                        }
                        Switch(
                            checked = goal.enabled,
                            onCheckedChange = { onGoal(goal.copy(enabled = it)) },
                        )
                    }
                    if (goal.enabled) {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(top = Space.lg),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            GoalRing(
                                progress = ratio,
                                modifier = Modifier.size(64.dp),
                            )
                            Column(
                                Modifier
                                    .weight(1f)
                                    .padding(start = Space.lg),
                            ) {
                                Text(
                                    text = "今天 ${todaySeconds / 60} / ${normalized.minutes} 分钟",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium,
                                )
                                Text(
                                    text = hint,
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(top = Space.xxs),
                                )
                            }
                        }
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(top = Space.md),
                            horizontalArrangement = Arrangement.spacedBy(Space.sm),
                        ) {
                            GOAL_CHOICES.forEach { m ->
                                GoalChip(
                                    text = "$m 分",
                                    selected = normalized.minutes == m,
                                    onClick = { onGoal(goal.copy(minutes = m)) },
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        }
                    }
                }
            }

            /* -------------------- 阅读统计 -------------------- */
            item {
                val secs = stats.optLong("seconds")
                val words = stats.optLong("words")
                val chaps = stats.optLong("chapters")
                val days = stats.optJSONObject("days")
                val dayKeys = days?.let {
                    it.keys().asSequence().toList().sorted().takeLast(7)
                }.orEmpty()
                val dayMax = dayKeys.maxOfOrNull { days?.optLong(it) ?: 0L }
                    ?.coerceAtLeast(1L) ?: 1L
                // 与上面 sectionIndex 用的是同一个 hasStats，避免序号表与实际渲染分叉
                if (hasStats) {
                    SectionHeader("阅读统计", index = sectionIndex.getValue("阅读统计"))
                    ToolCard {
                        // 今日与连续天数对所有人都有意义（不要求先开目标）：
                        // 累计数字会越读越麻木，只有「今天」和「没断」这两件事会推着人回来。
                        val todayKey = GoalMath.dayKey()
                        val todayMinutes = GoalMath.daySeconds(days, todayKey) / 60
                        val streakDays = GoalMath.streak(days, goal, todayKey)
                        Row(
                            Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = "今日 $todayMinutes 分钟",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Spacer(Modifier.weight(1f))
                            Text(
                                text = if (streakDays > 0) "连续 $streakDays 天" else "今天还没读",
                                fontSize = 11.sp,
                                color = if (streakDays > 0) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(top = Space.md),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            MetaStat(
                                value = "${secs / 60}",
                                label = "分钟",
                                modifier = Modifier.weight(1f),
                                emphasized = true,
                            )
                            MetaStatDivider()
                            MetaStat(
                                // 显式 Locale.US：默认 Locale 在部分地区会把小数点写成逗号
                                value = String.format(java.util.Locale.US, "%.1f", words / 10000.0),
                                label = "万字",
                                modifier = Modifier.weight(1f),
                            )
                            MetaStatDivider()
                            MetaStat(
                                value = "$chaps",
                                label = "章",
                                modifier = Modifier.weight(1f),
                            )
                        }
                        if (dayKeys.isNotEmpty()) {
                            Text(
                                text = "近 7 天",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = Space.md),
                            )
                            dayKeys.forEach { day ->
                                val v = days?.optLong(day) ?: 0L
                                Row(
                                    Modifier.fillMaxWidth().padding(top = Space.sm),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        text = day,
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.width(52.dp),
                                    )
                                    ProgressHairline(
                                        progress = (v.toFloat() / dayMax.toFloat()).coerceIn(0f, 1f),
                                        modifier = Modifier.weight(1f),
                                    )
                                    Text(
                                        text = "${v / 60} 分",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier
                                            .padding(start = Space.sm)
                                            .width(44.dp),
                                    )
                                }
                            }
                        }
                    }
                }
            }

            /* -------------------- 月度/年度回顾 -------------------- */
            // 统计区块只画「今天」和「近 7 天」，跨月的感受没人接住。
            // 这里按 stats.days 的日期前缀归月/归年，再做一张可以发出去的卡片。
            if (hasStats) {
                item {
                    SectionHeader("阅读回顾", index = sectionIndex.getValue("阅读统计"))
                    ToolCard {
                        ReviewBlock(
                            days = stats.optJSONObject("days"),
                            onOpenReport = onOpenReport,
                        )
                    }
                }
            }

            /* -------------------- 每日提醒 -------------------- */
            item {
                SectionHeader("每日提醒", index = sectionIndex.getValue("每日提醒"))
                ToolCard {
                    SwitchRow(
                        title = "每天提醒我读书",
                        description = "到点发一条通知，和阅读目标、新章提醒互不影响。",
                        checked = reminder.enabled,
                        onCheckedChange = { onReminder(reminder.copy(enabled = it)) },
                    )
                    if (reminder.enabled) {
                        ToolDivider()
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    text = "提醒时间",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                                Text(
                                    text = if (reminder.onlyIfUnmet) "达标当天不打扰" else "每天都提醒",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            TextButton(onClick = { remindTimeOpen = true }) {
                                Text(
                                    text = reminder.label(),
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                        }
                        ToolDivider()
                        SwitchRow(
                            title = "仅未达标时提醒",
                            description = if (goal.enabled) {
                                "达到当天目标后就不再发通知。"
                            } else {
                                "需要先开启阅读目标；目标没开时每天都会提醒。"
                            },
                            checked = reminder.onlyIfUnmet,
                            onCheckedChange = { onReminder(reminder.copy(onlyIfUnmet = it)) },
                        )
                        if (!notificationAllowed) {
                            ToolDivider()
                            Text(
                                text = "系统还没允许发送通知，提醒会静默失败。",
                                fontSize = 11.sp,
                                lineHeight = 17.sp,
                                color = MaterialTheme.colorScheme.error,
                            )
                            TextButton(onClick = onRequestNotification) { Text("去授权") }
                        }
                    }
                }
            }

            }
            if (page == ToolsPage.Content) {

            /* -------------------- 正文替换 -------------------- */
            item {
                SectionHeader("正文替换", index = sectionIndex.getValue("正文替换"))
                ToolCard {
                    Text(
                        text = "每行一条：原文 => 替换；正则写 /模式/ => 替换",
                        fontSize = 11.sp,
                        lineHeight = 17.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedTextField(
                        value = ruleText,
                        onValueChange = { ruleText = it },
                        placeholder = { Text("例如：多余空格 => ", fontSize = 12.sp) },
                        textStyle = androidx.compose.ui.text.TextStyle(fontSize = 12.sp),
                        shape = RoundedCornerShape(Radius.sm),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = Space.sm)
                            .height(120.dp),
                    )
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(top = Space.md),
                        horizontalArrangement = Arrangement.spacedBy(Space.sm),
                    ) {
                        PrimaryButton(
                            text = "保存规则",
                            onClick = {
                                val list = ruleText.split("\n").map { it.trim() }
                                    .filter { it.isNotEmpty() }.map { line ->
                                        val idx = line.indexOf("=>")
                                        var pattern =
                                            if (idx >= 0) line.substring(0, idx).trim() else line
                                        val replacement =
                                            if (idx >= 0) line.substring(idx + 2).trim() else ""
                                        var regex = false
                                        if (pattern.startsWith("/") &&
                                            pattern.lastIndexOf('/') > 0
                                        ) {
                                            pattern = pattern.substring(1, pattern.lastIndexOf('/'))
                                            regex = true
                                        }
                                        ReplaceRule(pattern, replacement, regex, true)
                                    }
                                onSaveRules(list)
                            },
                            modifier = Modifier.weight(1f),
                        )
                        SecondaryButton(
                            text = "清空",
                            onClick = { confirmClear = true },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }

            /* -------------------- 书摘 -------------------- */
            item {
                SectionHeader("书摘", index = sectionIndex.getValue("书摘"))
                ToolCard {
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Highlight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp),
                        )
                        Text(
                            text = "长按正文段落可高亮或加书签；书摘可导出为 Markdown，并随备份一起迁移。",
                            fontSize = 12.sp,
                            lineHeight = 18.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .weight(1f)
                                .padding(start = Space.sm),
                        )
                    }
                    SecondaryButton(
                        text = "查看书摘",
                        onClick = onOpenMarks,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = Space.md),
                    )
                }
            }

            }
            if (page == ToolsPage.Backup) {

            /* -------------------- 备份与恢复 -------------------- */
            item {
                SectionHeader("备份与恢复", index = sectionIndex.getValue("备份与恢复"))
                ToolCard {
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = Icons.Filled.SettingsBackupRestore,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp),
                        )
                        Text(
                            text = "导出至应用外部目录 x-read-backup.json（含书目、进度、书摘、统计、替换规则、追剧与收藏，不含正文）",
                            fontSize = 12.sp,
                            lineHeight = 18.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .weight(1f)
                                .padding(start = Space.sm),
                        )
                    }
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(top = Space.md),
                        horizontalArrangement = Arrangement.spacedBy(Space.sm),
                    ) {
                        PrimaryButton(
                            text = "导出备份",
                            onClick = onExport,
                            modifier = Modifier.weight(1f),
                        )
                        SecondaryButton(
                            text = "导入备份",
                            onClick = { confirmImport = true },
                            modifier = Modifier.weight(1f),
                        )
                    }

                    /* --- 自动备份：选目录 + 间隔 --- */
                    Text(
                        text = "自动备份",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(top = Space.lg),
                    )
                    Text(
                        text = if (backup.dirLabel.isEmpty()) {
                            "还没选目录。选一个文件夹后，应用启动时会按间隔覆盖写入 x-read-backup.json。"
                        } else {
                            val last = if (backup.lastLabel.isEmpty()) "" else "，上次 ${backup.lastLabel}"
                            "写入 ${backup.dirLabel} / x-read-backup.json$last"
                        },
                        fontSize = 12.sp,
                        lineHeight = 18.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = Space.xs),
                    )
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(top = Space.md),
                        horizontalArrangement = Arrangement.spacedBy(Space.sm),
                    ) {
                        SecondaryButton(
                            text = "选择目录",
                            onClick = onPickBackupDir,
                            modifier = Modifier.weight(1f),
                        )
                        PrimaryButton(
                            text = "立即备份",
                            onClick = onBackupNow,
                            enabled = backup.dirLabel.isNotEmpty(),
                            modifier = Modifier.weight(1f),
                        )
                    }
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(top = Space.sm),
                        horizontalArrangement = Arrangement.spacedBy(Space.xs),
                    ) {
                        listOf(0 to "关闭", 24 to "每天", 72 to "每 3 天", 168 to "每周").forEach { (hours, label) ->
                            BackupChip(
                                label = label,
                                active = backup.intervalHours == hours,
                                onClick = { onBackupInterval(hours) },
                            )
                        }
                    }

                    /* --- WebDAV：可选第二份 --- */
                    Text(
                        text = "WebDAV（可选）",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(top = Space.lg),
                    )
                    OutlinedTextField(
                        value = backup.davUrl,
                        onValueChange = { onDavSave(it, backup.davUser, backup.davPass, backup.davEnabled) },
                        label = { Text("目录地址") },
                        placeholder = { Text("https://dav.example.com/xread/") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = Space.sm),
                    )
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(top = Space.sm),
                        horizontalArrangement = Arrangement.spacedBy(Space.sm),
                    ) {
                        OutlinedTextField(
                            value = backup.davUser,
                            onValueChange = { onDavSave(backup.davUrl, it, backup.davPass, backup.davEnabled) },
                            label = { Text("账号") },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                        )
                        OutlinedTextField(
                            value = backup.davPass,
                            onValueChange = { onDavSave(backup.davUrl, backup.davUser, it, backup.davEnabled) },
                            label = { Text("密码") },
                            singleLine = true,
                            visualTransformation = PasswordVisualTransformation(),
                            modifier = Modifier.weight(1f),
                        )
                    }
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(top = Space.sm),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "每次自动备份后同步一份",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f),
                        )
                        Switch(
                            checked = backup.davEnabled,
                            onCheckedChange = { onDavSave(backup.davUrl, backup.davUser, backup.davPass, it) },
                        )
                    }
                    SecondaryButton(
                        text = "上传到 WebDAV",
                        onClick = onDavUpload,
                        enabled = backup.davUrl.isNotBlank(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = Space.sm),
                    )
                }
            }

            }
            if (page == ToolsPage.Content) {

            /* -------------------- 朗读 -------------------- */
            item {
                SectionHeader("朗读", index = sectionIndex.getValue("朗读"))
                ToolCard {
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                            contentDescription = null,
                            tint = if (ttsRunning) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp),
                        )
                        Text(
                            text = if (ttsRunning) "朗读中…" else "系统 TTS 朗读当前章节",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = if (ttsRunning) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier
                                .weight(1f)
                                .padding(start = Space.sm),
                        )
                    }
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(top = Space.md),
                        horizontalArrangement = Arrangement.spacedBy(Space.sm),
                    ) {
                        PrimaryButton(
                            text = "朗读本章",
                            onClick = onSpeak,
                            enabled = !ttsRunning,
                            modifier = Modifier.weight(1f),
                        )
                        SecondaryButton(
                            text = "停止",
                            onClick = onStopSpeak,
                            enabled = ttsRunning,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
            }
        }
    }

    confirmDelete?.let { book ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text("删除本地书籍") },
            text = { Text("将删除《${book.title}》已下载的 ${book.chapterCount} 章正文，不可恢复。书架条目与阅读进度会保留。") },
            confirmButton = {
                TextButton(onClick = {
                    onDelete(book); confirmDelete = null
                }) { Text("删除") }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = null }) { Text("取消") }
            },
        )
    }
    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("清空替换规则") },
            text = { Text("将删除全部 ${rules.size} 条替换规则，不可恢复。") },
            confirmButton = {
                TextButton(onClick = {
                    ruleText = ""; onSaveRules(emptyList()); confirmClear = false
                }) { Text("清空") }
            },
            dismissButton = {
                TextButton(onClick = { confirmClear = false }) { Text("取消") }
            },
        )
    }
    if (remindTimeOpen) {
        ReminderTimeDialog(
            reminder = reminder,
            onDismiss = { remindTimeOpen = false },
            onConfirm = { r ->
                remindTimeOpen = false
                onReminder(r)
            },
        )
    }

    if (confirmImport) {
        AlertDialog(
            onDismissRequest = { confirmImport = false },
            title = { Text("导入备份") },
            text = { Text("将用备份内容覆盖/补充书目、进度、统计与替换规则。本地已有的分组不会被清空。") },
            confirmButton = {
                TextButton(onClick = {
                    onImport(); confirmImport = false
                }) { Text("导入") }
            },
            dismissButton = {
                TextButton(onClick = { confirmImport = false }) { Text("取消") }
            },
        )
    }
}

/* --------------------------- 卡片内零件 --------------------------- */

/**
 * 工具页的区块卡片：**白面 + 1dp 发丝线 + 0 阴影**。
 *
 * 层级靠卡片与页面的底色差和这根发丝线，不靠投影。
 */
@Composable
private fun ToolCard(
    modifier: Modifier = Modifier,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = Space.gutter),
        shape = RoundedCornerShape(Radius.none),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(
            Modifier.padding(Space.card),
            verticalArrangement = Arrangement.spacedBy(Space.sm),
            content = content,
        )
    }
}

/** 卡片内的发丝分隔线：把同卡片里的多行切开。 */
@Composable
private fun ToolDivider(modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(MaterialTheme.colorScheme.outlineVariant),
    )
}

/**
 * 开关行：标题 13sp + 说明 11–12sp，整行 ≥48dp。
 *
 * 说明不是可有可无的装饰——「只下未读」和「仅 Wi-Fi」的字面差异太小，
 * 没有一行解释就分不清跳过的是哪一段。
 */
@Composable
private fun SwitchRow(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 14.sp,
                lineHeight = 20.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = description,
                fontSize = 12.sp,
                lineHeight = 18.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 1.dp),
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.padding(start = Space.md),
        )
    }
}

/** 图标动作按钮：24dp 图标 + 48dp 触控区，带 contentDescription。 */
@Composable
private fun IconAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
) {
    androidx.compose.material3.IconButton(
        onClick = onClick,
        modifier = Modifier.size(48.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp),
        )
    }
}

/* --------------------------- 阅读目标零件 --------------------------- */

/** 目标分钟档位。要精确值可用「自定义」——这里只放最常用的几档，避免一排 chip 溢出屏幕。 */
private val GOAL_CHOICES = listOf(10, 20, 30, 45, 60)

/**
 * 今日完成度环。
 *
 * 用 Canvas 手绘而不是 CircularProgressIndicator：后者是 indeterminate/带间隙的
 * 进度指示器，作「今日目标」时缺口方向、圆头、粗细都改不动。3px 圆头弧在 64dp
 * 直径上刚好压得住 13sp 的百分比字。
 */
@Composable
private fun GoalRing(progress: Float, modifier: Modifier = Modifier) {
    val accent = MaterialTheme.colorScheme.primary
    val track = MaterialTheme.colorScheme.surfaceVariant
    val p = progress.coerceIn(0f, 1f)
    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = 5.dp.toPx()
            val d = size.minDimension - stroke
            val topLeft = Offset((size.width - d) / 2f, (size.height - d) / 2f)
            val bounds = Size(d, d)
            drawArc(
                color = track,
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = topLeft,
                size = bounds,
                style = Stroke(stroke, cap = StrokeCap.Round),
            )
            if (p > 0f) {
                drawArc(
                    color = accent,
                    startAngle = -90f,
                    sweepAngle = 360f * p,
                    useCenter = false,
                    topLeft = topLeft,
                    size = bounds,
                    style = Stroke(stroke, cap = StrokeCap.Round),
                )
            }
        }
        Text(
            text = "${(p * 100).toInt()}%",
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = accent,
        )
    }
}

/** 目标档位 chip。选中用主色填充，未选中留发丝描边——和书架的筛选 chip 同一套语言。 */
@Composable
private fun GoalChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .height(36.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(Radius.sm),
        color = if (selected) MaterialTheme.colorScheme.primary
        else MaterialTheme.colorScheme.surface,
        contentColor = if (selected) MaterialTheme.colorScheme.onPrimary
        else MaterialTheme.colorScheme.onSurfaceVariant,
        border = if (selected) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(text, fontSize = 11.sp, fontWeight = FontWeight.Medium)
        }
    }
}

/* --------------------------- 存储上限档位 --------------------------- */

/** 档位 → 字节。0 表示不限。 */
private fun storageLimitBytes(label: String): Long = when (label) {
    "512MB" -> 512L * 1024 * 1024
    "1GB" -> 1024L * 1024 * 1024
    "2GB" -> 2048L * 1024 * 1024
    else -> 0L
}

/** 字节 → 档位名。超出上限的自定义值归到「不限」以外的最近档——不猜，落回「不限」。 */
private fun storageLimitLabel(maxBytes: Long): String = when (maxBytes) {
    0L -> "不限"
    512L * 1024 * 1024 -> "512MB"
    1024L * 1024 * 1024 -> "1GB"
    2048L * 1024 * 1024 -> "2GB"
    else -> "不限"
}

/**
 * 任务状态是内部常量（running/paused/failed/partial/done），
 * 直接渲染到界面上会给用户看英文小写单词。这里统一转成中文。
 */
private fun statusLabel(status: String): String = when (status) {
    "running" -> "下载中"
    "paused" -> "已暂停"
    "failed" -> "失败"
    "partial" -> "未下完"
    "done" -> "已完成"
    else -> status
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun ReminderTimeDialog(
    reminder: ReadingReminder,
    onDismiss: () -> Unit,
    onConfirm: (ReadingReminder) -> Unit,
) {
    val state = rememberTimePickerState(
        initialHour = reminder.hour,
        initialMinute = reminder.minute,
        is24Hour = true,
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("提醒时间") },
        text = { TimePicker(state = state) },
        confirmButton = {
            TextButton(onClick = {
                onConfirm(reminder.copy(hour = state.hour, minute = state.minute))
            }) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

/**
 * 一段时期（本月 / 今年）的阅读回顾。
 *
 * 数据只有 `stats.days` 里按天累加的秒数，所以这里能诚实给出的就是
 * 「读了几天 / 累计多久 / 最长一天多久」；章节数与字数在别处只有总量，
 * 拆不到单月，宁可不写也不估算。
 */
@Composable
private fun ReviewBlock(days: JSONObject?, onOpenReport: () -> Unit = {}) {
    val context = LocalContext.current
    var yearly by remember { mutableStateOf(false) }
    val calendar = remember { java.util.Calendar.getInstance() }
    val year = calendar.get(java.util.Calendar.YEAR)
    val month = calendar.get(java.util.Calendar.MONTH) + 1
    val prefix = if (yearly) String.format(java.util.Locale.US, "%04d", year)
    else String.format(java.util.Locale.US, "%04d-%02d", year, month)
    val label = if (yearly) "${year} 年" else "${month} 月"

    val entries = remember(days, prefix) {
        days?.keys()?.asSequence()
            ?.filter { it.startsWith(prefix) }
            ?.map { it to days.optLong(it) }
            ?.filter { (_, v) -> v > 0 }
            ?.toList()
            .orEmpty()
    }
    val totalSeconds = entries.sumOf { it.second }
    val activeDays = entries.size
    val bestDay = entries.maxOfOrNull { it.second } ?: 0L
    val hours = totalSeconds / 3600.0

    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = "回顾",
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.weight(1f))
        listOf(false to "本月", true to "今年").forEach { (y, text) ->
            Text(
                text = text,
                fontSize = 11.sp,
                fontWeight = if (yearly == y) FontWeight.Bold else FontWeight.Normal,
                color = if (yearly == y) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .padding(start = Space.md)
                    .clickable { yearly = y },
            )
        }
    }

    if (activeDays == 0) {
        Text(
            text = "${label}还没有读完的十分钟。",
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = Space.sm),
        )
        return
    }

    Row(
        Modifier
            .fillMaxWidth()
            .padding(top = Space.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MetaStat(value = "$activeDays", label = "天在读", modifier = Modifier.weight(1f), emphasized = true)
        MetaStatDivider()
        MetaStat(
            value = String.format(java.util.Locale.US, "%.1f", hours),
            label = "小时",
            modifier = Modifier.weight(1f),
        )
        MetaStatDivider()
        MetaStat(value = "${bestDay / 60}", label = "最长一天/分", modifier = Modifier.weight(1f))
    }

    // 三个数字只是摘要，真正的月报在独立页里（日历热力图 + 与上个周期对比 + 分享卡）。
    Row(
        Modifier
            .fillMaxWidth()
            .padding(top = Space.md),
        horizontalArrangement = Arrangement.spacedBy(Space.sm),
    ) {
        PrimaryButton(
            text = "打开${if (yearly) "年度" else "本月"}报告",
            modifier = Modifier.weight(1f),
            onClick = onOpenReport,
        )
        SecondaryButton(
            text = "分享文字",
            modifier = Modifier.weight(1f),
            onClick = {
                val summary = "${label}读了 $activeDays 天，累计 " +
                    String.format(java.util.Locale.US, "%.1f", hours) + " 小时，" +
                    "最长的一天 ${bestDay / 60} 分钟。"
                val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(android.content.Intent.EXTRA_TEXT, "拾页 · $summary")
                }
                context.startActivity(android.content.Intent.createChooser(intent, "分享阅读回顾"))
            },
        )
    }
}

/** 自动备份间隔的小圆角标签，和搜索页的历史词样式区分开：这里始终带选中态。 */
@Composable
private fun BackupChip(label: String, active: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(999.dp)
    Surface(
        shape = shape,
        color = if (active) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
        else Color.Transparent,
        border = BorderStroke(
            1.dp,
            if (active) MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
            else MaterialTheme.colorScheme.outlineVariant,
        ),
        modifier = Modifier.clickable(onClick = onClick),
    ) {
        Text(
            text = label,
            fontSize = 12.sp,
            color = if (active) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
        )
    }
}
