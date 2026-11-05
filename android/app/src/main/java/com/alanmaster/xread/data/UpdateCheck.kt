package com.alanmaster.xread.data

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.alanmaster.xread.MainActivity
import com.alanmaster.xread.R
import kotlinx.coroutines.runBlocking

/**
 * 新章定时检查。
 *
 * 不引入 WorkManager：构建环境的 Gradle 缓存里没有该依赖，加依赖会打断离线构建。
 * 用 `AlarmManager` 的**非精确**重复闹钟 + 显式广播即可：每天一次、允许系统合并，
 * 不需要 `SCHEDULE_EXACT_ALARM` 权限，也不需要额外 AAR。
 */
object UpdateScheduler {
    private const val REQUEST_CODE = 0x7EA3
    /** 每天 20:00 左右检查一次（非精确，系统会自行合并唤醒）。 */
    private const val HOUR = 20

    private fun pending(context: Context): PendingIntent {
        val intent = Intent(context, UpdateCheckReceiver::class.java)
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        return PendingIntent.getBroadcast(context, REQUEST_CODE, intent, flags)
    }

    fun schedule(context: Context) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val first = java.util.Calendar.getInstance().apply {
            set(java.util.Calendar.HOUR_OF_DAY, HOUR)
            set(java.util.Calendar.MINUTE, 0)
            set(java.util.Calendar.SECOND, 0)
            if (timeInMillis <= System.currentTimeMillis()) add(java.util.Calendar.DAY_OF_YEAR, 1)
        }
        runCatching {
            am.setInexactRepeating(AlarmManager.RTC_WAKEUP, first.timeInMillis,
                AlarmManager.INTERVAL_DAY, pending(context))
        }
    }

    fun cancel(context: Context) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        runCatching { am.cancel(pending(context)) }
    }
}

/** 收到闹钟后检查书架更新；只在确实有新章时发通知。 */
class UpdateCheckReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext
        val offline = OfflineRepo.of(app)
        if (!offline.newChapterNotifyEnabled()) return
        val pending = goAsync()
        Thread {
            try {
                val api = Api(app)
                val items = runBlocking { api.shelf() }
                if (items.isEmpty()) return@Thread
                val updates = runBlocking { api.checkUpdates(items) }
                val changed = items.filter { (updates[it.book.bookId] ?: 0) > 0 }
                if (changed.isEmpty()) return@Thread
                notify(app, changed.map { it.book.title }, changed.sumOf { updates[it.book.bookId] ?: 0 })
            } catch (e: Exception) {
                android.util.Log.w("xread-update", "check failed: ${e.javaClass.simpleName}")
            } finally {
                pending.finish()
            }
        }.start()
    }

    private fun notify(context: Context, titles: List<String>, newChapters: Int) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            manager.getNotificationChannel(CHANNEL_ID) == null
        ) {
            manager.createNotificationChannel(NotificationChannel(
                CHANNEL_ID, context.getString(R.string.update_channel),
                NotificationManager.IMPORTANCE_DEFAULT))
        }
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        val open = PendingIntent.getActivity(context, 0,
            Intent(context, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }, flags)
        val head = titles.take(3).joinToString("《", "《", "》") { it }
        val text = if (titles.size > 3) "$head 等 ${titles.size} 本共 $newChapters 章更新"
        else "$head 共 $newChapters 章更新"
        manager.notify(NOTIFICATION_ID,
            NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_playback)
                .setContentTitle("有书更新了")
                .setContentText(text)
                .setStyle(NotificationCompat.BigTextStyle().bigText(text))
                .setContentIntent(open)
                .setAutoCancel(true)
                .build())
    }

    companion object {
        private const val CHANNEL_ID = "xread_updates"
        private const val NOTIFICATION_ID = 0x7EA3
    }
}
