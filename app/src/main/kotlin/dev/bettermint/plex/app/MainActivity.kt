package dev.bettermint.plex.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import dev.bettermint.plex.app.ui.FailedScreen
import dev.bettermint.plex.app.ui.HomeScreen
import dev.bettermint.plex.app.ui.LoadingScreen
import dev.bettermint.plex.app.ui.ServerPickerScreen
import dev.bettermint.plex.app.ui.SignInScreen

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val graph = (application as BettermintApp).graph
        val viewModel = ViewModelProvider(
            this,
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    SessionViewModel(graph) as T
            },
        )[SessionViewModel::class.java]

        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    val state by viewModel.state.collectAsState()
                    when (val s = state) {
                        is SessionState.Loading -> LoadingScreen()
                        is SessionState.SignIn -> SignInScreen(s, onRetry = viewModel::startSignIn)
                        is SessionState.PickServer -> ServerPickerScreen(s, onSelect = viewModel::selectServer)
                        is SessionState.Ready -> HomeScreen(s, onSwitchUser = viewModel::switchHomeUser)
                        is SessionState.Failed -> FailedScreen(s, onRetry = viewModel::retry)
                    }
                }
            }
        }
    }
}
