package com.alanmaster.xread.data

import kotlinx.coroutines.launch
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * 数据安全回归：这些用例对应审查确认的「一次读失败就把整份数据写空」
 * 与「并发写互相截断」两类缺陷。
 */
class OfflineRepoSafetyTest {
    @get:Rule val temp = TemporaryFolder()

    private fun repo(): OfflineRepo =
        OfflineRepo(temp.newFolder("offline"), temp.newFolder("export"))

    @Test fun missingFilesStartEmptyInsteadOfFailing() {
        val r = repo()
        assertNull(r.progress("123"))
        assertEquals(0, r.savedConcurrency())
        assertTrue(r.rules().isEmpty())
        assertTrue(r.tasks().isEmpty())
        assertEquals(0L, r.stats().optLong("seconds"))
    }

    @Test fun exportBookTextFollowsDirectoryOrder() {
        val r = repo()
        // 目录顺序与 id 字典序故意相反：按文件名排序会把「第10章」插到「第2章」前面
        r.putBook(Book(bookId = "b1", title = "测试书", author = "某某"),
            listOf(Chapter("11", "第2章"), Chapter("12", "第10章")))
        r.putChapter("b1", "11", "第2章", "正文二")
        r.putChapter("b1", "12", "第10章", "正文十")
        val f = r.exportBookText("b1")
        assertNotNull(f)
        val text = f!!.readText()
        assertTrue(text.startsWith("测试书"))
        assertTrue(text.contains("作者：某某"))
        assertTrue(text.indexOf("正文二") < text.indexOf("正文十"))
    }

    @Test fun exportBookTextDropsRepeatedChapterTitle() {
        val r = repo()
        r.putBook(Book(bookId = "b3", title = "T"), listOf(Chapter("1", "第1章 起")))
        // 上游正文常以章节标题开头，导出后标题会出现两次
        r.putChapter("b3", "1", "第1章 起", "第1章 起\n正文甲")
        val text = r.exportBookText("b3")!!.readText()
        assertEquals(1, Regex("第1章 起").findAll(text).count())
        assertTrue(text.contains("正文甲"))
    }

    @Test fun exportBookTextSkipsBooksWithoutBodies() {
        val r = repo()
        r.putBook(Book(bookId = "b2", title = "空书"), listOf(Chapter("1", "第1章")))
        // 只有目录没有正文时不能留一个空 TXT 冒充导出成功
        assertNull(r.exportBookText("b2"))
    }

    @Test fun corruptProgressIsPreservedNotOverwritten() {
        val dir = temp.newFolder("offline2")
        val f = File(dir, "progress.json")
        f.writeText("{ this is not json")
        val r = OfflineRepo(dir, temp.newFolder("export2"))

        // 读：损坏时返回空，但坏文件必须另存，不能被直接清掉
        assertNull(r.progress("1"))
        assertNotNull("损坏的 progress.json 被直接清掉了", r.lastWarning)
        val kept = dir.listFiles()?.filter { it.name.contains(".corrupt-") }.orEmpty()
        assertEquals("坏文件没有被另存", 1, kept.size)
        assertTrue("另存的文件不是原始内容", kept[0].readText().contains("not json"))

        // 写：之后应当能正常保存，且坏文件仍在
        assertTrue(r.saveProgress("1", "c1", 7))
        assertEquals("c1" to 7, r.progress("1"))
        assertEquals(1, dir.listFiles()?.count { it.name.contains(".corrupt-") })
    }

    @Test fun writeFailureDoesNotTruncateExistingFile() {
        val dir = temp.newFolder("offline3")
        dir.mkdirs()
        val f = File(dir, "progress.json")
        f.writeText("""{"1":{"chapterId":"c1","offset":5}}""")
        val r = OfflineRepo(dir, temp.newFolder("export3"))
        // 目标位置被建成目录 → rename/移动都会失败，旧文件内容必须保住
        val blocker = File(dir, "stats.json")
        blocker.mkdirs()
        assertFalse(r.addStats(10, 100, 1))
        assertEquals("""{"1":{"chapterId":"c1","offset":5}}""", f.readText())
    }

