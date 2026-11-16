package com.alanmaster.xread.data

/**
 * 同一部短剧的多季条目在源站上是不同 series_id，但用户心里它们是同一个系列。
 * 订阅时按标题基名归组，避免「问鼎仙途」和「问鼎仙途第三季」在书架里分成两条。
 */
object DramaSeriesGrouping {
    private val seasonSuffix = Regex(
        "(?i)(?:\\s*[（(]\\s*(?:第[0-9零〇一二三四五六七八九十百千两]+[季部]|season\\s*\\d+|s\\d+)\\s*[)）]" +
            "|\\s*(?:第[0-9零〇一二三四五六七八九十百千两]+[季部]|season\\s*\\d+|s\\d+))\\s*$",
    )
    private val punctuation = Regex("[\\s\\u00A0\\u00B7\\uFF1A:\\-—_]+")

    /** 去掉标题末尾的季数后缀，保留「问鼎仙途」这类系列名。 */
    fun displayTitle(title: String): String {
        val trimmed = title.trim()
        val match = seasonSuffix.find(trimmed) ?: return trimmed
        val base = trimmed.substring(0, match.range.first)
            .trimEnd(' ', '\uFF1A', ':', '-', '—', '_')
        return base.ifBlank { trimmed }
    }

    /** 归组键。标题基名去掉空白与标点，标题缺失时退回 seriesId。 */
    fun groupKey(title: String, seriesId: String = ""): String {
        val base = displayTitle(title).replace(punctuation, "")
        return base.ifBlank { seriesId }
    }

    fun groupKey(series: DramaSeries): String = groupKey(series.title, series.seriesId)

    fun isSubscribed(series: DramaSeries, subscriptions: List<DramaSubscription>): Boolean {
        val key = groupKey(series)
        return key.isNotEmpty() && subscriptions.any { groupKey(it.series) == key }
    }

    fun group(subscriptions: List<DramaSubscription>): List<DramaSubscriptionGroup> =
        subscriptions.groupBy { groupKey(it.series) }
            .map { (_, items) -> DramaSubscriptionGroup(items) }
            .sortedByDescending { it.primary.latestEpisodeCount }
}

/** 书架「我的订阅」展示的一个系列，可能包含同一系列的多季条目。 */
data class DramaSubscriptionGroup(val subscriptions: List<DramaSubscription>) {
    val primary: DramaSubscription
        get() = subscriptions.maxWithOrNull(
            compareBy<DramaSubscription> { it.latestEpisodeCount }
                .thenBy { it.series.episodeCount }
                .thenBy { it.series.seriesId },
        ) ?: subscriptions.first()

    val title: String
        get() = if (subscriptions.size == 1) primary.series.title
        else DramaSeriesGrouping.displayTitle(primary.series.title)

    val newEpisodeCount: Int get() = subscriptions.sumOf { it.newEpisodeCount }
    val totalEpisodeCount: Int get() = subscriptions.sumOf { it.series.episodeCount }
}
