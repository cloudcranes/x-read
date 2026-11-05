package com.alanmaster.xread.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alanmaster.xread.data.AudioChapter
import com.alanmaster.xread.data.AudioPlayerController
import com.alanmaster.xread.data.AudioTone
import com.alanmaster.xread.data.Chapter
import com.alanmaster.xread.data.ChapterTimeline

/**
 * 听书（真人音频）：章节列表 + 系统播放器。
 *
 * 音频地址由内容核心直接给出，加密标记如实展示，不假装可播。
 *
 * 播放/暂停用 Material 图标而不是 emoji `⏸` `▶`：emoji 的字形随系统字体走，
 * 在不同机型上大小与基线都不同，按钮框会跳。
 */
@Composable
fun AudioScreen(
    bookTitle: String,
    chapters: List<Chapter>,
    tones: List<AudioTone>,
    tone: AudioTone?,
    current: AudioChapter?,
    timeline: ChapterTimeline? = null,
    loading: Boolean,
    error: String,
    onBack: () -> Unit,
    onPlay: (Chapter) -> Unit,
    onTone: (AudioTone) -> Unit,
    playback: AudioPlayerController.State,
    onToggle: () -> Unit,
    onSeek: (Int) -> Unit,
    onRate: (Float) -> Unit,
    onSleep: (Int) -> Unit,
    onRetry: () -> Unit,
) {
    Scaffold(
        topBar = {
            XReadTopBar(
                title = "听书",
                subtitle = bookTitle,
                onBack = onBack,
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            /* -------------------- 音色选择 -------------------- */
            if (tones.size > 1) {
                // 音色数量不定：用横向滚动，避免超过 3 个就被挤出屏幕点不到
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = Space.gutter),
                    horizontalArrangement = Arrangement.spacedBy(Space.sm),
                ) {
                    items(tones, key = { it.abookId }) { tr ->
                        androidx.compose.material3.FilterChip(
                            selected = tr.abookId == tone?.abookId,
                            onClick = { onTone(tr) },
                            label = {
                                Text(
                                    text = tr.title.take(12).ifEmpty { "音色" },
                                    fontSize = 11.sp,
                                )
                            },
                            shape = RoundedCornerShape(Radius.sm),
                        )
                    }
                }
            }

            /* -------------------- 播放器 -------------------- */
            Box(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Space.gutter, vertical = Space.md),
            ) {
                when {
                    loading -> Text(
                        text = "取音频地址…",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    error.isNotEmpty() -> Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Filled.ErrorOutline,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(18.dp),
                            )
                            Text(
                                text = error,
                                fontSize = 13.sp,
                                lineHeight = 19.sp,
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.padding(start = Space.sm),
                            )
                        }
                        androidx.compose.material3.TextButton(
                            onClick = onRetry,
                            modifier = Modifier.padding(top = Space.xs),
                        ) { Text("重试") }
                    }

                    current == null -> Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Filled.Headphones,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp),
                        )
                        Text(
                            text = "选择下方章节开始收听",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = Space.sm),
                        )
                    }

                    else -> Column {
                        Text(
                            text = current.title.ifEmpty { current.chapterId },
                            fontSize = 15.sp,
                            lineHeight = 21.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        // 加密提示必须保留：上游加密音频系统播放器可能播不了，
                        // 不说清楚用户会以为是 App 坏了。
                        //
                        // 但只在「本地没有解密副本」时才提示：core 拿到 cache=1
                        // 会自己下载并 CENC 解密到 /src，实测解密成功后照样能播，
                        // 这时候再挂一个「可能无法播放」就是虚假告警。
                        if (current.needsDecryptWarning) {
                            Row(
                                Modifier.padding(top = Space.xs),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Lock,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(14.dp),
                                )
                                Text(
                                    text = if (current.cacheError.isNotEmpty())
                                        "音频解密失败：${current.cacheError}"
                                    else "该音频经上游加密，系统播放器可能无法直接播放",
                                    fontSize = 11.sp,
                                    lineHeight = 17.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(start = Space.xs),
                                )
                            }
                        }
                        AudioControls(
                            state = playback,
                            onToggle = onToggle,
                            onSeek = onSeek,
                            onRate = onRate,
                            onSleep = onSleep,
                        )
                    }
                }
            }

            /* -------------------- 逐句字幕 / 章节列表 选项卡 -------------------- */
            var activeTab by remember { mutableIntStateOf(0) }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Space.gutter, vertical = Space.xs),
                horizontalArrangement = Arrangement.spacedBy(Space.sm),
            ) {
                val sentenceCount = timeline?.sentences?.size ?: 0
                androidx.compose.material3.FilterChip(
                    selected = activeTab == 0,
                    onClick = { activeTab = 0 },
                    label = {
                        Text(
                            text = if (sentenceCount > 0) "逐句字幕 ($sentenceCount)" else "逐句字幕",
                            fontSize = 12.sp,
                        )
                    },
                    shape = RoundedCornerShape(Radius.sm),
                )
                androidx.compose.material3.FilterChip(
                    selected = activeTab == 1,
                    onClick = { activeTab = 1 },
                    label = { Text("章节目录 (${chapters.size})", fontSize = 12.sp) },
                    shape = RoundedCornerShape(Radius.sm),
                )
            }

            if (activeTab == 0) {
                TimelineList(
                    timeline = timeline,
                    loading = loading,
                    positionMs = playback.positionMs,
                    seeking = playback.seeking,
                    onSeek = onSeek,
                )
            } else {
                if (chapters.isEmpty()) {
                    EmptyHint("该书暂无可收听章节")
                } else {
                    LazyColumn(
                        Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(bottom = Space.xxl),
                    ) {
                        itemsIndexed(chapters, key = { _, c -> c.id }) { index, c ->
                            ChapterRow(
                                index = index,
                                chapter = c,
                                playing = playback.playing && playback.chapterId == c.id,
                                onPlay = { onPlay(c) },
                            )
                        }
                    }
                }
            }
        }
    }
}

