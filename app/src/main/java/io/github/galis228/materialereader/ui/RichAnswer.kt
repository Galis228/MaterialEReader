package io.github.galis228.materialereader.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.galis228.materialereader.ai.Rich
import io.github.galis228.materialereader.ai.RichBlock
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/** Draws an AI answer: text, lists and tables, plus graphs, timelines and bar charts. */
@Composable
fun RichAnswer(md: String, modifier: Modifier = Modifier) {
    val blocks = remember(md) { Rich.parse(md) }
    SelectionContainer(modifier) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            for (b in blocks) when (b) {
                is RichBlock.Heading -> Text(inline(b.text), fontWeight = FontWeight.SemiBold,
                    style = if (b.level <= 2) MaterialTheme.typography.titleMedium else MaterialTheme.typography.titleSmall)
                is RichBlock.Para -> Text(inline(b.text), style = MaterialTheme.typography.bodyLarge)
                is RichBlock.Quote -> Row(Modifier.height(IntrinsicSize.Min)) {
                    Box(Modifier.width(3.dp).fillMaxHeight().background(MaterialTheme.colorScheme.primary))
                    Text(inline(b.text), Modifier.padding(start = 10.dp), fontStyle = FontStyle.Italic, style = MaterialTheme.typography.bodyMedium)
                }
                is RichBlock.Items -> Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    b.items.forEachIndexed { i, it ->
                        Row {
                            Text(if (b.ordered) "${i + 1}." else "•", Modifier.width(22.dp), color = MaterialTheme.colorScheme.primary)
                            Text(inline(it), style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
                is RichBlock.Table -> TableView(b)
                is RichBlock.Graph -> GraphView(b)
                is RichBlock.Timeline -> TimelineView(b)
                is RichBlock.Chart -> ChartView(b)
                is RichBlock.Code -> Text(b.text, style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh).padding(10.dp))
            }
        }
    }
}

/** **bold** and *italic* inside a line. */
private fun inline(s: String): AnnotatedString = buildAnnotatedString {
    var i = 0
    while (i < s.length) {
        when {
            s.startsWith("**", i) && s.indexOf("**", i + 2) > i + 2 -> {
                val e = s.indexOf("**", i + 2); val st = length
                append(s.substring(i + 2, e)); addStyle(SpanStyle(fontWeight = FontWeight.Bold), st, length); i = e + 2
            }
            s[i] == '*' && i + 1 < s.length && s[i + 1] != ' ' && s.indexOf('*', i + 1) > i + 1 -> {
                val e = s.indexOf('*', i + 1); val st = length
                append(s.substring(i + 1, e)); addStyle(SpanStyle(fontStyle = FontStyle.Italic), st, length); i = e + 1
            }
            else -> { append(s[i]); i++ }
        }
    }
}