    @Test fun repeatedSavesKeepAllKeys() {
        val r = repo()
        assertTrue(r.saveProgress("1", "c1", 1))
        assertTrue(r.saveProgress("2", "c2", 2))
        assertTrue(r.saveProgress("3", "c3", 3))
        assertEquals("c1" to 1, r.progress("1"))
        assertEquals("c2" to 2, r.progress("2"))
        assertEquals("c3" to 3, r.progress("3"))
    }

    @Test fun concurrencyAndRulesShareSettingsWithoutLosingKeys() {
        val r = repo()
        assertTrue(r.saveConcurrency(6))
        assertTrue(r.saveRules(listOf(ReplaceRule("一", "二"))))
        assertEquals("并发值被规则写入吃掉了", 6, r.savedConcurrency())
        assertEquals(1, r.rules().size)

        assertTrue(r.saveRules(listOf(ReplaceRule("三", "四"), ReplaceRule("五", "六"))))
        assertEquals("替换规则被并发值写入吃掉了", 6, r.savedConcurrency())
        assertEquals(2, r.rules().size)
    }

    @Test fun parallelWritersDoNotCorruptFile() {
        val r = repo()
        val threads = 8
        val perThread = 40
        val pool = Executors.newFixedThreadPool(threads)
        val start = CountDownLatch(1)
        repeat(threads) { t ->
            pool.submit {
                start.await()
                repeat(perThread) { i -> r.saveProgress("${t}_$i", "c$i", i) }
            }
        }
        start.countDown()
        pool.shutdown()
        assertTrue("并发写超时", pool.awaitTermination(60, TimeUnit.SECONDS))

        // 所有键都在，且文件仍是合法 JSON（说明没有互相截断）
        var found = 0
        repeat(threads) { t ->
            repeat(perThread) { i ->
                if (r.progress("${t}_$i") != null) found++
            }
        }
        assertEquals("并发写入丢了键", threads * perThread, found)
    }

    @Test fun statsAccumulateAcrossCalls() {
        val r = repo()
        assertTrue(r.addStats(30, 900, 1))
        assertTrue(r.addStats(12, 300, 2))
        val s = r.stats()
        assertEquals(42L, s.optLong("seconds"))
        assertEquals(1200L, s.optLong("words"))
        assertEquals(3L, s.optLong("chapters"))
        assertEquals(42L, s.optJSONObject("days")?.let { d -> d.keys().asSequence().sumOf { d.optLong(it) } })
    }

    @Test fun taskRoundTripKeepsSkippedAndFailedCounts() {
        val r = repo()
        val task = OfflineRepo.DownloadTask(
            bookId = "1", source = "fanqie", title = "书", author = "作者",
            maxChapters = 0, total = 100, done = 93, status = Downloader.STATUS_PARTIAL,
            updated = 5L, skipped = 4, failed = 3,
        )
        assertTrue(r.saveTask(task))
        val back = r.task("1")
        assertNotNull(back)
        assertEquals(Downloader.STATUS_PARTIAL, back!!.status)
        assertEquals(4, back.skipped)
        assertEquals(3, back.failed)
        assertEquals(93, back.done)
    }

    @Test fun tasksReadsFileOnceAndSortsByUpdated() {
        val r = repo()
        listOf(3L to "a", 1L to "b", 2L to "c").forEach { (t, id) ->
            r.saveTask(OfflineRepo.DownloadTask(id, "fanqie", id, "", 0, 10, 0,
                Downloader.STATUS_PAUSED, t))
        }
        val all = r.tasks()
        assertEquals(listOf("a", "c", "b"), all.map { it.bookId })
    }

    @Test fun chapterCountIgnoresInFlightTempFiles() {
        val dir = temp.newFolder("offline4")
        val r = OfflineRepo(dir, temp.newFolder("export4"))
        assertTrue(r.putChapter("9", "c1", "第一章", "正文"))
        // 模拟写入中途残留：临时文件不能被当成一章
        File(dir, "books/9/c_x.json.1234.tmp").writeText("{}")
        assertEquals(1, r.chapterCount("9"))
    }

