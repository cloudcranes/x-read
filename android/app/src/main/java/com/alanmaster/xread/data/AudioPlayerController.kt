package com.alanmaster.xread.data

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Build
import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * 真人音频播放器：进度、暂停/继续、拖动、倍速、连播下一集、定时关闭。
 *
 * 只播 core 解密后的本地文件；加密流若未解密则明确失败，不静默放杂音。
 */
class AudioPlayerController(
    onFinished: () -> Unit = {},
    /** 应用上下文；为空时不做音频焦点协商（单测或纯数学调用）。 */
    private val appContext: Context? = null,
) {
    /** 播完一集后的动作；由调用方设为「播放下一集」。 */
    var onFinished: () -> Unit = onFinished
    data class State(
        val chapterId: String = "",
        val title: String = "",
        val playing: Boolean = false,
        val prepared: Boolean = false,
        val positionMs: Int = 0,
        val durationMs: Int = 0,
        val rate: Float = 1f,
        val message: String = "",
        val sleepAtMs: Long = 0L,
        /** 用户正在拖动进度条：此时不要用 ticker 的位置覆盖 UI。 */
        val seeking: Boolean = false,
    )

    private val main = Handler(Looper.getMainLooper())
    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state

    private var player: MediaPlayer? = null
    private var generation = 0

    /* ---------- 音频焦点 ---------- */

    private val audioManager: AudioManager? by lazy {
        appContext?.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
    }
    private var focusRequest: AudioFocusRequest? = null
    /** 因来电/其它应用抢占而临时暂停时记下，焦点回来后自动续播。 */
    @Volatile private var resumeOnFocusGain = false

    private val focusListener = AudioManager.OnAudioFocusChangeListener { change ->
        when (change) {
            AudioManager.AUDIOFOCUS_LOSS -> {
                // 永久失去焦点：暂停但不自动恢复
                resumeOnFocusGain = false
                if (_state.value.playing) pauseInternal()
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT,
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                if (_state.value.playing) {
                    resumeOnFocusGain = true
                    pauseInternal()
                }
            }
            AudioManager.AUDIOFOCUS_GAIN -> {
                if (resumeOnFocusGain) {
                    resumeOnFocusGain = false
                    resumeInternal()
                }
            }
        }
    }

    private fun requestFocusIfPossible(): Boolean {
        val am = audioManager ?: return true
        val result = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val attrs = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()
            val request = focusRequest ?: AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(attrs)
                .setWillPauseWhenDucked(true)
                .setOnAudioFocusChangeListener(focusListener, main)
                .build().also { focusRequest = it }
            am.requestAudioFocus(request)
        } else {
            @Suppress("DEPRECATION")
            am.requestAudioFocus(focusListener, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN)
        }
        return result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
    }

    private fun abandonFocus() {
        val am = audioManager ?: return
        resumeOnFocusGain = false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            focusRequest?.let { am.abandonAudioFocusRequest(it) }
        } else {
            @Suppress("DEPRECATION")
            am.abandonAudioFocus(focusListener)
        }
    }

    /** 只暂停，不释放播放器；供焦点丢失与通知栏暂停共用。 */
    private fun pauseInternal() {
        val p = player ?: return
        if (!_state.value.prepared) return
        if (runCatching { p.isPlaying }.getOrDefault(false)) runCatching { p.pause() }
        _state.value = _state.value.copy(playing = false)
        main.removeCallbacks(ticker)
        // 不要撤 sleepWatch：定时关闭的语义是「到点就停」，用户暂停或来电打断
        // 之后定时器仍应生效；撤销只在 release()/stop() 里做。
    }

    /** 只恢复播放，不重新 prepare；供焦点恢复与通知栏继续共用。 */
    private fun resumeInternal() {
        val p = player ?: return
        if (!_state.value.prepared) return
        if (!requestFocusIfPossible()) return
        runCatching { p.start() }
        _state.value = _state.value.copy(playing = true, message = "")
        main.removeCallbacks(ticker)
        main.post(ticker)
        armSleepWatch()
    }

    private val ticker = object : Runnable {
        override fun run() {
            val p = player ?: return
            val st = _state.value
            if (st.playing) {
                if (!st.seeking) {
                    val pos = runCatching { p.currentPosition }.getOrDefault(0)
                    _state.value = st.copy(positionMs = pos)
                }
                checkSleepTimer()
                main.postDelayed(this, 500)
            }
        }
    }

    /** 定时关闭也必须独立于播放状态生效（暂停时到点同样要停）。 */
    private val sleepWatch = object : Runnable {
        override fun run() {
            val at = _state.value.sleepAtMs
            if (at == 0L) return
            if (System.currentTimeMillis() >= at) {
                stop()
                _state.value = _state.value.copy(sleepAtMs = 0L, message = "定时已到，已停止")
            } else {
                main.postDelayed(this, 1000)
            }
        }
    }

    fun play(chapterId: String, title: String, source: String) {
        // 同一个文件重复点播放（例如播完后重听）也要重新走一遍播放器
        release()
        if (source.isEmpty()) {
            _state.value = State(chapterId = chapterId, title = title,
                message = "该章没有可播放的音频（可能解密失败）")
            return
        }
        // 起播前先拿到音频焦点：拿不到就不打断别人的音乐
        if (!requestFocusIfPossible()) {
            _state.value = State(chapterId = chapterId, title = title,
                message = "音频焦点被占用，暂不能播放")
            return
        }
        val gen = ++generation
        val keepRate = _state.value.rate
        val keepSleep = _state.value.sleepAtMs
        _state.value = State(chapterId = chapterId, title = title, message = "准备中…",
            rate = keepRate, sleepAtMs = keepSleep)

        val mp = MediaPlayer()
        player = mp
        runCatching {
            mp.setDataSource(source)
            mp.setOnPreparedListener { it ->
                if (gen != generation) return@setOnPreparedListener
                applyRate(it, _state.value.rate)
                it.start()
                _state.value = _state.value.copy(
                    prepared = true, playing = true, durationMs = it.duration, message = "")
                main.removeCallbacks(ticker)
                main.post(ticker)
                armSleepWatch()
            }
            mp.setOnCompletionListener {
                if (gen != generation) return@setOnCompletionListener
                // prepared 必须一起复位：PlaybackService 的收尾条件是
                // `!prepared && !playing`，旧实现只清 playing，最后一集播完后
                // 前台服务与通知永不退场，还一直占着音频焦点。
                _state.value = _state.value.copy(playing = false, prepared = false,
                    positionMs = _state.value.durationMs)
                abandonFocus()
                onFinished()
            }
            mp.setOnErrorListener { _, what, extra ->
                if (gen == generation) {
                    _state.value = _state.value.copy(playing = false, prepared = false,
                        message = "播放失败（$what/$extra）")
                    abandonFocus()
                }
                true
            }
            mp.prepareAsync()
        }.onFailure {
            _state.value = _state.value.copy(playing = false,
                message = "播放失败：${it.javaClass.simpleName}")
        }
    }

    fun toggle() {
        val st = _state.value
        if (!st.prepared) return
        if (st.playing) {
            // 用户主动暂停：让出音频焦点，别人才能正常出声；
            // 同时清掉「焦点回来就续播」，否则一有焦点就自己响起来。
            resumeOnFocusGain = false
            pauseInternal()
            abandonFocus()
        } else {
            resumeInternal()
        }
    }

    /** 拖动开始/结束：拖动期间冻结 ticker 回写，避免滑块被拽回。 */
    fun beginSeek() { _state.value = _state.value.copy(seeking = true) }

    fun endSeek(ms: Int) {
        _state.value = _state.value.copy(seeking = false)
        seekTo(ms)
    }

    fun seekTo(ms: Int) {
        val p = player ?: return
        val target = ms.coerceIn(0, _state.value.durationMs.coerceAtLeast(0))
        runCatching {
            p.seekTo(target)
            _state.value = _state.value.copy(positionMs = target)
        }
    }

    /** 倍速：0.5–2.0。MediaPlayer 的 PlaybackParams 需 API 23+，以下版本忽略。 */
    fun setRate(rate: Float): Float {
        val clamped = rate.coerceIn(0.5f, 2f)
        _state.value = _state.value.copy(rate = clamped)
        player?.let { p -> if (_state.value.prepared) runCatching { applyRate(p, clamped) } }
        return clamped
    }

    private fun applyRate(p: MediaPlayer, rate: Float) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return
        runCatching {
            p.playbackParams = p.playbackParams.setSpeed(rate)
        }.onFailure {
            // 部分机型不支持变速；保持原速并如实告知
            _state.value = _state.value.copy(rate = 1f, message = "该机型不支持变速")
        }
    }

    /** 定时关闭：分钟数，<=0 取消。 */
    fun setSleepTimer(minutes: Int) {
        val at = if (minutes <= 0) 0L else System.currentTimeMillis() + minutes * 60_000L
        _state.value = _state.value.copy(sleepAtMs = at,
            message = if (minutes <= 0) "已取消定时" else "定时 $minutes 分钟")
        main.removeCallbacks(sleepWatch)
        if (at > 0) main.postDelayed(sleepWatch, 1000)
    }

    private fun armSleepWatch() {
        main.removeCallbacks(sleepWatch)
        if (_state.value.sleepAtMs > 0) main.postDelayed(sleepWatch, 1000)
    }

    private fun checkSleepTimer() {
        val at = _state.value.sleepAtMs
        if (at in 1..System.currentTimeMillis()) {
            stop()
            _state.value = _state.value.copy(sleepAtMs = 0L, message = "定时已到，已停止")
        }
    }

    fun stop() {
        release()
        _state.value = _state.value.copy(playing = false, prepared = false, positionMs = 0)
    }

    /** 仅停止播放并释放播放器，保留界面上正在播的章节名，便于「再点一次继续」。 */
    fun stopAndKeepTitle() {
        val st = _state.value
        release()
        _state.value = st.copy(playing = false, prepared = false, positionMs = 0,
            message = "已停止")
    }

    /**
     * 释放播放器。
     *
     * `stop()` 在 Preparing 态会抛 IllegalStateException，旧实现把 stop/release 放在
     * 同一 runCatching 里，异常会让 release() 被跳过 → MediaPlayer 泄漏，
     * 反复切集后出现「创建失败」或无声。这里分两步各自兜住。
     */
    private fun release() {
        generation++
        main.removeCallbacks(ticker)
        main.removeCallbacks(sleepWatch)
        abandonFocus()
        val p = player
        player = null
        if (p == null) return
        runCatching { p.stop() }
        runCatching { p.reset() }
        runCatching { p.release() }
    }

    fun shutdown() {
        release()
        _state.value = State()
    }

    companion object {
        /** 进度百分比，纯函数便于单测。 */
        fun percent(positionMs: Int, durationMs: Int): Int =
            if (durationMs <= 0) 0 else ((positionMs.toLong() * 100) / durationMs).toInt().coerceIn(0, 100)

        /** 百分比 → 毫秒，供 Slider 拖动用；时长未知时返回 0。 */
        fun fromPercent(percent: Int, durationMs: Int): Int =
            if (durationMs <= 0) 0 else ((durationMs.toLong() * percent.coerceIn(0, 100)) / 100).toInt()

        fun formatTime(ms: Int): String {
            val total = (ms.coerceAtLeast(0)) / 1000
            // 固定 Locale.US：默认 Locale 在 ar/fa/bn 下会把数字换成别的记数系统
            // （例如 ٣:٢٥），时长显示就废了。ToolsScreen/AuthorScreen 已是这个写法。
            return String.format(java.util.Locale.US, "%d:%02d", total / 60, total % 60)
        }
    }
}
