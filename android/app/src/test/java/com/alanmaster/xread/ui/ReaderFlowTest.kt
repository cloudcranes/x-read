package com.alanmaster.xread.ui

import com.alanmaster.xread.data.ChapterContent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 连续阅读的列表布局守卫。
 *
 * 跨章阅读把多章正文接成一条 LazyColumn，下标换算错了就会：
 * 滚到下一章却把进度写回上一章、书签挂到别的段落、朗读跳章。
 * 这些都是纯算术，所以用单测钉死。
 */
class ReaderFlowTest {
    private fun chapter(id: String, paragraphs: Int) =
        ChapterContent(id, id, List(paragraphs) { "p$it" }, previousId = null, nextId = null)

    @Test fun headerSitsAtChapterOffset() {
        val flow = listOf(chapter("a", 3), chapter("b", 2))
        // 第一章：页眉 0、段落 1..3 → 第二章页眉落在 4
        assertEquals(0, ReaderFlow.offsetOf(flow, 0))
        assertEquals(4, ReaderFlow.offsetOf(flow, 1))
    }

    @Test fun locateMapsHeaderToParagraphZero() {
        val flow = listOf(chapter("a", 3), chapter("b", 2))
        assertEquals(0 to 0, ReaderFlow.locate(flow, 0))
        assertEquals(0 to 0, ReaderFlow.locate(flow, 1))
        assertEquals(0 to 1, ReaderFlow.locate(flow, 2))
        assertEquals(0 to 2, ReaderFlow.locate(flow, 3))
        assertEquals(1 to 0, ReaderFlow.locate(flow, 4))
        assertEquals(1 to 0, ReaderFlow.locate(flow, 5))
        assertEquals(1 to 1, ReaderFlow.locate(flow, 6))
    }

    @Test fun locateBeyondEndFallsOnLastParagraph() {
        val flow = listOf(chapter("a", 3), chapter("b", 2))
        // 尾部导航项的下标越过了所有正文
        assertEquals(1 to 1, ReaderFlow.locate(flow, 99))
    }

    @Test fun indexOfIsInverseOfLocate() {
        val flow = listOf(chapter("a", 3), chapter("b", 2))
        for (k in flow.indices) {
            for (p in flow[k].paragraphs.indices) {
                val idx = ReaderFlow.indexOf(flow, k, p)
                assertEquals(k to p, ReaderFlow.locate(flow, idx))
            }
        }
    }

    @Test fun itemsCoverEveryChapterAndParagraph() {
        val flow = listOf(chapter("a", 3), chapter("b", 2))
        val items = ReaderFlow.items(flow)
        // 每章 1 个页眉 + 段落数
        assertEquals(1 + 3 + 1 + 2, items.size)
        assertTrue(items[0].header)
        assertEquals(0, items[0].chapter)
        assertEquals(1, items[4].chapter)
        assertTrue(items[4].header)
    }

    @Test fun bookPercentIsContinuousAcrossChapterBoundary() {
        val flow = listOf(chapter("a", 2), chapter("b", 2))
        val endOfA = ReaderFlow.indexOf(flow, 0, 1)
        val startOfB = ReaderFlow.indexOf(flow, 1, 0)
        val endPercent = ReaderFlow.bookPercent(flow, endOfA, 10, 0)
        val startPercent = ReaderFlow.bookPercent(flow, startOfB, 10, 0)
        // 跨章不回零：下一章开头必须比上一章结尾大
        assertTrue("跨章后进度不能回零", startPercent > endPercent)
        assertTrue(startPercent <= 1f)
    }

    @Test fun bookPercentClampsToBook() {
        val flow = listOf(chapter("a", 2))
        assertEquals(0f, ReaderFlow.bookPercent(flow, 0, 0, 0), 0.001f)
        assertTrue(ReaderFlow.bookPercent(flow, 0, 4, 3) <= 1f)
    }

    /**
     * 追加章节后必须用**新** flow 换算下标。
     *
     * 这是「读到第 10 章，退出重进回到第 2 章」的成因守卫：阅读页里那条 collect
     * 协程的 key 只跟 scrollRequest 与首章 id 走，追加章节时不会重启，
     * 于是闭包一直握着打开时的单章流。越界下标被 locate 压到最后一章，
     * 上报的永远是打开时那一章 → setParagraph 认为「没换章」→ 进度不落盘。
     *
     * 用同一个列表下标分别喂「旧流」与「增长后的流」：旧流给出的章号必须停在首章，
     * 新流必须给出读者真正滚到的那一章。两者不同，正是必须读最新 flow 的理由。
     */
    @Test fun staleFlowReportsTheWrongChapterAfterAppend() {
        val opened = listOf(chapter("a", 3))
        val grown = opened + listOf(chapter("b", 3), chapter("c", 3))

        // 读者已经滚到第 3 章第 1 段（locate 返回的是章序号 + 段序号）
        val scrolledIndex = ReaderFlow.indexOf(grown, 2, 1)
        assertEquals(2 to 1, ReaderFlow.locate(grown, scrolledIndex))

        // 旧闭包捕获的 opened 里没有这个下标，locate 越界后压到最后一章
        val stale = ReaderFlow.locate(opened, scrolledIndex)
        assertEquals("捕获旧 flow 时只能报回首章", 0, stale.first)

        assertTrue("新旧换算必须不同，否则这个 bug 不可复现", stale != ReaderFlow.locate(grown, scrolledIndex))
    }

    @Test fun appendedChapterIsAddressableAtItsOwnIndex() {
        // 触底追加后，新章页眉与段落都要能被定位，不能落到上一章
        val flow = listOf(chapter("a", 2), chapter("b", 2))
        val headerOfB = ReaderFlow.headerOf(flow, 1)
        assertEquals(1 to 0, ReaderFlow.locate(flow, headerOfB))
        assertEquals(1 to 1, ReaderFlow.locate(flow, headerOfB + 2))
    }
}
