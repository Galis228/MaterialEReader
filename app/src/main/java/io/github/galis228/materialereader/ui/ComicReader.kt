package io.github.galis228.materialereader.ui

import io.github.galis228.materialereader.data.ComicView
import androidx.compose.material.icons.rounded.TextFields
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.LazyColumn
import android.graphics.BitmapFactory
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.galis228.materialereader.VolumeKeys
import io.github.galis228.materialereader.data.BookEntry
import io.github.galis228.materialereader.data.Library
import io.github.galis228.materialereader.data.ReaderSettings
import io.github.galis228.materialereader.parse.ComicArchive
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt


@Composable
fun ComicReader(entry: BookEntry, s: ReaderSettings, colors: PageColors, onSettings: (ReaderSettings) -> Unit, onBack: () -> Unit, onOpenBook: (String) -> Unit = {}) {
    val ctx = LocalContext.current
    val library = remember { Library.get(ctx) }
    val comic by produceState<Result<ComicArchive>?>(null, entry.id) {
        value = runCatching { library.openComic(entry.id) }
    }
    DisposableEffect(comic) { onDispose { comic?.getOrNull()?.close() } }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        val c = comic
        when {
            c == null -> CircularProgressIndicator(Modifier.align(Alignment.Center), color = Color.White)
            c.isFailure -> Column(Modifier.align(Alignment.Center).padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(c.exceptionOrNull()?.message ?: "Не удалось открыть комикс", color = Color.White, textAlign = TextAlign.Center)
                TextButton(onClick = onBack) { Text("Назад") }
            }
            else -> ComicPages(entry, c.getOrThrow(), s, onSettings, onBack, onOpenBook)
        }
    }
}