    @Test fun chapterNeedsRefreshOnlyForOldFormatFiles() {
        val dir = temp.newFolder("offline4b")
        val r = OfflineRepo(dir, temp.newFolder("export4b"))
        assertTrue(r.putChapter("9", "c1", "第一章", "第一段\n第二段"))
        assertFalse("刚写下的章节不该被当成旧格式", r.chapterNeedsRefresh("9", "c1"))
        // 老版本批量下载写下的文件没有 fmt 字段，正文被折叠成一段
        File(dir, "books/9/c_c2.json").writeText(
            JSONObject().put("chapterId", "c2").put("title", "第二章")
                .put("content", "第一段 第二段").put("saved", 1L).toString(),
        )
        assertTrue("旧格式章节需要重下", r.chapterNeedsRefresh("9", "c2"))
        assertFalse("不存在的章节交给 hasChapter 判断", r.chapterNeedsRefresh("9", "c3"))
    }

    @Test fun lastSessionPicksTheMostRecentBook() {
        val dir = temp.newFolder("offline-last")
        val r = OfflineRepo(dir, temp.newFolder("export-last"))
        // 直接写文件：saveProgress 用的是 System.currentTimeMillis()，两次调用差不出先后
        File(dir, "progress.json").writeText(
            JSONObject()
                .put("11", JSONObject().put("chapterId", "c11").put("offset", 3).put("chapterIndex", 2).put("at", 1_000L))
                .put("22", JSONObject().put("chapterId", "c22").put("offset", 9).put("chapterIndex", 8).put("at", 5_000L))
                .put("33", JSONObject().put("chapterId", "c33").put("offset", 1).put("chapterIndex", 0).put("at", 3_000L))
                .toString(),
        )
        val session = r.lastSession()!!
        assertEquals("22", session.first.bookId)
        assertEquals(5_000L, session.second)
    }

    @Test fun lastSessionKeepsBookMetadataFromSaveProgress() {
        val dir = temp.newFolder("offline-last3")
        val r = OfflineRepo(dir, temp.newFolder("export-last3"))
        r.saveProgress("77", "c77", 4, 3,
            Book(bookId = "77", title = "书七", author = "作者七", coverUrl = "cover7"))
        assertEquals("书七", r.lastSession()!!.first.title)
        assertEquals("作者七", r.lastSession()!!.first.author)
        // 后续只带 id 的写法（比如切章）不能把已存的书目信息抹掉
        assertTrue(r.saveProgress("77", "c78", 0, 4))
        assertEquals("书七", r.lastSession()!!.first.title)
        assertEquals("c78", r.progress("77")!!.first)
    }

    @Test fun lastSessionIgnoresRecordsWithoutChapterOrTime() {
        val dir = temp.newFolder("offline-last2")
        val r = OfflineRepo(dir, temp.newFolder("export-last2"))
        assertNull("没有进度文件时不该造出一条会话", r.lastSession())
        File(dir, "progress.json").writeText(
            JSONObject()
                .put("44", JSONObject().put("chapterId", "").put("offset", 0).put("at", 9_000L))
                .put("55", JSONObject().put("chapterId", "c55").put("offset", 0).put("at", 0L))
                .toString(),
        )
        assertNull(r.lastSession())
    }

    @Test fun removeBookAlsoDropsItsTask() {
        val r = repo()
        r.putBook(Book(bookId = "7", title = "书"), listOf(Chapter("c1", "一")))
        r.saveTask(OfflineRepo.DownloadTask("7", "fanqie", "书", "", 0, 1, 0,
            Downloader.STATUS_PAUSED, 1L))
        assertNotNull(r.task("7"))
        assertTrue(r.removeBook("7"))
        assertNull("删书没有清掉下载任务，tasks.json 只增不减", r.task("7"))
    }

