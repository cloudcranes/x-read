package com.alanmaster.xread.data

import com.alanmaster.xread.BuildConfig
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class UiState(
    val loading: Boolean = false,
    val message: String = "",
    /** 全量书架（未被排序/过滤破坏）；UI 用 shelf。 */
    val shelfAll: List<ShelfItem> = emptyList(),
    val shelf: List<ShelfItem> = emptyList(),
    /**
     * 下拉刷新完成计数：每完成一次 +1。
     *
     * 不用布尔标记：快到同一帧完成时布尔翻转会被合帧吃掉，UI 就再也收不回指示器。
     */
    val shelfRefreshDone: Int = 0,
    val results: List<Book> = emptyList(),
    /** 是否已执行过一次搜索；用于区分「还没搜」与「搜了但没结果」。 */
    val searched: Boolean = false,
    /** 最近一次搜索的错误；空串表示没有错误。 */
    val searchError: String = "",
    val hot: List<HotWord> = emptyList(),
    /** 最近搜索词，新的在前；纯本地。 */
    val searchHistory: List<String> = emptyList(),
    val rankings: List<Book> = emptyList(),
    val rankAlgorithm: Int = 101,
    val rankCategory: String = "0",
    val rankCatalog: ContentParser.RankCatalog? = null,
    val book: Book? = null,
    val chapters: List<Chapter> = emptyList(),
    /** 目录重拉完成计数，驱动下拉指示器收起（布尔会被合帧吃掉）。 */
    val tocRefreshDone: Int = 0,
    /**
     * 连续阅读：已加载的相邻章节，按阅读顺序排列。
     *
     * 滚到本章末尾会自动把下一章接在它后面（[appendNextChapter]），
     * 滚到开头会往前接上一章（[prependPreviousChapter]），
     * 读者一路滚下去或滚上去都是一本书，不用换页、不用清屏。
     * 打开时第一项是当前打开的那一章，之后会随上下接章往两头长。
     */
    val flow: List<ChapterContent> = emptyList(),
    /** 当前活动章在 [flow] 里的下标。 */
    val flowIndex: Int = 0,
    /** 当前读到的章 id：进度、书签、段评都按它定位。 */
    val currentChapterId: String = "",
    /** 正在接下一章。 */
    val flowLoading: Boolean = false,
    /** 接下一章失败的原因；空串表示没有错误。 */
    val flowError: String = "",
    /** 正在往流头部接上一章。 */
    val flowLoadingPrev: Boolean = false,
    /** 上面接不动了：已经是第一章，或上一次接上一章失败。 */
    val flowPrevDone: Boolean = false,
    /** 跳章请求：请求序号自增即触发阅读页滚到 [scrollChapterId]。 */
    val scrollRequest: Int = 0,
    /** 要滚到的章 id。 */
    val scrollChapterId: String = "",
    /** 要滚到的段落下标（章内）；-1 表示滚到章首页眉。 */
    val scrollParagraph: Int = -1,
    /** 段评气泡：章 id → 该章的段落气泡（每章下标独立）。 */
    val bubblesByChapter: Map<String, List<ParagraphBubble>> = emptyMap(),
    /** 书签与高亮：章 id → 该章的标记（每章下标独立）。 */
    val marksByChapter: Map<String, List<Mark>> = emptyMap(),
    /** 朗读中的章；与阅读活动章无关——朗读可以跑在读者前面。 */
    val ttsChapter: ChapterContent? = null,
    /** 朗读请求序号：自增即触发起播。 */
    val ttsRequest: Int = 0,
    /** 本次起播的起始段落下标。 */
    val ttsRequestParagraph: Int = 0,
    /** 正文加载失败的文案；与一次性提示 message 分开，否则会被 clearMessage 立刻抹掉。 */
    val readerError: String = "",
    /** 阅读器外观偏好（字号/行距/背景/翻页方式），从 settings.json 载入。 */
    val readerPrefs: ReaderPrefs = ReaderPrefs(),
    val chapterIndex: Int = 0,
    val offlineBooks: List<Book> = emptyList(),
    val resumable: List<OfflineRepo.DownloadTask> = emptyList(),
    /** 目录页缓存圆点：章节键集合。整本一次扫盘，不在 composition 里做 IO。 */
    val cachedChapters: Set<String> = emptySet(),
    /** 离线库总体积与每本书占用（字节）。 */
    val storageTotal: Long = 0,
    val bookSizes: Map<String, Long> = emptyMap(),
    val leftoverCount: Int = 0,
    val leftoverBytes: Long = 0,
    /** 离线下载策略：仅 Wi-Fi / 容量上限 / 只下未读。 */
    val downloadPolicy: DownloadPolicy = DownloadPolicy(),
    /** 每天检查一次新章并发通知。 */
    val newChapterNotify: Boolean = false,
    /** 每日阅读目标；连续天数不存状态，由 [GoalMath] 现算。 */
    val readingGoal: ReadingGoal = ReadingGoal(),
    /** 每日阅读提醒（本地闹钟，不是新章提醒）。 */
    val reminder: ReadingReminder = ReadingReminder(),
    /** 今天已读秒数，随每次结算刷新，供工具页目标环使用。 */
    val todaySeconds: Long = 0,
    /**
     * 刚被移出书架的条目，非空时界面给出「撤销」。
     *
     * 存整条 ShelfItem 而不是 Book：撤销要把分组、入架时间、更新基线一起还回去，
     * 只凭 Book 重新入架会变成一本「今天刚加、没有分组」的新书。
     */
    val undoRemove: List<ShelfItem> = emptyList(),
    /** 批量缓存队列里还没轮到的本数；工具页据此显示「后面还有 N 本」。 */
    val cacheQueueSize: Int = 0,
    /** 首启引导是否盖在最上层；只在没有任何本地痕迹时置 true。 */
    val showOnboarding: Boolean = false,
    val related: List<Book> = emptyList(),
    val feedRanked: List<Book> = emptyList(),
    val feedRecommended: List<Book> = emptyList(),
    val feedNew: List<Book> = emptyList(),
    val feedBoards: List<HomeBoard> = emptyList(),
    val feedMoreLoading: Boolean = false,
    /** 首页推荐加载失败的原始原因；空串表示没有错误。 */
    val feedError: String = "",
    val feedOffset: Int = 0,
    val feedExhausted: Boolean = false,
    /** 书评广场（books/{id}/comments）：offset 分页，无排序参数。 */
    val comments: List<BookComment> = emptyList(),
    val commentTotal: Int = 0,
    /** 点评人数，与评论条数不是一个口径。 */
    val commentRaters: Int = 0,
    val commentContext: String = "",
    val commentsLoading: Boolean = false,
    val commentsHasMore: Boolean = false,
    val commentsOffset: Int = 0,
    /** 分类页：全部分类与某分类下的书。 */
    val categories: List<BookCategory> = emptyList(),
    val categoriesLoading: Boolean = false,
    val categoryBooks: List<Book> = emptyList(),
    val categoryTitle: String = "",
    val categoryLoading: Boolean = false,
    /** 段评面板打开时对应的章 id；跨章后气泡要按章取，段评请求也按章。 */
    val paragraphCommentChapter: String = "",
    /**
     * 段评面板：当前打开的段落与它的段评列表。
     *
     * [paragraphCommentOpen] 为 true 时界面展示这个面板。
     * 段评加载失败只落到 [paragraphCommentError] 上，**不影响正文**——
     * 读者正在看书，不能因为一条评论读不出来就打断阅读。
     */
    val paragraphCommentOpen: Boolean = false,
    /** 当前打开的段落下标；-1 表示没打开。 */
    val paragraphCommentIndex: Int = -1,
    val paragraphComments: List<BookHotReview> = emptyList(),
    /** 该段落的段评总数（气泡上的条数）。 */
    val paragraphCommentTotal: Int = 0,
    val paragraphCommentsLoading: Boolean = false,
    /** 段评面板的错误文案；空串表示没有错误。 */
    val paragraphCommentError: String = "",
    /** 段评翻页游标，原样回传给上游。 */
    val paragraphCommentCursor: String = "",
    val paragraphCommentsHasMore: Boolean = false,
    /** 全部书摘（跨书），书摘页用。 */
    val allMarks: List<MarkEntry> = emptyList(),
    val author: AuthorProfile? = null,
    val currentParagraph: Int = 0,
    /** 从书摘跳转时的目标章与段落；loadChapter 消费后清空。 */
    val pendingJumpChapter: String = "",
    val pendingJumpParagraph: Int = -1,
    /** 当前书上次读到的章节与段落，详情页「继续阅读」用；避免在组合期读磁盘。 */
    val resumeChapterId: String = "",
    val resumeParagraph: Int = 0,
    val audioChapters: List<Chapter> = emptyList(),
    val audioCurrent: AudioChapter? = null,
    val audioTimeline: ChapterTimeline? = null,
    val audioLoading: Boolean = false,
    val audioError: String = "",
    val audioBookId: String = "",
    val audioTones: List<AudioTone> = emptyList(),
    val audioTone: AudioTone? = null,
    /** 每次取到新的音频地址就自增：同章重播时 data class 值不变，需要它触发 effect。 */
    val audioPlayRequest: Int = 0,
    /** 听书榜（发现页「官方听书」入口进来的那页）。 */
    val audioFeedBooks: List<Book> = emptyList(),
    val audioFeedLoading: Boolean = false,
    /** 听书榜加载失败的原因；空串表示没有错误。 */
    val audioFeedError: String = "",
    /** 书架排序：recent=最近阅读, added=入架时间, title=书名, group=按分组。 */
    val shelfSort: String = "recent",
    val shelfGroupFilter: String = "",
    /**
     * 个人标记筛选：all / want / reading / done。
     *
     * 默认落在「在读」：架上的书绝大多数是在读的，进书架先看「全部」等于让每个人
     * 每次再手动点一下；想看全部仍然只有一次点击的距离。
     */
    val shelfStatusFilter: String = ShelfItem.STATUS_READING,
    /** 有新章的书目 id → 最新章数。 */
    val shelfUpdates: Map<String, Int> = emptyMap(),
    /** 查更新正在跑：既是防连点的闸，也是页头刷新图标的转圈依据。 */
    val checkingUpdates: Boolean = false,
    /** 整轮刷新（重读书架 + 查新章）是否在跑；下拉指示器与页头图标共用。 */
    val refreshingShelf: Boolean = false,
    val dramaFeed: List<DramaSeries> = emptyList(),
    val dramaFeedLoading: Boolean = false,
    /** 短剧流首次加载失败的原因；空串表示没有错误。 */
    val dramaFeedError: String = "",
    val dramaOffset: Int = 0,
    val dramaExhausted: Boolean = false,
    val dramaMoreLoading: Boolean = false,
    val currentDrama: DramaSeries? = null,
    val dramaDetailLoading: Boolean = false,
    val currentEpisode: DramaEpisode? = null,
    val currentVideoSource: DramaPlaySource? = null,
    val dramaVideoLoading: Boolean = false,
    val dramaVideoError: String = "",
    val danmakuList: List<DanmakuItem> = emptyList(),
    val danmakuLoading: Boolean = false,
    val dramaPlaying: Boolean = false,
    val dramaPlaybackPositionMs: Long = 0L,
    val dramaDurationMs: Long = 0L,
    val discoverSection: Int = 0,
    val mangaFeed: List<Book> = emptyList(),
    val mangaFeedLoading: Boolean = false,
    val mangaOffset: Int = 0,
    val mangaExhausted: Boolean = false,
    val mangaMoreLoading: Boolean = false,
    val mangaCategory: String = "全部",
    val dramaCategory: String = "全部",
    val searchTabType: Int = 3,
    val dramaResults: List<DramaSeries> = emptyList(),
    /** 当前搜索结果对应的关键词；翻页时要原样带回，不能读输入框（用户可能已经改了字） */
    val searchQuery: String = "",
    /** 下一页的 offset；0 表示只有首屏 */
    val searchOffset: Int = 0,
    /** 上游给的 passback，与 searchOffset 成对下发 */
    val searchPassback: String = "",
    val searchExhausted: Boolean = false,
    val searchMoreLoading: Boolean = false,
    /** 书库（已下载正文）全文检索的命中；与网络搜索的 [results] 互不占用。 */
    val libraryHits: List<TextHit> = emptyList(),
    val librarySearching: Boolean = false,
    val libraryDone: Boolean = false,
    val libraryScanned: Int = 0,
    val libraryTotal: Int = 0,
    val shelfMediaType: String = "all",
    val recentDramas: List<DramaSeries> = emptyList(),
    val dramaFavorites: List<DramaSeries> = emptyList(),
    // ---------------- Z-Library 电子书导入 ----------------
    /** 搜索到的电子书（EAPI 搜 / 详都不需要登录）。 */
    val zlibResults: List<ZLibraryBook> = emptyList(),
    val zlibQuery: String = "",
    val zlibLoading: Boolean = false,
    val zlibMoreLoading: Boolean = false,
    val zlibHasNext: Boolean = false,
    val zlibPage: Int = 0,
    /** 搜索/登录/导入的错误文案；空串表示没有错误。 */
    val zlibError: String = "",
    /** 本地保存的账号会话；null 表示没登录（能搜不能下）。 */
    val zlibSession: ZLibrarySession? = null,
    val zlibLoginLoading: Boolean = false,
    /** 正在导入的书 id；空串表示空闲。 */
    val zlibImporting: String = "",
)

/**
 * 把核心层透出的裸错误翻成用户能行动的说明。
 *
 * 纯函数，便于单测；未知错误原样返回，不假装知道原因。
 */
internal fun readableFailure(raw: String): String {
    val text = raw.trim()
    if (text.isEmpty()) return "内容加载失败"
    return when {
        text.contains("timeout", ignoreCase = true) ->
            "网络超时（内容域名在当前网络下不可达）"
        text.contains("dns", ignoreCase = true) ||
            text.contains("resolve", ignoreCase = true) -> "域名解析失败"
        text.contains("connection", ignoreCase = true) ||
            text.contains("connect", ignoreCase = true) -> "连接被拒绝或中断"
        else -> text
    }
}

/**
 * 弹幕发送失败的原因。
 *
 * 上游发弹幕要番茄登录态，拾页没有登录账号，所以最常见的是 103006「用户未登录」。
 * 纯函数，便于单测。
 */
internal fun danmakuSendFailure(raw: String): String {
    val reason = raw.trim()
    if (reason.isEmpty()) return "弹幕发送失败，请稍后再试。"
    if (reason.contains("未登录") || reason.contains("103006")) {
        return "弹幕发不出去：上游要求番茄登录态，拾页没有登录账号。"
    }
    return "弹幕发送失败：$reason"
}

/** 首页一个横向榜区：榜名 + 书单。 */
data class HomeBoard(val name: String, val algorithm: Int, val books: List<Book>)

class LibraryViewModel(private val api: Api, val offline: OfflineRepo) : ViewModel() {

    /** 移出书架后的撤销窗口：短到不挡手，长到够反应过来点撤销。 */
    private val UNDO_WINDOW_MS = 5_000L

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state

    val downloader = Downloader(api, offline, viewModelScope,
        onUnmetered = { api.onUnmeteredNetwork() })

    /** 当前章之前保留几章；与预取章数一起构成清理窗口。 */
    private val KEEP_BEHIND = 2

    /**
     * 阅读时悄悄补齐后面几章。手动下载在跑时它挂起等待，
     * 所以拖动下载进度和翻页预取不会互抢并发窗口。
     */
    private val prefetcher = Prefetcher(
        api, offline, viewModelScope,
        manualBusy = { downloader.state.value.running },
        onUnmetered = { api.onUnmeteredNetwork() },
        // 预取是无声的，唯一的可见反馈就是目录里的缓存圆点——每落一章刷新一次
        onSaved = { refreshCachedChapters() },
    )

