package io.github.galis228.materialereader.ui

import androidx.compose.material3.TextButton
import androidx.compose.runtime.mutableStateOf
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.aspectRatio
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
import kotlinx.coroutines.launch
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
                    SwitchRow("Яркость как в системе", s.brightness < 0f) { onChange(s.copy(brightness = if (it) -1f else 0.5f)) }
                    if (s.brightness >= 0f) LabeledSlider("Яркость", s.brightness, 0.01f..1f, { "${(it * 100).roundToInt()}%" }) {
                        onChange(s.copy(brightness = it))
                    }
                    LabeledSlider("Тёплый фильтр", s.warmth, 0f..1f, { if (it < 0.01f) "выкл" else "${(it * 100).roundToInt()}%" }) {
                        onChange(s.copy(warmth = it))
                    }
                    Text("Шрифт", style = MaterialTheme.typography.titleSmall)
                    FontPicker(s, onChange)
                    Text("Цвет текста", style = MaterialTheme.typography.titleSmall)
                    ColorRow(TextPalette, s.textColor) { onChange(s.copy(textColor = it)) }
                    Text("Цвет фона", style = MaterialTheme.typography.titleSmall)
                    ColorRow(BgPalette, s.bgColor) { onChange(s.copy(bgColor = it)) }
                    Text("Цвет сносок", style = MaterialTheme.typography.titleSmall)
                    ColorRow(NotePalette, s.noteColor) { onChange(s.copy(noteColor = it)) }
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
                    SwitchRow("Перенос слов", s.hyphenate) { onChange(s.copy(hyphenate = it)) }
                    SwitchRow("Бионическое чтение (начала слов жирным)", s.bionic) { onChange(s.copy(bionic = it)) }
                    SwitchRow("Буквица в начале главы", s.dropCap) { onChange(s.copy(dropCap = it)) }
                    SwitchRow("Без спойлеров: скрыть следующие главы и число страниц", s.noSpoilers) { onChange(s.copy(noSpoilers = it)) }
                    if (s.mode == ReadMode.SCROLL) {
                        SwitchRow("Двуязычный режим: перевод под абзацами", s.bilingual) { onChange(s.copy(bilingual = it)) }
                        LabeledSlider("Скорость автопрокрутки", s.autoScrollLpm, 4f..60f, { "${it.roundToInt()} строк/мин" }) {
                            onChange(s.copy(autoScrollLpm = it.roundToInt().toFloat()))
                        }
                    }
                    if (s.mode == ReadMode.PAGES) SwitchRow("Разворот в альбомной ориентации", s.twoPages) { onChange(s.copy(twoPages = it)) }
                }
                else -> {
                    SwitchRow("Листать тапом по краям", s.tapToTurn) { onChange(s.copy(tapToTurn = it)) }
                    SwitchRow("Листать кнопками громкости", s.volumeKeys) { onChange(s.copy(volumeKeys = it)) }
                    SwitchRow("Не гасить экран", s.keepScreenOn) { onChange(s.copy(keepScreenOn = it)) }
                    SwitchRow("Одной рукой: тап в любом месте — дальше, у левого края — назад", s.oneHand) { onChange(s.copy(oneHand = it)) }
                    if (!s.oneHand) {
                        Text("Зоны тапов", style = MaterialTheme.typography.titleSmall)
                        TapZonesEditor(s.tapZones) { onChange(s.copy(tapZones = it)) }
                    }
                    ActionRow("Двойной тап", s.doubleTap) { onChange(s.copy(doubleTap = it)) }
                    ActionRow("Два пальца вверх", s.twoFingerUp) { onChange(s.copy(twoFingerUp = it)) }
                    ActionRow("Два пальца вниз", s.twoFingerDown) { onChange(s.copy(twoFingerDown = it)) }
                    Text("Щипок двумя пальцами меняет размер шрифта", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    SwitchRow("Ночная тема с 21:00 до 7:00", s.autoNight) { onChange(s.copy(autoNight = it)) }
                    Text("Фоновый звук", style = MaterialTheme.typography.titleSmall)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        io.github.galis228.materialereader.data.Ambient.entries.forEach { a ->
                            FilterChip(selected = s.ambient == a, onClick = { onChange(s.copy(ambient = a)) }, label = { Text(a.label) })
                        }
                    }
                    if (s.ambient != io.github.galis228.materialereader.data.Ambient.NONE)
                        LabeledSlider("Громкость", s.ambientVolume, 0.05f..1f, { "${(it * 100).roundToInt()}%" }) { onChange(s.copy(ambientVolume = it)) }
                    LabeledSlider("Таймер «перед сном»", s.bedtimeMinutes.toFloat(), 5f..60f, { "${it.roundToInt()} мин" }) {
                        onChange(s.copy(bedtimeMinutes = (it / 5).roundToInt() * 5))
                    }
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
    highlights: List<io.github.galis228.materialereader.data.Highlight> = emptyList(),
    onPickHighlight: (io.github.galis228.materialereader.data.Highlight) -> Unit = {},
    noSpoilers: Boolean = false,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        var tab by remember { mutableIntStateOf(0) }
        PrimaryTabRow(selectedTabIndex = tab) {
            Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("Главы") })
            Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("Закладки · ${bookmarks.size}") })
            Tab(selected = tab == 2, onClick = { tab = 2 }, text = { Text("Цитаты · ${highlights.size}") })
        }
        if (tab == 2) {
            QuotesTab(book, highlights, onPickHighlight)
        } else if (tab == 0) {
            val state = rememberLazyListState((current - 3).coerceAtLeast(0))
            LazyColumn(state = state, modifier = Modifier.navigationBarsPadding()) {
                itemsIndexed(if (noSpoilers) book.chapters.take(current + 1) else book.chapters) { i, ch ->
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
                        if (!noSpoilers) Text("$pct%", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelMedium)
                    }
                }
                if (noSpoilers && current < book.chapters.lastIndex) item {
                    Text("Дальше скрыто — режим без спойлеров", color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(horizontal = 24.dp, vertical = 14.dp))
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

private val TextPalette = listOf(0xFF111111, 0xFF2B2118, 0xFF3E2C1C, 0xFF1F2A44, 0xFF23402B, 0xFF5B1F1F,
    0xFFE8E4DC, 0xFFB9B4AA, 0xFFF2D9A0, 0xFFA8D3AE, 0xFF9DB4FF, 0xFFFFB3A7)
private val NotePalette = listOf(0xFF00897B, 0xFF4DD0C0, 0xFF1E88E5, 0xFF8E24AA, 0xFFD81B60, 0xFFE53935, 0xFFF4511E, 0xFF43A047)
private val BgPalette = listOf(0xFFFFFFFF, 0xFFFBF8F1, 0xFFF4EAD6, 0xFFE9DCC0, 0xFFE4EEDF, 0xFFE3ECF7,
    0xFF2A2A2E, 0xFF1E2A22, 0xFF1B2233, 0xFF2B2018, 0xFF121212, 0xFF000000)

@Composable
private fun ColorRow(palette: List<Long>, selected: Long, onPick: (Long) -> Unit) {
    androidx.compose.foundation.lazy.LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            Box(
                Modifier.size(36.dp).clip(CircleShape).border(BorderStroke(if (selected == 0L) 3.dp else 1.dp,
                    if (selected == 0L) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant), CircleShape)
                    .clickable { onPick(0L) },
                contentAlignment = Alignment.Center,
            ) { Text("Авто", fontSize = 9.sp) }
        }
        items(palette.size) { i ->
            val c = palette[i]
            Box(
                Modifier.size(36.dp).clip(CircleShape).background(androidx.compose.ui.graphics.Color(c.toInt()))
                    .border(BorderStroke(if (selected == c) 3.dp else 1.dp,
                        if (selected == c) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant), CircleShape)
                    .clickable { onPick(c) },
            )
        }
    }
}

