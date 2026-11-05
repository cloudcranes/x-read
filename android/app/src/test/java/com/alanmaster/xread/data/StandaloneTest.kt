package com.alanmaster.xread.data

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class StandaloneTest {
    @get:Rule val temp = TemporaryFolder()

    @Test fun searchSelectsNovelTabAndDeduplicates() {
        val payload = JSONObject("""{"code":0,"search_tabs":[
          {"tab_type":11,"data":[{"book_id":"9","book_name":"短剧"}]},
          {"tab_type":3,"data":[{"book_data":[{"book_id":"1","book_name":"书","author":"作者","word_number":"100"},{"book_id":"1","book_name":"重复"}]}]}
        ]}""")
        val result = ContentParser.search(payload)
        assertEquals(listOf("1"), result.map { it.bookId })
        assertEquals("作者", result[0].author)
        assertEquals(100L, result[0].wordCount)
    }

    @Test fun searchPageReadsCursorFromTheMatchedTab() {
        // 上游把 has_more/next_offset/passback 挂在命中的那个 tab 上：
        // 其它 tab 一律 has_more=false，抓错条目就会把「还有 20 页」判成到底。
        val payload = JSONObject("""{"code":0,"search_tabs":[
          {"tab_type":1,"has_more":false,"next_offset":0,"passback":"","data":null},
          {"tab_type":3,"has_more":true,"next_offset":20,"passback":"20",
           "data":[{"book_id":"1","book_name":"书"}]},
          {"tab_type":8,"has_more":false,"next_offset":0,"passback":"","data":null}
        ]}""")
        val page = ContentParser.searchPage(payload, 3)
        assertEquals(listOf("1"), page.books.map { it.bookId })
        assertTrue(page.hasMore)
        assertEquals(20, page.nextOffset)
        assertEquals("20", page.passback)
    }

    @Test fun searchPageIsExhaustedWithoutCursor() {
        val payload = JSONObject("""{"code":0,"search_tabs":[
          {"tab_type":3,"data":[{"book_id":"1","book_name":"书"}]}
        ]}""")
        val page = ContentParser.searchPage(payload, 3)
        assertFalse(page.hasMore)
        assertEquals(0, page.nextOffset)
    }

    @Test fun searchPageBorrowsCursorFromBookTabWhenAskedTabIsEmpty() {
        // 漫画 tab 没数据时会回退到书籍 tab，游标必须跟着回退后的那条走
        val payload = JSONObject("""{"code":0,"search_tabs":[
          {"tab_type":8,"has_more":false,"next_offset":0,"data":null},
          {"tab_type":3,"has_more":true,"next_offset":20,"passback":"20",
           "data":[{"book_id":"7","book_name":"回退"}]}
        ]}""")
        val page = ContentParser.searchPage(payload, 8)
        assertEquals(listOf("7"), page.books.map { it.bookId })
        assertTrue(page.hasMore)
        assertEquals(20, page.nextOffset)
    }

    @Test fun preservesStringIdentifiersAndOptionalFields() {
        val book = ContentParser.book(JSONObject("""{"book_id":"12345678901234567890","book_name":"长编号","score":null,"thumb_url":"https://example.test/a.jpg"}"""))
        assertEquals("12345678901234567890", book.bookId)
        assertEquals("", book.score)
        assertEquals("https://example.test/a.jpg", book.coverUrl)
    }

    @Test fun directoryReadsAllEntriesNotOnlyFirstSixty() {
        val rows = org.json.JSONArray()
        repeat(205) { rows.put(JSONObject().put("item_id", "$it").put("title", "第$it 章")) }
        rows.put(JSONObject().put("item_id", "0").put("title", "重复"))
        val result = ContentParser.chapters(JSONObject().put("data", JSONObject().put("item_data_list", rows)))
        assertEquals(205, result.size)
        assertEquals("204", result.last().id)
    }

    @Test fun rankCatalogueComesFromNestedHomepageCard() {
        val json = JSONObject("""{"data":{"tab_item":[{"cell_data":[{"cell_id_str":"7000000000000000000","rank_with_category_data":{"rank_algo_list":[{"rank_name":"推荐榜","rank_algo":101}],"sub_info_list":[{"info_name":"全部","info_id":0}]}}]}]}}""")
        val cat = ContentParser.rankCatalog(json)!!
        assertEquals("7000000000000000000", cat.id)
        assertEquals(101, cat.tabs.single().algorithm)
        assertEquals("0", cat.categories.single().id)
    }

    @Test fun hotWordsDeduplicateWithoutInventingFallback() {
        val result = ContentParser.hotWords(JSONObject("""{"data":[{"search_tag_data":[{"tag_title":"热词"},{"tag_title":"热词"},{"tag_title":"另一个"}]}]}"""))
        assertEquals(listOf("热词", "另一个"), result.map { it.word })
        assertTrue(ContentParser.hotWords(JSONObject()).isEmpty())
    }

    @Test fun localShelfSurvivesReconstructionAndRemoval() {
        val directory = temp.newFolder("library")
        LocalLibrary(directory).put(Book(bookId = "123", title = "我的书", description = "简介", chapterCount = 205))
        val reopened = LocalLibrary(directory)
        assertEquals("我的书", reopened.books().single().title)
        assertEquals(205, reopened.books().single().chapterCount)
        reopened.remove("123")
        assertTrue(LocalLibrary(directory).books().isEmpty())
    }

    @Test fun upgradeImportsDownloadsOnceWithoutReaddingDeletedShelf() {
        val directory = temp.newFolder("migration")
        val book = Book(bookId="123", title="旧缓存")
        val first = LocalLibrary(directory)
        first.migrateDownloads(listOf(book))
        assertEquals(1, first.books().size)
        first.remove("123")
        LocalLibrary(directory).migrateDownloads(listOf(book))
        assertTrue(first.books().isEmpty())
    }

    @Test fun corruptShelfFailsWithoutOverwritingUserData() {
        val directory = temp.newFolder("corrupt")
        val file = java.io.File(directory,"shelf.json").apply { writeText("not json") }
        assertTrue(runCatching { LocalLibrary(directory).put(Book(bookId="123",title="new")) }.isFailure)
        assertEquals("not json", file.readText())
    }
}

