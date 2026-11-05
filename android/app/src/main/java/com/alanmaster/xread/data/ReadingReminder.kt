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
import org.json.JSONObject
import java.util.Calendar
import java.util.Locale

/**
 * 每日阅读提醒。
 *
 * 与新章提醒（[UpdateScheduler]）分开：那是「有内容可看」，这是「该坐下读一会儿了」，
 * 两者触发条件、文案、开关都不同，共用一个闹钟会互相顶掉。
 */
data class ReadingReminder(
    val enabled: Boolean = false,
    val hour: Int = DEFAULT_HOUR,
    val minute: Int = DEFAULT_MINUTE,
    /** 开了阅读目标后，达标当天就不再打扰。目标没开时这条规则无效。 */
    val onlyIfUnmet: Boolean = true,
) {
    fun normalized(): ReadingReminder = copy(hour = hour.coerceIn(0, 23), minute = minute.coerceIn(0, 59))

    fun label(): String = labelOf(hour, minute)

    fun toJson(): JSONObject = JSONObject()
        .put("enabled", enabled)
        .put("hour", hour)
        .put("minute", minute)
        .put("onlyIfUnmet", onlyIfUnmet)

    companion object {
        const val DEFAULT_HOUR = 20
        const val DEFAULT_MINUTE = 30

        fun labelOf(hour: Int, minute: Int): String =
            String.format(Locale.US, "%02d:%02d", hour.coerceIn(0, 23), minute.coerceIn(0, 59))

        fun from(o: JSONObject?): ReadingReminder {
            if (o == null) return ReadingReminder()
            return ReadingReminder(
                enabled = o.optBoolean("enabled", false),
                hour = o.optInt("hour", DEFAULT_HOUR),
                minute = o.optInt("minute", DEFAULT_MINUTE),
                onlyIfUnmet = o.optBoolean("onlyIfUnmet", true),
            ).normalized()
        }
    }
}

/** 非精确重复闹钟，理由同 [UpdateScheduler]：不需要精确权限，也不需要额外依赖。 */
object ReminderScheduler {
    private const val REQUEST_CODE = 0x7EA4

    private fun pending(context: Context): PendingIntent {
        val intent = Intent(context, ReminderReceiver::class.java)
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        return PendingIntent.getBroadcast(context, REQUEST_CODE, intent, flags)
    }

    fun schedule(context: Context, reminder: ReadingReminder) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val r = reminder.normalized()
        val first = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, r.hour)
            set(Calendar.MINUTE, r.minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (timeInMillis <= System.currentTimeMillis()) add(Calendar.DAY_OF_YEAR, 1)
        }
        runCatching {
            // 同一 PendingIntent 会替换旧闹钟，先 cancel 一遍让「改时间」立刻生效
            am.cancel(pending(context))
            am.setInexactRepeating(
                AlarmManager.RTC_WAKEUP, first.timeInMillis,
                AlarmManager.INTERVAL_DAY, pending(context),
            )
        }
    }

    fun cancel(context: Context) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        runCatching { am.cancel(pending(context)) }
    }
}

/** 到点判断是否该提醒，然后发一条通知。 */
class ReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext
        val offline = OfflineRepo.of(app)
        val reminder = offline.reminder()
        if (!reminder.enabled) return

        val goal = offline.readingGoal()
        val days = runCatching { offline.stats().optJSONObject("days") }.getOrNull()
        val today = GoalMath.dayKey()
        val todaySeconds = GoalMath.daySeconds(days, today)

        // 「仅未达标时提醒」只对开了目标的用户成立；没开目标就没法判断达标，照常提醒
        if (reminder.onlyIfUnmet && goal.enabled) {
            if (todaySeconds >= GoalMath.goalSeconds(goal)) return
        }
        notify(app, goal, todaySeconds)
    }

    private fun notify(
        context: Context,
        goal: ReadingGoal,
        todaySeconds: Long,
    ) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            manager.getNotificationChannel(CHANNEL_ID) == null
        ) {
            manager.createNotificationChannel(NotificationChannel(
                CHANNEL_ID, context.getString(R.string.reminder_channel),
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

        val text = when {
            !goal.enabled -> "睡前读十分钟，比刷十分钟更划算。"
            todaySeconds <= 0 -> "今天还没开始读，${goal.normalized().minutes} 分钟目标留着呢。"
            else -> {
                val left = ((GoalMath.goalSeconds(goal) - todaySeconds) / 60).coerceAtLeast(1)
                "还差 $left 分钟达标，接着昨天的读也行。"
            }
        }
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_playback)
            .setContentTitle("该读一会儿了")
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(open)
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
        runCatching { manager.notify(NOTIFICATION_ID, builder.build()) }
    }

    private companion object {
        const val CHANNEL_ID = "xread_reminder"
        const val NOTIFICATION_ID = 0x7EA4
    }
}
