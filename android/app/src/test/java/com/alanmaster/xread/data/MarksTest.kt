package com.alanmaster.xread.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class MarksTest {
    @get:Rule val temp = TemporaryFolder()

    private fun repo(root: File) = OfflineRepo(root, temp.newFolder())

    @Test fun addsDedupesAndRemoves() {
        val r = repo(File(temp.newFolder(), "offline"))
        assertTrue(r.addMark("123", "456", "第1章", Mark(3, "摘录A", Mark.KIND_HIGHLIGHT, 1000)))
        assertTrue(r.addMark("123", "456", "第1章", Mark(3, "摘录A2", Mark.KIND_HIGHLIGHT, 2000)))
        assertTrue(r.addMark("123", "456", "第1章", Mark(3, "摘录A", Mark.KIND_BOOKMARK, 3000)))

        val marks = r.marks("123", "456")
        assertEquals("同段落同类型应去重，不同类型应并存", 2, marks.size)
        assertEquals("摘录A2", marks.first { it.kind == Mark.KIND_HIGHLIGHT }.text)

        assertTrue(r.removeMark("123", "456", 3, Mark.KIND_HIGHLIGHT))
        assertEquals(1, r.marks("123", "456").size)
        assertTrue(r.removeMark("123", "456", 3, Mark.KIND_BOOKMARK))
        assertEquals(0, r.marks("123", "456").size)
    }

    @Test fun allMarksIsSortedAndCarriesChapterTitle() {
        val r = repo(File(temp.newFolder(), "offline"))
        r.addMark("1", "c1", "第1章", Mark(0, "a", Mark.KIND_HIGHLIGHT, 100))
        r.addMark("1", "c2", "第2章", Mark(5, "b", Mark.KIND_BOOKMARK, 300))
        val all = r.allMarks()
        assertEquals(2, all.size)
        assertEquals("第2章", all.first().chapterTitle)
        assertTrue(all.first().mark.at >= all.last().mark.at)
    }

    @Test fun exportsMarkdown() {
        val r = repo(File(temp.newFolder(), "offline"))
        r.addMark("1", "c1", "第1章", Mark(0, "第一段摘录", Mark.KIND_HIGHLIGHT, 100))
        val file = r.exportMarks(mapOf("1" to "书名"))
        assertNotNull(file)
        val text = file!!.readText()
        assertTrue(text.contains("书名"))
        assertTrue(text.contains("第1章"))
        assertTrue(text.contains("第一段摘录"))
    }

    @Test fun exportReturnsNullWhenEmpty() {
        val r = repo(File(temp.newFolder(), "offline"))
        assertEquals(null, r.exportMarks())
    }

    @Test fun backupCarriesMarksIntoFreshRepository() {
        val source = repo(File(temp.newFolder(), "offline"))
        source.addMark("123", "456", "第1章", Mark(7, "备份里的摘录", Mark.KIND_HIGHLIGHT, 500))
        assertNotNull(source.exportBackup())

        // 全新数据根目录 + 同一个导出目录，逼真模拟换机恢复
        val fresh = OfflineRepo(File(temp.newFolder(), "offline2"), source.exportDir)
        val msg = fresh.importBackup()
        assertTrue(msg, msg.contains("导入完成"))
        val marks = fresh.marks("123", "456")
        assertEquals(1, marks.size)
        assertEquals("备份里的摘录", marks.first().text)
    }

    @Test fun splitsTagsOnCommonSeparators() {
        assertEquals(
            listOf("人物", "伏笔"),
            Mark.splitTags("人物, 伏笔"),
        )
        assertEquals(
            listOf("人物", "伏笔", "线索"),
            Mark.splitTags("#人物　伏笔\n线索"),
        )
        assertEquals(emptyList<String>(), Mark.splitTags("   "))
    }

    @Test fun normalizesTagsDedupesAndCaps() {
        assertEquals(
            listOf("a", "b"),
            Mark.normalizeTags(Mark.splitTags("#a a,b b")),
        )
        val many = (1..12).joinToString(" ") { "t$it" }
        assertEquals(Mark.TAG_LIMIT, Mark.normalizeTags(Mark.splitTags(many)).size)
    }

    @Test fun noteAndTagsRoundTripThroughDisk() {
        val r = repo(File(temp.newFolder(), "offline"))
        r.addMark(
            "1", "c1", "第1章",
            Mark(0, "摘录", Mark.KIND_HIGHLIGHT, 100, note = "这里埋了伏笔", tags = listOf("人物")),
        )
        val mark = r.allMarks().single().mark
        assertEquals("这里埋了伏笔", mark.note)
        assertEquals(listOf("人物"), mark.tags)
        assertTrue(mark.hasNote)
    }

    @Test fun setMarkNoteRewritesOnlyTargetAndReportsMiss() {
        val r = repo(File(temp.newFolder(), "offline"))
        r.addMark("1", "c1", "第1章", Mark(0, "a", Mark.KIND_HIGHLIGHT, 100))
        r.addMark("1", "c1", "第1章", Mark(4, "b", Mark.KIND_HIGHLIGHT, 200))

        assertTrue(r.setMarkNote("1", "c1", 0, Mark.KIND_HIGHLIGHT, "批注", listOf("伏笔")))
        val marks = r.marks("1", "c1").sortedBy { it.paragraph }
        assertEquals("批注", marks.first().note)
        assertEquals(listOf("伏笔"), marks.first().tags)
        assertEquals("", marks.last().note)

        assertFalse(r.setMarkNote("1", "c1", 9, Mark.KIND_HIGHLIGHT, "x", emptyList()))
    }

    @Test fun exportUsesQuoteBlockOnlyWhenNoted() {
        val r = repo(File(temp.newFolder(), "offline"))
        r.addMark("1", "c1", "第1章", Mark(0, "无笔记摘录", Mark.KIND_HIGHLIGHT, 100, note = "", tags = emptyList()))
        val plain = r.exportMarks(mapOf("1" to "书名"))!!.readText()
        assertFalse("没有笔记时不该出现笔记段", plain.contains("笔记："))

        r.setMarkNote("1", "c1", 0, Mark.KIND_HIGHLIGHT, "我的批注", listOf("人物"))
        val noted = r.exportMarks(mapOf("1" to "书名"))!!.readText()
        assertTrue(noted.contains("> 无笔记摘录"))
        assertTrue(noted.contains("笔记：我的批注"))
        assertTrue(noted.contains("标签：#人物"))
    }

    @Test fun emptyNoteIsNotPersistedAsKey() {
        val root = File(temp.newFolder(), "offline")
        val r = repo(root)
        r.addMark("1", "c1", "第1章", Mark(0, "无笔记", Mark.KIND_HIGHLIGHT, 100))
        val raw = File(root, "marks.json").readText()
        assertFalse("空笔记不该写键，否则老格式文件会被无谓改写", raw.contains("\"n\":\"\""))
    }
}
