package com.polentita.music.feature.downloads

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.polentita.music.core.database.DownloadEntity
import com.polentita.music.core.database.AlbumEntity
import com.polentita.music.core.network.NetworkAccessPolicy
import com.polentita.music.core.network.NetworkAccessState
import com.polentita.music.core.network.UnrestrictedNetworkAccessPolicy
import com.polentita.music.core.network.userMessage
import com.polentita.music.data.downloader.DownloadCoordinator
import com.polentita.music.data.extractor.YtDlpExtractor
import com.polentita.music.data.extractor.YtDlpMediaInfo
import com.polentita.music.data.extractor.YtDlpPreviewInfo
import com.polentita.music.data.extractor.YtDlpSearchPage
import com.polentita.music.data.extractor.YtDlpSearchResult
import com.polentita.music.domain.model.Song
import com.polentita.music.domain.repository.MusicRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.LinkedHashMap

enum class DownloadPreviewStatus { IDLE, LOADING, READY, ERROR }

data class DownloadPreviewUiState(
    val status: DownloadPreviewStatus = DownloadPreviewStatus.IDLE,
    val url: String? = null,
    val preview: YtDlpPreviewInfo? = null,
    val message: String? = null,
)

data class DownloadsUiState(
    val downloads: List<DownloadEntity> = emptyList(),
    val albums: List<AlbumEntity> = emptyList(),
    val artists: List<String> = emptyList(),
    val inspectingWithYtDlp: Boolean = false,
    val ytDlpInspected: YtDlpMediaInfo? = null,
    val ytDlpSourceUrl: String? = null,
    val preview: DownloadPreviewUiState = DownloadPreviewUiState(),
    val songToOpen: Song? = null,
    val remoteSearchQuery: String = "",
    val remoteSearchResults: List<YtDlpSearchResult> = emptyList(),
    val remoteSearchPage: Int = 0,
    val remoteSearchHasMore: Boolean = false,
    val remoteSearching: Boolean = false,
    val remoteLoadingMore: Boolean = false,
    val remoteSearchError: String? = null,
    val error: String? = null,
    val networkAccess: NetworkAccessState = NetworkAccessState(),
)

