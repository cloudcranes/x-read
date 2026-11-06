package com.alanmaster.xread.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.alanmaster.xread.data.Book
import com.alanmaster.xread.data.BookStatus
import com.alanmaster.xread.data.ReadCountFormat

/* ================================================================== *
 * 共享组件库。
 *
 * 页面只允许用这里的组件拼装；不要在页面里另写一套按钮/卡片样式，
 * 否则间距与色值会再次各自为政。
 * ================================================================== */

/* --------------------------- 封面 --------------------------- */

/** 封面占位：无图时用书名首字，底色是 surfaceContainerHigh 而不是纯灰。 */
@Composable
fun BookCover(
    title: String,
    coverUrl: String,
    modifier: Modifier = Modifier,
    corner: Dp = Radius.sm,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(corner))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
        contentAlignment = Alignment.Center,
    ) {
        if (coverUrl.isNotBlank()) {
            AsyncImage(
                model = coverUrl,
                contentDescription = title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Text(
                text = title.take(1).ifEmpty { "文" },
                fontSize = 22.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/* --------------------------- 书籍卡片 --------------------------- */

/**
 * 网格里的书籍卡片：**封面 + 书名 + 一行元信息**。
 *
 * 元信息只有一行、只放两段（作者 · 章数），第二行留给「更新章数」「分组」这类
 * 状态标——信息密度靠分行控制，不靠缩小字号。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun BookCard(
    book: Book,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
) {
    Column(
        modifier = modifier.then(
            if (onLongClick == null) Modifier.clickable(onClick = onClick)
            else Modifier.combinedClickable(onClick = onClick, onLongClick = onLongClick)
        ),
    ) {
        BookCover(
            title = book.title,
            coverUrl = book.coverUrl,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(0.70f),
        )
        Text(
            text = book.title,
            fontWeight = FontWeight.Medium,
            fontSize = 14.sp,
            lineHeight = 20.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = Space.sm),
        )
        val meta = listOfNotNull(
            book.author.takeIf { it.isNotBlank() },
            if (book.chapterCount > 0) "${book.chapterCount}章" else null,
        ).joinToString(" · ")
        if (meta.isNotEmpty()) {
            Text(
                text = meta,
                fontSize = 12.sp,
                lineHeight = 18.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * 书架里的横排卡片：封面在左，右侧是书名、作者与状态行。
 *
 * 书架是「我的书」而非「商品列表」，横排能在同样高度下多给 2–3 倍信息量：
 * 封面只占 68dp，右侧留给「读到第几段 / 有几章新 / 属于哪个分组」。
 *
 * 进度用**段落数**而不是章数：书架数据里只有当前章 id 与段落下标，
 * 拿不到当前章在目录里的序号，写「第 N 章」会是假数据。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ShelfBookCard(
    book: Book,
    modifier: Modifier = Modifier,
    /** 主状态：有新章 / 分组 / 读到第几段。 */
    status: String? = null,
    /** 状态标是否用强调色（更新用主色，分组用弱色）。 */
    statusEmphasized: Boolean = false,
    /** 次要状态：最近阅读时间等。 */
    subStatus: String? = null,
    /** 书籍信息：完结/连载、总章数。 */
    meta: String? = null,
    /** 阅读进度 0..1；null 表示还没开始读。 */
    progress: Float? = null,
    /** 点「继续」直接进正文；null 时不显示入口。 */
    onContinue: (() -> Unit)? = null,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
) {
    Surface(
        modifier = modifier.then(
            if (onLongClick == null) Modifier.clickable(onClick = onClick)
            else Modifier.combinedClickable(onClick = onClick, onLongClick = onLongClick)
        ),
        shape = RoundedCornerShape(Radius.none),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(Modifier.padding(Space.card)) {
            BookCover(
                title = book.title,
                coverUrl = book.coverUrl,
                modifier = Modifier
                    .width(68.dp)
                    .aspectRatio(0.70f),
            )
            Column(
                Modifier
                    .weight(1f)
                    .padding(start = Space.md),
                verticalArrangement = Arrangement.spacedBy(Space.xs),
            ) {
                Text(
                    text = book.title,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp,
                    lineHeight = 20.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (book.author.isNotBlank()) {
                    Text(
                        text = book.author,
                        fontSize = 12.sp,
                        lineHeight = 16.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                            if (!meta.isNullOrBlank()) {
                Text(
                    text = meta,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
if (!status.isNullOrBlank()) {
                    Text(
                        text = status,
                        fontSize = 11.sp,
                        fontWeight = if (statusEmphasized) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (statusEmphasized) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (!subStatus.isNullOrBlank() || onContinue != null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = subStatus.orEmpty(),
                            modifier = Modifier.weight(1f),
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (onContinue != null) {
                            Text(
                                text = "继续",
                                modifier = Modifier
                                    .clickable(onClick = onContinue)
                                    .padding(start = Space.sm, top = Space.xxs, bottom = Space.xxs),
                                fontSize = 11.sp,
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
                            .height(2.dp)
                            .clip(RoundedCornerShape(1.dp)),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.outlineVariant,
                    )
                }
            }
        }
    }
}

/* --------------------------- 网格与列表 --------------------------- */

/**
 * 书籍网格。列宽用 `GridCells.Adaptive` 而不是固定列数：
 * 平板/折叠屏上自动变 3–4 列，手机上 3 列，不会出现一屏只有两本还左右留白。
 */
@Composable
fun BookGrid(
    books: List<Book>,
    modifier: Modifier = Modifier,
    onClick: (Book) -> Unit,
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(108.dp),
        modifier = modifier,
        contentPadding = PaddingValues(
            start = Space.gutter, end = Space.gutter,
            top = Space.sm, bottom = Space.xxl,
        ),
        horizontalArrangement = Arrangement.spacedBy(Space.md),
        verticalArrangement = Arrangement.spacedBy(Space.lg),
    ) {
        items(books, key = { it.bookId }) { book ->
            BookCard(book, onClick = { onClick(book) })
        }
    }
}

/* --------------------------- 芯片 --------------------------- */

/** 单选芯片行：榜种、分类、排序等横向可滚动的筛选项。 */
@Composable
fun ChipRow(options: List<Pair<String, () -> Unit>>, selected: String) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = Space.gutter),
        horizontalArrangement = Arrangement.spacedBy(Space.sm),
    ) {
        items(options) { (label, action) ->
            SelectChip(label = label, selected = label == selected, onClick = action)
        }
    }
}

/**
 * 筛选芯片。
 *
 * 不用 Material 的 `FilterChip`：它自带 8dp 圆角与 32dp 最小高度，
 * 一屏里会与封面（10dp）、抽屉（20dp）凑成三档圆角，且高度压不到设计要的 28dp。
 * v3 用直角 + 发丝线，选中态是朱红实底——与「刊物」语言一致。
 */
@Composable
fun SelectChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .height(28.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(Radius.none),
        color = if (selected) MaterialTheme.colorScheme.primary
        else Color.Transparent,
        border = BorderStroke(
            1.dp,
            if (selected) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.outline,
        ),
    ) {
        Box(
            Modifier.padding(horizontal = 10.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = label,
                fontSize = 12.sp,
                lineHeight = 16.sp,
                fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
                color = if (selected) MaterialTheme.colorScheme.onPrimary
                else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
    }
}

/** 标签徽标：排名、类型、新章数等。 */
@Composable
fun Tag(
    text: String,
    modifier: Modifier = Modifier,
    emphasized: Boolean = false,
    solid: Boolean = false,
) {
    val bg = when {
        solid -> MaterialTheme.colorScheme.primary
        emphasized -> MaterialTheme.colorScheme.primaryContainer
        else -> MaterialTheme.colorScheme.surfaceContainerHigh
    }
    val fg = when {
        solid -> MaterialTheme.colorScheme.onPrimary
        emphasized -> MaterialTheme.colorScheme.onPrimaryContainer
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(Radius.sm),
        color = bg,
    ) {
        Text(
            text = text,
            fontSize = 11.sp,
            lineHeight = 15.sp,
            fontWeight = FontWeight.Medium,
            color = fg,
            modifier = Modifier.padding(horizontal = Space.sm, vertical = 2.dp),
        )
    }
}

/* --------------------------- 数字块 --------------------------- */

/**
 * 元信息数字块：大号数字 + 小号标签。
 *
 * 旧实现把评分、字数、章数用「·」串成一行，扫读时抓不到「9.1 分」这个数。
 * 拆成三列等宽数字块后，每个数字有独立的视觉锚点，竖发丝线分隔。
 */
@Composable
fun MetaStat(
    value: String,
    label: String,
    modifier: Modifier = Modifier,
    /** 数字是否用强调色（评分等需要被看见的值）。 */
    emphasized: Boolean = false,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = value,
            fontSize = 16.sp,
            lineHeight = 22.sp,
            fontWeight = FontWeight.Bold,
            color = if (emphasized) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
        )
        Text(
            text = label,
            fontSize = 11.sp,
            lineHeight = 15.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
    }
}

/** 数字块之间的竖发丝线。 */
@Composable
fun MetaStatDivider(modifier: Modifier = Modifier) {
    Box(
        modifier
            .width(1.dp)
            .height(28.dp)
            .background(MaterialTheme.colorScheme.outlineVariant),
    )
}

/* --------------------------- 榜单行 --------------------------- */

/**
 * 榜单行：序号 + 封面 + 书名/元信息 + 右侧标记。
 *
 * 前三名序号用强调色衬线，其余用次要色；封面 42×56dp。
 * 行间只放发丝线，不给卡片底色——榜单是长列表，卡片会让页面碎掉。
 */
@Composable
fun RankRow(
    rank: Int,
    title: String,
    coverUrl: String,
    author: String,
    category: String,
    status: BookStatus,
    readCount: Long,
    modifier: Modifier = Modifier,
    /** 评分（如 "9.0"）；空串不显示。听书榜给这个，普通榜单不给。 */
    score: String = "",
    /** 右侧人数的量词：读书是「在读」，听书是「在听」。 */
    countLabel: String = "在读",
    onClick: () -> Unit,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = Space.gutter, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 序号：前三名朱红衬线，其余三级灰。宽度固定，让所有行左边界对齐。
        Text(
            text = "$rank",
            fontSize = 17.sp,
            lineHeight = 22.sp,
            fontFamily = Serif,
            fontWeight = FontWeight.Bold,
            color = if (rank <= 3) MaterialTheme.colorScheme.primary else ink3,
            textAlign = TextAlign.End,
            modifier = Modifier.width(26.dp),
        )
        Spacer(Modifier.width(Space.md))
        BookCover(
            title = title,
            coverUrl = coverUrl,
            modifier = Modifier
                .width(42.dp)
                .aspectRatio(0.75f),
        )
        Column(
            Modifier
                .weight(1f)
                .padding(horizontal = Space.md),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = title,
                fontSize = 15.sp,
                lineHeight = 20.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = listOfNotNull(
                    author.takeIf { it.isNotBlank() },
                    category.takeIf { it.isNotBlank() },
                    statusLabel(status),
                ).joinToString(" · "),
                fontSize = 11.sp,
                lineHeight = 16.sp,
                color = ink3,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (score.isNotBlank()) {
                Text(
                    text = "评分 $score",
                    fontSize = 11.sp,
                    lineHeight = 16.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        }
        // 右侧数字列：在读人数右对齐成列，这是整屏能被竖着扫读的关键。
        Column(horizontalAlignment = Alignment.End) {
            if (readCount > 0) {
                Text(
                    text = ReadCountFormat.short(readCount),
                    fontSize = 11.sp,
                    lineHeight = 15.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
                Text(
                    text = countLabel,
                    fontSize = 10.sp,
                    lineHeight = 14.sp,
                    color = ink3,
                    maxLines = 1,
                )
            } else {
                Text(
                    text = "—",
                    fontSize = 11.sp,
                    lineHeight = 15.sp,
                    color = ink3,
                )
            }
        }
    }
}

/** 连载状态的中文标签；未知时返回 null，不猜。 */
internal fun statusLabel(status: BookStatus): String? = when (status) {
    BookStatus.Serial -> "连载"
    BookStatus.Finished -> "完本"
    BookStatus.Unknown -> null
}

/* --------------------------- 阅读页版心 --------------------------- */

/**
 * 阅读页的版心与手势常量。
 *
 * 这三个值决定阅读手感，但散在 Compose 代码里很容易被顺手改掉，
 * 所以收在这里并由 [ReaderChromeTest] 钉住。
 */
object ReaderMetrics {
    /** 正文左右边距。中文竖排感靠足够的留白，行太长反而难读。 */
    val textGutter = 20.dp

    /** 屏幕中央多宽的比例点按才切换 chrome。两侧各留翻页区。 */
    const val centerTapRatio = 0.6f

    /** 底栏动作，顺序即从左到右。 */
    val bottomActions = listOf("目录", "设置", "听书", "夜间")
}

/* --------------------------- 进度细线 --------------------------- */

/**
 * 进度细线：2dp 细线 + 可选百分比文字。
 *
 * 阅读页常驻。旧实现用 Material 的 LinearProgressIndicator，
 * 轨道色跟着主题走，在夜色阅读底上会糊成一条灰带。
 */
@Composable
fun ProgressHairline(
    progress: Float,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    trackColor: Color = MaterialTheme.colorScheme.outlineVariant,
    /** 是否显示百分比文字。 */
    showLabel: Boolean = false,
    label: String? = null,
) {
    val pct = (progress * 100).toInt().coerceIn(0, 100)
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .weight(1f)
                .height(2.dp)
                .background(trackColor),
        ) {
            Box(
                Modifier
                    .fillMaxWidth(progress.coerceIn(0f, 1f))
                    .height(2.dp)
                    .background(color),
            )
        }
        if (showLabel) {
            Text(
                text = label ?: "$pct%",
                fontSize = 11.sp,
                lineHeight = 15.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = Space.sm),
            )
        }
    }
}

/* --------------------------- 分段控制 --------------------------- */

/**
 * 分段控制：一组互斥选项，选中项用 primaryContainer 底 + primary 字。
 *
 * 用于行距（紧/标准/松）、翻页方式（仿真/滑动/无动画）这类 2–3 选一的设置。
 * 不用 Material 的 SegmentedButton：它自带 20dp 圆角，会多出第三档圆角。
 */
@Composable
fun SegmentedRow(
    options: List<String>,
    selected: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(Space.sm),
    ) {
        options.forEach { label ->
            val on = label == selected
            Surface(
                modifier = Modifier
                    .weight(1f)
                    .height(36.dp)
                    .clickable { onSelect(label) },
                shape = RoundedCornerShape(Radius.sm),
                color = if (on) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surface,
                border = BorderStroke(
                    1.dp,
                    if (on) MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                    else MaterialTheme.colorScheme.outlineVariant,
                ),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = label,
                        fontSize = 13.sp,
                        color = if (on) MaterialTheme.colorScheme.onPrimaryContainer
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

/* --------------------------- 色板 --------------------------- */

/**
 * 阅读底色色板：一个 32dp 圆点 + 名称。
 *
 * 选中态是 2dp 强调色外环，不改圆点本身的大小——
 * 改大小会让整行跟着跳动。
 */
@Composable
fun PaletteSwatch(
    label: String,
    background: Color,
    foreground: Color,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .size(32.dp)
                .clip(androidx.compose.foundation.shape.CircleShape)
                .background(background)
                .then(
                    if (selected) Modifier.border(
                        2.dp,
                        MaterialTheme.colorScheme.primary,
                        androidx.compose.foundation.shape.CircleShape,
                    ) else Modifier.border(
                        1.dp,
                        MaterialTheme.colorScheme.outlineVariant,
                        androidx.compose.foundation.shape.CircleShape,
                    ),
                ),
            contentAlignment = Alignment.Center,
        ) {
            Text("文", fontSize = 13.sp, color = foreground)
        }
        Text(
            text = label,
            fontSize = 11.sp,
            lineHeight = 15.sp,
            color = if (selected) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            modifier = Modifier.padding(top = Space.xs),
        )
    }
}

/* --------------------------- 区块标题 --------------------------- */

/**
 * 区块标题：`01` 栏目序号 + 衬线标题 + 右侧动作。
 *
 * 序号是 v3 的刊物骨架：它把长页面切成可数的段落，读者滑到哪儿心里有数。
 * 序号用三级灰 + 1.6sp 字距，不抢标题。
 */
@Composable
fun SectionHeader(
    title: String,
    hint: String = "",
    modifier: Modifier = Modifier,
    /** 栏目序号（"01"、"02"…）。空串则不显示。 */
    index: String = "",
    action: Pair<String, () -> Unit>? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(
                start = Space.gutter, end = Space.sm,
                top = Space.huge, bottom = Space.md,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (index.isNotBlank()) {
            Text(
                text = index,
                fontSize = 10.sp,
                lineHeight = 14.sp,
                letterSpacing = 1.2.sp,
                color = ink3,
            )
            Spacer(Modifier.width(Space.sm))
        }
        Text(
            text = title,
            fontFamily = Serif,
            fontSize = 18.sp,
            lineHeight = 25.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f, fill = false),
        )
        Spacer(Modifier.weight(1f))
        if (action != null) {
            androidx.compose.material3.TextButton(
                onClick = action.second,
                contentPadding = PaddingValues(horizontal = Space.sm),
            ) {
                Text(action.first, fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
            }
        } else if (hint.isNotBlank()) {
            Text(
                text = hint,
                fontSize = 10.sp,
                color = ink3,
            )
        }
    }
}

/** 旧名保留：区块标题（无动作）。 */
@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.padding(horizontal = Space.gutter, vertical = Space.sm),
    )
}

/* --------------------------- 空态与加载 --------------------------- */

/**
 * 空态：图标 + 主文案 + 副文案。
 *
 * 旧实现只有一行灰字，用户看到「暂时取不到推荐」不知道该干嘛；
 * 现在给出下一步的提示。
 */
@Composable
fun EmptyHint(
    text: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(Space.xxl),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = text,
                fontSize = 14.sp,
                lineHeight = 21.sp,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (actionLabel != null && onAction != null) {
                androidx.compose.material3.TextButton(
                    onClick = onAction,
                    modifier = Modifier.padding(top = Space.md),
                ) { Text(actionLabel) }
            }
        }
    }
}

@Composable
fun Loading(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(120.dp),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator(
            color = MaterialTheme.colorScheme.primary,
            strokeWidth = 2.dp,
            modifier = Modifier.size(22.dp),
        )
    }
}

/* --------------------------- 分割线 --------------------------- */

/** 一像素分割线。用 outlineVariant 而不是 outline，避免在米黄底上显脏。 */
@Composable
fun DividerLine(modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(MaterialTheme.colorScheme.outlineVariant),
    )
}

/* --------------------------- 底部弹层 --------------------------- */

/**
 * 统一的底部浮层容器：圆角上沿 + 顶部拖拽条 + 背景用 surfaceContainerLow。
 *
 * 阅读设置、字号面板都用它，避免各处自己拼一个 Column 弹出来。
 */
@Composable
fun BottomSheet(
    modifier: Modifier = Modifier,
    title: String? = null,
    onClose: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(topStart = Radius.xl, topEnd = Radius.xl),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = Elevation.raised,
    ) {
        Column {
            Box(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = Space.sm),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    Modifier
                        .size(width = 32.dp, height = 4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(MaterialTheme.colorScheme.outline),
                )
            }
            if (title != null) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Space.gutter, vertical = Space.xs),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f),
                    )
                    if (onClose != null) {
                        androidx.compose.material3.TextButton(onClick = onClose) {
                            Text("完成", fontSize = 13.sp)
                        }
                    }
                }
            }
            content()
        }
    }
}

