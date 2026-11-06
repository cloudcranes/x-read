package com.alanmaster.xread.ui

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.RateReview
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alanmaster.xread.data.Book
import com.alanmaster.xread.data.Chapter
import com.alanmaster.xread.data.DownloadState
import com.alanmaster.xread.data.ReadCountFormat
import com.alanmaster.xread.data.Mark
import com.alanmaster.xread.data.ShelfItem

/**
 * 书籍详情页。
 *
 * v2 层级：**纸色带 + 封面 → 书名（衬线最大）→ 三列数字块 → 标签行 → 主次操作 → 内容**。
 *
 * 三条硬约束：
 * - 只有「开始阅读」是实心主按钮（56dp），架内/架外切换的全宽描边次按钮（48dp）；
 *   下载 / 前 20 章 / 目录 / 书评 / 听书 / 作者全部收进顶栏的「更多」菜单，
 *   不再平铺成第三、第四行按钮；
 * - 评分 / 字数 / 章数拆成三列数字块（[MetaStat] + [MetaStatDivider]），
 *   分类是字符串标签不是数字，放进下面的标签行；
 * - 目录预览行用 [androidx.compose.material.icons.Icons.Filled.Lock] 表示锁定章，
 *   不用 emoji。
 *
 * `resumeChapterId` / `resumeParagraph` 的语义不变：
 * 续读目标章 = 上次读到的章，找不到才退回第一章，绝不因为进详情页就覆盖进度。
 */