@Composable
private fun FontPicker(s: ReaderSettings, onChange: (ReaderSettings) -> Unit) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val picker = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val ok = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                runCatching {
                    val f = customFontFile(ctx).apply { parentFile?.mkdirs() }
                    ctx.contentResolver.openInputStream(uri)!!.use { input -> f.outputStream().use { input.copyTo(it) } }
                    // make sure Android can actually read it
                    android.graphics.Typeface.createFromFile(f) != null
                }.getOrDefault(false)
            }
            if (ok) { initCustomFont(ctx); onChange(s.copy(font = ReaderFont.CUSTOM)) }
            else android.widget.Toast.makeText(ctx, "Это не похоже на файл шрифта (нужен TTF или OTF)", android.widget.Toast.LENGTH_LONG).show()
        }
    }
    androidx.compose.foundation.lazy.LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(ReaderFont.entries.size) { i ->
            val f = ReaderFont.entries[i]
            FilterChip(
                selected = s.font == f,
                onClick = {
                    if (f == ReaderFont.CUSTOM) picker.launch(arrayOf("font/ttf", "font/otf", "application/x-font-ttf", "application/x-font-otf", "application/octet-stream", "font/*"))
                    else onChange(s.copy(font = f))
                },
                label = { Text(if (f == ReaderFont.CUSTOM) "+ Свой шрифт…" else "Аа ${f.label}", fontFamily = if (f == ReaderFont.CUSTOM) null else f.family()) },
            )
        }
    }
}

