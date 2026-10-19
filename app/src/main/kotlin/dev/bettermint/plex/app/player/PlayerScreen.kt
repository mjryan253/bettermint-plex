package dev.bettermint.plex.app.player

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import dev.bettermint.plex.api.model.Metadata
import dev.bettermint.plex.data.LibraryRepository
import dev.bettermint.plex.app.ui.LoadingScreen

/**
 * M3 player: direct play only. The chosen media version's first part streams
 * straight from the server; transcode fallback arrives in M4.
 */
@Composable
fun PlayerScreen(
    library: LibraryRepository,
    ratingKey: String,
    mediaIndex: Int,
    startMs: Long,
) {
    val item by produceState<Metadata?>(initialValue = null, ratingKey) {
        value = runCatching { library.details(ratingKey) }.getOrNull()
    }

    val loaded = item ?: run {
        LoadingScreen()
        return
    }
    val part = loaded.media.getOrNull(mediaIndex)?.parts?.firstOrNull()
        ?: loaded.media.firstOrNull()?.parts?.firstOrNull()
    if (part?.key == null) {
        Text("Nothing playable in this item.")
        return
    }

    val context = LocalContext.current
    val player = remember(part.key) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(library.api.directPlayUrl(part.key!!)))
            prepare()
            if (startMs > 0) seekTo(startMs)
            playWhenReady = true
        }
    }
    DisposableEffect(player) {
        onDispose { player.release() }
    }

    AndroidView(
        factory = { ctx ->
            PlayerView(ctx).apply {
                useController = true
                controllerShowTimeoutMs = 3500
                keepScreenOn = true
                isFocusable = true
                requestFocus()
            }
        },
        update = { view -> view.player = player },
        modifier = Modifier.fillMaxSize(),
    )
}
