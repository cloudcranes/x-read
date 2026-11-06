package com.alanmaster.xread.data

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import java.net.HttpURLConnection
import java.net.URL

/**
 * 自动备份的偏好。
 *
 * 目录用 SAF 的 tree uri 保存：拿到的是长期可写的授权，换机后只要重新选一次目录
 * 就能继续写，不需要申请存储权限，也不用猜别的厂商把外部目录放在哪。
 */
object BackupPrefs {
    private const val FILE = "backup"
    private const val KEY_TREE = "tree_uri"
    private const val KEY_INTERVAL = "interval_hours"
    private const val KEY_LAST = "last_at"
    private const val KEY_DAV_URL = "dav_url"
    private const val KEY_DAV_USER = "dav_user"
    private const val KEY_DAV_PASS = "dav_pass"
    private const val KEY_DAV_ON = "dav_on"
    private const val KEY_DAV_LAST = "dav_last"

    fun treeUri(c: Context): String = sp(c).getString(KEY_TREE, "").orEmpty()
    fun setTreeUri(c: Context, uri: String) {
        sp(c).edit().putString(KEY_TREE, uri).apply()
    }

    fun intervalHours(c: Context): Int = sp(c).getInt(KEY_INTERVAL, 0)
    fun setIntervalHours(c: Context, hours: Int) {
        sp(c).edit().putInt(KEY_INTERVAL, hours).apply()
    }

    fun lastAt(c: Context): Long = sp(c).getLong(KEY_LAST, 0L)
    fun setLastAt(c: Context, at: Long) {
        sp(c).edit().putLong(KEY_LAST, at).apply()
    }

    fun davUrl(c: Context): String = sp(c).getString(KEY_DAV_URL, "").orEmpty()
    fun davUser(c: Context): String = sp(c).getString(KEY_DAV_USER, "").orEmpty()
    fun davPass(c: Context): String = sp(c).getString(KEY_DAV_PASS, "").orEmpty()
    fun davEnabled(c: Context): Boolean = sp(c).getBoolean(KEY_DAV_ON, false)
    fun davLastAt(c: Context): Long = sp(c).getLong(KEY_DAV_LAST, 0L)

    fun saveDav(c: Context, url: String, user: String, pass: String, enabled: Boolean) {
        sp(c).edit()
            .putString(KEY_DAV_URL, url.trim())
            .putString(KEY_DAV_USER, user)
            .putString(KEY_DAV_PASS, pass)
            .putBoolean(KEY_DAV_ON, enabled)
            .apply()
    }

    fun setDavLastAt(c: Context, at: Long) {
        sp(c).edit().putLong(KEY_DAV_LAST, at).apply()
    }

    private fun sp(c: Context) = c.getSharedPreferences(FILE, Context.MODE_PRIVATE)
}

/** 备份设置面板要显示的一份快照；字段与 BackupPrefs 一一对应。 */
data class BackupUiState(
    val dirLabel: String = "",
    val intervalHours: Int = 0,
    val lastLabel: String = "",
    val davUrl: String = "",
    val davUser: String = "",
    val davPass: String = "",
    val davEnabled: Boolean = false,
)

private fun timeLabel(at: Long): String {
    if (at <= 0) return ""
    val f = java.text.SimpleDateFormat("MM-dd HH:mm", java.util.Locale.getDefault())
    return f.format(java.util.Date(at))
}

/** 从偏好读一份面板快照。SAF 的 tree id 形如 "primary:Documents/xread"，只留可读部分。 */
fun backupUiState(c: Context): BackupUiState {
    val tree = BackupPrefs.treeUri(c)
    val label = if (tree.isEmpty()) "" else runCatching {
        Uri.decode(Uri.parse(tree).lastPathSegment.orEmpty()).substringAfter(':').ifBlank { "已选目录" }
    }.getOrDefault("已选目录")
    return BackupUiState(
        dirLabel = label,
        intervalHours = BackupPrefs.intervalHours(c),
        lastLabel = timeLabel(BackupPrefs.lastAt(c)),
        davUrl = BackupPrefs.davUrl(c),
        davUser = BackupPrefs.davUser(c),
        davPass = BackupPrefs.davPass(c),
        davEnabled = BackupPrefs.davEnabled(c),
    )
}

