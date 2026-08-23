package com.polentita.music.data.playlistimport

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.polentita.music.core.database.PolentitaDatabase
import com.polentita.music.data.extractor.YtDlpExtractor
import com.polentita.music.core.database.SongEntity
import com.polentita.music.domain.playlistimport.ImportedCollection
import com.polentita.music.domain.playlistimport.ImportedCollectionSource
import com.polentita.music.domain.playlistimport.ImportedTrack
import com.polentita.music.domain.playlistimport.PlaylistImportProvider
import com.polentita.music.domain.playlistimport.PlaylistImportItemState
import com.polentita.music.domain.playlistimport.PlaylistImportRequest
import io.mockk.coEvery
import io.mockk.every
import kotlinx.coroutines.test.runTest
import io.mockk.mockk
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class PlaylistImportRepositoryTest {
    private lateinit var database: PolentitaDatabase
    private lateinit var repository: PlaylistImportRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, PolentitaDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val publicHttp = PublicPlaylistHttpClient(OkHttpClient())
        val registry = PlaylistImportProviderRegistry(
            FilePlaylistImportProvider(),
            SpotifyPlaylistImportProvider(publicHttp),
            TidalPlaylistImportProvider(publicHttp),
            YouTubePlaylistImportProvider(mockk<YtDlpExtractor>(relaxed = true)),
        )
        repository = PlaylistImportRepository(
            database,
            database.playlistImportDao(),
            database.songDao(),
            database.playlistDao(),
            registry,
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `analysis is idempotent and marks local missing and duplicate items`() = runTest {
        database.songDao().insert(song(1, "Local", "Autora"))
        val request = request()

        val firstId = repository.analyze(request)
        val secondId = repository.analyze(request)
        val items = repository.getItems(firstId)

        assertEquals(firstId, secondId)
        assertEquals(3, items.size)
        assertEquals(PlaylistImportItemState.IN_LIBRARY.name, items[0].state)
        assertEquals(PlaylistImportItemState.MISSING.name, items[1].state)
        assertEquals(PlaylistImportItemState.DUPLICATE.name, items[2].state)
    }

    @Test
    fun `analysis preserves provider total when only part of a collection is public`() = runTest {
        val collection = ImportedCollection(
            source = ImportedCollectionSource.YOUTUBE,
            sourceId = "partial-public",
            name = "Parcial",
            tracks = listOf(
                ImportedTrack("one", "Primera", listOf("Artista"), originalPosition = 0),
                ImportedTrack("two", "Segunda", listOf("Artista"), originalPosition = 1),
            ),
            totalTracks = 5,
        )
        val provider = mockk<PlaylistImportProvider>()
        every { provider.isConfigured } returns true
        coEvery { provider.analyze(any()) } returns Result.success(collection)
        val registry = mockk<PlaylistImportProviderRegistry>()
        every { registry.providerFor(any()) } returns provider
        val partialRepository = PlaylistImportRepository(
            database,
            database.playlistImportDao(),
            database.songDao(),
            database.playlistDao(),
            registry,
        )

        val importId = partialRepository.analyze(PlaylistImportRequest.Url("https://example.com/partial"))

        assertEquals(5, partialRepository.get(importId)?.totalTracks)
        assertEquals(2, partialRepository.getItems(importId).size)
    }

    @Test
    fun `partial playlist keeps original order when missing song completes later`() = runTest {
        val localId = database.songDao().insert(song(0, "Local", "Autora"))
        val importId = repository.analyze(request())

        val playlistId = repository.createLocalPlaylist(importId, "Trasladada", null)
        val firstRefs = database.playlistDao().getCrossRefs(playlistId)
        assertEquals(listOf(localId), firstRefs.map { it.songId })
        assertEquals(listOf(0), firstRefs.map { it.position })

        val downloadedId = database.songDao().insert(song(0, "Faltante", "Autor"))
        val missing = repository.getItems(importId).first { it.title == "Faltante" }
        repository.attachSong(importId, missing.id, downloadedId)

        val finalRefs = database.playlistDao().getCrossRefs(playlistId)
        assertEquals(listOf(localId, downloadedId), finalRefs.map { it.songId })
        assertEquals(listOf(0, 1_000), finalRefs.map { it.position })
        assertEquals(2, finalRefs.map { it.songId }.distinct().size)
    }

    @Test
    fun `reserved playlist name is rejected without deleting analysis`() = runTest {
        val importId = repository.analyze(request())

        assertThrows(IllegalArgumentException::class.java) {
            kotlinx.coroutines.runBlocking {
                repository.createLocalPlaylist(importId, "tus ME GUSTA", null)
            }
        }

        assertNotEquals(null, repository.get(importId))
        assertEquals(null, repository.get(importId)?.localPlaylistId)
    }

    @Test
    fun `retry preserves completed items and resets only recoverable errors`() = runTest {
        database.songDao().insert(song(1, "Local", "Autora"))
        val importId = repository.analyze(request())
        val items = repository.getItems(importId)
        val missing = items.first { it.state == PlaylistImportItemState.MISSING.name }
        repository.updateItem(
            missing.copy(
                state = PlaylistImportItemState.ERROR.name,
                errorMessage = "Temporal",
                attemptCount = 1,
            ),
        )

        repository.retryErrors(importId)

        val retried = repository.getItems(importId)
        assertEquals(PlaylistImportItemState.IN_LIBRARY.name, retried.first().state)
        assertEquals(PlaylistImportItemState.MISSING.name, retried[1].state)
        assertEquals(1, retried[1].attemptCount)
    }

    @Test
    fun `deleted local playlist leaves downloaded song intact and marks item error`() = runTest {
        database.songDao().insert(song(1, "Local", "Autora"))
        val importId = repository.analyze(request())
        val playlistId = repository.createLocalPlaylist(importId, "Temporal", null)
        val playlist = database.playlistDao().getById(playlistId)!!
        database.playlistDao().delete(playlist)
        val downloadedId = database.songDao().insert(song(0, "Faltante", "Autor"))
        val missing = repository.getItems(importId).first { it.title == "Faltante" }

        repository.attachSong(importId, missing.id, downloadedId)

        val finalItem = repository.getItem(missing.id)!!
        assertEquals(PlaylistImportItemState.ERROR.name, finalItem.state)
        assertEquals(downloadedId, finalItem.localSongId)
        assertEquals(downloadedId, database.songDao().getById(downloadedId)?.id)
    }

    @Test
    fun `missing title is metadata error while missing artist remains reviewable`() = runTest {
        val importId = repository.analyze(
            PlaylistImportRequest.FileContent(
                "incompleta.json",
                "application/json",
                """
                    {"tracks":[
                      {"id":"bad","title":"","artist":"A"},
                      {"id":"review","title":"Sin artista","album":"","durationMs":0}
                    ]}
                """.trimIndent(),
            ),
        )

        val items = repository.getItems(importId)
        assertEquals(PlaylistImportItemState.METADATA_ERROR.name, items[0].state)
        assertEquals(PlaylistImportItemState.REQUIRES_REVIEW.name, items[1].state)
    }

    private fun request() = PlaylistImportRequest.FileContent(
        "traslado.json",
        "application/json",
        """
            {
              "name":"Traslado",
              "tracks":[
                {"id":"local","title":"Local","artist":"Autora","durationMs":180000},
                {"id":"missing","title":"Faltante","artist":"Autor","durationMs":200000},
                {"id":"duplicate","title":"Local (Official Video)","artist":"Autora","durationMs":240000}
              ]
            }
        """.trimIndent(),
    )

    private fun song(id: Long, title: String, artist: String) = SongEntity(
        id = id,
        title = title,
        artist = artist,
        durationMs = if (title == "Local") 180_000 else 200_000,
        contentUri = "content://songs/${title.lowercase()}-$id",
        originalFileName = "$title.mp3",
        displayFileName = "$title.mp3",
        mimeType = "audio/mpeg",
        checksum = "checksum-${title.lowercase()}-$id",
    )
}
