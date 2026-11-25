package io.github.galis228.materialereader.ui

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

@OptIn(kotlinx.coroutines.FlowPreview::class)
@Composable
fun ComicReader(entry: BookEntry, s: ReaderSettings, colors: PageColors, onSettings: (ReaderSettings) -> Unit, onBack: () -> Unit) {
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
            else -> ComicPages(entry, c.getOrThrow(), s, onSettings, onBack)
        }
    }
}

@OptIn(kotlinx.coroutines.FlowPreview::class)
@Composable
private fun ComicPages(entry: BookEntry, comic: ComicArchive, s: ReaderSettings, onSettings: (ReaderSettings) -> Unit, onBack: () -> Unit) {
    val library = Library.get(LocalContext.current)
    val count = comic.pages.size
    val pager = rememberPagerState(initialPage = entry.position.coerceIn(0, count - 1)) { count }
    val scope = rememberCoroutineScope()
    var uiVisible by remember { mutableStateOf(false) }
    var zoomed by remember { mutableStateOf(false) }
    val rtl = s.mangaRtl

    SystemBars(uiVisible, dark = true, keepScreenOn = s.keepScreenOn)

    LaunchedEffect(Unit) {
        var last = System.currentTimeMillis()
        snapshotFlow { pager.currentPage }.debounce(500).collect { p ->
            val now = System.currentTimeMillis()
            library.updatePosition(entry.id, p, 0, 0, (now - last).coerceAtMost(5 * 60_000L)); last = now
        }
    }
    fun turn(dir: Int) = scope.launch { pager.animateScrollToPage((pager.currentPage + dir).coerceIn(0, count - 1)) }
    DisposableEffect(s.volumeKeys) { VolumeKeys.enabled = s.volumeKeys; onDispose { VolumeKeys.enabled = false } }
    LaunchedEffect(Unit) { VolumeKeys.events.collect { turn(it) } }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val w = constraints.maxWidth
        val h = constraints.maxHeight
        HorizontalPager(
            state = pager, reverseLayout = rtl, userScrollEnabled = !zoomed, beyondViewportPageCount = 1,
            modifier = Modifier.fillMaxSize(),
        ) { page ->
            ComicPage(comic, page, w, h, onZoomChange = { zoomed = it }) { x ->
                val fx = x / w
                when {
                    uiVisible -> uiVisible = false
                    fx < 0.28f -> turn(if (rtl) 1 else -1)
                    fx > 0.72f -> turn(if (rtl) -1 else 1)
                    else -> uiVisible = true
                }
            }
        }
    }

    AnimatedVisibility(uiVisible, enter = fadeIn(), exit = fadeOut()) {
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
            }
            Column(
                Modifier.align(Alignment.BottomCenter).fillMaxWidth().background(Color(0xE6101114)).navigationBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 10.dp),
            ) {
                var drag by remember { mutableStateOf<Float?>(null) }
                val v = drag ?: pager.currentPage.toFloat()
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("${v.roundToInt() + 1} / $count", color = Color(0xFFBDBDBD), fontSize = 12.sp)
                    Text(if (rtl) "справа налево" else "слева направо", color = Color(0xFFBDBDBD), fontSize = 12.sp)
                }
                Slider(
                    value = v, valueRange = 0f..(count - 1).coerceAtLeast(1).toFloat(),
                    onValueChange = { drag = it },
                    onValueChangeFinished = { drag?.let { scope.launch { pager.scrollToPage(it.roundToInt()) } }; drag = null },
                    modifier = Modifier.graphicsLayer { if (rtl) scaleX = -1f },
                    colors = SliderDefaults.colors(thumbColor = Color.White, activeTrackColor = Color.White, inactiveTrackColor = Color(0x55FFFFFF)),
                )
                Spacer(Modifier.height(4.dp))
            }
        }
    }
}

@Composable
private fun ComicPage(comic: ComicArchive, index: Int, w: Int, h: Int, onZoomChange: (Boolean) -> Unit, onTap: (Float) -> Unit) {
    val bitmap by produceState<ImageBitmap?>(null, index) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                val bytes = comic.page(index)
                val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, o)
                var sample = 1
                // keep enough resolution for 2.5x zoom without running out of memory
                while (o.outWidth / (sample * 2) >= w * 1.5 && o.outHeight / (sample * 2) >= h * 1.5) sample *= 2
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })?.asImageBitmap()
            }.getOrNull()
        }
    }
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    fun clamp(o: Offset, sc: Float) = Offset(
        o.x.coerceIn(-(sc - 1) * w / 2, (sc - 1) * w / 2),
        o.y.coerceIn(-(sc - 1) * h / 2, (sc - 1) * h / 2),
    )

    Box(
        Modifier.fillMaxSize()
            .pointerInput(index) {
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
            .pointerInput(index) {
                // pinch anytime; one-finger pan only while zoomed (otherwise the pager swipes)
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    do {
                        val event = awaitPointerEvent()
                        val pinch = event.changes.count { it.pressed } >= 2
                        if (pinch || scale > 1.01f) {
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
        val bmp = bitmap
        if (bmp == null) CircularProgressIndicator(color = Color(0x88FFFFFF))
        else Image(
            bmp, contentDescription = "Страница ${index + 1}", contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize().graphicsLayer {
                scaleX = scale; scaleY = scale; translationX = offset.x; translationY = offset.y
            },
        )
    }
}
