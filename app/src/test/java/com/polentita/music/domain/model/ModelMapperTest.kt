package com.polentita.music.domain.model

import com.polentita.music.core.database.SongEntity
import org.junit.Assert.assertEquals
import org.junit.Test

class ModelMapperTest {
    @Test
    fun `entity model conversion preserves all persisted fields`() {
        val entity = SongEntity(
            id = 7,
            title = "Sur",
            artist = "Artista",
            albumId = 2,
            albumName = "Casa",
            genre = "Folklore",
            year = 2026,
            trackNumber = 3,
            discNumber = 1,
            durationMs = 42_000,
            contentUri = "content://music/7",
            originalFileName = "sur.flac",
            displayFileName = "sur.flac",
            mimeType = "audio/flac",
            fileSize = 1234,
            coverUri = "content://covers/7",
            checksum = "abc",
            isFavorite = true,
        )
        val restored = entity.toModel().toEntity()
        assertEquals(entity, restored)
    }
}
