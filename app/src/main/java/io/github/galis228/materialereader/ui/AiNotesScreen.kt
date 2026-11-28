package io.github.galis228.materialereader.ui

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import kotlinx.coroutines.launch
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.galis228.materialereader.ai.AiHistory
import io.github.galis228.materialereader.ai.AiNote
import io.github.galis228.materialereader.data.Library

/** Sections of a book's AI page, by result kind. */
internal enum class AiSection(val title: String, val icon: ImageVector, val kinds: List<String>) {
    PEOPLE("Персонажи", Icons.Rounded.Groups, listOf("Карта персонажей", "Кто это?")),
    TIME("Хронология", Icons.Rounded.Timeline, listOf("Хронология")),
    RECAPS("Пересказы", Icons.Rounded.History, listOf("Где я остановился?", "Пересказ главы")),
    PAGES("Разборы страниц", Icons.Rounded.Psychology, listOf("Объяснить страницу", "Упростить текст", "Отсылки на странице")),
    WORDS("Глоссарий и карточки", Icons.Rounded.Style, listOf("Глоссарий", "Конспект в карточки")),
    ME("Обо мне", Icons.Rounded.Person, listOf("Читательский профиль", "Цитаты по темам")),
    QA("Вопросы и ответы", Icons.Rounded.QuestionAnswer, emptyList());

    companion object {
        fun of(kind: String) = entries.firstOrNull { kind in it.kinds } ?: QA
    }
}

private val dateFmt = java.text.SimpleDateFormat("d MMM, HH:mm", java.util.Locale("ru"))

