package com.alanmaster.xread.data

/**
 * 章内查找的命中区间。纯函数，便于离线单测。
 *
 * 与 [ChapterContent.findHits] 口径一致：去首尾空白、区分大小写、允许重叠。
 */
object FindHighlight {

    fun ranges(text: String, query: String): List<IntRange> {
        val q = query.trim()
        if (q.isEmpty() || text.isEmpty()) return emptyList()
        val out = ArrayList<IntRange>()
        var from = 0
        while (from <= text.length - q.length) {
            val at = text.indexOf(q, from)
            if (at < 0) break
            out.add(at until at + q.length)
            from = at + 1
        }
        return out
    }
}
