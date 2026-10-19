package dev.bettermint.plex.api

import io.ktor.client.engine.mock.respond
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** M4: video/audio/subtitle stream parsing and chapter parsing. */
class MediaStreamTest {

    private suspend fun detail(): dev.bettermint.plex.api.model.Metadata {
        val api = ServerApi(
            testClient { respond(fixture("metadata_streams.json"), headers = jsonHeaders) },
            "https://server.example:32400",
            accessToken = null,
        )
        return requireNotNull(api.getMetadata("555"))
    }

    @Test
    fun `streams split by type`() = runTest {
        val streams = detail().media.first().parts.first().streams
        assertEquals(1, streams.count { it.isVideo })
        assertEquals(2, streams.count { it.isAudio })
        assertEquals(2, streams.count { it.isSubtitle })
    }

    @Test
    fun `audio stream carries language channels and selected flag`() = runTest {
        val audio = detail().media.first().parts.first().streams.first { it.isAudio && it.selected }
        assertEquals("English", audio.language)
        assertEquals("en", audio.languageTag)
        assertEquals(6, audio.channels)
        assertEquals("English (DTS 5.1)", audio.label)
    }

    @Test
    fun `external subtitle exposes a sidecar key, embedded does not`() = runTest {
        val subs = detail().media.first().parts.first().streams.filter { it.isSubtitle }
        val external = subs.first { it.codec == "srt" }
        val embedded = subs.first { it.codec == "ass" }
        assertEquals("/library/streams/804", external.key)
        assertNull(embedded.key)
        assertFalse(embedded.forced)
    }

    @Test
    fun `label falls back through extended, display, title, language`() = runTest {
        val subs = detail().media.first().parts.first().streams.filter { it.isSubtitle }
        // extendedDisplayTitle absent on these, so displayTitle is used.
        assertEquals("English (SRT External)", subs.first { it.codec == "srt" }.label)
    }

    @Test
    fun `chapters parse with offsets and thumbs`() = runTest {
        val chapters = detail().chapters
        assertEquals(2, chapters.size)
        assertEquals("Opening", chapters[0].tag)
        assertEquals(0L, chapters[0].startTimeOffset)
        assertEquals(600000L, chapters[1].startTimeOffset)
        assertTrue(chapters[1].thumb!!.contains("chapterImages"))
    }
}
