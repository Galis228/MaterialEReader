package io.github.galis228.materialereader.parse

import com.github.junrar.Archive
import com.github.junrar.rarfile.FileHeader
import java.io.ByteArrayOutputStream
import java.io.Closeable
import java.io.File
import java.util.zip.ZipFile

/** Page images of a CBZ (zip) or CBR (rar) comic, in reading order. Thread-safe. */
class ComicArchive private constructor(
    val pages: List<String>,
    private val reader: (String) -> ByteArray,
    private val closer: () -> Unit,
) : Closeable {

    @Synchronized fun page(i: Int): ByteArray = reader(pages[i])
    override fun close() = closer()

    companion object {
        private val imageExt = listOf(".jpg", ".jpeg", ".png", ".webp", ".gif", ".bmp", ".avif")
        fun isImage(name: String): Boolean {
            val n = name.lowercase()
            if (n.startsWith("__macosx/") || n.substringAfterLast('/').startsWith(".")) return false
            return imageExt.any { n.endsWith(it) }
        }

        fun open(file: File, format: BookFormat): ComicArchive = when (format) {
            BookFormat.CBZ -> openZip(file)
            BookFormat.CBR -> openRar(file)
            else -> error("Не комикс: $format")
        }

        private fun openZip(file: File): ComicArchive {
            val zip = ZipFile(file)
            val names = zip.entries().asSequence().filter { !it.isDirectory && isImage(it.name) }.map { it.name }.toList()
                .sortedWith(NaturalOrder)
            if (names.isEmpty()) { zip.close(); error("В архиве нет изображений") }
            return ComicArchive(names, { n -> zip.getInputStream(zip.getEntry(n)).use { it.readBytes() } }, { zip.close() })
        }

        private fun openRar(file: File): ComicArchive {
            val rar = Archive(file)
            if (rar.isEncrypted) { rar.close(); error("Архив защищён паролем") }
            val headers: Map<String, FileHeader> = rar.fileHeaders
                .filter { !it.isDirectory && isImage(it.fileName.replace('\\', '/')) }
                .associateBy { it.fileName.replace('\\', '/') }
            if (headers.isEmpty()) { rar.close(); error("В архиве нет изображений") }
            return ComicArchive(headers.keys.sortedWith(NaturalOrder), { n ->
                ByteArrayOutputStream().also { rar.extractFile(headers.getValue(n), it) }.toByteArray()
            }, { rar.close() })
        }
    }
}

/** "page2" < "page10"; case-insensitive. */
object NaturalOrder : Comparator<String> {
    private val chunk = Regex("\\d+|\\D+")
    override fun compare(a: String, b: String): Int {
        val x = chunk.findAll(a.lowercase()).map { it.value }.toList()
        val y = chunk.findAll(b.lowercase()).map { it.value }.toList()
        for (i in 0 until minOf(x.size, y.size)) {
            val p = x[i]; val q = y[i]
            val c = if (p[0].isDigit() && q[0].isDigit()) {
                val pn = p.trimStart('0'); val qn = q.trimStart('0')
                if (pn.length != qn.length) pn.length - qn.length else pn.compareTo(qn)
            } else p.compareTo(q)
            if (c != 0) return c
        }
        return x.size - y.size
    }
}
