package dev.bettermint.plex.data

import androidx.paging.PagingSource
import androidx.paging.PagingState
import dev.bettermint.plex.api.ServerApi
import dev.bettermint.plex.api.model.Hub
import dev.bettermint.plex.api.model.Metadata

/** Browse/search/detail reads against the connected server. */
class LibraryRepository(
    val api: ServerApi,
    val machineId: String,
    private val titleResolver: TitleResolver? = null,
) {

    fun itemsPagingSource(sectionKey: String, sort: String? = null): PagingSource<Int, Metadata> =
        LibraryItemsPagingSource(api, sectionKey, sort, titleResolver)

    suspend fun hubs(): List<Hub> =
        api.getHubs().map { it.copy(items = fixTitles(it.items)) }

    suspend fun search(query: String): List<Hub> =
        api.search(query).filter { it.items.isNotEmpty() }
            .map { it.copy(items = fixTitles(it.items)) }

    suspend fun details(ratingKey: String): Metadata? {
        val item = api.getMetadata(ratingKey, includeExtras = true)
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

    suspend fun addToPlaylist(playlistRatingKey: String, itemRatingKey: String) =
        api.addToPlaylist(playlistRatingKey, machineId, itemRatingKey)

    fun imageUrl(path: String?, width: Int, height: Int): String? =
        path?.let { api.imageUrl(it, width, height) }
}

private class LibraryItemsPagingSource(
    private val api: ServerApi,
    private val sectionKey: String,
    private val sort: String?,
    private val titleResolver: TitleResolver?,
) : PagingSource<Int, Metadata>() {

    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, Metadata> = try {
        val start = params.key ?: 0
        val page = api.getLibraryItems(sectionKey, start = start, size = params.loadSize, sort = sort)
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
