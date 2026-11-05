package com.alanmaster.xread.data

/**
 * 在读人数的显示格式。
 *
 * 界面上这个数字要和排名、章数排成一列右对齐，所以格式必须稳定、可预测：
 *  - 一万以下：原样（`999`）
 *  - 一万到一亿：一位小数的「万」（`59.8 万`）
 *  - 一亿以上：一位小数的「亿」（`1.3 亿`）
 *
 * 用 Long 而不是 Double 做主体运算：`read_count` 上游给的是字符串整数，
 * 转 Double 过一遍会在亿级丢精度。
 *
 * **取一位小数时是截断，不是四舍五入**：597999 显示 `59.7 万`（真值 59.7999）。
 * 线上数字宁可略小于真实值，也不要把一本 59.0 万的书显示成 59.1 万。
 */
object ReadCountFormat {
    private const val TEN_THOUSAND = 10_000L
    private const val HUNDRED_MILLION = 100_000_000L

    fun short(count: Long): String {
        val n = count.coerceAtLeast(0L)
        return when {
            n < TEN_THOUSAND -> n.toString()
            n < HUNDRED_MILLION -> decimal(n, TEN_THOUSAND) + " 万"
            else -> decimal(n, HUNDRED_MILLION) + " 亿"
        }
    }

    /** 保留一位小数（截断），整数部分用整除避免浮点误差。 */
    private fun decimal(n: Long, unit: Long): String {
        val whole = n / unit
        val tenth = (n % unit) * 10 / unit
        return "$whole.$tenth"
    }
}
