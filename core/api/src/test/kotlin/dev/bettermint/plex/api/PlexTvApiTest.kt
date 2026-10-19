package dev.bettermint.plex.api

import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlexTvApiTest {

    @Test
    fun `createPin posts a non-strong pin and parses the code`() = runTest {
        var method: HttpMethod? = null
        var url = ""
        val api = PlexTvApi(testClient { request ->
            method = request.method
            url = request.url.toString()
            respond(fixture("pin_created.json"), headers = jsonHeaders)
        })
        val pin = api.createPin()

        assertEquals(HttpMethod.Post, method)
        assertTrue(url.startsWith("https://plex.tv/api/v2/pins"))
        assertTrue("manual-entry flow must not request a strong pin", !url.contains("strong=true"))
        assertEquals(12345678901L, pin.id)
        assertEquals("ABCD", pin.code)
        assertNull(pin.authToken)
    }

    @Test
    fun `getPin parses authToken once linked`() = runTest {
        val api = PlexTvApi(testClient { respond(fixture("pin_linked.json"), headers = jsonHeaders) })
        assertEquals("REDACTED-ACCOUNT-TOKEN", api.getPin(12345678901L).authToken)
    }

    @Test
    fun `resources parse and isServer filters players out`() = runTest {
        val api = PlexTvApi(testClient { request ->
            assertTrue(request.url.parameters["includeHttps"] == "1")
            assertTrue(request.url.parameters["includeRelay"] == "1")
            respond(fixture("resources.json"), headers = jsonHeaders)
        })
        val servers = api.getResources().filter { it.isServer }

        assertEquals(1, servers.size)
        val server = servers.single()
        assertEquals("home-server", server.name)
        assertEquals("machine-id-1", server.clientIdentifier)
        assertEquals("REDACTED-SERVER-TOKEN", server.accessToken)
        assertEquals(3, server.connections.size)
        assertEquals(1, server.connections.count { it.local })
        assertEquals(1, server.connections.count { it.relay })
    }

    @Test
    fun `home users parse with protected flag`() = runTest {
        val api = PlexTvApi(testClient { respond(fixture("home_users.json"), headers = jsonHeaders) })
        val users = api.getHomeUsers().users

        assertEquals(2, users.size)
        assertEquals(true, users.first { it.title == "Admin User" }.protected)
        assertEquals(false, users.first { it.title == "Kid" }.protected)
    }

    @Test
    fun `switchHomeUser posts to the uuid with optional pin and parses the new token`() = runTest {
        var method: HttpMethod? = null
        var url = ""
        val api = PlexTvApi(testClient { request ->
            method = request.method
            url = request.url.toString()
            respond("""{"authToken":"NEW-USER-TOKEN"}""", headers = jsonHeaders)
        })
        val switched = api.switchHomeUser("uuid-admin", pin = "1234")

        assertEquals(HttpMethod.Post, method)
        assertTrue(url.contains("/api/v2/home/users/uuid-admin/switch"))
        assertTrue(url.contains("pin=1234"))
        assertEquals("NEW-USER-TOKEN", switched.authToken)
    }

    @Test
    fun `getUser maps 200 to Valid and 401 to Invalid and 503 to Error`() = runTest {
        suspend fun check(status: HttpStatusCode, body: String = "{}"): TokenCheck =
            PlexTvApi(testClient { respond(body, status, jsonHeaders) }).getUser()

        assertTrue(check(HttpStatusCode.OK, """{"id":1,"username":"u"}""") is TokenCheck.Valid)
        assertEquals(TokenCheck.Invalid, check(HttpStatusCode.Unauthorized))
        assertEquals(TokenCheck.Error(503), check(HttpStatusCode.ServiceUnavailable))
    }
}
