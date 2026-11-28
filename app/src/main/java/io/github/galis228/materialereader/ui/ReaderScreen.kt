@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package io.github.galis228.materialereader.ui

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.systemBarsIgnoringVisibility
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.FormatListBulleted
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.BookmarkBorder
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.TextFields
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.foundation.layout.width
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFontFamilyResolver
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import io.github.galis228.materialereader.VolumeKeys
import io.github.galis228.materialereader.data.Bookmark
import io.github.galis228.materialereader.data.Library
import io.github.galis228.materialereader.data.AmbientSound
import io.github.galis228.materialereader.data.PageAnim
import io.github.galis228.materialereader.data.ReadMode
import io.github.galis228.materialereader.data.ReaderSettings
import io.github.galis228.materialereader.data.ReaderTheme
import io.github.galis228.materialereader.data.SettingsRepo
import io.github.galis228.materialereader.data.ReadingSpeed
import io.github.galis228.materialereader.parse.Block
import io.github.galis228.materialereader.parse.ParsedBook
import io.github.galis228.materialereader.parse.countWords
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.roundToInt


private sealed interface LoadState {
    data object Loading : LoadState
    data class Ready(val book: ParsedBook) : LoadState
    data class Failed(val message: String) : LoadState
}

@Composable
fun ReaderScreen(id: String, onBack: () -> Unit, onAppSettings: () -> Unit = {}) {
    val ctx = LocalContext.current
    val library = remember { Library.get(ctx) }
    val repo = remember { SettingsRepo(ctx) }
    val settings by repo.settings.collectAsState(initial = null)
    val entry = remember(id) { library.entry(id) }
    val settingsScope = rememberCoroutineScope()
    val s = settings ?: run { Box(Modifier.fillMaxSize()); return }
    // Night theme by the clock: re-checked every minute while reading
    val nightNow by produceState(isNightHour()) { while (true) { kotlinx.coroutines.delay(60_000); value = isNightHour() } }
    val shown = if (s.autoNight && nightNow) s.copy(theme = io.github.galis228.materialereader.data.ReaderTheme.NIGHT, textColor = 0L, bgColor = 0L) else s
    val colors = pageColors(shown, MaterialTheme.colorScheme)
    val onSettings: (ReaderSettings) -> Unit = { new -> settingsScope.launch { repo.update(new) } }

    if (entry?.isComic == true) {
        ComicReader(entry, s, colors, onSettings, onBack)
        return
    }

    val state by produceState<LoadState>(LoadState.Loading, id) {
        value = runCatching { LoadState.Ready(library.open(id)) }
            .getOrElse { LoadState.Failed(it.message ?: "Не удалось открыть книгу") }
    }

    Box(Modifier.fillMaxSize().background(colors.bg)) {
        when (val st = state) {
            LoadState.Loading -> CircularProgressIndicator(Modifier.align(Alignment.Center), color = colors.accent)
            is LoadState.Failed -> Column(Modifier.align(Alignment.Center).padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(st.message, color = colors.text, textAlign = TextAlign.Center)
                Spacer(Modifier.height(16.dp))
                TextButton(onClick = onBack) { Text("Назад") }
            }
            is LoadState.Ready -> BookReader(
                id = id, book = st.book, startBlock = entry?.position ?: 0, startOffset = entry?.offset ?: 0,
                startChar = entry?.charPos ?: 0, settings = s, colors = colors, onSettings = onSettings, onBack = onBack, onAppSettings = onAppSettings,
            )
        }
    }
}

/** What the panels need from either reading mode. */
class ReaderController {
    var block by mutableIntStateOf(0)
    var char by mutableIntStateOf(0)
    var sliderValue by mutableFloatStateOf(0f)
    var sliderMax by mutableFloatStateOf(1f)
    var pageLabel by mutableStateOf("")
    var ticks: List<Float> = emptyList()
    var jumpToSlider: (Float) -> Unit = {}
    var jumpTo: (block: Int, char: Int) -> Unit = { _, _ -> }
    var turn: (dir: Int) -> Unit = {}
    var visibleText: () -> String = { "" }
    /** Pages left until the next chapter (page mode only). */
    var pagesToChapter by mutableStateOf<Int?>(null)
    /** Auto-scroll running (scroll mode). */
    var autoScroll by mutableStateOf(false)
}

private fun isNightHour(): Boolean {
    val h = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
    return h >= 21 || h < 7
}

