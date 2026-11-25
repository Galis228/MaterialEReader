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

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        incoming = incomingUri(intent)
        setContent {
            AppTheme {
                val nav = rememberNavController()
                val library = remember { Library.get(this) }
                var update by remember { mutableStateOf<Release?>(null) }

                LaunchedEffect(Unit) {
                    if (library.folders.isNotEmpty()) library.scan()
                    update = Updater.autoCheck(this@MainActivity)
                }
                LaunchedEffect(incoming) {
                    val uri = incoming ?: return@LaunchedEffect
                    incoming = null
                    library.import(uri).onSuccess { nav.navigate("reader/${it.id}") }
                }
                NavHost(nav, startDestination = "library") {
                    composable("library") {
                        LibraryScreen(
                            onOpen = { id -> nav.navigate("reader/$id") },
                            onCatalog = { nav.navigate("catalog") },
                            onSettings = { nav.navigate("settings") },
                        )
                    }
                    composable("reader/{id}") { entry ->
                        ReaderScreen(id = entry.arguments?.getString("id").orEmpty(), onBack = { nav.popBackStack() })
                    }
                    composable("catalog") {
                        CatalogScreen(onBack = { nav.popBackStack() }, onOpenBook = { id -> nav.navigate("reader/$id") })
                    }
                    composable("settings") { AppSettingsScreen(onBack = { nav.popBackStack() }) }
                }
                update?.let { UpdateDialog(it) { update = null } }
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
        incomingUri(intent)?.let { incoming = it }
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
