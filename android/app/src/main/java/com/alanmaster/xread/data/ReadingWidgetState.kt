package com.alanmaster.xread.data

import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * 桌面「继续阅读」小组件要显示的快照。
 *
 * 纯数据 + 纯函数，不碰 Android 框架：小组件在没有 Activity 的进程里刷新，
 * 能离线跑的部分必须能脱离设备验证。
 */
data class ReadingWidgetState(
    val bookId: String,
    val title: String,
    val author: String,
    val source: String,
    /** 0 起算的章节下标；-1 表示记录里没有章节号。 */
    val chapterIndex: Int,
    /** 上次阅读时间（毫秒）。 */
    val lastReadAt: Long,
) {
    /** 「第 12 章」；没有章节号时留空。 */
    val chapterLabel: String get() = if (chapterIndex >= 0) "第 ${chapterIndex + 1} 章" else ""
}

/**
 * 从 `progress.json` 里挑出最近读的那一本。
 *
 * 记录结构和 [OfflineRepo.saveProgress] 写的一致：每本书一个对象，
 * 含 `chapterId` / `offset` / `chapterIndex` / `at` / `title` / `author` /
 * `coverUrl` / `source`。缺 `chapterId` 或 `at` 的半截记录直接跳过——
 * 小组件不该拿一条残缺记录去显示。
 */
object ReadingWidgetStateParser {

    fun parse(progress: JSONObject): ReadingWidgetState? {
        var best: ReadingWidgetState? = null
        val keys = progress.keys()
        while (keys.hasNext()) {
            val id = keys.next()
            val entry = progress.optJSONObject(id) ?: continue
            val chapterId = entry.optString("chapterId")
            val at = entry.optLong("at", 0L)
            if (chapterId.isEmpty() || at <= 0L) continue
            val candidate = ReadingWidgetState(
                bookId = id,
                title = entry.optString("title"),
                author = entry.optString("author"),
                source = entry.optString("source").ifEmpty { "fanqie" },
                chapterIndex = entry.optInt("chapterIndex", -1),
                lastReadAt = at,
            )
            if (best == null || candidate.lastReadAt > best.lastReadAt) best = candidate
        }
        return best
    }
}

/**
 * 上次阅读时间的相对说法：今天 / 昨天 / N 天前 / 具体日期。
 *
 * 跨年时 `DAY_OF_YEAR` 不可比，直接落到日期说法，不去猜「多少天前」。
 */
fun relativeReadLabel(at: Long, now: Long = System.currentTimeMillis()): String {
    if (at <= 0L) return ""
    val clock = SimpleDateFormat("HH:mm", Locale.getDefault())
    val that = Calendar.getInstance().apply { timeInMillis = at }
    val today = Calendar.getInstance().apply { timeInMillis = now }
    val sameYear = today.get(Calendar.YEAR) == that.get(Calendar.YEAR)
    val dayDiff =
        if (sameYear) today.get(Calendar.DAY_OF_YEAR) - that.get(Calendar.DAY_OF_YEAR)
        else Int.MAX_VALUE
    return when {
        dayDiff == 0 -> "今天 ${clock.format(Date(at))}"
        dayDiff == 1 -> "昨天 ${clock.format(Date(at))}"
        dayDiff in 2..6 -> "${dayDiff}天前"
        else -> SimpleDateFormat("M月d日", Locale.getDefault()).format(Date(at))
    }
}
