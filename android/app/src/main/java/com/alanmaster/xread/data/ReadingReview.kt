package com.alanmaster.xread.data

import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/**
 * 报告期：某个月（[month] 非空）或某一年（[month] 为 null）。
 *
 * 只保留「年月」两件事，不存起止时间戳——stats.days 的键本来就是本地日期字符串
 * `yyyy-MM-dd`，用前缀匹配最直接，也不会因为时区偏移把某天算到隔壁月。
 */
data class ReportPeriod(val year: Int, val month: Int? = null) {
    val yearly: Boolean get() = month == null

    /** `2026-11` 或 `2026`，用于匹配 `stats.days` 的键。 */
    val prefix: String
        get() = month?.let { String.format(Locale.US, "%04d-%02d", year, it) }
            ?: String.format(Locale.US, "%04d", year)

    /** 页头用的短标签：`11 月` / `2026 年`。 */
    val label: String
        get() = month?.let { "$it 月" } ?: "$year 年"

    /** 画在卡片上的全称：`2026 年 11 月` / `2026 年`。 */
    val longLabel: String
        get() = month?.let { "$year 年 $it 月" } ?: "$year 年"

    fun previous(): ReportPeriod = when {
        yearly -> ReportPeriod(year - 1)
        month == 1 -> ReportPeriod(year - 1, 12)
        else -> ReportPeriod(year, (month ?: 1) - 1)
    }

    fun next(): ReportPeriod = when {
        yearly -> ReportPeriod(year + 1)
        month == 12 -> ReportPeriod(year + 1, 1)
        else -> ReportPeriod(year, (month ?: 12) + 1)
    }
}

/** 月视图网格里的一格。[inPeriod] 为 false 的是补位空格。 */
data class ReportDay(
    val key: String,
    val dayOfMonth: Int,
    val seconds: Long,
    val inPeriod: Boolean,
)

/** 年视图里的一根柱。 */
data class ReportMonth(
    val month: Int,
    val seconds: Long,
    val activeDays: Int,
) {
    val label: String get() = "$month"
}

/** 一段时间内的汇总，UI 与分享卡都只读这个。 */
data class ReportSummary(
    val period: ReportPeriod,
    val totalSeconds: Long,
    val activeDays: Int,
    val bestDayKey: String?,
    val bestDaySeconds: Long,
    /** 期内最长的「连续有阅读的日子」，按天算，不看目标。 */
    val longestStreak: Int,
    /** 开了目标时，达标的（在期内）天数。 */
    val metDays: Int,
    /** 期内有阅读的日子，按日期升序。 */
    val readingDays: List<Pair<String, Long>>,
) {
    val hours: Double get() = totalSeconds / 3600.0
    val averageMinutesPerActiveDay: Int
        get() = if (activeDays == 0) 0 else (totalSeconds / activeDays / 60).toInt()
}

/**
 * 月报 / 年度报告的数据聚合。
 *
 * 只做纯计算，不碰文件也不碰 Compose：`stats.days` 进，[ReportSummary] 出，
 * 这样「闰年二月」「跨年 12 月 → 次年 1 月」「目标中途改过」这些边界都能在
 * 单测里钉死（见 `ReadingReviewTest`）。
 *
 * 与 [GoalMath] 一样不用 `java.time`：minSdk 24 且没开 desugaring，
 * 全仓统一走 [Calendar] + [SimpleDateFormat]。
 */
object ReadingReview {
    private const val DAY_PATTERN = "yyyy-MM-dd"

    /** 某个时刻所在的月份。 */
    fun currentPeriod(now: Long = System.currentTimeMillis()): ReportPeriod {
        val cal = Calendar.getInstance().apply { timeInMillis = now }
        return ReportPeriod(cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1)
    }

