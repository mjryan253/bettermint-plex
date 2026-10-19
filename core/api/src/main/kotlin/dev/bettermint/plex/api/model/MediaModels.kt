package dev.bettermint.plex.api.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * One item of any type the server returns (movie, show, season, episode,
 * collection, playlist, extra). Plex uses the same Metadata shape everywhere.
 */
@Serializable
data class Metadata(
    val ratingKey: String? = null,
    val guid: String? = null,
    val type: String? = null,
    val title: String = "",
    val summary: String? = null,
    val year: Int? = null,
    val index: Int? = null,
    val thumb: String? = null,
    val art: String? = null,
    val duration: Long? = null,
    val viewOffset: Long? = null,
    val viewCount: Int? = null,
    val leafCount: Int? = null,
    val viewedLeafCount: Int? = null,
    val childCount: Int? = null,
    val addedAt: Long? = null,
    val contentRating: String? = null,
    val rating: Double? = null,
    val audienceRating: Double? = null,
    val parentIndex: Int? = null,
    val parentTitle: String? = null,
    val parentThumb: String? = null,
    val grandparentTitle: String? = null,
    val grandparentThumb: String? = null,
    val playlistType: String? = null,
    val playQueueItemID: Long? = null,
    @SerialName("Extras") val extras: MetadataContainer? = null,
    @SerialName("Media") val media: List<MediaVersion> = emptyList(),
    @SerialName("Chapter") val chapters: List<Chapter> = emptyList(),
    @SerialName("Marker") val markers: List<Marker> = emptyList(),
)

/** Plex Pass intro/credit markers; type is "intro" or "credits". */
@Serializable
data class Marker(
    val type: String? = null,
    val startTimeOffset: Long? = null,
    val endTimeOffset: Long? = null,
)

/** One playable version of an item (e.g. the 4K file vs the 1080p file). */
@Serializable
data class MediaVersion(
    val bitrate: Int? = null,
    val videoResolution: String? = null,
    val videoCodec: String? = null,
    val audioCodec: String? = null,
    val container: String? = null,
    @SerialName("Part") val parts: List<MediaPart> = emptyList(),
) {
    /** Short label for a version picker, e.g. "1080p h264 mkv". */
    val label: String
        get() = listOfNotNull(
            videoResolution?.let { if (it.all(Char::isDigit)) "${it}p" else it },
            videoCodec,
            container,
        ).joinToString(" ").ifBlank { "Version" }
}

@Serializable
data class MediaPart(
    val key: String? = null,
    @SerialName("Stream") val streams: List<MediaStream> = emptyList(),
)

/** streamType: 1 = video, 2 = audio, 3 = subtitle. */
@Serializable
data class MediaStream(
    val id: Long,
    val streamType: Int,
    val codec: String? = null,
    val language: String? = null,
    val languageTag: String? = null,
    val displayTitle: String? = null,
    val extendedDisplayTitle: String? = null,
    val title: String? = null,
    val selected: Boolean = false,
    /** External subtitle sidecar key (e.g. /library/streams/123), null when embedded. */
    val key: String? = null,
) {
    val isAudio: Boolean get() = streamType == 2
    val isSubtitle: Boolean get() = streamType == 3
    val label: String get() = extendedDisplayTitle ?: displayTitle ?: title ?: language ?: "Track $id"
}

@Serializable
data class Chapter(
    val id: Long? = null,
    val index: Int? = null,
    val tag: String? = null,
    val startTimeOffset: Long? = null,
    val thumb: String? = null,
)

/**
 * Any list of items: paged section listings, children, collections, playlists,
 * play queues, and the Extras block nested in a detail response.
 */
@Serializable
data class MetadataContainer(
    val size: Int = 0,
    val totalSize: Int? = null,
    @SerialName("Metadata") val items: List<Metadata> = emptyList(),
)

/** Hub listings: /hubs, /hubs/search. */
@Serializable
data class HubsContainer(
    @SerialName("Hub") val hubs: List<Hub> = emptyList(),
)

@Serializable
data class Hub(
    val title: String = "",
    val type: String? = null,
    val hubIdentifier: String? = null,
    @SerialName("Metadata") val items: List<Metadata> = emptyList(),
)
