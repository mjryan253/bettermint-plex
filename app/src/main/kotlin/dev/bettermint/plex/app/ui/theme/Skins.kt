package dev.bettermint.plex.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.bettermint.plex.R

/**
 * Swappable skins. WebOS is the shipped default: a faithful Palm Pre webOS
 * revival (dark glossy chrome, warm linen card surfaces, selection-blue glow,
 * Nunito Sans standing in for Palm's Prelude). Minimal remains for testing.
 */
enum class Skin { WebOS, Minimal }

/** Raw webOS tokens for custom components (gloss brushes, linen cards). */
data class WebOsPalette(
    val ink: Color = Color(0xFF141416),
    val charcoal: Color = Color(0xFF2B2B2E),
    val charcoalHigh: Color = Color(0xFF3A3A3E),
    val linen: Color = Color(0xFFF2F0EB),
    val onLinen: Color = Color(0xFF232220),
    val graphite: Color = Color(0xFF6E6B66),
    val glow: Color = Color(0xFF3D8EDB),
    val gold: Color = Color(0xFFE8A33D),
    val warmWhite: Color = Color(0xFFF2F1EE),
)

val LocalWebOsPalette = staticCompositionLocalOf { WebOsPalette() }

val NunitoSans = FontFamily(
    Font(R.font.nunito_sans_300, FontWeight.Light),
    Font(R.font.nunito_sans_400, FontWeight.Normal),
    Font(R.font.nunito_sans_600, FontWeight.SemiBold),
    Font(R.font.nunito_sans_700, FontWeight.Bold),
    Font(R.font.nunito_sans_800, FontWeight.ExtraBold),
)

/**
 * webOS type scale: thin, airy screen titles (the "Launcher" voice), bold row
 * titles, generous body sizes for couch distance. Modular ~1.25 ratio.
 */
private val WebOsTypography = Typography(
    headlineMedium = TextStyle(
        fontFamily = NunitoSans,
        fontWeight = FontWeight.Light,
        fontSize = 34.sp,
        lineHeight = 40.sp,
    ),
    headlineSmall = TextStyle(
        fontFamily = NunitoSans,
        fontWeight = FontWeight.Bold,
        fontSize = 27.sp,
        lineHeight = 34.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = NunitoSans,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        letterSpacing = 1.2.sp,
        lineHeight = 22.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = NunitoSans,
        fontWeight = FontWeight.Normal,
        fontSize = 17.sp,
        lineHeight = 24.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = NunitoSans,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 21.sp,
    ),
    bodySmall = TextStyle(
        fontFamily = NunitoSans,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 18.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = NunitoSans,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 20.sp,
    ),
)

/** webOS rounds everything: cards, panels, pills. */
private val WebOsShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

private fun webOsColorScheme(p: WebOsPalette) = darkColorScheme(
    primary = p.glow,
    onPrimary = Color.White,
    primaryContainer = p.glow.copy(alpha = 0.25f),
    onPrimaryContainer = p.warmWhite,
    secondary = p.gold,
    onSecondary = Color(0xFF2A1E08),
    background = p.ink,
    onBackground = p.warmWhite,
    surface = p.charcoal,
    onSurface = p.warmWhite,
    surfaceVariant = p.charcoalHigh,
    onSurfaceVariant = Color(0xFFB8B5B0),
    outline = Color(0xFF55534F),
    error = Color(0xFFE88080),
    onError = Color(0xFF330A0A),
)

private val MinimalColors = darkColorScheme(
    primary = Color(0xFF8FD9C0),
    onPrimary = Color(0xFF00382A),
    background = Color(0xFF121212),
    onBackground = Color(0xFFE6E6E6),
    surface = Color(0xFF1E1E1E),
    onSurface = Color(0xFFE6E6E6),
    surfaceVariant = Color(0xFF2A2A2A),
    onSurfaceVariant = Color(0xFFBDBDBD),
    error = Color(0xFFF2B8B5),
)

@Composable
fun BettermintTheme(skin: Skin = Skin.WebOS, content: @Composable () -> Unit) {
    when (skin) {
        Skin.WebOS -> {
            val palette = WebOsPalette()
            CompositionLocalProvider(LocalWebOsPalette provides palette) {
                MaterialTheme(
                    colorScheme = webOsColorScheme(palette),
                    typography = WebOsTypography,
                    shapes = WebOsShapes,
                    content = content,
                )
            }
        }
        Skin.Minimal -> MaterialTheme(colorScheme = MinimalColors, content = content)
    }
}
