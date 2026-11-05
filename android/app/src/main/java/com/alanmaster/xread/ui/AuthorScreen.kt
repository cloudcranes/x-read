package com.alanmaster.xread.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.alanmaster.xread.data.AuthorProfile
import com.alanmaster.xread.data.Book

/**
 * 作者主页。头部是头像 + 名字 + 粉丝数，下面是作品网格。
 *
 * v2 调整：
 * - 头部用**两个数字块**（粉丝 / 作品）替代「12.3 万粉丝 · 4 部作品」这一行串：
 *   数字有独立的视觉锚点，不必逐字去数「12.3」和「4」哪个更重要。
 * - 头像加一圈发丝线，和纸底分开（无头像时是个空色块，容易被当成图片占位）。
 * - 简介最多 5 行，多了截断——作者简介经常是长篇自述，全展开会把作品列表顶到屏外。
 * - 作品网格用 [BookCard]，封面比例保持 3:4，文字让位。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AuthorScreen(
    author: AuthorProfile?,
    loading: Boolean,
    onBack: () -> Unit,
    onOpen: (Book) -> Unit,
) {
    Scaffold(
        topBar = {
            XReadTopBar(
                title = author?.name?.ifBlank { "作者主页" } ?: "作者主页",
                onBack = onBack,
            )
        }
    ) { padding ->
        when {
            loading && author == null ->
                Box(Modifier.fillMaxSize().padding(padding)) { Loading() }
            author == null ->
                Box(Modifier.fillMaxSize().padding(padding)) { EmptyHint("作者信息不可用") }
            else -> LazyVerticalGrid(
                columns = GridCells.Adaptive(104.dp),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(
                    start = Space.gutter, end = Space.gutter,
                    top = Space.md, bottom = Space.xxl,
                ),
                horizontalArrangement = Arrangement.spacedBy(Space.md),
                verticalArrangement = Arrangement.spacedBy(Space.lg),
            ) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    AuthorHeader(author)
                }
                item(span = { GridItemSpan(maxLineSpan) }) {
                    SectionHeader("作品", "${author.works.size} 本")
                }
                items(author.works, key = { it.bookId }) { book ->
                    BookCard(book, onClick = { onOpen(book) })
                }
            }
        }
    }
}

/** 作者头部：头像 + 名字 + 粉丝/作品数字块 + 简介。 */
@Composable
private fun AuthorHeader(author: AuthorProfile) {
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(60.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    .padding(1.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.background),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                    contentAlignment = Alignment.Center,
                ) {
                    if (author.avatar.isNotBlank()) {
                        AsyncImage(
                            model = author.avatar,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                        )
                    } else {
                        Text(
                            text = author.name.take(1),
                            fontSize = 22.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            Column(Modifier.padding(start = Space.md)) {
                Text(
                    text = author.name.ifBlank { "未知作者" },
                    fontSize = 18.sp,
                    lineHeight = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(
                    Modifier.padding(top = Space.xs),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (author.fans > 0) {
                        MetaStat(value = formatCount(author.fans), label = "粉丝")
                        MetaStatDivider(Modifier.padding(horizontal = Space.md))
                    }
                    MetaStat(value = "${author.works.size}", label = "作品")
                }
            }
        }
        if (author.description.isNotBlank()) {
            Text(
                text = author.description,
                fontSize = 13.sp,
                lineHeight = 20.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 5,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = Space.md),
            )
        }
    }
}

/**
 * 中文习惯的计数：12,345,678 应显示「1234.6 万」而不是「1000 万」。
 * 旧实现把亿位直接截断再补三个零，误差可达一半。
 */
private fun formatCount(value: Long): String = when {
    value >= 100_000_000 -> "%.1f 亿".format(java.util.Locale.US, value / 100_000_000.0)
    value >= 10_000 -> "%.1f 万".format(java.util.Locale.US, value / 10_000.0)
    else -> value.toString()
}