/** The screen as a 3×3 grid; tap a cell to choose what a tap there does. */
@Composable
private fun TapZonesEditor(zones: List<io.github.galis228.materialereader.data.TapAction>, onChange: (List<io.github.galis228.materialereader.data.TapAction>) -> Unit) {
    val cs = MaterialTheme.colorScheme
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { Column(
        Modifier.fillMaxWidth(0.62f).aspectRatio(0.62f).clip(androidx.compose.foundation.shape.RoundedCornerShape(18.dp))
            .border(BorderStroke(2.dp, cs.outlineVariant), androidx.compose.foundation.shape.RoundedCornerShape(18.dp)),
    ) {
        for (row in 0 until 3) Row(Modifier.weight(1f)) {
            for (col in 0 until 3) {
                val i = row * 3 + col
                var open by remember { mutableStateOf(false) }
                val a = zones.getOrElse(i) { io.github.galis228.materialereader.data.TapAction.NONE }
                Box(
                    Modifier.weight(1f).fillMaxHeight().border(BorderStroke(0.5.dp, cs.outlineVariant))
                        .background(when (a) {
                            io.github.galis228.materialereader.data.TapAction.NEXT -> cs.primaryContainer
                            io.github.galis228.materialereader.data.TapAction.PREV -> cs.secondaryContainer
                            io.github.galis228.materialereader.data.TapAction.NONE -> cs.surface
                            else -> cs.tertiaryContainer
                        })
                        .clickable { open = true },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(a.label, style = MaterialTheme.typography.labelSmall, textAlign = androidx.compose.ui.text.style.TextAlign.Center, modifier = Modifier.padding(2.dp))
                    androidx.compose.material3.DropdownMenu(open, { open = false }) {
                        io.github.galis228.materialereader.data.TapAction.entries.forEach { opt ->
                            androidx.compose.material3.DropdownMenuItem({ Text(opt.label) }, onClick = {
                                open = false; onChange(zones.toMutableList().also { it[i] = opt })
                            })
                        }
                    }
                }
            }
        }
    } }
    TextButton(onClick = { onChange(io.github.galis228.materialereader.data.TapAction.DEFAULT_ZONES) }) { Text("Как было") }
}

@Composable
private fun ActionRow(label: String, value: io.github.galis228.materialereader.data.TapAction, onPick: (io.github.galis228.materialereader.data.TapAction) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth().clickable { open = true }.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f))
        Box {
            Text(value.label, color = MaterialTheme.colorScheme.primary)
            androidx.compose.material3.DropdownMenu(open, { open = false }) {
                io.github.galis228.materialereader.data.TapAction.entries.forEach { opt ->
                    androidx.compose.material3.DropdownMenuItem({ Text(opt.label) }, onClick = { open = false; onPick(opt) })
                }
            }
        }
    }
}
