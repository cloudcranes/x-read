package com.alanmaster.xread

import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.LocalLibrary
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.alanmaster.xread.data.Api
import com.alanmaster.xread.data.AudioPlaybackHolder
import com.alanmaster.xread.data.PlaybackService
import com.alanmaster.xread.data.Book
import com.alanmaster.xread.data.LibraryViewModel
import com.alanmaster.xread.data.OfflineRepo
import com.alanmaster.xread.data.ShelfGrouping
import com.alanmaster.xread.data.TtsController
import com.alanmaster.xread.ui.AudioFeedScreen
import com.alanmaster.xread.ui.AudioScreen
import com.alanmaster.xread.ui.AuthorScreen
import com.alanmaster.xread.ui.CategoryBooksScreen
import com.alanmaster.xread.ui.CategoryScreen
import com.alanmaster.xread.ui.DetailScreen
import com.alanmaster.xread.ui.DiscoverScreen
import com.alanmaster.xread.ui.FloatingNavBar
import com.alanmaster.xread.ui.FloatingNavItem
import com.alanmaster.xread.ui.DramaDouyinScreen
import com.alanmaster.xread.ui.DramaScreen
import com.alanmaster.xread.ui.DramaSwipeScreen
import com.alanmaster.xread.ui.ReviewScreen
import com.alanmaster.xread.ui.TocScreen
import com.alanmaster.xread.ui.MarksScreen
import com.alanmaster.xread.ui.ReaderScreen
import com.alanmaster.xread.ui.RankScreen
import com.alanmaster.xread.ui.SearchScreen
import com.alanmaster.xread.ui.SettingsSheet
import com.alanmaster.xread.ui.Space
import com.alanmaster.xread.ui.ShelfScreen
import com.alanmaster.xread.ui.ToolsScreen
import com.alanmaster.xread.ui.XReadTheme
import org.json.JSONObject

/** 底栏四个入口。图标与文字在这里定死，选中态只影响展开与配色。 */
private val mainNavItems = listOf(
    FloatingNavItem(Icons.Filled.LocalLibrary, "书架"),
    FloatingNavItem(Icons.Filled.Explore, "发现"),
    FloatingNavItem(Icons.Filled.Movie, "短剧"),
    FloatingNavItem(Icons.Filled.Person, "我的"),
)

class MainActivity : ComponentActivity() {

    private lateinit var api: Api
    private lateinit var offline: OfflineRepo

    /**
     * 音量键翻页：Compose 的 onKeyEvent 拿不到被系统优先消费的音量键，只能在这里截。
     * 可见性与开关由 UI 侧写入 [com.alanmaster.xread.ui.ReaderPager.active]。
     */
    override fun dispatchKeyEvent(event: android.view.KeyEvent): Boolean {
        if (com.alanmaster.xread.ui.ReaderPager.active &&
            (event.keyCode == android.view.KeyEvent.KEYCODE_VOLUME_DOWN ||
                event.keyCode == android.view.KeyEvent.KEYCODE_VOLUME_UP)
        ) {
            if (event.action == android.view.KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
                com.alanmaster.xread.ui.ReaderPager.page(
                    if (event.keyCode == android.view.KeyEvent.KEYCODE_VOLUME_DOWN) 1 else -1)
            }
            return true
        }
        return super.dispatchKeyEvent(event)
    }

    /** 权限结果只回调一次，用完即清，避免下一次请求被上一次的回调串台。 */
    private var notificationPermissionResult: ((Boolean) -> Unit)? = null

    private val notificationPermission =
        registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.RequestPermission()) { granted ->
            val callback = notificationPermissionResult
            notificationPermissionResult = null
            callback?.invoke(granted)
        }

    /** 通知权限当前是否可用；13 以下没有这个权限，恒为 true。 */
    fun hasNotificationPermission(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED
    }

    /**
     * Android 13 起通知需要运行时授权。
     * 未授权时播放本身不受影响，只是看不到通知栏控制条——所以这里不阻断播放。
     * [onResult] 用来让调用方知道最终结果：新章提醒开关如果被拒，
     * 界面还是「开」，通知却永远不会出现，必须给用户一句话。
     */
    fun requestNotificationPermissionIfNeeded(onResult: ((Boolean) -> Unit)? = null) {
        if (hasNotificationPermission()) {
            onResult?.invoke(true)
            return
        }
        notificationPermissionResult = onResult
        runCatching { notificationPermission.launch(android.Manifest.permission.POST_NOTIFICATIONS) }
            .onFailure {
                notificationPermissionResult = null
                onResult?.invoke(false)
            }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        api = Api(this)
        offline = OfflineRepo.of(this)
        // 尽早注入上下文：音频控制器需要它申请音频焦点（来电/其它播放器打断）
        AudioPlaybackHolder.install(this)

        // 主流做法：ComponentActivity 的 OnBackPressedDispatcher（兼容预测式返回）
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                val action = backAction
                android.util.Log.i("xread-back", "dispatcher: " + (action != null))
                if (action != null) action() else {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                }
            }
        })

        setContent {
            XReadTheme {
                App(
                    api = api,
                    offline = offline,
                )
            }
        }
    }

    /** 由 Compose 注入；为 null 时退回系统默认（结束 Activity）。 */
    @Volatile
    private var backAction: (() -> Unit)? = null

    fun setAppBackHandler(handler: (() -> Unit)?) { backAction = handler }

    override fun onDestroy() {
        super.onDestroy()
    }
}

private sealed interface Screen {
    data object Shelf : Screen
    data object Discover : Screen
    data object Search : Screen
    data object Rank : Screen
    data class Detail(val book: Book) : Screen
    data object Reader : Screen
    data object Marks : Screen
    data object Tools : Screen
    data object Reviews : Screen
    data object Author : Screen
    data object Toc : Screen
    data object Audio : Screen
    data object AudioFeed : Screen
    data object Category : Screen
    data object CategoryBooks : Screen
    data object Drama : Screen
    data object DramaDouyin : Screen
    data object DramaSwipe : Screen
}

