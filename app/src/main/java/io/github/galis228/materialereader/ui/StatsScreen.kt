package io.github.galis228.materialereader.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.galis228.materialereader.data.Library
import io.github.galis228.materialereader.data.Stats
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.roundToInt

/** Reading stats: today, streak, the last week, the yearly goal and the year in review. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val stats = remember { Stats.get(ctx) }
    val rev by stats.rev.collectAsState()
    val books by Library.get(ctx).books.collectAsState()
    val year = LocalDate.now().year
    val week = remember(rev) { stats.lastDays(7) }
    val yearDays = remember(rev) { stats.yearDays(year) }
    val finishedThisYear = books.filter { it.finishedAt > 0 && java.time.Instant.ofEpochMilli(it.finishedAt).atZone(java.time.ZoneId.systemDefault()).year == year }
    val goal = remember(rev) { stats.yearGoal }
    val cs = MaterialTheme.colorScheme

    Scaffold(topBar = {
        TopAppBar(title = { Text("Статистика") }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Назад") } })
    }) { pad ->
        Column(
            Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            val today = week.last()
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Tile("${(today.millis / 60_000).toInt()} мин", "сегодня", Modifier.weight(1f))
                Tile("${stats.streak()}", "дней подряд", Modifier.weight(1f))
                Tile("${stats.bestStreak()}", "лучшая серия", Modifier.weight(1f))
            }

            Section("Неделя") {
                val max = (week.maxOf { it.millis }.coerceAtLeast(10 * 60_000L)).toFloat()
                val bar = cs.primary; val track = cs.surfaceContainerHighest
                Canvas(Modifier.fillMaxWidth().height(120.dp)) {
                    val gap = 10.dp.toPx()
                    val w = (size.width - gap * 6) / 7
                    week.forEachIndexed { i, d ->
                        val x = i * (w + gap)
                        drawRoundRect(track, Offset(x, 0f), Size(w, size.height), CornerRadius(8.dp.toPx()))
                        val h = size.height * (d.millis / max)
                        if (h > 0f) drawRoundRect(bar, Offset(x, size.height - h), Size(w, h), CornerRadius(8.dp.toPx()))
                    }
                }
                Row(Modifier.fillMaxWidth()) {
                    week.forEach { d ->
                        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(d.date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale("ru")), style = MaterialTheme.typography.labelSmall)
                            Text("${(d.millis / 60_000).toInt()}", style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant)
                        }
                    }
                }
            }

            Section("Цель на $year год") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("${finishedThisYear.size} из $goal книг", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                    FilledTonalIconButton(onClick = { stats.yearGoal = goal - 1 }) { Icon(Icons.Rounded.Remove, "Меньше") }
                    FilledTonalIconButton(onClick = { stats.yearGoal = goal + 1 }) { Icon(Icons.Rounded.Add, "Больше") }
                }
                LinearProgressIndicator(progress = { (finishedThisYear.size.toFloat() / goal).coerceIn(0f, 1f) }, Modifier.fillMaxWidth())
                val dayOfYear = LocalDate.now().dayOfYear
                val expected = goal * dayOfYear / LocalDate.now().lengthOfYear().toFloat()
                Text(
                    when {
                        finishedThisYear.size >= goal -> "Цель выполнена!"
                        finishedThisYear.size + 0.5f >= expected -> "Идёшь по плану"
                        else -> "Чтобы успеть, нужно ещё ${goal - finishedThisYear.size} — примерно по книге в ${((LocalDate.now().lengthOfYear() - dayOfYear) / (goal - finishedThisYear.size).coerceAtLeast(1))} дн."
                    },
                    style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant,
                )
            }

            Section("Итоги $year года") {
                val hours = yearDays.sumOf { it.millis } / 3_600_000.0
                val words = yearDays.sumOf { it.words.toLong() }
                val best = yearDays.maxByOrNull { it.millis }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Tile(if (hours < 10) "%.1f".format(hours) else "${hours.roundToInt()}", "часов", Modifier.weight(1f))
                    Tile("${finishedThisYear.size}", "книг дочитано", Modifier.weight(1f))
                    Tile(if (words >= 10_000) "${words / 1000}k" else "$words", "слов", Modifier.weight(1f))
                }
                Text("Дней с чтением: ${yearDays.count { it.millis >= 60_000L }}", style = MaterialTheme.typography.bodyMedium)
                best?.takeIf { it.millis > 0 }?.let {
                    Text("Самый читательский день: ${it.date.dayOfMonth} ${it.date.month.getDisplayName(TextStyle.FULL, Locale("ru"))} — ${(it.millis / 60_000)} мин",
                        style = MaterialTheme.typography.bodyMedium)
                }
                val authors = remember(rev) { stats.topAuthors(year) }
                if (authors.isNotEmpty()) {
                    Text("Любимые авторы", style = MaterialTheme.typography.titleSmall)
                    authors.forEachIndexed { i, (a, ms) -> Text("${i + 1}. $a — ${(ms / 60_000)} мин", style = MaterialTheme.typography.bodyMedium) }
                }
                if (finishedThisYear.isNotEmpty()) {
                    Text("Дочитано", style = MaterialTheme.typography.titleSmall)
                    finishedThisYear.sortedBy { it.finishedAt }.forEach { Text("· ${it.title}", style = MaterialTheme.typography.bodyMedium) }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Card(shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            content()
        }
    }
}

@Composable
private fun Tile(value: String, label: String, modifier: Modifier) {
    Card(modifier, shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
        Column(Modifier.padding(12.dp)) {
            Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(label, style = MaterialTheme.typography.labelSmall)
        }
    }
}
