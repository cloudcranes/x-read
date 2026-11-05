package com.alanmaster.xread.data

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 评论楼中楼与点赞状态解析单测。
 */
class CommentRepliesTest {

    @Test
    fun parseCommentRepliesStandardFormat() {
        val json = JSONObject().apply {
            put("code", 0)
            put("data", JSONObject().apply {
                put("common_list_info", JSONObject().apply {
                    put("has_more", true)
                    put("cursor", "page_cursor_123")
                    put("total", 42)
                })
                put("reply_list", JSONArray().apply {
                    put(JSONObject().apply {
                        put("reply", JSONObject().apply {
                            put("reply_id", "rep_001")
                            put("text", "这是一条精彩的楼中楼回复")
                            put("author", 1)
                            put("reply_to_user_name", "楼主小明")
                            put("stat", JSONObject().apply {
                                put("digg_count", 15)
                                put("user_digg", 1)
                            })
                            put("create_timestamp", 1700000000L)
                            put("user_info", JSONObject().apply {
                                put("base_info", JSONObject().apply {
                                    put("user_name", "路人甲")
                                    put("user_avatar", "https://img.example.com/avatar.jpg")
                                })
                            })
                        })
                    })
                    put(JSONObject().apply {
                        put("reply_id", "rep_002")
                        put("text", "同意楼上说法")
                        put("create_timestamp", 1700000100L)
                        put("stat", JSONObject().apply {
                            put("digg_count", 0)
                            put("user_digg", 0)
                        })
                        put("user", JSONObject().apply {
                            put("name", "路人乙")
                        })
                    })
                })
            })
        }

        val page = SocialParser.commentReplies(json)
        assertTrue(page.hasMore)
        assertEquals("page_cursor_123", page.cursor)
        assertEquals(42, page.total)
        assertEquals(2, page.items.size)

        val first = page.items[0]
        assertEquals("rep_001", first.id)
        assertEquals("路人甲", first.user)
        assertEquals("https://img.example.com/avatar.jpg", first.avatar)
        assertEquals("这是一条精彩的楼中楼回复", first.text)
        assertEquals(15, first.digg)
        assertTrue(first.isAuthor)
        assertEquals("楼主小明", first.replyToUser)
        assertTrue(first.userLiked)

        val second = page.items[1]
        assertEquals("rep_002", second.id)
        assertEquals("路人乙", second.user)
        assertEquals("同意楼上说法", second.text)
        assertEquals(0, second.digg)
        assertFalse(second.isAuthor)
        assertFalse(second.userLiked)
    }

    @Test
    fun parseBookCommentsUserLiked() {
        val json = JSONObject().apply {
            put("data", JSONObject().apply {
                put("comment", JSONArray().apply {
                    put(JSONObject().apply {
                        put("comment_id", "c_1")
                        put("text", "神作！")
                        put("user_info", JSONObject().apply {
                            put("user_name", "读者A")
                        })
                        put("digg_count", 100)
                        put("user_digg", 1)
                        put("reply_count", 5)
                    })
                })
            })
        }

        val page = SocialParser.bookComments(json)
        assertEquals(1, page.items.size)
        val comment = page.items[0]
        assertEquals("c_1", comment.id)
        assertTrue(comment.userLiked)
        assertEquals(100, comment.digg)
        assertEquals(5, comment.replyCount)
    }

    @Test
    fun parseBookHotReviewsUserLiked() {
        val json = JSONObject().apply {
            put("data", JSONObject().apply {
                put("data_list", JSONArray().apply {
                    put(JSONObject().apply {
                        put("comment", JSONObject().apply {
                            put("comment_id", "hr_1")
                            put("common", JSONObject().apply {
                                put("content", JSONObject().apply { put("text", "神作！") })
                                put("user_info", JSONObject().apply {
                                    put("base_info", JSONObject().apply { put("user_name", "读者A") })
                                })
                            })
                            put("stat", JSONObject().apply {
                                put("digg_count", 100)
                                put("user_digg", 1)
                                put("reply_count", 5)
                            })
                        })
                    })
                })
            })
        }

        val page = SocialParser.bookHotReviews(json)
        assertEquals(1, page.items.size)
        val review = page.items[0]
        assertEquals("hr_1", review.id)
        assertTrue(review.userLiked)
        assertEquals(100, review.digg)
        assertEquals(5, review.replyCount)
    }

    @Test
    fun parseParagraphCommentsUserLiked() {
        val json = JSONObject().apply {
            put("data", JSONObject().apply {
                put("data_list", JSONArray().apply {
                    put(JSONObject().apply {
                        put("comment", JSONObject().apply {
                            put("comment_id", "pc_1")
                            put("common", JSONObject().apply {
                                put("content", JSONObject().apply { put("text", "这一段伏笔太绝了") })
                                put("user_info", JSONObject().apply {
                                    put("base_info", JSONObject().apply { put("user_name", "书友B") })
                                })
                            })
                            put("stat", JSONObject().apply {
                                put("digg_count", 8)
                                put("user_digg", 1)
                            })
                        })
                    })
                })
            })
        }

        val page = SocialParser.paragraphComments(json)
        assertEquals(1, page.items.size)
        val review = page.items[0]
        assertEquals("pc_1", review.id)
        assertTrue(review.userLiked)
        assertEquals(8, review.digg)
    }
}
