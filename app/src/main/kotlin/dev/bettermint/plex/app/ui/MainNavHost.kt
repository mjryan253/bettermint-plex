package dev.bettermint.plex.app.ui

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import dev.bettermint.plex.app.SessionState
import dev.bettermint.plex.app.SessionViewModel
import dev.bettermint.plex.app.player.PlayerScreen
import dev.bettermint.plex.data.LibraryRepository

object Routes {
    const val HOME = "home"
    fun library(key: String, title: String) = "library/$key?title=${Uri.encode(title)}"
    fun details(ratingKey: String) = "details/$ratingKey"
    const val SEARCH = "search"
    fun collections(key: String, title: String) = "collections/$key?title=${Uri.encode(title)}"
    fun collectionItems(ratingKey: String, title: String) =
        "collectionItems/$ratingKey?title=${Uri.encode(title)}"
    const val PLAYLISTS = "playlists"
    fun playlistItems(ratingKey: String, title: String) =
        "playlistItems/$ratingKey?title=${Uri.encode(title)}"
    fun player(
        ratingKey: String,
        mediaIndex: Int = 0,
        startMs: Long = 0,
        audioId: Long? = null,
        subId: Long? = null,
        maxKbps: Int? = null,
    ) = buildString {
        append("player/$ratingKey?mediaIndex=$mediaIndex&startMs=$startMs")
        audioId?.let { append("&audioId=$it") }
        subId?.let { append("&subId=$it") }
        maxKbps?.let { append("&maxKbps=$it") }
    }

    const val SETTINGS = "settings"
}

/** Deterministic device-test shortcut straight into playback. */
data class TestPlay(val ratingKey: String, val startMs: Long, val maxKbps: Int?)

@Composable
fun MainNavHost(
    library: LibraryRepository,
    ready: SessionState.Ready,
    sessionViewModel: SessionViewModel,
    openRatingKey: String? = null,
    testPlay: TestPlay? = null,
) {
    val nav: NavHostController = rememberNavController()

    // Test hook: `adb shell am start ... --es openRatingKey <key>` jumps straight
    // to that item's detail page, so device verification is deterministic.
    androidx.compose.runtime.LaunchedEffect(openRatingKey, testPlay) {
        when {
            testPlay != null -> nav.navigate(
                Routes.player(
                    testPlay.ratingKey,
                    startMs = testPlay.startMs,
                    maxKbps = testPlay.maxKbps,
                ),
            )
            !openRatingKey.isNullOrBlank() -> nav.navigate(Routes.details(openRatingKey))
        }
    }

    NavHost(navController = nav, startDestination = Routes.HOME) {
        composable(Routes.HOME) {
            HomeScreen(library, ready, sessionViewModel, nav)
        }
        composable("library/{key}?title={title}") { entry ->
            val key = entry.arguments?.getString("key").orEmpty()
            val title = entry.arguments?.getString("title").orEmpty()
            LibraryGridScreen(library, key, title, nav)
        }
        composable("details/{ratingKey}") { entry ->
            DetailScreen(library, entry.arguments?.getString("ratingKey").orEmpty(), nav)
        }
        composable(Routes.SEARCH) {
            SearchScreen(library, nav)
        }
        composable(
            "player/{ratingKey}?mediaIndex={mediaIndex}&startMs={startMs}" +
                "&audioId={audioId}&subId={subId}&maxKbps={maxKbps}",
        ) { entry ->
            val playback = sessionViewModel.playbackOrNull()
            if (playback == null) {
                LoadingScreen()
            } else {
                PlayerScreen(
                    library = library,
                    playback = playback,
                    ratingKey = entry.arguments?.getString("ratingKey").orEmpty(),
                    mediaIndex = entry.arguments?.getString("mediaIndex")?.toIntOrNull() ?: 0,
                    startMs = entry.arguments?.getString("startMs")?.toLongOrNull() ?: 0L,
                    audioStreamId = entry.arguments?.getString("audioId")?.toLongOrNull(),
                    subtitleStreamId = entry.arguments?.getString("subId")?.toLongOrNull(),
                    maxKbpsOverride = entry.arguments?.getString("maxKbps")?.toIntOrNull(),
                )
            }
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(sessionViewModel)
        }
        composable("collections/{key}?title={title}") { entry ->
            MetadataListScreen(
                title = entry.arguments?.getString("title").orEmpty(),
                load = { library.collections(entry.arguments?.getString("key").orEmpty()) },
                library = library,
                onItem = { item ->
                    nav.navigate(
                        Routes.collectionItems(item.ratingKey.orEmpty(), item.title),
                    )
                },
            )
        }
        composable("collectionItems/{ratingKey}?title={title}") { entry ->
            MetadataListScreen(
                title = entry.arguments?.getString("title").orEmpty(),
                load = { library.collectionItems(entry.arguments?.getString("ratingKey").orEmpty()) },
                library = library,
                onItem = { item -> nav.navigate(Routes.details(item.ratingKey.orEmpty())) },
            )
        }
        composable(Routes.PLAYLISTS) {
            MetadataListScreen(
                title = "Playlists",
                load = { library.playlists() },
                library = library,
                onItem = { item ->
                    nav.navigate(Routes.playlistItems(item.ratingKey.orEmpty(), item.title))
                },
            )
        }
        composable("playlistItems/{ratingKey}?title={title}") { entry ->
            MetadataListScreen(
                title = entry.arguments?.getString("title").orEmpty(),
                load = { library.playlistItems(entry.arguments?.getString("ratingKey").orEmpty()) },
                library = library,
                onItem = { item -> nav.navigate(Routes.details(item.ratingKey.orEmpty())) },
            )
        }
    }
}
