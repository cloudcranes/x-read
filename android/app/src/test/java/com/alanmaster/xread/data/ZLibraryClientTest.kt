package com.alanmaster.xread.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * z-library EAPI 解析层的单测。
 *
 * 只测纯函数（JSON 文本 → 模型），不碰网络。
 */
class ZLibraryClientTest {

    // ---------------- 搜索 ----------------

    @Test
    fun parseSearchMapsBookFields() {
        val page = ZLibraryJson.parseSearch(searchPayload)

        assertEquals(2, page.books.size)
        val book = page.books[0]
        assertEquals("123456", book.id)
        assertEquals("c79ab3", book.hash)
        assertEquals("Atomic Habits", book.title)
        assertEquals("James Clear", book.author)
        assertEquals(2018, book.year)
        assertEquals("English", book.language)
        assertEquals("epub", book.extension)
        assertEquals("26.72 MB", book.fileSizeText)
        assertEquals(27950000L, book.fileSizeBytes)
        assertEquals("Avery", book.publisher)
        assertEquals(320, book.pages)
        assertEquals("https://covers.zlib.test/123456.jpg", book.coverUrl)
        assertEquals("Self-Help, Business", book.categories)
        assertEquals("80", book.qualityScore)
        assertTrue(book.readOnlineAvailable)
    }

    @Test
    fun parseSearchKeepsHtmlDescription() {
        val page = ZLibraryJson.parseSearch(searchPayload)

        assertEquals("<p>Every <b>habit</b> is a vote.</p>", page.books[0].description)
    }

    @Test
    fun parseSearchMapsPagination() {
        val page = ZLibraryJson.parseSearch(searchPayload)

        assertEquals(1, page.page)
        assertEquals(167, page.totalPages)
        assertEquals(500, page.totalItems)
        assertTrue(page.hasNext)
    }

    @Test
    fun parseSearchHasNextWhenNextIsPageNumber() {
        val page = ZLibraryJson.parseSearch(searchPayloadWithNext("2"))

        assertTrue(page.hasNext)
    }

    @Test
    fun parseSearchHasNoNextWhenNextIsNull() {
        val page = ZLibraryJson.parseSearch(searchPayloadWithNext("null"))

        assertFalse(page.hasNext)
    }

    @Test
    fun parseSearchHasNoNextWhenNextIsFalse() {
        val page = ZLibraryJson.parseSearch(searchPayloadWithNext("false"))

        assertFalse(page.hasNext)
    }

    @Test
    fun parseSearchAcceptsEmptyBookList() {
        val page = ZLibraryJson.parseSearch(
            """
            {"success":1,"books":[],"pagination":{"current":2,"next":null,"total_items":0,"total_pages":0}}
            """.trimIndent()
        )

        assertTrue(page.books.isEmpty())
        assertEquals(2, page.page)
        assertEquals(0, page.totalPages)
        assertEquals(0, page.totalItems)
        assertFalse(page.hasNext)
    }

    @Test
    fun parseSearchFillsDefaultsForMissingFields() {
        val page = ZLibraryJson.parseSearch(
            """
            {"success":1,"books":[{"id":"7","title":"Minimal"}]}
            """.trimIndent()
        )

        val book = page.books[0]
        assertEquals("7", book.id)
        assertEquals("Minimal", book.title)
        assertEquals("", book.hash)
        assertEquals("", book.author)
        assertEquals(0, book.year)
        assertEquals("", book.language)
        assertEquals("", book.extension)
        assertEquals("", book.fileSizeText)
        assertEquals(0L, book.fileSizeBytes)
        assertEquals("", book.publisher)
        assertEquals(0, book.pages)
        assertEquals("", book.coverUrl)
        assertEquals("", book.description)
        assertEquals("", book.categories)
        assertEquals("", book.qualityScore)
        assertFalse(book.readOnlineAvailable)
    }

    @Test
    fun parseSearchJoinsCategoriesInKeyOrder() {
        val page = ZLibraryJson.parseSearch(
            """
            {"success":1,"books":[{"id":"1","categories":{"600":"Business","599":"Self-Help"}}]}
            """.trimIndent()
        )

        assertEquals("Self-Help, Business", page.books[0].categories)
    }

