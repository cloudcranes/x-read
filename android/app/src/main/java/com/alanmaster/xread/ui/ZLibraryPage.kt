package com.alanmaster.xread.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.alanmaster.xread.data.ZLibraryBook
import com.alanmaster.xread.data.ZLibrarySession

/** Z-Library 主 Tab：搜索、登录、下载 EPUB 与本地导入。 */
@Composable
fun ZLibraryPage(
    results: List<ZLibraryBook>,
    loading: Boolean,
    moreLoading: Boolean,
    hasNext: Boolean,
    error: String,
    query: String,
    session: ZLibrarySession?,
    loginLoading: Boolean,
    importing: String,
    onSearch: (String) -> Unit,
    onLoadMore: () -> Unit,
    onLogin: (String, String) -> Unit,
    onLogout: () -> Unit,
    onImport: (ZLibraryBook) -> Unit,
    onPickLocal: () -> Unit,
) {
    Scaffold(
        topBar = {
            XReadTopBar(
                title = "Z-Library",
                subtitle = if (session == null) {
                    "电子书搜索 · 下载 EPUB · 导入书架"
                } else {
                    "已登录 · 下载 EPUB · 导入书架"
                },
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            ZLibrarySection(
                results = results,
                loading = loading,
                moreLoading = moreLoading,
                hasNext = hasNext,
                error = error,
                query = query,
                session = session,
                loginLoading = loginLoading,
                importing = importing,
                onSearch = onSearch,
                onLoadMore = onLoadMore,
                onLogin = onLogin,
                onLogout = onLogout,
                onImport = onImport,
                onPickLocal = onPickLocal,
            )
        }
    }
}
