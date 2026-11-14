package com.alanmaster.xread.data

import org.w3c.dom.Document
import org.w3c.dom.Element
import org.w3c.dom.Node
import org.xml.sax.ErrorHandler
import org.xml.sax.InputSource
import org.xml.sax.SAXParseException
import java.io.ByteArrayInputStream
import java.io.StringReader
import java.nio.charset.Charset
import java.util.zip.ZipInputStream
import javax.xml.parsers.DocumentBuilderFactory

/**
 * EPUB 里的一个章节：一串已切好的段落。
 */
data class EpubChapter(val id: String, val title: String, val paragraphs: List<String>)

/**
 * EPUB 解析结果。
 */
data class EpubBook(
    val title: String,
    val author: String,
    val description: String = "",
    val coverBytes: ByteArray? = null,
    val coverExtension: String = "jpg",
    val chapters: List<EpubChapter> = emptyList(),
)

/**
 * EPUB 解析器：把一本 epub 拆成「书籍信息 + 封面 + 章节段落」。
 *
 * 边界：这里**只做结构解析，不做校验**。
 * - 不验合规（mimetype 是否首项、OPF 声明的 media-type 对不对，一律不查），能读出结构就算成功；
 * - 不解密、不执行脚本、不联网、不写文件，输入只有一段字节，输出只有一份数据对象；
 * - 遇到看不懂的标签按纯文本处理，不报错，尽量把能读的部分读出来。
 *
 * 这么取舍是因为同一本书在不同工具下打包出来的目录结构、编码、命名都不一样，
 * 宁可「读出一部分」，也不要因为一处不标准就整本失败。
 *
 * 章节 id 从 "1" 开始连续编号：本项目的 `Api.checkId` 限定 `[0-9]{1,24}`，
 * 离线仓库与阅读进度都按这个编号寻址，所以这里不能出现 epub 原始文件名。
 */
object EpubParser {

    private const val CONTAINER_PATH = "META-INF/container.xml"
    private const val CORRUPTED = "EPUB 格式不正确或文件已损坏"
    private const val NO_BODY = "EPUB 里没有可读正文"

    /** 块级标签：出现即视为段落边界。 */
    private val BLOCK_TAG = Regex(
        "(?i)</?(?:p|div|h[1-6]|li|ul|ol|dl|dt|dd|blockquote|pre|figure|figcaption|" +
            "section|article|aside|header|footer|nav|main|hr|table|thead|tbody|tfoot|tr|td|th|br)\\b[^>]*>"
    )

    /** 其它标签：直接抹掉，只留标签里的文字。 */
    private val ANY_TAG = Regex("(?s)</?[A-Za-z][^>]*>")

    private val SCRIPT_STYLE = Regex("(?is)<(?:script|style)\\b[^>]*>.*?</(?:script|style)>")
    private val COMMENT = Regex("(?s)<!--.*?-->")
    private val ENTITY = Regex("&(#[xX][0-9a-fA-F]+|#\\d+|[A-Za-z][A-Za-z0-9]*);")
    private val WHITE_SPACE = Regex("\\s+")
    private val ENCODING_DECL = Regex("(?i)<\\?xml[^>]*encoding\\s*=\\s*[\"']([^\"']+)[\"']")

    private val NAMED_ENTITIES: Map<String, String> = mapOf(
        "amp" to "&", "lt" to "<", "gt" to ">", "quot" to "\"", "apos" to "'",
        "nbsp" to "\u00A0", "ensp" to "\u2002", "emsp" to "\u2003", "thinsp" to "\u2009",
        "hellip" to "\u2026", "mdash" to "\u2014", "ndash" to "\u2013",
        "lsquo" to "\u2018", "rsquo" to "\u2019", "ldquo" to "\u201C", "rdquo" to "\u201D",
        "middot" to "\u00B7", "bull" to "\u2022", "copy" to "\u00A9", "reg" to "\u00AE",
        "trade" to "\u2122", "deg" to "\u00B0", "laquo" to "\u00AB", "raquo" to "\u00BB",
        "times" to "\u00D7", "divide" to "\u00F7", "plusmn" to "\u00B1", "frac12" to "\u00BD",
        "frac14" to "\u00BC", "frac34" to "\u00BE", "sect" to "\u00A7", "para" to "\u00B6",
        "dagger" to "\u2020", "prime" to "\u2032", "permil" to "\u2030", "euro" to "\u20AC",
        "pound" to "\u00A3", "yen" to "\u00A5", "cent" to "\u00A2",
        "alpha" to "\u03B1", "beta" to "\u03B2", "gamma" to "\u03B3", "delta" to "\u03B4",
        "pi" to "\u03C0", "sigma" to "\u03C3", "omega" to "\u03C9",
        "larr" to "\u2190", "rarr" to "\u2192", "uarr" to "\u2191", "darr" to "\u2193",
        "harr" to "\u2194", "infin" to "\u221E", "ne" to "\u2260", "le" to "\u2264", "ge" to "\u2265",
    )