@Composable
fun DetailScreen(
    book: Book,
    chapters: List<Chapter>,
    related: List<Book>,
    download: DownloadState,
    inShelf: Boolean,
    shelfStatus: String = ShelfItem.STATUS_READING,
    shelfRating: Int = 0,
    shelfTags: List<String> = emptyList(),
    onSetStatus: (String) -> Unit = {},
    onSetRating: (Int) -> Unit = {},
    onSetTags: (List<String>) -> Unit = {},
    onBack: () -> Unit,
    /** 最近阅读的章节 id + 段落；用于「继续阅读」并避免覆盖已有进度。 */
    resumeChapterId: String,
    resumeParagraph: Int,
    onRead: (Chapter) -> Unit,
    onShelf: () -> Unit,
    onDownload: (Int) -> Unit,
    onCancel: () -> Unit,
    onOpenRelated: (Book) -> Unit,
    onOpenReviews: () -> Unit,
    onOpenAuthor: (String) -> Unit,
    onOpenToc: () -> Unit,
    onOpenAudio: () -> Unit,
    onShare: () -> Unit,
) {
    // 上游 related 常只回 1–2 本（且可能含当前书的听书版：同 id 不同标题）。
    // 去重口径必须与列表 key 一致——都按 bookId，否则同一 id 会生成两个 key 崩溃。
    val recommendations = remember(related, book.bookId) {
        related.filter { it.bookId.isNotBlank() && it.bookId != book.bookId }
            .distinctBy { it.bookId }
    }
    var descExpanded by remember(book.bookId) { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }
    // 移出书架会一并删掉这本书的分组与「已读到第几章」基线（LocalLibrary.remove 是整条移除），
    // 误触后重新加入也拿不回来，所以必须先问一句。
    var confirmRemove by remember(book.bookId) { mutableStateOf(false) }

    // 下载按钮只反映「本书」的下载状态，避免 A 书下载中在 B 书里显示「取消」
    val mine = download.running && download.bookId == book.bookId

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        LazyColumn(
            Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(bottom = Space.xxl),
        ) {
            /* -------------------- 头部：纸色带 + 封面 -------------------- */
            item {
                DetailHeader(
                    book = book,
                    menuOpen = menuOpen,
                    onMenuOpenChange = { menuOpen = it },
                    onBack = onBack,
                    onShare = onShare,
                    onToc = onOpenToc,
                    onReviews = onOpenReviews,
                    onAudio = onOpenAudio,
                    onAuthor = { onOpenAuthor(book.authorId) },
                    onDownload = { onDownload(0) },
                    onDownloadFirst = { onDownload(20) },
                    onCancel = onCancel,
                    downloading = mine,
                )
            }

            /* -------------------- 书名 + 作者行 -------------------- */
            item {
                Column(Modifier.padding(horizontal = Space.gutter)) {
                    Text(
                        text = book.title,
                        fontSize = 22.sp,
                        lineHeight = 30.sp,
                        fontFamily = FontFamily.Serif,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                    val byline = listOfNotNull(
                        book.author.takeIf { it.isNotBlank() },
                        book.category.takeIf { it.isNotBlank() },
                    ).joinToString(" · ")
                    if (byline.isNotEmpty()) {
                        Text(
                            text = byline,
                            fontSize = 13.sp,
                            lineHeight = 19.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = Space.xs),
                        )
                    }
                }
            }

            /* -------------------- 三列数字块 -------------------- */
            // 评分 / 字数 / 章数：三列等宽数字块，竖发丝线分隔。
            // 分类是字符串不是数字，放到下面的标签行。
            item {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Space.gutter, vertical = Space.lg),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // 三个数字都来自真实字段：在读人数（榜单接口会给）、
                    // 章数（目录）、字数（详情接口）。缺哪个就显示「—」。
                    MetaStat(
                        value = if (book.readCount > 0) ReadCountFormat.short(book.readCount) else "—",
                        label = "在读",
                        modifier = Modifier.weight(1f),
                    )
                    MetaStatDivider()
                    MetaStat(
                        value = if (chapters.isNotEmpty()) "${chapters.size}" else "—",
                        label = "章数",
                        modifier = Modifier.weight(1f),
                    )
                    MetaStatDivider()
                    MetaStat(
                        // 与左边「在读」用同一个格式化器，否则一栏「59.7 万」、
                        // 一栏「59 万字」，同一屏幕上两套口径。
                        value = if (book.wordCount > 0) ReadCountFormat.short(book.wordCount) + "字" else "—",
                        label = "字数",
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            /* -------------------- 标签行 -------------------- */
            item {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Space.gutter),
                    horizontalArrangement = Arrangement.spacedBy(Space.sm),
                ) {
                    // 标签只放「数字块里没说过」的东西：分类、连载状态、评分。
                    // 章数/字数已经在上面三列里，重复印一遍只会让人怀疑哪个才是准的。
                    if (book.category.isNotBlank()) {
                        Tag(book.category, emphasized = true)
                    }
                    statusLabel(book.status)?.let { Tag(it) }
                    if (book.score.isNotBlank()) {
                        Tag("${book.score} 分")
                    }
                }
            }

            /* -------------------- 主行动 -------------------- */
            item {
                // 续读目标：上次读到的章，找不到才退回第一章。
                // 旧实现固定 chapters.first()，读到第 50 章回详情页点「开始阅读」
                // 会把唯一一条进度覆盖成第 1 章。
                val resume = remember(chapters, resumeChapterId) {
                    chapters.firstOrNull { it.id == resumeChapterId } ?: chapters.firstOrNull()
                }
                val hasProgress = resumeChapterId.isNotEmpty() &&
                    chapters.any { it.id == resumeChapterId }
                val label = if (hasProgress) {
                    if (resumeParagraph > 0) {
                        "继续阅读 · 第${(chapters.indexOfFirst { it.id == resumeChapterId } + 1)}章"
                    } else "继续阅读"
                } else "开始阅读"
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Space.gutter, vertical = Space.lg),
                ) {
                    // 唯一的主操作：实心、全宽、56dp。
                    Button(
                        onClick = { resume?.let(onRead) },
                        enabled = resume != null,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                        shape = RoundedCornerShape(Radius.sm),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                        ),
                    ) {
                        Text(label, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                    }
                    // 次操作：全宽描边，48dp。
                    // 文案写「移出书架」而不是「已在书架」：后者读起来像状态标签，
                    // 点下去却会把书从书架删掉，用户不知道这一下是破坏性的。
                    SecondaryButton(
                        text = if (inShelf) "移出书架" else "加入书架",
                        onClick = { if (inShelf) confirmRemove = true else onShelf() },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = Space.sm),
                    )
                    // 标记只在架内出现：不在书架的书没有「我读到哪一步」这回事，
                    // 先让人把书收进来，标记才有落点。
                    if (inShelf) {
                        ShelfMarkBlock(
                            status = shelfStatus,
                            rating = shelfRating,
                            tags = shelfTags,
                            onSetStatus = onSetStatus,
                            onSetRating = onSetRating,
                            onSetTags = onSetTags,
                            modifier = Modifier.padding(top = Space.md),
                        )
                    }
                }
            }

            if (download.message.isNotEmpty()) {
                item {
                    Text(
                        "${download.message}  ${if (download.total > 0) "${download.done}/${download.total}" else ""}",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = Space.gutter, vertical = Space.xs),
                    )
                }
            }

            /* -------------------- 简介 -------------------- */
            if (book.description.isNotBlank()) {
                item { SectionHeader("简介") }
                item {
                    Column(Modifier.padding(horizontal = Space.gutter)) {
                        Text(
                            book.description,
                            fontSize = 13.sp,
                            lineHeight = 21.sp,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = if (descExpanded) Int.MAX_VALUE else 3,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (book.description.length > 60) {
                            TextButton(
                                onClick = { descExpanded = !descExpanded },
                                contentPadding = PaddingValues(horizontal = Space.xs),
                            ) {
                                Text(
                                    if (descExpanded) "收起" else "展开",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                        }
                    }
                }
            }

            /* -------------------- 相关推荐 -------------------- */
            // 相关推荐放在目录之前：目录动辄上千章，放后面等于看不见。
            if (recommendations.isNotEmpty()) {
                item { SectionHeader("相关推荐", "${recommendations.size} 本") }
                item {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = Space.gutter),
                        horizontalArrangement = Arrangement.spacedBy(Space.md),
                    ) {
                        items(recommendations, key = { it.bookId }) { r ->
                            Box(Modifier.width(100.dp)) {
                                BookCard(r, onClick = { onOpenRelated(r) })
                            }
                        }
                    }
                }
            }

            /* -------------------- 目录预览 -------------------- */
            item {
                TocHeader(chapters.size, onOpenToc)
            }
            items(chapters.take(4), key = { it.id }) { c ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { onOpenToc() }
                        .padding(horizontal = Space.gutter, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        c.title.ifEmpty { "第 ${c.id} 章" },
                        fontSize = 14.sp,
                        lineHeight = 20.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    if (c.locked) {
                        Icon(
                            Icons.Filled.Lock,
                            contentDescription = "已锁定",
                            modifier = Modifier.size(15.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                DividerLine()
            }
            if (chapters.size > 4) {
                item {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .padding(Space.sm),
                        contentAlignment = Alignment.Center,
                    ) {
                        TextButton(onClick = onOpenToc) {
                            Text("查看全部 ${chapters.size} 章", fontSize = 13.sp)
                        }
                    }
                }
            }
        }
    }

    if (confirmRemove) {
        AlertDialog(
            onDismissRequest = { confirmRemove = false },
            title = { Text("移出书架") },
            text = {
                Text(
                    "将《${book.title}》从书架移除。" +
                        "这本书的分组与「已读到第几章」标记会一起清掉，" +
                        "阅读进度和已下载正文保留。",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmRemove = false
                    onShelf()
                }) { Text("移出") }
            },
            dismissButton = {
                TextButton(onClick = { confirmRemove = false }) { Text("取消") }
            },
        )
    }
}