    // 阅读统计：每载入一章记一次，时长按阅读停留时间结算。
    private var readingSince = 0L
    private var pendingWords = 0
    private var pendingChapters = 0

    // 今日已读秒数的内存副本：启动时读一次 stats.json，之后由 rollToday 累加、跨日归零。
    private var todayKey = GoalMath.dayKey()
    private var todayCache = 0L

    // 阅读位置的落盘节流：同章内滚动不必每滑一下都写盘。
    private var progressSavedAt = 0L

    // MutableStateFlow.update 是 CAS 循环，读改写不会互相覆盖（多入口同时刷新时尤其重要）
    private fun update(block: (UiState) -> UiState) { _state.update(block) }

    /** 由界面投递一次性提示（例如分享卡片是否成功）。 */
    fun notify(text: String) { update { it.copy(message = text) } }

    fun clearMessage() { update { it.copy(message = "") } }

    fun loadShelf() = viewModelScope.launch {
        update { it.copy(loading = true) }
        fetchShelf()
    }

    /**
     * 下拉刷新 = 「查新章」。
     *
     * 只重读书架没有意义：那是本机 `shelf.json`，同一毫秒拉两次也是同一个结果。
     * 用户拉这一下想问的是「有没有新章」，所以这里先重读一遍书架（下载/移出会改变条目），
     * 再对全部书架书跑一次更新检查，检查结果落地后才收起指示器。
     *
     * 不碰全局 [UiState.loading]：它是搜索/榜单/详情共用的，一旦置位，书架空列表会整个跳成
     * 全屏 Loading()，下拉的转圈指示器也就跟着没了。
     */
    fun refreshShelf() = viewModelScope.launch {
        if (_state.value.refreshingShelf) return@launch
        update { it.copy(refreshingShelf = true) }
        try {
            fetchShelf()
            checkUpdatesNow()
        } finally {
            update { it.copy(refreshingShelf = false, shelfRefreshDone = it.shelfRefreshDone + 1) }
        }
    }

    private suspend fun fetchShelf() {
        runCatching { api.shelf() }
            .onSuccess { items ->
                update { st ->
                    st.copy(loading = false, shelfAll = items,
                        shelf = ShelfSort.apply(
                            items, st.shelfSort, st.shelfGroupFilter, st.shelfMediaType, st.shelfStatusFilter
                        ),
                        offlineBooks = offline.offlineBooks())
                }
            }
            .onFailure { e ->
                update { it.copy(loading = false, message = e.message ?: "书架加载失败") }
            }
    }

    fun loadHot() = viewModelScope.launch {
        runCatching { api.hotWords() }.onSuccess { w -> update { it.copy(hot = w) } }
    }

    /** 首页发现流。热词失败不影响它，反之亦然。 */
    fun loadFeed() = viewModelScope.launch {
        runCatching { api.homeFeed(0) }
            .onSuccess { f ->
                android.util.Log.i("xread-feed",
                    "initial ranked=${f.ranked.size} recommended=${f.recommended.size}")
                update {
                    // 刷新必须清掉「到底了」与在途标志，否则翻到底后换一批永久失效
                    it.copy(feedRanked = f.ranked, feedRecommended = f.recommended,
                        feedOffset = 10, loading = false, feedError = "",
                        feedExhausted = false, feedMoreLoading = false)
                }
                // 榜区先后加载，任一失败不影响主推荐
                runCatching { api.newBooks() }.onSuccess { n -> update { it.copy(feedNew = n) } }
                loadBoardSections()
            }
            .onFailure { e ->
                val reason = readableFailure(e.message ?: "首页加载失败")
                update {
                    it.copy(loading = false,
                        // 空态要能说出为什么，否则用户只看到一个「取不到推荐」的哑巴页面
                        feedError = if (it.feedRanked.isEmpty() && it.feedRecommended.isEmpty())
                            reason else it.feedError,
                        message = if (it.feedRanked.isEmpty() && it.feedRecommended.isEmpty())
                            reason else it.message)
                }
            }
    }

    /**
     * 六个榜种各取一屏做首页专区（推荐/完本/巅峰/新书/漫剧/短剧）。
     * 逐个请求，失败的榜种跳过；至少取到两个才展示，避免只剩一行。
     */
    private suspend fun loadBoardSections() {
        val catalog = runCatching { api.rankCatalog() }.getOrNull() ?: return
        val boards = catalog.tabs.mapNotNull { tab ->
            runCatching { HomeBoard(tab.name, tab.algorithm, api.board(tab.algorithm)) }.getOrNull()
                ?.takeIf { it.books.isNotEmpty() }
        }
        android.util.Log.i("xread-feed", "boards=" + boards.joinToString { "${it.name}:${it.books.size}" })
        if (boards.size >= 2) update { it.copy(feedBoards = boards) }
    }

    /**
     * 追加一批推荐。已展示的书按 id 去重，避免翻页重叠把自己刷成重复卡片。
     */
    fun loadMoreFeed() {
        val current = _state.value
        if (current.feedMoreLoading || current.feedExhausted) return
        update { it.copy(feedMoreLoading = true) }
        viewModelScope.launch {
            runCatching { api.homeFeed(current.feedOffset) }
                .onSuccess { f ->
                    update { st ->
                        val seen = (st.feedRecommended + st.feedRanked).map { it.bookId }.toSet()
                        // 页内也要去重：上游同一页的推荐 cell 与排行 cell 常含同一本书，
                        // 只对「上一轮已展示」去重会让 LazyGrid 出现重复 key 直接崩溃。
                        val fresh = (f.recommended + f.ranked)
                            .distinctBy { it.bookId }
                            .filter { it.bookId !in seen }
                        android.util.Log.i("xread-feed",
                            "more offset=${st.feedOffset} fresh=${fresh.size} total=${st.feedRecommended.size + fresh.size}")
                        st.copy(
                            feedMoreLoading = false,
                            feedOffset = st.feedOffset + 10,
                            feedRecommended = st.feedRecommended + fresh,
                            feedExhausted = fresh.isEmpty(),
                            message = if (fresh.isEmpty()) "暂时没有更多了" else st.message,
                        )
                    }
                }
                .onFailure { e ->
                    update { it.copy(feedMoreLoading = false, message = e.message ?: "加载更多失败") }
                }
        }
    }

    fun refreshAll() { loadShelf(); loadHot(); loadFeed() }

    fun setDiscoverSection(section: Int) {
        update { it.copy(discoverSection = section) }
        when (section) {
            1 -> if (_state.value.mangaFeed.isEmpty()) loadMangaFeed()
            2 -> if (_state.value.dramaFeed.isEmpty()) loadDramaFeed()
        }
    }

    fun loadMangaFeed(category: String = _state.value.mangaCategory) = viewModelScope.launch {
        update {
            it.copy(
                mangaFeedLoading = true, mangaCategory = category,
                mangaOffset = 0, mangaExhausted = false, mangaMoreLoading = false,
            )
        }
        val query = if (category == "全部") "漫画" else "$category 漫画"
        runCatching { api.mangaFeed(query, 0) }
            .onSuccess { list ->
                update { it.copy(mangaFeed = list, mangaFeedLoading = false, mangaOffset = list.size, mangaExhausted = list.isEmpty()) }
            }
            .onFailure {
                update { it.copy(mangaFeedLoading = false) }
            }
    }

    /** 漫画下滑续页：先按 offset 续取，无新内容时换题材关键词继续，按 bookId 去重追加 */
    fun loadMoreManga() {
        val st = _state.value
        if (st.mangaMoreLoading || st.mangaExhausted || st.mangaFeedLoading) return
        viewModelScope.launch {
            update { it.copy(mangaMoreLoading = true) }
            val base = if (st.mangaCategory == "全部") "漫画" else "${st.mangaCategory} 漫画"
            val keywords = listOf("热血漫画", "恋爱漫画", "悬疑漫画", "玄幻漫画", "都市漫画", "古风漫画", "穿越漫画", "搞笑漫画")
            var fresh = emptyList<Book>()
            var offsetDelta = 0
            for (kw in listOf(base) + keywords.filter { it != base }) {
                val useOffset = kw == base
                val batch = runCatching { api.mangaFeed(kw, if (useOffset) st.mangaOffset else 0) }
                    .getOrNull().orEmpty()
                if (batch.isEmpty()) continue
                val seen = _state.value.mangaFeed.mapTo(mutableSetOf()) { it.bookId }
                fresh = batch.filter { seen.add(it.bookId) }
                if (fresh.isNotEmpty()) {
                    if (useOffset) offsetDelta = batch.size
                    break
                }
            }
            update { cur ->
                val seen = cur.mangaFeed.mapTo(mutableSetOf()) { it.bookId }
                val add = fresh.filter { seen.add(it.bookId) }
                cur.copy(
                    mangaFeed = cur.mangaFeed + add,
                    mangaOffset = cur.mangaOffset + offsetDelta,
                    mangaExhausted = add.isEmpty(),
                    mangaMoreLoading = false,
                )
            }
        }
    }

    fun setMangaCategory(category: String) {
        loadMangaFeed(category)
    }

    fun setDramaCategory(category: String) {
        update { it.copy(dramaCategory = category) }
    }

    // ---------- Z-Library 电子书 ----------

    /** 读一次本地保存的 z-library 会话；进页面时调，避免在 composition 里读盘。 */
    fun loadZLibrarySession() = viewModelScope.launch {
        val session = withContext(Dispatchers.IO) { offline.zlibrarySession() }
        update { it.copy(zlibSession = session) }
    }

    fun zlibraryLogin(email: String, password: String) = viewModelScope.launch {
        if (email.isBlank() || password.isBlank()) {
            update { it.copy(zlibError = "请填写 Z-Library 的邮箱和密码") }
            return@launch
        }
        update { it.copy(zlibLoginLoading = true, zlibError = "") }
        runCatching { withContext(Dispatchers.IO) { ZLibraryClient().login(email.trim(), password) } }
            .onSuccess { session ->
                withContext(Dispatchers.IO) { offline.saveZlibrarySession(session) }
                update { it.copy(zlibLoginLoading = false, zlibSession = session, message = "已登录 Z-Library") }
            }
            .onFailure { e ->
                update { it.copy(zlibLoginLoading = false, zlibError = e.message ?: "登录失败") }
            }
    }

    fun zlibraryLogout() = viewModelScope.launch {
        withContext(Dispatchers.IO) { offline.saveZlibrarySession(null) }
        update { it.copy(zlibSession = null, message = "已退出 Z-Library 账号") }
    }

    fun zlibrarySearch(query: String) = viewModelScope.launch {
        val keyword = query.trim()
        if (keyword.isEmpty()) return@launch
        update {
            it.copy(
                zlibLoading = true, zlibError = "", zlibQuery = keyword,
                zlibResults = emptyList(), zlibPage = 0, zlibHasNext = false,
            )
        }
        runCatching { withContext(Dispatchers.IO) { zlibClient().search(keyword, page = 1) } }
            .onSuccess { page ->
                update {
                    it.copy(
                        zlibLoading = false,
                        zlibResults = page.books,
                        zlibPage = page.page,
                        zlibHasNext = page.hasNext,
                    )
                }
            }
            .onFailure { e ->
                update { it.copy(zlibLoading = false, zlibResults = emptyList(), zlibError = e.message ?: "搜索失败") }
            }
    }

    fun zlibraryLoadMore() = viewModelScope.launch {
        val st = _state.value
        if (st.zlibLoading || st.zlibMoreLoading || !st.zlibHasNext || st.zlibQuery.isBlank()) return@launch
        update { it.copy(zlibMoreLoading = true) }
        runCatching { withContext(Dispatchers.IO) { zlibClient().search(st.zlibQuery, page = st.zlibPage + 1) } }
            .onSuccess { page ->
                val seen = _state.value.zlibResults.mapTo(mutableSetOf()) { it.id }
                update {
                    it.copy(
                        zlibMoreLoading = false,
                        zlibResults = it.zlibResults + page.books.filter { b -> seen.add(b.id) },
                        zlibPage = page.page,
                        zlibHasNext = page.hasNext,
                    )
                }
            }
            .onFailure { e ->
                update { it.copy(zlibMoreLoading = false, zlibHasNext = false, zlibError = e.message ?: "加载更多失败") }
            }
    }

    /**
     * 导入一本 z-library 电子书：下载 EPUB → 解包分章 → 写本地书 → 进书架。
     *
     * 下载文件本体必须带账号会话；没登录就直接提示，不发请求。
     * 只支持 EPUB：mobi/azw3 要另一套解析器，不能假装能读。
     */
    fun zlibraryImport(raw: ZLibraryBook) = viewModelScope.launch {
        if (_state.value.zlibImporting.isNotBlank() || raw.extension.lowercase() != "epub") return@launch
        val session = _state.value.zlibSession ?: withContext(Dispatchers.IO) { offline.zlibrarySession() }
        if (session == null) {
            update { it.copy(zlibError = "下载电子书要先登录 Z-Library 账号（搜索和详情不需要登录）") }
            return@launch
        }
        update { it.copy(zlibImporting = raw.id, zlibError = "") }
        runCatching {
            withContext(Dispatchers.IO) {
                val client = ZLibraryClient(session = session)
                val link = client.downloadUrl(raw.id, raw.hash)
                val bytes = client.download(link)
                ZLibraryImporter(offline, api).import(raw, bytes)
            }
        }.onSuccess { book ->
            update { it.copy(zlibImporting = "", message = "《${book.title}》已导入书架，共 ${book.chapterCount} 章") }
            refreshShelf()
        }.onFailure { e ->
            update { it.copy(zlibImporting = "", zlibError = e.message ?: "导入失败") }
        }
    }

    /**
     * 导入本机选中的 EPUB 文件（不经过 z-library，不需要账号）。
     *
     * 和从 z-library 导入走同一条落库路径，只是书号/书名从文件名兜。
     */
    fun importLocalEpub(fileName: String, bytes: ByteArray) = viewModelScope.launch {
        if (_state.value.zlibImporting.isNotBlank()) return@launch
        update { it.copy(zlibImporting = "local", zlibError = "") }
        runCatching {
            withContext(Dispatchers.IO) {
                val title = fileName.substringBeforeLast('.').ifBlank { "本地电子书" }
                val raw = ZLibraryBook(id = "", hash = title, title = title, author = "", extension = "epub")
                ZLibraryImporter(offline, api).import(raw, bytes)
            }
        }.onSuccess { book ->
            update { it.copy(zlibImporting = "", message = "《${book.title}》已导入书架，共 ${book.chapterCount} 章") }
            refreshShelf()
        }.onFailure { e ->
            update { it.copy(zlibImporting = "", zlibError = e.message ?: "导入失败") }
        }
    }

    private fun zlibClient(): ZLibraryClient = ZLibraryClient(session = _state.value.zlibSession)

    fun setSearchTabType(tabType: Int, currentQuery: String = "") {
        update { it.copy(searchTabType = tabType) }
        if (currentQuery.isNotBlank()) {
            search(currentQuery, tabType)
        }
    }