    @Test fun importRestoresSecondsWithoutDividingAgain() {
        val export = temp.newFolder("export5")
        val backup = JSONObject()
            .put("app", "x-read").put("version", 2)
            .put("stats", JSONObject().put("seconds", 3600).put("words", 500).put("chapters", 7))
        File(export, "x-read-backup.json").writeText(backup.toString())
        val r = OfflineRepo(temp.newFolder("offline5"), export)
        assertTrue(r.importBackup().startsWith("导入完成"))
        assertEquals("导入把秒当毫秒又除了一次 1000", 3600L, r.stats().optLong("seconds"))
        assertEquals(500L, r.stats().optLong("words"))
        assertEquals(7L, r.stats().optLong("chapters"))
    }

    @Test fun importReportsFailureInsteadOfPretendingSuccess() {
        val export = temp.newFolder("export6")
        File(export, "x-read-backup.json").writeText("{ broken")
        val r = OfflineRepo(temp.newFolder("offline6"), export)
        assertEquals("备份无法解析", r.importBackup())
    }
}

/** 书架排序：切换排序必须立刻生效，且不能丢掉分组过滤。 */
class ShelfSortContractTest {
    private fun item(id: String, title: String, added: Long, group: String = "") =
        ShelfItem(
            book = Book(bookId = id, title = title),
            chapterId = "", chapterOffset = 0, updatedAt = "",
            group = group, addedAt = added, knownChapters = 0,
        )

    private val items = listOf(
        item("1", "丙", 300, "History"),
        item("2", "甲", 100),
        item("3", "乙", 200, "History"),
    )

    @Test fun recentSortsByLastReadNotByAddedTime() {
        // 「最近」必须按最近阅读排序：旧实现与「入架」完全相同，是个假按钮。
        val withReads = listOf(
            item("1", "丙", 300).copy(lastReadAt = 100),
            item("2", "甲", 100).copy(lastReadAt = 900),
            item("3", "乙", 200).copy(lastReadAt = 500),
        )
        assertEquals(listOf("2", "3", "1"),
            LibraryViewModel.ShelfSort.apply(withReads, "recent", "").map { it.book.bookId })
        // 「入架」仍是入架时间倒序，两者应当不同
        assertEquals(listOf("1", "3", "2"),
            LibraryViewModel.ShelfSort.apply(withReads, "added", "").map { it.book.bookId })
        assertNotEquals(
            LibraryViewModel.ShelfSort.apply(withReads, "recent", "").map { it.book.bookId },
            LibraryViewModel.ShelfSort.apply(withReads, "added", "").map { it.book.bookId },
        )
    }

    @Test fun neverReadBooksFallBackToAddedTime() {
        val mixed = listOf(
            item("1", "丙", 300),                       // 从未读过
            item("2", "甲", 100).copy(lastReadAt = 900),
        )
        // 读过的排前面；两个都没读过时按入架时间倒序
        assertEquals(listOf("2", "1"),
            LibraryViewModel.ShelfSort.apply(mixed, "recent", "").map { it.book.bookId })
        val unread = listOf(item("1", "丙", 300), item("2", "甲", 100))
        assertEquals(listOf("1", "2"),
            LibraryViewModel.ShelfSort.apply(unread, "recent", "").map { it.book.bookId })
    }

    @Test fun titleSortIsApplied() {
        // 中文按 UTF-16 码位比较：丙(U+4E19) < 乙(U+4E59) < 甲(U+7532)
        assertEquals(listOf("1", "3", "2"),
            LibraryViewModel.ShelfSort.apply(items, "title", "").map { it.book.bookId })
    }

    @Test fun groupFilterSurvivesSortSwitch() {
        val filtered = LibraryViewModel.ShelfSort.apply(items, "title", "History")
        assertEquals(listOf("1", "3"), filtered.map { it.book.bookId })
        // 换成按最近阅读，分组过滤仍然生效
        val reSorted = LibraryViewModel.ShelfSort.apply(items, "added", "History")
        assertEquals(listOf("1", "3"), reSorted.map { it.book.bookId })
    }

