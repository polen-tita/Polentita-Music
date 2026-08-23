package com.polentita.music.data.repository

import android.content.Context
import android.net.Uri
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.polentita.music.core.database.PolentitaDatabase
import com.polentita.music.core.database.AlbumEntity
import com.polentita.music.core.database.SongEntity
import com.polentita.music.core.database.SourceType
import com.polentita.music.core.storage.AudioMetadata
import com.polentita.music.core.storage.LibraryStorage
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class DownloadedImportRoomTest {
    private lateinit var database: PolentitaDatabase
    private lateinit var repository: DefaultMusicRepository
    private val storage = mockk<LibraryStorage>()

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, PolentitaDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = DefaultMusicRepository(
            database.songDao(),
            database.albumDao(),
            database.playlistDao(),
            database.historyDao(),
            storage,
        )
    }

    @After
    fun close() = database.close()

    @Test
    fun `completed audio is registered in room and duplicate returns existing row`() = runTest {
        val uri = Uri.parse("content://library/Downloads/demo.wav")
        coEvery { storage.checksum(uri) } returns "demo-checksum"
        coEvery { storage.extractMetadata(uri) } returns AudioMetadata(
            title = "Metadato",
            artist = "",
            album = "",
            genre = "",
            year = null,
            trackNumber = null,
            discNumber = null,
            durationMs = 4_000,
            displayName = "demo.wav",
            mimeType = "audio/wav",
            fileSize = 128_044,
            modifiedAt = 10,
            embeddedCover = null,
        )
        coEvery { storage.extractAndStoreCover(uri, "demo-checksum") } returns null

        val firstId = repository.registerDownloadedFile(
            uri,
            title = "Pista autorizada",
            artist = "Laboratorio",
            album = "Demo",
            sourceUrl = "https://demo.polentita.invalid/catalog/demo",
        )
        val duplicateId = repository.registerDownloadedFile(
            uri,
            title = "Otro título",
            artist = "",
            album = "",
            sourceUrl = "https://demo.polentita.invalid/catalog/demo",
        )
        val inserted = database.songDao().getById(firstId)

        assertNotNull(inserted)
        assertEquals(firstId, duplicateId)
        assertEquals("Pista autorizada", inserted?.title)
        assertEquals(SourceType.DOWNLOADED.name, inserted?.sourceType)
        assertEquals(1, database.songDao().observeAll().first().size)
    }

    @Test
    fun `downloaded cover uri is persisted without falling back to embedded artwork`() = runTest {
        val uri = Uri.parse("content://library/Downloads/con-portada.m4a")
        val coverUri = "content://library/Covers/checksum-cover.jpg"
        coEvery { storage.checksum(uri) } returns "checksum-cover"
        coEvery { storage.extractMetadata(uri) } returns AudioMetadata(
            title = "Con portada",
            artist = "Artista",
            album = "",
            genre = "",
            year = null,
            trackNumber = null,
            discNumber = null,
            durationMs = 8_000,
            displayName = "con-portada.m4a",
            mimeType = "audio/mp4",
            fileSize = 512,
            modifiedAt = 20,
            embeddedCover = null,
        )

        val id = repository.registerDownloadedFile(
            uri = uri,
            title = "Con portada",
            artist = "Artista",
            album = "",
            sourceUrl = "https://example.invalid/audio",
            downloadedCoverUri = coverUri,
        )

        assertEquals(coverUri, database.songDao().getById(id)?.coverUri)
        coVerify(exactly = 0) { storage.extractAndStoreCover(any(), any()) }
    }

    @Test
    fun `yt-dlp download with blank album does not create YouTube album`() = runTest {
        val uri = Uri.parse("content://library/Downloads/youtube-album.m4a")
        coEvery { storage.checksum(uri) } returns "youtube-album-checksum"
        coEvery { storage.extractMetadata(uri) } returns AudioMetadata(
            title = "Canción descargada",
            artist = "Artista remoto",
            album = "YouTube",
            genre = "",
            year = null,
            trackNumber = null,
            discNumber = null,
            durationMs = 12_000,
            displayName = "youtube-album.m4a",
            mimeType = "audio/mp4",
            fileSize = 256,
            modifiedAt = 25,
            embeddedCover = null,
        )
        coEvery { storage.extractAndStoreCover(uri, "youtube-album-checksum") } returns null

        val songId = repository.registerDownloadedFile(
            uri = uri,
            title = "Canción descargada",
            artist = "Artista remoto",
            album = "",
            sourceUrl = "https://youtube.invalid/watch?v=autorizado",
            useMetadataAlbumWhenBlank = false,
        )

        val inserted = database.songDao().getById(songId)
        assertEquals("", inserted?.albumName)
        assertEquals(0, database.albumDao().observeAll().first().size)
    }

    @Test
    fun `metadata identity reuses the same song even when duration differs`() = runTest {
        val existingId = database.songDao().insert(
            SongEntity(
                title = "Misma canción",
                artist = "Artista",
                durationMs = 180_000,
                contentUri = "content://library/Downloads/primera.m4a",
                originalFileName = "primera.m4a",
                displayFileName = "primera.m4a",
                mimeType = "audio/mp4",
                checksum = "primera",
            ),
        )

        val match = repository.findAvailableSongByMetadata(
            title = "Misma canción",
            artist = "Artista",
        )

        assertEquals(existingId, match?.id)
    }

    @Test
    fun `selected existing album is reused even when downloaded artist is different`() = runTest {
        val albumId = database.albumDao().insert(
            AlbumEntity(name = "Audios de TikTok", artist = ""),
        )
        val uri = Uri.parse("content://library/Downloads/tiktok.m4a")
        coEvery { storage.checksum(uri) } returns "tiktok-checksum"
        coEvery { storage.extractMetadata(uri) } returns AudioMetadata(
            title = "Audio corto",
            artist = "Creador de TikTok",
            album = "",
            genre = "",
            year = null,
            trackNumber = null,
            discNumber = null,
            durationMs = 15_000,
            displayName = "tiktok.m4a",
            mimeType = "audio/mp4",
            fileSize = 256,
            modifiedAt = 30,
            embeddedCover = null,
        )
        coEvery { storage.extractAndStoreCover(uri, "tiktok-checksum") } returns null

        val songId = repository.registerDownloadedFile(
            uri = uri,
            title = "Audio corto",
            artist = "Creador de TikTok",
            album = "Audios de TikTok",
            sourceUrl = "https://vt.tiktok.com/video-autorizado",
            preferredAlbumId = albumId,
        )

        val inserted = database.songDao().getById(songId)
        assertEquals(albumId, inserted?.albumId)
        assertEquals("Audios de TikTok", inserted?.albumName)
        assertEquals("Creador de TikTok", inserted?.artist)
        assertEquals(1, database.albumDao().observeAll().first().size)
    }
}
