package dev.bettermint.plex.data

import dev.bettermint.plex.api.PlaybackDecider.Method
import dev.bettermint.plex.api.model.MediaStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** M4 pure playback logic: fallback chain, burn decision, bitrate-cap precedence. */
class PlaybackRepositoryTest {

    @Test
    fun `fallback steps down then stops`() {
        assertEquals(Method.DirectStream, PlaybackRepository.fallbackAfter(Method.DirectPlay))
        assertEquals(Method.Transcode, PlaybackRepository.fallbackAfter(Method.DirectStream))
        assertNull(PlaybackRepository.fallbackAfter(Method.Transcode))
    }

    @Test
    fun `needsBurn is true for image and advanced formats, false for simple text`() {
        fun sub(codec: String?) = MediaStream(id = 1, streamType = 3, codec = codec)
        assertFalse(PlaybackRepository.needsBurn(null))
        assertFalse(PlaybackRepository.needsBurn(sub("srt")))
        assertFalse(PlaybackRepository.needsBurn(sub("subrip")))
        assertFalse(PlaybackRepository.needsBurn(sub("vtt")))
        assertTrue(PlaybackRepository.needsBurn(sub("ass")))
        assertTrue(PlaybackRepository.needsBurn(sub("ssa")))
        assertTrue(PlaybackRepository.needsBurn(sub("dvd_subtitle")))
        assertTrue("unknown codec should burn to be safe", PlaybackRepository.needsBurn(sub(null)))
    }

    @Test
    fun `nextInQueue returns the following item, or null at the end`() {
        val items = listOf(
            dev.bettermint.plex.api.model.Metadata(ratingKey = "1", title = "A"),
            dev.bettermint.plex.api.model.Metadata(ratingKey = "2", title = "B"),
            dev.bettermint.plex.api.model.Metadata(ratingKey = "3", title = "C"),
        )
        assertEquals("B", PlaybackRepository.nextInQueue(items, "1")?.title)
        assertEquals("C", PlaybackRepository.nextInQueue(items, "2")?.title)
        assertNull("last item has no next", PlaybackRepository.nextInQueue(items, "3"))
        assertNull("unknown key has no next", PlaybackRepository.nextInQueue(items, "999"))
        assertNull(PlaybackRepository.nextInQueue(emptyList(), "1"))
    }

    @Test
    fun `isWatched triggers at ninety percent`() {
        assertFalse(PlaybackRepository.isWatched(timeMs = 0, durationMs = 1000))
        assertFalse(PlaybackRepository.isWatched(timeMs = 899, durationMs = 1000))
        assertTrue(PlaybackRepository.isWatched(timeMs = 900, durationMs = 1000))
        assertTrue(PlaybackRepository.isWatched(timeMs = 1000, durationMs = 1000))
        assertFalse("no duration means not watched", PlaybackRepository.isWatched(timeMs = 500, durationMs = 0))
    }

    @Test
    fun `resolveCap prefers override, then connection-specific preset`() {
        assertEquals(4000, PlaybackRepository.resolveCap(4000, 0, 0, isLocal = true))
        assertEquals(8000, PlaybackRepository.resolveCap(null, 8000, 2000, isLocal = true))
        assertEquals(2000, PlaybackRepository.resolveCap(null, 8000, 2000, isLocal = false))
        assertNull(PlaybackRepository.resolveCap(null, 0, 0, isLocal = true))
        assertNull(PlaybackRepository.resolveCap(0, 8000, 8000, isLocal = true))
    }
}
