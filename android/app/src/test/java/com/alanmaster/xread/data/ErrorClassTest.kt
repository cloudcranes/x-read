package com.alanmaster.xread.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ErrorClassTest {

    @Test
    fun networkAndThrottleAreTransient() {
        assertTrue(ErrorClass.isTransient(0, "UPSTREAM_UNAVAILABLE"))
        assertTrue(ErrorClass.isTransient(429, "RATE_LIMITED"))
        assertTrue(ErrorClass.isTransient(503, "LOCAL_BUSY"))
        assertTrue(ErrorClass.isTransient(502, ""))
        assertTrue(ErrorClass.isTransient(500, ""))
    }

    @Test
    fun missingUpstreamContentIsPermanentEvenWhenWrappedIn502() {
        // 真实故障：服务端把上游缺页包成 502 + UPSTREAM_INVALID
        assertFalse(ErrorClass.isTransient(502, "UPSTREAM_INVALID"))
        assertFalse(ErrorClass.isTransient(500, "UPSTREAM_INVALID"))
    }

    @Test
    fun contentErrorsArePermanent() {
        assertFalse(ErrorClass.isTransient(403, "CHAPTER_LOCKED"))
        assertFalse(ErrorClass.isTransient(404, "CHAPTER_NOT_FOUND"))
        assertFalse(ErrorClass.isTransient(404, "NOT_FOUND"))
        assertFalse(ErrorClass.isTransient(401, "AUTH_REQUIRED"))
    }

    @Test
    fun unknownCodeFallsBackToStatus() {
        assertTrue(ErrorClass.isTransient(504, "SOMETHING_NEW"))
        assertFalse(ErrorClass.isTransient(400, "SOMETHING_NEW"))
    }

    @Test
    fun codeOutranksStatus() {
        assertFalse(ErrorClass.isTransient(503, "UPSTREAM_INVALID"))
        assertTrue(ErrorClass.isTransient(400, "UPSTREAM_HTTP"))
    }

    @Test
    fun plainStringErrorsFallBackToMessagePatterns() {
        // core 常只回一句字符串：这类缺页再重试也不会成功
        assertFalse(ErrorClass.isTransient(500, "", "Missing initial page JSON"))
        assertFalse(ErrorClass.isTransient(502, "", "chapter not found"))
        assertTrue(ErrorClass.isTransient(500, "", "upstream timeout"))
        assertTrue(ErrorClass.isTransient(502, "", ""))
    }

    @Test
    fun structuredCodeStillWinsOverMessage() {
        assertFalse(ErrorClass.isTransient(503, "UPSTREAM_INVALID", "temporary"))
        assertTrue(ErrorClass.isTransient(400, "UPSTREAM_HTTP", "missing initial page json"))
    }
}
