package com.polentita.music.playback.queue

import android.net.Uri
import com.polentita.music.domain.model.Song
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class MediaItemMapperTest {
    @Test
    fun `notification metadata includes title artist duration and real artwork uri`() {
        val song = Song(
            id = 7,
            title = "Tema de prueba",
            artist = "Artista de prueba",
            albumId = null,
            albumName = "Álbum de prueba",
            genre = "",
            year = null,
            trackNumber = null,
            discNumber = null,
            durationMs = 185_000,
            contentUri = "content://library/music/test.m4a",
            originalFileName = "test.m4a",
            displayFileName = "test.m4a",
            mimeType = "audio/mp4",
            fileSize = 1_024,
            coverUri = "content://library/covers/test.webp",
            sourceType = "DOWNLOADED",
            sourceUrl = null,
            dateAdded = 0,
            dateModified = 42,
            lastPlayedAt = null,
            playCount = 0,
            isFavorite = false,
            isAvailable = true,
            checksum = "test",
        )

        val mediaItem = song.toMediaItem()

        assertEquals("Tema de prueba", mediaItem.mediaMetadata.title)
        assertEquals("Artista de prueba", mediaItem.mediaMetadata.artist)
        assertEquals("Álbum de prueba", mediaItem.mediaMetadata.albumTitle)
        assertEquals(185_000L, mediaItem.mediaMetadata.durationMs)
        assertEquals(
            Uri.parse("content://library/covers/test.webp"),
            mediaItem.mediaMetadata.artworkUri,
        )
        assertEquals(
            42L,
            mediaItem.mediaMetadata.extras?.getLong("com.polentita.music.ARTWORK_REVISION"),
        )
    }

    @Test
    fun `queue origin and playback context survive media item mapping`() {
        val song = testSong()
        val context = PlaybackContext(
            kind = PlaybackContextKind.PLAYLIST,
            key = "42",
            label = "Viaje",
            songs = listOf(song),
        )

        val item = song.toMediaItem(PlaybackQueueOrigin.MANUAL, context)

        assertEquals(PlaybackQueueOrigin.MANUAL, item.playbackQueueOrigin())
        assertEquals(PlaybackContextKind.PLAYLIST, item.playbackContextKind())
        assertEquals("42", item.playbackContextKey())
        assertEquals("Viaje", item.playbackContextLabel())
    }

    @Test
    fun `automatic continuation keeps context order then library without duplicates`() {
        val songs = (1L..6L).map { testSong(it) }

        val continuation = playbackContinuation(
            currentSongId = 2,
            contextSongs = listOf(songs[0], songs[1], songs[2], songs[3]),
            manualSongIds = setOf(3),
            librarySongs = listOf(songs[5], songs[2], songs[4], songs[0]),
        )

        assertEquals(listOf(4L), continuation.contextSongs.map(Song::id))
        assertEquals(listOf(6L, 5L), continuation.librarySongs.map(Song::id))
    }

    @Test
    fun `shuffle keeps manual queue before context and library fallback`() {
        val origins = listOf(
            PlaybackQueueOrigin.CONTEXT,
            PlaybackQueueOrigin.CURRENT,
            PlaybackQueueOrigin.LIBRARY_FALLBACK,
            PlaybackQueueOrigin.CONTEXT,
            PlaybackQueueOrigin.MANUAL,
            PlaybackQueueOrigin.MANUAL,
            PlaybackQueueOrigin.LIBRARY_FALLBACK,
        )

        val order = priorityShuffleOrder(currentIndex = 1, origins = origins, seed = 7).toList()
        val afterCurrent = order.dropWhile { it != 1 }.drop(1).map(origins::get)

        assertEquals(PlaybackQueueOrigin.CONTEXT, origins[order.first()])
        assertEquals(
            listOf(
                PlaybackQueueOrigin.MANUAL,
                PlaybackQueueOrigin.MANUAL,
                PlaybackQueueOrigin.CONTEXT,
                PlaybackQueueOrigin.LIBRARY_FALLBACK,
                PlaybackQueueOrigin.LIBRARY_FALLBACK,
            ),
            afterCurrent,
        )
    }

    @Test
    fun `playback fallback mirrors descending library tie order`() {
        val songs = listOf(
            testSong(1).copy(title = "Alfa", dateAdded = 10),
            testSong(2).copy(title = "Zulu", dateAdded = 10),
            testSong(3).copy(title = "Medio", dateAdded = 20),
        )

        assertEquals(
            listOf(3L, 2L, 1L),
            songs.sortedForPlayback(sort = "DATE_ADDED", ascending = false).map(Song::id),
        )
    }

    private fun testSong(id: Long = 7) = Song(
        id = id,
        title = "Tema $id",
        artist = "Artista de prueba",
        albumId = null,
        albumName = "Álbum de prueba",
        genre = "",
        year = null,
        trackNumber = null,
        discNumber = null,
        durationMs = 185_000,
        contentUri = "content://library/music/$id.m4a",
        originalFileName = "$id.m4a",
        displayFileName = "$id.m4a",
        mimeType = "audio/mp4",
        fileSize = 1_024,
        coverUri = null,
        sourceType = "DOWNLOADED",
        sourceUrl = null,
        dateAdded = id,
        dateModified = id,
        lastPlayedAt = null,
        playCount = 0,
        isFavorite = false,
        isAvailable = true,
        checksum = id.toString(),
    )
}
