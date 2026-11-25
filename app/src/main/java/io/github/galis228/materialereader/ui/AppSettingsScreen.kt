package io.github.galis228.materialereader.ui

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import io.github.galis228.materialereader.data.Catalog
import io.github.galis228.materialereader.data.Library
import io.github.galis228.materialereader.data.Opds
import io.github.galis228.materialereader.data.Release
import io.github.galis228.materialereader.data.Updater
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppSettingsScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val library = remember { Library.get(ctx) }
    val folders by library.folderList.collectAsState()
    val scanning by library.scanning.collectAsState()
    var catalogs by remember { mutableStateOf(Opds.catalogs(ctx)) }
    var autoUpdate by remember { mutableStateOf(Updater.autoCheckEnabled(ctx)) }
    var checking by remember { mutableStateOf(false) }
    var offer by remember { mutableStateOf<Release?>(null) }
    var addCatalog by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }

    val treePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri: Uri? ->
        if (uri != null) {
            library.addFolder(uri)
            scope.launch {
                val n = library.scan()
                snackbar.showSnackbar(if (n > 0) "Найдено новых книг: $n" else "Новых книг в папке нет")
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Настройки") }, navigationIcon = {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Назад") }
            })
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { pad ->
        LazyColumn(Modifier.padding(pad).fillMaxSize()) {
            item { Section("Автопоиск книг") }
            item {
                Text(
                    "Выбери папки — например, Books, Documents или Download/Telegram. Приложение само добавит из них EPUB, FB2, TXT, CBZ и CBR при каждом запуске. Android не даёт выбрать корень «Загрузок» целиком — выбирай папку внутри.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
            items(folders, key = { it }) { f ->
                ListItem(
                    headlineContent = { Text(library.folderName(f)) },
                    leadingContent = { Icon(Icons.Rounded.Folder, null) },
                    trailingContent = { IconButton(onClick = { library.removeFolder(f) }) { Icon(Icons.Rounded.Close, "Убрать папку") } },
                )
            }
            item {
                Row(Modifier.padding(horizontal = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { treePicker.launch(null) }) { Icon(Icons.Rounded.Add, null); Text(" Добавить папку") }
                    if (folders.isNotEmpty()) TextButton(enabled = !scanning, onClick = {
                        scope.launch { val n = library.scan(); snackbar.showSnackbar(if (n > 0) "Добавлено книг: $n" else "Новых книг нет") }
                    }) { Text(if (scanning) "Сканирую…" else "Сканировать сейчас") }
                }
            }
            item { HorizontalDivider(Modifier.padding(vertical = 8.dp)) }

            item { Section("Каталоги OPDS") }
            items(catalogs, key = { it.url }) { c ->
                ListItem(
                    headlineContent = { Text(c.name) },
                    supportingContent = { Text(c.url, maxLines = 1) },
                    leadingContent = { Icon(Icons.Rounded.Public, null) },
                    trailingContent = {
                        if (catalogs.size > 1) IconButton(onClick = {
                            catalogs = catalogs - c; Opds.saveCatalogs(ctx, catalogs)
                        }) { Icon(Icons.Rounded.Close, "Удалить каталог") }
                    },
                )
            }
            item { TextButton(onClick = { addCatalog = true }, modifier = Modifier.padding(horizontal = 8.dp)) { Icon(Icons.Rounded.Add, null); Text(" Добавить каталог") } }
            item { HorizontalDivider(Modifier.padding(vertical = 8.dp)) }

            item { Section("Обновления") }
            item {
                ListItem(
                    headlineContent = { Text("Проверять обновления автоматически") },
                    supportingContent = { Text("Версия ${Updater.currentVersion} · github.com/${Updater.repo}") },
                    trailingContent = { Switch(checked = autoUpdate, onCheckedChange = { autoUpdate = it; Updater.setAutoCheck(ctx, it) }) },
                    modifier = Modifier.clickable { autoUpdate = !autoUpdate; Updater.setAutoCheck(ctx, autoUpdate) },
                )
            }
            item {
                Row(Modifier.padding(horizontal = 8.dp)) {
                    TextButton(enabled = !checking, onClick = {
                        checking = true
                        scope.launch {
                            Updater.latest()
                                .onSuccess { r ->
                                    if (Updater.isNewer(r.version, Updater.currentVersion)) offer = r
                                    else snackbar.showSnackbar("Установлена последняя версия")
                                }
                                .onFailure { snackbar.showSnackbar("Не удалось проверить: ${it.message}") }
                            checking = false
                        }
                    }) { Text(if (checking) "Проверяю…" else "Проверить сейчас") }
                    TextButton(onClick = {
                        ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/${Updater.repo}/releases")))
                    }) { Text("Все версии") }
                }
            }
        }
    }

    offer?.let { UpdateDialog(it) { offer = null } }
    if (addCatalog) AddCatalogDialog(onDismiss = { addCatalog = false }) { c ->
        catalogs = catalogs + c; Opds.saveCatalogs(ctx, catalogs); addCatalog = false
    }
}

@Composable
private fun Section(title: String) {
    Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 8.dp))
}

@Composable
private fun AddCatalogDialog(onDismiss: () -> Unit, onAdd: (Catalog) -> Unit) {
    var name by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("https://") }
    val valid = name.isNotBlank() && (url.startsWith("https://") || url.startsWith("http://")) && url.length > 10
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Новый каталог") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("Название") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(url, { url = it.trim() }, label = { Text("Адрес OPDS") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = { TextButton(enabled = valid, onClick = { onAdd(Catalog(name.trim(), url)) }) { Text("Добавить") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
    )
}
