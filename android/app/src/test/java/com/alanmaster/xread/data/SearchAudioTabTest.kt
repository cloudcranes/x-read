package com.alanmaster.xread.data

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 搜索页「听书」tab 的解析。
 *
 * 上游 search 接口把结果放在**顶层** `search_tabs` 里，每个 tab 各自带 data；
 * tab_type=2 是听书、3 是书籍、11 是短剧。听书与短剧不能回退到书籍 tab，
 * 否则用户点开才发现搜到的是同名小说而不是刚点的那本有声书。
 */
class SearchAudioTabTest {

    /** 三个 tab 同时存在，只有听书那格有数据。 */
    private fun payload() = JSONObject("""
        {
            "code": 0,
            "search_tabs": [
                {
                    "tab_type": 1,
                    "title": "综合",
                    "data": null
                },
                {
                    "tab_type": 3,
                    "title": "书籍",
                    "data": [
                        {
                            "book_data": [
                                {
                                    "book_id": "7571044867611036697",
                                    "book_name": "民间算命大师",
                                    "author": "道爷慈悲",
                                    "read_count": "228062",
                                    "listen_count": "224766"
                                }
                            ]
                        }
                    ],
                    "has_more": false,
                    "next_offset": 0,
                    "passback": ""
                },
                {
                    "tab_type": 2,
                    "title": "听书",
                    "data": [
                        {
                            "book_data": [
                                {
                                    "book_id": "7623743745996885054",
                                    "book_name": "命相实录【别名：民间算命大师】",
                                    "author": "道爷慈悲（主播：萧天意）",
                                    "book_type": "1",
                                    "listen_count": "14849",
                                    "read_count": "8876",
                                    "score": "8.4",
                                    "serial_count": "752",
                                    "audio_thumb_url_hd": "https://img.example.com/a.jpg",
                                    "pure_category_tags": "都市日常,第一人称"
                                }
                            ]
                        }
                    ],
                    "has_more": true,
                    "next_offset": 20,
                    "passback": "20"
                }
            ],
            "selected_tab_idx": 4
        }
    """.trimIndent())

    @Test
    fun picksTheAudioTabByTabType() {
        val page = ContentParser.searchPage(payload(), tabType = 2)
        assertEquals(1, page.books.size)
        assertEquals("7623743745996885054", page.books[0].bookId)
        // 同名的书籍 tab 不能混进来
        assertTrue(page.books.none { it.bookId == "7571044867611036697" })
    }

    @Test
    fun listenCountBecomesTheShownNumber() {
        val page = ContentParser.searchPage(payload(), tabType = 2)
        // 听书 tab 显示的是「在听人数」，不是「在读人数」
        assertEquals(14849L, page.books[0].readCount)
    }

    @Test
    fun bookTabKeepsReadCount() {
        val page = ContentParser.searchPage(payload(), tabType = 3)
        assertEquals(1, page.books.size)
        assertEquals(228062L, page.books[0].readCount)
    }

    @Test
    fun audioTabNeverFallsBackToTheBookTab() {
        val empty = JSONObject("""
            {
                "code": 0,
                "search_tabs": [
                    {
                        "tab_type": 3,
                        "title": "书籍",
                        "data": [
                            {
                                "book_data": [
                                    {"book_id": "7571044867611036697", "book_name": "民间算命大师", "read_count": "228062"}
                                ]
                            }
                        ]
                    },
                    {
                        "tab_type": 2,
                        "title": "听书",
                        "data": null
                    }
                ]
            }
        """.trimIndent())
        val page = ContentParser.searchPage(empty, tabType = 2)
        assertTrue(page.books.isEmpty())
    }

    @Test
    fun keepsPaginationCursor() {
        val page = ContentParser.searchPage(payload(), tabType = 2)
        assertTrue(page.hasMore)
        assertEquals(20, page.nextOffset)
        assertEquals("20", page.passback)
    }

    @Test
    fun keepsCoverAndCategory() {
        val page = ContentParser.searchPage(payload(), tabType = 2)
        assertEquals("https://img.example.com/a.jpg", page.books[0].coverUrl)
        assertEquals("都市日常", page.books[0].category)
        assertEquals(752, page.books[0].chapterCount)
        assertEquals("8.4", page.books[0].score)
    }

    @Test
    fun shortDramaTabIsNotTheAudioTab() {
        // tab_type=11 是短剧，data 里没有 book_data → 不该被当成听书结果
        val page = ContentParser.searchPage(payload(), tabType = 11)
        assertFalse(page.books.any { it.bookId == "7623743745996885054" })
    }
}
