package dev.bettermint.plex.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.bettermint.plex.api.model.Metadata
import dev.bettermint.plex.data.LibraryRepository

/** Simple non-paged poster grid used for collections, playlists and their contents. */
@Composable
fun MetadataListScreen(
    title: String,
    load: suspend () -> List<Metadata>,
    library: LibraryRepository,
    onItem: (Metadata) -> Unit,
) {
    val items by produceState<List<Metadata>?>(initialValue = null) {
        value = runCatching { load() }.getOrDefault(emptyList())
    }

    CardPage(title) {
        when (val list = items) {
            null -> Text("Loading...")
            else -> {
                if (list.isEmpty()) Text("Nothing here yet. Items you add in Plex appear here.")
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = POSTER_WIDTH_DP.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    itemsIndexed(list, key = { _, it -> it.ratingKey ?: it.title }) { index, item ->
                        PosterCard(
                            item = item,
                            library = library,
                            onClick = { onItem(item) },
                            modifier = if (index == 0) Modifier.initialFocus() else Modifier,
                        )
                    }
                }
            }
        }
    }
}
