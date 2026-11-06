package com.alanmaster.xread.data

/**
 * 书架的内置分组：按上游连载状态与个人标记分类，**不落盘**。
 *
 * 手工分组写进 `shelf.json` 的 `group` 字段，书一完结就得再搬一次；这里改为按
 * [Book.status] 或 [ShelfItem.status] 现算现用，查一次更新分类就跟着变，用户不用维护。
 *
 * 键统一以 `@` 开头，与手工分组名（用户输入框里写字）区分开。
 */
object ShelfGrouping {
    const val WANT = "@want"
    const val SERIAL = "@serial"
    const val FINISHED = "@finished"

    /** 键 → 显示名；顺序即菜单顺序。 */
    val BUILTIN: Map<String, String> = linkedMapOf(
        WANT to "待读",
        SERIAL to "连载中",
        FINISHED to "已完结",
    )

    fun isBuiltin(group: String): Boolean = BUILTIN.containsKey(group)

    fun labelOf(group: String): String = BUILTIN[group] ?: group

    /**
     * 「待读」看的是个人标记（[ShelfItem.status]），与上游连载状态无关：一本书可以
     * 既在「待读」又在「连载中」，两个分组是正交的两个问题。
     *
     * 未取到 `creation_status` 的书按「连载中」算。
     *
     * 与 [BookStatus] 的口径一致：宁可把已经完本的书留在连载组，也不要把还在更的书
     * 标成完结——后者会让人以为可以一口气读完。下拉刷新会把真状态补回来。
     */
    fun matches(item: ShelfItem, group: String): Boolean = when (group) {
        WANT -> item.status == ShelfItem.STATUS_WANT
        SERIAL -> item.book.status != BookStatus.Finished
        FINISHED -> item.book.status == BookStatus.Finished
        else -> true
    }

    /** 只返回有书的自动分组；0 本的分组列在菜单里只会占位置。 */
    fun summarize(items: List<ShelfItem>): List<Group> = BUILTIN.mapNotNull { (key, label) ->
        val count = items.count { matches(it, key) }
        if (count > 0) Group(key, label, count) else null
    }

    data class Group(val key: String, val label: String, val count: Int)
}
