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
import io.github.galis228.materialereader.data.PageAnim
import io.github.galis228.materialereader.data.ReadMode
import io.github.galis228.materialereader.data.ReaderSettings
import io.github.galis228.materialereader.data.ReaderTheme
import io.github.galis228.materialereader.data.SettingsRepo
import io.github.galis228.materialereader.parse.Block
import io.github.galis228.materialereader.parse.ParsedBook
import io.github.galis228.materialereader.parse.countWords
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.roundToInt

private const val WPM = 200

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
    val colors = pageColors(s.theme, MaterialTheme.colorScheme)
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
    val books by library.books.collectAsState()
    val bookmarks = books.firstOrNull { it.id == id }?.bookmarks.orEmpty()

    val wordsBefore = remember(book) {
        IntArray(book.blocks.size + 1).also { a -> book.blocks.forEachIndexed { i, b -> a[i + 1] = a[i] + countWords(b.text) } }
    }
    val chapterIndex by remember(book) { derivedStateOf { book.chapters.indexOfLast { it.startBlock <= ctl.block }.coerceAtLeast(0) } }

    SystemBars(uiVisible, colors.isDark, settings.keepScreenOn)

    // Persist position (debounced) and count reading time
    var scrollOffset by remember { mutableIntStateOf(startOffset) }
    LaunchedEffect(Unit) {
        var lastSave = System.currentTimeMillis()
        snapshotFlow { Triple(ctl.block, ctl.char, scrollOffset) }.debounce(700).collect { (b, c, off) ->
            val now = System.currentTimeMillis()
            library.updatePosition(id, b, off, c, (now - lastSave).coerceAtMost(5 * 60_000L))
            lastSave = now
        }
    }
    DisposableEffect(Unit) {
        onDispose { runBlocking { library.updatePosition(id, ctl.block, scrollOffset, ctl.char, 0) } }
    }

    DisposableEffect(settings.volumeKeys) {
        VolumeKeys.enabled = settings.volumeKeys
        onDispose { VolumeKeys.enabled = false }
    }
    LaunchedEffect(Unit) { VolumeKeys.events.collect { ctl.turn(it) } }
    BackHandler(enabled = sheet != null) { sheet = null }

    val onTap: (Float) -> Unit = { x ->
        when {
            uiVisible -> uiVisible = false
            settings.tapToTurn && x < 0.28f -> ctl.turn(-1)
            settings.tapToTurn && x > 0.72f -> ctl.turn(1)
            else -> uiVisible = true
        }
    }

    val hits = remember { BlockHits() }
    val highlights = books.firstOrNull { it.id == id }?.highlights.orEmpty()
    val hlMap = remember(highlights) { highlights.associateBy { it.block } }
    val onLongPress: (Offset) -> Unit = { p -> hits.at(p)?.let { paragraph = it; uiVisible = false; sheet = Sheet.PARAGRAPH } }

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
        CompositionLocalProvider(LocalBlockHits provides hits, LocalHighlights provides hlMap, LocalPageDark provides colors.isDark) {
            when (settings.mode) {
                ReadMode.SCROLL -> ScrollContent(book, settings, colors, ctl, startOffset, onTap, onLongPress) { scrollOffset = it }
                ReadMode.PAGES -> PagedContent(book, settings, colors, ctl, onTap, onLongPress)
            }
        }
        if (settings.warmth > 0.01f) Box(Modifier.fillMaxSize().background(Color(0xFFFF8F00).copy(alpha = settings.warmth * 0.28f)))

        if (!uiVisible && settings.mode == ReadMode.SCROLL) {
            val p = if (ctl.sliderMax <= 0f) 1f else (ctl.sliderValue / ctl.sliderMax).coerceIn(0f, 1f)
            Box(Modifier.align(Alignment.BottomStart).fillMaxWidth(p).height(2.dp).background(colors.accent.copy(alpha = 0.5f)))
        }

        val marked = bookmarks.any { it.block == ctl.block && abs(it.charPos - ctl.char) < 400 }
        AnimatedVisibility(uiVisible, Modifier.align(Alignment.TopCenter), enter = slideInVertically { -it } + fadeIn(), exit = slideOutVertically { -it } + fadeOut()) {
            TopPanel(
                book.title, book.chapters.getOrNull(chapterIndex)?.title.orEmpty(), colors, marked, onBack,
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
                book, ctl, colors, minutesLeft = (leftWords.toFloat() / WPM).roundToInt(),
                onText = { sheet = Sheet.TEXT; uiVisible = false }, onToc = { sheet = Sheet.TOC },
                onAi = { aiPreset = null; sheet = Sheet.AI },
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
        Sheet.PARAGRAPH -> ParagraphSheet(
            text = book.blocks.getOrNull(paragraph)?.text.orEmpty(), highlight = hlMap[paragraph],
            onSet = { color, note -> scope.launch { library.setHighlight(id, paragraph, color, note) } },
            onAskAi = { aiPreset = book.blocks.getOrNull(paragraph)?.text; sheet = Sheet.AI },
        ) { if (sheet == Sheet.PARAGRAPH) sheet = null }
        Sheet.AI -> AiSheet(
            book, ctl.block, book.chapters.getOrNull(chapterIndex)?.startBlock ?: 0,
            pageText = { aiPreset ?: ctl.visibleText() }, preset = aiPreset,
            onOpenSettings = { sheet = null; onAppSettings() },
        ) { sheet = null }
        null -> {}
    }
}

