package io.github.galis228.materialereader.data

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.view.View
import android.widget.RemoteViews
import io.github.galis228.materialereader.MainActivity
import io.github.galis228.materialereader.R
import kotlin.math.roundToInt

/** Home-screen widget: the book being read, its progress, and a tap to continue right where you stopped. */
class ContinueWidget : AppWidgetProvider() {
    override fun onUpdate(ctx: Context, mgr: AppWidgetManager, ids: IntArray) = render(ctx, mgr, ids)

    companion object {
        const val EXTRA_BOOK = "open_book"

        fun refresh(ctx: Context) {
            runCatching { Widgets.refreshAll(ctx) }
            val mgr = AppWidgetManager.getInstance(ctx)
            val ids = runCatching { mgr.getAppWidgetIds(ComponentName(ctx, ContinueWidget::class.java)) }.getOrNull() ?: return
            if (ids.isNotEmpty()) render(ctx, mgr, ids)
        }

        private fun render(ctx: Context, mgr: AppWidgetManager, ids: IntArray) {
            val lib = Library.get(ctx)
            val b = lib.books.value.filter { it.started && !it.finished && !it.hidden }.maxByOrNull { it.lastOpenedAt }
                ?: lib.books.value.filter { !it.hidden }.maxByOrNull { it.lastOpenedAt }
            val v = RemoteViews(ctx.packageName, R.layout.widget_continue)
            val open = Intent(ctx, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                if (b != null) putExtra(EXTRA_BOOK, b.id)
            }
            v.setOnClickPendingIntent(R.id.widget_root, PendingIntent.getActivity(ctx, 7, open, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
            if (b == null) {
                v.setTextViewText(R.id.widget_title, "Добавь книгу")
                v.setTextViewText(R.id.widget_progress_text, "")
                v.setViewVisibility(R.id.widget_progress, View.GONE)
                v.setViewVisibility(R.id.widget_cover, View.GONE)
            } else {
                v.setTextViewText(R.id.widget_title, b.title)
                v.setTextViewText(R.id.widget_progress_text, if (b.isComic) "стр. ${b.position + 1} из ${b.totalBlocks}" else "${(b.progress * 100).roundToInt()}% · ${b.author}")
                v.setViewVisibility(R.id.widget_progress, View.VISIBLE)
                v.setProgressBar(R.id.widget_progress, 1000, (b.progress * 1000).roundToInt(), false)
                val cover = runCatching {
                    val f = lib.coverFile(b.id)
                    if (!b.hasCover || !f.exists()) null
                    else BitmapFactory.decodeFile(f.path, BitmapFactory.Options().apply { inSampleSize = 4 })
                }.getOrNull()
                if (cover != null) { v.setImageViewBitmap(R.id.widget_cover, cover); v.setViewVisibility(R.id.widget_cover, View.VISIBLE) }
                else v.setViewVisibility(R.id.widget_cover, View.GONE)
            }
            mgr.updateAppWidget(ids, v)
        }
    }
}
