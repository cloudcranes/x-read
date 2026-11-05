package com.alanmaster.xread.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.alanmaster.xread.data.Book

/**
 * 听书榜页（发现页「官方听书」进来）。
 *
 * 上游只给一页（offset 再加就 SERVICE_ERROR），所以没有「加载更多」——
 * 按钮点了没反应比没有按钮更糟。
 *
 * 行右侧的数字是**在听人数**（上游 `listen_count`），不是「在读」；
 * 听书和读小说是两个池子，混着说会误导。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AudioFeedScreen(
    books: List<Book>,
    loading: Boolean,
    error: String,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onOpen: (Book) -> Unit,
) {
    Scaffold(
        topBar = {
            XReadTopBar(
                title = "官方听书",
                subtitle = if (books.isEmpty()) "主播朗读的有声书" else "${books.size} 本可收听",
                onBack = onBack,
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            when {
                loading && books.isEmpty() -> Loading()
                error.isNotBlank() && books.isEmpty() ->
                    EmptyHint(error, actionLabel = "重试", onAction = onRetry)
                books.isEmpty() ->
                    EmptyHint("暂时没有可收听的有声书，稍后再来看看")
                else -> LazyColumn {
                    itemsIndexed(books, key = { _, b -> b.bookId }) { index, book ->
                        RankRow(
                            rank = index + 1,
                            title = book.title,
                            coverUrl = book.coverUrl,
                            author = book.author,
                            category = book.category,
                            score = book.score,
                            status = book.status,
                            readCount = book.readCount,
                            countLabel = "在听",
                            onClick = { onOpen(book) },
                        )
                        DividerLine()
                    }
                }
            }
        }
    }
}
