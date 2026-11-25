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
        // Main body only (notes bodies have name="notes")
        val bodies = doc.select("FictionBook > body").filter { it.attr("name").isEmpty() }
            .ifEmpty { doc.select("body").take(1) }
        for (body in bodies) walk(body, blocks)
        return ParsedBook(title, author, blocks, chaptersFromHeadings(blocks, title), cover)
    }

    private fun walk(el: Element, out: MutableList<Block>) {
        for (child in el.children()) {
            when (child.tagName()) {
                "title" -> {
                    val t = child.select("p").joinToString(". ") { it.text().normalizeSpace() }
                        .ifEmpty { child.text().normalizeSpace() }
                    if (t.isNotEmpty()) out += Block(Block.Kind.HEADING, t)
                }
                "p", "subtitle" -> child.text().normalizeSpace().takeIf { it.isNotEmpty() }
                    ?.let { out += Block(Block.Kind.PARAGRAPH, it) }
                "v" -> child.text().normalizeSpace().takeIf { it.isNotEmpty() }
                    ?.let { out += Block(Block.Kind.QUOTE, it) }
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

    private fun decodeXml(bytes: ByteArray): String {
        val head = String(bytes, 0, minOf(200, bytes.size), Charsets.ISO_8859_1)
        val enc = Regex("encoding=[\"']([^\"']+)[\"']").find(head)?.groupValues?.get(1)
        val cs = runCatching { charset(enc ?: "UTF-8") }.getOrDefault(Charsets.UTF_8)
        return String(bytes, cs).removePrefix("﻿")
    }
}
