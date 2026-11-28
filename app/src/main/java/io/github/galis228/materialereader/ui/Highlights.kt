package io.github.galis228.materialereader.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Notes
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import io.github.galis228.materialereader.data.Highlight
import io.github.galis228.materialereader.parse.ParsedBook
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

val HighlightColors = listOf(Color(0xFFFFD54F), Color(0xFF81C784), Color(0xFF64B5F6), Color(0xFFF48FB1))
val HighlightNames = listOf("жёлтый", "зелёный", "синий", "розовый")

fun highlightTint(color: Int, dark: Boolean): Color =
    HighlightColors.getOrElse(color) { HighlightColors[0] }.copy(alpha = if (dark) 0.22f else 0.34f)

/** Where each paragraph is on screen right now, so a long press can tell which one was pressed. */
/** A piece of text on screen: [rect] in root coordinates, [startChar] = where the piece begins in its paragraph. */
class Hit(val block: Int, val rect: Rect, val layout: androidx.compose.ui.text.TextLayoutResult?, val startChar: Int) {
    /** Character of the paragraph under [p], or null without a layout. */
    fun charAt(p: Offset): Int? = layout?.let { l ->
        (l.getOffsetForPosition(p - rect.topLeft) + startChar).coerceAtMost(startChar + l.layoutInput.text.length - 1).coerceAtLeast(startChar)
    }

    /** Screen box of paragraph characters [a, b) inside this piece, or null if not here. */
    fun boxOf(a: Int, b: Int): Rect? {
        val l = layout ?: return null
        val n = l.layoutInput.text.length
        val from = (a - startChar).coerceAtLeast(0); val to = (b - startChar).coerceAtMost(n)
        if (from >= to) return null
        var r = l.getBoundingBox(from)
        for (i in from + 1 until to) {
            val o = l.getBoundingBox(i)
            r = Rect(minOf(r.left, o.left), minOf(r.top, o.top), maxOf(r.right, o.right), maxOf(r.bottom, o.bottom))
        }
        return r.translate(rect.topLeft)
    }
}

/** Where each paragraph piece is on screen right now, so taps and long presses can tell what was pressed. */
class BlockHits {
    // keyed by the on-screen text piece: one paragraph can be split across two composed pages
    private val hits = HashMap<Any, Hit>()
    fun put(token: Any, block: Int, r: Rect, layout: androidx.compose.ui.text.TextLayoutResult? = null, startChar: Int = 0) {
        hits[token] = Hit(block, r, layout, startChar)
    }
    fun remove(token: Any) { hits.remove(token) }
    fun hitAt(p: Offset): Hit? = hits.values.firstOrNull { it.rect.contains(p) }
    fun at(p: Offset): Int? = hitAt(p)?.block
    fun piecesOf(block: Int): List<Hit> = hits.values.filter { it.block == block }
}

val LocalBlockHits = staticCompositionLocalOf<BlockHits?> { null }
val LocalHighlights = staticCompositionLocalOf<Map<Int, List<Highlight>>> { emptyMap() }
val LocalPageDark = staticCompositionLocalOf { false }
val LocalPageAccent = staticCompositionLocalOf { Color.Unspecified }
/** Taps go here first (footnote marks); true = handled, so the tap doesn't turn the page. */
val LocalTapAt = staticCompositionLocalOf<(Offset) -> Boolean> { { false } }

fun copy(ctx: Context, text: String) {
    ctx.getSystemService(ClipboardManager::class.java)?.setPrimaryClip(ClipData.newPlainText("Цитата", text))
}

fun share(ctx: Context, text: String, subject: String? = null) {
    ctx.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"; putExtra(Intent.EXTRA_TEXT, text); subject?.let { putExtra(Intent.EXTRA_SUBJECT, it) }
    }, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}

/** All highlights and notes of a book as Markdown, grouped by chapter. */
fun quotesMarkdown(book: ParsedBook, highlights: List<Highlight>): String = buildString {
    append("# ").append(book.title).append('\n')
    if (book.author.isNotEmpty()) append("*").append(book.author).append("*\n")
    var lastChapter: String? = null
    for (h in highlights.sortedBy { it.block }) {
        val ch = book.chapters.lastOrNull { it.startBlock <= h.block }?.title.orEmpty()
        if (ch != lastChapter) { append("\n## ").append(ch).append("\n\n"); lastChapter = ch }
        append("> ").append(h.text(book.blocks.getOrNull(h.block)?.text.orEmpty()).replace("\n", "\n> ")).append('\n')
        if (h.note.isNotBlank()) append("\n").append(h.note).append('\n')
        append('\n')
    }
}

data class SearchHit(val block: Int, val char: Int, val before: String, val match: String, val after: String, val chapter: String)

