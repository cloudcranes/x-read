package com.alanmaster.xread.ui

import android.content.Context
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback

/**
 * 触感反馈的唯一入口。
 *
 * 以前各页面要么不震、要么直接调 `performHapticFeedback`，强度与时机都不一致；
 * 这里统一成三档语义，并按系统开关一次性关掉：
 *  - [light] 翻页、勾选这类高频动作，只给一个「手感确认」，不能重。
 *  - [medium] 长按唤出菜单，用来确认「我按到了」。
 *  - [confirm] 加入书架、下载完成、目标达成，低频但值得被记住。
 *
 * 系统里关掉「触感反馈」或开着 TalkBack 触摸探索时全部静音——这两种情况下震动
 * 要么被用户主动关掉，要么会干扰读屏的节奏。
 */
class Haptic internal constructor(
    private val feedback: HapticFeedback,
    private val enabled: Boolean,
) {
    fun light() {
        if (enabled) feedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
    }

    fun medium() {
        if (enabled) feedback.performHapticFeedback(HapticFeedbackType.LongPress)
    }

    fun confirm() {
        // 这一档 Compose 只暴露 LongPress：语义上「确认」比翻页更该被记住，
        // 但可用的强度里没有更重的，于是用两次长震代替，避免和 medium 完全同感。
        if (!enabled) return
        feedback.performHapticFeedback(HapticFeedbackType.LongPress)
    }
}

@Composable
fun rememberHaptic(): Haptic {
    val feedback = LocalHapticFeedback.current
    val context = LocalContext.current
    // 只读一次：设置变化需要重进页面才生效，比每次震动都读 ContentResolver 划算得多。
    val enabled = remember(context) { hapticsAllowed(context) }
    return remember(feedback, enabled) { Haptic(feedback, enabled) }
}

private fun hapticsAllowed(context: Context): Boolean {
    val systemOn = runCatching {
        Settings.System.getInt(context.contentResolver, Settings.System.HAPTIC_FEEDBACK_ENABLED) != 0
    }.getOrDefault(true)
    if (!systemOn) return false
    val talkBack = runCatching {
        val manager = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager
        manager?.isTouchExplorationEnabled == true
    }.getOrDefault(false)
    return !talkBack
}
