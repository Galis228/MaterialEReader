package io.github.galis228.materialereader.data

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import io.github.galis228.materialereader.MainActivity
import io.github.galis228.materialereader.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId

private fun openApp(ctx: Context) = PendingIntent.getActivity(ctx, 21, Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

/** Days in a row with reading, plus minutes today. */
class StreakWidget : AppWidgetProvider() {
    override fun onUpdate(ctx: Context, mgr: AppWidgetManager, ids: IntArray) = render(ctx, mgr, ids)
    companion object {
        fun render(ctx: Context, mgr: AppWidgetManager, ids: IntArray) {
            val st = Stats.get(ctx)
            val n = st.streak()
            val today = (st.day(LocalDate.now()).millis / 60_000).toInt()
            val v = RemoteViews(ctx.packageName, R.layout.widget_stat)
            v.setTextViewText(R.id.stat_value, "🔥 $n")
            v.setTextViewText(R.id.stat_label, "дней подряд")
            v.setTextViewText(R.id.stat_sub, if (today > 0) "сегодня $today мин" else "сегодня ещё не читал")
            v.setViewVisibility(R.id.stat_progress, View.GONE)
            v.setOnClickPendingIntent(R.id.stat_root, openApp(ctx))
            mgr.updateAppWidget(ids, v)
        }
    }
}

/** Books finished this year against the goal. */
class GoalWidget : AppWidgetProvider() {
    override fun onUpdate(ctx: Context, mgr: AppWidgetManager, ids: IntArray) = render(ctx, mgr, ids)
    companion object {
        fun render(ctx: Context, mgr: AppWidgetManager, ids: IntArray) {
            val year = LocalDate.now().year
            val done = Library.get(ctx).books.value.count { it.finishedAt > 0 && java.time.Instant.ofEpochMilli(it.finishedAt).atZone(ZoneId.systemDefault()).year == year }
            val goal = Stats.get(ctx).yearGoal
            val v = RemoteViews(ctx.packageName, R.layout.widget_stat)
            v.setTextViewText(R.id.stat_value, "$done / $goal")
            v.setTextViewText(R.id.stat_label, "книг за $year")
            v.setViewVisibility(R.id.stat_progress, View.VISIBLE)
            v.setProgressBar(R.id.stat_progress, 1000, (done * 1000 / goal.coerceAtLeast(1)).coerceAtMost(1000), false)
            v.setTextViewText(R.id.stat_sub, if (done >= goal) "цель выполнена" else "осталось ${goal - done}")
            v.setOnClickPendingIntent(R.id.stat_root, openApp(ctx))
            mgr.updateAppWidget(ids, v)
        }
    }
}

/** One of the person's own quotes, a new one each day. */
class QuoteWidget : AppWidgetProvider() {
    override fun onUpdate(ctx: Context, mgr: AppWidgetManager, ids: IntArray) {
        val done = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try { render(ctx, mgr, ids) } finally { done.finish() }
        }
    }
    companion object {
        suspend fun render(ctx: Context, mgr: AppWidgetManager, ids: IntArray) {
            val p = ctx.getSharedPreferences("quote_widget", 0)
            val today = LocalDate.now().toString()
            var text = p.getString("text", "").orEmpty(); var src = p.getString("src", "").orEmpty()
            if (p.getString("day", "") != today) {
                val lib = Library.get(ctx)
                val all = lib.books.value.filter { !it.hidden }.flatMap { b -> b.highlights.filter { it.whole || it.end > it.start }.map { b to it } }
                if (all.isNotEmpty()) {
                    val (b, h) = all[(LocalDate.now().toEpochDay() % all.size).toInt()]
                    runCatching { lib.open(b.id) }.getOrNull()?.let { book ->
                        text = h.text(book.blocks.getOrNull(h.block)?.text.orEmpty()).trim().take(400)
                        src = listOf(b.title, b.author).filter { it.isNotEmpty() }.joinToString(" — ")
                    }
                } else { text = ""; src = "" }
                p.edit().putString("day", today).putString("text", text).putString("src", src).apply()
            }
            val v = RemoteViews(ctx.packageName, R.layout.widget_quote)
            v.setTextViewText(R.id.quote_text, if (text.isNotEmpty()) "«$text»" else "Здесь будет цитата дня — выдели понравившиеся строки в книге")
            v.setTextViewText(R.id.quote_source, src)
            v.setOnClickPendingIntent(R.id.quote_root, openApp(ctx))
            mgr.updateAppWidget(ids, v)
        }
    }
}

/** Refresh every widget of the app (after reading, finishing a book, changing the goal). */
object Widgets {
    fun refreshAll(ctx: Context) {
        val mgr = AppWidgetManager.getInstance(ctx)
        fun ids(c: Class<*>) = runCatching { mgr.getAppWidgetIds(ComponentName(ctx, c)) }.getOrNull() ?: IntArray(0)
        ids(StreakWidget::class.java).takeIf { it.isNotEmpty() }?.let { StreakWidget.render(ctx, mgr, it) }
        ids(GoalWidget::class.java).takeIf { it.isNotEmpty() }?.let { GoalWidget.render(ctx, mgr, it) }
    }
}