@Composable
private fun App(
    api: Api,
    offline: OfflineRepo,
) {
    val vm: LibraryViewModel = viewModel(factory = LibraryViewModel.Factory(api, offline))
    val state by vm.state.collectAsStateWithLifecycle()
    // Compose 里要在所有 effect 之前拿到 Context：音频服务启动依赖它
    val hostContext = androidx.compose.ui.platform.LocalContext.current
    val download by vm.downloader.state.collectAsStateWithLifecycle()
    var screen by remember { mutableStateOf<Screen>(Screen.Shelf) }
    var mainTab by remember { mutableStateOf<Screen>(Screen.Shelf) }
    var readerFromShelf by remember { mutableStateOf(false) }
    /** 详情页是从阅读页点右上角进来的：返回时回正文，而不是把读者丢回书架。 */
    var detailFromReader by remember { mutableStateOf(false) }

    var dramaSwipePage by rememberSaveable { mutableIntStateOf(0) }
    var dramaBackToSwipe by rememberSaveable { mutableStateOf(false) }
    // 通知权限只在进入界面时读一次，授权回来后再刷新；不必每次重组都查系统
    var notifyAllowed by remember {
        mutableStateOf((hostContext as? MainActivity)?.hasNotificationPermission() ?: true)
    }
    // 应用没有开机广播接收器，闹钟靠每次启动重排一次；改时间也走这里
    LaunchedEffect(state.reminder) {
        val reminder = state.reminder
        if (reminder.enabled) {
            com.alanmaster.xread.data.ReminderScheduler.schedule(hostContext, reminder)
        }
    }
    LaunchedEffect(screen) {
        if (screen is Screen.Shelf || screen is Screen.Discover || screen is Screen.Tools) {
            mainTab = screen
        }
    }
    var showSettings by remember { mutableStateOf(false) }
    // 听书页当前对应哪本书、从哪儿进来的。
    // 听书榜进听书时 state.book 是空的（openAudio 不动它），只靠 state.book
    // 会标题空白、返回键失效。
    var audioBook by remember { mutableStateOf<Book?>(null) }
    var audioFromFeed by remember { mutableStateOf(false) }
    // 从搜索页「听书」tab 进来的听书：没有详情页，返回键要回搜索页。
    var audioFromSearch by remember { mutableStateOf(false) }
    /** 分组对话框的目标：单本来自长按菜单，多本来自多选模式。 */
    var groupTargets by remember { mutableStateOf<List<Book>>(emptyList()) }
    var groupDraft by remember { mutableStateOf("") }
    var query by remember { mutableStateOf("") }
    var statsSnapshot by remember { mutableStateOf(JSONObject()) }
    var rulesSnapshot by remember { mutableStateOf(vm.rules()) }
    var ttsRate by remember { mutableStateOf(1f) }
    // 读完自动续下一章；用户手动停止后置 false
    var autoContinue by remember { mutableStateOf(false) }
    val ctx = androidx.compose.ui.platform.LocalContext.current
    // 进程级单例：离开阅读页/Activity 销毁都不能 shutdown，否则后台朗读立刻中断。
    val tts = remember { com.alanmaster.xread.data.TtsHolder.install(ctx) }
    // 正在朗读的段落下标；-1 表示未在朗读
    var ttsParagraph by remember { mutableStateOf(-1) }

    androidx.compose.runtime.DisposableEffect(Unit) {
        com.alanmaster.xread.data.TtsHolder.onParagraph = { idx ->
            ttsParagraph = idx
            vm.setParagraph(idx)
        }
        com.alanmaster.xread.data.TtsHolder.onChapterFinished = {
            android.util.Log.i("xread-tts", "autoContinue=$autoContinue")
            if (autoContinue) vm.nextChapter()
        }
        onDispose {
            // 只摘回调，不销毁引擎：后台朗读要继续
            com.alanmaster.xread.data.TtsHolder.onParagraph = null
            com.alanmaster.xread.data.TtsHolder.onChapterFinished = null
        }
    }
    // 状态以控制器为单一来源
    val ttsSpeaking by tts.speakingFlow.collectAsStateWithLifecycle()
    val ttsMessage by tts.messageFlow.collectAsStateWithLifecycle()

    // 新章提醒的闹钟跟着状态走：状态由 loadDownloadPolicy 在 IO 里读盘后写入，
    // 不在主线程做文件 IO，也不会因为启动顺序漏排。
    androidx.compose.runtime.LaunchedEffect(state.newChapterNotify) {
        if (state.newChapterNotify) com.alanmaster.xread.data.UpdateScheduler.schedule(hostContext)
        else com.alanmaster.xread.data.UpdateScheduler.cancel(hostContext)
    }

    // 热更新：进界面先读一次偏好，再过节流自动查一次（一天最多一次）。
    // 检查/下载都是 IO，状态由 updater 自己的 StateFlow 推送，不占主线程。
    val updater = remember { com.alanmaster.xread.data.AppUpdater(hostContext) }
    val updateState by updater.state.collectAsStateWithLifecycle()
    androidx.compose.runtime.LaunchedEffect(Unit) {
        updater.hydrate()
        updater.autoCheckOnLaunch()
    }
    // Toast 只负责「冒一下」，真正的结果留在设置面板里，别手动清掉。
    androidx.compose.runtime.LaunchedEffect(updateState.message) {
        val text = updateState.message
        if (text.isNotEmpty()) {
            android.widget.Toast.makeText(hostContext, text, android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    // 开始朗读就拉前台上服务：锁屏后仍可读，并从通知栏暂停/停止
    androidx.compose.runtime.LaunchedEffect(ttsSpeaking) {
        if (ttsSpeaking) {
            (hostContext as? MainActivity)?.requestNotificationPermissionIfNeeded()
            com.alanmaster.xread.data.TtsService.start(hostContext)
        } else {
            ttsParagraph = -1
        }
    }

    // 音频播放器：进程级单例，前台服务与界面操作同一个 MediaPlayer。
    // 不能在 onDispose 里 shutdown——离开听书页要能继续播。
    val audioPlayer = remember { AudioPlaybackHolder.controller() }
    val audioState by audioPlayer.state.collectAsStateWithLifecycle()
    // 自动续章：**只在连续朗读中、且确实是新的一章时**才起播。
    // 旧实现把 autoContinue 也当 key，用户点「听书」后它会立刻用 startIndex=0 再播一遍，
    // 于是永远从第一段开始、还重复入队。
    var lastTtsChapter by remember { mutableStateOf("") }
    androidx.compose.runtime.LaunchedEffect(state.currentChapterId, autoContinue) {
        val c = state.flow.firstOrNull { it.id == state.currentChapterId } ?: return@LaunchedEffect
        if (!autoContinue || c.id == lastTtsChapter || c.paragraphs.isEmpty()) return@LaunchedEffect
        lastTtsChapter = c.id
        // 正文段落读取时已套过替换规则，这里不能再套一遍：
        // 「a => aa」这类规则会连跳两次，念出来是原文的四倍。
        tts.speak(c.paragraphs, 0, key = c.id)
    }

    // 同一章重复点「播放」也要重新起播：data class 值相同不会触发 effect，
    // 因此用请求序号做 key（LibraryViewModel 每次取到地址就自增）。
    androidx.compose.runtime.LaunchedEffect(state.audioPlayRequest) {
        if (state.audioPlayRequest == 0) return@LaunchedEffect
        val a = state.audioCurrent ?: return@LaunchedEffect
        // 首次听书时申请通知权限：没有它前台服务仍能播，但看不到控制条
        (hostContext as? MainActivity)?.requestNotificationPermissionIfNeeded()
        audioPlayer.play(a.chapterId, a.title, a.cachedUrl.ifEmpty { if (a.encrypted) "" else a.url })
        // 起播后拉起前台服务：锁屏/离开页面仍可播，并从通知栏控制
        if (audioPlayer.state.value.prepared || audioPlayer.state.value.playing) {
            PlaybackService.start(hostContext)
        }
    }

    // 播放进入「准备完成」时也补一次服务启动：prepareAsync 是异步的，
    // 上面那次判断可能还没 prepared。
    androidx.compose.runtime.LaunchedEffect(audioState.prepared) {
        if (audioState.prepared) PlaybackService.start(hostContext)
    }

    // 服务自己观察控制器状态：真正空闲时它自行 stopForeground + stopSelf。
    // 这里**不要**再发一次停止指令——连播时上一集结束到下一集 prepare 完成之间
    // 必然出现「未准备且未播放」的瞬间，外部插一脚会把刚起播的新一集打死
    // （实测：片花停在 0:00 且不播）。

    // 离开阅读页仍要停 TTS（它没有通知栏控制）；音频则交给前台服务继续播。
    androidx.compose.runtime.DisposableEffect(screen) {
        val leaving = screen
        onDispose {
            if (leaving == Screen.Reader) {
                autoContinue = false
                tts.stop()
            }
        }
    }
    // 通知栏「上一集/下一集」：服务拿不到章节清单，由界面注册回调
    androidx.compose.runtime.DisposableEffect(state.audioChapters, audioState.chapterId) {
        val list = state.audioChapters
        AudioPlaybackHolder.command = { action ->
            val idx = list.indexOfFirst { it.id == audioState.chapterId }
            val target = when (action) {
                com.alanmaster.xread.data.PlaybackService.ACTION_NEXT -> list.getOrNull(idx + 1)
                com.alanmaster.xread.data.PlaybackService.ACTION_PREV -> list.getOrNull(idx - 1)
                else -> null
            }
            target?.let { vm.playAudio(it) }
        }
        onDispose { AudioPlaybackHolder.command = null }
    }
    // 播完自动下一集：控制器回调里按当前章节定位下一条
    androidx.compose.runtime.LaunchedEffect(state.audioChapters, audioState.chapterId) {
        val chapters = state.audioChapters
        val current = audioState.chapterId
        audioPlayer.onFinished = {
            val idx = chapters.indexOfFirst { it.id == current }
            chapters.getOrNull(idx + 1)?.let { vm.playAudio(it) }
        }
    }

    androidx.compose.runtime.SideEffect {
        com.alanmaster.xread.ui.ReaderPager.active =
            screen == Screen.Reader && state.readerPrefs.volumeTurn
    }

    val snackbar = remember { mutableStateOf("") }
    /*
     * 撤销宿主。应用里原本只有 Toast，Toast 没有动作按钮，「移出书架」这类
     * 一失手就丢数据（含分组）的操作就没法挽回，所以另起一个 SnackbarHost。
     */
    val undoHost = remember { androidx.compose.material3.SnackbarHostState() }
    LaunchedEffect(state.undoRemove) {
        val items = state.undoRemove
        if (items.isEmpty()) return@LaunchedEffect
        val result = undoHost.showSnackbar(
            message = "已移出 ${items.size} 本",
            actionLabel = "撤销",
            duration = androidx.compose.material3.SnackbarDuration.Short,
        )
        if (result == androidx.compose.material3.SnackbarResult.ActionPerformed) vm.undoRemoveBooks()
    }
    LaunchedEffect(state.message) {
        if (state.message.isNotEmpty()) {
            snackbar.value = state.message
            vm.clearMessage()
        }
    }

    LaunchedEffect(Unit) {
        vm.loadReaderPrefs()
        vm.loadDownloadPolicy()
        vm.loadReadingGoal()
        vm.loadReminder()
        vm.checkOnboarding()
        vm.loadShelf()
        vm.loadRecentDramas()
        vm.loadDramaFavorites()
        vm.loadHot()
        vm.loadFeed()
        statsSnapshot = offline.stats()
        rulesSnapshot = vm.rules()
    }

    // 统计数字只在进「工具」页时刷新一次：统计卡要的是全量累计，读者在页内停留期间
    // 不太可能同时在读书；而每次结算都回读 stats.json 会让翻页多一次读盘。
    LaunchedEffect(screen) {
        if (screen is Screen.Tools) statsSnapshot = offline.stats()
    }

    // 目录裁剪标记：进目录时扫一次盘；下载推进时（done 变化）再扫，圆点跟着章走。
    LaunchedEffect(screen, download.done) {
        if (screen is Screen.Toc) vm.refreshCachedChapters()
    }

    // 冷启动回到上次阅读的位置：进度已经在本地 progress.json 里，这里只负责「打开哪本书、进哪个页面」。
    // 用 rememberSaveable 记一次，配置变化（旋转、切深浅色）不会重复把读者拽进阅读页。
    var restoredSession by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (restoredSession) return@LaunchedEffect
        restoredSession = true
        val last = vm.lastSessionBook() ?: return@LaunchedEffect
        readerFromShelf = true
        vm.openBook(last)
        screen = Screen.Reader
    }

    // 系统返回键：面板 → 详情 → 书架，逐级回退；书架处两次退出。
    var lastBack by remember { mutableStateOf(0L) }
    val activity = hostContext as? MainActivity
    androidx.compose.runtime.LaunchedEffect(showSettings, screen, state.paragraphCommentOpen, readerFromShelf) {
        activity?.setAppBackHandler {
            when {
                showSettings -> showSettings = false
                // 段评面板是盖在正文上的浮层：先关它，别把读者直接踢回详情页
                state.paragraphCommentOpen -> vm.closeParagraphComments()
                screen is Screen.Reader -> {
                    vm.flushStats(force = true)
                    if (readerFromShelf) {
                        readerFromShelf = false
                        vm.loadShelf()
                        screen = mainTab
                    } else {
                        state.book?.let { screen = Screen.Detail(it) }
                    }
                }
                screen is Screen.Reviews -> state.book?.let { screen = Screen.Detail(it) }
                screen is Screen.Author -> state.book?.let { screen = Screen.Detail(it) }
                screen is Screen.Toc -> state.book?.let { screen = Screen.Detail(it) }
                screen is Screen.Audio -> {
                    if (audioFromFeed) {
                        screen = Screen.AudioFeed
                    } else if (audioFromSearch) {
                        screen = Screen.Search
                    } else {
                        state.book?.let { screen = Screen.Detail(it) }
                    }
                }
                screen is Screen.AudioFeed -> screen = Screen.Discover
                screen is Screen.CategoryBooks -> screen = Screen.Category
                screen is Screen.Drama -> {
                    if (dramaBackToSwipe) {
                        dramaBackToSwipe = false
                        vm.closeDramaPlayer()
                        screen = Screen.DramaSwipe
                    } else {
                        if (state.currentDrama != null) {
                            vm.closeDramaPlayer()
                        }
                        screen = Screen.Discover
                    }
                }
                screen is Screen.DramaDouyin || screen is Screen.DramaSwipe -> {
                    if (state.currentDrama != null) {
                        vm.closeDramaPlayer()
                    }
                    screen = Screen.Discover
                }
                screen is Screen.Detail -> {
                    if (detailFromReader) {
                        detailFromReader = false
                        screen = Screen.Reader
                    } else {
                        screen = mainTab
                        vm.loadShelf()
                    }
                }
                screen is Screen.Category -> screen = Screen.Discover
                screen is Screen.Rank -> screen = Screen.Discover
                screen is Screen.Search -> screen = mainTab
                screen is Screen.Marks -> screen = Screen.Tools
                screen == Screen.Discover || screen == Screen.Tools -> screen = Screen.Shelf
                screen == Screen.Shelf -> {
                    val now = System.currentTimeMillis()
                    if (now - lastBack < 2000) activity.finish()
                    else { lastBack = now; snackbar.value = "再按一次退出拾页" }
                }
                else -> screen = Screen.Shelf
            }
        }
    }
    val isMainTab = screen == Screen.Shelf || screen == Screen.Discover || screen == Screen.Tools
    Scaffold { innerPadding ->
        Box(
            androidx.compose.ui.Modifier
                .fillMaxSize()
                .padding(bottom = innerPadding.calculateBottomPadding())
        ) {
            when (val s = screen) {
                Screen.Shelf -> ShelfScreen(
                    shelf = state.shelf,
                    loading = state.loading,
                    shelfSort = state.shelfSort,
                    shelfGroup = state.shelfGroupFilter,
                    shelfGroups = state.shelfAll.map { it.group }.filter { it.isNotBlank() }.distinct().sorted(),
                    builtinGroups = ShelfGrouping.summarize(state.shelfAll),
                    shelfMediaType = state.shelfMediaType,
                    onSelectMediaType = { vm.setShelfMediaType(it) },
                    shelfStatus = state.shelfStatusFilter,
                    onStatusFilter = { vm.setShelfStatusFilter(it) },
                    recentDramas = state.recentDramas,
                    onOpenDrama = { drama ->
                        vm.loadDramaDetail(drama.seriesId, drama.isMotionComic, autoPlayFirst = true)
                        screen = Screen.Drama
                    },
                    updates = state.shelfUpdates,
                    checkingUpdates = state.checkingUpdates,
                    onSort = { vm.setShelfSort(it) },
                    onGroupFilter = { vm.setShelfGroupFilter(it) },
                    onCheckUpdates = { vm.checkUpdates() },
                    onLongPressBook = { b ->
                        groupTargets = listOf(b)
                        groupDraft = state.shelfAll.firstOrNull { it.book.bookId == b.bookId }?.group.orEmpty()
                    },
                    onBatchGroup = { books ->
                        // 多选时的分组起点留空：混选的几本大概率分组不同，预填谁都不对。
                        groupDraft = ""
                        groupTargets = books
                    },
                    onBatchRemove = { books -> vm.removeBooksFromShelf(books) },
                    onBatchCache = { books -> vm.cacheBooks(books) },
                    onBatchFinish = { books -> vm.markBooksFinished(books) },
                    onRefresh = { vm.loadShelf() },
                    shelfRefreshDone = state.shelfRefreshDone,
                    onPullRefresh = { vm.refreshShelf() },
                    onSearch = {
                        vm.resetSearchView()
                        vm.loadSearchHistory()
                        // 搜索页的「大家在搜」用同一份热搜词，取不到就补一次。
                        if (state.hot.isEmpty()) vm.loadHot()
                        screen = Screen.Search
                    },
                    onSettings = { showSettings = true },
                    onDiscover = { screen = Screen.Discover },
                    // 追剧空态落在短剧板块（section 2），而不是小说首页再让用户自己切。
                    onDiscoverDrama = {
                        vm.setDiscoverSection(2)
                        screen = Screen.Discover
                    },
                    // 书架是「继续阅读」的地方：点书直接进正文，不再先过详情页。
                    onOpen = { b ->
                        readerFromShelf = true
                        vm.openBook(b)
                        screen = Screen.Reader
                    },
                    onContinue = {
                        readerFromShelf = true
                        vm.openBook(it)
                        screen = Screen.Reader
                    },
                    dramaFavorites = state.dramaFavorites,
                    onOpenFavoriteDrama = { drama ->
                        vm.loadDramaDetail(drama.seriesId, drama.isMotionComic, autoPlayFirst = true)
                        screen = Screen.Drama
                    },
                    onToggleFavoriteDrama = { vm.toggleDramaFavorite(it) },
                )

                Screen.Discover -> DiscoverScreen(
                    section = state.discoverSection,
                    onSelectSection = { sec -> vm.setDiscoverSection(sec) },
                    ranked = state.feedRanked,
                    recommended = state.feedRecommended,
                    newBooks = state.feedNew,
                    boards = state.feedBoards,
                    moreLoading = state.feedMoreLoading,
                    feedError = state.feedError,
                    exhausted = state.feedExhausted,
                    loading = state.loading,
                    mangaFeed = state.mangaFeed,
                    mangaLoading = state.mangaFeedLoading,
                    mangaMoreLoading = state.mangaMoreLoading,
                    mangaExhausted = state.mangaExhausted,
                    onMoreManga = { vm.loadMoreManga() },
                    mangaCategory = state.mangaCategory,
                    onSelectMangaCategory = { cat -> vm.setMangaCategory(cat) },
                    dramaFeed = state.dramaFeed,
                    dramaLoading = state.dramaFeedLoading,
                    dramaMoreLoading = state.dramaMoreLoading,
                    dramaExhausted = state.dramaExhausted,
                    onMoreDrama = { vm.loadMoreDrama() },
                    dramaCategory = state.dramaCategory,
                    onSelectDramaCategory = { cat -> vm.setDramaCategory(cat) },
                    onOpenDrama = { drama ->
                        vm.loadDramaDetail(drama.seriesId, drama.isMotionComic, autoPlayFirst = true)
                        screen = Screen.Drama
                    },
                    onOpenDramaDouyin = { drama ->
                        if (drama != null) {
                            vm.loadDramaDetail(drama.seriesId, drama.isMotionComic, autoPlayFirst = true)
                        } else if (state.dramaFeed.isNotEmpty()) {
                            val first = state.dramaFeed.first()
                            vm.loadDramaDetail(first.seriesId, first.isMotionComic, autoPlayFirst = true)
                        }
                        screen = Screen.DramaDouyin
                    },
                    onRefresh = {
                        when (state.discoverSection) {
                            0 -> { vm.loadHot(); vm.loadFeed() }
                            1 -> vm.loadMangaFeed()
                            2 -> vm.loadDramaFeed()
                        }
                    },
                    onSearch = {
                        vm.resetSearchView()
                        vm.loadSearchHistory()
                        // 搜索页的「大家在搜」用同一份热搜词，取不到就补一次。
                        if (state.hot.isEmpty()) vm.loadHot()
                        screen = Screen.Search
                    },
                    onAudio = {
                        vm.loadAudioFeed()
                        screen = Screen.AudioFeed
                    },
                    onRank = { vm.loadRankings(); screen = Screen.Rank },
                    onCategory = { vm.loadCategories(); screen = Screen.Category },
                    onBoard = { algo -> vm.loadRankings(algo, "0"); screen = Screen.Rank },
                    onMore = { vm.loadMoreFeed() },
                    onOpen = { b -> vm.openBook(b); screen = Screen.Detail(b) },
                )

                Screen.Drama -> DramaScreen(
                    feed = state.dramaFeed,
                    feedLoading = state.dramaFeedLoading,
                    currentDrama = state.currentDrama,
                    currentEpisode = state.currentEpisode,
                    currentVideoSource = state.currentVideoSource,
                    videoLoading = state.dramaVideoLoading,
                    videoError = state.dramaVideoError,
                    danmakuList = state.danmakuList,
                    onBack = {
                        if (dramaBackToSwipe) {
                            dramaBackToSwipe = false
                            vm.closeDramaPlayer()
                            screen = Screen.DramaSwipe
                        } else if (state.currentDrama != null) {
                            vm.closeDramaPlayer()
                        } else {
                            screen = Screen.Discover
                        }
                    },
                    onSelectDrama = { item ->
                        vm.loadDramaDetail(item.seriesId, item.isMotionComic, autoPlayFirst = true)
                    },
                    onSelectEpisode = { ep ->
                        vm.playDramaEpisode(ep)
                    },
                    onSendDanmaku = { text, offsetMs ->
                        vm.sendDanmaku(text, offsetMs)
                    },
                    onRefreshFeed = {
                        vm.loadDramaFeed()
                    },
                    onSwitchToDouyin = {
                        screen = Screen.DramaDouyin
                    },
                    favorites = state.dramaFavorites,
                    onToggleFavorite = { vm.toggleDramaFavorite(it) },
                )

                Screen.DramaDouyin -> DramaDouyinScreen(
                    drama = state.currentDrama,
                    currentEpisode = state.currentEpisode,
                    currentVideoSource = state.currentVideoSource,
                    videoLoading = state.dramaVideoLoading,
                    videoError = state.dramaVideoError,
                    danmakuList = state.danmakuList,
                    feed = state.dramaFeed,
                    favorites = state.dramaFavorites,
                    onToggleFavorite = { vm.toggleDramaFavorite(it) },
                    onBack = {
                        if (state.currentDrama != null) {
                            vm.closeDramaPlayer()
                        }
                        screen = Screen.Discover
                    },
                    onSelectDrama = { item ->
                        vm.loadDramaDetail(item.seriesId, item.isMotionComic, autoPlayFirst = true)
                    },
                    onSelectEpisode = { ep ->
                        vm.playDramaEpisode(ep)
                    },
                    onSendDanmaku = { text, offsetMs ->
                        vm.sendDanmaku(text, offsetMs)
                    },
                    onSwitchToClassic = {
                        screen = Screen.Drama
                    },
                )

                Screen.DramaSwipe -> DramaSwipeScreen(
                    feed = state.dramaFeed,
                    initialPage = dramaSwipePage,
                    onPageChanged = { dramaSwipePage = it },
                    drama = state.currentDrama,
                    currentEpisodeId = state.currentEpisode?.episodeId,
                    currentVideoSource = state.currentVideoSource,
                    videoLoading = state.dramaVideoLoading,
                    videoError = state.dramaVideoError,
                    onBack = {
                        if (state.currentDrama != null) {
                            vm.closeDramaPlayer()
                        }
                        screen = Screen.Discover
                    },
                    onLoadFirstEpisode = { item ->
                        if (state.currentDrama?.seriesId != item.seriesId) {
                            vm.loadDramaDetail(item.seriesId, item.isMotionComic, autoPlayFirst = true)
                        }
                    },
                    onOpenDetailNextEpisode = { item ->
                        val loaded = state.currentDrama?.takeIf { it.seriesId == item.seriesId }
                        val nextEpisode = loaded?.episodes?.getOrNull(1)
                        dramaBackToSwipe = true
                        if (loaded != null && nextEpisode != null) {
                            vm.playDramaEpisode(nextEpisode)
                            screen = Screen.Drama
                        } else {
                            vm.loadDramaDetail(item.seriesId, item.isMotionComic, autoPlayFirst = false)
                            screen = Screen.Drama
                        }
                    },
                    onRetryVideo = {
                        state.currentEpisode?.let { vm.playDramaEpisode(it) }
                    },
                    favorites = state.dramaFavorites,
                    onToggleFavorite = { vm.toggleDramaFavorite(it) },
                    onLoadMore = { vm.loadMoreDrama() },
                    feedLoading = state.dramaFeedLoading,
                    feedError = state.dramaFeedError,
                    onReloadFeed = { vm.loadDramaFeed() },
                )

        Screen.Search -> SearchScreen(
            initialQuery = query,
            results = state.results,
            loading = state.loading,
            searched = state.searched,
            error = state.searchError,
            history = state.searchHistory,
            hotWords = state.hot.map { it.word },
            searchTabType = state.searchTabType,
            onSelectTabType = { tab, q ->
                // setSearchTabType 内部已按需重搜；再调一次 search 会连发两个请求，
                // 后到的那个还可能把分页游标写回第一页
                vm.setSearchTabType(tab, q)
            },
            dramaResults = state.dramaResults,
            exhausted = state.searchExhausted,
            moreLoading = state.searchMoreLoading,
            onMore = { vm.loadMoreSearch() },
            onOpenDrama = { drama ->
                vm.loadDramaDetail(drama.seriesId, drama.isMotionComic, autoPlayFirst = true)
                screen = Screen.Drama
            },
            libraryAvailable = state.offlineBooks.isNotEmpty(),
            libraryHits = state.libraryHits,
            librarySearching = state.librarySearching,
            libraryDone = state.libraryDone,
            libraryScanned = state.libraryScanned,
            libraryTotal = state.libraryTotal,
            onLibrarySearch = { q -> vm.searchLibrary(q) },
            onLibraryOpen = { hit -> vm.openTextHit(hit) { readerFromShelf = true; screen = Screen.Reader } },
            onBack = { screen = mainTab },
            onSearch = { q -> query = q; vm.search(q) },
            onPickHistory = { w -> query = w; vm.search(w) },
            onClearHistory = { vm.clearSearchHistory() },
            onOpen = { b ->
                // 综合 tab 会把小说和有声书混在一条列表里，按条目自己判断，不能只看 tab
                if (b.isAudio || state.searchTabType == 2) {
                    // 听书 tab 的 book_id 就是有声书 id，直接进听书页
                    audioBook = b
                    audioFromFeed = false
                    audioFromSearch = true
                    vm.openAudio(b)
                    screen = Screen.Audio
                } else {
                    vm.openBook(b)
                    screen = Screen.Detail(b)
                }
            },
        )

        Screen.Rank -> RankScreen(
            books = state.rankings,
            catalog = state.rankCatalog,
            algorithm = state.rankAlgorithm,
            categoryId = state.rankCategory,
            loading = state.loading,
            onBack = { screen = Screen.Discover },
            onPick = { algo, cat -> vm.loadRankings(algo, cat) },
            onOpen = { b -> vm.openBook(b); screen = Screen.Detail(b) },
        )

        is Screen.Detail -> DetailScreen(
            book = state.book ?: s.book,
            chapters = state.chapters,
            related = state.related,
            download = download,
            inShelf = state.shelfAll.any { it.book.bookId == (state.book ?: s.book).bookId },
            // 标记只对架内的书有意义；查不到条目时给默认值，不给 null 分支
            shelfStatus = state.shelfAll.firstOrNull { it.book.bookId == (state.book ?: s.book).bookId }
                ?.status ?: com.alanmaster.xread.data.ShelfItem.STATUS_READING,
            shelfRating = state.shelfAll.firstOrNull { it.book.bookId == (state.book ?: s.book).bookId }
                ?.rating ?: 0,
            shelfTags = state.shelfAll.firstOrNull { it.book.bookId == (state.book ?: s.book).bookId }
                ?.tags ?: emptyList(),
            onSetStatus = { st -> (state.book ?: s.book).let { vm.setBookStatus(it.bookId, st) } },
            onSetRating = { r -> (state.book ?: s.book).let { vm.setBookRating(it.bookId, r) } },
            onSetTags = { t -> (state.book ?: s.book).let { vm.setBookTags(it.bookId, t) } },
            resumeChapterId = state.resumeChapterId,
            resumeParagraph = state.resumeParagraph,
            onBack = {
                if (detailFromReader) {
                    detailFromReader = false
                    screen = Screen.Reader
                } else {
                    screen = mainTab
                    vm.loadShelf()
                }
            },
            onRead = { c -> readerFromShelf = false; detailFromReader = false; vm.loadChapter(c.id); screen = Screen.Reader },
            onShelf = {
                val b = state.book ?: s.book
                if (state.shelfAll.any { it.book.bookId == b.bookId }) vm.removeFromShelf(b) else vm.addToShelf(b)
            },
            onDownload = { max -> (state.book ?: s.book).let { vm.startDownload(it, max) }; vm.refreshOffline() },
            onCancel = { vm.cancelDownload() },
            onOpenRelated = { r -> vm.openBook(r); screen = Screen.Detail(r) },
            onOpenReviews = { vm.loadComments(); screen = Screen.Reviews },
            onOpenAuthor = { id -> vm.loadAuthor(id); screen = Screen.Author },
            onOpenToc = { screen = Screen.Toc },
            onOpenAudio = {
                val b = state.book ?: s.book
                audioBook = b
                audioFromFeed = false
                audioFromSearch = false
                vm.openAudio(b)
                screen = Screen.Audio
            },
            onShare = {
                val b = state.book ?: s.book
                vm.loadShare(b.bookId) { info ->
                    val text = buildList {
                        add(info.copyLinkText.ifBlank { "推荐《${b.title}》" })
                        if (info.subText.isNotBlank()) add(info.subText)
                        if (b.author.isNotBlank()) add("作者：${b.author}")
                        if (info.shareUrl.isNotBlank()) add(info.shareUrl)
                    }.joinToString("\n")
                    val send = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_SUBJECT, b.title)
                        putExtra(Intent.EXTRA_TEXT, text)
                    }
                    runCatching { ctx.startActivity(Intent.createChooser(send, "分享《${b.title}》")) }
                }
            },
        )

        Screen.Reviews -> ReviewScreen(
            bookTitle = state.book?.title.orEmpty(),
            comments = state.comments,
            total = state.commentTotal,
            raters = state.commentRaters,
            context = state.commentContext,
            loading = state.commentsLoading,
            hasMore = state.commentsHasMore,
            onBack = { screen = Screen.Detail(state.book ?: return@ReviewScreen) },
            onMore = { vm.loadMoreComments() },
            onToggleCommentDigg = { cId -> vm.toggleCommentDigg(cId) },
            onToggleReplyDigg = { cId, rId -> vm.toggleReplyDigg(cId, rId) },
            onLoadReplies = { cId -> vm.loadReplies(cId) },
            onLoadMoreReplies = { cId -> vm.loadMoreReplies(cId) },
        )

        Screen.Category -> CategoryScreen(
            categories = state.categories,
            loading = state.categoriesLoading,
            onBack = { screen = Screen.Discover },
            onPick = { c -> vm.openCategory(c); screen = Screen.CategoryBooks },
        )

        Screen.CategoryBooks -> CategoryBooksScreen(
            title = state.categoryTitle,
            books = state.categoryBooks,
            loading = state.categoryLoading,
            onBack = { screen = Screen.Category },
            onOpen = { b -> vm.openBook(b); screen = Screen.Detail(b) },
        )

        Screen.AudioFeed -> AudioFeedScreen(
            books = state.audioFeedBooks,
            loading = state.audioFeedLoading,
            error = state.audioFeedError,
            onBack = { screen = Screen.Discover },
            onRetry = { vm.loadAudioFeed() },
            onOpen = { b ->
                audioBook = b
                audioFromFeed = true
                audioFromSearch = false
                vm.openAudio(b)
                screen = Screen.Audio
            },
        )

        Screen.Audio -> AudioScreen(
            bookTitle = (audioBook ?: state.book)?.title.orEmpty(),
            chapters = state.audioChapters,
            tones = state.audioTones,
            tone = state.audioTone,
            current = state.audioCurrent,
            timeline = state.audioTimeline,
            loading = state.audioLoading,
            error = state.audioError,
            onBack = {
                if (audioFromFeed) screen = Screen.AudioFeed
                else if (audioFromSearch) screen = Screen.Search
                else screen = Screen.Detail(state.book ?: return@AudioScreen)
            },
            onPlay = { c -> vm.playAudio(c) },
            onTone = { tr -> vm.selectTone(tr) },
            playback = audioState,
            onToggle = { audioPlayer.toggle() },
            onSeek = { ms -> audioPlayer.endSeek(ms) },
            onRate = { r -> audioPlayer.setRate(r) },
            onSleep = { minutes -> audioPlayer.setSleepTimer(minutes) },
            onRetry = { vm.retryAudio() },
        )

        Screen.Toc -> TocScreen(
            bookTitle = state.book?.title.orEmpty(),
            chapters = state.chapters,
            currentChapterId = state.currentChapterId,
            cached = state.cachedChapters,
            downloading = download.running && download.bookId == state.book?.bookId,
            onCache = { chapter, count ->
                val index = state.chapters.indexOfFirst { it.id == chapter.id }
                if (index >= 0) vm.cacheChapters(index, count)
            },
            onUncache = { chapter -> vm.uncacheChapter(chapter.id) },
            tocRefreshDone = state.tocRefreshDone,
            onRefreshChapters = { vm.refreshChapters() },
            onFillWindow = { vm.fillWindow() },
            onBack = { screen = Screen.Detail(state.book ?: return@TocScreen) },
            onSelect = { id -> vm.loadChapter(id); screen = Screen.Reader },
        )

        Screen.Author -> AuthorScreen(
            author = state.author,
            loading = state.loading,
            onBack = { screen = Screen.Detail(state.book ?: return@AuthorScreen) },
            onOpen = { b -> vm.openBook(b); screen = Screen.Detail(b) },
        )

        Screen.Reader -> ReaderScreen(
            bookTitle = state.book?.title ?: "",
            chapters = state.chapters,
            flow = state.flow,
            currentChapterId = state.currentChapterId,
            loading = state.loading,
            error = state.readerError,
            flowLoading = state.flowLoading,
            flowError = state.flowError,
            onRetry = {
                // 目录还没拿到（多半是打开书时网络不通）时，重试要重新开书，
                // 而不是拿空章节号去请求 —— 那只会报「作品或章节编号格式错误」。
                val b = state.book
                if (b != null && state.chapters.isEmpty()) vm.openBook(b)
                else {
                    state.currentChapterId.takeIf { it.isNotEmpty() }?.let { vm.loadChapter(it) }
                        ?: vm.loadChapter(state.chapters.getOrNull(state.chapterIndex)?.id.orEmpty())
                }
            },
            onAppendNext = { vm.appendNextChapter() },
            onCacheNext = { vm.cacheNext() },
            flowLoadingPrev = state.flowLoadingPrev,
            onPrependPrev = { vm.prependPreviousChapter() },
            prefs = state.readerPrefs,
            initialParagraph = state.currentParagraph,
            marksByChapter = state.marksByChapter,
            onToggleMark = { chapId, i, t, k -> vm.toggleMark(chapId, i, t, k) },
            // 阅读页也能收书：不必退回详情页。
            inShelf = state.book?.let { b -> state.shelfAll.any { it.book.bookId == b.bookId } } == true,
            onToggleShelf = {
                val b = state.book
                if (b != null) {
                    if (state.shelfAll.any { it.book.bookId == b.bookId }) vm.removeFromShelf(b) else vm.addToShelf(b)
                }
            },
            onOpenDetail = {
                // 离开阅读页先把这一段的阅读时长结掉，跟按返回键时一致。
                vm.flushStats(force = true)
                state.book?.let { b -> detailFromReader = true; screen = Screen.Detail(b) }
            },
            scrollRequest = state.scrollRequest,
            scrollChapterId = state.scrollChapterId,
            scrollParagraph = state.scrollParagraph,
            onBack = {
                vm.flushStats(force = true)
                if (readerFromShelf) {
                    readerFromShelf = false
                    vm.loadShelf()
                    screen = mainTab
                } else {
                    screen = Screen.Detail(state.book ?: return@ReaderScreen)
                }
            },
            onSelect = { id -> vm.loadChapter(id) },
            onPrev = { vm.prevChapter() },
            onNext = { vm.nextChapter() },
            onPrefs = { vm.setReaderPrefs(it) },
            onProgress = { chapId, idx -> vm.setParagraph(chapId, idx) },
            ttsSpeaking = ttsSpeaking,
            ttsChapterId = lastTtsChapter.ifEmpty { state.currentChapterId },
            ttsParagraph = ttsParagraph,
            ttsRate = ttsRate,
            ttsMessage = ttsMessage,
            onTtsToggle = {
                if (ttsSpeaking) {
                    tts.pause()
                } else {
                    autoContinue = true
                    val c = state.flow.firstOrNull { it.id == state.currentChapterId }
                    val paras = c?.paragraphs.orEmpty()
                    if (paras.isNotEmpty()) {
                        // 记录当前章，避免上面那个 LaunchedEffect 把进度重置到第 0 段
                        lastTtsChapter = c?.id.orEmpty()
                        // 正文读取时已套过替换规则（见 LibraryViewModel.renderable），
                        // 这里再套一次会让「a => aa」连跳两档。
                        tts.speak(paras, state.currentParagraph, key = c?.id.orEmpty())
                    }
                }
            },
            onTtsRate = { r -> ttsRate = tts.setRate(r) },
            // 取消定时（0 分钟）时原来会朗读「定时 0 分钟」，等于什么都没说。
            onTtsSleep = { minutes ->
                tts.setSleepTimer(minutes)
                tts.announce(if (minutes <= 0) "已取消定时" else "定时 $minutes 分钟后停止朗读")
            },
            bubblesByChapter = state.bubblesByChapter,
            paragraphCommentOpen = state.paragraphCommentOpen,
            paragraphCommentIndex = state.paragraphCommentIndex,
            paragraphComments = state.paragraphComments,
            paragraphCommentTotal = state.paragraphCommentTotal,
            paragraphCommentsLoading = state.paragraphCommentsLoading,
            paragraphCommentError = state.paragraphCommentError,
            paragraphCommentsHasMore = state.paragraphCommentsHasMore,
            onOpenParagraphComments = { chapId, i -> vm.openParagraphComments(chapId, i) },
            onCloseParagraphComments = { vm.closeParagraphComments() },
            onMoreParagraphComments = { vm.loadMoreParagraphComments() },
            onToggleParagraphCommentDigg = { rId -> vm.toggleParagraphCommentDigg(rId) },
        )

        Screen.Marks -> MarksScreen(
            marks = state.allMarks,
            bookTitles = (state.offlineBooks.associate { it.bookId to it.title } +
                    listOfNotNull(state.book?.let { it.bookId to it.title })),
            onBack = { screen = Screen.Tools },
            onOpen = { e -> vm.openMark(e) { screen = Screen.Reader } },
            onDelete = { e -> vm.deleteMark(e) },
            onShare = { e ->
                val titles = (state.offlineBooks.associate { it.bookId to it.title } +
                        listOfNotNull(state.book?.let { it.bookId to it.title }))
                val ok = com.alanmaster.xread.data.MarkCard.share(
                    ctx, e, titles[e.bookId] ?: e.bookId)
                vm.notify(if (ok) "书摘卡片已生成" else "书摘卡片生成失败")
            },
            onExport = { vm.exportMarks() },
            onSaveNote = { entry, note, tags -> vm.saveMarkNote(entry, note, tags) },
        )

        Screen.Tools -> ToolsScreen(
            offlineBooks = state.offlineBooks,
            resumable = state.resumable,
            download = download,
            cacheQueueSize = state.cacheQueueSize,
            stats = statsSnapshot,
            rules = rulesSnapshot,
            storageTotal = state.storageTotal,
            bookSizes = state.bookSizes,
            leftoverCount = state.leftoverCount,
            leftoverBytes = state.leftoverBytes,
            onCleanLeftovers = { vm.cleanLeftovers() },
            onOpenMarks = { vm.loadAllMarks(); screen = Screen.Marks },
            policy = state.downloadPolicy,
            onPolicy = { vm.setDownloadPolicy(it) },
            goal = state.readingGoal,
            todaySeconds = state.todaySeconds,
            onGoal = { vm.setReadingGoal(it) },
            reminder = state.reminder,
            onReminder = { reminder ->
                vm.setReminder(reminder)
                if (!reminder.enabled) {
                    com.alanmaster.xread.data.ReminderScheduler.cancel(hostContext)
                } else if (notifyAllowed) {
                    com.alanmaster.xread.data.ReminderScheduler.schedule(hostContext, reminder)
                } else {
                    (hostContext as? MainActivity)
                        ?.requestNotificationPermissionIfNeeded { granted ->
                            notifyAllowed = granted
                            if (granted) {
                                com.alanmaster.xread.data.ReminderScheduler
                                    .schedule(hostContext, reminder)
                            } else {
                                vm.notify("没有通知权限，阅读提醒发不出来。可在系统设置里允许「拾页」发送通知后再打开。")
                            }
                        }
                }
            },
            notificationAllowed = notifyAllowed,
            onRequestNotification = {
                (hostContext as? MainActivity)
                    ?.requestNotificationPermissionIfNeeded { granted ->
                        notifyAllowed = granted
                        if (granted && state.reminder.enabled) {
                            com.alanmaster.xread.data.ReminderScheduler
                                .schedule(hostContext, state.reminder)
                        }
                    }
            },
            newChapterNotify = state.newChapterNotify,
            onNewChapterNotify = { enabled ->
                if (!enabled) {
                    vm.setNewChapterNotify(false)
                    com.alanmaster.xread.data.UpdateScheduler.cancel(hostContext)
                } else {
                    // Android 13 起没有通知权限时开关照样能打开，但系统会把通知静默丢掉，
                    // 用户只会看到「开着」却永远等不到提醒。这里先要权限再决定开关状态。
                    (hostContext as? MainActivity)
                        ?.requestNotificationPermissionIfNeeded { granted ->
                            vm.setNewChapterNotify(granted)
                            if (granted) {
                                com.alanmaster.xread.data.UpdateScheduler.schedule(hostContext)
                            } else {
                                vm.notify("没有通知权限，新章提醒发不出来。可在系统设置里允许「拾页」发送通知后再打开。")
                            }
                        }
                        ?: run {
                            vm.setNewChapterNotify(true)
                            com.alanmaster.xread.data.UpdateScheduler.schedule(hostContext)
                        }
                }
            },
            ttsRunning = ttsSpeaking,
            onBack = null,
            onOpen = { b -> vm.openOfflineBook(b); screen = Screen.Reader },
            onExportBook = { b -> vm.exportBookText(b) },
            onDelete = { b -> vm.deleteOfflineBook(b.bookId) },
            onCancelDownload = { vm.cancelDownloads() },
            onResume = { task ->
                vm.resumeDownload(Book(bookId = task.bookId, title = task.title, author = task.author))
                vm.refreshOffline()
            },
            onSaveRules = { list -> vm.saveRules(list); rulesSnapshot = list },
            onExport = { vm.exportBackup() },
            // 导入是「恢复」，统计与规则都要立刻重读：只刷规则会留下导入前的旧数字，
            // 用户以为没导入成功，再点一次（旧实现重复导入还会把统计翻倍）。
            onImport = {
                vm.importBackup {
                    rulesSnapshot = vm.rules()
                    statsSnapshot = offline.stats()
                }
            },
            onSpeak = {
                val c = state.flow.firstOrNull { it.id == state.currentChapterId }
                val paras = c?.paragraphs.orEmpty()
                if (paras.isEmpty()) {
                    // 冷启动直接进「我的」时 currentChapterId 还是空的：
                    // 旧实现什么都不做，按钮像坏了一样。这里必须说清楚要先打开一本书。
                    vm.notify("先打开一本书再朗读")
                } else {
                    autoContinue = false
                    lastTtsChapter = c?.id.orEmpty()
                    // 同阅读页：段落已带替换结果，不再重复套规则。
                    tts.speak(paras, 0, key = c?.id.orEmpty())
                }
            },
            onStopSpeak = { autoContinue = false; tts.stop() },
            )
            }

            // 首启引导盖在最上层：它只是引导，不该改导航栈，否则返回键会把人带回一个空书架。
            if (state.showOnboarding) {
                com.alanmaster.xread.ui.OnboardingScreen(
                    prefs = state.readerPrefs,
                    onPrefs = { vm.setReaderPrefs(it) },
                    onImport = {
                        vm.importBackup {
                            rulesSnapshot = vm.rules()
                            statsSnapshot = offline.stats()
                        }
                    },
                    onDone = { vm.finishOnboarding() },
                )
            }

            // 撤销提示：Toast 没有动作按钮，「移出书架」这种会连分组一起丢的操作没法挽回。
            androidx.compose.material3.SnackbarHost(
                hostState = undoHost,
                modifier = androidx.compose.ui.Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = Space.navFloat),
            )

            // 悬浮底栏：叠在内容之上而不是占一行高度，页面背景一直铺到屏幕下缘。
            if (isMainTab) {
                FloatingNavBar(
                    items = mainNavItems,
                    selectedIndex = when (screen) {
                        Screen.Shelf -> 0
                        Screen.Discover -> 1
                        Screen.Tools -> 3
                        else -> 1
                    },
                    onSelect = { index ->
                        when (index) {
                            0 -> screen = Screen.Shelf
                            1 -> {
                                if (state.hot.isEmpty() || state.feedRanked.isEmpty()) {
                                    vm.loadHot()
                                    vm.loadFeed()
                                }
                                screen = Screen.Discover
                            }
                            2 -> {
                                if (state.dramaFeed.isEmpty()) vm.loadDramaFeed()
                                screen = Screen.DramaSwipe
                            }
                            else -> {
                                vm.flushStats(force = true)
                                statsSnapshot = offline.stats()
                                rulesSnapshot = vm.rules()
                                vm.refreshOffline()
                                screen = Screen.Tools
                            }
                        }
                    },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = Space.md),
                )
            }
        }
    }

    if (groupTargets.isNotEmpty()) {
        val target = groupTargets.first()
        val many = groupTargets.size > 1
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { groupTargets = emptyList() },
            title = {
                androidx.compose.material3.Text(
                    if (many) "分组 · 已选 ${groupTargets.size} 本" else "分组 · ${target.title}"
                )
            },
            text = {
                androidx.compose.foundation.layout.Column {
                    androidx.compose.material3.OutlinedTextField(
                        value = groupDraft,
                        onValueChange = { groupDraft = it },
                        label = { androidx.compose.material3.Text("分组名（留空移出分组）", fontSize = 12.sp) },
                        singleLine = true,
                    )
                    val existing = state.shelfAll.map { it.group }.filter { it.isNotBlank() }.distinct().sorted()
                    if (existing.isNotEmpty()) {
                        androidx.compose.foundation.layout.Row(
                            androidx.compose.ui.Modifier.padding(top = 8.dp),
                            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(6.dp),
                        ) {
                            existing.take(3).forEach { g ->
                                androidx.compose.material3.TextButton(onClick = { groupDraft = g }) {
                                    androidx.compose.material3.Text(g, fontSize = 12.sp)
                                }
                            }
                        }
                    }
                    // 书架点书直接进正文，详情页改从这里进。多选时没有「这一本」，不给入口。
                    if (!many) {
                        androidx.compose.material3.TextButton(onClick = {
                            groupTargets = emptyList()
                            vm.openBook(target)
                            screen = Screen.Detail(target)
                        }) { androidx.compose.material3.Text("查看书籍详情", fontSize = 12.sp) }
                    }
                }
            },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = {
                    if (many) vm.moveBooksToGroup(groupTargets, groupDraft) else vm.setGroup(target, groupDraft)
                    groupTargets = emptyList()
                }) { androidx.compose.material3.Text("保存") }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { groupTargets = emptyList() }) {
                    androidx.compose.material3.Text("取消")
                }
            },
        )
    }
    if (showSettings) {
        SettingsSheet(
            prefs = state.readerPrefs,
            onPrefs = { vm.setReaderPrefs(it) },
            onDismiss = { showSettings = false },
            onRetry = { showSettings = false; vm.refreshAll() },
            update = updateState,
            onCheckUpdate = { updater.check() },
            onDownloadUpdate = { updater.download() },
            onInstallUpdate = { updater.install() },
            onHotUpdate = { updater.setHotUpdate(it) },
            onUpdateSource = { source, token -> updater.saveSource(source, token) },
            onUpdateMirror = { updater.setMirror(it) },
        )
    }
    // 阅读时长按「前台停留」结算：进后台立即结算并暂停计时，
    // 否则按 Home 挂一小时再回来，统计里就多出一小时。
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    androidx.compose.runtime.DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            when (event) {
                androidx.lifecycle.Lifecycle.Event.ON_START -> vm.resumeReading()
                androidx.lifecycle.Lifecycle.Event.ON_STOP -> vm.pauseReading()
                androidx.lifecycle.Lifecycle.Event.ON_DESTROY -> vm.pauseReading()
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    androidx.compose.runtime.LaunchedEffect(snackbar.value) {
        val text = snackbar.value
        if (text.isNotEmpty()) {
            android.widget.Toast.makeText(hostContext, text, android.widget.Toast.LENGTH_SHORT).show()
            snackbar.value = ""
        }
    }
}
