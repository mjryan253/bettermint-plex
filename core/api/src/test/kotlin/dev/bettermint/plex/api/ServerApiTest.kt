package dev.bettermint.plex.api

import io.ktor.client.engine.mock.respond
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class ServerApiTest {

    @Test
    fun `identity parses machineIdentifier`() = runTest {
        val api = ServerApi(
            testClient { respond(fixture("identity.json"), headers = jsonHeaders) },
            "https://192-168-1-50.abcdef.plex.direct:32400",
            accessToken = null,
        )
        assertEquals("machine-id-1", api.getIdentity().machineIdentifier)
    }

    @Test
    fun `library sections parse across types`() = runTest {
        val api = ServerApi(
            testClient { respond(fixture("library_sections.json"), headers = jsonHeaders) },
            "https://server.example:32400",
            accessToken = "server-token",
        )
        val sections = api.getLibrarySections()

        assertEquals(3, sections.size)
        assertEquals(listOf("Movies", "TV Shows", "Music"), sections.map { it.title })
        assertEquals(listOf("movie", "show", "artist"), sections.map { it.type })
        assertEquals("1", sections.first().key)
    }
}
