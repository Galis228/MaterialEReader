package io.github.galis228.materialereader.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.TravelExplore
import androidx.compose.material3.IconButton
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.galis228.materialereader.data.BookEntry
import io.github.galis228.materialereader.data.Library
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private enum class Filter(val label: String) { ALL("Все"), READING("Читаю"), NEW("Новые"), DONE("Прочитанные") }

private const val WPM = 200

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun LibraryScreen(onOpen: (String) -> Unit, onCatalog: () -> Unit, onSettings: () -> Unit, onWords: () -> Unit = {}) {
    val ctx = LocalContext.current
    val library = remember { Library.get(ctx) }
    val books by library.books.collectAsState()
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var importing by remember { mutableStateOf(false) }
    val scanning by library.scanning.collectAsState()
    var filter by rememberSaveable { mutableStateOf(Filter.ALL) }
    var toDelete by remember { mutableStateOf<BookEntry?>(null) }
    var menuFor by remember { mutableStateOf<BookEntry?>(null) }
    val simple = remember { io.github.galis228.materialereader.data.SettingsRepo(ctx) }.settings.collectAsState(initial = null).value?.simpleMode == true

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        scope.launch {
            importing = true
            var failed = 0
            var last: BookEntry? = null
            for (u in uris) library.import(u).onSuccess { last = it }.onFailure { failed++ }
            importing = false
            when {
                failed > 0 -> snackbar.showSnackbar(if (uris.size == 1) "Не получилось открыть файл. Поддерживаются EPUB, FB2 и TXT."
                    else "Не удалось добавить книг: $failed")
                uris.size == 1 && last != null -> onOpen(last!!.id)
            }
        }
    }
    val launchPicker = {
        picker.launch(arrayOf("application/epub+zip", "application/x-fictionbook+xml", "application/x-fb2", "text/plain",
            "application/zip", "application/x-zip-compressed", "application/octet-stream", "text/xml", "application/xml",
            "application/vnd.comicbook+zip", "application/vnd.comicbook-rar", "application/x-cbz", "application/x-cbr", "application/x-rar-compressed"))
    }

    val scroll = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val current = books.filter { it.started && !it.finished }.maxByOrNull { it.lastOpenedAt }
    val shown = when (filter) {
        Filter.ALL -> books
        Filter.READING -> books.filter { it.started && !it.finished }
        Filter.NEW -> books.filter { !it.started }
        Filter.DONE -> books.filter { it.finished }
    }.sortedByDescending { maxOf(it.lastOpenedAt, it.addedAt) }

    Scaffold(
        modifier = Modifier.nestedScroll(scroll.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = { Text("Библиотека") }, scrollBehavior = scroll,
                actions = {
                    IconButton(onClick = onCatalog) { Icon(Icons.Rounded.TravelExplore, "Каталоги книг") }
                    if (!simple) IconButton(onClick = onWords) {
                        val due = io.github.galis228.materialereader.data.Words.get(ctx).all.collectAsState().value.count { it.due <= System.currentTimeMillis() }
                        androidx.compose.material3.BadgedBox(badge = { if (due > 0) androidx.compose.material3.Badge { Text("$due") } }) {
                            Icon(Icons.Rounded.School, "Слова")
                        }
                    }
                    IconButton(onClick = onSettings) { Icon(Icons.Rounded.Settings, "Настройки") }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(onClick = launchPicker, icon = { Icon(Icons.Rounded.Add, null) }, text = { Text("Добавить") })
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            if (importing || scanning) LinearProgressIndicator(Modifier.fillMaxWidth())
            if (books.isEmpty()) {
                EmptyLibrary(launchPicker, onCatalog, onSettings)
                return@Column
            }
            LazyVerticalGrid(
                columns = GridCells.Adaptive(if (simple) 150.dp else 104.dp),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 104.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                if (current != null) item(span = { GridItemSpan(maxLineSpan) }) {
                    ContinueCard(current) { onOpen(current.id) }
                }
                if (!simple) item(span = { GridItemSpan(maxLineSpan) }) { StatsRow(books) }
                if (!simple) item(span = { GridItemSpan(maxLineSpan) }) {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(Filter.entries) { f ->
                            FilterChip(selected = filter == f, onClick = { filter = f }, label = { Text(f.label) })
                        }
                    }
                }
                if (shown.isEmpty()) item(span = { GridItemSpan(maxLineSpan) }) {
                    Text("Здесь пока пусто", color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 24.dp), textAlign = TextAlign.Center)
                }
                items(shown, key = { it.id }) { b ->
                    Column(Modifier.combinedClickable(onClick = { onOpen(b.id) }, onLongClick = { menuFor = b })) {
                        BookCover(b, Modifier.fillMaxWidth().aspectRatio(0.68f))
                        Spacer(Modifier.height(6.dp))
                        Text(b.title, style = if (simple) MaterialTheme.typography.titleMedium else MaterialTheme.typography.labelLarge,
                            maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text(
                            when {
                                b.finished -> "прочитана"
                                b.started -> "${(b.progress * 100).roundToInt()}%"
                                b.isComic -> "комикс · ${b.totalBlocks} стр."
                                else -> "новая"
                            },
                            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }

    menuFor?.let { b ->
        // keep the sheet showing fresh data (cover, read mark) for the same book
        val fresh = books.firstOrNull { it.id == b.id } ?: b
        BookMenuSheet(fresh, onOpen = { onOpen(b.id) }, onDelete = { toDelete = fresh }) { menuFor = null }
    }

    toDelete?.let { b ->
        AlertDialog(
            onDismissRequest = { toDelete = null },
            title = { Text("Удалить книгу?") },
            text = { Text("«${b.title}» будет удалена из библиотеки вместе с прогрессом.") },
            confirmButton = { TextButton(onClick = { scope.launch { library.delete(b.id) }; toDelete = null }) { Text("Удалить") } },
            dismissButton = { TextButton(onClick = { toDelete = null }) { Text("Отмена") } },
        )
    }
}

@Composable
private fun ContinueCard(b: BookEntry, onRead: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Card(
        onClick = onRead,
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = cs.primary, contentColor = cs.onPrimary),
    ) {
        Row(Modifier.padding(18.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            BookCover(b, Modifier.width(84.dp).height(124.dp))
            Column(Modifier.weight(1f).height(124.dp)) {
                Text("Продолжить чтение", style = MaterialTheme.typography.labelMedium, color = cs.onPrimary.copy(alpha = 0.8f))
                Text(b.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                val wpm = io.github.galis228.materialereader.data.ReadingSpeed.wpm(LocalContext.current)
                val left = ((1f - b.progress) * b.words / wpm).roundToInt()
                Text(
                    if (b.isComic) "стр. ${b.position + 1} из ${b.totalBlocks}"
                    else "${(b.progress * 100).roundToInt()}% · ${formatMinutes(left)} до конца",
                    style = MaterialTheme.typography.labelMedium, color = cs.onPrimary.copy(alpha = 0.8f),
                )
                Spacer(Modifier.weight(1f))
                LinearProgressIndicator(
                    progress = { b.progress }, modifier = Modifier.fillMaxWidth(),
                    color = cs.onPrimary, trackColor = cs.onPrimary.copy(alpha = 0.25f), drawStopIndicator = {},
                )
                Spacer(Modifier.height(10.dp))
                Button(
                    onClick = onRead, modifier = Modifier.height(40.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = cs.onPrimary, contentColor = cs.primary),
                ) { Text("Читать") }
            }
        }
    }
}

@Composable
private fun StatsRow(books: List<BookEntry>) {
    val hours = books.sumOf { it.readingMillis } / 3_600_000.0
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Stat("${books.size}", "в библиотеке", Modifier.weight(1f))
        Stat("${books.count { it.finished }}", "прочитано", Modifier.weight(1f))
        Stat(if (hours < 10) "%.1f".format(hours) else "${hours.roundToInt()}", "часов чтения", Modifier.weight(1f))
    }
}

@Composable
private fun Stat(value: String, label: String, modifier: Modifier) {
    Card(modifier, shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)) {
        Column(Modifier.padding(12.dp)) {
            Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun EmptyLibrary(onAdd: () -> Unit, onCatalog: () -> Unit, onSettings: () -> Unit) {
    Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(Icons.AutoMirrored.Outlined.MenuBook, null, Modifier.size(64.dp), tint = MaterialTheme.colorScheme.primary)
            Text("Полки пока пустые", style = MaterialTheme.typography.titleLarge)
            Text("Добавь книги EPUB, FB2, TXT или комиксы CBZ/CBR. Можно выбрать папку, и приложение будет само находить в ней новые книги.",
                textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Button(onClick = onAdd) { Text("Выбрать файлы") }
            TextButton(onClick = onSettings) { Text("Выбрать папки для автопоиска") }
            TextButton(onClick = onCatalog) { Text("Найти в каталогах") }
        }
    }
}

fun formatMinutes(min: Int): String = when {
    min < 1 -> "меньше минуты"
    min < 60 -> "~$min мин"
    else -> "~${min / 60} ч ${min % 60} мин"
}
