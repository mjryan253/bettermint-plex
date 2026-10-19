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
    val key: String? = null,
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
    val originallyAvailableAt: String? = null,
    val contentRating: String? = null,
    val rating: Double? = null,
    val audienceRating: Double? = null,
    val parentRatingKey: String? = null,
    val parentIndex: Int? = null,
    val parentTitle: String? = null,
    val parentThumb: String? = null,
    val grandparentRatingKey: String? = null,
    val grandparentTitle: String? = null,
    val grandparentThumb: String? = null,
    val playlistType: String? = null,
    val smart: Boolean? = null,
    @SerialName("Extras") val extras: ExtrasContainer? = null,
)

@Serializable
data class ExtrasContainer(
    val size: Int = 0,
    @SerialName("Metadata") val items: List<Metadata> = emptyList(),
)

/** Paged listing: /library/sections/{key}/all, /children, /items, /collections, /playlists. */
@Serializable
data class MetadataResponse(
    @SerialName("MediaContainer") val mediaContainer: MetadataContainer,
)

@Serializable
data class MetadataContainer(
    val size: Int = 0,
    val totalSize: Int? = null,
    val offset: Int? = null,
    @SerialName("Metadata") val items: List<Metadata> = emptyList(),
)

/** Hub listings: /hubs, /hubs/search. */
@Serializable
data class HubsResponse(
    @SerialName("MediaContainer") val mediaContainer: HubsContainer,
)

@Serializable
data class HubsContainer(
    val size: Int = 0,
    @SerialName("Hub") val hubs: List<Hub> = emptyList(),
)

@Serializable
data class Hub(
    val title: String = "",
    val type: String? = null,
    val hubIdentifier: String? = null,
    val size: Int = 0,
    val more: Boolean = false,
    @SerialName("Metadata") val items: List<Metadata> = emptyList(),
)
