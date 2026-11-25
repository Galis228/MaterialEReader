package io.github.galis228.materialereader.parse

import io.github.galis228.materialereader.ai.BookAi
import io.github.galis228.materialereader.ai.visibleAnswer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AiTest {
    @Test fun stem() {
        assertEquals("Вер", BookAi.stem("Вера"))
        assertTrue("Веры пришла".contains(BookAi.stem("Вера"), true))
        assertEquals("Ник", BookAi.stem("Ник"))
    }

    @Test fun split() {
        val text = (1..50).joinToString("\n") { "Абзац номер $it. Здесь немного текста." }
        val parts = BookAi.split(text, 300)
        assertTrue(parts.all { it.length <= 300 })
        assertEquals(text.replace("\n", ""), parts.joinToString("") { it.replace("\n", "") })
    }

    @Test fun think() {
        assertEquals("Ответ", visibleAnswer("<think>хм</think>Ответ"))
        assertEquals("", visibleAnswer("<think>думаю"))
    }
}
