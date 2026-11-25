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

    suspend fun explain(pageText: String): Flow<String> {
        val t = pageText.take(ai.budgetChars())
        return ai.stream(system, "Объясни простыми словами, что происходит в этом фрагменте, и поясни сложные слова и отсылки, если они есть.\n\nФрагмент:\n$t")
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

    companion object {
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