    fun search(query: String, tabType: Int = _state.value.searchTabType) = viewModelScope.launch {
        if (query.isBlank()) return@launch
        update { it.copy(loading = true, searchError = "", searchTabType = tabType) }
        // 先记历史再发请求：搜不到也是用户真实搜过的词
        val history = withContext(Dispatchers.IO) { offline.pushSearch(query) }
        update { it.copy(searchHistory = history) }
        // 上游 tab_type=11 是短剧、2 是听书。听书走通用分页路径，
        // 只有短剧的结构（video_data）要单独解析。
        if (tabType == 11) {
            runCatching { api.searchDrama(query) }
                .onSuccess { r ->
                    update {
                        it.copy(loading = false, dramaResults = r, results = emptyList(), searched = true, searchError = "",
                            searchQuery = query, searchOffset = 0, searchPassback = "", searchExhausted = true)
                    }
                }
                .onFailure { e ->
                    update {
                        it.copy(loading = false, dramaResults = emptyList(), results = emptyList(), searched = true,
                            searchError = e.message ?: "搜索短剧失败",
                            searchQuery = query, searchOffset = 0, searchPassback = "", searchExhausted = true)
                    }
                }
        } else {
            runCatching { api.searchPage(query, tabType, offset = 0) }
                .onSuccess { page ->
                    update {
                        it.copy(loading = false, results = page.books, dramaResults = emptyList(), searched = true, searchError = "",
                            searchQuery = query, searchOffset = page.nextOffset, searchPassback = page.passback,
                            searchExhausted = !page.hasMore)
                    }
                }
                .onFailure { e ->
                    update {
                        it.copy(loading = false, results = emptyList(), dramaResults = emptyList(), searched = true,
                            searchError = e.message ?: "搜索失败",
                            searchQuery = query, searchOffset = 0, searchPassback = "", searchExhausted = true)
                    }
                }
        }
    }

    /**
     * 追加下一页搜索结果。
     *
     * 上游同一本书可能跨页重复（热词权重会抖动），按 bookId 去重后再拼；
     * 如果整页都是重复的，说明 offset 没能推进，直接判为到底，免得无限拉。
     */
    fun loadMoreSearch() = viewModelScope.launch {
        val st = _state.value
        if (st.searchMoreLoading || st.searchExhausted || st.loading) return@launch
        val query = st.searchQuery
        if (query.isBlank() || st.searchTabType == 11) return@launch
        update { it.copy(searchMoreLoading = true) }
        runCatching { api.searchPage(query, st.searchTabType, offset = st.searchOffset, passback = st.searchPassback) }
            .onSuccess { page ->
                val seen = _state.value.results.mapTo(mutableSetOf()) { it.bookId }
                val fresh = page.books.filter { seen.add(it.bookId) }
                update {
                    it.copy(
                        searchMoreLoading = false,
                        results = it.results + fresh,
                        searchOffset = page.nextOffset,
                        searchPassback = page.passback,
                        searchExhausted = !page.hasMore || fresh.isEmpty(),
                    )
                }
            }
            .onFailure { e ->
                // 翻页失败不清空已有结果：读者已经看到的部分不该消失
                update { it.copy(searchMoreLoading = false, searchExhausted = true, message = e.message ?: "加载更多失败") }
            }
    }

    /**
     * 从书架点进搜索页时清掉上一次的结果。
     * 不清的话 `searched` 一直为 true，历史入口永远被旧结果挡在后面。
     */
    fun resetSearchView() {
        update {
            it.copy(results = emptyList(), dramaResults = emptyList(), searched = false, searchError = "", loading = false,
                searchQuery = "", searchOffset = 0, searchPassback = "", searchExhausted = false, searchMoreLoading = false)
        }
    }

    fun loadSearchHistory() = viewModelScope.launch {
        val list = withContext(Dispatchers.IO) { offline.searchHistory() }
        update { it.copy(searchHistory = list) }
    }

    fun removeSearchHistory(query: String) = viewModelScope.launch {
        val list = withContext(Dispatchers.IO) { offline.removeSearchHistory(query) }
        update { it.copy(searchHistory = list) }
    }

    fun clearSearchHistory() = viewModelScope.launch {
        withContext(Dispatchers.IO) { offline.clearSearchHistory() }
        update { it.copy(searchHistory = emptyList()) }
    }

    /** 榜单：六榜种 × 十六分类，目录只取一次。 */
    fun loadRankings(algorithm: Int = _state.value.rankAlgorithm, categoryId: String = _state.value.rankCategory) =
        viewModelScope.launch {
            update { it.copy(loading = true, rankAlgorithm = algorithm, rankCategory = categoryId) }
            runCatching { api.rankCatalog() }.onSuccess { c ->
                update { it.copy(rankCatalog = c) }
                if (c.tabs.none { it.algorithm == algorithm }) update { it.copy(rankAlgorithm = c.tabs.firstOrNull()?.algorithm ?: algorithm) }
            }
            val effective = _state.value.rankAlgorithm
            runCatching { api.board(effective, categoryId) }
                .onSuccess { r -> update { it.copy(loading = false, rankings = r) } }
                .onFailure { e -> update { it.copy(loading = false, rankings = emptyList(), message = e.message ?: "榜单加载失败") } }
        }

    /** 断网或服务不可达时，用本地缓存打开：章节清单与正文都取自 OfflineRepo。 */
    fun openOfflineBook(book: Book) = viewModelScope.launch {
        val chapters = offline.offlineChapters(book.bookId)
        if (chapters.isEmpty()) {
            update { it.copy(message = "该书无本地目录，请联网后重新下载") }
            return@launch
        }
        val local = offline.progress(book.bookId)
        val target = local?.first?.takeIf { id -> chapters.any { it.id == id } } ?: chapters.first().id
        // 换书必须复位段落，否则 flushStats 会把上一本书的段落号写进这本书的进度
        val paragraph = resumeParagraph(local, target)
        update {
            it.copy(loading = false, book = book, chapters = chapters, related = emptyList(),
                currentParagraph = paragraph,
                chapterIndex = chapters.indexOfFirst { c -> c.id == target }.coerceAtLeast(0))
        }
        val raw = offline.getChapter(book.bookId, target)
        if (raw == null) {
            update { it.copy(message = "该章未缓存") }
            return@launch
        }
        // 先切段再套替换规则：段数不变，段评与书签下标才不用跟着换算。
        val paras = renderable(Paragraphs.split(raw.optString("content")))
        update {
            it.copy(flow = listOf(ChapterContent(target, raw.optString("title"), paras, null, null)),
                flowPrevDone = false,
                flowIndex = 0, currentChapterId = target, message = "离线阅读")
        }
    }

    fun hasOffline(bookId: String): Boolean = offline.offlineChapters(bookId).isNotEmpty()

    /**
     * 目录下拉刷新：只重拉章节列表，不动正文，也不改写阅读位置。
     *
     * 追更的书目录会一直变长。读者在目录页拉一下就该看到新章，而不是退回详情再进来。
     * 计数 +1 而不是布尔，原因与书架下拉一致：布尔在同一帧翻转会被合帧吃掉，指示器收不回去。
     */
    fun refreshChapters() = viewModelScope.launch {
        val book = _state.value.book ?: return@launch
        val before = _state.value.chapters.size
        runCatching { api.chapters(book.source, book.bookId) }
            .onSuccess { list ->
                update {
                    it.copy(
                        chapters = list,
                        tocRefreshDone = it.tocRefreshDone + 1,
                        message = when {
                            list.size > before -> "新增 ${list.size - before} 章"
                            list.size < before -> "目录已更新：${list.size} 章"
                            else -> "目录已是最新"
                        },
                    )
                }
                refreshCachedChapters()
            }
            .onFailure { e ->
                update {
                    it.copy(tocRefreshDone = it.tocRefreshDone + 1, message = e.message ?: "刷新目录失败")
                }
            }
    }

    /**
     * 只补详情页需要、阅读流程不需要的数据。
     *
     * 从阅读页点「书籍详情」时不能走 [openBook]：那会把 flow、当前章一起清掉，
     * 返回阅读页就得从头加载。这里只刷新书籍字段与相关推荐，正文进度原样保留。
     */
    fun refreshDetail(book: Book) = viewModelScope.launch {
        runCatching {
            val fresh = book.mergeFrom(api.book(book.source, book.bookId))
            fresh to runCatching { api.related(book.source, book.bookId) }.getOrDefault(emptyList())
        }.onSuccess { (fresh, related) ->
            update { st ->
                if (st.book?.bookId != book.bookId) st
                else st.copy(book = fresh, related = related)
            }
        }
    }

    fun openBook(book: Book) = viewModelScope.launch {
        update {
            it.copy(loading = true, book = book, chapters = emptyList(), flow = emptyList(), flowPrevDone = false,
                flowIndex = 0, currentChapterId = "", related = emptyList(),
                // 切书先清掉上一本的评论快照，否则横向卡片流会短暂展示旧书短评。
                comments = emptyList(), commentTotal = 0, commentRaters = 0, commentContext = "",
                commentsLoading = false, commentsHasMore = false, commentsOffset = 0)
        }
        runCatching {
            // 上游详情接口不是每次都给全字段：第二次打开同一本书可能少回 word_number
            // 或 score，直接替换会让详情页从「320 万字 / 9.9 分」掉成「— / 空」。
            // 以列表里那份快照为底，只用详情接口真正给了的字段覆盖。
            val fresh = book.mergeFrom(
                runCatching { api.book(book.source, book.bookId) }.getOrDefault(book),
            )
            val chapterList = api.chapters(book.source, book.bookId)
            val relatedList = runCatching { api.related(book.source, book.bookId) }.getOrDefault(emptyList())
            Triple(fresh, chapterList, relatedList)
        }.onSuccess { (fresh, chapterList, relatedList) ->
            val local = withContext(Dispatchers.IO) { offline.progress(book.bookId) }
            update { st ->
                st.copy(loading = false, book = fresh, chapters = chapterList,
                    related = relatedList,
                    resumeChapterId = local?.first.orEmpty(),
                    resumeParagraph = local?.second ?: 0)
            }
            val pending = _state.value.pendingJumpChapter
            val target = pending.takeIf { id -> chapterList.any { it.id == id } }
                ?: local?.first?.takeIf { id -> chapterList.any { it.id == id } }
                ?: chapterList.firstOrNull()?.id
            if (target != null) loadChapter(target)
        }.onFailure { e ->
            if (hasOffline(book.bookId)) openOfflineBook(book)
            else {
                // 正文页靠 readerError 显示错误；只写 message 的话用户只会看到
                // 「正文未加载」，不知道是网络还是书的问题。
                val reason = readableFailure(e.message ?: "打开失败")
                update { it.copy(loading = false, message = reason, readerError = reason) }
            }
        }
    }

    /**
     * 章节请求序号。连点「下一章」时后返回的旧响应不能覆盖新章正文，
     * 也不能拿旧章的下标去落盘进度。
     */
    private val chapterTicket = java.util.concurrent.atomic.AtomicInteger(0)

    fun loadChapter(chapterId: String) {
        // 章目录都还没拿到就来重试：空编号会被当成非法参数，
        // 报「作品或章节编号格式错误」——把网络问题说成了编号问题，误导且点不动。
        if (chapterId.isBlank()) {
            update { it.copy(loading = false, readerError = "章节还没加载好，请重试") }
            return
        }
        val ticket = chapterTicket.incrementAndGet()
        viewModelScope.launch {
            val book = _state.value.book ?: return@launch
            val index = _state.value.chapters.indexOfFirst { it.id == chapterId }
            update { it.copy(loading = true, readerError = "",
                chapterIndex = if (index >= 0) index else 0) }
            runCatching { api.chapter(book.source, book.bookId, chapterId) }
                .onSuccess { c ->
                    if (ticket != chapterTicket.get()) return@onSuccess
                    // 同章重进要沿用上次段落；换章才归零，否则会把已读位置写回 0。
                    // 从书摘跳转时用显式段落覆盖，并立刻清空待跳转标记。
                    val jump = _state.value
                    val paragraph = if (jump.pendingJumpChapter == chapterId && jump.pendingJumpParagraph >= 0)
                        jump.pendingJumpParagraph
                    else resumeParagraph(offline.progress(book.bookId), chapterId)
                    // 套替换规则只在渲染侧做：离线缓存里的正文保持上游原文，
                    // 否则用户删掉规则后磁盘上的文字也回不来。
                    val rendered = c.copy(paragraphs = renderable(c.paragraphs))
                    update { it.copy(loading = false, flow = listOf(rendered), flowIndex = 0, flowPrevDone = false,
                        currentChapterId = chapterId, currentParagraph = paragraph,
                        pendingJumpChapter = "", pendingJumpParagraph = -1, readerError = "",
                        scrollRequest = it.scrollRequest + 1, scrollChapterId = chapterId,
                        scrollParagraph = paragraph)
                        .resetParagraphComments() }
                    offline.saveProgress(book.bookId, chapterId, paragraph, index, book)
                    runCatching { api.saveProgress(book.source, book.bookId, chapterId, paragraph) }
                    countReading(c.paragraphs.sumOf { it.length })
                    loadBubbles(chapterId)
                    loadMarks(chapterId)
                    afterChapter(book, index)
                }
                .onFailure { e ->
                    if (ticket != chapterTicket.get()) return@onFailure
                    val local = offline.getChapter(book.bookId, chapterId)
                    if (local != null) {
                        val images = local.optJSONArray("images")?.let { arr ->
                            (0 until arr.length()).mapNotNull { idx -> arr.optString(idx).takeIf { it.isNotBlank() } }
                        }.orEmpty()
                        val paras = Paragraphs.split(local.optString("content")).ifEmpty {
                            if (images.isNotEmpty()) listOf(local.optString("title").ifEmpty { "漫画" }) else emptyList()
                        }.let { renderable(it) }
                        val paragraph = resumeParagraph(offline.progress(book.bookId), chapterId)
                        update {
                            it.copy(loading = false, message = "离线章节", currentParagraph = paragraph,
                                flow = listOf(ChapterContent(chapterId, local.optString("title"), paras, null, null, images = images)),
                                flowPrevDone = false,
                                flowIndex = 0, currentChapterId = chapterId,
                                scrollRequest = it.scrollRequest + 1, scrollChapterId = chapterId,
                                scrollParagraph = paragraph)
                        }
                        // 离线回退说明网络本来就不通，预取没有意义；只做窗口清理
                        trimAutoWindow(book, index)
                    } else {
                        // 用 readerError 而不是 message：message 被 MainActivity 当一次性
                        // 事件消费并立刻 clearMessage，正文页拿到的永远是空串。
                        // 换章失败时上一章的段评还挂在旧下标上，必须一起清掉。
                        // 错误文案走 readableFailure：上游超时别写成编号不对。
                        val reason = readableFailure(e.message ?: "章节加载失败")
                        update { it.copy(loading = false, readerError = reason) }
                    }
                }
        }
    }

    fun nextChapter() {
        val s = _state.value
        val cur = s.flow.getOrNull(s.flowIndex)
        val id = cur?.nextId ?: s.chapters.getOrNull(s.chapterIndex + 1)?.id
        // 到头了要给反馈，否则按钮像坏了
        if (id != null) loadChapter(id) else update { it.copy(message = "已经是最后一章") }
    }

    fun prevChapter() {
        val s = _state.value
        val cur = s.flow.getOrNull(s.flowIndex)
        val id = cur?.previousId ?: s.chapters.getOrNull(s.chapterIndex - 1)?.id
        if (id != null) loadChapter(id) else update { it.copy(message = "已经是第一章") }
    }

    /**
     * 每次真正落到一章之后：补后面的缺口，再把窗口外的预取章清掉。
     *
     * 两件事必须一起做：只预取不清理，长读一本 3000 章的书会把存储吃满；
     * 只清理不预取，窗口永远凑不齐。
     */
    private fun afterChapter(book: Book, index: Int) {
        prefetcher.request(book, _state.value.chapters, index)
        trimAutoWindow(book, index)
    }

