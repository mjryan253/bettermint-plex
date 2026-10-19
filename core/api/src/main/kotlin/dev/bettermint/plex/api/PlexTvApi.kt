package dev.bettermint.plex.api

import dev.bettermint.plex.api.model.HomeUsers
import dev.bettermint.plex.api.model.Metadata
import dev.bettermint.plex.api.model.MetadataResponse
import dev.bettermint.plex.api.model.Pin
import dev.bettermint.plex.api.model.PlexResource
import dev.bettermint.plex.api.model.SwitchUserResponse
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess

/** plex.tv account endpoints (PIN auth, resources, Plex Home). */
class PlexTvApi(
    private val client: HttpClient,
    private val baseUrl: String = "https://plex.tv",
    private val providerBaseUrl: String = "https://metadata.provider.plex.tv",
) {

    /**
     * Start the link flow. Non-strong pins return the short 4-character code the
     * user types at plex.tv/link (strong pins return a long code meant only for
     * the app.plex.tv/auth URL flow).
     */
    suspend fun createPin(): Pin =
        client.post("$baseUrl/api/v2/pins").bodyOrThrow()

    /** Poll until [Pin.authToken] is non-null (user completed linking) or the pin expires. */
    suspend fun getPin(id: Long): Pin =
        client.get("$baseUrl/api/v2/pins/$id").bodyOrThrow()

    /** All devices on the account; filter with [PlexResource.isServer] for servers. */
    suspend fun getResources(): List<PlexResource> =
        client.get("$baseUrl/api/v2/resources") {
            parameter("includeHttps", "1")
            parameter("includeRelay", "1")
        }.bodyOrThrow()

    suspend fun getHomeUsers(): HomeUsers =
        client.get("$baseUrl/api/v2/home/users").bodyOrThrow()

    /** Returns the switched-to user's account token. [pin] only for protected users. */
    suspend fun switchHomeUser(uuid: String, pin: String? = null): SwitchUserResponse =
        client.post("$baseUrl/api/v2/home/users/$uuid/switch") {
            if (pin != null) parameter("pin", pin)
        }.bodyOrThrow()

    /**
     * Canonical metadata from the Plex Discover provider for a `plex://` guid.
     * This is where Plex Web gets clean titles when a server item's own title
     * is stale (e.g. still the filename). Returns null for non-plex guids.
     */
    suspend fun getCanonicalMetadata(plexGuid: String): Metadata? {
        val id = plexGuid.substringAfter("plex://", "").substringAfterLast('/')
        if (id.isBlank()) return null
        return client.get("$providerBaseUrl/library/metadata/$id")
            .bodyOrThrow<MetadataResponse>().mediaContainer.items.firstOrNull()
    }
}

internal suspend inline fun <reified T> HttpResponse.bodyOrThrow(): T {
    if (!status.isSuccess()) throw PlexApiException(status.value, bodyAsTextSafe())
    return body()
}

internal suspend fun HttpResponse.bodyAsTextSafe(): String =
    runCatching { bodyAsText() }.getOrDefault("")

class PlexApiException(val httpStatus: Int, body: String) :
    Exception("Plex API error $httpStatus: ${body.take(500)}")
