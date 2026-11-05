package com.alanmaster.xread.data

import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/** 每日阅读目标。关闭时仍保留 [minutes]，重新打开不必再设一次。 */
data class ReadingGoal(
    val enabled: Boolean = false,
    val minutes: Int = GoalMath.DEFAULT_MINUTES,
) {
    fun normalized(): ReadingGoal = copy(minutes = minutes.coerceIn(GoalMath.MIN_MINUTES, GoalMath.MAX_MINUTES))
}

/**
 * 阅读目标与连续天数的计算。
 *
 * 全部基于 stats.json 里已有的 `days`（本地日期 → 秒），**不另存 streak**：
 * 另存一份就必然出现「天数文件说 5 天、days 里只有 3 天达标」的双份真相。
 * 代价是改目标后历史按新目标重算，这是有意的——目标是「每天读够 N 分钟」，
 * 以前按 10 分钟达标的日子放到 30 分钟口径下本来就不算达标。
 */
object GoalMath {
    const val MIN_MINUTES = 5
    const val MAX_MINUTES = 240
    const val DEFAULT_MINUTES = 30

    /** 往回数的最远天数：防止 days 里出现异常键时死循环。 */
    private const val MAX_LOOKBACK = 3650

    private const val PATTERN = "yyyy-MM-dd"

    fun dayKey(millis: Long = System.currentTimeMillis()): String =
        SimpleDateFormat(PATTERN, Locale.US).format(Date(millis))

    /** 前一天；键不是合法日期时返回 null。 */
    fun previousDay(key: String): String? {
        val fmt = SimpleDateFormat(PATTERN, Locale.US).apply { isLenient = false }
        val date = runCatching { fmt.parse(key) }.getOrNull() ?: return null
        val cal = Calendar.getInstance().apply {
            time = date
            add(Calendar.DAY_OF_YEAR, -1)
        }
        return fmt.format(cal.time)
    }

    fun goalSeconds(goal: ReadingGoal): Long =
        if (goal.enabled) goal.normalized().minutes.toLong() * 60L else 0L

    fun daySeconds(days: JSONObject?, key: String): Long =
        (days?.optLong(key) ?: 0L).coerceAtLeast(0L)

    fun metOn(days: JSONObject?, goal: ReadingGoal, key: String): Boolean {
        val target = goalSeconds(goal)
        return target > 0 && daySeconds(days, key) >= target
    }

    /**
     * 连续达标天数。
     *
     * 今天还没达标时**从昨天往回数**：早上打开应用就看到「连续 0 天」会把昨晚的努力
     * 一笔勾销，而实际上今天还没结束。今天达标则包含今天。
     */
    fun streak(days: JSONObject?, goal: ReadingGoal, today: String): Int {
        if (goalSeconds(goal) <= 0) return 0
        var cursor = if (metOn(days, goal, today)) today else previousDay(today) ?: return 0
        var count = 0
        while (count < MAX_LOOKBACK && metOn(days, goal, cursor)) {
            count++
            cursor = previousDay(cursor) ?: break
        }
        return count
    }

    /** 最近 [size] 个自然日（含今天），缺记录的日子补 0；旧的在前。 */
    fun lastDays(days: JSONObject?, today: String, size: Int): List<Pair<String, Long>> {
        if (size <= 0) return emptyList()
        val keys = ArrayDeque<String>()
        var cursor: String? = today
        while (keys.size < size && cursor != null) {
            keys.addFirst(cursor)
            cursor = previousDay(cursor)
        }
        return keys.map { it to daySeconds(days, it) }
    }

    /** 今日完成度 0..1；目标关闭时为 0。 */
    fun progress(days: JSONObject?, goal: ReadingGoal, today: String): Float {
        val target = goalSeconds(goal)
        if (target <= 0) return 0f
        return (daySeconds(days, today).toFloat() / target.toFloat()).coerceIn(0f, 1f)
    }
}
