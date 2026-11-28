package io.github.galis228.materialereader.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** A saved word or phrase with where it was met, for spaced review. */
data class Word(
    val id: Long, val word: String, val context: String, val translation: String, val meaning: String,
    val book: String, val createdAt: Long, val step: Int = 0, val due: Long = 0L,
)

class Words private constructor(ctx: Context) {
    private val file = File(ctx.filesDir, "words.json")
    private val _all = MutableStateFlow(load())
    val all: StateFlow<List<Word>> = _all.asStateFlow()

    fun dueNow(now: Long = System.currentTimeMillis()) = _all.value.filter { it.due <= now }

    suspend fun add(w: Word) = edit { list -> listOf(w.copy(due = System.currentTimeMillis())) + list.filterNot { it.word.equals(w.word, true) && it.book == w.book } }
    suspend fun update(w: Word) = edit { list -> list.map { if (it.id == w.id) w else it } }
    suspend fun remove(id: Long) = edit { list -> list.filterNot { it.id == id } }

    /** "Remember": the next review moves further away; "forgot": back to the start, shown again soon. */
    suspend fun review(w: Word, remembered: Boolean) {
        val now = System.currentTimeMillis()
        val next = if (remembered) {
            val step = (w.step + 1).coerceAtMost(INTERVAL_DAYS.size)
            w.copy(step = step, due = now + INTERVAL_DAYS[step - 1] * DAY)
        } else w.copy(step = 0, due = now + 10 * 60_000L)
        update(next)
    }

    private suspend fun edit(f: (List<Word>) -> List<Word>) {
        _all.update(f)
        withContext(Dispatchers.IO) { save() }
    }

    private fun save() {
        val a = JSONArray()
        for (w in _all.value) a.put(JSONObject().put("id", w.id).put("word", w.word).put("context", w.context)
            .put("translation", w.translation).put("meaning", w.meaning).put("book", w.book)
            .put("created", w.createdAt).put("step", w.step).put("due", w.due))
        val tmp = File(file.path + ".tmp"); tmp.writeText(a.toString()); tmp.renameTo(file)
    }

    private fun load(): List<Word> = runCatching {
        val a = JSONArray(file.readText())
        (0 until a.length()).map { i ->
            val o = a.getJSONObject(i)
            Word(o.getLong("id"), o.getString("word"), o.optString("context"), o.optString("translation"), o.optString("meaning"),
                o.optString("book"), o.optLong("created"), o.optInt("step"), o.optLong("due"))
        }
    }.getOrDefault(emptyList())

    companion object {
        val INTERVAL_DAYS = longArrayOf(1, 3, 7, 14, 30, 60)
        private const val DAY = 24 * 60 * 60_000L
        @Volatile private var instance: Words? = null
        fun get(ctx: Context): Words = instance ?: synchronized(this) { instance ?: Words(ctx.applicationContext).also { instance = it } }
    }
}
