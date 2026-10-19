package dev.bettermint.plex.api.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** PMS responses wrap everything in a MediaContainer object. */
@Serializable
data class IdentityResponse(
    @SerialName("MediaContainer") val mediaContainer: Identity,
)

@Serializable
data class Identity(
    val machineIdentifier: String,
    val version: String? = null,
)

/** GET {server}/library/sections */
@Serializable
data class SectionsResponse(
    @SerialName("MediaContainer") val mediaContainer: SectionsContainer,
)

@Serializable
data class SectionsContainer(
    val size: Int = 0,
    @SerialName("Directory") val directories: List<LibrarySection> = emptyList(),
)

@Serializable
data class LibrarySection(
    val key: String,
    val title: String,
    val type: String,
    val uuid: String? = null,
    val agent: String? = null,
    val hidden: Int = 0,
)
