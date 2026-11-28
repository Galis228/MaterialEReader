package io.github.galis228.materialereader.data

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import java.time.LocalDate

/**
 * A badge earned by reading. Tiered ones have bronze/silver/gold steps in [tiers];
 * [level] is how many are reached, [need] the next step (or the last one when all are done).
 */
data class Achievement(val icon: String, val title: String, val hintTpl: String, val have: Long, val tiers: List<Long>) {
    constructor(icon: String, title: String, hint: String, have: Long, need: Long) : this(icon, title, hint, have, listOf(need))
    val level get() = tiers.count { have >= it }
    val done get() = level > 0
    val maxed get() = level == tiers.size
    val need get() = tiers[level.coerceAtMost(tiers.size - 1)]
    val fraction get() = (have.toFloat() / need).coerceIn(0f, 1f)
    val hint get() = hintTpl.replace("%n", "$need")
    val tierName get() = if (tiers.size < 2 || level == 0) "" else listOf("Бронза", "Серебро", "Золото").getOrElse(level - 1) { "Золото" }
    val key get() = "$title#$level"
}

object Achievements {
    fun all(ctx: Context): List<Achievement> {
        val books = Library.get(ctx).books.value
        val stats = Stats.get(ctx)
        val done = books.filter { it.finished }
        val finished = done.size.toLong()
        val hours = books.sumOf { it.readingMillis } / 3_600_000L
        val quotes = books.sumOf { b -> b.highlights.count { !it.note.startsWith("📓") } }.toLong()
        val journal = books.sumOf { b -> b.highlights.count { it.note.startsWith("📓") } }.toLong()
        val words = Words.get(ctx).all.value.size.toLong()
        val history = io.github.galis228.materialereader.ai.AiHistory.get(ctx).all.value
        val ai = history.size.toLong()
        val maps = history.count { it.kind == "Карта персонажей" }.toLong()
        val bestDay = stats.yearDays(LocalDate.now().year).maxOfOrNull { it.millis } ?: 0L
        val best = stats.bestStreak().toLong()
        val year = LocalDate.now().year
        val thisYear = books.count { it.finishedAt > 0 && java.time.Instant.ofEpochMilli(it.finishedAt).atZone(java.time.ZoneId.systemDefault()).year == year }.toLong()
        val bySeries = books.filter { it.series.isNotEmpty() }.groupBy { it.series.lowercase() }.values
        val series = bySeries.count { s -> s.size >= 2 && s.all { it.finished } }.toLong()
        val binge = bySeries.maxOfOrNull { s -> s.count { it.finished } }?.toLong() ?: 0L
        val classics = done.count { b -> Regex("\\d{4}").find(b.year)?.value?.toIntOrNull()?.let { it < 1900 } == true }.toLong()
        val bricks = done.count { it.pages >= 500 }.toLong()
        val shelved = Library.get(ctx).shelfList.value.size.toLong()
        return listOf(
            Achievement("📖", "Первая книга", "Дочитать одну книгу", finished, 1),
            Achievement("📚", "Книжная полка", "Дочитать %n книг", finished, listOf(5, 25, 100)),
            Achievement("🔥", "Без перерыва", "Читать %n дней подряд", best, listOf(7, 30, 100)),
            Achievement("⏳", "Часы за книгой", "Провести за чтением %n часов", hours, listOf(10, 100, 500)),
            Achievement("🏃", "Марафон", "Читать 2 часа за один день", bestDay / 60_000, 120),
            Achievement("🏅", "Марафонец", "Читать 5 часов за один день", bestDay / 60_000, 300),
            Achievement("🪑", "За один присест", "Читать %n минут без перерыва", stats.longestSitting(), listOf(60, 120, 180)),
            Achievement("🦉", "Сова", "Читать после полуночи %n раз", stats.count("night"), listOf(1, 10, 50)),
            Achievement("🐦", "Ранняя пташка", "Читать до 8 утра %n раз", stats.count("morning"), listOf(1, 10, 50)),
            Achievement("☕", "Выходной читатель", "Читать в %n выходных дней", stats.count("weekend"), listOf(4, 20, 100)),
            Achievement("🧱", "Кирпич", "Дочитать книгу больше 500 страниц", bricks, listOf(1, 5, 20)),
            Achievement("🏛️", "Классик", "Дочитать %n книг, написанных до 1900 года", classics, listOf(5, 15, 40)),
            Achievement("🌍", "Полиглот", "Переводить с %n языков", stats.languages().toLong(), listOf(3, 5, 7)),
            Achievement("🔤", "Переводчик", "Перевести %n фрагментов", stats.count("translate"), listOf(100, 500, 2000)),
            Achievement("🗄️", "Коллекционер", "Собрать %n книг в библиотеке", books.size.toLong(), listOf(50, 200, 500)),
            Achievement("🗂", "Расставил по полкам", "Создать %n полок", shelved, listOf(5, 10, 20)),
            Achievement("🗺️", "Картограф", "Построить %n карт персонажей", maps, listOf(10, 30, 100)),
            Achievement("🔎", "Дотошник", "Открыть %n сносок", stats.count("footnote"), listOf(50, 250, 1000)),
            Achievement("📺", "Как сериал", "Дочитать %n книг одной серии", binge, listOf(3, 6, 10)),
            Achievement("📅", "Год с книгой", "Читать в %n разных дней", stats.readingDays().toLong(), listOf(30, 100, 365)),
            Achievement("🔠", "Миллион слов", "Прочитать %n слов", stats.totalWords(), listOf(100_000, 500_000, 1_000_000)),
            Achievement("👋", "Вернулся", "Вернуться к чтению после месяца перерыва", stats.count("comeback"), 1),
            Achievement("🛒", "Охотник за книгами", "Скачать %n книг из каталогов", stats.count("catalog"), listOf(10, 50, 200)),
            Achievement("✍️", "Цитатник", "Выделить %n цитат", quotes, listOf(20, 100, 500)),
            Achievement("🗃️", "Словарный запас", "Собрать %n карточек слов", words, listOf(50, 200, 1000)),
            Achievement("📓", "Дневник читателя", "Ответить на %n вопросов дневника", journal, listOf(5, 25, 100)),
            Achievement("✨", "Исследователь", "Спросить ИИ о книге %n раз", ai, listOf(10, 100, 500)),
            Achievement("🔗", "Вся серия", "Дочитать серию целиком", series, listOf(1, 3, 10)),
            Achievement("🎯", "Цель года", "Выполнить цель на $year год", thisYear, stats.yearGoal.toLong()),
        )
    }

    /** Shows a toast for every badge or tier earned since the last check. The first check only remembers. */
    fun check(ctx: Context) {
        val p = ctx.getSharedPreferences("achievements", 0)
        val list = runCatching { all(ctx) }.getOrNull() ?: return
        val now = list.filter { it.done }.map { it.key }.toSet()
        val seen = p.getStringSet("seen", null)
        p.edit().putStringSet("seen", now + (seen ?: emptySet())).apply()
        if (seen == null) return
        val fresh = list.filter { it.done && it.key !in seen }
        if (fresh.isEmpty()) return
        Handler(Looper.getMainLooper()).post {
            fresh.take(2).forEach { a ->
                Toast.makeText(ctx, "${a.icon} Достижение: ${a.title}" + (if (a.tierName.isNotEmpty()) " · ${a.tierName}" else ""), Toast.LENGTH_LONG).show()
            }
        }
    }
}
