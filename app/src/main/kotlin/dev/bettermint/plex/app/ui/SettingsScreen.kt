package dev.bettermint.plex.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
    var prefs by remember { mutableStateOf<AuthStore.QualityPrefs?>(null) }
    LaunchedEffect(store) { prefs = store.qualityPrefs() }

    fun save(updated: AuthStore.QualityPrefs) {
        prefs = updated
        scope.launch { store.setQualityPrefs(updated) }
    }

    CardPage("Settings") {
        val current = prefs
        if (current == null) {
            Text("Loading...")
            return@CardPage
        }
        QualityRow("Home network quality", current.maxKbpsLocal) { save(current.copy(maxKbpsLocal = it)) }
        Spacer(Modifier.height(16.dp))
        QualityRow("Remote quality", current.maxKbpsRemote) { save(current.copy(maxKbpsRemote = it)) }
    }
}

@Composable
private fun QualityRow(label: String, selectedKbps: Int, onPick: (Int) -> Unit) {
    Text(label, style = MaterialTheme.typography.titleMedium)
    Spacer(Modifier.height(8.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        qualitySteps.forEach { kbps ->
            LinenChip(selected = selectedKbps == kbps, onClick = { onPick(kbps) }, label = qualityLabel(kbps))
        }
    }
}
