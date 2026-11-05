package com.alanmaster.xread.data

/**
 * 正文段落切分。**纯函数**，不依赖 `android.text.Html`，因此可被 JVM 单测覆盖。
 *
 * 旧实现内联在 `Api.chapter()` 里，用 `split('\n')` + `String.trim()`，
 * 与 Python 侧 `common.py` 的 `splitlines() + strip()` 不一致：
 * - 只含 NBSP（U+00A0）的行：Python 丢掉（`str.strip()` 认它是空白），Android 保留；
 * - U+2028/U+2029：Python 的 splitlines 当换行，Android 不当。
 * 两边段落数不同，离线缓存与在线阅读的段落号就会整体错位。
 *
 * 这里对齐 Python 的语义：按 Unicode 行分隔符切分，逐行 trim（含 NBSP），丢空行。
 */
object Paragraphs {

    /** 与 Python `str.splitlines()` 一致的分隔符集合（\r\n 单独处理）。 */
    private val BREAKS = setOf(
        '\n', '\r', '\u000B', '\u000C', '\u001C', '\u001D', '\u001E', '\u0085', '\u2028', '\u2029',
    )

    fun split(text: String): List<String> {
        if (text.isEmpty()) return emptyList()
        val out = ArrayList<String>(text.length / 120 + 4)
        val line = StringBuilder()
        var i = 0
        while (i < text.length) {
            val c = text[i]
            when {
                c == '\r' -> {
                    flush(out, line)
                    i++
                    if (i < text.length && text[i] == '\n') i++
                }
                c in BREAKS -> {
                    flush(out, line)
                    i++
                }
                else -> {
                    line.append(c)
                    i++
                }
            }
        }
        flush(out, line)
        return out
    }

    private fun flush(out: MutableList<String>, line: StringBuilder) {
        if (line.isEmpty()) return
        // Kotlin 的 Char.isWhitespace 覆盖 isSpaceChar，因此 NBSP 也会被裁掉
        val trimmed = line.toString().trim { it.isWhitespace() || it == '\u00A0' }
        if (trimmed.isNotEmpty()) out.add(trimmed)
        line.setLength(0)
    }
}
