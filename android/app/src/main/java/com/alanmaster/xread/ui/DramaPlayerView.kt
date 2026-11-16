package com.alanmaster.xread.ui

import android.content.Context
import android.graphics.Color
import android.net.Uri
import android.view.MotionEvent
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import java.io.File

/**
 * Media3 player view used by the short-drama player.
 *
 * The Compose layer still owns all controls and gestures; this view only wraps
 * ExoPlayer and exposes the small surface the existing player code expects.
 */
@OptIn(UnstableApi::class)
class DramaPlayerView(context: Context) : PlayerView(context) {

    private val appContext = context.applicationContext
    private val exoPlayer = ExoPlayer.Builder(context).build()
    private var preparedListener: ((DramaPlayerView) -> Unit)? = null
    private var completionListener: ((DramaPlayerView) -> Unit)? = null
    private var errorListener: ((DramaPlayerView, Int, Int) -> Boolean)? = null
    private var mediaTransitionListener: ((DramaPlayerView) -> Unit)? = null
    private var prepared = false
    private var currentPath: String? = null
    private var currentUri: Uri? = null
    private var nextPath: String? = null

    override fun onTouchEvent(event: MotionEvent): Boolean = false

    init {
        useController = false
        isClickable = false
        isFocusable = false
        setOnTouchListener { _, _ -> false }
        setShowBuffering(SHOW_BUFFERING_NEVER)
        setShutterBackgroundColor(Color.BLACK)
        keepScreenOn = true
        player = exoPlayer
        exoPlayer.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                when (playbackState) {
                    Player.STATE_READY -> {
                        prepared = true
                        preparedListener?.invoke(this@DramaPlayerView)
                    }
                    Player.STATE_ENDED -> completionListener?.invoke(this@DramaPlayerView)
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                errorListener?.invoke(this@DramaPlayerView, error.errorCode, 0)
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                if (reason != Player.MEDIA_ITEM_TRANSITION_REASON_AUTO) return
                val uri = mediaItem?.localConfiguration?.uri
                currentUri = uri
                // Compose 传入的是绝对文件路径，而 MediaItem 的 Uri.toString() 会带
                // file:// 前缀。这里保存归一化后的路径，避免自动切集后 update() 误判
                // 成不同的视频并重新 prepare，破坏无缝衔接。
                currentPath = if (uri?.scheme == "file") uri.path else uri?.toString()
                nextPath = null
                mediaTransitionListener?.invoke(this@DramaPlayerView)
            }
        })
    }

    fun setOnPreparedListener(listener: (DramaPlayerView) -> Unit) {
        preparedListener = listener
        if (prepared) listener(this)
    }

    fun setOnCompletionListener(listener: (DramaPlayerView) -> Unit) {
        completionListener = listener
    }

    fun setOnErrorListener(listener: (DramaPlayerView, Int, Int) -> Boolean) {
        errorListener = listener
    }

    fun setOnMediaItemTransitionListener(listener: (DramaPlayerView) -> Unit) {
        mediaTransitionListener = listener
    }

    fun setVideoPath(path: String) {
        if (path.isEmpty()) return
        val playablePath = if (path.startsWith("http://") || path.startsWith("https://")) {
            path
        } else {
            Mp4Sanitizer.sanitize(appContext, path.removePrefix("file://"))
        }
        val uri = when {
            playablePath.startsWith("http://") || playablePath.startsWith("https://") || playablePath.startsWith("file://") ->
                Uri.parse(playablePath)
            else -> Uri.fromFile(File(playablePath))
        }
        if ((currentPath == path || currentUri == uri) && exoPlayer.playbackState != Player.STATE_IDLE) {
            exoPlayer.playWhenReady = true
            return
        }
        currentPath = path
        currentUri = uri
        nextPath = null
        prepared = false
        exoPlayer.setMediaItem(MediaItem.fromUri(uri))
        exoPlayer.prepare()
        exoPlayer.playWhenReady = true
    }

    /** Queue the next episode behind the current item so Media3 can buffer it in advance. */
    fun setNextVideoPath(path: String?) {
        if (path.isNullOrBlank()) {
            clearNextVideo()
            return
        }
        if (path == nextPath || path == currentPath) return
        val playablePath = if (path.startsWith("http://") || path.startsWith("https://")) {
            path
        } else {
            Mp4Sanitizer.sanitize(appContext, path.removePrefix("file://"))
        }
        val uri = when {
            playablePath.startsWith("http://") || playablePath.startsWith("https://") || playablePath.startsWith("file://") ->
                Uri.parse(playablePath)
            else -> Uri.fromFile(File(playablePath))
        }
        val firstNext = exoPlayer.currentMediaItemIndex + 1
        if (exoPlayer.mediaItemCount > firstNext) {
            exoPlayer.removeMediaItems(firstNext, exoPlayer.mediaItemCount)
        }
        if (exoPlayer.mediaItemCount == 0) return
        exoPlayer.addMediaItem(MediaItem.fromUri(uri))
        nextPath = path
        android.util.Log.i("DramaPlayer", "preloaded next path=$path items=${exoPlayer.mediaItemCount}")
    }

    fun clearNextVideo() {
        val firstNext = exoPlayer.currentMediaItemIndex + 1
        if (exoPlayer.mediaItemCount > firstNext) {
            exoPlayer.removeMediaItems(firstNext, exoPlayer.mediaItemCount)
        }
        nextPath = null
    }

    val currentPosition: Int
        get() = exoPlayer.currentPosition.coerceAtLeast(0L).toInt()

    val duration: Int
        get() = if (exoPlayer.duration == C.TIME_UNSET) 0 else exoPlayer.duration.coerceAtLeast(0L).toInt()

    val isPlaying: Boolean
        get() = exoPlayer.isPlaying

    fun seekTo(positionMs: Int) {
        exoPlayer.seekTo(positionMs.coerceAtLeast(0).toLong())
    }

    fun start() {
        exoPlayer.play()
    }

    fun pause() {
        exoPlayer.pause()
    }

    fun setPlaybackSpeed(speed: Float) {
        exoPlayer.setPlaybackSpeed(speed)
    }

    fun release() {
        exoPlayer.release()
    }
}
