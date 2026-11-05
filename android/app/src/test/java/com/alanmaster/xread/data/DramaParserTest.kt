package com.alanmaster.xread.data

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DramaParserTest {

    @Test
    fun parsesStandardDramaSeriesAndEpisodes() {
        val json = JSONObject("""
            {
              "code": 0,
              "data": {
                "series_id": "7612345678901234567",
                "series_title": "龙王赘婿",
                "series_intro": "三年之期已到，恭迎龙王回归！",
                "cover_url": "https://example.com/cover.jpg",
                "author": "阅文",
                "episode_cnt": 80,
                "content_type": 1,
                "item_data_list": [
                  {
                    "item_id": "ep_1",
                    "title": "第 1 集",
                    "duration": 120,
                    "cover_url": "https://example.com/ep1.jpg",
                    "episode_index": 1
                  },
                  {
                    "item_id": "ep_2",
                    "title": "第 2 集 龙王归来",
                    "duration": 115,
                    "cover_url": "https://example.com/ep2.jpg",
                    "episode_index": 2
                  }
                ]
              }
            }
        """.trimIndent())

        val series = DramaParser.dramaSeries("7612345678901234567", json)
        assertEquals("7612345678901234567", series.seriesId)
        assertEquals("龙王赘婿", series.title)
        assertEquals("三年之期已到，恭迎龙王回归！", series.intro)
        assertEquals(80, series.episodeCount)
        assertEquals(1, series.contentType)
        assertFalse(series.isMotionComic)
        assertEquals(2, series.episodes.size)

        val ep1 = series.episodes[0]
        assertEquals("ep_1", ep1.episodeId)
        assertEquals("第 1 集", ep1.title)
        assertEquals(120, ep1.durationSeconds)
        assertEquals(1, ep1.episodeIndex)

        val ep2 = series.episodes[1]
        assertEquals("ep_2", ep2.episodeId)
        assertEquals("第 2 集 龙王归来", ep2.title)
        assertEquals(115, ep2.durationSeconds)
    }

    @Test
    fun parsesMotionComicSeriesWithPseriesType() {
        val json = JSONObject("""
            {
              "code": 0,
              "data": {
                "title": "大奉打更人 漫剧",
                "abstract": "许七安的破案日常",
                "thumb_uri": "https://example.com/df.jpg",
                "pseries_type": 1004,
                "episodes": [
                  {
                    "video_id": "vid_101",
                    "item_title": "第一回 勾栏听曲",
                    "item_duration": 300
                  }
                ]
              }
            }
        """.trimIndent())

        val series = DramaParser.dramaSeries("comic_888", json)
        assertEquals("comic_888", series.seriesId)
        assertEquals("大奉打更人 漫剧", series.title)
        assertEquals("许七安的破案日常", series.intro)
        assertEquals(1004, series.contentType)
        assertTrue(series.isMotionComic)
        assertEquals(1, series.episodes.size)
        assertEquals("vid_101", series.episodes[0].episodeId)
        assertEquals("第一回 勾栏听曲", series.episodes[0].title)
        assertEquals(300, series.episodes[0].durationSeconds)
    }

    @Test
    fun parsesDramaFeedList() {
        val json = JSONObject("""
            {
              "code": 0,
              "data": [
                {
                  "cell_data": [
                    {
                      "series_id": "s_1",
                      "title": "重生之都市狂仙",
                      "thumb_url": "https://example.com/s1.jpg",
                      "episode_cnt": 60,
                      "content_type": 1
                    },
                    {
                      "series_id": "s_2",
                      "title": "师兄太稳健了 漫剧",
                      "thumb_url": "https://example.com/s2.jpg",
                      "episode_cnt": 30,
                      "content_type": 1004
                    }
                  ]
                }
              ]
            }
        """.trimIndent())

        val feed = DramaParser.dramaFeed(json)
        assertEquals(2, feed.size)
        assertEquals("s_1", feed[0].seriesId)
        assertEquals("重生之都市狂仙", feed[0].title)
        assertEquals("https://example.com/s1.jpg", feed[0].coverUrl)
        assertFalse(feed[0].isMotionComic)
        assertEquals("s_2", feed[1].seriesId)
        assertEquals("https://example.com/s2.jpg", feed[1].coverUrl)
        assertTrue(feed[1].isMotionComic)
    }

    @Test
    fun parsesDiverseCoverFormats() {
        // 1. Direct series_cover string (Fanqie/Hongguo real production schema)
        val json1 = JSONObject("""
            {
              "series_id": "7698294694886788121",
              "series_title": "和离后我带女儿横扫魔域",
              "series_cover": "https://p3-reading-sign.fqnovelpic.com/novel-pic/c37eb63db868db08eb95e0d8bacd0129.image",
              "series_intro": "女帝归来",
              "content_type": 1
            }
        """.trimIndent())
        val cover1 = DramaParser.parseCover(json1)
        assertEquals("https://p3-reading-sign.fqnovelpic.com/novel-pic/c37eb63db868db08eb95e0d8bacd0129.image", cover1)

        // 2. Short video clip with 'cover'
        val json2 = JSONObject("""
            {
              "series_id": "vid_clip_1",
              "title": "搞笑日常",
              "cover": "http://p3-novel.byteimg.com/clip.jpg"
            }
        """.trimIndent())
        val cover2 = DramaParser.parseCover(json2)
        assertEquals("https://p3-novel.byteimg.com/clip.jpg", cover2) // http upgraded to https

        // 3. Object with url_list
        val json3 = JSONObject("""
            {
              "series_id": "s_obj",
              "series_cover": {
                "url_list": [
                  "//p6-novel.byteimg.com/tos-cn-i/cover.png"
                ]
              }
            }
        """.trimIndent())
        val cover3 = DramaParser.parseCover(json3)
        assertEquals("https://p6-novel.byteimg.com/tos-cn-i/cover.png", cover3)

        // 4. Relative uri without scheme
        val json4 = JSONObject("""
            {
              "series_cover": "novel-pic/7654321.jpg"
            }
        """.trimIndent())
        val cover4 = DramaParser.parseCover(json4)
        assertEquals("https://p3-novel.byteimg.com/origin/novel-pic/7654321.jpg", cover4)

        // 5. Nested in video_data
        val json5 = JSONObject("""
            {
              "video_data": {
                "series_cover": "https://example.com/nested.png"
              }
            }
        """.trimIndent())
        val cover5 = DramaParser.parseCover(json5)
        assertEquals("https://example.com/nested.png", cover5)
    }

    @Test
    fun parsesAndSortsDanmakuItemsByOffset() {
        val json = JSONObject("""
            {
              "code": 0,
              "data": {
                "comments": [
                  {
                    "comment_id": "dm_2",
                    "text": "前方核能预警！！！",
                    "start_offset_time": 15000,
                    "user_info": { "user_name": "观众乙" }
                  },
                  {
                    "comment_id": "dm_1",
                    "text": "第一！",
                    "start_offset_time": 1000,
                    "user_info": { "user_name": "观众甲" }
                  },
                  {
                    "comment_id": "dm_3",
                    "content": { "text": "笑死我了哈哈哈" },
                    "business_param": { "start_offset_time": 25000 },
                    "user_info": { "base_info": { "user_name": "观众丙" } }
                  }
                ]
              }
            }
        """.trimIndent())

        val list = DramaParser.danmakuList(json)
        assertEquals(3, list.size)
        assertEquals("dm_1", list[0].id)
        assertEquals(1000L, list[0].offsetMs)
        assertEquals("第一！", list[0].text)
        assertEquals("观众甲", list[0].userName)

        assertEquals("dm_2", list[1].id)
        assertEquals(15000L, list[1].offsetMs)
        assertEquals("前方核能预警！！！", list[1].text)

        assertEquals("dm_3", list[2].id)
        assertEquals(25000L, list[2].offsetMs)
        assertEquals("笑死我了哈哈哈", list[2].text)
        assertEquals("观众丙", list[2].userName)
    }
}
