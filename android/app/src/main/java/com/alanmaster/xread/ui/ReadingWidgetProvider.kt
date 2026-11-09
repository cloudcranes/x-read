package com.alanmaster.xread.ui

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.alanmaster.xread.MainActivity
import com.alanmaster.xread.R
import com.alanmaster.xread.data.OfflineRepo
import com.alanmaster.xread.data.relativeReadLabel

/**
 * 桌面「继续阅读」小组件。
 *
 * 用平台自带的 AppWidget，不引 Glance：组件只显示本地进度，没有远程视图，
 * 几十行 RemoteViews 就够；新依赖只会白吃 APK 体积和 16KB 对齐的复验成本。
 */
class ReadingWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        // 读盘要离开主线程：进度文件虽小，但 onUpdate 跑在主线程上，
        // 平板首屏正好在刷新桌面。
        val pending = goAsync()
        Thread {
            try {
                appWidgetIds.forEach { render(context, appWidgetManager, it) }
            } finally {
                pending.finish()
            }
        }.start()
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_REFRESH) {
            // goAsync() 只有 BroadcastReceiver 实例上才有，静态刷新走广播进这里。
            val pending = goAsync()
            Thread {
                try {
                    renderAll(context)
                } finally {
                    pending.finish()
                }
            }.start()
        }
    }

    /** 重画所有已放置的小组件。 */
    private fun renderAll(context: Context) {
        val manager = AppWidgetManager.getInstance(context)
        val ids = manager.getAppWidgetIds(ComponentName(context, ReadingWidgetProvider::class.java))
        if (ids.isEmpty()) return
        ids.forEach { render(context, manager, it) }
    }

    companion object {
        /** 内部广播：进度变了就刷新小组件。 */
        const val ACTION_REFRESH = "com.alanmaster.xread.ACTION_REFRESH_WIDGET"

        /** 主动刷新所有已放置的小组件。 */
        fun refresh(context: Context) {
            context.sendBroadcast(
                Intent(ACTION_REFRESH)
                    .setComponent(ComponentName(context, ReadingWidgetProvider::class.java)),
            )
        }

        private fun render(context: Context, manager: AppWidgetManager, widgetId: Int) {
            val views = RemoteViews(context.packageName, R.layout.widget_reading)
            val state = OfflineRepo.of(context).readingWidgetState()
            if (state == null) {
                views.setTextViewText(R.id.widget_title, context.getString(R.string.widget_reading_empty_title))
                views.setTextViewText(R.id.widget_subtitle, context.getString(R.string.widget_reading_empty_hint))
                views.setTextViewText(R.id.widget_hint, "")
                views.setOnClickPendingIntent(R.id.widget_root, launchIntent(context, null))
            } else {
                views.setTextViewText(
                    R.id.widget_title,
                    state.title.ifEmpty { context.getString(R.string.widget_reading_untitled) },
                )
                views.setTextViewText(
                    R.id.widget_subtitle,
                    listOf(state.author, state.chapterLabel).filter { it.isNotEmpty() }.joinToString(" · "),
                )
                views.setTextViewText(
                    R.id.widget_hint,
                    context.getString(
                        R.string.widget_reading_hint,
                        relativeReadLabel(state.lastReadAt),
                    ),
                )
                views.setOnClickPendingIntent(R.id.widget_root, launchIntent(context, state))
            }
            manager.updateAppWidget(widgetId, views)
        }

        /** 点组件直接回到上次那本书的阅读位置。 */
        private fun launchIntent(context: Context, state: com.alanmaster.xread.data.ReadingWidgetState?): PendingIntent {
            val intent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                if (state != null) {
                    putExtra(MainActivity.EXTRA_BOOK_ID, state.bookId)
                    putExtra(MainActivity.EXTRA_BOOK_SOURCE, state.source)
                    putExtra(MainActivity.EXTRA_BOOK_TITLE, state.title)
                    putExtra(MainActivity.EXTRA_BOOK_AUTHOR, state.author)
                }
            }
            return PendingIntent.getActivity(
                context,
                state?.bookId?.hashCode() ?: 0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }
    }
}
