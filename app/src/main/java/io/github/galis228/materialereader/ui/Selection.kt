package io.github.galis228.materialereader.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Notes
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.MenuBook
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import io.github.galis228.materialereader.ai.Ai
import io.github.galis228.materialereader.ai.visibleAnswer
import io.github.galis228.materialereader.data.Highlight
import io.github.galis228.materialereader.data.Translate
import io.github.galis228.materialereader.data.Word
import io.github.galis228.materialereader.data.Words
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** Word boundaries around [at] in [text] (letters, digits, apostrophes and inner hyphens). */
fun wordAt(text: String, at: Int): IntRange {
    if (text.isEmpty()) return 0..0
    fun inWord(i: Int) = i in text.indices && (text[i].isLetterOrDigit() || text[i] in "'’ʼ" ||
        (text[i] == '-' && i > 0 && i < text.lastIndex && text[i - 1].isLetter() && text[i + 1].isLetter()))
    var a = at.coerceIn(0, text.lastIndex)
    if (!inWord(a) && inWord(a - 1)) a--
    if (!inWord(a)) return a until a + 1
    var b = a
    while (inWord(a - 1)) a--
    while (inWord(b + 1)) b++
    return a until b + 1
}

/** The sentence around [range] — saved with a word as its context. */
fun sentenceAround(text: String, range: IntRange): String {
    val enders = ".!?…"
    var a = range.first; while (a > 0 && text[a - 1] !in enders) a--
    var b = range.last; while (b < text.lastIndex && text[b] !in enders) b++
    while (b < text.lastIndex && text[b + 1] in "»\"”’)]") b++ // closing quotes belong to the sentence
    return text.substring(a, (b + 1).coerceAtMost(text.length)).trim()
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SelectionSheet(
    blockText: String, blockIndex: Int, initial: IntRange, bookTitle: String,
    highlights: List<Highlight>, onPut: (Highlight) -> Unit, onRemove: (Long) -> Unit,
    onAskAi: (String) -> Unit, bookId: String = "", onDismiss: () -> Unit,
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var tf by remember { mutableStateOf(TextFieldValue(blockText, TextRange(initial.first, initial.last + 1))) }
    val sel = tf.selection.let { if (it.collapsed) TextRange(initial.first, initial.last + 1) else TextRange(it.min, it.max) }
    val selected = blockText.substring(sel.start.coerceIn(0, blockText.length), sel.end.coerceIn(0, blockText.length)).trim()
    val overlapping = highlights.filter { it.whole || (it.start < sel.end && sel.start < it.end) }
    var note by remember { mutableStateOf(overlapping.firstOrNull()?.note.orEmpty()) }
    var showNote by remember { mutableStateOf(note.isNotEmpty()) }
    var translation by remember { mutableStateOf<String?>(null) }
    var meaning by remember { mutableStateOf<String?>(null) }
    var status by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var job by remember { mutableStateOf<Job?>(null) }
    var lang by remember { mutableStateOf<String?>(null) }
    /** Chosen by hand when the automatic guess was wrong. */
    var forced by remember { mutableStateOf<String?>(null) }
    val focus = remember { FocusRequester() }
    val maxText = (LocalConfiguration.current.screenHeightDp * 0.22f).dp
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    DisposableEffect(Unit) { onDispose { job?.cancel() } }

    fun highlight(color: Int) {
        overlapping.forEach { onRemove(it.createdAt) }
        onPut(Highlight(blockIndex, color, note.trim(), System.currentTimeMillis(), sel.start, sel.end))
    }
    fun translate() {
        job?.cancel(); error = null; translation = null
        job = scope.launch {
            runCatching {
                val src = (forced ?: Translate.detect(selected)).also { lang = it }
                status = if (Translate.needsDownload(ctx, src)) "Скачиваю языковой пакет (около 30 МБ, один раз)…" else "Перевожу…"
                val r = Translate.translate(ctx, selected, src)
                translation = r.text
                status = null
            }.onFailure { status = null; error = "Перевод не получился: ${it.message}" }
        }
    }
    fun explain() {
        job?.cancel(); error = null; meaning = ""
        job = scope.launch {
            status = "Спрашиваю ИИ…"
            runCatching {
                Ai.get(ctx).stream(
                    "Ты — толковый словарь для читателя. Отвечай коротко, на языке вопроса.",
                    "Объясни значение «$selected» в этом предложении: «${sentenceAround(blockText, sel.start until sel.end)}». " +
                        "Одно-два предложения, затем 2–3 синонима.", 0.2f, 300,
                ).collect { meaning = visibleAnswer(it); status = null }
                if (bookId.isNotEmpty()) io.github.galis228.materialereader.ai.AiHistory.get(ctx).add(io.github.galis228.materialereader.ai.AiNote(
                    System.currentTimeMillis(), bookId, bookTitle, "Значение слова", selected, meaning.orEmpty(), blockIndex, "", System.currentTimeMillis()))
            }.onFailure { status = null; meaning = null; error = it.message ?: "ИИ не ответил" }
        }
    }

    ModalBottomSheet(onDismissRequest = {
        // a changed note is kept with the highlight it belongs to
        overlapping.firstOrNull()?.let { h -> if (h.note != note.trim()) onPut(h.copy(note = note.trim())) }
        onDismiss()
    }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), scrimColor = Color.Transparent) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp).navigationBarsPadding().padding(bottom = 12.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("Потяни за края выделения, чтобы взять больше слов", style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            BasicTextField(
                value = tf, onValueChange = { tf = it.copy(text = blockText) }, readOnly = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                cursorBrush = SolidColor(Color.Transparent),
                modifier = Modifier.fillMaxWidth().heightIn(max = maxText).focusRequester(focus)
                    .clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh).padding(12.dp),
            )
            Text("«$selected»", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 2)

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                HighlightColors.forEachIndexed { i, c ->
                    val on = overlapping.any { it.color == i }
                    Box(Modifier.size(36.dp).clip(CircleShape).background(c)
                        .border(if (on) 3.dp else 0.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                        .clickable { highlight(i) })
                }
                if (overlapping.isNotEmpty()) TextButton(onClick = { overlapping.forEach { onRemove(it.createdAt) } }) {
                    Icon(Icons.Rounded.Block, null); Text(" Снять")
                }
            }

            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AssistChip(onClick = { translate() }, label = { Text("Перевести") }, leadingIcon = { Icon(Icons.Rounded.Translate, null, Modifier.size(18.dp)) })
                AssistChip(onClick = { explain() }, label = { Text("Значение") }, leadingIcon = { Icon(Icons.Rounded.MenuBook, null, Modifier.size(18.dp)) })
                AssistChip(onClick = {
                    scope.launch {
                        val l = lang ?: Translate.detect(selected)
                        val wiki = if (l == "uk" || l == "ru") l else "en"
                        ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://$wiki.wiktionary.org/wiki/" + Uri.encode(selected.lowercase().replace(' ', '_')))))
                    }
                }, label = { Text("Викисловарь") }, leadingIcon = { Icon(Icons.Rounded.Public, null, Modifier.size(18.dp)) })
                AssistChip(onClick = {
                    scope.launch {
                        Words.get(ctx).add(Word(System.currentTimeMillis(), selected, sentenceAround(blockText, sel.start until sel.end),
                            translation.orEmpty(), meaning.orEmpty(), bookTitle, System.currentTimeMillis()))
                        android.widget.Toast.makeText(ctx, "«$selected» в карточках", android.widget.Toast.LENGTH_SHORT).show()
                    }
                }, label = { Text("В карточки") }, leadingIcon = { Icon(Icons.Rounded.School, null, Modifier.size(18.dp)) })
                AssistChip(onClick = { showNote = !showNote }, label = { Text("Заметка") }, leadingIcon = { Icon(Icons.AutoMirrored.Rounded.Notes, null, Modifier.size(18.dp)) })
                AssistChip(onClick = { copy(ctx, selected) }, label = { Text("Копировать") }, leadingIcon = { Icon(Icons.Rounded.ContentCopy, null, Modifier.size(18.dp)) })
                AssistChip(onClick = { share(ctx, selected) }, label = { Text("Поделиться") }, leadingIcon = { Icon(Icons.Rounded.Share, null, Modifier.size(18.dp)) })
                AssistChip(onClick = { onAskAi(selected) }, label = { Text("Спросить ИИ") }, leadingIcon = { Icon(Icons.Rounded.AutoAwesome, null, Modifier.size(18.dp)) })
            }

            if (showNote) OutlinedTextField(
                note, { note = it }, placeholder = { Text("Заметка к выделению") }, modifier = Modifier.fillMaxWidth(), maxLines = 4,
                supportingText = { Text(if (overlapping.isEmpty()) "Сохранится, когда выберешь цвет выделения" else "Сохранится при закрытии") },
            )

            status?.let {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp); Spacer(Modifier.width(10.dp))
                    Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            translation?.let { t ->
                Column {
                    Text("Перевод" + (lang?.let { " · с языка: ${Translate.name(it)}" + if (forced == null) " (определён сам)" else "" } ?: ""),
                        style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                    Text(t, style = MaterialTheme.typography.bodyLarge)
                    // if the guess was wrong: pick the language and translate again
                    androidx.compose.foundation.lazy.LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 6.dp)) {
                        items(Translate.sources.size) { i ->
                            val l = Translate.sources[i]
                            androidx.compose.material3.FilterChip(selected = lang == l, onClick = { forced = l; translate() },
                                label = { Text(Translate.name(l)) })
                        }
                    }
                }
            }
            // the book's own glossary (made by the AI earlier) answers at once, without asking again
            val fromGlossary = remember(selected, bookId) { glossaryEntry(ctx, bookId, selected) }
            fromGlossary?.let { (term, text) ->
                Text("Из глоссария книги — $term", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                Text(text, style = MaterialTheme.typography.bodyMedium)
            }
            meaning?.takeIf { it.isNotEmpty() }?.let { m ->
                Column {
                    Text("Значение", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                    Text(m, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

/** Looks the selection up in the latest AI glossary of the book: "Термин | Пояснение" table rows. */
fun glossaryEntry(ctx: android.content.Context, bookId: String, word: String): Pair<String, String>? {
    if (bookId.isEmpty() || word.isBlank()) return null
    val note = io.github.galis228.materialereader.ai.AiHistory.get(ctx).latest(bookId, "Глоссарий") ?: return null
    val stem = io.github.galis228.materialereader.ai.BookAi.stem(word.trim().split(' ').first()).lowercase()
    if (stem.length < 3) return null
    val rows = io.github.galis228.materialereader.ai.Rich.parse(note.answer).filterIsInstance<io.github.galis228.materialereader.ai.RichBlock.Table>().flatMap { it.rows }
    return rows.firstOrNull { r -> r.firstOrNull()?.replace("**", "")?.lowercase()?.split(' ', ',')?.any { it.startsWith(stem) } == true }
        ?.let { r -> r[0].replace("**", "") to r.getOrElse(1) { "" } }
}
