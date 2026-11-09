package com.alanmaster.xread.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alanmaster.xread.BuildConfig
import com.alanmaster.xread.data.AppUpdate
import com.alanmaster.xread.data.NotificationChannels
import com.alanmaster.xread.data.OfflineRepo
import com.alanmaster.xread.data.ReaderPrefs
import com.alanmaster.xread.data.UpdateUi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 全局设置面板。
 *
 * 只留**应用级**的三件事：内容核心与同步、本地数据与存储、关于。
 * 阅读相关项（字号/行距/背景/翻页）已迁到阅读页底部抽屉，
 * 这里不再重复——两处都改同一份偏好会让用户以为有两个开关。
 *
 * 形态是**底部抽屉**而不是弹窗：旧实现用 AlertDialog 装一屏内容，
 * 超过可视高度的后半截（背景主题、两个开关）直接被裁掉，用户点不到。
 *
 * @param prefs 阅读偏好。**本面板不再修改它**，仅为保持调用方签名不变而保留。
 * @param onPrefs 阅读偏好回调。**本面板不再调用它**，仅为保持调用方签名不变而保留。
 */
@Suppress("UNUSED_PARAMETER")
@Composable
fun SettingsSheet(
    prefs: ReaderPrefs,
    onPrefs: (ReaderPrefs) -> Unit,
    onDismiss: () -> Unit,
    onRetry: () -> Unit,
    update: UpdateUi = UpdateUi(),
    onCheckUpdate: () -> Unit = {},
    onDownloadUpdate: () -> Unit = {},
    onInstallUpdate: () -> Unit = {},
    onHotUpdate: (Boolean) -> Unit = {},
    onUpdateSource: (String, String) -> Unit = { _, _ -> },
    onUpdateMirror: (String) -> Unit = {},
) {
    val local = rememberLocalStorage()

    BoxWithConstraints(Modifier.fillMaxSize()) {
        // 抽屉最高 80%：再多就会顶到状态栏，用户看不到标题栏。
        val sheetMax = maxHeight * 0.8f
        Box(Modifier.fillMaxSize()) {
            // 遮罩：点它关闭。抽屉本体没有自带遮罩，这里补一层。
            // 读屏播报时这一层没有文字，所以显式写一个动作说明。
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.45f))
                    .semantics { contentDescription = "关闭设置" }
                    .clickable(onClick = onDismiss),
            )
            Column(
                Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Bottom,
            ) {
                BottomSheet(
                    modifier = Modifier.heightIn(max = sheetMax),
                    title = "设置",
                    onClose = onDismiss,
                ) {
                    androidx.compose.foundation.lazy.LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth(),
                        contentPadding = PaddingValues(
                            start = Space.gutter,
                            end = Space.gutter,
                            bottom = Space.xl,
                        ),
                    ) {
                        /* ---------------- 内容核心与同步 ---------------- */
                        item {
                            SettingsSection(index = "01", title = "内容核心与同步", top = Space.md) {
                                SettingsRow("内容核心", "APK 内置")
                                SettingsRow("资源加载", "无需服务端 · 无需令牌")
                                Text(
                                    text = "书架、进度、下载、统计、书签与替换规则都保存在本机。",
                                    fontSize = 12.sp,
                                    lineHeight = 19.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(top = Space.sm),
                                )
                                Row(
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(top = Space.md),
                                    horizontalArrangement = Arrangement.End,
                                ) {
                                    SecondaryButton(text = "刷新内容核心", onClick = onRetry)
                                }
                            }
                        }

                        /* ---------------- 本地数据与存储 ---------------- */
                        item {
                            SettingsSection(index = "02", title = "本地数据与存储") {
                                Row(
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = Space.lg),
                                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                                ) {
                                    MetaStat(
                                        value = formatBytes(local.cacheBytes),
                                        label = "缓存占用",
                                        modifier = Modifier.weight(1f),
                                        emphasized = true,
                                    )
                                    MetaStatDivider()
                                    MetaStat(
                                        value = "${local.shelfCount}",
                                        label = "书架",
                                        modifier = Modifier.weight(1f),
                                    )
                                    MetaStatDivider()
                                    MetaStat(
                                        value = "${local.downloadedBooks}",
                                        label = "已下载",
                                        modifier = Modifier.weight(1f),
                                    )
                                }
                                DividerLine()
                                Row(
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = Space.md),
                                ) {
                                    SmallStat("${local.downloadedChapters}", "离线正文", Modifier.weight(1f))
                                    SmallStat("${local.marks}", "书摘", Modifier.weight(1f))
                                    SmallStat("${local.rules}", "替换规则", Modifier.weight(1f))
                                }
                            }
                        }

                        /* ---------------- 通知 ---------------- */
                        item {
                            SettingsSection(index = "03", title = "通知") {
                                Text(
                                    text = "四类通知各自独立，关掉一类不影响其他。",
                                    fontSize = 12.sp,
                                    lineHeight = 19.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(top = Space.sm),
                                )
                                NotificationChannelRows()
                            }
                        }

                        /* ---------------- 关于 ---------------- */
                        item {
                            SettingsSection(index = "04", title = "关于") {
                                SettingsRow("版本", "拾页 ${BuildConfig.VERSION_NAME}")
                                Text(
                                    text = "单文件安装即可使用，不采集、不上传任何阅读数据。" +
                                            "内容核心与字体映射数据的第三方声明见仓库 THIRD_PARTY_NOTICES.md。",
                                    fontSize = 12.sp,
                                    lineHeight = 19.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(top = Space.sm, bottom = Space.sm),
                                )
                                UpdatePanel(
                                    update = update,
                                    onCheck = onCheckUpdate,
                                    onDownload = onDownloadUpdate,
                                    onInstall = onInstallUpdate,
                                    onToggle = onHotUpdate,
                                    onSource = onUpdateSource,
                                    onMirror = onUpdateMirror,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * 通知渠道开关。
 *
 * 每个渠道一行：名称 + 一句说明 + 开关。关掉的是这个渠道本身，
 * 后台播放 / 朗读不会因此停止，只是不再显示控制条。
 * 开关状态取自系统，而不是本地偏好：用户在系统设置里改过之后，
 * 回到这里看到的必须是系统的真实状态。
 */
@Composable
private fun NotificationChannelRows() {
    val ctx = LocalContext.current
    val entries = remember { NotificationChannels.entries(ctx) }
    androidx.compose.runtime.LaunchedEffect(entries) {
        withContext(Dispatchers.IO) { NotificationChannels.ensure(ctx.applicationContext) }
    }
    Column(Modifier.fillMaxWidth()) {
        entries.forEach { entry ->
            var enabled by remember(entry.id) {
                mutableStateOf(NotificationChannels.isEnabled(ctx, entry.id))
            }
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = Space.md),
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = entry.title,
                        fontSize = 14.sp,
                        lineHeight = 20.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = entry.description,
                        fontSize = 11.sp,
                        lineHeight = 16.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = Space.xxs),
                    )
                }
                Switch(
                    checked = enabled,
                    onCheckedChange = { want ->
                        enabled = NotificationChannels.setEnabled(ctx, entry.id, want)
                    },
                    modifier = Modifier.semantics {
                        contentDescription = "${entry.title}通知"
                    },
                )
            }
            DividerLine()
        }
        Text(
            text = "部分机型在系统设置里手动关闭过渠道后，应用内的开关会回弹到关闭状态。",
            fontSize = 11.sp,
            lineHeight = 17.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = Space.sm),
        )
        Row(
            Modifier
                .fillMaxWidth()
                .padding(top = Space.sm),
            horizontalArrangement = Arrangement.End,
        ) {
            SecondaryButton(
                text = "打开系统通知设置",
                onClick = { NotificationChannels.openSystemSettings(ctx, "") },
            )
        }
    }
}

