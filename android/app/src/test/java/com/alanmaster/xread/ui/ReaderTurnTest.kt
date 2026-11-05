package com.alanmaster.xread.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 整页翻页落点守卫。
 *
 * 翻页跳到段中间，是阅读器最刺眼的毛病：每翻一页都得重新找行首。
 * 这里钉住两条：新页页首永远是段首；到末项时交回翻章。
 */
class ReaderTurnTest {
    private fun item(index: Int, offset: Int, size: Int = 100) = TurnItem(index, offset, size)

    @Test fun forwardLandsOnLastItemThatStartsAboveFold() {
        // 视口 1000，项高 100：0..9 依次排开，屏底之上起头的最后一项是 9
        val visible = (0..9).map { item(it, it * 100) }
        val plan = ReaderTurn.forward(visible, fold = 1000, firstIndex = 0)
        assertEquals(9, plan?.index)
    }

    @Test fun forwardSkipsItemsStartingBelowFold() {
        // 第 10 项顶部正好压在屏底，算「看不见」，不能作为落点
        val visible = (0..10).map { item(it, it * 100) }
        val plan = ReaderTurn.forward(visible, fold = 1000, firstIndex = 0)
        assertEquals(9, plan?.index)
    }

    @Test fun forwardRefusesWhenNothingNewFits() {
        // 一段占满整屏：唯一可见项的顶部就到顶了，翻不动
        val visible = listOf(item(3, 0, size = 1400))
        assertNull(ReaderTurn.forward(visible, fold = 1000, firstIndex = 3))
    }

    @Test fun forwardRefusesWhenTargetIsCurrentFirstItem() {
        val visible = listOf(item(5, 0), item(5, 600))
        assertNull(ReaderTurn.forward(visible, fold = 1000, firstIndex = 5))
    }

    @Test fun forwardTreatsScrolledOutItemsAsAboveFold() {
        // 首项被卷出 40px，offset 为负；它仍是可见项，但不能作为落点
        val visible = listOf(item(4, -40), item(5, 60), item(6, 160))
        val plan = ReaderTurn.forward(visible, fold = 1000, firstIndex = 4)
        assertEquals(6, plan?.index)
    }

    @Test fun backTargetStepsBackOneScreenWorthOfParagraphs() {
        // 段高 100，一屏 500 → 回推 5 段
        val visible = (0..6).map { item(it, it * 100) }
        assertEquals(1, ReaderTurn.backTarget(visible, firstIndex = 6, pagePx = 500))
    }

    @Test fun backTargetClampsToChapterStart() {
        val visible = listOf(item(0, 0), item(1, 100))
        assertEquals(0, ReaderTurn.backTarget(visible, firstIndex = 1, pagePx = 5000))
    }

    @Test fun backTargetIgnoresOversizedHeaderWhenEstimating() {
        // 页眉 400，正文 100；取中位数才不会被页眉带偏（回推 5 段而非 1 段）
        val visible = listOf(item(0, 0, size = 400), item(1, 400), item(2, 500), item(3, 600))
        assertEquals(0, ReaderTurn.backTarget(visible, firstIndex = 3, pagePx = 500))
    }
}