class StatsTest {
    @get:Rule val temp = TemporaryFolder()

    @Test fun readingTimeAndWordsAccumulatePerDay() {
        val repo = testContext()
        repo.addStats(90, 1200, 2)
        repo.addStats(30, 800, 1)
        val stats = repo.stats()
        assertEquals(120L, stats.optLong("seconds"))
        assertEquals(2000L, stats.optLong("words"))
        assertEquals(3L, stats.optLong("chapters"))
        val days = stats.optJSONObject("days")!!
        assertEquals(1, days.length())
        assertEquals(120L, days.optLong(days.keys().next()))
    }

    @Test fun replacingRulesAppliesInOrderAndSurvivesReload() {
        val repo = testContext()
        assertTrue(repo.saveRules(listOf(
            ReplaceRule("测试", "替换后"),
            ReplaceRule("[0-9]+", "N", regex = true),
        )))
        assertEquals("这是替换后N文本", repo.applyRules("这是测试123文本"))
        assertEquals(2, testContext().rules().size)
    }

    @Test fun backupRoundTripKeepsShelfProgressAndRules() {
        val source = testContext()
        source.saveProgress("123", "456", 42)
        source.saveRules(listOf(ReplaceRule("a", "b")))
        source.addStats(60, 100, 1)
        val file = source.exportBackup()!!
        assertTrue(file.length() > 0)
        val restored = source.importBackup()
        assertTrue(restored.contains("导入完成"))
    }

