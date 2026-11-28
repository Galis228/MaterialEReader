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
        val st = Walk()
        val spine = opf.getElementsByTag("itemref").mapNotNull { byId[it.attr("idref")] }
        val docs = HashMap<String, org.jsoup.nodes.Document>()
        for (item in spine) {
            if (!item.type.contains("html")) continue
            val raw = file(item.href) ?: continue
            val html = Jsoup.parse(String(raw, Charsets.UTF_8))
            docs[item.href] = html
            val start = blocks.size
            st.anchors.putIfAbsent(item.href, start)
            collect(html.body(), blocks, item.href, st)
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
        // illustrations the text points to (keeps memory sane: very large or too many images are skipped)
        val images = HashMap<String, ByteArray>()
        var total = 0L
        for (key in st.images) {
            val data = file(key) ?: continue
            if (data.size > 6_000_000 || total + data.size > 80_000_000) continue
            images[key] = data; total += data.size
        }
        val shown = blocks.filter { it.kind != Block.Kind.IMAGE || it.src in images }
        // dropping missing pictures shifts indexes; rebuild chapter starts and anchors accordingly
        val shift = IntArray(blocks.size + 1).also { a -> var d = 0; blocks.forEachIndexed { i, b -> a[i] = i - d; if (b.kind == Block.Kind.IMAGE && b.src !in images) d++ }; a[blocks.size] = blocks.size - d }
        return ParsedBook(title, author, shown, chapters.map { it.copy(startBlock = shift[it.startBlock.coerceIn(0, blocks.size)]) }, cover, notes,
            images, st.anchors.mapValues { shift[it.value.coerceIn(0, blocks.size)] },
            description = meta("dc:description")?.let { Jsoup.parse(it).text().normalizeSpace() }.orEmpty().take(3000),
            year = meta("dc:date")?.take(4)?.takeIf { y -> y.all { it.isDigit() } }.orEmpty(),
            genres = opf.getElementsByTag("dc:subject").map { it.text().normalizeSpace() }.filter { it.isNotEmpty() }.take(6))
    }

    /** Per-book state while walking the documents. */
    private class Walk {
        val anchors = HashMap<String, Int>()
        val images = LinkedHashSet<String>()
    }

    private val blockTags = setOf(
        "p", "div", "h1", "h2", "h3", "h4", "h5", "h6", "blockquote", "li", "section", "article",
        "ul", "ol", "table", "tr", "td", "th", "pre", "hr", "figure", "figcaption", "dd", "dt", "dl", "body", "header", "footer", "aside",
        "img", "svg",
    )

    private val verseClass = Regex("poem|stanza|verse|poetry|стих", RegexOption.IGNORE_CASE)
    private fun isVerse(el: Element) = verseClass.containsMatchIn(el.className()) || el.tagName() == "pre"

    /** Footnote/endnote containers: shown in a popup, not as text in the flow. */
    /** Blocks of a standalone HTML document (used by the MOBI reader). */
    fun htmlBlocks(html: String): List<Block> {
        val out = ArrayList<Block>()
        collect(Jsoup.parse(html).body(), out, "book.html", Walk())
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
    private fun collect(el: Element, out: MutableList<Block>, file: String, st: Walk) {
        if (isNoteContainer(el)) return
        val tag = el.tagName()
        if (el.id().isNotEmpty()) st.anchors.putIfAbsent("$file#${el.id()}", out.size)
        if (tag == "hr") { out += Block(Block.Kind.SEPARATOR, "* * *"); return }
        if (tag == "img" || tag == "svg") { image(el, out, file, st); return }
        if (tag == "table") { table(el, out, file, st); return }
        if (isVerse(el)) { verse(el, out, file, st); return }
        val hasBlockChild = el.children().any { it.tagName() in blockTags }
        if (!hasBlockChild) {
            val tb = TextBuilder()
            if (tag == "li") tb.text("• ")
            for (d in el.getElementsByAttribute("id")) if (d !== el) st.anchors.putIfAbsent("$file#${d.id()}", out.size)
            inline(el, tb, file)
            val (text, refs) = tb.build()
            if (text.isEmpty() || text == "•") return
            val kind = when {
                tag.length == 2 && tag[0] == 'h' && tag[1].isDigit() -> Block.Kind.HEADING
                tag == "blockquote" || el.parents().any { it.tagName() == "blockquote" } -> Block.Kind.QUOTE
                else -> Block.Kind.PARAGRAPH
            }
            out += Block(kind, text, refs, links = tb.links())
            return
        }
        // mixed content: loose text between block children becomes its own paragraph
        var loose = TextBuilder()
        fun flushLoose() {
            val (t, refs) = loose.build(); val links = loose.links(); loose = TextBuilder()
            if (t.isNotEmpty()) out += Block(Block.Kind.PARAGRAPH, t, refs, links = links)
        }
        for (n in el.childNodes()) {
            if (n is Element && n.tagName() in blockTags) { flushLoose(); collect(n, out, file, st) }
            else node(n, loose, file)
        }
        flushLoose()
    }

    private fun image(el: Element, out: MutableList<Block>, file: String, st: Walk) {
        val href = if (el.tagName() == "img") el.attr("src")
            else el.getElementsByTag("image").firstOrNull()?.attributes()?.asList()?.firstOrNull { it.key.endsWith("href") }?.value.orEmpty()
        if (href.isEmpty() || href.startsWith("data:") || href.startsWith("http")) return
        val key = resolve(file.substringBeforeLast('/', ""), href.substringBefore('#'))
        st.images += key
        out += Block(Block.Kind.IMAGE, el.attr("alt").normalizeSpace().take(200), src = key)
    }

    /** A table as rows of cells; a one-column "table" used for layout becomes plain paragraphs. */
    private fun table(el: Element, out: MutableList<Block>, file: String, st: Walk) {
        val rows = el.select("tr").filter { tr -> tr.parents().firstOrNull { it.tagName() == "table" } === el }
            .map { tr -> tr.children().filter { it.tagName() == "td" || it.tagName() == "th" }.map { it.text().normalizeSpace() } }
            .filter { r -> r.any { it.isNotEmpty() } }
        if (rows.isEmpty()) { el.select("img").forEach { image(it, out, file, st) }; return }
        if (rows.all { it.size <= 1 }) { rows.forEach { r -> r.firstOrNull()?.takeIf { it.isNotEmpty() }?.let { out += Block(Block.Kind.PARAGRAPH, it) } }; return }
        out += Block(Block.Kind.TABLE, rows.joinToString("\n") { it.joinToString(Block.TABLE_SEP) })
    }

    /** A poem: lines kept, one block per stanza. */
    private fun verse(el: Element, out: MutableList<Block>, file: String, st: Walk) {
        val stanzas = el.children().filter { isVerse(it) && it.children().isNotEmpty() }
        if (stanzas.isNotEmpty() && stanzas.size == el.children().count { it.tagName() in blockTags }) { stanzas.forEach { verse(it, out, file, st) }; return }
        val tb = TextBuilder()
        val pre = el.tagName() == "pre" || el.getElementsByTag("pre").isNotEmpty()
        fun walk(e: Element) {
            for (n in e.childNodes()) when {
                n is TextNode && pre -> n.wholeText.split('\n').forEachIndexed { i, part -> if (i > 0) tb.newline(); tb.text(part) }
                n is Element && n.tagName() == "br" -> tb.newline()
                n is Element && n.tagName() in blockTags -> { tb.newline(); walk(n); tb.newline() }
                n is Element && n.tagName() == "a" && isNoteRef(n) -> node(n, tb, file)
                n is Element -> walk(n)
                n is TextNode -> tb.text(n.wholeText)
            }
        }
        walk(el)
        val (text, refs) = tb.build()
        if (text.isNotBlank()) out += Block(Block.Kind.VERSE, text.trim(), refs)
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
                n.tagName() == "a" && n.attr("href").let { h -> h.isNotEmpty() && !h.contains(':') } -> {
                    // a link to another place of the book
                    val href = n.attr("href")
                    val path = if (href.startsWith("#")) file else resolve(file.substringBeforeLast('/', ""), href.substringBefore('#'))
                    val target = if ('#' in href) path + "#" + href.substringAfter('#') else path
                    tb.beginLink(target); inline(n, tb, file); tb.endLink()
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
