package dev.bettermint.plex.api

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandler
import io.ktor.http.HttpHeaders
import io.ktor.http.headersOf

fun fixture(name: String): String =
    checkNotNull(object {}.javaClass.getResource("/fixtures/$name")) { "missing fixture $name" }
        .readText()

val testDeviceInfo = PlexDeviceInfo(
    clientIdentifier = "test-client-id",
    platformVersion = "7.1.2",
    device = "AFTMM",
    deviceName = "Bettermint Test",
)

fun testClient(
    token: String? = null,
    handler: MockRequestHandler,
): HttpClient = PlexClient.create(
    deviceInfo = testDeviceInfo,
    tokenProvider = { token },
    engine = MockEngine(handler),
)

val jsonHeaders = headersOf(HttpHeaders.ContentType, "application/json")