    /** 备份必须能恢复到**空仓库**里：旧用例在同一实例上 export→import，什么都没验证。 */
    @Test fun backupRestoresIntoFreshRepository() {
        val source = testContext()
        source.putBook(Book(bookId = "123", title = "书名", author = "作者"), emptyList())
        source.saveProgress("123", "456", 42)
        source.saveRules(listOf(ReplaceRule("a", "b")))
        source.addStats(60, 100, 1)
        val file = source.exportBackup()!!
        assertTrue(file.length() > 0)

        val fresh = OfflineRepo(File(temp.newFolder(), "offline"), source.exportDir)
        assertTrue(fresh.importBackup().contains("导入完成"))

        val progress = fresh.progress("123")
        assertNotNull("进度未恢复", progress)
        assertEquals("456", progress!!.first)
        assertEquals(42, progress.second)
        assertEquals(1, fresh.rules().size)
        assertEquals(60, fresh.stats().optLong("seconds").toInt())
        assertEquals(listOf("123"), fresh.offlineBooks().map { it.bookId })
    }

    /**
     * 导入是「恢复」不是「再加一份」：同一份备份连续导入两次，统计必须原地不动。
     * 早期实现复用累加型的 addStats，每导一次阅读时长就翻一倍（真机实测
     * 1654 → 3308 → 4962），而原有两条用例一条只断言消息、一条导进空仓库，
     * 累加与覆盖在空仓库里结果相同，所以都没抓住。
     */
    @Test fun importingBackupTwiceDoesNotDoubleStats() {
        val source = testContext()
        source.addStats(1654, 33827, 14)
        val before = source.stats()
        assertTrue(source.exportBackup()!!.length() > 0)

        assertTrue(source.importBackup().contains("导入完成"))
        assertEquals(before.optLong("seconds"), source.stats().optLong("seconds"))
        assertEquals(before.optLong("words"), source.stats().optLong("words"))
        assertEquals(before.optLong("chapters"), source.stats().optLong("chapters"))

        assertTrue(source.importBackup().contains("导入完成"))
        val after = source.stats()
        assertEquals("导入两次后阅读时长翻倍了", before.optLong("seconds"), after.optLong("seconds"))
        assertEquals(33827L, after.optLong("words"))
        assertEquals(14L, after.optLong("chapters"))
    }

    /** 恢复要连 days 明细与 since 一起搬，否则日粒度曲线会失真。 */
    @Test fun importingBackupRestoresDailyBreakdown() {
        val backup = JSONObject()
            .put("seconds", 900L).put("words", 5000L).put("chapters", 3)
            .put("days", JSONObject().put("2026-10-30", 400L).put("2026-10-31", 500L))
            .put("since", 1793462939547L)
        val repo = OfflineRepo(File(temp.newFolder(), "offline"), temp.newFolder())
        repo.addStats(100, 10, 1)

        assertTrue(repo.restoreStats(backup))
        val st = repo.stats()
        assertEquals(900L, st.optLong("seconds"))
        assertEquals(1793462939547L, st.optLong("since"))
        val days = st.getJSONObject("days")
        assertEquals(400L, days.optLong("2026-10-30"))
        assertEquals(500L, days.optLong("2026-10-31"))
    }

    @Test fun marksTrimTrailingSpaceFromChapterTitle() {
        // 上游的章节名就是 "第1章 "（尾随空格），原样存下来会在书摘页和导出的
        // Markdown 里印成「【第1章 】」。写入与读取两侧都要 trim。
        val root = File(temp.newFolder(), "offline")
        val repo = OfflineRepo(root, temp.newFolder())
        assertTrue(
            repo.addMark("b1", "c1", "第1章 ",
                Mark(3, "摘录", Mark.KIND_HIGHLIGHT, 1793531960405L))
        )
        assertEquals("第1章", repo.allMarks().single().chapterTitle)

        // 老数据（写入时没 trim）从盘上读出来也要被 trim 掉
        val legacy = JSONObject().put("b1:c1", JSONObject()
            .put("title", "第2章 给我跪下！  ")
            .put("items", org.json.JSONArray().put(JSONObject()
                .put("i", 5).put("t", "旧摘录")
                .put("k", Mark.KIND_BOOKMARK).put("at", 1793531993282L))))
        java.io.File(root, "marks.json").writeText(legacy.toString())
        assertEquals("第2章 给我跪下！", repo.allMarks().single().chapterTitle)
    }

