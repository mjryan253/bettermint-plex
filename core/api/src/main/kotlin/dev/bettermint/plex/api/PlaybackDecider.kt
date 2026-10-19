package dev.bettermint.plex.api

import dev.bettermint.plex.api.model.MediaVersion

/**
 * Client-side playback decision. This server's /decision endpoint rejects all
 * known parameter shapes with 400 (see docs/memory.md), and the server runs its
 * own media-decision engine on start.m3u8 anyway, so the client picks a method
 * from its own capabilities and lets the HLS endpoint settle the rest.
 */
object PlaybackDecider {

    data class DeviceCapabilities(
        val containers: Set<String>,
        val videoCodecs: Set<String>,
        val audioCodecs: Set<String>,
    ) {
        companion object {
            /** Conservative Fire TV / Android TV defaults (AFTMM-class hardware). */
            val fireTv = DeviceCapabilities(
                containers = setOf("mp4", "mkv", "webm", "mov"),
                videoCodecs = setOf("h264", "hevc", "mpeg4", "vp9"),
                audioCodecs = setOf("aac", "ac3", "eac3", "mp3", "opus", "flac", "vorbis"),
            )
        }
    }

    sealed interface Method {
        /** Play the file bytes as-is. */
        data object DirectPlay : Method

        /** HLS with video copied, audio/container converted (directStream=1). */
        data object DirectStream : Method

        /** Full HLS transcode (directStream=0). */
        data object Transcode : Method
    }

    fun decide(
        version: MediaVersion,
        caps: DeviceCapabilities,
        maxVideoBitrateKbps: Int? = null,
        mustBurnSubtitles: Boolean = false,
    ): Method {
        val bitrateOk = maxVideoBitrateKbps == null ||
            (version.bitrate != null && version.bitrate <= maxVideoBitrateKbps)
        val videoOk = version.videoCodec?.lowercase() in caps.videoCodecs
        val audioOk = version.audioCodec?.lowercase() in caps.audioCodecs
        val containerOk = version.container?.lowercase() in caps.containers

        return when {
            // Burned subtitles are rendered server-side, so the video must be re-encoded.
            mustBurnSubtitles -> Method.Transcode
            !bitrateOk -> Method.Transcode
            videoOk && audioOk && containerOk -> Method.DirectPlay
            videoOk -> Method.DirectStream
            else -> Method.Transcode
        }
    }
}
