package dev.bettermint.plex.data

import dev.bettermint.plex.api.PlaybackDecider
import dev.bettermint.plex.api.PlaybackDecider.Method
import dev.bettermint.plex.api.ServerApi
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
        val prefCap = if (local) quality.maxKbpsLocal else quality.maxKbpsRemote
        val cap = (overrideMaxKbps ?: prefCap).takeIf { it > 0 }

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

    /** Next method to try after [failed], or null when nothing is left. */
    fun fallbackAfter(failed: Method): Method? = when (failed) {
        Method.DirectPlay -> Method.DirectStream
        Method.DirectStream -> Method.Transcode
        Method.Transcode -> null
    }

    suspend fun stop(source: Source) {
        source.sessionId?.let { api.stopTranscode(it) }
    }

    companion object {
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
