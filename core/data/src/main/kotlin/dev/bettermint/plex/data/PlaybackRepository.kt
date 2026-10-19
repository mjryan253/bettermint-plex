package dev.bettermint.plex.data

import dev.bettermint.plex.api.PlaybackDecider
import dev.bettermint.plex.api.PlaybackDecider.Method
import dev.bettermint.plex.api.ServerApi
import dev.bettermint.plex.api.model.Marker
import dev.bettermint.plex.api.model.MediaStream
import dev.bettermint.plex.api.model.Metadata
import java.util.UUID

/**
 * Chooses how to play an item (direct play, direct stream, transcode) and
 * builds the stream URL. Also owns the fallback chain used when a method
 * fails at runtime: DirectPlay -> DirectStream -> Transcode.
 */
class PlaybackRepository(
    private val api: ServerApi,
    private val authStore: AuthStore,
    private val caps: PlaybackDecider.DeviceCapabilities = PlaybackDecider.DeviceCapabilities.fireTv,
) {

    data class Source(
        val url: String,
        val method: Method,
        val sessionId: String?,
        /** Position the PLAYER must seek to; 0 when the server already offsets. */
        val seekToMs: Long,
    )

    data class TrackSelection(
        val audioStreamId: Long? = null,
        val subtitleStreamId: Long? = null,
        /** Subtitle needs server-side burning (unrenderable format, e.g. ASS). */
        val burnSubtitles: Boolean = false,
    )

    suspend fun source(
        item: Metadata,
        versionIndex: Int,
        startMs: Long,
        tracks: TrackSelection = TrackSelection(),
        overrideMaxKbps: Int? = null,
        forceMethod: Method? = null,
    ): Source? {
        val version = item.media.getOrNull(versionIndex) ?: item.media.firstOrNull() ?: return null
        val ratingKey = item.ratingKey ?: return null

        val quality = authStore.qualityPrefs()
        val local = authStore.selectedServer()?.localConnection != false
        val cap = resolveCap(overrideMaxKbps, quality.maxKbpsLocal, quality.maxKbpsRemote, local)

        val method = forceMethod ?: PlaybackDecider.decide(version, caps, cap, tracks.burnSubtitles)

        return when (method) {
            Method.DirectPlay -> {
                val partKey = version.parts.firstOrNull()?.key ?: return null
                Source(api.directPlayUrl(partKey), method, sessionId = null, seekToMs = startMs)
            }
            Method.DirectStream, Method.Transcode -> {
                val sessionId = UUID.randomUUID().toString()
                val url = api.hlsStartUrl(
                    ratingKey = ratingKey,
                    sessionId = sessionId,
                    mediaIndex = versionIndex.coerceAtMost(item.media.lastIndex).coerceAtLeast(0),
                    directStream = method == Method.DirectStream,
                    maxVideoBitrateKbps = cap,
                    audioStreamId = tracks.audioStreamId,
                    subtitleStreamId = tracks.subtitleStreamId,
                    burnSubtitles = tracks.burnSubtitles,
                )
                // Plex serves a full seekable HLS playlist; the player seeks and
                // the server transcodes from the requested segment (fastSeek).
                Source(url, method, sessionId, seekToMs = startMs)
            }
        }
    }

    suspend fun stop(source: Source) {
        source.sessionId?.let { api.stopTranscode(it) }
    }

    suspend fun reportProgress(
        ratingKey: String,
        state: String,
        timeMs: Long,
        durationMs: Long,
        playQueueItemId: Long? = null,
    ) = api.timeline(ratingKey, state, timeMs, durationMs, playQueueItemId)

    suspend fun markWatched(ratingKey: String) = api.scrobble(ratingKey)

    /** Continuous play queue for autoplay-next, or null if unavailable. */
    suspend fun playQueue(ratingKey: String): List<Metadata> {
        val machineId = authStore.selectedServer()?.machineId ?: return emptyList()
        return runCatching { api.createPlayQueue(machineId, ratingKey).items }.getOrDefault(emptyList())
    }

    companion object {
        /** The item that follows [afterRatingKey] in the queue, or null at the end. */
        fun nextInQueue(items: List<Metadata>, afterRatingKey: String): Metadata? {
            val index = items.indexOfFirst { it.ratingKey == afterRatingKey }
            return items.getOrNull(index + 1)?.takeIf { index >= 0 }
        }

        /** The marker of [type] (intro/credits) whose window contains [posMs], or null. */
        fun markerAt(markers: List<Marker>, type: String, posMs: Long): Marker? =
            markers.firstOrNull { m ->
                val start = m.startTimeOffset
                val end = m.endTimeOffset
                m.type == type && start != null && end != null && end > start && posMs >= start && posMs < end
            }

        const val WATCHED_FRACTION = 0.9

        /** True once playback has passed the watched threshold. */
        fun isWatched(timeMs: Long, durationMs: Long): Boolean =
            durationMs > 0 && timeMs >= durationMs * WATCHED_FRACTION

        /** Next method to try after [failed], or null when nothing is left. */
        fun fallbackAfter(failed: Method): Method? = when (failed) {
            Method.DirectPlay -> Method.DirectStream
            Method.DirectStream -> Method.Transcode
            Method.Transcode -> null
        }

        /**
         * Effective video bitrate cap in kbps, or null for original quality.
         * An explicit per-play override wins; otherwise the local or remote
         * preset applies by connection. 0 in any of these means "no cap".
         */
        fun resolveCap(overrideKbps: Int?, localPrefKbps: Int, remotePrefKbps: Int, isLocal: Boolean): Int? {
            val prefCap = if (isLocal) localPrefKbps else remotePrefKbps
            return (overrideKbps ?: prefCap).takeIf { it > 0 }
        }

        /** Subtitle formats Media3 renders during direct play; others burn in. */
        private val renderableSubtitles = setOf("srt", "subrip", "ass", "ssa", "vtt", "webvtt", "pgs", "hdmv_pgs_subtitle", "mov_text", "tx3g")

        fun needsBurn(subtitle: MediaStream?): Boolean {
            if (subtitle == null) return false
            val codec = subtitle.codec?.lowercase() ?: return true
            // ASS/SSA render only once ass-media is integrated; burn for now.
            if (codec in setOf("ass", "ssa")) return true
            return codec !in renderableSubtitles
        }
    }
}
