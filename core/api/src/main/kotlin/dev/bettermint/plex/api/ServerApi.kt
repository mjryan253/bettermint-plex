package dev.bettermint.plex.api

import dev.bettermint.plex.api.model.Hub
import dev.bettermint.plex.api.model.HubsResponse
import dev.bettermint.plex.api.model.Identity
import dev.bettermint.plex.api.model.IdentityResponse
import dev.bettermint.plex.api.model.LibrarySection
import dev.bettermint.plex.api.model.Metadata
import dev.bettermint.plex.api.model.MetadataContainer
import dev.bettermint.plex.api.model.MetadataResponse
import dev.bettermint.plex.api.model.PlayQueue
import dev.bettermint.plex.api.model.PlayQueueResponse
import dev.bettermint.plex.api.model.SectionsResponse
import io.ktor.client.HttpClient
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.http.encodeURLQueryComponent
import io.ktor.http.isSuccess

/**
 * Plex Media Server endpoints. [baseUrl] is the chosen connection URI,
 * [accessToken] the per-server token from the resources listing. [deviceInfo]
 * is only needed for stream URLs, whose fetches bypass the Ktor header plugin.
 */
class ServerApi(
    private val client: HttpClient,
    val baseUrl: String,
    private val accessToken: String?,
    private val deviceInfo: PlexDeviceInfo? = null,
) {

    /** Unauthenticated; used to probe connection candidates. */
    suspend fun getIdentity(): Identity =
        client.get("$baseUrl/identity").bodyOrThrow<IdentityResponse>().mediaContainer

    suspend fun getLibrarySections(): List<LibrarySection> =
        get("$baseUrl/library/sections").bodyOrThrow<SectionsResponse>().mediaContainer.directories

    /** One page of a section's items. [sort] is a Plex sort key like "titleSort:asc". */
    suspend fun getLibraryItems(
        sectionKey: String,
        start: Int,
        size: Int,
        sort: String? = null,
    ): MetadataContainer =
        get("$baseUrl/library/sections/$sectionKey/all") {
            sort?.let { parameter("sort", it) }
            header("X-Plex-Container-Start", start.toString())
            header("X-Plex-Container-Size", size.toString())
        }.bodyOrThrow<MetadataResponse>().mediaContainer

    /** Home hubs (Continue Watching, Recently Added, ...). */
    suspend fun getHubs(count: Int = 12): List<Hub> =
        get("$baseUrl/hubs") {
            parameter("count", count.toString())
        }.bodyOrThrow<HubsResponse>().mediaContainer.hubs

    suspend fun search(query: String, limit: Int = 30): List<Hub> =
        get("$baseUrl/hubs/search") {
            parameter("query", query)
            parameter("limit", limit.toString())
        }.bodyOrThrow<HubsResponse>().mediaContainer.hubs

    /** Full detail for one item; [includeExtras] nests trailers/extras in the result. */
    suspend fun getMetadata(ratingKey: String, includeExtras: Boolean = false): Metadata? =
        get("$baseUrl/library/metadata/$ratingKey") {
            if (includeExtras) parameter("includeExtras", "1")
            parameter("includeChapters", "1")
            parameter("includeMarkers", "1")
        }.bodyOrThrow<MetadataResponse>().mediaContainer.items.firstOrNull()

    /** Seasons of a show, or episodes of a season. */
    suspend fun getChildren(ratingKey: String): List<Metadata> =
        get("$baseUrl/library/metadata/$ratingKey/children")
            .bodyOrThrow<MetadataResponse>().mediaContainer.items

    /**
     * Build a server play queue for continuous playback. For an episode with
     * [continuous], the queue includes the following episodes, which powers
     * autoplay-next. Returns the ordered items, each with a playQueueItemID.
     */
    suspend fun createPlayQueue(
        machineId: String,
        ratingKey: String,
        continuous: Boolean = true,
    ): PlayQueue {
        val uri = "server://$machineId/com.plexapp.plugins.library/library/metadata/$ratingKey"
        return client.post("$baseUrl/playQueues") {
            accessToken?.let { header("X-Plex-Token", it) }
            parameter("type", "video")
            parameter("uri", uri)
            parameter("own", "1")
            if (continuous) parameter("continuous", "1")
        }.bodyOrThrow<PlayQueueResponse>().mediaContainer
    }

    suspend fun getCollections(sectionKey: String): List<Metadata> =
        get("$baseUrl/library/sections/$sectionKey/collections")
            .bodyOrThrow<MetadataResponse>().mediaContainer.items

    suspend fun getCollectionChildren(ratingKey: String): List<Metadata> =
        get("$baseUrl/library/collections/$ratingKey/children")
            .bodyOrThrow<MetadataResponse>().mediaContainer.items

    suspend fun getPlaylists(): List<Metadata> =
        get("$baseUrl/playlists") {
            parameter("playlistType", "video")
        }.bodyOrThrow<MetadataResponse>().mediaContainer.items

    suspend fun getPlaylistItems(ratingKey: String): List<Metadata> =
        get("$baseUrl/playlists/$ratingKey/items")
            .bodyOrThrow<MetadataResponse>().mediaContainer.items

    suspend fun addToPlaylist(playlistRatingKey: String, machineId: String, itemRatingKey: String) {
        val uri = "server://$machineId/com.plexapp.plugins.library/library/metadata/$itemRatingKey"
        val response = client.put("$baseUrl/playlists/$playlistRatingKey/items") {
            accessToken?.let { header("X-Plex-Token", it) }
            parameter("uri", uri)
        }
        if (!response.status.isSuccess()) {
            throw PlexApiException(response.status.value, response.bodyAsTextSafe())
        }
    }

    /**
     * Direct-play stream URL for a media part. The token rides as a query
     * parameter because the player's data source bypasses our client.
     */
    fun directPlayUrl(partKey: String): String {
        val token = accessToken?.let { "?X-Plex-Token=$it" } ?: ""
        return "$baseUrl$partKey$token"
    }

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
        partIndex: Int = 0,
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
        append("&partIndex=$partIndex")
        append("&protocol=hls")
        append("&fastSeek=1")
        append("&directPlay=0")
        append("&directStream=").append(if (directStream) "1" else "0")
        append("&session=$sessionId")
        maxVideoBitrateKbps?.let { append("&maxVideoBitrate=$it") }
        audioStreamId?.let { append("&audioStreamID=$it") }
        subtitleStreamId?.let { append("&subtitleStreamID=$it") }
        if (burnSubtitles) append("&subtitles=burn")
        deviceInfo?.let {
            append("&X-Plex-Product=").append(it.product.encodeURLQueryComponent())
            append("&X-Plex-Version=").append(it.version.encodeURLQueryComponent())
            append("&X-Plex-Client-Identifier=").append(it.clientIdentifier.encodeURLQueryComponent())
            append("&X-Plex-Platform=").append(it.platform.encodeURLQueryComponent())
            append("&X-Plex-Platform-Version=").append(it.platformVersion.encodeURLQueryComponent())
            append("&X-Plex-Device=").append(it.device.encodeURLQueryComponent())
            append("&X-Plex-Device-Name=").append(it.deviceName.encodeURLQueryComponent())
        }
        accessToken?.let { append("&X-Plex-Token=$it") }
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
    ) {
        runCatching {
            get("$baseUrl/:/timeline") {
                parameter("ratingKey", ratingKey)
                parameter("key", "/library/metadata/$ratingKey")
                parameter("state", state)
                parameter("time", timeMs.toString())
                parameter("duration", durationMs.toString())
                parameter("hasMDE", "1")
                playQueueItemId?.let { parameter("playQueueItemID", it.toString()) }
            }
        }
    }

    suspend fun scrobble(ratingKey: String) {
        runCatching {
            get("$baseUrl/:/scrobble") {
                parameter("key", ratingKey)
                parameter("identifier", "com.plexapp.plugins.library")
            }
        }
    }

    suspend fun unscrobble(ratingKey: String) {
        runCatching {
            get("$baseUrl/:/unscrobble") {
                parameter("key", ratingKey)
                parameter("identifier", "com.plexapp.plugins.library")
            }
        }
    }

    /** Tear down a transcode session when playback stops or restarts. */
    suspend fun stopTranscode(sessionId: String) {
        runCatching {
            get("$baseUrl/video/:/transcode/universal/stop") {
                parameter("session", sessionId)
            }
        }
    }

    /** What the server actually decided for a running session (verification aid). */
    suspend fun getTranscodeSessions(): String =
        get("$baseUrl/transcode/sessions").bodyAsTextSafe()

    /**
     * Server-side resized artwork URL for direct use by an image loader. The
     * token rides as a query parameter because the loader bypasses our client.
     */
    fun imageUrl(path: String, width: Int, height: Int): String {
        val encoded = path.encodeURLQueryComponent(encodeFull = true)
        val token = accessToken?.let { "&X-Plex-Token=$it" } ?: ""
        return "$baseUrl/photo/:/transcode?width=$width&height=$height&minSize=1&upscale=1&url=$encoded$token"
    }

    private suspend fun get(url: String, block: HttpRequestBuilder.() -> Unit = {}) =
        client.get(url) {
            accessToken?.let { header("X-Plex-Token", it) }
            block()
        }
}
