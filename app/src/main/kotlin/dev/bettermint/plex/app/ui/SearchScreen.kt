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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import dev.bettermint.plex.api.model.Hub
import dev.bettermint.plex.app.ui.theme.LocalWebOsPalette
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

    val palette = LocalWebOsPalette.current

    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        Spacer(Modifier.height(12.dp))
        // The webOS "Just type..." bar: a translucent rounded field over wallpaper.
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
            placeholder = { Text("Just type...", color = palette.warmWhite.copy(alpha = 0.55f)) },
            singleLine = true,
            shape = CircleShape,
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = palette.warmWhite,
                unfocusedTextColor = palette.warmWhite,
                focusedContainerColor = Color.White.copy(alpha = 0.12f),
                unfocusedContainerColor = Color.White.copy(alpha = 0.08f),
                focusedBorderColor = palette.glow,
                unfocusedBorderColor = Color.White.copy(alpha = 0.25f),
                cursorColor = palette.glow,
            ),
            modifier = Modifier.fillMaxWidth().initialFocus(),
        )
        Spacer(Modifier.height(16.dp))
        if (searching) Text("Searching...", color = palette.warmWhite)
        LazyColumn(verticalArrangement = Arrangement.spacedBy(18.dp)) {
            items(results, key = { it.hubIdentifier ?: it.title }) { hub ->
                FloatingCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        SectionHeader(
                            hub.title,
                            labelColor = palette.graphite,
                            ruleColor = palette.graphite.copy(alpha = 0.45f),
                        )
                        Spacer(Modifier.height(12.dp))
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
            item { Spacer(Modifier.height(8.dp)) }
        }
    }
}
