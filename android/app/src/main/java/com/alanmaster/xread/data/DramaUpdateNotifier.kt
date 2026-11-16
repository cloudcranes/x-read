package com.alanmaster.xread.data

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.alanmaster.xread.MainActivity
import com.alanmaster.xread.R

/**
 * 短剧追更通知。
 *
 * 只在本机订阅数据里已经有「上次看到的集数」、而 feed 刷新带回更大的集数时发一条，
 * 同一渠道复用新章提醒的 `xread_updates`，用户可以在设置里统一关掉。
 */
object DramaUpdateNotifier {

    private const val CHANNEL_ID = NotificationChannels.UPDATES
    private const val NOTIFICATION_ID = 0x7EA4

    fun notify(context: Context, updated: List<DramaUpdateNotice>) {
        if (updated.isEmpty()) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            manager.getNotificationChannel(CHANNEL_ID) == null
        ) {
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    context.getString(R.string.update_channel),
                    NotificationManager.IMPORTANCE_DEFAULT,
                )
            )
        }
        val total = updated.sumOf { it.newEpisodes }.coerceAtLeast(updated.size)
        val head = updated.take(3).joinToString("、") { "《${it.series.title}》" }
        val text = if (updated.size > 3) "$head 等 ${updated.size} 部短剧共更新 $total 集"
        else "$head 共更新 $total 集"
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        val open = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            },
            flags,
        )
        android.util.Log.i("DramaNotify", "post ${updated.size} notice(s), total=$total")
        manager.notify(
            NOTIFICATION_ID,
            NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_playback)
                .setContentTitle("短剧有更新了")
                .setContentText(text)
                .setStyle(NotificationCompat.BigTextStyle().bigText(text))
                .setContentIntent(open)
                .setAutoCancel(true)
                .build(),
        )
    }
}
