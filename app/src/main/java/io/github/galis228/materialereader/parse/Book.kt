package io.github.galis228.materialereader.parse

/** One displayable block of a book. [refs] mark footnote references inside [text]. */
data class Block(val kind: Kind, val text: String, val refs: List<NoteRef> = emptyList()) {
    enum class Kind { HEADING, PARAGRAPH, QUOTE, EPIGRAPH, SEPARATOR }

    /** Linked references plus bare "[12]" marks in the text that had no link in the file. */
    val allRefs: List<NoteRef> by lazy {
        val out = refs.toMutableList()
        for (m in BARE_REF.findAll(text)) {
            if (out.none { m.range.first < it.end && it.start <= m.range.last }) out += NoteRef(m.range.first, m.range.last + 1, null, m.groupValues[1])
        }
        out.sortedBy { it.start }
    }

    companion object { val BARE_REF = Regex("\\[(\\d{1,3})\\]") }
}

/**
 * A footnote reference: characters [start, end) of a block. [id] points into [ParsedBook.notes]
 * (null for a bare "[n]" found in text); [label] is the number shown.
 */
data class NoteRef(val start: Int, val end: Int, val id: String?, val label: String)

/** Builds normalized text (collapsed whitespace, trimmed) while keeping footnote ranges correct. */
class TextBuilder {
    private val sb = StringBuilder()
    private val refs = ArrayList<NoteRef>()
    private var space = false
    fun text(t: String) {
        for (c in t) {
            val ws = c.isWhitespace() || c == '\u00A0'
            if (ws) { if (sb.isNotEmpty()) space = true }
            else { if (space) { sb.append(' '); space = false }; sb.append(c) }
        }
    }
    fun ref(label: String, id: String?) {
        val l = label.trim().ifEmpty { return }
        if (space) { sb.append(' '); space = false }
        val start = sb.length
        sb.append(l)
        refs += NoteRef(start, sb.length, id, l.trim('[', ']', '{', '}', '(', ')', '*').ifEmpty { l })
    }
    fun isEmpty() = sb.isEmpty()
    fun build(): Pair<String, List<NoteRef>> = sb.toString() to refs.toList()
}

/** Footnote text without the leading back-link label like "1", "[1]", "1." or "↑". */
fun cleanNote(t: String): String = t.trim().replace(Regex("^(↑|\\^|\\[?\\d{1,3}\\]?[.)]?)\\s+"), "").trim()

data class Chapter(val title: String, val startBlock: Int)

data class ParsedBook(
    val title: String,
    val author: String,
    val blocks: List<Block>,
    val chapters: List<Chapter>,
    val cover: ByteArray? = null,
    /** Footnote texts by id. */
    val notes: Map<String, String> = emptyMap(),
) {
    val wordCount: Int by lazy { blocks.sumOf { countWords(it.text) } }
}

enum class BookFormat(val ext: String) {
    EPUB("epub"), FB2("fb2"), FB2_ZIP("fb2.zip"), TXT("txt"), CBZ("cbz"), CBR("cbr");

    val isComic get() = this == CBZ || this == CBR

    companion object {
        fun fromName(name: String): BookFormat? {
            val n = name.lowercase()
            return when {
                n.endsWith(".fb2.zip") || n.endsWith(".fbz") -> FB2_ZIP
                n.endsWith(".epub") -> EPUB
                n.endsWith(".fb2") -> FB2
                n.endsWith(".txt") -> TXT
                n.endsWith(".cbz") -> CBZ
                n.endsWith(".cbr") -> CBR
                else -> null
            }
        }
    }
}

fun countWords(s: String): Int {
    var count = 0
    var inWord = false
    for (c in s) {
        if (c.isLetterOrDigit()) {
            if (!inWord) { count++; inWord = true }
        } else inWord = false
    }
    return count
}

/** Collapses whitespace runs to single spaces and trims. */
internal fun String.normalizeSpace(): String =
    replace(' ', ' ').replace(Regex("\\s+"), " ").trim()

/** Builds chapter list from headings; falls back to a single chapter. */
internal fun chaptersFromHeadings(blocks: List<Block>, fallbackTitle: String): List<Chapter> {
    val ch = blocks.withIndex()
        .filter { it.value.kind == Block.Kind.HEADING }
        .map { Chapter(it.value.text.take(120), it.index) }
    return if (ch.isEmpty()) listOf(Chapter(fallbackTitle, 0)) else {
        if (ch.first().startBlock > 0) listOf(Chapter("Начало", 0)) + ch else ch
    }
}