/**
 * 热更新面板：开关 + 更新源 + 检查/下载/安装。
 *
 * 做的是整包更新：下载完的 APK 交给系统安装器覆盖安装，applicationId 不变，
 * 书架、进度、离线正文都在私有目录里，装完不用重新导入。
 */
@Composable
private fun UpdatePanel(
    update: UpdateUi,
    onCheck: () -> Unit,
    onDownload: () -> Unit,
    onInstall: () -> Unit,
    onToggle: (Boolean) -> Unit,
    onSource: (String, String) -> Unit,
    onMirror: (String) -> Unit,
) {
    var editing by remember { mutableStateOf(false) }
    var pickingMirror by remember { mutableStateOf(false) }

    DividerLine()
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = Space.md),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = "热更新",
                fontSize = 14.sp,
                lineHeight = 20.sp,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = if (update.hotUpdate) "启动时自动检查新版本，每天最多一次" else "已关掉自动检查，仍可手动检查",
                fontSize = 11.sp,
                lineHeight = 16.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = Space.xxs),
            )
        }
        Switch(checked = update.hotUpdate, onCheckedChange = onToggle)
    }

    Row(
        Modifier
            .fillMaxWidth()
            .padding(bottom = Space.xs),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
    ) {
        Text(
            text = "更新源",
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = update.source.removePrefix("https://"),
            fontSize = 11.sp,
            color = ink3,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1.8f),
        )
        TextButton(onClick = { editing = true }) { Text("编辑", fontSize = 12.sp) }
    }

    Row(
        Modifier
            .fillMaxWidth()
            .padding(bottom = Space.xs),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
    ) {
        Text(
            text = "下载加速",
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = AppUpdate.mirrorOf(update.mirror).label,
            fontSize = 11.sp,
            color = ink3,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1.8f),
        )
        TextButton(onClick = { pickingMirror = true }) { Text("更换", fontSize = 12.sp) }
    }

    Text(
        text = when {
            update.checking -> "正在检查更新…"
            update.downloading -> "正在下载 ${(update.progress * 100).toInt()}%"
            update.file != null -> "安装包已就绪，点安装完成更新"
            update.newer && update.release != null -> "发现新版本 ${update.release.versionName}"
            update.checkedVersion.isNotBlank() -> "已是最新 ${update.checkedVersion}"
            else -> "尚未检查"
        },
        fontSize = 12.sp,
        lineHeight = 18.sp,
        color = if (update.newer) MaterialTheme.colorScheme.primary else ink3,
    )

    if (update.downloading) {
        LinearProgressIndicator(
            progress = { update.progress },
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = Space.xs),
        )
    }

    if (update.message.isNotBlank()) {
        Text(
            text = update.message,
            fontSize = 12.sp,
            lineHeight = 18.sp,
            color = ink3,
            modifier = Modifier.padding(top = Space.xs),
        )
    }

    Row(
        Modifier
            .fillMaxWidth()
            .padding(top = Space.sm, bottom = Space.sm),
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.End,
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
    ) {
        when {
            update.file != null -> {
                SecondaryButton("重新下载", onDownload, enabled = !update.downloading)
                Spacer(Modifier.width(Space.sm))
                PrimaryButton("安装", onInstall)
            }
            update.newer -> {
                SecondaryButton("检查更新", onCheck, enabled = !update.checking && !update.downloading)
                Spacer(Modifier.width(Space.sm))
                PrimaryButton(
                    text = if (update.downloading) "下载中…" else "下载安装包",
                    onClick = onDownload,
                    enabled = !update.downloading,
                )
            }
            else -> SecondaryButton(
                text = if (update.checking) "检查中…" else "检查更新",
                onClick = onCheck,
                enabled = !update.checking,
            )
        }
    }

    if (editing) {
        var draftSource by remember(update.source) { mutableStateOf(update.source) }
        var draftToken by remember(update.token) { mutableStateOf(update.token) }
        AlertDialog(
            onDismissRequest = { editing = false },
            title = { Text("更新源", fontSize = 16.sp) },
            text = {
                Column {
                    OutlinedTextField(
                        value = draftSource,
                        onValueChange = { draftSource = it },
                        label = { Text("Release 或清单地址") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = draftToken,
                        onValueChange = { draftToken = it },
                        label = { Text("GitHub 令牌（公开源可留空）") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = Space.sm),
                    )
                    Text(
                        text = "仓库已公开，默认清单地址不用令牌；换成私有仓库或 GitHub API 时，" +
                                "才填一个只有 contents:read 的只读令牌。令牌只存在本机。",
                        fontSize = 11.sp,
                        lineHeight = 17.sp,
                        color = ink3,
                        modifier = Modifier.padding(top = Space.xs),
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onSource(draftSource, draftToken)
                        editing = false
                    },
                ) { Text("保存") }
            },
            dismissButton = {
                TextButton(onClick = { editing = false }) { Text("取消") }
            },
        )
    }

    if (pickingMirror) {
        AlertDialog(
            onDismissRequest = { pickingMirror = false },
            title = { Text("下载加速", fontSize = 16.sp) },
            text = {
                Column {
                    AppUpdate.MIRRORS.forEach { m ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onMirror(m.key)
                                    pickingMirror = false
                                }
                                .padding(vertical = Space.xxs),
                            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                        ) {
                            RadioButton(
                                selected = m.key == update.mirror,
                                onClick = {
                                    onMirror(m.key)
                                    pickingMirror = false
                                },
                                modifier = Modifier.semantics {
                                    contentDescription = "加速通道 ${m.label}"
                                },
                            )
                            Text(
                                text = m.label,
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(start = Space.xs),
                            )
                        }
                    }
                    Text(
                        text = "加速通道是第三方反向代理，只代理 APK 下载；更新源和令牌只发给 GitHub 自己，" +
                            "令牌不会发给代理。通道不通时会自动退回直连。",
                        fontSize = 11.sp,
                        lineHeight = 17.sp,
                        color = ink3,
                        modifier = Modifier.padding(top = Space.xs),
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { pickingMirror = false }) { Text("关闭") }
            },
        )
    }
}