    @Test
    fun parseSearchFailsWhenUpstreamReportsError() {
        try {
            ZLibraryJson.parseSearch("""{"success":0,"error":"Please login"}""")
            fail("上游报失败时应当抛出 ZLibraryException")
        } catch (e: ZLibraryException) {
            assertEquals("Please login", e.message)
        }
    }

    // ---------------- 错误文案 ----------------

    @Test
    fun errorMessageReturnsUpstreamError() {
        assertEquals("Please login", ZLibraryJson.errorMessage("""{"success":0,"error":"Please login"}"""))
    }

    @Test
    fun errorMessageReturnsNullOnSuccess() {
        assertNull(ZLibraryJson.errorMessage("""{"success":1,"books":[]}"""))
    }

    @Test
    fun errorMessageReturnsNullOnBrokenJson() {
        assertNull(ZLibraryJson.errorMessage("not json at all"))
    }

    // ---------------- 详情 ----------------

    @Test
    fun parseBookMapsFields() {
        val book = ZLibraryJson.parseBook(
            """
            {"success":1,"book":{
              "id":"991","hash":"ab12cd","title":"Deep Work","author":"Cal Newport","year":2016,
              "language":"English","extension":"pdf","filesize":10485760,"filesizeString":"10.00 MB",
              "publisher":"Grand Central","pages":304,"cover":"https://covers.zlib.test/991.jpg",
              "description":"<p>Focus.</p>","categories":{"1":"Business"},"qualityScore":90,
              "readOnlineAvailable":false
            }}
            """.trimIndent()
        )

        assertEquals("991", book.id)
        assertEquals("ab12cd", book.hash)
        assertEquals("Deep Work", book.title)
        assertEquals("Cal Newport", book.author)
        assertEquals(2016, book.year)
        assertEquals("English", book.language)
        assertEquals("pdf", book.extension)
        assertEquals("10.00 MB", book.fileSizeText)
        assertEquals(10485760L, book.fileSizeBytes)
        assertEquals("Grand Central", book.publisher)
        assertEquals(304, book.pages)
        assertEquals("https://covers.zlib.test/991.jpg", book.coverUrl)
        assertEquals("<p>Focus.</p>", book.description)
        assertEquals("Business", book.categories)
        assertEquals("90", book.qualityScore)
        assertFalse(book.readOnlineAvailable)
    }

    @Test
    fun parseBookAcceptsBookAtRoot() {
        val book = ZLibraryJson.parseBook("""{"success":1,"id":"5","title":"Root Level"}""")

        assertEquals("5", book.id)
        assertEquals("Root Level", book.title)
    }

    // ---------------- 登录 ----------------

    @Test
    fun parseSessionMapsUser() {
        val session = ZLibraryJson.parseSession(
            """{"success":1,"user":{"id":42,"remix_userkey":"KEY"}}"""
        )

        assertEquals("42", session.userId)
        assertEquals("KEY", session.userKey)
    }

    // ---------------- 域名 ----------------

    @Test
    fun parseDomainsListsDomains() {
        val domains = ZLibraryJson.parseDomains(
            """{"success":1,"domains":[{"domain":"a.b"},{"domain":"c.d"}]}"""
        )

        assertEquals(listOf("a.b", "c.d"), domains)
    }

    @Test
    fun parseDomainsSkipsBlankEntries() {
        val domains = ZLibraryJson.parseDomains(
            """{"success":1,"domains":[{"domain":"a.b"},{"domain":""},{"other":"x"}]}"""
        )

        assertEquals(listOf("a.b"), domains)
    }

    // ---------------- 下载链接 ----------------

    @Test
    fun extractDownloadUrlPrefersDownloadUrlField() {
        val url = ZLibraryJson.extractDownloadUrl(
            """{"success":1,"downloadUrl":"https://zlib.bz/dl/one","url":"https://zlib.bz/dl/two"}"""
        )

        assertEquals("https://zlib.bz/dl/one", url)
    }

