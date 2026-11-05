package com.alanmaster.xread.ui

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 阅读页左下角的钟。
 *
 * 纯函数，不碰 Compose：这样单测能直接断言，不必上 Robolectric。
 */
internal object ReaderClock {

    // ponytail: SimpleDateFormat 不是线程安全的，这里每次现建一个（每秒调一次，成本可忽略）；要跨线程就换 DateTimeFormatter（需开 desugaring）。

    /** 毫秒时间戳 → "HH:mm"。 */
    fun format(millis: Long): String = SimpleDateFormat("HH:mm", Locale.US).format(Date(millis))

    /**
     * 距离下一个整秒还差多少毫秒。
     *
     * 对齐整秒而不是整分钟：每拍都按当前时刻重算，唤醒抖动不会累积；
     * 万一某一拍被吞掉，最长也只差 1 秒就补上，不会像分钟粒度那样整整慢一分钟。
     */
    fun millisToNextSecond(millis: Long): Long = 1_000L - (millis % 1_000L)
}