@HiltViewModel
class DownloadsViewModel @Inject constructor(
    private val coordinator: DownloadCoordinator,
    private val repository: MusicRepository,
    private val ytDlpExtractor: YtDlpExtractor,
    private val networkAccessPolicy: NetworkAccessPolicy = UnrestrictedNetworkAccessPolicy,
) : ViewModel() {
    private val inspection = MutableStateFlow(DownloadsUiState())
    private var searchJob: Job? = null
    private var inspectionJob: Job? = null
    private var previewJob: Job? = null
    private val searchCache = object : LinkedHashMap<SearchCacheKey, CachedSearch>(
        SEARCH_CACHE_MAX_ENTRIES,
        0.75f,
        true,
    ) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<SearchCacheKey, CachedSearch>?): Boolean =
            size > SEARCH_CACHE_MAX_ENTRIES
    }
    val state: StateFlow<DownloadsUiState> = combine(
        coordinator.downloads,
        inspection,
        repository.observeAlbums(),
        repository.observeArtists(),
        networkAccessPolicy.state,
    ) { downloads, local, albums, artists, access ->
        local.copy(
            downloads = downloads,
            albums = albums,
            artists = artists,
            networkAccess = access,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DownloadsUiState())

    init {
        viewModelScope.launch {
            networkAccessPolicy.state.collect { access ->
                if (access.offlineMode) {
                    searchJob?.cancel()
                    inspectionJob?.cancel()
                    previewJob?.cancel()
                    inspection.value = inspection.value.copy(
                        inspectingWithYtDlp = false,
                        preview = DownloadPreviewUiState(),
                        remoteSearching = false,
                        remoteLoadingMore = false,
                        error = access.remoteBlockReason?.userMessage(),
                    )
                }
            }
        }
    }

    fun inspectWithYtDlp(url: String) {
        val access = networkAccessPolicy.state.value
        if (!access.remoteSearchAllowed) {
            inspection.value = inspection.value.copy(error = access.remoteBlockReason?.userMessage())
            return
        }
        inspectionJob?.cancel()
        inspectionJob = viewModelScope.launch {
            inspection.value = inspection.value.copy(
                inspectingWithYtDlp = true,
                ytDlpInspected = null,
                ytDlpSourceUrl = null,
                preview = DownloadPreviewUiState(),
                error = null,
            )
            runCatching { ytDlpExtractor.inspect(url) }
                .onSuccess {
                    inspection.value = inspection.value.copy(
                        inspectingWithYtDlp = false,
                        ytDlpInspected = it,
                        ytDlpSourceUrl = url.trim(),
                    )
                }
                .onFailure {
                    inspection.value = inspection.value.copy(
                        inspectingWithYtDlp = false,
                        error = it.message ?: "yt-dlp no pudo analizar este enlace",
                    )
                }
        }
    }

    fun enqueueYtDlp(title: String, artist: String, album: String, albumId: Long? = null) {
        val info = inspection.value.ytDlpInspected ?: return
        val sourceUrl = inspection.value.ytDlpSourceUrl ?: return
        val access = networkAccessPolicy.state.value
        if (!access.downloadAllowed) {
            inspection.value = inspection.value.copy(error = access.downloadBlockReason?.userMessage())
            return
        }
        viewModelScope.launch {
            runCatching {
                coordinator.enqueueYtDlp(sourceUrl, info, title, artist, album, albumId)
            }.onSuccess {
                clearInspection()
            }.onFailure { error ->
                inspection.value = inspection.value.copy(
                    error = error.message ?: "No se pudo iniciar la descarga",
                )
            }
        }
    }

    fun preview(url: String) {
        val sourceUrl = url.trim()
        if (sourceUrl.isBlank()) return
        val access = networkAccessPolicy.state.value
        if (!access.previewAllowed) {
            inspection.value = inspection.value.copy(
                preview = DownloadPreviewUiState(
                    status = DownloadPreviewStatus.ERROR,
                    url = sourceUrl,
                    message = access.remoteBlockReason?.userMessage(),
                ),
            )
            return
        }
        previewJob?.cancel()
        previewJob = viewModelScope.launch {
            inspection.value = inspection.value.copy(
                preview = DownloadPreviewUiState(
                    status = DownloadPreviewStatus.LOADING,
                    url = sourceUrl,
                ),
            )
            runCatching { ytDlpExtractor.resolvePreview(sourceUrl) }
                .onSuccess { info ->
                    inspection.value = inspection.value.copy(
                        preview = DownloadPreviewUiState(
                            status = DownloadPreviewStatus.READY,
                            url = sourceUrl,
                            preview = info,
                        ),
                    )
                }
                .onFailure { error ->
                    inspection.value = inspection.value.copy(
                        preview = DownloadPreviewUiState(
                            status = DownloadPreviewStatus.ERROR,
                            url = sourceUrl,
                            message = error.message ?: "No se pudo preparar el adelanto",
                        ),
                    )
                }
        }
    }

    fun clearPreview() {
        inspection.value = inspection.value.copy(preview = DownloadPreviewUiState())
    }

    fun clearRemoteSearchError() {
        inspection.value = inspection.value.copy(remoteSearchError = null)
    }

    fun clearError() {
        inspection.value = inspection.value.copy(error = null)
    }

    fun retry(id: String) = viewModelScope.launch { coordinator.retry(id) }
    fun cancel(id: String) = viewModelScope.launch { coordinator.cancel(id) }
    fun deleteRecord(id: String) = viewModelScope.launch { coordinator.deleteRecord(id) }
    fun clearInspection() {
        inspection.value = inspection.value.copy(
            inspectingWithYtDlp = false,
            ytDlpInspected = null,
            ytDlpSourceUrl = null,
            preview = DownloadPreviewUiState(),
            error = null,
        )
    }

    fun setRemoteSearchQuery(value: String) {
        inspection.value = inspection.value.copy(
            remoteSearchQuery = value,
            remoteSearchError = null,
        )
    }

    fun submitRemoteSearch() {
        val access = networkAccessPolicy.state.value
        if (!access.remoteSearchAllowed) {
            inspection.value = inspection.value.copy(
                remoteSearchError = access.remoteBlockReason?.userMessage(),
            )
            return
        }
        val query = inspection.value.remoteSearchQuery.trim()
        if (query.length < MIN_SEARCH_LENGTH) {
            inspection.value = inspection.value.copy(
                remoteSearchError = "Escribe al menos 3 caracteres",
            )
            return
        }
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            delay(SEARCH_DEBOUNCE_MS)
            loadRemoteSearch(query, page = 0, append = false)
        }
    }

    fun loadMoreRemoteResults() {
        if (!networkAccessPolicy.state.value.remoteSearchAllowed) return
        val current = inspection.value
        if (current.remoteLoadingMore || !current.remoteSearchHasMore) return
        viewModelScope.launch {
            loadRemoteSearch(
                query = current.remoteSearchQuery.trim(),
                page = current.remoteSearchPage + 1,
                append = true,
            )
        }
    }

    fun retryRemoteSearch() {
        submitRemoteSearch()
    }

    fun useSearchResult(result: YtDlpSearchResult): String = result.webpageUrl

    fun openDownloaded(id: String) {
        viewModelScope.launch {
            val destination = state.value.downloads.firstOrNull { it.id == id }?.destinationUri
            val song = destination?.let { repository.findSongByUri(it) }
            inspection.value = inspection.value.copy(
                songToOpen = song,
                error = if (song == null) {
                    "La descarga terminó, pero la canción no está disponible en la biblioteca"
                } else {
                    null
                },
            )
        }
    }

    fun consumeSongToOpen() {
        inspection.value = inspection.value.copy(songToOpen = null)
    }

    private suspend fun loadRemoteSearch(query: String, page: Int, append: Boolean) {
        val access = networkAccessPolicy.current()
        if (!access.remoteSearchAllowed) {
            inspection.value = inspection.value.copy(
                remoteSearching = false,
                remoteLoadingMore = false,
                remoteSearchError = access.remoteBlockReason?.userMessage(),
            )
            return
        }
        val key = SearchCacheKey(query.lowercase(), page)
        val cached = searchCache[key]?.takeIf {
            System.currentTimeMillis() - it.savedAt < SEARCH_CACHE_MS
        }?.page
        inspection.value = inspection.value.copy(
            remoteSearching = !append,
            remoteLoadingMore = append,
            remoteSearchError = null,
            remoteSearchResults = if (append) {
                inspection.value.remoteSearchResults
            } else {
                emptyList()
            },
        )
        runCatching { cached ?: ytDlpExtractor.search(query, page, SEARCH_PAGE_SIZE) }
            .onSuccess { result ->
                searchCache[key] = CachedSearch(result, System.currentTimeMillis())
                inspection.value = inspection.value.copy(
                    remoteSearching = false,
                    remoteLoadingMore = false,
                    remoteSearchResults = if (append) {
                        (inspection.value.remoteSearchResults + result.items).distinctBy {
                            it.id.ifBlank { it.webpageUrl }
                        }
                    } else {
                        result.items.distinctBy { it.id.ifBlank { it.webpageUrl } }
                    },
                    remoteSearchPage = result.page,
                    remoteSearchHasMore = result.hasMore,
                    remoteSearchError = null,
                )
            }
            .onFailure { error ->
                inspection.value = inspection.value.copy(
                    remoteSearching = false,
                    remoteLoadingMore = false,
                    remoteSearchError = readableSearchError(error),
                )
            }
    }

    private fun readableSearchError(error: Throwable): String {
        val message = error.message.orEmpty()
        return when {
            message.contains("429") || message.contains("quota", ignoreCase = true) ||
                message.contains("limit", ignoreCase = true) ->
                "YouTube limitó temporalmente las búsquedas. Intenta nuevamente más tarde."
            message.contains("network", ignoreCase = true) ||
                message.contains("timed out", ignoreCase = true) ||
                message.contains("resolve", ignoreCase = true) ->
                "No se pudo conectar con YouTube. Revisa la red Wi-Fi."
            else -> message.ifBlank { "No se pudo buscar en YouTube con yt-dlp" }
        }
    }

    private data class SearchCacheKey(val query: String, val page: Int)
    private data class CachedSearch(val page: YtDlpSearchPage, val savedAt: Long)

    companion object {
        const val MIN_SEARCH_LENGTH = 3
        const val SEARCH_DEBOUNCE_MS = 350L
        const val SEARCH_PAGE_SIZE = 10
        const val SEARCH_CACHE_MS = 2 * 60 * 1_000L
        const val SEARCH_CACHE_MAX_ENTRIES = 24
    }
}