    /**
     * 解析 EPUB 字节流。失败抛 IllegalArgumentException，message 用中文说明原因。
     */
    fun parse(bytes: ByteArray): EpubBook {
        val entries = readZip(bytes)
        val opfPath = locateOpf(entries) ?: throw IllegalArgumentException(CORRUPTED)
        val opfBytes = entries.entryOf(opfPath) ?: throw IllegalArgumentException(CORRUPTED)
        val opfRoot = parseXml(opfBytes)?.documentElement ?: throw IllegalArgumentException(CORRUPTED)
        val opfDir = directoryOf(opfPath)

        val metadata = elements(opfRoot, "metadata").firstOrNull()
        val title = metadata?.let { firstText(it, "title") }.orEmpty()
        val author = metadata?.let { firstText(it, "creator") }.orEmpty()
        val description = metadata?.let { firstText(it, "description") }.orEmpty()

        val manifest = readManifest(opfRoot)
        val spine = elements(opfRoot, "spine").firstOrNull()
        val titles = readTitles(entries, manifest, opfDir, spine)
        val chapters = readChapters(entries, manifest, opfDir, spine, titles)
        val cover = readCover(entries, manifest, opfDir)

        return EpubBook(
            title = title,
            author = author,
            description = description,
            coverBytes = cover?.bytes,
            coverExtension = cover?.extension ?: "jpg",
            chapters = chapters,
        )
    }

    // ---------------------------------------------------------------- zip 层

