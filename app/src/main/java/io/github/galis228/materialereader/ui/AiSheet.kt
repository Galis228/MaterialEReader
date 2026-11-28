package io.github.galis228.materialereader.ui

import androidx.compose.ui.text.drawText
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

private enum class AiAction(val label: String) {
    EXPLAIN("Объяснить страницу"), SIMPLIFY("Упростить текст"), RECAP("Где я остановился?"), CHAPTER("Пересказ главы"),
    CHARACTERS("Карта персонажей"), WHO("Кто это?"), ASK("Вопрос"), CARDS("Конспект в карточки"), JOURNAL("Вопрос для дневника"),
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AiSheet(
    book: ParsedBook, block: Int, chapterStart: Int, pageText: () -> String, onOpenSettings: () -> Unit, preset: String? = null,
    onJournal: (question: String, answer: String) -> Unit = { _, _ -> }, onDismiss: () -> Unit,
) {
    val ctx = LocalContext.current
    val ai = remember { Ai.get(ctx) }
    val helper = remember(book) { BookAi(ai, book) }
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
    val busy = job?.isActive == true
    val maxH = (LocalConfiguration.current.screenHeightDp * 0.55f).dp

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
            }.onFailure { if (it !is kotlinx.coroutines.CancellationException) error = it.message ?: "Ошибка модели" }
            status = null
        }
    }

    LaunchedEffect(preset) { if (preset != null) run(AiAction.EXPLAIN) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        scrimColor = Color.Transparent,
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
                IconButton(onClick = onOpenSettings) { Icon(Icons.Rounded.Settings, "Настройки ИИ") }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AiAction.entries.forEach { a ->
                    AssistChip(onClick = { run(a) }, label = { Text(a.label) }, enabled = !busy)
                }
            }
            if (action == AiAction.WHO || action == AiAction.ASK) {
                OutlinedTextField(
                    value = input, onValueChange = { input = it }, singleLine = true,
                    placeholder = { Text(if (action == AiAction.WHO) "Имя персонажа, место, предмет" else "Спроси о прочитанном") },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = { action?.let { run(it) } }),
                    trailingIcon = { IconButton(onClick = { action?.let { run(it) } }, enabled = input.isNotBlank() && !busy) { Icon(Icons.AutoMirrored.Rounded.Send, "Спросить") } },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) {
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
                if (answer.isNotEmpty() && action == AiAction.CHARACTERS) {
                    val people = remember(answer) { BookAi.parseCharacters(answer) }
                    if (people.size >= 2 && !busy) CharacterGraph(people)
                    SelectionContainer { Text(answer, style = MaterialTheme.typography.bodyMedium) }
                } else if (answer.isNotEmpty()) SelectionContainer { Text(answer, style = MaterialTheme.typography.bodyLarge) }
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

/** People as dots on a circle, related ones joined by lines. */
@Composable
private fun CharacterGraph(people: List<Triple<String, String, List<Pair<String, String>>>>) {
    val measurer = androidx.compose.ui.text.rememberTextMeasurer()
    val cs = MaterialTheme.colorScheme
    val names = people.map { it.first }.take(12)
    fun find(n: String): Int = names.indexOfFirst { it.equals(n, true) || it.split(' ').first().equals(n.split(' ').first(), true) }
    val edges = people.take(12).flatMapIndexed { i, p -> p.third.mapNotNull { (n, _) -> find(n).takeIf { it >= 0 && it != i }?.let { minOf(i, it) to maxOf(i, it) } } }.distinct()
    val label = MaterialTheme.typography.labelSmall.copy(color = cs.onSurface)
    androidx.compose.foundation.Canvas(Modifier.fillMaxWidth().height(260.dp).padding(vertical = 8.dp)) {
        val c = androidx.compose.ui.geometry.Offset(size.width / 2, size.height / 2)
        val r = minOf(size.width, size.height) / 2 - 34.dp.toPx()
        val pts = names.indices.map { i ->
            val a = 2 * Math.PI * i / names.size - Math.PI / 2
            androidx.compose.ui.geometry.Offset(c.x + r * kotlin.math.cos(a).toFloat() * 1.25f, c.y + r * kotlin.math.sin(a).toFloat())
        }
        for ((a, b) in edges) drawLine(cs.primary.copy(alpha = 0.45f), pts[a], pts[b], strokeWidth = 2.dp.toPx())
        names.forEachIndexed { i, n ->
            drawCircle(cs.primary, 7.dp.toPx(), pts[i])
            val t = measurer.measure(n.take(18), label)
            drawText(t, topLeft = androidx.compose.ui.geometry.Offset(
                (pts[i].x - t.size.width / 2).coerceIn(0f, size.width - t.size.width), pts[i].y + 9.dp.toPx()))
        }
    }
}
