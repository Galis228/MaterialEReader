package io.github.galis228.materialereader

import io.github.galis228.materialereader.parse.ComicArchive
import io.github.galis228.materialereader.parse.PdfText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FormatsTest {
    @Test fun series() {
        assertEquals("Berserk" to 3f, ComicArchive.seriesOf("Berserk Vol. 3"))
        assertEquals("Наруто" to 12f, ComicArchive.seriesOf("Наруто — том 12"))
        assertEquals("One Piece" to 105f, ComicArchive.seriesOf("One Piece ch 105"))
        assertEquals("Chainsaw Man" to 7f, ComicArchive.seriesOf("Chainsaw_Man_07".replace('_', ' ')))
        assertNull(ComicArchive.seriesOf("Watchmen"))
    }

    @Test fun pdfParagraphs() {
        val page = """
            Глава 1
            Это была длинная строка текста, которую макет PDF пере-
            нёс на следующую строку посреди слова и предложения
            без всякой причины, а потом закончил абзац.
            Новый абзац начинается здесь и тоже идёт в несколько
            строк подряд.
            12
        """.trimIndent()
        val out = PdfText.paragraphs(listOf(page))
        assertTrue(out, out.contains("перенёс"))
        assertTrue(out, out.contains("абзац.\n\nНовый"))
        assertTrue(out, !out.contains("12"))
    }
}
