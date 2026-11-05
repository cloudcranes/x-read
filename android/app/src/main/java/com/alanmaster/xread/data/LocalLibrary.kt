package com.alanmaster.xread.data

import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException

/** Phone-owned bookshelf. No HTTP and no server state. Unknown/corrupt files fail visibly. */
private fun JSONArray?.toStringList(): List<String> {
    if (this == null) return emptyList()
    return (0 until length()).mapNotNull { i -> optString(i).takeIf { it.isNotBlank() } }
}

class LocalLibrary(private val directory: File) {
    private val file = File(directory, "shelf.json")
    private fun load(): JSONObject = if (!file.exists()) JSONObject() else JSONObject(file.readText())
    private fun store(data: JSONObject) {
        if (!directory.isDirectory && !directory.mkdirs()) throw IOException("书架目录创建失败")
        val tmp = File(directory, "shelf.json.tmp")
        tmp.outputStream().use { out -> out.write(data.toString().toByteArray()); out.flush() }
        if (!tmp.renameTo(file)) throw IOException("书架保存失败；原数据已保留")
    }

    @Synchronized fun books(): List<Book> {
        val all = load()
        return all.keys().asSequence().mapNotNull { all.optJSONObject(it)?.book() }.toList()
    }

    @Synchronized fun put(book: Book) {
        require(book.bookId.matches(Regex("[0-9]{1,24}"))) { "作品编号格式错误" }
        val data = load()
        // 重新入架保留原分组与入架时间，避免覆盖用户整理结果。
        // optLong 在字段缺失时返回 0（不是 null），故必须用 has() 判断，
        // 否则旧数据迁移过来的条目会永远带 added_at=0、known_chapters=0。
        val previous = data.optJSONObject(book.bookId)
        val previousAdded = previous?.takeIf { it.has("added_at") }?.optLong("added_at")
            ?: System.currentTimeMillis()
        val previousKnown = previous?.takeIf { it.has("known_chapters") }?.optInt("known_chapters")
            ?: book.chapterCount
        data.put(book.bookId, JSONObject()
            .put("source", book.source).put("book_id", book.bookId).put("title", book.title)
            .put("author", book.author).put("author_id", book.authorId).put("description", book.description).put("cover_url", book.coverUrl)
            .put("category", book.category).put("score", book.score).put("word_count", book.wordCount)
            .put("chapter_count", book.chapterCount).put("latest_chapter_title", book.latestChapterTitle)
            .put("creation_status", book.creationStatus)
            .put("read_count", book.readCount).put("serial_count", book.serialCount)
            .put("group", previous?.optString("group").orEmpty())
            // 个人标记与分组同属「用户整理结果」，重新入架不该被上游书目冲掉
            .put("status", previous?.optString("status").orEmpty()
                .takeIf { it in ShelfItem.STATUSES } ?: ShelfItem.STATUS_READING)
            .put("rating", previous?.optInt("rating", 0) ?: 0)
            .put("tags", previous?.optJSONArray("tags") ?: JSONArray())
            .put("added_at", previousAdded)
            // 已记录的更新基线同样要继承，否则重新入架会让「有新章」判定失真
            .put("known_chapters", previousKnown))
        store(data)
    }

    /** 全量书架条目（含分组与入架时间）。 */
    @Synchronized fun entries(): List<ShelfItem> {
        val data = load()
        // 旧数据（迁移自服务端版本）没有 added_at 字段，读出来全是 0，
        // 会让「最近」「入架」两种排序结果完全一样。这里按现有 key 顺序补一次时间戳。
        val missing = data.keys().asSequence()
            .filter { data.optJSONObject(it)?.has("added_at") != true }.toList()
        if (missing.isNotEmpty()) {
            val base = System.currentTimeMillis() - missing.size * 1000L
            missing.forEachIndexed { i, key ->
                data.optJSONObject(key)?.put("added_at", base + i * 1000L)
            }
            runCatching { store(data) }
        }
        return data.keys().asSequence().mapNotNull { key ->
            val o = data.optJSONObject(key) ?: return@mapNotNull null
            ShelfItem(
                book = o.book(),
                chapterId = "", chapterOffset = 0, updatedAt = "",
                group = o.optString("group"),
                addedAt = o.optLong("added_at"),
                knownChapters = o.optInt("known_chapters", o.optInt("chapter_count")),
                status = o.optString("status").takeIf { it in ShelfItem.STATUSES }
                    ?: ShelfItem.STATUS_READING,
                rating = o.optInt("rating", 0).coerceIn(0, 5),
                tags = o.optJSONArray("tags").toStringList(),
            )
        }.toList()
    }

