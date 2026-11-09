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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alanmaster.xread.data.Book
import com.alanmaster.xread.data.BookComment
import com.alanmaster.xread.data.Chapter
import com.alanmaster.xread.data.DownloadState
import com.alanmaster.xread.data.ReadCountFormat
import com.alanmaster.xread.data.Mark
import com.alanmaster.xread.data.ShelfItem

/**
 * 书籍详情页。
 *
 * v3 层级：**纸色带 + 封面 / 书名 / 作者 / 简介（同一头部块）→ 三列数字块 → 标签行 →
 * 同行主次操作（我的标记默认折叠成一行）→ 书评横向卡片流 → 内容**。
 *
 * 硬约束：
 * - 简介紧贴书名与作者，和封面同处一个头部块：不再单独占一节把首屏往下推；
 *   上游 &#10; / <br> / &nbsp; 先折成单行再按 3 行截断，展开 / 收起保留全文；
 * - 「我的标记」默认只留一行摘要（状态 / 星级 / 标签数 + 展开箭头），点开才露出
 *   状态 chips / 星级 / 标签编辑；整行 ≥48dp 按钮语义，三项数据操作与弹窗一个不丢；
 * - 「继续阅读 / 开始阅读」与「加入 / 移出书架」并排同一行：主按钮实心 56dp、
 *   次按钮描边 56dp，两件事是同一层决策，不再竖排占掉首屏；
 *   下载 / 前 20 章 / 目录 / 书评 / 听书 / 作者仍收在顶栏「更多」菜单，
 *   但书评在页面上是可左右滑动的真实短评卡片流，末尾接「查看全部」；
 * - 评分 / 字数 / 章数拆成三列数字块（[MetaStat] + [MetaStatDivider]），
 *   分类是字符串标签不是数字，放进下面的标签行；
 * - 目录预览默认只列前 10 章名称（[TocPreviewCount]），其余走「查看全部」；
 *   锁定章用 [androidx.compose.material.icons.Icons.Filled.Lock] 表示，不用 emoji。
 *
 * `resumeChapterId` / `resumeParagraph` 的语义不变：
 * 续读目标章 = 上次读到的章，找不到才退回第一章，绝不因为进详情页就覆盖进度。
 */
/** 目录预览默认只列前 10 章名称，其余进目录页。 */
private const val TocPreviewCount = 10

