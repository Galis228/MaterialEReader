package io.github.galis228.materialereader.ui

import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.Hyphens
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextIndent
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import io.github.galis228.materialereader.data.ReaderSettings
import io.github.galis228.materialereader.parse.Block
import io.github.galis228.materialereader.parse.ParsedBook
import kotlinx.coroutines.ensureActive
import kotlin.coroutines.coroutineContext

/** A piece of one block shown on a page: characters [start, end) of the block's text. */
data class Slice(val block: Int, val start: Int, val end: Int)

data class Page(val slices: List<Slice>) {
    val firstBlock get() = slices.first().block
    val firstChar get() = slices.first().start
}

/**
 * Visual spec of a block kind, shared by measuring and drawing so pages match exactly.
 * All sizes in px.
 */
data class BlockSpec(val style: TextStyle, val top: Float, val bottom: Float, val padStart: Float, val padEnd: Float)

fun blockSpec(kind: Block.Kind, s: ReaderSettings, color: androidx.compose.ui.graphics.Color, muted: androidx.compose.ui.graphics.Color, density: Density): BlockSpec {
    val base = TextStyle(
        fontFamily = s.font.family(), fontSize = s.fontSize.sp, lineHeight = (s.fontSize * s.lineHeight).sp,
        color = color, hyphens = if (s.hyphenate) Hyphens.Auto else Hyphens.None, lineBreak = LineBreak.Paragraph,
        textAlign = if (s.justify) TextAlign.Justify else TextAlign.Start,
    )
    val dp = density.density
    // Paddings are drawn as whole pixels, so measure them as whole pixels too
    fun BlockSpec.whole() = copy(top = Math.round(top).toFloat(), bottom = Math.round(bottom).toFloat(),
        padStart = Math.round(padStart).toFloat(), padEnd = Math.round(padEnd).toFloat())
    return when (kind) {
        Block.Kind.HEADING -> BlockSpec(
            base.copy(fontSize = (s.fontSize * 1.3f).sp, lineHeight = (s.fontSize * 1.6f).sp, fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center, hyphens = Hyphens.None),
            top = 36 * dp, bottom = 18 * dp, padStart = 0f, padEnd = 0f,
        )
        Block.Kind.PARAGRAPH -> BlockSpec(base.copy(textIndent = TextIndent(firstLine = 1.5.em)), 0f, s.fontSize * 0.45f * dp, 0f, 0f)
        Block.Kind.QUOTE -> BlockSpec(base.copy(fontStyle = FontStyle.Italic, textAlign = TextAlign.Start), 0f, s.fontSize * 0.35f * dp, 24 * dp, 0f)
        Block.Kind.EPIGRAPH -> BlockSpec(
            base.copy(fontStyle = FontStyle.Italic, fontSize = (s.fontSize * 0.88f).sp, color = muted, textAlign = TextAlign.End),
            0f, 8 * dp, 64 * dp, 0f,
        )
        Block.Kind.SEPARATOR -> BlockSpec(base.copy(textAlign = TextAlign.Center, color = muted), 16 * dp, 16 * dp, 0f, 0f)
        Block.Kind.VERSE -> BlockSpec(
            base.copy(textAlign = TextAlign.Start, hyphens = Hyphens.None, lineBreak = LineBreak.Simple, textIndent = TextIndent.None),
            s.fontSize * 0.3f * dp, s.fontSize * 0.8f * dp, 28 * dp, 0f,
        )
        Block.Kind.TABLE -> BlockSpec(
            base.copy(fontSize = (s.fontSize * 0.82f).sp, lineHeight = (s.fontSize * 0.82f * 1.45f).sp, textAlign = TextAlign.Start,
                hyphens = Hyphens.None, textIndent = TextIndent.None),
            8 * dp, 14 * dp, 0f, 0f,
        )
        Block.Kind.IMAGE -> BlockSpec(base, 10 * dp, 14 * dp, 0f, 0f)
    }.whole()
}

/** Style for a slice: continuation slices drop the first-line indent. */
fun BlockSpec.forSlice(start: Int): TextStyle = if (start > 0) style.copy(textIndent = TextIndent.None) else style

/** Top padding is dropped for the first slice on a page. */
fun BlockSpec.topOnPage(first: Boolean) = if (first) 0f else top

