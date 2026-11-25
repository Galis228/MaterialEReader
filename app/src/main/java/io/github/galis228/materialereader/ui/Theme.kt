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
import androidx.compose.ui.text.font.FontFamily
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

fun ReaderFont.family(): FontFamily = when (this) {
    ReaderFont.SERIF -> FontFamily.Serif
    ReaderFont.SANS -> FontFamily.SansSerif
    ReaderFont.MONO -> FontFamily.Monospace
}