/** 逐句时间轴字幕：高亮播放中的当前句、点击跳转进度、平滑居中跟随。 */
@Composable
private fun TimelineList(
    timeline: ChapterTimeline?,
    loading: Boolean,
    positionMs: Int,
    seeking: Boolean,
    onSeek: (Int) -> Unit,
) {
    if (timeline == null || timeline.sentences.isEmpty()) {
        if (loading) {
            EmptyHint("正在加载字幕时间轴…")
        } else {
            EmptyHint("当前章节暂无逐句字幕，可切换到「章节目录」选章收听")
        }
        return
    }

    val currentIndex = remember(timeline, positionMs) { timeline.findCurrentIndex(positionMs) }
    val listState = rememberLazyListState()

    LaunchedEffect(currentIndex, seeking) {
        if (currentIndex in timeline.sentences.indices && !seeking) {
            listState.animateScrollToItem((currentIndex - 2).coerceAtLeast(0))
        }
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = Space.gutter, vertical = Space.sm),
        verticalArrangement = Arrangement.spacedBy(Space.xs),
    ) {
        itemsIndexed(timeline.sentences, key = { index, s -> "$index-${s.startMs}" }) { index, s ->
            val isCurrent = index == currentIndex
            val startMin = s.startMs / 60000
            val startSec = (s.startMs % 60000) / 1000
            val timeText = String.format(java.util.Locale.US, "%02d:%02d", startMin, startSec)

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(Radius.sm))
                    .background(
                        if (isCurrent) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                        else MaterialTheme.colorScheme.surface
                    )
                    .clickable { onSeek(s.startMs) }
                    .padding(horizontal = Space.sm, vertical = Space.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = timeText,
                    fontSize = 11.sp,
                    fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                    color = if (isCurrent) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier.width(44.dp),
                )
                Text(
                    text = s.text,
                    fontSize = if (isCurrent) 15.sp else 13.sp,
                    lineHeight = if (isCurrent) 22.sp else 19.sp,
                    fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                    color = if (isCurrent) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

/** 章节行：序号 + 标题 + 播放按钮，整行 ≥48dp。 */
@Composable
private fun ChapterRow(
    index: Int,
    chapter: Chapter,
    playing: Boolean,
    onPlay: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clickable(onClick = onPlay)
            .padding(horizontal = Space.gutter, vertical = Space.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "${index + 1}",
            fontSize = 11.sp,
            color = if (playing) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(28.dp),
        )
        Text(
            text = chapter.title.ifEmpty { chapter.id },
            fontSize = 14.sp,
            lineHeight = 20.sp,
            fontWeight = if (playing) FontWeight.SemiBold else FontWeight.Normal,
            color = if (playing) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        IconButton(
            onClick = onPlay,
            modifier = Modifier.size(48.dp),
        ) {
            Icon(
                imageVector = Icons.Filled.PlayArrow,
                contentDescription = "播放 ${chapter.title.ifEmpty { chapter.id }}",
                tint = if (playing) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

/** 受控播放器：状态全部来自控制器，UI 不自己持有播放器。 */
@Composable
private fun AudioControls(
    state: AudioPlayerController.State,
    onToggle: () -> Unit,
    onSeek: (Int) -> Unit,
    onRate: (Float) -> Unit,
    onSleep: (Int) -> Unit,
) {
    // 拖动期间用本地值驱动滑块：控制器每 500ms 回写位置，直接绑 state 会把拇指拽回去
    var dragPercent by remember { mutableStateOf<Float?>(null) }
    val live = AudioPlayerController.percent(state.positionMs, state.durationMs)
    val shown = dragPercent ?: live.toFloat()
    val shownMs = dragPercent?.let { AudioPlayerController.fromPercent(it.toInt(), state.durationMs) }
        ?: state.positionMs
    Column(
        Modifier
            .fillMaxWidth()
            .padding(top = Space.md),
    ) {
        /* -------------------- 进度条 -------------------- */
        Slider(
            value = shown,
            onValueChange = { v -> if (state.durationMs > 0) dragPercent = v },
            onValueChangeFinished = {
                val target = dragPercent?.let {
                    AudioPlayerController.fromPercent(it.toInt(), state.durationMs)
                }
                dragPercent = null
                if (target != null && target != state.positionMs) onSeek(target)
            },
            valueRange = 0f..100f,
            enabled = state.durationMs > 0,
            modifier = Modifier.fillMaxWidth(),
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                text = AudioPlayerController.formatTime(shownMs),
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = AudioPlayerController.formatTime(state.durationMs),
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        /* -------------------- 播放 / 暂停 -------------------- */
        Button(
            onClick = onToggle,
            enabled = state.prepared,
            shape = RoundedCornerShape(Radius.sm),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ),
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .padding(top = Space.md),
        ) {
            Icon(
                imageVector = if (state.playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
            )
            Text(
                text = if (state.playing) "暂停" else "播放",
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(start = Space.sm),
            )
        }

        /* -------------------- 倍速 -------------------- */
        Text(
            text = "倍速",
            fontSize = 12.sp,
            lineHeight = 16.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = Space.lg),
        )
        SegmentedRow(
            options = listOf("1x", "1.5x", "2x"),
            selected = rateLabel(state.rate),
            onSelect = { label -> onRate(rateValue(label)) },
            modifier = Modifier.padding(top = Space.sm),
        )

        /* -------------------- 定时 ------------------- */
        Text(
            text = "定时停止",
            fontSize = 12.sp,
            lineHeight = 16.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = Space.lg),
        )
        SegmentedRow(
            options = listOf("15分", "30分", "60分", "取消"),
            selected = sleepLabel(state.sleepAtMs),
            onSelect = { label -> onSleep(sleepMinutes(label)) },
            modifier = Modifier.padding(top = Space.sm),
        )

        if (state.message.isNotEmpty()) {
            Text(
                text = state.message,
                fontSize = 11.sp,
                lineHeight = 17.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = Space.sm),
            )
        }
    }
}

/* --------------------------- 档位映射 --------------------------- */

/** 倍速档位 → 倍率。 */
private fun rateValue(label: String): Float = when (label) {
    "1.5x" -> 1.5f
    "2x" -> 2f
    else -> 1f
}

/** 倍率 → 档位名。非标准倍率（如 1.25）落到「1x」。 */
private fun rateLabel(rate: Float): String = when (rate) {
    1.5f -> "1.5x"
    2f -> "2x"
    else -> "1x"
}

/** 定时档位 → 分钟。「取消」是 0，与控制器的 setSleepTimer(0) 语义一致。 */
private fun sleepMinutes(label: String): Int = when (label) {
    "15分" -> 15
    "30分" -> 30
    "60分" -> 60
    else -> 0
}

/**
 * 当前定时 → 档位名。
 *
 * 只在整点档位上高亮：定时是"到点就停"的一次性设置，
 * 剩余分钟数不固定，用剩余时间反推档位会在每分钟都跳档。
 */
private fun sleepLabel(sleepAtMs: Long): String {
    if (sleepAtMs <= 0L) return "取消"
    val remain = sleepAtMs - System.currentTimeMillis()
    val minutes = (remain / 60_000L).toInt()
    return when {
        minutes > 45 -> "60分"
        minutes > 20 -> "30分"
        else -> "15分"
    }
}
