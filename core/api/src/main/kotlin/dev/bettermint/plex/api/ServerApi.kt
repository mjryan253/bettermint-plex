package dev.bettermint.plex.api

import dev.bettermint.plex.api.model.Hub
import dev.bettermint.plex.api.model.HubsResponse
import dev.bettermint.plex.api.model.Identity
import dev.bettermint.plex.api.model.IdentityResponse
import dev.bettermint.plex.api.model.LibrarySection
import dev.bettermint.plex.api.model.Metadata
import dev.bettermint.plex.api.model.MetadataContainer
import dev.bettermint.plex.api.model.MetadataResponse
import dev.bettermint.plex.api.model.SectionsResponse
import io.ktor.client.HttpClient
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.put
import io.ktor.http.encodeURLQueryComponent
import io.ktor.http.isSuccess

/**
 * Plex Media Server endpoints. [baseUrl] is the chosen connection URI,
 * [accessToken] the per-server token from the resources listing.
 */
class ServerApi(
    private val client: HttpClient,
    val baseUrl: String,
    private val accessToken: String?,
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
        }.bodyOrThrow<MetadataResponse>().mediaContainer.items.firstOrNull()

    /** Seasons of a show, or episodes of a season. */
    suspend fun getChildren(ratingKey: String): List<Metadata> =
        get("$baseUrl/library/metadata/$ratingKey/children")
            .bodyOrThrow<MetadataResponse>().mediaContainer.items

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
