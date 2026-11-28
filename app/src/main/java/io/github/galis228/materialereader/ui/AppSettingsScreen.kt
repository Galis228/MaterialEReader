package io.github.galis228.materialereader.ui

import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Backup
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.rounded.LocalLibrary
import androidx.compose.material.icons.rounded.Palette
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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.rounded.FolderShared
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import io.github.galis228.materialereader.ai.Ai
import io.github.galis228.materialereader.ai.ApiEngine
import io.github.galis228.materialereader.ai.ApiProvider
import io.github.galis228.materialereader.ai.visibleAnswer
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.layout.size
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import io.github.galis228.materialereader.ai.EngineChoice
import io.github.galis228.materialereader.ai.SharedModels
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

    var busy by remember { mutableStateOf(false) }
    var confirmRestore by remember { mutableStateOf<Uri?>(null) }
    val backupTo = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri: Uri? ->
        if (uri != null) scope.launch {
            busy = true
            val r = runCatching { io.github.galis228.materialereader.data.Backup.export(ctx, uri) }
            busy = false
            snackbar.showSnackbar(r.fold({ "Копия сохранена: книг $it" }, { "Не удалось сохранить: ${it.message}" }))
        }
    }
    val restoreFrom = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? -> confirmRestore = uri }
    confirmRestore?.let { uri ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { confirmRestore = null },
            title = { Text("Восстановить из копии?") },
            text = { Text("Библиотека на этом телефоне заменится книгами, закладками, цитатами, карточками слов и статистикой из копии.") },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = {
                    confirmRestore = null
                    scope.launch {
                        busy = true
                        val r = runCatching { io.github.galis228.materialereader.data.Backup.restore(ctx, uri) }
                        busy = false
                        snackbar.showSnackbar(r.fold({ "Восстановлено книг: $it" }, { it.message ?: "Не удалось восстановить" }))
                    }
                }) { Text("Восстановить") }
            },
            dismissButton = { androidx.compose.material3.TextButton(onClick = { confirmRestore = null }) { Text("Отмена") } },
        )
    }

    val repo = remember { io.github.galis228.materialereader.data.SettingsRepo(ctx) }
    val rs by repo.settings.collectAsState(initial = null)
    fun edit(change: (io.github.galis228.materialereader.data.ReaderSettings) -> io.github.galis228.materialereader.data.ReaderSettings) {
        rs?.let { r -> scope.launch { repo.update(change(r)) } }
    }
    /** null = the list of categories; otherwise the open category. */
    var page by rememberSaveable { mutableStateOf<SettingsPage?>(null) }
    androidx.activity.compose.BackHandler(enabled = page != null) { page = null }
    val scroll = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(scroll.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(title = { Text(page?.title ?: "Настройки") }, scrollBehavior = scroll, navigationIcon = {
                IconButton(onClick = { if (page != null) page = null else onBack() }) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Назад") }
            })
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { pad ->
        androidx.compose.animation.AnimatedContent(page, label = "settings", transitionSpec = {
            (androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(220, 60)) +
                androidx.compose.animation.slideInHorizontally { if (targetState != null) it / 8 else -it / 8 }) togetherWith
                androidx.compose.animation.fadeOut(androidx.compose.animation.core.tween(120))
        }) { shownPage ->
        LazyColumn(Modifier.padding(pad).fillMaxSize(), contentPadding = PaddingValues(bottom = 32.dp)) {
            when (shownPage) {
            null -> {
                items(SettingsPage.entries) { p ->
                    ListItem(
                        headlineContent = { Text(p.title, style = MaterialTheme.typography.titleMedium) },
                        supportingContent = { Text(p.summary) },
                        leadingContent = {
                            Box(Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(p.tint(MaterialTheme.colorScheme)), contentAlignment = Alignment.Center) {
                                Icon(p.icon, null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
                            }
                        },
                        modifier = Modifier.clickable { page = p }.padding(horizontal = 4.dp, vertical = 2.dp),
                    )
                }
                item {
                    Text("Шрифт, отступы, тема страницы и анимации листания настраиваются прямо в книге — кнопка «Текст» внизу.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp))
                }
            }
            SettingsPage.LOOK -> {
                item { Section("Цвета приложения") }
                item {
                    Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                            val opts = io.github.galis228.materialereader.data.AppColor.entries
                            opts.forEachIndexed { i, o ->
                                SegmentedButton(selected = rs?.appColor == o, onClick = { edit { it.copy(appColor = o) } },
                                    shape = SegmentedButtonDefaults.itemShape(i, opts.size)) { Text(o.label, maxLines = 2, style = MaterialTheme.typography.labelMedium) }
                            }
                        }
                        Text(when (rs?.appColor) {
                            io.github.galis228.materialereader.data.AppColor.COVER -> "Цвета подбираются под обложку книги, которую ты читаешь."
                            io.github.galis228.materialereader.data.AppColor.CLASSIC -> "Спокойная зелёная тема приложения."
                            else -> "Material You: цвета из обоев телефона."
                        }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                item { Section("Тёмная тема") }
                item {
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                        val opts = io.github.galis228.materialereader.data.DarkMode.entries
                        opts.forEachIndexed { i, o ->
                            SegmentedButton(selected = rs?.darkMode == o, onClick = { edit { it.copy(darkMode = o) } },
                                shape = SegmentedButtonDefaults.itemShape(i, opts.size)) { Text(o.label, maxLines = 1) }
                        }
                    }
                }
                item {
                    ListItem(
                        headlineContent = { Text("Чёрный фон для OLED") },
                        supportingContent = { Text("В тёмной теме — чисто чёрные экраны, экономят батарею") },
                        trailingContent = { Switch(checked = rs?.amoled == true, onCheckedChange = { v -> edit { it.copy(amoled = v) } }) },
                    )
                }
                item { HorizontalDivider(Modifier.padding(vertical = 8.dp, horizontal = 16.dp)) }
            item {
                rs?.let { r ->
                    ListItem(
                        headlineContent = { Text("Простой режим") },
                        supportingContent = { Text("Крупные обложки и шрифт, светлая тема, только самые нужные кнопки. Удобно для пожилых и детей.") },
                        trailingContent = { Switch(checked = r.simpleMode, onCheckedChange = null) },
                        modifier = Modifier.clickable {
                            val on = !r.simpleMode
                            scope.launch {
                                repo.update(if (on) r.copy(simpleMode = true, fontSize = maxOf(r.fontSize, 24f), theme = io.github.galis228.materialereader.data.ReaderTheme.LIGHT,
                                    justify = false, textColor = 0L, bgColor = 0L, tapToTurn = true) else r.copy(simpleMode = false))
                            }
                        },
                    )
                }
            }
            }
            SettingsPage.LIBRARY -> {
                item { Section("Вид библиотеки") }
                item {
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                        val opts = io.github.galis228.materialereader.data.LibraryLayout.entries
                        opts.forEachIndexed { i, o ->
                            SegmentedButton(selected = rs?.libraryLayout == o, onClick = { edit { it.copy(libraryLayout = o) } },
                                shape = SegmentedButtonDefaults.itemShape(i, opts.size)) { Text(o.label, maxLines = 1) }
                        }
                    }
                }
                item {
                    Text(if (rs?.libraryLayout == io.github.galis228.materialereader.data.LibraryLayout.ROWS)
                        "Каждая полка — ряд обложек; фильтры и сортировка — за кнопкой «Показать»." else "Полки — вкладками сверху, сортировка и вид — в строке над книгами.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
                }
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
            }
            SettingsPage.READING -> {
                item { Section("Перевод") }
            item {
                var target by remember { mutableStateOf(io.github.galis228.materialereader.data.Translate.target(ctx)) }
                Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Переводить на", style = MaterialTheme.typography.bodyMedium)
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                        val opts = io.github.galis228.materialereader.data.Translate.targets
                        opts.forEachIndexed { i, (code, label) ->
                            SegmentedButton(selected = target == code, onClick = { target = code; io.github.galis228.materialereader.data.Translate.setTarget(ctx, code) },
                                shape = SegmentedButtonDefaults.itemShape(i, opts.size)) { Text(label, maxLines = 1) }
                        }
                    }
                    Text("Перевод работает на телефоне без интернета. Языковой пакет (около 30 МБ) скачивается один раз при первом переводе. Если слово уже на этом языке — переводится на английский.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
                item { Section("Напоминание почитать") }
                item { ReminderBlock() }
            }
            SettingsPage.AI -> {
                item { Section("Глубина объяснений") }
                item {
                    val levels = listOf("Как ребёнку", "Просто", "Подробно", "Академично")
                    Column(Modifier.padding(horizontal = 16.dp)) {
                        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                            levels.forEachIndexed { i, l ->
                                SegmentedButton(selected = (rs?.aiLevel ?: 1) == i, onClick = { edit { it.copy(aiLevel = i) } },
                                    shape = SegmentedButtonDefaults.itemShape(i, levels.size)) { Text(l, maxLines = 1, style = MaterialTheme.typography.labelSmall) }
                            }
                        }
                        Text("Влияет на объяснения, пересказы и ответы на вопросы.", style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp))
                    }
                }
                item { Section("Модель") }
                item { AiSettingsBlock() }
            }
            SettingsPage.DATA -> {
                item { Section("Резервная копия") }
            item {
                ListItem(
                    headlineContent = { Text("Сохранить копию") },
                    supportingContent = { Text("Книги, позиции, закладки, цитаты, слова и статистика одним файлом. Можно сохранить на Google Диск — так библиотека переезжает на новый телефон") },
                    modifier = Modifier.clickable(enabled = !busy) {
                        backupTo.launch("material-ereader-${java.time.LocalDate.now()}.zip")
                    },
                )
            }
            item {
                ListItem(
                    headlineContent = { Text("Восстановить из копии") },
                    modifier = Modifier.clickable(enabled = !busy) { restoreFrom.launch(arrayOf("application/zip", "application/octet-stream")) },
                )
            }
            if (busy) item { androidx.compose.material3.LinearProgressIndicator(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) }
            }
            SettingsPage.ABOUT -> {
                item {
                    ListItem(headlineContent = { Text("Material eReader") }, supportingContent = { Text("Версия ${Updater.currentVersion}") },
                        leadingContent = { Icon(Icons.AutoMirrored.Rounded.MenuBook, null, Modifier.size(40.dp), tint = MaterialTheme.colorScheme.primary) })
                }
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AiSettingsBlock() {
    val ctx = LocalContext.current
    val ai = remember { Ai.get(ctx) }
    var engine by remember { mutableStateOf(ai.settings.engine) }
    var model by remember { mutableStateOf(ai.settings.model) }
    var ctxTokens by remember { mutableStateOf(ai.settings.ctxTokens) }
    var gpu by remember { mutableStateOf(ai.settings.useGpu) }
    var access by remember { mutableStateOf(SharedModels.hasAccess()) }
    var nano by remember { mutableStateOf<Boolean?>(null) }
    var models by remember { mutableStateOf(ai.local.models()) }
    val scope = rememberCoroutineScope()

    // Back from system settings: re-check the "All files" permission and rescan the folder
    val owner = LocalLifecycleOwner.current
    LaunchedEffect(owner) {
        owner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            access = SharedModels.hasAccess(); models = ai.local.models()
        }
    }
    LaunchedEffect(Unit) { nano = ai.nano.available() }

    Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            EngineChoice.entries.forEachIndexed { i, e ->
                SegmentedButton(selected = engine == e, onClick = { engine = e; ai.settings.engine = e },
                    shape = SegmentedButtonDefaults.itemShape(i, EngineChoice.entries.size)) { Text(e.label, maxLines = 1) }
            }
        }
        Text(
            "Gemini Nano: " + when (nano) { null -> "проверяю…"; true -> "готова"; false -> "недоступна на этом телефоне" },
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (engine == EngineChoice.API || engine == EngineChoice.AUTO) ApiBlock(ai)
        ModelDownloadsCard { models = ai.local.models() }
        OutlinedCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.FolderShared, null); Spacer(Modifier.width(8.dp))
                    Text("Общая папка моделей", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                    if (access) Text("включена", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                }
                Text("«${SharedModels.path}» — та же папка, что у НаноПеро и «Персоны». Скачанные там модели (.gguf, .litertlm) " +
                    "появятся здесь без повторной загрузки.", style = MaterialTheme.typography.bodySmall)
                if (!access) {
                    Text("Нужно разрешение «Доступ ко всем файлам» — иначе Android не даёт читать файлы, скачанные другим приложением.",
                        style = MaterialTheme.typography.bodySmall)
                    FilledTonalButton(onClick = { SharedModels.requestAccess(ctx) }) { Text("Открыть доступ к папке") }
                }
            }
        }
        if (models.isEmpty()) Text(
            if (access) "В папке пока нет моделей. Скачай модель в НаноПеро или «Персоне» — она сразу появится тут."
            else "Модели появятся после того, как откроешь доступ к общей папке.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        models.forEach { f ->
            Row(
                Modifier.fillMaxWidth().clickable { model = f.name; ai.settings.model = f.name; scope.launch { ai.local.release() } },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(selected = model == f.name, onClick = null)
                Column(Modifier.weight(1f).padding(start = 8.dp)) {
                    Text(f.name.substringBeforeLast('.'), maxLines = 1)
                    Text("%.1f ГБ · %s".format(f.length() / 1073741824f, f.extension.uppercase()),
                        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        Text("Контекст (сколько текста модель видит за раз)", style = MaterialTheme.typography.bodyMedium)
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            listOf(2048, 4096, 8192).forEachIndexed { i, n ->
                SegmentedButton(selected = ctxTokens == n, onClick = { ctxTokens = n; ai.settings.ctxTokens = n; scope.launch { ai.local.release() } },
                    shape = SegmentedButtonDefaults.itemShape(i, 3)) { Text("$n") }
            }
        }
        Row(Modifier.fillMaxWidth().clickable { gpu = !gpu; ai.settings.useGpu = gpu }, verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Ускорение на GPU")
                Text("Для моделей .litertlm (Gemma 4). Если модель не запускается — выключи.",
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(checked = gpu, onCheckedChange = null)
        }
    }
}

@Composable
private fun ApiBlock(ai: Ai) {
    var provider by remember { mutableStateOf(ai.settings.apiProvider) }
    var url by remember { mutableStateOf(ai.settings.apiUrl) }
    var model by remember { mutableStateOf(ai.settings.apiModel) }
    var key by remember { mutableStateOf(ai.settings.apiKey) }
    var showKey by remember { mutableStateOf(false) }
    var test by remember { mutableStateOf<String?>(null) }
    var testing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    fun save() { ai.settings.apiProvider = provider; ai.settings.apiUrl = url.trim(); ai.settings.apiModel = model.trim(); ai.settings.apiKey = key.trim() }

    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Cloud, null); Spacer(Modifier.width(8.dp))
                Text("Нейросеть по API", style = MaterialTheme.typography.titleSmall)
            }
            Text("В этом режиме фрагменты книги отправляются выбранному сервису. Ключ хранится на телефоне в зашифрованном виде.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                ApiProvider.entries.forEachIndexed { i, p ->
                    SegmentedButton(selected = provider == p, onClick = {
                        if (url.isBlank() || ApiProvider.entries.any { it.defaultUrl == url }) url = p.defaultUrl
                        provider = p; save()
                    }, shape = SegmentedButtonDefaults.itemShape(i, ApiProvider.entries.size)) { Text(if (p == ApiProvider.OPENAI) "OpenAI и др." else p.label, maxLines = 1) }
                }
            }
            OutlinedTextField(url, { url = it; save() }, label = { Text("Адрес API") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                supportingText = if (provider == ApiProvider.OPENAI) {
                    { Text("OpenRouter: https://openrouter.ai/api/v1 · DeepSeek: https://api.deepseek.com/v1 · LM Studio: http://IP:1234/v1") }
                } else null)
            ModelPicker(ai, provider, url, key, model) { model = it; save() }
            OutlinedTextField(
                key, { key = it; save() }, label = { Text("Ключ API") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                visualTransformation = if (showKey) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                trailingIcon = { IconButton(onClick = { showKey = !showKey }) { Icon(if (showKey) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility, if (showKey) "Скрыть ключ" else "Показать ключ") } },
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                FilledTonalButton(enabled = !testing && model.isNotBlank(), onClick = {
                    save(); testing = true; test = null
                    scope.launch {
                        test = runCatching {
                            var last = ""
                            ApiEngine.stream(ai.settings.api, "Отвечай одним коротким предложением.", "Скажи, что соединение работает.", 0.2f, 60).collect { last = it }
                            "Работает: ${visibleAnswer(last).take(80)}"
                        }.getOrElse { it.message ?: "Ошибка" }
                        testing = false
                    }
                }) { Text(if (testing) "Проверяю…" else "Проверить") }
                test?.let { Text(it, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(start = 12.dp)) }
            }
        }
    }
}

/** Model field with a drop-down of the models the service actually offers for this key. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ModelPicker(ai: Ai, provider: ApiProvider, url: String, key: String, model: String, onPick: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    var models by remember { mutableStateOf<List<String>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    // reload when the service, address or key changes (after a short pause while typing)
    LaunchedEffect(provider, url, key) {
        models = emptyList(); error = null
        if (url.isBlank()) return@LaunchedEffect
        kotlinx.coroutines.delay(600)
        loading = true
        runCatching { ApiEngine.listModels(io.github.galis228.materialereader.ai.ApiConfig(provider, url, model, key)) }
            .onSuccess { models = it; if (it.isEmpty()) error = "Сервис не вернул список моделей — впиши название вручную" }
            .onFailure { error = if (key.isBlank()) "Укажи ключ, чтобы загрузить список моделей" else "Список моделей не загрузился: ${it.message}" }
        loading = false
    }
    val filtered = remember(models, model) {
        val q = model.trim().lowercase()
        if (q.isEmpty() || models.any { it.equals(q, true) }) models else models.filter { q in it.lowercase() }.ifEmpty { models }
    }
    androidx.compose.material3.ExposedDropdownMenuBox(expanded = expanded && filtered.isNotEmpty(), onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = model, onValueChange = { onPick(it); expanded = true }, singleLine = true,
            label = { Text("Модель") }, placeholder = { Text(provider.modelHint) },
            supportingText = {
                Text(when {
                    loading -> "Загружаю список моделей…"
                    error != null -> error!!
                    models.isNotEmpty() -> "Доступно моделей: ${models.size} — выбери из списка или начни вводить"
                    else -> ""
                })
            },
            trailingIcon = {
                if (loading) androidx.compose.material3.CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                else androidx.compose.material3.ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
            },
            modifier = Modifier.fillMaxWidth().menuAnchor(androidx.compose.material3.ExposedDropdownMenuAnchorType.PrimaryEditable),
        )
        ExposedDropdownMenu(expanded = expanded && filtered.isNotEmpty(), onDismissRequest = { expanded = false }) {
            filtered.take(200).forEach { m ->
                androidx.compose.material3.DropdownMenuItem(
                    text = { Text(m, maxLines = 1) },
                    onClick = { onPick(m); expanded = false },
                    contentPadding = androidx.compose.material3.ExposedDropdownMenuDefaults.ItemContentPadding,
                )
            }
        }
    }
}

/** Download a ready model into the shared folder, with progress, right from the settings. */
@Composable
private fun ModelDownloadsCard(onReady: () -> Unit) {
    val ctx = LocalContext.current
    var tick by remember { mutableStateOf(0) }
    LaunchedEffect(Unit) { while (true) { kotlinx.coroutines.delay(1000); tick++ } }
    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Скачать модель", style = MaterialTheme.typography.titleSmall)
            Text("Файл ляжет в «${io.github.galis228.materialereader.ai.SharedModels.path}» и станет виден другим приложениям. Лучше по Wi-Fi.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            io.github.galis228.materialereader.ai.ModelDownloads.offers.forEach { o ->
                val st = remember(tick) { io.github.galis228.materialereader.ai.ModelDownloads.state(ctx, o) }
                LaunchedEffect(st is io.github.galis228.materialereader.ai.ModelDownloads.State.Ready) { if (st is io.github.galis228.materialereader.ai.ModelDownloads.State.Ready) onReady() }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("${o.title} · ${if (o.sizeMb >= 1000) "%.1f ГБ".format(o.sizeMb / 1000f) else "${o.sizeMb} МБ"}", style = MaterialTheme.typography.bodyMedium)
                        Text(o.note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (st is io.github.galis228.materialereader.ai.ModelDownloads.State.Loading)
                            androidx.compose.material3.LinearProgressIndicator(progress = { st.fraction }, Modifier.fillMaxWidth().padding(top = 4.dp))
                    }
                    when (st) {
                        is io.github.galis228.materialereader.ai.ModelDownloads.State.Ready -> Text("есть", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
                        is io.github.galis228.materialereader.ai.ModelDownloads.State.Loading ->
                            TextButton(onClick = { io.github.galis228.materialereader.ai.ModelDownloads.cancel(ctx, o); tick++ }) { Text("Отмена") }
                        else -> TextButton(onClick = { io.github.galis228.materialereader.ai.ModelDownloads.start(ctx, o); tick++ }) {
                            Text(if (st is io.github.galis228.materialereader.ai.ModelDownloads.State.Failed) "Повторить" else "Скачать")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ReminderBlock() {
    val ctx = LocalContext.current
    var on by remember { mutableStateOf(io.github.galis228.materialereader.data.Reminders.enabled(ctx)) }
    var h by remember { mutableStateOf(io.github.galis228.materialereader.data.Reminders.hour(ctx)) }
    var m by remember { mutableStateOf(io.github.galis228.materialereader.data.Reminders.minute(ctx)) }
    val askPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        on = granted; io.github.galis228.materialereader.data.Reminders.set(ctx, granted, h, m)
    }
    ListItem(
        headlineContent = { Text("Каждый день в %02d:%02d".format(h, m)) },
        supportingContent = { Text("Только если сегодня ещё не читал — чтобы не прервалась серия дней") },
        trailingContent = {
            androidx.compose.material3.Switch(checked = on, onCheckedChange = { want ->
                if (want && android.os.Build.VERSION.SDK_INT >= 33 &&
                    ctx.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED)
                    askPermission.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                else { on = want; io.github.galis228.materialereader.data.Reminders.set(ctx, want, h, m) }
            })
        },
        modifier = Modifier.clickable {
            android.app.TimePickerDialog(ctx, { _, hh, mm -> h = hh; m = mm; io.github.galis228.materialereader.data.Reminders.set(ctx, on, hh, mm) }, h, m, true).show()
        },
    )
}

/** Settings categories, each its own page. */
private enum class SettingsPage(val title: String, val summary: String, val icon: androidx.compose.ui.graphics.vector.ImageVector) {
    LOOK("Оформление", "Цвета, тёмная тема, простой режим", Icons.Rounded.Palette),
    LIBRARY("Библиотека", "Вид, папки с книгами, каталоги", Icons.Rounded.LocalLibrary),
    READING("Чтение и перевод", "Язык перевода, напоминание почитать", Icons.AutoMirrored.Rounded.MenuBook),
    AI("ИИ-помощник", "Модель, ключ API, глубина объяснений", Icons.Rounded.AutoAwesome),
    DATA("Данные", "Резервная копия и восстановление", Icons.Rounded.Backup),
    ABOUT("О приложении", "Версия и обновления", Icons.Rounded.Info);

    fun tint(cs: androidx.compose.material3.ColorScheme) = when (this) {
        LOOK, AI -> cs.tertiaryContainer
        LIBRARY, DATA -> cs.secondaryContainer
        else -> cs.primaryContainer
    }
}
