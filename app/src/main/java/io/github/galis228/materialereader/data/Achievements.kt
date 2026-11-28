package io.github.galis228.materialereader.data

import android.content.Context
import java.time.LocalDate

/** A badge earned by reading. [have] of [need] — unlocked when have ≥ need. */
data class Achievement(val icon: String, val title: String, val hint: String, val have: Long, val need: Long) {
    val done get() = have >= need
    val fraction get() = (have.toFloat() / need).coerceIn(0f, 1f)
}

object Achievements {
    fun all(ctx: Context): List<Achievement> {
        val books = Library.get(ctx).books.value
        val stats = Stats.get(ctx)
        val finished = books.count { it.finished }.toLong()
        val hours = books.sumOf { it.readingMillis } / 3_600_000L
        val quotes = books.sumOf { b -> b.highlights.count { !it.note.startsWith("📓") } }.toLong()
        val journal = books.sumOf { b -> b.highlights.count { it.note.startsWith("📓") } }.toLong()
        val words = Words.get(ctx).all.value.size.toLong()
        val ai = io.github.galis228.materialereader.ai.AiHistory.get(ctx).all.value.size.toLong()
        val bestDay = stats.yearDays(LocalDate.now().year).maxOfOrNull { it.millis } ?: 0L
        val best = stats.bestStreak().toLong()
        val year = LocalDate.now().year
        val thisYear = books.count { it.finishedAt > 0 && java.time.Instant.ofEpochMilli(it.finishedAt).atZone(java.time.ZoneId.systemDefault()).year == year }.toLong()
        val series = books.filter { it.series.isNotEmpty() }.groupBy { it.series.lowercase() }.values.count { s -> s.size >= 2 && s.all { it.finished } }.toLong()
        return listOf(
            Achievement("📖", "Первая книга", "Дочитать одну книгу", finished, 1),
            Achievement("📚", "Книжная полка", "Дочитать 5 книг", finished, 5),
            Achievement("🏛️", "Библиотекарь", "Дочитать 25 книг", finished, 25),
            Achievement("🔥", "Неделя подряд", "Читать 7 дней без перерыва", best, 7),
            Achievement("🌋", "Месяц подряд", "Читать 30 дней без перерыва", best, 30),
            Achievement("⏳", "10 часов", "Провести за чтением 10 часов", hours, 10),
            Achievement("⌛", "100 часов", "Провести за чтением 100 часов", hours, 100),
            Achievement("🏃", "Марафон", "Читать 2 часа за один день", bestDay / 60_000, 120),
            Achievement("✍️", "Цитатник", "Выделить 20 цитат", quotes, 20),
            Achievement("🗂️", "Словарный запас", "Собрать 50 карточек слов", words, 50),
            Achievement("📓", "Дневник читателя", "Ответить на 5 вопросов дневника", journal, 5),
            Achievement("✨", "Исследователь", "10 раз спросить ИИ о книге", ai, 10),
            Achievement("🔗", "Вся серия", "Дочитать серию целиком", series, 1),
            Achievement("🎯", "Цель года", "Выполнить цель на $year год", thisYear, stats.yearGoal.toLong()),
        )
    }
}
