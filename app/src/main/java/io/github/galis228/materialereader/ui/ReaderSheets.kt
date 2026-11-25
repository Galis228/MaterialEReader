package io.github.galis228.materialereader.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material.icons.rounded.Close
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.IconButton
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.galis228.materialereader.data.Bookmark
import io.github.galis228.materialereader.data.PageAnim
import io.github.galis228.materialereader.data.ReadMode
import io.github.galis228.materialereader.data.ReaderFont
import io.github.galis228.materialereader.data.ReaderSettings
import io.github.galis228.materialereader.data.ReaderTheme
import io.github.galis228.materialereader.parse.ParsedBook
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun TextSettingsSheet(s: ReaderSettings, onChange: (ReaderSettings) -> Unit, onDismiss: () -> Unit) {
    // Transparent scrim and a short sheet: the page stays visible above, so every change is seen right away
    val maxH = (LocalConfiguration.current.screenHeightDp * 0.40f).dp
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        scrimColor = androidx.compose.ui.graphics.Color.Transparent,
        dragHandle = { BottomSheetDefaults.DragHandle(Modifier.padding(top = 4.dp, bottom = 0.dp)) },
    ) {
        var tab by remember { mutableIntStateOf(0) }
        PrimaryTabRow(selectedTabIndex = tab) {
            Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("Текст") })
            Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("Страница") })
            Tab(selected = tab == 2, onClick = { tab = 2 }, text = { Text("Управление") })
        }
        Column(
            Modifier.fillMaxWidth().heightIn(max = maxH).verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp).navigationBarsPadding().padding(top = 12.dp, bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            when (tab) {
                0 -> {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        for (t in ReaderTheme.entries) {
                            val pc = pageColors(t, MaterialTheme.colorScheme)
                            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                                Box(
                                    Modifier.size(44.dp).clip(CircleShape).background(pc.bg)
                                        .border(BorderStroke(if (s.theme == t) 3.dp else 1.dp,
                                            if (s.theme == t) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant), CircleShape)
                                        .clickable(role = Role.RadioButton) { onChange(s.copy(theme = t)) },
                                    contentAlignment = Alignment.Center,
                                ) {
                                    if (s.theme == t) Icon(Icons.Rounded.Check, null, tint = pc.text)
                                    else Text("Аа", color = pc.text, fontSize = 13.sp)
                                }
                                Text(t.label, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Размер", Modifier.weight(1f))
                        FilledTonalIconButton(onClick = { onChange(s.copy(fontSize = (s.fontSize - 1).coerceAtLeast(12f))) }) {
                            Icon(Icons.Rounded.Remove, "Меньше")
                        }
                        Text("${s.fontSize.roundToInt()}", Modifier.width(40.dp), fontWeight = FontWeight.SemiBold,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                        FilledTonalIconButton(onClick = { onChange(s.copy(fontSize = (s.fontSize + 1).coerceAtMost(36f))) }) {
                            Icon(Icons.Rounded.Add, "Больше")
                        }
                    }
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                        ReaderFont.entries.forEachIndexed { i, f ->
                            SegmentedButton(
                                selected = s.font == f, onClick = { onChange(s.copy(font = f)) },
                                shape = SegmentedButtonDefaults.itemShape(i, ReaderFont.entries.size),
                            ) { Text(f.label, fontFamily = f.family(), maxLines = 1) }
                        }
                    }
                }
                1 -> {
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                        ReadMode.entries.forEachIndexed { i, m ->
                            SegmentedButton(
                                selected = s.mode == m, onClick = { onChange(s.copy(mode = m)) },
                                shape = SegmentedButtonDefaults.itemShape(i, ReadMode.entries.size),
                            ) { Text(m.label) }
                        }
                    }
                    if (s.mode == ReadMode.PAGES) {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            PageAnim.entries.forEach { a ->
                                FilterChip(selected = s.anim == a, onClick = { onChange(s.copy(anim = a)) }, label = { Text(a.label) })
                            }
                        }
                    }
                    LabeledSlider("Межстрочный интервал", s.lineHeight, 1.1f..2.2f, { "%.2f".format(it) }) {
                        onChange(s.copy(lineHeight = (it * 20).roundToInt() / 20f))
                    }
                    LabeledSlider("Поля", s.margin, 8f..48f, { "${it.roundToInt()} dp" }) {
                        onChange(s.copy(margin = it.roundToInt().toFloat()))
                    }
                    SwitchRow("Выравнивание по ширине", s.justify) { onChange(s.copy(justify = it)) }
                    if (s.mode == ReadMode.PAGES) SwitchRow("Разворот в альбомной ориентации", s.twoPages) { onChange(s.copy(twoPages = it)) }
                }
                else -> {
                    SwitchRow("Листать тапом по краям", s.tapToTurn) { onChange(s.copy(tapToTurn = it)) }
                    SwitchRow("Листать кнопками громкости", s.volumeKeys) { onChange(s.copy(volumeKeys = it)) }
                    SwitchRow("Не гасить экран", s.keepScreenOn) { onChange(s.copy(keepScreenOn = it)) }
                }
            }
        }
    }
}

