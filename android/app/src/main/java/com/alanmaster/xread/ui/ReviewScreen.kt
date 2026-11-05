package com.alanmaster.xread.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.alanmaster.xread.data.BookComment
import com.alanmaster.xread.data.CommentReply

/**
 * 书评页。
 *
 * 结构：**顶栏 → 汇总数字块 → 书评流 → 加载更多**。
 *
 * v2 调整：
 * - 汇总从「一句灰字 + 若干「· xx」」改成三个**数字块**（[MetaStat]）：
 *   点评人数 / 书评条数 / 已载入。数字有独立的视觉锚点，扫一眼就知道规模。
 * - 点赞、回复数从 emoji（👍💬）换成 Material 图标 + 数字：
 *   emoji 在不同系统上字形不同、基线不齐，且在深色底上偏色。
 * - 长评仍收成 6 行，可展开/收起（上游的热评动辄两三百字，全展开会占满一屏）。
 * - 翻页仍然只靠 [onMore]：`hasMore` 为真时列表末尾自动续一页，
 *   手动点「加载更多」是它的兜底。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReviewScreen(
    bookTitle: String,
    comments: List<BookComment>,
    total: Int,
    raters: Int,
    context: String,
    loading: Boolean,
    hasMore: Boolean,
    onBack: () -> Unit,
    onMore: () -> Unit,
    onToggleCommentDigg: (commentId: String) -> Unit = {},
    onToggleReplyDigg: (commentId: String, replyId: String) -> Unit = { _, _ -> },
    onLoadReplies: (commentId: String) -> Unit = {},
    onLoadMoreReplies: (commentId: String) -> Unit = {},
) {
    val listState = rememberLazyListState()

    Scaffold(
        topBar = {
            XReadTopBar(
                title = "书评",
                subtitle = bookTitle,
                onBack = onBack,
            )
        }
    ) { padding ->
        // 同一页数只发一次续取：放在 lazy item 里会随滚出组合被销毁而反复触发
        var moreRequested by remember { mutableIntStateOf(-1) }
        Column(Modifier.fillMaxSize().padding(padding)) {
            CommentSummary(context, raters, total, comments.size)
            when {
                loading && comments.isEmpty() -> Loading()
                comments.isEmpty() -> EmptyHint("暂无书评")
                else -> LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = Space.xxl),
                ) {
                    // key 用「下标 + id」：上游给了重复或空 id 也不撞 key
                    itemsIndexed(comments, key = { i, c -> "$i-${c.id}" }) { _, comment ->
                        CommentRow(
                            comment = comment,
                            onToggleCommentDigg = onToggleCommentDigg,
                            onToggleReplyDigg = onToggleReplyDigg,
                            onLoadReplies = onLoadReplies,
                            onLoadMoreReplies = onLoadMoreReplies,
                        )
                        DividerLine()
                    }
                    item {
                        LaunchedEffect(comments.size, loading, hasMore) {
                            if (loading || !hasMore) return@LaunchedEffect
                            if (moreRequested == comments.size) return@LaunchedEffect
                            moreRequested = comments.size
                            onMore()
                        }
                        Box(
                            Modifier.fillMaxWidth().padding(Space.xl),
                            contentAlignment = Alignment.Center,
                        ) {
                            when {
                                loading -> Text(
                                    "加载中…", fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                hasMore -> TextButton(onClick = onMore) { Text("加载更多") }
                                else -> Text(
                                    "已全部加载 · 共 ${comments.size} 条", fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
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
 * 顶部汇总：三个数字块。
 *
 * 上游自己给了「19.5万人点评」这种口径，优先用它做点评人数的展示文案，
 * 缺了再按数字拼；条数与已载入数用 [formatCount] 压缩，头部计数不撑破一行。
 */
@Composable
private fun CommentSummary(context: String, raters: Int, total: Int, loaded: Int) {
    val headline = when {
        context.isNotBlank() -> context
        raters > 0 -> "${formatCount(raters)}人点评"
        else -> ""
    }
    if (headline.isBlank() && total <= 0) return
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = Space.gutter, vertical = Space.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (headline.isNotBlank()) {
            Text(
                text = headline,
                fontSize = 15.sp,
                lineHeight = 22.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
        }
        if (total > 0) {
            MetaStat(value = formatCount(total), label = "条书评")
            MetaStatDivider(Modifier.padding(horizontal = Space.md))
        }
        if (loaded > 0) {
            MetaStat(value = "$loaded", label = "已载入")
        }
    }
}

