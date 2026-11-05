package com.alanmaster.xread.data

/**
 * 离线下载策略。全部本地判定，不需要任何服务端能力。
 *
 * 旧实现只有「全下」一种行为：1496 章一次拉完，既不区分网络，也没有容量上限，
 * 中途断网只会把失败摊在进度条上。
 */
data class DownloadPolicy(
    /** 仅 Wi-Fi 下自动续传/开始下载；移动网络下直接提示而不是硬跑。 */
    val wifiOnly: Boolean = false,
    /** 离线库总字节上限；<= 0 表示不限。 */
    val maxBytes: Long = 0L,
    /** 只下未读（跳过阅读进度之前的章节）。 */
    val skipRead: Boolean = false,
    /**
     * 阅读时自动缓存当前章之后的章数；0 = 关闭。
     *
     * 只认 [PREFETCH_CHOICES] 里的档位，其余值（旧配置、手改文件）按 [DEFAULT_PREFETCH] 处理。
     */
    val prefetchChapters: Int = DEFAULT_PREFETCH,
) {
    val unlimited: Boolean get() = maxBytes <= 0

    /** 归一化后的预取章数，界面与预取器都读它，避免各自判一次合法性。 */
    val prefetch: Int get() = if (prefetchChapters in PREFETCH_CHOICES) prefetchChapters else DEFAULT_PREFETCH

    companion object {
        const val DEFAULT_PREFETCH = 3
        val PREFETCH_CHOICES = listOf(0, 1, 3, 5, 10)
        val PREFETCH_LABELS = linkedMapOf(
            0 to "关",
            1 to "后 1 章",
            3 to "后 3 章",
            5 to "后 5 章",
            10 to "后 10 章",
        )
    }
}
