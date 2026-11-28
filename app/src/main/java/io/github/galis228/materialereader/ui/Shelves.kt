package io.github.galis228.materialereader.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Label
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.galis228.materialereader.data.BookEntry
import io.github.galis228.materialereader.data.Library
import kotlinx.coroutines.launch

/**
 * Put one book on shelves: tick a shelf and it's done, no save button. A new shelf is typed right at the top
 * and the book lands on it at once.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookShelvesSheet(bookId: String, onDismiss: () -> Unit) {
    val library = Library.get(LocalContext.current)
    val scope = rememberCoroutineScope()
    val shelves by library.shelfList.collectAsState()
    val books by library.books.collectAsState()
    val book = books.firstOrNull { it.id == bookId } ?: return
    var name by remember { mutableStateOf("") }
    fun add() {
        val n = library.createShelf(name) ?: return
        name = ""
        if (n !in book.shelves) scope.launch { library.toggleShelf(book.id, n) }
    }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.navigationBarsPadding().padding(bottom = 12.dp)) {
            Text("Полки", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 24.dp))
            Text(book.title, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(horizontal = 24.dp))
            NewShelfField(name, { name = it }, ::add, Modifier.padding(horizontal = 16.dp, vertical = 12.dp))
            if (shelves.isEmpty()) Text("Полок пока нет — назови первую, например «Классика» или «На отпуск».",
                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp))
            LazyColumn(Modifier.heightIn(max = 420.dp)) {
                items(shelves, key = { it }) { sh ->
                    val on = sh in book.shelves
                    Row(
                        Modifier.fillMaxWidth().clickable { scope.launch { library.toggleShelf(book.id, sh) } }.padding(horizontal = 12.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(checked = on, onCheckedChange = { scope.launch { library.toggleShelf(book.id, sh) } })
                        Text(sh, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                        Text("${books.count { sh in it.shelves }}", color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(end = 4.dp))
                        ShelfMenuButton(sh)
                    }
                }
            }
        }
    }
}

/** All shelves in one place: create, rename, reorder, delete. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShelfManagerSheet(onDismiss: () -> Unit) {
    val library = Library.get(LocalContext.current)
    val scope = rememberCoroutineScope()
    val shelves by library.shelfList.collectAsState()
    val books by library.books.collectAsState()
    var name by remember { mutableStateOf("") }
    var editing by remember { mutableStateOf<String?>(null) }
    var editText by remember { mutableStateOf("") }
    var toDelete by remember { mutableStateOf<String?>(null) }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.navigationBarsPadding().padding(bottom = 12.dp)) {
            Text("Мои полки", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 24.dp))
            Text("Книгу на полку — долгое нажатие на книгу → «Полки»", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 24.dp))
            NewShelfField(name, { name = it }, { library.createShelf(name); name = "" }, Modifier.padding(horizontal = 16.dp, vertical = 12.dp))
            LazyColumn(Modifier.heightIn(max = 460.dp)) {
                items(shelves, key = { it }) { sh ->
                    Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.AutoMirrored.Rounded.Label, null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(12.dp))
                        if (editing == sh) {
                            val focus = remember { FocusRequester() }
                            LaunchedEffect(Unit) { focus.requestFocus() }
                            OutlinedTextField(editText, { editText = it }, singleLine = true, modifier = Modifier.weight(1f).focusRequester(focus),
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done, capitalization = KeyboardCapitalization.Sentences),
                                keyboardActions = KeyboardActions(onDone = { scope.launch { library.renameShelf(sh, editText) }; editing = null }))
                            IconButton(onClick = { scope.launch { library.renameShelf(sh, editText) }; editing = null }) { Icon(Icons.Rounded.Check, "Готово") }
                        } else {
                            Column(Modifier.weight(1f).clickable { editing = sh; editText = sh }.padding(vertical = 10.dp)) {
                                Text(sh, style = MaterialTheme.typography.bodyLarge)
                                val n = books.count { sh in it.shelves }
                                Text(booksWord(n), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            IconButton(onClick = { library.moveShelf(sh, -1) }, Modifier.size(40.dp)) { Icon(Icons.Rounded.KeyboardArrowUp, "Выше") }
                            IconButton(onClick = { library.moveShelf(sh, 1) }, Modifier.size(40.dp)) { Icon(Icons.Rounded.KeyboardArrowDown, "Ниже") }
                            IconButton(onClick = { editing = sh; editText = sh }, Modifier.size(40.dp)) { Icon(Icons.Rounded.Edit, "Переименовать") }
                            IconButton(onClick = { toDelete = sh }, Modifier.size(40.dp)) { Icon(Icons.Rounded.Delete, "Удалить", tint = MaterialTheme.colorScheme.error) }
                        }
                    }
                }
            }
        }
    }
    toDelete?.let { sh ->
        AlertDialog(
            onDismissRequest = { toDelete = null },
            title = { Text("Удалить полку «$sh»?") },
            text = { Text("Книги останутся в библиотеке, уберётся только полка.") },
            confirmButton = { TextButton(onClick = { scope.launch { library.deleteShelf(sh) }; toDelete = null }) { Text("Удалить") } },
            dismissButton = { TextButton(onClick = { toDelete = null }) { Text("Отмена") } },
        )
    }
}

/** Quick dialog for a new shelf from the library's chip row. */
@Composable
fun NewShelfDialog(onCreated: (String) -> Unit, onDismiss: () -> Unit) {
    val library = Library.get(LocalContext.current)
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Новая полка") },
        text = { NewShelfField(name, { name = it }, { library.createShelf(name)?.let(onCreated) }, Modifier, autoFocus = true, withButton = false) },
        confirmButton = { TextButton(onClick = { library.createShelf(name)?.let(onCreated) }, enabled = name.isNotBlank()) { Text("Создать") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
    )
}

@Composable
private fun NewShelfField(value: String, onChange: (String) -> Unit, onAdd: () -> Unit, modifier: Modifier, autoFocus: Boolean = false, withButton: Boolean = true) {
    val focus = remember { FocusRequester() }
    if (autoFocus) LaunchedEffect(Unit) { focus.requestFocus() }
    OutlinedTextField(
        value, onChange, singleLine = true, placeholder = { Text("Новая полка") },
        leadingIcon = { Icon(Icons.Rounded.Add, null) },
        trailingIcon = if (withButton) ({ TextButton(onClick = onAdd, enabled = value.isNotBlank()) { Text("Создать") } }) else null,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done, capitalization = KeyboardCapitalization.Sentences),
        keyboardActions = KeyboardActions(onDone = { if (value.isNotBlank()) onAdd() }),
        modifier = modifier.fillMaxWidth().focusRequester(focus),
    )
}

