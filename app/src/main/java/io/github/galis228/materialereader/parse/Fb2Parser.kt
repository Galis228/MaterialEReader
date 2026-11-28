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
        val anchors = HashMap<String, Int>()
        val wanted = LinkedHashSet<String>()
        for (body in bodies) walk(body, blocks, anchors, wanted)
        val images = HashMap<String, ByteArray>()
        var total = 0L
        for (id in wanted) {
            val bin = doc.select("binary").firstOrNull { it.attr("id") == id } ?: continue
            val data = runCatching { Base64.getMimeDecoder().decode(bin.text()) }.getOrNull() ?: continue
            if (data.size > 6_000_000 || total + data.size > 80_000_000) continue
            images[id] = data; total += data.size
        }
        val shown = blocks.filter { it.kind != Block.Kind.IMAGE || it.src in images }
        val shift = IntArray(blocks.size + 1).also { a -> var d = 0; blocks.forEachIndexed { i, b -> a[i] = i - d; if (b.kind == Block.Kind.IMAGE && b.src !in images) d++ }; a[blocks.size] = blocks.size - d }
        blocks.clear(); blocks.addAll(shown)
        anchors.replaceAll { _, v -> shift[v.coerceIn(0, shift.lastIndex)] }
        // Footnotes: sections with an id inside the notes bodies
        val notes = HashMap<String, String>()
        for (b in allBodies.filter { it.attr("name").isNotEmpty() }) {
            for (sec in b.select("section[id]")) {
                val text = sec.children().filter { it.tagName() != "title" }.joinToString("\n") { it.text().normalizeSpace() }.trim()
                    .ifEmpty { sec.text().normalizeSpace() }
                if (text.isNotEmpty()) notes[sec.attr("id")] = cleanNote(text)
            }
        }
        return ParsedBook(title, author, blocks, chaptersFromHeadings(blocks, title), cover, notes, images, anchors,
            description = titleInfo?.selectFirst("annotation")?.text()?.normalizeSpace().orEmpty().take(3000),
            year = (titleInfo?.selectFirst("date")?.let { it.attr("value").ifEmpty { it.text() } } ?: doc.selectFirst("publish-info > year")?.text()).orEmpty()
                .let { Regex("\\d{4}").find(it)?.value.orEmpty() },
            genres = titleInfo?.select("genre")?.map { it.text().trim() }?.filter { it.isNotEmpty() }.orEmpty().take(6))
    }

    private fun href(e: Element) = e.attributes().asList().firstOrNull { it.key.endsWith("href") }?.value.orEmpty()

    private fun walk(el: Element, out: MutableList<Block>, anchors: MutableMap<String, Int>, wanted: MutableSet<String>) {
        for (child in el.children()) {
            if (child.id().isNotEmpty()) anchors.putIfAbsent("#" + child.id(), out.size)
            when (child.tagName()) {
                "title" -> {
                    val t = child.select("p").joinToString(". ") { it.text().normalizeSpace() }
                        .ifEmpty { child.text().normalizeSpace() }
                    if (t.isNotEmpty()) out += Block(Block.Kind.HEADING, t)
                }
                "p", "subtitle" -> paragraph(child, Block.Kind.PARAGRAPH)?.let { out += it }
                "v" -> paragraph(child, Block.Kind.VERSE)?.let { out += it }
                "empty-line" -> {}
                "epigraph" -> child.select("p, text-author").forEach { p ->
                    p.text().normalizeSpace().takeIf { it.isNotEmpty() }?.let { out += Block(Block.Kind.EPIGRAPH, it) }
                }
                "cite" -> child.select("p, v, text-author").forEach { p ->
                    p.text().normalizeSpace().takeIf { it.isNotEmpty() }?.let { out += Block(Block.Kind.QUOTE, it) }
                }
                "stanza" -> {
                    // a stanza: its lines in one block, line breaks kept
                    val tb = TextBuilder()
                    child.children().filter { it.tagName() == "v" }.forEach { v -> tb.newline(); paragraphInto(v, tb) }
                    val (text, refs) = tb.build()
                    if (text.isNotBlank()) out += Block(Block.Kind.VERSE, text.trim(), refs)
                }
                "image" -> href(child).removePrefix("#").takeIf { it.isNotEmpty() }?.let { id ->
                    wanted += id; out += Block(Block.Kind.IMAGE, child.attr("title").ifEmpty { child.attr("alt") }, src = id)
                }
                "table" -> {
                    val rows = child.select("tr").map { tr -> tr.children().map { it.text().normalizeSpace() } }.filter { r -> r.any { it.isNotEmpty() } }
                    if (rows.isNotEmpty()) out += Block(Block.Kind.TABLE, rows.joinToString("\n") { it.joinToString(Block.TABLE_SEP) })
                }
                "section", "poem" -> walk(child, out, anchors, wanted)
                "annotation" -> {}
                else -> walk(child, out, anchors, wanted)
            }
        }
    }

    /** Paragraph text with footnote links: <a l:href="#n1" type="note">[1]</a>, and links to other places. */
    private fun paragraph(el: Element, kind: Block.Kind): Block? {
        val tb = TextBuilder()
        paragraphInto(el, tb)
        if (tb.isEmpty()) return null
        val (text, refs) = tb.build()
        return Block(kind, text, refs, links = tb.links())
    }

    private fun paragraphInto(e: Element, tb: TextBuilder) {
        for (n in e.childNodes()) when (n) {
            is org.jsoup.nodes.TextNode -> tb.text(n.wholeText)
            is Element -> {
                val href = href(n)
                when {
                    n.tagName() == "a" && href.startsWith("#") && (n.attr("type") == "note" || n.text().trim().matches(NOTE_LABEL)) ->
                        tb.ref(n.text(), href.removePrefix("#"))
                    n.tagName() == "a" && href.startsWith("#") -> { tb.beginLink(href); paragraphInto(n, tb); tb.endLink() }
                    else -> paragraphInto(n, tb)
                }
            }
        }
    }

    private val NOTE_LABEL = Regex("^[\\[({]?\\*?\\d{0,3}\\*?[\\])}]?$")

    private fun decodeXml(bytes: ByteArray): String {
        val head = String(bytes, 0, minOf(200, bytes.size), Charsets.ISO_8859_1)
        val enc = Regex("encoding=[\"']([^\"']+)[\"']").find(head)?.groupValues?.get(1)
        val cs = runCatching { charset(enc ?: "UTF-8") }.getOrDefault(Charsets.UTF_8)
        return String(bytes, cs).removePrefix("﻿")
    }
}