@OptIn(FlowPreview::class)
@Composable
private fun BookReader(
    id: String, book: ParsedBook, startBlock: Int, startOffset: Int, startChar: Int,
    settings: ReaderSettings, colors: PageColors,
    onSettings: (ReaderSettings) -> Unit, onBack: () -> Unit, onAppSettings: () -> Unit,
) {
    val library = Library.get(LocalContext.current)
    val scope = rememberCoroutineScope()
    val ctl = remember { ReaderController().apply { block = startBlock.coerceIn(0, (book.blocks.size - 1).coerceAtLeast(0)); char = startChar } }
    var uiVisible by remember { mutableStateOf(false) }
    var sheet by remember { mutableStateOf<Sheet?>(null) }
    var paragraph by remember { mutableIntStateOf(0) }
    var aiPreset by remember { mutableStateOf<String?>(null) }
    var searchQuery by remember { mutableStateOf("") }
    // Background sound lives as long as the book is open
    DisposableEffect(settings.ambient) {
        AmbientSound.play(settings.ambient, settings.ambientVolume)
        onDispose { AmbientSound.stop() }
    }
    LaunchedEffect(settings.ambientVolume) { AmbientSound.setVolume(settings.ambientVolume) }
    // Bedtime: the screen warms up and dims over the timer, then the position is saved and the page goes dark
    var bedtimeEnd by remember { mutableStateOf<Long?>(null) }
    var bedtimeLeft by remember { mutableFloatStateOf(1f) } // 1 = just started, 0 = time is up
    LaunchedEffect(bedtimeEnd) {
        val end = bedtimeEnd ?: return@LaunchedEffect
        val total = (end - System.currentTimeMillis()).coerceAtLeast(1L).toFloat()
        while (true) {
            val left = end - System.currentTimeMillis()
            bedtimeLeft = (left / total).coerceIn(0f, 1f)
            if (left <= 0) break
            kotlinx.coroutines.delay(2_000)
        }
        AmbientSound.stop()
    }
    val books by library.books.collectAsState()
    val bookmarks = books.firstOrNull { it.id == id }?.bookmarks.orEmpty()

    val wordsBefore = remember(book) {
        IntArray(book.blocks.size + 1).also { a -> book.blocks.forEachIndexed { i, b -> a[i + 1] = a[i] + countWords(b.text) } }
    }
    val chapterIndex by remember(book) { derivedStateOf { book.chapters.indexOfLast { it.startBlock <= ctl.block }.coerceAtLeast(0) } }

    SystemBars(uiVisible, colors.isDark, settings.keepScreenOn && !(bedtimeEnd != null && bedtimeLeft <= 0f))

    // Personal reading speed: each step forward = words read / time spent on them
    val appCtx = LocalContext.current.applicationContext
    var wpm by remember { mutableFloatStateOf(ReadingSpeed.wpm(appCtx)) }
    var calibrated by remember { mutableStateOf(ReadingSpeed.calibrated(appCtx)) }
    LaunchedEffect(Unit) {
        var lastWords = -1; var lastTime = System.currentTimeMillis()
        snapshotFlow { ctl.block to ctl.char }.collect { (b, c) ->
            val blockLen = book.blocks.getOrNull(b)?.text?.length?.coerceAtLeast(1) ?: 1
            val inBlock = ((wordsBefore[(b + 1).coerceAtMost(book.blocks.size)] - wordsBefore[b.coerceAtMost(book.blocks.size)]) * c / blockLen)
            val words = wordsBefore[b.coerceIn(0, book.blocks.size)] + inBlock
            val now = System.currentTimeMillis()
            // in scroll mode small steps accumulate until there is a page's worth of words
            if (lastWords >= 0 && words > lastWords && words - lastWords < 15 && now - lastTime < 4 * 60_000L) return@collect
            if (lastWords >= 0 && words > lastWords && words - lastWords < 3000) io.github.galis228.materialereader.data.Stats.get(appCtx).addWords(words - lastWords)
            if (lastWords >= 0 && words > lastWords && !uiVisible && sheet == null) {
                if (ReadingSpeed.record(appCtx, words - lastWords, now - lastTime)) {
                    wpm = ReadingSpeed.wpm(appCtx); calibrated = ReadingSpeed.calibrated(appCtx)
                }
            }
            lastWords = words; lastTime = now
        }
    }

    // Persist position (debounced) and count reading time
    var scrollOffset by remember { mutableIntStateOf(startOffset) }
    LaunchedEffect(Unit) {
        var lastSave = System.currentTimeMillis()
        snapshotFlow { Triple(ctl.block, ctl.char, scrollOffset) }.debounce(400).collect { (b, c, off) ->
            val now = System.currentTimeMillis()
            library.updatePosition(id, b, off, c, (now - lastSave).coerceAtMost(5 * 60_000L))
            lastSave = now
        }
    }
    DisposableEffect(Unit) {
        onDispose { runBlocking { library.updatePosition(id, ctl.block, scrollOffset, ctl.char, 0) } }
    }
    // Save right away when the app goes to the background: the system may kill it before the debounce fires
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val obs = androidx.lifecycle.LifecycleEventObserver { _, e ->
            if (e == androidx.lifecycle.Lifecycle.Event.ON_STOP) runBlocking { library.updatePosition(id, ctl.block, scrollOffset, ctl.char, 0) }
        }
        lifecycleOwner.lifecycle.addObserver(obs)
        onDispose { lifecycleOwner.lifecycle.removeObserver(obs) }
    }

    DisposableEffect(settings.volumeKeys) {
        VolumeKeys.enabled = settings.volumeKeys
        onDispose { VolumeKeys.enabled = false }
    }
    LaunchedEffect(Unit) { VolumeKeys.events.collect { ctl.turn(it) } }
    BackHandler(enabled = sheet != null) { sheet = null }

    val onTap: (Float) -> Unit = { x ->
        when {
            ctl.autoScroll -> ctl.autoScroll = false
            settings.oneHand && uiVisible -> uiVisible = false
            settings.oneHand && x < 0.22f -> ctl.turn(-1)
            settings.oneHand && x < 0.45f -> uiVisible = true
            settings.oneHand -> ctl.turn(1)
            uiVisible -> uiVisible = false
            settings.tapToTurn && x < (if (settings.simpleMode) 0.35f else 0.28f) -> ctl.turn(-1)
            settings.tapToTurn && x > (if (settings.simpleMode) 0.65f else 0.72f) -> ctl.turn(1)
            else -> uiVisible = true
        }
    }

    val hits = remember { BlockHits() }
    val highlights = books.firstOrNull { it.id == id }?.highlights.orEmpty()
    val hlMap = remember(highlights) { highlights.groupBy { it.block } }
    // Long press on a word: select it (the selection can be stretched in the sheet)
    var selection by remember { mutableStateOf<IntRange?>(null) }
    val onLongPress: (Offset) -> Unit = { p ->
        if (!settings.simpleMode) hits.hitAt(p)?.let { h ->
            val text = book.blocks.getOrNull(h.block)?.text.orEmpty()
            paragraph = h.block
            selection = h.charAt(p)?.let { wordAt(text, it) } ?: text.indices
            uiVisible = false; sheet = Sheet.SELECT
        }
    }

    // Tap on a footnote mark: show the note instead of turning the page
    var footnote by remember { mutableStateOf<Footnote?>(null) }
    var footnoteContext by remember { mutableStateOf("") }
    // Turning the page closes an open footnote
    LaunchedEffect(Unit) { snapshotFlow { ctl.block to ctl.char }.drop(1).collect { footnote = null } }
    var returnTo by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    val slop = with(LocalDensity.current) { 14.dp.toPx() }
    val tapAt: (Offset) -> Boolean = tap@{ p ->
        if (footnote != null) { footnote = null; return@tap true } // a tap on the page closes the note panel
        if (uiVisible || sheet != null) return@tap false
        val h = hits.hitAt(p) ?: return@tap false
        val blk = book.blocks.getOrNull(h.block) ?: return@tap false
        val ref = blk.allRefs.firstOrNull { r ->
            h.boxOf(r.start, r.end)?.let { b -> androidx.compose.ui.geometry.Rect(b.left - slop, b.top - slop, b.right + slop, b.bottom + slop).contains(p) } == true
        } ?: return@tap false
        footnote = findFootnote(book, h.block, ref)
        footnoteContext = sentenceAround(blk.text, ref.start until ref.end.coerceAtLeast(ref.start + 1))
        true
    }

    // Reader brightness (null = follow the system)
    val view = LocalView.current
    DisposableEffect(settings.brightness) {
        val window = (view.context as Activity).window
        val lp = window.attributes
        lp.screenBrightness = if (settings.brightness < 0f) android.view.WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
            else settings.brightness.coerceIn(0.01f, 1f)
        window.attributes = lp
        onDispose {
            val l = window.attributes; l.screenBrightness = android.view.WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE; window.attributes = l
        }
    }

    Box(Modifier.fillMaxSize().background(colors.bg)) {
        CompositionLocalProvider(LocalBlockHits provides hits, LocalHighlights provides hlMap, LocalPageDark provides colors.isDark,
            LocalPageAccent provides colors.accent, LocalTapAt provides tapAt, LocalBionic provides settings.bionic) {
            when (settings.mode) {
                ReadMode.SCROLL -> ScrollContent(book, settings, colors, ctl, startOffset, onTap, onLongPress) { scrollOffset = it }
                ReadMode.PAGES -> PagedContent(book, settings, colors, ctl, onTap, onLongPress)
            }
        }
        if (settings.warmth > 0.01f) Box(Modifier.fillMaxSize().background(Color(0xFFFF8F00).copy(alpha = settings.warmth * 0.28f)))
        if (bedtimeEnd != null) {
            val k = 1f - bedtimeLeft
            Box(Modifier.fillMaxSize().background(Color(0xFFFF8F00).copy(alpha = 0.10f + 0.18f * k)))
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = (k * k * 0.85f).coerceAtMost(0.85f))))
            if (bedtimeLeft <= 0f) Box(
                Modifier.fillMaxSize().background(Color.Black).pointerInput(Unit) { detectTapGestures { bedtimeEnd = null } },
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Спокойной ночи", color = Color(0xFF6E6A62), fontSize = 20.sp)
                    Spacer(Modifier.height(8.dp))
                    Text("Место в книге сохранено. Коснись, чтобы читать дальше", color = Color(0xFF4A4740), fontSize = 13.sp)
                }
            }
        }
        if (ctl.autoScroll) androidx.compose.material3.Surface(
            onClick = { ctl.autoScroll = false },
            shape = RoundedCornerShape(24.dp), color = colors.panel, contentColor = colors.text, shadowElevation = 4.dp,
            modifier = Modifier.align(Alignment.BottomEnd).navigationBarsPadding().padding(16.dp),
        ) { Text("❚❚  Автопрокрутка", Modifier.padding(horizontal = 14.dp, vertical = 8.dp), fontSize = 13.sp) }
        returnTo?.let { (rb, rc) ->
            androidx.compose.material3.Surface(
                onClick = { returnTo = null; ctl.jumpTo(rb, rc) },
                shape = RoundedCornerShape(24.dp), color = colors.panel, contentColor = colors.text, shadowElevation = 6.dp,
                modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 36.dp),
            ) {
                Row(Modifier.padding(start = 16.dp, end = 6.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("↩ Вернуться к чтению", fontSize = 14.sp)
                    IconButton(onClick = { returnTo = null }, modifier = Modifier.size(32.dp)) {
                        Icon(androidx.compose.material.icons.Icons.Rounded.Clear, "Остаться здесь", Modifier.size(18.dp))
                    }
                }
            }
        }

        AnimatedVisibility(footnote != null && !uiVisible, Modifier.align(Alignment.BottomCenter),
            enter = slideInVertically { it } + fadeIn(), exit = slideOutVertically { it } + fadeOut()) {
            var shown by remember { mutableStateOf<Footnote?>(null) }
            footnote?.let { shown = it } // keep the content while the panel slides away
            shown?.let { n ->
                FootnotePanel(n, footnoteContext, book.title, colors,
                    onGo = { b -> returnTo = ctl.block to ctl.char; footnote = null; ctl.jumpTo(b, 0) },
                    onDismiss = { footnote = null })
            }
        }
        BackHandler(enabled = footnote != null) { footnote = null }

        if (!uiVisible && settings.mode == ReadMode.SCROLL) {
            val p = if (ctl.sliderMax <= 0f) 1f else (ctl.sliderValue / ctl.sliderMax).coerceIn(0f, 1f)
            Box(Modifier.align(Alignment.BottomStart).fillMaxWidth(p).height(2.dp).background(colors.accent.copy(alpha = 0.5f)))
        }

        val marked = bookmarks.any { it.block == ctl.block && abs(it.charPos - ctl.char) < 400 }
        AnimatedVisibility(uiVisible, Modifier.align(Alignment.TopCenter), enter = slideInVertically { -it } + fadeIn(), exit = slideOutVertically { -it } + fadeOut()) {
            TopPanel(
                book.title, book.chapters.getOrNull(chapterIndex)?.title.orEmpty(), colors, marked, onBack, simple = settings.simpleMode,
                onSearch = { uiVisible = false; sheet = Sheet.SEARCH },
                onBookmark = {
                    scope.launch {
                        val existing = bookmarks.firstOrNull { it.block == ctl.block && abs(it.charPos - ctl.char) < 400 }
                        if (existing != null) library.removeBookmark(id, existing.createdAt)
                        else {
                            val t = book.blocks.getOrNull(ctl.block)?.text.orEmpty()
                            val snippet = t.substring(ctl.char.coerceIn(0, t.length)).take(140)
                            library.addBookmark(id, Bookmark(ctl.block, ctl.char, snippet,
                                book.chapters.getOrNull(chapterIndex)?.title.orEmpty(), System.currentTimeMillis()))
                        }
                    }
                },
            )
        }
        AnimatedVisibility(uiVisible, Modifier.align(Alignment.BottomCenter), enter = slideInVertically { it } + fadeIn(), exit = slideOutVertically { it } + fadeOut()) {
            val nextStart = book.chapters.getOrNull(chapterIndex + 1)?.startBlock ?: book.blocks.size
            val leftWords = wordsBefore[nextStart] - wordsBefore[ctl.block.coerceIn(0, book.blocks.size)]
            BottomPanel(
                book, ctl, colors, minutesLeft = (leftWords.toFloat() / wpm).roundToInt(), personal = calibrated,
                onText = { sheet = Sheet.TEXT; uiVisible = false }, onToc = { sheet = Sheet.TOC },
                onAi = { aiPreset = null; sheet = Sheet.AI },
                simple = settings.simpleMode,
                bedtimeOn = bedtimeEnd != null,
                onBedtime = {
                    uiVisible = false
                    bedtimeEnd = if (bedtimeEnd != null) null else System.currentTimeMillis() + settings.bedtimeMinutes * 60_000L
                },
                onAutoScroll = if (settings.mode == ReadMode.SCROLL) ({ uiVisible = false; ctl.autoScroll = true }) else null,
                onFont = { d -> onSettings(settings.copy(fontSize = (settings.fontSize + d).coerceIn(14f, 40f))) },
                onToggleNight = { onSettings(settings.copy(theme = if (colors.isDark) ReaderTheme.PAPER else ReaderTheme.NIGHT)) },
            )
        }
    }

    when (sheet) {
        Sheet.TEXT -> TextSettingsSheet(settings, onSettings) { sheet = null }
        Sheet.TOC -> TocSheet(
            book, chapterIndex, bookmarks,
            onPick = { idx -> sheet = null; uiVisible = false; ctl.jumpTo(book.chapters[idx].startBlock, 0) },
            onPickBookmark = { m -> sheet = null; uiVisible = false; ctl.jumpTo(m.block, m.charPos) },
            onDeleteBookmark = { m -> scope.launch { library.removeBookmark(id, m.createdAt) } },
            highlights = highlights,
            onPickHighlight = { h -> sheet = null; uiVisible = false; ctl.jumpTo(h.block, 0) },
        ) { sheet = null }
        Sheet.SEARCH -> SearchSheet(book, searchQuery, onQuery = { searchQuery = it }, onPick = { h ->
            sheet = null; ctl.jumpTo(h.block, h.char)
        }) { sheet = null }
        Sheet.SELECT -> SelectionSheet(
            blockText = book.blocks.getOrNull(paragraph)?.text.orEmpty(), blockIndex = paragraph,
            initial = selection ?: 0..0, bookTitle = book.title, highlights = hlMap[paragraph].orEmpty(),
            onPut = { h -> scope.launch { library.putHighlight(id, h) } },
            onRemove = { c -> scope.launch { library.removeHighlight(id, c) } },
            onAskAi = { text -> aiPreset = text; sheet = Sheet.AI },
        ) { if (sheet == Sheet.SELECT) sheet = null }
        Sheet.AI -> AiSheet(
            book, ctl.block, book.chapters.getOrNull(chapterIndex)?.startBlock ?: 0,
            pageText = { aiPreset ?: ctl.visibleText() }, preset = aiPreset,
            onOpenSettings = { sheet = null; onAppSettings() },
            onJournal = { q, ans ->
                // a journal entry is a note on the current paragraph that colors no text (empty range)
                scope.launch { library.putHighlight(id, io.github.galis228.materialereader.data.Highlight(ctl.block, 0, "📓 $q\n$ans", System.currentTimeMillis(), 0, 0)) }
            },
        ) { sheet = null }
        null -> {}
    }
}

