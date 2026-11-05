package com.alanmaster.xread.data

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class GoalMathTest {
    @get:Rule val temp = TemporaryFolder()

    private fun days(vararg pairs: Pair<String, Long>): JSONObject {
        val o = JSONObject()
        pairs.forEach { (k, v) -> o.put(k, v) }
        return o
    }

    private fun repo(): OfflineRepo = OfflineRepo(File(temp.newFolder(), "offline"), temp.newFolder())

    /* ---------------- 目标本身 ---------------- */

    @Test fun clampsGoalMinutes() {
        assertEquals(GoalMath.MIN_MINUTES, ReadingGoal(minutes = 0).normalized().minutes)
        assertEquals(GoalMath.MAX_MINUTES, ReadingGoal(minutes = 999).normalized().minutes)
        assertEquals(30, ReadingGoal(minutes = 30).normalized().minutes)
    }

    @Test fun disabledGoalHasNoTarget() {
        assertEquals(0L, GoalMath.goalSeconds(ReadingGoal(enabled = false, minutes = 30)))
        assertEquals(1800L, GoalMath.goalSeconds(ReadingGoal(enabled = true, minutes = 30)))
    }

    /* ---------------- 日期 ---------------- */

    @Test fun dayKeyIsIsoDay() {
        assertEquals(10, GoalMath.dayKey().length)
        assertTrue(GoalMath.dayKey().matches(Regex("\\d{4}-\\d{2}-\\d{2}")))
    }

    @Test fun previousDayCrossesMonthAndYear() {
        assertEquals("2023-12-31", GoalMath.previousDay("2024-01-01"))
        assertEquals("2024-02-29", GoalMath.previousDay("2024-03-01"))
        assertEquals("2023-02-28", GoalMath.previousDay("2023-03-01"))
    }

    @Test fun previousDayRejectsGarbage() {
        assertNull(GoalMath.previousDay(""))
        assertNull(GoalMath.previousDay("2024-13-01"))
        assertNull(GoalMath.previousDay("不是日期"))
    }

    /* ---------------- 连续天数 ---------------- */

    @Test fun streakCountsToday() {
        val goal = ReadingGoal(enabled = true, minutes = 30)
        val d = days("2024-05-01" to 1800L, "2024-05-02" to 1800L, "2024-05-03" to 1800L)
        assertEquals(3, GoalMath.streak(d, goal, "2024-05-03"))
    }

    @Test fun streakFallsBackToYesterdayWhenTodayUnmet() {
        val goal = ReadingGoal(enabled = true, minutes = 30)
        val d = days("2024-05-01" to 1800L, "2024-05-02" to 1800L, "2024-05-03" to 60L)
        assertEquals(2, GoalMath.streak(d, goal, "2024-05-03"))
    }

    @Test fun streakBreaksOnMissedDay() {
        val goal = ReadingGoal(enabled = true, minutes = 30)
        val d = days("2024-05-01" to 1800L, "2024-05-03" to 1800L)
        assertEquals(1, GoalMath.streak(d, goal, "2024-05-03"))
    }

    @Test fun streakIsZeroWithNoHistory() {
        assertEquals(0, GoalMath.streak(JSONObject(), ReadingGoal(enabled = true), "2024-05-03"))
        assertEquals(0, GoalMath.streak(null, ReadingGoal(enabled = true), "2024-05-03"))
        assertEquals(0, GoalMath.streak(days("2024-05-03" to 9999L), ReadingGoal(enabled = false), "2024-05-03"))
    }

    @Test fun streakUsesCurrentGoalRatherThanHistory() {
        // 历史每天 10 分钟达标了旧目标；把目标提到 30 分钟后不再算连续。
        val d = days("2024-05-01" to 600L, "2024-05-02" to 600L, "2024-05-03" to 600L)
        assertEquals(3, GoalMath.streak(d, ReadingGoal(enabled = true, minutes = 10), "2024-05-03"))
        assertEquals(0, GoalMath.streak(d, ReadingGoal(enabled = true, minutes = 30), "2024-05-03"))
    }

    /* ---------------- 进度与展示 ---------------- */

    @Test fun progressClampsToOne() {
        val goal = ReadingGoal(enabled = true, minutes = 30)
        assertEquals(0.5f, GoalMath.progress(days("2024-05-03" to 900L), goal, "2024-05-03"), 0.001f)
        assertEquals(1f, GoalMath.progress(days("2024-05-03" to 9999L), goal, "2024-05-03"), 0.001f)
        assertEquals(0f, GoalMath.progress(days("2024-05-03" to 900L), ReadingGoal(enabled = false), "2024-05-03"), 0.001f)
    }

    @Test fun lastDaysPadsMissingDays() {
        val got = GoalMath.lastDays(days("2024-05-01" to 60L, "2024-05-03" to 120L), "2024-05-03", 3)
        assertEquals(listOf("2024-05-01" to 60L, "2024-05-02" to 0L, "2024-05-03" to 120L), got)
    }

    @Test fun lastDaysFromEmptyWindow() {
        assertEquals(0, GoalMath.lastDays(null, "2024-05-03", 0).size)
        assertEquals(2, GoalMath.lastDays(null, "2024-05-03", 2).size)
    }

    /* ---------------- 落盘 ---------------- */

    @Test fun goalSurvivesRoundTrip() {
        val root = File(temp.newFolder(), "offline")
        assertTrue(OfflineRepo(root, temp.newFolder()).saveReadingGoal(ReadingGoal(enabled = true, minutes = 45)))

        val back = OfflineRepo(root, temp.newFolder()).readingGoal()
        assertTrue(back.enabled)
        assertEquals(45, back.minutes)
    }

    @Test fun goalDefaultsWhenAbsent() {
        val goal = repo().readingGoal()
        assertFalse(goal.enabled)
        assertEquals(GoalMath.DEFAULT_MINUTES, goal.minutes)
    }

    @Test fun goalIsClampedWhenSaved() {
        val root = File(temp.newFolder(), "offline")
        OfflineRepo(root, temp.newFolder()).saveReadingGoal(ReadingGoal(enabled = true, minutes = 100000))
        assertEquals(GoalMath.MAX_MINUTES, OfflineRepo(root, temp.newFolder()).readingGoal().minutes)
    }

    @Test fun addStatsAccumulatesTodayUnderOneKey() {
        val repo = repo()
        val today = GoalMath.dayKey()
        assertTrue(repo.addStats(120, 10, 1))
        assertTrue(repo.addStats(60, 5, 1))
        val days = repo.stats().optJSONObject("days")
        assertEquals(180L, GoalMath.daySeconds(days, today))
        assertEquals(1, days.length())
    }
}
