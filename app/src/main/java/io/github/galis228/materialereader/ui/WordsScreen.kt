package io.github.galis228.materialereader.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.School
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import io.github.galis228.materialereader.data.Word
import io.github.galis228.materialereader.data.Words
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun WordsScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val words = remember { Words.get(ctx) }
    val all by words.all.collectAsState()
    val scope = rememberCoroutineScope()
    var reviewing by remember { mutableStateOf(false) }
    var toDelete by remember { mutableStateOf<Word?>(null) }
    val due = all.filter { it.due <= System.currentTimeMillis() }

    if (reviewing) { ReviewMode(words, onClose = { reviewing = false }); return }

    Scaffold(topBar = {
        TopAppBar(title = { Text("Слова") }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Назад") } })
    }) { pad ->
        if (all.isEmpty()) {
            Box(Modifier.padding(pad).fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Icon(Icons.Rounded.School, null, Modifier.size(56.dp), tint = MaterialTheme.colorScheme.primary)
                    Text("Карточек пока нет", style = MaterialTheme.typography.titleLarge)
                    Text("Во время чтения нажми и подержи слово, затем «В карточки». Слова будут возвращаться на повторение через день, 3 дня, неделю и дальше.",
                        textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            return@Scaffold
        }
        LazyColumn(Modifier.padding(pad), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer), shape = RoundedCornerShape(24.dp)) {
                    Row(Modifier.fillMaxWidth().padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(if (due.isEmpty()) "На сегодня всё повторено" else "К повторению: ${due.size}", style = MaterialTheme.typography.titleMedium)
                            Text("Всего слов: ${all.size}", style = MaterialTheme.typography.bodySmall)
                        }
                        Button(onClick = { reviewing = true }, enabled = due.isNotEmpty()) { Text("Повторить") }
                    }
                }
            }
            items(all, key = { it.id }) { w ->
                Column(Modifier.fillMaxWidth().combinedClickable(onClick = {}, onLongClick = { toDelete = w }).padding(vertical = 6.dp)) {
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(w.word, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        if (w.translation.isNotEmpty()) Text("  —  ${w.translation}", style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Text(w.context, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(w.book, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline, maxLines = 1)
                }
            }
        }
    }
    toDelete?.let { w ->
        AlertDialog(
            onDismissRequest = { toDelete = null },
            title = { Text("Удалить «${w.word}»?") },
            confirmButton = { TextButton(onClick = { scope.launch { words.remove(w.id) }; toDelete = null }) { Text("Удалить") } },
            dismissButton = { TextButton(onClick = { toDelete = null }) { Text("Отмена") } },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReviewMode(words: Words, onClose: () -> Unit) {
    val scope = rememberCoroutineScope()
    val queue = remember { words.dueNow().shuffled().toMutableList() }
    var index by remember { mutableStateOf(0) }
    var shown by remember { mutableStateOf(false) }
    val w = queue.getOrNull(index)

    Scaffold(topBar = {
        TopAppBar(title = { Text(if (w == null) "Готово" else "${index + 1} из ${queue.size}") },
            navigationIcon = { IconButton(onClick = onClose) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Закончить") } })
    }) { pad ->
        Column(Modifier.padding(pad).fillMaxSize().padding(20.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            if (w == null) {
                Text("Все карточки на сегодня повторены 🎉", style = MaterialTheme.typography.titleLarge)
                Button(onClick = onClose) { Text("К списку слов") }
                return@Column
            }
            Card(shape = RoundedCornerShape(28.dp), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Text(w.word, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
                    Text(buildAnnotatedString {
                        val i = w.context.indexOf(w.word, ignoreCase = true)
                        if (i < 0) append(w.context) else {
                            append(w.context.substring(0, i))
                            withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(w.context.substring(i, i + w.word.length)) }
                            append(w.context.substring(i + w.word.length))
                        }
                    }, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (shown) {
                        if (w.translation.isNotEmpty()) Text(w.translation, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                        if (w.meaning.isNotEmpty()) Text(w.meaning, style = MaterialTheme.typography.bodyMedium)
                        if (w.translation.isEmpty() && w.meaning.isEmpty()) Text("Перевода и значения не сохранено — вспомни по контексту.",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("«${w.book}»", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                    }
                }
            }
            if (!shown) FilledTonalButton(onClick = { shown = true }, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text("Показать ответ") }
            else Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = { scope.launch { words.review(w, false) }; shown = false; index++ }, modifier = Modifier.weight(1f).height(52.dp)) { Text("Не помню") }
                Button(onClick = { scope.launch { words.review(w, true) }; shown = false; index++ }, modifier = Modifier.weight(1f).height(52.dp)) { Text("Помню") }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}