    /**
     * 展开整个 zip 到内存。
     *
     * epub 的体积通常在几 MB 上下，整本读进内存比反复回读 zip 简单得多，
     * 也省掉「同一文件被章节、目录、封面各解压一次」的重复开销。
     */
    private fun readZip(bytes: ByteArray): Map<String, ByteArray> {
        val entries = LinkedHashMap<String, ByteArray>()
        try {
            ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    if (!entry.isDirectory) {
                        val name = entry.name.removePrefix("./")
                        if (name.isNotEmpty()) entries[name] = zip.readBytes()
                    }
                    zip.closeEntry()
                }
            }
        } catch (e: Exception) {
            throw IllegalArgumentException(CORRUPTED)
        }
        return entries
    }

    /**
     * 按名字取条目：精确命中优先，其次忽略大小写，最后按 percent 解码后再比一次。
     *
     * OPF 里的 href 常写成 percent 编码（`ch%201.xhtml`），zip 里的文件名却是原样，
     * 只按精确名匹配会漏掉带空格的文件名。
     */
    private fun Map<String, ByteArray>.entryOf(path: String): ByteArray? {
        if (path.isEmpty()) return null
        this[path]?.let { return it }
        val decoded = percentDecode(path)
        this[decoded]?.let { return it }
        return entries.firstOrNull { (key, _) ->
            key.equals(path, ignoreCase = true) || key.equals(decoded, ignoreCase = true)
        }?.value
    }

    // ------------------------------------------------------------ 元数据层

    /** container.xml → rootfile 的 full-path → OPF 在 zip 里的路径。 */
    private fun locateOpf(entries: Map<String, ByteArray>): String? {
        val container = entries.entryOf(CONTAINER_PATH) ?: return null
        val doc = parseXml(container)
        val fullPath = if (doc != null) {
            elements(doc.documentElement, "rootfile")
                .firstOrNull { it.getAttribute("full-path").isNotBlank() }
                ?.getAttribute("full-path")
        } else {
            null
        } ?: Regex("full-path\\s*=\\s*[\"']([^\"']+)[\"']").find(String(container, Charsets.UTF_8))
            ?.groupValues?.get(1)
            ?: return null
        return normalizePath(resolve("", fullPath))
    }

    private data class ManifestItem(
        val id: String,
        val href: String,
        val mediaType: String,
        val properties: String,
    )

    private fun readManifest(opfRoot: Element): Map<String, ManifestItem> {
        val manifest = elements(opfRoot, "manifest").firstOrNull() ?: return emptyMap()
        val out = LinkedHashMap<String, ManifestItem>()
        for (item in elements(manifest, "item")) {
            val id = item.getAttribute("id").ifBlank { item.getAttribute("href") }
            if (id.isEmpty()) continue
            out[id] = ManifestItem(
                id = id,
                href = item.getAttribute("href"),
                mediaType = item.getAttribute("media-type"),
                properties = item.getAttribute("properties"),
            )
        }
        return out
    }

    /**
     * 按 spine 顺序把章节读出来。
     *
     * 单章出问题（文件缺失、正文为空）只跳这一章，不让整本失败；
     * 一章都没读出来才算失败，因为那说明这本书没法看。
     */
    private fun readChapters(
        entries: Map<String, ByteArray>,
        manifest: Map<String, ManifestItem>,
        opfDir: String,
        spine: Element?,
        titles: Map<String, String>,
    ): List<EpubChapter> {
        val chapters = ArrayList<EpubChapter>()
        if (spine != null) {
            for (ref in elements(spine, "itemref")) {
                val idref = ref.getAttribute("idref").ifBlank { ref.getAttribute("id") }
                val item = manifest[idref] ?: continue
                if (item.href.isBlank()) continue
                val path = normalizePath(resolve(opfDir, item.href))
                val bytes = entries.entryOf(path) ?: continue
                val html = decodeText(bytes)
                val paragraphs = extractParagraphs(html)
                if (paragraphs.isEmpty()) continue
                val id = (chapters.size + 1).toString()
                val title = titles[path]?.takeIf { it.isNotBlank() }
                    ?: firstHeading(html).takeIf { it.isNotBlank() }
                    ?: "第 $id 章"
                chapters.add(EpubChapter(id = id, title = title, paragraphs = paragraphs))
            }
        }
        if (chapters.isEmpty()) throw IllegalArgumentException(NO_BODY)
        return chapters
    }

    /**
     * 章节标题表：优先 toc.ncx（EPUB2），其次 nav.xhtml（EPUB3）。
     *
     * 目录里的 href 相对于「目录文件自己所在的目录」，而章节文件相对于 OPF 所在目录，
     * 两者经常不同，所以每份目录都按两种基准各解析一遍，避免对不上。
     */
    private fun readTitles(
        entries: Map<String, ByteArray>,
        manifest: Map<String, ManifestItem>,
        opfDir: String,
        spine: Element?,
    ): Map<String, String> {
        val titles = LinkedHashMap<String, String>()

        val ncxItem = manifest[spine?.getAttribute("toc").orEmpty()]
            ?: manifest.values.firstOrNull { it.mediaType.equals("application/x-dtbncx+xml", true) }
            ?: manifest.values.firstOrNull { it.href.endsWith(".ncx", true) }
        if (ncxItem != null && ncxItem.href.isNotBlank()) {
            val path = normalizePath(resolve(opfDir, ncxItem.href))
            entries.entryOf(path)?.let { bytes ->
                val ncxDir = directoryOf(path)
                titles.mergeTitles(ncxTitles(bytes, ncxDir))
                if (ncxDir != opfDir) titles.mergeTitles(ncxTitles(bytes, opfDir))
            }
        }

        val navItem = manifest.values.firstOrNull { item ->
            item.properties.split(Regex("\\s+")).any { it.equals("nav", true) }
        } ?: manifest.values.firstOrNull { it.href.endsWith("nav.xhtml", true) }
        if (navItem != null && navItem.href.isNotBlank()) {
            val path = normalizePath(resolve(opfDir, navItem.href))
            entries.entryOf(path)?.let { bytes ->
                val navDir = directoryOf(path)
                titles.mergeTitles(navTitles(decodeText(bytes), navDir))
                if (navDir != opfDir) titles.mergeTitles(navTitles(decodeText(bytes), opfDir))
            }
        }

        return titles
    }

    /**
     * 合并目录标题：已经有的键不覆盖。
     *
     * EPUB2 的 toc.ncx 与 EPUB3 的 nav.xhtml 可能同时存在，
     * 按规范 toc.ncx 优先，所以后来的 nav 只能补位，不能把目录里的标题改掉。
     */
    private fun MutableMap<String, String>.mergeTitles(source: Map<String, String>) {
        for ((key, value) in source) {
            if (!containsKey(key)) put(key, value)
        }
    }

    /** EPUB2 目录：navPoint 的 text 对上 content 的 src。 */
    private fun ncxTitles(bytes: ByteArray, baseDir: String): Map<String, String> {
        val doc = parseXml(bytes) ?: return emptyMap()
        val root = doc.documentElement ?: return emptyMap()
        val out = LinkedHashMap<String, String>()
        for (point in elements(root, "navPoint")) {
            val label = elements(point, "navLabel")
                .firstOrNull()
                ?.let { elements(it, "text").firstOrNull() }
                ?.let { plainText(textOf(it)) }
                .orEmpty()
            val src = elements(point, "content").firstOrNull()?.getAttribute("src").orEmpty()
            if (label.isEmpty() || src.isEmpty()) continue
            val path = normalizePath(resolve(baseDir, src))
            if (path.isEmpty()) continue
            out.getOrPut(path) { label }
        }
        return out
    }

    /**
     * EPUB3 目录：nav.xhtml 里 toc 导航的 `<a href>`。
     *
     * 这里用正则而不是 DOM：EPUB3 的 nav.xhtml 常带 `&nbsp;` 这类 XML 未定义的实体，
     * DOM 解析会直接失败，而链接文字本来就要按纯文本取。
     */
    private fun navTitles(html: String, baseDir: String): Map<String, String> {
        val out = LinkedHashMap<String, String>()
        val anchors = Regex("(?is)<a\\b[^>]*href\\s*=\\s*([\"'])(.*?)\\1[^>]*>(.*?)</a>")
        for (match in anchors.findAll(navScope(html))) {
            val href = match.groupValues[2].trim()
            val label = plainText(match.groupValues[3])
            if (href.isEmpty() || label.isBlank()) continue
            val path = normalizePath(resolve(baseDir, href))
            if (path.isEmpty()) continue
            out.getOrPut(path) { label }
        }
        return out
    }

    /** 优先取 epub:type="toc" 的 nav 块，取不到就退回整份文档。 */
    private fun navScope(html: String): String {
        val blocks = Regex("(?is)<nav\\b[^>]*>.*?</nav>").findAll(html).map { it.value }.toList()
        if (blocks.isEmpty()) return html
        return blocks.firstOrNull { it.contains("epub:type") && it.contains("toc") }
            ?: blocks.firstOrNull { it.contains("toc") }
            ?: blocks.first()
    }

    // -------------------------------------------------------------- 封面层

    private data class Cover(val bytes: ByteArray, val extension: String)

    /**
     * 封面：manifest 里 media-type 是图片、且 id 或文件名带 cover 的那一条。
     * 扩展名优先用文件名，其次按 media-type 猜，都猜不出就用 jpg。
     */
    private fun readCover(
        entries: Map<String, ByteArray>,
        manifest: Map<String, ManifestItem>,
        opfDir: String,
    ): Cover? {
        val item = manifest.values.firstOrNull { entry ->
            entry.mediaType.startsWith("image/", ignoreCase = true) &&
                (entry.id.contains("cover", true) || entry.href.contains("cover", true))
        } ?: return null
        val path = normalizePath(resolve(opfDir, item.href))
        val bytes = entries.entryOf(path) ?: return null
        return Cover(bytes, extensionOf(item.href, item.mediaType))
    }

    private fun extensionOf(href: String, mediaType: String): String {
        val fromName = href.substringAfterLast('/', "")
            .substringAfterLast('.', "")
            .lowercase()
        if (fromName in COVER_EXTENSIONS) return fromName
        val fromType = mediaType.substringAfterLast('/', "").lowercase()
        if (fromType in COVER_EXTENSIONS) return fromType
        return "jpg"
    }

    private val COVER_EXTENSIONS = setOf("jpg", "jpeg", "png", "webp")

    // -------------------------------------------------------------- 正文层

    /**
     * XHTML 正文 → 段落列表。
     *
     * 顺序很重要：先去 script/style 与注释，再只取 body，
     * 然后把块级标签换成换行（段落边界），再抹掉其余标签，最后才解实体。
     * 实体放在最后解，是为了让正文里出现的 `&lt;p&gt;` 不会被当成真标签。
     */
    private fun extractParagraphs(html: String): List<String> {
        var text = SCRIPT_STYLE.replace(html, " ")
        text = COMMENT.replace(text, " ")
        text = bodyOf(text)
        text = BLOCK_TAG.replace(text, "\n")
        text = ANY_TAG.replace(text, "")
        text = decodeEntities(text)
        return Paragraphs.split(text)
    }

    /** 有 body 就只取 body，没有就抹掉 head 用剩下的。 */
    private fun bodyOf(html: String): String {
        val bodyStart = Regex("(?is)<body\\b[^>]*>").find(html)
        if (bodyStart != null) {
            val from = bodyStart.range.last + 1
            val to = html.indexOf("</body>", from, ignoreCase = true)
            return if (to < 0) html.substring(from) else html.substring(from, to)
        }
        val head = Regex("(?is)<head\\b[^>]*>.*?</head>").find(html)
        return if (head != null) html.removeRange(head.range) else html
    }

    /** 章节标题回退：先 h1，再 h2，再 title。 */
    private fun firstHeading(html: String): String {
        for (tag in listOf("h1", "h2", "title")) {
            val match = Regex("(?is)<$tag\\b[^>]*>(.*?)</$tag\\s*>").find(html) ?: continue
            val text = plainText(match.groupValues[1])
            if (text.isNotBlank()) return text
        }
        return ""
    }

    /** 抽纯文本：去标签、解实体、空白折叠。 */
    private fun plainText(raw: String): String {
        val noTag = ANY_TAG.replace(raw, " ")
        val decoded = decodeEntities(noTag).replace('\u00A0', ' ')
        return WHITE_SPACE.replace(decoded, " ").trim()
    }

    /** 解 HTML 实体。未知实体原样保留，不吞内容。 */
    private fun decodeEntities(text: String): String {
        if (!text.contains('&')) return text
        return ENTITY.replace(text) { match ->
            val body = match.groupValues[1]
            when {
                body.startsWith("#") -> {
                    val code = if (body.length > 1 && (body[1] == 'x' || body[1] == 'X')) {
                        body.substring(2).toIntOrNull(16)
                    } else {
                        body.substring(1).toIntOrNull()
                    }
                    if (code != null && code in 1..0x10FFFF) String(Character.toChars(code)) else match.value
                }
                else -> NAMED_ENTITIES[body.lowercase()] ?: match.value
            }
        }
    }

    // ------------------------------------------------------------ 文本编码

    /** 优先看 XML 声明里的编码，其次 BOM，默认 UTF-8。 */
    private fun decodeText(bytes: ByteArray): String {
        val head = String(bytes.take(256).toByteArray(), Charsets.UTF_8)
        val declared = ENCODING_DECL.find(head)?.groupValues?.get(1)
        val charset = declared?.let { name -> runCatching { Charset.forName(name) }.getOrNull() }
        val text = String(bytes, charset ?: Charsets.UTF_8)
        return if (text.startsWith("\uFEFF")) text.substring(1) else text
    }

    // ---------------------------------------------------------------- 路径

    /** href 相对 base 目录解析；去掉锚点与查询串，处理 percent 编码。 */
    private fun resolve(base: String, href: String): String {
        val clean = href.substringBefore('#').substringBefore('?').trim()
        if (clean.isEmpty()) return ""
        val decoded = percentDecode(clean)
        if (decoded.startsWith("/")) return decoded.trimStart('/')
        return if (base.isEmpty()) decoded else "$base/$decoded"
    }

    /** 消掉 `.` 与 `..`，得到 zip 里可直接命中的相对路径。 */
    private fun normalizePath(path: String): String {
        if (path.isEmpty()) return ""
        val parts = ArrayList<String>()
        for (segment in path.split('/')) {
            when (segment) {
                "", "." -> Unit
                ".." -> if (parts.isNotEmpty()) parts.removeAt(parts.size - 1)
                else -> parts.add(segment)
            }
        }
        return parts.joinToString("/")
    }

    private fun directoryOf(path: String): String {
        val cut = path.lastIndexOf('/')
        return if (cut < 0) "" else path.substring(0, cut)
    }

    /** 只解 %XX，不动 '+'（路径里的加号是文件名的一部分）。 */
    private fun percentDecode(text: String): String {
        if (!text.contains('%')) return text
        val bytes = ArrayList<Byte>()
        var i = 0
        while (i < text.length) {
            val c = text[i]
            if (c == '%' && i + 2 < text.length) {
                val value = text.substring(i + 1, i + 3).toIntOrNull(16)
                if (value != null) {
                    bytes.add(value.toByte())
                    i += 3
                    continue
                }
            }
            bytes.addAll(c.toString().toByteArray(Charsets.UTF_8).toList())
            i++
        }
        return String(bytes.toByteArray(), Charsets.UTF_8)
    }

    // ------------------------------------------------------------------ DOM

    /** 解析 XML；解析不了返回 null，由调用方决定是跳过还是判为坏文件。 */
    private fun parseXml(bytes: ByteArray): Document? = try {
        val factory = DocumentBuilderFactory.newInstance()
        factory.isNamespaceAware = false
        factory.isValidating = false
        // 实体保持默认的「展开」：这样 `&amp;` 直接变成文本节点，
        // 读出来的文字就是书里的内容，不用再自己补一遍实体表。
        // epub 里偶尔带外部 DTD，关掉免得解析时去下载。
        runCatching { factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false) }
        runCatching { factory.setFeature("http://xml.org/sax/features/external-general-entities", false) }
        runCatching { factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false) }
        val builder = factory.newDocumentBuilder()
        builder.setEntityResolver { _, _ -> InputSource(StringReader("")) }
        builder.setErrorHandler(QUIET_HANDLER)
        builder.parse(InputSource(ByteArrayInputStream(bytes)))
    } catch (e: Exception) {
        null
    }

    /** 忽略解析器的告警，致命错误照抛（由上层转成 null）。 */
    private val QUIET_HANDLER = object : ErrorHandler {
        override fun warning(exception: SAXParseException) = Unit
        override fun error(exception: SAXParseException) = Unit
        override fun fatalError(exception: SAXParseException): Nothing = throw exception
    }

    /** 按 local name 找后代元素（去掉命名空间前缀），顺序即文档顺序。 */
    private fun elements(scope: Element, localName: String): List<Element> {
        val nodes = scope.getElementsByTagName("*")
        val out = ArrayList<Element>()
        for (i in 0 until nodes.length) {
            val element = nodes.item(i) as? Element ?: continue
            if (localNameOf(element) == localName) out.add(element)
        }
        return out
    }

    private fun localNameOf(element: Element): String {
        val name = element.nodeName ?: return ""
        return if (name.contains(':')) name.substringAfter(':') else name
    }

    /** 取第一个非空元素的纯文本。 */
    private fun firstText(scope: Element, localName: String): String {
        for (element in elements(scope, localName)) {
            val text = plainText(textOf(element))
            if (text.isNotBlank()) return text
        }
        return ""
    }

    /**
     * 取节点下的纯文本。
     *
     * 不用 `textContent`：CDATA 与普通文本要分头收，实体引用节点也顺手兜住，
     * 自己递归一次比判断节点类型再回头补一遍更省事。
     */
    private fun textOf(node: Node): String {
        val sb = StringBuilder()
        val children = node.childNodes
        for (i in 0 until children.length) {
            val child = children.item(i)
            when (child.nodeType) {
                Node.TEXT_NODE, Node.CDATA_SECTION_NODE -> sb.append(child.nodeValue ?: "")
                Node.ENTITY_REFERENCE_NODE -> sb.append(textOf(child))
                else -> if (child.hasChildNodes()) sb.append(textOf(child))
            }
        }
        return sb.toString()
    }
}
