package dev.bettermint.plex.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import dev.bettermint.plex.app.ui.theme.LocalWebOsPalette

/** One destination in the bottom pill bar. */
data class TabSpec(val id: String, val label: String)

/**
 * The webOS bottom bar: a floating dark glossy pill holding the top-level
 * destinations. The selected tab sits in a blue rounded glow, the webOS
 * selection treatment; a focused-but-unselected tab gets a blue outline.
 */
@Composable
fun WebOsTabBar(
    tabs: List<TabSpec>,
    selectedId: String?,
    onSelect: (TabSpec) -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = LocalWebOsPalette.current
    Row(
        modifier = modifier.fillMaxWidth().padding(top = 8.dp, bottom = 10.dp),
        horizontalArrangement = Arrangement.Center,
    ) {
        Row(
            modifier = Modifier
                .clip(CircleShape)
                .background(
                    Brush.verticalGradient(
                        listOf(palette.charcoalHigh, palette.ink),
                    ),
                )
                .border(1.dp, Color.White.copy(alpha = 0.10f), CircleShape)
                .padding(5.dp)
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            tabs.forEach { tab ->
                var focused by remember { mutableStateOf(false) }
                val selected = tab.id == selectedId
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(
                            when {
                                selected -> Brush.verticalGradient(
                                    listOf(palette.glow, palette.glow.copy(alpha = 0.75f)),
                                )
                                focused -> Brush.verticalGradient(
                                    listOf(Color.White.copy(alpha = 0.14f), Color.Transparent),
                                )
                                else -> Brush.verticalGradient(
                                    listOf(Color.Transparent, Color.Transparent),
                                )
                            },
                        )
                        .border(
                            width = if (focused) 2.dp else 0.dp,
                            color = if (focused) palette.glow else Color.Transparent,
                            shape = CircleShape,
                        )
                        .onFocusChanged { focused = it.isFocused }
                        .clickable { onSelect(tab) }
                        .padding(horizontal = 20.dp, vertical = 10.dp),
                ) {
                    Text(
                        tab.label,
                        style = MaterialTheme.typography.labelLarge,
                        color = if (selected) Color.White else palette.warmWhite.copy(alpha = 0.85f),
                    )
                }
            }
        }
    }
}

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
 * webOS section header: uppercase label, hairline rule ending in a dot, as in
 * the Pre's email account groupings.
 */
@Composable
fun SectionHeader(title: String, modifier: Modifier = Modifier) {
    val palette = LocalWebOsPalette.current
    val rule = palette.graphite.copy(alpha = 0.6f)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.fillMaxWidth().padding(end = 24.dp),
    ) {
        Text(
            title.uppercase(),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(12.dp))
        Box(Modifier.weight(1f).height(1.dp).background(rule))
        Box(Modifier.size(6.dp).clip(CircleShape).background(rule))
    }
}
