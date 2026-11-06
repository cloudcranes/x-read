package com.alanmaster.xread.data

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

/**
 * 月报聚合的边界：闰年二月、月长、跨年、网格对齐、连续天数。
 *
 * 这些算错了不会崩，只会在报告里悄悄少一天或多一天——所以逐条钉死。
 */
class ReadingReviewTest {

    private fun days(vararg pairs: Pair<String, Long>): JSONObject =
        JSONObject().apply { pairs.forEach { (k, v) -> put(k, v) } }

    private fun nowAt(year: Int, month: Int, day: Int): Long {
        val cal = Calendar.getInstance().apply {
            clear()
            set(year, month - 1, day, 12, 0, 0)
        }
        return cal.timeInMillis
    }

    @Test
    fun currentPeriodReadsCalendarMonth() {
        val p = ReadingReview.currentPeriod(nowAt(2026, 11, 6))
        assertEquals(2026, p.year)
        assertEquals(11, p.month)
        assertEquals("2026-11", p.prefix)
        assertEquals("2026 年 11 月", p.longLabel)
    }

    @Test
    fun previousMonthCrossesYear() {
        assertEquals(ReportPeriod(2025, 12), ReportPeriod(2026, 1).previous())
        assertEquals(ReportPeriod(2027, 1), ReportPeriod(2026, 12).next())
        assertEquals(ReportPeriod(2025), ReportPeriod(2026).previous())
        assertEquals(ReportPeriod(2027), ReportPeriod(2026).next())
    }

    @Test
    fun summaryOnlyCountsPeriodAndPositiveDays() {
        val stats = days(
            "2026-11-01" to 1800L,
            "2026-11-02" to 0L,       // 0 秒不算「读过」
            "2026-11-03" to 3600L,
            "2026-10-31" to 9999L,    // 上个月
            "2026-12-01" to 9999L,    // 下个月
        )
        val s = ReadingReview.summarize(stats, ReportPeriod(2026, 11))
        assertEquals(5400L, s.totalSeconds)
        assertEquals(2, s.activeDays)
        assertEquals("2026-11-03", s.bestDayKey)
        assertEquals(3600L, s.bestDaySeconds)
        // 1 号与 3 号之间断了 2 号，所以最长连续是 1
        assertEquals(1, s.longestStreak)
        assertEquals(45, s.averageMinutesPerActiveDay)
    }

    @Test
    fun yearlySummarySpansAllMonths() {
        val stats = days(
            "2026-01-05" to 600L,
            "2026-06-30" to 1200L,
            "2026-12-31" to 300L,
            "2025-12-31" to 5000L,
        )
        val s = ReadingReview.summarize(stats, ReportPeriod(2026))
        assertEquals(2100L, s.totalSeconds)
        assertEquals(3, s.activeDays)
        assertEquals("2026-06-30", s.bestDayKey)
    }

    @Test
    fun metDaysNeedsEnabledGoal() {
        val stats = days("2026-11-01" to 1800L, "2026-11-02" to 300L)
        val off = ReadingReview.summarize(stats, ReportPeriod(2026, 11), ReadingGoal(enabled = false, minutes = 30))
        assertEquals(0, off.metDays)
        val on = ReadingReview.summarize(stats, ReportPeriod(2026, 11), ReadingGoal(enabled = true, minutes = 30))
        assertEquals(1, on.metDays)
    }

    @Test
    fun monthGridAlignsFirstDayToMondayColumns() {
        // 2026-11-01 是周日 → 前面要补 6 格（周一…周六）
        val grid = ReadingReview.monthGrid(days("2026-11-01" to 60L), 2026, 11)
        assertEquals(0, grid.size % 7)
        assertEquals(6, grid.indexOfFirst { it.inPeriod })
        assertEquals(1, grid[6].dayOfMonth)
        assertEquals(60L, grid[6].seconds)
        assertEquals(30, grid.count { it.inPeriod })
    }

    @Test
    fun monthGridHandlesLeapFebruary() {
        val grid = ReadingReview.monthGrid(null, 2024, 2)
        assertEquals(29, grid.count { it.inPeriod })
        // 2024-02-01 是周四 → 补 3 格
        assertEquals(3, grid.indexOfFirst { it.inPeriod })
        assertEquals(35, grid.size)
    }

    @Test
    fun monthGridPadsToWholeWeeks() {
        // 2026-03 有 31 天且 1 号是周日 → 6 + 31 = 37 → 补到 42
        val grid = ReadingReview.monthGrid(null, 2026, 3)
        assertEquals(42, grid.size)
        assertTrue(grid.all { it.inPeriod || it.key.isEmpty() })
    }

    @Test
    fun yearMonthsAlwaysTwelve() {
        val months = ReadingReview.yearMonths(days("2026-03-01" to 600L, "2026-03-02" to 600L), 2026)
        assertEquals(12, months.size)
        assertEquals(1200L, months[2].seconds)
        assertEquals(2, months[2].activeDays)
        assertEquals(0L, months[0].seconds)
        assertEquals("3", months[2].label)
    }

    @Test
    fun longestStreakCountsConsecutiveDays() {
        assertEquals(0, ReadingReview.longestStreak(emptyList()))
        assertEquals(
            4,
            ReadingReview.longestStreak(
                listOf("2026-11-01", "2026-11-02", "2026-11-03", "2026-11-04", "2026-11-09")
            ),
        )
        // 跨年也算连续
        assertEquals(2, ReadingReview.longestStreak(listOf("2025-12-31", "2026-01-01")))
        // 重复日期不重复计数
        assertEquals(2, ReadingReview.longestStreak(listOf("2026-11-01", "2026-11-01", "2026-11-02")))
    }

    @Test
    fun emptyStatsProduceEmptySummary() {
        val s = ReadingReview.summarize(null, ReportPeriod(2026, 11))
        assertEquals(0L, s.totalSeconds)
        assertEquals(0, s.activeDays)
        assertNull(s.bestDayKey)
        assertEquals(0, s.longestStreak)
        assertEquals(0, s.averageMinutesPerActiveDay)
    }

    @Test
    fun weekdayIndexUsesMondayFirst() {
        assertEquals(6, ReadingReview.weekdayIndex(2026, 11, 1)) // 周日
        assertEquals(4, ReadingReview.weekdayIndex(2026, 11, 6)) // 周五
        assertEquals(3, ReadingReview.weekdayIndex(2024, 2, 1))  // 周四
    }

    @Test
    fun summaryPeriodSurvivesLongGap() {
        // GoalMath.previousDay 的解析不该被「隔了半年」影响
        val stats = days("2026-01-01" to 60L, "2026-07-01" to 60L)
        val s = ReadingReview.summarize(stats, ReportPeriod(2026))
        assertEquals(2, s.activeDays)
        assertEquals(1, s.longestStreak)
        assertEquals(1, s.averageMinutesPerActiveDay) // 合计 120 秒 / 2 天 = 1 分钟
    }
}