    /** 只删「预取写的」且落在窗口外的章；手动下载的章不在 auto 集合里，删不到。 */
    private fun trimAutoWindow(book: Book, index: Int) {
        val ahead = _state.value.downloadPolicy.prefetch
        if (ahead <= 0 || index < 0) return
        val chapters = _state.value.chapters
        if (chapters.isEmpty()) return
        val from = (index - KEEP_BEHIND).coerceAtLeast(0)
        val to = (index + ahead).coerceAtMost(chapters.lastIndex)
        if (to < from) return
        val keep = HashSet<String>(to - from + 1)
        for (i in from..to) keep.add(chapters[i].id)
        viewModelScope.launch(Dispatchers.IO) {
            val removed = offline.trimAutoChapters(book.bookId, keep)
            if (removed > 0) refreshCachedChapters()
        }
    }

    /**
     * 章末读不下去时的出路：把后面几章补下来，回来了就原地续接。
     *
     * 不复用 [cacheBooks]：那是「批量缓存整本」的队列，走它要等前面所有排队任务，
     * 而这里读者正停在章末，必须立刻下。
     */
    fun cacheNext(count: Int = 5) {
        val s = _state.value
        val book = s.book ?: return
        val from = s.chapterIndex + 1
        if (from <= 0) return
        viewModelScope.launch {
            downloader.policy = _state.value.downloadPolicy
            // 先把手上那本跑完再插队：startRange 在已有任务时会被直接丢掉，
            // 读者会看到「按钮点了没反应」。等它结束再下这 5 章才是真的排上了。
            downloader.awaitIdle()
            downloader.startRange(book, from, count)
            downloader.awaitIdle()
            appendNextChapter()
        }
    }

    /** 目录页「补全当前窗口」：一次补齐前 [KEEP_BEHIND] 章到后 N 章里的缺口。 */
    fun fillWindow() {
        val s = _state.value
        val book = s.book ?: return
        if (s.chapters.isEmpty()) return
        val ahead = s.downloadPolicy.prefetch
        val from = (s.chapterIndex - KEEP_BEHIND).coerceAtLeast(0)
        val to = (s.chapterIndex + ahead).coerceAtMost(s.chapters.lastIndex)
        if (to < from) return
        downloader.policy = s.downloadPolicy
        downloader.startRange(book, from, to - from + 1)
        notify("开始补齐当前窗口")
    }

    /** 书评首页。offset 分页，老接口默认就是最赞。 */
    fun loadComments(bookId: String? = _state.value.book?.bookId) {
        val id = bookId ?: return
        viewModelScope.launch {
            // 详情页卡片流与书评页共用这份结果；A 书迟到的响应不能落到已切到的 B 书上。
            update { st -> if (st.book?.bookId != id) st else st.copy(commentsLoading = true) }
            runCatching { api.bookComments(id, offset = 0, count = PAGE) }
                .onSuccess { page ->
                    update { st ->
                        if (st.book?.bookId != id) st else st.copy(
                            commentsLoading = false, comments = page.items,
                            commentTotal = page.total, commentRaters = page.raters,
                            commentContext = page.context,
                            commentsHasMore = page.hasMore, commentsOffset = page.nextOffset)
                    }
                }
                .onFailure { e ->
                    update { st ->
                        if (st.book?.bookId != id) st
                        else st.copy(commentsLoading = false, message = e.message ?: "书评加载失败")
                    }
                }
        }
    }

    fun loadMoreComments() {
        val st = _state.value
        val bookId = st.book?.bookId ?: return
        if (st.commentsLoading || !st.commentsHasMore) return
        update { cur -> if (cur.book?.bookId != bookId) cur else cur.copy(commentsLoading = true) }
        viewModelScope.launch {
            runCatching { api.bookComments(bookId, offset = st.commentsOffset, count = PAGE) }
                .onSuccess { page ->
                    val seen = st.comments.map { it.id }.toSet()
                    val fresh = page.items.filter { it.id !in seen }
                    // 偏移没推进就视为到底，避免原地反复取同一页
                    val advanced = page.nextOffset > st.commentsOffset
                    update { cur ->
                        if (cur.book?.bookId != bookId) cur else cur.copy(
                            commentsLoading = false,
                            comments = st.comments + fresh,
                            commentTotal = page.total.let { if (it > 0) it else st.commentTotal },
                            commentsHasMore = page.hasMore && advanced && fresh.isNotEmpty(),
                            commentsOffset = if (advanced) page.nextOffset else st.commentsOffset)
                    }
                }
                .onFailure { e ->
                    update { cur ->
                        if (cur.book?.bookId != bookId) cur
                        else cur.copy(commentsLoading = false, message = e.message ?: "书评加载失败")
                    }
                }
        }
    }

    /** 展开并加载评论楼中楼回复。 */
    fun loadReplies(commentId: String) {
        val st = _state.value
        val bookId = st.book?.bookId ?: return
        val target = st.comments.firstOrNull { it.id == commentId } ?: return
        if (target.repliesLoading) return
        update {
            it.copy(comments = it.comments.map { c ->
                if (c.id == commentId) c.copy(repliesLoading = true) else c
            })
        }
        viewModelScope.launch {
            runCatching {
                api.commentReplies(commentId = commentId, bookId = bookId, count = 20, cursor = "")
            }.onSuccess { page ->
                update {
                    it.copy(comments = it.comments.map { c ->
                        if (c.id == commentId) {
                            c.copy(
                                repliesLoading = false,
                                repliesLoaded = true,
                                replies = page.items,
                                repliesHasMore = page.hasMore,
                                repliesCursor = page.cursor,
                            )
                        } else c
                    })
                }
            }.onFailure { e ->
                update {
                    it.copy(
                        comments = it.comments.map { c ->
                            if (c.id == commentId) c.copy(repliesLoading = false) else c
                        },
                        message = e.message ?: "回复加载失败",
                    )
                }
            }
        }
    }

    /** 加载更多评论回复。 */
    fun loadMoreReplies(commentId: String) {
        val st = _state.value
        val bookId = st.book?.bookId ?: return
        val target = st.comments.firstOrNull { it.id == commentId } ?: return
        if (target.repliesLoading || !target.repliesHasMore) return
        update {
            it.copy(comments = it.comments.map { c ->
                if (c.id == commentId) c.copy(repliesLoading = true) else c
            })
        }
        viewModelScope.launch {
            runCatching {
                api.commentReplies(commentId = commentId, bookId = bookId, count = 20, cursor = target.repliesCursor)
            }.onSuccess { page ->
                val seen = target.replies.map { it.id }.toSet()
                val fresh = page.items.filter { it.id !in seen }
                update {
                    it.copy(comments = it.comments.map { c ->
                        if (c.id == commentId) {
                            c.copy(
                                repliesLoading = false,
                                replies = c.replies + fresh,
                                repliesHasMore = page.hasMore && fresh.isNotEmpty(),
                                repliesCursor = page.cursor,
                            )
                        } else c
                    })
                }
            }.onFailure { e ->
                update {
                    it.copy(
                        comments = it.comments.map { c ->
                            if (c.id == commentId) c.copy(repliesLoading = false) else c
                        },
                        message = e.message ?: "加载更多回复失败",
                    )
                }
            }
        }
    }

    /** 书评点赞/取消点赞。 */
    fun toggleCommentDigg(commentId: String) {
        val st = _state.value
        val bookId = st.book?.bookId ?: return
        val comment = st.comments.firstOrNull { it.id == commentId } ?: return
        val newLiked = !comment.userLiked
        val newDigg = (comment.digg + if (newLiked) 1 else -1).coerceAtLeast(0)
        update {
            it.copy(comments = it.comments.map { c ->
                if (c.id == commentId) c.copy(userLiked = newLiked, digg = newDigg) else c
            })
        }
        viewModelScope.launch {
            runCatching {
                api.diggComment(commentId = commentId, liked = newLiked, targetType = 1, bookId = bookId)
            }.onFailure {
                // 失败回滚
                update {
                    it.copy(comments = it.comments.map { c ->
                        if (c.id == commentId) c.copy(userLiked = comment.userLiked, digg = comment.digg) else c
                    })
                }
            }
        }
    }

    /** 回复点赞/取消点赞。 */
    fun toggleReplyDigg(commentId: String, replyId: String) {
        val st = _state.value
        val bookId = st.book?.bookId ?: return
        val comment = st.comments.firstOrNull { it.id == commentId } ?: return
        val reply = comment.replies.firstOrNull { it.id == replyId } ?: return
        val newLiked = !reply.userLiked
        val newDigg = (reply.digg + if (newLiked) 1 else -1).coerceAtLeast(0)
        update {
            it.copy(comments = it.comments.map { c ->
                if (c.id == commentId) {
                    c.copy(replies = c.replies.map { r ->
                        if (r.id == replyId) r.copy(userLiked = newLiked, digg = newDigg) else r
                    })
                } else c
            })
        }
        viewModelScope.launch {
            runCatching {
                api.diggComment(commentId = replyId, liked = newLiked, targetType = 2, bookId = bookId)
            }.onFailure {
                update {
                    it.copy(comments = it.comments.map { c ->
                        if (c.id == commentId) {
                            c.copy(replies = c.replies.map { r ->
                                if (r.id == replyId) r.copy(userLiked = reply.userLiked, digg = reply.digg) else r
                            })
                        } else c
                    })
                }
            }
        }
    }

    /** 段评点赞/取消点赞。 */
    fun toggleParagraphCommentDigg(reviewId: String) {
        val st = _state.value
        val bookId = st.book?.bookId ?: return
        val review = st.paragraphComments.firstOrNull { it.id == reviewId } ?: return
        val newLiked = !review.userLiked
        val newDigg = (review.digg + if (newLiked) 1 else -1).coerceAtLeast(0)
        update {
            it.copy(paragraphComments = it.paragraphComments.map { r ->
                if (r.id == reviewId) r.copy(userLiked = newLiked, digg = newDigg) else r
            })
        }
        viewModelScope.launch {
            runCatching {
                api.diggComment(commentId = reviewId, liked = newLiked, targetType = 1, bookId = bookId)
            }.onFailure {
                update {
                    it.copy(paragraphComments = it.paragraphComments.map { r ->
                        if (r.id == reviewId) r.copy(userLiked = review.userLiked, digg = review.digg) else r
                    })
                }
            }
        }
    }

    /** 分类清单；失败一次后不重试，用户可以退出去重进。 */
    fun loadCategories() {
        if (_state.value.categoriesLoading) return
        viewModelScope.launch {
            update { it.copy(categoriesLoading = true) }
            runCatching { api.categories() }
                .onSuccess { list -> update { it.copy(categoriesLoading = false, categories = list) } }
                .onFailure { e -> update { it.copy(categoriesLoading = false, message = e.message ?: "分类加载失败") } }
        }
    }

    /**
     * 听书榜（发现页「官方听书」进来的那页）。
     * 上游只给 offset=0 这一页，失败一次后不重试，用户可以退出去重进。
     */
    fun loadAudioFeed() {
        if (_state.value.audioFeedLoading) return
        viewModelScope.launch {
            update { it.copy(audioFeedLoading = true, audioFeedError = "") }
            runCatching { api.audioFeed() }
                .onSuccess { list -> update { it.copy(audioFeedLoading = false, audioFeedBooks = list) } }
                .onFailure { e ->
                    update { it.copy(audioFeedLoading = false, audioFeedError = readableFailure(e.message.orEmpty())) }
                }
        }
    }

    /**
     * 某个分类下的书：复用推荐榜（algo 101）+ 分类 id。
     * 上游的 rank_sub_info_id 只做推荐位筛选、不是严格过滤，书里会混进别的分类，
     * 所以标题写明是「推荐」，不谎称精确分类列表。
     */
    fun openCategory(category: BookCategory) {
        viewModelScope.launch {
            update {
                it.copy(categoryTitle = category.name, categoryBooks = emptyList(),
                    categoryLoading = true)
            }
            runCatching { api.board(101, category.id) }
                .onSuccess { books ->
                    update { it.copy(categoryLoading = false, categoryBooks = books) }
                }
                .onFailure { e ->
                    update { it.copy(categoryLoading = false, message = e.message ?: "分类书单加载失败") }
                }
        }
    }

    /** 段评气泡；失败静默（正文可读性优先）。 */
    fun loadBubbles(chapterId: String) = viewModelScope.launch {
        update { it.copy(bubblesByChapter = it.bubblesByChapter + (chapterId to emptyList())) }
        runCatching { api.paragraphBubbles(chapterId) }
            .onSuccess { list ->
                // 旧章的段评晚到时不能按新章的段落数归一化，否则会把新章段评串成旧章的。
                val content = _state.value.flow.firstOrNull { it.id == chapterId } ?: return@onSuccess
                // 上游键与应用段落下标差 1（上游不计标题行）；对不上就不给气泡，
                // 免得把评论挂到错的段落上。
                val mapped = ParagraphBubbleIndex.toAppIndexes(
                    bubbles = list,
                    upstreamParagraphs = content.upstreamParagraphs,
                    appParagraphs = content.paragraphs.size,
                )
                update { it.copy(bubblesByChapter = it.bubblesByChapter + (chapterId to mapped)) }
            }
            .onFailure { /* 段评缺失不影响阅读 */ }
    }

    /* ---------- 段评正文（点气泡弹出） ---------- */

    /**
     * 段评请求序号。快速切换段落时，先发出的请求可能后到，
     * 那时面板上已经是另一段的内容了，晚到的旧响应必须丢掉。
     */
    private val paragraphCommentTicket = java.util.concurrent.atomic.AtomicInteger(0)

    /**
     * 换章时重置段评面板：旧章的段评对新章没有意义，
     * 留着会让用户看到上一页的评论还挂在当前章的气泡上。
     */
    private fun UiState.resetParagraphComments(): UiState = copy(
        paragraphCommentOpen = false,
        paragraphCommentIndex = -1,
        paragraphComments = emptyList(),
        paragraphCommentTotal = 0,
        paragraphCommentError = "",
        paragraphCommentsLoading = false,
        paragraphCommentsHasMore = false,
        paragraphCommentCursor = "",
    )

    /**
     * 打开某一段的段评。[paragraph] 是**应用段落下标**。
     *
     * 段评要带章节版本（`item_version`），否则上游直接拒 103001。
     * 版本从当前章节正文取（`ChapterContent.version`），拿不到就退到目录里的
     * `Chapter.version`——两处同源同值。
     *
     * 请求参数 `para_index` 用的是上游坐标系（0-based、不计标题行），
     * 由 `ChapterContent.upstreamParagraphIndex` 换算；换算不出就只报面板错误，
     * 不发请求（宁可报「无法定位」，也不能把评论挂到错的段落上）。
     *
     * 失败只写进段评面板的错误位，**不碰正文**：读不到评论也要能继续看书。
     */
    fun openParagraphComments(chapterId: String, paragraph: Int) {
        val st = _state.value
        val book = st.book ?: return
        val content = st.flow.firstOrNull { it.id == chapterId } ?: return
        val version = content.version.takeIf { it.isNotBlank() }
            ?: st.chapters.firstOrNull { it.id == content.id }?.version.orEmpty()
        val upstreamIndex = content.upstreamParagraphIndex(paragraph)
        val ticket = paragraphCommentTicket.incrementAndGet()
        if (version.isBlank()) {
            update {
                it.copy(paragraphCommentOpen = true, paragraphCommentIndex = paragraph,
                    paragraphCommentChapter = content.id,
                    paragraphComments = emptyList(), paragraphCommentTotal = 0,
                    paragraphCommentError = "章节版本缺失，无法读取段评",
                    paragraphCommentsLoading = false, paragraphCommentsHasMore = false,
                    paragraphCommentCursor = "")
            }
            return
        }
        if (upstreamIndex < 0) {
            update {
                it.copy(paragraphCommentOpen = true, paragraphCommentIndex = paragraph,
                    paragraphCommentChapter = content.id,
                    paragraphComments = emptyList(), paragraphCommentTotal = 0,
                    paragraphCommentError = "无法定位该段的段评",
                    paragraphCommentsLoading = false, paragraphCommentsHasMore = false,
                    paragraphCommentCursor = "")
            }
            return
        }
        update {
            it.copy(paragraphCommentOpen = true, paragraphCommentIndex = paragraph,
                paragraphCommentChapter = content.id,
                paragraphComments = emptyList(), paragraphCommentTotal = 0,
                paragraphCommentError = "", paragraphCommentsLoading = true,
                paragraphCommentsHasMore = false, paragraphCommentCursor = "")
        }
        viewModelScope.launch {
            runCatching {
                api.paragraphComments(book.bookId, content.id, version, upstreamIndex, count = PAGE)
            }.onSuccess { page ->
                // 期间用户可能已经关掉面板或切到别的段落：过期响应直接丢掉
                if (ticket != paragraphCommentTicket.get()) return@onSuccess
                update {
                    it.copy(paragraphCommentsLoading = false, paragraphComments = page.items,
                        paragraphCommentTotal = page.total, paragraphCommentsHasMore = page.hasMore,
                        paragraphCommentCursor = page.cursor, paragraphCommentError = "")
                }
            }.onFailure { e ->
                if (ticket != paragraphCommentTicket.get()) return@onFailure
                update {
                    it.copy(paragraphCommentsLoading = false, paragraphCommentError =
                        e.message ?: "段评加载失败")
                }
            }
        }
    }

