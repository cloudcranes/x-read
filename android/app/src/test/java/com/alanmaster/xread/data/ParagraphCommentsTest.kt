package com.alanmaster.xread.data

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.URLDecoder

/**
 * 单段段评（`books/{id}/reviews` + `para_index` + `item_version`）解析的单测。
 *
 * fixture 用的是实测上游响应的真实形状：条目把内容拆成 common / stat / expand 三组，
 * 用户名与头像还要再往下一层 `common.user_info.base_info` 里找。
 *
 * 与全书热评（[BookHotReviewsTest]）的关键差异只有一个：**cursor**。
 * 全书热评的 cursor 是 JSON 字符串（`{"session_id":…,"offset":20}`），
 * 段评这边上游给的是**数字字符串**（`"5"`），必须原样回传，不能转成 int。
 */
class ParagraphCommentsTest {

    private fun payload(
        dataList: String = """
            [
              {
                "data_type": 4,
                "comment": {
                  "comment_id": "7677871546636665662",
                  "common": {
                    "content": { "text": "对，修仙就得这么玩" },
                    "user_info": {
                      "base_info": { "user_name": "柳卿云", "user_avatar": "https://img/a.heic" }
                    },
                    "create_timestamp": 1787647246,
                    "group_id": "7663033361933271576"
                  },
                  "expand": { "book_id": "7662934798771424280", "score": "8.5",
                              "comment_pos": { "start_para_index": 66, "end_para_index": 66 },
                              "item_version": "1e3d6c97_1_7db266a3" },
                  "stat": { "digg_count": 263, "reply_count": 4, "show_pv": 1955 }
                }
              },
              {
                "data_type": 4,
                "comment": {
                  "comment_id": "7680144395240325912",
                  "common": {
                    "content": { "text": "老师:不是哥们[微笑]" },
                    "user_info": {
                      "base_info": { "user_name": "教导的饭桶", "user_avatar": "https://img/b.heic" }
                    },
                    "create_timestamp": 1787650000
                  },
                  "expand": { "score": "" },
                  "stat": { "digg_count": 67, "reply_count": 4 }
                }
              }
            ]
        """.trimIndent(),
        listInfo: String? = """
            { "total": 30, "has_more": true, "cursor": "3" }
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
        val page = SocialParser.paragraphComments(payload())
        assertEquals(2, page.items.size)
        val r = page.items.first()
        assertEquals("7677871546636665662", r.id)
        assertEquals("柳卿云", r.user)
        assertEquals("https://img/a.heic", r.avatar)
        assertEquals("对，修仙就得这么玩", r.text)
        assertEquals("8.5", r.score)
        assertEquals(263, r.digg)
        assertEquals(4, r.replyCount)
        assertEquals(1787647246L, r.createdAt)
        assertEquals(30, page.total)
        assertTrue(page.hasMore)
    }

    /**
     * cursor 是**数字字符串**，必须原样留着。
     *
     * 上游要回的就是这个字符串本身；转成 int 再拼回去会让分页从错误的
     * 位置继续，表现为翻页重复或跳段。
     */
    @Test fun keepsCursorAsRawNumericString() {
        val page = SocialParser.paragraphComments(payload())
        assertEquals("3", page.cursor)
        assertEquals("\"3\"", JSONObject.quote(page.cursor))
    }

    @Test fun cursorSurvivesUrlEncodingRoundTrip() {
        val cursor = "5"
        val encoded = Api.enc(cursor)
        assertFalse("编码后不能出现裸引号", encoded.contains("\""))
        // 核心层用 form_urlencoded 解析 query，回解后必须与原值一致
        assertEquals(cursor, URLDecoder.decode(encoded, "UTF-8"))
    }

    @Test fun dropsEntryWithoutText() {
        val page = SocialParser.paragraphComments(payload(dataList = """
            [
              { "data_type": 4, "comment": { "comment_id": "1",
                "common": { "content": { "text": "   " },
                            "user_info": { "base_info": { "user_name": "空白" } } } } },
              { "data_type": 4, "comment": { "comment_id": "2",
                "common": { "content": { "text": "有正文" } } } },
              { "data_type": 4, "comment": { "comment_id": "3",
                "common": { "content": {} } } }
            ]
        """.trimIndent()))
        // 正文为空/全空白的不造条目
        assertEquals(1, page.items.size)
        assertEquals("有正文", page.items.first().text)
    }

    @Test fun missingOptionalFieldsDegradeToEmptyValues() {
        val page = SocialParser.paragraphComments(payload(dataList = """
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
        val page = SocialParser.paragraphComments(payload(listInfo = null))
        assertEquals(2, page.items.size)
        assertEquals(0, page.total)
        assertFalse(page.hasMore)
        assertEquals("", page.cursor)
    }