/** 一条书评：头像 + 用户名 + 评分 + 正文 + 点赞/回复/时间。 */
@Composable
private fun CommentRow(
    comment: BookComment,
    onToggleCommentDigg: (String) -> Unit,
    onToggleReplyDigg: (String, String) -> Unit,
    onLoadReplies: (String) -> Unit,
    onLoadMoreReplies: (String) -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = Space.gutter, vertical = Space.md),
        horizontalArrangement = Arrangement.spacedBy(Space.md),
    ) {
        Avatar(comment.avatar, comment.user)
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = comment.user,
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (comment.isAuthor) AuthorBadge()
                Box(Modifier.weight(1f))
                if (comment.score.isNotBlank()) {
                    StarLabel(comment.score)
                }
            }
            // 长书评先收成 6 行：这本书的热评动辄两三百字，全展开会把一屏占满
            var expanded by remember(comment.id) { mutableStateOf(false) }
            var overflow by remember(comment.id) { mutableStateOf(false) }
            Text(
                text = comment.text,
                fontSize = 13.5.sp,
                lineHeight = 21.sp,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = if (expanded) Int.MAX_VALUE else COLLAPSED_LINES,
                overflow = TextOverflow.Ellipsis,
                onTextLayout = { if (!expanded) overflow = it.hasVisualOverflow },
                modifier = Modifier.padding(top = Space.xs),
            )
            if (overflow || expanded) {
                Text(
                    text = if (expanded) "收起" else "展开",
                    fontSize = 12.sp,
                    lineHeight = 17.sp,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .padding(top = 2.dp)
                        .clickable { expanded = !expanded },
                )
            }
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(top = Space.sm),
                horizontalArrangement = Arrangement.spacedBy(Space.md),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                StatChip(
                    icon = Icons.Filled.ThumbUp,
                    text = "${comment.digg}",
                    tint = if (comment.userLiked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    onClick = { onToggleCommentDigg(comment.id) },
                )
                if (comment.replyCount > 0) {
                    StatChip(
                        icon = Icons.Filled.ChatBubbleOutline,
                        text = "${comment.replyCount}",
                        onClick = if (!comment.repliesLoaded) ({ onLoadReplies(comment.id) }) else null,
                    )
                }
                val time = relativeTime(comment.createdAt)
                if (time.isNotEmpty()) {
                    Text(
                        text = time,
                        fontSize = 11.sp,
                        lineHeight = 15.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (comment.replies.isNotEmpty() || comment.repliesLoading || comment.repliesLoaded) {
                ReplyList(
                    comment = comment,
                    onToggleReplyDigg = { rId -> onToggleReplyDigg(comment.id, rId) },
                    onLoadReplies = { onLoadReplies(comment.id) },
                    onLoadMoreReplies = { onLoadMoreReplies(comment.id) },
                )
            }
        }
    }
}

/**
 * 评论楼中楼回复列表。支持按需展开全量分页加载。
 */
@Composable
private fun ReplyList(
    comment: BookComment,
    onToggleReplyDigg: (replyId: String) -> Unit,
    onLoadReplies: () -> Unit,
    onLoadMoreReplies: () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(Radius.sm),
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = Space.sm),
    ) {
        Column(
            Modifier.padding(horizontal = Space.md, vertical = Space.sm),
            verticalArrangement = Arrangement.spacedBy(Space.sm),
        ) {
            comment.replies.forEach { r ->
                ReplyRow(
                    reply = r,
                    onToggleDigg = { onToggleReplyDigg(r.id) },
                )
            }
            if (comment.repliesLoading) {
                Text(
                    text = "回复加载中…",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 4.dp),
                )
            } else if (!comment.repliesLoaded && comment.replyCount > comment.replies.size) {
                Text(
                    text = "展开全部 ${comment.replyCount} 条回复",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .clickable(onClick = onLoadReplies)
                        .padding(vertical = 4.dp),
                )
            } else if (comment.repliesHasMore) {
                Text(
                    text = "加载更多回复",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .clickable(onClick = onLoadMoreReplies)
                        .padding(vertical = 4.dp),
                )
            }
        }
    }
}

