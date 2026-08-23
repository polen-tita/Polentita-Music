package com.polentita.music.domain.playlistimport

enum class ImportedCollectionSource(val displayName: String) {
    FILE("Archivo"),
    SPOTIFY("Spotify"),
    TIDAL("TIDAL"),
    YOUTUBE("YouTube / YouTube Music"),
}

enum class ImportedCollectionType { PLAYLIST, ALBUM }

data class ImportedCollection(
    val source: ImportedCollectionSource,
    val sourceId: String,
    val sourceUrl: String? = null,
    val type: ImportedCollectionType = ImportedCollectionType.PLAYLIST,
    val name: String,
    val description: String? = null,
    val artworkUrl: String? = null,
    val owner: String? = null,
    val tracks: List<ImportedTrack>,
    val totalTracks: Int = tracks.size,
)

data class ImportedTrack(
    val sourceId: String,
    val title: String,
    val artists: List<String>,
    val album: String = "",
    val durationMs: Long = 0,
    val trackNumber: Int? = null,
    val discNumber: Int? = null,
    val isrc: String? = null,
    val artworkUrl: String? = null,
    val originalPosition: Int,
)

sealed interface PlaylistImportRequest {
    data class Url(val value: String) : PlaylistImportRequest

    data class FileContent(
        val displayName: String,
        val mimeType: String?,
        val content: String,
    ) : PlaylistImportRequest
}

interface PlaylistImportProvider {
    val source: ImportedCollectionSource
    val displayName: String
    val isConfigured: Boolean
    val configurationMessage: String?

    fun supports(request: PlaylistImportRequest): Boolean
    suspend fun analyze(request: PlaylistImportRequest): Result<ImportedCollection>
}

data class ImportedSourceReference(
    val source: ImportedCollectionSource,
    val type: ImportedCollectionType,
    val sourceId: String,
    val canonicalUrl: String,
)

enum class ImportMatchStatus {
    IN_LIBRARY,
    PROBABLE_MATCH,
    MISSING,
    REQUIRES_REVIEW,
    NOT_AVAILABLE,
    DUPLICATE,
    METADATA_ERROR,
}

enum class PlaylistImportState {
    ANALYZED,
    RESOLVING,
    REVIEW,
    READY,
    RUNNING,
    PAUSED,
    COMPLETED,
    PARTIAL,
    CANCELLED,
    ERROR,
}

enum class PlaylistImportItemState {
    IN_LIBRARY,
    PROBABLE_MATCH,
    MISSING,
    SEARCHING,
    REQUIRES_REVIEW,
    NOT_AVAILABLE,
    DUPLICATE,
    METADATA_ERROR,
    PENDING,
    PREPARING,
    DOWNLOADING,
    VALIDATING,
    SAVING,
    COMPLETED,
    OMITTED,
    PAUSED,
    ERROR,
}

enum class CandidateConfidence { HIGH, MEDIUM, LOW }

data class ImportCandidate(
    val providerId: String,
    val remoteTrackId: String,
    val title: String,
    val artist: String,
    val album: String,
    val durationMs: Long,
    val artworkUrl: String?,
    val externalUrl: String,
    val score: Double,
    val confidence: CandidateConfidence,
)
