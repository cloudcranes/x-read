package com.alanmaster.xread.data

import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLDecoder
import java.net.URLEncoder

/**
 * z-library 的一本书（EAPI 口径）。
 *
 * 只取展示与选文件真正用得上的字段：搜索结果和详情页共用这一份结构，
 * 两边的字段名本来就不完全一样，这里统一收敛掉。
 */
data class ZLibraryBook(
    /** z-library 的书号（上游给的是数字字符串）。 */
    val id: String,
    /** 短 hash，取详情与下载链接都要它。 */
    val hash: String,
    val title: String,
    val author: String,
    val year: Int = 0,
    val language: String = "",
    /** 文件后缀，如 `epub` / `pdf`。 */
    val extension: String = "",
    /** 体积文案，如 `26.72 MB`。 */
    val fileSizeText: String = "",
    /** 体积字节数。 */
    val fileSizeBytes: Long = 0L,
    val publisher: String = "",
    val pages: Int = 0,
    val coverUrl: String = "",
    /** 简介原文（上游就是 HTML），不在这里转纯文本。 */
    val description: String = "",
    /** 分类名，多个用 `, ` 连接。 */
    val categories: String = "",
    /** 质量分，上游给的是数字，这里统一成字符串。 */
    val qualityScore: String = "",
    /** 能否在线读。 */
    val readOnlineAvailable: Boolean = false,
)

/** 一页搜索结果。 */
data class ZLibraryPage(
    val books: List<ZLibraryBook>,
    /** 当前页号（从 1 开始）。 */
    val page: Int,
    val totalPages: Int,
    val totalItems: Int,
    /** 还有下一页。 */
    val hasNext: Boolean,
)

/** 登录后的凭证：后续请求靠这两个字段拼 Cookie。 */
data class ZLibrarySession(val userId: String, val userKey: String)

/** z-library 侧的任何失败：HTTP 错误、业务失败、连不上服务器。 */
class ZLibraryException(message: String) : Exception(message)

/**
 * EAPI 应答的纯解析：JSON 字符串 → 数据模型。
 *
 * 单独拆成 object 是为了让这一半能在 JVM 单测里直接跑，不碰网络。
 * 真正的 IO 在 [ZLibraryClient]。
 */
object ZLibraryJson {

    /** 具名下载链接字段的优先顺序。 */
    private val DOWNLOAD_URL_KEYS = listOf("downloadUrl", "url", "link", "href")

    /**
     * 搜索应答。
     *
     * 上游形状：`{"success":1,"books":[...],"pagination":{"current":1,"next":2,"total_items":500,"total_pages":167}}`。
     * `pagination.next` 是「还有下一页」的页码，没有下一页时给 `null` 或 `false`。
     *
     * @throws ZLibraryException 上游回了 `success != 1`。
     */
    fun parseSearch(body: String): ZLibraryPage {
        val root = requireSuccess(body)
        val array = root.optJSONArray("books")
        val books = if (array == null) {
            emptyList()
        } else {
            (0 until array.length()).mapNotNull { i -> array.optJSONObject(i)?.let { bookOf(it) } }
        }
        val pagination = root.optJSONObject("pagination")
        return ZLibraryPage(
            books = books,
            page = pagination?.optInt("current", 1) ?: 1,
            totalPages = pagination?.optInt("total_pages", 0) ?: 0,
            totalItems = pagination?.optLong("total_items", 0L)?.toInt() ?: 0,
            hasNext = hasNextPage(pagination),
        )
    }

    /**
     * 详情应答：`{"success":1,"book":{...}}`。
     *
     * 上游偶尔把 book 直接摊在根上，两种都认。
     *
     * @throws ZLibraryException 上游回了 `success != 1`。
     */
    fun parseBook(body: String): ZLibraryBook {
        val root = requireSuccess(body)
        val node = root.optJSONObject("book") ?: root
        return bookOf(node)
    }

    /**
     * 登录应答：`{"success":1,"user":{"id":42,"remix_userkey":"..."}}`。
     *
     * @throws ZLibraryException 上游回了 `success != 1`，或应答里没有用户信息。
     */
    fun parseSession(body: String): ZLibrarySession {
        val root = requireSuccess(body)
        val user = root.optJSONObject("user")
            ?: throw ZLibraryException("上游没有返回用户信息")
        return ZLibrarySession(user.str("id"), user.str("remix_userkey"))
    }

    /**
     * 域名列表应答：`{"success":1,"domains":[{"domain":"z-lib.sk"},...]}`。
     *
     * @throws ZLibraryException 上游回了 `success != 1`。
     */
    fun parseDomains(body: String): List<String> {
        val root = requireSuccess(body)
        val array = root.optJSONArray("domains") ?: return emptyList()
        val out = ArrayList<String>()
        for (i in 0 until array.length()) {
            val node = array.optJSONObject(i) ?: continue
            val domain = node.str("domain")
            if (domain.isNotBlank()) out.add(domain)
        }
        return out
    }

