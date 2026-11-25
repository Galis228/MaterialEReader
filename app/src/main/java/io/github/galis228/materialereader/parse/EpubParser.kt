package io.github.galis228.materialereader.parse

import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode
import org.jsoup.parser.Parser
import java.net.URLDecoder
import java.util.zip.ZipInputStream

object EpubParser {

    fun parse(bytes: ByteArray, fileName: String): ParsedBook {
        val files = HashMap<String, ByteArray>()
        ZipInputStream(bytes.inputStream()).use { zip ->
            while (true) {
                val e = zip.nextEntry ?: break
                if (!e.isDirectory) files[e.name] = zip.readBytes()
            }
        }
        fun file(path: String) = files[path] ?: files.entries.firstOrNull { it.key.equals(path, true) }?.value

        val container = file("META-INF/container.xml") ?: error("Не найден META-INF/container.xml")
        val opfPath = Jsoup.parse(String(container, Charsets.UTF_8), "", Parser.xmlParser())
            .selectFirst("rootfile")?.attr("full-path") ?: error("Не найден OPF")
        val opfDir = opfPath.substringBeforeLast('/', "")
        val opf = Jsoup.parse(String(file(opfPath) ?: error("OPF пуст"), Charsets.UTF_8), "", Parser.xmlParser())

        fun meta(tag: String) = opf.getElementsByTag(tag).firstOrNull()?.text()?.normalizeSpace()?.takeIf { it.isNotEmpty() }
        val title = meta("dc:title") ?: fileName.substringBeforeLast('.')
        val author = opf.getElementsByTag("dc:creator").joinToString(", ") { it.text().normalizeSpace() }

        data class Item(val id: String, val href: String, val type: String, val props: String)
        val manifest = opf.getElementsByTag("item").map {
            Item(it.attr("id"), resolve(opfDir, it.attr("href")), it.attr("media-type"), it.attr("properties"))
        }
        val byId = manifest.associateBy { it.id }

        val coverId = opf.getElementsByTag("meta").firstOrNull { it.attr("name") == "cover" }?.attr("content")
        val coverItem = manifest.firstOrNull { "cover-image" in it.props }
            ?: coverId?.let { byId[it] }?.takeIf { it.type.startsWith("image") }
            ?: manifest.firstOrNull { it.type.startsWith("image") && "cover" in it.href.lowercase() }
        val cover = coverItem?.let { file(it.href) }

        // TOC titles by file (EPUB 2 NCX / EPUB 3 nav)
        val tocTitles = HashMap<String, String>()
        manifest.firstOrNull { it.type == "application/x-dtbncx+xml" }?.let { ncxItem ->
            file(ncxItem.href)?.let { raw ->
                val ncx = Jsoup.parse(String(raw, Charsets.UTF_8), "", Parser.xmlParser())
                val dir = ncxItem.href.substringBeforeLast('/', "")
                ncx.getElementsByTag("navPoint").forEach { np ->
                    val label = np.getElementsByTag("text").firstOrNull()?.text()?.normalizeSpace()
                    val src = np.getElementsByTag("content").firstOrNull()?.attr("src")
                    if (!label.isNullOrEmpty() && src != null) tocTitles.putIfAbsent(resolve(dir, src.substringBefore('#')), label)
                }
            }
        }

        val blocks = ArrayList<Block>()
        val chapters = ArrayList<Chapter>()
        val spine = opf.getElementsByTag("itemref").mapNotNull { byId[it.attr("idref")] }
        for (item in spine) {
            if (!item.type.contains("html")) continue
            val raw = file(item.href) ?: continue
            val html = Jsoup.parse(String(raw, Charsets.UTF_8))
            val start = blocks.size
            collect(html.body(), blocks)
            if (blocks.size == start) continue
            val heading = blocks.subList(start, blocks.size).firstOrNull { it.kind == Block.Kind.HEADING }?.text
            val chTitle = tocTitles[item.href] ?: heading
            if (chTitle != null) chapters += Chapter(chTitle.take(120), start)
            else if (chapters.isEmpty()) chapters += Chapter("Начало", start)
        }
        if (chapters.isEmpty()) chapters += Chapter(title, 0)
        return ParsedBook(title, author, blocks, chapters, cover)
    }

    private val blockTags = setOf(
        "p", "div", "h1", "h2", "h3", "h4", "h5", "h6", "blockquote", "li", "section", "article",
        "ul", "ol", "table", "tr", "td", "th", "pre", "hr", "figure", "figcaption", "dd", "dt", "dl", "body", "header", "footer", "aside",
    )

    /** Walks the DOM; a block element that has no block children becomes one Block. */
    private fun collect(el: Element, out: MutableList<Block>) {
        val hasBlockChild = el.children().any { it.tagName() in blockTags }
        val tag = el.tagName()
        if (tag == "hr") { out += Block(Block.Kind.SEPARATOR, "* * *"); return }
        if (!hasBlockChild) {
            val text = inlineText(el).normalizeSpace()
            if (text.isEmpty()) return
            val kind = when {
                tag.length == 2 && tag[0] == 'h' && tag[1].isDigit() -> Block.Kind.HEADING
                tag == "blockquote" || el.parents().any { it.tagName() == "blockquote" } -> Block.Kind.QUOTE
                else -> Block.Kind.PARAGRAPH
            }
            out += Block(kind, if (tag == "li") "• $text" else text)
            return
        }
        // mixed content: loose text between block children becomes its own paragraph
        val loose = StringBuilder()
        fun flushLoose() {
            val s = loose.toString().normalizeSpace(); loose.clear()
            if (s.isNotEmpty()) out += Block(Block.Kind.PARAGRAPH, s)
        }
        for (n in el.childNodes()) {
            if (n is Element && n.tagName() in blockTags) { flushLoose(); collect(n, out) }
            else loose.append(nodeText(n))
        }
        flushLoose()
    }

    private fun inlineText(el: Element): String = buildString { el.childNodes().forEach { append(nodeText(it)) } }

    private fun nodeText(n: Node): String = when (n) {
        is TextNode -> n.wholeText
        is Element -> if (n.tagName() == "br") " " else if (n.tagName() in setOf("script", "style")) "" else inlineText(n)
        else -> ""
    }

    private fun resolve(base: String, href: String): String {
        val decoded = runCatching { URLDecoder.decode(href, "UTF-8") }.getOrDefault(href)
        val parts = (if (base.isEmpty()) emptyList() else base.split('/')).toMutableList()
        for (p in decoded.split('/')) {
            when (p) {
                "", "." -> {}
                ".." -> if (parts.isNotEmpty()) parts.removeAt(parts.lastIndex)
                else -> parts += p
            }
        }
        return parts.joinToString("/")
    }
}