    @Test fun missingDataGivesEmptyPage() {
        val page = SocialParser.paragraphComments(JSONObject())
        assertTrue(page.items.isEmpty())
        assertEquals(0, page.total)
        assertFalse(page.hasMore)
        assertEquals("", page.cursor)
    }

    @Test fun emptyDataListGivesEmptyPage() {
        val page = SocialParser.paragraphComments(payload(dataList = "[]"))
        assertTrue(page.items.isEmpty())
        assertEquals(30, page.total)
    }

    /**
     * 目录里的 `item_data_list[].version` 是段评请求的必需参数，
     * 解析时必须带上，漏了就等于没带 item_version，上游会拒 103001。
     */
    @Test fun chaptersKeepsItemVersion() {
        val payload = JSONObject()
        val data = JSONObject()
        data.put("item_data_list", org.json.JSONArray("""
            [
              { "item_id": "7244486474891004965", "title": "第一部分 感悟人生",
                "volume_name": "正文卷",
                "version": "b17026d435cc1ab49770cab12dc3f161_1_92d0e277c842dd7" },
              { "item_id": "7244486475947969593", "title": "第二章",
                "version": "66fc633442d4e0312b4b8faaccb3232c_1_c2730de8bd73657" }
            ]
        """.trimIndent()))
        payload.put("data", data)

        val chapters = ContentParser.chapters(payload)
        assertEquals(2, chapters.size)
        assertEquals("b17026d435cc1ab49770cab12dc3f161_1_92d0e277c842dd7", chapters[0].version)
        assertEquals("66fc633442d4e0312b4b8faaccb3232c_1_c2730de8bd73657", chapters[1].version)
        assertEquals("正文卷", chapters[0].volume)
    }

    /** 上游没给 version 时留空串，不编一个版本号去骗上游。 */
    @Test fun chaptersWithoutVersionKeepsEmptyString() {
        val payload = JSONObject()
        val data = JSONObject()
        data.put("item_data_list", org.json.JSONArray("""
            [ { "item_id": "1", "title": "无版本" } ]
        """.trimIndent()))
        payload.put("data", data)

        val chapters = ContentParser.chapters(payload)
        assertEquals(1, chapters.size)
        assertEquals("", chapters[0].version)
    }

    /**
     * 段评气泡：`data.data` 是以段落序号为键的对象。
     *
     * fixture 用实测上游形状：`hot` 是**数字字符串** `"0"` / `"1"`，
     * 不是布尔。`JSONObject.optBoolean` 只认 `"true"` / `"false"`，
     * 拿它读会把 `"1"` 读成 false——所以这里用 flag 解析。
     */
    @Test fun parsesBubbleDistributionWithStringFlags() {
        val root = JSONObject()
        val data = JSONObject()
        val inner = JSONObject()
        inner.put("0", JSONObject("""{ "count": 79512, "hot": "1", "is_author_comment": "1" }"""))
        inner.put("1", JSONObject("""{ "count": 4783, "hot": "0", "is_author_comment": false }"""))
        // 零评论的段落不占气泡
        inner.put("2", JSONObject("""{ "count": 0, "hot": "0" }"""))
        // 上游的全书汇总桶，不是某一段
        inner.put("10000", JSONObject("""{ "count": 158, "hot": "0" }"""))
        data.put("data", inner)
        root.put("data", data)

        val bubbles = SocialParser.paragraphBubbles(root)
        assertEquals(3, bubbles.size)
        assertEquals(listOf(0, 1, 10000), bubbles.map { it.index })
        assertTrue(bubbles[0].hot)
        assertTrue(bubbles[0].authorCommented)
        assertFalse(bubbles[1].hot)
        assertFalse(bubbles[1].authorCommented)
        assertEquals(79512, bubbles[0].count)
        assertEquals(4783, bubbles[1].count)
    }

    /** 布尔标记：数字字符串、字符串 "true"、真布尔都要读对。 */
    @Test fun flagReadsNumericStringsAndBooleans() {
        val o = JSONObject()
        o.put("a", "1"); o.put("b", "0"); o.put("c", "true"); o.put("d", false)
        o.put("e", 1); o.put("f", 0)
        assertTrue(o.flag("a"))
        assertFalse(o.flag("b"))
        assertTrue(o.flag("c"))
        assertFalse(o.flag("d"))
        assertTrue(o.flag("e"))
        assertFalse(o.flag("f"))
        // 缺字段走兜底
        assertTrue(o.flag("missing", true))
        assertFalse(o.flag("missing"))
    }
}
