package io.github.galis228.materialereader.ai

/**
 * AI answers are Markdown plus three picture blocks the app draws itself:
 *
 * ```graph            ```timeline              ```chart
 * Пьер -> Андрей: друг   Начало войны | Андрей уходит   Пьер: 120
 * ```                 ```                      ```
 *
 * Tables use ordinary Markdown pipes. Anything that doesn't parse is shown as plain text, so a half-streamed
 * or slightly malformed answer still reads fine.
 */
sealed interface RichBlock {
    data class Heading(val level: Int, val text: String) : RichBlock
    data class Para(val text: String) : RichBlock
    data class Items(val items: List<String>, val ordered: Boolean) : RichBlock
    data class Table(val header: List<String>, val rows: List<List<String>>) : RichBlock
    data class Graph(val nodes: List<String>, val edges: List<Edge>) : RichBlock
    data class Timeline(val items: List<Pair<String, String>>) : RichBlock
    data class Chart(val title: String, val items: List<Pair<String, Float>>) : RichBlock
    data class Quote(val text: String) : RichBlock
    data class Code(val text: String) : RichBlock
}

data class Edge(val from: String, val to: String, val label: String)

object Rich {
    fun parse(md: String): List<RichBlock> {
        val out = ArrayList<RichBlock>()
        val lines = md.replace("\r", "").lines()
        var i = 0
        val para = StringBuilder()
        fun flushPara() { if (para.isNotBlank()) out += RichBlock.Para(para.toString().trim()); para.clear() }
        while (i < lines.size) {
            val raw = lines[i]
            val l = raw.trim()
            when {
                l.startsWith("```") -> {
                    flushPara()
                    val kind = l.removePrefix("```").trim().lowercase()
                    val body = ArrayList<String>()
                    i++
                    while (i < lines.size && !lines[i].trim().startsWith("```")) { body += lines[i]; i++ }
                    if (kind != "people") out += block(kind, body) // data for the character map, not drawn
                }
                l.isEmpty() -> flushPara()
                l.startsWith("#") -> { flushPara(); val lvl = l.takeWhile { it == '#' }.length; out += RichBlock.Heading(lvl.coerceIn(1, 4), l.drop(lvl).trim()) }
                l.startsWith("|") -> {
                    flushPara()
                    val rows = ArrayList<List<String>>()
                    while (i < lines.size && lines[i].trim().startsWith("|")) {
                        val cells = lines[i].trim().trim('|').split('|').map { it.trim() }
                        if (!cells.all { c -> c.isNotEmpty() && c.all { it == '-' || it == ':' || it == ' ' } }) rows += cells
                        i++
                    }
                    i--
                    if (rows.size >= 2) out += RichBlock.Table(rows.first(), rows.drop(1)) else rows.firstOrNull()?.let { out += RichBlock.Para(it.joinToString(" · ")) }
                }
                bullet(l) != null || numbered(l) != null -> {
                    flushPara()
                    val ordered = numbered(l) != null
                    val items = ArrayList<String>()
                    while (i < lines.size) {
                        val t = lines[i].trim()
                        val item = (if (ordered) numbered(t) else bullet(t)) ?: break
                        items += item; i++
                    }
                    i--
                    out += RichBlock.Items(items, ordered)
                }
                l.startsWith(">") -> { flushPara(); out += RichBlock.Quote(l.trimStart('>', ' ')) }
                else -> { if (para.isNotEmpty()) para.append(' '); para.append(l) }
            }
            i++
        }
        flushPara()
        return out
    }

    private fun bullet(l: String) = if ((l.startsWith("- ") || l.startsWith("* ") || l.startsWith("• ")) && l.length > 2) l.substring(2).trim() else null
    private fun numbered(l: String) = Regex("^(\\d{1,2})[.)]\\s+(.+)").find(l)?.groupValues?.get(2)

    private fun block(kind: String, body: List<String>): RichBlock {
        val lines = body.map { it.trim().trimStart('-', '•', '*', ' ') }.filter { it.isNotEmpty() }
        return when (kind) {
            "graph", "граф" -> {
                val edges = ArrayList<Edge>(); val nodes = LinkedHashSet<String>()
                for (l in lines) {
                    val m = Regex("^(.+?)\\s*(?:->|→|—|–|<->|↔)\\s*(.+?)(?:\\s*[:(]\\s*(.+?)\\)?)?$").find(l)
                    if (m == null) { nodes += l.substringBefore(':').trim(); continue }
                    val a = m.groupValues[1].trim(); val b = m.groupValues[2].trim()
                    if (a.isEmpty() || b.isEmpty()) continue
                    nodes += a; nodes += b; edges += Edge(a, b, m.groupValues[3].trim())
                }
                if (nodes.isEmpty()) RichBlock.Code(body.joinToString("\n")) else RichBlock.Graph(nodes.toList(), edges)
            }
            "timeline", "хронология" -> {
                val items = lines.mapNotNull { l ->
                    val i = l.indexOf('|').takeIf { it > 0 } ?: l.indexOf(" — ").takeIf { it > 0 } ?: l.indexOf(" - ").takeIf { it > 0 } ?: return@mapNotNull null
                    val sep = if (l[i] == '|') 1 else 3
                    l.substring(0, i).trim() to l.substring(i + sep).trim()
                }
                if (items.isEmpty()) RichBlock.Code(body.joinToString("\n")) else RichBlock.Timeline(items)
            }
            "chart", "bar", "график" -> {
                var title = ""
                val items = lines.mapNotNull { l ->
                    if (l.startsWith("title:", true)) { title = l.substringAfter(':').trim(); return@mapNotNull null }
                    val m = Regex("^(.+?)\\s*[:|]\\s*(-?[\\d\\s]+(?:[.,]\\d+)?)\\s*%?$").find(l) ?: return@mapNotNull null
                    val v = m.groupValues[2].replace(" ", "").replace(',', '.').toFloatOrNull() ?: return@mapNotNull null
                    m.groupValues[1].trim() to v
                }
                if (items.isEmpty()) RichBlock.Code(body.joinToString("\n")) else RichBlock.Chart(title, items)
            }
            else -> {
                // a table or list written inside a fence still deserves to be drawn
                val inner = parse(body.joinToString("\n"))
                if (inner.size == 1 && inner[0] !is RichBlock.Para) inner[0] else RichBlock.Code(body.joinToString("\n"))
            }
        }
    }

    // ---- Builders: turn plain "A — B" lines from small models into visual Markdown here, not in the model ----

    fun table(header: List<String>, rows: List<List<String>>): String = buildString {
        append("| ").append(header.joinToString(" | ")).append(" |\n|")
        repeat(header.size) { append(" --- |") }
        append('\n')
        for (r in rows) append("| ").append(r.joinToString(" | ") { it.replace("|", "/") }).append(" |\n")
    }

    fun timeline(items: List<Pair<String, String>>) = "```timeline\n" + items.joinToString("\n") { "${it.first} | ${it.second}" } + "\n```"

    fun graph(edges: List<Edge>, extraNodes: List<String> = emptyList()) =
        "```graph\n" + (edges.map { "${it.from} -> ${it.to}" + if (it.label.isNotEmpty()) ": ${it.label}" else "" } + extraNodes).joinToString("\n") + "\n```"

    fun chart(title: String, items: List<Pair<String, Number>>) =
        "```chart\ntitle: $title\n" + items.joinToString("\n") { "${it.first}: ${it.second}" } + "\n```"
}
