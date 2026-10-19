package dev.bettermint.plex.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.bettermint.plex.api.ServerApi
import dev.bettermint.plex.api.model.HomeUser
import dev.bettermint.plex.data.LibraryRepository
import dev.bettermint.plex.data.PlaybackRepository
import dev.bettermint.plex.api.model.LibrarySection
import dev.bettermint.plex.api.model.PlexResource
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

sealed interface SessionState {
    data object Loading : SessionState
    data class SignIn(val code: String?, val error: String? = null) : SessionState
    data class PickServer(val servers: List<PlexResource>, val error: String? = null) : SessionState
    data class Ready(
        val serverName: String,
        val libraries: List<LibrarySection>,
        val homeUsers: List<HomeUser> = emptyList(),
        val switchError: String? = null,
    ) : SessionState

    data class Failed(val message: String) : SessionState
}

class SessionViewModel(private val graph: AppGraph) : ViewModel() {

    private val _state = MutableStateFlow<SessionState>(SessionState.Loading)
    val state: StateFlow<SessionState> = _state

    private var pinJob: Job? = null

    init {
        viewModelScope.launch { boot() }
    }

    private suspend fun boot() {
        val token = graph.authStore.currentAccountToken()
        if (token == null) {
            startSignIn()
            return
        }
        graph.accountToken = token
        val cached = graph.serverRepository.cachedServer()
        if (cached != null && runCatching { loadReady(cached) }.isSuccess) return
        loadServerPicker()
    }

    fun startSignIn() {
        pinJob?.cancel()
        pinJob = viewModelScope.launch {
            _state.value = SessionState.SignIn(code = null)
            try {
                val pin = graph.plexTv.createPin()
                _state.value = SessionState.SignIn(code = pin.code)
                val deadline = System.currentTimeMillis() + (pin.expiresIn ?: 900) * 1000
                while (System.currentTimeMillis() < deadline) {
                    delay(2_000)
                    val polled = graph.plexTv.getPin(pin.id)
                    val token = polled.authToken
                    if (!token.isNullOrBlank()) {
                        graph.authStore.setAccountToken(token)
                        graph.accountToken = token
                        loadServerPicker()
                        return@launch
                    }
                }
                _state.value = SessionState.SignIn(code = null, error = "Code expired. Get a new one.")
            } catch (e: Exception) {
                _state.value = SessionState.SignIn(code = null, error = e.message ?: "Sign-in failed")
            }
        }
    }

    private suspend fun loadServerPicker() {
        _state.value = SessionState.Loading
        try {
            val servers = graph.serverRepository.listServers()
            _state.value = SessionState.PickServer(servers)
        } catch (e: Exception) {
            _state.value = SessionState.Failed(e.message ?: "Could not list servers")
        }
    }

    fun selectServer(server: PlexResource) {
        viewModelScope.launch {
            _state.value = SessionState.Loading
            val api = runCatching { graph.serverRepository.connect(server) }.getOrNull()
            if (api == null) {
                _state.value = SessionState.PickServer(
                    servers = runCatching { graph.serverRepository.listServers() }.getOrDefault(emptyList()),
                    error = "Could not reach ${server.name}",
                )
                return@launch
            }
            runCatching { loadReady(api) }.onFailure {
                _state.value = SessionState.Failed(it.message ?: "Could not load libraries")
            }
        }
    }

    private suspend fun loadReady(api: ServerApi) {
        val selected = graph.authStore.selectedServer()
        graph.library = LibraryRepository(
            api,
            machineId = selected?.machineId ?: "",
            titleResolver = graph.titleResolver,
        )
        graph.playback = PlaybackRepository(api, graph.authStore)
        val libraries = api.getLibrarySections()
        val users = runCatching { graph.plexTv.getHomeUsers().users }.getOrDefault(emptyList())
        _state.value = SessionState.Ready(selected?.name ?: "Plex Server", libraries, users)
    }

    fun switchHomeUser(user: HomeUser, pin: String?) {
        val current = _state.value as? SessionState.Ready ?: return
        viewModelScope.launch {
            _state.value = SessionState.Loading
            try {
                val switched = graph.plexTv.switchHomeUser(user.uuid, pin)
                graph.authStore.setAccountToken(switched.authToken)
                graph.accountToken = switched.authToken
                // Server access tokens are per-user: rediscover and reconnect.
                val machineId = graph.authStore.selectedServer()?.machineId
                val server = graph.serverRepository.listServers()
                    .firstOrNull { it.clientIdentifier == machineId }
                val api = server?.let { graph.serverRepository.connect(it) }
                if (api != null) loadReady(api) else loadServerPicker()
            } catch (e: Exception) {
                _state.value = current.copy(switchError = "Switch failed: ${e.message}")
            }
        }
    }

    fun retry() {
        viewModelScope.launch { boot() }
    }

    fun playbackOrNull(): PlaybackRepository? = graph.playback

    fun authStore() = graph.authStore
}
