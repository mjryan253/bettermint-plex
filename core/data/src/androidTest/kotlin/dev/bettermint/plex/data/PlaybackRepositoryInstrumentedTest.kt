package dev.bettermint.plex.data

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.bettermint.plex.api.PlaybackDecider.Method
import dev.bettermint.plex.api.PlexClient
import dev.bettermint.plex.api.PlexDeviceInfo
import dev.bettermint.plex.api.ServerApi
import dev.bettermint.plex.api.model.MediaPart
import dev.bettermint.plex.api.model.MediaVersion
import dev.bettermint.plex.api.model.Metadata
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.headersOf
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * PlaybackRepository.source() end to end with a REAL AuthStore (DataStore),
 * which is the integration the JVM tier cannot run. The ServerApi is still a
 * MockEngine so no live server is needed.
 */
@RunWith(AndroidJUnit4::class)
class PlaybackRepositoryInstrumentedTest {

    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val store = AuthStore(context)

    private fun api() = ServerApi(
        PlexClient.create(
            PlexDeviceInfo("id", platformVersion = "1", device = "d", deviceName = "n"),
            tokenProvider = { null },
            engine = MockEngine { respond("{}", headers = headersOf(HttpHeaders.ContentType, "application/json")) },
        ),
        "https://s:32400",
        accessToken = "tok",
    )

    private fun movie(container: String, video: String, audio: String, bitrate: Int) = Metadata(
        ratingKey = "900",
        type = "movie",
        title = "X",
        media = listOf(
            MediaVersion(
                container = container, videoCodec = video, audioCodec = audio, bitrate = bitrate,
                parts = listOf(MediaPart(key = "/library/parts/1/file.$container")),
            ),
        ),
    )

    @Before
    @After
    fun reset() = runBlocking {
        store.clearSession()
        store.setQualityPrefs(AuthStore.QualityPrefs(0, 0))
    }

    @Test
    fun supported_file_on_local_at_original_quality_direct_plays() = runBlocking {
        val repo = PlaybackRepository(api(), store, machineId = "m", isLocal = true)
        val source = repo.source(movie("mp4", "h264", "aac", 3000), versionIndex = 0, startMs = 5000)
        assertEquals(Method.DirectPlay, source!!.method)
        assertTrue(source.url.contains("/library/parts/1/file.mp4"))
        assertEquals("player seeks to resume offset on direct play", 5000L, source.seekToMs)
        assertNull(source.sessionId)
    }

    @Test
    fun local_quality_preset_forces_transcode() = runBlocking {
        store.setQualityPrefs(AuthStore.QualityPrefs(maxKbpsLocal = 2000, maxKbpsRemote = 0))
        val repo = PlaybackRepository(api(), store, machineId = "m", isLocal = true)
        // 20 Mbps file but a 2 Mbps local cap -> transcode.
        val source = repo.source(movie("mkv", "h264", "aac", 20000), versionIndex = 0, startMs = 0)
        assertEquals(Method.Transcode, source!!.method)
        assertTrue(source.url.contains("/transcode/universal/start.m3u8"))
        assertTrue(source.url.contains("maxVideoBitrate=2000"))
        assertTrue(source.sessionId!!.isNotBlank())
    }

    @Test
    fun remote_connection_uses_remote_preset() = runBlocking {
        store.setQualityPrefs(AuthStore.QualityPrefs(maxKbpsLocal = 0, maxKbpsRemote = 4000))
        val repo = PlaybackRepository(api(), store, machineId = "m", isLocal = false)
        val source = repo.source(movie("mp4", "h264", "aac", 20000), versionIndex = 0, startMs = 0)
        assertEquals(Method.Transcode, source!!.method)
        assertTrue(source.url.contains("maxVideoBitrate=4000"))
    }

    @Test
    fun unsupported_audio_direct_streams() = runBlocking {
        val repo = PlaybackRepository(api(), store, machineId = "m", isLocal = true)
        val source = repo.source(movie("mkv", "h264", "truehd", 3000), versionIndex = 0, startMs = 0)
        assertEquals(Method.DirectStream, source!!.method)
        assertTrue(source.url.contains("directStream=1"))
    }
}