    @Test fun blankGroupMeansAll() {
        assertEquals(3, LibraryViewModel.ShelfSort.apply(items, "recent", "").size)
    }

    @Test fun unknownGroupYieldsEmpty() {
        assertTrue(LibraryViewModel.ShelfSort.apply(items, "recent", "不存在").isEmpty())
    }
}

/** 自适应闸门：排队中被取消的协程不得泄漏许可。 */
class AdaptiveGateTest {
    @Test fun cancelledWaiterDoesNotLeakPermit() {
        val window = AdaptiveConcurrency(start = 1, min = 1, max = 4)
        val gate = AdaptiveGate(window)
        runBlockingShort { gate.acquire() }
        assertEquals(1, gate.activeCount)

        // 三个协程排队后全部被取消
        kotlinx.coroutines.runBlocking {
            val jobs = (1..3).map {
                launch(kotlinx.coroutines.Dispatchers.Default) {
                    runCatching { gate.acquire() }
                }
            }
            kotlinx.coroutines.delay(150)
            jobs.forEach { it.cancel() }
            jobs.forEach { runCatching { it.join() } }
        }

        assertEquals("取消的等待者泄漏了许可计数", 1, gate.activeCount)
        runBlockingShort { gate.release() }
        assertEquals("许可没有真正归还", 0, gate.activeCount)

        // 归还后仍能重新拿到，说明没有残留 active 卡住窗口
        runBlockingShort { gate.acquire() }
        assertEquals(1, gate.activeCount)
        runBlockingShort { gate.release() }
        assertEquals(0, gate.activeCount)
    }

    @Test fun releaseWakesExactlyOneQueuedWaiter() {
        val window = AdaptiveConcurrency(start = 1, min = 1, max = 4)
        val gate = AdaptiveGate(window)
        runBlockingShort { gate.acquire() }
        val reached = CountDownLatch(1)
        val t = Thread {
            runBlockingShort { gate.acquire(); reached.countDown() }
        }
        t.start()
        Thread.sleep(150)
        assertEquals("排队者不应提前放行", 1L, reached.count)
        runBlockingShort { gate.release() }
        assertTrue("归还许可后未唤醒排队者", reached.await(3, TimeUnit.SECONDS))
        t.join(3000)
        assertEquals(1, gate.activeCount)
        runBlockingShort { gate.release() }
    }

    private fun runBlockingShort(block: suspend () -> Unit) {
        kotlinx.coroutines.runBlocking { block() }
    }
}

/** 备份必须带上书架整理结果；规则改动后缓存要失效。 */
class BackupAndRulesTest {
    @get:Rule val temp = TemporaryFolder()

    private fun repoPair(): Pair<OfflineRepo, File> {
        val files = temp.newFolder("files")
        val export = temp.newFolder("export")
        return OfflineRepo(File(files, "offline"), export) to files
    }

    @Test fun backupCarriesShelfGrouping() {
        val (r, files) = repoPair()
        r.putBook(Book(bookId = "1", title = "书"), listOf(Chapter("c1", "一")))
        // 模拟书架：分组 + 入架时间 + 更新基线
        val shelfDir = File(files, "library")
        shelfDir.mkdirs()
        File(shelfDir, "shelf.json").writeText(
            """{"1":{"source":"fanqie","book_id":"1","title":"书","group":"History","added_at":123456,"known_chapters":42}}""")
        assertNotNull(r.exportBackup())
        val backup = File(files.parentFile, "export/x-read-backup.json")
        val text = backup.readText()
        assertTrue("备份未带书架分组", text.contains("History"))
        assertTrue("备份未带入架时间", text.contains("123456"))
    }