/** 详情只横滑展示前几条短评，完整列表仍走书评页，避免 LazyRow 扛整包评论。 */
private const val ReviewPreviewCount = 6
private val ReviewCardWidth = 260.dp
private val ReviewCardHeight = 124.dp
private val ReviewAllCardWidth = 160.dp
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
    /** 横向卡片流只消费 ViewModel 里已有的评论快照，不另起数据契约。 */
    reviews: List<BookComment> = emptyList(),
    reviewTotal: Int = 0,
    reviewsLoading: Boolean = false,
    onLoadReviews: (String) -> Unit = {},
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

    // 卡片流要显示真实短评：复用书评页的 loadComments，不复制数据、不新增接口。
    // 按 bookId 触发，切书时重取；loadComments 自己会按当前书守卫迟到响应。
    LaunchedEffect(book.bookId) {
        if (book.bookId.isNotBlank()) onLoadReviews(book.bookId)
    }

    // 下载按钮只反映「本书」的下载状态，避免 A 书下载中在 B 书里显示「取消」
    val mine = download.running && download.bookId == book.bookId

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            DetailTopBar(
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
        },
    ) { padding ->
        LazyColumn(
            Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(bottom = Space.navFloat),
        ) {
            /* ---------- 头部：封面 + 书名/作者 + 简介，同一个 item ---------- */
            item {
                Column(Modifier.fillMaxWidth()) {
                    DetailCoverSection(book = book)
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
                        // 简介与封面、书名同块：上游常带 &#10; / <br> / &nbsp;，
                        // 折成单行再按 3 行截断，展开后给全文。
                        val intro = bookDescription(book.description)
                        if (intro.isNotEmpty()) {
                            Text(
                                text = intro,
                                fontSize = 13.sp,
                                lineHeight = 21.sp,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = if (descExpanded) Int.MAX_VALUE else 3,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(top = Space.sm),
                            )
                            if (intro.length > 60) {
                                TextButton(
                                    onClick = { descExpanded = !descExpanded },
                                    modifier = Modifier.heightIn(min = 48.dp),
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
                    DividerLine(
                        Modifier.padding(
                            start = Space.gutter,
                            top = Space.md,
                            end = Space.gutter,
                        ),
                    )
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
                val resumeIndex = chapters.indexOfFirst { it.id == resumeChapterId }
                val hasProgress = resumeChapterId.isNotEmpty() && resumeIndex >= 0
                val label = if (hasProgress) "继续阅读" else "开始阅读"
                // 同行后按钮里放不下「· 第 N 章」，完整进度交给读屏播报。
                val spokenLabel = if (hasProgress && resumeParagraph > 0) {
                    "继续阅读，上次读到第 ${resumeIndex + 1} 章"
                } else label
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Space.gutter, vertical = Space.lg),
                ) {
                    // 「继续阅读」与「加入 / 移出书架」并排：两件事是同一层决策，
                    // 竖排成两行会把书评与目录都挤到首屏之外。
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(Space.sm),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        // 唯一主操作：实心，56dp。
                        Button(
                            onClick = { resume?.let(onRead) },
                            enabled = resume != null,
                            modifier = Modifier
                                .weight(1.35f)
                                .height(56.dp)
                                .semantics { contentDescription = spokenLabel },
                            shape = RoundedCornerShape(Radius.sm),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary,
                            ),
                        ) {
                            Text(
                                label,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        // 次操作：描边，与主按钮等高 56dp。
                        // 文案写「移出书架」而不是「已在书架」：后者读起来像状态标签，
                        // 点下去却会把书从书架删掉，用户不知道这一下是破坏性的。
                        SecondaryButton(
                            text = if (inShelf) "移出书架" else "加入书架",
                            onClick = { if (inShelf) confirmRemove = true else onShelf() },
                            modifier = Modifier.weight(1f),
                            height = 56.dp,
                        )
                    }
                    // 进度落点单独写成一行小字，信息不丢，按钮也不折行。
                    if (hasProgress) {
                        Text(
                            text = "上次读到第 ${resumeIndex + 1} 章",
                            fontSize = 11.sp,
                            lineHeight = 15.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = Space.xs),
                        )
                    }
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

            /* -------------------- 读者书评：横向卡片流 -------------------- */
            item {
                ReviewCarousel(
                    reviews = reviews,
                    reviewTotal = reviewTotal,
                    reviewsLoading = reviewsLoading,
                    onOpenReviews = onOpenReviews,
                )
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
            items(chapters.take(TocPreviewCount), key = { it.id }) { c ->
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
            if (chapters.size > TocPreviewCount) {
                item {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .padding(Space.sm),
                        contentAlignment = Alignment.Center,
                    ) {
                        TextButton(
                            onClick = onOpenToc,
                            modifier = Modifier.height(48.dp),
                        ) {
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
private fun DetailTopBar(
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
    XReadTopBar(
        title = "",
        onBack = onBack,
        actions = {
            IconButton(onClick = onShare) {
                Icon(
                    Icons.Filled.Share,
                    "分享",
                    modifier = Modifier.size(22.dp),
                )
            }
            Box {
                IconButton(onClick = { onMenuOpenChange(true) }) {
                    Icon(
                        Icons.Filled.MoreVert,
                        "更多",
                        modifier = Modifier.size(22.dp),
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
        },
    )
}

@Composable
private fun DetailCoverSection(book: Book) {
    Box(
        Modifier
            .fillMaxWidth()
            .padding(top = Space.xs, bottom = Space.md),
        contentAlignment = Alignment.Center,
    ) {
        BookCover(
            title = book.title,
            coverUrl = book.coverUrl,
            corner = Radius.sm,
            modifier = Modifier
                .width(128.dp)
                .aspectRatio(0.75f),
        )
    }
}

/**
 * 「更多」菜单：目录 / 书评 / 听书 / 作者 / 下载。
 *
 * 这些入口一个都不能丢；书评在页面上另有显眼卡片，菜单里保留一份快捷入口。
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

/* --------------------------- 读者书评：横向卡片流 --------------------------- */

/**
 * 详情页首屏只做「可左右移动」的短评展示：
 * - 原生 [LazyRow] 承担横向手势，不与外层竖向 LazyColumn 抢事件；
 * - 数据直接来自 ViewModel 的评论快照（与书评页同一份 loadComments 结果），
 *   详情页不复制、不缓存、不新增接口；
 * - 每张卡都是 [Role.Button]，标题行也是按钮，给滑动之外一条明确的点击/读屏路径；
 * - 末尾固定「查看全部」卡，即使 0 条短评也有两张卡可左右移动。
 */
@Composable
private fun ReviewCarousel(
    reviews: List<BookComment>,
    reviewTotal: Int,
    reviewsLoading: Boolean,
    onOpenReviews: () -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(top = Space.sm)) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = Space.gutter)
                .heightIn(min = 48.dp)
                .clickable(onClick = onOpenReviews, role = Role.Button)
                .semantics(mergeDescendants = true) {
                    contentDescription = "读者书评，共 $reviewTotal 条，点击查看全部书评"
                },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Filled.RateReview,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp),
            )
            Text(
                "读者书评",
                fontFamily = FontFamily.Serif,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(start = Space.sm),
            )
            Spacer(Modifier.weight(1f))
            Text(
                if (reviewTotal > 0) "共 $reviewTotal 条 · 左右滑动" else "左右滑动 · 查看全部",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
        }
        LazyRow(
            Modifier
                .fillMaxWidth()
                .height(ReviewCardHeight),
            contentPadding = PaddingValues(horizontal = Space.gutter),
            horizontalArrangement = Arrangement.spacedBy(Space.md),
        ) {
            if (reviews.isEmpty()) {
                item(key = "review-state") {
                    ReviewStateCard(loading = reviewsLoading, onClick = onOpenReviews)
                }
            }
            itemsIndexed(
                reviews.take(ReviewPreviewCount),
                key = { i, c -> "$i-${c.id}" },
            ) { index, comment ->
                ReviewCard(index = index, comment = comment, onClick = onOpenReviews)
            }
            item(key = "review-all") {
                ReviewAllCard(count = reviewTotal, onClick = onOpenReviews)
            }
        }
    }
}

@Composable
private fun ReviewCard(index: Int, comment: BookComment, onClick: () -> Unit) {
    val snippet = reviewSnippet(comment.text)
    Surface(
        modifier = Modifier
            .width(ReviewCardWidth)
            .height(ReviewCardHeight)
            .clickable(onClick = onClick, role = Role.Button)
            .semantics(mergeDescendants = true) {
                contentDescription = "第 ${index + 1} 条短评，读者 ${comment.user}：${snippet}，点击查看全部书评"
            },
        shape = RoundedCornerShape(Radius.sm),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(horizontal = Space.md, vertical = Space.sm),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Filled.Person,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp),
                )
                Text(
                    comment.user.ifBlank { "读者" },
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = Space.sm),
                )
                if (comment.score.isNotBlank()) {
                    Text(
                        "${comment.score} 分",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            Text(
                snippet,
                fontSize = 12.sp,
                lineHeight = 18.sp,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .weight(1f)
                    .padding(top = Space.sm),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(Space.md)) {
                Text(
                    "${comment.digg} 赞",
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (comment.replyCount > 0) {
                    Text(
                        "${comment.replyCount} 回复",
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun ReviewAllCard(count: Int, onClick: () -> Unit) {
    Surface(
        modifier = Modifier
            .width(ReviewAllCardWidth)
            .height(ReviewCardHeight)
            .clickable(onClick = onClick, role = Role.Button)
            .semantics(mergeDescendants = true) {
                contentDescription = "查看全部书评"
            },
        shape = RoundedCornerShape(Radius.sm),
        color = MaterialTheme.colorScheme.primaryContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(
            Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp),
            )
            Text(
                "查看全部",
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = Space.xs),
            )
            Text(
                if (count > 0) "共 $count 条" else "读者书评",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ReviewStateCard(loading: Boolean, onClick: () -> Unit) {
    Surface(
        modifier = Modifier
            .width(ReviewCardWidth)
            .height(ReviewCardHeight)
            .clickable(onClick = onClick, role = Role.Button)
            .semantics(mergeDescendants = true) {
                contentDescription = if (loading) "正在加载读者书评" else "暂无短评，点击查看书评"
            },
        shape = RoundedCornerShape(Radius.sm),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(
            Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(
                Icons.Filled.RateReview,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp),
            )
            Text(
                if (loading) "正在加载读者书评…" else "暂无短评，点此查看",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = Space.sm),
            )
        }
    }
}

/** 上游简介常带 &#10; / <br> / &nbsp;，折成单行再让 Text 按行数截断；空简介交回调用方判断。 */
private fun bookDescription(raw: String): String = raw
    .replace(Regex("&#(?:10|13|xa0|160);?|&nbsp;|<br\\s*/?>", RegexOption.IGNORE_CASE), " ")
    .replace(Regex("\\s+"), " ")
    .trim()

/** 上游短评常带 &#10; / <br> / &nbsp;，折成单行再让 Text 自己按 3 行截断。 */
private fun reviewSnippet(raw: String): String = raw
    .replace(Regex("&#(?:10|13|xa0|160);?|&nbsp;|<br\\s*/?>", RegexOption.IGNORE_CASE), " ")
    .replace(Regex("\\s+"), " ")
    .trim()
    .ifEmpty { "这位读者没有留下文字" }

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
 * 三件事在用户脑子里是同一个问题——「这本书对我来说是什么」，所以仍放同一块；
 * 但整块常驻太占首屏，默认只留一行摘要，点开才露出编辑控件，数据操作一个不丢。
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
    var expanded by remember { mutableStateOf(false) }
    var editingTags by remember { mutableStateOf(false) }
    val summary = buildString {
        append(ShelfItem.labelOf(status))
        append(" · ")
        append(if (rating > 0) "$rating 星" else "未评分")
        if (tags.isNotEmpty()) append(" · ${tags.size} 个标签")
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(0.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column {
            // 折叠态只有这一行：整行 ≥48dp、按钮语义、读屏能播报当前标记摘要。
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .clickable(role = Role.Button) { expanded = !expanded }
                    .semantics(mergeDescendants = true) {
                        contentDescription = "我的标记，$summary，点击${if (expanded) "收起" else "展开"}编辑"
                    }
                    .padding(horizontal = Space.card, vertical = Space.sm),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Space.sm),
            ) {
                Text(
                    text = "我的标记",
                    fontSize = 11.sp,
                    letterSpacing = 1.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = summary,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Icon(
                    imageVector = if (expanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (expanded) {
                DividerLine()
                Column(
                    Modifier.padding(Space.card),
                    verticalArrangement = Arrangement.spacedBy(Space.sm),
                ) {
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
                                modifier = Modifier.size(48.dp),
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
                        TextButton(
                            onClick = { editingTags = true },
                            modifier = Modifier.heightIn(min = 48.dp),
                        ) {
                            Text(if (tags.isEmpty()) "加标签" else "改标签", fontSize = 11.sp)
                        }
                    }
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
