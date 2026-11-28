package io.github.galis228.materialereader.parse

import io.github.galis228.materialereader.ui.findFootnote
import io.github.galis228.materialereader.ui.sentenceAround
import io.github.galis228.materialereader.ui.wordAt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class FootnoteTest {
    @Test fun textBuilderKeepsRanges() {
        val tb = TextBuilder()
        tb.text("  Текст \n с  "); tb.ref("[1]", "n1"); tb.text(" дальше. ")
        val (t, refs) = tb.build()
        assertEquals("Текст с [1] дальше.", t)
        assertEquals("[1]", t.substring(refs[0].start, refs[0].end))
        assertEquals("1", refs[0].label)
    }

    @Test fun fb2Notes() {
        val xml = """<?xml version="1.0" encoding="utf-8"?>
<FictionBook xmlns="http://www.gribuser.ru/xml/fictionbook/2.0" xmlns:l="http://www.w3.org/1999/xlink">
<description><title-info><book-title>Б</book-title></title-info></description>
<body><section><p>Слово<a l:href="#n_1" type="note">[1]</a> и <emphasis>ещё</emphasis>.</p></section></body>
<body name="notes"><section id="n_1"><title><p>1</p></title><p>Пояснение к слову.</p></section></body></FictionBook>"""
        val b = Fb2Parser.parse(xml.toByteArray(), "b.fb2")
        val p = b.blocks.first()
        assertEquals("Слово[1] и ещё.", p.text)
        assertEquals("n_1", p.refs.single().id)
        assertEquals("Пояснение к слову.", b.notes["n_1"])
        assertEquals("Пояснение к слову.", findFootnote(b, 0, p.allRefs.single()).text)
    }

    @Test fun epubNotes() {
        val bos = ByteArrayOutputStream()
        ZipOutputStream(bos).use { z ->
            fun put(n: String, c: String) { z.putNextEntry(ZipEntry(n)); z.write(c.toByteArray()); z.closeEntry() }
            put("META-INF/container.xml", """<container><rootfiles><rootfile full-path="c.opf"/></rootfiles></container>""")
            put("c.opf", """<package><metadata><dc:title>T</dc:title></metadata><manifest>
                <item id="a" href="t/a.xhtml" media-type="application/xhtml+xml"/><item id="n" href="t/notes.xhtml" media-type="application/xhtml+xml"/></manifest>
                <spine><itemref idref="a"/><itemref idref="n"/></spine></package>""")
            put("t/a.xhtml", """<html><body><p>Текст<sup><a epub:type="noteref" href="notes.xhtml#fn1">1</a></sup> тут.</p>
                <aside epub:type="footnote" id="x"><p>Скрытая</p></aside><p>Второй<a href="#loc">2</a>.</p><p id="loc">2. Локальная сноска</p></body></html>""")
            put("t/notes.xhtml", """<html><body><ol><li id="fn1"><a href="a.xhtml">↑</a> Текст сноски.</li></ol></body></html>""")
        }
        val b = EpubParser.parse(bos.toByteArray(), "t.epub")
        assertEquals("Текст1 тут.", b.blocks[0].text)
        assertEquals("t/notes.xhtml#fn1", b.blocks[0].refs.single().id)
        assertEquals("Текст сноски.", b.notes["t/notes.xhtml#fn1"])
        assert(b.blocks.none { it.text.contains("Скрытая") })
        assertEquals("Локальная сноска", b.notes["t/a.xhtml#loc"])
    }

    @Test fun bareBrackets() {
        val book = ParsedBook("x", "", listOf(
            Block(Block.Kind.PARAGRAPH, "Он ушёл[3] навсегда."),
            Block(Block.Kind.PARAGRAPH, "Обычный текст."),
            Block(Block.Kind.PARAGRAPH, "[3] Это сноска номер три."),
        ), listOf(Chapter("x", 0)))
        val r = book.blocks[0].allRefs.single()
        assertEquals("3", r.label)
        val n = findFootnote(book, 0, r)
        assertEquals("Это сноска номер три.", n.text)
        assertEquals(2, n.block)
        assertNull(findFootnote(book, 0, NoteRef(0, 1, null, "9")).text)
    }

    @Test fun words() {
        val t = "Кто-то сказал: «привет, мир!» и ушёл."
        assertEquals("Кто-то", t.substring(wordAt(t, 2).first, wordAt(t, 2).last + 1))
        assertEquals("привет", t.substring(wordAt(t, 17).first, wordAt(t, 17).last + 1))
        assertEquals("Кто-то сказал: «привет, мир!»", sentenceAround(t, 16..21))
    }
}
