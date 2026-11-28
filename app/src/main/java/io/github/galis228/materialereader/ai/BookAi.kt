package io.github.galis228.materialereader.ai

import io.github.galis228.materialereader.parse.ParsedBook
import kotlinx.coroutines.flow.Flow

/** Reading helpers on top of [Ai]. Everything uses only the text up to the reader's position — no spoilers. */
class BookAi(private val ai: Ai, private val book: ParsedBook) {

    private val system = "Ты — помощник читателя книги «${book.title}». Отвечай по-русски, коротко и по делу. " +
        "Опирайся только на данный фрагмент книги. Не придумывай событий и не рассказывай, что будет дальше."

    private fun text(from: Int, to: Int) = buildString {
        for (i in from.coerceAtLeast(0) until to.coerceAtMost(book.blocks.size)) { append(book.blocks[i].text); append('\n') }
    }

    /** The last [budget] characters of the book before [block]. */
    private fun recent(block: Int, budget: Int): String {
        val sb = StringBuilder()
        var i = block.coerceAtMost(book.blocks.size - 1)
        while (i >= 0 && sb.length < budget) { sb.insert(0, book.blocks[i].text + "\n"); i-- }
        return sb.takeLast(budget).toString()
    }

    suspend fun explain(pageText: String, single: Boolean = false): Flow<String> {
        val t = pageText.take(ai.budgetChars())
        val what = if (single) "абзаце" else "фрагменте"
        return ai.stream(system, "Объясни простыми словами, что происходит в этом $what, и поясни сложные слова и отсылки, если они есть.\n\nТекст:\n$t")
    }

    suspend fun recap(block: Int): Flow<String> {
        val t = recent(block, ai.budgetChars())
        return ai.stream(system, "Читатель вернулся к книге после перерыва. Напомни в 4–6 предложениях, что происходило перед этим местом, " +
            "кто главные действующие лица и на чём остановилось действие.\n\nТекст перед местом остановки:\n$t")
    }

    /** Summary of the current chapter up to [block]; long chapters are condensed piece by piece first. */
    suspend fun chapterSummary(chapterStart: Int, block: Int, onStep: (Int, Int) -> Unit): Flow<String> {
        val budget = ai.budgetChars()
        val full = text(chapterStart, block + 1)
        val material = if (full.length <= budget) full else {
            val parts = split(full, budget)
            val notes = parts.mapIndexed { i, part ->
                onStep(i + 1, parts.size)
                ai.complete(system, "Кратко перескажи этот кусок главы в 3–4 предложениях:\n\n$part", maxOut = 220)
            }
            notes.joinToString("\n")
        }
        onStep(0, 0)
        return ai.stream(system, "Перескажи прочитанную часть главы связно, в одном-двух абзацах.\n\n$material")
    }

    /** Who is [name]: collects paragraphs mentioning it (first and latest mentions) up to [block]. */
    suspend fun whoIs(name: String, block: Int): Flow<String> {
        val budget = ai.budgetChars()
        val stem = stem(name)
        val hits = (0..block.coerceAtMost(book.blocks.size - 1)).filter { book.blocks[it].text.contains(stem, ignoreCase = true) }
        if (hits.isEmpty()) return kotlinx.coroutines.flow.flowOf("В прочитанной части книги «$name» пока не встречается.")
        val picked = LinkedHashSet<Int>()
        hits.take(3).forEach { picked += it }
        hits.reversed().forEach { if (picked.sumOf { i -> book.blocks[i].text.length } < budget) picked += it }
        val ctx = picked.sorted().joinToString("\n…\n") { book.blocks[it].text }.take(budget)
        return ai.stream(system, "Кто такой (или что такое) «$name»? Расскажи, что о нём известно к этому моменту: " +
            "роль в истории, отношения с другими персонажами. Только по фрагментам ниже.\n\n$ctx")
    }

    suspend fun ask(question: String, block: Int): Flow<String> {
        val t = recent(block, ai.budgetChars())
        return ai.stream(system, "Вопрос читателя: $question\n\nОтветь по тексту, прочитанному к этому моменту:\n$t")
    }

    /** Rewrites a hard passage in plain modern language, keeping the meaning. */
    suspend fun simplify(pageText: String): Flow<String> {
        val t = pageText.take(ai.budgetChars())
        return ai.stream(system, "Перескажи этот фрагмент простым современным языком, близко к тексту, ничего не добавляя. " +
            "Сложные слова замени понятными, длинные предложения раздели.\n\nТекст:\n$t")
    }

