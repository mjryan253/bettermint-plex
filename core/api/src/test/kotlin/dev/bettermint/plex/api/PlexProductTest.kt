package dev.bettermint.plex.api

import org.junit.Assert.assertTrue
import org.junit.Test

class PlexProductTest {
    @Test
    fun `product name is set and is not a Plex product name`() {
        assertTrue(PlexProduct.NAME.isNotBlank())
        assertTrue(!PlexProduct.NAME.contains("plex", ignoreCase = true))
    }
}
