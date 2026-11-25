package io.github.galis228.materialereader.ui

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.galis228.materialereader.data.BookEntry
import io.github.galis228.materialereader.data.Library
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val coverPalette = listOf(
    Color(0xFF264653) to Color(0xFFF1FAEE), Color(0xFFE76F51) to Color(0xFF2A1A12),
    Color(0xFF8AB17D) to Color(0xFF1B2A1E), Color(0xFFE9C46A) to Color(0xFF2A2A1A),
    Color(0xFF3D4F8A) to Color(0xFFEEF0FA), Color(0xFF7A3E5C) to Color(0xFFFAEEF4),
    Color(0xFF2A9D8F) to Color(0xFF0E2724), Color(0xFFB5651D) to Color(0xFFFFF4E6),
)

@Composable
fun BookCover(book: BookEntry, modifier: Modifier = Modifier, corner: Dp = 12.dp, titleSize: Int = 13) {
    val ctx = LocalContext.current
    val bitmap by produceState<ImageBitmap?>(null, book.id, book.hasCover, book.coverRev) {
        if (book.hasCover) value = withContext(Dispatchers.IO) {
            runCatching { BitmapFactory.decodeFile(Library.get(ctx).coverFile(book.id).path)?.asImageBitmap() }.getOrNull()
        }
    }
    val shape = RoundedCornerShape(corner)
    val bmp = bitmap
    if (bmp != null) {
        Image(bmp, contentDescription = "Обложка: ${book.title}", contentScale = ContentScale.Crop, modifier = modifier.clip(shape))
    } else {
        val (bg, fg) = coverPalette[(book.title.hashCode() and 0x7fffffff) % coverPalette.size]
        Box(modifier.clip(shape).background(bg)) {
            Column(Modifier.fillMaxSize().padding(10.dp), verticalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween) {
                Text(book.title, color = fg, fontFamily = FontFamily.Serif, fontWeight = FontWeight.SemiBold,
                    fontSize = titleSize.sp, lineHeight = (titleSize + 3).sp, maxLines = 5, overflow = TextOverflow.Ellipsis)
                if (book.author.isNotEmpty()) Text(book.author, color = fg.copy(alpha = 0.8f), fontSize = (titleSize - 3).sp,
                    maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.align(Alignment.Start))
            }
        }
    }
}
