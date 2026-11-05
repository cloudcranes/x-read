package com.alanmaster.xread.data

/**
 * 进程内共享的音频控制器。
 *
 * 前台服务与 Compose 界面必须操作**同一个** MediaPlayer，否则通知栏的
 * 暂停/停止会作用在一个已被回收的实例上。Activity 重建也不应中断播放。
 */
object AudioPlaybackHolder {
    @Volatile
    private var instance: AudioPlayerController? = null
    @Volatile
    private var appContext: android.content.Context? = null

    /**
     * 由 Application/Activity 尽早注入应用上下文。
     *
     * 控制器需要它来申请音频焦点；若注入晚于首次 [controller] 调用，
     * 会重建控制器（此时还没有开始播放，重建安全）。
     */
    fun install(context: android.content.Context) {
        val app = context.applicationContext
        if (appContext === app && instance != null) return
        synchronized(this) {
            if (appContext !== app) {
                appContext = app
                if (instance != null) {
                    instance?.shutdown()
                    instance = null
                }
            }
        }
    }

    fun controller(): AudioPlayerController =
        instance ?: synchronized(this) {
            instance ?: AudioPlayerController(appContext = appContext).also { instance = it }
        }

    /** 通知栏「上一集/下一集」由界面注册处理（服务拿不到章节清单）。 */
    @Volatile
    var command: ((String) -> Unit)? = null

    /** 仅用于测试或彻底退出时丢弃实例。 */
    fun reset() = synchronized(this) {
        instance?.shutdown()
        instance = null
    }
}
