package com.alanmaster.xread.data

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 短剧弹幕的真实上游载荷是嵌套的：
 * 文本在 comment.common.content.text，编号在 comment.comment_id，偏移在 comment.expand.offset_time。
 * 早期载荷是扁平的（comment_id / text / start_offset_time），两种都要认。
 */
class DanmakuPayloadTest {

    @Test
    fun parsesNestedUpstreamPayload() {
        val json = JSONObject("""
            {
              "code": 0,
              "data": {
                "data_list": [
                  {
                    "comment": {
                      "comment_id": "7320000000000000001",
                      "common": {
                        "content": { "text": "鬼呀" },
                        "user_info": { "base_info": {}, "user_id": "1417701892299675" }
                      },
                      "expand": { "offset_time": 67777 },
                      "stat": { "digg_count": 0, "reply_count": 0 }
                    },
                    "data_type": 4
                  }
                ]
              }
            }
        """.trimIndent())

        val list = DramaParser.danmakuList(json)
        assertEquals(1, list.size)
        assertEquals("7320000000000000001", list[0].id)
        assertEquals("鬼呀", list[0].text)
        assertEquals(67777L, list[0].offsetMs)
    }

    @Test
    fun parsesSeveralNestedItemsSortedByOffset() {
        val json = JSONObject("""
            {
              "data": {
                "data_list": [
                  { "comment": { "comment_id": "b", "common": { "content": { "text": "帝君" } }, "expand": { "offset_time": 88225 } } },
                  { "comment": { "comment_id": "a", "common": { "content": { "text": "神仙" } }, "expand": { "offset_time": 87120 } } }
                ]
              }
            }
        """.trimIndent())

        val list = DramaParser.danmakuList(json)
        assertEquals(2, list.size)
        assertEquals("a", list[0].id)
        assertEquals(87120L, list[0].offsetMs)
        assertEquals("b", list[1].id)
        assertEquals(88225L, list[1].offsetMs)
    }

    @Test
    fun keepsParsingFlatLegacyPayload() {
        val json = JSONObject("""
            {
              "data": {
                "comments": [
                  { "comment_id": "dm_1", "text": "第一！", "start_offset_time": 1000 }
                ]
              }
            }
        """.trimIndent())

        val list = DramaParser.danmakuList(json)
        assertEquals(1, list.size)
        assertEquals("dm_1", list[0].id)
        assertEquals("第一！", list[0].text)
        assertEquals(1000L, list[0].offsetMs)
    }

    @Test
    fun sendFailureExplainsMissingLogin() {
        assertEquals(
            "弹幕发不出去：上游要求番茄登录态，拾页没有登录账号。",
            danmakuSendFailure("用户未登录")
        )
        assertEquals(
            "弹幕发不出去：上游要求番茄登录态，拾页没有登录账号。",
            danmakuSendFailure("弹幕发送失败（103006）")
        )
    }

    @Test
    fun sendFailureKeepsOtherReasons() {
        assertEquals("弹幕发送失败，请稍后再试。", danmakuSendFailure(""))
        assertEquals("弹幕发送失败：网络超时", danmakuSendFailure("网络超时"))
    }
}
