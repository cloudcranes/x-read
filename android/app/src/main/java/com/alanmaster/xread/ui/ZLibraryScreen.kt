package com.alanmaster.xread.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alanmaster.xread.data.ZLibraryBook
import com.alanmaster.xread.data.ZLibraryImportMapper
import com.alanmaster.xread.data.ZLibrarySession

/**
 * Z-Library 电子书：搜索 → 下载 EPUB → 导入书架。
 *
 * 权限边界（实测）：搜索与详情走 EAPI，**不需要登录**；下载文件本体与在线阅读
 * 需要用户自己的 z-library 账号，没登录时下载会回 401。所以登录入口只影响「导入」，
 * 不挡搜索——先让用户能搜到书，再提示登录。
 */
@Composable
fun ZLibrarySection(
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
    onPickLocal: () -> Unit = {},
) {
    var keyword by rememberSaveable { mutableStateOf(query) }
    var showLogin by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = Space.gutter),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onPickLocal) { Text("本地导入", fontSize = 13.sp) }
            TextButton(onClick = { showLogin = true }) {
                Text(if (session == null) "登录" else "账号", fontSize = 13.sp)
            }
        }
            Row(
                Modifier.fillMaxWidth().padding(horizontal = Space.gutter, vertical = Space.sm),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Space.sm),
            ) {
                OutlinedTextField(
                    value = keyword,
                    onValueChange = { keyword = it },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    placeholder = { Text("书名或作者", fontSize = 14.sp) },
                    textStyle = MaterialTheme.typography.bodyMedium,
                    shape = RoundedCornerShape(Radius.none),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { onSearch(keyword) }),
                    trailingIcon = {
                        IconButton(onClick = { onSearch(keyword) }, modifier = Modifier.size(48.dp)) {
                            Icon(Icons.Filled.Search, contentDescription = "搜索", modifier = Modifier.size(20.dp))
                        }
                    },
                )
                Button(
                    onClick = { onSearch(keyword) },
                    modifier = Modifier.heightIn(min = 48.dp),
                    shape = RoundedCornerShape(Radius.sm),
                ) { Text("搜索", fontSize = 14.sp) }
            }

            if (error.isNotBlank()) {
                Text(
                    text = error,
                    modifier = Modifier.padding(horizontal = Space.gutter, vertical = Space.xs),
                    fontSize = 12.sp,
                    lineHeight = 18.sp,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            when {
                loading -> Box(Modifier.fillMaxWidth().padding(Space.section), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(modifier = Modifier.size(28.dp))
                }

                results.isEmpty() -> Box(Modifier.fillMaxSize().padding(Space.gutter)) {
                    Text(
                        text = if (query.isBlank()) {
                            "搜索 Z-Library 的电子书。导入后按本地书阅读，断网也能看。\n\n" +
                                "搜索和详情不需要登录；下载文件要登录自己的 Z-Library 账号。"
                        } else {
                            "没有找到「$query」。换个书名或作者再试试。"
                        },
                        fontSize = 13.sp,
                        lineHeight = 21.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                else -> LazyColumn(
                    Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = Space.navFloat),
                    verticalArrangement = Arrangement.spacedBy(Space.sm),
                ) {
                    items(results, key = { it.id }) { book ->
                        ZLibraryRow(
                            book = book,
                            importing = importing == book.id,
                            busy = importing.isNotBlank() && importing != book.id,
                            onImport = { onImport(book) },
                        )
                    }
                    if (hasNext) {
                        item {
                            Box(Modifier.fillMaxWidth().padding(Space.md), contentAlignment = Alignment.Center) {
                                if (moreLoading) {
                                    CircularProgressIndicator(modifier = Modifier.size(22.dp))
                                } else {
                                    TextButton(onClick = onLoadMore, modifier = Modifier.heightIn(min = 48.dp)) {
                                        Text("加载更多", fontSize = 13.sp)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

    if (showLogin) {
        ZLibraryLoginDialog(
            session = session,
            loading = loginLoading,
            error = error,
            onDismiss = { showLogin = false },
            onLogin = { email, password -> onLogin(email, password) },
            onLogout = {
                onLogout()
                showLogin = false
            },
        )
    }
}

@Composable
private fun ZLibraryRow(book: ZLibraryBook, importing: Boolean, busy: Boolean, onImport: () -> Unit) {
    val epub = book.extension.equals("epub", ignoreCase = true)
    Row(
        Modifier.fillMaxWidth().padding(horizontal = Space.gutter, vertical = Space.xs),
        horizontalArrangement = Arrangement.spacedBy(Space.md),
    ) {
        BookCover(title = book.title, coverUrl = book.coverUrl, modifier = Modifier.width(64.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = book.title,
                fontSize = 14.sp,
                lineHeight = 20.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = buildString {
                    if (book.author.isNotBlank()) append(book.author)
                    if (book.year > 0) { if (isNotEmpty()) append(" · "); append(book.year) }
                    if (book.language.isNotBlank()) { if (isNotEmpty()) append(" · "); append(book.language) }
                },
                fontSize = 11.sp,
                lineHeight = 16.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = buildString {
                    append(book.extension.uppercase().ifBlank { "未知格式" })
                    if (book.fileSizeText.isNotBlank()) append(" · ${book.fileSizeText}")
                    if (book.pages > 0) append(" · ${book.pages} 页")
                },
                fontSize = 11.sp,
                lineHeight = 16.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            val intro = remember(book.description) { ZLibraryImportMapper.plainDescription(book.description) }
            if (intro.isNotBlank()) {
                Text(
                    text = intro,
                    fontSize = 11.sp,
                    lineHeight = 16.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            Row(Modifier.padding(top = Space.xs), verticalAlignment = Alignment.CenterVertically) {
                Button(
                    onClick = onImport,
                    enabled = epub && !importing && !busy,
                    modifier = Modifier.heightIn(min = 44.dp),
                    shape = RoundedCornerShape(Radius.sm),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                ) {
                    if (importing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary,
                        )
                    } else {
                        Text(if (epub) "导入" else "仅支持 EPUB", fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun ZLibraryLoginDialog(
    session: ZLibrarySession?,
    loading: Boolean,
    error: String,
    onDismiss: () -> Unit,
    onLogin: (String, String) -> Unit,
    onLogout: () -> Unit,
) {
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (session == null) "登录 Z-Library" else "Z-Library 账号", fontSize = 16.sp) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Space.sm)) {
                if (session == null) {
                    Text(
                        "只用来下载电子书；搜索和详情不需要登录。账号只存在本机，用来换 remix 会话 cookie。",
                        fontSize = 12.sp,
                        lineHeight = 18.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedTextField(
                        value = email,
                        onValueChange = { email = it },
                        label = { Text("邮箱", fontSize = 12.sp) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(Radius.none),
                    )
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text("密码", fontSize = 12.sp) },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(Radius.none),
                    )
                    if (error.isNotBlank()) {
                        Text(error, fontSize = 12.sp, color = MaterialTheme.colorScheme.error)
                    }
                } else {
                    Text("已登录，用户 id：${session.userId}", fontSize = 13.sp)
                    Text(
                        "退出后不影响已导入的书，只是不能再下载新的。",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = {
            if (session == null) {
                TextButton(onClick = { onLogin(email, password) }, enabled = !loading) {
                    Text(if (loading) "登录中…" else "登录", fontSize = 13.sp)
                }
            } else {
                TextButton(onClick = onLogout) { Text("退出登录", fontSize = 13.sp) }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("关闭", fontSize = 13.sp) }
        },
    )
}
