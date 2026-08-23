package com.polentita.music.data.provider

import com.polentita.music.core.common.FileNameSanitizer
import com.polentita.music.data.extractor.YtDlpExtractor
import com.polentita.music.data.extractor.YtDlpSearchResult
import com.polentita.music.domain.provider.AuthorizedDownload
import com.polentita.music.domain.provider.AuthorizedDownloadSource
import com.polentita.music.domain.provider.AuthorizedMusicProvider
import com.polentita.music.domain.provider.DownloadNotAllowedException
import com.polentita.music.domain.provider.PaginatedAuthorizedMusicProvider
import com.polentita.music.domain.provider.ProviderAttribution
import com.polentita.music.domain.provider.ProviderLicense
import com.polentita.music.domain.provider.RemoteAlbum
import com.polentita.music.domain.provider.RemoteArtist
import com.polentita.music.domain.provider.RemoteSearchPage
import com.polentita.music.domain.provider.RemoteTrack
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Singleton
class YouTubeAuthorizedMusicProvider @Inject constructor(
    private val extractor: YtDlpExtractor,
) : AuthorizedMusicProvider, PaginatedAuthorizedMusicProvider {
    override val id = PROVIDER_ID
    override val displayName = "YouTube"
    override val allowsDownload = true
    override val isConfigured = true
    override val configurationMessage: String? = null

    override suspend fun search(query: String): Result<List<RemoteTrack>> =
        searchPage(query).map(RemoteSearchPage::tracks)

    override suspend fun searchPage(
        query: String,
        pageToken: String?,
    ): Result<RemoteSearchPage> = withContext(Dispatchers.IO) {
        runCatching {
            val cleanQuery = query.trim()
            if (cleanQuery.isBlank()) return@runCatching RemoteSearchPage(emptyList())
            val page = pageToken.toPageIndex()
            val searchPage = extractor.search(cleanQuery, page, RESULTS_PER_PAGE)
            val tracks = searchPage.items
                .mapNotNull { it.toRemoteTrack() }
                .distinctBy(RemoteTrack::id)
            RemoteSearchPage(
                tracks = tracks,
                nextPageToken = if (searchPage.hasMore) {
                    (page + 1).toString()
                } else {
                    null
                },
            )
        }
    }

    override suspend fun resolveDownload(track: RemoteTrack): Result<AuthorizedDownload> =
        withContext(Dispatchers.IO) {
            runCatching {
                require(track.providerId == id) { "La pista pertenece a otro proveedor" }
                val sourceUrl = track.externalUrl
                    ?: throw DownloadNotAllowedException("YouTube no informó un enlace descargable")
                AuthorizedDownload(
                    track = track,
                    fileName = FileNameSanitizer.sanitize("${track.title}.webm"),
                    mimeType = "audio/webm",
                    source = AuthorizedDownloadSource.YtDlp(sourceUrl),
                )
            }
        }

    private fun String?.toPageIndex(): Int {
        if (this.isNullOrBlank()) return 0
        val page = toIntOrNull() ?: throw IllegalArgumentException("La página de YouTube no es válida")
        require(page >= 0) { "La página de YouTube no es válida" }
        return page
    }

    private fun YtDlpSearchResult.toRemoteTrack(): RemoteTrack? {
        val trackId = id.ifBlank { webpageUrl }
        val sourceUrl = webpageUrl.takeIf(String::isNotBlank) ?: return null
        if (trackId.isBlank()) return null
        val artist = RemoteArtist(
            id = "youtube-artist-$trackId",
            name = channel.ifBlank { "YouTube" },
        )
        return RemoteTrack(
            id = trackId,
            title = title.ifBlank { "Sin título" },
            artist = artist,
            album = RemoteAlbum("youtube", "YouTube", artist),
            durationMs = durationMs.coerceAtLeast(0),
            coverUri = thumbnailUrl,
            providerId = PROVIDER_ID,
            providerName = displayName,
            license = ProviderLicense(
                id = "youtube",
                name = "Licencia no informada",
                url = "https://www.youtube.com/t/terms",
                allowsDownload = false,
                requiresAttribution = true,
            ),
            attribution = ProviderAttribution("YouTube · ${artist.name}", sourceUrl),
            allowsDownload = allowsDownload,
            externalUrl = sourceUrl,
        )
    }

    companion object {
        const val PROVIDER_ID = "youtube"
        const val RESULTS_PER_PAGE = 20
    }
}
