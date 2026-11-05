package com.alanmaster.xread.data

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 在线单章正文换行的回归测试。
 *
 * `Html.fromHtml` 在 Android 上会把连续空白（含 `\n`）折叠成一个空格，
 * 纯文本正文过一遍就整章粘成一段，段落号与段评下标全部错位。
 * 这里用 [androidLikeFromHtml] 模拟 AOSP `HtmlToSpannedConverter` 的行为：
 * - 文本里的空白折叠成一个空格（`\n` 也算）；
 * - `<br>` / `</p>` 这类块级标签才产生换行。
 */
class ChapterTextToPlainTest {

    private val BLOCK_END = Regex("(?i)^<\\s*(br\\s*/?|/p\\s*|/div\\s*)>$")

    /** 模拟 `android.text.Html.fromHtml`：空白折叠成空格，块级标签换行。 */
    private fun androidLikeFromHtml(text: String): String {
        val sb = StringBuilder()
        var prev: Char? = null
        var i = 0
        while (i < text.length) {
            val c = text[i]
            if (c == '<') {
                val end = text.indexOf('>', i)
                if (end < 0) {
                    sb.append(text, i, text.length)
                    break
                }
                if (BLOCK_END.matches(text.substring(i, end + 1))) sb.append('\n')
                prev = null
                i = end + 1
                continue
            }
            if (c.isWhitespace()) {
                if (prev != ' ' && prev != '\n') sb.append(' ')
                while (i < text.length && text[i].isWhitespace()) i++
                prev = ' '
                continue
            }
            sb.append(c)
            prev = c
            i++
        }
        return sb.toString()
    }

    @Test
    fun plainTextKeepsNewlines() {
        val text = "第一段\n第二段\n第三段"
        val plain = ChapterText.toPlain(text, ::androidLikeFromHtml)
        assertEquals(text, plain)
        assertEquals(listOf("第一段", "第二段", "第三段"), Paragraphs.split(plain))
    }

    @Test
    fun htmlIsParsed() {
        val plain = ChapterText.toPlain("<p>第一段</p><p>第二段</p>", ::androidLikeFromHtml)
        assertEquals(listOf("第一段", "第二段"), Paragraphs.split(plain))
    }

    @Test
    fun brokenParserFallsBackToOriginal() {
        val text = "<p>第一段</p>"
        val plain = ChapterText.toPlain(text) { throw RuntimeException("boom") }
        assertEquals(text, plain)
    }

    @Test
    fun withoutGuardPlainTextWouldCollapse() {
        // 回归依据：把纯文本直接丢给 fromHtml 就会粘成一段。
        assertEquals(1, Paragraphs.split(androidLikeFromHtml("第一段\n第二段\n第三段")).size)
        // 走 toPlain 之后段落保持不变。
        val plain = ChapterText.toPlain("第一段\n第二段\n第三段", ::androidLikeFromHtml)
        assertEquals(3, Paragraphs.split(plain).size)
        assertEquals(listOf("第一段", "第二段", "第三段"), Paragraphs.split(plain))
    }
}
