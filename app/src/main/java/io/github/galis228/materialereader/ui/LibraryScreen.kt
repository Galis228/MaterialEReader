package io.github.galis228.materialereader.ui

import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.ViewAgenda
import androidx.compose.material.icons.automirrored.rounded.ViewList
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.automirrored.rounded.Label
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
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material.icons.rounded.Insights
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
import androidx.compose.foundation.clickable
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

private enum class Filter(val label: String) { ALL("Все"), READING("Читаю"), NEW("Новые"), DONE("Прочитанные"), WISH("Хочу прочитать") }
private enum class Sort(val label: String) { RECENT("Недавние"), TITLE("По названию"), AUTHOR("По автору"), PROGRESS("По прогрессу"), ADDED("По дате добавления") }
private enum class View(val label: String) { GRID("Сетка"), LIST("Список"), LARGE("Большие обложки") }

private const val WPM = 200

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun LibraryScreen(onOpen: (String) -> Unit, onCatalog: () -> Unit, onSettings: () -> Unit, onWords: () -> Unit = {}, onStats: () -> Unit = {}, onAiNotes: () -> Unit = {}, onSearch: () -> Unit = {}) {
    val ctx = LocalContext.current
    val library = remember { Library.get(ctx) }
    val books by library.books.collectAsState()
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var importing by remember { mutableStateOf(false) }
    val scanning by library.scanning.collectAsState()
    var filter by rememberSaveable { mutableStateOf(Filter.ALL) }
    val prefs = remember { ctx.getSharedPreferences("library_view", 0) }
    var sort by remember { mutableStateOf(runCatching { Sort.valueOf(prefs.getString("sort", "")!!) }.getOrDefault(Sort.RECENT)) }
    var view by remember { mutableStateOf(runCatching { View.valueOf(prefs.getString("view", "")!!) }.getOrDefault(View.GRID)) }
    val wishes by io.github.galis228.materialereader.data.Wishlist.get(ctx).all.collectAsState()
    /** A shelf picked instead of a filter; HIDDEN_SHELF = the hidden shelf. */
    var shelf by rememberSaveable { mutableStateOf<String?>(null) }
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
                failed > 0 -> snackbar.showSnackbar(if (uris.size == 1) "Не получилось открыть файл. Поддерживаются EPUB, FB2, MOBI, PDF, TXT и комиксы CBZ/CBR."
                    else "Не удалось добавить книг: $failed")
                uris.size == 1 && last != null -> onOpen(last!!.id)
            }
        }
    }
    val launchPicker = {
        picker.launch(arrayOf("application/epub+zip", "application/x-fictionbook+xml", "application/x-fb2", "text/plain",
            "application/zip", "application/x-zip-compressed", "application/octet-stream", "text/xml", "application/xml",
            "application/vnd.comicbook+zip", "application/vnd.comicbook-rar", "application/x-cbz", "application/x-cbr", "application/x-rar-compressed",
            "application/pdf", "application/x-mobipocket-ebook", "application/vnd.amazon.ebook"))
    }

    val scroll = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val visible = books.filterNot { it.hidden }
    val current = visible.filter { it.started && !it.finished }.maxByOrNull { it.lastOpenedAt }
    val shelves by library.shelfList.collectAsState()
    var newShelf by remember { mutableStateOf(false) }
    var manageShelves by remember { mutableStateOf(false) }
    val hasHidden = books.any { it.hidden }
    val shown = when (val sh = shelf) {
        HIDDEN_SHELF -> books.filter { it.hidden }
        null -> when (filter) {
            Filter.ALL -> visible
            Filter.READING -> visible.filter { it.started && !it.finished }
            Filter.NEW -> visible.filter { !it.started }
            Filter.DONE -> visible.filter { it.finished }
            Filter.WISH -> emptyList()
        }
        else -> visible.filter { sh in it.shelves }
    }.let { list ->
        when (sort) {
            Sort.RECENT -> list.sortedByDescending { maxOf(it.lastOpenedAt, it.addedAt) }
            Sort.TITLE -> list.sortedBy { it.title.lowercase() }
            Sort.AUTHOR -> list.sortedWith(compareBy({ surname(it.author).lowercase().ifEmpty { "\uffff" } }, { it.title.lowercase() }))
            Sort.PROGRESS -> list.sortedByDescending { if (it.finished) -1f else it.progress }
            Sort.ADDED -> list.sortedByDescending { it.addedAt }
        }
    }.let(::groupSeries)

    Scaffold(
        modifier = Modifier.nestedScroll(scroll.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = { Text("Библиотека") }, scrollBehavior = scroll,
                actions = {
                    if (!simple) IconButton(onClick = onSearch) { Icon(Icons.Rounded.Search, "Поиск по всем книгам") }
                    IconButton(onClick = onCatalog) { Icon(Icons.Rounded.TravelExplore, "Каталоги книг") }
                    if (!simple) IconButton(onClick = onStats) { Icon(Icons.Rounded.Insights, "Статистика") }
                    if (!simple) IconButton(onClick = onAiNotes) { Icon(Icons.Rounded.AutoAwesome, "Результаты ИИ") }
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
                columns = if (view == View.LIST) GridCells.Fixed(1) else GridCells.Adaptive(if (simple || view == View.LARGE) 150.dp else 104.dp),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 104.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                if (current != null) item(span = { GridItemSpan(maxLineSpan) }) {
                    ContinueCard(current) { onOpen(current.id) }
                }
                if (!simple) item(span = { GridItemSpan(maxLineSpan) }) { Box(Modifier.clickable(onClick = onStats)) { StatsRow(visible) } }
                if (!simple) item(span = { GridItemSpan(maxLineSpan) }) {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(Filter.entries.filter { it != Filter.WISH || wishes.isNotEmpty() }) { f ->
                            FilterChip(selected = shelf == null && filter == f, onClick = { filter = f; shelf = null },
                                label = { Text(if (f == Filter.WISH) "${f.label} · ${wishes.size}" else f.label) })
                        }
                        items(shelves) { name ->
                            FilterChip(selected = shelf == name, onClick = { shelf = if (shelf == name) null else name },
                                label = { Text("$name · ${visible.count { name in it.shelves }}") },
                                leadingIcon = { Icon(Icons.AutoMirrored.Rounded.Label, null, Modifier.size(16.dp)) })
                        }
                        item {
                            androidx.compose.material3.AssistChip(onClick = { newShelf = true }, label = { Text("Полка") },
                                leadingIcon = { Icon(Icons.Rounded.Add, null, Modifier.size(16.dp)) })
                        }
                        if (shelves.isNotEmpty()) item {
                            androidx.compose.material3.AssistChip(onClick = { manageShelves = true }, label = { Text("Изменить") },
                                leadingIcon = { Icon(Icons.Rounded.Edit, null, Modifier.size(16.dp)) })
                        }
                        if (hasHidden) item {
                            FilterChip(selected = shelf == HIDDEN_SHELF, onClick = { shelf = if (shelf == HIDDEN_SHELF) null else HIDDEN_SHELF },
                                label = { Text("Скрытые") }, leadingIcon = { Icon(Icons.Rounded.VisibilityOff, null, Modifier.size(16.dp)) })
                        }
                    }
                }
                shelf?.takeIf { it != HIDDEN_SHELF }?.let { sh ->
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.AutoMirrored.Rounded.Label, null, tint = MaterialTheme.colorScheme.primary)
                            Text(sh, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(start = 8.dp).weight(1f))
                            Text(booksWord(shown.size), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            ShelfMenuButton(sh, onDeleted = { shelf = null }, onRenamed = { shelf = it })
                        }
                    }
                }
                if (!simple) item(span = { GridItemSpan(maxLineSpan) }) {
                    SortViewRow(sort, view, onSort = { sort = it; prefs.edit().putString("sort", it.name).apply() },
                        onView = { view = it; prefs.edit().putString("view", it.name).apply() })
                }
                if (filter == Filter.WISH && shelf == null) {
                    items(wishes, key = { "w" + it.id }, span = { GridItemSpan(maxLineSpan) }) { w ->
                        WishRow(w, onOpened = onOpen)
                    }
                } else if (shown.isEmpty()) item(span = { GridItemSpan(maxLineSpan) }) {
                    Text("Здесь пока пусто", color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 24.dp), textAlign = TextAlign.Center)
                }
                if (!(filter == Filter.WISH && shelf == null)) items(shown, key = { it.id }) { b ->
                    if (view == View.LIST) { ListRow(b, Modifier.combinedClickable(onClick = { onOpen(b.id) }, onLongClick = { menuFor = b })); return@items }
                    Column(Modifier.combinedClickable(onClick = { onOpen(b.id) }, onLongClick = { menuFor = b })) {
                        BookCover(b, Modifier.fillMaxWidth().aspectRatio(0.68f))
                        Spacer(Modifier.height(6.dp))
                        Text(b.title, style = if (simple) MaterialTheme.typography.titleMedium else MaterialTheme.typography.labelLarge,
                            maxLines = 2, overflow = TextOverflow.Ellipsis)
                        if (b.series.isNotEmpty()) Text(
                            b.series + if (b.seriesIndex > 0f) " · №" + (if (b.seriesIndex % 1f == 0f) b.seriesIndex.toInt().toString() else b.seriesIndex.toString()) else "",
                            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
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

    if (newShelf) NewShelfDialog(onCreated = { shelf = it; newShelf = false }) { newShelf = false }
    if (manageShelves) ShelfManagerSheet { manageShelves = false }
    // a shelf that was renamed or deleted can't stay selected
    LaunchedEffect(shelves) { if (shelf != null && shelf != HIDDEN_SHELF && shelf !in shelves) shelf = null }

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

private const val HIDDEN_SHELF = "\u0000hidden"

/** Books of one series stand together, in series order, where the first of them would be. */
private fun groupSeries(list: List<BookEntry>): List<BookEntry> {
    val bySeries = list.filter { it.series.isNotEmpty() }.groupBy { it.series.lowercase() }
    val placed = HashSet<String>()
    val out = ArrayList<BookEntry>(list.size)
    for (b in list) {
        if (b.series.isEmpty()) { out += b; continue }
        val key = b.series.lowercase()
        if (placed.add(key)) out += bySeries.getValue(key).sortedWith(compareBy({ if (it.seriesIndex > 0f) it.seriesIndex else Float.MAX_VALUE }, { it.title }))
    }
    return out
}

@Composable
private fun SortViewRow(sort: Sort, view: View, onSort: (Sort) -> Unit, onView: (View) -> Unit) {
    var sortOpen by remember { mutableStateOf(false) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box {
            TextButton(onClick = { sortOpen = true }) {
                Icon(Icons.AutoMirrored.Rounded.Sort, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text(sort.label)
            }
            androidx.compose.material3.DropdownMenu(sortOpen, { sortOpen = false }) {
                Sort.entries.forEach { s -> androidx.compose.material3.DropdownMenuItem({ Text(s.label) }, onClick = { sortOpen = false; onSort(s) },
                    trailingIcon = { if (s == sort) Icon(Icons.Rounded.Check, null) }) }
            }
        }
        Spacer(Modifier.weight(1f))
        androidx.compose.material3.SingleChoiceSegmentedButtonRow {
            View.entries.forEachIndexed { i, v ->
                SegmentedButton(selected = view == v, onClick = { onView(v) },
                    shape = androidx.compose.material3.SegmentedButtonDefaults.itemShape(i, View.entries.size),
                    icon = {}, label = {
                        Icon(when (v) { View.GRID -> Icons.Rounded.GridView; View.LIST -> Icons.AutoMirrored.Rounded.ViewList; View.LARGE -> Icons.Rounded.ViewAgenda }, v.label, Modifier.size(18.dp))
                    })
            }
        }
    }
}

@Composable
private fun ListRow(b: BookEntry, modifier: Modifier) {
    Row(modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        BookCover(b, Modifier.width(52.dp).height(76.dp), corner = 6.dp, titleSize = 8)
        Column(Modifier.weight(1f).padding(start = 14.dp)) {
            Text(b.title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (b.author.isNotEmpty()) Text(b.author, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            if (b.series.isNotEmpty()) Text(b.series + if (b.seriesIndex > 0f) " · №" + b.seriesIndex.toString().removeSuffix(".0") else "",
                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, maxLines = 1)
            if (b.started && !b.finished) LinearProgressIndicator(progress = { b.progress }, Modifier.fillMaxWidth().padding(top = 6.dp), drawStopIndicator = {})
            else Text(if (b.finished) "прочитана" else "новая", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** A wished-for book: download in one tap (it then leaves the list), or remove. */
@Composable
private fun WishRow(w: io.github.galis228.materialereader.data.Wish, onOpened: (String) -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Column(Modifier.fillMaxWidth().padding(14.dp)) {
            Text(w.title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (w.author.isNotEmpty()) Text(w.author, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (w.summary.isNotEmpty()) Text(w.summary, style = MaterialTheme.typography.bodySmall, maxLines = 3, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp))
            error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                val best = w.acquisitions.minByOrNull { it.rank }
                if (busy) CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 3.dp)
                else if (best != null) Button(onClick = {
                    busy = true; error = null
                    scope.launch {
                        runCatching {
                            val (bytes, name) = io.github.galis228.materialereader.data.Opds.download(best, w.title)
                            Library.get(ctx).importBytes(bytes, name).getOrThrow()
                        }.onSuccess { io.github.galis228.materialereader.data.Wishlist.get(ctx).remove(w.id); onOpened(it.id) }
                            .onFailure { error = "Не удалось скачать: ${it.message}" }
                        busy = false
                    }
                }) { Text("Скачать ${best.label}") }
                Spacer(Modifier.weight(1f))
                TextButton(onClick = { io.github.galis228.materialereader.data.Wishlist.get(ctx).remove(w.id) }) { Text("Убрать") }
            }
        }
    }
}
