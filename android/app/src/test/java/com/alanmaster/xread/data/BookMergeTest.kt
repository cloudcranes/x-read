package com.alanmaster.xread.data

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * [Book.mergeFrom] 的口径：上游没给的字段沿用旧值，给了的以新值为准。
 *
 * 起因是真机上同一本书两次打开元数据全变——`word_number` / `score` 第二次没回，
 * 详情页就从「320 万字 / 9.9 分」掉成「— / 空标签」。番茄的详情接口不是
 * 每次都返回全字段，这里把「这次没给」和「这次给了 0」统一按「没给」处理。
 */
class BookMergeTest {

    private fun book(
        bookId: String = "7111222333",
        title: String = "十日终焉",
        wordCount: Long = 3_200_000L,
        chapterCount: Int = 1496,
        score: String = "9.9",
        readCount: Long = 1_683_000L,
    ) = Book(
        bookId = bookId,
        title = title,
        author = "杀虫队队员",
        description = "悬疑脑洞",
        coverUrl = "https://img.example.com/c.jpg",
        category = "悬疑脑洞",
        score = score,
        wordCount = wordCount,
        chapterCount = chapterCount,
        creationStatus = "1",
        readCount = readCount,
        serialCount = chapterCount,
    )

    @Test
    fun keepsKnownValuesWhenFreshOmitsThem() {
        val known = book()
        // 第二次打开时上游只回了 id / 标题，其余字段全缺
        val sparse = Book(bookId = "7111222333", title = "十日终焉")

        val merged = known.mergeFrom(sparse)

        assertEquals("9.9", merged.score)
        assertEquals(3_200_000L, merged.wordCount)
        assertEquals(1496, merged.chapterCount)
        assertEquals(1_683_000L, merged.readCount)
        assertEquals("杀虫队队员", merged.author)
        assertEquals("https://img.example.com/c.jpg", merged.coverUrl)
        assertEquals("悬疑脑洞", merged.category)
        assertEquals("1", merged.creationStatus)
    }

    @Test
    fun takesNewValuesWhenUpstreamProvidesThem() {
        val known = book(chapterCount = 1496, readCount = 1_683_000L)
        // 章节数涨了、在读人数变了——这些是真实变化，不能被旧值钉死
        val fresh = book(chapterCount = 1497, readCount = 430_000L, score = "9.7")

        val merged = known.mergeFrom(fresh)

        assertEquals(1497, merged.chapterCount)
        assertEquals(430_000L, merged.readCount)
        assertEquals("9.7", merged.score)
        assertEquals(3_200_000L, merged.wordCount) // 这次没给，沿用
    }

    @Test
    fun doesNotLetZeroOverwriteARealCount() {
        val known = book(wordCount = 3_200_000L, readCount = 1_683_000L)
        val zeroed = Book(bookId = "7111222333", title = "十日终焉", wordCount = 0, readCount = 0)

        val merged = known.mergeFrom(zeroed)

        assertEquals(3_200_000L, merged.wordCount)
        assertEquals(1_683_000L, merged.readCount)
    }

    @Test
    fun keepsIdentityFieldsWhenFreshIsBlank() {
        val known = book()
        val blank = Book(bookId = "", title = "", author = "", coverUrl = "")

        val merged = known.mergeFrom(blank)

        assertEquals("7111222333", merged.bookId)
        assertEquals("十日终焉", merged.title)
        assertEquals("杀虫队队员", merged.author)
        assertEquals("https://img.example.com/c.jpg", merged.coverUrl)
    }

    @Test
    fun parsesThenMergesSparseDetailPayload() {
        // 走一遍真实链路上的形状：第一份详情带 word_number，第二份没有
        val first = ContentParser.book(
            JSONObject(
                """
                {"book_id":"7111222333","book_name":"十日终焉","author":"杀虫队队员",
                 "word_number":"3200000","score":"9.9","serial_count":"1496",
                 "read_count":"1683000","creation_status":"1"}
                """.trimIndent(),
            ),
        )
        val second = ContentParser.book(
            JSONObject(
                """
                {"book_id":"7111222333","book_name":"十日终焉","author":"杀虫队队员",
                 "serial_count":"1497","creation_status":"1"}
                """.trimIndent(),
            ),
        )

        val merged = first.mergeFrom(second)

        assertEquals(3_200_000L, merged.wordCount) // 第二次没给，沿用第一次
        assertEquals("9.9", merged.score)
        assertEquals(1497, merged.chapterCount) // 章节数增长是真变化
        assertEquals(1_683_000L, merged.readCount)
    }
}
