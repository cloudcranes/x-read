package com.alanmaster.xread.data

/**
 * 书签与高亮。全部本地存储，不依赖任何网络端点。
 *
 * 一个段落可以同时有书签与高亮，所以按 (paragraph, kind) 去重而不是只看下标。
 */
data class Mark(
    val paragraph: Int,
    /** 摘录文本；导出 Markdown 时直接用它，不依赖当时是否还能读到正文。 */
    val text: String,
    /** [KIND_BOOKMARK] 或 [KIND_HIGHLIGHT] */
    val kind: String,
    val at: Long,
    /** 自己的批注。空串表示没写；旧 marks.json 没有这个字段，读出即空。 */
    val note: String = "",
    /** 标签，存盘前统一去掉 `#` 与首尾空白。旧数据同样按空处理。 */
    val tags: List<String> = emptyList(),
) {
    val hasNote: Boolean get() = note.isNotBlank() || tags.isNotEmpty()

    companion object {
        const val KIND_BOOKMARK = "bookmark"
        const val KIND_HIGHLIGHT = "highlight"

        /** 一条批注最多这么多字——够写三大段，也够在列表里撑住一屏而不爆内存。 */
        const val NOTE_LIMIT = 2000
        const val TAG_LIMIT = 8

        /**
         * 标签归一化：去 `#`、去空白、去空串、去重、限个数。
         *
         * 用户输入习惯是「#人物 #伏笔」，有的人用逗号，有的人用空格，
         * 所以切分与清洗都放在这里，UI 与导出都只认清洗后的结果。
         */
        fun splitTags(raw: String): List<String> = normalizeTags(
            raw.split(',', '，', ' ', '\u3000', '\n', '\t', '#').map { it.trim() }
        )

        fun normalizeTags(raw: List<String>): List<String> = raw.asSequence()
            .map { it.trim().removePrefix("#").trim() }
            .filter { it.isNotEmpty() }
            .distinct()
            .take(TAG_LIMIT)
            .toList()
    }
}

/** 书摘列表用的一行：书 + 章 + 标记。 */
data class MarkEntry(
    val bookId: String,
    val chapterId: String,
    val chapterTitle: String,
    val mark: Mark,
)