fun searchBook(book: ParsedBook, query: String, limit: Int = 300): List<SearchHit> {
    val q = query.trim()
    if (q.length < 2) return emptyList()
    val out = ArrayList<SearchHit>()
    for ((i, b) in book.blocks.withIndex()) {
        var from = 0
        while (out.size < limit) {
            val at = b.text.indexOf(q, from, ignoreCase = true)
            if (at < 0) break
            out += SearchHit(
                i, at, b.text.substring((at - 50).coerceAtLeast(0), at).let { if (at > 50) "…" + it.trimStart() else it },
                b.text.substring(at, at + q.length), b.text.substring(at + q.length, (at + q.length + 70).coerceAtMost(b.text.length)),
                book.chapters.lastOrNull { it.startBlock <= i }?.title.orEmpty(),
            )
            from = at + q.length
        }
        if (out.size >= limit) break
    }
    return out
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchSheet(book: ParsedBook, initial: String, onQuery: (String) -> Unit, onPick: (SearchHit) -> Unit, onDismiss: () -> Unit) {
    var query by remember { mutableStateOf(initial) }
    val results by produceState<List<SearchHit>?>(null, query) {
        delay(250)
        value = withContext(Dispatchers.Default) { searchBook(book, query) }
    }
    val maxH = (LocalConfiguration.current.screenHeightDp * 0.6f).dp
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().heightIn(max = maxH).navigationBarsPadding()) {
            OutlinedTextField(
                query, { query = it; onQuery(it) }, singleLine = true, placeholder = { Text("Найти в книге") },
                leadingIcon = { Icon(Icons.Rounded.Search, null) },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search), keyboardActions = KeyboardActions(onSearch = {}),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            )
            val r = results
            Text(
                when { query.trim().length < 2 -> "Введи хотя бы две буквы"; r == null -> "Ищу…"; r.isEmpty() -> "Ничего не найдено"
                    r.size >= 300 -> "Больше 300 совпадений — уточни запрос"; else -> "Найдено: ${r.size}" },
                style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
            )
            LazyColumn {
                items(r.orEmpty()) { h ->
                    Column(Modifier.fillMaxWidth().clickable { onPick(h) }.padding(horizontal = 20.dp, vertical = 10.dp)) {
                        Text(h.chapter, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, maxLines = 1)
                        Text(buildAnnotatedString {
                            append(h.before)
                            withStyle(SpanStyle(fontWeight = FontWeight.Bold, background = HighlightColors[0].copy(alpha = 0.45f))) { append(h.match) }
                            append(h.after)
                        }, maxLines = 3, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }
}

@Composable
fun QuotesTab(book: ParsedBook, highlights: List<Highlight>, onPick: (Highlight) -> Unit) {
    val ctx = LocalContext.current
    if (highlights.isEmpty()) {
        Text("Выделений пока нет. Нажми и подержи любой абзац, чтобы выделить его цветом или добавить заметку.",
            color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(24.dp).navigationBarsPadding())
        return
    }
    Column(Modifier.navigationBarsPadding()) {
        Row(Modifier.padding(horizontal = 12.dp)) {
            TextButton(onClick = { share(ctx, quotesMarkdown(book, highlights), "Цитаты: ${book.title}") }) {
                Icon(Icons.Rounded.Share, null, Modifier.size(18.dp)); Text(" Экспорт в Markdown")
            }
            TextButton(onClick = { copy(ctx, quotesMarkdown(book, highlights)) }) {
                Icon(Icons.Rounded.ContentCopy, null, Modifier.size(18.dp)); Text(" Копировать всё")
            }
        }
        LazyColumn {
            items(highlights.sortedWith(compareBy({ it.block }, { it.start })), key = { it.createdAt }) { h ->
                Row(Modifier.fillMaxWidth().clickable { onPick(h) }.padding(horizontal = 20.dp, vertical = 10.dp)) {
                    Box(Modifier.padding(top = 4.dp).size(10.dp).clip(CircleShape).background(HighlightColors.getOrElse(h.color) { HighlightColors[0] }))
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(h.text(book.blocks.getOrNull(h.block)?.text.orEmpty()), maxLines = 3, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
                        if (h.note.isNotBlank()) Text(h.note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(top = 4.dp))
                    }
                }
            }
        }
    }
}

/** Bionic reading: bold the start of each word. */
val LocalBionic = androidx.compose.runtime.staticCompositionLocalOf { false }

val LocalDropCap = androidx.compose.runtime.staticCompositionLocalOf { false }
/** The open book, for blocks that need context (drop caps, illustrations). */
val LocalBookForCaps = androidx.compose.runtime.staticCompositionLocalOf<io.github.galis228.materialereader.parse.ParsedBook?> { null }
/** Double-tap action, or null when none is set (so single taps aren't delayed). */
val LocalDoubleTap = androidx.compose.runtime.staticCompositionLocalOf<((Offset) -> Unit)?> { null }
val LocalNoteColor = androidx.compose.runtime.staticCompositionLocalOf { Color.Unspecified }
