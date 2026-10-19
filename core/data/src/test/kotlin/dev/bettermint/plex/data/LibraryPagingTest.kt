package dev.bettermint.plex.data

import androidx.paging.PagingSource
import dev.bettermint.plex.api.PlexClient
import dev.bettermint.plex.api.PlexDeviceInfo
import dev.bettermint.plex.api.ServerApi
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** M2 paging math: offsets become the next key until totalSize is reached. */
class LibraryPagingTest {

    private val pageBody = """
        {"MediaContainer":{"size":2,"totalSize":5,"offset":0,"Metadata":[
          {"ratingKey":"1","type":"movie","title":"A"},
          {"ratingKey":"2","type":"movie","title":"B"}
        ]}}
    """.trimIndent()

    private val lastPageBody = """
        {"MediaContainer":{"size":1,"totalSize":5,"offset":4,"Metadata":[
          {"ratingKey":"5","type":"movie","title":"E"}
        ]}}
    """.trimIndent()

    private fun repo(body: String): LibraryRepository {
        val client = PlexClient.create(
            deviceInfo = PlexDeviceInfo("id", platformVersion = "1", device = "d", deviceName = "n"),
            tokenProvider = { null },
            engine = MockEngine { respond(body, headers = headersOf(HttpHeaders.ContentType, "application/json")) },
        )
        return LibraryRepository(ServerApi(client, "https://s:32400", null), "machine")
    }

    @Test
    fun `first page sets nextKey to the running offset`() = runTest {
        val source = repo(pageBody).itemsPagingSource("1")
        val result = source.load(PagingSource.LoadParams.Refresh(key = 0, loadSize = 2, placeholdersEnabled = false))
        val page = result as PagingSource.LoadResult.Page
        assertEquals(listOf("A", "B"), page.data.map { it.title })
        assertEquals(2, page.nextKey)
    }

    @Test
    fun `final page returns a null nextKey`() = runTest {
        val source = repo(lastPageBody).itemsPagingSource("1")
        val result = source.load(PagingSource.LoadParams.Append(key = 4, loadSize = 2, placeholdersEnabled = false))
        val page = result as PagingSource.LoadResult.Page
        assertEquals(listOf("E"), page.data.map { it.title })
        assertNull(page.nextKey)
    }

    @Test
    fun `server error becomes a LoadResult Error, not a crash`() = runTest {
        val client = PlexClient.create(
            deviceInfo = PlexDeviceInfo("id", platformVersion = "1", device = "d", deviceName = "n"),
            tokenProvider = { null },
            engine = MockEngine {
                respond("boom", io.ktor.http.HttpStatusCode.InternalServerError, headersOf(HttpHeaders.ContentType, "application/json"))
            },
        )
        val source = LibraryRepository(ServerApi(client, "https://s:32400", null), "m").itemsPagingSource("1")
        val result = source.load(PagingSource.LoadParams.Refresh(key = null, loadSize = 2, placeholdersEnabled = false))
        assertTrue(result is PagingSource.LoadResult.Error)
    }
}
