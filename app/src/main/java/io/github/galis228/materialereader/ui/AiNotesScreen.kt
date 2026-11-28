package io.github.galis228.materialereader.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.galis228.materialereader.ai.AiHistory

/** Every saved AI result across the library, filterable by book. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiNotesScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val all by remember { AiHistory.get(ctx) }.all.collectAsState()
    var book by rememberSaveable { mutableStateOf<String?>(null) }
    val books = all.distinctBy { it.bookId }.map { it.bookId to it.bookTitle }
    Scaffold(topBar = {
        TopAppBar(title = { Text("Результаты ИИ") }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Назад") } })
    }) { pad ->
        Column(Modifier.padding(pad).fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (books.size > 1) LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item { FilterChip(selected = book == null, onClick = { book = null }, label = { Text("Все") }) }
                items(books) { (id, title) ->
                    FilterChip(selected = book == id, onClick = { book = if (book == id) null else id },
                        label = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) })
                }
            }
            AiNotesList(all.filter { book == null || it.bookId == book }, Modifier.weight(1f), showBook = book == null)
        }
    }
}

/** The same list for one book, from its menu in the library. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookAiNotesSheet(bookId: String, title: String, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val all by remember { AiHistory.get(ctx) }.all.collectAsState()
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.padding(horizontal = 16.dp).navigationBarsPadding().padding(bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Результаты ИИ", style = MaterialTheme.typography.titleLarge)
            Text(title, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            AiNotesList(all.filter { it.bookId == bookId })
        }
    }
}
