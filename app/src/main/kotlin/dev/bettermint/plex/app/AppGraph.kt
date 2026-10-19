package dev.bettermint.plex.app

import android.content.Context
import android.os.Build
import dev.bettermint.plex.BuildConfig
import dev.bettermint.plex.api.PlexClient
import dev.bettermint.plex.api.PlexDeviceInfo
import dev.bettermint.plex.api.PlexTvApi
import dev.bettermint.plex.data.AuthStore
import dev.bettermint.plex.data.LibraryRepository
import dev.bettermint.plex.data.PlaybackRepository
import dev.bettermint.plex.data.ServerRepository
import dev.bettermint.plex.data.TitleResolver
import okhttp3.OkHttpClient

/**
 * Manual DI: one place that builds and owns the app's singletons.
 * [engine] lets instrumented tests swap in a MockEngine so nothing
 * touches the network; production passes nothing.
 */
class AppGraph(context: Context, engine: io.ktor.client.engine.HttpClientEngine? = null) {

    val authStore = AuthStore(context)

    val deviceInfo = PlexDeviceInfo(
        clientIdentifier = authStore.getOrCreateClientIdentifier(),
        platformVersion = Build.VERSION.RELEASE ?: "unknown",
        device = Build.MODEL ?: "Android",
        deviceName = "Bettermint on ${Build.MODEL ?: "Android"}",
    )

    /** The app's single HTTP stack: Plex API calls and poster loading share its pool. */
    val okHttp = OkHttpClient()

    val client = PlexClient.create(
        deviceInfo,
        tokenProvider = { authStore.accountToken },
        engine = engine,
        logging = BuildConfig.DEBUG,
        okHttp = okHttp,
    )

    val plexTv = PlexTvApi(client)

    val serverRepository = ServerRepository(client, plexTv, authStore, deviceInfo)

    val titleResolver = TitleResolver(plexTv)

    /** Set by SessionViewModel once a server connection is established. */
    @Volatile
    var library: LibraryRepository? = null

    @Volatile
    var playback: PlaybackRepository? = null
}
