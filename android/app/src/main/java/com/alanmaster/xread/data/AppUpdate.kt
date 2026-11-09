package com.alanmaster.xread.data

import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest

/**
 * 一个可安装的版本。
 *
 * 三种来源都归一到这个结构：GitHub Release API（`releases/latest`）、
 * CI 随包上传的 `update.json` 清单、以及自建服务返回的同名 JSON。
 */
data class ReleaseInfo(
    /** 纯数字版本名，已去掉 `v` 前缀与 `-standalone` 之类的后缀，例如 `3.8.0`。 */
    val versionName: String,
    /** 清单里给了就按它比，GitHub 的 Release 没有这个字段，按版本名比。 */
    val versionCode: Int = 0,
    val apkUrl: String,
    val notes: String = "",
    val sizeBytes: Long = 0L,
    /** 小写十六进制；为空表示来源没提供校验和。 */
    val sha256: String = "",
)

/**
 * 下载加速通道：把完整 GitHub 直链拼在前缀后面。
 *
 * `key` 存进 `settings.json` 的 `updateMirror`；`prefix` 为空表示直连。
 */
data class Mirror(val key: String, val label: String, val prefix: String)

/**
 * 热更新里纯计算的那一半：版本比较、清单解析、校验和、节流判断。
 *
 * 不碰网络也不碰 Android，全部可在 JVM 单测里跑——真正做 IO 的部分在 [AppUpdater]。
 */
object AppUpdate {

    /**
     * 默认更新源：CI 随每个 `v*` tag 附到 Release 的公开 `update.json` 清单。
     *
     * 仓库已公开，这个地址走 Release 资产 CDN，不走 `api.github.com`，
     * 不吃匿名接口每小时 60 次的限额，也不需要令牌；[AppUpdate.parseManifest]
     * 能直接读它。用户手动换成私有仓库或 GitHub API 时才需要填只读令牌。
     */
    const val DEFAULT_SOURCE =
        "https://github.com/cloudcranes/x-read/releases/latest/download/update.json"

    /** 4.5.2 之前的默认源；匿名请求每小时 60 次，耗尽时回 403，正是这次误报的来源。 */
    const val LEGACY_DEFAULT_SOURCE =
        "https://api.github.com/repos/cloudcranes/x-read/releases/latest"

    /** 存量配置里若仍写着旧默认源，静默迁到公开清单；用户手填的其它地址原样保留。 */
    fun migrateSource(source: String): String =
        if (source.trim() == LEGACY_DEFAULT_SOURCE) DEFAULT_SOURCE else source

    /** 启动时自动检查的节流：一天最多一次。 */
    const val CHECK_INTERVAL_MS = 24L * 60 * 60 * 1000

    /**
     * 下载加速通道。
     *
     * GitHub Release 的直链在国内经常几十 KB/s，这几家是常用的反向代理，
     * 用法都是「前缀 + 完整原始 URL」。直连排第一个，也是未知 key 的回退值。
     */
    val MIRRORS: List<Mirror> = listOf(
        Mirror("", "直连 GitHub", ""),
        Mirror("ghfast", "ghfast.top", "https://ghfast.top/"),
        Mirror("ghproxy", "gh-proxy.com", "https://gh-proxy.com/"),
        Mirror("ghnet", "ghproxy.net", "https://ghproxy.net/"),
    )

    /** 未知的 key（旧配置、手改过的 JSON）一律回退成直连。 */
    fun mirrorOf(key: String): Mirror = MIRRORS.firstOrNull { it.key == key } ?: MIRRORS.first()

    /**
     * 给 GitHub 直链套上加速前缀。
     *
     * 直连、非 https（自建清单常见）、已经带过同一前缀的，都原样返回，
     * 避免拼成 `https://ghfast.top/https://ghfast.top/https://…`。
     */
    fun accelerate(url: String, key: String): String {
        val prefix = mirrorOf(key).prefix
        if (prefix.isEmpty() || url.startsWith(prefix) || !url.startsWith("https://")) return url
        return prefix + url
    }

