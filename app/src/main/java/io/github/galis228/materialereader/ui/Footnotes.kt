package io.github.galis228.materialereader.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FootnoteSheet(note: Footnote, onGo: (Int) -> Unit, onDismiss: () -> Unit) {
    val maxH = (LocalConfiguration.current.screenHeightDp * 0.45f).dp
    ModalBottomSheet(onDismissRequest = onDismiss, scrimColor = Color.Transparent) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).navigationBarsPadding().padding(bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Сноска ${note.label}", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            Column(Modifier.heightIn(max = maxH).verticalScroll(rememberScrollState())) {
                SelectionContainer {
                    Text(note.text ?: "Текст этой сноски в книге не нашёлся.", style = MaterialTheme.typography.bodyLarge,
                        color = if (note.text == null) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                note.block?.let { b -> FilledTonalButton(onClick = { onGo(b) }) { Text("Перейти к сноске") } }
                TextButton(onClick = onDismiss) { Text("Закрыть") }
            }
        }
    }
}
