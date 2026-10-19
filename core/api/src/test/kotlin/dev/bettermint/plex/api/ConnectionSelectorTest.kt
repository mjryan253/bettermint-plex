package dev.bettermint.plex.api

import dev.bettermint.plex.api.ConnectionSelector.Probe
import dev.bettermint.plex.api.model.PlexConnection
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ConnectionSelectorTest {

    private fun conn(uri: String, local: Boolean = false, relay: Boolean = false) =
        PlexConnection(uri = uri, local = local, relay = relay)

    private val local = conn("https://local:32400", local = true)
    private val remote = conn("https://remote:32400")
    private val relay = conn("https://relay:443", relay = true)

    @Test
    fun `local beats remote beats relay regardless of latency`() {
        val picked = ConnectionSelector.pickBest(
            listOf(Probe(relay, 10), Probe(remote, 50), Probe(local, 500)),
        )
        assertEquals(local, picked)
    }

    @Test
    fun `remote beats relay when local is unreachable`() {
        val picked = ConnectionSelector.pickBest(listOf(Probe(relay, 10), Probe(remote, 900)))
        assertEquals(remote, picked)
    }

    @Test
    fun `latency breaks ties within the same class`() {
        val remote2 = conn("https://remote2:32400")
        val picked = ConnectionSelector.pickBest(listOf(Probe(remote, 300), Probe(remote2, 80)))
        assertEquals(remote2, picked)
    }

    @Test
    fun `selectBest probes and skips failing connections`() = runTest {
        val picked = ConnectionSelector.selectBest(listOf(local, remote, relay)) { c ->
            c != local // local unreachable
        }
        assertEquals(remote, picked)
    }

    @Test
    fun `selectBest returns null when nothing is reachable`() = runTest {
        val picked = ConnectionSelector.selectBest(listOf(local, remote)) { false }
        assertNull(picked)
    }

    @Test
    fun `selectBest treats probe exceptions as unreachable`() = runTest {
        val picked = ConnectionSelector.selectBest(listOf(local, remote)) { c ->
            if (c == local) error("tls failure") else true
        }
        assertEquals(remote, picked)
    }
}
