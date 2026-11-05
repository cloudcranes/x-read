package com.alanmaster.xread.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FindHighlightTest {

    @Test fun emptyQueryHasNoRanges() {
        assertTrue(FindHighlight.ranges("abc", "").isEmpty())
        assertTrue(FindHighlight.ranges("abc", "   ").isEmpty())
    }

    @Test fun findsEveryOccurrenceInOrder() {
        assertEquals(
            listOf(0 until 2, 5 until 7, 7 until 9),
            FindHighlight.ranges("ab123abab", "ab"),
        )
    }

    @Test fun overlappingHitsAdvanceOneChar() {
        assertEquals(listOf(0 until 2, 1 until 3), FindHighlight.ranges("aaa", "aa"))
    }

    @Test fun longerQueryThanTextHasNoRange() {
        assertTrue(FindHighlight.ranges("ab", "abcdef").isEmpty())
    }

    @Test fun matchesTheSameParagraphsAsFindHits() {
        val chapter = ChapterContent(
            "c1", "第一章",
            listOf("陆锋走进禁区", "灵根被毁", "再说陆锋"),
            previousId = null, nextId = null,
        )
        val query = "陆锋"
        assertEquals(listOf(0, 2), chapter.findHits(query))
        assertEquals(1, FindHighlight.ranges(chapter.paragraphs[0], query).size)
    }
}
