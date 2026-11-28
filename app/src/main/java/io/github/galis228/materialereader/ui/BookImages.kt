package io.github.galis228.materialereader.ui

import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import io.github.galis228.materialereader.ai.Rich
import io.github.galis228.materialereader.parse.Block
import io.github.galis228.materialereader.parse.ParsedBook

/** Decoded illustrations, shared by pagination (sizes) and drawing (bitmaps). */
object BookImages {
    private val sizes = HashMap<String, IntSize?>()
    private val bitmaps = object : LruCache<String, ImageBitmap>(48 * 1024 * 1024) {
        override fun sizeOf(key: String, value: ImageBitmap) = value.width * value.height * 4
    }
    private fun key(book: ParsedBook, src: String) = "${System.identityHashCode(book.images)}|$src"

    @Synchronized fun size(book: ParsedBook, src: String): IntSize? = sizes.getOrPut(key(book, src)) {
        val data = book.images[src] ?: return@getOrPut null
        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(data, 0, data.size, o)
        if (o.outWidth > 0 && o.outHeight > 0) IntSize(o.outWidth, o.outHeight) else null
    }

    /** A bitmap at least [targetW] px wide (or the original if smaller), sampled down to save memory. */
    fun bitmap(book: ParsedBook, src: String, targetW: Int): ImageBitmap? {
        val k = key(book, src) + "@" + targetW
        bitmaps.get(k)?.let { return it }
        val data = book.images[src] ?: return null
        val full = size(book, src) ?: return null
        var sample = 1
        while (full.width / (sample * 2) >= targetW) sample *= 2
        val bmp = runCatching { BitmapFactory.decodeByteArray(data, 0, data.size, BitmapFactory.Options().apply { inSampleSize = sample }) }.getOrNull() ?: return null
        return bmp.asImageBitmap().also { bitmaps.put(k, it) }
    }
}

/** Full-screen picture with pinch zoom and pan; tap closes. */
@Composable
fun ImageViewer(book: ParsedBook, src: String, caption: String, onDismiss: () -> Unit) {
    val bmp = remember(src) { BookImages.bitmap(book, src, 2000) }
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(
            Modifier.fillMaxSize().background(Color.Black)
                .pointerInput(Unit) { detectTransformGestures { _, pan, zoom, _ -> scale = (scale * zoom).coerceIn(1f, 6f); offset = if (scale == 1f) Offset.Zero else offset + pan } }
                .pointerInput(Unit) { detectTapGestures(onDoubleTap = { scale = if (scale > 1f) 1f else 2.5f; offset = Offset.Zero }) { onDismiss() } },
            contentAlignment = Alignment.Center,
        ) {
            if (bmp != null) Image(bmp, caption, Modifier.fillMaxSize().graphicsLayer { scaleX = scale; scaleY = scale; translationX = offset.x; translationY = offset.y },
                contentScale = ContentScale.Fit)
            IconButton(onClick = onDismiss, modifier = Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(8.dp)) {
                Icon(Icons.Rounded.Close, "Закрыть", tint = Color.White)
            }
            if (caption.isNotBlank()) Text(caption, color = Color.White.copy(alpha = 0.8f),
                modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(16.dp))
        }
    }
}

/** A table from the book as a real grid that scrolls sideways. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TableViewer(text: String, onDismiss: () -> Unit) {
    val rows = remember(text) { text.lines().map { it.split(Block.TABLE_SEP).map(String::trim) } }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.padding(horizontal = 16.dp).navigationBarsPadding().padding(bottom = 16.dp)) {
            Text("Таблица", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 8.dp))
            RichAnswer(Rich.table(rows.first(), rows.drop(1)))
        }
    }
}