private enum class Sheet { TEXT, TOC, AI, SEARCH, PARAGRAPH }

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
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val total = (book.blocks.size - 1).coerceAtLeast(1)

    LaunchedEffect(list) {
        snapshotFlow { list.firstVisibleItemIndex to list.firstVisibleItemScrollOffset }.collect { (i, off) ->
            ctl.block = i; ctl.char = 0; ctl.sliderValue = i.toFloat(); onOffset(off)
        }
    }
    ctl.sliderMax = total.toFloat()
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
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val w = constraints.maxWidth.toFloat()
        LazyColumn(
            state = list,
            modifier = Modifier.fillMaxSize().pointerInput(Unit) { detectTapGestures(onLongPress = { onLongPress(it) }) { onTap(it.x / w) } },
            contentPadding = PaddingValues(
                start = s.margin.dp, end = s.margin.dp,
                top = bars.calculateTopPadding() + 28.dp, bottom = bars.calculateBottomPadding() + 120.dp,
            ),
        ) {
            itemsIndexed(book.blocks, contentType = { _, b -> b.kind }) { i, b ->
                val spec = specs.getValue(b.kind)
                SliceText(if (b.kind == Block.Kind.SEPARATOR) "* * *" else b.text, spec, 0, first = i == 0, block = i)
            }
        }
    }
}

@Composable
private fun rememberSpecs(s: ReaderSettings, c: PageColors): Map<Block.Kind, BlockSpec> {
    val density = LocalDensity.current
    return remember(s.fontSize, s.lineHeight, s.font, s.justify, c, density) {
        Block.Kind.entries.associateWith { blockSpec(it, s, c.text, c.muted, density) }
    }
}

@Composable
private fun SliceText(text: String, spec: BlockSpec, start: Int, first: Boolean, block: Int) {
    val d = LocalDensity.current
    val hits = LocalBlockHits.current
    val hl = LocalHighlights.current[block]
    val dark = LocalPageDark.current
    val token = remember { Any() }
    if (hits != null) DisposableEffect(token) { onDispose { hits.remove(token) } }
    with(d) {
        Text(
            text, style = spec.forSlice(start),
            modifier = Modifier.fillMaxWidth()
                .then(if (hits != null) Modifier.onGloballyPositioned { hits.put(token, block, it.boundsInRoot()) } else Modifier)
                .padding(
                top = spec.topOnPage(first).toDp(), bottom = spec.bottom.toDp(),
                start = spec.padStart.toDp(), end = spec.padEnd.toDp(),
            ).then(if (hl != null) Modifier.background(highlightTint(hl.color, dark), RoundedCornerShape(4.dp)) else Modifier),
        )
    }
}

// ---------------- Page mode ----------------