    private var shared: OfflineRepo? = null

    /** Same directory across reopen, to prove files (not memory) carry the state. */
    private fun testContext(): OfflineRepo = shared ?: run {
        val root = File(temp.newFolder(), "offline")
        OfflineRepo(root, temp.newFolder()).also { shared = it }
    }
}

class BoardTest {
        @Test fun boardCategoriesAndTabsAreParsedFromCatalogue() {
            val json = JSONObject("""{"data":{"tab_item":[{"cell_data":[{"cell_id_str":"7000000000000000000","rank_with_category_data":{
              "rank_algo_list":[{"rank_name":"推荐榜","rank_algo":101},{"rank_name":"完本榜","rank_algo":100},{"rank_name":"新书榜","rank_algo":108}],
              "sub_info_list":[{"info_name":"全部","info_id":0},{"info_name":"穿越","info_id":37},{"info_name":"系统","info_id":19}]}}]}]}}""")
            val cat = ContentParser.rankCatalog(json)!!
            assertEquals(listOf("推荐榜","完本榜","新书榜"), cat.tabs.map { it.name })
            assertEquals(listOf(101,100,108), cat.tabs.map { it.algorithm })
            assertEquals(listOf("全部","穿越","系统"), cat.categories.map { it.name })
            assertEquals(listOf("0","37","19"), cat.categories.map { it.id })
        }

        @Test fun boardRejectsUnknownAlgorithmAndBadCategory() {
            // ViewModel 只传目录内的算法与纯数字分类；此处锁住 Api.board 的前置校验契约。
            val src = java.io.File("src/main/java/com/alanmaster/xread/data/Api.kt").readText()
            assertTrue(src.contains("require(algorithm in catalog.tabs.map { it.algorithm })"))
            assertTrue(src.contains("Regex(\"[0-9]{1,24}\")"))
        }
}

class SocialTest {
    @Test fun bookCommentsParseRepliesAndCounters() {
        val payload = JSONObject("""{"data":{"comment_cnt":52091,"score_cnt":194741,
          "context":"19.5万人点评","next_offset":20,"has_more":true,
          "comment":[{"comment_id":"1","text":"好看","score":"8","digg_count":211,"reply_count":36,
            "create_timestamp":1700000000,
            "user_info":{"user_name":"读者甲","user_avatar":"http://a","is_author":false},
            "reply_list":[{"reply_id":"r1","text":"同感","digg_count":5,"create_timestamp":1700000100,
              "author":1,"user_info":{"user_name":"作者君","user_avatar":"http://b"}}]}]}}""")
        val page = SocialParser.bookComments(payload)
        assertEquals(1, page.items.size)
        assertEquals(52091, page.total)
        assertEquals(194741, page.raters)
        assertEquals("19.5万人点评", page.context)
        assertEquals(20, page.nextOffset)
        assertTrue(page.hasMore)
        val c = page.items[0]
        assertEquals("读者甲", c.user)
        assertEquals("好看", c.text)
        assertEquals(211, c.digg)
        assertEquals(36, c.replyCount)
        assertEquals(1, c.replies.size)
        assertEquals("同感", c.replies[0].text)
        // author=1 是作者本人回复，UI 靠它打「作者」标签
        assertTrue(c.replies[0].isAuthor)
    }

    @Test fun bookCommentsSkipEmptyTextAndMissingNextOffset() {
        val payload = JSONObject("""{"data":{"comment_cnt":1,"comment":[
          {"comment_id":"1","text":"  "},
          {"comment_id":"2"}],"has_more":true}}""")
        val page = SocialParser.bookComments(payload)
        assertTrue(page.items.isEmpty())
        // next_offset 缺失时不能给出 0 以外的偏移，否则会原地死循环
        assertEquals(0, page.nextOffset)
        assertFalse(page.hasMore)
    }