@Composable
private fun TableView(t: RichBlock.Table) {
    val cs = MaterialTheme.colorScheme
    val cols = maxOf(t.header.size, t.rows.maxOfOrNull { it.size } ?: 0)
    Box(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
        Column(Modifier.clip(RoundedCornerShape(12.dp)).border(1.dp, cs.outlineVariant, RoundedCornerShape(12.dp))) {
            @Composable fun row(cells: List<String>, head: Boolean, odd: Boolean) {
                Row(Modifier.height(IntrinsicSize.Min).background(if (head) cs.secondaryContainer else if (odd) cs.surfaceContainerLow else cs.surface)) {
                    for (c in 0 until cols) {
                        Text(inline(cells.getOrElse(c) { "" }), Modifier.widthIn(min = 72.dp, max = 220.dp).fillMaxHeight().padding(horizontal = 10.dp, vertical = 8.dp),
                            fontWeight = if (head) FontWeight.SemiBold else null, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
            row(t.header, head = true, odd = false)
            t.rows.forEachIndexed { i, r -> row(r, head = false, odd = i % 2 == 1) }
        }
    }
}

@Composable
private fun TimelineView(t: RichBlock.Timeline) {
    val cs = MaterialTheme.colorScheme
    Column {
        t.items.forEachIndexed { i, (whenText, what) ->
            Row(Modifier.height(IntrinsicSize.Min)) {
                Box(Modifier.width(22.dp).fillMaxHeight(), contentAlignment = Alignment.TopCenter) {
                    if (i < t.items.lastIndex) Box(Modifier.padding(top = 6.dp).width(2.dp).fillMaxHeight().background(cs.primary.copy(alpha = 0.35f)))
                    Box(Modifier.padding(top = 4.dp).size(12.dp).clip(CircleShape).background(cs.primary))
                }
                Column(Modifier.padding(start = 8.dp, bottom = 14.dp)) {
                    Text(inline(whenText), color = cs.primary, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.labelLarge)
                    Text(inline(what), style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

@Composable
private fun ChartView(c: RichBlock.Chart) {
    val cs = MaterialTheme.colorScheme
    val max = c.items.maxOf { it.second }.coerceAtLeast(0.0001f)
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (c.title.isNotEmpty()) Text(c.title, style = MaterialTheme.typography.titleSmall)
        c.items.take(15).forEach { (label, v) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(label, Modifier.width(110.dp), style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Box(Modifier.weight(1f).height(18.dp)) {
                    Box(Modifier.fillMaxWidth((v / max).coerceIn(0.02f, 1f)).fillMaxHeight().clip(RoundedCornerShape(4.dp)).background(cs.primary))
                }
                Text(if (v % 1f == 0f) "${v.toInt()}" else "%.1f".format(v), Modifier.width(48.dp).padding(start = 6.dp),
                    style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant)
            }
        }
    }
}

/** People or ideas on a circle, joined by labeled lines; the most connected in the middle. */
@Composable
private fun GraphView(g: RichBlock.Graph) {
    val cs = MaterialTheme.colorScheme
    val measurer = rememberTextMeasurer()
    val names = g.nodes.take(14)
    val degree = names.associateWith { n -> g.edges.count { it.from == n || it.to == n } }
    val hub = names.maxByOrNull { degree[it] ?: 0 }?.takeIf { (degree[it] ?: 0) >= 3 }
    val ring = names.filter { it != hub }
    val nameStyle = MaterialTheme.typography.labelMedium.copy(color = cs.onSurface, fontWeight = FontWeight.SemiBold)
    val edgeStyle = MaterialTheme.typography.labelSmall.copy(color = cs.onSurfaceVariant)
    val h = if (names.size <= 4) 220.dp else 320.dp
    Canvas(Modifier.fillMaxWidth().height(h).clip(RoundedCornerShape(16.dp)).background(cs.surfaceContainerLow)) {
        val c = Offset(size.width / 2, size.height / 2)
        val rx = size.width / 2 - 56.dp.toPx(); val ry = size.height / 2 - 30.dp.toPx()
        val pos = HashMap<String, Offset>()
        hub?.let { pos[it] = c }
        ring.forEachIndexed { i, n ->
            val a = 2 * Math.PI * i / ring.size.coerceAtLeast(1) - Math.PI / 2
            pos[n] = Offset(c.x + rx * cos(a).toFloat(), c.y + ry * sin(a).toFloat())
        }
        for (e in g.edges) {
            val a = pos[e.from] ?: continue; val b = pos[e.to] ?: continue
            drawLine(cs.primary.copy(alpha = 0.4f), a, b, strokeWidth = 2.dp.toPx())
            if (e.label.isNotEmpty() && g.edges.size <= 16) {
                val t = measurer.measure(e.label.take(16), edgeStyle)
                val m = Offset((a.x + b.x) / 2 - t.size.width / 2, (a.y + b.y) / 2 - t.size.height / 2)
                drawRoundRect(cs.surfaceContainerLow, m - Offset(3f, 1f), Size(t.size.width + 6f, t.size.height + 2f), CornerRadius(6f))
                drawText(t, topLeft = m)
            }
        }
        for (n in names) {
            val p = pos[n] ?: continue
            val r = (6 + min(degree[n] ?: 0, 6)).dp.toPx()
            drawCircle(cs.primary, r, p)
            val t = measurer.measure(n.take(20), nameStyle)
            drawText(t, topLeft = Offset((p.x - t.size.width / 2).coerceIn(0f, size.width - t.size.width), (p.y + r + 2.dp.toPx()).coerceAtMost(size.height - t.size.height)))
        }
    }
    Spacer(Modifier.height(2.dp))
}
