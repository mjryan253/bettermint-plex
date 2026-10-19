package dev.bettermint.plex.data

import dev.bettermint.plex.api.PlexClient
import dev.bettermint.plex.api.PlexDeviceInfo
import dev.bettermint.plex.api.PlexTvApi
import dev.bettermint.plex.api.model.Metadata
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.headersOf
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TitleResolverTest {

    private val providerBody = """
        {"MediaContainer":{"size":1,"Metadata":[
          {"guid":"plex://movie/abc123","type":"movie","title":"Coraline","year":2009}
        ]}}
    """.trimIndent()

    private fun resolverWith(
        calls: AtomicInteger = AtomicInteger(),
        body: String = providerBody,
        status: io.ktor.http.HttpStatusCode = io.ktor.http.HttpStatusCode.OK,
    ): TitleResolver {
        val client = PlexClient.create(
            deviceInfo = PlexDeviceInfo("id", platformVersion = "1", device = "d", deviceName = "n"),
            tokenProvider = { "token" },
            engine = MockEngine {
                calls.incrementAndGet()
                respond(body, status, headersOf(HttpHeaders.ContentType, "application/json"))
            },
        )
        return TitleResolver(PlexTvApi(client))
    }

    private val staleItem = Metadata(
        ratingKey = "77150",
        guid = "plex://movie/abc123",
        type = "movie",
        title = "Coraline.2009.1080p.BluRay.H264.AAC-RARBG",
    )
    private val cleanItem = Metadata(
        ratingKey = "1",
        guid = "plex://movie/def456",
        type = "movie",
        title = "The American Revolution",
    )

    @Test
    fun `filename detection catches scan-time titles and spares real ones`() {
        listOf(
            "Coraline.2009.1080p.BluRay.H264.AAC-RARBG",
            "Clerks.II.2006.1080p.BluRay.x265-RARBG",
            "Batman.Mask.of.the.Phantasm.1993.1080p.BluRay.X264-AMIABLE.Dual-clovinho",
            "Encanto.2021.1080p.WEBRip.x264-RARBG",
            "Some Movie 1080p",
        ).forEach { assertTrue(it, TitleResolver.looksLikeFilename(it)) }

        listOf(
            "Coraline",
            "The American Revolution",
            "2001: A Space Odyssey",
            "Se7en",
            "Blade Runner 2049",
            "1917",
            "Spider-Man: Across the Spider-Verse",
        ).forEach { assertFalse(it, TitleResolver.looksLikeFilename(it)) }
    }

    @Test
    fun `stale titles are replaced from the provider and clean ones untouched`() = runTest {
        val resolver = resolverWith()
        val out = resolver.resolve(listOf(staleItem, cleanItem))

        assertEquals("Coraline", out[0].title)
        assertEquals("The American Revolution", out[1].title)
        assertEquals("77150", out[0].ratingKey)
    }

    @Test
    fun `provider results are cached per guid`() = runTest {
        val calls = AtomicInteger()
        val resolver = resolverWith(calls)
        resolver.resolve(listOf(staleItem))
        resolver.resolve(listOf(staleItem))
        resolver.resolve(staleItem)

        assertEquals(1, calls.get())
    }

    @Test
    fun `no provider calls when nothing needs fixing`() = runTest {
        val calls = AtomicInteger()
        val resolver = resolverWith(calls)
        val out = resolver.resolve(listOf(cleanItem))

        assertEquals(0, calls.get())
        assertEquals(listOf(cleanItem), out)
    }

    @Test
    fun `provider failure keeps the original title`() = runTest {
        val resolver = resolverWith(body = "oops", status = io.ktor.http.HttpStatusCode.ServiceUnavailable)
        val out = resolver.resolve(listOf(staleItem))

        assertEquals(staleItem.title, out[0].title)
    }

    @Test
    fun `a definitive miss is cached but a transient failure retries`() = runTest {
        val missCalls = AtomicInteger()
        val misses = resolverWith(missCalls, body = """{"MediaContainer":{"size":0}}""")
        misses.resolve(listOf(staleItem))
        val out = misses.resolve(listOf(staleItem))
        assertEquals(1, missCalls.get())
        assertEquals(staleItem.title, out[0].title)

        val errorCalls = AtomicInteger()
        val errors = resolverWith(errorCalls, body = "oops", status = io.ktor.http.HttpStatusCode.ServiceUnavailable)
        errors.resolve(listOf(staleItem))
        errors.resolve(listOf(staleItem))
        assertEquals(2, errorCalls.get())
    }
}
