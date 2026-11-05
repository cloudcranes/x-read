package com.alanmaster.xread.data

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.URLDecoder

/**
 * 全书热评（`books/{id}/reviews`）解析的单测。
 *
 * fixture 用的是实测上游响应的真实形状：内容拆成 common / stat / expand 三组，
 * 用户名与头像还要再往下一层 `user_info.base_info` 里找。
 * 少一层就会解析成空值，所以这里逐个字段钉住。
 */
class BookHotReviewsTest {

    private fun payload(
        dataList: String = """
            [
              {
                "data_type": 4,
                "comment": {
                  "comment_id": "7365890123456789012",
                  "common": {
                    "content": { "text": "这本书的节奏太好了，停不下来。" },
                    "user_info": {
                      "base_info": { "user_name": "夜航船", "user_avatar": "https://img/a.jpg" }
                    },
                    "create_timestamp": 1785678735
                  },
                  "expand": { "book_id": "7300000000000000000", "score": "9.9",
                              "reply_show_count": 1, "reply_show_row": 3 },
                  "stat": { "digg_count": 3413, "reply_count": 107 }
                }
              }
            ]
        """.trimIndent(),
        listInfo: String? = """
            { "total": 437549, "has_more": true, "cursor": "{\"session_id\":\"abc\",\"offset\":20}" }
        """.trimIndent(),
    ): JSONObject {
        val root = JSONObject()
        val data = JSONObject()
        data.put("data_list", org.json.JSONArray(dataList))
        if (listInfo != null) data.put("common_list_info", JSONObject(listInfo))
        root.put("data", data)
        return root
    }

    @Test fun parsesFullEntry() {
        val page = SocialParser.bookHotReviews(payload())
        assertEquals(1, page.items.size)
        val r = page.items.first()
        assertEquals("7365890123456789012", r.id)
        assertEquals("夜航船", r.user)
        assertEquals("https://img/a.jpg", r.avatar)
        assertEquals("这本书的节奏太好了，停不下来。", r.text)
        assertEquals("9.9", r.score)
        assertEquals(3413, r.digg)
        assertEquals(107, r.replyCount)
        assertEquals(1785678735L, r.createdAt)
        assertEquals(437549, page.total)
        assertTrue(page.hasMore)
        assertEquals("{\"session_id\":\"abc\",\"offset\":20}", page.cursor)
    }

    @Test fun dropsEntryWithoutText() {
        val page = SocialParser.bookHotReviews(payload(dataList = """
            [
              { "data_type": 4, "comment": { "comment_id": "1",
                "common": { "content": { "text": "   " },
                            "user_info": { "base_info": { "user_name": "空白" } } } } },
              { "data_type": 4, "comment": { "comment_id": "2",
                "common": { "content": { "text": "有正文" } } } }
            ]
        """.trimIndent()))
        // 正文为空/全空白的不造条目；只有 id 缺的用兜底 id 保留
        assertEquals(1, page.items.size)
        assertEquals("有正文", page.items.first().text)
    }

    @Test fun missingFieldsDegradeToEmptyValues() {
        val page = SocialParser.bookHotReviews(payload(dataList = """
            [
              { "data_type": 4, "comment": { "common": { "content": { "text": "只有正文" } } } }
            ]
        """.trimIndent()))
        val r = page.items.first()
        assertEquals("只有正文", r.text)
        assertEquals("读者", r.user)
        assertEquals("", r.avatar)
        assertEquals("", r.score)
        assertEquals(0, r.digg)
        assertEquals(0, r.replyCount)
        assertEquals(0L, r.createdAt)
        // 缺 id 时兜底成可用的唯一 id，不能是空串（空串会让列表 key 撞车）
        assertTrue("缺 id 时应有兜底 id", r.id.isNotEmpty())
    }

    @Test fun missingCommonListInfoGivesEmptyPaging() {
        val page = SocialParser.bookHotReviews(payload(listInfo = null))
        assertEquals(1, page.items.size)
        assertEquals(0, page.total)
        assertFalse(page.hasMore)
        assertEquals("", page.cursor)
    }

    @Test fun missingDataGivesEmptyPage() {
        val empty = JSONObject()
        val page = SocialParser.bookHotReviews(empty)
        assertTrue(page.items.isEmpty())
        assertEquals(0, page.total)
        assertFalse(page.hasMore)
        assertEquals("", page.cursor)
    }

    @Test fun emptyDataListGivesEmptyPage() {
        val page = SocialParser.bookHotReviews(payload(dataList = "[]"))
        assertTrue(page.items.isEmpty())
        assertEquals(437549, page.total)
    }

    /**
     * cursor 透传：上游给的 cursor 是含引号与花括号的 JSON 字符串，
     * 必须整体 URL 编码后再拼进 query，原样回传给上游时才能被解回同一个值。
     */
    @Test fun cursorSurvivesUrlEncodingRoundTrip() {
        val cursor = "{\"session_id\":\"s-1\",\"offset\":20}"
        val encoded = Api.enc(cursor)
        assertFalse("编码后不能出现裸花括号", encoded.contains("{"))
        assertFalse("编码后不能出现裸引号", encoded.contains("\""))
        // 核心层用 form_urlencoded 解析 query，回解后必须与原值一致
        val decoded = URLDecoder.decode(encoded, "UTF-8")
        assertEquals(cursor, decoded)
    }
}
