package dev.bettermint.plex.api.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Every PMS and provider response wraps its payload in a MediaContainer object. */
@Serializable
data class MediaContainer<T>(
    @SerialName("MediaContainer") val container: T,
)

@Serializable
data class Identity(
    val machineIdentifier: String,
)

/** GET {server}/library/sections */
@Serializable
data class SectionsContainer(
    @SerialName("Directory") val directories: List<LibrarySection> = emptyList(),
)

@Serializable
data class LibrarySection(
    val key: String,
    val title: String,
    val type: String,
)
