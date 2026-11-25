package io.github.galis228.materialereader.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

enum class ReaderTheme(val label: String) { PAPER("Бумага"), LIGHT("Светлая"), DARK("Тёмная"), NIGHT("Ночь OLED"), YOU("Material You") }
enum class ReaderFont(val label: String) { SERIF("С засечками"), SANS("Без засечек"), MONO("Моно") }
enum class ReadMode(val label: String) { SCROLL("Прокрутка"), PAGES("Страницы") }
enum class PageAnim(val label: String) { SLIDE("Сдвиг"), COVER("Наложение"), FADE("Затухание"), CURL("Загиб"), NONE("Без анимации") }

data class ReaderSettings(
    val fontSize: Float = 19f,
    val lineHeight: Float = 1.55f,
    val margin: Float = 24f,
    val font: ReaderFont = ReaderFont.SERIF,
    val theme: ReaderTheme = ReaderTheme.PAPER,
    val justify: Boolean = true,
    val volumeKeys: Boolean = true,
    val keepScreenOn: Boolean = true,
    val tapToTurn: Boolean = true,
    val mode: ReadMode = ReadMode.PAGES,
    val anim: PageAnim = PageAnim.SLIDE,
    val twoPages: Boolean = true,
    val mangaRtl: Boolean = false,
)

private val Context.store by preferencesDataStore("reader_settings")

class SettingsRepo(private val ctx: Context) {
    private object K {
        val size = floatPreferencesKey("size")
        val line = floatPreferencesKey("line")
        val margin = floatPreferencesKey("margin")
        val font = stringPreferencesKey("font")
        val theme = stringPreferencesKey("theme")
        val justify = booleanPreferencesKey("justify")
        val volume = booleanPreferencesKey("volume")
        val screenOn = booleanPreferencesKey("screen_on")
        val tap = booleanPreferencesKey("tap")
        val mode = stringPreferencesKey("mode")
        val anim = stringPreferencesKey("anim")
        val two = booleanPreferencesKey("two_pages")
        val rtl = booleanPreferencesKey("manga_rtl")
    }

    val settings: Flow<ReaderSettings> = ctx.store.data.map { p -> p.toSettings() }

    private fun Preferences.toSettings(): ReaderSettings {
        val d = ReaderSettings()
        return ReaderSettings(
            fontSize = this[K.size] ?: d.fontSize,
            lineHeight = this[K.line] ?: d.lineHeight,
            margin = this[K.margin] ?: d.margin,
            font = this[K.font]?.let { runCatching { ReaderFont.valueOf(it) }.getOrNull() } ?: d.font,
            theme = this[K.theme]?.let { runCatching { ReaderTheme.valueOf(it) }.getOrNull() } ?: d.theme,
            justify = this[K.justify] ?: d.justify,
            volumeKeys = this[K.volume] ?: d.volumeKeys,
            keepScreenOn = this[K.screenOn] ?: d.keepScreenOn,
            tapToTurn = this[K.tap] ?: d.tapToTurn,
            mode = this[K.mode]?.let { runCatching { ReadMode.valueOf(it) }.getOrNull() } ?: d.mode,
            anim = this[K.anim]?.let { runCatching { PageAnim.valueOf(it) }.getOrNull() } ?: d.anim,
            twoPages = this[K.two] ?: d.twoPages,
            mangaRtl = this[K.rtl] ?: d.mangaRtl,
        )
    }

    suspend fun update(s: ReaderSettings) {
        ctx.store.edit {
            it[K.size] = s.fontSize; it[K.line] = s.lineHeight; it[K.margin] = s.margin
            it[K.font] = s.font.name; it[K.theme] = s.theme.name; it[K.justify] = s.justify
            it[K.volume] = s.volumeKeys; it[K.screenOn] = s.keepScreenOn; it[K.tap] = s.tapToTurn
            it[K.mode] = s.mode.name; it[K.anim] = s.anim.name; it[K.two] = s.twoPages; it[K.rtl] = s.mangaRtl
        }
    }
}
