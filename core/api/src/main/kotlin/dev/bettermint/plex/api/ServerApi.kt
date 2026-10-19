package dev.bettermint.plex.api

import dev.bettermint.plex.api.model.Hub
import dev.bettermint.plex.api.model.HubsContainer
import dev.bettermint.plex.api.model.Identity
import dev.bettermint.plex.api.model.LibrarySection
import dev.bettermint.plex.api.model.MediaContainer
import dev.bettermint.plex.api.model.Metadata
import dev.bettermint.plex.api.model.MetadataContainer
import dev.bettermint.plex.api.model.SectionsContainer
import io.ktor.client.HttpClient
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.request
import io.ktor.http.HttpMethod
import io.ktor.http.encodeURLQueryComponent
import kotlinx.coroutines.CancellationException

/**
 * Plex Media Server endpoints. [baseUrl] is the chosen connection URI,
 * [accessToken] the per-server token from the resources listing. [deviceInfo]
 * is only needed for stream URLs, whose fetches bypass the Ktor header plugin.
 */
class ServerApi(
    private val client: HttpClient,
    private val baseUrl: String,
    private val accessToken: String?,
    deviceInfo: PlexDeviceInfo? = null,
) {

    /** Identity and token as query params, built once: players and image loaders bypass our client. */
    private val identityQuery: String =
        deviceInfo?.headers?.joinToString("") { (name, value) -> "&$name=${value.encodeURLQueryComponent()}" }.orEmpty()
    private val tokenQuery: String = accessToken?.let { "&X-Plex-Token=$it" }.orEmpty()

    /** Unauthenticated; used to probe connection candidates. */
    suspend fun getIdentity(): Identity =
        client.get("$baseUrl/identity").bodyOrThrow<MediaContainer<Identity>>().container

    suspend fun getLibrarySections(): List<LibrarySection> =
        request("/library/sections").bodyOrThrow<MediaContainer<SectionsContainer>>().container.directories

    /** One page of a section's items. [sort] is a Plex sort key like "titleSort:asc". */
    suspend fun getLibraryItems(
        sectionKey: String,
        start: Int,
        size: Int,
        sort: String? = null,
    ): MetadataContainer =
        request("/library/sections/$sectionKey/all") {
            sort?.let { parameter("sort", it) }
            header("X-Plex-Container-Start", start.toString())
            header("X-Plex-Container-Size", size.toString())
        }.bodyOrThrow<MediaContainer<MetadataContainer>>().container

    /** Home hubs (Continue Watching, Recently Added, ...). */
    suspend fun getHubs(): List<Hub> = hubs("/hubs") { parameter("count", "12") }

    suspend fun search(query: String): List<Hub> = hubs("/hubs/search") {
        parameter("query", query)
        parameter("limit", "30")
    }

    /** Full detail for one item, with trailers/extras, chapters and intro/credit markers. */
    suspend fun getMetadata(ratingKey: String): Metadata? =
        items("/library/metadata/$ratingKey") {
            parameter("includeExtras", "1")
            parameter("includeChapters", "1")
            parameter("includeMarkers", "1")
        }.firstOrNull()

    /** Seasons of a show, or episodes of a season. */
    suspend fun getChildren(ratingKey: String): List<Metadata> =
        items("/library/metadata/$ratingKey/children")

    /**
     * Build a continuous server play queue: for an episode, the queue includes
     * the following episodes, which powers autoplay-next. Returns the ordered
     * items, each with a playQueueItemID.
     */
    suspend fun createPlayQueue(machineId: String, ratingKey: String): List<Metadata> =
        items("/playQueues", HttpMethod.Post) {
            parameter("type", "video")
            parameter("uri", libraryUri(machineId, ratingKey))
            parameter("own", "1")
            parameter("continuous", "1")
        }

    suspend fun getCollections(sectionKey: String): List<Metadata> =
        items("/library/sections/$sectionKey/collections")

    suspend fun getCollectionChildren(ratingKey: String): List<Metadata> =
        items("/library/collections/$ratingKey/children")

    suspend fun getPlaylists(): List<Metadata> =
        items("/playlists") { parameter("playlistType", "video") }

    suspend fun getPlaylistItems(ratingKey: String): List<Metadata> =
        items("/playlists/$ratingKey/items")

    suspend fun addToPlaylist(playlistRatingKey: String, machineId: String, itemRatingKey: String) {
        request("/playlists/$playlistRatingKey/items", HttpMethod.Put) {
            parameter("uri", libraryUri(machineId, itemRatingKey))
        }.ensureSuccess()
    }

    /**
     * Direct-play stream URL for a media part. The token rides as a query
     * parameter because the player's data source bypasses our client.
     */
    fun directPlayUrl(partKey: String): String =
        if (accessToken == null) "$baseUrl$partKey" else "$baseUrl$partKey?X-Plex-Token=$accessToken"

    /**
     * HLS stream URL for direct-stream or full transcode. Built from scratch
     * (never from decision-response params; replayed params cause 400s per
     * forum reports and the shape below is verified working on this server).
     * All identity rides the query because ExoPlayer fetches without our client.
     */
    fun hlsStartUrl(
        ratingKey: String,
        sessionId: String,
        mediaIndex: Int = 0,
        directStream: Boolean = true,
        maxVideoBitrateKbps: Int? = null,
        audioStreamId: Long? = null,
        subtitleStreamId: Long? = null,
        burnSubtitles: Boolean = false,
    ): String = buildString {
        append("$baseUrl/video/:/transcode/universal/start.m3u8")
        append("?hasMDE=1")
        append("&path=").append("/library/metadata/$ratingKey".encodeURLQueryComponent(encodeFull = true))
        append("&mediaIndex=$mediaIndex")
        append("&partIndex=0")
        append("&protocol=hls")
        append("&fastSeek=1")
        append("&directPlay=0")
        append("&directStream=").append(if (directStream) "1" else "0")
        append("&session=$sessionId")
        maxVideoBitrateKbps?.let { append("&maxVideoBitrate=$it") }
        audioStreamId?.let { append("&audioStreamID=$it") }
        subtitleStreamId?.let { append("&subtitleStreamID=$it") }
        if (burnSubtitles) append("&subtitles=burn")
        append(identityQuery)
        append(tokenQuery)
    }

    /**
     * Report playback position to the server. [state] is playing/paused/stopped.
     * Drives Continue Watching and resume offsets, and makes our session visible
     * in /status/sessions. Fire-and-forget; errors are swallowed.
     */
    suspend fun timeline(
        ratingKey: String,
        state: String,
        timeMs: Long,
        durationMs: Long,
        playQueueItemId: Long? = null,
    ) = fireAndForget("/:/timeline") {
        parameter("ratingKey", ratingKey)
        parameter("key", "/library/metadata/$ratingKey")
        parameter("state", state)
        parameter("time", timeMs.toString())
        parameter("duration", durationMs.toString())
        parameter("hasMDE", "1")
        playQueueItemId?.let { parameter("playQueueItemID", it.toString()) }
    }

    suspend fun scrobble(ratingKey: String) = fireAndForget("/:/scrobble") {
        parameter("key", ratingKey)
        parameter("identifier", "com.plexapp.plugins.library")
    }

    /** Tear down a transcode session when playback stops or restarts. */
    suspend fun stopTranscode(sessionId: String) = fireAndForget("/video/:/transcode/universal/stop") {
        parameter("session", sessionId)
    }

    /**
     * Server-side resized artwork URL for direct use by an image loader. The
     * token rides as a query parameter because the loader bypasses our client.
     */
    fun imageUrl(path: String, width: Int, height: Int): String =
        "$baseUrl/photo/:/transcode?width=$width&height=$height&minSize=1&upscale=1&url=" +
            path.encodeURLQueryComponent(encodeFull = true) + tokenQuery

    private fun libraryUri(machineId: String, ratingKey: String) =
        "server://$machineId/com.plexapp.plugins.library/library/metadata/$ratingKey"

    private suspend fun request(
        path: String,
        method: HttpMethod = HttpMethod.Get,
        block: HttpRequestBuilder.() -> Unit = {},
    ) = client.request("$baseUrl$path") {
        this.method = method
        accessToken?.let { header("X-Plex-Token", it) }
        block()
    }

    private suspend fun items(
        path: String,
        method: HttpMethod = HttpMethod.Get,
        block: HttpRequestBuilder.() -> Unit = {},
    ): List<Metadata> =
        request(path, method, block).bodyOrThrow<MediaContainer<MetadataContainer>>().container.items

    private suspend fun hubs(path: String, block: HttpRequestBuilder.() -> Unit): List<Hub> =
        request(path, block = block).bodyOrThrow<MediaContainer<HubsContainer>>().container.hubs

    /** Swallows server and network errors, but never coroutine cancellation. */
    private suspend fun fireAndForget(path: String, block: HttpRequestBuilder.() -> Unit) {
        try {
            request(path, block = block)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
        }
    }
}
