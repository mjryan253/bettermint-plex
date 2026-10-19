package dev.bettermint.plex.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking

private val Context.dataStore by preferencesDataStore(name = "auth")

/** Persisted session state: client identity, account token, selected server. */
class AuthStore(private val context: Context) {

    private object Keys {
        val clientId = stringPreferencesKey("client_identifier")
        val accountToken = stringPreferencesKey("account_token")
        val serverMachineId = stringPreferencesKey("server_machine_id")
        val serverName = stringPreferencesKey("server_name")
        val serverUri = stringPreferencesKey("server_uri")
        val serverToken = stringPreferencesKey("server_token")
    }

    /**
     * Stable device identity, generated once per install. Read synchronously at
     * startup because every request's X-Plex-Client-Identifier needs it.
     */
    fun getOrCreateClientIdentifier(): String = runBlocking {
        val existing = context.dataStore.data.first()[Keys.clientId]
        existing ?: UUID.randomUUID().toString().also { generated ->
            context.dataStore.edit { it[Keys.clientId] = generated }
        }
    }

    val accountToken: Flow<String?> = context.dataStore.data.map { it[Keys.accountToken] }

    suspend fun currentAccountToken(): String? = accountToken.first()

    suspend fun setAccountToken(token: String) {
        context.dataStore.edit { it[Keys.accountToken] = token }
    }

    data class SelectedServer(
        val machineId: String,
        val name: String,
        val uri: String,
        val accessToken: String?,
    )

    suspend fun selectedServer(): SelectedServer? {
        val prefs = context.dataStore.data.first()
        val machineId = prefs[Keys.serverMachineId] ?: return null
        val name = prefs[Keys.serverName] ?: return null
        val uri = prefs[Keys.serverUri] ?: return null
        return SelectedServer(machineId, name, uri, prefs[Keys.serverToken])
    }

    suspend fun setSelectedServer(server: SelectedServer) {
        context.dataStore.edit {
            it[Keys.serverMachineId] = server.machineId
            it[Keys.serverName] = server.name
            it[Keys.serverUri] = server.uri
            server.accessToken?.let { token -> it[Keys.serverToken] = token }
        }
    }

    suspend fun clearSession() {
        context.dataStore.edit {
            it.remove(Keys.accountToken)
            it.remove(Keys.serverMachineId)
            it.remove(Keys.serverName)
            it.remove(Keys.serverUri)
            it.remove(Keys.serverToken)
        }
    }
}