/* --------------------------- 头部 --------------------------- */

/**
 * 头部：24dp 纸色带 + 居中封面 + 压在色带上的返回/分享/更多三个图标。
 *
 * 色带是纯色块而不是模糊大图：省内存，也更「纸」。
 * 三个图标用负偏移压在色带上，因此 Scaffold 不再单独占一个顶栏高度。
 */
@Composable
private fun DetailHeader(
    book: Book,
    menuOpen: Boolean,
    onMenuOpenChange: (Boolean) -> Unit,
    onBack: () -> Unit,
    onShare: () -> Unit,
    onToc: () -> Unit,
    onReviews: () -> Unit,
    onAudio: () -> Unit,
    onAuthor: () -> Unit,
    onDownload: () -> Unit,
    onDownloadFirst: () -> Unit,
    onCancel: () -> Unit,
    downloading: Boolean,
) {
    Box(Modifier.fillMaxWidth()) {
        // 纸色带
        Box(
            Modifier
                .fillMaxWidth()
                .height(24.dp)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
        )
        // 图标行：负偏移 12dp，图标正好落在色带上；触控区仍是 48dp。
        Row(
            Modifier
                .fillMaxWidth()
                .offset(y = (-12).dp)
                .padding(horizontal = Space.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    "返回",
                    modifier = Modifier.size(24.dp),
                )
            }
            Spacer(Modifier.weight(1f))
            IconButton(onClick = onShare) {
                Icon(
                    Icons.Filled.Share,
                    "分享",
                    modifier = Modifier.size(24.dp),
                )
            }
            Box {
                IconButton(onClick = { onMenuOpenChange(true) }) {
                    Icon(
                        Icons.Filled.MoreVert,
                        "更多",
                        modifier = Modifier.size(24.dp),
                    )
                }
                DetailMenu(
                    expanded = menuOpen,
                    onDismiss = { onMenuOpenChange(false) },
                    hasAuthor = book.authorId.isNotBlank(),
                    downloading = downloading,
                    onToc = { onMenuOpenChange(false); onToc() },
                    onReviews = { onMenuOpenChange(false); onReviews() },
                    onAudio = { onMenuOpenChange(false); onAudio() },
                    onAuthor = { onMenuOpenChange(false); onAuthor() },
                    onDownload = { onMenuOpenChange(false); onDownload() },
                    onDownloadFirst = { onMenuOpenChange(false); onDownloadFirst() },
                    onCancel = { onMenuOpenChange(false); onCancel() },
                )
            }
        }
        // 封面：3:4、120dp 宽、8dp 圆角，居中压在色带上。
        BookCover(
            title = book.title,
            coverUrl = book.coverUrl,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .width(120.dp)
                .aspectRatio(0.75f),
        )
    }
}

