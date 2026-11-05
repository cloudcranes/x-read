package com.alanmaster.xread.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AdaptiveConcurrencyTest {

    @Test
    fun startsAtConfiguredWindow() {
        assertEquals(4, AdaptiveConcurrency().permits)
        assertEquals(2, AdaptiveConcurrency(start = 2).permits)
    }

    @Test
    fun clampsStartIntoRange() {
        assertEquals(1, AdaptiveConcurrency(start = 0).permits)
        assertEquals(8, AdaptiveConcurrency(start = 99).permits)
    }

    @Test
    fun increasesAfterAWholeWindowOfSuccesses() {
        val w = AdaptiveConcurrency(start = 2)
        w.recordSuccess()
        assertEquals(2, w.permits)
        w.recordSuccess()
        assertEquals(3, w.permits)
    }

    @Test
    fun neverExceedsMax() {
        val w = AdaptiveConcurrency(start = 7, max = 8)
        repeat(50) { w.recordSuccess() }
        assertEquals(8, w.permits)
    }

    @Test
    fun halvesOnFailureAndStopsAtMin() {
        val w = AdaptiveConcurrency(start = 8)
        w.recordFailure(); assertEquals(4, w.permits)
        w.recordFailure(); assertEquals(2, w.permits)
        w.recordFailure(); assertEquals(1, w.permits)
        w.recordFailure(); assertEquals(1, w.permits)
    }

    @Test
    fun failureResetsSuccessStreak() {
        // 窗口降到 2 之后，必须重新攒满 2 次成功才 +1；旧的 3 次成功不得继续计数。
        val w = AdaptiveConcurrency(start = 4)
        repeat(3) { w.recordSuccess() }
        w.recordFailure()
        assertEquals(2, w.permits)
        w.recordSuccess()
        assertEquals("streak restarted after failure", 2, w.permits)
        w.recordSuccess()
        assertEquals(3, w.permits)
    }

    @Test
    fun backoffGrowsAndIsCapped() {
        val w = AdaptiveConcurrency(start = 4)
        assertEquals(0L, w.backoffMs)
        w.recordFailure()
        assertEquals(0L, w.backoffMs)
        w.recordFailure()
        assertEquals(AdaptiveConcurrency.BASE_BACKOFF_MS, w.backoffMs)
        w.recordFailure()
        assertEquals(AdaptiveConcurrency.BASE_BACKOFF_MS * 2, w.backoffMs)
        repeat(10) { w.recordFailure() }
        assertEquals(AdaptiveConcurrency.MAX_BACKOFF_MS, w.backoffMs)
    }

    @Test
    fun successClearsBackoff() {
        val w = AdaptiveConcurrency(start = 4)
        repeat(3) { w.recordFailure() }
        assertTrue(w.backoffMs > 0)
        w.recordSuccess()
        assertEquals(0L, w.backoffMs)
    }

    @Test
    fun countersTrackOutcomes() {
        val w = AdaptiveConcurrency(start = 4)
        repeat(5) { w.recordSuccess() }
        repeat(2) { w.recordFailure() }
        assertEquals(5, w.successes)
        assertEquals(2, w.failures)
        assertTrue(w.summary().contains("并发"))
    }
}
