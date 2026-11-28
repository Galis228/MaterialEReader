package io.github.galis228.materialereader.ui

import androidx.compose.material3.ListItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Tab
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.TopAppBar
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.BookmarkBorder
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.DoneAll
import androidx.compose.material.icons.rounded.SelectAll
import androidx.compose.material.icons.rounded.Close
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.background
import androidx.activity.compose.BackHandler
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
private enum class Sort(val label: String, val short: String) {
    RECENT("Недавние", "Недавние"), TITLE("По названию", "Название"), AUTHOR("По автору", "Автор"),
    PROGRESS("По прогрессу", "Прогресс"), ADDED("По дате добавления", "Добавлены"),
}
private enum class View(val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector) {
    GRID("Сетка", Icons.Rounded.GridView), LIST("Список", Icons.AutoMirrored.Rounded.ViewList), LARGE("Большие обложки", Icons.Rounded.ViewAgenda),
}

private const val WPM = 200

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
/** What the library shows: a filter, a shelf, or the hidden shelf. */
private sealed interface Pick {
    data class F(val f: Filter) : Pick
    data class S(val name: String) : Pick
    data object Hidden : Pick
}

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
    val prefs = remember { ctx.getSharedPreferences("library_view", 0) }
    var sort by remember { mutableStateOf(runCatching { Sort.valueOf(prefs.getString("sort", "")!!) }.getOrDefault(Sort.RECENT)) }
    var view by remember { mutableStateOf(runCatching { View.valueOf(prefs.getString("view", "")!!) }.getOrDefault(View.GRID)) }
    val wishes by io.github.galis228.materialereader.data.Wishlist.get(ctx).all.collectAsState()
    var pick by remember { mutableStateOf<Pick>(Pick.F(Filter.ALL)) }
    /** Rows layout: true = the home with shelves as rows; false = one shelf or filter as a grid. */
    var rowsHome by rememberSaveable { mutableStateOf(true) }
    var toDelete by remember { mutableStateOf<List<BookEntry>>(emptyList()) }
    var menuFor by remember { mutableStateOf<BookEntry?>(null) }
    var selected by remember { mutableStateOf<Set<String>>(emptySet()) }
    var shelfPickFor by remember { mutableStateOf<Set<String>?>(null) }
    var showSheet by remember { mutableStateOf(false) }
    var overflow by remember { mutableStateOf(false) }
    val repo = remember { io.github.galis228.materialereader.data.SettingsRepo(ctx) }
    val st = repo.settings.collectAsState(initial = null).value
    val simple = st?.simpleMode == true
    val layout = st?.libraryLayout ?: io.github.galis228.materialereader.data.LibraryLayout.TABS
    val selecting = selected.isNotEmpty()
    BackHandler(enabled = selecting) { selected = emptySet() }

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
            "application/pdf", "application/x-mobipocket-ebook", "application/vnd.amazon.ebook", "application/x-7z-compressed"))
    }

    val scroll = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val visible = books.filterNot { it.hidden }
    val current = visible.filter { it.started && !it.finished }.maxByOrNull { it.lastOpenedAt }
    val shelves by library.shelfList.collectAsState()
    var newShelf by remember { mutableStateOf(false) }
    var manageShelves by remember { mutableStateOf(false) }
    val hasHidden = books.any { it.hidden }
    fun sorted(list: List<BookEntry>) = when (sort) {
        Sort.RECENT -> list.sortedByDescending { maxOf(it.lastOpenedAt, it.addedAt) }
        Sort.TITLE -> list.sortedBy { it.title.lowercase() }
        Sort.AUTHOR -> list.sortedWith(compareBy({ surname(it.author).lowercase().ifEmpty { "￿" } }, { it.title.lowercase() }))
        Sort.PROGRESS -> list.sortedByDescending { if (it.finished) -1f else it.progress }
        Sort.ADDED -> list.sortedByDescending { it.addedAt }
    }.let(::groupSeries)
    fun booksOf(p: Pick): List<BookEntry> = when (p) {
        Pick.Hidden -> books.filter { it.hidden }
        is Pick.S -> visible.filter { p.name in it.shelves }
        is Pick.F -> when (p.f) {
            Filter.ALL -> visible
            Filter.READING -> visible.filter { it.started && !it.finished }
            Filter.NEW -> visible.filter { !it.started }
            Filter.DONE -> visible.filter { it.finished }
            Filter.WISH -> emptyList()
        }
    }
    val shown = sorted(booksOf(pick))
    val wishMode = pick == Pick.F(Filter.WISH)
    val home = layout == io.github.galis228.materialereader.data.LibraryLayout.ROWS && rowsHome && !simple
    // a shelf that was renamed or deleted can't stay picked
    LaunchedEffect(shelves) { (pick as? Pick.S)?.let { if (it.name !in shelves) pick = Pick.F(Filter.ALL) } }

    val open: (BookEntry) -> Unit = { b -> if (selecting) selected = if (b.id in selected) selected - b.id else selected + b.id else onOpen(b.id) }

    Scaffold(
        modifier = Modifier.nestedScroll(scroll.nestedScrollConnection),
        topBar = {
            if (selecting) TopAppBar(
                title = { Text("Выбрано: ${selected.size}") },
                navigationIcon = { IconButton(onClick = { selected = emptySet() }) { Icon(Icons.Rounded.Close, "Отменить выбор") } },
                actions = {
                    IconButton(onClick = { selected = shown.map { it.id }.toSet() }) { Icon(Icons.Rounded.SelectAll, "Выбрать все") }
                    IconButton(onClick = { shelfPickFor = selected }) { Icon(Icons.AutoMirrored.Rounded.Label, "На полку") }
                    IconButton(onClick = { val ids = selected; scope.launch { library.editMany(ids) { it.copy(readMark = 1, finishedAt = it.finishedAt.takeIf { f -> f > 0 } ?: System.currentTimeMillis()) } }; selected = emptySet() }) {
                        Icon(Icons.Rounded.DoneAll, "Отметить прочитанными")
                    }
                    IconButton(onClick = { val ids = selected; val hide = pick != Pick.Hidden; scope.launch { library.editMany(ids) { it.copy(hidden = hide) } }; selected = emptySet() }) {
                        Icon(if (pick == Pick.Hidden) Icons.Rounded.Visibility else Icons.Rounded.VisibilityOff, if (pick == Pick.Hidden) "Вернуть" else "Скрыть")
                    }
                    IconButton(onClick = { toDelete = books.filter { it.id in selected } }) { Icon(Icons.Rounded.Delete, "Удалить") }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
            ) else LargeTopAppBar(
                title = { Text("Библиотека") }, scrollBehavior = scroll,
                actions = {
                    if (!simple) IconButton(onClick = onSearch) { Icon(Icons.Rounded.Search, "Поиск по всем книгам") }
                    if (layout == io.github.galis228.materialereader.data.LibraryLayout.ROWS && !simple)
                        IconButton(onClick = { showSheet = true }) { Icon(Icons.Rounded.Tune, "Показать и сортировать") }
                    IconButton(onClick = onCatalog) { Icon(Icons.Rounded.TravelExplore, "Каталоги книг") }
                    Box {
                        IconButton(onClick = { overflow = true }) { Icon(Icons.Rounded.MoreVert, "Ещё") }
                        DropdownMenu(overflow, { overflow = false }) {
                            if (!simple) {
                                DropdownMenuItem({ Text("Статистика") }, leadingIcon = { Icon(Icons.Rounded.Insights, null) }, onClick = { overflow = false; onStats() })
                                DropdownMenuItem({ Text("Результаты ИИ") }, leadingIcon = { Icon(Icons.Rounded.AutoAwesome, null) }, onClick = { overflow = false; onAiNotes() })
                                DropdownMenuItem({ Text("Слова") }, leadingIcon = { Icon(Icons.Rounded.School, null) }, onClick = { overflow = false; onWords() })
                                DropdownMenuItem({ Text("Мои полки") }, leadingIcon = { Icon(Icons.AutoMirrored.Rounded.Label, null) }, onClick = { overflow = false; manageShelves = true })
                            }
                            DropdownMenuItem({ Text("Настройки") }, leadingIcon = { Icon(Icons.Rounded.Settings, null) }, onClick = { overflow = false; onSettings() })
                        }
                    }
                },
            )
        },
        floatingActionButton = {
            if (!selecting) ExtendedFloatingActionButton(onClick = launchPicker, icon = { Icon(Icons.Rounded.Add, null) }, text = { Text("Добавить") })
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            if (importing || scanning) LinearProgressIndicator(Modifier.fillMaxWidth())
            if (books.isEmpty()) {
                EmptyLibrary(launchPicker, onCatalog, onSettings)
                return@Column
            }
            // ---- Tabs layout: shelves as scrollable tabs ----
            if (layout == io.github.galis228.materialereader.data.LibraryLayout.TABS && !simple) {
                val tabs = buildList<Pair<Pick, String>> {
                    Filter.entries.filter { it != Filter.WISH }.forEach { add(Pick.F(it) to it.label) }
                    shelves.forEach { add(Pick.S(it) to it) }
                    if (wishes.isNotEmpty()) add(Pick.F(Filter.WISH) to "Хочу прочитать")
                    if (hasHidden) add(Pick.Hidden to "Скрытые")
                }
                val index = tabs.indexOfFirst { it.first == pick }.coerceAtLeast(0)
                PrimaryScrollableTabRow(selectedTabIndex = index, edgePadding = 12.dp) {
                    tabs.forEachIndexed { i, (p, label) ->
                        Tab(selected = i == index, onClick = { pick = p; selected = emptySet() }, text = { Text(label, maxLines = 1) })
                    }
                    Tab(selected = false, onClick = { newShelf = true }, icon = { Icon(Icons.Rounded.Add, "Новая полка") })
                }
            }
            if (home) {
                RowsHome(
                    current = current, books = visible, shelves = shelves, wishes = wishes.size,
                    onOpenBook = { onOpen(it.id) }, onLongBook = { menuFor = it },
                    onShow = { p -> pick = p; rowsHome = false },
                )
                return@Column
            }
            LazyVerticalGrid(
                columns = if (view == View.LIST) GridCells.Fixed(1) else GridCells.Adaptive(if (simple || view == View.LARGE) 150.dp else 104.dp),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 104.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                if (layout == io.github.galis228.materialereader.data.LibraryLayout.ROWS && !simple) item(span = { GridItemSpan(maxLineSpan) }) {
                    // back to the rows home
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { rowsHome = true; pick = Pick.F(Filter.ALL) }) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Все полки") }
                        Text(pickTitle(pick), style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                    }
                }
                if (pick == Pick.F(Filter.ALL) && current != null && layout == io.github.galis228.materialereader.data.LibraryLayout.TABS) item(span = { GridItemSpan(maxLineSpan) }) {
                    ContinueCard(current) { onOpen(current.id) }
                }
                if (!simple) item(span = { GridItemSpan(maxLineSpan) }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(if (wishMode) "${wishes.size} в списке" else booksWord(shown.size), style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                        (pick as? Pick.S)?.let { sh -> ShelfMenuButton(sh.name, onDeleted = { pick = Pick.F(Filter.ALL) }, onRenamed = { pick = Pick.S(it) }) }
                        if (!wishMode) SortViewButtons(sort, view, onSort = { sort = it; prefs.edit().putString("sort", it.name).apply() },
                            onView = { view = it; prefs.edit().putString("view", it.name).apply() })
                    }
                }
                if (wishMode) {
                    items(wishes, key = { "w" + it.id }, span = { GridItemSpan(maxLineSpan) }) { w -> WishRow(w, onOpened = onOpen) }
                } else if (shown.isEmpty()) item(span = { GridItemSpan(maxLineSpan) }) {
                    Text(if (pick is Pick.S) "На этой полке пока пусто — долгое нажатие на книгу → «Полки» или «Выбрать»" else "Здесь пока пусто",
                        color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 24.dp), textAlign = TextAlign.Center)
                }
                if (!wishMode) items(shown, key = { it.id }) { b ->
                    val mod = Modifier.combinedClickable(onClick = { open(b) }, onLongClick = { if (selecting) open(b) else menuFor = b })
                    val sel = b.id in selected
                    if (view == View.LIST) { ListRow(b, mod.then(selectedMark(sel))); return@items }
                    Column(mod) {
                        Box {
                            BookCover(b, Modifier.fillMaxWidth().aspectRatio(0.68f))
                            if (selecting) SelectBadge(sel, Modifier.align(Alignment.TopEnd).padding(6.dp))
                        }
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

    if (showSheet) ShowSheet(
        pick = pick, home = rowsHome, sort = sort, view = view, shelves = shelves, visible = visible, wishes = wishes.size, hasHidden = hasHidden,
        onPick = { pick = it; rowsHome = false }, onHome = { rowsHome = true; pick = Pick.F(Filter.ALL) },
        onSort = { sort = it; prefs.edit().putString("sort", it.name).apply() },
        onView = { view = it; prefs.edit().putString("view", it.name).apply(); rowsHome = false },
        onNewShelf = { newShelf = true }, onManage = { manageShelves = true },
    ) { showSheet = false }
    if (newShelf) NewShelfDialog(onCreated = { pick = Pick.S(it); rowsHome = false; newShelf = false }) { newShelf = false }
    if (manageShelves) ShelfManagerSheet { manageShelves = false }
    shelfPickFor?.let { ids -> PickShelfSheet(ids.size, shelves, onPick = { name ->
        scope.launch { library.addToShelf(ids, name); snackbar.showSnackbar("${booksWord(ids.size)} на полке «$name»") }
        shelfPickFor = null; selected = emptySet()
    }) { shelfPickFor = null } }

    menuFor?.let { b ->
        // keep the sheet showing fresh data (cover, read mark) for the same book
        val fresh = books.firstOrNull { it.id == b.id } ?: b
        BookMenuSheet(fresh, onOpen = { onOpen(b.id) }, onDelete = { toDelete = listOf(fresh) },
            onSelect = { selected = setOf(fresh.id); if (home) rowsHome = false },
            onAutoShelf = { name, count -> scope.launch { snackbar.showSnackbar("Полка «$name»: ${booksWord(count)}") }; pick = Pick.S(name); rowsHome = false },
        ) { menuFor = null }
    }

    if (toDelete.isNotEmpty()) AlertDialog(
        onDismissRequest = { toDelete = emptyList() },
        title = { Text(if (toDelete.size == 1) "Удалить книгу?" else "Удалить ${booksWord(toDelete.size)}?") },
        text = { Text(if (toDelete.size == 1) "«${toDelete[0].title}» будет удалена из библиотеки вместе с прогрессом." else "Книги будут удалены из библиотеки вместе с прогрессом.") },
        confirmButton = { TextButton(onClick = { val ids = toDelete.map { it.id }; scope.launch { ids.forEach { library.delete(it) } }; toDelete = emptyList(); selected = emptySet() }) { Text("Удалить") } },
        dismissButton = { TextButton(onClick = { toDelete = emptyList() }) { Text("Отмена") } },
    )
}

private fun pickTitle(p: Pick) = when (p) { is Pick.F -> p.f.label; is Pick.S -> p.name; Pick.Hidden -> "Скрытые" }

@Composable
private fun selectedMark(sel: Boolean) = if (sel) Modifier.clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.secondaryContainer) else Modifier

@Composable
private fun SelectBadge(sel: Boolean, modifier: Modifier) {
    Box(modifier.size(26.dp).clip(CircleShape).background(if (sel) MaterialTheme.colorScheme.primary else Color.Black.copy(alpha = 0.35f)),
        contentAlignment = Alignment.Center) {
        if (sel) Icon(Icons.Rounded.Check, "Выбрана", Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onPrimary)
    }
}

/** Sort as a dropdown and view as a small segmented choice, in one compact row. */
@Composable
private fun SortViewButtons(sort: Sort, view: View, onSort: (Sort) -> Unit, onView: (View) -> Unit) {
    var sortOpen by remember { mutableStateOf(false) }
    var viewOpen by remember { mutableStateOf(false) }
    Box {
        TextButton(onClick = { sortOpen = true }) { Icon(Icons.AutoMirrored.Rounded.Sort, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text(sort.short) }
        DropdownMenu(sortOpen, { sortOpen = false }) {
            Sort.entries.forEach { s -> DropdownMenuItem({ Text(s.label) }, onClick = { sortOpen = false; onSort(s) },
                trailingIcon = { if (s == sort) Icon(Icons.Rounded.Check, null) }) }
        }
    }
    Box {
        IconButton(onClick = { viewOpen = true }) { Icon(view.icon, "Вид: ${view.label}") }
        DropdownMenu(viewOpen, { viewOpen = false }) {
            View.entries.forEach { v -> DropdownMenuItem({ Text(v.label) }, leadingIcon = { Icon(v.icon, null) }, onClick = { viewOpen = false; onView(v) },
                trailingIcon = { if (v == view) Icon(Icons.Rounded.Check, null) }) }
        }
    }
}

/** Rows layout home: «Продолжить», then every shelf as a row of covers that scrolls sideways. */
@Composable
private fun RowsHome(
    current: BookEntry?, books: List<BookEntry>, shelves: List<String>, wishes: Int,
    onOpenBook: (BookEntry) -> Unit, onLongBook: (BookEntry) -> Unit, onShow: (Pick) -> Unit,
) {
    val reading = books.filter { it.started && !it.finished }.sortedByDescending { it.lastOpenedAt }
    val rows = buildList<Triple<String, Pick, List<BookEntry>>> {
        if (reading.isNotEmpty()) add(Triple("Читаю сейчас", Pick.F(Filter.READING), reading))
        shelves.forEach { sh -> books.filter { sh in it.shelves }.takeIf { it.isNotEmpty() }?.let { add(Triple(sh, Pick.S(sh), groupSeries(it))) } }
        books.filter { !it.started }.sortedByDescending { it.addedAt }.takeIf { it.isNotEmpty() }?.let { add(Triple("Новые", Pick.F(Filter.NEW), it)) }
        books.filter { it.finished }.sortedByDescending { it.finishedAt }.takeIf { it.isNotEmpty() }?.let { add(Triple("Прочитанные", Pick.F(Filter.DONE), it)) }
    }
    LazyColumn(contentPadding = PaddingValues(bottom = 104.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        current?.let { c -> item { Box(Modifier.padding(horizontal = 16.dp)) { ContinueCard(c) { onOpenBook(c) } } } }
        items(rows, key = { it.first }) { (title, p, list) ->
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(Modifier.fillMaxWidth().clickable { onShow(p) }.padding(start = 20.dp, end = 8.dp, top = 4.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    Text("${list.size}", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, "Открыть полку", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(list.take(20), key = { it.id }) { b ->
                        Column(Modifier.width(96.dp).combinedClickable(onClick = { onOpenBook(b) }, onLongClick = { onLongBook(b) })) {
                            BookCover(b, Modifier.width(96.dp).height(140.dp), corner = 10.dp, titleSize = 10)
                            Text(b.title, style = MaterialTheme.typography.labelMedium, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp))
                            if (b.started && !b.finished) LinearProgressIndicator(progress = { b.progress }, Modifier.fillMaxWidth().padding(top = 3.dp), drawStopIndicator = {})
                        }
                    }
                }
            }
        }
        if (wishes > 0) item {
            Row(Modifier.fillMaxWidth().clickable { onShow(Pick.F(Filter.WISH)) }.padding(horizontal = 20.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.BookmarkBorder, null, tint = MaterialTheme.colorScheme.primary)
                Text("Хочу прочитать", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 10.dp).weight(1f))
                Text("$wishes", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        item { TextButton(onClick = { onShow(Pick.F(Filter.ALL)) }, modifier = Modifier.padding(horizontal = 12.dp)) { Text("Все книги · ${books.size}") } }
    }
}

/** «Показать»: books, shelves, sort and view in one sheet (rows layout). */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun ShowSheet(
    pick: Pick, home: Boolean, sort: Sort, view: View, shelves: List<String>, visible: List<BookEntry>, wishes: Int, hasHidden: Boolean,
    onPick: (Pick) -> Unit, onHome: () -> Unit, onSort: (Sort) -> Unit, onView: (View) -> Unit, onNewShelf: () -> Unit, onManage: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(horizontal = 20.dp).navigationBarsPadding().padding(bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("Показать", style = MaterialTheme.typography.headlineSmall)
            @Composable fun sec(t: String, content: @Composable () -> Unit) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { Text(t, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant); content() }
            }
            sec("Книги") {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Filter.entries.filter { it != Filter.WISH || wishes > 0 }.forEach { f ->
                        FilterChip(selected = !home && pick == Pick.F(f), onClick = { onPick(Pick.F(f)); onDismiss() }, label = { Text(f.label) })
                    }
                    if (hasHidden) FilterChip(selected = !home && pick == Pick.Hidden, onClick = { onPick(Pick.Hidden); onDismiss() }, label = { Text("Скрытые") })
                }
            }
            sec("Полки") {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    shelves.forEach { sh ->
                        FilterChip(selected = !home && pick == Pick.S(sh), onClick = { onPick(Pick.S(sh)); onDismiss() },
                            label = { Text("$sh · ${visible.count { sh in it.shelves }}") }, leadingIcon = { Icon(Icons.AutoMirrored.Rounded.Label, null, Modifier.size(16.dp)) })
                    }
                    AssistChip(onClick = { onDismiss(); onNewShelf() }, label = { Text("Новая полка") }, leadingIcon = { Icon(Icons.Rounded.Add, null, Modifier.size(16.dp)) })
                    if (shelves.isNotEmpty()) AssistChip(onClick = { onDismiss(); onManage() }, label = { Text("Изменить полки") }, leadingIcon = { Icon(Icons.Rounded.Edit, null, Modifier.size(16.dp)) })
                }
            }
            sec("Сортировка") {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Sort.entries.forEach { s -> FilterChip(selected = sort == s, onClick = { onSort(s) }, label = { Text(s.label) }) }
                }
            }
            sec("Вид") {
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    val opts = listOf<Pair<String, View?>>("Полки рядами" to null, "Сетка" to View.GRID, "Список" to View.LIST)
                    opts.forEachIndexed { i, (label, v) ->
                        SegmentedButton(selected = if (v == null) home else (!home && view == v), onClick = { if (v == null) onHome() else onView(v); onDismiss() },
                            shape = SegmentedButtonDefaults.itemShape(i, opts.size), label = { Text(label, maxLines = 1) })
                    }
                }
            }
        }
    }
}

/** Choose (or create) a shelf for several selected books. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PickShelfSheet(count: Int, shelves: List<String>, onPick: (String) -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf("") }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.navigationBarsPadding().padding(bottom = 12.dp)) {
            Text("${booksWord(count)} на полку", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 24.dp))
            OutlinedTextField(name, { name = it }, singleLine = true, placeholder = { Text("Новая полка") },
                leadingIcon = { Icon(Icons.Rounded.Add, null) },
                trailingIcon = { TextButton(onClick = { onPick(name.trim()) }, enabled = name.isNotBlank()) { Text("Создать") } },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp))
            LazyColumn(Modifier.heightIn(max = 400.dp)) {
                items(shelves, key = { it }) { sh ->
                    ListItem({ Text(sh) }, Modifier.clickable { onPick(sh) }, leadingContent = { Icon(Icons.AutoMirrored.Rounded.Label, null) })
                }
            }
        }
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
