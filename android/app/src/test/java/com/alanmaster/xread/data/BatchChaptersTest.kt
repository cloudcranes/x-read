package com.alanmaster.xread.data

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BatchChaptersTest {

    @Test
    fun parseFullObjectItemInfos() {
        val json = JSONObject("""
            {
              "code": 0,
              "data": {
                "item_infos": {
                  "101": { "item_id": "101", "title": "第一章", "content": "第一章正文内容" },
                  "102": { "item_id": "102", "title": "第二章", "content": "第二章正文内容" },
                  "103": { "item_id": "103", "title": "第三章", "content": "" }
                }
              }
            }
        """.trimIndent())
        val map = ContentParser.batchChapters(json)
        assertEquals(2, map.size)
        assertEquals("第一章正文内容", map["101"])
        assertEquals("第二章正文内容", map["102"])
        assertTrue(!map.containsKey("103"))
    }

    @Test
    fun parseFullArrayItemInfos() {
        val json = JSONObject("""
            {
              "code": 0,
              "data": {
                "item_infos": [
                  { "item_id": "201", "content": "第201章" },
                  { "id": "202", "content": "第202章" },
                  { "item_id": "203", "content": "  " }
                ]
              }
            }
        """.trimIndent())
        val map = ContentParser.batchChapters(json)
        assertEquals(2, map.size)
        assertEquals("第201章", map["201"])
        assertEquals("第202章", map["202"])
        assertTrue(!map.containsKey("203"))
    }

    @Test
    fun parseBatchDataMap() {
        val json = JSONObject("""
            {
              "code": 0,
              "data": {
                "301": { "content": "第301章" },
                "302": { "content": "第302章" }
              }
            }
        """.trimIndent())
        val map = ContentParser.batchChapters(json)
        assertEquals(2, map.size)
        assertEquals("第301章", map["301"])
        assertEquals("第302章", map["302"])
    }

    @Test
    fun parseEmptyOrErrorPayload() {
        assertTrue(ContentParser.batchChapters(JSONObject("{}")).isEmpty())
        assertTrue(ContentParser.batchChapters(JSONObject("""{"code": 500, "message": "error"}""")).isEmpty())
    }
}