@OptIn(kotlinx.coroutines.FlowPreview::class)
@Composable
private fun ComicPages(entry: BookEntry, comic: ComicArchive, s: ReaderSettings, onSettings: (ReaderSettings) -> Unit, onBack: () -> Unit, onOpenBook: (String) -> Unit) {
    val ctx = LocalContext.current
    val library = Library.get(ctx)
    val count = comic.pages.size
    val scope = rememberCoroutineScope()
    var uiVisible by remember { mutableStateOf(false) }
    var zoomed by remember { mutableStateOf(false) }
    val rtl = s.mangaRtl
    val next = remember(entry.id) { if (s.comicAutoNext) library.nextInSeries(entry.id) else null }
    var position by remember { mutableIntStateOf(entry.position.coerceIn(0, count - 1)) }

    SystemBars(uiVisible, dark = true, keepScreenOn = s.keepScreenOn)

    LaunchedEffect(Unit) {
        var last = System.currentTimeMillis()
        snapshotFlow { position }.debounce(500).collect { p ->
            val now = System.currentTimeMillis()
            library.updatePosition(entry.id, p, 0, 0, (now - last).coerceAtMost(5 * 60_000L)); last = now
        }
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val w = constraints.maxWidth
        val h = constraints.maxHeight
        val spreads = s.twoPages && w > h && s.comicView != ComicView.WEBTOON
        if (s.comicView == ComicView.WEBTOON) {
            val list = rememberLazyListState(initialFirstVisibleItemIndex = position)
            LaunchedEffect(list) { snapshotFlow { list.firstVisibleItemIndex }.collect { position = it.coerceAtMost(count - 1) } }
            LazyColumn(state = list, modifier = Modifier.fillMaxSize().pointerInput(Unit) { detectTapGestures { uiVisible = !uiVisible } }) {
                items(count) { i -> StripPage(comic, i, w, s) }
                if (next != null) item { NextChapterCard(next) { onOpenBook(next.id) } }
            }
        } else {
            // pager pages: single pages or pairs (landscape spreads); the last extra page leads to the next volume
            val groups = remember(count, spreads) { if (spreads) (0 until count step 2).map { listOfNotNull(it, (it + 1).takeIf { n -> n < count }) } else (0 until count).map { listOf(it) } }
            val pages = groups.size + if (next != null) 1 else 0
            val pager = rememberPagerState(initialPage = groups.indexOfFirst { position in it }.coerceAtLeast(0)) { pages }
            var panel by remember { mutableIntStateOf(0) }
            val panelCounts = remember { mutableMapOf<Int, Int>() }
            LaunchedEffect(pager) {
                snapshotFlow { pager.settledPage }.collect { p ->
                    groups.getOrNull(p)?.let { position = it.first() }
                    if (next != null && p == groups.size) { kotlinx.coroutines.delay(900); if (pager.settledPage == groups.size) onOpenBook(next.id) }
                }
            }
            fun turn(dir: Int) = scope.launch { pager.animateScrollToPage((pager.currentPage + dir).coerceIn(0, pages - 1)) }
            fun step(dir: Int) {
                val n = panelCounts[pager.currentPage] ?: 0
                if (s.comicView == ComicView.PANELS && !spreads && n > 1) {
                    val np = panel + dir
                    if (np in 0 until n) { panel = np; return }
                    panel = if (dir > 0) 0 else Int.MAX_VALUE
                }
                turn(dir)
            }
            DisposableEffect(s.volumeKeys) { VolumeKeys.enabled = s.volumeKeys; onDispose { VolumeKeys.enabled = false } }
            LaunchedEffect(Unit) { VolumeKeys.events.collect { step(it) } }
            LaunchedEffect(pager.currentPage) { if (panel != Int.MAX_VALUE) panel = 0 }
            HorizontalPager(
                state = pager, reverseLayout = rtl, userScrollEnabled = !zoomed, beyondViewportPageCount = 1,
                modifier = Modifier.fillMaxSize(),
            ) { page ->
                val g = groups.getOrNull(page)
                if (g == null) NextChapterCard(next!!) { onOpenBook(next.id) }
                else ComicPage(comic, g, w, h, s, panel = if (s.comicView == ComicView.PANELS && !spreads && page == pager.currentPage) panel else -1,
                    onPanels = { n -> panelCounts[page] = n; if (page == pager.currentPage && panel == Int.MAX_VALUE) panel = (n - 1).coerceAtLeast(0) },
                    onZoomChange = { zoomed = it }) { x ->
                    val fx = x / w
                    when {
                        uiVisible -> uiVisible = false
                        fx < 0.28f -> step(if (rtl) 1 else -1)
                        fx > 0.72f -> step(if (rtl) -1 else 1)
                        else -> uiVisible = true
                    }
                }
            }
            ComicBars(entry, s, onSettings, onBack, uiVisible, count, position, rtl) { target ->
                scope.launch { pager.scrollToPage(groups.indexOfFirst { target in it }.coerceAtLeast(0)) }
            }
            return@BoxWithConstraints
        }
        ComicBars(entry, s, onSettings, onBack, uiVisible, count, position, rtl) { }
    }
}

