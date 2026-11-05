package com.alanmaster.xread.data

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 听书榜解析：只认 tab_type=5 那一格，并把 listen_count 当成「在听人数」。
 */
class AudioFeedTest {

    /** 上游 tab_item 的形状：十个 tab，只有听书那格带 cell_data。 */
    private fun payload() = JSONObject("""
        {
            "code": 0,
            "data": {
                "tab_item": [
                    {
                        "tab_type": 2,
                        "tab_title": "推荐",
                        "cell_data": [
                            {
                                "cell_name": "猜你喜欢",
                                "cell_data": [
                                    {
                                        "book_data": [
                                            {
                                                "book_id": "9000000000000000001",
                                                "book_name": "推荐流里的书",
                                                "author": "甲",
                                                "read_count": "999999"
                                            }
                                        ]
                                    }
                                ]
                            }
                        ]
                    },
                    {
                        "tab_type": 5,
                        "tab_title": "听书",
                        "cell_data": [
                            {
                                "cell_name": "爆款网文听听看",
                                "cell_data": [
                                    {
                                        "book_data": [
                                            {
                                                "book_id": "7571044867611036697",
                                                "book_name": "民间算命大师",
                                                "author": "道爷慈悲",
                                                "audio_thumb_url_hd": "https://img.example.com/a.jpg",
                                                "listen_count": "224766",
                                                "read_count": "228062",
                                                "pure_category_tags": "都市日常,第一人称",
                                                "serial_count": "751",
                                                "creation_status": "1",
                                                "score": "9.0"
                                            },
                                            {
                                                "book_id": "7189544077984009277",
                                                "book_name": "军神出狱",
                                                "author": "梭罗果",
                                                "listen_count": "310000",
                                                "score": "9.1"
                                            }
                                        ]
                                    }
                                ]
                            }
                        ]
                    },
                    {
                        "tab_type": 9,
                        "tab_title": "漫画",
                        "cell_data": []
                    }
                ]
            }
        }
    """.trimIndent())

    @Test
    fun picksOnlyTheAudioTab() {
        val books = ContentParser.audioFeed(payload())
        assertEquals(2, books.size)
        assertTrue(books.none { it.title == "推荐流里的书" })
        assertEquals("民间算命大师", books[0].title)
        assertEquals("军神出狱", books[1].title)
    }

    @Test
    fun listenCountBecomesTheShownNumber() {
        val books = ContentParser.audioFeed(payload())
        // listen_count 优先于 read_count：页面上写的是「在听」，不是「在读」。
        assertEquals(224766L, books[0].readCount)
        assertEquals(310000L, books[1].readCount)
    }

    @Test
    fun keepsCoverAndCategory() {
        val books = ContentParser.audioFeed(payload())
        assertEquals("https://img.example.com/a.jpg", books[0].coverUrl)
        assertEquals("都市日常", books[0].category)
        assertEquals(751, books[0].chapterCount)
        assertEquals("9.0", books[0].score)
    }

    @Test
    fun missingAudioTabYieldsEmptyList() {
        val empty = JSONObject("""{"code":0,"data":{"tab_item":[{"tab_type":2,"cell_data":[]}]}}""")
        assertTrue(ContentParser.audioFeed(empty).isEmpty())
        assertTrue(ContentParser.audioFeed(JSONObject("{}")).isEmpty())
    }

    @Test
    fun duplicatesAreRemoved() {
        val dup = JSONObject("""
            {
                "code": 0,
                "data": {
                    "tab_item": [
                        {
                            "tab_type": 5,
                            "cell_data": [
                                {
                                    "cell_data": [
                                        {
                                            "book_data": [
                                                {"book_id":"1","book_name":"甲","listen_count":"10"},
                                                {"book_id":"1","book_name":"甲","listen_count":"10"}
                                            ]
                                        }
                                    ]
                                }
                            ]
                        }
                    ]
                }
            }
        """.trimIndent())
        assertEquals(1, ContentParser.audioFeed(dup).size)
    }
}
