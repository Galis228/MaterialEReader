package io.github.galis228.materialereader

import io.github.galis228.materialereader.data.Translate
import org.junit.Assert.assertEquals
import org.junit.Test

class LangTest {
    @Test fun lettersDecide() {
        assertEquals("es", Translate.guessByLetters("mañana"))
        assertEquals("de", Translate.guessByLetters("Straße"))
        assertEquals("fr", Translate.guessByLetters("garçon"))
        assertEquals("it", Translate.guessByLetters("perché è così"))
        assertEquals("uk", Translate.guessByLetters("їжак"))
        assertEquals("ru", Translate.guessByLetters("ёжик"))
        assertEquals(null, Translate.guessByLetters("madre"))
    }
}