private enum class Sheet { TEXT, TOC, AI, SEARCH, SELECT }

@Composable
fun SystemBars(visible: Boolean, dark: Boolean, keepScreenOn: Boolean) {
    val view = LocalView.current
    DisposableEffect(visible, dark) {
        val window = (view.context as Activity).window
        val c = WindowCompat.getInsetsController(window, view)
        c.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        c.isAppearanceLightStatusBars = !dark
        c.isAppearanceLightNavigationBars = !dark
        if (visible) c.show(WindowInsetsCompat.Type.systemBars()) else c.hide(WindowInsetsCompat.Type.systemBars())
        onDispose { }
    }
    DisposableEffect(Unit) {
        onDispose {
            val window = (view.context as Activity).window
            WindowCompat.getInsetsController(window, view).show(WindowInsetsCompat.Type.systemBars())
        }
    }
    DisposableEffect(keepScreenOn) {
        view.keepScreenOn = keepScreenOn
        onDispose { view.keepScreenOn = false }
    }
}

// ---------------- Scroll mode ----------------

@Composable
private fun ScrollContent(
    book: ParsedBook, s: ReaderSettings, c: PageColors, ctl: ReaderController, startOffset: Int,
    onTap: (Float) -> Unit, onLongPress: (Offset) -> Unit, onOffset: (Int) -> Unit,
) {
    val list: LazyListState = rememberLazyListState(ctl.block, startOffset)
    val tapAt = LocalTapAt.current
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val total = (book.blocks.size - 1).coerceAtLeast(1)

    LaunchedEffect(list) {
        snapshotFlow { list.firstVisibleItemIndex to list.firstVisibleItemScrollOffset }.collect { (i, off) ->
            ctl.block = i; ctl.char = 0; ctl.sliderValue = i.toFloat(); onOffset(off)
        }
    }
    ctl.sliderMax = total.toFloat()
    ctl.pagesToChapter = null
    ctl.ticks = book.chapters.drop(1).map { it.startBlock.toFloat() / total }
    ctl.pageLabel = "${(ctl.sliderValue / total * 100).roundToInt()}%"
    ctl.jumpTo = { b, _ -> scope.launch { list.scrollToItem(b) } }
    ctl.jumpToSlider = { v -> scope.launch { list.scrollToItem(v.roundToInt()) } }
    ctl.visibleText = { list.layoutInfo.visibleItemsInfo.joinToString("\n") { book.blocks[it.index].text } }
    ctl.turn = { dir ->
        scope.launch {
            val step = list.layoutInfo.viewportSize.height - with(density) { (s.fontSize * s.lineHeight * 1.5f).sp.toPx() }
            list.animateScrollBy(dir * step)
        }
    }

    val specs = rememberSpecs(s, c)
    val bars = WindowInsets.systemBarsIgnoringVisibility.asPaddingValues()
    // Auto-scroll: a steady glide at the chosen lines per minute, one frame at a time
    LaunchedEffect(ctl.autoScroll, s.autoScrollLpm, s.fontSize, s.lineHeight) {
        if (!ctl.autoScroll) return@LaunchedEffect
        val linePx = with(density) { (s.fontSize * s.lineHeight).sp.toPx() }
        val pxPerSec = linePx * s.autoScrollLpm / 60f
        var last = 0L
        var carry = 0f
        while (ctl.autoScroll) {
            androidx.compose.runtime.withFrameNanos { now ->
                if (last != 0L) carry += pxPerSec * (now - last) / 1e9f
                last = now
            }
            if (carry >= 1f) {
                val step = carry.toInt().toFloat(); carry -= step
                if (list.scrollBy(step) < step * 0.5f) ctl.autoScroll = false // reached the end
            }
        }
    }
    val bilingual = s.bilingual
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val w = constraints.maxWidth.toFloat()
        LazyColumn(
            state = list,
            modifier = Modifier.fillMaxSize().pointerInput(Unit) { detectTapGestures(onLongPress = { onLongPress(it) }) { if (!tapAt(it)) onTap(it.x / w) } },
            contentPadding = PaddingValues(
                start = s.margin.dp, end = s.margin.dp,
                top = bars.calculateTopPadding() + 28.dp, bottom = bars.calculateBottomPadding() + 120.dp,
            ),
        ) {
            itemsIndexed(book.blocks, contentType = { _, b -> b.kind }) { i, b ->
                val spec = specs.getValue(b.kind)
                SliceText(b, i, spec, first = i == 0)
                if (bilingual && (b.kind == Block.Kind.PARAGRAPH || b.kind == Block.Kind.QUOTE) && b.text.length > 1)
                    TranslationLine(b.text, spec, c)
            }
        }
    }
}

