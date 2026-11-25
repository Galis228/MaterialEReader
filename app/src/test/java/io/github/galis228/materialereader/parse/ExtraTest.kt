package io.github.galis228.materialereader.parse

import io.github.galis228.materialereader.data.Opds
import io.github.galis228.materialereader.data.Updater
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class ExtraTest {
    @Test fun naturalOrder() {
        val names = listOf("p10.jpg", "p2.jpg", "P1.jpg", "cover.jpg", "p02b.png")
        assertEquals(listOf("cover.jpg", "P1.jpg", "p2.jpg", "p02b.png", "p10.jpg"), names.sortedWith(NaturalOrder))
    }

    @Test fun cbz() {
        val bos = ByteArrayOutputStream()
        ZipOutputStream(bos).use { z ->
            for (n in listOf("ch1/10.png", "ch1/2.png", "__MACOSX/ch1/._2.png", "info.txt", "ch1/1.png")) {
                z.putNextEntry(ZipEntry(n)); z.write(n.toByteArray()); z.closeEntry()
            }
        }
        val bytes = bos.toByteArray()
        assertEquals(BookFormat.CBZ, BookParser.detect(bytes, "noext"))
        val f = File.createTempFile("comic", ".cbz").apply { writeBytes(bytes); deleteOnExit() }
        ComicArchive.open(f, BookFormat.CBZ).use { c ->
            assertEquals(listOf("ch1/1.png", "ch1/2.png", "ch1/10.png"), c.pages)
            assertEquals("ch1/10.png", String(c.page(2)))
        }
        assertEquals(BookFormat.CBR, BookParser.detect("Rar!\u001a\u0007\u0000xxxx".toByteArray(Charsets.ISO_8859_1), "a"))
    }

    @Test fun versions() {
        assertTrue(Updater.isNewer("0.10.0", "0.9.3"))
        assertTrue(Updater.isNewer("v0.2.1".removePrefix("v"), "0.2.0"))
        assertFalse(Updater.isNewer("0.2.0", "0.2.0"))
        assertFalse(Updater.isNewer("0.1.9", "0.2"))
    }

    @Test fun opds() {
        val xml = """<?xml version="1.0"?>
<feed xmlns="http://www.w3.org/2005/Atom" xmlns:opds="http://opds-spec.org/2010/catalog">
<title>Search</title>
<link rel="search" type="application/atom+xml" href="/search?q={searchTerms}"/>
<link rel="next" href="?start=26"/>
<entry><title>War and Peace</title><id>urn:1</id><author><name>Tolstoy</name></author>
  <link type="application/atom+xml;profile=opds-catalog" rel="subsection" href="/ebooks/2600.opds"/></entry>
<entry><title>Anna</title><id>urn:2</id>
  <link rel="http://opds-spec.org/acquisition" type="application/x-mobipocket-ebook" href="/a.mobi"/>
  <link rel="http://opds-spec.org/acquisition" type="application/epub+zip" href="/a.epub"/>
  <link rel="http://opds-spec.org/image/thumbnail" type="image/jpeg" href="/a.jpg"/></entry>
</feed>"""
        val f = Opds.parse(xml, "https://example.org/cat/root.xml").feed
        assertEquals(2, f.entries.size)
        assertEquals("https://example.org/ebooks/2600.opds", f.entries[0].navUrl)
        assertEquals("Tolstoy", f.entries[0].author)
        assertNull(f.entries[1].navUrl)
        assertEquals(listOf("EPUB"), f.entries[1].acquisitions.map { it.label })
        assertEquals("https://example.org/a.jpg", f.entries[1].coverUrl)
        assertEquals("https://example.org/cat/root.xml?start=26", f.next)
        assertEquals("https://example.org/search?q=war+and", Opds.searchUrl(f.searchTemplate!!, "war and"))
    }
}
