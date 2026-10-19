package dev.bettermint.plex.app.player

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import dev.bettermint.plex.api.PlaybackDecider.Method
import dev.bettermint.plex.api.model.Metadata
import dev.bettermint.plex.app.ui.LoadingScreen
import dev.bettermint.plex.data.LibraryRepository
import dev.bettermint.plex.data.PlaybackRepository
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Player with a runtime fallback chain: whatever the decider picked, a fatal
 * player error steps down one method (DirectPlay -> DirectStream -> Transcode)
 * until nothing is left.
 */
@Composable
fun PlayerScreen(
    library: LibraryRepository,
    playback: PlaybackRepository,
    ratingKey: String,
    mediaIndex: Int,
    startMs: Long,
    audioStreamId: Long?,
    subtitleStreamId: Long?,
    maxKbpsOverride: Int?,
) {
    val item by produceState<Metadata?>(initialValue = null, ratingKey) {
        value = runCatching { library.details(ratingKey) }.getOrNull()
    }
    var forcedMethod by remember { mutableStateOf<Method?>(null) }
    var exhausted by remember { mutableStateOf(false) }

    val loaded = item ?: run {
        LoadingScreen()
        return
    }
    if (exhausted) {
        Text("Playback failed: no compatible method left for this file.")
        return
    }

    val source by produceState<PlaybackRepository.Source?>(
        initialValue = null,
        loaded,
        forcedMethod,
    ) {
        val selectedSub = loaded.media.getOrNull(mediaIndex)?.parts?.firstOrNull()
            ?.streams?.firstOrNull { it.id == subtitleStreamId }
        value = runCatching {
            playback.source(
                item = loaded,
                versionIndex = mediaIndex,
                startMs = startMs,
                tracks = PlaybackRepository.TrackSelection(
                    audioStreamId = audioStreamId,
                    subtitleStreamId = subtitleStreamId,
                    burnSubtitles = PlaybackRepository.needsBurn(selectedSub),
                ),
                overrideMaxKbps = maxKbpsOverride,
                forceMethod = forcedMethod,
            )
        }.getOrNull()
    }

    val active = source ?: run {
        LoadingScreen()
        return
    }

    val context = LocalContext.current
    val player = remember(active.url) {
        val selectedSub = loaded.media.getOrNull(mediaIndex)?.parts?.firstOrNull()
            ?.streams?.firstOrNull { it.id == subtitleStreamId }
        val mediaItem = MediaItem.Builder().setUri(active.url).apply {
            // External text subtitle sidecars ride along during direct play.
            if (active.method == Method.DirectPlay && selectedSub?.key != null) {
                val subUrl = library.api.directPlayUrl(selectedSub.key!!)
                val mime = when (selectedSub.codec?.lowercase()) {
                    "vtt", "webvtt" -> MimeTypes.TEXT_VTT
                    else -> MimeTypes.APPLICATION_SUBRIP
                }
                setSubtitleConfigurations(
                    listOf(
                        MediaItem.SubtitleConfiguration.Builder(android.net.Uri.parse(subUrl))
                            .setMimeType(mime)
                            .setLanguage(selectedSub.languageTag ?: selectedSub.language)
                            .setSelectionFlags(androidx.media3.common.C.SELECTION_FLAG_DEFAULT)
                            .build(),
                    ),
                )
            }
        }.build()

        ExoPlayer.Builder(context).build().apply {
            setMediaItem(mediaItem)
            addListener(object : Player.Listener {
                override fun onPlayerError(error: PlaybackException) {
                    val next = PlaybackRepository.fallbackAfter(active.method)
                    if (next != null) forcedMethod = next else exhausted = true
                }
            })
            prepare()
            if (active.seekToMs > 0) seekTo(active.seekToMs)
            playWhenReady = true
        }
    }
    // Report progress every 10s while playing, plus on pause, and scrobble once
    // past the watched threshold. The server uses this for Continue Watching,
    // resume offsets, and to show our session in /status/sessions.
    LaunchedEffect(player, loaded.ratingKey) {
        val ratingKey = loaded.ratingKey ?: return@LaunchedEffect
        var scrobbled = false
        // Last position read while the player was alive. The stopped ping uses
        // this instead of reading the player, which DisposableEffect may have
        // already released during composable teardown.
        var lastPos = active.seekToMs
        var lastDur = loaded.duration ?: 0L
        try {
            while (isActive) {
                val (pos, dur, playing) = withContext(Dispatchers.Main) {
                    Triple(player.currentPosition, player.duration, player.isPlaying)
                }
                lastPos = pos
                if (dur > 0) lastDur = dur
                playback.reportProgress(ratingKey, if (playing) "playing" else "paused", pos, lastDur)
                if (!scrobbled && PlaybackRepository.isWatched(pos, lastDur)) {
                    scrobbled = true
                    playback.markWatched(ratingKey)
                }
                delay(10_000)
            }
        } finally {
            withContext(NonCancellable) {
                runCatching { playback.reportProgress(ratingKey, "stopped", lastPos, lastDur) }
            }
        }
    }

    DisposableEffect(player) {
        onDispose {
            player.release()
            // Fire-and-forget: the composable's scope is gone by now and a
            // blocking network call here would freeze the main thread.
            @OptIn(DelicateCoroutinesApi::class)
            GlobalScope.launch(Dispatchers.IO) { runCatching { playback.stop(active) } }
        }
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
