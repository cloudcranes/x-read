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
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * 朗读前台服务：让系统 TTS 在离开阅读页、锁屏后继续读，并提供通知栏控制。
 *
 * 与 [PlaybackService] 同构：服务只管通知与生命周期，真正的朗读器是进程级
 * [TtsHolder]，所以界面与服务操作的是同一个 TextToSpeech 实例。
 */
class TtsService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var observeJob: Job? = null
    private var idleJob: Job? = null
    private var foreground = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        TtsHolder.install(this)
        ensureChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_TOGGLE -> TtsHolder.controller()?.let { if (it.speakingFlow.value) it.pause() else it.resume() }
            ACTION_STOP -> {
                TtsHolder.controller()?.stop()
                stopSelfSafely()
                return START_NOT_STICKY
            }
        }
        observe()
        return START_STICKY
    }

    private fun observe() {
        if (observeJob != null) return
        val controller = TtsHolder.controller() ?: return
        observeJob = scope.launch {
            controller.speakingFlow.collectLatest { speaking ->
                if (speaking) {
                    idleJob?.cancel()
                    idleJob = null
                    startForegroundSafely()
                } else {
                    // 章与章之间会短暂为 false（自动续章要等新正文），留 6 秒缓冲
                    if (idleJob != null) return@collectLatest
                    idleJob = scope.launch {
                        delay(IDLE_TIMEOUT_MS)
                        if (!controller.speakingFlow.value) stopSelfSafely()
                    }
                }
            }
        }
    }

    private fun startForegroundSafely() {
        if (foreground) return
        val controller = TtsHolder.controller() ?: return
        val notification = buildNotification(controller.speakingFlow.value, controller.messageFlow.value)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(NOTIFICATION_ID, notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
            foreground = true
        } catch (e: Exception) {
            // Android 12+ 后台起前台服务受限：降级为普通通知，不崩溃
            runCatching { notificationManager()?.notify(NOTIFICATION_ID, notification) }
            android.util.Log.w("xread-tts", "startForeground failed: ${e.javaClass.simpleName}")
        }
    }

    private fun stopSelfSafely() {
        observeJob?.cancel(); observeJob = null
        idleJob?.cancel(); idleJob = null
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

    private fun buildNotification(speaking: Boolean, message: String): Notification {
        val open = PendingIntent.getActivity(this, 0,
            Intent(this, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            },
            pendingFlags())
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_playback)
            .setContentTitle(if (speaking) "朗读中" else "朗读已暂停")
            .setContentText(message.ifEmpty { "系统 TTS" })
            .setContentIntent(open)
            .setOngoing(speaking)
            .setOnlyAlertOnce(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .addAction(0, if (speaking) "暂停" else "继续", action(ACTION_TOGGLE, 1))
            .addAction(0, "停止", action(ACTION_STOP, 2))
            .build()
    }

    private fun action(action: String, requestCode: Int): PendingIntent {
        val intent = Intent(this, TtsService::class.java).setAction(action)
        return PendingIntent.getService(this, requestCode, intent, pendingFlags())
    }

    private fun pendingFlags(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }

    private fun notificationManager(): NotificationManager? =
        getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = notificationManager() ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.tts_channel),
            NotificationManager.IMPORTANCE_LOW,
        ).apply { setShowBadge(false) }
        manager.createNotificationChannel(channel)
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val CHANNEL_ID = "xread_tts"
        private const val NOTIFICATION_ID = 0x7EA2
        private const val IDLE_TIMEOUT_MS = 6_000L
        private const val ACTION_TOGGLE = "com.alanmaster.xread.action.TTS_TOGGLE"
        private const val ACTION_STOP = "com.alanmaster.xread.action.TTS_STOP"

        fun start(context: Context) {
            val intent = Intent(context, TtsService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
    }
}
