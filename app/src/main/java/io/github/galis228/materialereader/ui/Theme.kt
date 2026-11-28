package io.github.galis228.materialereader.ui

import androidx.compose.ui.unit.dp
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import io.github.galis228.materialereader.R
import io.github.galis228.materialereader.data.ReaderFont
import io.github.galis228.materialereader.data.ReaderTheme

private val LightGreen = lightColorScheme(
    primary = Color(0xFF2F5D3A), onPrimary = Color(0xFFF3F6EE),
    primaryContainer = Color(0xFFC8DCC0), onPrimaryContainer = Color(0xFF1B2A1E),
    secondaryContainer = Color(0xFFDFE8D6), onSecondaryContainer = Color(0xFF1B2A1E),
    background = Color(0xFFF3F6EE), surface = Color(0xFFF3F6EE), onSurface = Color(0xFF1B2A1E),
    surfaceContainer = Color(0xFFE4ECDC), surfaceContainerHigh = Color(0xFFDFE8D6),
    onSurfaceVariant = Color(0xFF4D5C50), outline = Color(0xFF8A9A8C), outlineVariant = Color(0xFFB9C6B2),
)
private val DarkGreen = darkColorScheme(
    primary = Color(0xFFA8D3AE), onPrimary = Color(0xFF113A1C),
    primaryContainer = Color(0xFF2F5D3A), onPrimaryContainer = Color(0xFFC8DCC0),
)

/**
 * Material 3 theme of the app. Colors come from the wallpaper (Material You), from the cover of the book being
 * read, or the classic green; dark mode follows the system or the person's choice; OLED black is optional.
 * Shapes are a little rounder than the defaults.
 */
@Composable
fun AppTheme(content: @Composable () -> Unit) {
    val ctx = LocalContext.current
    val s = androidx.compose.runtime.remember { io.github.galis228.materialereader.data.SettingsRepo(ctx) }
        .settings.collectAsState(initial = null).value
    val dark = when (s?.darkMode) {
        io.github.galis228.materialereader.data.DarkMode.LIGHT -> false
        io.github.galis228.materialereader.data.DarkMode.DARK -> true
        else -> isSystemInDarkTheme()
    }
    val coverSeed = if (s?.appColor == io.github.galis228.materialereader.data.AppColor.COVER) rememberCoverSeed() else null
    var scheme = when {
        coverSeed != null -> schemeFromSeed(coverSeed, dark)
        s?.appColor != io.github.galis228.materialereader.data.AppColor.CLASSIC && Build.VERSION.SDK_INT >= 31 ->
            if (dark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
        dark -> DarkGreen
        else -> LightGreen
    }
    if (dark && s?.amoled == true) scheme = scheme.copy(
        background = Color.Black, surface = Color.Black, surfaceContainerLowest = Color.Black,
        surfaceContainerLow = Color(0xFF0B0B0D), surfaceContainer = Color(0xFF111114),
    )
    MaterialTheme(colorScheme = scheme, shapes = AppShapes, content = content)
}

private val AppShapes = androidx.compose.material3.Shapes(
    extraSmall = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
    small = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
    medium = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
    large = androidx.compose.foundation.shape.RoundedCornerShape(24.dp),
    extraLarge = androidx.compose.foundation.shape.RoundedCornerShape(32.dp),
)

/** Seed color of the current book's cover, the most "Material" one (Celebi quantizer + scoring). */
@Composable
private fun rememberCoverSeed(): Int? {
    val ctx = LocalContext.current
    val lib = androidx.compose.runtime.remember { io.github.galis228.materialereader.data.Library.get(ctx) }
    val books by lib.books.collectAsState()
    val cur = books.filter { it.hasCover && !it.hidden }.maxByOrNull { it.lastOpenedAt } ?: return null
    val seed by androidx.compose.runtime.produceState<Int?>(seedCache[cur.id + "|" + cur.coverRev], cur.id, cur.coverRev) {
        if (value != null) return@produceState
        value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) { coverSeed(lib.coverFile(cur.id)) }
            ?.also { seedCache[cur.id + "|" + cur.coverRev] = it }
    }
    return seed
}

private val seedCache = java.util.concurrent.ConcurrentHashMap<String, Int>()

