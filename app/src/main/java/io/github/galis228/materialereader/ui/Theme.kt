package io.github.galis228.materialereader.ui

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

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val ctx = LocalContext.current
    val scheme = when {
        Build.VERSION.SDK_INT >= 31 -> if (dark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
        dark -> DarkGreen
        else -> LightGreen
    }
    MaterialTheme(colorScheme = scheme, content = content)
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
