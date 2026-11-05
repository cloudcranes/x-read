package com.alanmaster.xread.data

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 综合 tab（tab_type=1）会把小说和有声书混在一条列表里。
 *
 * 之前判定「是不是有声书」只看当前 tab，于是综合 tab 里的有声书被当小说打开，
 * 点进去正文是空的。现在改成按条目自己的 book_type 判断。
 */
class SearchMixedAudioTabTest {

    private fun payload() = JSONObject("""
        {
            "code": 0,
            "search_tabs": [
                {
                    "tab_type": 1,
                    "title": "综合",
                    "data": [
                        {
                            "book_data": [
                                {
                                    "book_id": "7571044867611036697",
                                    "book_name": "民间算命大师",
                                    "book_type": "0",
                                    "read_count": "228062",
                                    "listen_count": "224766"
                                }
                            ]
                        },
                        {
                            "book_data": [
                                {
                                    "book_id": "7623743745996885054",
                                    "book_name": "命相实录【别名：民间算命大师】",
                                    "book_type": "1",
                                    "read_count": "8876",
                                    "listen_count": "14849",
                                    "serial_count": "752"
                                }
                            ]
                        }
                    ],
                    "has_more": false,
                    "next_offset": 0,
                    "passback": ""
                }
            ],
            "selected_tab_idx": 0
        }
    """.trimIndent())

    @Test
    fun marksOnlyTheAudioEntry() {
        val page = ContentParser.searchPage(payload(), tabType = 1)
        assertEquals(2, page.books.size)
        assertFalse(page.books[0].isAudio)
        assertTrue(page.books[1].isAudio)
    }

    @Test
    fun audioEntryShowsListenCountEvenInsideTheMixedTab() {
        val page = ContentParser.searchPage(payload(), tabType = 1)
        // 小说仍是「在读 228062」，有声书显示「在听 14849」
        assertEquals(228062L, page.books[0].readCount)
        assertEquals(14849L, page.books[1].readCount)
    }

    @Test
    fun audioEntryKeepsItsOwnId() {
        val page = ContentParser.searchPage(payload(), tabType = 1)
        // 听书页要拿 book_id 去查音色，不能被同名小说顶掉
        assertEquals("7623743745996885054", page.books[1].bookId)
    }
}
