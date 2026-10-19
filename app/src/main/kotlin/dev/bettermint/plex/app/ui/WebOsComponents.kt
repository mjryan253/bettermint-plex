package dev.bettermint.plex.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.bettermint.plex.app.ui.theme.LocalWebOsPalette

/**
 * The webOS wallpaper: every screen floats over a photographic-feeling dusk
 * scene, never flat black. Drawn as layered gradients (deep night sky falling
 * to a lit horizon band, with a soft atmospheric glow) so no bundled image or
 * RenderEffect is needed and it renders identically on Fire OS 6.
 */
@Composable
fun WebOsWallpaper(modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxSize()
            .drawBehind {
                drawRect(
                    Brush.verticalGradient(
                        0.00f to Color(0xFF0A101C),
                        0.42f to Color(0xFF14273E),
                        0.68f to Color(0xFF28567C),
                        0.76f to Color(0xFF3A79A4),
                        0.84f to Color(0xFF1A2A38),
                        1.00f to Color(0xFF090D12),
                    ),
                )
                // Atmospheric glow sitting on the horizon line.
                drawRect(
                    Brush.radialGradient(
                        listOf(Color(0x5560AFE0), Color.Transparent),
                        center = Offset(size.width * 0.5f, size.height * 0.74f),
                        radius = size.width * 0.72f,
                    ),
                )
            },
    )
}

/**
 * A webOS card: light floating surface with rounded corners and a drop shadow,
 * leaving wallpaper visible in the margins around it. All browse content lives
 * on these, mirroring the Pre's card-and-wallpaper layering.
 */
@Composable
fun FloatingCard(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val palette = LocalWebOsPalette.current
    Surface(
        color = palette.linen,
        contentColor = palette.onLinen,
        shape = MaterialTheme.shapes.large,
        shadowElevation = 12.dp,
        modifier = modifier,
        content = content,
    )
}

/** Which glossy icon a dock entry shows. */
enum class DockGlyph { Home, Movies, Shows, Playlists, Search, Settings }

/** One destination in the Quick Launch dock. */
data class DockSpec(val id: String, val label: String, val glyph: DockGlyph)

/**
 * The webOS Quick Launch bar: round glossy icons on a translucent shelf at the
 * bottom edge, over the wallpaper. Focusing an icon raises a small label pill
 * above it (the d-pad stand-in for webOS's touch affordances); the current
 * destination keeps the selection-blue glow ring.
 */
@Composable
fun WebOsDock(
    tabs: List<DockSpec>,
    selectedId: String?,
    onSelect: (DockSpec) -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = LocalWebOsPalette.current
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    listOf(Color.Transparent, palette.ink.copy(alpha = 0.66f), palette.ink.copy(alpha = 0.88f)),
                ),
            ),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.Bottom,
            modifier = Modifier.padding(top = 4.dp, bottom = 8.dp),
        ) {
            tabs.forEach { tab ->
                DockIcon(
                    tab = tab,
                    selected = tab.id == selectedId,
                    onClick = { onSelect(tab) },
                )
            }
        }
    }
}

/** Per-glyph sphere tints, echoing the colorful webOS launcher icons. */
private fun glyphTint(glyph: DockGlyph): Color = when (glyph) {
    DockGlyph.Home -> Color(0xFFE8A33D)
    DockGlyph.Movies -> Color(0xFF3D8EDB)
    DockGlyph.Shows -> Color(0xFF43A074)
    DockGlyph.Playlists -> Color(0xFF8A6FC9)
    DockGlyph.Search -> Color(0xFF5B7E99)
    DockGlyph.Settings -> Color(0xFF6E6B66)
}

@Composable
private fun DockIcon(tab: DockSpec, selected: Boolean, onClick: () -> Unit) {
    val palette = LocalWebOsPalette.current
    var focused by remember { mutableStateOf(false) }
    val tint = glyphTint(tab.glyph)
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        // Reserved label slot so focus never shifts the shelf's layout.
        Box(Modifier.height(30.dp), contentAlignment = Alignment.Center) {
            if (focused) {
                Box(
                    Modifier
                        .clip(CircleShape)
                        .background(palette.charcoalHigh.copy(alpha = 0.92f))
                        .border(1.dp, Color.White.copy(alpha = 0.14f), CircleShape)
                        .padding(horizontal = 12.dp, vertical = 3.dp),
                ) {
                    Text(tab.label, style = MaterialTheme.typography.bodySmall, color = palette.warmWhite)
                }
            }
        }
        Spacer(Modifier.height(3.dp))
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(
                    Brush.verticalGradient(
                        listOf(tint.lighten(0.35f), tint, tint.darken(0.35f)),
                    ),
                )
                .border(
                    width = 2.dp,
                    color = when {
                        focused -> Color.White
                        selected -> palette.glow
                        else -> Color.White.copy(alpha = 0.18f)
                    },
                    shape = CircleShape,
                )
                .drawBehind {
                    // Glass highlight across the upper half of the sphere.
                    drawOval(
                        Brush.verticalGradient(
                            listOf(Color.White.copy(alpha = 0.50f), Color.White.copy(alpha = 0.04f)),
                            endY = size.height * 0.52f,
                        ),
                        topLeft = Offset(size.width * 0.14f, size.height * 0.05f),
                        size = Size(size.width * 0.72f, size.height * 0.46f),
                    )
                }
                .onFocusChanged { focused = it.isFocused }
                // The sphere is the accessible element; inner glyphs are decorative.
                .semantics { contentDescription = tab.label }
                .clickable { onClick() },
        ) {
            when (tab.glyph) {
                DockGlyph.Home -> Icon(Icons.Filled.Home, null, tint = Color.White, modifier = Modifier.size(26.dp))
                DockGlyph.Movies -> Icon(Icons.Filled.PlayArrow, null, tint = Color.White, modifier = Modifier.size(28.dp))
                DockGlyph.Shows -> TvGlyph()
                DockGlyph.Playlists -> Icon(Icons.AutoMirrored.Filled.List, null, tint = Color.White, modifier = Modifier.size(26.dp))
                DockGlyph.Search -> Icon(Icons.Filled.Search, null, tint = Color.White, modifier = Modifier.size(26.dp))
                DockGlyph.Settings -> Icon(Icons.Filled.Settings, null, tint = Color.White, modifier = Modifier.size(24.dp))
            }
        }
    }
}