    /** 自动判别来源：有 `assets` 数组的是 GitHub Release，否则按清单解析。 */
    fun parse(payload: String): ReleaseInfo? {
        val root = runCatching { JSONObject(payload) }.getOrNull() ?: return null
        return if (root.has("assets")) parseGitHub(root) else parseManifest(root)
    }

    /**
     * GitHub `releases/latest` 的应答。
     *
     * 一个 Release 往往同时挂着 debug 包、standalone 包和更新清单，必须挑对：
     * 名字带 `standalone` 的最优先，其次 `arm64`；带 `debug` 或 `unsigned` 的分数为负，
     * 直接跳过——那种包装不到正式版上，白让用户点一次安装。
     */
    fun parseGitHub(root: JSONObject): ReleaseInfo? {
        val tag = root.optString("tag_name").ifBlank { root.optString("name") }
        val version = cleanVersion(tag).ifBlank { cleanVersion(root.optString("name")) }
        if (version.isBlank()) return null
        val assets = root.optJSONArray("assets") ?: JSONArray()
        val apk = (0 until assets.length())
            .mapNotNull { assets.optJSONObject(it) }
            .filter { it.optString("name").endsWith(".apk", ignoreCase = true) }
            .filter { assetScore(it.optString("name")) >= 0 }
            .maxByOrNull { assetScore(it.optString("name")) }
            ?: return null
        val url = apk.optString("browser_download_url").ifBlank { apk.optString("url") }
        if (url.isBlank()) return null
        return ReleaseInfo(
            versionName = version,
            versionCode = root.optInt("versionCode", 0),
            apkUrl = url,
            notes = root.optString("body").trim(),
            sizeBytes = apk.optLong("size", 0L),
            sha256 = shaOf(apk.optString("digest")),
        )
    }

    /** 自定义清单：`{"versionName":"3.8.0","versionCode":13,"url":"...","sha256":"..."}`。 */
    fun parseManifest(root: JSONObject): ReleaseInfo? {
        val version = cleanVersion(
            root.optString("versionName").ifBlank { root.optString("version") })
        if (version.isBlank()) return null
        val url = root.optString("url")
            .ifBlank { root.optString("apkUrl") }
            .ifBlank { root.optString("apk") }
        if (url.isBlank()) return null
        return ReleaseInfo(
            versionName = version,
            versionCode = root.optInt("versionCode", 0),
            apkUrl = url,
            notes = root.optString("notes").ifBlank { root.optString("body") }.trim(),
            sizeBytes = root.optLong("size", 0L),
            sha256 = shaOf(root.optString("sha256").ifBlank { root.optString("digest") }),
        )
    }

    /**
     * 判重用的「择优分」。
     *
     * 交付包叫 `x-read-standalone.apk`，CI 的兜底包叫 `x-read-debug.apk`；
     * 名字里带 `debug` 的一律扣到 0 以下，避免手滑把调试包装上去。
     */
    fun assetScore(name: String): Int {
        val n = name.lowercase()
        var score = 0
        if (n.contains("standalone")) score += 4
        if (n.contains("arm64") || n.contains("aarch64")) score += 2
        if (n.endsWith(".apk")) score += 1
        if (n.contains("debug")) score -= 10
        if (n.contains("unsigned")) score -= 5
        return score
    }

    /** `v3.8.0-standalone` → `3.8.0`；解析不出数字就返回空串。 */
    fun cleanVersion(raw: String): String {
        val text = raw.trim().trimStart('v', 'V')
        val match = Regex("^[0-9]+(?:\\.[0-9]+)*").find(text) ?: return ""
        return match.value
    }

    fun parts(version: String): List<Int> =
        cleanVersion(version).split('.').map { it.toIntOrNull() ?: 0 }