    fun openParagraphComments(paragraph: Int) {
        openParagraphComments(_state.value.currentChapterId, paragraph)
    }

    /**
     * 段评翻页。
     *
     * cursor 是上游给的数字字符串，**原样回传**；上游没给更多就到此为止。
     * 翻页失败同样只写面板的错误位，不影响正文与已加载的段评。
     */
    fun loadMoreParagraphComments() {
        val st = _state.value
        if (!st.paragraphCommentOpen || st.paragraphCommentsLoading) return
        if (!st.paragraphCommentsHasMore) return
        val book = st.book ?: return
        val chapId = st.paragraphCommentChapter.ifEmpty { st.currentChapterId }
        val content = st.flow.firstOrNull { it.id == chapId } ?: return
        val paragraph = st.paragraphCommentIndex
        val version = content.version.takeIf { it.isNotBlank() }
            ?: st.chapters.firstOrNull { it.id == content.id }?.version.orEmpty()
        if (version.isBlank()) {
            update { it.copy(paragraphCommentError = "章节版本缺失，无法读取段评") }
            return
        }
        val upstreamIndex = content.upstreamParagraphIndex(paragraph)
        if (upstreamIndex < 0) {
            update { it.copy(paragraphCommentError = "无法定位该段的段评") }
            return
        }
        val cursor = st.paragraphCommentCursor
        val ticket = paragraphCommentTicket.incrementAndGet()
        update { it.copy(paragraphCommentsLoading = true) }
        viewModelScope.launch {
            runCatching {
                api.paragraphComments(book.bookId, content.id, version, upstreamIndex,
                    count = PAGE, cursor = cursor)
            }.onSuccess { page ->
                if (ticket != paragraphCommentTicket.get()) return@onSuccess
                val seen = st.paragraphComments.map { it.id }.toSet()
                val fresh = page.items.filter { it.id !in seen }
                update {
                    it.copy(paragraphCommentsLoading = false,
                        paragraphComments = st.paragraphComments + fresh,
                        paragraphCommentTotal = page.total.let { if (it > 0) it else st.paragraphCommentTotal },
                        paragraphCommentsHasMore = page.hasMore,
                        paragraphCommentCursor = page.cursor, paragraphCommentError = "")
                }
            }.onFailure { e ->
                if (ticket != paragraphCommentTicket.get()) return@onFailure
                update {
                    it.copy(paragraphCommentsLoading = false,
                        paragraphCommentError = e.message ?: "段评加载失败")
                }
            }
        }
    }

    /** 关闭段评面板；顺带清掉列表，免得下次打开闪出上一次的旧内容。 */
    fun closeParagraphComments() {
        // 作废在途请求：关掉之后回来的响应不该再把面板填上
        paragraphCommentTicket.incrementAndGet()
        update {
            it.copy(paragraphCommentOpen = false, paragraphCommentIndex = -1,
                paragraphComments = emptyList(), paragraphCommentTotal = 0,
                paragraphCommentError = "", paragraphCommentsLoading = false,
                paragraphCommentsHasMore = false, paragraphCommentCursor = "")
        }
    }

    /* ---------- 书签与书摘 ---------- */

    fun loadMarks(chapterId: String) = viewModelScope.launch {
        val bookId = _state.value.book?.bookId ?: return@launch
        val list = withContext(Dispatchers.IO) { offline.marks(bookId, chapterId) }
        update { it.copy(marksByChapter = it.marksByChapter + (chapterId to list)) }
    }

    /** 切换高亮/书签。同一段落同一类型再点一次即取消。 */
    fun toggleMark(chapterId: String, paragraph: Int, text: String, kind: String) = viewModelScope.launch {
        val st = _state.value
        val bookId = st.book?.bookId ?: return@launch
        val content = st.flow.firstOrNull { it.id == chapterId } ?: return@launch
        val existing = st.marksByChapter[content.id]
            ?.firstOrNull { it.paragraph == paragraph && it.kind == kind }
        val ok = withContext(Dispatchers.IO) {
            if (existing != null) offline.removeMark(bookId, content.id, paragraph, kind)
            else offline.addMark(bookId, content.id, content.title,
                Mark(paragraph, text.take(500), kind, System.currentTimeMillis()))
        }
        if (!ok) {
            update { it.copy(message = "标记写入失败") }
            return@launch
        }
        val list = withContext(Dispatchers.IO) { offline.marks(bookId, content.id) }
        update {
            it.copy(marksByChapter = it.marksByChapter + (content.id to list),
                message = if (existing != null) "已取消" else if (kind == Mark.KIND_BOOKMARK) "已加书签" else "已高亮")
        }
    }

    fun toggleMark(paragraph: Int, text: String, kind: String) {
        toggleMark(_state.value.currentChapterId, paragraph, text, kind)
    }

    fun loadAllMarks() = viewModelScope.launch {
        val list = withContext(Dispatchers.IO) { offline.allMarks() }
        update { it.copy(allMarks = list) }
    }

    /**
     * 保存一条书摘的批注与标签。
     *
     * 页面里是 300ms 防抖后实时落盘，所以这里不弹「已保存」提示——
     * 边写边提示会把 Snackbar 变成噪音。真正失败才说话。
     */
    fun saveMarkNote(entry: MarkEntry, note: String, tags: List<String>) = viewModelScope.launch {
        val ok = withContext(Dispatchers.IO) {
            offline.setMarkNote(entry.bookId, entry.chapterId, entry.mark.paragraph, entry.mark.kind, note, tags)
        }
        if (!ok) {
            notify("这条书摘已不在了，批注没保存")
            return@launch
        }
        loadAllMarks()
        val chapterId = _state.value.currentChapterId
        if (chapterId.isNotEmpty() && _state.value.book?.bookId == entry.bookId &&
            chapterId == entry.chapterId
        ) loadMarks(chapterId)
    }

    fun deleteMark(entry: MarkEntry) = viewModelScope.launch {
        withContext(Dispatchers.IO) {
            offline.removeMark(entry.bookId, entry.chapterId, entry.mark.paragraph, entry.mark.kind)
        }
        loadAllMarks()
        val chapterId = _state.value.currentChapterId
        if (chapterId.isNotEmpty() && _state.value.book?.bookId == entry.bookId &&
            chapterId == entry.chapterId) loadMarks(chapterId)
    }

    /** 取书籍分享文案；回调在主线程，便于直接发 Intent。 */
    fun loadShare(bookId: String, onReady: (ShareInfo) -> Unit) = viewModelScope.launch {
        runCatching { api.share(bookId) }
            .onSuccess(onReady)
            .onFailure { e -> update { it.copy(message = e.message ?: "分享文案加载失败") } }
    }

    /** 整本导出：把离线章节拼成 TXT 放到外部目录。 */
    fun exportBookText(book: Book) = viewModelScope.launch {
        val file = withContext(Dispatchers.IO) { offline.exportBookText(book.bookId) }
        update {
            it.copy(message = if (file == null) "这本书还没有缓存正文，先在详情页下载"
                             else "已导出 ${file.name}（${file.length() / 1024} KB）")
        }
    }

    fun exportMarks() = viewModelScope.launch {
        val titles = _state.value.offlineBooks.associate { it.bookId to it.title } +
                listOfNotNull(_state.value.book?.let { it.bookId to it.title })
        val file = withContext(Dispatchers.IO) { offline.exportMarks(titles) }
        update {
            it.copy(message = when {
                file == null && offline.allMarks().isEmpty() -> "还没有书摘"
                file == null -> "书摘导出失败"
                else -> "书摘已导出：${file.name}"
            })
        }
    }

    /**
     * 从书摘跳到原文。用 pendingJump 传给 loadChapter，而不是在加载后再改
     * currentParagraph —— 阅读页的定位 effect 以 content.id 为 key，加载完再改已经错过时机。
     */
    /**
     * 在已下载的正文里找。空词直接清空，不产出任何一轮扫描。
     */
    fun searchLibrary(query: String) {
        libraryJob?.cancel()
        val q = query.trim()
        if (q.isEmpty()) {
            update {
                it.copy(
                    libraryHits = emptyList(), librarySearching = false, libraryDone = false,
                    libraryScanned = 0, libraryTotal = 0,
                )
            }
            return
        }
        update {
            it.copy(
                libraryHits = emptyList(), librarySearching = true, libraryDone = false,
                libraryScanned = 0, libraryTotal = 0,
            )
        }
        libraryJob = viewModelScope.launch {
            FullTextSearch.search(offline, q).collect { p ->
                update {
                    it.copy(
                        libraryHits = p.hits,
                        libraryScanned = p.scanned,
                        libraryTotal = p.total,
                        librarySearching = !p.done,
                        libraryDone = p.done,
                    )
                }
            }
        }
    }

    fun cancelLibrarySearch() {
        libraryJob?.cancel()
        libraryJob = null
        update { it.copy(librarySearching = false) }
    }

    /** 命中项跳转：与书摘同一条路径，保证落点、段落定位、返回行为完全一致。 */
    fun openTextHit(hit: TextHit, onReady: () -> Unit = {}) = viewModelScope.launch {
        update { it.copy(pendingJumpChapter = hit.chapterId, pendingJumpParagraph = hit.paragraphIndex) }
        val current = _state.value.book
        if (current != null && current.bookId == hit.bookId && _state.value.chapters.any { it.id == hit.chapterId }) {
            loadChapter(hit.chapterId)
        } else {
            val known = _state.value.offlineBooks.firstOrNull { it.bookId == hit.bookId }
            openBook(known ?: Book(bookId = hit.bookId, title = hit.bookTitle))
        }
        onReady()
    }

    fun openMark(entry: MarkEntry, onReady: () -> Unit = {}) = viewModelScope.launch {
        update { it.copy(pendingJumpChapter = entry.chapterId, pendingJumpParagraph = entry.mark.paragraph) }
        val current = _state.value.book
        if (current != null && current.bookId == entry.bookId && _state.value.chapters.any { it.id == entry.chapterId }) {
            loadChapter(entry.chapterId)
        } else {
            val known = _state.value.offlineBooks.firstOrNull { it.bookId == entry.bookId }
            openBook(known ?: Book(bookId = entry.bookId, title = entry.chapterTitle))
        }
        onReady()
    }

    fun loadAuthor(authorId: String) = viewModelScope.launch {
        // 必须清掉上一个作者：否则切作者时先渲染上一位的头像与简介
        update { it.copy(loading = true, author = null) }
        runCatching { api.author(authorId) }
            .onSuccess { a -> update { it.copy(loading = false, author = a) } }
            .onFailure { e -> update { it.copy(loading = false, message = e.message ?: "作者信息加载失败") } }
    }

    /**
     * 阅读状态自动推进：进正文把「想读」变「在读」，读到末章最后一段变「读完」。
     *
     * 手标「读完」的书重读不降级（规则见 [autoShelfStatus]）；不在书架上的书直接跳过，
     * 免得为了读一本试读本先把它塞进书架。
     */
    private fun advanceShelfStatus(bookId: String, atEnd: Boolean) {
        val st = _state.value
        val item = st.shelfAll.firstOrNull { it.book.bookId == bookId } ?: return
        val next = autoShelfStatus(item.status, atEnd) ?: return
        if (!api.setBookStatusNow(bookId, next)) return
        val items = st.shelfAll.map { if (it.book.bookId == bookId) it.copy(status = next) else it }
        update { s ->
            s.copy(
                shelfAll = items,
                shelf = ShelfSort.apply(items, s.shelfSort, s.shelfGroupFilter, s.shelfMediaType, s.shelfStatusFilter),
            )
        }
    }

    /** 阅读位置：记录章节与段落；跨章立刻落盘，同章内按间隔节流落盘。 */
    fun setParagraph(chapterId: String, index: Int) {
        val s = _state.value
        val chIndex = s.chapters.indexOfFirst { it.id == chapterId }.takeIf { it >= 0 } ?: s.chapterIndex
        // 书架标记要先算：刚进正文时报的就是已保存的段落，会走下面的提前返回。
        // 也放在节流判断之前——同一章里一路读到底同样要能触发。
        s.book?.let { b ->
            val lastParagraph = s.flow.lastOrNull { it.id == chapterId }?.paragraphs?.lastIndex ?: -1
            val atEnd = s.chapters.isNotEmpty() && chIndex >= s.chapters.size - 1 &&
                lastParagraph >= 0 && index >= lastParagraph
            advanceShelfStatus(b.bookId, atEnd)
        }
        if (s.currentChapterId == chapterId && s.currentParagraph == index) return
        val chapterChanged = s.currentChapterId.isNotEmpty() && s.currentChapterId != chapterId
        val fIndex = s.flow.indexOfFirst { it.id == chapterId }.takeIf { it >= 0 } ?: s.flowIndex
        update {
            it.copy(
                currentChapterId = chapterId,
                currentParagraph = index,
                chapterIndex = chIndex,
                flowIndex = fIndex
            )
        }
        // 换章必须立刻写：否则「读到第 10 章退出」会把位置留在旧章。
        // 同章内滚动也写，但要节流——读者停下来退出时由 flushStats 兜底再写一次。
        val now = System.currentTimeMillis()
        if (!progressDue(chapterChanged, progressSavedAt, now)) return
        progressSavedAt = now
        s.book?.let { b ->
            offline.saveProgress(b.bookId, chapterId, index, chIndex, b)
            if (chapterChanged) {
                viewModelScope.launch { runCatching { api.saveProgress(b.source, b.bookId, chapterId, index) } }
            }
        }
    }

    fun setParagraph(index: Int) {
        setParagraph(_state.value.currentChapterId, index)
    }

    /**
     * 正文落地到界面前的最后一道：逐段套用「正文替换」规则。
     *
     * 必须放在这里而不是 [Api.chapter]，因为下载器也走那个方法，会把替换后的
     * 文本写进离线缓存——用户之后删掉规则，磁盘里的正文也回不来了。
     * 逐段套用不改变段数，段评下标与书签段落号照旧对齐上游。
     */
    private fun renderable(paras: List<String>): List<String> = offline.applyRules(paras)

