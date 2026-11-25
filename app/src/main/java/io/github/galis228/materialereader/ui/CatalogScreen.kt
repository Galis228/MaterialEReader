package io.github.galis228.materialereader.ui

import android.graphics.BitmapFactory
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.galis228.materialereader.data.Acquisition
import io.github.galis228.materialereader.data.Library
import io.github.galis228.materialereader.data.Opds
import io.github.galis228.materialereader.data.OpdsEntry
import io.github.galis228.materialereader.data.OpdsFeed
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private object CoverCache {
    val map = object : LinkedHashMap<String, ImageBitmap>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, ImageBitmap>?) = size > 120
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CatalogScreen(onBack: () -> Unit, onOpenBook: (String) -> Unit) {
    val ctx = LocalContext.current
    val library = remember { Library.get(ctx) }
    val catalogs = remember { Opds.catalogs(ctx) }
    var catalogIndex by remember { mutableStateOf(0) }
    val stack = remember { mutableStateListOf<String>() } // feed urls, last = current
    var feed by remember { mutableStateOf<OpdsFeed?>(null) }
    val entries = remember { mutableStateListOf<OpdsEntry>() }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var query by remember { mutableStateOf("") }
    var downloading by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val catalog = catalogs.getOrNull(catalogIndex)

    fun open(url: String, push: Boolean = true, append: Boolean = false) {
        scope.launch {
            loading = true; error = null
            runCatching { Opds.load(url) }
                .onSuccess { f ->
                    if (push) stack += url
                    feed = f
                    if (!append) entries.clear()
                    entries += f.entries
                }
                .onFailure { error = it.message ?: "Каталог недоступен" }
            loading = false
        }
    }
    fun search() {
        val q = query.trim(); if (q.isEmpty()) return
        val template = catalog?.searchTemplate ?: feed?.searchTemplate
        if (template == null) { error = "Этот каталог не поддерживает поиск"; return }
        open(Opds.searchUrl(template, q))
    }

    LaunchedEffect(catalogIndex) { stack.clear(); catalog?.let { open(it.url) } }
    BackHandler(enabled = stack.size > 1) { stack.removeAt(stack.lastIndex); open(stack.last(), push = false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Каталоги") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Назад") } },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            if (catalogs.size > 1) LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(catalogs.size) { i -> FilterChip(selected = i == catalogIndex, onClick = { catalogIndex = i }, label = { Text(catalogs[i].name) }) }
            }
            OutlinedTextField(
                value = query, onValueChange = { query = it },
                placeholder = { Text("Название или автор") },
                leadingIcon = { Icon(Icons.Rounded.Search, null) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { search() }),
                shape = RoundedCornerShape(28.dp),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            )
            if (catalog?.url?.startsWith("wikisource:ru") == true || catalog?.url?.contains("gutenberg") == true) LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(Opds.classics.size) { i ->
                    val name = Opds.classics[i]
                    androidx.compose.material3.SuggestionChip(onClick = { query = name; search() }, label = { Text(name) })
                }
            }
            if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
            error?.let { msg ->
                Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(msg, color = MaterialTheme.colorScheme.error)
                    TextButton(onClick = { (stack.lastOrNull() ?: catalog?.url)?.let { open(it, push = stack.isEmpty()) } }) { Text("Повторить") }
                }
            }
            if (stack.size > 1) Text(
                feed?.title.orEmpty(), style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp), maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            if (!loading && error == null && entries.isEmpty() && feed != null) Text(
                if (stack.size <= 1) "Введи название или автора — или выбери классика выше. Книги бесплатные: срок авторских прав на них истёк."
                else "Ничего не нашлось",
                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(24.dp),
            )
            LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
                items(entries, key = { it.id + it.title }) { e ->
                    EntryRow(
                        e, downloading == e.id,
                        onNavigate = { e.navUrl?.let { open(it) } },
                        onDownload = { a -> download(e, a, scope, library, snackbar, onOpenBook) { downloading = it } },
                    )
                }
                feed?.next?.let { next ->
                    item {
                        Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                            OutlinedButton(onClick = { open(next, push = false, append = true) }, enabled = !loading) { Text("Показать ещё") }
                        }
                    }
                }
            }
        }
    }
}

private fun download(
    e: OpdsEntry, a: Acquisition, scope: kotlinx.coroutines.CoroutineScope, library: Library,
    snackbar: SnackbarHostState, onOpenBook: (String) -> Unit, setBusy: (String?) -> Unit,
) {
    scope.launch {
        setBusy(e.id)
        val result = runCatching {
            val (bytes, name) = Opds.download(a, e.title)
            library.importBytes(bytes, name).getOrThrow()
        }
        setBusy(null)
        result.onSuccess { book ->
            if (snackbar.showSnackbar("«${book.title}» на полке", actionLabel = "Читать") == SnackbarResult.ActionPerformed) onOpenBook(book.id)
        }.onFailure { snackbar.showSnackbar("Не удалось скачать: ${it.message}") }
    }
}

@Composable
private fun EntryRow(e: OpdsEntry, busy: Boolean, onNavigate: () -> Unit, onDownload: (Acquisition) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(enabled = e.navUrl != null, onClick = onNavigate).padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        RemoteCover(e.coverUrl, Modifier.width(56.dp).height(82.dp))
        Column(Modifier.weight(1f)) {
            Text(e.title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (e.author.isNotEmpty()) Text(e.author, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            if (e.summary.isNotEmpty() && e.acquisitions.isNotEmpty()) Text(e.summary, style = MaterialTheme.typography.bodySmall, maxLines = 2,
                overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (e.acquisitions.isNotEmpty()) Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (busy) CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 3.dp)
                else {
                    val best = e.acquisitions.first()
                    Button(onClick = { onDownload(best) }, contentPadding = PaddingValues(horizontal = 14.dp)) { Text("Скачать ${best.label}") }
                    e.acquisitions.drop(1).distinctBy { it.label }.take(2).forEach { a ->
                        OutlinedButton(onClick = { onDownload(a) }, contentPadding = PaddingValues(horizontal = 12.dp)) { Text(a.label) }
                    }
                }
            }
        }
        if (e.navUrl != null) Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, Modifier.align(Alignment.CenterVertically))
    }
}

@Composable
private fun RemoteCover(url: String?, modifier: Modifier) {
    val img by produceState(url?.let { CoverCache.map[it] }, url) {
        if (url == null || value != null) return@produceState
        value = withContext(Dispatchers.IO) {
            runCatching {
                val bytes = Opds.get(url, maxBytes = 3L * 1024 * 1024)
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = 2 })?.asImageBitmap()
            }.getOrNull()
        }?.also { CoverCache.map[url] = it }
    }
    Box(modifier.clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh)) {
        img?.let { Image(it, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) }
    }
}
