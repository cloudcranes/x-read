package com.alanmaster.xread.data

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.alanmaster.xread.MainActivity
import com.alanmaster.xread.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * 前台播放服务：让真人音频在离开听书页、锁屏后继续播，并提供通知栏控制。
 *
 * 只负责「通知 + 生命周期」，真正的播放器是进程级单例 [AudioPlaybackHolder]，
 * 因此界面与服务操作的是同一个 MediaPlayer。
 */
class PlaybackService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var observeJob: Job? = null
    private var foreground = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        // 服务可能先于 Activity 被拉起（系统重启后恢复播放），这里也要注入
        AudioPlaybackHolder.install(this)
        ensureChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        when (action) {
            ACTION_TOGGLE -> AudioPlaybackHolder.controller().toggle()
            ACTION_STOP -> {
                AudioPlaybackHolder.controller().stop()
                stopSelfSafely()
                return START_NOT_STICKY
            }
            ACTION_PREV, ACTION_NEXT -> {
                // 切集需要章节清单，交由界面侧注册的回调处理；服务只转发
                AudioPlaybackHolder.command?.invoke(action)
            }
        }
        val controller = AudioPlaybackHolder.controller()
        val state = controller.state.value
        if (state.prepared) {
            startForegroundSafely(state)
            observe()
        } else if (!state.playing) {
            stopSelfSafely()
            return START_NOT_STICKY
        }
        return START_NOT_STICKY
    }

    /**
     * 空闲多久才真正收起通知。
     *
     * 连播时「上一集结束 → 下一集 prepare 完成」之间必然有一段
     * `prepared=false && playing=false`，若立刻退出前台就会把新一集打死。
     * 5 秒足够覆盖一次网络取址，又不会让停止后的通知长期滞留。
     */
    private val idleTimeoutMs = 5_000L
    private var idleJob: Job? = null

    private fun observe() {
        if (observeJob != null) return
        val controller = AudioPlaybackHolder.controller()
        observeJob = scope.launch {
            controller.state.collectLatest { st ->
                if (st.prepared || st.playing) {
                    idleJob?.cancel()
                    idleJob = null
                    startForegroundSafely(st)
                    notificationManager()?.notify(NOTIFICATION_ID, buildNotification(st))
                } else {
                    // 只有持续空闲才收尾；过渡态直接忽略
                    if (idleJob != null) return@collectLatest
                    idleJob = scope.launch {
                        kotlinx.coroutines.delay(idleTimeoutMs)
                        val now = controller.state.value
                        if (!now.prepared && !now.playing) stopSelfSafely()
                    }
                }
            }
        }
    }

    private fun startForegroundSafely(state: AudioPlayerController.State) {
        if (foreground) return
        val notification = buildNotification(state)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(NOTIFICATION_ID, notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
            foreground = true
        } catch (e: Exception) {
            // Android 12+ 后台启动前台服务受限；失败时降级为普通通知，不崩溃
            runCatching { notificationManager()?.notify(NOTIFICATION_ID, notification) }
            android.util.Log.w("xread-playback", "startForeground failed: ${e.javaClass.simpleName}")
        }
    }

    private fun stopSelfSafely() {
        observeJob?.cancel()
        observeJob = null
        if (foreground) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                stopForeground(STOP_FOREGROUND_REMOVE)
            } else {
                @Suppress("DEPRECATION")
                stopForeground(true)
            }
            foreground = false
        } else {
            notificationManager()?.cancel(NOTIFICATION_ID)
        }
        stopSelf()
    }

    private fun notificationManager(): NotificationManager? =
        getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = notificationManager() ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.playback_channel),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = getString(R.string.playback_channel_desc)
            setShowBadge(false)
        }
        manager.createNotificationChannel(channel)
    }

    private fun buildNotification(state: AudioPlayerController.State): Notification {
        val open = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            pendingFlags(),
        )
        val toggleIcon = if (state.playing) R.drawable.ic_playback_pause else R.drawable.ic_playback_play
        val toggleLabel = if (state.playing) "暂停" else "播放"
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_playback)
            .setContentTitle(state.title.ifEmpty { "听书" })
            .setContentText(
                if (state.durationMs > 0) {
                    "${AudioPlayerController.formatTime(state.positionMs)} / " +
                        AudioPlayerController.formatTime(state.durationMs) +
                        " · ${state.rate}x"
                } else "准备中…"
            )
            .setContentIntent(open)
            .setOngoing(state.playing)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .addAction(R.drawable.ic_playback_prev, "上一集", action(ACTION_PREV, 3))
            .addAction(toggleIcon, toggleLabel, action(ACTION_TOGGLE, 1))
            .addAction(R.drawable.ic_playback_next, "下一集", action(ACTION_NEXT, 4))
            .addAction(R.drawable.ic_playback_stop, "停止", action(ACTION_STOP, 2))
        return builder.build()
    }

    private fun action(action: String, requestCode: Int): PendingIntent {
        val intent = Intent(this, PlaybackService::class.java).setAction(action)
        return PendingIntent.getService(this, requestCode, intent, pendingFlags())
    }

    private fun pendingFlags(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }

    override fun onDestroy() {
        idleJob?.cancel()
        observeJob?.cancel()
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        const val ACTION_TOGGLE = "com.alanmaster.xread.action.TOGGLE"
        const val ACTION_STOP = "com.alanmaster.xread.action.STOP"
        const val ACTION_PREV = "com.alanmaster.xread.action.PREV"
        const val ACTION_NEXT = "com.alanmaster.xread.action.NEXT"
        private const val CHANNEL_ID = "xread_playback"
        private const val NOTIFICATION_ID = 0x7EAD

        /** 由界面注册：通知栏上一条/下一条需要章节清单，服务自己拿不到。 */
        fun start(context: Context) {
            val intent = Intent(context, PlaybackService::class.java)
            runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            }
        }

        fun stop(context: Context) {
            runCatching {
                context.startService(
                    Intent(context, PlaybackService::class.java).setAction(ACTION_STOP))
            }
        }
    }
}
