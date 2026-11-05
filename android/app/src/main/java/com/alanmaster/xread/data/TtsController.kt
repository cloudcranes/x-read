package com.alanmaster.xread.data

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.Locale

/**
 * 听书控制器：按段落排队朗读，支持倍速、暂停续读、定时关闭、读完自动续下一章。
 *
 * 段落逐条入队（QUEUE_ADD），这样进度可精确到段，暂停时也能从当前段继续；
 * 若整章一次性入队，暂停后只能从头再来。
 */
class TtsController(
    private val context: Context,
    private val onProgress: (paragraphIndex: Int) -> Unit,
    private val onChapterFinished: () -> Unit,
    private val onStateChanged: (speaking: Boolean, message: String) -> Unit,
) {
    private val main = Handler(Looper.getMainLooper())

    /** 朗读状态单一来源：Compose 直接收集，不再自己记一份。 */
    private val _speaking = MutableStateFlow(false)
    val speakingFlow: StateFlow<Boolean> = _speaking
    private val _message = MutableStateFlow("")
    val messageFlow: StateFlow<String> = _message

    @Volatile private var engineReady = false
    @Volatile private var languageOk = false
    @Volatile private var lastError = ""
    private var startWatchdog: Runnable? = null
    private var sleepTask: Runnable? = null
    /** 每次开始朗读自增；看门狗只在同一代次内有效，避免暂停后续读被旧定时器误杀。 */
    private var generation = 0
    /** 本轮是否还没收到任何 onStart；只有它仍为 true 才算「引擎哑了」。 */
    @Volatile private var awaitingFirstStart = false
    @Volatile private var speaking = false
    private var pending: List<String> = emptyList()
    private var nextIndex = 0
    /** 正在朗读的章节标识；与段落下标一起构成 utteranceId 前缀，防止跨章回调串味。 */
    private var chapterKey = ""
    private var rate = 1.0f
    private var sleepAtMillis = 0L
    private var paused = false

    private var ttsRef: TextToSpeech? = null

    /* ---------- 音频焦点 ---------- */
    // 朗读也是媒体播放：来电或别的播放器起播时应让路，否则两路声音同时响。

    private val audioManager: AudioManager? by lazy {
        context.applicationContext.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
    }
    private var focusRequest: AudioFocusRequest? = null
    @Volatile private var resumeOnFocusGain = false

    private val focusListener = AudioManager.OnAudioFocusChangeListener { change ->
        when (change) {
            AudioManager.AUDIOFOCUS_LOSS -> {
                resumeOnFocusGain = false
                if (speaking) pause()
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT,
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                if (speaking) { resumeOnFocusGain = true; pause() }
            }
            AudioManager.AUDIOFOCUS_GAIN -> {
                if (resumeOnFocusGain) { resumeOnFocusGain = false; resume() }
            }
        }
    }

    private fun requestFocus(): Boolean {
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

    private fun publish(speakingNow: Boolean, message: String) {
        android.util.Log.i("xread-tts", "publish speaking=$speakingNow msg=$message thread=${Thread.currentThread().name}")
        _speaking.value = speakingNow
        _message.value = message
        onStateChanged(speakingNow, message)
    }

    private val tts: TextToSpeech = TextToSpeech(context.applicationContext) { status ->
        // 构造回调可能早于字段赋值，此处只记状态，语言在首次朗读前设置。
        engineReady = status == TextToSpeech.SUCCESS
        if (!engineReady) lastError = "语音引擎初始化失败"
        main.post { publish(speaking, if (engineReady) "" else lastError) }
    }.also { ttsRef = it }

    /** 首次朗读前协商语言；各厂商引擎对 zh 的写法不一，逐个回退。 */
    private fun ensureLanguage(): Boolean {
        if (languageOk) return true
        for (locale in listOf(Locale.SIMPLIFIED_CHINESE, Locale.CHINA, Locale.CHINESE, Locale.getDefault())) {
            val result = runCatching { tts.setLanguage(locale) }.getOrNull() ?: continue
            if (result >= TextToSpeech.LANG_AVAILABLE) { languageOk = true; return true }
            lastError = when (result) {
                TextToSpeech.LANG_MISSING_DATA -> "语音包缺失：$locale"
                TextToSpeech.LANG_NOT_SUPPORTED -> "引擎不支持：$locale"
                else -> "语言设置失败：$result"
            }
        }
        return false
    }

    init {
        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                android.util.Log.i("xread-tts", "onStart id=$utteranceId")
                awaitingFirstStart = false
                startWatchdog?.let { main.removeCallbacks(it) }
                languageOk = true
            }
            override fun onDone(utteranceId: String?) {
                android.util.Log.i("xread-tts", "onDone id=$utteranceId")
                // utteranceId 形如 "章标识#段落下标"；旧引擎回调可能来自上一章/上一代
                val parsed = parseUtterance(utteranceId) ?: return
                main.post(Runnable {
                    if (parsed.first != chapterKey) return@Runnable
                    val index = parsed.second
                    onProgress(index)
                    if (index >= nextIndex) nextIndex = (index + 1).coerceAtMost(pending.lastIndex)
                    // 最后一段读完 → 交给上层决定是否续下一章
                    if (pending.isNotEmpty() && index >= pending.lastIndex) {
                        android.util.Log.i("xread-tts", "chapter finished after id=$index")
                        speaking = false
                        publish(false, "本章读完")
                        onChapterFinished()
                    }
                })
            }

            @Deprecated("平台已废弃 onError(String)，保留以兼容旧引擎回调")
            @Suppress("OVERRIDE_DEPRECATION")
            override fun onError(utteranceId: String?) {
                android.util.Log.w("xread-tts", "onError id=$utteranceId")
                val parsed = parseUtterance(utteranceId) ?: return
                main.post(Runnable {
                    if (parsed.first != chapterKey) return@Runnable
                    speaking = false
                    publish(false, "朗读失败")
                })
            }
        })
    }

    /** "章标识#下标" → (章标识, 下标)；格式不符则返回 null。 */
    private fun parseUtterance(id: String?): Pair<String, Int>? = parseUtteranceId(id)

    fun available(): Boolean = engineReady

    /** 只更新提示文案，不改朗读状态。 */
    fun announce(message: String) { publish(_speaking.value, message) }

    /** 从 [startIndex] 段开始朗读整章。[key] 标识本章（一般用章节 id）。 */
    fun speak(paragraphs: List<String>, startIndex: Int = 0, key: String = "") {
        if (!engineReady) {
            publish(false, lastError.ifEmpty { "语音引擎不可用" })
            return
        }
        if (!ensureLanguage()) {
            publish(false, lastError.ifEmpty { "系统缺少中文语音包" })
            return
        }
        val clean = paragraphs.map { it.trim() }.filter { it.isNotEmpty() }
        if (clean.isEmpty()) {
            publish(false, "本章没有可朗读的文字")
            return
        }
        // 拿不到焦点就不抢别人的声音（来电、音乐播放中）
        if (!requestFocus()) {
            publish(false, "音频焦点被占用，暂不能朗读")
            return
        }
        // 注意：不能调用会清掉 sleepAtMillis 的 stopInternal，否则定时关闭在每次起播
        // 或自动续章时都被静默取消。
        haltPlayback()
        pending = clean
        chapterKey = if (key.isNotEmpty()) key else System.currentTimeMillis().toString()
        nextIndex = startIndex.coerceIn(0, clean.lastIndex)
        paused = false
        tts.setSpeechRate(rate)
        speakFrom(nextIndex)
    }

    private fun speakFrom(index: Int) {
        speaking = true
        publish(true, "朗读中")
        // 必须先武装看门狗与代次，再把段落入队。
        // onStart 是异步回调，可能在 speak() 返回前就到达；若晚于回调才把
        // awaitingFirstStart 置 true，就会把刚收到的 onStart 覆盖掉，
        // 8 秒后误报「语音引擎未响应」（实测复现）。
        startWatchdog?.let { main.removeCallbacks(it) }
        val gen = ++generation
        awaitingFirstStart = true
        val watchdog = Runnable {
            // 引擎停止旧队列再起新段可能耗时数秒，故只在「一个 onStart 都没有」时才判失败。
            if (gen == generation && speaking && awaitingFirstStart) {
                speaking = false
                publish(false, lastError.ifEmpty { "语音引擎未响应，请检查系统语音设置" })
            }
        }
        startWatchdog = watchdog
        main.postDelayed(watchdog, 8000)

        for (i in index until pending.size) {
            tts.speak(pending[i], TextToSpeech.QUEUE_ADD, null, "$chapterKey#$i")
        }
        nextIndex = index
        armSleepTimerIfNeeded()
    }

    fun pause() {
        if (!speaking) return
        // 焦点丢失触发的 pause 会先把 resumeOnFocusGain 置 true，这里不能清掉它
        if (!resumeOnFocusGain) abandonFocus()
        paused = true
        speaking = false
        generation++
        startWatchdog?.let { main.removeCallbacks(it) }
        startWatchdog = null
        tts.stop()
        publish(false, "已暂停（第 ${nextIndex + 1} 段）")
    }

    fun resume() {
        if (!paused) return
        if (!requestFocus()) {
            publish(false, "音频焦点被占用，暂不能朗读")
            return
        }
        paused = false
        tts.setSpeechRate(rate)
        speakFrom(nextIndex)
    }

    fun stop() {
        // 用户主动停止：不应在焦点恢复后自动续读
        resumeOnFocusGain = false
        haltPlayback()
        abandonFocus()
        sleepAtMillis = 0L
        cancelSleepTask()
        publish(false, "已停止")
    }

    /** 只停止朗读，不动定时器（供 speak 内部换章时复用）。 */
    private fun haltPlayback() {
        generation++
        startWatchdog?.let { main.removeCallbacks(it) }
        startWatchdog = null
        speaking = false
        paused = false
        pending = emptyList()
        nextIndex = 0
        chapterKey = ""
        tts.stop()
    }

    /** 倍速：0.5–3.0。语速变化在下一段生效。 */
    fun setRate(value: Float): Float {
        rate = value.coerceIn(0.5f, 3f)
        tts.setSpeechRate(rate)
        return rate
    }

    fun rate(): Float = rate

    /** 定时关闭：minutes<=0 取消。 */
    fun setSleepTimer(minutes: Int) {
        sleepAtMillis = if (minutes <= 0) 0L else System.currentTimeMillis() + minutes * 60_000L
        armSleepTimerIfNeeded()
    }

    /** 剩余定时毫秒；0 表示未设置。 */
    fun sleepRemainingMs(): Long {
        val at = sleepAtMillis
        return if (at <= 0L) 0L else (at - System.currentTimeMillis()).coerceAtLeast(0L)
    }

    private fun cancelSleepTask() {
        sleepTask?.let { main.removeCallbacks(it) }
        sleepTask = null
    }

    private fun armSleepTimerIfNeeded() {
        cancelSleepTask()
        if (sleepAtMillis <= 0L) return
        val delay = sleepAtMillis - System.currentTimeMillis()
        if (delay <= 0) {
            stop()
            publish(false, "定时已到，已停止")
            return
        }
        val task = Runnable {
            // 到点即停，不依赖 sleepAtMillis 之外的状态，也不会累积多个旧任务
            if (sleepAtMillis > 0L && System.currentTimeMillis() >= sleepAtMillis) {
                stop()
                publish(false, "定时已到，已停止")
            }
        }
        sleepTask = task
        main.postDelayed(task, delay)
    }

    fun shutdown() {
        haltPlayback()
        abandonFocus()
        cancelSleepTask()
        tts.shutdown()
    }

    companion object {
        /** 纯函数便于单测：从 utteranceId 还原 (章标识, 段落下标)。 */
        fun parseUtteranceId(id: String?): Pair<String, Int>? {
            val raw = id ?: return null
            val at = raw.lastIndexOf('#')
            if (at <= 0) return null
            val index = raw.substring(at + 1).toIntOrNull() ?: return null
            if (index < 0) return null
            return raw.substring(0, at) to index
        }
    }
}