@Composable
private fun ComicBars(entry: BookEntry, s: ReaderSettings, onSettings: (ReaderSettings) -> Unit, onBack: () -> Unit, visible: Boolean,
                      count: Int, position: Int, rtl: Boolean, onSeek: (Int) -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var more by remember { mutableStateOf(false) }
    AnimatedVisibility(visible, enter = fadeIn(), exit = fadeOut()) {
        Box(Modifier.fillMaxSize()) {
            Row(
                Modifier.fillMaxWidth().background(Color(0xE6101114)).statusBarsPadding().padding(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val dark = pageColors(io.github.galis228.materialereader.data.ReaderTheme.NIGHT, androidx.compose.material3.MaterialTheme.colorScheme)
                RoundIcon(onBack, dark, "Назад к библиотеке") { Icon(Icons.AutoMirrored.Rounded.ArrowBack, null) }
                Text(entry.title, color = Color.White, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f).padding(horizontal = 12.dp))
                TextButton(onClick = { onSettings(s.copy(mangaRtl = !rtl)) }) {
                    Icon(Icons.Rounded.SwapHoriz, null, tint = Color.White)
                    Spacer(Modifier.padding(2.dp))
                    Text(if (rtl) "Манга ←" else "Комикс →", color = Color.White)
                }
                Box {
                    androidx.compose.material3.IconButton(onClick = { more = true }) { Icon(Icons.Rounded.Tune, "Вид", tint = Color.White) }
                    androidx.compose.material3.DropdownMenu(more, { more = false }) {
                        ComicView.entries.forEach { v ->
                            androidx.compose.material3.DropdownMenuItem(text = { Text(v.label) }, onClick = { onSettings(s.copy(comicView = v)); more = false },
                                trailingIcon = { if (s.comicView == v) Icon(Icons.Rounded.Check, null) })
                        }
                        androidx.compose.material3.HorizontalDivider()
                        androidx.compose.material3.DropdownMenuItem(text = { Text("Разворот в альбомной") }, onClick = { onSettings(s.copy(twoPages = !s.twoPages)) },
                            trailingIcon = { androidx.compose.material3.Checkbox(s.twoPages, null) })
                        androidx.compose.material3.DropdownMenuItem(text = { Text("Обрезать поля") }, onClick = { onSettings(s.copy(comicCrop = !s.comicCrop)) },
                            trailingIcon = { androidx.compose.material3.Checkbox(s.comicCrop, null) })
                        androidx.compose.material3.DropdownMenuItem(text = { Text("Больше контраста") }, onClick = { onSettings(s.copy(comicContrast = !s.comicContrast)) },
                            trailingIcon = { androidx.compose.material3.Checkbox(s.comicContrast, null) })
                        androidx.compose.material3.DropdownMenuItem(text = { Text("Сразу следующий том") }, onClick = { onSettings(s.copy(comicAutoNext = !s.comicAutoNext)) },
                            trailingIcon = { androidx.compose.material3.Checkbox(s.comicAutoNext, null) })
                        if (entry.format == io.github.galis228.materialereader.parse.BookFormat.PDF && io.github.galis228.materialereader.parse.PdfText.supported) {
                            androidx.compose.material3.HorizontalDivider()
                            androidx.compose.material3.DropdownMenuItem(text = { Text("Читать как текст") }, leadingIcon = { Icon(Icons.Rounded.TextFields, null) }, onClick = {
                                more = false
                                scope.launch {
                                    Library.get(ctx).pdfToText(entry.id)
                                        .onSuccess { android.widget.Toast.makeText(ctx, "Текстовая копия «${it.title}» добавлена в библиотеку", android.widget.Toast.LENGTH_LONG).show() }
                                        .onFailure { android.widget.Toast.makeText(ctx, it.message, android.widget.Toast.LENGTH_LONG).show() }
                                }
                            })
                        }
                    }
                }
            }
            Column(
                Modifier.align(Alignment.BottomCenter).fillMaxWidth().background(Color(0xE6101114)).navigationBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 10.dp),
            ) {
                var drag by remember { mutableStateOf<Float?>(null) }
                val v = drag ?: position.toFloat()
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("${v.roundToInt() + 1} / $count", color = Color(0xFFBDBDBD), fontSize = 12.sp)
                    Text(s.comicView.label.lowercase() + " · " + if (rtl) "справа налево" else "слева направо", color = Color(0xFFBDBDBD), fontSize = 12.sp)
                }
                Slider(
                    value = v, valueRange = 0f..(count - 1).coerceAtLeast(1).toFloat(),
                    onValueChange = { drag = it },
                    onValueChangeFinished = { drag?.let { onSeek(it.roundToInt()) }; drag = null },
                    modifier = Modifier.graphicsLayer { if (rtl) scaleX = -1f },
                    colors = SliderDefaults.colors(thumbColor = Color.White, activeTrackColor = Color.White, inactiveTrackColor = Color(0x55FFFFFF)),
                )
                Spacer(Modifier.height(4.dp))
            }
        }
    }
}

@Composable
private fun NextChapterCard(next: BookEntry, onOpen: () -> Unit) {
    Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Конец тома", color = Color(0xFFBDBDBD), fontSize = 14.sp)
            Spacer(Modifier.height(8.dp))
            Text(next.title, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 20.sp, textAlign = TextAlign.Center)
            Spacer(Modifier.height(16.dp))
            androidx.compose.material3.FilledTonalButton(onClick = onOpen) { Text("Читать дальше") }
        }
    }
}

