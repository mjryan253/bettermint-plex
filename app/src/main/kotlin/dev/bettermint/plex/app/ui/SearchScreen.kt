package dev.bettermint.plex.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import dev.bettermint.plex.api.model.Hub
import dev.bettermint.plex.data.LibraryRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun SearchScreen(library: LibraryRepository, nav: NavHostController) {
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<Hub>>(emptyList()) }
    var searching by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    var pending by remember { mutableStateOf<Job?>(null) }

    Column(Modifier.fillMaxSize().padding(horizontal = 24.dp)) {
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = query,
            onValueChange = { text ->
                query = text
                pending?.cancel()
                pending = scope.launch {
                    delay(400)
                    if (text.length >= 2) {
                        searching = true
                        results = runCatching { library.search(text) }.getOrDefault(emptyList())
                        searching = false
                    } else {
                        results = emptyList()
                    }
                }
            },
            label = { Text("Search your libraries") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(16.dp))
        if (searching) Text("Searching...")
        LazyColumn(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            items(results, key = { it.hubIdentifier ?: it.title }) { hub ->
                Column {
                    Text(hub.title, style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(hub.items, key = { it.ratingKey ?: it.title }) { item ->
                            PosterCard(
                                item = item,
                                library = library,
                                onClick = { nav.navigate(Routes.details(item.ratingKey.orEmpty())) },
                            )
                        }
                    }
                }
            }
        }
    }
}
