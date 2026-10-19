package dev.bettermint.plex.data

import androidx.paging.PagingSource
import androidx.paging.PagingState
import dev.bettermint.plex.api.ServerApi
import dev.bettermint.plex.api.model.Hub
import dev.bettermint.plex.api.model.Metadata

/** Browse/search/detail reads against the connected server. */
class LibraryRepository(
    val api: ServerApi,
    private val machineId: String,
    private val titleResolver: TitleResolver? = null,
) {

    fun itemsPagingSource(sectionKey: String): PagingSource<Int, Metadata> =
        LibraryItemsPagingSource(api, sectionKey, titleResolver)

    /** The latest [hubs] result, so Home can show it instantly on return while it refreshes. */
    @Volatile
    var lastHubs: List<Hub>? = null
        private set

    suspend fun hubs(): List<Hub> = clean(api.getHubs()).also { lastHubs = it }

    suspend fun search(query: String): List<Hub> = clean(api.search(query))

    /** Drops music (hub-level and per item), fixes filename titles, then drops emptied hubs. */
    private suspend fun clean(hubs: List<Hub>): List<Hub> =
        hubs.filterNot { it.type in musicTypes }
            .map { hub -> hub.copy(items = fixTitles(hub.items.filterNot { it.isMusicItem() })) }
            .filter { it.items.isNotEmpty() }

    suspend fun details(ratingKey: String): Metadata? {
        val item = api.getMetadata(ratingKey)
        return titleResolver?.resolve(item) ?: item
    }

    suspend fun children(ratingKey: String): List<Metadata> = fixTitles(api.getChildren(ratingKey))

    suspend fun collections(sectionKey: String): List<Metadata> = api.getCollections(sectionKey)

    suspend fun collectionItems(ratingKey: String): List<Metadata> =
        fixTitles(api.getCollectionChildren(ratingKey))

    suspend fun playlists(): List<Metadata> = api.getPlaylists()

    suspend fun playlistItems(ratingKey: String): List<Metadata> =
        fixTitles(api.getPlaylistItems(ratingKey))

    private suspend fun fixTitles(items: List<Metadata>): List<Metadata> =
        titleResolver?.resolve(items) ?: items

    companion object {
        /** Music is out of scope (Plexamp covers it); hide its hubs by default. */
        private val musicTypes = setOf("artist", "album", "track")

        /** Music tracks/albums/artists and audio playlists are out of scope. */
        private fun Metadata.isMusicItem(): Boolean =
            type in musicTypes || playlistType == "audio"
    }

    suspend fun addToPlaylist(playlistRatingKey: String, itemRatingKey: String) =
        api.addToPlaylist(playlistRatingKey, machineId, itemRatingKey)

    fun imageUrl(path: String?, width: Int, height: Int): String? =
        path?.let { api.imageUrl(it, width, height) }
}

private class LibraryItemsPagingSource(
    private val api: ServerApi,
    private val sectionKey: String,
    private val titleResolver: TitleResolver?,
) : PagingSource<Int, Metadata>() {

    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, Metadata> = try {
        val start = params.key ?: 0
        val page = api.getLibraryItems(sectionKey, start = start, size = params.loadSize)
        val total = page.totalSize ?: page.size
        val next = start + page.items.size
        LoadResult.Page(
            data = titleResolver?.resolve(page.items) ?: page.items,
            prevKey = null,
            nextKey = if (next < total && page.items.isNotEmpty()) next else null,
        )
    } catch (e: Exception) {
        LoadResult.Error(e)
    }

    override fun getRefreshKey(state: PagingState<Int, Metadata>): Int? =
        state.anchorPosition?.let { anchor ->
            maxOf(0, anchor - state.config.initialLoadSize / 2)
        }
}
