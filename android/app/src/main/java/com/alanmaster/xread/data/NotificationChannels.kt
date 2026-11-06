package com.alanmaster.xread.data

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import com.alanmaster.xread.R

/**
 * 通知渠道的单一入口。
 *
 * 四个渠道过去各自在服务 / 提醒 / 检查更新里建，设置页看不到全貌，
 * 想关掉「朗读控制条」得先知道它是个渠道，再翻到系统设置里找。
 * 这里集中声明 id、默认重要级别与文案，设置页据此渲染一组开关。
 */
object NotificationChannels {

    const val PLAYBACK = "xread_playback"
    const val TTS = "xread_tts"
    const val UPDATES = "xread_updates"
    const val REMINDER = "xread_reminder"

    data class Entry(
        val id: String,
        val title: String,
        val description: String,
        val importance: Int,
    )

    fun entries(context: Context): List<Entry> = listOf(
        Entry(
            id = PLAYBACK,
            title = context.getString(R.string.playback_channel),
            description = context.getString(R.string.playback_channel_desc),
            importance = NotificationManager.IMPORTANCE_LOW,
        ),
        Entry(
            id = TTS,
            title = context.getString(R.string.tts_channel),
            description = context.getString(R.string.tts_channel_desc),
            importance = NotificationManager.IMPORTANCE_LOW,
        ),
        Entry(
            id = UPDATES,
            title = context.getString(R.string.update_channel),
            description = context.getString(R.string.update_channel_desc),
            importance = NotificationManager.IMPORTANCE_DEFAULT,
        ),
        Entry(
            id = REMINDER,
            title = context.getString(R.string.reminder_channel),
            description = context.getString(R.string.reminder_channel_desc),
            importance = NotificationManager.IMPORTANCE_DEFAULT,
        ),
    )

    /** 建齐尚未存在的渠道；已存在的不动用户改过的重要性。 */
    fun ensure(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = manager(context) ?: return
        entries(context).forEach { entry ->
            if (manager.getNotificationChannel(entry.id) == null) {
                manager.createNotificationChannel(channelOf(entry))
            }
        }
    }

    fun isEnabled(context: Context, id: String): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return manager(context)?.areNotificationsEnabled() ?: false
        }
        val channel = manager(context)?.getNotificationChannel(id) ?: return true
        return channel.importance != NotificationManager.IMPORTANCE_NONE
    }

    /**
     * 渠道开 / 关。返回操作后渠道的真实状态：部分机型在用户手动关闭过渠道后
     * 会忽略应用侧的再次开启，这时开关要如实回弹，而不是假装打开。
     */
    fun setEnabled(context: Context, id: String, enabled: Boolean): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return enabled
        val manager = manager(context) ?: return enabled
        val entry = entries(context).firstOrNull { it.id == id } ?: return enabled
        val existing = manager.getNotificationChannel(id)
        val channel = existing ?: channelOf(entry)
        channel.importance = if (enabled) entry.importance else NotificationManager.IMPORTANCE_NONE
        manager.createNotificationChannel(channel)
        return isEnabled(context, id)
    }

    /** 跳到系统里这个渠道的设置页；渠道没建过时退回应用通知设置。 */
    fun openSystemSettings(context: Context, id: String) {
        val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && id.isNotEmpty()) {
            Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                .putExtra(Settings.EXTRA_CHANNEL_ID, id)
        } else {
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
        }
        runCatching { context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }

    private fun channelOf(entry: Entry) = NotificationChannel(
        entry.id,
        entry.title,
        entry.importance,
    ).apply {
        description = entry.description
        if (entry.importance <= NotificationManager.IMPORTANCE_LOW) setShowBadge(false)
    }

    private fun manager(context: Context): NotificationManager? =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
}
