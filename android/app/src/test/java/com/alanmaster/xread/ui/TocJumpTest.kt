package com.alanmaster.xread.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TocJumpTest {

    @Test
    fun chapterNumberBecomesListIndex() {
        assertEquals(0, chapterJumpIndex("1", 937))
        assertEquals(11, chapterJumpIndex("12", 937))
        assertEquals(936, chapterJumpIndex("937", 937))
    }

    @Test
    fun leadingZerosStillJump() {
        assertEquals(11, chapterJumpIndex("012", 937))
    }

    @Test
    fun numberOutsideTheBookIsNotAJump() {
        assertNull(chapterJumpIndex("0", 937))
        assertNull(chapterJumpIndex("938", 937))
        assertNull(chapterJumpIndex("99999", 937))
    }

    @Test
    fun textFallsBackToTitleFiltering() {
        assertNull(chapterJumpIndex("", 937))
        assertNull(chapterJumpIndex("第12章", 937))
        assertNull(chapterJumpIndex("12章", 937))
        assertNull(chapterJumpIndex("12 章", 937))
    }
}
