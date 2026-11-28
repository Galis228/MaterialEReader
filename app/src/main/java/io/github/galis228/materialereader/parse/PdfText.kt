package io.github.galis228.materialereader.parse

import android.graphics.pdf.PdfRenderer
import android.os.Build
import android.os.ParcelFileDescriptor
import java.io.File

/** The text layer of a PDF, joined back into paragraphs. Needs Android 15 (API 35). */
object PdfText {
    val supported get() = Build.VERSION.SDK_INT >= 35

    fun extract(file: File): String {
        if (!supported) error("Текст из PDF читается на Android 15 и новее")
        val pages = ArrayList<String>()
        ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { fd ->
            PdfRenderer(fd).use { pdf ->
                for (i in 0 until pdf.pageCount) pdf.openPage(i).use { p ->
                    pages += if (Build.VERSION.SDK_INT >= 35) p.textContents.joinToString("\n") { it.text } else ""
                }
            }
        }
        return paragraphs(pages)
    }

    /** Lines wrapped by the layout are joined; a short line or a new capitalized line after a full stop starts a paragraph. */
    fun paragraphs(pages: List<String>): String {
        val lines = pages.flatMap { pg ->
            pg.lines().map { it.trim() }.filter { it.isNotEmpty() && !Regex("^[-–—]?\\s*\\d{1,4}\\s*[-–—]?$").matches(it) } // page numbers
        }
        if (lines.isEmpty()) return ""
        val typical = lines.map { it.length }.sorted()[lines.size * 3 / 4].coerceAtLeast(20)
        val out = StringBuilder()
        var cur = StringBuilder()
        fun flush() { if (cur.isNotEmpty()) { out.append(cur.toString().trim()).append("\n\n"); cur = StringBuilder() } }
        for ((i, l) in lines.withIndex()) {
            if (cur.isEmpty()) cur.append(l)
            else if (cur.endsWith("-") && l.first().isLowerCase()) { cur.setLength(cur.length - 1); cur.append(l) }
            else cur.append(' ').append(l)
            val next = lines.getOrNull(i + 1)
            val ends = l.last() in ".!?…:»\""
            val heading = Regex("^(глава|часть|chapter|part)\\b", RegexOption.IGNORE_CASE)
            if (next == null || (ends && l.length < typical * 0.9) || (ends && next.first().let { it == '—' || it == '–' }) ||
                heading.containsMatchIn(next) || (heading.containsMatchIn(l) && l.length < typical * 0.5) ||
                (l.length < typical * 0.4 && next.first().isUpperCase())) flush()
        }
        flush()
        return out.toString()
    }
}
