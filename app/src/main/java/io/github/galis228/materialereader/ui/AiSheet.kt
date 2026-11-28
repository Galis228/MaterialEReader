package io.github.galis228.materialereader.ui

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilledIconButton
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.QuestionAnswer
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.Style
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.material.icons.rounded.PersonSearch
import androidx.compose.material.icons.rounded.Timeline
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.automirrored.rounded.Subject
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.automirrored.rounded.ShortText
import androidx.compose.material.icons.rounded.Psychology
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.runtime.collectAsState
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import io.github.galis228.materialereader.ai.Ai
import io.github.galis228.materialereader.ai.BookAi
import io.github.galis228.materialereader.ai.visibleAnswer
import io.github.galis228.materialereader.parse.ParsedBook
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

private enum class AiAction(val label: String, val tile: String, val hint: String, val icon: androidx.compose.ui.graphics.vector.ImageVector) {
    EXPLAIN("Объяснить страницу", "Объяснить", "что здесь происходит", Icons.Rounded.Psychology),
    SIMPLIFY("Упростить текст", "Упростить", "простым языком", Icons.AutoMirrored.Rounded.ShortText),
    REFS("Отсылки на странице", "Отсылки", "имена, цитаты, реалии", Icons.Rounded.Link),
    RECAP("Где я остановился?", "Где я?", "пересказ за 30 секунд", Icons.Rounded.History),
    CHAPTER("Пересказ главы", "Глава", "пересказ прочитанного", Icons.AutoMirrored.Rounded.Subject),
    CHARACTERS("Карта персонажей", "Персонажи", "кто есть кто и связи", Icons.Rounded.Groups),
    TIMELINE("Хронология", "Хронология", "лента событий", Icons.Rounded.Timeline),
    WHO("Кто это?", "Кто это?", "персонаж, место, вещь", Icons.Rounded.PersonSearch),
    GLOSSARY("Глоссарий", "Глоссарий", "термины книги", Icons.Rounded.Translate),
    CARDS("Конспект в карточки", "В карточки", "главные мысли", Icons.Rounded.Style),
    JOURNAL("Вопрос для дневника", "Дневник", "вопрос по главе", Icons.Rounded.EditNote),
    ASK("Вопрос", "Вопрос", "о прочитанном", Icons.Rounded.QuestionAnswer),
}