/**
 * 「更多」菜单：目录 / 书评 / 听书 / 作者 / 下载。
 *
 * 这些入口一个都不能丢，只是从「第三、第四行平铺按钮」收进一个菜单。
 */
@Composable
private fun DetailMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    hasAuthor: Boolean,
    downloading: Boolean,
    onToc: () -> Unit,
    onReviews: () -> Unit,
    onAudio: () -> Unit,
    onAuthor: () -> Unit,
    onDownload: () -> Unit,
    onDownloadFirst: () -> Unit,
    onCancel: () -> Unit,
) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        DropdownMenuItem(
            text = { Text("目录") },
            leadingIcon = { Icon(Icons.AutoMirrored.Filled.List, null, Modifier.size(20.dp)) },
            onClick = onToc,
        )
        DropdownMenuItem(
            text = { Text("书评") },
            leadingIcon = { Icon(Icons.Filled.RateReview, null, Modifier.size(20.dp)) },
            onClick = onReviews,
        )
        DropdownMenuItem(
            text = { Text("听书") },
            leadingIcon = { Icon(Icons.Filled.Headphones, null, Modifier.size(20.dp)) },
            onClick = onAudio,
        )
        if (hasAuthor) {
            DropdownMenuItem(
                text = { Text("作者主页") },
                leadingIcon = { Icon(Icons.Filled.Person, null, Modifier.size(20.dp)) },
                onClick = onAuthor,
            )
        }
        DropdownMenuItem(
            text = { Text(if (downloading) "取消下载" else "下载全书") },
            leadingIcon = { Icon(Icons.Filled.Download, null, Modifier.size(20.dp)) },
            onClick = if (downloading) onCancel else onDownload,
        )
        DropdownMenuItem(
            text = { Text("下载前 20 章") },
            leadingIcon = { Icon(Icons.Filled.Download, null, Modifier.size(20.dp)) },
            onClick = onDownloadFirst,
        )
    }
}