private fun contrastFilter(on: Boolean): ColorFilter? = if (!on) null else {
    val c = 1.3f; val t = (1 - c) * 128f
    ColorFilter.colorMatrix(ColorMatrix(floatArrayOf(c, 0f, 0f, 0f, t, 0f, c, 0f, 0f, t, 0f, 0f, c, 0f, t, 0f, 0f, 0f, 1f, 0f)))
}

/** Decodes a page at a size fit for [w]×[h] (with room to zoom); trims margins if asked. Returns the bitmap and its layout. */
private suspend fun loadPage(comic: ComicArchive, index: Int, w: Int, h: Int, crop: Boolean, layout: Boolean, rtl: Boolean): Pair<android.graphics.Bitmap, ComicImage.Layout?>? =
    withContext(Dispatchers.IO) {
        runCatching {
            val bytes = comic.page(index)
            val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, o)
            var sample = 1
            while (o.outWidth / (sample * 2) >= w * 1.5 && o.outHeight / (sample * 2) >= h * 1.5) sample *= 2
            var bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample }) ?: return@runCatching null
            val lay = if (crop || layout) ComicImage.analyze(bmp, rtl) else null
            if (crop && lay != null) {
                val c = lay.content
                val pad = 0.01f
                val l = ((c.left - pad).coerceAtLeast(0f) * bmp.width).toInt(); val t = ((c.top - pad).coerceAtLeast(0f) * bmp.height).toInt()
                val r = ((c.right + pad).coerceAtMost(1f) * bmp.width).toInt(); val b = ((c.bottom + pad).coerceAtMost(1f) * bmp.height).toInt()
                if (r - l > bmp.width / 3 && b - t > bmp.height / 3 && (r - l < bmp.width || b - t < bmp.height)) {
                    val cropped = android.graphics.Bitmap.createBitmap(bmp, l, t, r - l, b - t)
                    val fx = { x: Float -> ((x * bmp.width - l) / (r - l)).coerceIn(0f, 1f) }
                    val fy = { y: Float -> ((y * bmp.height - t) / (b - t)).coerceIn(0f, 1f) }
                    val panels = lay.panels.map { android.graphics.RectF(fx(it.left), fy(it.top), fx(it.right), fy(it.bottom)) }
                    bmp = cropped
                    return@runCatching bmp to ComicImage.Layout(android.graphics.RectF(0f, 0f, 1f, 1f), panels)
                }
            }
            bmp to lay
        }.getOrNull()
    }

/** One page of the vertical strip, full width. */
@Composable
private fun StripPage(comic: ComicArchive, index: Int, w: Int, s: ReaderSettings) {
    val page by produceState<Pair<android.graphics.Bitmap, ComicImage.Layout?>?>(null, index, s.comicCrop) {
        value = loadPage(comic, index, w, w * 3, s.comicCrop, false, false)
    }
    val p = page
    if (p == null) Box(Modifier.fillMaxWidth().aspectRatio(0.7f), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Color(0x88FFFFFF)) }
    else Image(p.first.asImageBitmap(), "Страница ${index + 1}", Modifier.fillMaxWidth().aspectRatio(p.first.width.toFloat() / p.first.height),
        contentScale = ContentScale.FillWidth, colorFilter = contrastFilter(s.comicContrast))
}

