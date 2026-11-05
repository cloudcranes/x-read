package com.alanmaster.xread.data

import com.alanmaster.xread.data.LibraryViewModel.ShelfSort
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class ShelfMarkTest {

    @get:Rule val temp = TemporaryFolder()

    private fun dir(): File = File(temp.newFolder(), "library")

    @Test fun defaultsToReadingWithNoRatingOrTags() {
        val lib = LocalLibrary(dir())
        lib.put(Book(bookId = "1", title = "书"))
        val item = lib.entries().single()
        assertEquals(ShelfItem.STATUS_READING, item.status)
        assertEquals(0, item.rating)
        assertTrue(item.tags.isEmpty())
    }

    @Test fun persistsStatusRatingAndTags() {
        val d = dir()
        val lib = LocalLibrary(d)
        lib.put(Book(bookId = "1", title = "书"))
        assertTrue(lib.setStatus("1", ShelfItem.STATUS_DONE))
        assertTrue(lib.setRating("1", 4))
        assertTrue(lib.setTags("1", listOf("#人物", "伏笔", "人物")))

        // 换一个实例重新读盘，确认真的写进了 shelf.json
        val item = LocalLibrary(d).entries().single()
        assertEquals(ShelfItem.STATUS_DONE, item.status)
        assertEquals(4, item.rating)
        assertEquals(listOf("人物", "伏笔"), item.tags)
    }

    @Test fun rejectsUnknownStatusAndClampsRating() {
        val lib = LocalLibrary(dir())
        lib.put(Book(bookId = "1", title = "书"))
        assertFalse(lib.setStatus("1", "bogus"))
        assertEquals(ShelfItem.STATUS_READING, lib.entries().single().status)

        assertTrue(lib.setRating("1", 9))
        assertEquals(5, lib.entries().single().rating)
        assertTrue(lib.setRating("1", -3))
        assertEquals(0, lib.entries().single().rating)
    }

    @Test fun reAddingKeepsMarks() {
        val lib = LocalLibrary(dir())
        lib.put(Book(bookId = "1", title = "书"))
        lib.setStatus("1", ShelfItem.STATUS_WANT)
        lib.setRating("1", 3)
        lib.setTags("1", listOf("短篇"))

        // 用户重复点「加入书架」不该把整理结果清空
        lib.put(Book(bookId = "1", title = "书（改名）"))
        val item = lib.entries().single()
        assertEquals("书（改名）", item.book.title)
        assertEquals(ShelfItem.STATUS_WANT, item.status)
        assertEquals(3, item.rating)
        assertEquals(listOf("短篇"), item.tags)
    }

    @Test fun tagsAreCappedAndDeduped() {
        val lib = LocalLibrary(dir())
        lib.put(Book(bookId = "1", title = "书"))
        lib.setTags("1", (1..12).map { "t$it" } + listOf("t1", " t2 "))
        val tags = lib.entries().single().tags
        assertEquals(ShelfItem.TAG_LIMIT, tags.size)
        assertEquals(tags.distinct(), tags)
        assertFalse(tags.any { it.startsWith("#") })
    }

    @Test fun restoreBringsMarksBackAfterRemoval() {
        val lib = LocalLibrary(dir())
        lib.put(Book(bookId = "1", title = "书"))
        lib.setStatus("1", ShelfItem.STATUS_DONE)
        lib.setRating("1", 5)
        lib.setTags("1", listOf("神作"))
        val snapshot = lib.entries().single()

        lib.remove("1")
        assertTrue(lib.entries().isEmpty())

        lib.restore(snapshot)
        val back = lib.entries().single()
        assertEquals(ShelfItem.STATUS_DONE, back.status)
        assertEquals(5, back.rating)
        assertEquals(listOf("神作"), back.tags)
    }

    @Test fun backupRoundTripKeepsMarks() {
        val root = File(temp.newFolder(), "offline")
        val lib = LocalLibrary(File(root.parentFile, "library"))
        lib.put(Book(bookId = "1", title = "书"))
        lib.setStatus("1", ShelfItem.STATUS_DONE)
        lib.setRating("1", 4)
        lib.setTags("1", listOf("伏笔"))

        val repo = OfflineRepo(root, temp.newFolder())
        assertTrue(repo.exportBackup() != null)

        val root2 = File(temp.newFolder(), "offline2")
        val msg = OfflineRepo(root2, repo.exportDir).importBackup()
        assertTrue(msg, msg.contains("导入完成"))

        val restored = LocalLibrary(File(root2.parentFile, "library")).entries().single()
        assertEquals(ShelfItem.STATUS_DONE, restored.status)
        assertEquals(4, restored.rating)
        assertEquals(listOf("伏笔"), restored.tags)
    }

    @Test fun importDoesNotOverwriteLocalMarks() {
        // 备份里的标记只补空缺：本地已经整理过的书不该被老备份覆盖
        val root = File(temp.newFolder(), "offline")
        val lib = LocalLibrary(File(root.parentFile, "library"))
        lib.put(Book(bookId = "1", title = "书"))
        lib.setStatus("1", ShelfItem.STATUS_WANT)
        val repo = OfflineRepo(root, temp.newFolder())
        assertTrue(repo.exportBackup() != null)

        val root2 = File(temp.newFolder(), "offline2")
        val lib2 = LocalLibrary(File(root2.parentFile, "library"))
        lib2.put(Book(bookId = "1", title = "书"))
        lib2.setStatus("1", ShelfItem.STATUS_DONE)
        lib2.setRating("1", 5)

        OfflineRepo(root2, repo.exportDir).importBackup()
        val item = LocalLibrary(File(root2.parentFile, "library")).entries().single()
        assertEquals(ShelfItem.STATUS_DONE, item.status)
        assertEquals(5, item.rating)
    }

    @Test fun statusFilterStacksWithGroupAndMediaType() {
        val items = listOf(
            ShelfItem(book = Book(bookId = "1", title = "甲"), chapterId = "", chapterOffset = 0, updatedAt = "",
                group = "历史", status = ShelfItem.STATUS_DONE, addedAt = 1),
            ShelfItem(book = Book(bookId = "2", title = "乙", source = "manga"), chapterId = "", chapterOffset = 0, updatedAt = "",
                group = "历史", status = ShelfItem.STATUS_DONE, addedAt = 2),
            ShelfItem(book = Book(bookId = "3", title = "丙"), chapterId = "", chapterOffset = 0, updatedAt = "",
                group = "历史", status = ShelfItem.STATUS_READING, addedAt = 3),
            ShelfItem(book = Book(bookId = "4", title = "丁"), chapterId = "", chapterOffset = 0, updatedAt = "",
                group = "科幻", status = ShelfItem.STATUS_DONE, addedAt = 4),
        )
        // 读完 + 历史 + 小说：只剩甲
        assertEquals(
            listOf("1"),
            ShelfSort.apply(items, "recent", "历史", "novel", ShelfItem.STATUS_DONE).map { it.book.bookId },
        )
        assertEquals(3, ShelfSort.apply(items, "recent", "", "all", ShelfItem.STATUS_DONE).size)
        assertEquals(4, ShelfSort.apply(items, "recent", "", "all", "all").size)
        // 未知筛选值当作「全部」，不返回空列表
        assertEquals(4, ShelfSort.apply(items, "recent", "", "all", "bogus").size)
    }

    @Test fun shelfJsonStaysReadableForOldClients() {
        val d = dir()
        val lib = LocalLibrary(d)
        lib.put(Book(bookId = "1", title = "书"))
        // 老版本读不带 status 字段的 shelf.json 也能跑；这里确认写入的是合法 JSON 且键在
        val obj = JSONObject(File(d, "shelf.json").readText())
        assertTrue(obj.has("1"))
        assertEquals("书", obj.getJSONObject("1").optString("title"))
    }
}
