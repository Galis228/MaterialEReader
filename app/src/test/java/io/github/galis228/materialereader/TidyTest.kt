package io.github.galis228.materialereader

import io.github.galis228.materialereader.parse.Block
import io.github.galis228.materialereader.parse.BookParser
import org.junit.Assert.assertEquals
import org.junit.Test

class TidyTest {
    private fun kind(t: String, k: Block.Kind = Block.Kind.PARAGRAPH) = BookParser.tidy(Block(k, t)).kind

    @Test fun sceneBreaksBecomeSeparators() {
        for (t in listOf("* * *", "***", " *  *  * ", "⁂", "———", "~ ~ ~", "◆◆◆", "* * * * *")) assertEquals(t, Block.Kind.SEPARATOR, kind(t))
    }

    @Test fun normalTextStays() {
        for (t in listOf("Он вошёл.", "— Да.", "...", "A", "1999 год", "*Курсив*")) assertEquals(t, Block.Kind.PARAGRAPH, kind(t))
    }

    @Test fun loneNumbersBecomeHeadings() {
        for (t in listOf("IV", "XII.", "12")) assertEquals(t, Block.Kind.HEADING, kind(t))
        assertEquals(Block.Kind.QUOTE, kind("IV", Block.Kind.QUOTE))
    }
}