/** The value is applied when the finger lifts, so the book isn't re-laid out on every pixel of the drag. */
@Composable
private fun LabeledSlider(label: String, v: Float, range: ClosedFloatingPointRange<Float>, fmt: (Float) -> String, onCommit: (Float) -> Unit) {
    var local by remember(v) { mutableFloatStateOf(v) }
    Column {
        Row { Text(label, Modifier.weight(1f)); Text(fmt(local), color = MaterialTheme.colorScheme.onSurfaceVariant) }
        Slider(value = local, onValueChange = { local = it }, onValueChangeFinished = { onCommit(local) }, valueRange = range)
    }
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(role = Role.Switch) { onChange(!checked) }.padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = null)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TocSheet(
    book: ParsedBook, current: Int, bookmarks: List<Bookmark>,
    onPick: (Int) -> Unit, onPickBookmark: (Bookmark) -> Unit, onDeleteBookmark: (Bookmark) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        var tab by remember { mutableIntStateOf(0) }
        PrimaryTabRow(selectedTabIndex = tab) {
            Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("Главы") })
            Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("Закладки · ${bookmarks.size}") })
        }
        if (tab == 0) {
            val state = rememberLazyListState((current - 3).coerceAtLeast(0))
            LazyColumn(state = state, modifier = Modifier.navigationBarsPadding()) {
                itemsIndexed(book.chapters) { i, ch ->
                    val selected = i == current
                    Row(
                        Modifier.fillMaxWidth().clickable { onPick(i) }
                            .background(if (selected) MaterialTheme.colorScheme.secondaryContainer else androidx.compose.ui.graphics.Color.Transparent)
                            .padding(horizontal = 24.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(ch.title, Modifier.weight(1f), fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                            maxLines = 2, overflow = TextOverflow.Ellipsis)
                        val pct = if (book.blocks.size > 1) ch.startBlock * 100 / (book.blocks.size - 1) else 0
                        Text("$pct%", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        } else if (bookmarks.isEmpty()) {
            Text(
                "Закладок пока нет. Открой панель тапом по центру и нажми на значок закладки вверху.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(24.dp).navigationBarsPadding(),
            )
        } else {
            LazyColumn(modifier = Modifier.navigationBarsPadding()) {
                itemsIndexed(bookmarks, key = { _, m -> m.createdAt }) { _, m ->
                    Row(
                        Modifier.fillMaxWidth().clickable { onPickBookmark(m) }.padding(start = 24.dp, end = 8.dp, top = 12.dp, bottom = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(m.chapter.ifEmpty { "Без главы" }, style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(m.snippet, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
                        }
                        IconButton(onClick = { onDeleteBookmark(m) }) { Icon(Icons.Rounded.Close, "Удалить закладку") }
                    }
                }
            }
        }
    }
}
