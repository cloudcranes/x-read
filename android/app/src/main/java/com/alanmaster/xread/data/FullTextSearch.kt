package com.alanmaster.xread.data

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.yield

/**
 * 正文里命中的一处。
 *
 * 段落编号与阅读页、书签同一坐标系（`Paragraphs.split` 的下标），
 * 所以点开就能直接定位，不需要再回头换算。
 */
data class TextHit(
    val bookId: String,
    val bookTitle: String,
    val chapterId: String,
    val chapterTitle: String,
    val paragraphIndex: Int,
    val snippet: String,
)

/**
 * 一次搜索的进度快照。
 *
 * 边扫边发而不是最后一次性返回：一本 900 章的书扫完要一两秒，
 * 期间界面得能告诉人「已经翻了 300 章」，否则看起来像卡死。
 */
data class SearchProgress(
    val hits: List<TextHit> = emptyList(),
    val scanned: Int = 0,
    val total: Int = 0,
    val done: Boolean = false,
)

/**
 * 只在**已下载**的正文里做全文检索。
 *
 * 三条边界是有意的：
 * - 不触发网络：搜书是本地动作，扫到一半跳出去拉一章会让人以为应用在自己跑流量；
 * - 搜原文、不套替换规则：替换规则是阅读时的排版偏好，不是书的内容；
 * - 中文不分词：`contains` 已经能覆盖「人名」「地名」「半句话」三种最常见的查法，
 *   上分词器要么加依赖（违背零新依赖），要么自己造一份词表（收益远小于维护成本）。
 */
object FullTextSearch {

    /** 单次最多给这么多条。搜「的」能命中几万段，全渲染出来只会卡住列表。 */
    const val LIMIT = 200

    private const val YIELD_EVERY = 20
    private const val WINDOW = 20

    fun search(repo: OfflineRepo, query: String): Flow<SearchProgress> = flow {
        val q = query.trim()
        if (q.isEmpty()) {
            emit(SearchProgress(done = true))
            return@flow
        }

        // 只取「目录里存在且已落盘」的章节：meta.json 的 chapters 是全量目录，
        // 真正的缓存命中以 downloadedChapters 为准。
        val plan = repo.offlineBooks().map { book ->
            val cached = repo.downloadedChapters(book.bookId)
            book to repo.offlineChapters(book.bookId).filter { it.id in cached }
        }
        val total = plan.sumOf { it.second.size }
        val hits = ArrayList<TextHit>()
        var scanned = 0
        emit(SearchProgress(hits.toList(), 0, total, done = total == 0))

        for ((book, chapters) in plan) {
            for (chapter in chapters) {
                if (!currentCoroutineContext().isActive) return@flow
                if (hits.size >= LIMIT) {
                    emit(SearchProgress(hits.toList(), scanned, total, done = true))
                    return@flow
                }
                scanned++
                val raw = repo.getChapter(book.bookId, chapter.id) ?: continue
                val paragraphs = Paragraphs.split(raw.optString("content"))
                for (i in paragraphs.indices) {
                    val text = paragraphs[i]
                    if (!text.contains(q, ignoreCase = true)) continue
                    hits.add(
                        TextHit(
                            bookId = book.bookId,
                            bookTitle = book.title.ifBlank { book.bookId },
                            chapterId = chapter.id,
                            chapterTitle = chapter.title,
                            paragraphIndex = i,
                            snippet = snippetOf(text, q),
                        )
                    )
                    if (hits.size >= LIMIT) break
                }
                if (scanned % YIELD_EVERY == 0) {
                    emit(SearchProgress(hits.toList(), scanned, total, done = false))
                    yield()
                }
            }
        }
        emit(SearchProgress(hits.toList(), scanned, total, done = true))
    }

    /** 命中处前后各取 20 字，两端按需补省略号。 */
    internal fun snippetOf(text: String, query: String): String {
        val at = text.indexOf(query, ignoreCase = true)
        if (at < 0) return text.take(WINDOW * 2)
        val from = (at - WINDOW).coerceAtLeast(0)
        val to = (at + query.length + WINDOW).coerceAtMost(text.length)
        val head = if (from > 0) "…" else ""
        val tail = if (to < text.length) "…" else ""
        return head + text.substring(from, to) + tail
    }
}
