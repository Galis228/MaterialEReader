package io.github.galis228.materialereader.parse

/** One displayable block of a book. */
data class Block(val kind: Kind, val text: String) {
    enum class Kind { HEADING, PARAGRAPH, QUOTE, EPIGRAPH, SEPARATOR }
}

data class Chapter(val title: String, val startBlock: Int)

data class ParsedBook(
    val title: String,
    val author: String,
    val blocks: List<Block>,
    val chapters: List<Chapter>,
    val cover: ByteArray? = null,
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
