package com.polentita.music.domain.provider

data class RemoteArtist(
    val id: String,
    val name: String,
)

data class RemoteAlbum(
    val id: String,
    val name: String,
    val artist: RemoteArtist,
)

data class ProviderLicense(
    val id: String,
    val name: String,
    val url: String?,
    val allowsDownload: Boolean,
    val requiresAttribution: Boolean,
)

data class ProviderAttribution(
    val text: String,
    val url: String? = null,
)

data class RemoteTrack(
    val id: String,
    val title: String,
    val artist: RemoteArtist,
    val album: RemoteAlbum,
    val durationMs: Long,
    val coverUri: String?,
    val providerId: String,
    val providerName: String,
    val license: ProviderLicense,
    val attribution: ProviderAttribution?,
    val allowsDownload: Boolean,
    val externalUrl: String? = null,
)

data class RemoteSearchPage(
    val tracks: List<RemoteTrack>,
    val nextPageToken: String? = null,
)

sealed interface AuthorizedDownloadSource {
    data class YtDlp(
        val sourceUrl: String,
    ) : AuthorizedDownloadSource
}

data class AuthorizedDownload(
    val track: RemoteTrack,
    val fileName: String,
    val mimeType: String,
    val source: AuthorizedDownloadSource,
)

interface AuthorizedMusicProvider {
    val id: String
    val displayName: String
    val allowsDownload: Boolean
    val isConfigured: Boolean get() = true
    val configurationMessage: String? get() = null

    suspend fun search(query: String): Result<List<RemoteTrack>>

    suspend fun resolveDownload(track: RemoteTrack): Result<AuthorizedDownload>
}

interface PaginatedAuthorizedMusicProvider {
    suspend fun searchPage(query: String, pageToken: String? = null): Result<RemoteSearchPage>
}

class ProviderNotConfiguredException(message: String) : IllegalStateException(message)
class ProviderOfflineException(message: String) : IllegalStateException(message)
class DownloadNotAllowedException(message: String) : IllegalStateException(message)
