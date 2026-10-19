package dev.bettermint.plex.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.cachedIn
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import dev.bettermint.plex.data.LibraryRepository

/**
 * Holds a section's pages for the lifetime of its nav entry, so returning from
 * a detail page shows the loaded grid at once (scroll kept) instead of refetching.
 */
private class LibraryGridViewModel(library: LibraryRepository, sectionKey: String) : ViewModel() {
    val items = Pager(PagingConfig(pageSize = 60, initialLoadSize = 60, prefetchDistance = 30)) {
        library.itemsPagingSource(sectionKey)
    }.flow.cachedIn(viewModelScope)
}

@Composable
fun LibraryGridScreen(
    library: LibraryRepository,
    sectionKey: String,
    title: String,
    nav: NavHostController,
) {
    val grid = viewModel(key = sectionKey) { LibraryGridViewModel(library, sectionKey) }
    val items = grid.items.collectAsLazyPagingItems()

    CardPage(
        title = title,
        trailing = {
            LinenChip(
                selected = false,
                onClick = { nav.navigate(Routes.collections(sectionKey, "$title Collections")) },
                label = "Collections",
            )
        },
    ) {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = POSTER_WIDTH_DP.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.focusRestorer(),
        ) {
            items(
                count = items.itemCount,
                key = items.itemKey { it.ratingKey ?: it.title },
            ) { index ->
                items[index]?.let { item ->
                    PosterCard(
                        item = item,
                        library = library,
                        onClick = { nav.navigate(Routes.details(item.ratingKey.orEmpty())) },
                        modifier = if (index == 0) Modifier.initialFocus() else Modifier,
                    )
                }
            }
        }
    }
}