    /**
     * Characters met so far and how they're related, one per line: "Имя — кто это; связи: Другой (кем приходится), …".
     * Looks through the whole read part: pieces spread over it are condensed first, then merged.
     */
    suspend fun characters(block: Int, onStep: (Int, Int) -> Unit): Flow<String> {
        val budget = ai.budgetChars()
        val read = text(0, block + 1)
        val material = if (read.length <= budget) read else {
            // a few evenly spread pieces, the latest one always included
            val n = (read.length / budget).coerceIn(2, 6)
            val step = (read.length - budget) / (n - 1).coerceAtLeast(1)
            val notes = (0 until n).map { i ->
                onStep(i + 1, n)
                val part = read.substring((i * step).coerceAtMost(read.length - budget), ((i * step) + budget).coerceAtMost(read.length))
                ai.complete(system, "Перечисли персонажей этого фрагмента и их отношения друг с другом, кратко, списком.\n\n$part", maxOut = 260)
            }
            notes.joinToString("\n")
        }
        onStep(0, 0)
        return ai.stream(system, "Составь карту персонажей по прочитанному. Каждая строка строго в формате:\n" +
            "Имя — кто это, одной фразой; связи: Имя2 (кем приходится), Имя3 (кем приходится)\n" +
            "Только персонажи, которые уже появились. Не больше 12 строк. Без вступления.\n\n$material", maxOut = 700)
    }

    /** Key ideas of the chapter read so far, one per line: "Мысль — пояснение". For non-fiction cards. */
    suspend fun keyPoints(chapterStart: Int, block: Int): String {
        val budget = ai.budgetChars()
        val t = text(chapterStart, block + 1).let { if (it.length > budget) it.takeLast(budget) else it }
        return ai.complete(system, "Выдели 3–7 ключевых мыслей этого фрагмента. Каждая строка строго в формате:\n" +
            "Короткая мысль — пояснение в одном-двух предложениях\nБез нумерации и вступления.\n\n$t", maxOut = 600)
    }

    /** One question about the chapter for the reading journal. */
    suspend fun journalQuestion(chapterStart: Int, block: Int): Flow<String> {
        val t = text(chapterStart, block + 1).takeLast(ai.budgetChars())
        return ai.stream(system, "Задай читателю один короткий вопрос для читательского дневника по этой главе: " +
            "о его мнении, чувствах или выводах, без пересказа. Только сам вопрос.\n\n$t", maxOut = 120)
    }

    companion object {
        /** "Мысль — пояснение" lines into pairs. */
        fun parsePoints(text: String): List<Pair<String, String>> = text.lines()
            .map { it.trim().trimStart('-', '•', '*', ' ').replace(Regex("^\\d+[.)]\\s*"), "") }
            .mapNotNull { l -> val i = l.indexOf(" — ").takeIf { it > 0 } ?: l.indexOf(" - ").takeIf { it > 0 } ?: return@mapNotNull null
                l.substring(0, i).trim().trim('*') to l.substring(i + 3).trim() }
            .filter { it.first.isNotEmpty() && it.second.isNotEmpty() }

        /** Character map lines into names, descriptions and links between them. */
        fun parseCharacters(text: String): List<Triple<String, String, List<Pair<String, String>>>> = text.lines().mapNotNull { raw ->
            val l = raw.trim().trimStart('-', '•', '*', ' ').replace("**", "")
            val i = l.indexOf(" — ").takeIf { it > 0 } ?: l.indexOf(" - ").takeIf { it > 0 } ?: return@mapNotNull null
            val name = l.substring(0, i).trim().removeSuffix(":")
            val rest = l.substring(i + 3)
            val j = rest.indexOf("связи:", ignoreCase = true)
            val desc = (if (j >= 0) rest.substring(0, j) else rest).trim().trimEnd(';', ',', '.').trim()
            val links = if (j < 0) emptyList() else rest.substring(j + 6).split(Regex(",\\s*(?![^()]*\\))")).mapNotNull { part ->
                val p = part.trim().trimEnd('.')
                if (p.isEmpty() || p.startsWith("нет", true)) null
                else p.substringBefore("(").trim() to p.substringAfter("(", "").substringBefore(")").trim()
            }
            if (name.isEmpty() || name.length > 40) null else Triple(name, desc, links)
        }

        /** Crude Russian stem so «Веры», «Вере», «Верой» all match «Вера». */
        fun stem(name: String): String {
            val w = name.trim()
            if (w.length <= 3) return w
            val st = w.dropLastWhile { it.lowercaseChar() in "аеёиоуыэюяйь" }
            return if (st.length >= 3) st else w
        }

        fun split(text: String, max: Int): List<String> {
            val out = ArrayList<String>()
            var rest = text
            while (rest.length > max) {
                var cut = rest.lastIndexOf('\n', max).takeIf { it > max / 2 } ?: rest.lastIndexOf(". ", max).takeIf { it > max / 2 } ?: max
                if (cut <= 0) cut = max
                out += rest.substring(0, cut); rest = rest.substring(cut).trimStart()
            }
            if (rest.isNotBlank()) out += rest
            return out
        }
    }
}
