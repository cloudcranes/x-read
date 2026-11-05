package com.alanmaster.xread.data

/**
 * 传输层失败的分类。决定下载器是「降并发退避（可重试）」还是「跳过该章（永久）」。
 *
 * 关键点：上游内容缺失（如 `Missing initial page JSON`）会被服务端包成 502 返回，
 * 因此**错误码优先于状态码**——否则一次缺章就会把整池并发拖到 1。
 */
object ErrorClass {

    private val PERMANENT = setOf(
        "UPSTREAM_INVALID", "NOT_FOUND", "CHAPTER_NOT_FOUND", "CHAPTER_LOCKED",
        "INVALID_ARGUMENT", "SOURCE_NOT_FOUND", "UPSTREAM_REJECTED", "UPSTREAM_ACCESS_DENIED",
        "AUTH_REQUIRED", "SHELF_NOT_FOUND", "UNSUPPORTED", "INVALID_CURSOR",
    )

    private val TRANSIENT_CODES = setOf(
        "UPSTREAM_UNAVAILABLE", "UPSTREAM_HTTP", "LOCAL_BUSY", "RATE_LIMITED",
        "STORAGE_UNAVAILABLE", "INTERNAL_ERROR",
    )

    private val TRANSIENT_STATUS = setOf(408, 425, 429, 500, 502, 503, 504)

    /**
     * core 多数时候只回一句字符串错误（`{"error":"..."}`），没有结构化错误码。
     * 这些文案代表上游确定不会好转，重试纯属浪费并会拖垮并发窗口。
     */
    private val PERMANENT_HINTS = listOf(
        "missing initial page json",
        "chapter not found",
        "no such chapter",
        "book not found",
        "不存在",
        "已下架",
        "locked",
    )

    /** true 表示值得重试（网络/限流/服务端临时故障）。 */
    fun isTransient(status: Int, code: String, message: String = ""): Boolean {
        if (code in PERMANENT) return false
        if (code in TRANSIENT_CODES) return true
        val text = message.lowercase()
        if (text.isNotEmpty() && PERMANENT_HINTS.any { text.contains(it) }) return false
        return status in TRANSIENT_STATUS
    }
}