    /**
     * 取一章正文：本地有缓存先读本地，段落为空再回落网络。
     *
     * 离线缓存只存正文，不带上下章 id；连续阅读要靠 [ChapterContent.previousId]
     * 往回接、[ChapterContent.nextId] 往后接，所以这里按目录补齐。目录里找不到
     * （老数据、章节被删）时才用调用方给的兜底值——流里相邻的那一章。
     */
    private suspend fun chapterContent(
        book: Book,
        chapterId: String,
        fallbackPrevId: String?,
        fallbackNextId: String?,
    ): ChapterContent {
        val local = offline.getChapter(book.bookId, chapterId)
        if (local != null) {
            val images = local.optJSONArray("images")?.let { arr ->
                (0 until arr.length()).mapNotNull { idx -> arr.optString(idx).takeIf { it.isNotBlank() } }
            }.orEmpty()
            val paras = renderable(Paragraphs.split(local.optString("content")).ifEmpty {
                if (images.isNotEmpty()) listOf(local.optString("title").ifEmpty { "漫画" }) else emptyList()
            })
            if (paras.isNotEmpty() || images.isNotEmpty()) {
                val at = _state.value.chapters.indexOfFirst { it.id == chapterId }
                val chapters = _state.value.chapters
                return ChapterContent(
                    chapterId,
                    local.optString("title") ?: "",
                    paras,
                    chapters.getOrNull(at - 1)?.id ?: fallbackPrevId,
                    chapters.getOrNull(at + 1)?.id ?: fallbackNextId,
                    images = images,
                )
            }
        }
        val online = api.chapter(book.source, book.bookId, chapterId)
        return online.copy(paragraphs = renderable(online.paragraphs))
    }

    /** 跨章连续阅读：在流末尾追加下一章。 */
    fun appendNextChapter() {
        val s = _state.value
        if (s.flowLoading) return
        val last = s.flow.lastOrNull() ?: return
        val nextId = last.nextId ?: run {
            val idx = s.chapters.indexOfFirst { it.id == last.id }
            if (idx >= 0 && idx + 1 < s.chapters.size) s.chapters[idx + 1].id else null
        } ?: return
        if (s.flow.any { it.id == nextId }) return
        val book = s.book ?: return

        update { it.copy(flowLoading = true, flowError = "") }
        viewModelScope.launch {
            val ticket = chapterTicket.get()
            runCatching { chapterContent(book, nextId, fallbackPrevId = last.id, fallbackNextId = null) }
                .onSuccess { c ->
                    if (ticket != chapterTicket.get()) return@onSuccess
                    update {
                        if (it.flow.any { existing -> existing.id == c.id }) {
                            it.copy(flowLoading = false)
                        } else {
                            it.copy(flowLoading = false, flow = it.flow + c)
                        }
                    }
                    loadBubbles(c.id)
                    loadMarks(c.id)
                    // 续接到新的一章：缺口从新位置重算，否则预取永远停在打开时那一屏
                    val at = _state.value.chapters.indexOfFirst { it.id == c.id }
                    if (at >= 0) afterChapter(book, at)
                }
                .onFailure { e ->
                    if (ticket != chapterTicket.get()) return@onFailure
                    update { it.copy(flowLoading = false, flowError = e.message ?: "下一章加载失败") }
                }
        }
    }

    /**
     * 跨章连续阅读：在流头部接上一章。
     *
     * 和 [appendNextChapter] 是一对。没有它时，流的第一章永远是打开时那一章，
     * 读者从第 2 章往下能丝滑读到第 3 章，往回滚到第 2 章开头却什么都没有——
     * 只能点「上一章」，而那会清屏重载。接上去之后，向上滚就是同一条书流。
     */
    fun prependPreviousChapter() {
        val s = _state.value
        if (s.flowLoadingPrev || s.flowPrevDone) return
        val first = s.flow.firstOrNull() ?: return
        val prevId = previousChapterId(first, s.chapters)
        val book = s.book
        if (prevId == null || book == null || s.flow.any { it.id == prevId }) {
            update { it.copy(flowPrevDone = true) }
            return
        }

        update { it.copy(flowLoadingPrev = true) }
        viewModelScope.launch {
            val ticket = chapterTicket.get()
            runCatching { chapterContent(book, prevId, fallbackPrevId = null, fallbackNextId = first.id) }
                .onSuccess { c ->
                    // 期间读者跳章了：接上来的这章不属于新流，只把加载态放下
                    if (ticket != chapterTicket.get()) {
                        update { it.copy(flowLoadingPrev = false) }
                        return@onSuccess
                    }
                    update {
                        if (it.flow.any { existing -> existing.id == c.id }) {
                            it.copy(flowLoadingPrev = false, flowPrevDone = true)
                        } else {
                            it.copy(
                                flowLoadingPrev = false,
                                flow = listOf(c) + it.flow,
                                // 前面插了一章，当前章节的位置整体后移一格
                                flowIndex = it.flowIndex + 1,
                            )
                        }
                    }
                    loadBubbles(c.id)
                    loadMarks(c.id)
                    val at = _state.value.chapters.indexOfFirst { it.id == c.id }
                    if (at >= 0) trimAutoWindow(book, at)
                }
                .onFailure {
                    if (ticket != chapterTicket.get()) {
                        update { it.copy(flowLoadingPrev = false) }
                        return@onFailure
                    }
                    // 失败也要停手：不停的话滚到顶会反复重试，网络一抖就一直打
                    update { it.copy(flowLoadingPrev = false, flowPrevDone = true, message = "上一章加载失败") }
                }
        }
    }

    /**
     * 冷启动要恢复的那本书：进度文件里 `at` 最新的一条，且不超过 [SESSION_RESTORE_TTL_MS]。
     *
     * 书目本身还在本地 bookcase 里才返回——书架删掉的书不该把读者拽进阅读页。
     */
    suspend fun lastSessionBook(now: Long = System.currentTimeMillis()): Book? {
        val (saved, at) = offline.lastSession() ?: return null
        if (!shouldRestoreSession(at, now)) return null
        // 在架书目优先：它有最新的章节数等元信息；不在架（从发现/榜单点进去读过）时
        // 用进度记录里存的副本，书名封面都在，照旧能进阅读页。
        val onShelf = runCatching { api.shelf() }.getOrDefault(emptyList())
            .firstOrNull { it.book.bookId == saved.bookId }?.book
        return onShelf ?: saved.takeIf { it.title.isNotEmpty() }
    }

    companion object {
        /** 书评每页条数：上游对 count 上限敏感，20 与既有翻页手感一致。 */
        private const val PAGE = 20

        /**
         * 同章内滚动的落盘节流间隔（毫秒）。
         *
         * 换章必须立刻写，这里管的是「同一章内挪了几个段落」——每滑一下都写盘
         * 既浪费 IO 又没必要（读者滑到底后再退出，位置由 flushStats 兜底）。
         */
        private const val PROGRESS_SAVE_INTERVAL_MS = 1_500L

        /**
         * 这次位置变化要不要落盘。纯函数，便于单测。
         *
         * 换章一律写；同章内按 [PROGRESS_SAVE_INTERVAL_MS] 节流。
         * `lastSavedAt <= 0` 视为「从没写过」，必须写——否则刚进书的第一次滚动
         * 会被节流挡掉，退出后位置还是 0。
         */
        fun progressDue(chapterChanged: Boolean, lastSavedAt: Long, now: Long): Boolean =
            chapterChanged || lastSavedAt <= 0L || now - lastSavedAt >= PROGRESS_SAVE_INTERVAL_MS

        /**
         * 冷启动自动回到上次阅读位置的时限：超过一天就不再打扰，直接停在书架。
         *
         * 再久之前的「上次在读」多半是另一本书/另一段时期的上下文，直接跳进去反而突然。
         */
        const val SESSION_RESTORE_TTL_MS = 24 * 60 * 60 * 1000L

        /** 这条进度还值不值得冷启动时自动恢复。纯函数，便于单测。 */
        fun shouldRestoreSession(lastAt: Long, now: Long): Boolean =
            lastAt > 0L && now >= lastAt && now - lastAt <= SESSION_RESTORE_TTL_MS

        /**
         * 同一章重进沿用上次段落；换章归零。纯函数，便于单测。
         */
        fun resumeParagraph(saved: Pair<String, Int>?, chapterId: String): Int =
            if (saved != null && saved.first == chapterId) saved.second.coerceAtLeast(0) else 0

        /**
         * 流头部那一章的上一章 id：先信章节自带的 `previousId`，
         * 再退回目录里它前面的那一本；都在最前面就返回 null。
         *
         * 退路是必需的：离线读出来的章没有 `previousId`（缓存里只有正文），
         * 只认它的话向上连续阅读对下载过的书直接失效。
         */
        fun previousChapterId(head: ChapterContent?, chapters: List<Chapter>): String? {
            head ?: return null
            head.previousId?.let { return it }
            val at = chapters.indexOfFirst { it.id == head.id }
            return if (at > 0) chapters[at - 1].id else null
        }
    }

    /** 打开听书：取音频目录（章节清单来自普通目录，音频地址单独按章取）。 */
    /**
     * 打开听书：小说 id → tones（取 abook_id）→ 有声目录。
     * 直接用小说 id 打音频接口会返回 NO_THIS_TONE，故这里必须两步。
     */
    fun openAudio(book: Book) = viewModelScope.launch {
        // 回到同一本书的听书页且正在播：保留当前章节，不要从第一集重播。
        val current = _state.value
        val sameBook = current.audioBookId == book.bookId && current.audioCurrent != null
        val playingId = AudioPlaybackHolder.controller().state.value.chapterId
        val keepPlaying = sameBook && playingId.isNotEmpty()
        update {
            it.copy(audioLoading = true, audioError = "", audioBookId = book.bookId,
                audioChapters = if (keepPlaying) it.audioChapters else emptyList(),
                audioCurrent = if (keepPlaying) it.audioCurrent else null,
                audioTone = if (keepPlaying) it.audioTone else null)
        }
        val tones = runCatching { api.audioTones(book.bookId) }.getOrElse { e ->
            update { it.copy(audioLoading = false, audioError = e.message ?: "该作品没有有声版本") }
            return@launch
        }
        if (tones.isEmpty()) {
            update { it.copy(audioLoading = false, audioError = "该作品暂无可收听的有声版本") }
            return@launch
        }
        // 已有的音色选择要保留（用户可能选了第二个主播）
        val chosen = tones.firstOrNull { it.abookId == _state.value.audioTone?.abookId } ?: tones.first()
        update { it.copy(audioTones = tones, audioTone = chosen) }
        runCatching { api.audioChapters(book.bookId, chosen.abookId) }
            .onSuccess { list ->
                update { it.copy(audioLoading = false, audioChapters = list) }
                // 正在播时不重新起播，否则进度与播放位置都会丢
                if (!keepPlaying) list.firstOrNull()?.let { playAudio(it) }
            }
            .onFailure { e -> update { it.copy(audioLoading = false, audioError = e.message ?: "有声目录不可用") } }
    }

    fun selectTone(tone: AudioTone) = viewModelScope.launch {
        val bookId = _state.value.audioBookId
        if (bookId.isEmpty()) return@launch
        update { it.copy(audioTone = tone, audioLoading = true, audioError = "", audioCurrent = null) }
        runCatching { api.audioChapters(bookId, tone.abookId) }
            .onSuccess { list ->
                update { it.copy(audioLoading = false, audioChapters = list) }
                list.firstOrNull()?.let { playAudio(it) }
            }
            .onFailure { e -> update { it.copy(audioLoading = false, audioError = e.message ?: "有声目录不可用") } }
    }

    fun playAudio(chapter: Chapter) = viewModelScope.launch {
        val abookId = _state.value.audioTone?.abookId ?: return@launch
        // 请求序号：快速点 A、B 两章时，先发的 A 晚到会把 B 顶掉（loadChapter 已有同款对照）。
        val ticket = audioTicket.incrementAndGet()
        lastAudioChapter = chapter
        update { it.copy(audioLoading = true, audioError = "", audioTimeline = null) }

        // 异步并发拉取逐句时间轴字幕
        launch {
            val tl = runCatching { api.timeline(chapter.id, abookId) }.getOrNull()
            if (ticket == audioTicket.get()) {
                update { it.copy(audioTimeline = tl) }
            }
        }

        runCatching { api.audioChapter(abookId, chapter.id, chapter.title) }
            .onSuccess { a ->
                if (ticket != audioTicket.get()) return@onSuccess
                update { it.copy(audioLoading = false, audioCurrent = a,
                    audioPlayRequest = it.audioPlayRequest + 1) }
            }
            .onFailure { e ->
                if (ticket != audioTicket.get()) return@onFailure
                update { it.copy(audioLoading = false, audioError = e.message ?: "音频地址不可用",
                    audioCurrent = null) }
            }
    }

    /** 批量缓存的待办队列与「是否已有泵在工作」；队列本身加锁访问。 */
    private val cacheQueue = ArrayDeque<Book>()
    private var queuePumping = false
    /** 撤销移出的倒计时任务；新的批量移出会顶掉旧的。 */
    private var undoJob: kotlinx.coroutines.Job? = null

    /** 书库全文检索任务：换词就取消上一轮，否则旧结果会盖住新结果。 */
    private var libraryJob: kotlinx.coroutines.Job? = null

    private val audioTicket = java.util.concurrent.atomic.AtomicInteger()

    /** 记住上次尝试的章节：失败时 audioCurrent 被置 null，重试不能依赖它。 */
    private var lastAudioChapter: Chapter? = null

    fun retryAudio() {
        val chapter = lastAudioChapter ?: _state.value.audioChapters.firstOrNull() ?: return
        playAudio(chapter)
    }

    fun addToShelf(book: Book) = viewModelScope.launch {
        runCatching { api.addToShelf(book.source, book.bookId) }
            .onSuccess { update { st -> st.copy(message = "已加入书架") }; loadShelf() }
            .onFailure { e -> update { it.copy(message = e.message ?: "加入失败") } }
    }

    fun removeFromShelf(book: Book) = viewModelScope.launch {
        runCatching { api.removeFromShelf(book.source, book.bookId) }
            .onSuccess { loadShelf(); update { it.copy(message = "已移出书架") } }
            .onFailure { e -> update { it.copy(message = e.message ?: "移除失败") } }
    }

    /* ---------- 书架批量操作 ----------
     * 全部落在本地 shelf.json，逐本调用也是一次文件重写，不值得再造批处理格式；
     * 但界面不再需要用户一本本点。
     */

    /** 批量移动分组；空串表示移出分组。 */
    fun moveBooksToGroup(books: List<Book>, group: String) = viewModelScope.launch {
        if (books.isEmpty()) return@launch
        var failed = 0
        for (b in books) {
            if (runCatching { api.setGroup(b.bookId, group) }.isFailure) failed++
        }
        loadShelf()
        notify(
            when {
                failed == 0 && group.isBlank() -> "已把 ${books.size} 本移出分组"
                failed == 0 -> "已把 ${books.size} 本归入「$group」"
                else -> "分组完成 ${books.size - failed} 本，失败 $failed 本"
            }
        )
    }

    /** 批量标记读完：把进度下标推到最后一章，正文位置保持不动。 */
    fun markBooksFinished(books: List<Book>) = viewModelScope.launch {
        if (books.isEmpty()) return@launch
        var marked = 0
        withContext(Dispatchers.IO) {
            for (b in books) {
                // 批量「读完」只推进度的话，筛选「读完」抓不到这批书——状态一起落。
                api.setBookStatusNow(b.bookId, ShelfItem.STATUS_DONE)
                val total = b.chapterCount
                if (total <= 0) continue
                val current = offline.progress(b.bookId)
                val ok = offline.saveProgress(
                    bookId = b.bookId,
                    chapterId = current?.first.orEmpty(),
                    offset = current?.second ?: 0,
                    chapterIndex = total - 1,
                    book = b,
                )
                if (ok) marked++
            }
        }
        loadShelf()
        notify(if (marked > 0) "已标记 $marked 本为读完" else "这些书没有章节数，无法标记读完")
    }

