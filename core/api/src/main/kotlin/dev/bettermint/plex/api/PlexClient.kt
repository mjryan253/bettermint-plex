package dev.bettermint.plex.api

import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.api.createClientPlugin
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.plugins.logging.SIMPLE
import io.ktor.http.HttpHeaders
import kotlinx.serialization.json.Json

/**
 * Shared Ktor client carrying the full X-Plex-* header set on every request.
 * All Plex traffic (plex.tv and PMS) must go through this client so the headers
 * are never absent.
 */
object PlexClient {

    val json: Json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    fun create(
        deviceInfo: PlexDeviceInfo,
        tokenProvider: () -> String?,
        engine: HttpClientEngine? = null,
        logging: Boolean = false,
    ): HttpClient {
        val plexHeaders = createClientPlugin("PlexHeaders") {
            onRequest { request, _ ->
                request.headers.apply {
                    append(HttpHeaders.Accept, "application/json")
                    deviceInfo.headers.forEach { (name, value) -> append(name, value) }
                    // Explicit per-request token (e.g. a server access token) wins.
                    if (!contains("X-Plex-Token")) {
                        tokenProvider()?.let { append("X-Plex-Token", it) }
                    }
                }
            }
        }

        val config: io.ktor.client.HttpClientConfig<*>.() -> Unit = {
            install(plexHeaders)
            if (logging) {
                // INFO logs method, URL and status only. Never raise to HEADERS/ALL:
                // that would leak X-Plex-Token into logcat.
                install(Logging) {
                    logger = Logger.SIMPLE
                    level = LogLevel.INFO
                }
            }
        }
        return if (engine != null) HttpClient(engine, config) else HttpClient(OkHttp, config)
    }
}
