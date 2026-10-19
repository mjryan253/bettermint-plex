package dev.bettermint.plex.api

import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Non-2xx responses surface as PlexApiException carrying the status. */
class ApiErrorTest {

    @Test
    fun `server error throws PlexApiException with status`() = runTest {
        val api = ServerApi(
            testClient { respond("boom", HttpStatusCode.InternalServerError, jsonHeaders) },
            "https://server.example:32400",
            accessToken = null,
        )
        val thrown = runCatching { api.getLibrarySections() }.exceptionOrNull()
        assertTrue(thrown is PlexApiException)
        assertEquals(500, (thrown as PlexApiException).httpStatus)
    }

    @Test
    fun `not found throws with 404`() = runTest {
        val api = PlexTvApi(testClient { respond("nope", HttpStatusCode.NotFound, jsonHeaders) })
        val thrown = runCatching { api.getResources() }.exceptionOrNull()
        assertEquals(404, (thrown as? PlexApiException)?.httpStatus)
    }

    @Test
    fun `addToPlaylist surfaces a failed write`() = runTest {
        val api = ServerApi(
            testClient { respond("denied", HttpStatusCode.Forbidden, jsonHeaders) },
            "https://server.example:32400",
            accessToken = "t",
        )
        val thrown = runCatching { api.addToPlaylist("1", "m", "2") }.exceptionOrNull()
        assertEquals(403, (thrown as? PlexApiException)?.httpStatus)
    }
}
