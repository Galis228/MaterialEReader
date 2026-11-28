package io.github.galis228.materialereader

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import io.github.galis228.materialereader.data.Library
import io.github.galis228.materialereader.data.Release
import io.github.galis228.materialereader.data.Updater
import io.github.galis228.materialereader.ui.AppSettingsScreen
import io.github.galis228.materialereader.ui.AppTheme
import io.github.galis228.materialereader.ui.CatalogScreen
import io.github.galis228.materialereader.ui.LibraryScreen
import io.github.galis228.materialereader.ui.ReaderScreen
import io.github.galis228.materialereader.ui.UpdateDialog
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow

/** Volume-key page turns, routed from the activity to the open reader. */
object VolumeKeys {
    @Volatile var enabled = false
    private val _events = MutableSharedFlow<Int>(extraBufferCapacity = 4)
    val events: SharedFlow<Int> = _events
    fun emit(dir: Int) = _events.tryEmit(dir)
}

class MainActivity : ComponentActivity() {
    private var incoming by mutableStateOf<Uri?>(null)
    private var incomingImage by mutableStateOf<Uri?>(null)
    /** A book to open right away (from the home-screen widget). */
    private var openBook by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        io.github.galis228.materialereader.data.CrashLog.install(this)
        io.github.galis228.materialereader.ui.initCustomFont(this)
        route(intent)
        setContent {
            AppTheme {
                val nav = rememberNavController()
                val library = remember { Library.get(this) }
                var update by remember { mutableStateOf<Release?>(null) }

                LaunchedEffect(Unit) {
                    if (library.folders.isNotEmpty()) library.scan()
                    update = Updater.autoCheck(this@MainActivity)
                }
                LaunchedEffect(openBook) {
                    val id = openBook ?: return@LaunchedEffect
                    openBook = null
                    if (library.entry(id) != null) nav.navigate("reader/$id") { popUpTo("library") }
                }
                LaunchedEffect(incoming) {
                    val uri = incoming ?: return@LaunchedEffect
                    incoming = null
                    library.import(uri).onSuccess { nav.navigate("reader/${it.id}") }
                }
                NavHost(
                    nav, startDestination = "library",
                    // Material 3 shared-axis motion between screens
                    enterTransition = { androidx.compose.animation.slideInHorizontally(androidx.compose.animation.core.tween(320)) { it / 6 } + androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(220, 60)) },
                    exitTransition = { androidx.compose.animation.slideOutHorizontally(androidx.compose.animation.core.tween(320)) { -it / 6 } + androidx.compose.animation.fadeOut(androidx.compose.animation.core.tween(120)) },
                    popEnterTransition = { androidx.compose.animation.slideInHorizontally(androidx.compose.animation.core.tween(320)) { -it / 6 } + androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(220, 60)) },
                    popExitTransition = { androidx.compose.animation.slideOutHorizontally(androidx.compose.animation.core.tween(320)) { it / 6 } + androidx.compose.animation.fadeOut(androidx.compose.animation.core.tween(120)) },
                ) {
                    composable("library") {
                        LibraryScreen(
                            onOpen = { id -> nav.navigate("reader/$id") },
                            onCatalog = { nav.navigate("catalog") },
                            onWords = { nav.navigate("words") },
                            onSettings = { nav.navigate("settings") },
                            onStats = { nav.navigate("stats") },
                            onAiNotes = { nav.navigate("ainotes") },
                            onSearch = { nav.navigate("search") },
                        )
                    }
                    composable("ainotes") { io.github.galis228.materialereader.ui.AiNotesScreen(onBack = { nav.popBackStack() }, onOpenAt = { id, b -> nav.navigate("reader/$id?b=$b&c=0") }) }
                    composable("stats") { io.github.galis228.materialereader.ui.StatsScreen(onBack = { nav.popBackStack() }) }
                    composable(
                        "reader/{id}?b={b}&c={c}",
                        arguments = listOf(
                            androidx.navigation.navArgument("b") { type = androidx.navigation.NavType.IntType; defaultValue = -1 },
                            androidx.navigation.navArgument("c") { type = androidx.navigation.NavType.IntType; defaultValue = 0 },
                        ),
                    ) { entry ->
                        val b = entry.arguments?.getInt("b") ?: -1
                        ReaderScreen(
                            id = entry.arguments?.getString("id").orEmpty(), onBack = { nav.popBackStack() },
                            onAppSettings = { nav.navigate("settings") },
                            startAt = if (b >= 0) b to (entry.arguments?.getInt("c") ?: 0) else null,
                            onOpenBook = { next -> nav.navigate("reader/$next") { popUpTo("reader/{id}?b={b}&c={c}") { inclusive = true } } },
                        )
                    }
                    composable("search") {
                        io.github.galis228.materialereader.ui.GlobalSearchScreen(onBack = { nav.popBackStack() },
                            onOpenAt = { id, b, c -> nav.navigate("reader/$id?b=$b&c=$c") })
                    }
                    composable("catalog") {
                        CatalogScreen(onBack = { nav.popBackStack() }, onOpenBook = { id -> nav.navigate("reader/$id") })
                    }
                    composable("settings") { AppSettingsScreen(onBack = { nav.popBackStack() }) }
                    composable("words") { io.github.galis228.materialereader.ui.WordsScreen(onBack = { nav.popBackStack() }) }
                }
                update?.let { UpdateDialog(it) { update = null } }
                var crash by remember { mutableStateOf(io.github.galis228.materialereader.data.CrashLog.pending(this@MainActivity)) }
                crash?.let { report ->
                    androidx.compose.material3.AlertDialog(
                        onDismissRequest = { io.github.galis228.materialereader.data.CrashLog.clear(this@MainActivity); crash = null },
                        title = { androidx.compose.material3.Text("Приложение закрылось с ошибкой") },
                        text = { androidx.compose.material3.Text("В прошлый раз Material eReader упал. Отправь отчёт разработчику — по нему ошибку найти намного проще. В отчёте только технические данные, без твоих книг.") },
                        confirmButton = {
                            androidx.compose.material3.TextButton(onClick = {
                                io.github.galis228.materialereader.ui.share(this@MainActivity, report, "Отчёт об ошибке Material eReader")
                                io.github.galis228.materialereader.data.CrashLog.clear(this@MainActivity); crash = null
                            }) { androidx.compose.material3.Text("Отправить отчёт") }
                        },
                        dismissButton = {
                            androidx.compose.material3.TextButton(onClick = {
                                io.github.galis228.materialereader.data.CrashLog.clear(this@MainActivity); crash = null
                            }) { androidx.compose.material3.Text("Закрыть") }
                        },
                    )
                }
                incomingImage?.let { img ->
                    val target = remember(img) { io.github.galis228.materialereader.ui.PendingCover.take() }
                    io.github.galis228.materialereader.ui.SharedCoverDialog(img, target) { incomingImage = null }
                }
            }
        }
    }

    private fun incomingUri(i: Intent?): Uri? = when (i?.action) {
        Intent.ACTION_VIEW -> i.data
        Intent.ACTION_SEND -> if (Build.VERSION.SDK_INT >= 33) i.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
            else @Suppress("DEPRECATION") i.getParcelableExtra(Intent.EXTRA_STREAM)
        else -> null
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        route(intent)
    }

    /** Books go to the library; a shared picture becomes a cover. */
    private fun route(i: Intent?) {
        i?.getStringExtra(io.github.galis228.materialereader.data.ContinueWidget.EXTRA_BOOK)?.let { openBook = it; i.removeExtra(io.github.galis228.materialereader.data.ContinueWidget.EXTRA_BOOK); return }
        val uri = incomingUri(i) ?: return
        val type = i?.type ?: contentResolver.getType(uri)
        if (type?.startsWith("image/") == true) incomingImage = uri else incoming = uri
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (VolumeKeys.enabled) when (keyCode) {
            KeyEvent.KEYCODE_VOLUME_DOWN -> { VolumeKeys.emit(1); return true }
            KeyEvent.KEYCODE_VOLUME_UP -> { VolumeKeys.emit(-1); return true }
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent?): Boolean {
        if (VolumeKeys.enabled && (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN || keyCode == KeyEvent.KEYCODE_VOLUME_UP)) return true
        return super.onKeyUp(keyCode, event)
    }
}