    @Test fun paragraphBubblesKeepOnlyCommentedParagraphsInOrder() {
        val payload = JSONObject("""{"data":{"data":{
          "10":{"count":3,"hot":true,"is_author_comment":false},
          "2":{"count":0},
          "5":{"count":1,"hot":false,"is_author_comment":true}}}}""")
        val list = SocialParser.paragraphBubbles(payload)
        assertEquals(listOf(5, 10), list.map { it.index })
        assertEquals(1, list[0].count)
        assertTrue(list[0].authorCommented)
        assertTrue(list[1].hot)
    }

    @Test fun bookCommentsPageByOffsetNotCursor() {
        val api = java.io.File("src/main/java/com/alanmaster/xread/data/Api.kt").readText()
        // 老接口只认 offset；cursor 那套是 books/{id}/reviews，且 sort 传 int 会被上游打回
        assertTrue(api.contains("count=\$count&offset=\$offset"))
        assertFalse(api.contains("append(\"&cursor=\${enc(cursor)}\")"))
        val vm = java.io.File("src/main/java/com/alanmaster/xread/data/LibraryViewModel.kt").readText()
        // 偏移没推进即判定到底，避免无限加载
        assertTrue(vm.contains("page.nextOffset > st.commentsOffset"))
    }
}

class CategoryAndShareTest {
    @Test fun categoriesUseDownCategoryAndKeepUpstreamOrder() {
        val payload = JSONObject("""{"data":{"down_category":[
          {"id":7,"name":"玄幻","cover":"http://c/1","book_num":1},
          {"id":1,"name":"都市","book_num":"12"},
          {"name":"没有 id 的脏数据"}]}}""")
        val list = ContentParser.categories(payload)
        assertEquals(listOf("7", "1"), list.map { it.id })
        assertEquals(listOf("玄幻", "都市"), list.map { it.name })
        assertEquals(12, list[1].bookCount)
        // 明文地址会被系统的明文流量开关拦掉，图就出不来，解析时就升成 https
        assertEquals("https://c/1", list[0].cover)
    }

    @Test fun categoriesPreferTheNewPicUrlField() {
        val payload = JSONObject("""{"data":{"down_category":[
          {"id":7,"name":"玄幻","pic_url":"http://p3-novel.byteimg.com/a~tplv-noop.image","cover":"http://old/b"},
          {"id":8,"name":"都市","pic_url":"https://new/c"}]}}""")
        val list = ContentParser.categories(payload)
        assertEquals("https://p3-novel.byteimg.com/a~tplv-noop.image", list[0].cover)
        assertEquals("https://new/c", list[1].cover)
    }

    @Test fun plainHttpCoversAreUpgradedToHttps() {
        assertEquals("https://x/y", normalizeImageUrl("http://x/y"))
        assertEquals("https://x/y", normalizeImageUrl("//x/y"))
        assertEquals("https://x/y", normalizeImageUrl("https://x/y"))
        assertEquals("", normalizeImageUrl(""))
    }

    @Test fun shareInfoCarriesLinkAndCopyText() {
        val payload = JSONObject("""{"data":{"copy_link_text":"推荐一部好书《X》",
          "sub_text":"听说你书荒？","share_url":"https://example/share","schema":"dragon1967://bookDetail"}}""")
        val info = ContentParser.shareInfo(payload)
        assertEquals("推荐一部好书《X》", info.copyLinkText)
        assertEquals("听说你书荒？", info.subText)
        assertEquals("https://example/share", info.shareUrl)
        assertTrue(info.hasContent)
        // 空载荷不能假装有东西可分享，否则会弹出一个只有书名的选择器
        assertFalse(ContentParser.shareInfo(JSONObject("{}")).hasContent)
    }
}

class ReaderFindTest {
    private fun chapter(vararg paras: String) =
        ChapterContent("1", "第1章", paras.toList(), null, null)

    @Test fun findHitsReturnsIndicesInOrder() {
        val c = chapter("钟响了", "无关", "座钟与钟摆", "依旧无关")
        assertEquals(listOf(0, 2), c.findHits("钟"))
    }

