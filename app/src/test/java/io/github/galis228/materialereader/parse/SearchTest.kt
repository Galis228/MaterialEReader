package io.github.galis228.materialereader.parse

import io.github.galis228.materialereader.data.Highlight
import io.github.galis228.materialereader.ui.quotesMarkdown
import io.github.galis228.materialereader.ui.searchBook
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchTest {
    private val book = ParsedBook(
        "Маяк", "Автор",
        listOf(Block(Block.Kind.HEADING, "Глава 1"), Block(Block.Kind.PARAGRAPH, "Маяк светил. Свет маяка был тёплым."),
            Block(Block.Kind.HEADING, "Глава 2"), Block(Block.Kind.PARAGRAPH, "Утром маяк погас.")),
        listOf(Chapter("Глава 1", 0), Chapter("Глава 2", 2)),
    )

    @Test fun search() {
        val r = searchBook(book, "маяк")
        assertEquals(3, r.size)
        assertEquals(1, r[0].block); assertEquals(0, r[0].char)
        assertEquals("маяка", book.blocks[1].text.substring(r[1].char, r[1].char + 5))
        assertEquals("Глава 2", r[2].chapter)
        assertTrue(searchBook(book, "м").isEmpty())
    }

    @Test fun markdown() {
        val md = quotesMarkdown(book, listOf(Highlight(3, 1, "заметка", 0), Highlight(1, 0, "", 0)))
        assertTrue(md.startsWith("# Маяк"))
        assertTrue(md.indexOf("## Глава 1") < md.indexOf("## Глава 2"))
        assertTrue("> Утром маяк погас.\n\nзаметка" in md)
    }
}
