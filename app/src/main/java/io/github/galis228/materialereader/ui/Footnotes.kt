package io.github.galis228.materialereader.ui

import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import io.github.galis228.materialereader.ai.Ai
import io.github.galis228.materialereader.ai.visibleAnswer
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import io.github.galis228.materialereader.parse.NoteRef
import io.github.galis228.materialereader.parse.ParsedBook
import io.github.galis228.materialereader.parse.cleanNote

/** A footnote found for a reference: its text and, if it sits in the book's text, which paragraph to jump to. */
data class Footnote(val label: String, val text: String?, val block: Int?)

/**
 * Text of a footnote: from the book's notes by id (FB2, linked EPUB notes), otherwise — for a bare "[3]" —
 * the first paragraph after the reference that starts with that number ("[3] …", "3. …", "3) …").
 */
fun findFootnote(book: ParsedBook, block: Int, ref: NoteRef): Footnote {
    ref.id?.let { id -> book.notes[id]?.let { return Footnote(ref.label, it, null) } }
    val n = Regex.escape(ref.label.trim('[', ']', '*'))
    val starts = Regex("^\\s*(\\[$n\\]|\\*?$n[.)]|$n\\s)")
    for (i in block + 1 until book.blocks.size) {
        val t = book.blocks[i].text
        if (starts.containsMatchIn(t)) return Footnote(ref.label, cleanNote(t).ifEmpty { t }, i)
    }
    return Footnote(ref.label, null, null)
}

/**
 * Compact note panel at the bottom of the page. It doesn't cover the book with a modal sheet: tapping the
 * page closes it. If the note's text isn't in the book, the AI explains what the marked place likely refers to.
 */
@Composable
fun FootnotePanel(
    note: Footnote, context: String, bookTitle: String, c: PageColors, bookId: String, block: Int,
    onGo: (Int) -> Unit, onDismiss: () -> Unit, modifier: Modifier = Modifier,
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var answer by remember(note) { mutableStateOf<String?>(null) }
    var status by remember(note) { mutableStateOf<String?>(null) }
    var job by remember { mutableStateOf<Job?>(null) }
    fun explain() {
        job?.cancel(); answer = ""; status = "Спрашиваю ИИ…"
        job = scope.launch {
            runCatching {
                val ai = Ai.get(ctx)
                val user = if (note.text != null)
                    "Объясни простыми словами эту сноску из книги «$bookTitle». Место в тексте: «$context». Сноска: «${note.text}»."
                else
                    "В книге «$bookTitle» стоит сноска ${note.label} в этом месте: «$context». Текста сноски нет. " +
                        "Коротко объясни, что здесь, вероятно, нуждается в пояснении: слово, имя, реалия, цитата или перевод."
                ai.stream("Ты — комментатор книги. Отвечай коротко, 2–4 предложения, на языке книги.", user, 0.2f, 350)
                    .collect { answer = visibleAnswer(it); if (!answer.isNullOrEmpty()) status = null }
                io.github.galis228.materialereader.ai.AiHistory.get(ctx).add(io.github.galis228.materialereader.ai.AiNote(
                    System.currentTimeMillis(), bookId, bookTitle, "Сноска", note.label, answer.orEmpty(), block, "", System.currentTimeMillis()))
            }.onFailure { if (it !is kotlinx.coroutines.CancellationException) { status = null; answer = null; status = it.message ?: "ИИ не ответил" } }
        }
    }
    LaunchedEffect(note) { if (note.text == null) explain() }
    DisposableEffect(Unit) { onDispose { job?.cancel() } }

    val maxH = (LocalConfiguration.current.screenHeightDp * 0.32f).dp
    Surface(
        modifier = modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 10.dp, vertical = 8.dp)
            .pointerInput(note) { detectVerticalDragGestures { _, dy -> if (dy > 18f) onDismiss() } },
        shape = RoundedCornerShape(20.dp), color = c.panel, contentColor = c.text, shadowElevation = 8.dp,
    ) {
        Column(Modifier.padding(start = 16.dp, end = 6.dp, top = 6.dp, bottom = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Сноска ${note.label}", color = c.accent, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, modifier = Modifier.weight(1f))
                IconButton(onClick = onDismiss, modifier = Modifier.size(36.dp)) { Icon(Icons.Rounded.Clear, "Закрыть", Modifier.size(18.dp)) }
            }
            Column(Modifier.padding(end = 10.dp).heightIn(max = maxH).verticalScroll(rememberScrollState())) {
                SelectionContainer {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (note.text != null) Text(note.text, fontSize = 15.sp, lineHeight = 21.sp)
                        else Text("В книге нет текста этой сноски.", color = c.muted, fontSize = 13.sp)
                        answer?.takeIf { it.isNotEmpty() }?.let {
                            Text("✦ ИИ: $it", fontSize = 14.sp, lineHeight = 20.sp, color = c.text.copy(alpha = 0.9f))
                        }
                        status?.let { Text(it, color = c.muted, fontSize = 13.sp) }
                    }
                }
            }
            Row(Modifier.padding(top = 2.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                note.block?.let { b -> TextButton(onClick = { onGo(b) }) { Text("Перейти к сноске", color = c.accent) } }
                if (note.text != null && answer == null) TextButton(onClick = { explain() }) { Text("Объяснить с ИИ", color = c.accent) }
            }
        }
    }
}
