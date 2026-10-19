package dev.bettermint.plex.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import java.util.UUID
import kotlinx.coroutines.flow.first
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
        val serverLocal = stringPreferencesKey("server_local")
        val maxKbpsLocal = intPreferencesKey("max_kbps_local")
        val maxKbpsRemote = intPreferencesKey("max_kbps_remote")
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

    /**
     * In-memory copy of the account token for the HTTP client's synchronous
     * token provider. Set whenever the token is read or written here.
     */
    @Volatile
    var accountToken: String? = null
        private set

    suspend fun currentAccountToken(): String? =
        context.dataStore.data.first()[Keys.accountToken].also { accountToken = it }

    suspend fun setAccountToken(token: String) {
        context.dataStore.edit { it[Keys.accountToken] = token }
        accountToken = token
    }

    data class SelectedServer(
        val machineId: String,
        val name: String,
        val uri: String,
        val accessToken: String?,
        val localConnection: Boolean = true,
    )

    suspend fun selectedServer(): SelectedServer? {
        val prefs = context.dataStore.data.first()
        val machineId = prefs[Keys.serverMachineId] ?: return null
        val name = prefs[Keys.serverName] ?: return null
        val uri = prefs[Keys.serverUri] ?: return null
        return SelectedServer(
            machineId,
            name,
            uri,
            prefs[Keys.serverToken],
            prefs[Keys.serverLocal] != "false",
        )
    }

    suspend fun setSelectedServer(server: SelectedServer) {
        context.dataStore.edit {
            it[Keys.serverMachineId] = server.machineId
            it[Keys.serverName] = server.name
            it[Keys.serverUri] = server.uri
            it[Keys.serverLocal] = server.localConnection.toString()
            // A server without its own token must not inherit the previous server's.
            if (server.accessToken != null) it[Keys.serverToken] = server.accessToken else it.remove(Keys.serverToken)
        }
    }

    /** Quality caps in kbps; 0 means original quality (no cap). */
    data class QualityPrefs(val maxKbpsLocal: Int, val maxKbpsRemote: Int)

    suspend fun qualityPrefs(): QualityPrefs {
        val prefs = context.dataStore.data.first()
        return QualityPrefs(prefs[Keys.maxKbpsLocal] ?: 0, prefs[Keys.maxKbpsRemote] ?: 0)
    }

    suspend fun setQualityPrefs(quality: QualityPrefs) {
        context.dataStore.edit {
            it[Keys.maxKbpsLocal] = quality.maxKbpsLocal
            it[Keys.maxKbpsRemote] = quality.maxKbpsRemote
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
        accountToken = null
    }
}