/** All AI results: books as covers → a book's page by sections → one result. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiNotesScreen(onBack: () -> Unit, onOpenAt: (String, Int) -> Unit = { _, _ -> }) {
    val ctx = LocalContext.current
    val all by remember { AiHistory.get(ctx) }.all.collectAsState()
    val books by remember { Library.get(ctx) }.books.collectAsState()
    var bookId by rememberSaveable { mutableStateOf<String?>(null) }
    var noteId by rememberSaveable { mutableStateOf<Long?>(null) }
    val note = all.firstOrNull { it.id == noteId }
    BackHandler(bookId != null || noteId != null) { if (noteId != null) noteId = null else bookId = null }
    val title = when { note != null -> note.kind; bookId != null -> all.firstOrNull { it.bookId == bookId }?.bookTitle ?: ""; else -> "Результаты ИИ" }
    Scaffold(topBar = {
        TopAppBar(
            title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            navigationIcon = {
                IconButton(onClick = { when { noteId != null -> noteId = null; bookId != null -> bookId = null; else -> onBack() } }) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Назад")
                }
            },
            actions = {
                if (note != null) NoteActions(note, onDelete = { noteId = null })
                else if (bookId == null) {
                    var menu by remember { mutableStateOf(false) }
                    var busy by remember { mutableStateOf(false) }
                    val scope = rememberCoroutineScope()
                    if (busy) CircularProgressIndicator(Modifier.size(22.dp).padding(end = 4.dp), strokeWidth = 2.dp)
                    IconButton(onClick = { menu = true }, enabled = !busy) { Icon(Icons.Rounded.AutoAwesome, "Обо мне") }
                    DropdownMenu(menu, { menu = false }) {
                        DropdownMenuItem(text = { Text("Мой читательский профиль") }, leadingIcon = { Icon(Icons.Rounded.Person, null) }, onClick = {
                            menu = false; busy = true
                            scope.launch { runCatching { io.github.galis228.materialereader.ai.LibraryAi.profile(ctx) }; busy = false; bookId = io.github.galis228.materialereader.ai.LibraryAi.ME }
                        })
                        DropdownMenuItem(text = { Text("Мои цитаты по темам") }, leadingIcon = { Icon(Icons.Rounded.FormatQuote, null) }, onClick = {
                            menu = false; busy = true
                            scope.launch { runCatching { io.github.galis228.materialereader.ai.LibraryAi.quotesByTopic(ctx) }; busy = false; bookId = io.github.galis228.materialereader.ai.LibraryAi.ME }
                        })
                    }
                }
            },
        )
    }) { pad ->
        AnimatedContent(Triple(bookId, note?.id, 0), Modifier.padding(pad).fillMaxSize(), label = "ai") { (b, n, _) ->
            val open = all.firstOrNull { it.id == n }
            when {
                open != null -> NoteView(open, onGo = { onOpenAt(open.bookId, open.block) })
                b != null -> BookPage(all.filter { it.bookId == b }, books.firstOrNull { it.id == b }) { noteId = it }
                else -> BooksGrid(all, books) { bookId = it }
            }
        }
    }
}

@Composable
private fun BooksGrid(all: List<AiNote>, books: List<io.github.galis228.materialereader.data.BookEntry>, onBook: (String) -> Unit) {
    val groups = all.groupBy { it.bookId }.toList().sortedByDescending { (_, n) -> n.maxOf { it.createdAt } }
    if (groups.isEmpty()) {
        Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
            Text("Здесь будут карты персонажей, хронологии, пересказы и ответы ИИ — по каждой книге.",
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }
    LazyVerticalGrid(GridCells.Adaptive(108.dp), contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        items(groups, key = { it.first }) { (id, notes) ->
            val entry = books.firstOrNull { it.id == id }
            Column(Modifier.clickableNoRipple { onBook(id) }) {
                Box {
                    if (entry != null) BookCover(entry, Modifier.fillMaxWidth().aspectRatio(0.68f))
                    else Surface(Modifier.fillMaxWidth().aspectRatio(0.68f), shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceContainerHighest) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(if (id == io.github.galis228.materialereader.ai.LibraryAi.ME) Icons.Rounded.Person else Icons.AutoMirrored.Rounded.MenuBook, null, Modifier.size(40.dp))
                        }
                    }
                    Badge(Modifier.align(Alignment.TopEnd).padding(6.dp), containerColor = MaterialTheme.colorScheme.primary) { Text("${notes.size}") }
                }
                Spacer(Modifier.height(6.dp))
                Text(notes.first().bookTitle, style = MaterialTheme.typography.labelLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    notes.map { AiSection.of(it.kind) }.distinct().sortedBy { it.ordinal }.take(5).forEach {
                        Icon(it.icon, it.title, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
private fun BookPage(notes: List<AiNote>, entry: io.github.galis228.materialereader.data.BookEntry?, onNote: (Long) -> Unit) {
    val bySection = notes.groupBy { AiSection.of(it.kind) }.toSortedMap(compareBy { it.ordinal })
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (entry != null) BookCover(entry, Modifier.width(64.dp).height(94.dp), corner = 8.dp, titleSize = 8)
                Spacer(Modifier.width(14.dp))
                Column {
                    Text(entry?.title ?: notes.first().bookTitle, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    if (entry != null) Text(entry.author, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("Результатов: ${notes.size}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                }
            }
        }
        val pinned = notes.filter { it.pinned }
        if (pinned.isNotEmpty()) item { SectionCard("Закреплённое", Icons.Rounded.PushPin, pinned, onNote) }
        bySection.forEach { (s, list) -> item(key = s.name) { SectionCard(s.title, s.icon, list, onNote) } }
    }
}

@Composable
private fun SectionCard(title: String, icon: ImageVector, notes: List<AiNote>, onNote: (Long) -> Unit) {
    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(vertical = 8.dp)) {
            Row(Modifier.padding(horizontal = 16.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Surface(shape = RoundedCornerShape(10.dp), color = MaterialTheme.colorScheme.secondaryContainer) {
                    Icon(icon, null, Modifier.padding(6.dp).size(20.dp), tint = MaterialTheme.colorScheme.onSecondaryContainer)
                }
                Spacer(Modifier.width(10.dp))
                Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                Text("${notes.size}", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            notes.sortedByDescending { it.createdAt }.take(6).forEach { n ->
                ListItem(
                    headlineContent = { Text(n.query.ifEmpty { n.kind }, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    supportingContent = { Text(preview(n.answer), maxLines = 2, overflow = TextOverflow.Ellipsis) },
                    overlineContent = { Text(listOfNotNull(n.chapter.takeIf { it.isNotEmpty() }, dateFmt.format(java.util.Date(n.createdAt))).joinToString(" · "), maxLines = 1) },
                    trailingContent = if (n.pinned) ({ Icon(Icons.Rounded.PushPin, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary) }) else null,
                    colors = ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent),
                    modifier = Modifier.clickableNoRipple { onNote(n.id) },
                )
            }
            if (notes.size > 6) Text("и ещё ${notes.size - 6}", Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Plain-text preview of a Markdown answer, without the diagram blocks. */
