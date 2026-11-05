package com.alanmaster.xread.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 批量下载的两条上游路径返回的正文形态不同：`chapters/full` 已经是带换行的纯文本，
 * `chapters/batch` 才是原始 HTML。只有后者该走 `Html.fromHtml`，否则 Android 会把
 * 换行折叠成空格，整章粘成一段。
 */
class ChapterTextTest {

    @Test
    fun plainTextWithNewlinesIsNotHtml() {
        assertFalse(ChapterText.looksLikeHtml("第一段\n第二段\n第三段"))
        assertFalse(ChapterText.looksLikeHtml("可陆锋仿佛没听见这诛心讥讽。"))
    }

    @Test
    fun htmlTagsAreDetected() {
        assertTrue(ChapterText.looksLikeHtml("<p>第一段</p><p>第二段</p>"))
        assertTrue(ChapterText.looksLikeHtml("第一段<br/>第二段"))
        assertTrue(ChapterText.looksLikeHtml("<div class=\"content\">正文</div>"))
        assertTrue(ChapterText.looksLikeHtml("</p>"))
    }

    @Test
    fun bareComparisonsAreNotMistakenForTags() {
        assertFalse(ChapterText.looksLikeHtml("他说 3 < 5 且 7 > 2"))
        assertFalse(ChapterText.looksLikeHtml("速度 <= 10 米每秒"))
    }
}
