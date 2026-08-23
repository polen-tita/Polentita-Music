package com.polentita.music.data.playlistimport

import com.polentita.music.data.extractor.YtDlpDownloadedFile
import com.polentita.music.data.extractor.YtDlpExtractor
import com.polentita.music.data.extractor.YtDlpMediaInfo
import com.polentita.music.data.extractor.YtDlpPlaylistEntry
import com.polentita.music.data.extractor.YtDlpPlaylistInfo
import com.polentita.music.data.extractor.YtDlpPreviewInfo
import com.polentita.music.data.extractor.YtDlpSearchPage
import com.polentita.music.domain.playlistimport.ImportedCollectionType
import com.polentita.music.domain.playlistimport.PlaylistImportRequest
import java.io.File
import java.util.Base64
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okhttp3.Request
import okhttp3.MediaType.Companion.toMediaType
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class PlaylistImportProvidersTest {
    private val fileProvider = FilePlaylistImportProvider()

    @Test
    fun `extracts ids from supported playlist and album links without query data`() {
        val spotify = SpotifyPlaylistImportProvider.extractSourceReference(
            "https://open.spotify.com/playlist/37i9dQZF1DXcBWIGoYBM5M?si=secret",
        )
        val spotifyAlbum = SpotifyPlaylistImportProvider.extractSourceReference(
            "https://open.spotify.com/album/4aawyAB9vmqN3uQ7FjRGTy",
        )
        val tidal = TidalPlaylistImportProvider.extractSourceReference(
            "https://tidal.com/browse/playlist/01234567-abcd-4321-abcd-0123456789ab",
        )
        val youtube = YouTubePlaylistImportProvider.extractSourceReference(
            "https://music.youtube.com/playlist?list=PL1234567890ABCDE&feature=share",
        )

        assertEquals("37i9dQZF1DXcBWIGoYBM5M", spotify?.sourceId)
        assertEquals("https://open.spotify.com/playlist/37i9dQZF1DXcBWIGoYBM5M", spotify?.canonicalUrl)
        assertEquals(ImportedCollectionType.ALBUM, spotifyAlbum?.type)
        assertEquals("01234567-abcd-4321-abcd-0123456789ab", tidal?.sourceId)
        assertEquals("PL1234567890ABCDE", youtube?.sourceId)
        assertFalse(youtube?.canonicalUrl.orEmpty().contains("feature"))
    }

    @Test
    fun `rejects lookalike hosts and non https links`() {
        assertNull(
            SpotifyPlaylistImportProvider.extractSourceReference(
                "https://open.spotify.com.evil.example/playlist/37i9dQZF1DXcBWIGoYBM5M",
            ),
        )
        assertNull(
            YouTubePlaylistImportProvider.extractSourceReference(
                "http://music.youtube.com/playlist?list=PL1234567890ABCDE",
            ),
        )
    }

    @Test
    fun `spotify reads embedded state and follows public offset pagination`() = runTest {
        val tracks = listOf(
            spotifyTrack("one", "Primera", "A"),
            spotifyTrack("two", "Segunda", "B"),
            spotifyTrack("three", "Tercera", "C"),
        )
        val firstPage = spotifyPage(tracks.take(2), total = 3, nextOffset = 2)
        val secondPage = spotifyPage(tracks, total = 3, nextOffset = null)
        val requests = mutableListOf<String>()
        val http = fakeHttp { request ->
            requests += request.url.toString()
            if (request.url.queryParameter("offset") == "2") secondPage else firstPage
        }

        val result = SpotifyPlaylistImportProvider(http).analyze(
            PlaylistImportRequest.Url(
                "https://open.spotify.com/playlist/37i9dQZF1DXcBWIGoYBM5M",
            ),
        ).getOrThrow()

        assertEquals("Viaje público", result.name)
        assertEquals(3, result.totalTracks)
        assertEquals(listOf("Primera", "Segunda", "Tercera"), result.tracks.map { it.title })
        assertEquals(listOf(0, 1, 2), result.tracks.map { it.originalPosition })
        assertEquals(2, requests.size)
        assertTrue(requests.last().contains("offset=2"))
    }

    @Test
    fun `tidal public test playlist resolves all sixteen tracks in order`() = runTest {
        val playlistId = "5bf6fa0e-4ea1-4604-86a5-5c235b2f969a"
        val titles = listOf(
            "Guchi Polo", "Water", "BBYNOSE_147BPM9S", "WAIFU_153BPM2B",
            "Twilight", "BALENCI", "Amor ácido", "Ya No", "HUMO", "FLASHLIGHT",
            "Se Que Está Mal", "10percs", "CHROME", "NANA", "TOMBOY", "Sola",
        )
        val requests = mutableListOf<Request>()
        val items = JSONArray().apply {
            titles.forEachIndexed { index, title ->
                put(
                    JSONObject()
                        .put("type", "track")
                        .put(
                            "item",
                            JSONObject()
                                .put("id", "tidal-${index + 1}")
                                .put("title", title)
                                .put("duration", 120 + index)
                                .put("trackNumber", index + 1)
                                .put("volumeNumber", 1)
                                .put("isrc", "US7VG21554${index.toString().padStart(2, '0')}")
                                .put("artist", JSONObject().put("name", "Artista $index"))
                                .put("album", JSONObject().put("title", "Álbum $index")),
                        ),
                )
            }
        }
        val http = fakeHttp { request ->
            requests += request
            when {
                request.url.host == "tidal.com" ->
                    """
                    <meta property="og:title" content="Sarita">
                    <meta property="og:description" content="Playlist - Sarita - 16 items">
                    <meta property="og:image" content="https://resources.tidal.com/images/cover/640x640.jpg">
                    <script type="application/ld+json">{"@type":"WebApplication","name":"TIDAL"}</script>
                    """.trimIndent()
                request.url.encodedPath.endsWith("/items") ->
                    JSONObject()
                        .put("totalNumberOfItems", 16)
                        .put("limit", 100)
                        .put("offset", 0)
                        .put("items", items)
                        .toString()
                else ->
                    JSONObject()
                        .put("title", "Sarita")
                        .put("numberOfTracks", 16)
                        .put("description", "Playlist pública")
                        .toString()
            }
        }

        val result = TidalPlaylistImportProvider(http).analyze(
            PlaylistImportRequest.Url("https://tidal.com/playlist/$playlistId"),
        ).getOrThrow()

        assertEquals("Sarita", result.name)
        assertEquals(16, result.totalTracks)
        assertEquals(titles, result.tracks.map { it.title })
        assertEquals((0 until 16).toList(), result.tracks.map { it.originalPosition })
        assertEquals("https://tidal.com/playlist/$playlistId", result.sourceUrl)
        assertTrue(requests.any { it.url.host == "api.tidal.com" && it.header("x-tidal-token") != null })
        assertTrue(requests.none { it.url.encodedPath.contains("audio", ignoreCase = true) })
    }

    @Test
    fun `public spotify page without complete state returns precise error`() = runTest {
        val provider = SpotifyPlaylistImportProvider(
            fakeHttp { "<meta property=\"og:title\" content=\"Privada\">" },
        )

        val failure = provider.analyze(
            PlaylistImportRequest.Url(
                "https://open.spotify.com/playlist/37i9dQZF1DXcBWIGoYBM5M",
            ),
        ).exceptionOrNull()

        assertEquals(PUBLIC_PLAYLIST_UNAVAILABLE_MESSAGE, failure?.message)
    }

    @Test
    fun `spotify repeated public window keeps first page as partial import`() = runTest {
        val tracks = listOf(
            spotifyTrack("one", "Primera", "A"),
            spotifyTrack("two", "Segunda", "B"),
        )
        val page = spotifyPage(tracks, total = 4, nextOffset = 2)
        val provider = SpotifyPlaylistImportProvider(fakeHttp { page })

        val result = provider.analyze(
            PlaylistImportRequest.Url(
                "https://open.spotify.com/playlist/37i9dQZF1DXcBWIGoYBM5M",
            ),
        ).getOrThrow()

        assertEquals(4, result.totalTracks)
        assertEquals(listOf("Primera", "Segunda"), result.tracks.map { it.title })
        assertEquals(listOf(0, 1), result.tracks.map { it.originalPosition })
    }

    @Test
    fun `youtube flat metadata analysis does not invoke audio download`() = runTest {
        val extractor = RecordingYtDlpExtractor(
            YtDlpPlaylistInfo(
                id = "PL1234567890ABCDE",
                title = "YouTube pública",
                description = "Descripción",
                thumbnailUrl = "https://i.ytimg.com/vi/one/hqdefault.jpg",
                webpageUrl = "https://www.youtube.com/playlist?list=PL1234567890ABCDE",
                entries = listOf(
                    YtDlpPlaylistEntry(
                        id = "one",
                        title = "Primera",
                        artist = "Artista",
                        album = "Álbum",
                        durationMs = 120_000,
                        thumbnailUrl = null,
                        webpageUrl = "https://www.youtube.com/watch?v=one",
                    ),
                ),
                totalTracks = 1,
            ),
        )

        val result = YouTubePlaylistImportProvider(extractor).analyze(
            PlaylistImportRequest.Url("https://www.youtube.com/playlist?list=PL1234567890ABCDE"),
        ).getOrThrow()

        assertEquals(1, result.tracks.size)
        assertEquals(0, extractor.downloadCalls)
    }

    @Test
    fun `youtube keeps public entries when unavailable playlist items are omitted`() = runTest {
        val extractor = RecordingYtDlpExtractor(
            YtDlpPlaylistInfo(
                id = "PL1234567890ABCDE",
                title = "YouTube parcial",
                description = null,
                thumbnailUrl = null,
                webpageUrl = "https://www.youtube.com/playlist?list=PL1234567890ABCDE",
                entries = listOf(
                    YtDlpPlaylistEntry(
                        id = "one",
                        title = "Disponible",
                        artist = "Artista",
                        album = "",
                        durationMs = 120_000,
                        thumbnailUrl = null,
                        webpageUrl = "https://www.youtube.com/watch?v=one",
                    ),
                ),
                totalTracks = 2,
            ),
        )

        val result = YouTubePlaylistImportProvider(extractor).analyze(
            PlaylistImportRequest.Url("https://www.youtube.com/playlist?list=PL1234567890ABCDE"),
        ).getOrThrow()

        assertEquals(2, result.totalTracks)
        assertEquals(1, result.tracks.size)
        assertEquals("Disponible", result.tracks.single().title)
    }

    @Test
    fun `youtube music retries with its public host when canonical host fails`() = runTest {
        val playlist = YtDlpPlaylistInfo(
            id = "PL1234567890ABCDE",
            title = "YouTube Music pública",
            description = null,
            thumbnailUrl = null,
            webpageUrl = "https://music.youtube.com/playlist?list=PL1234567890ABCDE",
            entries = listOf(
                YtDlpPlaylistEntry(
                    id = "one",
                    title = "Primera",
                    artist = "Artista",
                    album = "",
                    durationMs = 120_000,
                    thumbnailUrl = null,
                    webpageUrl = "https://www.youtube.com/watch?v=one",
                ),
            ),
            totalTracks = 1,
        )
        val extractor = RecordingYtDlpExtractor(
            playlist = playlist,
            supportedUrl = "https://music.youtube.com/playlist?list=PL1234567890ABCDE",
        )

        val result = YouTubePlaylistImportProvider(extractor).analyze(
            PlaylistImportRequest.Url(
                "https://music.youtube.com/playlist?list=PL1234567890ABCDE&si=share",
            ),
        ).getOrThrow()

        assertEquals(1, result.tracks.size)
        assertEquals(
            listOf(
                "https://www.youtube.com/playlist?list=PL1234567890ABCDE",
                "https://music.youtube.com/playlist?list=PL1234567890ABCDE",
            ),
            extractor.playlistCalls,
        )
    }

    @Test
    fun `parses json metadata and ordered tracks`() = runTest {
        val result = fileProvider.analyze(
            PlaylistImportRequest.FileContent(
                displayName = "viaje.json",
                mimeType = "application/json",
                content = """
                    {
                      "name": "Viaje",
                      "description": "Ruta larga",
                      "tracks": [
                        {"id":"one","title":"Árbol","artists":["Sol", "Luna"],"album":"Norte","durationMs":181000,"isrc":"ar-abc-24-00001"},
                        {"id":"two","title":"Río","artist":"Mar","duration":"3:20"}
                      ]
                    }
                """.trimIndent(),
            ),
        ).getOrThrow()

        assertEquals("Viaje", result.name)
        assertEquals(2, result.totalTracks)
        assertEquals(listOf("Sol", "Luna"), result.tracks[0].artists)
        assertEquals("AR-ABC-24-00001", result.tracks[0].isrc)
        assertEquals(200_000, result.tracks[1].durationMs)
        assertEquals(listOf(0, 1), result.tracks.map { it.originalPosition })
    }

    @Test
    fun `parses quoted csv and keeps large collection order`() = runTest {
        val rows = buildString {
            appendLine("title,artist,album,durationMs")
            repeat(150) { index ->
                appendLine("\"Tema, $index\",Artista,Álbum,180000")
            }
        }
        val result = fileProvider.analyze(
            PlaylistImportRequest.FileContent("grande.csv", "text/csv", rows),
        ).getOrThrow()

        assertEquals(150, result.tracks.size)
        assertEquals("Tema, 0", result.tracks.first().title)
        assertEquals("Tema, 149", result.tracks.last().title)
        assertEquals((0 until 150).toList(), result.tracks.map { it.originalPosition })
    }

    @Test
    fun `parses structured txt and missing optional metadata`() = runTest {
        val result = fileProvider.analyze(
            PlaylistImportRequest.FileContent(
                "lista.txt",
                "text/plain",
                """
                    # name: Pruebas
                    title|artist|album|duration
                    Sin artista|||0
                    Completa|Autora||2:00
                """.trimIndent(),
            ),
        ).getOrThrow()

        assertEquals("Pruebas", result.name)
        assertTrue(result.tracks.first().artists.isEmpty())
        assertEquals("", result.tracks.first().album)
        assertEquals(0, result.tracks.first().durationMs)
        assertEquals(120_000, result.tracks.last().durationMs)
    }

    private fun spotifyTrack(id: String, title: String, artist: String) =
        JSONObject()
            .put("uri", "spotify:track:$id")
            .put("name", title)
            .put(
                "artists",
                JSONObject().put(
                    "items",
                    JSONArray().put(
                        JSONObject().put("profile", JSONObject().put("name", artist)),
                    ),
                ),
            )
            .put("albumOfTrack", JSONObject().put("name", "Álbum"))
            .put("duration", JSONObject().put("totalMilliseconds", 180_000))
            .put("trackNumber", 1)

    private fun spotifyPage(
        tracks: List<JSONObject>,
        total: Int,
        nextOffset: Int?,
    ): String {
        val trackItems = JSONArray().apply {
            tracks.forEach { track -> put(JSONObject().put("itemV2", JSONObject().put("data", track))) }
        }
        val content = JSONObject()
            .put("__typename", "PlaylistItemsPage")
            .put("totalCount", total)
            .put("items", trackItems)
            .put("pagingInfo", JSONObject().apply {
                nextOffset?.let { put("nextOffset", it) }
            })
        val entity = JSONObject()
            .put("__typename", "Playlist")
            .put("id", "37i9dQZF1DXcBWIGoYBM5M")
            .put("name", "Viaje público")
            .put("content", content)
        val root = JSONObject().put(
            "entities",
            JSONObject().put(
                "items",
                JSONObject().put("spotify:playlist:37i9dQZF1DXcBWIGoYBM5M", entity),
            ),
        )
        val state = Base64.getEncoder().encodeToString(root.toString().toByteArray())
        return "<meta property=\"og:title\" content=\"Viaje público\"><script id=\"initialState\" type=\"text/plain\">$state</script>"
    }

    private fun fakeHttp(responder: (Request) -> String): PublicPlaylistHttpClient {
        val client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val response = responder(chain.request()).toResponseBody("application/json".toMediaType())
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body(response)
                    .build()
            }
            .build()
        return PublicPlaylistHttpClient(client)
    }

    private class RecordingYtDlpExtractor(
        private val playlist: YtDlpPlaylistInfo,
        private val supportedUrl: String? = null,
    ) : YtDlpExtractor {
        var downloadCalls = 0
        val playlistCalls = mutableListOf<String>()

        override suspend fun inspect(url: String): YtDlpMediaInfo = error("no usado")
        override suspend fun inspectPlaylist(url: String): YtDlpPlaylistInfo {
            playlistCalls += url
            if (supportedUrl != null && url != supportedUrl) error("host no compatible")
            return playlist
        }
        override suspend fun resolvePreview(url: String): YtDlpPreviewInfo = error("no usado")
        override suspend fun download(
            url: String,
            outputDirectory: File,
            callback: com.polentita.music.data.extractor.YtDlpProgressCallback,
        ): YtDlpDownloadedFile {
            downloadCalls++
            error("no debe descargarse durante el análisis")
        }

        override suspend fun search(query: String, page: Int, pageSize: Int): YtDlpSearchPage =
            error("no usado")
    }
}