@Composable
private fun rememberSpecs(s: ReaderSettings, c: PageColors): Map<Block.Kind, BlockSpec> {
    val density = LocalDensity.current
    return remember(s.fontSize, s.lineHeight, s.font.family(), s.justify, s.hyphenate, c, density) {
        Block.Kind.entries.associateWith { blockSpec(it, s, c.text, c.muted, density) }
    }
}

@Composable
private fun SliceText(block: Block, index: Int, spec: BlockSpec, first: Boolean) {
    val d = LocalDensity.current
    val hits = LocalBlockHits.current
    val hls = LocalHighlights.current[index].orEmpty()
    val dark = LocalPageDark.current
    val accent = LocalPageAccent.current
    val token = remember { Any() }
    var layout by remember { mutableStateOf<androidx.compose.ui.text.TextLayoutResult?>(null) }
    if (hits != null) DisposableEffect(token) { onDispose { hits.remove(token) } }
    val bionic = LocalBionic.current
    val text = remember(block, hls, accent, dark, bionic) { styledSlice(block, 0, block.text.length, accent, hls, dark, bionic) }
    with(d) {
        Text(
            text, style = spec.forSlice(0), onTextLayout = { layout = it },
            modifier = Modifier.fillMaxWidth()
                .padding(
                    top = spec.topOnPage(first).toDp(), bottom = spec.bottom.toDp(),
                    start = spec.padStart.toDp(), end = spec.padEnd.toDp(),
                )
                .then(if (hits != null) Modifier.onGloballyPositioned { hits.put(token, index, it.boundsInRoot(), layout, 0) } else Modifier),
        )
    }
}