@Composable
private fun ComicPage(comic: ComicArchive, indices: List<Int>, w: Int, h: Int, s: ReaderSettings, panel: Int,
                      onPanels: (Int) -> Unit, onZoomChange: (Boolean) -> Unit, onTap: (Float) -> Unit) {
    val single = indices.size == 1
    val wantLayout = single && s.comicView == ComicView.PANELS
    val loaded by produceState<List<Pair<android.graphics.Bitmap, ComicImage.Layout?>>?>(null, indices, s.comicCrop, wantLayout, s.mangaRtl) {
        val each = w / indices.size
        value = indices.mapNotNull { loadPage(comic, it, each, h, s.comicCrop, wantLayout, s.mangaRtl) }
    }
    LaunchedEffect(loaded) { loaded?.firstOrNull()?.second?.let { onPanels(it.panels.size) } ?: run { if (loaded != null) onPanels(0) } }
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    fun clamp(o: Offset, sc: Float) = Offset(
        o.x.coerceIn(-(sc - 1) * w / 2, (sc - 1) * w / 2),
        o.y.coerceIn(-(sc - 1) * h / 2, (sc - 1) * h / 2),
    )
    // panel by panel: glide to the panel's frame
    LaunchedEffect(panel, loaded) {
        val (bmp, lay) = loaded?.firstOrNull() ?: return@LaunchedEffect
        val r = lay?.panels?.getOrNull(panel.coerceAtMost((lay.panels.size - 1).coerceAtLeast(0)))
        val (ts, to) = if (panel < 0 || r == null) 1f to Offset.Zero else {
            val fs = minOf(w.toFloat() / bmp.width, h.toFloat() / bmp.height)
            val dw = bmp.width * fs; val dh = bmp.height * fs
            val left = (w - dw) / 2; val top = (h - dh) / 2
            val pw = r.width() * dw; val ph = r.height() * dh
            val cx = left + r.centerX() * dw; val cy = top + r.centerY() * dh
            val sc = (minOf(w / pw, h / ph) * 0.96f).coerceIn(1f, 6f)
            sc to Offset((w / 2f - cx) * sc, (h / 2f - cy) * sc)
        }
        val s0 = scale; val o0 = offset
        androidx.compose.animation.core.animate(0f, 1f, animationSpec = androidx.compose.animation.core.tween<Float>(380)) { v, _ ->
            scale = s0 + (ts - s0) * v; offset = o0 + (to - o0) * v
        }
    }

    Box(
        Modifier.fillMaxSize()
            .pointerInput(indices) {
                detectTapGestures(
                    onDoubleTap = { p ->
                        if (scale > 1.01f) { scale = 1f; offset = Offset.Zero; onZoomChange(false) }
                        else {
                            scale = 2.5f
                            offset = clamp(Offset((w / 2f - p.x) * 1.5f, (h / 2f - p.y) * 1.5f), 2.5f)
                            onZoomChange(true)
                        }
                    },
                    onTap = { onTap(it.x) },
                )
            }
            .pointerInput(indices) {
                // pinch anytime; one-finger pan only while zoomed by hand (otherwise the pager swipes)
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    do {
                        val event = awaitPointerEvent()
                        val pinch = event.changes.count { it.pressed } >= 2
                        if (pinch || (scale > 1.01f && panel < 0)) {
                            val z = event.calculateZoom()
                            val pan = event.calculatePan()
                            val ns = (scale * z).coerceIn(1f, 5f)
                            offset = clamp(offset + pan, ns)
                            scale = ns
                            if (ns <= 1.01f) { scale = 1f; offset = Offset.Zero }
                            onZoomChange(scale > 1.01f)
                            event.changes.forEach { if (it.positionChanged()) it.consume() }
                        }
                    } while (event.changes.any { it.pressed })
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        val pages = loaded
        if (pages == null) CircularProgressIndicator(color = Color(0x88FFFFFF))
        else Row(
            Modifier.fillMaxSize().graphicsLayer { scaleX = scale; scaleY = scale; translationX = offset.x; translationY = offset.y },
            horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
        ) {
            // in a spread the right page comes first for manga
            val ordered = if (s.mangaRtl) pages.reversed() else pages
            ordered.forEachIndexed { i, (bmp, _) ->
                Image(bmp.asImageBitmap(), "Страница", if (ordered.size == 1) Modifier.fillMaxSize() else Modifier.weight(1f).fillMaxHeight(),
                    contentScale = ContentScale.Fit,
                    alignment = if (ordered.size == 1) Alignment.Center else if (i == 0) Alignment.CenterEnd else Alignment.CenterStart, colorFilter = contrastFilter(s.comicContrast))
            }
        }
    }
}