    @Test fun importRestoresGroupWhenLocalIsBlank() {
        val (r, files) = repoPair()
        val export = File(files.parentFile, "export")
        val shelfDir = File(files, "library")
        shelfDir.mkdirs()
        // 本地只有书目、没有整理信息
        File(shelfDir, "shelf.json").writeText(
            """{"1":{"source":"fanqie","book_id":"1","title":"书","group":"","added_at":0,"known_chapters":0}}""")
        File(export, "x-read-backup.json").writeText(
            """{"app":"x-read","version":2,"books":[],"shelf":{"1":{"book_id":"1","group":"History","added_at":999,"known_chapters":7}}}""")
        assertTrue(r.importBackup().startsWith("导入完成"))
        val restored = File(shelfDir, "shelf.json").readText()
        assertTrue("分组未恢复", restored.contains("History"))
        assertTrue("入架时间未恢复", restored.contains("999"))
    }

    @Test fun importDoesNotOverwriteExistingGroup() {
        val (r, files) = repoPair()
        val export = File(files.parentFile, "export")
        val shelfDir = File(files, "library")
        shelfDir.mkdirs()
        File(shelfDir, "shelf.json").writeText(
            """{"1":{"book_id":"1","group":"我的组","added_at":5,"known_chapters":3}}""")
        File(export, "x-read-backup.json").writeText(
            """{"app":"x-read","version":2,"books":[],"shelf":{"1":{"book_id":"1","group":"History","added_at":999,"known_chapters":7}}}""")
        r.importBackup()
        val restored = File(shelfDir, "shelf.json").readText()
        assertTrue("本地已有分组被备份覆盖了", restored.contains("我的组"))
        assertFalse(restored.contains("History"))
    }

    @Test fun rulesAreCachedUntilChanged() {
        val (r, _) = repoPair()
        assertTrue(r.saveRules(listOf(ReplaceRule("一", "壹"))))
        assertEquals("壹个", r.applyRules("一个"))
        // 再调一次仍应正确（走缓存路径）
        assertEquals("壹个", r.applyRules("一个"))
        // 修改规则后缓存必须失效
        assertTrue(r.saveRules(listOf(ReplaceRule("二", "贰"))))
        assertEquals("一个", r.applyRules("一个"))
        assertEquals("贰个", r.applyRules("二个"))
    }

    @Test fun regexRulesStillWorkAfterCaching() {
        val (r, _) = repoPair()
        assertTrue(r.saveRules(listOf(ReplaceRule("\\d+", "#", regex = true))))
        assertEquals("第#章", r.applyRules("第123章"))
        assertEquals("第#章", r.applyRules("第9章"))
    }

    @Test fun invalidRegexDoesNotBreakOtherRules() {
        val (r, _) = repoPair()
        assertTrue(r.saveRules(listOf(
            ReplaceRule("[", "x", regex = true),      // 非法正则
            ReplaceRule("坏", "好"),                    // 正常规则
        )))
        assertEquals("好词", r.applyRules("坏词"))
    }

    /**
     * 正文渲染也要套规则（旧实现只有朗读套，阅读页正文原样显示，
     * 用户配好规则后正文一字不变）。逐段套用必须保持段数不变，
     * 否则段评下标与书签段落号会整体错位。
     */
    @Test fun paragraphRulesKeepParagraphCountAndOrder() {
        val (r, _) = repoPair()
        // 替换文本里带换行：若对整篇正文替换，段数会被改写
        assertTrue(r.saveRules(listOf(ReplaceRule("广告位", "第一行\n第二行"))))
        val before = listOf("开头一段", "这里有广告位", "结尾一段")
        val after = r.applyRules(before)
        assertEquals("段数变了，段评下标会错位", before.size, after.size)
        assertEquals("开头一段", after[0])
        assertEquals("结尾一段", after[2])
        assertTrue("规则没生效", after[1].contains("第一行"))
    }

    @Test fun paragraphRulesAreNoOpWhenNoRuleEnabled() {
        val (r, _) = repoPair()
        val paragraphs = listOf("一", "二")
        assertEquals(paragraphs, r.applyRules(paragraphs))
    }
}

/**
 * 段评气泡下标换算：上游键与应用段落下标差一个标题行，不能挂错段。
 *
 * 上游键与段评请求参数 `para_index` 同一坐标系（0-based、不计标题行），
 * 而应用第 0 段恰是那行标题，所以应用下标 = 上游键 + 1。
 */
