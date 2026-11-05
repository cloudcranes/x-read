package com.alanmaster.xread.data

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TimelineParserTest {

    @Test
    fun parseStandardTimeline() {
        val json = JSONObject("""
            {
              "code": 0,
              "data": {
                "speech_text": "[1760,0]<1760,0,0>本节目由番茄畅听出品\n[8810,0]<8810,0,0>第一集\n[17030,0]<17030,0,0>浓雾中\n[18700,0]<18700,0,0>一辆破旧的大巴缓缓驶来"
              }
            }
        """.trimIndent())
        val tl = TimelineParser.parse("chap_1", json)
        assertEquals("chap_1", tl.chapterId)
        assertEquals(4, tl.sentences.size)

        val s0 = tl.sentences[0]
        assertEquals(1760, s0.startMs)
        assertEquals(8810, s0.endMs)
        assertEquals("本节目由番茄畅听出品", s0.text)

        val s1 = tl.sentences[1]
        assertEquals(8810, s1.startMs)
        assertEquals(17030, s1.endMs)
        assertEquals("第一集", s1.text)

        val s3 = tl.sentences[3]
        assertEquals(18700, s3.startMs)
        assertEquals(23700, s3.endMs)
        assertEquals("一辆破旧的大巴缓缓驶来", s3.text)

        // findCurrentIndex 验证
        assertEquals(-1, tl.findCurrentIndex(500))
        assertEquals(0, tl.findCurrentIndex(1760))
        assertEquals(0, tl.findCurrentIndex(5000))
        assertEquals(1, tl.findCurrentIndex(8810))
        assertEquals(1, tl.findCurrentIndex(12000))
        assertEquals(2, tl.findCurrentIndex(17030))
        assertEquals(3, tl.findCurrentIndex(18700))
        assertEquals(3, tl.findCurrentIndex(30000))
    }

    @Test
    fun parseWithoutAngleBrackets() {
        val json = JSONObject("""
            {
              "code": 0,
              "data": {
                "speech_text": "[1000, 0] 第一句\n[3500, 0] 第二句"
              }
            }
        """.trimIndent())
        val tl = TimelineParser.parse("chap_2", json)
        assertEquals(2, tl.sentences.size)
        assertEquals("第一句", tl.sentences[0].text)
        assertEquals(1000, tl.sentences[0].startMs)
        assertEquals(3500, tl.sentences[0].endMs)
        assertEquals("第二句", tl.sentences[1].text)
        assertEquals(3500, tl.sentences[1].startMs)
        assertEquals(8500, tl.sentences[1].endMs)
    }

    @Test
    fun parseEmptyOrErrorPayload() {
        val emptyTl = TimelineParser.parse("c_err", JSONObject("{}"))
        assertTrue(emptyTl.sentences.isEmpty())
        assertEquals(-1, emptyTl.findCurrentIndex(1000))

        val noSpeech = TimelineParser.parse("c_err2", JSONObject("""{"code": 0, "data": {"speech_text": ""}}"""))
        assertTrue(noSpeech.sentences.isEmpty())
    }
}
