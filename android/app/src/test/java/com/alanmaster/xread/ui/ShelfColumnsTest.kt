package com.alanmaster.xread.ui

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 书架卡片视图的列数。
 *
 * 阈值必须和阅读页双栏一致（720dp）：同一台设备上阅读页两栏、书架却还是单列，
 * 看起来就像两套布局拼起来的。列表视图永远单列——它的信息密度按整行设计。
 */
class ShelfColumnsTest {
    @Test fun phoneStaysSingleColumn() {
        assertEquals(1, shelfColumns(listView = false, widthDp = 392))
    }

    @Test fun tabletSplitsIntoTwoColumns() {
        assertEquals(2, shelfColumns(listView = false, widthDp = 720))
    }

    @Test fun widerTabletStillTwoColumns() {
        assertEquals(2, shelfColumns(listView = false, widthDp = 1024))
    }

    @Test fun listViewNeverSplits() {
        assertEquals(1, shelfColumns(listView = true, widthDp = 1024))
    }

    @Test fun justBelowThresholdKeepsOneColumn() {
        assertEquals(1, shelfColumns(listView = false, widthDp = 719))
    }
}
