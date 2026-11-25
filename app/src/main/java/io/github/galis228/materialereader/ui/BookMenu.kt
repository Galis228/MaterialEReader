package io.github.galis228.materialereader.ui

import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.ImageSearch
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.galis228.materialereader.data.BookEntry
import io.github.galis228.materialereader.data.Library
import io.github.galis228.materialereader.data.Opds
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.URLEncoder

/** The book the person went to Google Images for: a picture shared back to the app becomes its cover. */
object PendingCover {
    @Volatile var bookId: String? = null
    @Volatile var since: Long = 0L
    fun take(): String? = bookId?.takeIf { System.currentTimeMillis() - since < 30 * 60_000L }.also { bookId = null }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookMenuSheet(book: BookEntry, onOpen: () -> Unit, onDelete: () -> Unit, onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    val library = Library.get(LocalContext.current)
    var covers by remember { mutableStateOf(false) }
    if (covers) { CoverSheet(book) { covers = false; onDismiss() }; return }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.navigationBarsPadding().padding(bottom = 8.dp)) {
            Row(Modifier.padding(horizontal = 20.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                BookCover(book, Modifier.width(48.dp).height(70.dp), corner = 6.dp, titleSize = 8)
                Column(Modifier.padding(start = 14.dp)) {
                    Text(book.title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    if (book.author.isNotEmpty()) Text(book.author, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            val colors = ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent)
            ListItem({ Text("Читать") }, Modifier.clickable { onDismiss(); onOpen() }, leadingContent = { Icon(Icons.AutoMirrored.Rounded.MenuBook, null) }, colors = colors)
            if (book.finished) ListItem({ Text("Отметить как непрочитанную") },
                Modifier.clickable { scope.launch { library.setReadMark(book.id, 2) }; onDismiss() },
                leadingContent = { Icon(Icons.Rounded.RadioButtonUnchecked, null) }, colors = colors)
            else ListItem({ Text("Отметить как прочитанную") },
                Modifier.clickable { scope.launch { library.setReadMark(book.id, 1) }; onDismiss() },
                leadingContent = { Icon(Icons.Rounded.CheckCircle, null) }, colors = colors)
            ListItem({ Text("Сменить обложку") }, Modifier.clickable { covers = true }, leadingContent = { Icon(Icons.Rounded.Image, null) }, colors = colors)
            ListItem({ Text("Удалить", color = MaterialTheme.colorScheme.error) }, Modifier.clickable { onDismiss(); onDelete() },
                leadingContent = { Icon(Icons.Rounded.Delete, null, tint = MaterialTheme.colorScheme.error) }, colors = colors)
        }
    }
}

/** Cover candidates from Google Books and Open Library for a title/author. */
suspend fun findCovers(title: String, author: String): List<String> = withContext(Dispatchers.IO) {
    fun enc(s: String) = URLEncoder.encode(s, "UTF-8")
    coroutineScope {
        val google = async {
            runCatching {
                val q = "intitle:${enc(title)}" + if (author.isNotBlank()) "+inauthor:${enc(author.split(',').first().trim())}" else ""
                val o = JSONObject(String(Opds.get("https://www.googleapis.com/books/v1/volumes?q=$q&maxResults=20&printType=books")))
                val items = o.optJSONArray("items") ?: return@runCatching emptyList<String>()
                (0 until items.length()).mapNotNull { i ->
                    val links = items.getJSONObject(i).optJSONObject("volumeInfo")?.optJSONObject("imageLinks") ?: return@mapNotNull null
                    (links.optString("extraLarge").ifEmpty { null } ?: links.optString("large").ifEmpty { null }
                        ?: links.optString("thumbnail").ifEmpty { null })
                        ?.replace("http://", "https://")?.replace("&edge=curl", "")
                }
            }.getOrDefault(emptyList())
        }
        val openLibrary = async {
            runCatching {
                val q = "title=${enc(title)}" + if (author.isNotBlank()) "&author=${enc(author.split(',').first().trim())}" else ""
                val o = JSONObject(String(Opds.get("https://openlibrary.org/search.json?$q&limit=20&fields=cover_i")))
                val docs = o.optJSONArray("docs") ?: return@runCatching emptyList<String>()
                (0 until docs.length()).mapNotNull { i -> docs.getJSONObject(i).optLong("cover_i").takeIf { it > 0 } }
                    .distinct().map { "https://covers.openlibrary.org/b/id/$it-L.jpg" }
            }.getOrDefault(emptyList())
        }
        listOf(google, openLibrary).awaitAll().flatten().distinct()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CoverSheet(book: BookEntry, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val library = Library.get(ctx)
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    val found by produceState<List<String>?>(null, book.id) { value = findCovers(book.title, book.author) }
    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri: Uri? ->
        if (uri != null) scope.launch {
            busy = true
            val bytes = withContext(Dispatchers.IO) { runCatching { ctx.contentResolver.openInputStream(uri)?.use { it.readBytes() } }.getOrNull() }
            if (bytes != null) library.setCover(book.id, bytes)
            busy = false; onDismiss()
        }
    }
    val maxH = (LocalConfiguration.current.screenHeightDp * 0.75f).dp

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.heightIn(max = maxH).navigationBarsPadding().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Обложка: ${book.title}", style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = {
                    PendingCover.bookId = book.id; PendingCover.since = System.currentTimeMillis()
                    val q = Uri.encode("${book.title} ${book.author} обложка книги")
                    ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?tbm=isch&q=$q")))
                }) { Icon(Icons.Rounded.ImageSearch, null); Text(" Google Картинки") }
                OutlinedButton(onClick = { gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }) {
                    Icon(Icons.Rounded.PhotoLibrary, null); Text(" Галерея")
                }
            }
            Text("В Google Картинках открой нужную картинку → «Поделиться» → Material eReader, и она станет обложкой этой книги.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedButton(onClick = { scope.launch { busy = true; library.resetCover(book.id); busy = false; onDismiss() } }) {
                Icon(Icons.Rounded.Restore, null); Text(" Вернуть обложку из файла")
            }
            Text("Найдено в интернете", style = MaterialTheme.typography.titleSmall)
            val list = found
            when {
                busy || list == null -> Box(Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                list.isEmpty() -> Text("Ничего не нашлось по названию. Попробуй Google Картинки или галерею.",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                else -> LazyVerticalGrid(GridCells.Adaptive(96.dp), horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(bottom = 12.dp)) {
                    items(list) { url ->
                        RemoteImage(url, Modifier.fillMaxWidth().aspectRatio(0.68f).clickable {
                            scope.launch {
                                busy = true
                                val bytes = withContext(Dispatchers.IO) { runCatching { Opds.get(url, maxBytes = 8L * 1024 * 1024) }.getOrNull() }
                                if (bytes != null) library.setCover(book.id, bytes)
                                busy = false; onDismiss()
                            }
                        })
                    }
                }
            }
        }
    }
}

@Composable
fun RemoteImage(url: String, modifier: Modifier) {
    val img by produceState<ImageBitmap?>(null, url) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                val bytes = Opds.get(url, maxBytes = 8L * 1024 * 1024)
                val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, o)
                var s = 1; while (o.outWidth / (s * 2) >= 300) s *= 2
                // 1×1 placeholder images some services return mean "no cover"
                if (o.outWidth < 20) null else BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = s })?.asImageBitmap()
            }.getOrNull()
        }
    }
    Box(modifier.clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh)) {
        img?.let { Image(it, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) }
    }
}

