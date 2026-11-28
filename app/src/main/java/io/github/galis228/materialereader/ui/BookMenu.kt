package io.github.galis228.materialereader.ui

import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.automirrored.rounded.Label
import androidx.compose.runtime.collectAsState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.Edit
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
    var editing by remember { mutableStateOf(false) }
    var shelving by remember { mutableStateOf(false) }
    var aiNotes by remember { mutableStateOf(false) }
    if (aiNotes) { BookAiNotesSheet(book.id, book.title) { aiNotes = false; onDismiss() }; return }
    val aiCount = io.github.galis228.materialereader.ai.AiHistory.get(LocalContext.current).all.collectAsState().value.count { it.bookId == book.id }
    if (shelving) { BookShelvesSheet(book.id) { shelving = false; onDismiss() }; return }
    if (covers) { CoverSheet(book) { covers = false; onDismiss() }; return }
    if (editing) { EditBookDialog(book) { editing = false; onDismiss() }; return }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.navigationBarsPadding().padding(bottom = 8.dp)) {
            Row(Modifier.padding(horizontal = 20.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                BookCover(book, Modifier.width(48.dp).height(70.dp), corner = 6.dp, titleSize = 8)
                Column(Modifier.padding(start = 14.dp)) {
                    Text(book.title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    if (book.author.isNotEmpty()) Text(book.author, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            // About the book: year, genres, pages and the annotation; fetched on request
            var fetching by remember { mutableStateOf(false) }
            var expanded by remember { mutableStateOf(false) }
            val facts = listOfNotNull(book.year.takeIf { it.isNotEmpty() }, book.pages.takeIf { it > 0 }?.let { "$it стр." }, book.genres.take(3).joinToString(", ").takeIf { it.isNotEmpty() })
            Column(Modifier.padding(horizontal = 20.dp, vertical = 6.dp)) {
                if (facts.isNotEmpty()) Text(facts.joinToString(" · "), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                if (book.description.isNotEmpty()) Text(book.description, style = MaterialTheme.typography.bodySmall,
                    maxLines = if (expanded) 30 else 4, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.clickable { expanded = !expanded }.padding(top = 4.dp))
                if (book.description.isEmpty() || facts.isEmpty()) androidx.compose.material3.TextButton(onClick = {
                    fetching = true
                    scope.launch { library.fetchInfo(book.id); fetching = false }
                }, enabled = !fetching) { Text(if (fetching) "Ищу описание…" else "Найти описание, год и жанр") }
            }
            val colors = ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent)
            ListItem({ Text("Читать") }, Modifier.clickable { onDismiss(); onOpen() }, leadingContent = { Icon(Icons.AutoMirrored.Rounded.MenuBook, null) }, colors = colors)
            if (book.finished) ListItem({ Text("Отметить как непрочитанную") },
                Modifier.clickable { scope.launch { library.setReadMark(book.id, 2) }; onDismiss() },
                leadingContent = { Icon(Icons.Rounded.RadioButtonUnchecked, null) }, colors = colors)
            else ListItem({ Text("Отметить как прочитанную") },
                Modifier.clickable { scope.launch { library.setReadMark(book.id, 1) }; onDismiss() },
                leadingContent = { Icon(Icons.Rounded.CheckCircle, null) }, colors = colors)
            ListItem({ Text("Полки") }, Modifier.clickable { shelving = true },
                supportingContent = { Text(if (book.shelves.isEmpty()) "Не на полке" else book.shelves.joinToString(", ")) },
                leadingContent = { Icon(Icons.AutoMirrored.Rounded.Label, null) }, colors = colors)
            if (aiCount > 0) ListItem({ Text("Результаты ИИ") }, Modifier.clickable { aiNotes = true },
                supportingContent = { Text("Сохранено: $aiCount") },
                leadingContent = { Icon(Icons.Rounded.AutoAwesome, null) }, colors = colors)
            ListItem({ Text("Название, автор, серия") }, Modifier.clickable { editing = true },
                leadingContent = { Icon(Icons.Rounded.Edit, null) }, colors = colors)
            if (book.hidden) ListItem({ Text("Вернуть из скрытых") }, Modifier.clickable { scope.launch { library.setHidden(book.id, false) }; onDismiss() },
                leadingContent = { Icon(Icons.Rounded.Visibility, null) }, colors = colors)
            else ListItem({ Text("Скрыть") }, Modifier.clickable { scope.launch { library.setHidden(book.id, true) }; onDismiss() },
                supportingContent = { Text("Книга уйдёт на полку «Скрытые»") },
                leadingContent = { Icon(Icons.Rounded.VisibilityOff, null) }, colors = colors)
            ListItem({ Text("Сменить обложку") }, Modifier.clickable { covers = true }, leadingContent = { Icon(Icons.Rounded.Image, null) }, colors = colors)
            ListItem({ Text("Удалить", color = MaterialTheme.colorScheme.error) }, Modifier.clickable { onDismiss(); onDelete() },
                leadingContent = { Icon(Icons.Rounded.Delete, null, tint = MaterialTheme.colorScheme.error) }, colors = colors)
        }
    }
}

/** Title without series marks, subtitles and brackets — what cover databases index. */
fun cleanTitle(t: String) = t.substringBefore(" / ").substringBefore(". ").replace(Regex("\\s*[\\(\\[].*?[\\)\\]]"), "")
    .replace(Regex("[«»\"]"), "").trim().ifEmpty { t }

/** The author's surname: "Лев Николаевич Толстой" or "Толстой Лев" both give a usable search term. */
fun surname(author: String): String {
    val first = author.split(',', ';').first().trim()
    val parts = first.split(' ').filter { it.length > 1 }
    return parts.maxByOrNull { if (it.endsWith(".")) 0 else it.length }.orEmpty()
}

/**
 * Cover candidates for a title/author from several free sources, best first:
 * Open Library, Wikipedia (the article's lead image is usually the cover), Wikimedia Commons, Google Books.
 * Any source that fails or rate-limits is skipped.
 */
suspend fun findCovers(title: String, author: String): List<String> = withContext(Dispatchers.IO) {
    fun enc(s: String) = URLEncoder.encode(s, "UTF-8")
    val t = cleanTitle(title)
    val a = surname(author)
    fun json(url: String) = JSONObject(String(Opds.get(url), Charsets.UTF_8))
    coroutineScope {
        val openLibrary = async {
            runCatching {
                val out = LinkedHashSet<String>()
                for (q in listOf("title=${enc(t)}" + if (a.isNotEmpty()) "&author=${enc(a)}" else "", "title=${enc(t)}", "q=${enc("$t $a".trim())}")) {
                    val docs = json("https://openlibrary.org/search.json?$q&limit=24&fields=cover_i").optJSONArray("docs") ?: continue
                    (0 until docs.length()).mapNotNull { docs.getJSONObject(it).optLong("cover_i").takeIf { id -> id > 0 } }
                        .forEach { out += "https://covers.openlibrary.org/b/id/$it-L.jpg" }
                    if (out.size >= 6) break
                }
                out.toList()
            }.getOrDefault(emptyList())
        }
        val wiki = async {
            runCatching {
                val out = ArrayList<String>()
                for (lang in listOf("ru", "en")) {
                    val pages = json("https://$lang.wikipedia.org/w/api.php?action=query&format=json&generator=search&gsrlimit=6" +
                        "&prop=pageimages&piprop=thumbnail&pithumbsize=600&gsrsearch=${enc("$t $a".trim())}")
                        .optJSONObject("query")?.optJSONObject("pages") ?: continue
                    pages.keys().asSequence().map { pages.getJSONObject(it) }.sortedBy { it.optInt("index") }
                        .mapNotNull { it.optJSONObject("thumbnail")?.optString("source")?.takeIf { s -> s.isNotEmpty() } }
                        .forEach { out += it }
                }
                out
            }.getOrDefault(emptyList())
        }
        val commons = async {
            runCatching {
                val pages = json("https://commons.wikimedia.org/w/api.php?action=query&format=json&generator=search&gsrnamespace=6&gsrlimit=8" +
                    "&prop=imageinfo&iiprop=url&iiurlwidth=600&gsrsearch=${enc("$t $a обложка".trim())}")
                    .optJSONObject("query")?.optJSONObject("pages") ?: return@runCatching emptyList<String>()
                pages.keys().asSequence().map { pages.getJSONObject(it) }
                    .mapNotNull { it.optJSONArray("imageinfo")?.optJSONObject(0)?.optString("thumburl")?.takeIf { s -> s.isNotEmpty() } }
                    .filterNot { it.endsWith(".pdf.jpg") || it.contains(".djvu") }
                    .toList()
            }.getOrDefault(emptyList())
        }
        val google = async {
            runCatching {
                val q = "intitle:${enc(t)}" + if (a.isNotEmpty()) "+inauthor:${enc(a)}" else ""
                val items = json("https://www.googleapis.com/books/v1/volumes?q=$q&maxResults=20&printType=books").optJSONArray("items")
                    ?: return@runCatching emptyList<String>()
                (0 until items.length()).mapNotNull { i ->
                    val links = items.getJSONObject(i).optJSONObject("volumeInfo")?.optJSONObject("imageLinks") ?: return@mapNotNull null
                    links.optString("thumbnail").ifEmpty { null }?.replace("http://", "https://")?.replace("&edge=curl", "")?.replace("zoom=1", "zoom=0")
                }
            }.getOrDefault(emptyList()) // the shared free quota is often used up — then this source just stays empty
        }
        listOf(openLibrary, wiki, commons, google).awaitAll().flatten().distinct()
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

/** One likely cover for a catalog row: Open Library first, then the Wikipedia article's image. Null if none. */
suspend fun quickCover(title: String, author: String): String? = withContext(Dispatchers.IO) {
    fun enc(s: String) = URLEncoder.encode(s, "UTF-8")
    val t = cleanTitle(title)
    // Wikisource titles carry the author in brackets: «Мцыри (Лермонтов)»
    val a = surname(author).ifEmpty { Regex("\\(([^)]+)\\)").find(title)?.groupValues?.get(1)?.let(::surname).orEmpty() }
    runCatching {
        val docs = JSONObject(String(Opds.get("https://openlibrary.org/search.json?title=${enc(t)}" +
            (if (a.isNotEmpty()) "&author=${enc(a)}" else "") + "&limit=5&fields=cover_i"), Charsets.UTF_8)).optJSONArray("docs")
        (0 until (docs?.length() ?: 0)).firstNotNullOfOrNull { docs!!.getJSONObject(it).optLong("cover_i").takeIf { id -> id > 0 } }
            ?.let { "https://covers.openlibrary.org/b/id/$it-M.jpg" }
    }.getOrNull() ?: runCatching {
        val lang = if (t.any { it in 'а'..'я' || it in 'А'..'Я' || it in "іїєґІЇЄҐ" }) (if (t.any { it in "іїєґІЇЄҐ" }) "uk" else "ru") else "en"
        val pages = JSONObject(String(Opds.get("https://$lang.wikipedia.org/w/api.php?action=query&format=json&generator=search&gsrlimit=1" +
            "&prop=pageimages&piprop=thumbnail&pithumbsize=300&gsrsearch=${enc("$t $a".trim())}"), Charsets.UTF_8))
            .optJSONObject("query")?.optJSONObject("pages")
        pages?.keys()?.asSequence()?.mapNotNull { pages.getJSONObject(it).optJSONObject("thumbnail")?.optString("source") }?.firstOrNull()
    }.getOrNull()
}

/** A plain tile with the title, like the covers drawn on the shelf for books without one. */
@Composable
fun TitleTile(title: String, author: String, modifier: Modifier) {
    val palette = listOf(0xFF264653, 0xFF3D4F8A, 0xFF7A3E5C, 0xFF2A9D8F, 0xFFB5651D, 0xFF5B6B3A)
    val bg = androidx.compose.ui.graphics.Color(palette[(title.hashCode() and 0x7fffffff) % palette.size].toInt())
    Box(modifier.clip(RoundedCornerShape(8.dp)).background(bg).padding(6.dp)) {
        Text(title, color = androidx.compose.ui.graphics.Color.White, style = MaterialTheme.typography.labelSmall,
            fontFamily = androidx.compose.ui.text.font.FontFamily.Serif, maxLines = 5, overflow = TextOverflow.Ellipsis)
    }
}

/** Fix a book's title and author and put it in a series. */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun EditBookDialog(book: BookEntry, onDone: () -> Unit) {
    val scope = rememberCoroutineScope()
    val library = Library.get(LocalContext.current)
    var title by remember { mutableStateOf(book.title) }
    var author by remember { mutableStateOf(book.author) }
    var series by remember { mutableStateOf(book.series) }
    var index by remember { mutableStateOf(if (book.seriesIndex > 0f) book.seriesIndex.toString().removeSuffix(".0") else "") }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDone,
        title = { Text("О книге") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(title, { title = it }, label = { Text("Название") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(author, { author = it }, label = { Text("Автор") }, modifier = Modifier.fillMaxWidth())
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(series, { series = it }, label = { Text("Серия") }, singleLine = true, modifier = Modifier.weight(1f))
                    OutlinedTextField(index, { index = it.filter { c -> c.isDigit() || c == '.' || c == ',' }.take(5) }, label = { Text("№") }, singleLine = true,
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal),
                        modifier = Modifier.width(76.dp))
                }
            }
        },
        confirmButton = {
            androidx.compose.material3.TextButton(onClick = {
                scope.launch {
                    library.setMeta(book.id, title, author, series, index.replace(',', '.').toFloatOrNull() ?: 0f)
                    onDone()
                }
            }) { Text("Сохранить") }
        },
        dismissButton = { androidx.compose.material3.TextButton(onClick = onDone) { Text("Отмена") } },
    )
}
