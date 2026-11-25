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
enum class ReaderFont(val label: String) {
    LITERATA("Literata"), PT_SERIF("PT Serif"), LORA("Lora"), GARAMOND("EB Garamond"),
    PT_SANS("PT Sans"), FIRA("Fira Sans"), ANDIKA("Andika"),
    SERIF("Системный с засечками"), SANS("Системный"), MONO("Моно"), CUSTOM("Свой шрифт"),
}
enum class ReadMode(val label: String) { SCROLL("Прокрутка"), PAGES("Страницы") }
enum class PageAnim(val label: String) { SLIDE("Сдвиг"), COVER("Наложение"), FADE("Затухание"), CURL("Загиб"), NONE("Без анимации") }

data class ReaderSettings(
    val fontSize: Float = 19f,
    val lineHeight: Float = 1.55f,
    val margin: Float = 24f,
    val font: ReaderFont = ReaderFont.LITERATA,
    val theme: ReaderTheme = ReaderTheme.PAPER,
    val justify: Boolean = true,
    val volumeKeys: Boolean = true,
    val keepScreenOn: Boolean = true,
    val tapToTurn: Boolean = true,
    val mode: ReadMode = ReadMode.PAGES,
    val anim: PageAnim = PageAnim.SLIDE,
    val twoPages: Boolean = true,
    val mangaRtl: Boolean = false,
    /** -1 = follow the system. */
    val brightness: Float = -1f,
    val warmth: Float = 0f,
    /** 0 = take from the theme. */
    val textColor: Long = 0L,
    val bgColor: Long = 0L,
    val simpleMode: Boolean = false,
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
        val bright = floatPreferencesKey("brightness")
        val warm = floatPreferencesKey("warmth")
        val textColor = androidx.datastore.preferences.core.longPreferencesKey("text_color")
        val bgColor = androidx.datastore.preferences.core.longPreferencesKey("bg_color")
        val simple = booleanPreferencesKey("simple_mode")
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
            brightness = this[K.bright] ?: d.brightness,
            warmth = this[K.warm] ?: d.warmth,
            textColor = this[K.textColor] ?: d.textColor,
            bgColor = this[K.bgColor] ?: d.bgColor,
            simpleMode = this[K.simple] ?: d.simpleMode,
        )
    }

    suspend fun update(s: ReaderSettings) {
        ctx.store.edit {
            it[K.size] = s.fontSize; it[K.line] = s.lineHeight; it[K.margin] = s.margin
            it[K.font] = s.font.name; it[K.theme] = s.theme.name; it[K.justify] = s.justify
            it[K.volume] = s.volumeKeys; it[K.screenOn] = s.keepScreenOn; it[K.tap] = s.tapToTurn
            it[K.mode] = s.mode.name; it[K.anim] = s.anim.name; it[K.two] = s.twoPages; it[K.rtl] = s.mangaRtl
            it[K.bright] = s.brightness; it[K.warm] = s.warmth
            it[K.textColor] = s.textColor; it[K.bgColor] = s.bgColor; it[K.simple] = s.simpleMode
        }
    }
}
