package dev.bettermint.plex.data

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Exercises AuthStore against the real Android DataStore on a device.
 * This is the persistence the JVM tier cannot cover.
 */
@RunWith(AndroidJUnit4::class)
class AuthStoreInstrumentedTest {

    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val store = AuthStore(context)

    @Before
    @After
    fun reset() = runBlocking {
        store.clearSession()
        store.setQualityPrefs(AuthStore.QualityPrefs(0, 0))
    }

    @Test
    fun clientIdentifier_is_stable_across_calls() {
        val first = store.getOrCreateClientIdentifier()
        val second = store.getOrCreateClientIdentifier()
        assertTrue(first.isNotBlank())
        assertEquals(first, second)
    }

    @Test
    fun account_token_round_trips() = runBlocking {
        assertNull(store.currentAccountToken())
        store.setAccountToken("real-token")
        assertEquals("real-token", store.currentAccountToken())
    }

    @Test
    fun selected_server_round_trips_with_local_flag() = runBlocking {
        assertNull(store.selectedServer())
        store.setSelectedServer(
            AuthStore.SelectedServer(
                machineId = "machine-1",
                name = "thegremlin",
                uri = "https://192-168-1-9.plex.direct:32400",
                accessToken = "server-token",
                localConnection = false,
            ),
        )
        val saved = store.selectedServer()
        assertNotNull(saved)
        assertEquals("machine-1", saved!!.machineId)
        assertEquals("thegremlin", saved.name)
        assertEquals("server-token", saved.accessToken)
        assertEquals(false, saved.localConnection)
    }

    @Test
    fun quality_prefs_round_trip() = runBlocking {
        store.setQualityPrefs(AuthStore.QualityPrefs(maxKbpsLocal = 8000, maxKbpsRemote = 2000))
        val prefs = store.qualityPrefs()
        assertEquals(8000, prefs.maxKbpsLocal)
        assertEquals(2000, prefs.maxKbpsRemote)
    }

    @Test
    fun clear_session_keeps_client_id_but_drops_token_and_server() = runBlocking {
        val id = store.getOrCreateClientIdentifier()
        store.setAccountToken("t")
        store.setSelectedServer(
            AuthStore.SelectedServer("m", "n", "u", "st", true),
        )
        store.clearSession()
        assertNull(store.currentAccountToken())
        assertNull(store.selectedServer())
        assertEquals("client id survives sign-out", id, store.getOrCreateClientIdentifier())
    }
}
