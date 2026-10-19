package dev.bettermint.plex.data

import dev.bettermint.plex.api.ConnectionSelector
import dev.bettermint.plex.api.PlexTvApi
import dev.bettermint.plex.api.ServerApi
import dev.bettermint.plex.api.model.PlexResource
import io.ktor.client.HttpClient

/** Server discovery: list account servers, pick the best connection, remember it. */
class ServerRepository(
    private val client: HttpClient,
    private val plexTv: PlexTvApi,
    private val authStore: AuthStore,
) {

    suspend fun listServers(): List<PlexResource> =
        plexTv.getResources().filter { it.isServer }

    /**
     * Probe the server's connections (local > remote > relay, 2s timeout each)
     * and persist the winner as the selected server.
     */
    suspend fun connect(server: PlexResource): ServerApi? {
        val best = ConnectionSelector.selectBest(server.connections) { connection ->
            val api = ServerApi(client, connection.uri, server.accessToken)
            api.getIdentity().machineIdentifier == server.clientIdentifier
        } ?: return null

        authStore.setSelectedServer(
            AuthStore.SelectedServer(
                machineId = server.clientIdentifier,
                name = server.name,
                uri = best.uri,
                accessToken = server.accessToken,
            ),
        )
        return ServerApi(client, best.uri, server.accessToken)
    }

    /** Rebuild the API for the cached selection, or null if nothing is cached. */
    suspend fun cachedServer(): ServerApi? {
        val saved = authStore.selectedServer() ?: return null
        return ServerApi(client, saved.uri, saved.accessToken)
    }
}
