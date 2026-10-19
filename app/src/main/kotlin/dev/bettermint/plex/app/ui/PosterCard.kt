package dev.bettermint.plex.app.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import dev.bettermint.plex.api.model.Metadata
import dev.bettermint.plex.data.LibraryRepository

const val POSTER_WIDTH_DP = 150
const val POSTER_HEIGHT_DP = 225

/** Display line for an item: episodes show their show, everything else its title. */
fun Metadata.displayTitle(): String =
    if (type == "episode" && !grandparentTitle.isNullOrBlank()) grandparentTitle!! else title

fun Metadata.displaySubtitle(): String? = when (type) {
    "episode" -> buildString {
        parentIndex?.let { append("S$it") }
        index?.let { append(if (isEmpty()) "E$it" else " E$it") }
        if (isNotEmpty()) append(" ")
        append(title)
    }
    "season" -> parentTitle
    else -> year?.toString()
}

/** Poster image choice: episodes look better with the show poster. */
fun Metadata.posterPath(): String? =
    if (type == "episode") grandparentThumb ?: thumb else thumb ?: parentThumb

@Composable
fun PosterCard(
    item: Metadata,
    library: LibraryRepository,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(onClick = onClick, modifier = modifier.width(POSTER_WIDTH_DP.dp)) {
        AsyncImage(
            model = library.imageUrl(item.posterPath(), POSTER_WIDTH_DP * 2, POSTER_HEIGHT_DP * 2),
            contentDescription = item.displayTitle(),
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxWidth().aspectRatio(2f / 3f),
        )
        Column(Modifier.padding(8.dp)) {
            Text(
                item.displayTitle(),
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            item.displaySubtitle()?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