    /** 移动分组：空串表示清除分组。 */
    @Synchronized fun setGroup(bookId: String, group: String): Boolean = runCatching<Boolean> {
        val data = load()
        val o = data.optJSONObject(bookId) ?: return@runCatching false
        o.put("group", group.trim())
        data.put(bookId, o)
        store(data)
        true
    }.getOrDefault(false)

    /** 读写个人标记（想读/在读/读完）。非法值按「在读」处理，不留脏数据。 */
    @Synchronized fun setStatus(bookId: String, status: String): Boolean = runCatching<Boolean> {
        if (status !in ShelfItem.STATUSES) return@runCatching false
        val data = load()
        val o = data.optJSONObject(bookId) ?: return@runCatching false
        o.put("status", status)
        data.put(bookId, o)
        store(data)
        true
    }.getOrDefault(false)

    /** 个人评分 0–5；0 = 取消评分。 */
    @Synchronized fun setRating(bookId: String, rating: Int): Boolean = runCatching<Boolean> {
        val data = load()
        val o = data.optJSONObject(bookId) ?: return@runCatching false
        o.put("rating", rating.coerceIn(0, 5))
        data.put(bookId, o)
        store(data)
        true
    }.getOrDefault(false)

    @Synchronized fun setTags(bookId: String, tags: List<String>): Boolean = runCatching<Boolean> {
        val data = load()
        val o = data.optJSONObject(bookId) ?: return@runCatching false
        val clean = tags.map { it.trim().removePrefix("#") }.filter { it.isNotEmpty() }
            .distinct().take(ShelfItem.TAG_LIMIT)
        o.put("tags", JSONArray(clean))
        data.put(bookId, o)
        store(data)
        true
    }.getOrDefault(false)

    /** 记录当前已知章节数，作为下次「有更新」的比较基线。 */
    @Synchronized fun markChapterBaseline(bookId: String, chapters: Int): Boolean = runCatching<Boolean> {
        val data = load()
        val o = data.optJSONObject(bookId) ?: return@runCatching false
        o.put("known_chapters", chapters)
        data.put(bookId, o)
        store(data)
        true
    }.getOrDefault(false)

    /** 全部已用分组名（去重、排序）。 */
    @Synchronized fun groups(): List<String> = runCatching {
        entries().map { it.group }.filter { it.isNotBlank() }.distinct().sorted()
    }.getOrDefault(emptyList())

    /**
     * 撤销「移出书架」：按备份条目原样写回。
     *
     * 不能只调 [put]：那会把入架时间重置成现在、更新基线归零，撤销后排序与
     * 「有新章」判定就跟移出前对不上了。
     */
    @Synchronized fun restore(item: ShelfItem) {
        put(item.book)
        val data = load()
        val o = data.optJSONObject(item.book.bookId) ?: return
        o.put("group", item.group.trim())
        if (item.addedAt > 0) o.put("added_at", item.addedAt)
        if (item.knownChapters > 0) o.put("known_chapters", item.knownChapters)
        o.put("status", item.status)
        o.put("rating", item.rating)
        o.put("tags", JSONArray(item.tags))
        data.put(item.book.bookId, o)
        store(data)
    }

    @Synchronized fun remove(bookId: String) {
        val data = load()
        data.remove(bookId)
        store(data)
    }

    /** One-time upgrade: make existing downloads discoverable, without reading the old NAS. */
    @Synchronized fun migrateDownloads(books: List<Book>) {
        val marker = File(directory, "standalone-migrated")
        if (marker.exists()) return
        val existing = load()
        books.filter { !existing.has(it.bookId) }.forEach(::put)
        if (!directory.isDirectory && !directory.mkdirs()) throw IOException("迁移目录创建失败")
        marker.writeText("1")
    }
}
