package io.github.galis228.materialereader.ai

import android.content.Context
import io.github.galis228.materialereader.data.Library
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** AI over the whole library, not one book: search by meaning, the reader's profile, quotes by topic. */
object LibraryAi {
    const val ME = "@me"
    private const val SYS = "Ты — помощник читателя. Отвечай по-русски. Оформляй ответ в Markdown."

    /** Words and short phrases a text about [topic] would likely contain, to search for them literally. */
    suspend fun expand(ctx: Context, topic: String): List<String> {
        val out = Ai.get(ctx).complete(SYS, "Читатель ищет в книгах фрагменты на тему: «$topic». " +
            "Дай 6–10 слов или коротких фраз (1–2 слова), которые скорее всего встретятся в таком фрагменте художественного текста: " +
            "синонимы, связанные предметы, действия. Только основы слов без окончаний, где можно (например «любв», «сердц»). " +
            "Каждое с новой строки, без нумерации и пояснений.", maxOut = 120)
        return (listOf(topic) + out.lines().map { it.trim().trimStart('-', '•', '*', ' ').replace(Regex("^\\d+[.)]\\s*"), "").trim('«', '»', '"', '.', ',') })
            .filter { it.length in 3..30 }.distinctBy { it.lowercase() }.take(11)
    }

    /** A short portrait of the reader from what they read, finished and marked. Saved to AI results. */
    suspend fun profile(ctx: Context): String {
        val books = Library.get(ctx).books.value.filter { !it.hidden && !it.isComic }
        if (books.isEmpty()) return "Пока в библиотеке нет книг — профиль появится, когда ты начнёшь читать."
        val lines = books.sortedByDescending { it.lastOpenedAt }.take(60).joinToString("\n") { b ->
            "${b.title} — ${b.author}; " + (if (b.finished) "прочитана" else "прочитано ${(b.progress * 100).toInt()}%") +
                (if (b.genres.isNotEmpty()) "; жанры: ${b.genres.take(3).joinToString()}" else "") + (if (b.highlights.isNotEmpty()) "; выделений: ${b.highlights.size}" else "")
        }
        val ans = Ai.get(ctx).complete(SYS, "Вот библиотека читателя. Составь его читательский профиль: " +
            "## Что ты любишь — жанры, темы, эпохи; ## Твои авторы; ## Как ты читаешь — дочитываешь ли, что бросаешь; " +
            "## Что почитать дальше — 5 книг, которых нет в списке, с одной фразой почему. Коротко, тепло, на «ты».\n\n$lines", maxOut = 700)
        AiHistory.get(ctx).add(AiNote(System.currentTimeMillis(), ME, "Обо мне", "Читательский профиль", "", ans, 0, "", System.currentTimeMillis()))
        return ans
    }

    /** The reader's highlights grouped by themes. Saved to AI results. */
    suspend fun quotesByTopic(ctx: Context): String {
        val lib = Library.get(ctx)
        val quotes = withContext(Dispatchers.IO) {
            lib.books.value.filter { !it.hidden && it.highlights.isNotEmpty() }.flatMap { b ->
                val book = runCatching { lib.open(b.id) }.getOrNull() ?: return@flatMap emptyList()
                b.highlights.filter { it.whole || it.end > it.start }.map { h ->
                    h.text(book.blocks.getOrNull(h.block)?.text.orEmpty()).trim().take(300) + " (${b.title})"
                }
            }
        }.filter { it.length > 12 }.take(80)
        if (quotes.size < 3) return "Выдели в книгах хотя бы три понравившиеся цитаты — и я разложу их по темам."
        val ans = Ai.get(ctx).complete(SYS, "Это цитаты, которые читатель выделил в разных книгах. Разложи их по 3–7 темам. " +
            "Для каждой темы: заголовок «## Тема», одна фраза о ней и цитаты списком как есть, с книгой в скобках. Не придумывай новых цитат.\n\n" +
            quotes.joinToString("\n") { "- $it" }, maxOut = 1400)
        AiHistory.get(ctx).add(AiNote(System.currentTimeMillis(), ME, "Обо мне", "Цитаты по темам", "", ans, 0, "", System.currentTimeMillis()))
        return ans
    }
}
