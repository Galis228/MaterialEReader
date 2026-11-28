package io.github.galis228.materialereader.data

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import io.github.galis228.materialereader.MainActivity
import io.github.galis228.materialereader.R
import java.util.Calendar

/** A daily nudge at a chosen time — only on days with no reading yet, so the streak doesn't break. */
object Reminders {
    private const val CHANNEL = "reading_reminder"
    private fun prefs(ctx: Context) = ctx.getSharedPreferences("reminder", 0)

    fun enabled(ctx: Context) = prefs(ctx).getBoolean("on", false)
    fun hour(ctx: Context) = prefs(ctx).getInt("hour", 21)
    fun minute(ctx: Context) = prefs(ctx).getInt("minute", 0)

    fun set(ctx: Context, on: Boolean, hour: Int = hour(ctx), minute: Int = minute(ctx)) {
        prefs(ctx).edit().putBoolean("on", on).putInt("hour", hour).putInt("minute", minute).apply()
        schedule(ctx)
    }

    private fun pending(ctx: Context) = PendingIntent.getBroadcast(ctx, 11, Intent(ctx, ReminderReceiver::class.java),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

    /** Next alarm at the chosen time (today if still ahead, else tomorrow). Inexact: no special permission needed. */
    fun schedule(ctx: Context) {
        val am = ctx.getSystemService(AlarmManager::class.java)
        am.cancel(pending(ctx))
        if (!enabled(ctx)) return
        val c = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour(ctx)); set(Calendar.MINUTE, minute(ctx)); set(Calendar.SECOND, 0)
            if (timeInMillis <= System.currentTimeMillis() + 30_000) add(Calendar.DAY_OF_YEAR, 1)
        }
        am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, c.timeInMillis, pending(ctx))
    }

    fun notifyNow(ctx: Context) {
        val stats = Stats.get(ctx)
        if (stats.day(java.time.LocalDate.now()).millis >= 60_000L) return // already read today
        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL, "Напоминание почитать", NotificationManager.IMPORTANCE_DEFAULT))
        val book = Library.get(ctx).books.value.filter { it.started && !it.finished && !it.hidden }.maxByOrNull { it.lastOpenedAt }
        val open = PendingIntent.getActivity(ctx, 12, Intent(ctx, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            book?.let { putExtra(ContinueWidget.EXTRA_BOOK, it.id) }
        }, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val streak = stats.streak()
        val text = when {
            streak > 0 && book != null -> "Серия $streak ${days(streak)} — продолжим «${book.title}»?"
            book != null -> "Пара страниц «${book.title}» перед сном?"
            else -> "Самое время почитать"
        }
        nm.notify(12, NotificationCompat.Builder(ctx, CHANNEL).setSmallIcon(R.drawable.ic_stat_book)
            .setContentTitle("Почитаем?").setContentText(text).setContentIntent(open).setAutoCancel(true).build())
    }

    private fun days(n: Int): String { val m10 = n % 10; val m100 = n % 100
        return when { m10 == 1 && m100 != 11 -> "день"; m10 in 2..4 && m100 !in 12..14 -> "дня"; else -> "дней" } }

    /** Long-press shortcuts on the app icon: the last three books. */
    fun updateShortcuts(ctx: Context) = runCatching {
        val recent = Library.get(ctx).books.value.filter { it.lastOpenedAt > 0 && !it.hidden }.sortedByDescending { it.lastOpenedAt }.take(3)
        ShortcutManagerCompat.setDynamicShortcuts(ctx, recent.mapIndexed { i, b ->
            ShortcutInfoCompat.Builder(ctx, "book-${b.id}")
                .setShortLabel(b.title.take(24)).setLongLabel(b.title.take(48))
                .setIcon(IconCompat.createWithResource(ctx, R.drawable.ic_stat_book)).setRank(i)
                .setIntent(Intent(ctx, MainActivity::class.java).setAction(Intent.ACTION_MAIN).putExtra(ContinueWidget.EXTRA_BOOK, b.id))
                .build()
        })
    }
}

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) { Reminders.schedule(ctx); return }
        runCatching { Reminders.notifyNow(ctx) }
        Reminders.schedule(ctx) // and the next day
    }
}