/** 一条回复：用户名 + 作者标 + 回复对象 + 正文 + 赞数 + 时间。 */
@Composable
private fun ReplyRow(
    reply: CommentReply,
    onToggleDigg: () -> Unit = {},
) {
    Row(horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
        Text(
            text = reply.user,
            fontSize = 12.sp,
            lineHeight = 18.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.primary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.width(64.dp),
        )
        if (reply.isAuthor) AuthorBadge()
        Column(Modifier.weight(1f)) {
            if (reply.replyToUser.isNotBlank()) {
                Text(
                    text = "回复 @${reply.replyToUser}:",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 2.dp),
                )
            }
            Text(
                text = reply.text,
                fontSize = 12.5.sp,
                lineHeight = 18.sp,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(Space.md),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(top = 2.dp),
            ) {
                StatChip(
                    icon = Icons.Filled.ThumbUp,
                    text = "${reply.digg}",
                    tint = if (reply.userLiked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    onClick = onToggleDigg,
                )
                val time = relativeTime(reply.createdAt)
                if (time.isNotEmpty()) {
                    Text(
                        text = time,
                        fontSize = 10.5.sp,
                        lineHeight = 15.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/** 「作者」徽标：极浅的强调底 + 主色字。 */
@Composable
private fun AuthorBadge() {
    Surface(
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
        shape = RoundedCornerShape(Radius.sm),
        modifier = Modifier.padding(start = Space.xs),
    ) {
        Text(
            text = "作者",
            fontSize = 10.sp,
            lineHeight = 14.sp,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = Space.xs, vertical = 1.dp),
        )
    }
}

/**
 * 统计小标：图标 + 数字。支持点击交互与主色高亮。
 */
@Composable
private fun StatChip(
    icon: ImageVector,
    text: String,
    tint: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    onClick: (() -> Unit)? = null,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        modifier = if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(13.dp),
        )
        Text(
            text = text,
            fontSize = 11.sp,
            lineHeight = 15.sp,
            color = tint,
        )
    }
}

/**
 * 评分：星形图标 + 十分制数字。
 *
 * 旧实现用「★☆」字符拼星号，空心星在部分字体上会掉出基线。
 * 现在图标是矢量图形（不会掉基线），数字另起一列，两者都读得出来。
 */
@Composable
private fun StarLabel(score: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Icon(
            imageVector = Icons.Filled.Star,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.tertiary,
            modifier = Modifier.size(13.dp),
        )
        Text(
            text = score,
            fontSize = 11.sp,
            lineHeight = 15.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** 头像：34dp 圆形；无图时用用户名首字。 */
@Composable
private fun Avatar(url: String, name: String) {
    Box(
        Modifier
            .size(34.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
        contentAlignment = Alignment.Center,
    ) {
        if (url.isNotBlank()) {
            AsyncImage(
                model = url,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Text(
                text = name.take(1),
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * 52091 → 5.2万，避免头部计数撑破一行。
 *
 * 固定 Locale.US：默认 Locale 在 ar/fa/bn 下会把数字换成别的记数系统
 * （「٥.٢万」），正文是中文时这套数字看着就像乱码。
 */
internal fun formatCount(value: Int): String = when {
    value >= 100_000_000 -> String.format(java.util.Locale.US, "%.1f亿", value / 100_000_000.0)
    value >= 10_000 -> String.format(java.util.Locale.US, "%.1f万", value / 10_000.0)
    else -> value.toString()
}

private fun relativeTime(epochSeconds: Long): String {
    if (epochSeconds <= 0) return ""
    val minutes = (System.currentTimeMillis() / 1000 - epochSeconds) / 60
    return when {
        minutes < 1 -> "刚刚"
        minutes < 60 -> "${minutes} 分钟前"
        minutes < 60 * 24 -> "${minutes / 60} 小时前"
        minutes < 60 * 24 * 30 -> "${minutes / (60 * 24)} 天前"
        else -> "${minutes / (60 * 24 * 30)} 个月前"
    }
}

private const val MAX_INLINE_REPLIES = 3
private const val COLLAPSED_LINES = 6
