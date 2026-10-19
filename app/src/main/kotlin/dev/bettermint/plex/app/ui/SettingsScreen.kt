package dev.bettermint.plex.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.bettermint.plex.app.SessionViewModel
import dev.bettermint.plex.data.AuthStore
import kotlinx.coroutines.launch

/** kbps presets; 0 = original quality. */
private val qualitySteps = listOf(0, 20000, 12000, 8000, 4000, 2000)

private fun qualityLabel(kbps: Int): String =
    if (kbps == 0) "Original" else "${kbps / 1000} Mbps"

@Composable
fun SettingsScreen(sessionViewModel: SessionViewModel) {
    val store = sessionViewModel.authStore()
    val scope = rememberCoroutineScope()
    var prefs by androidx.compose.runtime.remember {
        mutableStateOf<AuthStore.QualityPrefs?>(null)
    }
    val initial by produceState<AuthStore.QualityPrefs?>(initialValue = null) {
        value = store.qualityPrefs()
    }
    val current = prefs ?: initial

    Column(Modifier.fillMaxSize().padding(horizontal = 24.dp)) {
        Spacer(Modifier.height(8.dp))
        Text("Settings", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(16.dp))
        if (current == null) {
            Text("Loading...")
            return@Column
        }

        Text("Home network quality", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            qualitySteps.forEach { kbps ->
                FilterChip(
                    selected = current.maxKbpsLocal == kbps,
                    onClick = {
                        val updated = current.copy(maxKbpsLocal = kbps)
                        prefs = updated
                        scope.launch { store.setQualityPrefs(updated) }
                    },
                    label = { Text(qualityLabel(kbps)) },
                )
            }
        }
        Spacer(Modifier.height(16.dp))
        Text("Remote quality", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            qualitySteps.forEach { kbps ->
                FilterChip(
                    selected = current.maxKbpsRemote == kbps,
                    onClick = {
                        val updated = current.copy(maxKbpsRemote = kbps)
                        prefs = updated
                        scope.launch { store.setQualityPrefs(updated) }
                    },
                    label = { Text(qualityLabel(kbps)) },
                )
            }
        }
    }
}
