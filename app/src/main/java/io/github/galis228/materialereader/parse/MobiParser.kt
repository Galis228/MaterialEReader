package io.github.galis228.materialereader.parse

import java.io.ByteArrayOutputStream

/**
 * MOBI / AZW (Mobipocket) books: a Palm database whose text records hold HTML, usually PalmDOC-compressed.
 * HUFF/CDIC-compressed and DRM-protected files are not supported.
 */
object MobiParser {
    fun isMobi(bytes: ByteArray): Boolean =
        bytes.size > 68 && String(bytes, 60, 8, Charsets.ISO_8859_1).let { it == "BOOKMOBI" || it == "TEXtREAd" }

    fun parse(bytes: ByteArray, fileName: String): ParsedBook {
        require(isMobi(bytes)) { "Это не MOBI-файл" }
        val n = u16(bytes, 76)
        val offsets = IntArray(n) { u32(bytes, 78 + it * 8).toInt() }
        fun record(i: Int): ByteArray {
            val start = offsets[i]; val end = if (i + 1 < n) offsets[i + 1] else bytes.size
            return bytes.copyOfRange(start, end.coerceAtLeast(start))
        }
        val r0 = record(0)
        val compression = u16(r0, 0)
        val textRecords = u16(r0, 8)
        val encryption = u16(r0, 12)
        require(encryption == 0) { "Книга защищена DRM — её не открыть без ключа" }
        require(compression == 1 || compression == 2) { "Это сжатие MOBI (HUFF/CDIC) не поддерживается — сконвертируй книгу в EPUB" }

        var title = String(bytes, 0, 32, Charsets.ISO_8859_1).trimEnd('\u0000').replace('_', ' ')
        var author = ""
        var utf8 = true
        var extraFlags = 0
        var firstImage = -1
        var coverOffset = -1
        val isMobiHeader = r0.size > 20 && String(r0, 16, 4, Charsets.ISO_8859_1) == "MOBI"
        if (isMobiHeader) {
            val headerLen = u32(r0, 20).toInt()
            utf8 = u32(r0, 28).toInt() == 65001
            val nameOff = u32(r0, 84).toInt(); val nameLen = u32(r0, 88).toInt()
            if (nameOff > 0 && nameOff + nameLen <= r0.size) title = String(r0, nameOff, nameLen, if (utf8) Charsets.UTF_8 else charset("windows-1252"))
            firstImage = u32(r0, 108).toInt()
            if (headerLen >= 0xE4 && r0.size >= 16 + 0xE4) extraFlags = u16(r0, 16 + 0xE2)
            val exth = (u32(r0, 128).toInt() and 0x40) != 0
            val exthAt = 16 + headerLen
            if (exth && exthAt + 12 <= r0.size && String(r0, exthAt, 4, Charsets.ISO_8859_1) == "EXTH") {
                val count = u32(r0, exthAt + 8).toInt()
                var p = exthAt + 12
                repeat(count) {
                    if (p + 8 > r0.size) return@repeat
                    val type = u32(r0, p).toInt(); val len = u32(r0, p + 4).toInt()
                    if (len < 8 || p + len > r0.size) return@repeat
                    val data = r0.copyOfRange(p + 8, p + len)
                    val text = String(data, if (utf8) Charsets.UTF_8 else charset("windows-1252"))
                    when (type) {
                        100 -> author = if (author.isEmpty()) text else "$author, $text"
                        503 -> title = text
                        201 -> if (data.size >= 4) coverOffset = u32(data, 0).toInt()
                    }
                    p += len
                }
            }
        }

        val out = ByteArrayOutputStream()
        for (i in 1..textRecords.coerceAtMost(n - 1)) {
            var rec = record(i)
            rec = rec.copyOf(rec.size - trailingSize(rec, extraFlags))
            out.write(if (compression == 2) palmDoc(rec) else rec)
        }
        val html = String(out.toByteArray(), if (utf8) Charsets.UTF_8 else charset("windows-1252"))
            .replace(Regex("<mbp:pagebreak\\s*/?>", RegexOption.IGNORE_CASE), "")

        val blocks = EpubParser.htmlBlocks(html)
        val chapters = blocks.withIndex().filter { it.value.kind == Block.Kind.HEADING }.map { Chapter(it.value.text.take(120), it.index) }
            .ifEmpty { listOf(Chapter(title, 0)) }
        val cover = if (firstImage > 0 && coverOffset >= 0 && firstImage + coverOffset < n) record(firstImage + coverOffset) else null
        return ParsedBook(title.ifBlank { fileName.substringBeforeLast('.') }, author, blocks, chapters, cover)
    }

    /** Size of the extra entries at the end of a text record (multibyte flag and indexing data). */
    private fun trailingSize(rec: ByteArray, flags: Int): Int {
        var size = 0
        var f = flags shr 1
        while (f != 0) {
            if (f and 1 != 0) size += backwardInt(rec, rec.size - size)
            f = f shr 1
        }
        if (flags and 1 != 0 && rec.size - size - 1 >= 0) size += (rec[rec.size - size - 1].toInt() and 3) + 1
        return size.coerceAtMost(rec.size)
    }

    private fun backwardInt(rec: ByteArray, end: Int): Int {
        var v = 0; var shift = 0; var p = end - 1
        while (p >= 0 && shift < 28) {
            val b = rec[p].toInt() and 0xFF
            v = v or ((b and 0x7F) shl shift)
            shift += 7
            if (b and 0x80 != 0) break
            p--
        }
        return v
    }

    /** PalmDOC LZ77 decompression. */
    fun palmDoc(src: ByteArray): ByteArray {
        val out = ByteArrayOutputStream(src.size * 2)
        val buf = ArrayList<Byte>(src.size * 2)
        var i = 0
        while (i < src.size) {
            val c = src[i++].toInt() and 0xFF
            when {
                c == 0 || c in 9..0x7F -> buf += c.toByte()
                c in 1..8 -> repeat(c) { if (i < src.size) buf += src[i++] }
                c in 0x80..0xBF -> {
                    if (i >= src.size) break
                    val pair = (c shl 8) or (src[i++].toInt() and 0xFF)
                    val dist = (pair shr 3) and 0x7FF
                    val len = (pair and 7) + 3
                    val from = buf.size - dist
                    if (from < 0) continue
                    repeat(len) { k -> buf += buf[from + k] }
                }
                else -> { buf += ' '.code.toByte(); buf += (c xor 0x80).toByte() }
            }
        }
        out.write(buf.toByteArray())
        return out.toByteArray()
    }

    private fun u16(b: ByteArray, o: Int) = if (o + 2 > b.size) 0 else ((b[o].toInt() and 0xFF) shl 8) or (b[o + 1].toInt() and 0xFF)
    private fun u32(b: ByteArray, o: Int): Long = if (o + 4 > b.size) 0L else
        ((b[o].toLong() and 0xFF) shl 24) or ((b[o + 1].toLong() and 0xFF) shl 16) or ((b[o + 2].toLong() and 0xFF) shl 8) or (b[o + 3].toLong() and 0xFF)
}
