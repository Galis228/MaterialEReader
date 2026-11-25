package io.github.galis228.materialereader.parse

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.util.Base64
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class ParserTest {

    private fun zip(vararg entries: Pair<String, String>): ByteArray {
        val bos = ByteArrayOutputStream()
        ZipOutputStream(bos).use { z ->
            for ((name, content) in entries) {
                z.putNextEntry(ZipEntry(name)); z.write(content.toByteArray(Charsets.UTF_8)); z.closeEntry()
            }
        }
        return bos.toByteArray()
    }

    private val png = Base64.getEncoder().encodeToString(byteArrayOf(1, 2, 3, 4))

    @Test fun epub() {
        val bytes = zip(
            "mimetype" to "application/epub+zip",
            "META-INF/container.xml" to """<?xml version="1.0"?><container><rootfiles><rootfile full-path="OEBPS/content.opf" media-type="application/oebps-package+xml"/></rootfiles></container>""",
            "OEBPS/content.opf" to """<?xml version="1.0"?><package xmlns="http://www.idpf.org/2007/opf"><metadata xmlns:dc="http://purl.org/dc/elements/1.1/"><dc:title>Тихая гавань</dc:title><dc:creator>Анна Петрова</dc:creator><meta name="cover" content="cov"/></metadata>
                <manifest><item id="cov" href="img/cover.jpg" media-type="image/jpeg"/><item id="c1" href="text/ch1.xhtml" media-type="application/xhtml+xml"/><item id="c2" href="text/ch%202.xhtml" media-type="application/xhtml+xml"/><item id="ncx" href="toc.ncx" media-type="application/x-dtbncx+xml"/></manifest>
                <spine toc="ncx"><itemref idref="c1"/><itemref idref="c2"/></spine></package>""",
            "OEBPS/img/cover.jpg" to "JPEG",
            "OEBPS/toc.ncx" to """<?xml version="1.0"?><ncx><navMap><navPoint><navLabel><text>Первая</text></navLabel><content src="text/ch1.xhtml"/></navPoint></navMap></ncx>""",
            "OEBPS/text/ch1.xhtml" to """<html><body><h1>Глава 1</h1><p>Утро&nbsp;пришло <i>рано</i>.</p><div><p>Вложенный абзац.</p></div><hr/></body></html>""",
            "OEBPS/text/ch 2.xhtml" to """<html><body><section><h2>Глава 2</h2><p>Второй<br/>текст.</p><blockquote><p>Цитата</p></blockquote></section></body></html>""",
        )
        val b = EpubParser.parse(bytes, "x.epub")
        assertEquals("Тихая гавань", b.title)
        assertEquals("Анна Петрова", b.author)
        assertNotNull(b.cover)
        assertEquals("Утро пришло рано.", b.blocks[1].text)
        assertEquals(Block.Kind.SEPARATOR, b.blocks[3].kind)
        assertEquals(listOf("Первая", "Глава 2"), b.chapters.map { it.title })
        assertEquals(Block.Kind.QUOTE, b.blocks.last().kind)
        assertEquals("Второй текст.", b.blocks[5].text)
    }

    @Test fun fb2() {
        val xml = """<?xml version="1.0" encoding="utf-8"?>
<FictionBook xmlns="http://www.gribuser.ru/xml/fictionbook/2.0" xmlns:l="http://www.w3.org/1999/xlink">
<description><title-info><author><first-name>Иван</first-name><last-name>Сидоров</last-name></author><book-title>Маяк</book-title><coverpage><image l:href="#cover.jpg"/></coverpage></title-info></description>
<body><title><p>Маяк</p></title><section><title><p>Глава первая</p></title><epigraph><p>Свет ждёт.</p></epigraph><p>Текст <emphasis>главы</emphasis>.</p><empty-line/><p>Второй.</p></section>
<section><title><p>Глава вторая</p></title><poem><stanza><v>Строка</v></stanza></poem></section></body>
<body name="notes"><section><p>Сноска</p></section></body>
<binary id="cover.jpg" content-type="image/jpeg">$png</binary></FictionBook>"""
        val b = Fb2Parser.parse(xml.toByteArray(), "m.fb2")
        assertEquals("Маяк", b.title)
        assertEquals("Иван Сидоров", b.author)
        assertEquals(4, b.cover!!.size)
        assertTrue(b.blocks.none { it.text == "Сноска" })
        assertEquals(listOf("Маяк", "Глава первая", "Глава вторая"), b.chapters.map { it.title })
        assertEquals(Block.Kind.EPIGRAPH, b.blocks[2].kind)
        assertEquals("Текст главы.", b.blocks[3].text)

        val z = ByteArrayOutputStream()
        ZipOutputStream(z).use { it.putNextEntry(ZipEntry("m.fb2")); it.write(xml.toByteArray()); it.closeEntry() }
        assertEquals("Маяк", Fb2Parser.parseZip(z.toByteArray(), "m.fb2.zip").title)
        assertEquals(BookFormat.FB2_ZIP, BookParser.detect(z.toByteArray(), "book"))
    }

    @Test fun txt() {
        val text = "Глава 1\n\nПервая строка\nпродолжение абзаца.\n\nВторой абзац.\n\n***\n\nГлава 2\n\nКонец."
        val cp1251 = text.toByteArray(charset("windows-1251"))
        val b = TxtParser.parse(cp1251, "my_book.txt")
        assertEquals("my book", b.title)
        assertEquals("Первая строка продолжение абзаца.", b.blocks[1].text)
        assertEquals(Block.Kind.SEPARATOR, b.blocks[3].kind)
        assertEquals(listOf("Глава 1", "Глава 2"), b.chapters.map { it.title })
        assertEquals(TxtParser.decode(text.toByteArray()), text)
    }
}
