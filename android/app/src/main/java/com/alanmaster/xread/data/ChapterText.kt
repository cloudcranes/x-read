package com.alanmaster.xread.data

/**
 * 章节正文的「是否还需要做 HTML 解析」判定。
 *
 * 批量下载有两条上游路径，返回的正文形态**不一样**：
 * - `chapters/full`：原生核心的 `FullContentProcessor` 已经把 `<br>`/`<p>` 换成
 *   `\n` 并去掉其余标签，返回的是**带换行的纯文本**；
 * - `chapters/batch`：只做解密，返回的仍是**原始 HTML**。
 *
 * 两条路混用后无差别再套一遍 `Html.fromHtml`，Android 的 HTML 解析会把连续空白
 * （含 `\n`）折叠成一个空格，整章正文就粘成一段 —— 离线阅读的段落号与在线阅读、
 * 段评下标全部错位。所以只有真的看到标签时才走 HTML 解析。
 */
object ChapterText {

    /** 形如 `<p>`、`</p>`、`<br/>`、`<div class="x">` 的标签。 */
    private val TAG = Regex("<\\s*/?\\s*[a-zA-Z][^>]*>")

    fun looksLikeHtml(text: String): Boolean = TAG.containsMatchIn(text)

    /**
     * 章节正文 → 纯文本。**只有真的看到标签时才做 HTML 解析**。
     *
     * 纯文本正文过一遍 `Html.fromHtml` 会丢光换行（Android 把连续空白折叠成
     * 一个空格），整章粘成一段，段落号与段评下标全部错位。
     *
     * [fromHtml] 由调用方注入（Android 上就是 `Html.fromHtml`），这样这条策略
     * 能在没有 Robolectric 的 JVM 单测里跑。
     */
    fun toPlain(text: String, fromHtml: (String) -> String): String =
        if (!looksLikeHtml(text)) text
        else try {
            fromHtml(text)
        } catch (_: Throwable) {
            text
        }
}