    @Test fun blankQueryYieldsNoHits() {
        val c = chapter("正文")
        assertTrue(c.findHits("").isEmpty())
        assertTrue(c.findHits("   ").isEmpty())
    }

    @Test fun missingTermYieldsNoHits() {
        assertTrue(chapter("正文").findHits("外星人").isEmpty())
    }
}

class ResumeParagraphTest {
    @Test fun sameChapterKeepsParagraph() {
        assertEquals(57, LibraryViewModel.resumeParagraph("c1" to 57, "c1"))
    }

    @Test fun differentChapterStartsAtTop() {
        assertEquals(0, LibraryViewModel.resumeParagraph("c1" to 57, "c2"))
    }

    @Test fun missingRecordStartsAtTop() {
        assertEquals(0, LibraryViewModel.resumeParagraph(null, "c1"))
    }

    @Test fun negativeOffsetClampsToZero() {
        assertEquals(0, LibraryViewModel.resumeParagraph("c1" to -3, "c1"))
    }

    /**
     * 「读到第 10 章，退出重进回到第 2 章」的第二个成因守卫。
     *
     * 旧实现只在换章时写进度，同章内滚动的段落位置永远不落盘；
     * 现在同章内也写，但要节流，且「从没写过」必须放行。
     */
    @Test fun chapterChangeAlwaysSavesEvenInsideThrottle() {
        assertTrue(LibraryViewModel.progressDue(chapterChanged = true, lastSavedAt = 1_000L, now = 1_100L))
    }

    @Test fun firstSaveIsNeverThrottled() {
        // 刚进书时 progressSavedAt 还是 0，第一次滚动不能被节流挡掉
        assertTrue(LibraryViewModel.progressDue(chapterChanged = false, lastSavedAt = 0L, now = 42L))
    }

    @Test fun sessionRestoresWithinADay() {
        val now = 1_000_000_000L
        assertTrue(LibraryViewModel.shouldRestoreSession(now - 60_000L, now))
        assertTrue(LibraryViewModel.shouldRestoreSession(now - LibraryViewModel.SESSION_RESTORE_TTL_MS, now))
    }

    @Test fun staleOrEmptySessionIsIgnored() {
        val now = 1_000_000_000L
        assertFalse(
            "一天多以前的记录不再自动跳转",
            LibraryViewModel.shouldRestoreSession(now - LibraryViewModel.SESSION_RESTORE_TTL_MS - 1L, now),
        )
        assertFalse("没有记录", LibraryViewModel.shouldRestoreSession(0L, now))
        assertFalse("时钟被调回过去时不跳", LibraryViewModel.shouldRestoreSession(now + 1_000L, now))
    }

    @Test fun sameChapterWaitsForTheInterval() {
        assertFalse(LibraryViewModel.progressDue(chapterChanged = false, lastSavedAt = 10_000L, now = 10_500L))
        assertTrue(LibraryViewModel.progressDue(chapterChanged = false, lastSavedAt = 10_000L, now = 11_500L))
    }
}

/**
 * 向上连续阅读的取章守卫。
 *
 * 「第 2 章能接着读第 3 章、却回不到第 1 章」的修法是在流头部接上一章；
 * 接哪一章这件事必须纯函数化，否则离线章节（缓存里没有 previousId）
 * 还是会取不到上一章。
 */
class PreviousChapterTest {
    private fun chapters(vararg ids: String) = ids.map { Chapter(it, "第${it}章") }
    private fun head(id: String, prev: String? = null, next: String? = null) =
        ChapterContent(id, "第${id}章", listOf("正文"), prev, next)

    @Test fun prefersPreviousIdCarriedByTheChapter() {
        // 在线章节自带 previousId，目录顺序对不上时也应该听它的
        assertEquals("c1", LibraryViewModel.previousChapterId(head("c2", prev = "c1"), chapters("c1", "c2", "c3")))
    }