    /**
     * 失败时的错误文案；成功返回 null。
     *
     * 上游失败一律带 `error` 字段（例：`Please login`、`Incorrect email or password`），
     * 少数接口给 `message`。都没有就给一句兜底。
     */
    fun errorMessage(body: String): String? {
        val root = runCatching { JSONObject(body) }.getOrNull() ?: return null
        if (root.optInt("success", 1) == 1) return null
        val text = root.str("error").ifBlank { root.str("message") }
        return text.ifBlank { "上游返回失败" }
    }

    /**
     * 从取文件链接的应答里挖出下载地址。
     *
     * 这一条的形状不稳定：字段名可能是 `downloadUrl` / `url` / `link` / `href`，
     * 也可能整个应答就是一个裸 URL 字符串。找不到具名字段时，
     * 就把整段 JSON 里第一个以 `http` 开头的字符串捞出来。
     */
    fun extractDownloadUrl(body: String): String? {
        val trimmed = body.trim()
        // 整个应答就是一个裸 URL（连 JSON 都不是）
        if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) return trimmed
        val root = runCatching { JSONObject(trimmed) }.getOrNull()
        if (root != null) {
            for (key in DOWNLOAD_URL_KEYS) {
                val value = root.opt(key) ?: continue
                val url = when (value) {
                    is String -> value
                    is JSONObject -> firstHttpUrl(value)
                    else -> null
                }
                if (url != null && url.startsWith("http")) return url
            }
            firstHttpUrl(root)?.let { return it }
        }
        // JSON 字符串字面量："https://..."
        if (trimmed.length >= 2 && trimmed.startsWith("\"") && trimmed.endsWith("\"")) {
            val inner = trimmed.substring(1, trimmed.length - 1)
            if (inner.startsWith("http")) return inner
        }
        return null
    }

    /** 上游失败（`success != 1`）时抛异常，带上上游给的原因。 */
    private fun requireSuccess(body: String): JSONObject {
        val root = runCatching { JSONObject(body) }.getOrNull()
            ?: throw ZLibraryException("上游返回的内容看不懂")
        if (root.optInt("success", 1) != 1) {
            throw ZLibraryException(errorMessage(body) ?: "上游返回失败")
        }
        return root
    }

    /** 搜索与详情里的书籍条目，字段名两边一致，直接照抄。 */
    private fun bookOf(node: JSONObject): ZLibraryBook = ZLibraryBook(
        id = node.str("id"),
        hash = node.str("hash"),
        title = node.str("title"),
        author = node.str("author"),
        year = node.optInt("year", 0),
        language = node.str("language"),
        extension = node.str("extension"),
        fileSizeText = node.str("filesizeString"),
        fileSizeBytes = node.long("filesize", 0L),
        publisher = node.str("publisher"),
        pages = node.optInt("pages", 0),
        // 上游偶尔给 http:// 或 // 开头的封面地址，统一升成 https（见 normalizeImageUrl）
        coverUrl = normalizeImageUrl(node.str("cover")),
        description = node.str("description"),
        categories = categoriesOf(node),
        qualityScore = node.str("qualityScore"),
        readOnlineAvailable = node.flag("readOnlineAvailable", false),
    )

    /**
     * 分类：上游给的是 `{"599":"Self-Help","600":"Business"}` 这样的字典。
     *
     * JSON 对象的键没有顺序可言，所以按键排一遍再拼，保证同一份数据每次渲染出来的顺序一致。
     */
    private fun categoriesOf(node: JSONObject): String {
        val obj = node.optJSONObject("categories") ?: return ""
        val entries = ArrayList<Pair<String, String>>()
        val keys = obj.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            val value = obj.optString(key).trim()
            if (value.isNotEmpty()) entries.add(key to value)
        }
        entries.sortBy { it.first }
        return entries.joinToString(", ") { it.second }
    }

    /** `pagination.next`：有下一页时是页码，没有时是 `null` 或 `false`。 */
    private fun hasNextPage(pagination: JSONObject?): Boolean {
        if (pagination == null) return false
        val next = pagination.opt("next") ?: return false
        return when (next) {
            JSONObject.NULL -> false
            is Boolean -> next
            is Number -> next.toInt() > 0
            is String -> next.isNotEmpty() && next != "0" && !next.equals("false", ignoreCase = true)
            else -> false
        }
    }

    /** 深度优先找第一个以 `http` 开头的字符串值。 */
    private fun firstHttpUrl(value: Any?): String? = when (value) {
        is String -> if (value.startsWith("http")) value else null
        is JSONObject -> firstHttpInObject(value)
        is JSONArray -> firstHttpInArray(value)
        else -> null
    }

    private fun firstHttpInObject(node: JSONObject): String? {
        val keys = node.keys()
        while (keys.hasNext()) {
            firstHttpUrl(node.opt(keys.next()))?.let { return it }
        }
        return null
    }

    private fun firstHttpInArray(array: JSONArray): String? {
        for (i in 0 until array.length()) {
            firstHttpUrl(array.opt(i))?.let { return it }
        }
        return null
    }
}

