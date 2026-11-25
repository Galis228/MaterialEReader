package io.github.galis228.materialereader.parse

import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction

object TxtParser {
    private val chapterRe = Regex(
        "^(глава|часть|пролог|эпилог|chapter|part|prologue|epilogue|розділ)(?![\\p{L}]).{0,80}$",
        RegexOption.IGNORE_CASE,
    )

    fun decode(bytes: ByteArray): String {
        // BOMs
        if (bytes.size >= 3 && bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte())
            return String(bytes, 3, bytes.size - 3, Charsets.UTF_8)
        if (bytes.size >= 2 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xFE.toByte())
            return String(bytes, 2, bytes.size - 2, Charsets.UTF_16LE)
        if (bytes.size >= 2 && bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte())
            return String(bytes, 2, bytes.size - 2, Charsets.UTF_16BE)
        return try {
            Charsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes)).toString()
        } catch (e: CharacterCodingException) {
            String(bytes, Charset.forName("windows-1251"))
        }
    }

    fun parse(bytes: ByteArray, fileName: String): ParsedBook {
        val text = decode(bytes).replace("\r\n", "\n").replace('\r', '\n')
        val lines = text.split('\n')
        // If the text uses blank lines between paragraphs, join wrapped lines; otherwise each line is a paragraph.
        val blankCount = lines.count { it.isBlank() }
        val blankSeparated = blankCount > lines.size / 6
        val blocks = ArrayList<Block>()
        val buf = StringBuilder()
        fun flush() {
            val s = buf.toString().normalizeSpace()
            buf.clear()
            if (s.isEmpty()) return
            if (s.length < 90 && chapterRe.matches(s)) blocks += Block(Block.Kind.HEADING, s)
            else if (s.all { it == '*' || it == ' ' || it == '-' || it == '—' }) blocks += Block(Block.Kind.SEPARATOR, "* * *")
            else blocks += Block(Block.Kind.PARAGRAPH, s)
        }
        for (line in lines) {
            if (line.isBlank()) { flush(); continue }
            if (blankSeparated) {
                if (buf.isNotEmpty()) buf.append(' ')
                buf.append(line)
            } else {
                buf.append(line); flush()
            }
        }
        flush()
        val title = fileName.substringBeforeLast('.').replace('_', ' ')
        return ParsedBook(title, "", blocks, chaptersFromHeadings(blocks, title))
    }
}