    /** 批量移出书架；留下撤销窗口，避免误触清空整组。 */
    fun removeBooksFromShelf(books: List<Book>) = viewModelScope.launch {
        if (books.isEmpty()) return@launch
        val snapshot = books.mapNotNull { b ->
            _state.value.shelfAll.firstOrNull { it.book.bookId == b.bookId }
        }
        var failed = 0
        withContext(Dispatchers.IO) {
            for (item in snapshot) {
                if (runCatching { api.removeFromShelf(item.book.source, item.book.bookId) }.isFailure) failed++
            }
        }
        update { it.copy(undoRemove = snapshot) }
        loadShelf()
        // 成功时不再发 toast：界面会用带「撤销」的 Snackbar 说同一句话，双份提示很吵。
        if (failed > 0) notify("移出 ${snapshot.size - failed} 本，失败 $failed 本")
        undoJob?.cancel()
        undoJob = viewModelScope.launch {
            delay(UNDO_WINDOW_MS)
            update { if (it.undoRemove === snapshot) it.copy(undoRemove = emptyList()) else it }
        }
    }

    /** 撤销上一次批量移出。 */
    fun undoRemoveBooks() = viewModelScope.launch {
        val items = _state.value.undoRemove
        if (items.isEmpty()) return@launch
        undoJob?.cancel()
        var failed = 0
        withContext(Dispatchers.IO) {
            for (item in items) {
                if (runCatching { api.restoreToShelf(item) }.isFailure) failed++
            }
        }
        update { it.copy(undoRemove = emptyList()) }
        loadShelf()
        notify(if (failed == 0) "已恢复 ${items.size} 本" else "恢复 ${items.size - failed} 本，失败 $failed 本")
    }

    /**
     * 批量缓存全书。
     *
     * 下载器同一时刻只跑一本（共享 AIMD 窗口与任务记录），所以这里维护一条队列
     * 顺序推进；上一本结束时才起下一本，不并发抢带宽。
     */
    fun cacheBooks(books: List<Book>) {
        if (books.isEmpty()) return
        // 正在读的那本插到队首：用户点「缓存」通常就是想让手上这本先能离线读，
        // 先来先得会让它排在七本无关的书后面。
        val current = _state.value.book?.bookId
        val ordered = if (current == null) books
        else books.filter { it.bookId == current } + books.filter { it.bookId != current }
        synchronized(cacheQueue) { cacheQueue.addAll(ordered) }
        val pending = synchronized(cacheQueue) { cacheQueue.size }
        update { it.copy(cacheQueueSize = pending) }
        notify("已加入缓存队列：${books.size} 本")
        if (queuePumping) return
        queuePumping = true
        viewModelScope.launch {
            var finished = 0
            while (true) {
                val next = synchronized(cacheQueue) { cacheQueue.removeFirstOrNull() } ?: break
                update { it.copy(cacheQueueSize = synchronized(cacheQueue) { cacheQueue.size }) }
                downloader.policy = _state.value.downloadPolicy
                downloader.awaitIdle()
                downloader.start(next, 0)
                finished++
            }
            queuePumping = false
            update { it.copy(cacheQueueSize = 0) }
            notify("批量缓存完成：$finished 本")
        }
    }

    /** 取消当前下载并清空排队中的书。 */
    fun cancelDownloads() = viewModelScope.launch {
        // 队列先清：只取消当前任务的话，泵会立刻把下一本拉起来，用户会以为「取消没生效」。
        val pending = synchronized(cacheQueue) { cacheQueue.size.also { cacheQueue.clear() } }
        update { it.copy(cacheQueueSize = 0) }
        downloader.cancel()
        // 预取也停：用户按下取消，不是只想停掉排队里的那几本
        prefetcher.cancel()
        refreshOffline()
        if (pending > 0) notify("已清空排队中的 $pending 本")
    }

    fun startDownload(book: Book, maxChapters: Int) {
        // 每次起跑前同步一次策略：设置页改完立刻生效，不需要重启
        downloader.policy = _state.value.downloadPolicy
        downloader.start(book, maxChapters)
    }
    /**
     * 书架排序与分组过滤。纯函数，便于离线单测。
     * group 排序时按分组名升序、组内按最近阅读倒序，未分组排最后。
     */
    object ShelfSort {
        fun isManga(book: Book): Boolean =
            book.category.contains("漫") || book.title.contains("漫画") || book.source == "manga"

        fun apply(
            items: List<ShelfItem>,
            sort: String,
            group: String,
            mediaType: String = "all",
            status: String = "all",
        ): List<ShelfItem> {
            val byStatus = if (status in ShelfItem.STATUSES) items.filter { it.status == status } else items
            // 内置分组（连载中/已完结）走 book.status，手工分组走 shelf.json 的 group 字段。
            val byGroup = when {
                group.isBlank() -> byStatus
                ShelfGrouping.isBuiltin(group) -> byStatus.filter { ShelfGrouping.matches(it, group) }
                else -> byStatus.filter { it.group == group }
            }
            val byMedia = when (mediaType) {
                "novel" -> byGroup.filter { !isManga(it.book) }
                "manga" -> byGroup.filter { isManga(it.book) }
                else -> byGroup
            }
            return when (sort) {
                // 「最近」= 最近一次活动：读过看阅读时间，没读过看入架时间。
                // 旧实现让没读过的书永远沉底，刚加入书架的书反而找不到。
                "recent" -> byMedia.sortedWith(
                    compareByDescending<ShelfItem> { maxOf(it.lastReadAt, it.addedAt) })
                "added" -> byMedia.sortedByDescending { it.addedAt }
                "title" -> byMedia.sortedBy { it.book.title }
                // 内置分组里再按手工分组排没有意义（组内手工名是散的），退回「最近」。
                "group" -> if (ShelfGrouping.isBuiltin(group)) {
                    byMedia.sortedWith(compareByDescending<ShelfItem> { maxOf(it.lastReadAt, it.addedAt) })
                } else {
                    byMedia.sortedWith(
                        compareBy({ it.group.ifBlank { "\uFFFF" } }, { -it.lastReadAt }, { -it.addedAt }))
                }
                else -> byMedia.sortedWith(
                    compareByDescending<ShelfItem> { maxOf(it.lastReadAt, it.addedAt) })
            }
        }
    }

    fun setShelfMediaType(type: String) {
        require(type in setOf("all", "novel", "manga", "drama")) { "未知板块" }
        update {
            it.copy(
                shelfMediaType = type,
                shelf = ShelfSort.apply(
                    it.shelfAll, it.shelfSort, it.shelfGroupFilter, type, it.shelfStatusFilter
                )
            )
        }
    }

    /** 从全量书架派生当前视图；任何过滤都基于 shelfAll，避免在已过滤结果上二次过滤。 */
    fun shelfGroups(): List<String> =
        _state.value.shelfAll.map { it.group }.filter { it.isNotBlank() }.distinct().sorted()

    fun setShelfSort(sort: String) {
        require(sort in setOf("recent", "added", "title", "group")) { "未知排序" }
        update {
            // 传新 sort，且沿用当前分组过滤：否则选中「分组X」切排序就会显示全部书
            it.copy(shelfSort = sort,
                shelf = ShelfSort.apply(
                    it.shelfAll, sort, it.shelfGroupFilter, it.shelfMediaType, it.shelfStatusFilter
                ))
        }
    }

    fun setShelfGroupFilter(group: String) {
        update {
            val derived = ShelfSort.apply(
                it.shelfAll, it.shelfSort, group, it.shelfMediaType, it.shelfStatusFilter
            )
            android.util.Log.i("xread-shelf",
                "filter='$group' all=${it.shelfAll.size} visible=${derived.size}")
            it.copy(shelfGroupFilter = group, shelf = derived)
        }
    }

    fun setShelfStatusFilter(status: String) {
        require(status == "all" || status in ShelfItem.STATUSES) { "未知标记" }
        update {
            it.copy(
                shelfStatusFilter = status,
                shelf = ShelfSort.apply(
                    it.shelfAll, it.shelfSort, it.shelfGroupFilter, it.shelfMediaType, status
                ),
            )
        }
    }

    fun setGroup(book: Book, group: String) = viewModelScope.launch {
        runCatching { api.setGroup(book.bookId, group) }
            .onSuccess { loadShelf(); update { st -> st.copy(message = if (group.isBlank()) "已移出分组" else "已归入「$group」") } }
            .onFailure { e -> update { it.copy(message = e.message ?: "分组失败") } }
    }

    fun setBookStatus(bookId: String, status: String) = viewModelScope.launch {
        runCatching { api.setBookStatus(bookId, status) }
            .onSuccess { loadShelf() }
            .onFailure { e -> notify(e.message ?: "状态保存失败") }
    }

    fun setBookRating(bookId: String, rating: Int) = viewModelScope.launch {
        runCatching { api.setBookRating(bookId, rating) }
            .onSuccess { loadShelf() }
            .onFailure { e -> notify(e.message ?: "评分保存失败") }
    }

    fun setBookTags(bookId: String, tags: List<String>) = viewModelScope.launch {
        runCatching { api.setBookTags(bookId, tags) }
            .onSuccess { loadShelf() }
            .onFailure { e -> notify(e.message ?: "标签保存失败") }
    }

    /**
     * 真正干活的那一半，[refreshShelf] 直接 await 它——指示器要等到结果落地才收，
     * 不能先返回再后台跑。
     */
    private suspend fun checkUpdatesNow() {
        // 用 shelfAll：shelf 可能被分组过滤过，用它会漏掉其余书的更新
        val items = _state.value.shelfAll
        if (items.isEmpty() || _state.value.checkingUpdates) return
        update { it.copy(checkingUpdates = true) }
        runCatching { api.checkUpdates(items) }
            .onSuccess { found ->
                update {
                    it.copy(checkingUpdates = false, shelfUpdates = found,
                        message = if (found.isEmpty()) "已是最新" else "${found.size} 本有新章")
                }
            }
            .onFailure { e -> update { it.copy(checkingUpdates = false, message = e.message ?: "检查更新失败") } }
    }

    fun refreshOffline() = viewModelScope.launch {
        val loaded = withContext(Dispatchers.IO) {
            val books = offline.offlineBooks()
            val sizes = offline.bookSizes().toMap()
            val junk = offline.leftovers()
            StorageSnapshot(books, downloader.resumable(), offline.sizeBytes(), sizes,
                junk.size, junk.sumOf { runCatching { it.length() }.getOrDefault(0L) })
        }
        update {
            it.copy(offlineBooks = loaded.books, resumable = loaded.resumable,
                storageTotal = loaded.total, bookSizes = loaded.sizes,
                leftoverCount = loaded.junkCount, leftoverBytes = loaded.junkBytes)
        }
    }

    private data class StorageSnapshot(
        val books: List<Book>,
        val resumable: List<OfflineRepo.DownloadTask>,
        val total: Long,
        val sizes: Map<String, Long>,
        val junkCount: Int,
        val junkBytes: Long,
    )

    /** 清理原子写残留与损坏隔离文件。 */
    fun cleanLeftovers() = viewModelScope.launch {
        val removed = withContext(Dispatchers.IO) { offline.cleanLeftovers() }
        refreshOffline()
        update { it.copy(message = if (removed > 0) "已清理 $removed 个残留文件" else "没有可清理的残留") }
    }

    /** 删除中／取消下载的入口：cancel() 现在是 suspend 且会真正 join 旧任务。 */
    fun cancelDownload(refresh: Boolean = false) = viewModelScope.launch {
        downloader.cancel()
        if (refresh) refreshOffline()
    }

    /** 删本地书：deleteRecursively 可能上千个文件，必须离开主线程。 */
    fun deleteOfflineBook(bookId: String) = viewModelScope.launch {
        // 先停掉这本书正在跑的下载，否则 worker 的 mkdirs 会在删除后把目录重建，
        // 删完又「复活」成半本（chapterCount 随后还会把它标成完成）。
        downloader.cancelIfRunning(bookId)
        withContext(Dispatchers.IO) { offline.removeBook(bookId) }
        refreshOffline()
        update { it.copy(message = "已删除本地书籍") }
    }

    fun resumeDownload(book: Book) {
        downloader.policy = _state.value.downloadPolicy
        downloader.resume(book)
    }

    /**
     * 刷新当前书的缓存标记。一次 listFiles 建集合，939 行目录只付一次代价；
     * 打开目录时调一次，下载推进时再调一次。
     */
    fun refreshCachedChapters() {
        val book = _state.value.book ?: return
        viewModelScope.launch {
            val ids = withContext(Dispatchers.IO) { offline.downloadedChapters(book.bookId) }
            update { it.copy(cachedChapters = ids) }
        }
    }

    /**
     * 从目录补缓存第 [index] 章起 [count] 章（[index] 是目录下标，不是章号）。
     * 区间下载是手动点的那几章，不套「只下未读」，也不受整本总章数上限约束。
     */
    fun cacheChapters(index: Int, count: Int) {
        val book = _state.value.book ?: return
        downloader.policy = _state.value.downloadPolicy
        downloader.startRange(book, index, count)
    }

    /** 删掉单章缓存：容量吃紧时先清掉读过的几百章，不必删整本。 */
    fun uncacheChapter(chapterId: String) = viewModelScope.launch {
        val book = _state.value.book ?: return@launch
        val ok = withContext(Dispatchers.IO) { offline.removeChapter(book.bookId, chapterId) }
        val ids = withContext(Dispatchers.IO) { offline.downloadedChapters(book.bookId) }
        update {
            it.copy(
                cachedChapters = ids,
                message = if (ok) "已删除本章缓存" else "删除失败",
            )
        }
    }
    fun addStats(seconds: Int, words: Int, chapters: Int) {
        if (offline.addStats(seconds, words, chapters)) rollToday(seconds)
    }

    /** 记录一章阅读；时长在 flushStats 结算。 */
    private fun countReading(words: Int) {
        if (readingSince == 0L) readingSince = System.currentTimeMillis()
        pendingWords += words
        pendingChapters += 1
    }

    /**
     * 把当前阅读位置落盘。
     *
     * 和 [flushStats] 分开，因为两者的触发条件不同：阅读时长只在「真的读了」时结算，
     * 而阅读位置任何时候都可以安全重写。合在一起会让「没在计时」顺带吞掉进度保存。
     */
    fun saveReadingProgress(): Boolean {
        val b = _state.value.book ?: return false
        val cid = _state.value.currentChapterId
        if (cid.isEmpty()) return false
        progressSavedAt = System.currentTimeMillis()
        return offline.saveProgress(b.bookId, cid, _state.value.currentParagraph, _state.value.chapterIndex, b)
    }

    /** 结算阅读时长；离屏、切章或退出前调用。 */
    fun flushStats(force: Boolean = false): Boolean {
        // 进度不该被计时状态挡住：退出阅读页时若暂停过计时（readingSince 已归零），
        // 旧实现在这里直接 return，于是进度一条都没写，重进就回到旧章节。
        val progressed = saveReadingProgress()
        if (readingSince == 0L) return progressed
        val seconds = ((System.currentTimeMillis() - readingSince) / 1000).toInt()
        if (!force && seconds < 3) return progressed
        if (!offline.addStats(seconds, pendingWords, pendingChapters)) return progressed
        readingSince = System.currentTimeMillis()
        pendingWords = 0
        pendingChapters = 0
        val todayTotal = rollToday(seconds)
        announceGoalMet(todayTotal, seconds)
        return true
    }

