package com.alanmaster.xread.ui

/**
 * 整页翻页的落点计算：纯整数、无副作用，便于单测钉住。
 *
 * 旧实现是 `animateScrollBy(±一屏高度)`：按像素滚，落点必然切在段中间，
 * 读者每翻一页都得重新找行首；到章末还会滚出一片空白才翻章。
 *
 * 新口径：翻页后新页的页首永远顶在一段的开头。
 * 取「最后一个顶部仍在屏底之上的可见段」，把它顶到内容区顶端。上一页底部
 * 被切掉的那段因此会在新页完整重放——宁可重叠一段，不可漏字。
 */

/** 列表项的最小信息，字段含义同 `LazyListItemInfo`：offset 相对视口起点，向上滚出时为负。 */
internal data class TurnItem(val index: Int, val offset: Int, val size: Int)

/**
 * 一次翻页的落点。
 *
 * @param index 翻页后应落在内容区顶端的项
 */
internal data class TurnPlan(val index: Int)

internal object ReaderTurn {

    /**
     * 下一页：最后一个顶部仍在 [fold] 之上的可见项。
     *
     * @param fold 内容区底边在视口内的坐标
     * @return null 表示这一屏推不动（首段比一屏还高，或已到末项），调用方去翻章
     */
    fun forward(visible: List<TurnItem>, fold: Int, firstIndex: Int): TurnPlan? {
        var target: TurnItem? = null
        for (item in visible) {
            // 顶部正好压在屏底的项不算看得见（视口底边是开区间）
            if (item.offset >= fold) break
            target = item
        }
        val land = target ?: return null
        // 必须真的换了一项，否则原地打转
        if (land.index <= firstIndex) return null
        return TurnPlan(land.index)
    }

    /**
     * 上一页的落点：按一屏高度回推若干段，落在段落起点。
     *
     * 往回时看不见的那些项没有被量过（LazyColumn 只量可见项），没法像前进那样
     * 精确凑一屏，只能按「可见段的典型高度」估算一屏能装几段，再回推同样多段。
     * 用中位数而不是平均数：页眉比正文高得多，平均数会被它带偏。
     *
     * @param visible 当前可见项
     * @param firstIndex 当前首项下标
     * @param pagePx 一屏的像素高度
     * @return 上一页页首所在的下标（段首对齐）
     */
    fun backTarget(visible: List<TurnItem>, firstIndex: Int, pagePx: Int): Int {
        if (firstIndex <= 0) return 0
        val sizes = visible.map { it.size }.filter { it > 0 }.sorted()
        val median = if (sizes.isEmpty()) 100 else sizes[sizes.size / 2]
        val step = if (median <= 0) 1 else (pagePx + median - 1) / median
        return (firstIndex - step.coerceAtLeast(1)).coerceAtLeast(0)
    }
}