private data class PageKey(val w: Int, val h: Int, val size: Float, val line: Float, val font: Any, val justify: Boolean)

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
        val key = PageKey(wPx, hPx, s.fontSize, s.lineHeight, s.font, s.justify)

        var progress by remember { mutableFloatStateOf(0f) }
        val pages by produceState<List<Page>?>(PageCache.pages.takeIf { PageCache.key == book to key }, book, key) {
            if (PageCache.key == book to key) { value = PageCache.pages; return@produceState }
            value = null
            val measurer = TextMeasurer(fontResolver, density, layoutDir, cacheSize = 0)
            val paginator = Paginator(measurer, specs, wPx, hPx)
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
        var spread by remember { mutableIntStateOf((list.indexOfPosition(ctl.block, ctl.char) / per).coerceIn(0, spreads - 1)) }
        val scope = rememberCoroutineScope()
        val anim = rememberUpdatedState(s.anim)
        var curlTurn by remember { mutableStateOf<(Int) -> Unit>({}) }

        // After re-pagination (font change, rotation) keep the reading position
        LaunchedEffect(list, per) { spread = (list.indexOfPosition(ctl.block, ctl.char) / per).coerceIn(0, spreads - 1) }
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

        if (s.anim == PageAnim.CURL) {
            ctl.turn = { dir -> curlTurn(dir) }
            CurlPager(
                count = spreads, current = spread, onCurrentChange = { spread = it },
                paper = lerpColor(c.bg, if (c.isDark) Color.White else Color.Black, 0.04f),
                registerTurn = { curlTurn = it }, onTap = onTap, onLongPress = onLongPress, page = spreadView,
            )
        } else {
            val pager = rememberPagerState(initialPage = spread) { spreads }
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
                modifier = Modifier.fillMaxSize().pointerInput(Unit) { detectTapGestures(onLongPress = { onLongPress(it) }) { onTap(it.x / w) } },
                beyondViewportPageCount = 1,
            ) { sp ->
                Box(Modifier.fillMaxSize().pageTransition(pager, sp, s.anim)) { spreadView(sp) }
            }
        }
    }
}

private fun lerpColor(a: Color, b: Color, t: Float) = androidx.compose.ui.graphics.lerp(a, b, t)

@Composable
private fun PageView(page: Page, book: ParsedBook, specs: Map<Block.Kind, BlockSpec>) {
    Column(Modifier.fillMaxWidth()) {
        page.slices.forEachIndexed { k, sl ->
            val b = book.blocks[sl.block]
            val full = if (b.kind == Block.Kind.SEPARATOR) "* * *" else b.text
            val text = sliceText(full, sl.start, sl.end)
            SliceText(text, specs.getValue(b.kind), sl.start, first = k == 0, block = sl.block)
        }
    }
}

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
            PageAnim.CURL -> {
                translationX = offset * size.width
                if (offset < 0f) {
                    // incoming page turns over the spine like a sheet of paper
                    transformOrigin = TransformOrigin(0f, 0.5f)
                    rotationY = (-offset * 100f).coerceAtMost(90f)
                    cameraDistance = 14f * density
                    shadowElevation = 16f
                }
            }
            PageAnim.NONE -> {
                translationX = offset * size.width
                alpha = if (abs(offset) < 0.5f) 1f else 0f
            }
        }
    }
    .drawWithContent {
        drawContent()
        if (anim == PageAnim.CURL || anim == PageAnim.COVER) {
            val offset = (pager.currentPage - page) + pager.currentPageOffsetFraction
            if (offset > 0f) drawRect(Color.Black, alpha = (offset * 0.35f).coerceIn(0f, 0.35f))
        }
    }

// ---------------- Panels ----------------

@Composable
private fun TopPanel(title: String, chapter: String, c: PageColors, marked: Boolean, onBack: () -> Unit, onSearch: () -> Unit, onBookmark: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().background(c.panel).statusBarsPadding().padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RoundIcon(onBack, c, "Назад к библиотеке") { Icon(Icons.AutoMirrored.Rounded.ArrowBack, null) }
        Column(Modifier.weight(1f).padding(horizontal = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(title, color = c.text, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(chapter, color = c.muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
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
    book: ParsedBook, ctl: ReaderController, c: PageColors, minutesLeft: Int,
    onText: () -> Unit, onToc: () -> Unit, onToggleNight: () -> Unit, onAi: () -> Unit,
) {
    var dragging by remember { mutableStateOf(false) }
    var dragValue by remember { mutableFloatStateOf(0f) }
    val value = if (dragging) dragValue else ctl.sliderValue
    val label = if (dragging) "${(value / ctl.sliderMax * 100).roundToInt()}%" else ctl.pageLabel

    Column(Modifier.fillMaxWidth().background(c.panel).navigationBarsPadding().padding(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 8.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, color = c.muted, fontSize = 12.sp, maxLines = 1, modifier = Modifier.weight(1f))
            Text("до конца главы ${formatMinutes(minutesLeft)}", color = c.muted, fontSize = 12.sp)
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
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
            PanelButton("Текст", c, onText) { Icon(Icons.Rounded.TextFields, null) }
            PanelButton("Главы", c, onToc) { Icon(Icons.AutoMirrored.Rounded.FormatListBulleted, null) }
            PanelButton("ИИ", c, onAi) { Icon(Icons.Rounded.AutoAwesome, null) }
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

