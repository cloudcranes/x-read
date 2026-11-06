package com.alanmaster.xread.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 跨章浮现的章名文案。
 *
 * 上游章节标题自带「第6章」前缀，若再按「第 N 章 · 标题」拼一遍，
 * 屏幕上就会出现「第 6 章 · 第6章 好苦啊」这种重复章号。
 */
class ChapterToastTextTest {
    @Test fun stripsUpstreamChapterPrefix() {
        assertEquals("第 6 章 · 好苦啊", chapterToastText(5, "第6章 好苦啊"))
    }

    @Test fun stripsPrefixWithColon() {
        assertEquals("第 3 章 · 写日记的人", chapterToastText(2, "第3章：写日记的人"))
    }

    @Test fun stripsChineseNumeralPrefix() {
        assertEquals("第 2 章 · 游戏开始", chapterToastText(1, "第二章 游戏开始"))
    }

    @Test fun keepsTitleWithoutPrefix() {
        assertEquals("第 1 章 · 序章", chapterToastText(0, "序章"))
    }

    @Test fun fallsBackToNumberWhenTitleIsOnlyHeading() {
        assertEquals("第 6 章", chapterToastText(5, "第6章"))
        assertEquals("第 7 章", chapterToastText(6, "   "))
    }

    @Test fun dropsNumberWhenChapterIsUnknown() {
        assertEquals("好苦啊", chapterToastText(-1, "第6章 好苦啊"))
        assertNull(chapterToastText(-1, ""))
    }
}
