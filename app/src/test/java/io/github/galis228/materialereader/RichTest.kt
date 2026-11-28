package io.github.galis228.materialereader

import io.github.galis228.materialereader.ai.BookAi
import io.github.galis228.materialereader.ai.Rich
import io.github.galis228.materialereader.ai.RichBlock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RichTest {
    @Test fun parsesMixedAnswer() {
        val b = Rich.parse("""
            ## Связи
            Текст **жирный** тут.
            ```graph
            Пьер -> Андрей: друг
            Наташа — Андрей (невеста)
            Соня
            ```
            | Имя | Кто |
            | --- | --- |
            | Пьер | граф |
            - раз
            - два
            ```timeline
            1805 | Аустерлиц
            1812 — Бородино
            ```
            ```chart
            title: Упоминания
            Пьер: 120
            Андрей: 95
            ```
        """.trimIndent())
        assertEquals(RichBlock.Heading(2, "Связи"), b[0])
        val g = b.filterIsInstance<RichBlock.Graph>().single()
        assertEquals(listOf("Пьер", "Андрей", "Наташа", "Соня"), g.nodes)
        assertEquals("невеста", g.edges[1].label)
        assertEquals(listOf(listOf("Пьер", "граф")), b.filterIsInstance<RichBlock.Table>().single().rows)
        assertEquals(2, b.filterIsInstance<RichBlock.Items>().single().items.size)
        assertEquals(listOf("1805" to "Аустерлиц", "1812" to "Бородино"), b.filterIsInstance<RichBlock.Timeline>().single().items)
        val c = b.filterIsInstance<RichBlock.Chart>().single()
        assertEquals("Упоминания", c.title); assertEquals(120f, c.items[0].second)
    }

    @Test fun unfinishedFenceStillShows() {
        val b = Rich.parse("Начало\n```graph\nА -> Б")
        assertTrue(b.any { it is RichBlock.Graph })
    }

    @Test fun builderRoundTrip() {
        val md = Rich.table(listOf("Термин", "Пояснение"), BookAi.parsePoints("Аустерлиц — сражение 1805 года\nДуэль — поединок").map { listOf(it.first, it.second) })
        assertEquals(2, (Rich.parse(md).single() as RichBlock.Table).rows.size)
    }
}
