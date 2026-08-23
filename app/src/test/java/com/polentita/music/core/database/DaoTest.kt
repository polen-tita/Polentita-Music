package com.polentita.music.core.database

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class DaoTest {
    private lateinit var database: PolentitaDatabase
    private lateinit var songDao: SongDao
    private lateinit var albumDao: AlbumDao
    private lateinit var playlistDao: PlaylistDao
    private lateinit var remoteReferenceDao: RemoteReferenceDao

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, PolentitaDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        songDao = database.songDao()
        albumDao = database.albumDao()
        playlistDao = database.playlistDao()
        remoteReferenceDao = database.remoteReferenceDao()
    }

    @After
    fun close() = database.close()

    @Test
    fun `song dao stores favorites and missing files`() = runTest {
        val availableId = songDao.insert(song("Mate", "Ana", checksum = "1"))
        val missingId = songDao.insert(song("Lluvia", "Beto", checksum = "2", available = false))

        songDao.toggleFavorite(availableId, 10)
        val all = songDao.observeAll().first()

        assertEquals(2, all.size)
        assertTrue(all.first { it.id == availableId }.isFavorite)
        assertFalse(all.first { it.id == missingId }.isAvailable)
    }

    @Test
    fun `search finds title artist album genre filename and playlist`() = runTest {
        val first = songDao.insert(
            song("Ruta", "Clara", checksum = "a").copy(
                albumName = "Montaña",
                genre = "Ambient",
                displayFileName = "viaje.flac",
            ),
        )
        songDao.insert(song("Otra", "Diego", checksum = "b"))
        val playlist = playlistDao.insert(PlaylistEntity(name = "Para manejar"))
        playlistDao.addSongs(playlist, listOf(first))

        suspend fun query(text: String) = songDao.search(query = text).first().map { it.id }
        assertEquals(listOf(first), query("Ruta"))
        assertEquals(listOf(first), query("Clara"))
        assertEquals(listOf(first), query("Montaña"))
        assertEquals(listOf(first), query("Ambient"))
        assertEquals(listOf(first), query("viaje"))
        assertEquals(listOf(first), query("manejar"))
    }

    @Test
    fun `filters and ordering are applied in sql`() = runTest {
        songDao.insert(song("Zeta", "A", checksum = "a").copy(isFavorite = true, playCount = 2))
        songDao.insert(song("Alfa", "B", checksum = "b").copy(playCount = 8))

        val favorites = songDao.search(query = "", favoriteOnly = true).first()
        assertEquals(listOf("Zeta"), favorites.map { it.title })

        val byPlayCount = songDao.search(
            query = "",
            sort = "PLAY_COUNT",
            ascending = false,
        ).first()
        assertEquals(listOf("Alfa", "Zeta"), byPlayCount.map { it.title })
    }

    @Test
    fun `playlist reorder keeps requested stable positions`() = runTest {
        val ids = listOf(
            songDao.insert(song("Uno", checksum = "1")),
            songDao.insert(song("Dos", checksum = "2")),
            songDao.insert(song("Tres", checksum = "3")),
        )
        val playlistId = playlistDao.insert(PlaylistEntity(name = "Orden"))
        playlistDao.addSongs(playlistId, ids)
        playlistDao.replaceOrder(playlistId, listOf(ids[2], ids[0], ids[1]))

        assertEquals(listOf(ids[2], ids[0], ids[1]), playlistDao.observeSongs(playlistId).first().map { it.id })
        assertEquals(listOf(0, 1, 2), playlistDao.getCrossRefs(playlistId).map { it.position })
    }

    @Test
    fun `remote reference dao persists replaces and removes provider track`() = runTest {
        val reference = RemoteReferenceEntity(
            providerId = "youtube",
            remoteTrackId = "video-1",
            title = "Referencia",
            artist = "Canal",
            album = "YouTube",
            durationMs = 5_000,
            thumbnailUrl = "https://i.example/cover.jpg",
            externalUrl = "https://www.youtube.com/watch?v=video-1",
            license = "Licencia estándar de YouTube",
            attribution = "Canal",
            dateSaved = 10,
        )

        remoteReferenceDao.upsert(reference)
        remoteReferenceDao.upsert(reference.copy(title = "Referencia editada", dateSaved = 20))

        val saved = remoteReferenceDao.observeAll().first()
        assertEquals(1, saved.size)
        assertEquals("Referencia editada", saved.single().title)

        remoteReferenceDao.delete("youtube", "video-1")
        assertTrue(remoteReferenceDao.observeAll().first().isEmpty())
    }

    @Test
    fun `deleting album detaches songs without deleting audio records`() = runTest {
        val albumId = albumDao.insert(AlbumEntity(name = "Temporal", artist = "Banda"))
        val songId = songDao.insert(
            song("Con álbum", checksum = "album-song").copy(
                albumId = albumId,
                albumName = "Temporal",
            ),
        )

        albumDao.deletePreservingSongs(albumId)

        assertEquals(null, albumDao.getById(albumId))
        val preserved = songDao.getById(songId)
        assertEquals("content://test/album-song", preserved?.contentUri)
        assertEquals(null, preserved?.albumId)
        assertEquals("", preserved?.albumName)
    }

    @Test
    fun `clearing artist detaches songs without changing downloaded records`() = runTest {
        val songId = songDao.insert(
            song("Shadows", "Pastel Ghost", checksum = "artist-song").copy(
                sourceType = SourceType.DOWNLOADED.name,
            ),
        )
        songDao.insert(song("Otra", "Otro artista", checksum = "other-artist"))

        assertEquals(1, songDao.clearArtist("Pastel Ghost"))

        val preserved = songDao.getById(songId)
        assertEquals("", preserved?.artist)
        assertEquals(SourceType.DOWNLOADED.name, preserved?.sourceType)
        assertEquals("content://test/artist-song", preserved?.contentUri)
        assertEquals("Otro artista", songDao.observeAll().first().first { it.checksum == "other-artist" }.artist)
    }

    @Test
    fun `renaming artist updates every song without changing files`() = runTest {
        val firstId = songDao.insert(song("Uno", "Pastel Ghost", checksum = "rename-one"))
        songDao.insert(song("Dos", "Pastel Ghost", checksum = "rename-two"))
        songDao.insert(song("Tres", "Otra artista", checksum = "rename-other"))

        assertEquals(2, songDao.renameArtist("Pastel Ghost", "Pastel Ghost Official"))

        assertEquals("Pastel Ghost Official", songDao.getById(firstId)?.artist)
        assertEquals(
            listOf("Otra artista", "Pastel Ghost Official", "Pastel Ghost Official"),
            songDao.observeAll().first().map { it.artist }.sorted(),
        )
    }

    @Test
    fun `clean empty albums removes only albums without songs`() = runTest {
        val usedAlbum = albumDao.insert(AlbumEntity(name = "Usado"))
        albumDao.insert(AlbumEntity(name = "Vacío"))
        songDao.insert(
            song("Asignada", checksum = "used-album").copy(
                albumId = usedAlbum,
                albumName = "Usado",
            ),
        )

        val deleted = albumDao.deleteEmpty()

        assertEquals(1, deleted)
        assertEquals(listOf("Usado"), albumDao.observeAll().first().map { it.name })
    }

    private fun song(
        title: String,
        artist: String = "",
        checksum: String,
        available: Boolean = true,
    ) = SongEntity(
        title = title,
        artist = artist,
        contentUri = "content://test/$checksum",
        originalFileName = "$title.mp3",
        displayFileName = "$title.mp3",
        mimeType = "audio/mpeg",
        checksum = checksum,
        isAvailable = available,
    )
}