    /** 逐段比较，缺段按 0 补齐：`3.8` 与 `3.8.0` 相等。 */
    fun compareVersions(a: String, b: String): Int {
        val left = parts(a)
        val right = parts(b)
        val size = maxOf(left.size, right.size)
        for (i in 0 until size) {
            val l = left.getOrElse(i) { 0 }
            val r = right.getOrElse(i) { 0 }
            if (l != r) return if (l > r) 1 else -1
        }
        return 0
    }

    /**
     * 是否需要更新。
     *
     * 两边都有 versionCode 时以它为准（更可靠，能表达「版本号没变但重新发版」）；
     * 否则退回版本名比较。
     */
    fun isNewer(release: ReleaseInfo, currentName: String, currentCode: Int): Boolean {
        if (release.versionCode > 0 && currentCode > 0) return release.versionCode > currentCode
        return compareVersions(release.versionName, currentName) > 0
    }

    /**
     * 缓存的包是否已经没用了：装完系统重启应用，`pendingUpdate` 还指着刚装上的那个版本，
     * 面板会一直挂着「点安装」。
     *
     * 记过 versionCode 的按它比；老数据没有这个键，退回文件名里的版本名。
     */
    fun pendingIsStale(pendingCode: Int, pendingName: String, installedCode: Int, installedName: String): Boolean {
        if (pendingCode > 0 && installedCode > 0) return pendingCode <= installedCode
        return compareVersions(pendingName, installedName) <= 0
    }

    /** 下载文件名：只留安全字符并去掉两端的点与横线，避免版本号里混进分隔符写出目录。 */
    fun fileName(release: ReleaseInfo): String {
        val safe = release.versionName
            .replace(Regex("[^0-9A-Za-z._-]"), "-")
            .trim('.', '-')
            .ifBlank { "latest" }
        return "x-read-$safe.apk"
    }

    /** 节流：距上次检查不足 [CHECK_INTERVAL_MS] 就跳过；时钟回拨也算到期。 */
    fun dueForCheck(now: Long, lastCheckAt: Long): Boolean {
        if (lastCheckAt <= 0L) return true
        if (now < lastCheckAt) return true
        return now - lastCheckAt >= CHECK_INTERVAL_MS
    }

    /**
     * GitHub 匿名 API 的 403 限额耗尽（`X-RateLimit-Remaining: 0`）与 429 都是限流，
     * 不是令牌失效。旧实现把 401/403 一律报成「令牌无效」，用户没填令牌也会被误导。
     */
    fun isRateLimited(code: Int, rateLimitRemaining: String?): Boolean =
        code == 429 || (code == 403 && rateLimitRemaining?.trim() == "0")

    /** 把 HTTP 失败翻成人话；纯函数，全部分支都能在 JVM 单测里覆盖。 */
    fun httpMessage(code: Int, rateLimited: Boolean, hasToken: Boolean): String = when {
        rateLimited ->
            "GitHub 接口请求次数已用完（$code）：稍后再试，或换用公开清单地址；填有效只读令牌也能提高限额"
        code == 401 ->
            "令牌无效或已过期（401）：到「更新源」换一个有效的 GitHub 只读令牌；用公开清单地址可以留空"
        code == 403 -> if (hasToken) {
            "令牌没有读 Release 的权限（403）：换成带 contents:read 的只读令牌"
        } else {
            "访问被拒绝（403）：检查网络，或在「更新源」里填一个只读令牌"
        }
        code == 404 ->
            "更新源 404：地址填错了，或这个版本还没发布 Release / update.json"
        else -> "更新源返回 HTTP $code"
    }

    /** `sha256:abcd...` / `abcd...` 都归一成小写十六进制。 */
    fun shaOf(raw: String): String =
        raw.trim().removePrefix("sha256:").removePrefix("SHA256:").trim().lowercase()

    fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes)
            .joinToString("") { "%02x".format(it) }

    fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val read = input.read(buffer)
                if (read <= 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    /** 空期望值不校验（来源没给就当放行）；给了就必须一致。 */
    fun matchesSha256(expected: String, actual: String): Boolean {
        val want = shaOf(expected)
        return want.isEmpty() || want == actual.lowercase()
    }
}
