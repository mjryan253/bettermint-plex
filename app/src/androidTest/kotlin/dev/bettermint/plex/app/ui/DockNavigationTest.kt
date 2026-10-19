package dev.bettermint.plex.app.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.bettermint.plex.api.PlexClient
import dev.bettermint.plex.api.PlexDeviceInfo
import dev.bettermint.plex.api.ServerApi
import dev.bettermint.plex.api.model.LibrarySection
import dev.bettermint.plex.app.AppGraph
import dev.bettermint.plex.app.SessionState
import dev.bettermint.plex.app.SessionViewModel
import dev.bettermint.plex.app.ui.theme.BettermintTheme
import dev.bettermint.plex.data.LibraryRepository
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * UI regression tests for Quick Launch dock navigation. Both library sections
 * share the one library/{key} nav destination; a state-restoration bug once
 * sent every library tab to the first-visited section's grid.
 *
 * All Plex traffic is served by MockEngines; nothing touches the network.
 */
@RunWith(AndroidJUnit4::class)
class DockNavigationTest {

    @get:Rule
    val compose = createComposeRule()

    private val jsonHeaders = headersOf(HttpHeaders.ContentType, "application/json")

    private val hubsBody = """
        {"MediaContainer":{"size":1,"Hub":[
          {"title":"Continue Watching","type":"movie","hubIdentifier":"home.continue","size":1,
           "Metadata":[{"ratingKey":"10","type":"movie","title":"Hub Movie"}]}
        ]}}
    """.trimIndent()

    private fun itemsBody(title: String) = """
        {"MediaContainer":{"size":1,"totalSize":1,"Metadata":[
          {"ratingKey":"1$title","type":"movie","title":"$title"}
        ]}}
    """.trimIndent()

    private val searchBody = """
        {"MediaContainer":{"size":1,"Hub":[
          {"title":"Shows","type":"show","hubIdentifier":"show","size":1,
           "Metadata":[{"ratingKey":"30","type":"show","title":"Search Show"}]}
        ]}}
    """.trimIndent()

    private fun testLibrary(): LibraryRepository {
        val client = PlexClient.create(
            deviceInfo = PlexDeviceInfo("test-id", platformVersion = "1", device = "d", deviceName = "n"),
            tokenProvider = { null },
            engine = MockEngine { request ->
                val path = request.url.encodedPath
                when {
                    path.contains("/hubs/search") -> respond(searchBody, headers = jsonHeaders)
                    path.contains("/hubs") -> respond(hubsBody, headers = jsonHeaders)
                    path.contains("/library/sections/1/all") ->
                        respond(itemsBody("Grid Movie"), headers = jsonHeaders)
                    path.contains("/library/sections/2/all") ->
                        respond(itemsBody("Grid Show"), headers = jsonHeaders)
                    else -> respond("{}", HttpStatusCode.NotFound, jsonHeaders)
                }
            },
        )
        return LibraryRepository(ServerApi(client, "https://server:32400", null), "machine")
    }

    private val ready = SessionState.Ready(
        serverName = "Test Server",
        libraries = listOf(
            LibrarySection(key = "1", title = "Movies", type = "movie"),
            LibrarySection(key = "2", title = "TV Shows", type = "show"),
        ),
    )

    private fun waitForText(text: String) {
        compose.waitUntil(timeoutMillis = 10_000) {
            compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test
    fun libraryTabsOpenTheirOwnSections() {
        val graph = sharedGraph()
        compose.setContent {
            BettermintTheme {
                MainNavHost(
                    library = testLibrary(),
                    ready = ready,
                    sessionViewModel = SessionViewModel(graph),
                )
            }
        }
        waitForText("Hub Movie")

        compose.onNodeWithContentDescription("Movies").performClick()
        waitForText("Grid Movie")
        compose.onNodeWithText("Grid Movie").assertIsDisplayed()

        // Regression: after visiting Movies, the TV Shows tab must load its
        // own section, not restore the Movies back-stack entry.
        compose.onNodeWithContentDescription("TV Shows").performClick()
        waitForText("Grid Show")
        compose.onNodeWithText("Grid Show").assertIsDisplayed()
        compose.onNodeWithText("TV Shows").assertIsDisplayed()
    }

    @Test
    fun searchFieldReturnsCardedResults() {
        val graph = sharedGraph()
        compose.setContent {
            BettermintTheme {
                MainNavHost(
                    library = testLibrary(),
                    ready = ready,
                    sessionViewModel = SessionViewModel(graph),
                )
            }
        }
        waitForText("Hub Movie")

        compose.onNodeWithContentDescription("Search").performClick()
        waitForText("Just type...")
        compose.onNode(hasSetTextAction()).performTextInput("search show")
        // Covers the 400ms debounce, the search call, and the result cards.
        waitForText("Search Show")
        compose.onNodeWithText("Search Show").assertIsDisplayed()
    }

    companion object {
        /**
         * One AppGraph per test process: AuthStore owns a DataStore, and two
         * instances over the same file would crash. The 404 engine keeps the
         * SessionViewModel's boot sequence fully offline.
         */
        private val graphHolder by lazy {
            AppGraph(
                InstrumentationRegistry.getInstrumentation().targetContext,
                engine = MockEngine {
                    respond("{}", HttpStatusCode.NotFound, headersOf(HttpHeaders.ContentType, "application/json"))
                },
            )
        }

        fun sharedGraph(): AppGraph = graphHolder
    }
}
