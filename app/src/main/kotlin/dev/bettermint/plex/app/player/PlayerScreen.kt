package dev.bettermint.plex.app.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil3.compose.AsyncImage
import dev.bettermint.plex.api.PlaybackDecider.Method
import dev.bettermint.plex.api.model.Metadata
import dev.bettermint.plex.app.ui.LoadingScreen
import dev.bettermint.plex.app.ui.displaySubtitle
import dev.bettermint.plex.app.ui.displayTitle
import dev.bettermint.plex.app.ui.initialFocus
import dev.bettermint.plex.app.ui.posterPath
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

private const val NEXT_UP_SECONDS = 12

/**
 * Plays an item and, at natural end, advances through the server play queue
 * with a next-up countdown. A fatal player error steps the method down
 * (DirectPlay -> DirectStream -> Transcode) until nothing is left.
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
    // The item currently playing. Advances as autoplay moves through the queue;
    // track/version overrides only apply to the first (user-chosen) item.
    var currentKey by remember { mutableStateOf(ratingKey) }
    var currentStartMs by remember { mutableStateOf(startMs) }
    var currentMediaIndex by remember { mutableStateOf(mediaIndex) }
    var currentAudioId by remember { mutableStateOf(audioStreamId) }
    var currentSubId by remember { mutableStateOf(subtitleStreamId) }
    var forcedMethod by remember(currentKey) { mutableStateOf<Method?>(null) }
    var exhausted by remember(currentKey) { mutableStateOf(false) }
    var nextUp by remember { mutableStateOf<Metadata?>(null) }

    val queue by produceState<List<Metadata>>(emptyList(), ratingKey) {
        value = playback.playQueue(ratingKey)
    }
    val item by produceState<Metadata?>(initialValue = null, currentKey) {
        value = runCatching { library.details(currentKey) }.getOrNull()
    }

    val loaded = item ?: run {
        LoadingScreen()
        return
    }
    if (exhausted) {
        Text("Playback failed for this file. Try a lower quality from its details page.")
        return
    }

    val selectedSub = loaded.media.getOrNull(currentMediaIndex)?.parts?.firstOrNull()
        ?.streams?.firstOrNull { it.id == currentSubId }

    val source by produceState<PlaybackRepository.Source?>(
        initialValue = null,
        loaded,
        forcedMethod,
        currentMediaIndex,
        currentAudioId,
        currentSubId,
    ) {
        value = runCatching {
            playback.source(
                item = loaded,
                versionIndex = currentMediaIndex,
                startMs = currentStartMs,
                tracks = PlaybackRepository.TrackSelection(
                    audioStreamId = currentAudioId,
                    subtitleStreamId = currentSubId,
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
        val mediaItem = MediaItem.Builder().setUri(active.url).apply {
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

                override fun onPlaybackStateChanged(state: Int) {
                    // Only advance from the item this player was built for, so a
                    // late STATE_ENDED from the previous item cannot skip ahead.
                    if (state == Player.STATE_ENDED && loaded.ratingKey == currentKey) {
                        nextUp = PlaybackRepository.nextInQueue(queue, currentKey)
                    }
                }
            })
            prepare()
            if (active.seekToMs > 0) seekTo(active.seekToMs)
            playWhenReady = true
        }
    }

    LaunchedEffect(player, currentKey) {
        val key = loaded.ratingKey ?: return@LaunchedEffect
        var scrobbled = false
        var lastPos = active.seekToMs
        var lastDur = loaded.duration ?: 0L
        // Read per tick: the play queue loads separately and may arrive after playback starts.
        fun playQueueItemId() = queue.firstOrNull { it.ratingKey == key }?.playQueueItemID
        try {
            while (isActive) {
                val pos = player.currentPosition
                val dur = player.duration
                lastPos = pos
                if (dur > 0) lastDur = dur
                val state = if (player.isPlaying) "playing" else "paused"
                playback.reportProgress(key, state, pos, lastDur, playQueueItemId())
                if (!scrobbled && PlaybackRepository.isWatched(pos, lastDur)) {
                    scrobbled = true
                    playback.markWatched(key)
                }
                delay(10_000)
            }
        } finally {
            withContext(NonCancellable) {
                runCatching { playback.reportProgress(key, "stopped", lastPos, lastDur, playQueueItemId()) }
            }
        }
    }

    // Fine-grained poll (Plex Pass markers): show Skip Intro inside the intro
    // window, and start the next-up prompt early once credits begin.
    var skipTarget by remember(currentKey) { mutableStateOf<Long?>(null) }
    LaunchedEffect(player, currentKey, loaded.markers) {
        val markers = loaded.markers
        if (markers.isEmpty()) return@LaunchedEffect
        while (isActive) {
            // During an autoplay advance, currentKey moves to the next item
            // before its details/player load, so the player here may still be
            // the previous item sitting in its credits. Only act when the loaded
            // item is the current target, or we would prompt the wrong next-up.
            if (loaded.ratingKey != currentKey) {
                delay(750)
                continue
            }
            val pos = player.currentPosition
            skipTarget = PlaybackRepository.markerAt(markers, "intro", pos)?.endTimeOffset
            if (nextUp == null && PlaybackRepository.markerAt(markers, "credits", pos) != null) {
                PlaybackRepository.nextInQueue(queue, currentKey)?.let { nextUp = it }
            }
            delay(750)
        }
    }

    DisposableEffect(player) {
        onDispose {
            player.release()
            @OptIn(DelicateCoroutinesApi::class)
            GlobalScope.launch(Dispatchers.IO) { runCatching { playback.stop(active) } }
        }
    }

    AndroidView(
        factory = { ctx ->
            PlayerView(ctx).apply {
                // Letterbox on black; the app wallpaper is not drawn under video.
                setBackgroundColor(android.graphics.Color.BLACK)
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

    // Skip Intro sits bottom-end over the video. Touch works now; TV d-pad focus
    // over the PlayerView is polished in M6.
    skipTarget?.let { target ->
        androidx.compose.foundation.layout.Box(
            modifier = Modifier.fillMaxSize().padding(48.dp),
            contentAlignment = Alignment.BottomEnd,
        ) {
            Button(onClick = { player.seekTo(target) }, modifier = Modifier.initialFocus(target)) {
                Text("Skip Intro")
            }
        }
    }

    fun advanceTo(next: Metadata) {
        currentKey = next.ratingKey ?: return
        currentStartMs = 0
        currentMediaIndex = 0
        currentAudioId = null
        currentSubId = null
        nextUp = null
    }

    nextUp?.let { next ->
        NextUpOverlay(
            next = next,
            library = library,
            onPlayNow = { advanceTo(next) },
            onCancel = { nextUp = null },
        )
    }
}

@Composable
private fun NextUpOverlay(
    next: Metadata,
    library: LibraryRepository,
    onPlayNow: () -> Unit,
    onCancel: () -> Unit,
) {
    var seconds by remember(next.ratingKey) { mutableStateOf(NEXT_UP_SECONDS) }
    LaunchedEffect(next.ratingKey) {
        while (seconds > 0) {
            delay(1000)
            seconds -= 1
        }
        onPlayNow()
    }

    // The webOS panel treatment: a rounded charcoal card centered over a scrim,
    // like the Pre's context menus.
    val palette = dev.bettermint.plex.app.ui.theme.LocalWebOsPalette.current
    Column(
        modifier = Modifier.fillMaxSize().background(Color(0x99000000)).padding(48.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        androidx.compose.material3.Surface(
            color = palette.charcoal,
            contentColor = palette.warmWhite,
            shape = MaterialTheme.shapes.large,
            border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.10f)),
            shadowElevation = 16.dp,
        ) {
            Column(
                modifier = Modifier.padding(28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    "Up next in ${seconds}s",
                    style = MaterialTheme.typography.titleMedium,
                    color = palette.warmWhite.copy(alpha = 0.8f),
                )
                Spacer(Modifier.height(16.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    AsyncImage(
                        model = library.imageUrl(next.posterPath(), 200, 300),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .width(100.dp)
                            .height(150.dp)
                            .clip(MaterialTheme.shapes.small),
                    )
                    Column {
                        Text(next.displayTitle(), style = MaterialTheme.typography.headlineSmall)
                        next.displaySubtitle()?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
                    }
                }
                Spacer(Modifier.height(24.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.wrapContentSize()) {
                    Button(onClick = onPlayNow, modifier = Modifier.initialFocus(next.ratingKey)) { Text("Play now") }
                    OutlinedButton(onClick = onCancel) { Text("Cancel") }
                }
            }
        }
    }
}
