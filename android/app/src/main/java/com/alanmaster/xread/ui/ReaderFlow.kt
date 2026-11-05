package com.alanmaster.xread.ui

import com.alanmaster.xread.data.ChapterContent

/**
 * 连续阅读的列表布局：多章正文接成**一条** LazyColumn。
 *
 * 每章占「页眉 1 项 + 段落 n 项」，章与章首尾相接；读者滚过本章末尾就是下一章正文，
 * 滚回开头就接着上一章正文，不换页、不清屏。
 *
 * 因为两头都会接，流的第 0 项不一定是打开时那一章；一切换算都只依赖当前 [flow]。
 *
 * 下标换算全在这里，纯函数，便于单测钉住。
 */
internal data class FlowItem(val chapter: Int, val paragraph: Int, val header: Boolean)

internal object ReaderFlow {

    fun contentSize(chapter: ChapterContent): Int =
        if (chapter.isManga) chapter.images.size else chapter.paragraphs.size

    /** 章 k 的页眉所在列表下标。 */
    fun offsetOf(flow: List<ChapterContent>, chapter: Int): Int {
        var offset = 0
        val end = chapter.coerceAtMost(flow.size)
        for (i in 0 until end) offset += 1 + contentSize(flow[i])
        return offset
    }

    /** 章 k 的页眉下标：跳章时滚到它就是章首。 */
    fun headerOf(flow: List<ChapterContent>, chapter: Int): Int = offsetOf(flow, chapter)

    /** 展开成扁平的列表项：每章「页眉 + 若干段落/图片」。 */
    fun items(flow: List<ChapterContent>): List<FlowItem> {
        val out = ArrayList<FlowItem>(flow.size * 8)
        for ((k, chapter) in flow.withIndex()) {
            out.add(FlowItem(k, 0, header = true))
            val size = contentSize(chapter)
            for (p in 0 until size) out.add(FlowItem(k, p, header = false))
        }
        return out
    }

    /** 正文项数（不含尾部那一行）。 */
    fun bodyItems(flow: List<ChapterContent>): Int = items(flow).size

    /**
     * 列表下标 → (章序号, 章内段落/图片下标)。
     *
     * 页眉项算该章第 0 段：旧实现里页眉就是这么算的（列表下标 0 → 段落 0），
     * 书签、朗读高亮、段评都按段落下标走，别改口径。
     */
    fun locate(flow: List<ChapterContent>, listIndex: Int): Pair<Int, Int> {
        if (flow.isEmpty()) return 0 to 0
        var offset = 0
        for ((k, chapter) in flow.withIndex()) {
            val size = contentSize(chapter)
            val len = 1 + size
            if (listIndex < offset + len) {
                val rel = listIndex - offset
                val paragraph = if (rel <= 0) 0 else (rel - 1).coerceAtMost(size - 1)
                return k to paragraph
            }
            offset += len
        }
        // 越界（尾部导航项）：落在最后一章的最后一段/最后一张图
        val last = flow.last()
        return flow.lastIndex to (contentSize(last) - 1).coerceAtLeast(0)
    }

    /** (章序号, 章内段落/图片下标) → 列表下标。 */
    fun indexOf(flow: List<ChapterContent>, chapter: Int, paragraph: Int): Int {
        if (flow.isEmpty()) return 0
        val k = chapter.coerceIn(0, flow.lastIndex)
        val size = contentSize(flow[k])
        return offsetOf(flow, k) + 1 + paragraph.coerceIn(0, (size - 1).coerceAtLeast(0))
    }

    /**
     * 全书口径的阅读进度（0f..1f）。
     *
     * 目录只给章 id 与标题，不给每章段数，所以按章等权估算：章长差异大时有偏差，
     * 但它连续、单调，跨章时不会像「章内百分比」那样从 100% 跳回 0%。
     */
    fun bookPercent(
        flow: List<ChapterContent>,
        listIndex: Int,
        chaptersTotal: Int,
        firstChapterIndex: Int,
    ): Float {
        if (flow.isEmpty() || chaptersTotal <= 0) return 0f
        val (k, p) = locate(flow, listIndex)
        val size = contentSize(flow[k])
        val within = if (size <= 1) 0f else (p.toFloat() / size).coerceIn(0f, 1f)
        return ((firstChapterIndex + k + within) / chaptersTotal.toFloat()).coerceIn(0f, 1f)
    }
}