/**
 * z-library 移动端 EAPI 客户端。
 *
 * 匿名就能搜索、看详情；只有真去下文件才需要用户自己的账号 Cookie
 * （`remix_userid` / `remix_userkey`）。
 *
 * 域名是这套接口最脆的一环：同一个服务有一堆镜像域名，随时被拦、被质询。
 * 所以搜索与详情会在 [FALLBACK_BASES] 里逐个回退；登录与下载不做回退——
 * 登录失败是账号问题，下载链接跟域名绑定，换域名没有意义。
 *
 * 方法都是阻塞的普通函数，由调用方放在 IO 线程里跑。
 */
class ZLibraryClient(
    private val baseUrl: String = DEFAULT_BASE,
    private val session: ZLibrarySession? = null,
) {
    companion object {
        const val DEFAULT_BASE = "https://zlib.bz"

        /** 实测可用的域名表；一个不通就换下一个。 */
        val FALLBACK_BASES: List<String> = listOf(
            "https://zlib.bz", "https://z-library.ec", "https://z-lib.gl", "https://article.sk",
            "https://articles.sk", "https://z-lib.by", "https://z-library.qa", "https://z-library.website", "https://z-lib.gd",
        )

        /** 移动端 Chrome。上游对 UA 很敏感，桌面 UA 会被当成脚本流量挡掉。 */
        private const val USER_AGENT =
            "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"

        private const val CONNECT_TIMEOUT_MS = 15_000
        private const val READ_TIMEOUT_MS = 30_000

        /**
         * 从下载 URL 末段推断文件名。
         *
         * 上游给的链接末段就是文件名（带后缀），取不到时给个兜底名。
         */
        fun fileNameFrom(url: String): String {
            val path = url.substringBefore('?').substringBefore('#')
            // 以 / 结尾说明只给了目录，没有文件名；别把目录名（dl）当成文件名。
            if (path.endsWith("/")) return "download"
            val name = path.substringAfterLast('/')
            if (name.isBlank()) return "download"
            return try {
                URLDecoder.decode(name.replace("+", "%2B"), "UTF-8")
            } catch (e: Exception) {
                name
            }
        }
    }

    /**
     * 搜索。匿名可用。
     *
     * @param page 页号，从 1 开始。
     * @param limit 每页条数。
     * @throws ZLibraryException 所有域名都试完仍失败。
     */
    fun search(query: String, page: Int = 1, limit: Int = 20): ZLibraryPage = withFallback { base ->
        val form = formOf(
            "message" to query,
            "limit" to limit.toString(),
            "page" to page.toString(),
        )
        parse { ZLibraryJson.parseSearch(post("$base/eapi/book/search", form)) }
    }

    /**
     * 书籍详情。匿名可用。
     *
     * @param id 书号。
     * @param hash 短 hash。
     * @throws ZLibraryException 所有域名都试完仍失败。
     */
    fun book(id: String, hash: String): ZLibraryBook = withFallback { base ->
        val body = get("$base/eapi/book/${pathPart(id)}/${pathPart(hash)}")
        parse { ZLibraryJson.parseBook(body) }
    }

    /**
     * 账号登录。
     *
     * 成功后拿到的凭证要交给新建的客户端实例才会带上 Cookie：
     * `ZLibraryClient(baseUrl, ZLibraryClient(...).login(email, password))`。
     *
     * @throws ZLibraryException 账号密码不对，或连不上服务器。
     */
    fun login(email: String, password: String): ZLibrarySession {
        val base = normalizeBase(baseUrl)
        val form = formOf("email" to email, "password" to password)
        val body = post("$base/eapi/user/login", form)
        return parse { ZLibraryJson.parseSession(body) }
    }

    /**
     * 取文件下载地址。需要登录态：未登录时上游回 `Please login`。
     *
     * 不做域名回退——下载链接跟域名绑定，换域名拿到的链接对不上。
     *
     * @throws ZLibraryException 未登录、上游没给链接，或连不上服务器。
     */
    fun downloadUrl(id: String, hash: String): String {
        val base = normalizeBase(baseUrl)
        val body = get("$base/eapi/book/${pathPart(id)}/${pathPart(hash)}/file")
        return ZLibraryJson.extractDownloadUrl(body)
            ?: throw ZLibraryException(ZLibraryJson.errorMessage(body) ?: "上游没有返回下载链接")
    }

    /**
     * 下载文件本体，返回原始字节。
     *
     * 拿到 HTML 说明下到的是登录页或质询页，不是文件——这时不能把网页当电子书存下去。
     *
     * @throws ZLibraryException 拿到的是网页，或连接失败。
     */
    fun download(url: String): ByteArray {
        val target = if (url.startsWith("http://") || url.startsWith("https://")) {
            url
        } else {
            normalizeBase(baseUrl) + "/" + url.trimStart('/')
        }
        var conn: HttpURLConnection? = null
        try {
            val connection = open(target, "GET")
            conn = connection
            val code = connection.responseCode
            if (code !in 200..299) {
                val text = connection.errorStream?.use { it.readBytes().toString(Charsets.UTF_8) } ?: ""
                throw ZLibraryException(ZLibraryJson.errorMessage(text) ?: "HTTP $code")
            }
            val contentType = connection.contentType?.lowercase().orEmpty()
            if (contentType.contains("text/html")) {
                throw ZLibraryException("下载返回的是网页而不是文件，请重新登录后再试")
            }
            return connection.inputStream.use { it.readBytes() }
        } catch (e: ZLibraryException) {
            throw e
        } catch (e: IOException) {
            throw ZLibraryException("连不上 z-library 服务器")
        } finally {
            conn?.disconnect()
        }
    }

    /**
     * 列出上游当前在用的域名，方便在设置页里挑一个能通的。
     *
     * @throws ZLibraryException 所有域名都试完仍失败。
     */
    fun domains(): List<String> = withFallback { base ->
        val body = get("$base/eapi/info/domains")
        parse { ZLibraryJson.parseDomains(body) }
    }

    // ---------------- HTTP ----------------

    /** 跨域名回退：先试 [baseUrl]，再依次试 [FALLBACK_BASES] 里剩下的域名，全失败抛最后一个错误。 */
    private fun <T> withFallback(block: (String) -> T): T {
        var last: ZLibraryException? = null
        for (base in candidateBases()) {
            try {
                return block(base)
            } catch (e: ZLibraryException) {
                last = e
            }
        }
        throw last ?: ZLibraryException("z-library 没有可用域名")
    }

    /** 当前域名 + 去掉重复的备用域名。 */
    private fun candidateBases(): List<String> {
        val first = normalizeBase(baseUrl)
        val rest = FALLBACK_BASES.map { normalizeBase(it) }.filter { it != first }
        return listOf(first) + rest
    }

    private fun normalizeBase(base: String): String = base.trim().trimEnd('/')

    private fun get(url: String): String = request(url, null)

    private fun post(url: String, form: String): String = request(url, form)

    /** 表单编码：`a=1&b=2`。 */
    private fun formOf(vararg pairs: Pair<String, String>): String =
        pairs.joinToString("&") { (key, value) -> "${enc(key)}=${enc(value)}" }

    private fun enc(value: String): String = URLEncoder.encode(value, "UTF-8")

    /** 路径段编码：表单编码把空格写成 `+`，路径里要的是 `%20`。 */
    private fun pathPart(value: String): String = enc(value).replace("+", "%20")

    /** JSON 解析出错时也归一成 z-library 的异常，别把 org.json 的异常漏给上层。 */
    private fun <T> parse(block: () -> T): T = try {
        block()
    } catch (e: ZLibraryException) {
        throw e
    } catch (e: Exception) {
        throw ZLibraryException("上游返回的内容看不懂")
    }

    private fun request(url: String, form: String?): String {
        var conn: HttpURLConnection? = null
        try {
            val connection = open(url, if (form == null) "GET" else "POST")
            conn = connection
            if (form != null) {
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
                connection.outputStream.use { it.write(form.toByteArray(Charsets.UTF_8)) }
            }
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val text = stream?.use { it.readBytes().toString(Charsets.UTF_8) } ?: ""
            if (code !in 200..299) {
                throw ZLibraryException(ZLibraryJson.errorMessage(text) ?: "HTTP $code")
            }
            return text
        } catch (e: ZLibraryException) {
            throw e
        } catch (e: IOException) {
            throw ZLibraryException("连不上 z-library 服务器")
        } finally {
            conn?.disconnect()
        }
    }

    private fun open(url: String, method: String): HttpURLConnection {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.requestMethod = method
        conn.connectTimeout = CONNECT_TIMEOUT_MS
        conn.readTimeout = READ_TIMEOUT_MS
        conn.instanceFollowRedirects = true
        conn.setRequestProperty("User-Agent", USER_AGENT)
        conn.setRequestProperty("Accept", "application/json, text/plain, */*")
        session?.let {
            conn.setRequestProperty("Cookie", "remix_userid=${it.userId}; remix_userkey=${it.userKey}")
        }
        return conn
    }
}