/* --------------------------- 按钮 --------------------------- */

/**
 * 主要动作按钮。高度 48dp（触控下限），圆角 Radius.sm。
 *
 * 全应用只有一种主按钮样式：实心 primary。
 */
@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    androidx.compose.material3.Button(
        onClick = onClick,
        modifier = modifier.height(48.dp),
        enabled = enabled,
        shape = RoundedCornerShape(Radius.sm),
    ) {
        Text(text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
    }
}

/** 次要动作按钮：描边，1dp 边框。 */
@Composable
fun SecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    androidx.compose.material3.OutlinedButton(
        onClick = onClick,
        modifier = modifier.height(48.dp),
        enabled = enabled,
        shape = RoundedCornerShape(Radius.sm),
    ) {
        Text(text, fontSize = 14.sp)
    }
}

/* --------------------------- 顶栏 --------------------------- */

/**
 * 统一顶栏。返回键 + 标题 + 右侧动作。
 *
 * 标题默认 16sp SemiBold（不再是 Bold 20sp）：中文标题在 Bold 下笔画会糊成一团，
 * 且顶栏只有一行内容时不该抢正文层级。
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun XReadTopBar(
    title: String,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    subtitle: String? = null,
    actions: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit = {},
) {
    androidx.compose.material3.TopAppBar(
        modifier = modifier,
        title = {
            Column {
                Text(
                    text = title,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (!subtitle.isNullOrBlank()) {
                    Text(
                        text = subtitle,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        },
        navigationIcon = {
            if (onBack != null) {
                androidx.compose.material3.IconButton(onClick = onBack) {
                    androidx.compose.material3.Icon(
                        androidx.compose.material.icons.Icons.AutoMirrored.Filled.ArrowBack,
                        "返回",
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        },
        actions = actions,
        colors = androidx.compose.material3.TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.background,
            titleContentColor = MaterialTheme.colorScheme.onBackground,
        ),
    )
}

val Transparent = Color.Transparent


/**
 * 多选用的勾选圈：未选中是空心圈，选中是主色实心 + 白勾。
 *
 * 独立于卡片：行式与卡片式两种书架布局都要盖同一个标记，样式只有这一份。
 */
@Composable
fun SelectionMark(selected: Boolean, modifier: Modifier = Modifier) {
    val accent = MaterialTheme.colorScheme.primary
    Box(
        modifier = modifier
            .size(22.dp)
            .background(
                if (selected) accent else MaterialTheme.colorScheme.surface,
                CircleShape,
            )
            .border(
                1.dp,
                if (selected) accent else MaterialTheme.colorScheme.outlineVariant,
                CircleShape,
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (selected) {
            Icon(
                Icons.Filled.Check,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
                tint = MaterialTheme.colorScheme.onPrimary,
            )
        }
    }
}
