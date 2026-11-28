package io.github.galis228.materialereader.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

internal data class Person(val name: String, val group: String, val about: String, val mentions: Int, val links: List<Pair<String, String>>)

/** Reads the ```people block written by BookAi.charactersMarkdown. */
internal fun parsePeople(md: String): List<Person> {
    val body = Regex("```people\\n([\\s\\S]*?)```").find(md)?.groupValues?.get(1) ?: return emptyList()
    return body.lines().mapNotNull { l ->
        val f = l.split(" | ")
        if (f.size < 5 || f[0].isBlank()) null
        else Person(f[0].trim(), f[1].trim(), f[2].trim(), f[3].trim().toIntOrNull() ?: 0,
            f[4].split(';').mapNotNull { p -> p.trim().takeIf { it.isNotEmpty() }?.let { it.substringBefore(':').trim() to it.substringAfter(':', "").trim() } })
    }
}

/** Character map in three views: one person and their ties, families/sides, and a searchable list. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CharacterMap(md: String, modifier: Modifier = Modifier) {
    val people = remember(md) { parsePeople(md).sortedByDescending { it.mentions } }
    if (people.size < 2) { RichAnswer(md, modifier); return }
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var focus by remember(md) { mutableStateOf(people.first().name) }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            listOf("Фокус", "Семьи", "Список").forEachIndexed { i, t ->
                SegmentedButton(selected = tab == i, onClick = { tab = i }, shape = SegmentedButtonDefaults.itemShape(i, 3)) { Text(t) }
            }
        }
        when (tab) {
            0 -> FocusView(people, focus) { focus = it }
            1 -> FamiliesView(people) { focus = it; tab = 0 }
            else -> ListView(people) { focus = it; tab = 0 }
        }
    }
}

private val palette = listOf(0xFF6750A4, 0xFF006A6A, 0xFF8B5000, 0xFF2E6A2E, 0xFFB3261E, 0xFF1D5FA8, 0xFF7D5260, 0xFF5B6300)
private fun groupColor(groups: List<String>, g: String) = Color(palette[(groups.indexOf(g).coerceAtLeast(0)) % palette.size])

@Composable
private fun Avatar(name: String, color: Color, size: Int = 40) {
    Surface(Modifier.size(size.dp), shape = CircleShape, color = color.copy(alpha = 0.18f)) {
        Box(contentAlignment = Alignment.Center) {
            Text(name.take(1).uppercase(), color = color, fontWeight = FontWeight.Bold, style = if (size > 48) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.titleMedium)
        }
    }
}

@Composable
private fun FocusView(people: List<Person>, focus: String, onFocus: (String) -> Unit) {
    val groups = people.map { it.group }.filter { it.isNotEmpty() }.distinct()
    val p = people.firstOrNull { it.name == focus } ?: people.first()
    // ties both ways: what this person lists and who lists them
    val ties = (p.links + people.filter { o -> o.name != p.name && o.links.any { it.first.equals(p.name, true) } && p.links.none { it.first.equals(o.name, true) } }
        .map { o -> o.name to (o.links.first { it.first.equals(p.name, true) }.second) }).distinctBy { it.first.lowercase() }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        androidx.compose.foundation.lazy.LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            items(people.size) { i ->
                val o = people[i]
                FilterChip(selected = o.name == p.name, onClick = { onFocus(o.name) }, label = { Text(o.name, maxLines = 1) })
            }
        }
        val c = groupColor(groups, p.group)
        ElevatedCard(Modifier.fillMaxWidth()) {
            Column(Modifier.fillMaxWidth().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Avatar(p.name, c, 64)
                Text(p.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                if (p.group.isNotEmpty()) Text(p.group, style = MaterialTheme.typography.labelLarge, color = c)
                Text(p.about, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (p.mentions > 0) Text("Упоминаний: ${p.mentions}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (ties.isEmpty()) Text("Связей пока не видно.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        else Text("Связи", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        ties.forEach { (name, rel) ->
            val o = people.firstOrNull { it.name.equals(name, true) }
            OutlinedCard(onClick = { if (o != null) onFocus(o.name) }, modifier = Modifier.fillMaxWidth(), enabled = o != null) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Avatar(name, groupColor(groups, o?.group ?: ""))
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(name, fontWeight = FontWeight.SemiBold)
                        if (rel.isNotEmpty()) Text(rel, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                        o?.about?.takeIf { it.isNotEmpty() }?.let { Text(it, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FamiliesView(people: List<Person>, onPick: (String) -> Unit) {
    val groups = people.map { it.group }.filter { it.isNotEmpty() }.distinct()
    val byGroup = people.groupBy { it.group.ifEmpty { "Остальные" } }.toList().sortedBy { (g, _) -> if (g == "Остальные") Int.MAX_VALUE else groups.indexOf(g) }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (groups.isEmpty()) Text("ИИ не разделил героев на семьи — обнови карту, чтобы получить группы.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        byGroup.forEach { (g, members) ->
            val c = groupColor(groups, g)
            Surface(shape = RoundedCornerShape(20.dp), color = c.copy(alpha = 0.08f), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(10.dp).padding(0.dp)) { Surface(Modifier.fillMaxSize(), shape = CircleShape, color = c) {} }
                        Spacer(Modifier.width(8.dp))
                        Text(g, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = c, modifier = Modifier.weight(1f))
                        Text("${members.size}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        members.forEach { m ->
                            AssistChip(onClick = { onPick(m.name) }, label = { Text(m.name) }, leadingIcon = {
                                Text(m.name.take(1), color = c, fontWeight = FontWeight.Bold)
                            })
                        }
                    }
                    // ties between groups, so the conflict is visible at a glance
                    val outside = members.flatMap { m -> m.links.mapNotNull { (n, r) ->
                        val o = people.firstOrNull { it.name.equals(n, true) } ?: return@mapNotNull null
                        if (o.group == m.group) null else "${m.name} → ${o.name}" + if (r.isNotEmpty()) " ($r)" else ""
                    } }.take(4)
                    outside.forEach { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
            }
        }
    }
}

@Composable
private fun ListView(people: List<Person>, onPick: (String) -> Unit) {
    var q by remember { mutableStateOf("") }
    val groups = people.map { it.group }.filter { it.isNotEmpty() }.distinct()
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        OutlinedTextField(q, { q = it }, Modifier.fillMaxWidth(), singleLine = true, placeholder = { Text("Найти персонажа") },
            leadingIcon = { Icon(Icons.Rounded.Search, null) }, shape = RoundedCornerShape(28.dp))
        people.filter { q.isBlank() || it.name.contains(q, true) || it.about.contains(q, true) || it.group.contains(q, true) }.forEach { p ->
            ListItem(
                leadingContent = { Avatar(p.name, groupColor(groups, p.group)) },
                headlineContent = { Text(p.name, fontWeight = FontWeight.SemiBold) },
                supportingContent = { Text(listOf(p.group, p.about).filter { it.isNotEmpty() }.joinToString(" · "), maxLines = 2, overflow = TextOverflow.Ellipsis) },
                trailingContent = { if (p.mentions > 0) Text("${p.mentions}", style = MaterialTheme.typography.labelMedium) },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                modifier = Modifier.clickableNoRipple { onPick(p.name) },
            )
        }
    }
}
