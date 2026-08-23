package com.polentita.music.data.repository

import android.net.Uri
import com.polentita.music.core.database.AlbumDao
import com.polentita.music.core.database.PlaybackHistoryDao
import com.polentita.music.core.database.PlaylistDao
import com.polentita.music.core.database.PlaylistEntity
import com.polentita.music.core.database.PlaylistSongCrossRef
import com.polentita.music.core.database.SongDao
import com.polentita.music.core.database.SongEntity
import com.polentita.music.core.storage.AudioMetadata
import com.polentita.music.core.storage.LibraryStorage
import com.polentita.music.domain.model.PlaylistNames
import androidx.documentfile.provider.DocumentFile
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class DefaultMusicRepositoryTest {
    private val songDao = mockk<SongDao>(relaxed = true)
    private val albumDao = mockk<AlbumDao>(relaxed = true)
    private val playlistDao = mockk<PlaylistDao>(relaxed = true)
    private val historyDao = mockk<PlaybackHistoryDao>(relaxed = true)
    private val storage = mockk<LibraryStorage>(relaxed = true)
    private val repository = DefaultMusicRepository(songDao, albumDao, playlistDao, historyDao, storage)

    @Test
    fun `duplicate checksum is not copied or inserted`() = runTest {
        val uri = Uri.parse("content://picker/song")
        coEvery { storage.extractMetadata(uri) } returns metadata()
        coEvery { storage.checksum(uri) } returns "same"
        coEvery { songDao.findByChecksum("same") } returns entity()

        val result = repository.importFiles(listOf(uri))

        assertEquals(0, result.imported)
        assertEquals(1, result.duplicates)
        coVerify(exactly = 0) { storage.copyIntoLibrary(any(), any(), any(), any()) }
        coVerify(exactly = 0) { songDao.insert(any()) }
    }

    @Test
    fun `repository delegates playlist order transaction`() = runTest {
        repository.reorderPlaylist(9, listOf(3, 1, 2))
        coVerify { playlistDao.replaceOrder(9, listOf(3, 1, 2)) }
    }

    @Test
    fun `toggling favorite creates and synchronizes likes playlist`() = runTest {
        val first = entity().copy(id = 1)
        coEvery { songDao.getById(1) } returns first
        coEvery { songDao.favoriteIds() } returns listOf(1, 2)
        coEvery { playlistDao.findByName(PlaylistNames.TUS_ME_GUSTA) } returns null
        coEvery { playlistDao.insert(match { it.name == PlaylistNames.TUS_ME_GUSTA }) } returns 7
        coEvery { playlistDao.getCrossRefs(7) } returns emptyList()

        repository.toggleFavorite(1)

        coVerify { songDao.toggleFavorite(1, any()) }
        coVerify { playlistDao.addSongs(7, listOf(1, 2)) }
    }

    @Test
    fun `unfavoriting removes song from likes playlist without deleting it`() = runTest {
        val first = entity().copy(id = 1, isFavorite = true)
        val playlist = PlaylistEntity(id = 7, name = PlaylistNames.TUS_ME_GUSTA)
        coEvery { songDao.getById(1) } returns first
        coEvery { songDao.favoriteIds() } returns emptyList()
        coEvery { playlistDao.findByName(PlaylistNames.TUS_ME_GUSTA) } returns playlist
        coEvery { playlistDao.getCrossRefs(7) } returns listOf(PlaylistSongCrossRef(7, 1, 0))

        repository.toggleFavorite(1)

        coVerify { songDao.toggleFavorite(1, any()) }
        coVerify { playlistDao.removeSong(7, 1) }
        coVerify(exactly = 0) { songDao.deleteById(1) }
    }

    @Test
    fun `deleting a song deletes an unreferenced managed cover safely`() = runTest {
        val audioUri = Uri.parse("content://library/Music/song.m4a")
        val coverUri = "content://library/Covers/song.jpg"
        coEvery { songDao.getById(1) } returns entity().copy(
            id = 1,
            contentUri = audioUri.toString(),
            coverUri = coverUri,
        )
        coEvery { storage.delete(audioUri) } returns true

        assertEquals(true, repository.removeSong(1, deleteFile = true))

        coVerify { storage.delete(audioUri) }
        coVerify { songDao.deleteById(1) }
        coVerify { storage.deleteManagedCover(coverUri) }
        coVerify(exactly = 0) { storage.delete(Uri.parse(coverUri)) }
    }

    @Test
    fun `deleting a song preserves a cover referenced by its album`() = runTest {
        val audioUri = Uri.parse("content://library/Music/song.m4a")
        val coverUri = "content://library/Covers/shared.jpg"
        coEvery { songDao.getById(1) } returns entity().copy(
            id = 1,
            contentUri = audioUri.toString(),
            coverUri = coverUri,
        )
        coEvery { storage.delete(audioUri) } returns true
        coEvery { albumDao.countCoverReferences(coverUri) } returns 1

        assertEquals(true, repository.removeSong(1, deleteFile = true))

        coVerify { songDao.deleteById(1) }
        coVerify(exactly = 0) { storage.deleteManagedCover(any()) }
        coVerify(exactly = 0) { storage.delete(Uri.parse(coverUri)) }
    }

    @Test
    fun `removing only the database entry never deletes audio or cover files`() = runTest {
        val audioUri = "content://library/Music/song.m4a"
        val coverUri = "content://external/images/song.jpg"
        coEvery { songDao.getById(1) } returns entity().copy(
            id = 1,
            contentUri = audioUri,
            coverUri = coverUri,
        )

        assertEquals(true, repository.removeSong(1, deleteFile = false))

        coVerify { songDao.deleteById(1) }
        coVerify(exactly = 0) { storage.delete(any()) }
        coVerify(exactly = 0) { storage.deleteManagedCover(any()) }
    }

    @Test
    fun `library scan restores downloaded cover stored by checksum`() = runTest {
        val audioUri = Uri.parse("content://library/Downloads/track.opus")
        val coverUri = "content://library/Covers/downloaded-checksum.webp"
        val document = mockk<DocumentFile>()
        every { document.uri } returns audioUri
        coEvery { storage.audioDocuments() } returns listOf(document)
        coEvery { songDao.allContentUris() } returns emptyList()
        coEvery { songDao.findByUri(audioUri.toString()) } returns null
        coEvery { storage.checksum(audioUri) } returns "downloaded-checksum"
        coEvery { songDao.findByChecksum("downloaded-checksum") } returns null
        coEvery { storage.extractMetadata(audioUri) } returns metadata()
        coEvery { storage.findStoredCover("downloaded-checksum") } returns coverUri

        val result = repository.scanLibrary()

        assertEquals(1, result.added)
        coVerify {
            songDao.insert(
                match {
                    it.contentUri == audioUri.toString() &&
                        it.checksum == "downloaded-checksum" &&
                        it.coverUri == coverUri
                },
            )
        }
        coVerify(exactly = 0) {
            storage.extractAndStoreCover(audioUri, "downloaded-checksum")
        }
    }

    private fun metadata() = AudioMetadata(
        title = "Prueba",
        artist = "Local",
        album = "",
        genre = "",
        year = null,
        trackNumber = null,
        discNumber = null,
        durationMs = 1_000,
        displayName = "prueba.wav",
        mimeType = "audio/wav",
        fileSize = 64,
        modifiedAt = 0,
        embeddedCover = null,
    )

    private fun entity() = SongEntity(
        title = "Prueba",
        contentUri = "content://library/song",
        originalFileName = "prueba.wav",
        displayFileName = "prueba.wav",
        mimeType = "audio/wav",
        checksum = "same",
    )
}
