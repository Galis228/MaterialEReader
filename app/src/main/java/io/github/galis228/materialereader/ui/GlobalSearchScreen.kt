package io.github.galis228.materialereader.ui

import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Row
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material3.FilterChip
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import io.github.galis228.materialereader.data.BookEntry
import io.github.galis228.materialereader.data.Library
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Search a word or a quote in every book at once; results grouped by book, a tap opens the exact place. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GlobalSearchScreen(onBack: () -> Unit, onOpenAt: (id: String, block: Int, char: Int) -> Unit) {
    val ctx = LocalContext.current
    val library = remember { Library.get(ctx) }
    val scope = rememberCoroutineScope()
    var query by rememberSaveable { mutableStateOf("") }
    val results = remember { mutableStateListOf<Pair<BookEntry, List<SearchHit>>>() }
    var progress by remember { mutableFloatStateOf(-1f) }
    var job by remember { mutableStateOf<Job?>(null) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }

    var meaning by rememberSaveable { mutableStateOf(false) }
    var terms by remember { mutableStateOf<List<String>>(emptyList()) }
    fun run() {
        val q = query.trim()
        if (q.length < 2) return
        job?.cancel(); results.clear(); terms = emptyList()
        val books = library.books.value.filter { !it.isComic && !it.hidden }.sortedByDescending { it.lastOpenedAt }
        job = scope.launch {
            progress = 0f
            // by meaning: the AI turns a topic into words such a passage would contain, then those are searched
            val words = if (meaning) runCatching { io.github.galis228.materialereader.ai.LibraryAi.expand(ctx, q) }.getOrDefault(listOf(q)).also { terms = it } else listOf(q)
            books.forEachIndexed { i, b ->
                progress = i.toFloat() / books.size.coerceAtLeast(1)
                val hits = withContext(Dispatchers.Default) {
                    runCatching { val book = library.open(b.id); words.flatMap { w -> searchBook(book, w, if (words.size > 1) 8 else 30) } }.getOrDefault(emptyList())
                }.distinctBy { it.block }.sortedBy { it.block }.take(30)
                if (hits.isNotEmpty()) results += b to hits
            }
            progress = -1f
        }
    }

    Scaffold(topBar = {
        TopAppBar(title = { Text("Поиск по библиотеке") }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Назад") } })
    }) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            OutlinedTextField(
                query, { query = it }, singleLine = true, placeholder = { Text(if (meaning) "Тема: «разлука», «море», «предательство»" else "Слово, имя или цитата") },
                leadingIcon = { Icon(Icons.Rounded.Search, null) },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search), keyboardActions = KeyboardActions(onSearch = { run() }),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).focusRequester(focus),
            )
            Row(Modifier.padding(horizontal = 16.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = !meaning, onClick = { meaning = false }, label = { Text("Точно") })
                FilterChip(selected = meaning, onClick = { meaning = true }, label = { Text("По смыслу (ИИ)") },
                    leadingIcon = { Icon(Icons.Rounded.AutoAwesome, null, Modifier.size(18.dp)) })
            }
            if (meaning && terms.isNotEmpty()) Text("Ищу: " + terms.joinToString(", "), style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 16.dp))
            if (progress >= 0f) LinearProgressIndicator(progress = { progress }, Modifier.fillMaxWidth().padding(16.dp))
            val total = results.sumOf { it.second.size }
            if (results.isNotEmpty()) Text("Найдено: $total в ${results.size} ${if (results.size == 1) "книге" else "книгах"}",
                style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
            else if (progress < 0f && job != null) Text("Ничего не нашлось", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(16.dp))
            LazyColumn(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                results.forEach { (book, hits) ->
                    item(key = "h" + book.id) {
                        Text(book.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 14.dp))
                    }
                    items(hits, key = { "${book.id}|${it.block}|${it.char}" }) { h ->
                        Column(Modifier.fillMaxWidth().clickable { onOpenAt(book.id, h.block, h.char) }.padding(horizontal = 16.dp, vertical = 8.dp)) {
                            Text(buildAnnotatedString {
                                append(h.before); withStyle(SpanStyle(fontWeight = FontWeight.Bold, background = MaterialTheme.colorScheme.tertiaryContainer)) { append(h.match) }; append(h.after)
                            }, style = MaterialTheme.typography.bodyMedium, maxLines = 3)
                            if (h.chapter.isNotEmpty()) Text(h.chapter, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                        }
                    }
                }
            }
        }
    }
}
