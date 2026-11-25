package io.github.galis228.materialereader.parse

import java.util.zip.ZipInputStream

object BookParser {
    fun parse(bytes: ByteArray, fileName: String, format: BookFormat): ParsedBook = when (format) {
        BookFormat.EPUB -> EpubParser.parse(bytes, fileName)
        BookFormat.FB2 -> Fb2Parser.parse(bytes, fileName)
        BookFormat.FB2_ZIP -> Fb2Parser.parseZip(bytes, fileName)
        BookFormat.TXT -> TxtParser.parse(bytes, fileName)
        BookFormat.CBZ, BookFormat.CBR -> error("Комикс открывается через ComicArchive")
    }

    /** Guesses format by file name, then by content signature. */
    fun detect(bytes: ByteArray, fileName: String): BookFormat? {
        BookFormat.fromName(fileName)?.let { return it }
        if (bytes.size > 4 && bytes[0] == 'R'.code.toByte() && bytes[1] == 'a'.code.toByte() &&
            bytes[2] == 'r'.code.toByte() && bytes[3] == '!'.code.toByte()) return BookFormat.CBR
        if (bytes.size > 4 && bytes[0] == 'P'.code.toByte() && bytes[1] == 'K'.code.toByte()) {
            val head = String(bytes, 0, minOf(bytes.size, 200), Charsets.ISO_8859_1)
            if ("application/epub+zip" in head) return BookFormat.EPUB
            var images = 0
            ZipInputStream(bytes.inputStream()).use { z ->
                while (true) {
                    val e = z.nextEntry ?: break
                    val n = e.name.lowercase()
                    if (n.endsWith(".fb2")) return BookFormat.FB2_ZIP
                    if (n == "meta-inf/container.xml") return BookFormat.EPUB
                    if (ComicArchive.isImage(n)) images++
                }
            }
            return if (images > 0) BookFormat.CBZ else null
        }
        val head = String(bytes, 0, minOf(bytes.size, 600), Charsets.ISO_8859_1)
        if ("<FictionBook" in head) return BookFormat.FB2
        return BookFormat.TXT
    }
}
