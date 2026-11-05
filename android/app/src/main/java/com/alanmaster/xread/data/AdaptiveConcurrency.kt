package com.alanmaster.xread.data

/**
 * 自适应并发窗口（AIMD）：
 * - 连续 [permits] 次成功后 +1（加性增），上限 [max]；
 * - 任一次失败则减半（乘性减），下限 [min]；
 * - 连续失败递增退避，避免在服务端排队或网络抖动时硬撞。
 *
 * 纯 Kotlin，无 Android 依赖，可用 JVM 单测直接验证。
 */
class AdaptiveConcurrency(
    start: Int = DEFAULT_START,
    val min: Int = DEFAULT_MIN,
    val max: Int = DEFAULT_MAX,
) {
    init {
        require(min >= 1) { "min must be >= 1" }
        require(max >= min) { "max must be >= min" }
    }

    @Volatile
    var permits: Int = start.coerceIn(min, max)
        private set

    var successes: Int = 0
        private set

    var failures: Int = 0
        private set

    private var streak = 0
    private var consecutiveFailures = 0

    /** 记录一次成功；返回调整后的窗口。 */
    fun recordSuccess(): Int {
        successes++
        streak++
        consecutiveFailures = 0
        if (streak >= permits) {
            permits = (permits + 1).coerceAtMost(max)
            streak = 0
        }
        return permits
    }

    /** 记录一次失败；返回调整后的窗口。 */
    fun recordFailure(): Int {
        failures++
        streak = 0
        consecutiveFailures++
        permits = (permits / 2).coerceAtLeast(min)
        return permits
    }

    /** 当前应等待的退避毫秒：连续失败 2 次起，每多一次翻倍，封顶 [MAX_BACKOFF_MS]。 */
    val backoffMs: Long
        get() = when {
            consecutiveFailures < 2 -> 0L
            else -> {
                val shift = (consecutiveFailures - 2).coerceAtMost(4)
                (BASE_BACKOFF_MS shl shift).coerceAtMost(MAX_BACKOFF_MS)
            }
        }

    fun summary(): String =
        "自适应 $permits 并发 · 成功 $successes · 失败 $failures"

    companion object {
        const val DEFAULT_START = 4
        const val DEFAULT_MIN = 1
        const val DEFAULT_MAX = 8
        const val BASE_BACKOFF_MS = 400L
        const val MAX_BACKOFF_MS = 4000L
    }
}