/** TV-set glyph (screen plus stand); no matching icon ships in the core set. */
@Composable
private fun TvGlyph() {
    Canvas(Modifier.size(24.dp)) {
        val stroke = Stroke(width = size.width * 0.11f)
        drawRoundRect(
            color = Color.White,
            topLeft = Offset(0f, size.height * 0.08f),
            size = Size(size.width, size.height * 0.62f),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(size.width * 0.12f),
            style = stroke,
        )
        drawLine(
            color = Color.White,
            start = Offset(size.width * 0.28f, size.height * 0.92f),
            end = Offset(size.width * 0.72f, size.height * 0.92f),
            strokeWidth = stroke.width,
        )
    }
}

private fun Color.lighten(amount: Float): Color =
    Color(
        red + (1f - red) * amount,
        green + (1f - green) * amount,
        blue + (1f - blue) * amount,
        alpha,
    )

private fun Color.darken(amount: Float): Color =
    Color(red * (1f - amount), green * (1f - amount), blue * (1f - amount), alpha)

/** The webOS top-left app pill: small charcoal capsule with the context name. */
@Composable
fun WebOsHeaderPill(text: String, modifier: Modifier = Modifier) {
    val palette = LocalWebOsPalette.current
    Box(
        modifier = modifier
            .clip(CircleShape)
            .background(
                Brush.verticalGradient(listOf(palette.charcoalHigh, palette.charcoal)),
            )
            .border(1.dp, Color.White.copy(alpha = 0.08f), CircleShape)
            .padding(horizontal = 16.dp, vertical = 7.dp),
    ) {
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            color = palette.warmWhite,
        )
    }
}

/**
 * Full-bleed artwork backdrop under an ink scrim: the webOS wallpaper-behind-
 * cards effect. The image is requested tiny and scaled up, which gives a soft
 * blur on every API level (Fire OS 6 has no RenderEffect).
 */
@Composable
fun BlurBackdrop(imageUrl: String?, modifier: Modifier = Modifier) {
    val palette = LocalWebOsPalette.current
    Box(modifier) {
        if (imageUrl != null) {
            coil3.compose.AsyncImage(
                model = imageUrl,
                contentDescription = null,
                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                modifier = Modifier.matchParentSize(),
            )
        }
        Box(Modifier.matchParentSize().background(palette.ink.copy(alpha = 0.82f)))
    }
}

/**
 * A selectable pill for use on the light linen card surface, where the default
 * dark-theme chip colors would be unreadable. Selected pills fill with the
 * selection blue; focus adds the blue outline.
 */
@Composable
fun LinenChip(
    selected: Boolean,
    onClick: () -> Unit,
    label: String,
    modifier: Modifier = Modifier,
) {
    val palette = LocalWebOsPalette.current
    var focused by remember { mutableStateOf(false) }
    Box(
        modifier = modifier
            .clip(CircleShape)
            .background(
                if (selected) {
                    Brush.verticalGradient(listOf(palette.glow, palette.glow.copy(alpha = 0.8f)))
                } else {
                    Brush.verticalGradient(
                        listOf(Color.White.copy(alpha = 0.55f), Color.White.copy(alpha = 0.25f)),
                    )
                },
            )
            .border(
                width = if (focused) 2.dp else 1.dp,
                color = if (focused) palette.glow else palette.graphite.copy(alpha = 0.45f),
                shape = CircleShape,
            )
            .onFocusChanged { focused = it.isFocused }
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = if (selected) Color.White else palette.onLinen,
        )
    }
}

/**
 * webOS section header: label, hairline rule ending in a dot, as in the Pre's
 * email account groupings. Colors default to the dark backdrop; pass the
 * onLight variants when it sits on a linen card.
 */
@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    labelColor: Color = Color.Unspecified,
    ruleColor: Color = Color.Unspecified,
) {
    val palette = LocalWebOsPalette.current
    val label = labelColor.takeOrElse { MaterialTheme.colorScheme.onSurfaceVariant }
    val rule = ruleColor.takeOrElse { palette.graphite.copy(alpha = 0.6f) }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.fillMaxWidth().padding(end = 24.dp),
    ) {
        Text(
            title.uppercase(),
            style = MaterialTheme.typography.titleMedium,
            color = label,
        )
        Spacer(Modifier.width(12.dp))
        Box(Modifier.weight(1f).height(1.dp).background(rule))
        Box(Modifier.size(6.dp).clip(CircleShape).background(rule))
    }
}
