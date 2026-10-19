package dev.bettermint.plex.api.model

import kotlinx.serialization.Serializable

/** POST/GET https://plex.tv/api/v2/pins */
@Serializable
data class Pin(
    val id: Long,
    val code: String,
    val authToken: String? = null,
    val expiresIn: Long? = null,
    val expiresAt: String? = null,
)

/** GET https://plex.tv/api/v2/user */
@Serializable
data class PlexUser(
    val id: Long,
    val uuid: String? = null,
    val username: String? = null,
    val title: String? = null,
    val email: String? = null,
    val thumb: String? = null,
)

/** One entry of GET https://plex.tv/api/v2/resources */
@Serializable
data class PlexResource(
    val name: String,
    val clientIdentifier: String,
    val provides: String = "",
    val accessToken: String? = null,
    val owned: Boolean = false,
    val presence: Boolean = false,
    val connections: List<PlexConnection> = emptyList(),
) {
    val isServer: Boolean get() = provides.split(",").contains("server")
}

@Serializable
data class PlexConnection(
    val protocol: String,
    val address: String,
    val port: Int,
    val uri: String,
    val local: Boolean = false,
    val relay: Boolean = false,
    val IPv6: Boolean = false,
)

/** GET https://plex.tv/api/v2/home/users */
@Serializable
data class HomeUsers(
    val id: Long? = null,
    val name: String? = null,
    val users: List<HomeUser> = emptyList(),
)

@Serializable
data class HomeUser(
    val id: Long,
    val uuid: String,
    val title: String,
    val username: String? = null,
    val admin: Boolean = false,
    val protected: Boolean = false,
    val thumb: String? = null,
)

/** POST https://plex.tv/api/v2/home/users/{uuid}/switch */
@Serializable
data class SwitchUserResponse(
    val authToken: String,
)
