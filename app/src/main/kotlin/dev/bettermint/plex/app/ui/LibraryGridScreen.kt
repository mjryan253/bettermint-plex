package dev.bettermint.plex.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.material3.AssistChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import dev.bettermint.plex.data.LibraryRepository

@Composable
fun LibraryGridScreen(
    library: LibraryRepository,
    sectionKey: String,
    title: String,
    nav: NavHostController,
) {
    val pager = remember(sectionKey) {
        Pager(PagingConfig(pageSize = 60, initialLoadSize = 60, prefetchDistance = 30)) {
            library.itemsPagingSource(sectionKey)
        }
    }
    val items = pager.flow.collectAsLazyPagingItems()

    Column(Modifier.fillMaxSize().padding(horizontal = 24.dp)) {
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(title, style = MaterialTheme.typography.headlineMedium)
            AssistChip(
                onClick = { nav.navigate(Routes.collections(sectionKey, "$title Collections")) },
                label = { Text("Collections") },
            )
        }
        Spacer(Modifier.height(12.dp))
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
