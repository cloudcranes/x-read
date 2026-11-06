package com.alanmaster.xread.data

import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 与 Python 侧 `tests/test_paragraph_split.py` 共用同一份 fixture（本目录
 * `src/test/resources/fixtures/paragraphs.json`）—— 两边段落数一旦分叉，离线缓存
 * 与在线阅读的段落号就会整体错位。
 *
 * fixture 只覆盖**纯文本切分**；实体解码属于 HTML 阶段（Android `Html.fromHtml` /
 * Python `convert_charrefs`），JVM 单测无法执行，故由 Python 侧单独断言。
 */
class ParagraphsTest {

    private fun fixture(): JSONArray {
        val stream = javaClass.getResourceAsStream("/fixtures/paragraphs.json")
            ?: throw AssertionError("找不到 fixtures/paragraphs.json 测试资源")
        return JSONArray(stream.use { it.readBytes().toString(Charsets.UTF_8) })
    }

    @Test fun matchesSharedFixture() {
        val cases = fixture()
        assertTrue("fixture 不能为空", cases.length() > 0)
        for (i in 0 until cases.length()) {
            val case = cases.getJSONObject(i)
            val expected = case.getJSONArray("out")
            val want = (0 until expected.length()).map { expected.getString(it) }
            assertEquals(case.getString("name"), want, Paragraphs.split(case.getString("text")))
        }
    }

    @Test fun splitsOnUnicodeLineSeparators() {
        assertEquals(listOf("a", "b"), Paragraphs.split("a\u2029b"))
        assertEquals(listOf("a", "b"), Paragraphs.split("a\u0085b"))
    }

    @Test fun keepsInnerNonBreakingSpace() {
        assertEquals(listOf("a\u00a0b"), Paragraphs.split("a\u00a0b"))
    }
}
