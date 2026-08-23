package com.polentita.music.data.artwork

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.polentita.music.core.database.AlbumEntity
import com.polentita.music.core.database.PlaylistEntity
import com.polentita.music.core.database.PolentitaDatabase
import com.polentita.music.core.database.SongEntity
import com.polentita.music.domain.artwork.ArtworkCandidate
import com.polentita.music.domain.artwork.ArtworkChoice
import com.polentita.music.domain.artwork.ArtworkSource
import com.polentita.music.domain.model.toModel
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class DefaultArtworkEditingRepositoryTest {
    private lateinit var database: PolentitaDatabase
    private lateinit var store: RecordingArtworkStore
    private lateinit var repository: DefaultArtworkEditingRepository

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, PolentitaDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        store = RecordingArtworkStore(NEW_COVER)
        repository = DefaultArtworkEditingRepository(database, store)
    }

    @After
    fun close() = database.close()

    @Test
    fun `song artwork updates only the edited song transactionally`() = runTest {
        val albumId = database.albumDao().insert(
            AlbumEntity(name = "AM", artist = "Arctic Monkeys", coverUri = OLD_ALBUM_COVER),
        )
        val firstId = database.songDao().insert(songEntity("Do I Wanna Know?", albumId, OLD_ALBUM_COVER, "one"))
        database.songDao().insert(songEntity("R U Mine?", albumId, SHARED_OLD_COVER, "two"))
        database.songDao().insert(songEntity("Otra", null, SHARED_OLD_COVER, "three"))
        val edited = requireNotNull(database.songDao().getById(firstId)).toModel().copy(title = "Do I Wanna Know")

        repository.saveSong(edited, remoteChoice())

        val album = database.albumDao().getById(albumId)
        val albumSongs = database.songDao().getAvailable().filter { it.albumId == albumId }
        assertEquals(OLD_ALBUM_COVER, album?.coverUri)
        assertEquals(NEW_COVER, albumSongs[0].coverUri)
        assertEquals(SHARED_OLD_COVER, albumSongs[1].coverUri)
        assertTrue(albumSongs[0].dateModified > 1)
        assertEquals(1L, albumSongs[1].dateModified)
        assertEquals("Do I Wanna Know", database.songDao().getById(firstId)?.title)
        assertFalse(OLD_ALBUM_COVER in store.deleted)
        assertFalse(SHARED_OLD_COVER in store.deleted)
    }

    @Test
    fun `album artwork updates only the album and preserves every song cover`() = runTest {
        val albumId = database.albumDao().insert(
            AlbumEntity(name = "AM", artist = "Arctic Monkeys", coverUri = OLD_ALBUM_COVER),
        )
        database.songDao().insert(songEntity("One", albumId, OLD_ALBUM_COVER, "one"))
        database.songDao().insert(songEntity("Two", albumId, SHARED_OLD_COVER, "two"))
        val album = requireNotNull(database.albumDao().getById(albumId))

        repository.saveAlbum(album, remoteChoice())

        assertEquals(NEW_COVER, database.albumDao().getById(albumId)?.coverUri)
        val albumSongs = database.songDao().getAvailable().filter { it.albumId == albumId }
        assertEquals(OLD_ALBUM_COVER, albumSongs[0].coverUri)
        assertEquals(SHARED_OLD_COVER, albumSongs[1].coverUri)
        assertFalse(OLD_ALBUM_COVER in store.deleted)
        assertFalse(SHARED_OLD_COVER in store.deleted)
    }

    @Test
    fun `album removal preserves every song cover and never deletes an external legacy file`() = runTest {
        val external = "content://photos/document/external-cover"
        val albumId = database.albumDao().insert(
            AlbumEntity(name = "Legacy", artist = "Artist", coverUri = external),
        )
        database.songDao().insert(songEntity("One", albumId, external, "legacy-one"))
        val album = requireNotNull(database.albumDao().getById(albumId))

        repository.saveAlbum(album, ArtworkChoice.Remove)

        assertNull(database.albumDao().getById(albumId)?.coverUri)
        assertEquals(external, database.songDao().getAvailable().single().coverUri)
        assertTrue(store.deleted.isEmpty())
        assertTrue(store.deleteAttempts.isEmpty())
    }

    @Test
    fun `managed cover remains while a playlist still references it`() = runTest {
        val albumId = database.albumDao().insert(
            AlbumEntity(name = "Shared", coverUri = OLD_ALBUM_COVER),
        )
        database.songDao().insert(songEntity("One", albumId, OLD_ALBUM_COVER, "shared-one"))
        database.playlistDao().insert(PlaylistEntity(name = "Visual", coverUri = OLD_ALBUM_COVER))
        val album = requireNotNull(database.albumDao().getById(albumId))

        repository.saveAlbum(album, remoteChoice())

        assertFalse(OLD_ALBUM_COVER in store.deleted)
    }

    @Test
    fun `prepared managed file is cleaned when the database transaction fails`() = runTest {
        val missingSong = songEntity("Missing", null, null, "missing")
            .copy(id = 999)
            .toModel()

        val failure = runCatching { repository.saveSong(missingSong, remoteChoice()) }.exceptionOrNull()

        assertEquals("La canción ya no existe", failure?.message)
        assertTrue(NEW_COVER in store.deleted)
    }

    private fun remoteChoice() = ArtworkChoice.Remote(
        ArtworkCandidate(
            id = "tidal:test",
            source = ArtworkSource.TIDAL,
            title = "AM",
            artist = "Arctic Monkeys",
            imageUrl = "https://resources.tidal.com/images/test/1280x1280.jpg",
        ),
    )

    private fun songEntity(
        title: String,
        albumId: Long?,
        coverUri: String?,
        checksum: String,
    ) = SongEntity(
        title = title,
        artist = "Artist",
        albumId = albumId,
        albumName = if (albumId == null) "" else "Album",
        contentUri = "content://library/Music/$checksum.m4a",
        originalFileName = "$checksum.m4a",
        displayFileName = "$checksum.m4a",
        mimeType = "audio/mp4",
        checksum = checksum,
        coverUri = coverUri,
        dateModified = 1,
    )

    private class RecordingArtworkStore(
        private val preparedUri: String?,
    ) : ManagedArtworkStore {
        val deleted = mutableListOf<String>()
        val deleteAttempts = mutableListOf<String>()

        override suspend fun prepare(choice: ArtworkChoice): String? =
            if (choice == ArtworkChoice.Remove) null else preparedUri

        override suspend fun deleteIfManaged(uri: String): Boolean {
            deleteAttempts += uri
            if (!uri.startsWith("content://library/Covers/")) return false
            deleted += uri
            return true
        }
    }

    private companion object {
        const val NEW_COVER = "content://library/Covers/new.jpg"
        const val OLD_ALBUM_COVER = "content://library/Covers/old-album.jpg"
        const val SHARED_OLD_COVER = "content://library/Covers/shared.jpg"
    }
}