class Paginator(
    private val measurer: TextMeasurer,
    private val specs: Map<Block.Kind, BlockSpec>,
    private val width: Int,
    private val height: Int,
    private val bionic: Boolean = false,
    /** Pixel size of an illustration by its key, or null if it can't be read. */
    private val imageSize: (String) -> androidx.compose.ui.unit.IntSize? = { null },
    private val dropCap: Boolean = false,
) {
    private fun measure(text: androidx.compose.ui.text.AnnotatedString, spec: BlockSpec, start: Int): TextLayoutResult {
        val w = (width - spec.padStart - spec.padEnd).toInt().coerceAtLeast(1)
        // fixed width, so centered and right-aligned blocks are laid out across the whole column
        return measurer.measure(text, spec.forSlice(start), constraints = Constraints.fixedWidth(w), skipCache = true)
    }

    suspend fun paginate(book: ParsedBook, from: Int = 0, to: Int = book.blocks.size, onProgress: (Float) -> Unit = {}): List<Page> {
        val pages = ArrayList<Page>()
        var cur = ArrayList<Slice>()
        var y = 0f
        val chapterStarts = book.chapters.map { it.startBlock }.toHashSet()
        fun push() { if (cur.isNotEmpty()) { pages += Page(cur); cur = ArrayList(); y = 0f } }

        for (i in from until to) {
            val b = book.blocks[i]
            if (i % 50 == 0) { coroutineContext.ensureActive(); onProgress((i - from).toFloat() / (to - from).coerceAtLeast(1)) }
            val spec = specs.getValue(b.kind)
            val text = if (b.kind == Block.Kind.SEPARATOR) "* * *" else b.text
            if (i in chapterStarts && i > from) push()
            if (b.kind == Block.Kind.IMAGE) {
                val box = imageSize(b.src)?.let { imageBox(it, width, height) } ?: continue
                val top = spec.topOnPage(cur.isEmpty())
                if (y + top + box.height > height && cur.isNotEmpty()) push()
                cur += Slice(i, 0, 0)
                y += spec.topOnPage(cur.size == 1) + box.height + spec.bottom
                continue
            }
            val cap = dropCap && isDropCap(book, i)
            var start = 0
            var guard = 0
            while (start < text.length && guard++ < 10_000) {
                val top = spec.topOnPage(cur.isEmpty())
                val avail = height - y - top
                val layout = measure(styledSlice(b, start, text.length, bionic = bionic, dropCap = cap && start == 0), spec, start)
                val lineH = if (layout.lineCount > 0) layout.getLineBottom(0) - layout.getLineTop(0) else 0f
                // keep headings with at least two following lines
                val need = if (b.kind == Block.Kind.HEADING) layout.size.height + lineH * 2 else layout.size.height.toFloat()
                if (need <= avail - 1f) {
                    cur += Slice(i, start, text.length)
                    y += top + layout.size.height + spec.bottom
                    break
                }
                if (b.kind == Block.Kind.HEADING || b.kind == Block.Kind.SEPARATOR) {
                    if (cur.isNotEmpty()) { push(); continue }
                    cur += Slice(i, start, text.length); y += layout.size.height + spec.bottom; break
                }
                var fit = 0
                while (fit < layout.lineCount && layout.getLineBottom(fit) <= avail) fit++
                // avoid a lonely first line at the bottom of a page
                if ((fit == 0 || (fit == 1 && layout.lineCount > 2 && start == 0)) && cur.isNotEmpty()) { push(); continue }
                if (fit == 0) fit = 1
                // High-quality line breaking balances the whole paragraph, so a cut piece can wrap differently
                // than the same lines inside the full paragraph. Measure the piece exactly as it will be drawn
                // and give back lines until it really fits — otherwise the bottom line gets clipped.
                var end = start + layout.getLineEnd(fit - 1)
                while (fit > 1 && measure(styledSlice(b, start, end, bionic = bionic, dropCap = cap && start == 0), spec, start).size.height > avail - 1f) {
                    fit--
                    end = start + layout.getLineEnd(fit - 1)
                }
                // never split a word across pages ("финан-" / "систа"): end the page before that word instead
                if (end in (start + 1) until text.length && text[end - 1].isLetter() && text[end].isLetter()) {
                    val space = text.lastIndexOf(' ', end - 1)
                    if (space > start) end = space + 1
                }
                cur += Slice(i, start, end)
                push()
                start = end
                while (start < text.length && text[start] == ' ') start++
            }
        }
        push()
        onProgress(1f)
        return pages
    }
}

/** Index of the page that contains (block, char). */
fun List<Page>.indexOfPosition(block: Int, char: Int): Int {
    if (isEmpty()) return 0
    var lo = 0; var hi = lastIndex
    while (lo < hi) {
        val mid = (lo + hi + 1) / 2
        val p = this[mid]
        if (p.firstBlock < block || (p.firstBlock == block && p.firstChar <= char)) lo = mid else hi = mid - 1
    }
    return lo
}

/** The text drawn for a slice: a word split across pages keeps its hyphen. Shared by measuring and drawing. */
fun sliceText(full: String, start: Int, end: Int): String {
    val e = end.coerceAtMost(full.length)
    var t = full.substring(start.coerceAtMost(e), e)
    if (e < full.length && e > 0 && full[e - 1].isLetter() && full[e].isLetter()) t += "-"
    return t
}

/** An illustration scaled to the column: never wider than the column or taller than the page, at most 2× its pixels. */
fun imageBox(px: androidx.compose.ui.unit.IntSize, maxW: Int, maxH: Int): androidx.compose.ui.unit.IntSize {
    if (px.width <= 0 || px.height <= 0) return androidx.compose.ui.unit.IntSize(maxW, maxW / 2)
    val k = minOf(maxW.toFloat() / px.width, maxH * 0.92f / px.height, 2f)
    return androidx.compose.ui.unit.IntSize((px.width * k).toInt().coerceAtLeast(1), (px.height * k).toInt().coerceAtLeast(1))
}

/** The first paragraph right after a heading gets a large first letter. */
fun isDropCap(book: ParsedBook, i: Int): Boolean =
    book.blocks[i].kind == Block.Kind.PARAGRAPH && book.blocks.getOrNull(i - 1)?.kind == Block.Kind.HEADING &&
        book.blocks[i].text.firstOrNull()?.isLetter() == true
