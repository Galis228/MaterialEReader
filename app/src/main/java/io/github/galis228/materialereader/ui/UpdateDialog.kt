package io.github.galis228.materialereader.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import io.github.galis228.materialereader.data.Release
import io.github.galis228.materialereader.data.Updater
import kotlinx.coroutines.launch
import java.io.File

@Composable
fun UpdateDialog(release: Release, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var progress by remember { mutableFloatStateOf(-1f) }
    var apk by remember { mutableStateOf<File?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    val sizeMb = if (release.size > 0) " · %.1f МБ".format(release.size / 1048576f) else ""

    fun install(f: File) {
        if (!Updater.canInstall(ctx)) { Updater.openInstallPermission(ctx); return }
        Updater.install(ctx, f)
    }

    AlertDialog(
        onDismissRequest = { if (progress < 0f || apk != null) onDismiss() },
        title = { Text("Доступна версия ${release.version}") },
        text = {
            Column {
                Text("Сейчас установлена ${Updater.currentVersion}$sizeMb", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (release.notes.isNotEmpty()) Text(
                    release.notes, style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 12.dp).heightIn(max = 260.dp).verticalScroll(rememberScrollState()),
                )
                if (progress >= 0f && apk == null) LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth().padding(top = 16.dp))
                if (apk != null && !Updater.canInstall(ctx)) Text(
                    "Разреши Material eReader устанавливать приложения, затем вернись и нажми «Установить».",
                    style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 12.dp),
                )
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 12.dp)) }
            }
        },
        confirmButton = {
            when {
                apk != null -> TextButton(onClick = { install(apk!!) }) { Text("Установить") }
                progress >= 0f -> TextButton(onClick = {}, enabled = false) { Text("Скачиваю…") }
                else -> TextButton(onClick = {
                    error = null; progress = 0f
                    scope.launch {
                        runCatching { Updater.download(ctx, release) { progress = it } }
                            .onSuccess { apk = it; install(it) }
                            .onFailure { error = it.message ?: "Ошибка загрузки"; progress = -1f }
                    }
                }) { Text("Обновить") }
            }
        },
        dismissButton = {
            if (apk == null && progress < 0f) TextButton(onClick = { Updater.skip(ctx, release.version); onDismiss() }) { Text("Пропустить версию") }
            else if (apk != null) TextButton(onClick = onDismiss) { Text("Позже") }
        },
    )
}
