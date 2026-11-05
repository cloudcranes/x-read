package com.alanmaster.xread.ui

import kotlinx.coroutines.flow.MutableSharedFlow

/**
 * 音量键/外设翻页指令。
 *
 * 音量键由 Activity 的 `dispatchKeyEvent` 捕获（Compose 的 onKeyEvent 拿不到被系统
 * 优先消费的音量键），再通过这里转给正在显示的阅读页；+1 下一页，-1 上一页。
 */
object ReaderPager {
    val commands = MutableSharedFlow<Int>(extraBufferCapacity = 8)

    /**
     * 阅读页是否可见且允许音量键翻页。由 UI 侧的 SideEffect 维护；
     * Activity 只读它，避免把 Compose 状态反推到 Activity 字段。
     */
    @Volatile var active: Boolean = false

    fun page(delta: Int) {
        if (delta != 0) commands.tryEmit(delta)
    }
}
