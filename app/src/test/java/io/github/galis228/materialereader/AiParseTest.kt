package io.github.galis228.materialereader

import io.github.galis228.materialereader.ai.BookAi
import org.junit.Assert.assertEquals
import org.junit.Test

class AiParseTest {
    @Test fun characters() {
        val r = BookAi.parseCharacters("""
            Карта персонажей:
            - **Пьер Безухов** — наследник графа; связи: Андрей Болконский (друг), Элен (жена)
            Наташа Ростова — младшая дочь Ростовых; связи: нет
        """.trimIndent())
        assertEquals(2, r.size)
        assertEquals("Пьер Безухов", r[0].first)
        assertEquals(listOf("Андрей Болконский" to "друг", "Элен" to "жена"), r[0].third)
        assertEquals(emptyList<Pair<String, String>>(), r[1].third)
    }

    @Test fun points() {
        val r = BookAi.parsePoints("1. Привычки копятся — маленькие шаги дают большой итог\n• Среда важнее воли - окружение задаёт поведение\nбез разделителя")
        assertEquals(listOf("Привычки копятся", "Среда важнее воли"), r.map { it.first })
    }
}
