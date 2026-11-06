package com.alanmaster.xread.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import com.alanmaster.xread.BuildConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/** 热更新面板要显示的一切。 */
data class UpdateUi(
    val checking: Boolean = false,
    val downloading: Boolean = false,
    /** 0f..1f；只有下载且服务端给了 Content-Length 时才有意义。 */
    val progress: Float = 0f,
    val checkedVersion: String = "",
    val release: ReleaseInfo? = null,
    val newer: Boolean = false,
    /** 已经落盘的安装包；非空时按钮变成「安装」。 */
    val file: File? = null,
    val message: String = "",
    val hotUpdate: Boolean = true,
    val source: String = AppUpdate.DEFAULT_SOURCE,
    val token: String = "",
    /** 下载加速通道的 key，见 [AppUpdate.MIRRORS]；空串是直连。 */
    val mirror: String = "",
)

/**
 * 应用自更新（热更新）：检查本仓库最新 Release → 下载 APK → 交给系统安装器。
 *
 * 为什么是「自更新」而不是 Tinker 那类代码级热修复：本项目所有业务逻辑都在
 * Kotlin 里编译进 APK，没有独立插件进程，也没有补丁分发通道；要真正替换
 * dex 得引入补丁 SDK 与签名密钥托管，成本远高于收益。这里做的是**整包热更新**：
 * 用户不用连电脑、不用重装数据，点一下就从 GitHub Release 换到新版本，
 * 书架/进度/下载的私有目录原样保留（同 applicationId 覆盖安装）。
 *
 * 所有 IO 都在 [scope] 里跑，UI 只读 [state]。
 */
class AppUpdater(context: Context) {

    private val app = context.applicationContext
    private val repo = OfflineRepo.of(app)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _state = MutableStateFlow(UpdateUi())
    val state: StateFlow<UpdateUi> = _state

    private var job: Job? = null

    /** 安装包目录：cache/update（由 file_paths.xml 的 `update` 暴露给安装器）。 */
    val dir: File get() = File(app.cacheDir, "update").apply { mkdirs() }

    /** 从磁盘读一次偏好；在 IO 线程调用，组合期不读盘。 */
    suspend fun hydrate() = withContext(Dispatchers.IO) {
        runCatching {
            _state.update {
                it.copy(
                    hotUpdate = repo.hotUpdateEnabled(),
                    source = repo.updateSource(),
                    token = repo.updateToken(),
                    mirror = repo.updateMirror(),
                )
            }
            // 上次已经下好、但没装就退出的包，重启后直接显示「安装」。
            // 装完系统会在后台重启应用：这时缓存里的包对应的版本已经不新了，清掉。
            val saved = repo.pendingUpdate()
            if (saved.isNotBlank()) {
                val f = File(saved)
                val stale = AppUpdate.pendingIsStale(
                    repo.pendingUpdateCode(), f.nameWithoutExtension,
                    BuildConfig.VERSION_CODE, BuildConfig.VERSION_NAME,
                )
                if (stale) {
                    repo.savePendingUpdate("", 0)
                    f.delete()
                } else if (f.isFile && f.length() > 0) {
                    _state.update { it.copy(file = f, message = "已下载 ${formatMb(f.length())}，点安装完成更新") }
                } else {
                    repo.savePendingUpdate("", 0)
                }
            }
        }
    }

    /** 启动时的自动检查：热更新关闭、或者今天已经查过，就直接返回。 */
    fun autoCheckOnLaunch() {
        if (!_state.value.hotUpdate) return
        if (!AppUpdate.dueForCheck(System.currentTimeMillis(), repo.lastUpdateCheckAt())) return
        check(manual = false)
    }

