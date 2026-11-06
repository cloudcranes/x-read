package com.alanmaster.xread.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** 阅读状态自动推进：只升不降，末章末段才算读完。 */
class ShelfStatusTest {

    @Test fun openingBodyTurnsWantIntoReading() {
        assertEquals(ShelfItem.STATUS_READING,
            autoShelfStatus(ShelfItem.STATUS_WANT, atEnd = false))
    }

    @Test fun readingStaysReadingInTheMiddle() {
        assertNull(autoShelfStatus(ShelfItem.STATUS_READING, atEnd = false))
    }

    @Test fun lastParagraphOfLastChapterFinishes() {
        assertEquals(ShelfItem.STATUS_DONE,
            autoShelfStatus(ShelfItem.STATUS_READING, atEnd = true))
        assertEquals(ShelfItem.STATUS_DONE,
            autoShelfStatus(ShelfItem.STATUS_WANT, atEnd = true))
    }

    @Test fun finishedNeverGoesBack() {
        assertNull(autoShelfStatus(ShelfItem.STATUS_DONE, atEnd = false))
        assertNull(autoShelfStatus(ShelfItem.STATUS_DONE, atEnd = true))
    }
}
