package com.polentita.music.data.artwork

import com.polentita.music.core.network.NetworkAccessPolicy
import com.polentita.music.core.network.NetworkAccessState
import com.polentita.music.data.playlistimport.PublicPlaylistHttpClient
import com.polentita.music.data.provider.AuthorizedProviderRegistry
import com.polentita.music.domain.artwork.ArtworkCandidate
import com.polentita.music.domain.artwork.ArtworkSearchRequest
import com.polentita.music.domain.artwork.ArtworkSource
import com.polentita.music.domain.provider.AuthorizedMusicProvider
import com.polentita.music.domain.provider.ProviderLicense
import com.polentita.music.domain.provider.RemoteAlbum
import com.polentita.music.domain.provider.RemoteArtist
import com.polentita.music.domain.provider.RemoteTrack
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ArtworkSearchSourcesTest {
    @Test
    fun `musicbrainz gate spaces consecutive metadata requests`() = runTest {
        val gate = MusicBrainzRequestGate()
        val starts = mutableListOf<Pair<Long, Long>>()

        gate.request { starts += testScheduler.currentTime to System.nanoTime() }
        gate.request { starts += testScheduler.currentTime to System.nanoTime() }

        val virtualElapsedMs = starts[1].first - starts[0].first
        val wallElapsedMs = (starts[1].second - starts[0].second) / 1_000_000.0
        assertTrue(virtualElapsedMs + wallElapsedMs >= 1_099.0)
    }

    @Test
    fun `internet image search returns original and lightweight preview urls`() = runTest {
        val requests = mutableListOf<Request>()
        val http = fakeHttp { request ->
            requests += request
            when (request.url.encodedPath) {
                "/" -> "<html><script>vqd='4-12345-abc'</script></html>"
                "/i.js" -> JSONObject()
                    .put(
                        "results",
                        JSONArray().put(
                            JSONObject()
                                .put("title", "Laufey album cover")
                                .put("image", "https://images.example.test/laufey-full.jpg")
                                .put("thumbnail", "https://images.example.test/laufey-thumb.jpg")
                                .put("url", "https://example.test/laufey")
                                .put("source", "Example")
                                .put("width", 1600)
                                .put("height", 1600),
                        ),
                    )
                    .toString()
                else -> JSONObject().toString()
            }
        }

        val results = InternetArtworkSource(http).search(
            ArtworkSearchRequest("Laufey", page = 1),
        )

        val candidate = results.single()
        assertEquals(ArtworkSource.INTERNET, candidate.source)
        assertEquals("https://images.example.test/laufey-full.jpg", candidate.imageUrl)
        assertEquals("https://images.example.test/laufey-thumb.jpg", candidate.previewUrl)
        assertEquals("https://example.test/laufey", candidate.externalUrl)
        assertEquals("24", requests.single { it.url.encodedPath == "/i.js" }.url.queryParameter("s"))
        assertEquals(
            "4-12345-abc",
            requests.single { it.url.encodedPath == "/i.js" }.url.queryParameter("vqd"),
        )
    }

    @Test
    fun `internet image search ignores unsafe original and keeps safe thumbnail`() = runTest {
        val http = fakeHttp { request ->
            when (request.url.encodedPath) {
                "/" -> "<html>vqd=4-unsafe-test</html>"
                "/i.js" -> JSONObject()
                    .put(
                        "results",
                        JSONArray().put(
                            JSONObject()
                                .put("title", "Portada")
                                .put("image", "http://images.example.test/full.jpg")
                                .put("thumbnail", "https://images.example.test/thumb.jpg"),
                        ),
                    )
                    .toString()
                else -> JSONObject().toString()
            }
        }

        val result = InternetArtworkSource(http).search(ArtworkSearchRequest("Portada"))

        assertEquals("https://images.example.test/thumb.jpg", result.single().imageUrl)
        assertEquals(null, result.single().previewUrl)
    }

    @Test
    fun `musicbrainz keeps spotify only when a public relation exists`() = runTest {
        val requests = mutableListOf<Request>()
        val http = fakeHttp { request ->
            requests += request
            when {
                request.url.host == "open.spotify.com" -> JSONObject()
                    .put("title", "AM")
                    .put("thumbnail_url", "https://i.scdn.co/image/am")
                    .put("thumbnail_width", 640)
                    .put("thumbnail_height", 640)
                    .toString()
                request.url.encodedPath.endsWith("/release/") -> JSONObject()
                    .put(
                        "releases",
                        JSONArray().put(
                            JSONObject().put(
                                "relations",
                                JSONArray().put(
                                    JSONObject().put(
                                        "url",
                                        JSONObject().put(
                                            "resource",
                                            "https://open.spotify.com/album/78bpIziExqiI9qztvNFlQu",
                                        ),
                                    ),
                                ),
                            ),
                        ),
                    )
                    .toString()
                else -> JSONObject()
                    .put(
                        "release-groups",
                        JSONArray().put(
                            JSONObject()
                                .put("id", "02a8f31d-8dbe-4e4c-9e4b-4f16f51427c4")
                                .put("title", "AM")
                                .put(
                                    "artist-credit",
                                    JSONArray().put(JSONObject().put("name", "Arctic Monkeys")),
                                ),
                        ),
                    )
                    .toString()
            }
        }

        val results = MusicBrainzArtworkSource(http, MusicBrainzRequestGate()).search(
            ArtworkSearchRequest("AM", "Arctic Monkeys"),
        )

        assertEquals(ArtworkSource.SPOTIFY, results.single().source)
        assertFalse(requests.any { it.url.host == "coverartarchive.org" })
    }

    @Test
    fun `youtube artwork uses provider search metadata without resolving a download`() = runTest {
        val provider = mockk<AuthorizedMusicProvider>()
        val registry = mockk<AuthorizedProviderRegistry>()
        every { registry.defaultProvider() } returns provider
        coEvery { provider.search(any()) } returns Result.success(
            listOf(
                RemoteTrack(
                    id = "video",
                    title = "AM full album",
                    artist = RemoteArtist("artist", "Arctic Monkeys"),
                    album = RemoteAlbum("album", "AM", RemoteArtist("artist", "Arctic Monkeys")),
                    durationMs = 0,
                    coverUri = "https://i.ytimg.com/vi/video/maxresdefault.jpg",
                    providerId = "youtube",
                    providerName = "YouTube",
                    license = ProviderLicense("provider", "Provider", null, true, false),
                    attribution = null,
                    allowsDownload = true,
                    externalUrl = "https://www.youtube.com/watch?v=video",
                ),
            ),
        )

        val result = YouTubeArtworkSource(registry).search(
            ArtworkSearchRequest("AM", "Arctic Monkeys"),
        )

        assertEquals(ArtworkSource.YOUTUBE, result.single().source)
        coVerify(exactly = 1) { provider.search(match { it.contains("album cover") }) }
        coVerify(exactly = 0) { provider.resolveDownload(any()) }
    }

    @Test
    fun `repository keeps partial results and reports only the failed source`() = runTest {
        val musicBrainz = mockk<MusicBrainzArtworkSource>()
        val tidal = mockk<TidalArtworkSource>()
        val youtube = mockk<YouTubeArtworkSource>()
        val internet = mockk<InternetArtworkSource>()
        val policy = mockk<NetworkAccessPolicy>()
        val candidate = ArtworkCandidate(
            id = "internet:one",
            source = ArtworkSource.INTERNET,
            title = "AM",
            artist = "Arctic Monkeys",
            imageUrl = "https://images.example.test/am.jpg",
            score = 0.9,
        )
        every { musicBrainz.reportedSources } returns setOf(ArtworkSource.SPOTIFY)
        every { tidal.reportedSources } returns setOf(ArtworkSource.TIDAL)
        every { youtube.reportedSources } returns setOf(ArtworkSource.YOUTUBE)
        every { internet.reportedSources } returns setOf(ArtworkSource.INTERNET)
        coEvery { musicBrainz.search(any()) } returns emptyList()
        coEvery { tidal.search(any()) } throws IllegalStateException("TIDAL no respondió")
        coEvery { youtube.search(any()) } returns emptyList()
        coEvery { internet.search(any()) } returns listOf(candidate)
        coEvery { policy.current() } returns NetworkAccessState(
            connected = true,
            remoteSearchAllowed = true,
            downloadAllowed = true,
            remoteBlockReason = null,
            downloadBlockReason = null,
        )
        val repository = DefaultArtworkSearchRepository(
            musicBrainz = musicBrainz,
            tidal = tidal,
            youtube = youtube,
            internet = internet,
            networkAccessPolicy = policy,
        )

        val result = repository.search(ArtworkSearchRequest("AM", "Arctic Monkeys"))

        assertEquals(listOf(candidate), result.candidates)
        assertEquals("TIDAL no respondió", result.sourceErrors[ArtworkSource.TIDAL])
        assertFalse(ArtworkSource.INTERNET in result.sourceErrors)
        assertTrue(result.hasMore)
    }

    private fun fakeHttp(responder: (Request) -> String): PublicPlaylistHttpClient {
        val client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body(responder(chain.request()).toResponseBody("application/json".toMediaType()))
                    .build()
            }
            .build()
        return PublicPlaylistHttpClient(client)
    }
}
