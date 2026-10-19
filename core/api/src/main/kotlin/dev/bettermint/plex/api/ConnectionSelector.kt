package dev.bettermint.plex.api

import dev.bettermint.plex.api.model.PlexConnection
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Picks the best reachable connection for a server: probe all candidates in
 * parallel with GET /identity, then prefer local > remote > relay, breaking
 * ties by lowest latency.
 */
object ConnectionSelector {

    data class Probe(val connection: PlexConnection, val latencyMs: Long)

    suspend fun selectBest(
        connections: List<PlexConnection>,
        timeoutMs: Long = 2_000,
        probe: suspend (PlexConnection) -> Boolean,
    ): PlexConnection? {
        val results = coroutineScope {
            connections.map { connection ->
                async {
                    val start = System.currentTimeMillis()
                    val ok = withTimeoutOrNull(timeoutMs) {
                        runCatching { probe(connection) }.getOrDefault(false)
                    } ?: false
                    if (ok) Probe(connection, System.currentTimeMillis() - start) else null
                }
            }.awaitAll().filterNotNull()
        }
        return pickBest(results)
    }

    /** Pure ranking, separated for direct unit testing. */
    fun pickBest(probes: List<Probe>): PlexConnection? =
        probes.minWithOrNull(
            compareBy(
                { rank(it.connection) },
                { it.latencyMs },
            ),
        )?.connection

    private fun rank(c: PlexConnection): Int = when {
        c.local && !c.relay -> 0
        !c.relay -> 1
        else -> 2
    }
}
