package com.alanmaster.xread.data

import android.content.Context

/**
 * TTS 的进程级持有者。
 *
 * 旧实现把 [TtsController] 建在 Compose 的 `remember` 里，并在 `onDispose` 里
 * `shutdown()` —— 一离开阅读页（或 Activity 被销毁）朗读就断。改为与听书播放器
 * 同样的做法：进程单例 + 前台服务托管，界面只挂接回调。
 *
 * 回调通过可变槽位转发，避免把 ViewModel 生命周期绑死在控制器上：界面销毁时
 * 置空，控制器继续朗读但不再回调已失效的作用域。
 */
object TtsHolder {
    private var instance: TtsController? = null

    var onParagraph: ((Int) -> Unit)? = null
    var onChapterFinished: (() -> Unit)? = null

    @Synchronized
    fun install(context: Context): TtsController {
        instance?.let { return it }
        val created = TtsController(
            context.applicationContext,
            onProgress = { idx -> onParagraph?.invoke(idx) },
            onChapterFinished = { onChapterFinished?.invoke() },
            onStateChanged = { _, _ -> },
        )
        instance = created
        return created
    }

    fun controller(): TtsController? = instance
}
