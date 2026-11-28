package io.github.galis228.materialereader

import io.github.galis228.materialereader.parse.Block
import io.github.galis228.materialereader.parse.BookFormat
import io.github.galis228.materialereader.parse.BookParser
import io.github.galis228.materialereader.parse.MobiParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream

class MobiTest {
    @Test fun palmDocDecompresses() {
        // "abc" literal, back-reference 3 back × 3 chars, then " a" as a space+letter byte
        val src = byteArrayOf('a'.code.toByte(), 'b'.code.toByte(), 'c'.code.toByte(), 0x80.toByte(), 0x18, 0xE1.toByte())
        assertEquals("abcabc a", String(MobiParser.palmDoc(src)))
    }

    private fun mobi(html: String): ByteArray {
        val text = html.toByteArray()
        val r0 = ByteArray(16).also {
            it[1] = 1 // no compression
            it[4] = (text.size shr 24).toByte(); it[5] = (text.size shr 16).toByte(); it[6] = (text.size shr 8).toByte(); it[7] = text.size.toByte()
            it[9] = 1 // one text record
            it[10] = 0x10 // 4096
        }
        val head = ByteArray(78 + 2 * 8 + 2)
        "Test_Book".toByteArray().copyInto(head, 0)
        "BOOKMOBI".toByteArray().copyInto(head, 60)
        head[77] = 2
        val off0 = head.size; val off1 = off0 + r0.size
        fun put(at: Int, v: Int) { head[at] = (v shr 24).toByte(); head[at + 1] = (v shr 16).toByte(); head[at + 2] = (v shr 8).toByte(); head[at + 3] = v.toByte() }
        put(78, off0); put(86, off1)
        return ByteArrayOutputStream().apply { write(head); write(r0); write(text) }.toByteArray()
    }

    @Test fun readsSimpleMobi() {
        val bytes = mobi("<html><body><h2>Глава первая</h2><p>Жил-был кот.</p><mbp:pagebreak/><h2>Глава вторая</h2><p>И пёс.</p></body></html>")
        assertEquals(BookFormat.MOBI, BookParser.detect(bytes, "book"))
        val b = BookParser.parse(bytes, "book", BookFormat.MOBI)
        assertEquals("Test Book", b.title)
        assertEquals(2, b.chapters.size)
        assertTrue(b.blocks.any { it.kind == Block.Kind.PARAGRAPH && it.text == "Жил-был кот." })
    }
}
