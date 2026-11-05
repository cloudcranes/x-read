package com.alanmaster.xread.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.alanmaster.xread.data.Book
import com.alanmaster.xread.data.BookCategory

/**
 * 分类页。194 个分类靠滚动找太费劲，所以顶部常驻一个过滤框。
 *
 * v2 调整：
 * - 过滤框换成统一的 48dp 搜索框（[CompactSearchField]），与搜索页、目录页一致；
 * - 分类块的**名字移到封面下方**，不再压在图片上——压在图上的白字在浅色封面上
 *   根本看不清，挪到纸底上对比度才有保证；
 * - 空态区分「没匹配到」和「分类加载失败」，前者提示换词，后者提示返回重试。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryScreen(
    categories: List<BookCategory>,
    loading: Boolean,
    onBack: () -> Unit,
    onPick: (BookCategory) -> Unit,
) {
    Scaffold(
        topBar = {
            XReadTopBar(
                title = "分类",
                subtitle = "${categories.size} 个分类",
                onBack = onBack,
            )
        }
    ) { padding ->
        var filter by remember { mutableStateOf("") }
        val shown = remember(categories, filter) {
            filterCategories(categories, filter)
        }
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            CompactSearchField(
                value = filter,
                onValueChange = { filter = it },
                placeholder = "筛选分类",
                modifier = Modifier.padding(horizontal = Space.gutter, vertical = Space.sm),
            )
            when {
                loading && categories.isEmpty() -> Loading()
                categories.isEmpty() -> EmptyHint("分类加载失败，请返回重试")
                shown.isEmpty() -> EmptyHint("没有匹配「$filter」的分类，换个词试试")
                else -> LazyVerticalGrid(
                    columns = GridCells.Adaptive(104.dp),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = Space.gutter, end = Space.gutter,
                        top = Space.xs, bottom = Space.xxl,
                    ),
                    horizontalArrangement = Arrangement.spacedBy(Space.md),
                    verticalArrangement = Arrangement.spacedBy(Space.lg),
                ) {
                    items(shown, key = { "cat-${it.id}" }) { c -> CategoryTile(c, onPick) }
                }
            }
        }
    }
}

/**
 * 分类块：封面图 + 名称（+ 册数）。
 *
 * 名称写在图下方而不是叠在图上：封面色深浅不可控，压在图上的文字对比度没有保证。
 *
 * v3 修「分类格没有封面图」：
 * - down_category 的 pic_url 全是 **500×500 正方形**封面。格子原来是 aspectRatio(1.30f)
 *   的横版，Crop 会先把正方形放大到填满格宽，再纵向裁掉上下约 23%。人物和构图基本都在这
 *   23% 里，裁完只剩中间一条平涂色带，肉眼看上去就是"没有图"。改成 1:1 后正方形对正方形，
 *   Crop 等于不裁，封面完整可见。
 * - 底层先画分类名，封面再盖上去。Coil 下载期间/失败时格子里显示的是分类名而不是纯色块，
 *   194 张图逐个到达的过程也就不会看起来像"一片空白"。
 */
@Composable
private fun CategoryTile(category: BookCategory, onPick: (BookCategory) -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clickable { onPick(category) },
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                // 封面是 500×500 正方形，格子也用正方形，ContentScale.Crop 才等于不裁
                .aspectRatio(1f)
                .clip(RoundedCornerShape(Radius.sm))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                category.name,
                fontSize = 13.sp,
                lineHeight = 17.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = Space.sm),
            )
            if (category.cover.isNotBlank()) {
                AsyncImage(
                    model = category.cover,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        Text(
            text = category.name,
            fontSize = 14.sp,
            lineHeight = 20.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = Space.sm),
        )
        // 实测 down_category 的 book_num 恒为 1（占位值），显示出来只会误导
        if (category.bookCount > 1) {
            Text(
                text = "${formatCount(category.bookCount)} 本",
                fontSize = 11.sp,
                lineHeight = 15.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}

/**
 * 分类下的书单。
 *
 * 顶部保留那句免责声明——榜单接口的 rank_sub_info_id 只做推荐位筛选，不是严格分类过滤，
 * 实测「玄幻」榜里会混进都市高武，文案不能写成「玄幻分类全部小说」。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryBooksScreen(
    title: String,
    books: List<Book>,
    loading: Boolean,
    onBack: () -> Unit,
    onOpen: (Book) -> Unit,
) {
    Scaffold(
        topBar = {
            XReadTopBar(
                title = title,
                subtitle = "推荐位",
                onBack = onBack,
            )
        }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            Text(
                text = "来自推荐榜按该分类的推荐位，可能混有其他分类的作品",
                fontSize = 11.sp,
                lineHeight = 16.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = Space.gutter, vertical = Space.xs),
            )
            when {
                loading && books.isEmpty() -> Loading()
                books.isEmpty() -> EmptyHint("该分类暂无数据")
                else -> {
                    SectionHeader("全部作品", "${books.size} 本")
                    BookGrid(books, Modifier.weight(1f), onOpen)
                }
            }
        }
    }
}

/** 分类过滤：空词返回全部，否则按名字做大小写无关的子串匹配。纯函数，便于单测。 */
internal fun filterCategories(all: List<BookCategory>, query: String): List<BookCategory> {
    val q = query.trim()
    if (q.isEmpty()) return all
    return all.filter { it.name.contains(q, ignoreCase = true) }
}