// ---------------- Page mode ----------------

private data class PageKey(val w: Int, val h: Int, val size: Float, val line: Float, val font: Any, val justify: Boolean, val hyph: Boolean, val bionic: Boolean)

/** Last pagination, so reopening the same book with the same layout is instant. */
private object PageCache {
    var key: Pair<ParsedBook, PageKey>? = null
    var pages: List<Page> = emptyList()
}

@Composable
private fun PagedContent(book: ParsedBook, s: ReaderSettings, c: PageColors, ctl: ReaderController, onTap: (Float) -> Unit, onLongPress: (Offset) -> Unit) {
    val density = LocalDensity.current
    val fontResolver = LocalFontFamilyResolver.current
    val layoutDir = LocalLayoutDirection.current
    val bars = WindowInsets.systemBarsIgnoringVisibility.asPaddingValues()
    val specs = rememberSpecs(s, c)
    val tapAt = LocalTapAt.current

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val landscape = maxWidth > maxHeight
        val two = s.twoPages && landscape
        val gutter = 40.dp
        val topPad = bars.calculateTopPadding() + 24.dp
        val bottomPad = bars.calculateBottomPadding() + 36.dp
        val margin = s.margin.dp
        val areaW = maxWidth - margin * 2 - (if (landscape) 24.dp else 0.dp)
        val colW = if (two) (areaW - gutter) / 2 else areaW
        val colH = maxHeight - topPad - bottomPad
        val wPx = with(density) { colW.roundToPx() }
        val hPx = with(density) { colH.roundToPx() }
        val key = PageKey(wPx, hPx, s.fontSize, s.lineHeight, s.font.family(), s.justify, s.hyphenate, s.bionic)

        var progress by remember { mutableFloatStateOf(0f) }
        val pages by produceState<List<Page>?>(PageCache.pages.takeIf { PageCache.key == book to key }, book, key) {
            if (PageCache.key == book to key) { value = PageCache.pages; return@produceState }
            value = null
            val measurer = TextMeasurer(fontResolver, density, layoutDir, cacheSize = 0)
            val paginator = Paginator(measurer, specs, wPx, hPx, s.bionic)
            // The current chapter first, so the text shows up (and settings changes preview) right away
            val ch = book.chapters.indexOfLast { it.startBlock <= ctl.block }.coerceAtLeast(0)
            val from = book.chapters.getOrNull(ch)?.startBlock ?: 0
            val to = book.chapters.getOrNull(ch + 1)?.startBlock ?: book.blocks.size
            if (from > 0 || to < book.blocks.size) {
                value = withContext(Dispatchers.Default) { paginator.paginate(book, from, to) }
            }
            val result = withContext(Dispatchers.Default) {
                paginator.paginate(book) { progress = it }
            }
            PageCache.key = book to key; PageCache.pages = result
            value = result
        }

        val list = pages
        if (list == null || list.isEmpty()) {
            Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator(progress = { progress }, color = c.accent)
                Spacer(Modifier.height(12.dp))
                Text("Раскладываю по страницам… ${(progress * 100).roundToInt()}%", color = c.muted, fontSize = 13.sp)
            }
            return@BoxWithConstraints
        }

        val per = if (two) 2 else 1
        val spreads = (list.size + per - 1) / per
        // Recomputed together with the pages (first the current chapter, then the whole book, after a font change or
        // rotation), so a page index from the old list is never read against the new one.
        var spread by remember(list, per) { mutableIntStateOf((list.indexOfPosition(ctl.block, ctl.char) / per).coerceIn(0, spreads - 1)) }
        val scope = rememberCoroutineScope()
        val anim = rememberUpdatedState(s.anim)
        var curlTurn by remember { mutableStateOf<(Int) -> Unit>({}) }

        LaunchedEffect(list, per) {
            snapshotFlow { spread }.collect { sp ->
                val p = list[(sp * per).coerceAtMost(list.lastIndex)]
                ctl.block = p.firstBlock; ctl.char = p.firstChar
                ctl.sliderValue = sp.toFloat()
            }
        }
        val chapterPages = remember(list, per) { book.chapters.drop(1).map { list.indexOfPosition(it.startBlock, 0) / per } }
        ctl.sliderMax = (spreads - 1).coerceAtLeast(1).toFloat()
        ctl.ticks = chapterPages.map { it.toFloat() / ctl.sliderMax }
        ctl.pageLabel = "стр. ${spread * per + 1} из ${list.size}"
        val nextChapterSpread = chapterPages.firstOrNull { it > spread } ?: spreads
        ctl.pagesToChapter = ((nextChapterSpread - spread) * per).coerceAtLeast(1)
        ctl.jumpTo = { b, ch -> spread = (list.indexOfPosition(b, ch) / per).coerceIn(0, spreads - 1) }
        ctl.jumpToSlider = { v -> spread = v.roundToInt().coerceIn(0, spreads - 1) }
        ctl.visibleText = {
            (0 until per).mapNotNull { list.getOrNull(spread * per + it) }.joinToString("\n") { pg ->
                pg.slices.joinToString("\n") { sl -> book.blocks[sl.block].text.let { t -> t.substring(sl.start.coerceAtMost(t.length), sl.end.coerceAtMost(t.length)) } }
            }
        }

        val w = constraints.maxWidth.toFloat()
        val spreadView: @Composable (Int) -> Unit = { sp ->
            Box(Modifier.fillMaxSize().background(c.bg)) {
                Row(
                    Modifier.align(Alignment.TopCenter).padding(top = topPad),
                    horizontalArrangement = Arrangement.spacedBy(gutter),
                ) {
                    for (k in 0 until per) {
                        val pi = sp * per + k
                        Box(Modifier.requiredWidth(colW).requiredHeight(colH)) {
                            list.getOrNull(pi)?.let { PageView(it, book, specs) }
                        }
                    }
                }
                val ch = book.chapters.lastOrNull { it.startBlock <= (list.getOrNull(sp * per)?.firstBlock ?: 0) }?.title.orEmpty()
                Row(
                    Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                        .padding(start = margin, end = margin, bottom = bars.calculateBottomPadding() + 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(ch, color = c.muted, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    Text("${sp * per + 1} / ${list.size}", color = c.muted, fontSize = 11.sp, modifier = Modifier.padding(start = 12.dp))
                }
            }
        }

        if (s.anim.isCurl) {
            ctl.turn = { dir -> curlTurn(dir) }
            CurlPager(
                dark = c.isDark,
                count = spreads, current = spread, onCurrentChange = { spread = it },
                paper = lerpColor(c.bg, if (c.isDark) Color.White else Color.Black, 0.04f),
                registerTurn = { curlTurn = it }, onTap = onTap, onLongPress = onLongPress, page = spreadView,
            )
        } else {
            val pager = remember(list, per) { PagerState(currentPage = spread) { spreads } }
            LaunchedEffect(pager) { snapshotFlow { pager.currentPage }.collect { spread = it } }
            LaunchedEffect(spread) { if (pager.currentPage != spread && !pager.isScrollInProgress) pager.scrollToPage(spread) }
            ctl.turn = { dir ->
                scope.launch {
                    val t = (pager.currentPage + dir).coerceIn(0, spreads - 1)
                    if (anim.value == PageAnim.NONE) pager.scrollToPage(t) else pager.animateScrollToPage(t)
                }
            }
            HorizontalPager(
                state = pager,
                modifier = Modifier.fillMaxSize().pointerInput(Unit) { detectTapGestures(onLongPress = { onLongPress(it) }) { if (!tapAt(it)) onTap(it.x / w) } },
                beyondViewportPageCount = 1,
            ) { sp ->
                Box(Modifier.fillMaxSize().pageTransition(pager, sp, s.anim)) { spreadView(sp) }
            }
        }
    }
}

