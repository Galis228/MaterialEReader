package io.github.galis228.materialereader.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** A book from a catalog saved for later, with what's needed to download it in one tap. */
data class Wish(
    val id: String, val title: String, val author: String, val summary: String, val coverUrl: String?,
    val acquisitions: List<Acquisition>, val addedAt: Long,
)

/** «Хочу прочитать»: books from the catalogs that aren't downloaded yet. */
class Wishlist private constructor(ctx: Context) {
    private val file = File(ctx.filesDir, "wishlist.json")
    private val _all = MutableStateFlow(load())
    val all: StateFlow<List<Wish>> = _all.asStateFlow()

    fun contains(id: String) = _all.value.any { it.id == id }

    @Synchronized fun toggle(e: OpdsEntry) {
        _all.value = if (contains(e.id)) _all.value.filterNot { it.id == e.id }
        else listOf(Wish(e.id, e.title, e.author, e.summary, e.coverUrl, e.acquisitions, System.currentTimeMillis())) + _all.value
        save()
    }

    @Synchronized fun remove(id: String) { _all.value = _all.value.filterNot { it.id == id }; save() }
    @Synchronized fun reload() { _all.value = load() }

    private fun save() = runCatching {
        val a = JSONArray()
        for (w in _all.value) a.put(JSONObject().put("id", w.id).put("title", w.title).put("author", w.author).put("summary", w.summary)
            .put("cover", w.coverUrl ?: "").put("added", w.addedAt)
            .put("acq", JSONArray().apply { w.acquisitions.forEach { put(JSONObject().put("url", it.url).put("type", it.type)) } }))
        file.writeText(a.toString())
    }

    private fun load(): List<Wish> = runCatching {
        val a = JSONArray(file.readText())
        (0 until a.length()).map { i ->
            val o = a.getJSONObject(i)
            val acq = o.optJSONArray("acq")
            Wish(o.getString("id"), o.optString("title"), o.optString("author"), o.optString("summary"), o.optString("cover").ifEmpty { null },
                if (acq == null) emptyList() else (0 until acq.length()).map { j -> acq.getJSONObject(j).let { Acquisition(it.getString("url"), it.optString("type")) } },
                o.optLong("added"))
        }
    }.getOrDefault(emptyList())

    companion object {
        @Volatile private var instance: Wishlist? = null
        fun get(ctx: Context): Wishlist = instance ?: synchronized(this) { instance ?: Wishlist(ctx.applicationContext).also { instance = it } }
    }
}