    /**
     * 在内存里累加今日秒数，顺带处理跨日。
     *
     * 本来想让 [OfflineRepo.addStats] 把今日累计回传，但它的返回值是「写盘成功」，
     * 改签名会破坏既有语义。而回读 stats.json 又给每次结算多加一次读盘——
     * 统计数字本来就只在启动时读一次、之后全靠累加，这里延续同一套做法。
     */
    private fun rollToday(seconds: Int): Long {
        val key = GoalMath.dayKey()
        if (key != todayKey) {
            todayKey = key
            todayCache = 0L
        }
        todayCache += seconds.coerceAtLeast(0).toLong()
        update { it.copy(todaySeconds = todayCache) }
        return todayCache
    }

    /** 本次结算刚跨过目标线才提示；已经达标后再读不重复打扰。 */
    private fun announceGoalMet(todayTotal: Long, seconds: Int) {
        val goal = _state.value.readingGoal
        val target = GoalMath.goalSeconds(goal)
        if (target <= 0) return
        if (todayTotal >= target && todayTotal - seconds < target) {
            notify("今日 ${goal.normalized().minutes} 分钟目标已完成")
        }
    }

    /**
     * 进后台：结算已读时长并**停止计时**。
     * 只调用 [flushStats] 不够——它会把起点重置为「现在」，
     * 于是后台停留的时间照样计入下次结算。
     */
    fun pauseReading() {
        flushStats(force = true)
        readingSince = 0L
    }

    /** 回前台：若正文仍在，重新开始计时（否则要等切章才会计入时长）。 */
    fun resumeReading() {
        if (readingSince == 0L && _state.value.currentChapterId.isNotEmpty()) {
            readingSince = System.currentTimeMillis()
        }
    }
    fun rules(): List<ReplaceRule> = offline.rules()

    fun loadReminder() = viewModelScope.launch {
        val r = withContext(Dispatchers.IO) { offline.reminder() }
        update { it.copy(reminder = r) }
    }

    /** 落盘在前、界面在后：闹钟由调用方（Activity）按新值重排。 */
    fun setReminder(reminder: ReadingReminder) {
        val r = reminder.normalized()
        update { it.copy(reminder = r) }
        viewModelScope.launch(Dispatchers.IO) { offline.saveReminder(r) }
    }

    fun setNewChapterNotify(enabled: Boolean) = viewModelScope.launch {
        update { it.copy(newChapterNotify = enabled) }
        withContext(Dispatchers.IO) { offline.saveNewChapterNotify(enabled) }
    }

    fun checkOnboarding() = viewModelScope.launch {
        val needed = withContext(Dispatchers.IO) { offline.needsOnboarding() }
        if (needed) update { it.copy(showOnboarding = true) }
    }

    /** 结束引导：标记写盘后立刻撤掉浮层，不等 IO。 */
    fun finishOnboarding() {
        update { it.copy(showOnboarding = false) }
        viewModelScope.launch(Dispatchers.IO) { offline.saveOnboarded() }
    }

    fun loadReadingGoal() = viewModelScope.launch {
        val goal = withContext(Dispatchers.IO) { offline.readingGoal() }
        // 只在这里读一次 stats.json，之后今日秒数靠 rollToday 累加。
        val today = withContext(Dispatchers.IO) {
            val key = GoalMath.dayKey()
            key to GoalMath.daySeconds(offline.stats().optJSONObject("days"), key)
        }
        todayKey = today.first
        todayCache = today.second
        update { it.copy(readingGoal = goal, todaySeconds = today.second) }
    }

    fun setReadingGoal(goal: ReadingGoal) {
        val next = goal.normalized()
        update { it.copy(readingGoal = next) }
        viewModelScope.launch { withContext(Dispatchers.IO) { offline.saveReadingGoal(next) } }
    }

    fun loadDownloadPolicy() = viewModelScope.launch {
        val p = withContext(Dispatchers.IO) { offline.downloadPolicy() }
        val notify = withContext(Dispatchers.IO) { offline.newChapterNotifyEnabled() }
        downloader.policy = p
        // 预取器也要拿到落盘的档位：只同步 downloader 的话，重启后预取会按缺省 3 章跑，
        // 用户在设置里选的「关」或「后 10 章」等于没生效。
        prefetcher.policy = p
        update { it.copy(downloadPolicy = p, newChapterNotify = notify) }
    }

    fun setDownloadPolicy(p: DownloadPolicy) = viewModelScope.launch {
        downloader.policy = p
        prefetcher.policy = p
        update { it.copy(downloadPolicy = p) }
        withContext(Dispatchers.IO) { offline.saveDownloadPolicy(p) }
        // 关掉预取要立刻停手；改大窗口则从当前章重新补
        val book = _state.value.book
        if (book == null || p.prefetch <= 0) prefetcher.cancel()
        else if (_state.value.chapters.isNotEmpty()) afterChapter(book, _state.value.chapterIndex)
    }

    /** 载入阅读器偏好：磁盘读写统一走 IO。 */
    fun loadReaderPrefs() = viewModelScope.launch {
        val prefs = withContext(Dispatchers.IO) { offline.readerPrefs() }
        update { it.copy(readerPrefs = prefs) }
    }

    /** 恢复追剧记录：书架「短剧」页靠它，进程重启后不再永远是空的。 */
    fun loadRecentDramas() = viewModelScope.launch {
        val recent = withContext(Dispatchers.IO) { offline.dramaRecent() }
        if (recent.isEmpty()) return@launch
        // 磁盘上的记录可能比当前内存里的旧（刚看的那部已经在前），合并时保持「新的在前」。
        update { st ->
            val merged = (st.recentDramas + recent).distinctBy { it.seriesId }.take(20)
            st.copy(recentDramas = merged)
        }
    }

    /** 恢复短剧收藏：以前只写 SharedPreferences 的布尔值，界面上根本没有地方读回来。 */
    fun loadDramaFavorites() = viewModelScope.launch {
        val favorites = withContext(Dispatchers.IO) { offline.dramaFavorites() }
        update { it.copy(dramaFavorites = favorites) }
    }

    /**
     * 收藏/取消收藏一部短剧。列表跟着星标一起变，用户点完立刻能在书架「短剧」页看到。
     */
    fun toggleDramaFavorite(series: DramaSeries) = viewModelScope.launch {
        val current = _state.value.dramaFavorites.any { it.seriesId == series.seriesId }
        val next = !current
        val updated = withContext(Dispatchers.IO) { offline.setDramaFavorite(series, next) }
        update { it.copy(dramaFavorites = updated, message = if (next) "已收藏《${series.title}》" else "已取消收藏") }
    }

    fun setReaderPrefs(prefs: ReaderPrefs) = viewModelScope.launch {
        val v = prefs.normalized()
        update { it.copy(readerPrefs = v) }
        withContext(Dispatchers.IO) { offline.saveReaderPrefs(v) }
    }

    fun saveRules(list: List<ReplaceRule>) = viewModelScope.launch {
        val ok = withContext(Dispatchers.IO) { offline.saveRules(list) }
        update { it.copy(message = if (ok) "规则已保存 ${list.size} 条" else "规则保存失败") }
    }

    fun exportBackup() = viewModelScope.launch {
        val file = withContext(Dispatchers.IO) { offline.exportBackup() }
        update { it.copy(message = file?.let { f -> "已导出 ${f.absolutePath}" } ?: "导出失败") }
    }

    /**
     * 导入备份。写盘在 IO 完成之后才回调 [onDone]：
     * 旧实现 UI 紧接着同步读 rules()/loadShelf()，拿到的是导入**之前**的数据，
     * 于是「保存规则」会用旧快照把刚导入的规则覆盖回去。
     */
    fun importBackup(onDone: () -> Unit = {}) = viewModelScope.launch {
        val text = withContext(Dispatchers.IO) { offline.importBackup() }
        loadShelf()
        onDone()
        update { it.copy(message = text) }
    }

    fun closeDramaPlayer() {
        update { it.copy(currentDrama = null, currentEpisode = null, currentVideoSource = null, danmakuList = emptyList(), dramaVideoError = "") }
    }

    fun loadDramaFeed() {
        viewModelScope.launch {
            update {
                it.copy(dramaFeedLoading = true, dramaOffset = 0, dramaExhausted = false,
                    dramaMoreLoading = false, dramaFeedError = "")
            }
            try {
                // 先取首个 cell 让界面立刻可滑，再补齐其余 cell
                val quick = api.dramaFeed(maxCells = 2)
                update { it.copy(dramaFeed = quick, dramaFeedLoading = false, dramaOffset = 10) }
                val full = api.dramaFeed(maxCells = 10)
                if (full.size > quick.size) {
                    update { it.copy(dramaFeed = full, dramaOffset = 10) }
                }
            } catch (e: Exception) {
                // 短剧页是全屏播放器，加载失败必须自己说清楚，否则只剩一个转不停的圈
                update {
                    it.copy(dramaFeedLoading = false, dramaFeedError = readableFailure(e.message.orEmpty()))
                }
            }
        }
    }

    /** 短剧下滑续页：沿频道首页 offset 往后翻，按 seriesId 去重追加 */
    fun loadMoreDrama() {
        val st = _state.value
        if (st.dramaMoreLoading || st.dramaExhausted || st.dramaFeedLoading) return
        viewModelScope.launch {
            update { it.copy(dramaMoreLoading = true) }
            runCatching { api.dramaFeed(maxCells = 5, offset = st.dramaOffset) }
                .onSuccess { list ->
                    update { cur ->
                        val seen = cur.dramaFeed.mapTo(mutableSetOf()) { it.seriesId }
                        val fresh = list.filter { seen.add(it.seriesId) }
                        cur.copy(
                            dramaFeed = cur.dramaFeed + fresh,
                            dramaOffset = cur.dramaOffset + 10,
                            dramaExhausted = list.isEmpty(),
                            dramaMoreLoading = false,
                        )
                    }
                }
                .onFailure { update { it.copy(dramaMoreLoading = false) } }
        }
    }

    fun loadDramaDetail(seriesId: String, isMotionComic: Boolean = false, autoPlayFirst: Boolean = false) {
        viewModelScope.launch {
            update { it.copy(dramaDetailLoading = true) }
            try {
                val parsed = api.dramaDetail(seriesId, isMotionComic)
                val feedItem = _state.value.dramaFeed.firstOrNull { it.seriesId == parsed.seriesId }
                val placeholder = "短剧 ${parsed.seriesId}"
                val drama = if (feedItem == null) parsed else parsed.copy(
                    title = if (parsed.title == placeholder) feedItem.title else parsed.title,
                    coverUrl = parsed.coverUrl.ifBlank { feedItem.coverUrl },
                    intro = parsed.intro.ifBlank { feedItem.intro },
                    author = parsed.author.ifBlank { feedItem.author },
                    episodeCount = if (parsed.episodeCount > 0) parsed.episodeCount else feedItem.episodeCount,
                )
                // 落盘后再更新界面：追剧记录以前只存在内存，杀进程就没了。
                val persisted = offline.pushDramaRecent(drama)
                val updatedRecent =
                    if (persisted.isNotEmpty()) persisted
                    else (listOf(drama) + _state.value.recentDramas.filterNot { it.seriesId == drama.seriesId }).take(20)
                update { it.copy(currentDrama = drama, dramaDetailLoading = false, recentDramas = updatedRecent) }
                if (autoPlayFirst && drama.episodes.isNotEmpty()) {
                    playDramaEpisode(drama.episodes.first())
                }
            } catch (e: Exception) {
                update { it.copy(dramaDetailLoading = false, message = readableFailure(e.message.orEmpty())) }
            }
        }
    }

    fun playDramaEpisode(episode: DramaEpisode) {
        viewModelScope.launch {
            update { it.copy(
                currentEpisode = episode,
                dramaVideoLoading = true,
                dramaVideoError = "",
                currentVideoSource = null,
                danmakuList = emptyList()
            ) }
            try {
                val source = api.dramaEpisodePlay(episode.episodeId)
                update { it.copy(currentVideoSource = source, dramaVideoLoading = false) }
                loadDanmaku(episode.seriesId, episode.episodeId, durationSeconds = episode.durationSeconds)
            } catch (e: Exception) {
                update { it.copy(dramaVideoLoading = false, dramaVideoError = readableFailure(e.message.orEmpty())) }
            }
        }
    }

    fun loadDanmaku(seriesId: String, videoId: String, offsetMs: Long = 0L, durationSeconds: Int = 0) {
        viewModelScope.launch {
            update { it.copy(danmakuLoading = true) }
            try {
                val list = api.dramaDanmaku(seriesId, videoId, offsetMs, durationSeconds)
                update { it.copy(danmakuList = list, danmakuLoading = false) }
            } catch (_: Exception) {
                update { it.copy(danmakuLoading = false) }
            }
        }
    }

    fun sendDanmaku(text: String, currentOffsetMs: Long) {
        val series = _state.value.currentDrama ?: return
        val ep = _state.value.currentEpisode ?: return
        if (text.isBlank()) return
        val optimistic = DanmakuItem(
            id = "local_${System.currentTimeMillis()}",
            text = text.trim(),
            offsetMs = currentOffsetMs,
            userName = "我"
        )
        update { state ->
            val updated = (state.danmakuList + optimistic).sortedBy { it.offsetMs }
            state.copy(danmakuList = updated)
        }
        viewModelScope.launch {
            try {
                val ok = api.sendDramaDanmaku(series.seriesId, ep.episodeId, text.trim(), currentOffsetMs)
                if (!ok) {
                    // code 是成功但 body 不是 success 形态：同样算没发出去，
                    // 不然用户会看到自己那条留在屏上，却根本没上墙。
                    update { state ->
                        state.copy(danmakuList = state.danmakuList.filterNot { it.id == optimistic.id })
                    }
                    notify(danmakuSendFailure("上游没有确认接收"))
                } else {
                    notify("弹幕已发送")
                }
            } catch (e: Exception) {
                // 发不出去就把自己那条撤掉，并说清楚为什么。
                // 上游要番茄登录态，没登录时统一回 103006「用户未登录」。
                update { state ->
                    state.copy(danmakuList = state.danmakuList.filterNot { it.id == optimistic.id })
                }
                notify(danmakuSendFailure(e.message.orEmpty()))
            }
        }
    }

    class Factory(private val api: Api, private val offline: OfflineRepo) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            LibraryViewModel(api, offline) as T
    }
}

/** 段评气泡下标的基准归一化。纯函数，便于单测。 */
object ParagraphBubbleIndex {
    /**
     * 把上游段评气泡的键换算成应用段落下标。
     *
     * 上游键与段评请求参数 `para_index` 同一坐标系：0-based，且**不计章节标题行**
     * （实测 47 章的 `paragraphs_num` 与首行标题一致）。应用第 0 段恰是那行标题，
     * 所以应用下标 = 上游键 + [ParagraphOffsets.UPSTREAM_TITLE_OFFSET]。
     *
     * 只有在上游报了段落数、且它恰好等于应用段落数减一时才换算；换算不出就返回空列表
     * ——宁可不显示气泡，也不能把评论挂到错的段落上。越界的键（上游有一个
     * 全书汇总桶 `10000`）一律丢弃。
     */
    fun toAppIndexes(
        bubbles: List<ParagraphBubble>,
        upstreamParagraphs: Int,
        appParagraphs: Int,
    ): List<ParagraphBubble> {
        if (bubbles.isEmpty()) return emptyList()
        if (upstreamParagraphs <= 0) return emptyList()
        // 正文可能带标题行（app = up + 1），也可能拆掉了标题行（app = up）。
        // 仅这两种对齐关系可信，其他一律不给气泡，免得挂错段落。
        val offset = appParagraphs - upstreamParagraphs
        if (offset != 0 && offset != ParagraphOffsets.UPSTREAM_TITLE_OFFSET) {
            return emptyList()
        }
        return bubbles.mapNotNull { bubble ->
            val appIndex = bubble.index + offset
            if (appIndex < 0 || appIndex >= appParagraphs) null else bubble.copy(index = appIndex)
        }.sortedBy { it.index }
    }
}
