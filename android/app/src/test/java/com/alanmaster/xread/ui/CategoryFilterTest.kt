package com.alanmaster.xread.ui

import com.alanmaster.xread.data.BookCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CategoryFilterTest {
    private val all = listOf(
        BookCategory("7", "玄幻", "", 0),
        BookCategory("1", "都市", "", 0),
        BookCategory("511", "东方玄幻", "", 0),
    )

    @Test fun blankQueryKeepsEverything() {
        assertEquals(all, filterCategories(all, ""))
        assertEquals(all, filterCategories(all, "   "))
    }

    @Test fun matchesChineseSubstring() {
        assertEquals(listOf("7", "511"), filterCategories(all, "玄幻").map { it.id })
        assertEquals(listOf("1"), filterCategories(all, "都").map { it.id })
    }

    @Test fun noMatchReturnsEmpty() {
        assertTrue(filterCategories(all, "zz").isEmpty())
    }
}