/** Tiles in three groups, as in the approved concept. */
private val tileGroups = listOf(
    "Эта страница" to listOf(AiAction.EXPLAIN, AiAction.SIMPLIFY, AiAction.REFS),
    "Книга до этого места" to listOf(AiAction.RECAP, AiAction.CHAPTER, AiAction.CHARACTERS, AiAction.TIMELINE, AiAction.WHO, AiAction.GLOSSARY),
    "Для себя" to listOf(AiAction.CARDS, AiAction.JOURNAL),
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AiSheet(
    book: ParsedBook, block: Int, chapterStart: Int, pageText: () -> String, onOpenSettings: () -> Unit, preset: String? = null,
    onJournal: (question: String, answer: String) -> Unit = { _, _ -> }, bookId: String = "", chapterTitle: String = "",
    onDismiss: () -> Unit,
) {
    val ctx = LocalContext.current
    val ai = remember { Ai.get(ctx) }
    val level = remember { io.github.galis228.materialereader.data.SettingsRepo(ctx) }.settings.collectAsState(initial = null).value?.aiLevel ?: 1
    val helper = remember(book, level) { BookAi(ai, book, level) }
    val scope = rememberCoroutineScope()
    var label by remember { mutableStateOf("…") }
    var online by remember { mutableStateOf(false) }
    var action by remember { mutableStateOf<AiAction?>(null) }
    var input by remember { mutableStateOf("") }
    var answer by remember { mutableStateOf("") }
    var status by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var job by remember { mutableStateOf<Job?>(null) }
    var journalAnswer by remember { mutableStateOf("") }
    var journalSaved by remember { mutableStateOf(false) }
    val history = remember { io.github.galis228.materialereader.ai.AiHistory.get(ctx) }
    val saved = history.all.collectAsState().value.filter { it.bookId == bookId }
    var showSaved by remember { mutableStateOf(false) }
    val busy = job?.isActive == true
    val maxH = (LocalConfiguration.current.screenHeightDp * 0.8f).dp

    LaunchedEffect(Unit) { label = ai.label(); online = ai.online() }
    DisposableEffect(Unit) { onDispose { job?.cancel(); ai.cancel() } }

    fun run(a: AiAction) {
        if ((a == AiAction.WHO || a == AiAction.ASK) && input.isBlank()) { action = a; return }
        job?.cancel(); ai.cancel()
        action = a; answer = ""; error = null; journalSaved = false; journalAnswer = ""
        status = "Думаю…"
        job = scope.launch {
            runCatching {
                val flow: Flow<String> = when (a) {
                    AiAction.EXPLAIN -> helper.explain(pageText(), single = preset != null)
                    AiAction.RECAP -> helper.recap(block)
                    AiAction.CHAPTER -> helper.chapterSummary(chapterStart, block) { i, n -> status = if (n > 0) "Читаю главу: часть $i из $n…" else "Пишу пересказ…" }
                    AiAction.WHO -> helper.whoIs(input.trim(), block)
                    AiAction.ASK -> helper.ask(input.trim(), block)
                    AiAction.SIMPLIFY -> helper.simplify(pageText())
                    AiAction.CHARACTERS -> helper.characters(block) { i, n -> status = if (n > 0) "Перечитываю книгу: часть $i из $n…" else "Рисую связи…" }
                    AiAction.TIMELINE -> helper.timeline(block) { i, n -> status = if (n > 0) "Перечитываю книгу: часть $i из $n…" else "Выстраиваю события…" }
                    AiAction.GLOSSARY -> helper.glossary(block) { i, n -> status = if (n > 0) "Перечитываю книгу: часть $i из $n…" else "Собираю термины…" }
                    AiAction.REFS -> helper.references(pageText())
                    AiAction.JOURNAL -> helper.journalQuestion(chapterStart, block)
                    AiAction.CARDS -> {
                        status = "Выделяю главные мысли…"
                        val points = BookAi.parsePoints(helper.keyPoints(chapterStart, block))
                        val words = io.github.galis228.materialereader.data.Words.get(ctx)
                        val now = System.currentTimeMillis()
                        points.forEachIndexed { i, (idea, why) ->
                            words.add(io.github.galis228.materialereader.data.Word(now + i, idea, "", "", why, book.title, now))
                        }
                        kotlinx.coroutines.flow.flowOf(
                            if (points.isEmpty()) "Не получилось выделить мысли — попробуй ещё раз."
                            else "В карточки добавлено: ${points.size}. Они появятся в разделе «Слова» для повторения.\n\n" +
                                points.joinToString("\n\n") { "• ${it.first}\n${it.second}" },
                        )
                    }
                }
                flow.collect { answer = visibleAnswer(it); if (answer.isNotEmpty()) status = null }
                // plain lines from the model become graphs, timelines and tables here
                answer = when (a) {
                    AiAction.CHARACTERS -> helper.charactersMarkdown(answer, block)
                    AiAction.TIMELINE -> helper.timelineMarkdown(answer)
                    AiAction.GLOSSARY -> helper.termsMarkdown(answer, "Глоссарий", "Термин")
                    AiAction.REFS -> helper.termsMarkdown(answer, "Отсылки", "Отсылка")
                    else -> answer
                }
                if (a != AiAction.JOURNAL && bookId.isNotEmpty()) history.add(io.github.galis228.materialereader.ai.AiNote(
                    System.currentTimeMillis(), bookId, book.title, a.label,
                    if (a == AiAction.WHO || a == AiAction.ASK) input.trim() else "", answer, block, chapterTitle, System.currentTimeMillis()))
            }.onFailure { if (it !is kotlinx.coroutines.CancellationException) error = it.message ?: "Ошибка модели" }
            status = null
        }
    }

    LaunchedEffect(preset) { if (preset != null) run(AiAction.EXPLAIN) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            Modifier.fillMaxWidth().heightIn(max = maxH).padding(horizontal = 20.dp).navigationBarsPadding().padding(bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.AutoAwesome, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text("ИИ-помощник", style = MaterialTheme.typography.titleMedium)
                    Text("$label · ${if (online) "через интернет" else "офлайн"} · без спойлеров", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (saved.isNotEmpty()) androidx.compose.material3.FilledTonalButton(onClick = { showSaved = !showSaved }) {
                    Text(if (showSaved) "Назад" else "Сохранённое · ${saved.size}")
                }
                IconButton(onClick = onOpenSettings) { Icon(Icons.Rounded.Settings, "Настройки ИИ") }
            }
            if (showSaved) {
                AiNotesList(saved, Modifier.weight(1f, fill = false))
                return@Column
            }
            if (action == null) {
                // the start: action tiles in groups, and a question field at the bottom
                Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    tileGroups.forEach { (title, acts) ->
                        Text(title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        acts.chunked(3).forEach { row ->
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                row.forEach { a -> AiTile(a, Modifier.weight(1f)) { run(a) } }
                                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                            }
                        }
                    }
                }
            } else Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { job?.cancel(); ai.cancel(); action = null; answer = ""; status = null; error = null }) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Все действия")
                }
                Icon(action!!.icon, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Text(action!!.label, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            }
            if (action == null || action == AiAction.WHO || action == AiAction.ASK) {
                val target = if (action == AiAction.WHO) AiAction.WHO else AiAction.ASK
                OutlinedTextField(
                    value = input, onValueChange = { input = it }, singleLine = true,
                    placeholder = { Text(if (target == AiAction.WHO) "Имя персонажа, место, предмет" else "Спросить о книге…") },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = { if (input.isNotBlank()) run(target) }),
                    trailingIcon = { FilledIconButton(onClick = { run(target) }, enabled = input.isNotBlank() && !busy) { Icon(Icons.AutoMirrored.Rounded.Send, "Спросить") } },
                    shape = RoundedCornerShape(28.dp),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (action != null)             Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) {
                status?.let {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(10.dp))
                        Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                error?.let { msg ->
                    Text(msg, color = MaterialTheme.colorScheme.error)
                    TextButton(onClick = onOpenSettings) { Text("Открыть настройки ИИ") }
                }
                if (answer.isNotEmpty()) { if (action == AiAction.CHARACTERS && !busy) CharacterMap(answer) else RichAnswer(answer) }
                if (answer.isNotEmpty() && !busy && action != AiAction.JOURNAL && bookId.isNotEmpty())
                    Text("Сохранено — открыть позже можно в «Сохранённом»", style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp))
                if (action == AiAction.JOURNAL && answer.isNotEmpty() && !busy) {
                    Spacer(Modifier.height(8.dp))
                    if (journalSaved) Text("Сохранено в дневник — он в «Главы» → «Цитаты»", color = MaterialTheme.colorScheme.primary)
                    else {
                        OutlinedTextField(journalAnswer, { journalAnswer = it }, placeholder = { Text("Твой ответ") }, modifier = Modifier.fillMaxWidth(), minLines = 2)
                        TextButton(onClick = { onJournal(answer.trim(), journalAnswer.trim()); journalSaved = true }, enabled = journalAnswer.isNotBlank()) {
                            Text("Сохранить в дневник")
                        }
                    }
                }
            }
            if (busy) TextButton(onClick = { job?.cancel(); ai.cancel() }, modifier = Modifier.align(Alignment.End)) {
                Icon(Icons.Rounded.Stop, null); Text(" Остановить", fontWeight = FontWeight.Medium)
            }
        }
    }
}

