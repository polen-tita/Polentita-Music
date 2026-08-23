package com.polentita.music.data.playlistimport

import com.polentita.music.core.database.SongEntity
import com.polentita.music.domain.playlistimport.ImportMatchStatus
import com.polentita.music.domain.playlistimport.ImportedTrack
import com.polentita.music.domain.provider.ProviderLicense
import com.polentita.music.domain.provider.RemoteAlbum
import com.polentita.music.domain.provider.RemoteArtist
import com.polentita.music.domain.provider.RemoteTrack
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaylistImportMatcherTest {
    @Test
    fun `normalizes accents suffixes featured artists punctuation and artist order`() {
        assertEquals(
            "cancion del arbol",
            PlaylistImportMatcher.normalizeTitle("  CANCIÓN del Árbol (Official Video) feat. Invitada "),
        )
        assertEquals(
            PlaylistImportMatcher.normalizeArtist("Beyoncé & Jay-Z"),
            PlaylistImportMatcher.normalizeArtist("JAY Z; Beyonce"),
        )
    }

    @Test
    fun `isrc exact match has priority`() {
        val track = imported("Título distinto", "Otra", isrc = "AR-AAA-24-00001")
        val song = song(7, "Local", "Artista", isrc = "ARAAA2400001")

        val match = PlaylistImportMatcher.localMatch(track, listOf(song))

        assertEquals(ImportMatchStatus.IN_LIBRARY, match.status)
        assertEquals(7L, match.songId)
        assertEquals(1.0, match.score, 0.0)
    }

    @Test
    fun `duration tolerance supports safe exact metadata match`() {
        val track = imported("Tema", "Artista", duration = 180_000)
        val song = song(2, "Tema", "Artista", duration = 183_900)

        assertEquals(ImportMatchStatus.IN_LIBRARY, PlaylistImportMatcher.localMatch(track, listOf(song)).status)
        assertTrue(PlaylistImportMatcher.durationCompatible(180_000, 184_000))
        assertFalse(PlaylistImportMatcher.durationCompatible(180_000, 195_000))
    }

    @Test
    fun `same title and artist still identify a song when an upload has another duration`() {
        val track = imported("Tema", "Artista", duration = 180_000)
        val song = song(3, "Tema", "Artista", duration = 335_000)

        val match = PlaylistImportMatcher.localMatch(track, listOf(song))

        assertEquals(ImportMatchStatus.IN_LIBRARY, match.status)
        assertEquals(3L, match.songId)
    }

    @Test
    fun `best remote candidate is selected even when alternatives need review`() {
        val track = imported("Luz", "Norte", duration = 180_000)
        val first = remote("1", "Luz", "Norte", 180_000)
        val second = remote("2", "Luz (Official Video)", "Norte", 181_000)

        val selection = PlaylistImportMatcher.selectCandidates(track, listOf(first, second))

        assertTrue(selection.ambiguous)
        assertEquals("1", selection.selected?.remoteTrackId)
        assertEquals(2, selection.candidates.size)
    }

    @Test
    fun `version mismatch penalizes live remix and cover candidates`() {
        val track = imported("Luz", "Norte", duration = 180_000)
        val studio = remote("studio", "Luz (Official Audio)", "Norte", 180_500)
        val live = remote("live", "Luz Live Remix Cover", "Norte", 180_000)

        val selection = PlaylistImportMatcher.selectCandidates(track, listOf(live, studio))

        assertEquals("studio", selection.candidates.first().remoteTrackId)
    }

    @Test
    fun `detects duplicate tracks and keeps first original position`() {
        val tracks = listOf(
            imported("Tema", "A", position = 0),
            imported("Tema (Official Video)", "A", duration = 240_000, position = 1),
            imported("Otro", "A", position = 2),
        )

        assertEquals(setOf(1), PlaylistImportMatcher.duplicatePositions(tracks))
    }

    @Test
    fun `does not treat live or remix versions as the same playlist track`() {
        val tracks = listOf(
            imported("Tema", "A", duration = 180_000, position = 0),
            imported("Tema Live", "A", duration = 240_000, position = 1),
            imported("Tema Remix", "A", duration = 210_000, position = 2),
        )

        assertEquals(emptySet<Int>(), PlaylistImportMatcher.duplicatePositions(tracks))
    }

    private fun imported(
        title: String,
        artist: String,
        duration: Long = 180_000,
        isrc: String? = null,
        position: Int = 0,
    ) = ImportedTrack("source-$position", title, listOf(artist), durationMs = duration, isrc = isrc, originalPosition = position)

    private fun song(
        id: Long,
        title: String,
        artist: String,
        duration: Long = 180_000,
        isrc: String? = null,
    ) = SongEntity(
        id = id,
        title = title,
        artist = artist,
        durationMs = duration,
        contentUri = "content://song/$id",
        originalFileName = "$id.mp3",
        displayFileName = "$id.mp3",
        mimeType = "audio/mpeg",
        checksum = "checksum-$id",
        isrc = isrc,
    )

    private fun remote(id: String, title: String, artist: String, duration: Long): RemoteTrack {
        val remoteArtist = RemoteArtist("artist-$id", artist)
        return RemoteTrack(
            id = id,
            title = title,
            artist = remoteArtist,
            album = RemoteAlbum("album-$id", "", remoteArtist),
            durationMs = duration,
            coverUri = null,
            providerId = "youtube",
            providerName = "YouTube",
            license = ProviderLicense("youtube", "YouTube", null, true, true),
            attribution = null,
            allowsDownload = true,
            externalUrl = "https://www.youtube.com/watch?v=$id",
        )
    }
}
