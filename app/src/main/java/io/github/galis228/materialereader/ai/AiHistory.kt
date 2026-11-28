package io.github.galis228.materialereader.ai

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** One finished AI answer, kept to look at later. [answer] is Markdown with the app's visual blocks. */
data class AiNote(
    val id: Long,
    val bookId: String,
    val bookTitle: String,
    /** What was asked: "Карта персонажей", "Вопрос", … */
    val kind: String,
    /** The question or word, if there was one. */
    val query: String,
    val answer: String,
    /** Where in the book it was asked (paragraph index). */
    val block: Int,
    val chapter: String,
    val createdAt: Long,
    val pinned: Boolean = false,
)

/** Every AI result is saved here automatically: maps, timelines, summaries, answers. */
class AiHistory private constructor(ctx: Context) {
    private val file = File(ctx.filesDir, "ai_history.json")
    private val _all = MutableStateFlow(load())
    val all: StateFlow<List<AiNote>> = _all.asStateFlow()

    @Synchronized fun add(n: AiNote) {
        if (n.answer.isBlank()) return
        // asking the same thing again at the same place replaces the older answer
        _all.value = listOf(n) + _all.value.filterNot { it.bookId == n.bookId && it.kind == n.kind && it.query == n.query && it.block == n.block }
        save()
    }

    @Synchronized fun remove(id: Long) { _all.value = _all.value.filterNot { it.id == id }; save() }
    @Synchronized fun togglePin(id: Long) { _all.value = _all.value.map { if (it.id == id) it.copy(pinned = !it.pinned) else it }; save() }
    @Synchronized fun removeBook(bookId: String) { _all.value = _all.value.filterNot { it.bookId == bookId }; save() }

    /** The latest answer of a kind for a book (e.g. the glossary, to look words up in it). */
    fun latest(bookId: String, kind: String): AiNote? = _all.value.firstOrNull { it.bookId == bookId && it.kind == kind }

    @Synchronized fun reload() { _all.value = load() }

    private fun save() = runCatching {
        val a = JSONArray()
        for (n in _all.value) a.put(JSONObject().put("id", n.id).put("book", n.bookId).put("title", n.bookTitle).put("kind", n.kind)
            .put("query", n.query).put("answer", n.answer).put("block", n.block).put("chapter", n.chapter)
            .put("created", n.createdAt).put("pinned", n.pinned))
        val tmp = File(file.path + ".tmp"); tmp.writeText(a.toString()); tmp.renameTo(file)
    }

    private fun load(): List<AiNote> = runCatching {
        val a = JSONArray(file.readText())
        (0 until a.length()).map { i ->
            val o = a.getJSONObject(i)
            AiNote(o.getLong("id"), o.getString("book"), o.optString("title"), o.optString("kind"), o.optString("query"),
                o.optString("answer"), o.optInt("block"), o.optString("chapter"), o.optLong("created"), o.optBoolean("pinned"))
        }
    }.getOrDefault(emptyList())

    companion object {
        @Volatile private var instance: AiHistory? = null
        fun get(ctx: Context): AiHistory = instance ?: synchronized(this) { instance ?: AiHistory(ctx.applicationContext).also { instance = it } }
    }
}
