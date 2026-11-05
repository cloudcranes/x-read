package com.alanmaster.xread.data

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 榜单字段解析与连载状态口径。
 *
 * 所有断言都对着**真机抓到的上游载荷结构**写，不是对着想象的 API：
 * 原生核心 `rank_data` 返回 `data.cell_view.cell_data[].book_data[0]`，
 * 其中没有 `currentPos` / `rankPosDiff`（那是 Python 后端走官网接口才有的），
 * 但有 `read_count` / `creation_status` / `serial_count` / `rank_score`。
 */
class BookStatusTest {

    @Test fun zeroMeansFinished() {
        assertEquals(BookStatus.Finished, BookStatus.of("0"))
    }

    @Test fun oneMeansSerial() {
        assertEquals(BookStatus.Serial, BookStatus.of("1"))
    }

    @Test fun missingOrUnknownIsUnknown() {
        assertEquals(BookStatus.Unknown, BookStatus.of(""))
        assertEquals(BookStatus.Unknown, BookStatus.of("2"))
        assertEquals(BookStatus.Unknown, BookStatus.of("abc"))
    }

    @Test fun bookExposesStatusFromCreationStatus() {
        assertEquals(BookStatus.Serial, Book(bookId = "1", title = "连载中", creationStatus = "1").status)
        assertEquals(BookStatus.Finished, Book(bookId = "1", title = "已完结", creationStatus = "0").status)
    }

    /** 榜单载荷：`read_count` 是字符串，`serial_count` 是字符串，都要能读成数字。 */
    @Test fun parsesRankPayloadFields() {
        val node = JSONObject(
            """
            {
              "book_id": "7617740782291651646",
              "book_name": "请活到天明时分",
              "author": "阿一不是阿二",
              "score": "9.5",
              "read_count": "2413916",
              "serial_count": "412",
              "creation_status": "1",
              "last_chapter_title": "第412章 归途"
            }
            """.trimIndent(),
        )
        val book = ContentParser.book(node)
        assertEquals("7617740782291651646", book.bookId)
        assertEquals("请活到天明时分", book.title)
        assertEquals(2_413_916L, book.readCount)
        assertEquals(412, book.chapterCount)
        assertEquals(412, book.serialCount)
        assertEquals(BookStatus.Serial, book.status)
        assertEquals("第412章 归途", book.latestChapterTitle)
    }

    /** 缺字段时必须是 0 / Unknown，不能把 0 当成「有 0 人在读」显示出去。 */
    @Test fun absentFieldsAreZeroNotGuessed() {
        val book = ContentParser.book(JSONObject("""{"book_id":"1","book_name":"x"}"""))
        assertEquals(0L, book.readCount)
        assertEquals(BookStatus.Unknown, book.status)
        assertEquals(0, book.serialCount)
    }

    /** 详情接口用的是 snake_case（word_number），榜单用的是 serial_count，两条路都要通。 */
    @Test fun detailPayloadStillWorks() {
        val book = ContentParser.book(
            JSONObject("""{"book_id":"1","book_name":"x","word_number":"4230000","chapter_number":"1496"}"""),
        )
        assertEquals(4_230_000L, book.wordCount)
        assertEquals(1496, book.chapterCount)
    }

    @Test fun shelfJsonRoundTripKeepsNewFields() {
        val original = Book(
            bookId = "7143038691944959011",
            title = "十日终焉",
            creationStatus = "0",
            readCount = 597_999L,
            serialCount = 0,
        )
        // shelf.json 走的是 JSONObject.book() 这条读回路径
        val roundTripped = JSONObject()
            .put("book_id", original.bookId)
            .put("title", original.title)
            .put("creation_status", original.creationStatus)
            .put("read_count", original.readCount)
            .put("serial_count", original.serialCount)
            .book()
        assertEquals(original.creationStatus, roundTripped.creationStatus)
        assertEquals(original.readCount, roundTripped.readCount)
        assertEquals(BookStatus.Finished, roundTripped.status)
    }

    @Test fun longHelperRespectsFallback() {
        val o = JSONObject("""{"absent_str":"abc"}""")
        assertEquals(0L, o.long("missing"))
        assertEquals(-1L, o.long("missing", -1L))
        // 非数字字符串不能被当成 0 悄悄吞掉语义，但也不能抛
        assertEquals(0L, o.long("absent_str"))
        assertEquals(7L, o.long("absent_str", 7L))
    }

    /**
     * 在读人数格式化：万位一档，保留一位小数。
     *
     * 界面上它要和排名数字右对齐成列，所以格式必须稳定。
     */
    @Test fun readCountFormatting() {
        assertEquals("0", ReadCountFormat.short(0))
        assertEquals("999", ReadCountFormat.short(999))
        // 1 千还没到「万」，原样输出——阈值是 10_000，不是 1_000
        assertEquals("1000", ReadCountFormat.short(1_000))
        assertEquals("9999", ReadCountFormat.short(9_999))
        assertEquals("1.0 万", ReadCountFormat.short(10_000))
        // 截断而非四舍五入：597999 真值 59.7999 万，取一位是 59.7。
        // 宁可比真实值小一点，也不把 59.0 万的书显示成 59.1 万。
        assertEquals("59.7 万", ReadCountFormat.short(597_999))
        assertEquals("241.3 万", ReadCountFormat.short(2_413_916))
        assertEquals("1.3 亿", ReadCountFormat.short(134_298_450))
    }

    @Test fun readCountFormattingIsMonotonic() {
        val samples = listOf(0L, 1L, 999L, 1_000L, 9_999L, 10_000L, 597_999L, 2_413_916L, 134_298_450L)
        val formatted = samples.map { ReadCountFormat.short(it) }
        assertTrue("格式必须随数量增长而不倒退：$formatted", formatted.distinct().size == samples.size)
    }
}

/**
 * 分类字段的清洗。
 *
 * 真机上抓到过 `category = "116个老书虫在看"`——上游把这个字段当社交证明位用了。
 * 这种值一旦印到榜单行上，读者会以为书的分类叫「116个老书虫在看」。
 */
class CategoryParseTest {
    @Test fun prefersPureCategoryTags() {
        val n = JSONObject(
            """{"book_name":"x","category":"116个老书虫在看","pure_category_tags":"都市日常,仕途"}""",
        )
        assertEquals("都市日常", ContentParser.parseCategory(n))
    }

    @Test fun rejectsCoercionTextInCategory() {
        listOf(
            "116个老书虫在看",
            "69.7万人在读",
            "2.1万人读过",
            "书友力荐",
        ).forEach { value ->
            val n = JSONObject().put("category", value)
            assertEquals("分类 $value 应被判为非分类", "", ContentParser.parseCategory(n))
        }
    }

    @Test fun keepsRealCategory() {
        val n = JSONObject("""{"category":"悬疑脑洞"}""")
        assertEquals("悬疑脑洞", ContentParser.parseCategory(n))
    }

    @Test fun fallsBackToTagsWhenCategoryIsCoercion() {
        val n = JSONObject("""{"category":"116个老书虫在看","tags":"都市日常,都市"}""")
        assertEquals("都市日常", ContentParser.parseCategory(n))
    }

    @Test fun emptyWhenNothingUsable() {
        assertEquals("", ContentParser.parseCategory(JSONObject("{}")))
    }
}
