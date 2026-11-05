package com.alanmaster.xread.data

import com.alanmaster.xread.ui.ReaderFlow
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class MangaModeTest {

    @Test
    fun parseMangaImages_stringList() {
        val json = JSONObject("""
            {
                "data": {
                    "images": [
                        "http://example.com/p1.jpg",
                        "http://example.com/p2.jpg"
                    ]
                }
            }
        """.trimIndent())
        val images = ContentParser.mangaImages(json)
        assertEquals(2, images.size)
        assertEquals("http://example.com/p1.jpg", images[0])
        assertEquals("http://example.com/p2.jpg", images[1])
    }

    @Test
    fun parseMangaImages_objectList() {
        val json = JSONObject("""
            {
                "data": {
                    "pic_list": [
                        {"url": "http://example.com/obj1.jpg"},
                        {"path": "/local/obj2.jpg"}
                    ]
                }
            }
        """.trimIndent())
        val images = ContentParser.mangaImages(json)
        assertEquals(2, images.size)
        assertEquals("http://example.com/obj1.jpg", images[0])
        assertEquals("/local/obj2.jpg", images[1])
    }

    @Test
    fun chapterContent_isMangaProperty() {
        val novel = ChapterContent(
            id = "1",
            title = "Novel Chap",
            paragraphs = listOf("P1", "P2"),
            previousId = null,
            nextId = null,
        )
        assertFalse(novel.isManga)

        val manga = ChapterContent(
            id = "2",
            title = "Manga Chap",
            paragraphs = emptyList(),
            images = listOf("img1.jpg", "img2.jpg", "img3.jpg"),
            previousId = null,
            nextId = null,
        )
        assertTrue(manga.isManga)
    }

    @Test
    fun readerFlow_mangaOffsetAndLocate() {
        val manga1 = ChapterContent(
            id = "c1",
            title = "Chap 1",
            paragraphs = emptyList(),
            images = listOf("img1", "img2", "img3"),
            previousId = null,
            nextId = "c2",
        )
        val manga2 = ChapterContent(
            id = "c2",
            title = "Chap 2",
            paragraphs = emptyList(),
            images = listOf("imgA", "imgB"),
            previousId = "c1",
            nextId = null,
        )
        val flow = listOf(manga1, manga2)

        // c1 header is at 0, items at 1, 2, 3
        // c2 header is at 4, items at 5, 6
        assertEquals(0, ReaderFlow.offsetOf(flow, 0))
        assertEquals(4, ReaderFlow.offsetOf(flow, 1))

        // Locate item 0 (c1 header) -> para 0
        val locHeader = ReaderFlow.locate(flow, 0)
        assertEquals(0, locHeader.first)
        assertEquals(0, locHeader.second)

        // Locate item 2 (c1 second image) -> para 1 (0-based)
        val locImg2 = ReaderFlow.locate(flow, 2)
        assertEquals(0, locImg2.first)
        assertEquals(1, locImg2.second)

        // Locate item 5 (c2 first image) -> c2, para 0
        val locC2Img1 = ReaderFlow.locate(flow, 5)
        assertEquals(1, locC2Img1.first)
        assertEquals(0, locC2Img1.second)
    }

    @Test
    fun offlineRepo_saveAndLoadMangaImages() {
        val tmpOffline = File.createTempFile("manga_off", "dir")
        tmpOffline.delete()
        tmpOffline.mkdirs()
        val tmpExport = File.createTempFile("manga_exp", "dir")
        tmpExport.delete()
        tmpExport.mkdirs()
        try {
            val repo = OfflineRepo(tmpOffline, tmpExport)
            val saved = repo.putChapter(
                bookId = "book_manga",
                chapterId = "m100",
                title = "Manga Offline",
                content = "",
                images = listOf("/data/img1.jpg", "/data/img2.jpg"),
            )
            assertTrue(saved)
            assertTrue(repo.hasChapter("book_manga", "m100"))

            val loaded = repo.getChapter("book_manga", "m100")
            assertEquals("m100", loaded?.optString("chapterId"))
            assertEquals("Manga Offline", loaded?.optString("title"))
            val arr = loaded?.optJSONArray("images")
            assertEquals(2, arr?.length())
            assertEquals("/data/img1.jpg", arr?.optString(0))
            assertEquals("/data/img2.jpg", arr?.optString(1))
        } finally {
            tmpOffline.deleteRecursively()
            tmpExport.deleteRecursively()
        }
    }
}
