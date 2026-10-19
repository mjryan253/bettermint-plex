package dev.bettermint.plex.data

import dev.bettermint.plex.api.PlexTvApi
import dev.bettermint.plex.api.model.Metadata
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

/**
 * Replaces stale filename-style titles (e.g. "Coraline.2009.1080p.BluRay...")
 * with the canonical title from the Plex Discover provider, the same source
 * Plex Web overlays. Only items with a plex:// guid and a filename-looking
 * title trigger a provider call; results are cached for the session.
 */
class TitleResolver(private val plexTv: PlexTvApi) {

    /** guid to canonical title; "" marks a definitive miss so it isn't refetched. */
    private val cache = ConcurrentHashMap<String, String>()
    private val parallelism = Semaphore(6)

    suspend fun resolve(items: List<Metadata>): List<Metadata> {
        val needs = items.map(::needsResolve)
        if (true !in needs) return items
        val missing = items.filterIndexed { i, _ -> needs[i] }
            .map { it.guid!! }
            .distinct()
            .filterNot(cache::containsKey)
        if (missing.isNotEmpty()) {
            coroutineScope {
                missing.map { guid -> async { parallelism.withPermit { fetch(guid) } } }.awaitAll()
            }
        }
        return items.mapIndexed { i, item ->
            val better = if (needs[i]) cache[item.guid!!] else null
            if (better.isNullOrEmpty()) item else item.copy(title = better)
        }
    }

    suspend fun resolve(item: Metadata?): Metadata? =
        item?.let { resolve(listOf(it)).first() }

    /** Caches the provider's answer; transient errors are not cached, so they retry next time. */
    private suspend fun fetch(guid: String) {
        val title = try {
            plexTv.getCanonicalMetadata(guid)?.title
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            return
        }
        cache[guid] = title?.takeIf { it.isNotBlank() }.orEmpty()
    }

    private fun needsResolve(item: Metadata): Boolean =
        item.guid?.startsWith("plex://") == true && looksLikeFilename(item.title)

    companion object {
        private val filenameSignals = Regex(
            "(\\.(19|20)\\d\\d\\.)" +
                "|((^|[ .])(1080p|720p|2160p|4k|bluray|webrip|web-dl|hdtv|x264|x265|h264|h265|hevc|aac|dts|remux|proper|extended)([ .]|$))" +
                "|(\\.[A-Za-z0-9]+-[A-Za-z0-9]+$)",
            RegexOption.IGNORE_CASE,
        )

        /** True for scan-time titles that are really filenames. */
        fun looksLikeFilename(title: String): Boolean =
            filenameSignals.containsMatchIn(title)
    }
}