private fun lerpColor(a: Color, b: Color, t: Float) = androidx.compose.ui.graphics.lerp(a, b, t)

/**
 * Draws a page from text layouts measured exactly like the paginator measures them, with the same
 * spacing arithmetic, so what fits in pagination always fits on screen (no clipped bottom line).
 */
@Composable
private fun PageView(page: Page, book: ParsedBook, specs: Map<Block.Kind, BlockSpec>) {
    val measurer = rememberTextMeasurer(cacheSize = 0)
    val hits = LocalBlockHits.current
    val highlights = LocalHighlights.current
    val dark = LocalPageDark.current
    val accent = LocalPageAccent.current
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val width = constraints.maxWidth
        val bionic = LocalBionic.current
        val placed = remember(page, specs, width, measurer, highlights, accent, dark, bionic) {
            var y = 0f
            page.slices.mapIndexed { k, sl ->
                val b = book.blocks[sl.block]
                val spec = specs.getValue(b.kind)
                val w = (width - spec.padStart - spec.padEnd).toInt().coerceAtLeast(1)
                val layout = measurer.measure(styledSlice(b, sl.start, sl.end, accent, highlights[sl.block].orEmpty(), dark, bionic),
                    spec.forSlice(sl.start), constraints = Constraints.fixedWidth(w), skipCache = true)
                val top = y + spec.topOnPage(k == 0)
                y = top + layout.size.height + spec.bottom
                PlacedSlice(sl.block, layout, Offset(spec.padStart, top), w, sl.start)
            }
        }
        var origin by remember { mutableStateOf(Offset.Zero) }
        if (hits != null) {
            val tokens = remember(placed) { placed.map { Any() } }
            DisposableEffect(tokens) { onDispose { tokens.forEach { hits.remove(it) } } }
            LaunchedEffect(origin, placed) {
                placed.forEachIndexed { i, p ->
                    hits.put(tokens[i], p.block, androidx.compose.ui.geometry.Rect(origin + p.at,
                        androidx.compose.ui.geometry.Size(p.width.toFloat(), p.layout.size.height.toFloat())), p.layout, p.startChar)
                }
            }
        }
        Canvas(
            Modifier.fillMaxSize()
                .onGloballyPositioned { origin = it.positionInRoot() }
                .semantics { contentDescription = placed.joinToString("\n") { it.layout.layoutInput.text.text } },
        ) {
            for (p in placed) drawText(p.layout, topLeft = p.at)
        }
    }
}

