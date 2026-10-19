package dev.bettermint.plex.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import coil3.compose.AsyncImage
import dev.bettermint.plex.api.model.Metadata
import dev.bettermint.plex.data.LibraryRepository
import kotlinx.coroutines.launch

private data class DetailData(
    val item: Metadata?,
    val children: List<Metadata>,
)

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
        Column(
            Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) { CircularProgressIndicator() }
        return
    }
    val item = loaded.item
    if (item == null) {
        Column(Modifier.fillMaxSize().padding(24.dp)) { Text("Item not found.") }
        return
    }

    val scope = rememberCoroutineScope()
    var showPlaylistPicker by remember { mutableStateOf(false) }
    var playlistMessage by remember { mutableStateOf<String?>(null) }

    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                AsyncImage(
                    model = library.imageUrl(item.posterPath(), POSTER_WIDTH_DP * 2, POSTER_HEIGHT_DP * 2),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.width(POSTER_WIDTH_DP.dp).height(POSTER_HEIGHT_DP.dp),
                )
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(item.displayTitle(), style = MaterialTheme.typography.headlineMedium)
                    item.displaySubtitle()?.let { Text(it, style = MaterialTheme.typography.titleMedium) }
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        item.contentRating?.let { Text(it) }
                        item.rating?.let { Text("Critics $it") }
                        item.audienceRating?.let { Text("Audience $it") }
                        item.duration?.let { Text("${it / 60000} min") }
                    }
                    item.summary?.takeIf { it.isNotBlank() }?.let {
                        Text(it, style = MaterialTheme.typography.bodyMedium, maxLines = 6)
                    }
                    if (item.type == "movie" || item.type == "episode") {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            AssistChip(
                                onClick = { showPlaylistPicker = true },
                                label = { Text("Add to playlist") },
                            )
                        }
                    }
                    playlistMessage?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                }
            }
        }

        if (loaded.children.isNotEmpty()) {
            item {
                Column {
                    Text(
                        if (item.type == "show") "Seasons" else "Episodes",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Spacer(Modifier.height(8.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(loaded.children, key = { it.ratingKey ?: it.title }) { child ->
                            PosterCard(
                                item = child,
                                library = library,
                                onClick = { nav.navigate(Routes.details(child.ratingKey.orEmpty())) },
                            )
                        }
                    }
                }
            }
        }

        val extras = item.extras?.items.orEmpty()
        if (extras.isNotEmpty()) {
            item {
                Column {
                    Text("Trailers & Extras", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(extras, key = { it.ratingKey ?: it.title }) { extra ->
                            PosterCard(
                                item = extra,
                                library = library,
                                // Playback arrives in M3; detail view until then.
                                onClick = { nav.navigate(Routes.details(extra.ratingKey.orEmpty())) },
                            )
                        }
                    }
                }
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
