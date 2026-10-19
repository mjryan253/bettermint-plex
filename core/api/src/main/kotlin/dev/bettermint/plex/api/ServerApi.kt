package dev.bettermint.plex.api

import dev.bettermint.plex.api.model.Identity
import dev.bettermint.plex.api.model.IdentityResponse
import dev.bettermint.plex.api.model.LibrarySection
import dev.bettermint.plex.api.model.SectionsResponse
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header

/**
 * Plex Media Server endpoints. [baseUrl] is the chosen connection URI,
 * [accessToken] the per-server token from the resources listing.
 */
class ServerApi(
    private val client: HttpClient,
    val baseUrl: String,
    private val accessToken: String?,
) {

    /** Unauthenticated; used to probe connection candidates. */
    suspend fun getIdentity(): Identity =
        client.get("$baseUrl/identity").bodyOrThrow<IdentityResponse>().mediaContainer

    suspend fun getLibrarySections(): List<LibrarySection> =
        get("$baseUrl/library/sections").bodyOrThrow<SectionsResponse>().mediaContainer.directories

    private suspend fun get(url: String) = client.get(url) {
        accessToken?.let { header("X-Plex-Token", it) }
    }
}
