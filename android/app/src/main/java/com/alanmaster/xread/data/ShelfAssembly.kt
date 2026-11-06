package com.alanmaster.xread.data

/**
 * 把阅读进度叠到书架条目上，拼出界面用的书架列表。
 *
 * 只覆盖「随阅读变化」的字段，其余一律原样保留 —— 尤其是用户自己的标记
 * （想读/在读/读完、评分、标签）和分组、入架时间、更新基线。这些字段只存在
 * shelf.json 的条目里，重建 ShelfItem 时漏写一个，界面上就会表现为「点了没反应」：
 * 4.0.0 就漏了 status/rating/tags，详情页的标记每次刷新都回落成默认值，
 * 书架按「想读」「读完」筛也永远是空的。
 *
 * 抽成纯函数是为了能被单测盯住这条不变量（见 [ShelfAssemblyTest]）。
 */
internal fun assembleShelf(
    entries: List<ShelfItem>,
    progress: Map<String, Pair<String, Int>>,
    lastRead: Map<String, Long>,
    readIndexes: Map<String, Int>,
): List<ShelfItem> = entries.map { item ->
    val p = progress[item.book.bookId]
    item.copy(
        chapterId = p?.first.orEmpty(),
        chapterOffset = p?.second ?: 0,
        updatedAt = "",
        lastReadAt = lastRead[item.book.bookId] ?: 0L,
        readChapterIndex = readIndexes[item.book.bookId] ?: -1,
    )
}
