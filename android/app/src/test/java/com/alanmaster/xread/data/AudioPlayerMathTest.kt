package com.alanmaster.xread.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** 听书控制器的纯函数契约：utteranceId 解析与进度换算。 */
class AudioPlayerMathTest {

    @Test fun percentIsZeroWhenDurationUnknown() {
        assertEquals(0, AudioPlayerController.percent(5000, 0))
        assertEquals(0, AudioPlayerController.percent(5000, -1))
    }

    @Test fun percentIsClamped() {
        assertEquals(0, AudioPlayerController.percent(0, 1000))
        assertEquals(50, AudioPlayerController.percent(500, 1000))
        assertEquals(100, AudioPlayerController.percent(1000, 1000))
        assertEquals("超出时长的位置不应算出 >100%", 100, AudioPlayerController.percent(9000, 1000))
        assertEquals("负位置不应算出负百分比", 0, AudioPlayerController.percent(-500, 1000))
    }

    @Test fun formatTimeHandlesHoursAndNegatives() {
        assertEquals("0:00", AudioPlayerController.formatTime(0))
        assertEquals("0:49", AudioPlayerController.formatTime(49_000))
        assertEquals("3:46", AudioPlayerController.formatTime(226_000))
        assertEquals("60:00", AudioPlayerController.formatTime(3_600_000))
        assertEquals("未知时长显示 0:00", "0:00", AudioPlayerController.formatTime(-1))
    }

    @Test fun fromPercentRoundTripsWithPercent() {
        val duration = 226_000
        listOf(0, 25, 50, 75, 100).forEach { p ->
            val ms = AudioPlayerController.fromPercent(p, duration)
            assertEquals("百分比 $p 往返不一致", p, AudioPlayerController.percent(ms, duration))
        }
    }

    @Test fun fromPercentIsSafeWithoutDuration() {
        assertEquals(0, AudioPlayerController.fromPercent(50, 0))
        assertEquals(0, AudioPlayerController.fromPercent(-10, 1000))
        assertEquals(1000, AudioPlayerController.fromPercent(200, 1000))
    }

    @Test fun utteranceIdCarriesChapterAndIndex() {
        assertEquals("c123" to 7, TtsController.parseUtteranceId("c123#7"))
        // 章标识本身含 # 时取最后一个分隔符，仍能正确还原
        assertEquals("a#b" to 3, TtsController.parseUtteranceId("a#b#3"))
    }

    @Test fun malformedUtteranceIdIsRejected() {
        assertNull(TtsController.parseUtteranceId(null))
        assertNull("没有分隔符的旧格式不应被当成有效回调", TtsController.parseUtteranceId("7"))
        assertNull(TtsController.parseUtteranceId("#5"))
        assertNull(TtsController.parseUtteranceId("c1#"))
        assertNull(TtsController.parseUtteranceId("c1#x"))
        assertNull(TtsController.parseUtteranceId("c1#-2"))
    }

    @Test fun staleChapterCallbackIsDetectable() {
        // 上一章的回调必须能被识别出来，否则会误判「本章读完」而自动跳章
        val fromOldChapter = TtsController.parseUtteranceId("old-chapter#99")
        assertEquals("old-chapter", fromOldChapter?.first)
        assertEquals(99, fromOldChapter?.second)
    }
}
