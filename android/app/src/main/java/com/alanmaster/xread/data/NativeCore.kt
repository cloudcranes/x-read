package com.alanmaster.xread.data

import android.content.Context
import com.alanmaster.xread.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.util.UUID
import java.util.concurrent.Executors
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Shared in-process Rust engine. Never binds a socket or sends requests to a NAS. */
object NativeCore {
    private val initLock = Mutex()
    // 下载侧最多 16 个 worker、闸门最多放 8 个并发；池子小于并发上限时，
    // 多出来的请求会排在队列里，而排队中的请求无法被 nativeCancel 打断
    // （id 还没进 core），取消只是丢掉结果。池子留够余量，前台请求也不必
    // 排在 8 个长下载后面等 60 秒超时。
    private val calls = Executors.newFixedThreadPool(16) { r ->
        Thread(r, "xread-core").apply { isDaemon = true }
    }
    @Volatile private var ready = false
    @Volatile private var libraryLoaded = false

    @JvmStatic external fun nativeInit(dataDir: String): String
    @JvmStatic external fun nativeRequest(id: String, path: String, query: String, method: String, body: String): String
    @JvmStatic external fun nativeCancel(id: String)

    private fun unpack(text: String): JSONObject {
        // 只在 debug 构建里记前 300 字：release 下这就是把章节正文写进 logcat
        if (BuildConfig.DEBUG) android.util.Log.i("xread-core", "resp ${text.take(300)}")
        val envelope = JSONObject(text)
        val status = envelope.optInt("status", 500)
        val body = envelope.optJSONObject("body") ?: throw ApiException("内容核心响应格式异常")
        if (status == 499) throw kotlinx.coroutines.CancellationException("内容请求已取消")
        if (status !in 200..299 || body.optBoolean("success", true).not()) {
            val err = body.opt("error")
            // core 的错误可能是字符串，也可能是 {code,message}；两者都要能取到错误码，
            // 否则「上游缺页」这类永久错误会被当成临时故障，反复重试并压缩并发窗口。
            val code = when (err) {
                is JSONObject -> err.str("code")
                else -> ""
            }
            val message = if (err is JSONObject) {
                err.str("message", "内容加载失败")
            } else {
                err?.toString() ?: "内容加载失败 ($status)"
            }
            throw ApiException(message, transient = ErrorClass.isTransient(status, code, message))
        }
        val code = body.optInt("code", 0)
        if (code != 0 && code != 200) throw ApiException(body.str("message", "上游业务错误 $code"))
        return body
    }

    suspend fun initialize(context: Context) = initLock.withLock {
        if (ready) return@withLock
        withContext(Dispatchers.IO) {
            if (!libraryLoaded) {
                try { System.loadLibrary("xread_native") }
                catch (e: UnsatisfiedLinkError) { throw ApiException("内容核心加载失败，请安装适配本机架构的 APK") }
                libraryLoaded = true
            }
            val dir = File(context.applicationContext.filesDir, "content-core")
            if (!dir.isDirectory && !dir.mkdirs()) throw ApiException("内容核心目录创建失败")
            // 必须在 nativeInit 之前写：core 只在 config.json 缺失时才落默认值，
            // 已有配置不会被覆盖，这里写进去的 proxy 才会被读到。
            applySystemProxy(context, dir)
            unpack(nativeInit(dir.absolutePath))
            ready = true
            android.util.Log.i("xread-core", "ready transport=jni listener=false")
        }
    }

    /** core 返回的 /src/xxx 相对路径 → 本机可读的绝对路径。 */
    fun localFile(context: Context, corePath: String): String {
        if (corePath.isEmpty()) return ""
        val name = corePath.substringAfterLast('/')
        if (name.isEmpty()) return ""
        return File(File(context.filesDir, "content-core"), "src/$name").absolutePath
    }


    /**
     * 把系统 HTTP 代理转交给 core。
     *
     * 某些网络（模拟器、受限出口）会把 `*.snssdk.com` 解析到没人接管的 fake-IP，
     * 直连会一直挂到超时，调用方只能看到「取不到内容」。系统里配了代理时
     * 由 core 走代理，这类网络就恢复了。
     */
    private fun applySystemProxy(context: Context, dir: File) {
        val raw = runCatching {
            android.provider.Settings.Global.getString(
                context.contentResolver, android.provider.Settings.Global.HTTP_PROXY)
        }.getOrNull().orEmpty().trim()
        val file = File(dir, "config/config.json")
        val config = if (file.isFile) {
            runCatching { JSONObject(file.readText()) }.getOrNull() ?: JSONObject()
        } else {
            JSONObject().put("algorithm_type", "8404").put("port", 0)
        }
        val current = config.optString("proxy")
        val next = normalizeProxy(raw)
        if (next == null) {
            // 系统代理关掉后不能留着旧值，否则会拿一个已失效的代理把请求卡死
            if (current.isEmpty()) return
            config.remove("proxy")
        } else {
            if (current == next) return
            config.put("proxy", next)
        }
        file.parentFile?.mkdirs()
        runCatching { file.writeText(config.toString()) }
    }

    /** ":0" 是 Android 表示"未设置代理"的写法；reqwest 需要带 scheme 的 URL。 */
    private fun normalizeProxy(raw: String): String? {
        val value = raw.trim()
        if (value.isEmpty() || value == ":0") return null
        if (value.startsWith("[") || value.contains("://")) return value
        return "http://" + value
    }

    suspend fun request(context: Context, path: String, query: String = "", method: String = "GET", body: String = ""): JSONObject {
        initialize(context)
        val id = UUID.randomUUID().toString()
        return suspendCancellableCoroutine { continuation ->
            val task = Runnable {
                // 取消后被调度到的任务直接跳过，不再白跑一次 60 秒超时的请求
                if (!continuation.isActive) return@Runnable
                try {
                    val value = unpack(nativeRequest(id, path, query, method, body))
                    if (continuation.isActive) continuation.resume(value)
                } catch (e: Exception) {
                    if (continuation.isActive) continuation.resumeWithException(e)
                }
            }
            val future = calls.submit(task)
            continuation.invokeOnCancellation {
                future.cancel(false)
                nativeCancel(id)
            }
        }
    }
}
