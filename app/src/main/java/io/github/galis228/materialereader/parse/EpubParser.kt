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
        val docs = HashMap<String, org.jsoup.nodes.Document>()
        for (item in spine) {
            if (!item.type.contains("html")) continue
            val raw = file(item.href) ?: continue
            val html = Jsoup.parse(String(raw, Charsets.UTF_8))
            docs[item.href] = html
            val start = blocks.size
            collect(html.body(), blocks, item.href)
            if (blocks.size == start) continue
            val heading = blocks.subList(start, blocks.size).firstOrNull { it.kind == Block.Kind.HEADING }?.text
            val chTitle = tocTitles[item.href] ?: heading
            if (chTitle != null) chapters += Chapter(chTitle.take(120), start)
            else if (chapters.isEmpty()) chapters += Chapter("Начало", start)
        }
        if (chapters.isEmpty()) chapters += Chapter(title, 0)
        // Footnote texts: the elements the references point to, possibly in another file of the book
        val notes = HashMap<String, String>()
        for (b in blocks) for (r in b.refs) {
            val id = r.id ?: continue
            if (id in notes) continue
            val f = id.substringBefore('#'); val frag = id.substringAfter('#', "")
            val target = docs[f]?.let { d -> if (frag.isEmpty()) d.body() else d.getElementById(frag) } ?: continue
            // the id often sits on the little back-link anchor: take the whole note around it
            val holder = if (target.tagName() in setOf("a", "span", "sup", "sub", "b", "i", "em", "strong"))
                target.parents().firstOrNull { it.tagName() in setOf("p", "li", "aside", "div", "section", "dd", "td") } ?: target else target
            val text = cleanNote(holder.text().normalizeSpace())
            if (text.isNotEmpty()) notes[id] = text.take(4000)
        }
        return ParsedBook(title, author, blocks, chapters, cover, notes)
    }

    private val blockTags = setOf(
        "p", "div", "h1", "h2", "h3", "h4", "h5", "h6", "blockquote", "li", "section", "article",
        "ul", "ol", "table", "tr", "td", "th", "pre", "hr", "figure", "figcaption", "dd", "dt", "dl", "body", "header", "footer", "aside",
    )

    /** Footnote/endnote containers: shown in a popup, not as text in the flow. */
    /** Blocks of a standalone HTML document (used by the MOBI reader). */
    fun htmlBlocks(html: String): List<Block> {
        val out = ArrayList<Block>()
        collect(Jsoup.parse(html).body(), out, "book.html")
        return out
    }

    private fun isNoteContainer(el: Element): Boolean {
        val t = (el.attr("epub:type") + " " + el.attr("role")).lowercase()
        return listOf("footnote", "endnote", "rearnote", "doc-footnote", "doc-endnote").any { it in t }
    }

    private val NOTE_LABEL = Regex("^[\\[({]?\\*{0,3}\\d{0,3}\\*?[\\])}]?$")

    /** Is this link a footnote reference? */
    private fun isNoteRef(a: Element): Boolean {
        val href = a.attr("href")
        if ('#' !in href || href.startsWith("http")) return false
        val t = (a.attr("epub:type") + " " + a.attr("role")).lowercase()
        if ("noteref" in t) return true
        val label = a.text().trim()
        return label.isNotEmpty() && label.length <= 6 && label.matches(NOTE_LABEL)
    }

    /** Walks the DOM; a block element that has no block children becomes one Block. */
    private fun collect(el: Element, out: MutableList<Block>, file: String) {
        if (isNoteContainer(el)) return
        val hasBlockChild = el.children().any { it.tagName() in blockTags }
        val tag = el.tagName()
        if (tag == "hr") { out += Block(Block.Kind.SEPARATOR, "* * *"); return }
        if (!hasBlockChild) {
            val tb = TextBuilder()
            if (tag == "li") tb.text("• ")
            inline(el, tb, file)
            val (text, refs) = tb.build()
            if (text.isEmpty() || text == "•") return
            val kind = when {
                tag.length == 2 && tag[0] == 'h' && tag[1].isDigit() -> Block.Kind.HEADING
                tag == "blockquote" || el.parents().any { it.tagName() == "blockquote" } -> Block.Kind.QUOTE
                else -> Block.Kind.PARAGRAPH
            }
            out += Block(kind, text, refs)
            return
        }
        // mixed content: loose text between block children becomes its own paragraph
        var loose = TextBuilder()
        fun flushLoose() {
            val (t, refs) = loose.build(); loose = TextBuilder()
            if (t.isNotEmpty()) out += Block(Block.Kind.PARAGRAPH, t, refs)
        }
        for (n in el.childNodes()) {
            if (n is Element && n.tagName() in blockTags) { flushLoose(); collect(n, out, file) }
            else node(n, loose, file)
        }
        flushLoose()
    }

    private fun inline(el: Element, tb: TextBuilder, file: String) { el.childNodes().forEach { node(it, tb, file) } }

    private fun node(n: Node, tb: TextBuilder, file: String) {
        when (n) {
            is TextNode -> tb.text(n.wholeText)
            is Element -> when {
                n.tagName() == "br" -> tb.text(" ")
                n.tagName() in setOf("script", "style") || isNoteContainer(n) -> {}
                n.tagName() == "a" && isNoteRef(n) -> {
                    val href = n.attr("href")
                    val target = (if (href.startsWith("#")) file else resolve(file.substringBeforeLast('/', ""), href.substringBefore('#'))) +
                        "#" + href.substringAfter('#')
                    tb.ref(n.text(), target)
                }
                // <sup><a>1</a></sup> — the link inside carries the reference
                else -> inline(n, tb, file)
            }
        }
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
