package dev.bettermint.plex.app

import android.content.Context
import android.os.Build
import dev.bettermint.plex.api.PlexClient
import dev.bettermint.plex.api.PlexDeviceInfo
import dev.bettermint.plex.api.PlexTvApi
import dev.bettermint.plex.data.AuthStore
import dev.bettermint.plex.data.ServerRepository

/** Manual DI: one place that builds and owns the app's singletons. */
class AppGraph(context: Context) {

    val authStore = AuthStore(context)

    /**
     * In-memory copy of the account token so the sync tokenProvider never blocks.
     * AuthStore stays the source of truth; both are updated together.
     */
    @Volatile
    var accountToken: String? = null

    private val deviceInfo = PlexDeviceInfo(
        clientIdentifier = authStore.getOrCreateClientIdentifier(),
        platformVersion = Build.VERSION.RELEASE ?: "unknown",
        device = Build.MODEL ?: "Android",
        deviceName = "Bettermint on ${Build.MODEL ?: "Android"}",
    )

    val client = PlexClient.create(deviceInfo, tokenProvider = { accountToken })

    val plexTv = PlexTvApi(client)

    val serverRepository = ServerRepository(client, plexTv, authStore)
}