@android.annotation.SuppressLint("RestrictedApi")
fun coverSeed(file: java.io.File): Int? = runCatching {
    val bmp = android.graphics.BitmapFactory.decodeFile(file.path, android.graphics.BitmapFactory.Options().apply { inSampleSize = 8 }) ?: return null
    val small = android.graphics.Bitmap.createScaledBitmap(bmp, 64, (64f * bmp.height / bmp.width).toInt().coerceAtLeast(1), true)
    val px = IntArray(small.width * small.height).also { small.getPixels(it, 0, small.width, 0, 0, small.width, small.height) }
    val q = com.google.android.material.color.utilities.QuantizerCelebi.quantize(px, 64)
    com.google.android.material.color.utilities.Score.score(q).firstOrNull()
}.getOrNull()

/** A full Material 3 color scheme from one seed color (tonal spot, like Material You does with the wallpaper). */
@android.annotation.SuppressLint("RestrictedApi")
fun schemeFromSeed(seed: Int, dark: Boolean): ColorScheme {
    val sc = com.google.android.material.color.utilities.SchemeTonalSpot(com.google.android.material.color.utilities.Hct.fromInt(seed), dark, 0.0)
    val m = com.google.android.material.color.utilities.MaterialDynamicColors()
    fun c(d: com.google.android.material.color.utilities.DynamicColor) = Color(d.getArgb(sc))
    val base = if (dark) darkColorScheme() else lightColorScheme()
    return base.copy(
        primary = c(m.primary()), onPrimary = c(m.onPrimary()), primaryContainer = c(m.primaryContainer()), onPrimaryContainer = c(m.onPrimaryContainer()),
        secondary = c(m.secondary()), onSecondary = c(m.onSecondary()), secondaryContainer = c(m.secondaryContainer()), onSecondaryContainer = c(m.onSecondaryContainer()),
        tertiary = c(m.tertiary()), onTertiary = c(m.onTertiary()), tertiaryContainer = c(m.tertiaryContainer()), onTertiaryContainer = c(m.onTertiaryContainer()),
        error = c(m.error()), onError = c(m.onError()), errorContainer = c(m.errorContainer()), onErrorContainer = c(m.onErrorContainer()),
        background = c(m.background()), onBackground = c(m.onBackground()), surface = c(m.surface()), onSurface = c(m.onSurface()),
        surfaceVariant = c(m.surfaceVariant()), onSurfaceVariant = c(m.onSurfaceVariant()), outline = c(m.outline()), outlineVariant = c(m.outlineVariant()),
        inverseSurface = c(m.inverseSurface()), inverseOnSurface = c(m.inverseOnSurface()), inversePrimary = c(m.inversePrimary()),
        surfaceDim = c(m.surfaceDim()), surfaceBright = c(m.surfaceBright()), surfaceContainerLowest = c(m.surfaceContainerLowest()),
        surfaceContainerLow = c(m.surfaceContainerLow()), surfaceContainer = c(m.surfaceContainer()), surfaceContainerHigh = c(m.surfaceContainerHigh()),
        surfaceContainerHighest = c(m.surfaceContainerHighest()), surfaceTint = c(m.primary()), scrim = Color.Black,
    )
}

/** Colors of the reading page. */
data class PageColors(val bg: Color, val text: Color, val muted: Color, val accent: Color, val panel: Color, val isDark: Boolean)

fun pageColors(theme: ReaderTheme, scheme: ColorScheme): PageColors = when (theme) {
    ReaderTheme.PAPER -> PageColors(Color(0xFFF4EAD6), Color(0xFF2B2118), Color(0xFF6B5A48), Color(0xFFB5651D), Color(0xFFEADDC4), false)
    ReaderTheme.LIGHT -> PageColors(Color(0xFFFBF8F1), Color(0xFF1F1F1F), Color(0xFF5E5E5E), Color(0xFF3D5A9E), Color(0xFFEFEBE3), false)
    ReaderTheme.DARK -> PageColors(Color(0xFF1E1E20), Color(0xFFD8D4CC), Color(0xFF9A968E), Color(0xFFE0A85C), Color(0xFF2A2A2E), true)
    ReaderTheme.NIGHT -> PageColors(Color(0xFF000000), Color(0xFFB9B4AA), Color(0xFF7D7A74), Color(0xFF8FA8FF), Color(0xFF15161B), true)
    ReaderTheme.YOU -> PageColors(scheme.surface, scheme.onSurface, scheme.onSurfaceVariant, scheme.primary, scheme.surfaceContainerHigh,
        scheme.surface.luminance() < 0.4f)
}

