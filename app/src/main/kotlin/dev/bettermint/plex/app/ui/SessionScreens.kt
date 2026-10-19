package dev.bettermint.plex.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
            itemsIndexed(state.servers, key = { _, s -> s.clientIdentifier }) { index, server ->
                Card(
                    onClick = { onSelect(server) },
                    modifier = Modifier.fillMaxWidth()
                        .then(if (index == 0) Modifier.initialFocus(state.servers.size) else Modifier),
                ) {
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
