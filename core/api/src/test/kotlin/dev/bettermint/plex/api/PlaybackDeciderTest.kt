package dev.bettermint.plex.api

import dev.bettermint.plex.api.PlaybackDecider.DeviceCapabilities
import dev.bettermint.plex.api.PlaybackDecider.Method
import dev.bettermint.plex.api.model.MediaVersion
import io.ktor.client.engine.mock.respond
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackDeciderTest {

    private val caps = DeviceCapabilities.fireTv

    private fun version(
        container: String = "mkv",
        video: String = "h264",
        audio: String = "aac",
        bitrate: Int? = 3000,
    ) = MediaVersion(container = container, videoCodec = video, audioCodec = audio, bitrate = bitrate)

    @Test
    fun `supported codecs and container direct play`() {
        assertEquals(Method.DirectPlay, PlaybackDecider.decide(version(), caps))
        assertEquals(Method.DirectPlay, PlaybackDecider.decide(version(container = "mp4", video = "hevc", audio = "eac3"), caps))
    }

    @Test
    fun `unsupported audio but supported video direct streams`() {
        assertEquals(Method.DirectStream, PlaybackDecider.decide(version(audio = "truehd"), caps))
        assertEquals(Method.DirectStream, PlaybackDecider.decide(version(audio = "dca"), caps))
    }

    @Test
    fun `unsupported container with supported codecs direct streams`() {
        assertEquals(Method.DirectStream, PlaybackDecider.decide(version(container = "avi"), caps))
    }

    @Test
    fun `unsupported video transcodes`() {
        assertEquals(Method.Transcode, PlaybackDecider.decide(version(video = "mpeg2video"), caps))
        assertEquals(Method.Transcode, PlaybackDecider.decide(version(video = "vc1"), caps))
    }

    @Test
    fun `bitrate cap forces transcode`() {
        assertEquals(Method.Transcode, PlaybackDecider.decide(version(bitrate = 20000), caps, maxVideoBitrateKbps = 4000))
        assertEquals(Method.DirectPlay, PlaybackDecider.decide(version(bitrate = 3000), caps, maxVideoBitrateKbps = 4000))
    }

    @Test
    fun `burned subtitles force transcode`() {
        assertEquals(Method.Transcode, PlaybackDecider.decide(version(), caps, mustBurnSubtitles = true))
    }

    @Test
    fun `hls url carries identity and scratch-built params only`() {
        val api = ServerApi(
            testClient { respond("{}", headers = jsonHeaders) },
            "https://server.example:32400",
            accessToken = "server-token",
            deviceInfo = testDeviceInfo,
        )
        val url = api.hlsStartUrl(
            ratingKey = "77150",
            sessionId = "session-guid",
            directStream = false,
            startMs = 65_000,
            maxVideoBitrateKbps = 4000,
            subtitleStreamId = 42,
            burnSubtitles = true,
        )

        assertTrue(url.startsWith("https://server.example:32400/video/:/transcode/universal/start.m3u8?hasMDE=1"))
        assertTrue(url.contains("path=%2Flibrary%2Fmetadata%2F77150"))
        assertTrue(url.contains("protocol=hls"))
        assertTrue(url.contains("directPlay=0"))
        assertTrue(url.contains("directStream=0"))
        assertTrue(url.contains("session=session-guid"))
        assertTrue(url.contains("offset=65"))
        assertTrue(url.contains("maxVideoBitrate=4000"))
        assertTrue(url.contains("subtitleStreamID=42"))
        assertTrue(url.contains("subtitles=burn"))
        assertTrue(url.contains("X-Plex-Client-Identifier=test-client-id"))
        assertTrue(url.contains("X-Plex-Device=AFTMM"))
        assertTrue(url.contains("X-Plex-Token=server-token"))
        assertTrue("never replay decision params", !url.contains("decision"))
    }

    @Test
    fun `hls url omits optional params when unset`() {
        val api = ServerApi(
            testClient { respond("{}", headers = jsonHeaders) },
            "https://server.example:32400",
            accessToken = null,
            deviceInfo = testDeviceInfo,
        )
        val url = api.hlsStartUrl(ratingKey = "1", sessionId = "s")

        assertTrue(url.contains("directStream=1"))
        assertTrue(!url.contains("offset="))
        assertTrue(!url.contains("maxVideoBitrate"))
        assertTrue(!url.contains("audioStreamID"))
        assertTrue(!url.contains("subtitleStreamID"))
        assertTrue(!url.contains("subtitles="))
        assertTrue(!url.contains("X-Plex-Token"))
    }
}
