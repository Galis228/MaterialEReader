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
            BookFormat.PDF -> openPdf(file)
            BookFormat.CB7 -> openSevenZ(file)
            else -> error("Не комикс: $format")
        }

        private fun openZip(file: File): ComicArchive {
            val zip = ZipFile(file)
            val names = zip.entries().asSequence().filter { !it.isDirectory && isImage(it.name) }.map { it.name }.toList()
                .sortedWith(NaturalOrder)
            if (names.isEmpty()) { zip.close(); error("В архиве нет изображений") }
            return ComicArchive(names, { n -> zip.getInputStream(zip.getEntry(n)).use { it.readBytes() } }, { zip.close() })
        }

        /** PDF pages rendered by the system's PdfRenderer, about 1600 px wide — sharp enough to zoom. */
        private fun openPdf(file: File): ComicArchive {
            val fd = android.os.ParcelFileDescriptor.open(file, android.os.ParcelFileDescriptor.MODE_READ_ONLY)
            val pdf = try { android.graphics.pdf.PdfRenderer(fd) } catch (e: Throwable) { fd.close(); error("PDF не открылся: ${e.message}") }
            if (pdf.pageCount == 0) { pdf.close(); fd.close(); error("В PDF нет страниц") }
            return ComicArchive((0 until pdf.pageCount).map { "$it" }, { n ->
                pdf.openPage(n.toInt()).use { p ->
                    val w = 1600
                    val h = (w.toFloat() * p.height / p.width).toInt().coerceIn(1, 4000)
                    val bmp = android.graphics.Bitmap.createBitmap(w, h, android.graphics.Bitmap.Config.ARGB_8888)
                    bmp.eraseColor(android.graphics.Color.WHITE)
                    p.render(bmp, null, null, android.graphics.pdf.PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    ByteArrayOutputStream().also { bmp.compress(android.graphics.Bitmap.CompressFormat.JPEG, 90, it); bmp.recycle() }.toByteArray()
                }
            }, { pdf.close(); fd.close() })
        }

        /** 7z can't be read at random cheaply, so its images are unpacked once next to the file. */
        private fun openSevenZ(file: File): ComicArchive {
            val dir = File(file.parentFile, ".${file.name}.pages")
            if (!File(dir, ".done").exists()) {
                dir.deleteRecursively(); dir.mkdirs()
                val sz = org.apache.commons.compress.archivers.sevenz.SevenZFile.builder().setFile(file).get()
                sz.use { z ->
                    var e = z.nextEntry
                    var n = 0
                    while (e != null) {
                        val name = e.name.replace('\\', '/')
                        if (!e.isDirectory && isImage(name)) {
                            // keep the order key in the file name, flattened
                            File(dir, "%05d_".format(n++) + name.replace('/', '_')).outputStream().use { out -> z.getInputStream(e).copyTo(out) }
                            File(dir, ".names").appendText(name + "\t" + "%05d_".format(n - 1) + name.replace('/', '_') + "\n")
                        }
                        e = z.nextEntry
                    }
                }
                File(dir, ".done").writeText("1")
            }
            val map = runCatching { File(dir, ".names").readLines().filter { '\t' in it }.associate { it.substringBefore('\t') to it.substringAfter('\t') } }.getOrDefault(emptyMap())
            if (map.isEmpty()) error("В архиве нет изображений")
            return ComicArchive(map.keys.sortedWith(NaturalOrder), { n -> File(dir, map.getValue(n)).readBytes() }, {})
        }

        /** Images (already in order) packed into a plain CBZ, for folders of manga pages. */
        fun zipImages(images: List<Pair<String, ByteArray>>): ByteArray {
            val bos = ByteArrayOutputStream()
            java.util.zip.ZipOutputStream(bos).use { z ->
                z.setLevel(0)
                images.forEachIndexed { i, (name, bytes) ->
                    z.putNextEntry(java.util.zip.ZipEntry("%05d_".format(i) + name.substringAfterLast('/')))
                    z.write(bytes); z.closeEntry()
                }
            }
            return bos.toByteArray()
        }

        /** "Berserk Vol. 3", "Наруто — том 12", "One Piece ch 105" → series and number. */
        fun seriesOf(title: String): Pair<String, Float>? {
            val m = Regex("^(.*?)[\\s._\\-—,]*(?:vol(?:ume)?|том|t|v|ch(?:apter)?|глава|гл|часть|#)?\\.?\\s*(\\d{1,4}(?:[.,]\\d)?)\\s*(?:\\(.*\\)|\\[.*])?\\s*$", RegexOption.IGNORE_CASE).find(title.trim()) ?: return null
            val name = m.groupValues[1].trim().trimEnd('-', '—', ',', '.', '_').trim()
            val n = m.groupValues[2].replace(',', '.').toFloatOrNull() ?: return null
            return if (name.length >= 2) name to n else null
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