/**
 * 区块：`01` 序号 + 衬线标题 + 发丝线，随后是内容。
 *
 * 与全站 SectionHeader 对齐（序号 + 衬线 + 细线），让抽屉里的三段可数、可分。
 */
@Composable
private fun SettingsSection(
    index: String,
    title: String,
    top: androidx.compose.ui.unit.Dp = Space.section,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(top = top, bottom = Space.sm),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
        ) {
            Text(
                text = index,
                fontSize = 11.sp,
                lineHeight = 15.sp,
                letterSpacing = 1.2.sp,
                color = ink3,
            )
            Spacer(Modifier.width(Space.sm))
            Text(
                text = title,
                fontFamily = Serif,
                fontSize = 17.sp,
                lineHeight = 24.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        DividerLine()
        Column(Modifier.fillMaxWidth(), content = content)
    }
}

/** 设置项行：左标签 + 右值，行间只留空白，不加底色。 */
@Composable
private fun SettingsRow(label: String, value: String, modifier: Modifier = Modifier) {
    Row(
        modifier
            .fillMaxWidth()
            .padding(vertical = Space.md),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            fontSize = 14.sp,
            lineHeight = 20.sp,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = value,
            fontSize = 13.sp,
            lineHeight = 19.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** 左侧小统计：值在上、标签在下，三段等分。 */
@Composable
private fun SmallStat(value: String, label: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(
            text = value,
            fontSize = 14.sp,
            lineHeight = 20.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = label,
            fontSize = 11.sp,
            lineHeight = 16.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** 本机存储占用。落盘统计是 IO，放到后台线程，组合期不读盘。 */
private data class LocalStorage(
    val cacheBytes: Long = 0,
    val shelfCount: Int = 0,
    val downloadedBooks: Int = 0,
    val downloadedChapters: Int = 0,
    val marks: Int = 0,
    val rules: Int = 0,
)

@Composable
private fun rememberLocalStorage(): LocalStorage {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val stats by produceState(LocalStorage(), ctx) {
        value = withContext(Dispatchers.IO) {
            val repo = OfflineRepo.of(ctx)
            val books = runCatching { repo.offlineBooks() }.getOrDefault(emptyList())
            LocalStorage(
                // 缓存占用 = 离线库 + 内容核心目录（解密后的音频缓存在 core 的 src/ 下）。
                cacheBytes = runCatching { repo.sizeBytes() }.getOrDefault(0L) +
                        dirSize(File(ctx.filesDir, "content-core")),
                // 书架只落在 Api 私有的 LocalLibrary 里，UI 层拿不到现成计数，
                // 这里直接数一次本地书架文件的条目数（读不到就按 0 显示）。
                shelfCount = readShelfCount(ctx),
                downloadedBooks = books.size,
                downloadedChapters = books.sumOf { it.chapterCount },
                marks = runCatching { repo.allMarks().size }.getOrDefault(0),
                rules = runCatching { repo.rules().size }.getOrDefault(0),
            )
        }
    }
    return stats
}

/** 目录占用（字节）。读不到一律按 0，不让统计失败拖垮整个面板。 */
private fun dirSize(dir: File): Long = runCatching {
    fun size(f: File): Long =
        if (f.isFile) f.length() else (f.listFiles()?.sumOf { size(it) } ?: 0L)
    size(dir)
}.getOrDefault(0L)

/** 书架条目数：解析失败一律按 0，绝不让设置面板因为一个坏文件打不开。 */
private fun readShelfCount(ctx: android.content.Context): Int = runCatching {
    val f = File(ctx.filesDir, "library/shelf.json")
    if (!f.isFile) return@runCatching 0
    org.json.JSONObject(f.readText()).length()
}.getOrDefault(0)
