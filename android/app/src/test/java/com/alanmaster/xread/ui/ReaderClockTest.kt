package com.alanmaster.xread.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.TimeZone

/**
 * 阅读页左下角时钟守卫。
 *
 * 三条规则钉住：
 * 1. 时间格式是 "HH:mm"（分钟粒度，够用且不刺眼）；
 * 2. 刷新定时器对齐到整秒——每一拍都按当前墙钟重算延迟，唤醒抖动不累积；
 * 3. 抖动容忍：最坏情况下也只允许迟 1 秒，不允许出现整秒级空洞
 *    （分钟粒度定时器被系统推迟一次，就能让显示整整慢一分钟）。
 */
class ReaderClockTest {

    @Test fun formatsHourAndMinute() {
        val old = TimeZone.getDefault()
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
            // 2024-01-01 07:10 UTC
            assertEquals("07:10", ReaderClock.format(1_704_093_000_000L))
            assertEquals("00:00", ReaderClock.format(1_704_067_200_000L))
        } finally {
            TimeZone.setDefault(old)
        }
    }

    @Test fun nextSecondDelayLandsOnSecondBoundary() {
        assertEquals(1_000L, ReaderClock.millisToNextSecond(1_704_093_000_000L))
        assertEquals(1L, ReaderClock.millisToNextSecond(1_704_093_000_999L))
        assertEquals(500L, ReaderClock.millisToNextSecond(1_704_093_000_500L))
        // 延迟必须落在 (0, 1000]，不能是 0（否则 delay(0) 变忙等）。
        listOf(0L, 1L, 499L, 500L, 999L).forEach { offset ->
            val delay = ReaderClock.millisToNextSecond(1_704_093_000_000L + offset)
            assertTrue("延迟应为正", delay > 0)
            assertTrue("延迟不应超过一秒", delay <= 1_000L)
            assertEquals(
                "加完延迟正好落在整秒",
                0L,
                (1_704_093_000_000L + offset + delay) % 1_000L,
            )
        }
    }

    @Test fun jitterNeverStallsTheClockForAWholeSecond() {
        // 模拟唤醒抖动：每拍都比计划晚 0..900ms。
        // 分钟粒度定时器被推迟一次就可能慢整整一分钟；秒粒度每拍重算，最坏只迟 1 秒。
        val wakes = ArrayList<Long>()
        var wake = 1_704_093_000_000L
        repeat(300) { i ->
            val jitter = (i * 137L) % 901L
            wake += ReaderClock.millisToNextSecond(wake) + jitter
            wakes += wake
        }
        // 相邻两次唤醒间隔不超过 2 秒（1 秒定时 + 最坏 900ms 抖动）。
        for (i in 1 until wakes.size) {
            assertTrue(
                "第 $i 拍间隔 ${wakes[i] - wakes[i - 1]}ms，超出容忍范围",
                wakes[i] - wakes[i - 1] <= 2_000L,
            )
        }
        // 300 拍至少覆盖 290 个不同的秒边界（不能出现成片空洞）。
        val covered = wakes.map { it / 1_000L }.toSet().size
        assertTrue("实际只覆盖 $covered 个秒边界", covered >= 290)
    }
}