    /** 期内有阅读（>0 秒）的日子，键升序。 */
    fun readingDays(days: JSONObject?, period: ReportPeriod): List<Pair<String, Long>> {
        val keys = days?.keys() ?: return emptyList()
        val out = ArrayList<Pair<String, Long>>()
        while (keys.hasNext()) {
            val key = keys.next()
            if (!key.startsWith(period.prefix)) continue
            val v = days.optLong(key)
            if (v > 0) out.add(key to v)
        }
        out.sortBy { it.first }
        return out
    }

    fun summarize(
        days: JSONObject?,
        period: ReportPeriod,
        goal: ReadingGoal = ReadingGoal(),
    ): ReportSummary {
        val entries = readingDays(days, period)
        val total = entries.sumOf { it.second }
        val best = entries.maxByOrNull { it.second }
        val met = if (goal.enabled) {
            entries.count { (key, v) -> GoalMath.metOn(days, goal, key) && v > 0 }
        } else {
            0
        }
        return ReportSummary(
            period = period,
            totalSeconds = total,
            activeDays = entries.size,
            bestDayKey = best?.first,
            bestDaySeconds = best?.second ?: 0L,
            longestStreak = longestStreak(entries.map { it.first }),
            metDays = met,
            readingDays = entries,
        )
    }

    /**
     * 最长连续天数。
     *
     * 只认「有阅读的日子」而不是「达标的日子」：月报要回答的是
     * 「这个月有几天连着读」，目标是可以随时改的。
     */
    fun longestStreak(days: List<String>): Int {
        if (days.isEmpty()) return 0
        val sorted = days.sorted()
        var best = 1
        var run = 1
        for (i in 1 until sorted.size) {
            val prev = sorted[i - 1]
            val cur = sorted[i]
            run = if (cur == prev) run
            else if (GoalMath.previousDay(cur) == prev) run + 1
            else 1
            if (run > best) best = run
        }
        return best
    }

    /**
     * 月视图网格：周一开头，前面补空格让 1 号落在正确的星期列，末尾补到整周。
     *
     * 返回长度是 7 的倍数（通常是 35 或 42 格），UI 直接按 7 列铺即可，
     * 不需要在 Composable 里再算一次日历。
     */
    fun monthGrid(days: JSONObject?, year: Int, month: Int): List<ReportDay> {
        val cal = Calendar.getInstance().apply {
            clear()
            set(year, month - 1, 1)
        }
        val daysInMonth = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
        // Calendar.DAY_OF_WEEK：周日=1…周六=7；换成周一=0…周日=6
        val leading = (cal.get(Calendar.DAY_OF_WEEK) + 5) % 7
        val cells = ArrayList<ReportDay>(leading + daysInMonth + 6)
        repeat(leading) { cells.add(ReportDay("", 0, 0L, inPeriod = false)) }
        val fmt = SimpleDateFormat(DAY_PATTERN, Locale.US)
        for (d in 1..daysInMonth) {
            cal.set(year, month - 1, d)
            val key = fmt.format(cal.time)
            cells.add(
                ReportDay(
                    key = key,
                    dayOfMonth = d,
                    seconds = (days?.optLong(key) ?: 0L).coerceAtLeast(0L),
                    inPeriod = true,
                )
            )
        }
        while (cells.size % 7 != 0) cells.add(ReportDay("", 0, 0L, inPeriod = false))
        return cells
    }

    /** 年视图：固定 12 根柱，没有记录的日子算 0，界面自己决定要不要画。 */
    fun yearMonths(days: JSONObject?, year: Int): List<ReportMonth> = (1..12).map { m ->
        val period = ReportPeriod(year, m)
        val entries = readingDays(days, period)
        ReportMonth(
            month = m,
            seconds = entries.sumOf { it.second },
            activeDays = entries.size,
        )
    }

    /** 某天是周几（周一=0…周日=6）；给日历表头用。 */
    fun weekdayIndex(year: Int, month: Int, day: Int): Int {
        val cal = Calendar.getInstance().apply {
            clear()
            set(year, month - 1, day)
        }
        return (cal.get(Calendar.DAY_OF_WEEK) + 5) % 7
    }
}
