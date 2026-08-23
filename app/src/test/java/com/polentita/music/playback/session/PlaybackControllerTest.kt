package com.polentita.music.playback.session

import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import com.polentita.music.domain.model.Song
import com.polentita.music.playback.service.isPreviousPlaybackCommand
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PlaybackControllerTest {
    @Test
    fun `previous command works when the current song has no previous media item`() {
        assertEquals(
            true,
            isPreviousPlaybackCommand(Player.COMMAND_SEEK_TO_PREVIOUS),
        )
        assertEquals(
            true,
            isPreviousPlaybackCommand(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM),
        )
        assertEquals(
            false,
            isPreviousPlaybackCommand(Player.COMMAND_SEEK_TO_NEXT),
        )
    }

    @Test
    fun `previous always restarts on the first press`() {
        assertEquals(
            PreviousPlaybackAction.RESTART,
            previousPlaybackAction(positionMs = 0),
        )
        assertEquals(
            PreviousPlaybackAction.RESTART,
            previousPlaybackAction(positionMs = 3_000),
        )
        assertEquals(
            PreviousPlaybackAction.RESTART,
            previousPlaybackAction(positionMs = 3_001),
        )
    }

    @Test
    fun `previous changes song only after the current song was restarted`() {
        assertEquals(
            PreviousPlaybackAction.PREVIOUS,
            previousPlaybackAction(
                positionMs = 0,
                hasRestartedCurrentItem = true,
            ),
        )
        assertEquals(
            PreviousPlaybackAction.PREVIOUS,
            previousPlaybackAction(
                positionMs = 3_000,
                hasRestartedCurrentItem = true,
            ),
        )
        assertEquals(
            PreviousPlaybackAction.RESTART,
            previousPlaybackAction(
                positionMs = 3_001,
                hasRestartedCurrentItem = true,
            ),
        )
    }

    @Test
    fun `previous restarts the first song instead of wrapping to another song`() {
        assertEquals(
            PreviousPlaybackAction.RESTART,
            previousPlaybackAction(
                positionMs = 0,
                canSeekPrevious = false,
                hasRestartedCurrentItem = true,
            ),
        )
        assertEquals(
            PreviousPlaybackAction.RESTART,
            previousPlaybackAction(
                positionMs = 3_000,
                canSeekPrevious = false,
                hasRestartedCurrentItem = true,
            ),
        )
    }

    @Test
    fun `previous restarts again when the second press is delayed`() {
        assertEquals(
            PreviousPlaybackAction.RESTART,
            previousPlaybackAction(
                positionMs = 3_001,
                canSeekPrevious = true,
                hasRestartedCurrentItem = true,
            ),
        )
    }

    @Test
    fun `queue indexes start after the current song`() {
        assertEquals(emptyList<Int>(), queuePlayerIndices(currentPlayerIndex = 0, mediaItemCount = 1))
        assertEquals(listOf(3, 4, 5), queuePlayerIndices(currentPlayerIndex = 2, mediaItemCount = 6))
        assertEquals(null, playerIndexForQueueItem(currentPlayerIndex = 2, queueIndex = -1))
        assertEquals(4, playerIndexForQueueItem(currentPlayerIndex = 2, queueIndex = 1))
    }

    @Test
    fun `playing another song preserves the pending queue`() {
        val current = MediaItem.Builder().setMediaId("1").build()
        val pending = listOf(
            MediaItem.Builder().setMediaId("2").build(),
            MediaItem.Builder().setMediaId("3").build(),
        )

        val result = mediaItemsForNewSong(
            newItem = MediaItem.Builder().setMediaId("9").build(),
            currentItems = listOf(current) + pending,
            currentIndex = 0,
        )

        assertEquals(listOf("9", "2", "3"), result.map(MediaItem::mediaId))
    }

    @Test
    fun `library navigation skips unavailable songs and follows library order`() {
        val songs = listOf(
            song(1, "Amor Amarillo"),
            song(2, "Dark Beach"),
            song(3, "Archivo faltante", available = false),
            song(4, "10percs"),
        )

        assertEquals(
            songs[0],
            neighboringAvailableSong(songs, 2, LibraryNavigationDirection.PREVIOUS),
        )
        assertEquals(
            songs[3],
            neighboringAvailableSong(songs, 2, LibraryNavigationDirection.NEXT),
        )
        assertNull(neighboringAvailableSong(songs, 1, LibraryNavigationDirection.PREVIOUS))
        assertNull(neighboringAvailableSong(songs, 4, LibraryNavigationDirection.NEXT))
        assertEquals(songs[0], libraryNavigationTarget(songs, 4, LibraryNavigationDirection.NEXT))
        assertEquals(songs[3], libraryNavigationTarget(songs, 1, LibraryNavigationDirection.PREVIOUS))
    }

    private fun song(id: Long, title: String, available: Boolean = true) = Song(
        id = id,
        title = title,
        artist = "Artista",
        albumId = null,
        albumName = "Álbum",
        genre = "",
        year = null,
        trackNumber = null,
        discNumber = null,
        durationMs = 1_000,
        contentUri = "content://test/$id",
        originalFileName = "$title.wav",
        displayFileName = "$title.wav",
        mimeType = "audio/wav",
        fileSize = 44,
        coverUri = null,
        sourceType = "IMPORTED",
        sourceUrl = null,
        dateAdded = 0,
        dateModified = 0,
        lastPlayedAt = null,
        playCount = 0,
        isFavorite = false,
        isAvailable = available,
        checksum = id.toString(),
    )
}
