package dev.bettermint.plex.api

import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** M5: progress reporting and watched-state request shapes. */
class TimelineTest {

    @Test
    fun `timeline sends state time duration and the metadata key`() = runTest {
        var url = ""
        val api = ServerApi(
            testClient { request -> url = request.url.toString(); respond("", headers = jsonHeaders) },
            "https://s:32400",
            accessToken = "tok",
        )
        api.timeline("77150", state = "playing", timeMs = 300000, durationMs = 6036161, playQueueItemId = 42)

        assertTrue(url.contains("/:/timeline"))
        assertTrue(url.contains("ratingKey=77150"))
        assertTrue(url.contains("state=playing"))
        assertTrue(url.contains("time=300000"))
        assertTrue(url.contains("duration=6036161"))
        assertTrue(url.contains("playQueueItemID=42"))
        assertTrue(url.contains("key=%2Flibrary%2Fmetadata%2F77150"))
    }

    @Test
    fun `timeline swallows errors so a bad ping never breaks playback`() = runTest {
        val api = ServerApi(
            testClient { respond("err", HttpStatusCode.InternalServerError, jsonHeaders) },
            "https://s:32400",
            accessToken = null,
        )
        // Must not throw.
        api.timeline("1", "playing", 0, 1000)
    }

    @Test
    fun `scrobble targets the library identifier`() = runTest {
        var scrobbleUrl = ""
        ServerApi(
            testClient { r -> scrobbleUrl = r.url.toString(); respond("", headers = jsonHeaders) },
            "https://s:32400", "tok",
        ).scrobble("77150")

        assertTrue(scrobbleUrl.contains("/:/scrobble"))
        assertTrue(scrobbleUrl.contains("key=77150"))
        assertTrue(scrobbleUrl.contains("identifier=com.plexapp.plugins.library"))
    }
}
