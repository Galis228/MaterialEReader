package io.github.galis228.materialereader.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.unit.em
import io.github.galis228.materialereader.data.Highlight
import io.github.galis228.materialereader.parse.Block

/**
 * The text of a slice with footnote marks set as small superscripts and highlights as backgrounds.
 * Pagination calls it with no colors/highlights; only the footnote spans change the layout, so the
 * measured and drawn text break into identical lines.
 */
fun styledSlice(
    block: Block, start: Int, end: Int, accent: Color = Color.Unspecified, highlights: List<Highlight> = emptyList(),
    dark: Boolean = false, bionic: Boolean = false, dropCap: Boolean = false, noteColor: Color = Color.Unspecified,
): AnnotatedString {
    val full = if (block.kind == Block.Kind.SEPARATOR) "* * *" else block.text
    val s = start.coerceIn(0, full.length)
    val e = end.coerceIn(s, full.length)
    val text = sliceText(full, s, e)
    return buildAnnotatedString {
        append(text)
        if (block.kind != Block.Kind.SEPARATOR) for (r in block.allRefs) {
            val a = (r.start - s).coerceAtLeast(0); val b = (r.end - s).coerceAtMost(e - s)
            // footnote marks: small, bold and in their own color so they stand apart from the text and from links
            if (a < b) addStyle(SpanStyle(fontSize = 0.72.em, baselineShift = BaselineShift.Superscript, fontWeight = FontWeight.Bold,
                color = if (noteColor != Color.Unspecified) noteColor else accent), a, b)
        }
        if (bionic && block.kind != Block.Kind.SEPARATOR) for (m in wordRe.findAll(text)) {
            // bold the first ~half of each word: the eye anchors on it and skims the rest
            val n = m.value.length
            val k = if (n <= 3) 1 else (n + 1) / 2
            addStyle(SpanStyle(fontWeight = FontWeight.Bold), m.range.first, m.range.first + k)
        }
        // links inside the book: colored and underlined (no layout change)
        if (accent != Color.Unspecified) for (l in block.links) {
            val a = (l.start - s).coerceAtLeast(0); val b = (l.end - s).coerceAtMost(e - s)
            if (a < b) addStyle(SpanStyle(color = accent, textDecoration = androidx.compose.ui.text.style.TextDecoration.Underline), a, b)
        }
        if (dropCap && s == 0 && text.isNotEmpty()) addStyle(SpanStyle(fontSize = 2.1.em, fontWeight = FontWeight.Bold), 0, 1)
        for (h in highlights) {
            val a = if (h.whole) 0 else (h.start - s).coerceAtLeast(0)
            val b = if (h.whole) text.length else (h.end - s).coerceAtMost(e - s)
            if (a < b) addStyle(SpanStyle(background = highlightTint(h.color, dark)), a, b)
        }
    }
}

private val wordRe = Regex("""\p{L}[\p{L}\p{Mn}'’-]*""")

/** Footnote color: the person's choice, or teal — apart from every theme's accent and from link color. */
fun footnoteColor(chosen: Long, dark: Boolean): Color =
    if (chosen != 0L) Color(chosen) else if (dark) Color(0xFF4DD0C0) else Color(0xFF00897B)
