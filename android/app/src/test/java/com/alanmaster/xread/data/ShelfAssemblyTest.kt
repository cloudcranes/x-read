package com.alanmaster.xread.data

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 组装书架时必须保住用户标记。
 *
 * 4.0.0 在这里丢过 status/rating/tags：`Api.shelf()` 用 `books()` 重建 ShelfItem，
 * 只抄了分组和入架时间。结果详情页点「读完」写入成功、`loadShelf()` 一刷新又变回
 * 「在读」，看起来像点击没反应；书架按「想读」「读完」筛也永远是空的。
 */
class ShelfAssemblyTest {

    private fun item(id: String) = ShelfItem(
        book = Book(bookId = id, title = "书$id", chapterCount = 100),
        chapterId = "",
        chapterOffset = 0,
        updatedAt = "",
        group = "收藏",
        addedAt = 1_700_000_000_000L,
        knownChapters = 90,
        status = ShelfItem.STATUS_DONE,
        rating = 4,
        tags = listOf("人物", "伏笔"),
    )

    @Test fun keepsUserMarksAndGroup() {
        val out = assembleShelf(
            entries = listOf(item("7")),
            progress = emptyMap(),
            lastRead = emptyMap(),
            readIndexes = emptyMap(),
        ).single()

        assertEquals(ShelfItem.STATUS_DONE, out.status)
        assertEquals(4, out.rating)
        assertEquals(listOf("人物", "伏笔"), out.tags)
        assertEquals("收藏", out.group)
        assertEquals(1_700_000_000_000L, out.addedAt)
        assertEquals(90, out.knownChapters)
    }

    @Test fun overlaysReadingProgressOnly() {
        val out = assembleShelf(
            entries = listOf(item("7")),
            progress = mapOf("7" to ("c-9" to 42)),
            lastRead = mapOf("7" to 1_700_000_111_000L),
            readIndexes = mapOf("7" to 8),
        ).single()

        assertEquals("c-9", out.chapterId)
        assertEquals(42, out.chapterOffset)
        assertEquals(1_700_000_111_000L, out.lastReadAt)
        assertEquals(8, out.readChapterIndex)
        // 进度覆盖不能顺手把标记抹掉
        assertEquals(ShelfItem.STATUS_DONE, out.status)
        assertEquals(4, out.rating)
        assertEquals(listOf("人物", "伏笔"), out.tags)
    }

    @Test fun missingProgressLeavesEmptyDefaults() {
        val out = assembleShelf(
            entries = listOf(item("7")),
            progress = emptyMap(),
            lastRead = emptyMap(),
            readIndexes = emptyMap(),
        ).single()

        assertEquals("", out.chapterId)
        assertEquals(0, out.chapterOffset)
        assertEquals(0L, out.lastReadAt)
        assertEquals(-1, out.readChapterIndex)
    }

    @Test fun everyEntryIsKeptInOrder() {
        val out = assembleShelf(
            entries = listOf(item("1"), item("2"), item("3")),
            progress = mapOf("2" to ("c-2" to 5)),
            lastRead = emptyMap(),
            readIndexes = mapOf("2" to 1),
        )
        assertEquals(listOf("1", "2", "3"), out.map { it.book.bookId })
        assertEquals("c-2", out[1].chapterId)
    }
}
