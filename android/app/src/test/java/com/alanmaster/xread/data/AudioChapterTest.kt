package com.alanmaster.xread.data

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 听书章节解析。
 *
 * 重点在加密标记：上游实测同一份载荷里 `is_encrypt` 是布尔 `true`，
 * 也出现过字符串 `"True"` / `"1"`。用 optBoolean 读字符串数字一律当 false，
 * 于是「加密音频」被当成明文直接丢给系统播放器，播出来是一段噪音。
 */
class AudioChapterTest {

    private fun payload(row: String): JSONObject =
        JSONObject("""{"code":0,"data":[$row]}""".trimIndent())

    @Test
    fun plainAudioHasNoDecryptWarning() {
        val chapter = AudioParser.chapter(
            "c1", "第一集",
            payload("""{"main_url":"https://x/1.mp4","is_encrypt":false}"""),
        )!!
        assertFalse(chapter.encrypted)
        assertFalse(chapter.needsDecryptWarning)
        assertEquals("https://x/1.mp4", chapter.url)
    }

    @Test
    fun encryptedFlagIsReadFromStringTrue() {
        val chapter = AudioParser.chapter(
            "c1", "第一集",
            payload("""{"main_url":"https://x/1.mp4","is_encrypt":"True"}"""),
        )!!
        assertTrue(chapter.encrypted)
        // 没有本地解密副本 → 要提示
        assertTrue(chapter.needsDecryptWarning)
    }

    @Test
    fun encryptedFlagIsReadFromNumericString() {
        val chapter = AudioParser.chapter(
            "c1", "第一集",
            payload("""{"main_url":"https://x/1.mp4","is_encrypt":"1"}"""),
        )!!
        assertTrue(chapter.encrypted)
    }

    @Test
    fun decryptedLocalCopySuppressesTheWarning() {
        // core 拿到 cache=1 会自己下载并 CENC 解密到 /src：
        // 实测 is_encrypt=true 且 decrypted=true、cached_url 有值，这时音频能播，
        // 再挂一个「系统播放器可能无法播放」就是虚假告警。
        val chapter = AudioParser.chapter(
            "c1", "第一集",
            payload(
                """{"main_url":"https://x/1.mp4","is_encrypt":true,"decrypted":true,""" +
                    """"cached_url":"/src/1.mp4","cache_error":null}""",
            ),
        )!!
        assertTrue(chapter.encrypted)
        assertEquals("/src/1.mp4", chapter.cachedUrl)
        assertFalse(chapter.needsDecryptWarning)
    }

    @Test
    fun cacheErrorIsCarriedOutForDisplay() {
        val chapter = AudioParser.chapter(
            "c1", "第一集",
            payload(
                """{"main_url":"https://x/1.mp4","is_encrypt":true,""" +
                    """"cache_error":"download failed"}""",
            ),
        )!!
        assertEquals("download failed", chapter.cacheError)
        assertTrue(chapter.needsDecryptWarning)
    }

    @Test
    fun rowWithoutAnyUrlIsSkipped() {
        val chapter = AudioParser.chapter(
            "c1", "第一集",
            payload(
                """{"main_url":"","backup_url":"","is_encrypt":true},""" +
                    """{"main_url":"https://x/2.mp4","backup_url":"https://x/b.mp4"}""",
            ),
        )!!
        assertEquals("https://x/2.mp4", chapter.url)
        assertEquals("https://x/b.mp4", chapter.backupUrl)
    }

    @Test
    fun emptyPayloadYieldsNoChapter() {
        assertEquals(null, AudioParser.chapter("c1", "第一集", JSONObject("""{"code":0}""")))
    }
}