/** A picture shared into the app: becomes the cover of the book the person was searching for, or of one they pick. */
@Composable
fun SharedCoverDialog(image: Uri, targetId: String?, onDone: () -> Unit) {
    val ctx = LocalContext.current
    val library = Library.get(ctx)
    val books = library.books.value.sortedByDescending { maxOf(it.lastOpenedAt, it.addedAt) }
    val scope = rememberCoroutineScope()
    fun apply(id: String) = scope.launch {
        val bytes = withContext(Dispatchers.IO) { runCatching { ctx.contentResolver.openInputStream(image)?.use { it.readBytes() } }.getOrNull() }
        val ok = bytes != null && library.setCover(id, bytes)
        android.widget.Toast.makeText(ctx, if (ok) "Обложка обновлена" else "Не получилось открыть картинку", android.widget.Toast.LENGTH_SHORT).show()
        onDone()
    }
    if (targetId != null) {
        androidx.compose.runtime.LaunchedEffect(image) { apply(targetId) }
        return
    }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDone,
        title = { Text("Сделать обложкой для…") },
        text = {
            androidx.compose.foundation.lazy.LazyColumn(Modifier.heightIn(max = 360.dp)) {
                items(books.size) { i ->
                    val b = books[i]
                    Text(b.title, Modifier.fillMaxWidth().clickable { apply(b.id) }.padding(vertical = 12.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        },
        confirmButton = { androidx.compose.material3.TextButton(onClick = onDone) { Text("Отмена") } },
    )
}