    fun check(manual: Boolean = true) {
        if (job?.isActive == true) return
        job = scope.launch {
            _state.update {
                it.copy(checking = true, downloading = false, progress = 0f,
                    message = if (manual) "" else it.message)
            }
            val source = _state.value.source.ifBlank { AppUpdate.DEFAULT_SOURCE }
            val token = _state.value.token
            val outcome = runCatching { fetchText(source, token) }
            outcome.onFailure { e ->
                _state.update {
                    it.copy(checking = false, message = describe(e))
                }
                return@launch
            }
            val payload = outcome.getOrThrow()
            val release = AppUpdate.parse(payload)
            if (release == null) {
                _state.update {
                    it.copy(checking = false, message = "更新源返回的内容看不懂（既不是 Release 也不是清单）")
                }
                return@launch
            }
            val newer = AppUpdate.isNewer(release, BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE)
            repo.saveLastUpdateCheckAt(System.currentTimeMillis())
            _state.update {
                it.copy(
                    checking = false,
                    release = release,
                    checkedVersion = release.versionName,
                    newer = newer,
                    // 版本没变就丢掉上次残留的包，避免把旧包当成新版本装上去。
                    file = if (newer) it.file?.takeIf { f -> f.isFile } else null,
                    message = when {
                        newer -> ""
                        manual -> "已经是最新版本 ${BuildConfig.VERSION_NAME}"
                        else -> ""
                    },
                )
            }
            if (!newer) repo.savePendingUpdate("", 0)
        }
    }

    fun download() {
        val release = _state.value.release ?: return
        if (job?.isActive == true) return
        job = scope.launch {
            _state.update { it.copy(downloading = true, progress = 0f, message = "") }
            val target = File(dir, AppUpdate.fileName(release))
            val part = File(dir, "${target.name}.part")
            val mirror = _state.value.mirror
            // 加速通道不通就退回直连，两个都失败才算失败。
            val urls = buildList {
                add(AppUpdate.accelerate(release.apkUrl, mirror))
                if (mirror.isNotBlank()) add(release.apkUrl)
            }.distinct()
            var used = urls.first()
            var failure: Throwable? = null
            for (url in urls) {
                val attempt = runCatching { fetchApk(release, _state.value.token, part, url) }
                if (attempt.isSuccess) {
                    used = url
                    failure = null
                    break
                }
                failure = attempt.exceptionOrNull()
                part.delete()
                _state.update { it.copy(progress = 0f) }
            }
            runCatching {
                failure?.let { throw it }
                part.renameTo(target)
                if (release.sha256.isNotBlank()) {
                    val actual = AppUpdate.sha256(target)
                    if (!AppUpdate.matchesSha256(release.sha256, actual)) {
                        target.delete()
                        throw IllegalStateException("校验和不一致，包可能下载损坏或被替换")
                    }
                }
                target
            }.onSuccess { file ->
                repo.savePendingUpdate(file.absolutePath, release.versionCode)
                val size = formatMb(file.length())
                _state.update {
                    it.copy(
                        downloading = false,
                        progress = 1f,
                        file = file,
                        message = if (used == urls.first()) {
                            "已下载 $size，点安装完成更新"
                        } else {
                            "加速通道没通，已改用直连；已下载 $size，点安装完成更新"
                        },
                    )
                }
            }.onFailure { e ->
                part.delete()
                val hint = if (e is HttpFailure && _state.value.mirror.isNotBlank()) {
                    "；加速通道取不到私有仓库的包，换个通道或选直连"
                } else {
                    ""
                }
                _state.update { it.copy(downloading = false, message = describe(e) + hint) }
            }
        }
    }

    /**
     * 交给系统安装器。
     *
     * Android 8 起「安装未知应用」是按应用授权的；没授权时跳转到本应用的授权页，
     * 回来后点同一个按钮即可继续。
     */
    fun install(): Boolean {
        val file = _state.value.file
        if (file == null || !file.isFile) {
            _state.update { it.copy(message = "安装包不在缓存里了，请重新下载") }
            return false
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            !app.packageManager.canRequestPackageInstalls()
        ) {
            _state.update { it.copy(message = "先允许「安装未知应用」，回来再点安装") }
            openInstallPermission()
            return false
        }
        return runCatching {
            val uri = FileProvider.getUriForFile(app, "${app.packageName}.fileprovider", file)
            val intent = Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            app.startActivity(intent)
            true
        }.getOrElse { e ->
            _state.update { it.copy(message = "调不起安装器：${e.javaClass.simpleName}") }
            false
        }
    }

