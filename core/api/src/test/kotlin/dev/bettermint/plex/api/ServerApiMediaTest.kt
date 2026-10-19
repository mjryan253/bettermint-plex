package dev.bettermint.plex.api

import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpMethod
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ServerApiMediaTest {

    @Test
    fun `library items page sends container headers and parses totalSize`() = runTest {
        var startHeader: String? = null
        var sizeHeader: String? = null
        var sortParam: String? = null
        val api = ServerApi(
            testClient { request ->
                startHeader = request.headers["X-Plex-Container-Start"]
                sizeHeader = request.headers["X-Plex-Container-Size"]
                sortParam = request.url.parameters["sort"]
                respond(fixture("library_items_page.json"), headers = jsonHeaders)
            },
            "https://server.example:32400",
            accessToken = "server-token",
        )
        val page = api.getLibraryItems("1", start = 40, size = 2, sort = "titleSort:asc")

        assertEquals("40", startHeader)
        assertEquals("2", sizeHeader)
        assertEquals("titleSort:asc", sortParam)
        assertEquals(1234, page.totalSize)
        assertEquals(2, page.items.size)
        assertEquals("Example Movie", page.items.first().title)
        assertEquals(1200000L, page.items[1].viewOffset)
    }

    @Test
    fun `hubs parse with nested episode metadata`() = runTest {
        val api = ServerApi(
            testClient { respond(fixture("hubs.json"), headers = jsonHeaders) },
            "https://server.example:32400",
            accessToken = null,
        )
        val hubs = api.getHubs()

        assertEquals(2, hubs.size)
        val continueWatching = hubs.first()
        assertEquals("Continue Watching", continueWatching.title)
        assertEquals("home.continue", continueWatching.hubIdentifier)
        val episode = continueWatching.items.single()
        assertEquals("episode", episode.type)
        assertEquals("Example Show", episode.grandparentTitle)
        assertEquals(900000L, episode.viewOffset)
    }

    @Test
    fun `search sends the query parameter`() = runTest {
        var query: String? = null
        val api = ServerApi(
            testClient { request ->
                query = request.url.parameters["query"]
                respond(fixture("hubs.json"), headers = jsonHeaders)
            },
            "https://server.example:32400",
            accessToken = null,
        )
        api.search("example movie")
        assertEquals("example movie", query)
    }

    @Test
    fun `metadata detail includes extras when requested`() = runTest {
        var includeExtras: String? = null
        val api = ServerApi(
            testClient { request ->
                includeExtras = request.url.parameters["includeExtras"]
                respond(fixture("metadata_detail.json"), headers = jsonHeaders)
            },
            "https://server.example:32400",
            accessToken = null,
        )
        val detail = api.getMetadata("101")

        assertEquals("1", includeExtras)
        assertEquals("Example Movie", detail?.title)
        assertEquals(1, detail?.extras?.items?.size)
        assertEquals("clip", detail?.extras?.items?.first()?.type)
    }

    @Test
    fun `metadata detail parses media versions and parts`() = runTest {
        val api = ServerApi(
            testClient { respond(fixture("metadata_detail.json"), headers = jsonHeaders) },
            "https://server.example:32400",
            accessToken = null,
        )
        val detail = api.getMetadata("101")

        val versions = detail?.media.orEmpty()
        assertEquals(2, versions.size)
        assertEquals("4k hevc mkv", versions[0].label)
        assertEquals("1080p h264 mp4", versions[1].label)
        assertEquals("/library/parts/91001/1700000000/file.mkv", versions[0].parts.first().key)
        assertEquals(1200000L, detail?.viewOffset)
    }

    @Test
    fun `directPlayUrl appends the part key and token`() {
        val api = ServerApi(
            testClient { respond("{}", headers = jsonHeaders) },
            "https://server.example:32400",
            accessToken = "server-token",
        )
        assertEquals(
            "https://server.example:32400/library/parts/91002/1700000000/file.mp4?X-Plex-Token=server-token",
            api.directPlayUrl("/library/parts/91002/1700000000/file.mp4"),
        )
    }

    @Test
    fun `children parse seasons with watched counts`() = runTest {
        val api = ServerApi(
            testClient { respond(fixture("show_children.json"), headers = jsonHeaders) },
            "https://server.example:32400",
            accessToken = null,
        )
        val seasons = api.getChildren("199")

        assertEquals(listOf(1, 2), seasons.map { it.index })
        assertEquals(3, seasons.first().viewedLeafCount)
    }

    @Test
    fun `collections and playlists parse`() = runTest {
        val collectionsApi = ServerApi(
            testClient { respond(fixture("collections.json"), headers = jsonHeaders) },
            "https://server.example:32400",
            accessToken = null,
        )
        val collections = collectionsApi.getCollections("1")
        assertEquals("James Bond", collections.single().title)
        assertEquals(25, collections.single().childCount)

        val playlistsApi = ServerApi(
            testClient { request ->
                assertEquals("video", request.url.parameters["playlistType"])
                respond(fixture("playlists.json"), headers = jsonHeaders)
            },
            "https://server.example:32400",
            accessToken = null,
        )
        val playlists = playlistsApi.getPlaylists()
        assertEquals("Movie Night", playlists.single().title)
        assertEquals("video", playlists.single().playlistType)
    }

    @Test
    fun `createPlayQueue posts a continuous video queue and parses items with pq ids`() = runTest {
        var method: HttpMethod? = null
        var url = ""
        val api = ServerApi(
            testClient { request ->
                method = request.method
                url = request.url.toString()
                respond(fixture("play_queue.json"), headers = jsonHeaders)
            },
            "https://server.example:32400",
            accessToken = "server-token",
        )
        val queue = api.createPlayQueue(machineId = "machine-1", ratingKey = "71936")

        assertEquals(HttpMethod.Post, method)
        assertTrue(url.contains("/playQueues"))
        assertTrue(url.contains("type=video"))
        assertTrue(url.contains("continuous=1"))
        assertTrue(url.contains("own=1"))
        assertTrue(url.contains("uri=server%3A%2F%2Fmachine-1%2Fcom.plexapp.plugins.library%2Flibrary%2Fmetadata%2F71936"))
        assertEquals(3, queue.size)
        assertEquals(111110L, queue.first().playQueueItemID)
        assertEquals(listOf("Gem Glow", "Laser Light Cannon", "Cheeseburger Backpack"), queue.map { it.title })
    }

    @Test
    fun `addToPlaylist puts the server uri`() = runTest {
        var method: HttpMethod? = null
        var uri: String? = null
        val api = ServerApi(
            testClient { request ->
                method = request.method
                uri = request.url.parameters["uri"]
                respond("""{"MediaContainer":{"size":1}}""", headers = jsonHeaders)
            },
            "https://server.example:32400",
            accessToken = "server-token",
        )
        api.addToPlaylist("601", machineId = "machine-id-1", itemRatingKey = "101")

        assertEquals(HttpMethod.Put, method)
        assertEquals("server://machine-id-1/com.plexapp.plugins.library/library/metadata/101", uri)
    }

    @Test
    fun `imageUrl resizes through the photo transcoder with the token`() {
        val api = ServerApi(
            testClient { respond("{}", headers = jsonHeaders) },
            "https://server.example:32400",
            accessToken = "server-token",
        )
        val url = api.imageUrl("/library/metadata/101/thumb/1700000000", width = 300, height = 450)

        assertTrue(url.startsWith("https://server.example:32400/photo/:/transcode?"))
        assertTrue(url.contains("width=300"))
        assertTrue(url.contains("height=450"))
        assertTrue(url.contains("url=%2Flibrary%2Fmetadata%2F101%2Fthumb%2F1700000000"))
        assertTrue(url.contains("X-Plex-Token=server-token"))
    }
}
