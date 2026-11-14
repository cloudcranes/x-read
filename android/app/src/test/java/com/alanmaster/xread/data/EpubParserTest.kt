package com.alanmaster.xread.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * EPUB 解析器的 JVM 单测。
 *
 * 这里现场用 ZipOutputStream 造最小 EPUB，不依赖任何真实书库文件：
 * 一来避免往仓库塞二进制样本，二来能把「目录/正文/封面/编码」这些边界单独钉死。
 */
class EpubParserTest {

    // ------------------------------------------------------------------ 工具

    private fun epub(vararg entries: Pair<String, ByteArray>): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            zip.putNextEntry(ZipEntry("mimetype"))
            zip.write("application/epub+zip".toByteArray())
            zip.closeEntry()
            for ((name, bytes) in entries) {
                zip.putNextEntry(ZipEntry(name))
                zip.write(bytes)
                zip.closeEntry()
            }
        }
        return out.toByteArray()
    }

    private fun text(value: String): ByteArray = value.toByteArray(Charsets.UTF_8)

    private fun container(rootFile: String = "OEBPS/content.opf"): ByteArray = text(
        """
        <?xml version="1.0" encoding="UTF-8"?>
        <container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
          <rootfiles>
            <rootfile full-path="$rootFile" media-type="application/oebps-package+xml"/>
          </rootfiles>
        </container>
        """.trimIndent()
    )

    /** EPUB2 书包：带 toc.ncx、两章正文、一张 jpg 封面。 */
    private fun epub2Bytes(): ByteArray = epub(
        "META-INF/container.xml" to container(),
        "OEBPS/content.opf" to text(
            """
            <?xml version="1.0" encoding="UTF-8"?>
            <package xmlns="http://www.idpf.org/2007/opf" version="2.0" unique-identifier="BookId">
              <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
                <dc:title>测试书名</dc:title>
                <dc:creator>测试作者</dc:creator>
                <dc:description><![CDATA[<p>第一行简介</p><p>第二行简介</p>]]></dc:description>
              </metadata>
              <manifest>
                <item id="ncx" href="toc.ncx" media-type="application/x-dtbncx+xml"/>
                <item id="cover" href="images/cover.jpg" media-type="image/jpeg"/>
                <item id="c1" href="text/ch1.xhtml" media-type="application/xhtml+xml"/>
                <item id="c2" href="text/ch2.xhtml" media-type="application/xhtml+xml"/>
              </manifest>
              <spine toc="ncx">
                <itemref idref="c1"/>
                <itemref idref="c2"/>
              </spine>
            </package>
            """.trimIndent()
        ),
        "OEBPS/toc.ncx" to text(
            """
            <?xml version="1.0" encoding="UTF-8"?>
            <ncx xmlns="http://www.daisy.org/z3986/2005/ncx/" version="2005-1">
              <head><meta name="dtb:uid" content="uid-1"/></head>
              <docTitle><text>测试书名</text></docTitle>
              <navMap>
                <navPoint id="np1" playOrder="1">
                  <navLabel><text>第一章 开端</text></navLabel>
                  <content src="text/ch1.xhtml"/>
                </navPoint>
                <navPoint id="np2" playOrder="2">
                  <navLabel><text>第二章 收束</text></navLabel>
                  <content src="text/ch2.xhtml"/>
                </navPoint>
              </navMap>
            </ncx>
            """.trimIndent()
        ),
        "OEBPS/text/ch1.xhtml" to text(
            """
            <?xml version="1.0" encoding="utf-8"?>
            <html xmlns="http://www.w3.org/1999/xhtml">
              <head><title>第一章 开端</title></head>
              <body>
                <h1>第一章 开端</h1>
                <p>第一段</p>
                <p>第二段<br/>换行后</p>
                <p>实体 &amp; &lt; &gt; &quot; &#39;</p>
                <p>　　</p>
                <p>末段</p>
              </body>
            </html>
            """.trimIndent()
        ),
        "OEBPS/text/ch2.xhtml" to text(
            """
            <?xml version="1.0" encoding="utf-8"?>
            <html xmlns="http://www.w3.org/1999/xhtml">
              <head><title>第二章 收束</title></head>
              <body><p>收束段落</p></body>
            </html>
            """.trimIndent()
        ),
        "OEBPS/images/cover.jpg" to byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte(), 0x00, 0x10),
    )

    // ------------------------------------------------------------------ 用例

    @Test
    fun parsesMetadataFromOpf() {
        val book = EpubParser.parse(epub2Bytes())
        assertEquals("测试书名", book.title)
        assertEquals("测试作者", book.author)
        // 简介里裹着 HTML 标签，抽出来必须是纯文本。
        assertEquals("第一行简介 第二行简介", book.description)
    }

    @Test
    fun decodesEntitiesInOpfMetadata() {
        // OPF 里写的是 XML 实体，读出来得是书里的原文：
        // & 要还原成 &，不能整段丢掉，也不能留在文字里。
        val bytes = epub(
            "META-INF/container.xml" to container(),
            "OEBPS/content.opf" to text(
                """
                <?xml version="1.0" encoding="UTF-8"?>
                <package xmlns="http://www.idpf.org/2007/opf" version="2.0">
                  <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
                    <dc:title>书架 &amp; 阁楼</dc:title>
                    <dc:creator>某 &amp; 某</dc:creator>
                    <dc:description>他说 &quot;好&quot;</dc:description>
                  </metadata>
                  <manifest>
                    <item id="c1" href="c1.xhtml" media-type="application/xhtml+xml"/>
                  </manifest>
                  <spine><itemref idref="c1"/></spine>
                </package>
                """.trimIndent()
            ),
            "OEBPS/c1.xhtml" to text("<html><body><p>正文</p></body></html>"),
        )
        val book = EpubParser.parse(bytes)
        assertEquals("书架 & 阁楼", book.title)
        assertEquals("某 & 某", book.author)
        assertEquals("他说 \"好\"", book.description)
    }

    @Test
    fun numbersChaptersFromOne() {
        val book = EpubParser.parse(epub2Bytes())
        assertEquals(2, book.chapters.size)
        assertEquals(listOf("1", "2"), book.chapters.map { it.id })
    }

    @Test
    fun takesChapterTitlesFromNcx() {
        val book = EpubParser.parse(epub2Bytes())
        assertEquals("第一章 开端", book.chapters[0].title)
        assertEquals("第二章 收束", book.chapters[1].title)
    }

    @Test
    fun splitsParagraphsAndDecodesEntities() {
        val book = EpubParser.parse(epub2Bytes())
        assertEquals(
            listOf(
                "第一章 开端",
                "第一段",
                "第二段",
                "换行后",
                "实体 & < > \" '",
                "末段",
            ),
            book.chapters[0].paragraphs,
        )
        assertEquals(listOf("收束段落"), book.chapters[1].paragraphs)
    }

    @Test
    fun dropsBlankOnlyParagraph() {
        // 只有全角空格的行按空白行丢掉（Paragraphs 的语义），不占段落号。
        val book = EpubParser.parse(epub2Bytes())
        assertTrue(book.chapters[0].paragraphs.none { it.isBlank() })
    }

    @Test
    fun readsCoverBytesAndExtension() {
        val book = EpubParser.parse(epub2Bytes())
        assertNotNull(book.coverBytes)
        assertEquals(6, book.coverBytes!!.size)
        assertEquals("jpg", book.coverExtension)
    }

    @Test
    fun takesCoverExtensionFromFileName() {
        val bytes = epub(
            "META-INF/container.xml" to container(),
            "OEBPS/content.opf" to text(
                """
                <?xml version="1.0" encoding="UTF-8"?>
                <package xmlns="http://www.idpf.org/2007/opf" version="2.0">
                  <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
                    <dc:title>图集</dc:title><dc:creator>画师</dc:creator>
                  </metadata>
                  <manifest>
                    <item id="cover-image" href="images/cover.png" media-type="image/png"/>
                    <item id="c1" href="c1.xhtml" media-type="application/xhtml+xml"/>
                  </manifest>
                  <spine><itemref idref="c1"/></spine>
                </package>
                """.trimIndent()
            ),
            "OEBPS/c1.xhtml" to text("<html><body><p>图</p></body></html>"),
            "OEBPS/images/cover.png" to byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47),
        )
        val book = EpubParser.parse(bytes)
        assertEquals("png", book.coverExtension)
        assertEquals(4, book.coverBytes!!.size)
    }

    @Test
    fun takesChapterTitlesFromEpub3Nav() {
        val bytes = epub(
            "META-INF/container.xml" to container(),
            "OEBPS/content.opf" to text(
                """
                <?xml version="1.0" encoding="UTF-8"?>
                <package xmlns="http://www.idpf.org/2007/opf" version="3.0">
                  <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
                    <dc:title>三书</dc:title><dc:creator>作者三</dc:creator>
                  </metadata>
                  <manifest>
                    <item id="nav" href="nav.xhtml" media-type="application/xhtml+xml" properties="nav"/>
                    <item id="c1" href="text/ch1.xhtml" media-type="application/xhtml+xml"/>
                    <item id="c2" href="text/ch2.xhtml" media-type="application/xhtml+xml"/>
                  </manifest>
                  <spine><itemref idref="c1"/><itemref idref="c2"/></spine>
                </package>
                """.trimIndent()
            ),
            "OEBPS/nav.xhtml" to text(
                """
                <?xml version="1.0" encoding="utf-8"?>
                <html xmlns="http://www.w3.org/1999/xhtml" xmlns:epub="http://www.idpf.org/2007/ops">
                  <head><title>目录</title></head>
                  <body>
                    <nav epub:type="toc" id="toc">
                      <ol>
                        <li><a href="text/ch1.xhtml">第一章 新序</a></li>
                        <li><a href="text/ch2.xhtml">第二章 终章</a></li>
                      </ol>
                    </nav>
                  </body>
                </html>
                """.trimIndent()
            ),
            "OEBPS/text/ch1.xhtml" to text("<html><body><p>新序正文</p></body></html>"),
            "OEBPS/text/ch2.xhtml" to text("<html><body><p>终章正文</p></body></html>"),
        )
        val book = EpubParser.parse(bytes)
        assertEquals("第一章 新序", book.chapters[0].title)
        assertEquals("第二章 终章", book.chapters[1].title)
    }

    @Test
    fun prefersNcxTitlesOverNav() {
        // 有些书两套目录都有，按规范 toc.ncx 优先，nav 只能补位。
        val bytes = epub(
            "META-INF/container.xml" to container(),
            "OEBPS/content.opf" to text(
                """
                <?xml version="1.0" encoding="UTF-8"?>
                <package xmlns="http://www.idpf.org/2007/opf" version="3.0">
                  <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
                    <dc:title>两套目录</dc:title><dc:creator>某</dc:creator>
                  </metadata>
                  <manifest>
                    <item id="ncx" href="toc.ncx" media-type="application/x-dtbncx+xml"/>
                    <item id="nav" href="nav.xhtml" media-type="application/xhtml+xml" properties="nav"/>
                    <item id="c1" href="text/ch1.xhtml" media-type="application/xhtml+xml"/>
                  </manifest>
                  <spine toc="ncx"><itemref idref="c1"/></spine>
                </package>
                """.trimIndent()
            ),
            "OEBPS/toc.ncx" to text(
                """
                <?xml version="1.0" encoding="UTF-8"?>
                <ncx xmlns="http://www.daisy.org/z3986/2005/ncx/" version="2005-1">
                  <navMap>
                    <navPoint id="np1" playOrder="1">
                      <navLabel><text>第一章 目录名</text></navLabel>
                      <content src="text/ch1.xhtml"/>
                    </navPoint>
                  </navMap>
                </ncx>
                """.trimIndent()
            ),
            "OEBPS/nav.xhtml" to text(
                """
                <?xml version="1.0" encoding="utf-8"?>
                <html xmlns="http://www.w3.org/1999/xhtml" xmlns:epub="http://www.idpf.org/2007/ops">
                  <body>
                    <nav epub:type="toc">
                      <ol><li><a href="text/ch1.xhtml">第一章 导航名</a></li></ol>
                    </nav>
                  </body>
                </html>
                """.trimIndent()
            ),
            "OEBPS/text/ch1.xhtml" to text("<html><body><p>正文</p></body></html>"),
        )
        val book = EpubParser.parse(bytes)
        assertEquals("第一章 目录名", book.chapters[0].title)
    }

    @Test
    fun fallsBackToHeadingThenGenericTitle() {
        val bytes = epub(
            "META-INF/container.xml" to container(),
            "OEBPS/content.opf" to text(
                """
                <?xml version="1.0" encoding="UTF-8"?>
                <package xmlns="http://www.idpf.org/2007/opf" version="2.0">
                  <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
                    <dc:title>无目录</dc:title><dc:creator>无名</dc:creator>
                  </metadata>
                  <manifest>
                    <item id="c1" href="c1.xhtml" media-type="application/xhtml+xml"/>
                    <item id="c2" href="c2.xhtml" media-type="application/xhtml+xml"/>
                  </manifest>
                  <spine><itemref idref="c1"/><itemref idref="c2"/></spine>
                </package>
                """.trimIndent()
            ),
            "OEBPS/c1.xhtml" to text("<html><body><h1>标题回退</h1><p>正文一</p></body></html>"),
            "OEBPS/c2.xhtml" to text("<html><body><p>正文二</p></body></html>"),
        )
        val book = EpubParser.parse(bytes)
        assertEquals("标题回退", book.chapters[0].title)
        // 既没有目录也没有标题标签时，用「第 N 章」兜底，编号跟章节 id 一致。
        assertEquals("第 2 章", book.chapters[1].title)
    }

    @Test
    fun skipsBrokenChapterButKeepsOthers() {
        val bytes = epub(
            "META-INF/container.xml" to container(),
            "OEBPS/content.opf" to text(
                """
                <?xml version="1.0" encoding="UTF-8"?>
                <package xmlns="http://www.idpf.org/2007/opf" version="2.0">
                  <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
                    <dc:title>缺章</dc:title><dc:creator>某</dc:creator>
                  </metadata>
                  <manifest>
                    <item id="c1" href="c1.xhtml" media-type="application/xhtml+xml"/>
                    <item id="c2" href="c2.xhtml" media-type="application/xhtml+xml"/>
                  </manifest>
                  <spine><itemref idref="c1"/><itemref idref="c2"/></spine>
                </package>
                """.trimIndent()
            ),
            "OEBPS/c1.xhtml" to text("<html><body><p>能读的一章</p></body></html>"),
        )
        val book = EpubParser.parse(bytes)
        assertEquals(1, book.chapters.size)
        assertEquals("1", book.chapters[0].id)
    }

    @Test
    fun throwsWhenNoReadableBody() {
        val bytes = epub(
            "META-INF/container.xml" to container(),
            "OEBPS/content.opf" to text(
                """
                <?xml version="1.0" encoding="UTF-8"?>
                <package xmlns="http://www.idpf.org/2007/opf" version="2.0">
                  <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
                    <dc:title>空书</dc:title><dc:creator>某</dc:creator>
                  </metadata>
                  <manifest>
                    <item id="c1" href="c1.xhtml" media-type="application/xhtml+xml"/>
                  </manifest>
                  <spine><itemref idref="c1"/></spine>
                </package>
                """.trimIndent()
            ),
            "OEBPS/c1.xhtml" to text("<html><body><p>　　</p></body></html>"),
        )
        try {
            EpubParser.parse(bytes)
            fail("没有正文时应抛 IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("EPUB"))
            assertEquals("EPUB 里没有可读正文", e.message)
        }
    }

    @Test
    fun throwsWhenContainerMissing() {
        val bytes = epub(
            "OEBPS/content.opf" to text("<package/>"),
        )
        try {
            EpubParser.parse(bytes)
            fail("缺 container.xml 时应抛 IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("EPUB"))
            assertEquals("EPUB 格式不正确或文件已损坏", e.message)
        }
    }

    @Test
    fun throwsWhenOpfMissing() {
        val bytes = epub(
            "META-INF/container.xml" to container("OEBPS/content.opf"),
        )
        try {
            EpubParser.parse(bytes)
            fail("缺 OPF 时应抛 IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("EPUB"))
            assertEquals("EPUB 格式不正确或文件已损坏", e.message)
        }
    }

    @Test
    fun throwsWhenZipBroken() {
        try {
            EpubParser.parse("not a zip at all".toByteArray())
            fail("坏 zip 应抛 IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("EPUB"))
            assertEquals("EPUB 格式不正确或文件已损坏", e.message)
        }
    }

    @Test
    fun keepsBookWithoutCover() {
        val bytes = epub(
            "META-INF/container.xml" to container(),
            "OEBPS/content.opf" to text(
                """
                <?xml version="1.0" encoding="UTF-8"?>
                <package xmlns="http://www.idpf.org/2007/opf" version="2.0">
                  <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
                    <dc:title>无图</dc:title><dc:creator>某</dc:creator>
                  </metadata>
                  <manifest>
                    <item id="c1" href="c1.xhtml" media-type="application/xhtml+xml"/>
                  </manifest>
                  <spine><itemref idref="c1"/></spine>
                </package>
                """.trimIndent()
            ),
            "OEBPS/c1.xhtml" to text("<html><body><p>正文</p></body></html>"),
        )
        val book = EpubParser.parse(bytes)
        assertNull(book.coverBytes)
        assertEquals("jpg", book.coverExtension)
        assertEquals(1, book.chapters.size)
    }
}
