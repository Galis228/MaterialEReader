package io.github.galis228.materialereader.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject
import java.io.File
import java.time.LocalDate

/** One day of reading. */
data class Day(val date: LocalDate, val millis: Long, val words: Int)

/**
 * Reading log by day: minutes and words, plus time per author for the year in review.
 * Kept in stats.json; small enough to rewrite on every change.
 */
class Stats private constructor(ctx: Context) {
    private val file = File(ctx.filesDir, "stats.json")
    private val prefs = ctx.getSharedPreferences("stats", 0)
    private val days = HashMap<String, LongArray>() // date -> [millis, words]
    private val authors = HashMap<String, Long>()  // "year|author" -> millis
    private val _rev = MutableStateFlow(0)
    /** Changes whenever the log changes, so screens can recompose. */
    val rev: StateFlow<Int> = _rev.asStateFlow()

    init { load() }

    var yearGoal: Int
        get() = prefs.getInt("goal", 12)
        set(v) { prefs.edit().putInt("goal", v.coerceIn(1, 500)).apply(); _rev.value++ }

    @Synchronized fun addReading(millis: Long, author: String) {
        if (millis <= 0) return
        val today = LocalDate.now()
        days.getOrPut(today.toString()) { LongArray(2) }[0] += millis
        if (author.isNotBlank()) authors.merge("${today.year}|$author", millis, Long::plus)
        save()
    }

    @Synchronized fun addWords(words: Int) {
        if (words <= 0) return
        days.getOrPut(LocalDate.now().toString()) { LongArray(2) }[1] += words.toLong()
        save()
    }

    @Synchronized fun day(d: LocalDate): Day = days[d.toString()].let { Day(d, it?.get(0) ?: 0L, (it?.get(1) ?: 0L).toInt()) }

    /** The last [n] days, oldest first. */
    fun lastDays(n: Int): List<Day> { val t = LocalDate.now(); return (n - 1 downTo 0).map { day(t.minusDays(it.toLong())) } }

    /** Days in a row with at least a minute of reading, counting today only if it already has some. */
    @Synchronized fun streak(): Int {
        var d = LocalDate.now()
        if ((days[d.toString()]?.get(0) ?: 0L) < 60_000L) d = d.minusDays(1)
        var n = 0
        while ((days[d.toString()]?.get(0) ?: 0L) >= 60_000L) { n++; d = d.minusDays(1) }
        return n
    }

    @Synchronized fun bestStreak(): Int {
        val dates = days.filter { it.value[0] >= 60_000L }.keys.mapNotNull { runCatching { LocalDate.parse(it) }.getOrNull() }.sorted()
        var best = 0; var cur = 0; var prev: LocalDate? = null
        for (d in dates) { cur = if (prev != null && d == prev.plusDays(1)) cur + 1 else 1; best = maxOf(best, cur); prev = d }
        return best
    }

    @Synchronized fun yearDays(year: Int): List<Day> =
        days.filterKeys { it.startsWith("$year-") }.map { (k, v) -> Day(LocalDate.parse(k), v[0], v[1].toInt()) }.sortedBy { it.date }

    @Synchronized fun topAuthors(year: Int, n: Int = 3): List<Pair<String, Long>> =
        authors.filterKeys { it.startsWith("$year|") }.map { it.key.substringAfter('|') to it.value }.sortedByDescending { it.second }.take(n)

    private fun load() {
        runCatching {
            val o = JSONObject(file.readText())
            o.optJSONObject("days")?.let { d -> d.keys().forEach { k -> val a = d.getJSONArray(k); days[k] = longArrayOf(a.getLong(0), a.getLong(1)) } }
            o.optJSONObject("authors")?.let { a -> a.keys().forEach { k -> authors[k] = a.getLong(k) } }
        }
    }

    private fun save() {
        runCatching {
            val o = JSONObject()
            o.put("days", JSONObject().apply { days.forEach { (k, v) -> put(k, org.json.JSONArray().put(v[0]).put(v[1])) } })
            o.put("authors", JSONObject().apply { authors.forEach { (k, v) -> put(k, v) } })
            val tmp = File(file.path + ".tmp"); tmp.writeText(o.toString()); tmp.renameTo(file)
        }
        _rev.value++
    }

    /** After restoring a backup. */
    @Synchronized fun reload() { days.clear(); authors.clear(); load(); _rev.value++ }

    companion object {
        @Volatile private var instance: Stats? = null
        fun get(ctx: Context): Stats = instance ?: synchronized(this) { instance ?: Stats(ctx.applicationContext).also { instance = it } }
    }
}