@Composable
private fun AiTile(a: AiAction, modifier: Modifier, onClick: () -> Unit) {
    androidx.compose.material3.Surface(onClick = onClick, modifier = modifier, shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHighest) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Icon(a.icon, null, tint = MaterialTheme.colorScheme.primary)
            Text(a.tile, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, maxLines = 1)
            Text(a.hint, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, minLines = 2)
        }
    }
}

/** Saved AI results: newest first, pinned on top; tap to open, with pin and delete. */
@Composable
fun AiNotesList(notes: List<io.github.galis228.materialereader.ai.AiNote>, modifier: Modifier = Modifier, showBook: Boolean = false) {
    val ctx = LocalContext.current
    val history = remember { io.github.galis228.materialereader.ai.AiHistory.get(ctx) }
    var open by remember { mutableStateOf<Long?>(null) }
    val sorted = notes.sortedWith(compareByDescending<io.github.galis228.materialereader.ai.AiNote> { it.pinned }.thenByDescending { it.createdAt })
    val fmt = remember { java.text.SimpleDateFormat("d MMM, HH:mm", java.util.Locale("ru")) }
    androidx.compose.foundation.lazy.LazyColumn(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (sorted.isEmpty()) item { Text("Здесь будут результаты ИИ: карты персонажей, хронологии, пересказы и ответы.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        items(sorted.size, key = { sorted[it].id }) { i ->
            val n = sorted[i]
            androidx.compose.material3.OutlinedCard(onClick = { open = if (open == n.id) null else n.id }, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(n.kind + if (n.query.isNotEmpty()) ": ${n.query}" else "", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                            Text(listOfNotNull(if (showBook) n.bookTitle else null, n.chapter.takeIf { it.isNotEmpty() }, fmt.format(java.util.Date(n.createdAt))).joinToString(" · "),
                                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                        }
                        IconButton(onClick = { history.togglePin(n.id) }) {
                            Icon(if (n.pinned) Icons.Rounded.PushPin else Icons.Outlined.PushPin, if (n.pinned) "Открепить" else "Закрепить",
                                tint = if (n.pinned) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        IconButton(onClick = { history.remove(n.id) }) { Icon(Icons.Rounded.Delete, "Удалить", tint = MaterialTheme.colorScheme.onSurfaceVariant) }
                    }
                    if (open == n.id) { Spacer(Modifier.height(8.dp)); RichAnswer(n.answer) }
                    else Text(n.answer.lines().firstOrNull { it.isNotBlank() && !it.startsWith("#") && !it.startsWith("```") && !it.startsWith("|") }.orEmpty()
                        .replace("**", ""), maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp))
                }
            }
        }
    }
}