class ParagraphBubbleIndexTest {
    private fun b(index: Int, count: Int = 3) = ParagraphBubble(index, count, false, false)

    @Test fun upstreamKeyIsShiftedByTitleOffset() {
        // 上游 3 段气泡 → 应用第 4 段（应用第 0 段是标题行）
        val out = ParagraphBubbleIndex.toAppIndexes(
            listOf(b(0), b(3), b(7)),
            upstreamParagraphs = 8,
            appParagraphs = 9,
        )
        assertEquals(listOf(1, 4, 8), out.map { it.index })
    }

    @Test fun resultIsSortedByAppIndex() {
        val out = ParagraphBubbleIndex.toAppIndexes(
            listOf(b(5), b(1), b(3)),
            upstreamParagraphs = 8,
            appParagraphs = 9,
        )
        assertEquals(listOf(2, 4, 6), out.map { it.index })
    }

    @Test fun mismatchedParagraphCountGivesUpInsteadOfGuessing() {
        // 应用段落数与上游对不上：换算不出，宁可不显示气泡
        val out = ParagraphBubbleIndex.toAppIndexes(
            listOf(b(1), b(2)),
            upstreamParagraphs = 8,
            appParagraphs = 20,
        )
        assertTrue(out.isEmpty())
    }

    @Test fun emptyOrUnknownUpstreamCountIsSafe() {
        assertTrue(
            ParagraphBubbleIndex.toAppIndexes(emptyList(), upstreamParagraphs = 10, appParagraphs = 11)
                .isEmpty(),
        )
        // 上游没报段落数（0）：换算不出，不给气泡
        val out = ParagraphBubbleIndex.toAppIndexes(
            listOf(b(1), b(2)),
            upstreamParagraphs = 0,
            appParagraphs = 3,
        )
        assertTrue(out.isEmpty())
    }

    @Test fun outOfRangeKeysAreDropped() {
        // 上游有一个全书汇总桶 10000；越界的键一律丢掉，不能挂到某一段上
        val out = ParagraphBubbleIndex.toAppIndexes(
            listOf(b(0), b(10000)),
            upstreamParagraphs = 8,
            appParagraphs = 9,
        )
        assertEquals(listOf(1), out.map { it.index })
    }

    @Test fun countAndFlagsSurviveShift() {
        // 上游 8 段 → 键 7 是最后一段 → 应用第 8 段
        val out = ParagraphBubbleIndex.toAppIndexes(
            listOf(ParagraphBubble(7, 42, hot = true, authorCommented = true)),
            upstreamParagraphs = 8,
            appParagraphs = 9,
        )
        assertEquals(8, out[0].index)
        assertEquals(42, out[0].count)
        assertTrue(out[0].hot)
        assertTrue(out[0].authorCommented)
    }
}

/** 阅读进步下标换算：切章瞬间的旧下标不能越界写进新章。 */
class ReaderProgressTest {
    @Test fun titleItemIsExcluded() {
        assertEquals(0, com.alanmaster.xread.ui.ReaderProgress.paragraphOf(0, 10))
        assertEquals(0, com.alanmaster.xread.ui.ReaderProgress.paragraphOf(1, 10))
        assertEquals(1, com.alanmaster.xread.ui.ReaderProgress.paragraphOf(2, 10))
    }

    @Test fun staleIndexFromLongerChapterIsClamped() {
        // 上一章滚到第 50 段，切到只有 8 段的短章
        assertEquals(7, com.alanmaster.xread.ui.ReaderProgress.paragraphOf(51, 8))
    }

    @Test fun tailNavigationItemStaysInsideRange() {
        // 末项是「上一章/下一章」导航
        assertEquals(9, com.alanmaster.xread.ui.ReaderProgress.paragraphOf(11, 10))
    }

    @Test fun emptyContentIsSafe() {
        assertEquals(0, com.alanmaster.xread.ui.ReaderProgress.paragraphOf(0, 0))
        assertEquals(0, com.alanmaster.xread.ui.ReaderProgress.paragraphOf(5, 0))
    }
}
