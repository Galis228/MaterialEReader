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
    dark: Boolean = false, bionic: Boolean = false,
): AnnotatedString {
    val full = if (block.kind == Block.Kind.SEPARATOR) "* * *" else block.text
    val s = start.coerceIn(0, full.length)
    val e = end.coerceIn(s, full.length)
    val text = sliceText(full, s, e)
    return buildAnnotatedString {
        append(text)
        if (block.kind != Block.Kind.SEPARATOR) for (r in block.allRefs) {
            val a = (r.start - s).coerceAtLeast(0); val b = (r.end - s).coerceAtMost(e - s)
            if (a < b) addStyle(SpanStyle(fontSize = 0.68.em, baselineShift = BaselineShift.Superscript, color = accent), a, b)
        }
        if (bionic && block.kind != Block.Kind.SEPARATOR) for (m in wordRe.findAll(text)) {
            // bold the first ~half of each word: the eye anchors on it and skims the rest
            val n = m.value.length
            val k = if (n <= 3) 1 else (n + 1) / 2
            addStyle(SpanStyle(fontWeight = FontWeight.Bold), m.range.first, m.range.first + k)
        }
        for (h in highlights) {
            val a = if (h.whole) 0 else (h.start - s).coerceAtLeast(0)
            val b = if (h.whole) text.length else (h.end - s).coerceAtMost(e - s)
            if (a < b) addStyle(SpanStyle(background = highlightTint(h.color, dark)), a, b)
        }
    }
}

private val wordRe = Regex("""\p{L}[\p{L}\p{Mn}'’-]*""")