fun booksWord(n: Int): String {
    val m10 = n % 10; val m100 = n % 100
    val w = when { m10 == 1 && m100 != 11 -> "книга"; m10 in 2..4 && m100 !in 12..14 -> "книги"; else -> "книг" }
    return "$n $w"
}

@Suppress("unused") private fun BookEntry.onShelf(s: String) = s in shelves

/** ⋮ next to a shelf: rename or delete it. */
@Composable
fun ShelfMenuButton(shelf: String, onDeleted: () -> Unit = {}, onRenamed: (String) -> Unit = {}) {
    val library = Library.get(LocalContext.current)
    val scope = rememberCoroutineScope()
    var menu by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }
    androidx.compose.foundation.layout.Box {
        IconButton(onClick = { menu = true }) { Icon(Icons.Rounded.MoreVert, "Действия с полкой") }
        androidx.compose.material3.DropdownMenu(menu, { menu = false }) {
            androidx.compose.material3.DropdownMenuItem({ Text("Переименовать") }, leadingIcon = { Icon(Icons.Rounded.Edit, null) },
                onClick = { menu = false; renaming = true })
            androidx.compose.material3.DropdownMenuItem({ Text("Удалить полку") }, leadingIcon = { Icon(Icons.Rounded.Delete, null) },
                onClick = { menu = false; deleting = true })
        }
    }
    if (renaming) {
        var name by remember { mutableStateOf(shelf) }
        AlertDialog(
            onDismissRequest = { renaming = false },
            title = { Text("Переименовать полку") },
            text = { NewShelfField(name, { name = it }, {}, Modifier, autoFocus = true, withButton = false) },
            confirmButton = { TextButton(enabled = name.isNotBlank(), onClick = {
                val n = name.trim(); renaming = false
                scope.launch { library.renameShelf(shelf, n); onRenamed(n) }
            }) { Text("Сохранить") } },
            dismissButton = { TextButton(onClick = { renaming = false }) { Text("Отмена") } },
        )
    }
    if (deleting) AlertDialog(
        onDismissRequest = { deleting = false },
        title = { Text("Удалить полку «$shelf»?") },
        text = { Text("Книги останутся в библиотеке, уберётся только полка.") },
        confirmButton = { TextButton(onClick = { deleting = false; scope.launch { library.deleteShelf(shelf); onDeleted() } }) { Text("Удалить") } },
        dismissButton = { TextButton(onClick = { deleting = false }) { Text("Отмена") } },
    )
}
