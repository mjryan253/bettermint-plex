package dev.bettermint.plex.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.FlowRowScope
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import coil3.compose.AsyncImage
import dev.bettermint.plex.api.model.Metadata
import dev.bettermint.plex.app.ui.theme.LocalWebOsPalette
import dev.bettermint.plex.data.LibraryRepository
import kotlinx.coroutines.launch

private data class DetailData(
    val item: Metadata?,
    val children: List<Metadata>,
)

private fun formatMs(ms: Long): String {
    val totalMinutes = ms / 60000
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m"
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DetailScreen(
    library: LibraryRepository,
    ratingKey: String,
    nav: NavHostController,
) {
    val data by produceState<DetailData?>(initialValue = null, ratingKey) {
        val item = runCatching { library.details(ratingKey) }.getOrNull()
        val children = if (item?.type == "show" || item?.type == "season") {
            runCatching { library.children(ratingKey) }.getOrDefault(emptyList())
        } else {
            emptyList()
        }
        value = DetailData(item, children)
    }

    val loaded = data
    if (loaded == null) {
        LoadingScreen()
        return
    }
    val item = loaded.item
    if (item == null) {
        Column(Modifier.fillMaxSize().padding(24.dp)) { Text("This item is no longer on the server.") }
        return
    }

    val scope = rememberCoroutineScope()
    var showPlaylistPicker by remember { mutableStateOf(false) }
    var playlistMessage by remember { mutableStateOf<String?>(null) }
    var versionIndex by remember(ratingKey) { mutableStateOf(0) }
    var audioId by remember(ratingKey) { mutableStateOf<Long?>(null) }
    var subId by remember(ratingKey) { mutableStateOf<Long?>(null) }
    var qualityOverride by remember(ratingKey) { mutableStateOf<Int?>(null) }

    val palette = LocalWebOsPalette.current

    fun play(startMs: Long) = nav.navigate(
        Routes.player(
            item.ratingKey.orEmpty(),
            versionIndex,
            startMs,
            audioId = audioId,
            subId = subId,
            maxKbps = qualityOverride,
        ),
    )

    BlurBackdrop(
        imageUrl = library.imageUrl(item.art ?: item.posterPath(), 64, 36),
        modifier = Modifier.fillMaxSize(),
    )

    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Spacer(Modifier.height(14.dp))
            // The webOS card: content floats on a warm linen surface over the
            // dimmed artwork, like an app card over the wallpaper.
            Surface(
                color = palette.linen,
                contentColor = palette.onLinen,
                shape = MaterialTheme.shapes.extraLarge,
                shadowElevation = 12.dp,
            ) {
                val poster: @Composable () -> Unit = {
                    AsyncImage(
                        model = library.imageUrl(item.posterPath(), POSTER_WIDTH_DP * 2, POSTER_HEIGHT_DP * 2),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .width(POSTER_WIDTH_DP.dp)
                            .height(POSTER_HEIGHT_DP.dp)
                            .clip(MaterialTheme.shapes.medium),
                    )
                }
                val info: @Composable () -> Unit = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(item.displayTitle(), style = MaterialTheme.typography.headlineSmall)
                        item.displaySubtitle()?.let {
                            Text(it, style = MaterialTheme.typography.bodyMedium, color = palette.graphite)
                        }
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            val meta = listOfNotNull(
                                item.contentRating,
                                item.duration?.let { "${it / 60000} min" },
                                item.rating?.let { "Critics $it" },
                                item.audienceRating?.let { "Audience $it" },
                            )
                            meta.forEach {
                                Text(it, style = MaterialTheme.typography.bodySmall, color = palette.graphite)
                            }
                        }
                        item.summary?.takeIf { it.isNotBlank() }?.let {
                            Text(
                                it,
                                style = MaterialTheme.typography.bodyLarge,
                                maxLines = 5,
                                modifier = Modifier.widthIn(max = 620.dp),
                            )
                        }
                        if (item.media.isNotEmpty()) {
                            if (item.media.size > 1) {
                                ChipRow {
                                    item.media.forEachIndexed { index, version ->
                                        LinenChip(
                                            selected = index == versionIndex,
                                            onClick = { versionIndex = index },
                                            label = version.label,
                                        )
                                    }
                                }
                            }
                            val streams = item.media.getOrNull(versionIndex)?.parts?.firstOrNull()
                                ?.streams.orEmpty()
                            val audioTracks = streams.filter { it.isAudio }
                            val subtitleTracks = streams.filter { it.isSubtitle }
                            if (audioTracks.size > 1) {
                                ChipRow {
                                    audioTracks.forEach { track ->
                                        LinenChip(
                                            selected = audioId == track.id || (audioId == null && track.selected),
                                            onClick = { audioId = track.id },
                                            label = track.label,
                                        )
                                    }
                                }
                            }
                            if (subtitleTracks.isNotEmpty()) {
                                ChipRow {
                                    LinenChip(
                                        selected = subId == null,
                                        onClick = { subId = null },
                                        label = "No subtitles",
                                    )
                                    subtitleTracks.forEach { track ->
                                        LinenChip(
                                            selected = subId == track.id,
                                            onClick = { subId = track.id },
                                            label = track.label,
                                        )
                                    }
                                }
                            }
                            ChipRow {
                                listOf(null, 8000, 4000, 2000).forEach { kbps ->
                                    LinenChip(
                                        selected = qualityOverride == kbps,
                                        onClick = { qualityOverride = kbps },
                                        label = if (kbps == null) "Auto quality" else "${kbps / 1000} Mbps",
                                    )
                                }
                            }
                            ChipRow(spacing = 10.dp) {
                                val resumeMs = item.viewOffset ?: 0L
                                if (resumeMs > 0) {
                                    Button(onClick = { play(resumeMs) }, modifier = Modifier.initialFocus(item.ratingKey)) {
                                        Text("Resume ${formatMs(resumeMs)}")
                                    }
                                    OutlinedButton(
                                        onClick = { play(0) },
                                        colors = ButtonDefaults.outlinedButtonColors(contentColor = palette.onLinen),
                                        border = BorderStroke(1.dp, palette.graphite),
                                    ) { Text("Start over") }
                                } else {
                                    Button(onClick = { play(0) }, modifier = Modifier.initialFocus(item.ratingKey)) {
                                        Text("Play")
                                    }
                                }
                                if (item.type == "movie" || item.type == "episode") {
                                    OutlinedButton(
                                        onClick = { showPlaylistPicker = true },
                                        colors = ButtonDefaults.outlinedButtonColors(contentColor = palette.onLinen),
                                        border = BorderStroke(1.dp, palette.graphite),
                                    ) {
                                        Text("Add to playlist")
                                    }
                                }
                            }
                        }
                        playlistMessage?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = palette.graphite) }
                    }
                }
                // Side-by-side on TV and tablets; stacked when the card (screen
                // minus the list's 24dp side padding) is under 560dp, too narrow
                // for a poster plus a readable text column.
                Box {
                    if (LocalConfiguration.current.screenWidthDp < 608) {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                            modifier = Modifier.padding(20.dp),
                        ) {
                            poster()
                            info()
                        }
                    } else {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(24.dp),
                            modifier = Modifier.padding(20.dp),
                        ) {
                            poster()
                            info()
                        }
                    }
                }
            }
        }

        if (loaded.children.isNotEmpty()) {
            item {
                PosterRow(
                    title = if (item.type == "show") "Seasons" else "Episodes",
                    items = loaded.children,
                    library = library,
                    onItem = { nav.navigate(Routes.details(it.ratingKey.orEmpty())) },
                )
            }
        }

        if (item.chapters.isNotEmpty()) {
            item {
                Column {
                    SectionHeader("Chapters")
                    Spacer(Modifier.height(10.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(item.chapters, key = { it.id ?: (it.startTimeOffset ?: 0L) }) { chapter ->
                            Card(onClick = { play(chapter.startTimeOffset ?: 0L) }) {
                                AsyncImage(
                                    model = library.imageUrl(chapter.thumb, 240, 135),
                                    contentDescription = chapter.tag,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.width(160.dp).height(90.dp),
                                )
                                Text(
                                    chapter.tag ?: "Chapter ${chapter.index ?: ""}".trim() +
                                        " ${formatMs(chapter.startTimeOffset ?: 0L)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.padding(6.dp),
                                )
                            }
                        }
                    }
                }
            }
        }

        val extras = item.extras?.items.orEmpty()
        if (extras.isNotEmpty()) {
            item {
                PosterRow(
                    title = "Trailers & Extras",
                    items = extras,
                    library = library,
                    onItem = { nav.navigate(Routes.player(it.ratingKey.orEmpty())) },
                )
            }
        }

        item { Spacer(Modifier.height(24.dp)) }
    }

    if (showPlaylistPicker) {
        val playlists by produceState<List<Metadata>?>(initialValue = null) {
            value = runCatching { library.playlists() }.getOrDefault(emptyList())
        }
        AlertDialog(
            onDismissRequest = { showPlaylistPicker = false },
            title = { Text("Add to playlist") },
            text = {
                when (val list = playlists) {
                    null -> CircularProgressIndicator(Modifier.size(32.dp))
                    else -> Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        if (list.isEmpty()) Text("No video playlists on this server.")
                        list.forEach { playlist ->
                            TextButton(onClick = {
                                showPlaylistPicker = false
                                scope.launch {
                                    playlistMessage = runCatching {
                                        library.addToPlaylist(
                                            playlist.ratingKey.orEmpty(),
                                            item.ratingKey.orEmpty(),
                                        )
                                    }.fold(
                                        onSuccess = { "Added to ${playlist.title}" },
                                        onFailure = { "Failed: ${it.message}" },
                                    )
                                }
                            }) { Text(playlist.title) }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showPlaylistPicker = false }) { Text("Cancel") }
            },
        )
    }
}

/** A wrapping row of chips or buttons on the detail card. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChipRow(spacing: Dp = 8.dp, content: @Composable FlowRowScope.() -> Unit) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(spacing),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        content = content,
    )
}
