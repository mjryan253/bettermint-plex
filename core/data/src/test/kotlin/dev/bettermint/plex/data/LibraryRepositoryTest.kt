package dev.bettermint.plex.data

import dev.bettermint.plex.api.PlexClient
import dev.bettermint.plex.api.PlexDeviceInfo
import dev.bettermint.plex.api.ServerApi
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class LibraryRepositoryTest {

    private val mixedHubsBody = """
        {"MediaContainer":{"size":4,"Hub":[
          {"title":"Movies","type":"movie","hubIdentifier":"movie","size":1,
           "Metadata":[{"ratingKey":"1","type":"movie","title":"A Film"}]},
          {"title":"Artists","type":"artist","hubIdentifier":"artist","size":1,
           "Metadata":[{"ratingKey":"2","type":"artist","title":"The Beatles"}]},
          {"title":"Albums","type":"album","hubIdentifier":"album","size":1,
           "Metadata":[{"ratingKey":"3","type":"album","title":"Abbey Road"}]},
          {"title":"Shows","type":"show","hubIdentifier":"show","size":1,
           "Metadata":[{"ratingKey":"4","type":"show","title":"A Show"}]},
          {"title":"Recent Playlists","type":"playlist","hubIdentifier":"home.playlists","size":2,
           "Metadata":[
             {"ratingKey":"5","type":"playlist","playlistType":"audio","title":"All Music"},
             {"ratingKey":"6","type":"playlist","playlistType":"video","title":"Movie Night"}
           ]}
        ]}}
    """.trimIndent()

    private fun repository(): LibraryRepository {
        val client = PlexClient.create(
            deviceInfo = PlexDeviceInfo("id", platformVersion = "1", device = "d", deviceName = "n"),
            tokenProvider = { null },
            engine = MockEngine {
                respond(mixedHubsBody, headers = headersOf(HttpHeaders.ContentType, "application/json"))
            },
        )
        return LibraryRepository(ServerApi(client, "https://server:32400", null), "machine")
    }

    @Test
    fun `music hubs are hidden from search`() = runTest {
        val hubs = repository().search("beat")
        assertEquals(listOf("Movies", "Shows", "Recent Playlists"), hubs.map { it.title })
        assertEquals(listOf("Movie Night"), hubs.last().items.map { it.title })
    }

    @Test
    fun `music hubs are hidden from home and audio playlists are dropped from mixed hubs`() = runTest {
        val hubs = repository().hubs()
        assertEquals(listOf("Movies", "Shows", "Recent Playlists"), hubs.map { it.title })
        val playlists = hubs.last().items
        assertEquals(listOf("Movie Night"), playlists.map { it.title })
    }
}
