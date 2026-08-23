package com.polentita.music.domain.artwork

import com.polentita.music.core.database.AlbumEntity
import com.polentita.music.domain.model.Song

enum class ArtworkSource {
    INTERNET,
    SPOTIFY,
    TIDAL,
    YOUTUBE,
}

data class ArtworkCandidate(
    val id: String,
    val source: ArtworkSource,
    val title: String,
    val artist: String,
    val imageUrl: String,
    val previewUrl: String? = null,
    val externalUrl: String? = null,
    val width: Int? = null,
    val height: Int? = null,
    val score: Double = 0.0,
)

data class ArtworkSearchRequest(
    val query: String,
    val artist: String = "",
    val page: Int = 0,
) {
    val fullQuery: String
        get() = listOf(query.trim(), artist.trim())
            .filter(String::isNotBlank)
            .distinct()
            .joinToString(" ")
}

data class ArtworkSearchResult(
    val candidates: List<ArtworkCandidate>,
    val sourceErrors: Map<ArtworkSource, String> = emptyMap(),
    val hasMore: Boolean = false,
)

sealed interface ArtworkChoice {
    data object Unchanged : ArtworkChoice
    data object Remove : ArtworkChoice
    data class LocalFile(val contentUri: String) : ArtworkChoice
    data class Remote(val candidate: ArtworkCandidate) : ArtworkChoice
}

interface ArtworkSearchRepository {
    suspend fun search(request: ArtworkSearchRequest): ArtworkSearchResult
}

interface ArtworkEditingRepository {
    suspend fun saveSong(song: Song, artworkChoice: ArtworkChoice)
    suspend fun saveAlbum(album: AlbumEntity, artworkChoice: ArtworkChoice)
}