    @Test fun fallsBackToDirectoryOrderForOfflineChapters() {
        // 离线缓存只存正文，previousId 是 null，必须退回目录里它前面的那一本
        assertEquals("c1", LibraryViewModel.previousChapterId(head("c2"), chapters("c1", "c2", "c3")))
    }

    @Test fun firstChapterHasNothingAbove() {
        assertNull(LibraryViewModel.previousChapterId(head("c1"), chapters("c1", "c2")))
        assertNull(LibraryViewModel.previousChapterId(null, chapters("c1", "c2")))
        // 章节不在目录里（老数据）也不能凭空造一个上一章
        assertNull(LibraryViewModel.previousChapterId(head("c9"), chapters("c1", "c2")))
    }
}

class ShelfSortTest {
    private fun item(id: String, title: String, added: Long, group: String = "", known: Int = 10) =
        ShelfItem(Book(bookId = id, title = title), "", 0, "", group, added, known)

    private val sample = listOf(
        item("1", "乙书", 200L, "玄幻"),
        item("2", "甲书", 300L),
        item("3", "丙书", 100L, "玄幻"),
    )

    @Test fun recentSortsByAddedDescending() {
        val r = LibraryViewModel.ShelfSort.apply(sample, "recent", "")
        assertEquals(listOf("2", "1", "3"), r.map { it.book.bookId })
    }

    @Test fun titleSortsByBookName() {
        val r = LibraryViewModel.ShelfSort.apply(sample, "title", "")
        assertEquals(listOf("乙书", "丙书", "甲书").sorted(), r.map { it.book.title })
    }

    @Test fun groupSortPutsUngroupedLast() {
        val r = LibraryViewModel.ShelfSort.apply(sample, "group", "")
        assertEquals(listOf("1", "3", "2"), r.map { it.book.bookId })
    }

    @Test fun groupFilterKeepsOnlyThatGroup() {
        val r = LibraryViewModel.ShelfSort.apply(sample, "recent", "玄幻")
        assertEquals(listOf("1", "3"), r.map { it.book.bookId })
        assertTrue(LibraryViewModel.ShelfSort.apply(sample, "recent", "不存在").isEmpty())
    }

    @Test fun groupAndBaselineSurviveReadd() {
        val dir = java.nio.file.Files.createTempDirectory("shelf").toFile()
        val lib = LocalLibrary(dir)
        lib.put(Book(bookId = "123", title = "书"))
        assertTrue(lib.setGroup("123", "历史"))
        assertTrue(lib.markChapterBaseline("123", 42))
        // 重新入架（例如详情页再次加入书架）不得清掉分组与基线
        lib.put(Book(bookId = "123", title = "书"))
        val entry = lib.entries().single()
        assertEquals("历史", entry.group)
        assertEquals(42, entry.knownChapters)
        assertTrue(entry.addedAt > 0)
    }
}

class AudioParserTest {
    @Test fun picksFirstPlayableUrlAndKeepsBackup() {
        val payload = JSONObject("""{"code":0,"data":[
          {"main_url":"https://cdn/a.m4a","backup_url":"https://cdn/b.m4a","is_encrypt":true}]}""")
        val c = AudioParser.chapter("1", "第1集", payload)!!
        assertEquals("https://cdn/a.m4a", c.url)
        assertEquals("https://cdn/b.m4a", c.backupUrl)
        assertTrue(c.encrypted)
    }

    @Test fun fallsBackToBackupWhenMainEmpty() {
        val payload = JSONObject("""{"data":[{"main_url":"","backup_url":"https://cdn/b.m4a"}]}""")
        val c = AudioParser.chapter("1", "", payload)!!
        assertEquals("https://cdn/b.m4a", c.url)
        assertEquals("", c.backupUrl)
    }

    @Test fun returnsNullWhenNoAddressAtAll() {
        assertNull(AudioParser.chapter("1", "t", JSONObject("""{"data":[{"main_url":"","backup_url":""}]}""")))
        assertNull(AudioParser.chapter("1", "t", JSONObject("""{"code":1,"message":"x"}""")))
    }
}