private class PlacedSlice(val block: Int, val layout: androidx.compose.ui.text.TextLayoutResult, val at: Offset, val width: Int, val startChar: Int)

/** Page-turn effects. Pages with a higher index are drawn on top, so effects animate the incoming page. */
private fun Modifier.pageTransition(pager: PagerState, page: Int, anim: PageAnim): Modifier = this
    .graphicsLayer {
        val offset = (pager.currentPage - page) + pager.currentPageOffsetFraction
        when (anim) {
            PageAnim.SLIDE -> {}
            PageAnim.COVER -> {
                // the page being left stays in place; the incoming one slides over it
                if (offset > 0f) translationX = offset * size.width
                else if (offset < 0f) { shadowElevation = 24f }
            }
            PageAnim.FADE -> {
                translationX = offset * size.width
                alpha = (1f - abs(offset)).coerceIn(0f, 1f)
            }
            PageAnim.NONE -> {
                translationX = offset * size.width
                alpha = if (abs(offset) < 0.5f) 1f else 0f
            }
            else -> {} // curls are drawn by CurlPager
        }
    }
    .drawWithContent {
        drawContent()
        if (anim == PageAnim.COVER) {
            val offset = (pager.currentPage - page) + pager.currentPageOffsetFraction
            if (offset > 0f) drawRect(Color.Black, alpha = (offset * 0.35f).coerceIn(0f, 0.35f))
        }
    }

// ---------------- Panels ----------------

