package dev.bettermint.plex.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.bettermint.plex.api.model.HomeUser
import dev.bettermint.plex.api.model.LibrarySection
import dev.bettermint.plex.api.model.PlexResource
import dev.bettermint.plex.app.SessionState

@Composable
fun LoadingScreen() {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CircularProgressIndicator()
    }
}

@Composable
fun SignInScreen(state: SessionState.SignIn, onRetry: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Sign in to Plex", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(24.dp))
        when {
            state.error != null -> {
                Text(state.error, color = MaterialTheme.colorScheme.error)
                Spacer(Modifier.height(16.dp))
                Button(onClick = onRetry) { Text("Get a new code") }
            }
            state.code == null -> CircularProgressIndicator()
            else -> {
                Text("On another device, open")
                Text("plex.tv/link", fontWeight = FontWeight.Bold, fontSize = 28.sp)
                Spacer(Modifier.height(8.dp))
                Text("and enter this code:")
                Spacer(Modifier.height(16.dp))
                Text(
                    state.code,
                    fontSize = 64.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 12.sp,
                )
            }
        }
    }
}

@Composable
fun ServerPickerScreen(state: SessionState.PickServer, onSelect: (PlexResource) -> Unit) {
    Column(modifier = Modifier.fillMaxSize().padding(32.dp)) {
        Text("Choose a server", style = MaterialTheme.typography.headlineMedium)
        if (state.error != null) {
            Spacer(Modifier.height(8.dp))
            Text(state.error, color = MaterialTheme.colorScheme.error)
        }
        Spacer(Modifier.height(16.dp))
        if (state.servers.isEmpty()) {
            Text("No servers found on this account.")
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(state.servers, key = { it.clientIdentifier }) { server ->
                Card(onClick = { onSelect(server) }, modifier = Modifier.fillMaxWidth()) {
                    ListItem(
                        headlineContent = { Text(server.name) },
                        supportingContent = {
                            Text(if (server.owned) "Owned server" else "Shared with you")
                        },
                    )
                }
            }
        }
    }
}

@Composable
fun HomeScreen(
    state: SessionState.Ready,
    onSwitchUser: (HomeUser, String?) -> Unit,
) {
    var pinPromptFor by remember { mutableStateOf<HomeUser?>(null) }

    Column(modifier = Modifier.fillMaxSize().padding(32.dp)) {
        Text(state.serverName, style = MaterialTheme.typography.headlineMedium)
        if (state.switchError != null) {
            Spacer(Modifier.height(8.dp))
            Text(state.switchError, color = MaterialTheme.colorScheme.error)
        }
        Spacer(Modifier.height(16.dp))
        Text("Libraries", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(state.libraries, key = { it.key }) { library ->
                Card(onClick = { /* browse arrives in M2 */ }, modifier = Modifier.fillMaxWidth()) {
                    ListItem(
                        headlineContent = { Text(library.title) },
                        supportingContent = { Text(library.type) },
                    )
                }
            }
            if (state.homeUsers.isNotEmpty()) {
                item { HorizontalDivider(Modifier.padding(vertical = 16.dp)) }
                item { Text("Switch user", style = MaterialTheme.typography.titleMedium) }
                items(state.homeUsers, key = { it.uuid }) { user ->
                    Card(
                        onClick = {
                            if (user.protected) pinPromptFor = user else onSwitchUser(user, null)
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        ListItem(
                            headlineContent = { Text(user.title) },
                            supportingContent = {
                                Text(if (user.protected) "PIN protected" else "No PIN")
                            },
                        )
                    }
                }
            }
        }
    }

    pinPromptFor?.let { user ->
        var pin by remember(user.uuid) { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { pinPromptFor = null },
            title = { Text("PIN for ${user.title}") },
            text = {
                OutlinedTextField(value = pin, onValueChange = { pin = it }, label = { Text("PIN") })
            },
            confirmButton = {
                TextButton(onClick = {
                    pinPromptFor = null
                    onSwitchUser(user, pin)
                }) { Text("Switch") }
            },
            dismissButton = {
                TextButton(onClick = { pinPromptFor = null }) { Text("Cancel") }
            },
        )
    }
}

@Composable
fun FailedScreen(state: SessionState.Failed, onRetry: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Something went wrong", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(8.dp))
        Text(state.message)
        Spacer(Modifier.height(16.dp))
        Button(onClick = onRetry) { Text("Retry") }
    }
}
