package dev.bettermint.plex.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.layout.fillMaxWidth
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
import dev.bettermint.plex.api.model.HomeUser
import dev.bettermint.plex.app.SessionState
import dev.bettermint.plex.app.SessionViewModel
import dev.bettermint.plex.app.ui.theme.LocalWebOsPalette
import dev.bettermint.plex.data.LibraryRepository

@Composable
fun HomeScreen(
    library: LibraryRepository,
    ready: SessionState.Ready,
    sessionViewModel: SessionViewModel,
    nav: NavHostController,
) {
    // Show the last hubs at once (no flash, scroll kept on Back), then refresh so
    // Continue Watching reflects what was just played.
    val hubs by produceState(initialValue = library.lastHubs, library) {
        value = runCatching { library.hubs() }.getOrDefault(value ?: emptyList())
    }
    var pinPromptFor by remember { mutableStateOf<HomeUser?>(null) }

    val palette = LocalWebOsPalette.current

    // webOS card view: each hub is a light floating card over the wallpaper,
    // with margins keeping the scene visible around every card.
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        item {
            Spacer(Modifier.height(12.dp))
            WebOsHeaderPill(ready.serverName)
            if (ready.switchError != null) {
                Text(ready.switchError, color = MaterialTheme.colorScheme.error)
            }
        }

        when (val loaded = hubs) {
            null -> item { Text("Loading your library...", color = palette.warmWhite) }
            else -> itemsIndexed(loaded, key = { _, h -> h.hubIdentifier ?: h.title }) { hubIndex, hub ->
                FloatingCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        PosterRow(
                            title = hub.title,
                            items = hub.items,
                            library = library,
                            onItem = { nav.navigate(Routes.details(it.ratingKey.orEmpty())) },
                            onLinen = true,
                            focusFirst = hubIndex == 0,
                        )
                    }
                }
            }
        }

        if (ready.homeUsers.size > 1) {
            item {
                FloatingCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        SectionHeader("Switch user", onLinen = true)
                        Spacer(Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            ready.homeUsers.forEach { user ->
                                LinenChip(
                                    selected = false,
                                    onClick = {
                                        if (user.protected) {
                                            pinPromptFor = user
                                        } else {
                                            sessionViewModel.switchHomeUser(user, null)
                                        }
                                    },
                                    label = user.title,
                                )
                            }
                        }
                    }
                }
            }
        }

        item { Spacer(Modifier.height(8.dp)) }
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
