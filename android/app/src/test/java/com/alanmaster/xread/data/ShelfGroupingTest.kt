package com.alanmaster.xread.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ShelfGroupingTest {

    private fun item(
        id: String,
        creationStatus: String,
        group: String = "",
        status: String = ShelfItem.STATUS_READING,
    ): ShelfItem = ShelfItem(
        book = Book(bookId = id, title = "书$id", creationStatus = creationStatus),
        chapterId = "",
        chapterOffset = 0,
        updatedAt = "",
        group = group,
        status = status,
    )

    @Test
    fun finishedOnlyWhenUpstreamSaysFinished() {
        assertTrue(ShelfGrouping.matches(item("1", "0"), ShelfGrouping.FINISHED))
        assertFalse(ShelfGrouping.matches(item("2", "1"), ShelfGrouping.FINISHED))
    }

    @Test
    fun unknownStatusCountsAsSerial() {
        // 上游没给 creation_status 时不能当完结——那会让人以为能一口气读完。
        assertTrue(ShelfGrouping.matches(item("1", ""), ShelfGrouping.SERIAL))
        assertFalse(ShelfGrouping.matches(item("1", ""), ShelfGrouping.FINISHED))
    }

    @Test
    fun manualGroupMatchesEverything() {
        assertTrue(ShelfGrouping.matches(item("1", "0"), "历史"))
        assertTrue(ShelfGrouping.matches(item("1", "1"), "历史"))
    }

    @Test
    fun summarizeCountsAndKeepsMenuOrder() {
        val groups = ShelfGrouping.summarize(
            listOf(
                item("1", "1"),
                item("2", ""),
                item("3", "0"),
            ),
        )

        assertEquals(listOf(ShelfGrouping.SERIAL, ShelfGrouping.FINISHED), groups.map { it.key })
        assertEquals(listOf(2, 1), groups.map { it.count })
        assertEquals(listOf("连载中", "已完结"), groups.map { it.label })
    }

    @Test
    fun summarizeSkipsEmptyGroups() {
        // 架上一本完结都没有时，菜单里不该出现「已完结」。
        val groups = ShelfGrouping.summarize(listOf(item("1", "1"), item("2", "")))

        assertEquals(listOf(ShelfGrouping.SERIAL), groups.map { it.key })
        assertEquals(2, groups.first().count)
    }

    @Test
    fun summarizeOfEmptyShelfIsEmpty() {
        assertTrue(ShelfGrouping.summarize(emptyList()).isEmpty())
    }

    @Test
    fun builtinKeysAreDistinguishableFromManualNames() {
        assertTrue(ShelfGrouping.isBuiltin(ShelfGrouping.SERIAL))
        assertTrue(ShelfGrouping.isBuiltin(ShelfGrouping.FINISHED))
        assertFalse(ShelfGrouping.isBuiltin("连载中"))
        assertFalse(ShelfGrouping.isBuiltin(""))
        assertEquals("连载中", ShelfGrouping.labelOf(ShelfGrouping.SERIAL))
        assertEquals("科幻", ShelfGrouping.labelOf("科幻")) // 手工组名原样返回
    }

    @Test
    fun shelfSortFiltersByBuiltinGroup() {
        val serial = item("1", "1")
        val unknown = item("2", "")
        val finished = item("3", "0")
        val items = listOf(serial, unknown, finished)

        val serialOnly = LibraryViewModel.ShelfSort.apply(items, "recent", ShelfGrouping.SERIAL)
        assertEquals(listOf("1", "2"), serialOnly.map { it.book.bookId })

        val finishedOnly = LibraryViewModel.ShelfSort.apply(items, "recent", ShelfGrouping.FINISHED)
        assertEquals(listOf("3"), finishedOnly.map { it.book.bookId })
    }

    @Test
    fun wantGroupMatchesOnlyWantStatus() {
        // 「待读」看个人标记，和上游连载状态正交：一本连载中的书可以在待读，也可以不在。
        val want = item("1", "1", status = ShelfItem.STATUS_WANT)
        val reading = item("2", "1", status = ShelfItem.STATUS_READING)
        val done = item("3", "1", status = ShelfItem.STATUS_DONE)

        assertTrue(ShelfGrouping.matches(want, ShelfGrouping.WANT))
        assertFalse(ShelfGrouping.matches(reading, ShelfGrouping.WANT))
        assertFalse(ShelfGrouping.matches(done, ShelfGrouping.WANT))
        // 待读的书照样落在连载状态分组里
        assertTrue(ShelfGrouping.matches(want, ShelfGrouping.SERIAL))
    }

    @Test
    fun summarizePutsWantFirstWhenPresent() {
        val groups = ShelfGrouping.summarize(
            listOf(
                item("1", "1", status = ShelfItem.STATUS_WANT),
                item("2", "0", status = ShelfItem.STATUS_WANT),
                item("3", "1"),
            ),
        )

        // 三本里两本待读（其中一本已完结），所以三个自动分组都有书，待读排在最前。
        assertEquals(
            listOf(ShelfGrouping.WANT, ShelfGrouping.SERIAL, ShelfGrouping.FINISHED),
            groups.map { it.key },
        )
        assertEquals(listOf(2, 2, 1), groups.map { it.count })
        assertEquals("待读", groups.first().label)
    }

    @Test
    fun shelfSortFiltersByWantGroup() {
        val items = listOf(
            item("1", "1", status = ShelfItem.STATUS_WANT),
            item("2", "1"),
            item("3", "0", status = ShelfItem.STATUS_DONE),
        )

        val want = LibraryViewModel.ShelfSort.apply(items, "group", ShelfGrouping.WANT)
        assertEquals(listOf("1"), want.map { it.book.bookId })
    }

    @Test
    fun shelfSortStillFiltersManualGroup() {
        val a = item("1", "1", group = "历史")
        val b = item("2", "0", group = "科幻")

        val history = LibraryViewModel.ShelfSort.apply(listOf(a, b), "group", "历史")
        assertEquals(listOf("1"), history.map { it.book.bookId })
    }

    @Test
    fun shelfSortStacksBuiltinGroupWithStatusFilter() {
        val readingSerial = item("1", "1").copy(status = ShelfItem.STATUS_READING)
        val doneSerial = item("2", "1").copy(status = ShelfItem.STATUS_DONE)
        val doneFinished = item("3", "0").copy(status = ShelfItem.STATUS_DONE)

        val result = LibraryViewModel.ShelfSort.apply(
            listOf(readingSerial, doneSerial, doneFinished),
            "group",
            ShelfGrouping.SERIAL,
            status = ShelfItem.STATUS_DONE,
        )

        assertEquals(listOf("2"), result.map { it.book.bookId })
    }
}