private fun Color.luminance(): Float = 0.2126f * red + 0.7152f * green + 0.0722f * blue

@OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)
private fun variable(regular: Int, italic: Int) = FontFamily(
    listOf(400, 600, 700).flatMap { w ->
        listOf(
            Font(regular, FontWeight(w), FontStyle.Normal, variationSettings = FontVariation.Settings(FontVariation.weight(w))),
            Font(italic, FontWeight(w), FontStyle.Italic, variationSettings = FontVariation.Settings(FontVariation.weight(w))),
        )
    },
)

private val literata by lazy { variable(R.font.literata, R.font.literata_italic) }
private val lora by lazy { variable(R.font.lora, R.font.lora_italic) }
private val garamond by lazy { variable(R.font.ebgaramond, R.font.ebgaramond_italic) }
private val ptSerif by lazy { FontFamily(Font(R.font.ptserif_regular), Font(R.font.ptserif_italic, style = FontStyle.Italic), Font(R.font.ptserif_bold, FontWeight.Bold), Font(R.font.ptserif_bold, FontWeight.SemiBold)) }
private val ptSans by lazy { FontFamily(Font(R.font.ptsans_regular), Font(R.font.ptsans_italic, style = FontStyle.Italic), Font(R.font.ptsans_bold, FontWeight.Bold), Font(R.font.ptsans_bold, FontWeight.SemiBold)) }
private val dyslexic by lazy { FontFamily(Font(R.font.opendyslexic_regular), Font(R.font.opendyslexic_italic, style = FontStyle.Italic), Font(R.font.opendyslexic_bold, FontWeight.Bold), Font(R.font.opendyslexic_bold, FontWeight.SemiBold)) }
private val andika by lazy { FontFamily(Font(R.font.andika_regular), Font(R.font.andika_italic, style = FontStyle.Italic), Font(R.font.andika_bold, FontWeight.Bold), Font(R.font.andika_bold, FontWeight.SemiBold)) }
private val fira by lazy { FontFamily(Font(R.font.firasans_regular), Font(R.font.firasans_italic, style = FontStyle.Italic), Font(R.font.firasans_semibold, FontWeight.SemiBold), Font(R.font.firasans_semibold, FontWeight.Bold)) }

/** A font file the reader picked; set by [setCustomFont]. */
@Volatile private var custom: Pair<String, FontFamily>? = null
fun customFontFile(ctx: android.content.Context) = java.io.File(ctx.filesDir, "fonts/custom_font")
private var customPath: String? = null
fun initCustomFont(ctx: android.content.Context) { customPath = customFontFile(ctx).takeIf { it.exists() }?.path }

fun ReaderFont.family(): FontFamily = when (this) {
    ReaderFont.LITERATA -> literata
    ReaderFont.PT_SERIF -> ptSerif
    ReaderFont.LORA -> lora
    ReaderFont.GARAMOND -> garamond
    ReaderFont.PT_SANS -> ptSans
    ReaderFont.FIRA -> fira
    ReaderFont.ANDIKA -> andika
    ReaderFont.DYSLEXIC -> dyslexic
    ReaderFont.SERIF -> FontFamily.Serif
    ReaderFont.SANS -> FontFamily.SansSerif
    ReaderFont.MONO -> FontFamily.Monospace
    ReaderFont.CUSTOM -> customPath?.let { p ->
        val f = java.io.File(p)
        val key = "$p:${f.lastModified()}"
        custom?.takeIf { it.first == key }?.second
            ?: runCatching { FontFamily(Font(f)) }.getOrNull()?.also { custom = key to it }
    } ?: FontFamily.Serif
}

/** Theme colors with the reader's own text/background colors applied on top. */
fun pageColors(s: io.github.galis228.materialereader.data.ReaderSettings, scheme: ColorScheme): PageColors {
    val base = pageColors(s.theme, scheme)
    if (s.textColor == 0L && s.bgColor == 0L) return base
    val bg = if (s.bgColor != 0L) Color(s.bgColor.toInt()) else base.bg
    val dark = bg.luminance() < 0.4f
    val text = if (s.textColor != 0L) Color(s.textColor.toInt()) else if (s.bgColor != 0L) (if (dark) Color(0xFFDDD8CF) else Color(0xFF222222)) else base.text
    return PageColors(bg, text, text.copy(alpha = 0.65f), base.accent, androidx.compose.ui.graphics.lerp(bg, text, 0.07f), dark)
}
