package com.alanmaster.xread.data

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * 以 [AdaptiveConcurrency.permits] 为窗口的准入闸门：窗口收缩后已在跑的请求不打断，
 * 新请求排队等窗口放行。
 *
 * 关键约束：排队的等待者被取消时必须**从队列里摘掉**。否则 [release] 会把许可
 * 发给一个再也不会来归还的协程，`active` 只增不减，攒够次数后 [acquire] 永久挂起。
 */
class AdaptiveGate(val window: AdaptiveConcurrency) {

    private val mutex = Mutex()
    private var active = 0
    private val waiters = ArrayDeque<CompletableDeferred<Unit>>()

    val activeCount: Int get() = active
    val permits: Int get() = window.permits

    suspend fun acquire() {
        val ticket = CompletableDeferred<Unit>()
        val immediate = mutex.withLock {
            if (active < window.permits) {
                active++
                true
            } else {
                waiters.addLast(ticket)
                false
            }
        }
        if (immediate) return
        try {
            ticket.await()
        } catch (e: CancellationException) {
            // 协程已处于取消态，普通 withLock 会立刻再抛 CancellationException，
            // 清理代码根本执行不到——必须放进 NonCancellable。
            kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable) {
                if (ticket.isCompleted) {
                    // 许可已经发到手上，需归还
                    release()
                } else {
                    val removed = mutex.withLock { waiters.remove(ticket) }
                    // remove 失败说明刚好在这一瞬间被放行，同样要归还
                    if (!removed) release()
                }
            }
            throw e
        }
    }

    suspend fun release() {
        val toWake = ArrayList<CompletableDeferred<Unit>>(2)
        mutex.withLock {
            if (active > 0) active--
            while (waiters.isNotEmpty() && active < window.permits) {
                active++
                toWake.add(waiters.removeFirst())
            }
        }
        // 在锁外完成，避免被唤醒方立刻回调 release 造成重入等待
        toWake.forEach { it.complete(Unit) }
    }

    suspend fun recordSuccess() = mutex.withLock { window.recordSuccess() }
    suspend fun recordFailure() = mutex.withLock { window.recordFailure() }

    companion object {
        /** 失败后的短暂退避，避免在服务端排队或抖动时硬撞。调用方须已归还许可。 */
        suspend fun backoff(gate: AdaptiveGate) {
            val ms = gate.window.backoffMs
            if (ms > 0) kotlinx.coroutines.delay(ms)
        }
    }
}
