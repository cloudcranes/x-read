package com.alanmaster.xread.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class RankCategoryTest {

    private val all = listOf(
        "全部", "穿越", "系统", "都市", "现代言情", "玄幻",
        "古代言情", "重生", "架空", "东方玄幻", "打脸", "奇幻仙侠",
    )

    @Test
    fun collapsedKeepsOneRowAndKeepsSelection() {
        // 默认停在「全部」：收起时只铺前 5 个，选中项本来就在里面
        assertEquals(
            listOf("全部", "穿越", "系统", "都市", "现代言情"),
            visibleCategories(all, "全部", expanded = false),
        )
    }

    @Test
    fun selectionOutsideTheRowReplacesTheLastChip() {
        // 选了「奇幻仙侠」（第 12 个）也必须看得见，于是顶掉第 5 个
        assertEquals(
            listOf("全部", "穿越", "系统", "都市", "奇幻仙侠"),
            visibleCategories(all, "奇幻仙侠", expanded = false),
        )
    }

    @Test
    fun expandedShowsEverything() {
        assertEquals(all, visibleCategories(all, "奇幻仙侠", expanded = true))
    }

    @Test
    fun shortListIsNeverCollapsed() {
        // 分类本来就少时不该多出「全部 N」按钮
        val short = listOf("全部", "玄幻", "都市", "言情", "穿越", "系统")
        assertEquals(short, visibleCategories(short, "系统", expanded = false))
    }

    @Test
    fun unknownSelectionFallsBackToThePlainHead() {
        assertEquals(
            all.take(CategoryRowLimit),
            visibleCategories(all, "不存在的分类", expanded = false),
        )
    }
}
