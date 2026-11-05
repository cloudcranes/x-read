package com.alanmaster.xread.data

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * 阅读预取：把「当前章之后 N 章」缺的那些悄悄拉到磁盘。
 *
 * 与 [Downloader] 分开而不是塞进去，因为两者的优先级完全相反：
 * 用户点的下载必须抢占，预取必须随时让路。塞进同一个单任务循环里，要么
 * 预取把用户任务顶掉，要么用户任务把预取饿死，两种都难解释。
 *
 * 让路方式是**挂起等待**而不是取消：用户下载跑完，预取接着从断点继续，不必重算缺口。
 * 一次只发一个请求，失败静默——背景动作不该弹任何提示。
 */
class Prefetcher(
    private val api: Api,
    private val offline: OfflineRepo,
    private val scope: CoroutineScope,
    /** 手动下载是否正在跑。 */
    private val manualBusy: () -> Boolean,
    /** 当前是否 Wi-Fi/以太网；仅在 [DownloadPolicy.wifiOnly] 下参与判断。 */
    private val onUnmetered: () -> Boolean = { true },
    /** 每落盘一章回调一次：目录页圆点据此逐个变实。 */
    private val onSaved: () -> Unit = {},
    @Volatile var policy: DownloadPolicy = DownloadPolicy(),
) {
    private var job: Job? = null
    /** 上次请求的缺口签名：翻一章会重算一次，缺口没变就别重启任务。 */
    private var signature: String = ""

    fun request(book: Book, chapters: List<Chapter>, index: Int) {
        val count = policy.prefetch
        if (count <= 0 || index < 0 || chapters.isEmpty()) return cancel()
        val from = index + 1
        val to = (index + count).coerceAtMost(chapters.lastIndex)
        if (from > to) return cancel()
        val missing = (from..to)
            .map { chapters[it] }
            .filter { !offline.hasChapter(book.bookId, it.id) }
        if (missing.isEmpty()) return cancel()
        val next = book.bookId + ":" + missing.joinToString(",") { it.id }
        if (next == signature && job?.isActive == true) return
        signature = next
        job?.cancel()
        job = scope.launch(Dispatchers.IO) { run(book, missing) }
    }

    private suspend fun run(book: Book, queue: List<Chapter>) {
        for (chapter in queue) {
            while (manualBusy() && currentCoroutineContext().isActive) delay(600)
            if (!currentCoroutineContext().isActive) return
            if (policy.wifiOnly && !onUnmetered()) return
            if (offline.hasChapter(book.bookId, chapter.id)) continue
            try {
                val content = api.chapter(book.source, book.bookId, chapter.id)
                val saved = offline.putChapter(
                    book.bookId, chapter.id,
                    content.title.ifEmpty { chapter.title }, content.text, content.images,
                    auto = true,
                )
                if (saved) onSaved()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // 背景动作：这一章失败就跳过，下一章继续，不打扰读者
            }
            // 章节内容是大块文本，连着拉会和服务端串行节流撞上；留一点间隔
            delay(250)
        }
    }

    fun cancel() {
        job?.cancel()
        job = null
        signature = ""
    }
}