    @Test
    fun extractDownloadUrlAcceptsUrlField() {
        val url = ZLibraryJson.extractDownloadUrl("""{"success":1,"url":"https://zlib.bz/dl/two"}""")

        assertEquals("https://zlib.bz/dl/two", url)
    }

    @Test
    fun extractDownloadUrlAcceptsLinkField() {
        val url = ZLibraryJson.extractDownloadUrl("""{"success":1,"link":"https://zlib.bz/dl/three"}""")

        assertEquals("https://zlib.bz/dl/three", url)
    }

    @Test
    fun extractDownloadUrlAcceptsHrefField() {
        val url = ZLibraryJson.extractDownloadUrl("""{"success":1,"href":"https://zlib.bz/dl/four"}""")

        assertEquals("https://zlib.bz/dl/four", url)
    }

    @Test
    fun extractDownloadUrlAcceptsBareString() {
        val url = ZLibraryJson.extractDownloadUrl("\"https://zlib.bz/dl/five\"")

        assertEquals("https://zlib.bz/dl/five", url)
    }

    @Test
    fun extractDownloadUrlAcceptsRawTextUrl() {
        val url = ZLibraryJson.extractDownloadUrl("https://zlib.bz/dl/six")

        assertEquals("https://zlib.bz/dl/six", url)
    }

    @Test
    fun extractDownloadUrlScansNestedValues() {
        val url = ZLibraryJson.extractDownloadUrl(
            """{"success":1,"data":{"file":{"link":"https://zlib.bz/dl/seven"}}}"""
        )

        assertEquals("https://zlib.bz/dl/seven", url)
    }

    @Test
    fun extractDownloadUrlReturnsNullWhenAbsent() {
        assertNull(ZLibraryJson.extractDownloadUrl("""{"success":1,"error":"Please login"}"""))
    }

    // ---------------- 文件名 ----------------

    @Test
    fun fileNameFromUrlTakesLastSegment() {
        assertEquals(
            "Book Title.epub",
            ZLibraryClient.fileNameFrom("https://zlib.bz/dl/token/Book%20Title.epub?expires=1"),
        )
    }

    @Test
    fun fileNameFromUrlFallsBackWhenNoSegment() {
        assertEquals("download", ZLibraryClient.fileNameFrom("https://zlib.bz/dl/"))
    }

    // ---------------- 固定应答 ----------------

    private fun searchPayloadWithNext(next: String): String = """
        {"success":1,"books":[],"pagination":{"limit":20,"current":1,"before":false,"next":$next,"total_items":500,"total_pages":167}}
    """.trimIndent()

    private val searchPayload = """
        {
          "success": 1,
          "exactBooksCount": 500,
          "books": [
            {
              "id": "123456",
              "content_type": "book",
              "title": "Atomic Habits",
              "author": "James Clear",
              "volume": "1",
              "year": 2018,
              "edition": "1",
              "publisher": "Avery",
              "identifier": "9780735211292",
              "language": "English",
              "pages": 320,
              "series": "",
              "cover": "https://covers.zlib.test/123456.jpg",
              "active": 1,
              "deleted": 0,
              "filesize": 27950000,
              "filesizeString": "26.72 MB",
              "extension": "epub",
              "md5": "0123456789abcdef0123456789abcdef",
              "sha256": "abcdef0123456789abcdef0123456789abcdef0123456789abcdef0123456789",
              "href": "https://zlib.bz/book/6EOmrdlVwd/atomic-habits.html",
              "hash": "c79ab3",
              "kindleAvailable": 0,
              "sendToEmailAvailable": 1,
              "interestScore": 55,
              "qualityScore": 80,
              "dl": "/dl/Zg3xloAbjw",
              "readOnlineUrl": "https://zlib.bz/reader/abc",
              "categories": {"599": "Self-Help", "600": "Business"},
              "description": "<p>Every <b>habit</b> is a vote.</p>",
              "readOnlineAvailable": true
            },
            {
              "id": "2",
              "title": "Second Book"
            }
          ],
          "pagination": {
            "limit": 20,
            "current": 1,
            "before": false,
            "next": 2,
            "total_items": 500,
            "total_pages": 167
          }
        }
    """.trimIndent()
}