    fun openInstallPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        runCatching {
            app.startActivity(
                Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${app.packageName}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }

    fun setHotUpdate(enabled: Boolean) {
        _state.update { it.copy(hotUpdate = enabled) }
        scope.launch { runCatching { repo.saveHotUpdateEnabled(enabled) } }
        if (enabled) autoCheckOnLaunch()
    }

    fun saveSource(source: String, token: String) {
        val url = source.trim().ifBlank { AppUpdate.DEFAULT_SOURCE }
        val key = token.trim()
        _state.update {
            it.copy(source = url, token = key,
                message = "更新源已保存", release = null, file = it.file, newer = false)
        }
        scope.launch { runCatching { repo.saveUpdateSource(url); repo.saveUpdateToken(key) } }
    }

    /** 切换下载加速通道；只影响下一次下载，已经下好的包不动。 */
    fun setMirror(mirrorKey: String) {
        val mirror = AppUpdate.mirrorOf(mirrorKey).key
        _state.update { it.copy(mirror = mirror, message = "") }
        scope.launch { runCatching { repo.saveUpdateMirror(mirror) } }
    }


    // ---------------- HTTP ----------------

    private fun fetchText(url: String, token: String): String {
        val conn = open(url, token)
        try {
            val code = conn.responseCode
            if (code !in 200..299) throw HttpFailure(code)
            return conn.inputStream.use { it.readBytes().toString(Charsets.UTF_8) }
        } finally {
            conn.disconnect()
        }
    }

    private fun fetchApk(release: ReleaseInfo, token: String, part: File, url: String) {
        val conn = open(url, token)
        try {
            val code = conn.responseCode
            if (code !in 200..299) throw HttpFailure(code)
            val total = conn.contentLengthLong
            var written = 0L
            conn.inputStream.use { input ->
                part.outputStream().use { output ->
                    val buffer = ByteArray(128 * 1024)
                    while (true) {
                        val read = input.read(buffer)
                        if (read <= 0) break
                        output.write(buffer, 0, read)
                        written += read
                        if (total > 0) {
                            _state.update { it.copy(progress = (written.toFloat() / total).coerceIn(0f, 0.99f)) }
                        }
                    }
                }
            }
            if (release.sizeBytes > 0 && written != release.sizeBytes) {
                throw IllegalStateException("包大小对不上（期望 ${release.sizeBytes}，实际 $written）")
            }
        } finally {
            conn.disconnect()
        }
    }

    private fun open(url: String, token: String): HttpURLConnection {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = 15_000
        conn.readTimeout = 30_000
        conn.instanceFollowRedirects = true
        conn.setRequestProperty("User-Agent", "x-read/${BuildConfig.VERSION_NAME}")
        conn.setRequestProperty("Accept", "application/vnd.github+json")
        // 令牌只发给 GitHub 自己：加速通道是第三方代理，绝不能把 PAT 一并递过去。
        if (token.isNotBlank() && isGitHubHost(url)) {
            conn.setRequestProperty("Authorization", "Bearer $token")
        }
        return conn
    }

    private fun isGitHubHost(url: String): Boolean {
        val host = runCatching { URL(url).host.lowercase() }.getOrNull() ?: return false
        return host == "github.com" || host.endsWith(".github.com") ||
            host == "githubusercontent.com" || host.endsWith(".githubusercontent.com")
    }

    private class HttpFailure(val code: Int) : Exception("HTTP $code")

    private fun describe(e: Throwable): String = when {
        e is HttpFailure && e.code == 404 ->
            "更新源 404：私有仓库要在「更新源」里填 GitHub 令牌，或换成公开清单地址"
        e is HttpFailure && (e.code == 401 || e.code == 403) ->
            "令牌无效或没有读 Release 的权限（${e.code}）"
        e is HttpFailure -> "更新源返回 HTTP ${e.code}"
        else -> "检查失败：${e.message ?: e.javaClass.simpleName}"
    }

    private fun formatMb(bytes: Long): String =
        if (bytes >= 1024 * 1024) "%.1f MB".format(bytes / 1024.0 / 1024.0)
        else "${bytes / 1024} KB"
}