/**
 * 把备份写进用户选定的目录或 WebDAV。
 *
 * 没有引第三方 WebDAV 库：备份是一个几十 KB 的 JSON，一次 PUT 就够，
 * 引一个库反而多一份要维护的依赖和证书信任面。
 */
object BackupManager {
    const val FILE_NAME = "x-read-backup.json"

    /** 写进 SAF 目录；同名文件覆盖。返回可展示的结果文案。 */
    fun writeToTree(context: Context, treeUri: String, body: String): Result<String> = runCatching {
        val tree = Uri.parse(treeUri)
        val resolver = context.contentResolver
        val rootId = DocumentsContract.getTreeDocumentId(tree)
        val root = DocumentsContract.buildDocumentUriUsingTree(tree, rootId)

        var existing: Uri? = null
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, rootId)
        resolver.query(
            children,
            arrayOf(
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            ),
            null,
            null,
            null,
        )?.use { cursor ->
            while (cursor.moveToNext()) {
                if (cursor.getString(1) == FILE_NAME) {
                    existing = DocumentsContract.buildDocumentUriUsingTree(tree, cursor.getString(0))
                    break
                }
            }
        }

        // 覆盖要显式用 "wt"：默认的 "w" 不清空旧内容，备份变短时会留下尾巴。
        val target = existing ?: DocumentsContract.createDocument(
            resolver,
            root,
            "application/json",
            FILE_NAME,
        ) ?: error("目录不可写")

        resolver.openOutputStream(target, "wt")?.use { out ->
            out.write(body.toByteArray(Charsets.UTF_8))
            out.flush()
        } ?: error("无法写入备份文件")

        "${Uri.decode(tree.lastPathSegment.orEmpty())}/$FILE_NAME"
    }

    /** WebDAV PUT。url 可以是目录，也可以直接是完整的文件地址。 */
    fun uploadWebDav(url: String, user: String, pass: String, body: String): Result<String> = runCatching {
        val trimmed = url.trim().trimEnd('/')
        require(trimmed.startsWith("http://") || trimmed.startsWith("https://")) { "地址要以 http(s):// 开头" }
        val target = if (trimmed.endsWith(".json")) trimmed else "$trimmed/$FILE_NAME"
        val conn = (URL(target).openConnection() as HttpURLConnection).apply {
            requestMethod = "PUT"
            connectTimeout = 15_000
            readTimeout = 30_000
            doOutput = true
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            if (user.isNotEmpty()) {
                val token = android.util.Base64.encodeToString(
                    "$user:$pass".toByteArray(Charsets.UTF_8),
                    android.util.Base64.NO_WRAP,
                )
                setRequestProperty("Authorization", "Basic $token")
            }
        }
        try {
            conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            val code = conn.responseCode
            if (code !in 200..299) error("服务器返回 $code")
            target
        } finally {
            conn.disconnect()
        }
    }

    /**
     * 到点就备份一次。返回结果文案，null 表示这次不需要动。
     *
     * 没有引入 WorkManager：备份是一个几 KB 的写盘动作，交给系统调度反而要处理
     * 后台限制与唤醒失败。应用每次启动/回前台时检查一次「距上次备份多久」，
     * 对阅读器这种使用节奏足够准时，也不会因为省电策略整个失效。
     */
    fun maybeAutoBackup(context: Context, body: () -> String?): String? {
        val hours = BackupPrefs.intervalHours(context)
        if (hours <= 0) return null
        val now = System.currentTimeMillis()
        val last = BackupPrefs.lastAt(context)
        if (last > 0 && now - last < hours * 3_600_000L) return null

        val json = body() ?: return null
        val tree = BackupPrefs.treeUri(context)
        if (tree.isNotEmpty()) {
            writeToTree(context, tree, json).onSuccess { BackupPrefs.setLastAt(context, now) }
                .onFailure { return "自动备份失败：${it.message}" }
        }
        // WebDAV 是第二份副本：目录写失败也已经返回了，不会误报成功
        if (BackupPrefs.davEnabled(context)) {
            uploadWebDav(BackupPrefs.davUrl(context), BackupPrefs.davUser(context), BackupPrefs.davPass(context), json)
                .onSuccess { BackupPrefs.setDavLastAt(context, now) }
        }
        return "已自动备份到 ${if (tree.isNotEmpty()) "所选目录" else "WebDAV"}"
    }
}