/* --------------------------- 目录标题 --------------------------- */

/**
 * 目录区标题：左侧可点的「目录」，右侧「共 N 章」。
 *
 * 单独写而不是用 [SectionHeader]：SectionHeader 的右位要么是弱说明、要么是动作，
 * 这里要的是「右位显示章数 + 点标题进目录页」。
 */
@Composable
private fun TocHeader(chapterCount: Int, onOpenToc: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = Space.gutter, end = Space.sm, top = Space.section, bottom = Space.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "目录",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier
                .clickable(onClick = onOpenToc)
                .padding(vertical = Space.xs),
        )
        Spacer(Modifier.weight(1f))
        Text(
            text = "共 $chapterCount 章",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/* --------------------------- 工具 --------------------------- */

/* -------------------- 个人标记 -------------------- */

/**
 * 想读 / 在读 / 读完 + 星级 + 标签。
 *
 * 三件事放在同一块里，是因为它们在用户脑子里是同一个问题——「这本书对我来说是什么」。
 * 拆成三个入口会让详情页再多三行按钮。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ShelfMarkBlock(
    status: String,
    rating: Int,
    tags: List<String>,
    onSetStatus: (String) -> Unit,
    onSetRating: (Int) -> Unit,
    onSetTags: (List<String>) -> Unit,
    modifier: Modifier = Modifier,
) {
    var editingTags by remember { mutableStateOf(false) }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(0.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(
            Modifier.padding(Space.card),
            verticalArrangement = Arrangement.spacedBy(Space.sm),
        ) {
            Text(
                text = "我的标记",
                fontSize = 11.sp,
                letterSpacing = 1.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(Space.xs)) {
                ShelfItem.STATUS_LABELS.forEach { (key, label) ->
                    SelectChip(
                        label = label,
                        selected = status == key,
                        onClick = { onSetStatus(key) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                (1..5).forEach { i ->
                    IconButton(
                        onClick = { onSetRating(if (rating == i) 0 else i) },
                        modifier = Modifier.size(32.dp),
                    ) {
                        Icon(
                            imageVector = if (i <= rating) Icons.Filled.Star else Icons.Filled.StarBorder,
                            contentDescription = "$i 星",
                            tint = if (i <= rating) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.outlineVariant,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
                Spacer(Modifier.weight(1f))
                Text(
                    text = if (rating > 0) "$rating / 5" else "未评分",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.xs)) {
                tags.forEach { tag ->
                    Surface(
                        shape = RoundedCornerShape(0.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                    ) {
                        Text(
                            text = "#$tag",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = Space.sm, vertical = 3.dp),
                        )
                    }
                }
                TextButton(onClick = { editingTags = true }, modifier = Modifier.height(28.dp)) {
                    Text(if (tags.isEmpty()) "加标签" else "改标签", fontSize = 11.sp)
                }
            }
        }
    }

    if (editingTags) {
        TagEditDialog(
            initial = tags.joinToString(" "),
            onDismiss = { editingTags = false },
            onConfirm = { raw ->
                editingTags = false
                onSetTags(Mark.normalizeTags(Mark.splitTags(raw)))
            },
        )
    }
}

@Composable
private fun TagEditDialog(initial: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("标签") },
        text = {
            Column {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    singleLine = true,
                    placeholder = { Text("空格或逗号分隔，最多 ${ShelfItem.TAG_LIMIT} 个") },
                )
                Text(
                    text = "标签只在本地保存，不会同步到书源。",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = Space.xs),
                )
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(text) }) { Text("保存") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}
