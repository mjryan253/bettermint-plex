package dev.bettermint.plex.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import dev.bettermint.plex.api.model.Hub
import dev.bettermint.plex.api.model.HomeUser
import dev.bettermint.plex.app.SessionState
import dev.bettermint.plex.app.SessionViewModel
import dev.bettermint.plex.data.LibraryRepository

@Composable
fun HomeScreen(
    library: LibraryRepository,
    ready: SessionState.Ready,
    sessionViewModel: SessionViewModel,
    nav: NavHostController,
) {
    val hubs by produceState<List<Hub>?>(initialValue = null, library) {
        value = runCatching { library.hubs().filter { it.items.isNotEmpty() } }.getOrDefault(emptyList())
    }
    var pinPromptFor by remember { mutableStateOf<HomeUser?>(null) }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Spacer(Modifier.height(10.dp))
            WebOsHeaderPill(ready.serverName)
            if (ready.switchError != null) {
                Text(ready.switchError, color = MaterialTheme.colorScheme.error)
            }
        }

        when (val loaded = hubs) {
            null -> item { Text("Loading your library...") }
            else -> itemsIndexed(loaded, key = { _, h -> h.hubIdentifier ?: h.title }) { hubIndex, hub ->
                Column {
                    SectionHeader(hub.title)
                    Spacer(Modifier.height(10.dp))
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.focusRestorer(),
                    ) {
                        itemsIndexed(hub.items, key = { _, it -> it.ratingKey ?: it.title }) { index, item ->
                            PosterCard(
                                item = item,
                                library = library,
                                onClick = { nav.navigate(Routes.details(item.ratingKey.orEmpty())) },
                                modifier = if (hubIndex == 0 && index == 0) Modifier.initialFocus() else Modifier,
                            )
                        }
                    }
                }
            }
        }

        if (ready.homeUsers.size > 1) {
            item {
                Column {
                    SectionHeader("Switch user")
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ready.homeUsers.forEach { user ->
                            AssistChip(
                                onClick = {
                                    if (user.protected) {
                                        pinPromptFor = user
                                    } else {
                                        sessionViewModel.switchHomeUser(user, null)
                                    }
                                },
                                label = { Text(user.title) },
                            )
                        }
                    }
                }
            }
        }

        item { Spacer(Modifier.height(24.dp)) }
    }

    pinPromptFor?.let { user ->
        var pin by remember(user.uuid) { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { pinPromptFor = null },
            title = { Text("PIN for ${user.title}") },
            text = { OutlinedTextField(value = pin, onValueChange = { pin = it }, label = { Text("PIN") }) },
            confirmButton = {
                TextButton(onClick = {
                    pinPromptFor = null
                    sessionViewModel.switchHomeUser(user, pin)
                }) { Text("Switch") }
            },
            dismissButton = { TextButton(onClick = { pinPromptFor = null }) { Text("Cancel") } },
        )
    }
}