@Composable
private fun TopPanel(title: String, chapter: String, c: PageColors, marked: Boolean, onBack: () -> Unit, simple: Boolean = false, onSearch: () -> Unit, onBookmark: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().background(c.panel).statusBarsPadding().padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RoundIcon(onBack, c, "Назад к библиотеке") { Icon(Icons.AutoMirrored.Rounded.ArrowBack, null) }
        Column(Modifier.weight(1f).padding(horizontal = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(title, color = c.text, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(chapter, color = c.muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (simple) return@Row
        RoundIcon(onSearch, c, "Поиск по книге") { Icon(Icons.Rounded.Search, null) }
        Spacer(Modifier.width(6.dp))
        RoundIcon(onBookmark, c, if (marked) "Убрать закладку" else "Добавить закладку") {
            Icon(if (marked) Icons.Rounded.Bookmark else Icons.Rounded.BookmarkBorder, null, tint = if (marked) c.accent else c.text)
        }
    }
}

@Composable
fun RoundIcon(onClick: () -> Unit, c: PageColors, label: String, icon: @Composable () -> Unit) {
    IconButton(
        onClick = onClick,
        colors = IconButtonDefaults.iconButtonColors(containerColor = c.text.copy(alpha = 0.08f), contentColor = c.text),
        modifier = Modifier.semantics { contentDescription = label },
    ) { icon() }
}

@Composable
private fun BottomPanel(
    book: ParsedBook, ctl: ReaderController, c: PageColors, minutesLeft: Int, personal: Boolean,
    onText: () -> Unit, onToc: () -> Unit, onToggleNight: () -> Unit, onAi: () -> Unit,
    simple: Boolean = false, onFont: (Float) -> Unit = {},
    bedtimeOn: Boolean = false, onBedtime: () -> Unit = {}, onAutoScroll: (() -> Unit)? = null,
) {
    var dragging by remember { mutableStateOf(false) }
    var dragValue by remember { mutableFloatStateOf(0f) }
    val value = if (dragging) dragValue else ctl.sliderValue
    val label = if (dragging) "${(value / ctl.sliderMax * 100).roundToInt()}%" else ctl.pageLabel

    Column(Modifier.fillMaxWidth().background(c.panel).navigationBarsPadding().padding(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 8.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, color = c.muted, fontSize = 12.sp, maxLines = 1, modifier = Modifier.weight(1f))
            val pages = ctl.pagesToChapter
            Text(
                buildString {
                    append("до конца главы ")
                    if (pages != null) append("$pages стр. · ")
                    append(formatMinutes(minutesLeft))
                    if (personal) append(" (твой темп)")
                },
                color = c.muted, fontSize = 12.sp, maxLines = 1,
            )
        }
        Box(contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxWidth().height(10.dp).padding(horizontal = 10.dp)) {
                if (ctl.ticks.size in 1..80) for (t in ctl.ticks) {
                    val x = size.width * t
                    drawLine(c.muted.copy(alpha = 0.6f), Offset(x, 0f), Offset(x, size.height), strokeWidth = 2f)
                }
            }
            Slider(
                value = value.coerceIn(0f, ctl.sliderMax), valueRange = 0f..ctl.sliderMax,
                onValueChange = { dragging = true; dragValue = it },
                onValueChangeFinished = { ctl.jumpToSlider(dragValue); dragging = false },
                colors = SliderDefaults.colors(thumbColor = c.accent, activeTrackColor = c.accent, inactiveTrackColor = c.text.copy(alpha = 0.15f)),
            )
        }
        if (simple) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
            PanelButton("Мельче", c, { onFont(-2f) }) { Text("А−", color = c.text, fontSize = 20.sp) }
            PanelButton("Крупнее", c, { onFont(2f) }) { Text("А+", color = c.text, fontSize = 20.sp) }
            PanelButton(if (c.isDark) "Светлая" else "Ночь", c, onToggleNight) {
                Icon(if (c.isDark) Icons.Rounded.LightMode else Icons.Rounded.DarkMode, null)
            }
        } else Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
            PanelButton("Текст", c, onText) { Icon(Icons.Rounded.TextFields, null) }
            PanelButton("Главы", c, onToc) { Icon(Icons.AutoMirrored.Rounded.FormatListBulleted, null) }
            PanelButton("ИИ", c, onAi) { Icon(Icons.Rounded.AutoAwesome, null) }
            onAutoScroll?.let { PanelButton("Авто", c, it) { Icon(Icons.Rounded.PlayArrow, null) } }
            PanelButton(if (bedtimeOn) "Не спать" else "Сон", c, onBedtime) { Icon(Icons.Rounded.Bedtime, null, tint = if (bedtimeOn) c.accent else c.text) }
            PanelButton(if (c.isDark) "Светлая" else "Ночь", c, onToggleNight) {
                Icon(if (c.isDark) Icons.Rounded.LightMode else Icons.Rounded.DarkMode, null)
            }
        }
    }
}

@Composable
fun PanelButton(label: String, c: PageColors, onClick: () -> Unit, icon: @Composable () -> Unit) {
    TextButton(onClick = onClick, shape = RoundedCornerShape(16.dp)) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.size(24.dp), contentAlignment = Alignment.Center) {
                CompositionLocalProvider(LocalContentColor provides c.text) { icon() }
            }
            Spacer(Modifier.height(2.dp))
            Text(label, color = c.text, fontSize = 12.sp)
        }
    }
}


/** Session cache of paragraph translations for the bilingual mode. */
private val translationCache = java.util.concurrent.ConcurrentHashMap<String, String>()

@Composable
private fun TranslationLine(text: String, spec: BlockSpec, c: PageColors) {
    val ctx = LocalContext.current
    val d = LocalDensity.current
    val tr by produceState(translationCache[text], text) {
        if (value != null) return@produceState
        value = runCatching {
            val src = io.github.galis228.materialereader.data.Translate.detect(text)
            io.github.galis228.materialereader.data.Translate.translate(ctx, text, src).text
        }.getOrNull()?.takeIf { it.isNotBlank() && it.trim() != text.trim() }?.also { translationCache[text] = it }
    }
    val t = tr ?: return
    with(d) {
        Text(
            t, style = spec.style.copy(color = c.muted, fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                fontSize = spec.style.fontSize * 0.9f, textIndent = null),
            modifier = Modifier.fillMaxWidth().padding(start = spec.padStart.toDp() + 12.dp, bottom = spec.bottom.toDp() + 4.dp)
                .drawBehind { drawLine(c.accent.copy(alpha = 0.4f), Offset(-8.dp.toPx(), 0f), Offset(-8.dp.toPx(), size.height), 2.dp.toPx()) },
        )
    }
}