internal fun preview(md: String): String = md.replace(Regex("```[\\s\\S]*?```"), " ")
    .replace(Regex("[#*_>|`-]+"), " ").replace(Regex("\\s+"), " ").trim().take(180)

@Composable
private fun NoteView(n: AiNote, onGo: () -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(listOfNotNull(n.query.takeIf { it.isNotEmpty() }, n.chapter.takeIf { it.isNotEmpty() }, dateFmt.format(java.util.Date(n.createdAt))).joinToString(" · "),
            style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (n.kind == "Карта персонажей") CharacterMap(n.answer) else RichAnswer(n.answer)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (n.bookId != io.github.galis228.materialereader.ai.LibraryAi.ME) FilledTonalButton(onClick = onGo) { Icon(Icons.AutoMirrored.Rounded.MenuBook, null); Text("  К месту в книге") }
            if (n.kind in listOf("Карта персонажей", "Хронология", "Глоссарий", "Где я остановился?"))
                OutlinedButton(onClick = onGo) { Icon(Icons.Rounded.Refresh, null); Text("  Обновить") }
        }
        if (n.kind in listOf("Карта персонажей", "Хронология", "Глоссарий", "Где я остановился?"))
            Text("«Обновить» открывает книгу там, где ты сейчас, — запусти то же действие в ИИ-помощнике, и результат заменит этот.",
                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun NoteActions(n: AiNote, onDelete: () -> Unit) {
    val ctx = LocalContext.current
    val history = remember { AiHistory.get(ctx) }
    IconButton(onClick = { history.togglePin(n.id) }) {
        Icon(if (n.pinned) Icons.Rounded.PushPin else Icons.Outlined.PushPin, if (n.pinned) "Открепить" else "Закрепить",
            tint = if (n.pinned) MaterialTheme.colorScheme.primary else LocalContentColor.current)
    }
    IconButton(onClick = {
        ctx.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain")
            .putExtra(Intent.EXTRA_TEXT, "${n.bookTitle} — ${n.kind}\n\n${n.answer}"), null))
    }) { Icon(Icons.Rounded.Share, "Поделиться") }
    IconButton(onClick = { history.remove(n.id); onDelete() }) { Icon(Icons.Rounded.Delete, "Удалить") }
}

@Composable
internal fun Modifier.clickableNoRipple(onClick: () -> Unit): Modifier =
    this.then(Modifier.clickable(onClick = onClick))

/** The same results for one book, from its menu in the library. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookAiNotesSheet(bookId: String, title: String, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val all by remember { AiHistory.get(ctx) }.all.collectAsState()
    val books by remember { Library.get(ctx) }.books.collectAsState()
    var noteId by remember { mutableStateOf<Long?>(null) }
    val note = all.firstOrNull { it.id == noteId }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.navigationBarsPadding().padding(bottom = 12.dp)) {
            Row(Modifier.padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                if (note != null) IconButton(onClick = { noteId = null }) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Назад") }
                Text(note?.kind ?: "Результаты ИИ", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 8.dp).weight(1f))
                if (note != null) NoteActions(note) { noteId = null }
            }
            if (note != null) NoteView(note) {}
            else BookPage(all.filter { it.bookId == bookId }.ifEmpty { return@Column Text("Пока нет результатов ИИ для «$title».", Modifier.padding(16.dp)) },
                books.firstOrNull { it.id == bookId }) { noteId = it }
        }
    }
}
