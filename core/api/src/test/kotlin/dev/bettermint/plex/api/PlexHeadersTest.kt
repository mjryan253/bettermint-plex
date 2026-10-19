package dev.bettermint.plex.api

import io.ktor.client.engine.mock.respond
import io.ktor.client.request.get
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PlexHeadersTest {

    private val requiredHeaders = listOf(
        "X-Plex-Product",
        "X-Plex-Version",
        "X-Plex-Client-Identifier",
        "X-Plex-Platform",
        "X-Plex-Platform-Version",
        "X-Plex-Device",
        "X-Plex-Device-Name",
    )

    @Test
    fun `every request carries the full X-Plex header set`() = runTest {
        var seen: Map<String, String?> = emptyMap()
        val client = testClient(token = "account-token") { request ->
            seen = (requiredHeaders + "X-Plex-Token").associateWith { request.headers[it] }
            respond("{}", headers = jsonHeaders)
        }
        client.get("https://plex.tv/api/v2/user")

        requiredHeaders.forEach { name ->
            val value = seen[name]
            assertEquals("header $name must be present and non-blank", false, value.isNullOrBlank())
        }
        assertEquals("account-token", seen["X-Plex-Token"])
    }

    @Test
    fun `no token header when provider returns null`() = runTest {
        var token: String? = "sentinel"
        val client = testClient(token = null) { request ->
            token = request.headers["X-Plex-Token"]
            respond("{}", headers = jsonHeaders)
        }
        client.get("https://plex.tv/api/v2/pins/1")
        assertNull(token)
    }

    @Test
    fun `per-request token overrides the account token`() = runTest {
        var token: String? = null
        val client = testClient(token = "account-token") { request ->
            token = request.headers["X-Plex-Token"]
            respond(fixture("library_sections.json"), headers = jsonHeaders)
        }
        val api = ServerApi(client, "https://server.example:32400", accessToken = "server-token")
        api.getLibrarySections()
        assertEquals("server-token", token)
    }
}
