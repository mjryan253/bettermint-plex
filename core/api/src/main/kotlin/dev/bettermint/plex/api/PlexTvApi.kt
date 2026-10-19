package dev.bettermint.plex.api

import dev.bettermint.plex.api.model.HomeUsers
import dev.bettermint.plex.api.model.Pin
import dev.bettermint.plex.api.model.PlexResource
import dev.bettermint.plex.api.model.PlexUser
import dev.bettermint.plex.api.model.SwitchUserResponse
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.http.isSuccess

/** plex.tv account endpoints (PIN auth, resources, Plex Home). */
class PlexTvApi(private val client: HttpClient, private val baseUrl: String = "https://plex.tv") {

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

    /**
     * Token validity check. 200 → valid, 401 → invalid (discard token).
     * Anything else is a transient error, NOT proof the token is bad.
     */
    suspend fun getUser(): TokenCheck {
        val response = client.get("$baseUrl/api/v2/user")
        return when {
            response.status.isSuccess() -> TokenCheck.Valid(response.body())
            response.status == HttpStatusCode.Unauthorized -> TokenCheck.Invalid
            else -> TokenCheck.Error(response.status.value)
        }
    }

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
}

sealed interface TokenCheck {
    data class Valid(val user: PlexUser) : TokenCheck
    data object Invalid : TokenCheck
    data class Error(val httpStatus: Int) : TokenCheck
}

internal suspend inline fun <reified T> HttpResponse.bodyOrThrow(): T {
    if (!status.isSuccess()) throw PlexApiException(status.value, bodyAsTextSafe())
    return body()
}

internal suspend fun HttpResponse.bodyAsTextSafe(): String =
    runCatching { bodyAsText() }.getOrDefault("")

class PlexApiException(val httpStatus: Int, body: String) :
    Exception("Plex API error $httpStatus: ${body.take(500)}")
