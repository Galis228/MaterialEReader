package io.github.galis228.materialereader.parse

import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.parser.Parser
import java.util.Base64
import java.util.zip.ZipInputStream

object Fb2Parser {

    fun parseZip(bytes: ByteArray, fileName: String): ParsedBook {
        ZipInputStream(bytes.inputStream()).use { zip ->
            while (true) {
                val e = zip.nextEntry ?: break
                if (!e.isDirectory && e.name.lowercase().endsWith(".fb2")) return parse(zip.readBytes(), fileName)
            }
        }
        error("В архиве нет файла .fb2")
    }

    fun parse(bytes: ByteArray, fileName: String): ParsedBook {
        val xml = decodeXml(bytes)
        val doc: Document = Jsoup.parse(xml, "", Parser.xmlParser())
        val titleInfo = doc.selectFirst("description > title-info")
        val title = titleInfo?.selectFirst("book-title")?.text()?.normalizeSpace()
            ?.takeIf { it.isNotEmpty() } ?: fileName.substringBefore('.')
        val author = titleInfo?.select("author")?.joinToString(", ") { a ->
            listOf("first-name", "middle-name", "last-name")
                .mapNotNull { a.selectFirst(it)?.text()?.normalizeSpace()?.takeIf(String::isNotEmpty) }
                .ifEmpty { listOfNotNull(a.selectFirst("nickname")?.text()) }
                .joinToString(" ")
        }?.trim().orEmpty()

        val cover = titleInfo?.selectFirst("coverpage image")?.let { img ->
            val href = img.attributes().asList().firstOrNull { it.key.endsWith("href") }?.value
            href?.removePrefix("#")?.let { id -> doc.select("binary").firstOrNull { it.attr("id") == id } }
        }?.let { runCatching { Base64.getMimeDecoder().decode(it.text()) }.getOrNull() }

        val blocks = ArrayList<Block>()
        // Main body only (notes bodies have name="notes"/"comments")
        val allBodies = doc.select("FictionBook > body").ifEmpty { doc.select("body") }
        val bodies = allBodies.filter { it.attr("name").isEmpty() }.ifEmpty { allBodies.take(1) }
        for (body in bodies) walk(body, blocks)
        // Footnotes: sections with an id inside the notes bodies
        val notes = HashMap<String, String>()
        for (b in allBodies.filter { it.attr("name").isNotEmpty() }) {
            for (sec in b.select("section[id]")) {
                val text = sec.children().filter { it.tagName() != "title" }.joinToString("\n") { it.text().normalizeSpace() }.trim()
                    .ifEmpty { sec.text().normalizeSpace() }
                if (text.isNotEmpty()) notes[sec.attr("id")] = cleanNote(text)
            }
        }
        return ParsedBook(title, author, blocks, chaptersFromHeadings(blocks, title), cover, notes)
    }

    private fun walk(el: Element, out: MutableList<Block>) {
        for (child in el.children()) {
            when (child.tagName()) {
                "title" -> {
                    val t = child.select("p").joinToString(". ") { it.text().normalizeSpace() }
                        .ifEmpty { child.text().normalizeSpace() }
                    if (t.isNotEmpty()) out += Block(Block.Kind.HEADING, t)
                }
                "p", "subtitle" -> paragraph(child, Block.Kind.PARAGRAPH)?.let { out += it }
                "v" -> paragraph(child, Block.Kind.QUOTE)?.let { out += it }
                "empty-line" -> {}
                "epigraph" -> child.select("p, text-author").forEach { p ->
                    p.text().normalizeSpace().takeIf { it.isNotEmpty() }?.let { out += Block(Block.Kind.EPIGRAPH, it) }
                }
                "cite" -> child.select("p, v, text-author").forEach { p ->
                    p.text().normalizeSpace().takeIf { it.isNotEmpty() }?.let { out += Block(Block.Kind.QUOTE, it) }
                }
                "section", "poem", "stanza" -> walk(child, out)
                "image", "annotation" -> {}
                else -> walk(child, out)
            }
        }
    }

    /** Paragraph text with footnote links: <a l:href="#n1" type="note">[1]</a>. */
    private fun paragraph(el: Element, kind: Block.Kind): Block? {
        val tb = TextBuilder()
        fun walkInline(e: Element) {
            for (n in e.childNodes()) when (n) {
                is org.jsoup.nodes.TextNode -> tb.text(n.wholeText)
                is Element -> {
                    val href = n.attributes().asList().firstOrNull { it.key.endsWith("href") }?.value.orEmpty()
                    if (n.tagName() == "a" && href.startsWith("#") && (n.attr("type") == "note" || n.text().trim().matches(NOTE_LABEL)))
                        tb.ref(n.text(), href.removePrefix("#"))
                    else walkInline(n)
                }
            }
        }
        walkInline(el)
        if (tb.isEmpty()) return null
        val (text, refs) = tb.build()
        return Block(kind, text, refs)
    }

    private val NOTE_LABEL = Regex("^[\\[({]?\\*?\\d{0,3}\\*?[\\])}]?$")

    private fun decodeXml(bytes: ByteArray): String {
        val head = String(bytes, 0, minOf(200, bytes.size), Charsets.ISO_8859_1)
        val enc = Regex("encoding=[\"']([^\"']+)[\"']").find(head)?.groupValues?.get(1)
        val cs = runCatching { charset(enc ?: "UTF-8") }.getOrDefault(Charsets.UTF_8)
        return String(bytes, cs).removePrefix("﻿")
    }
}
