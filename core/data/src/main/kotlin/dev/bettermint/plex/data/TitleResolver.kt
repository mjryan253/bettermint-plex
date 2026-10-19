package dev.bettermint.plex.data

import dev.bettermint.plex.api.PlexTvApi
import dev.bettermint.plex.api.model.Metadata
import java.util.concurrent.ConcurrentHashMap
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

    private val cache = ConcurrentHashMap<String, String>()
    private val parallelism = Semaphore(6)

    suspend fun resolve(items: List<Metadata>): List<Metadata> {
        if (items.none { needsResolve(it) }) return items
        val resolved: Map<String, String> = coroutineScope {
            items.filter { needsResolve(it) }
                .mapNotNull { it.guid }
                .distinct()
                .map { guid ->
                    async {
                        parallelism.withPermit {
                            val title = cache[guid] ?: runCatching {
                                plexTv.getCanonicalMetadata(guid)?.title
                            }.getOrNull()?.takeIf { it.isNotBlank() }?.also { cache[guid] = it }
                            title?.let { guid to it }
                        }
                    }
                }.awaitAll().filterNotNull().toMap()
        }
        if (resolved.isEmpty()) return items
        return items.map { item ->
            val better = item.guid?.let(resolved::get)
            if (better != null && needsResolve(item)) item.copy(title = better) else item
        }
    }

    suspend fun resolve(item: Metadata?): Metadata? =
        item?.let { resolve(listOf(it)).first() }

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
