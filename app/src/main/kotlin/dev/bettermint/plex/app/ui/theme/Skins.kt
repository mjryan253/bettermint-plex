package dev.bettermint.plex.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * Swappable skins. Minimal is the plain test skin used through M6; the Palm Pre
 * webOS-styled skin becomes the shipped default at the M7 design pass.
 */
enum class Skin { Minimal }

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
fun BettermintTheme(skin: Skin = Skin.Minimal, content: @Composable () -> Unit) {
    when (skin) {
        Skin.Minimal -> MaterialTheme(colorScheme = MinimalColors, content = content)
    }
}
