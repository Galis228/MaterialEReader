package io.github.galis228.materialereader

import io.github.galis228.materialereader.parse.Block
import io.github.galis228.materialereader.parse.EpubParser
import io.github.galis228.materialereader.parse.Fb2Parser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.util.Base64
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class RichBookTest {
    private fun zip(vararg entries: Pair<String, String>): ByteArray {
        val bos = ByteArrayOutputStream()
        ZipOutputStream(bos).use { z -> for ((n, c) in entries) { z.putNextEntry(ZipEntry(n)); z.write(c.toByteArray()); z.closeEntry() } }
        return bos.toByteArray()
    }

    private val epub get() = zip(
        "mimetype" to "application/epub+zip",
        "META-INF/container.xml" to """<container><rootfiles><rootfile full-path="OEBPS/content.opf"/></rootfiles></container>""",
        "OEBPS/content.opf" to """<package><metadata xmlns:dc="http://purl.org/dc/elements/1.1/"><dc:title>Т</dc:title><dc:description>&lt;p&gt;Про море.&lt;/p&gt;</dc:description></metadata>
            <manifest><item id="a" href="a.xhtml" media-type="application/xhtml+xml"/><item id="b" href="b.xhtml" media-type="application/xhtml+xml"/></manifest>
            <spine><itemref idref="a"/><itemref idref="b"/></spine></package>""",
        "OEBPS/pic.png" to "PNGDATA",
        "OEBPS/a.xhtml" to """<html><body><h1>Начало</h1><p>См. <a href="b.xhtml#end">конец</a>.</p><p><img src="pic.png" alt="Карта"/></p>
            <div class="poem"><div class="stanza"><p>Строка один</p><p>Строка два</p></div><div class="stanza"><p>Три<br/>Четыре</p></div></div>
            <table><tr><th>Год</th><th>Событие</th></tr><tr><td>1805</td><td>Аустерлиц</td></tr></table></body></html>""",
        "OEBPS/b.xhtml" to """<html><body><h1>Финал</h1><p>Текст.</p><p id="end">Конец.</p></body></html>""",
    )

    @Test fun epubPicturesVerseTablesLinks() {
        val b = EpubParser.parse(epub, "x.epub")
        val img = b.blocks.single { it.kind == Block.Kind.IMAGE }
        assertEquals("OEBPS/pic.png", img.src); assertEquals("Карта", img.text)
        assertTrue(b.images.containsKey("OEBPS/pic.png"))
        val verses = b.blocks.filter { it.kind == Block.Kind.VERSE }
        assertEquals(listOf("Строка один\nСтрока два", "Три\nЧетыре"), verses.map { it.text })
        assertEquals("Год │ Событие\n1805 │ Аустерлиц", b.blocks.single { it.kind == Block.Kind.TABLE }.text)
        val link = b.blocks.first { it.links.isNotEmpty() }.links.single()
        assertEquals("конец", b.blocks.first { it.links.isNotEmpty() }.text.substring(link.start, link.end))
        assertEquals("Конец.", b.blocks[b.anchors.getValue(link.target)].text)
        assertEquals("Про море.", b.description)
    }

    @Test fun fb2StanzasAndImages() {
        val png = Base64.getEncoder().encodeToString("PNGDATA".toByteArray())
        val xml = """<?xml version="1.0" encoding="utf-8"?><FictionBook xmlns:l="http://www.w3.org/1999/xlink"><description><title-info>
            <book-title>Стихи</book-title><annotation><p>О любви.</p></annotation><date>1830</date></title-info></description>
            <body><section><title><p>Часть</p></title><p>Смотри <a l:href="#s2">дальше</a>.</p><image l:href="#p1"/>
            <poem><stanza><v>Я помню</v><v>чудное мгновенье</v></stanza></poem></section>
            <section id="s2"><title><p>Вторая</p></title><p>Тут.</p></section></body>
            <binary id="p1" content-type="image/png">$png</binary></FictionBook>"""
        val b = Fb2Parser.parse(xml.toByteArray(), "s.fb2")
        assertEquals("Я помню\nчудное мгновенье", b.blocks.single { it.kind == Block.Kind.VERSE }.text)
        assertEquals("p1", b.blocks.single { it.kind == Block.Kind.IMAGE }.src)
        assertEquals("PNGDATA", String(b.images.getValue("p1")))
        val l = b.blocks.first { it.links.isNotEmpty() }.links.single()
        assertEquals("Вторая", b.blocks[b.anchors.getValue(l.target)].text)
        assertEquals("О любви.", b.description); assertEquals("1830", b.year)
    }
}
