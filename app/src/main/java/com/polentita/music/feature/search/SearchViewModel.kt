package com.polentita.music.feature.search

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.polentita.music.R
import com.polentita.music.core.database.AlbumEntity
import com.polentita.music.core.database.RemoteReferenceEntity
import com.polentita.music.core.database.SourceType
import com.polentita.music.core.network.NetworkAccessPolicy
import com.polentita.music.core.network.NetworkAccessState
import com.polentita.music.core.network.NetworkBlockReason
import com.polentita.music.core.network.UnrestrictedNetworkAccessPolicy
import com.polentita.music.core.network.userMessage
import com.polentita.music.core.storage.PreferencesStore
import com.polentita.music.data.downloader.DownloadCoordinator
import com.polentita.music.data.extractor.YtDlpExtractor
import com.polentita.music.data.extractor.YtDlpMediaInfo
import com.polentita.music.data.extractor.YtDlpPreviewInfo
import com.polentita.music.data.extractor.YtDlpSourceResolver
import com.polentita.music.data.provider.AuthorizedProviderRegistry
import com.polentita.music.data.repository.RemoteReferenceRepository
import com.polentita.music.domain.model.Song
import com.polentita.music.domain.model.SongFilter
import com.polentita.music.domain.model.SongSort
import com.polentita.music.domain.provider.AuthorizedDownloadSource
import com.polentita.music.domain.provider.AuthorizedMusicProvider
import com.polentita.music.domain.provider.DownloadNotAllowedException
import com.polentita.music.domain.provider.ProviderAttribution
import com.polentita.music.domain.provider.ProviderLicense
import com.polentita.music.domain.provider.ProviderNotConfiguredException
import com.polentita.music.domain.provider.ProviderOfflineException
import com.polentita.music.domain.provider.PaginatedAuthorizedMusicProvider
import com.polentita.music.domain.provider.RemoteAlbum
import com.polentita.music.domain.provider.RemoteArtist
import com.polentita.music.domain.provider.RemoteSearchPage
import com.polentita.music.domain.provider.RemoteTrack
import com.polentita.music.domain.repository.MusicRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.text.Normalizer
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

enum class SearchTab { LIBRARY, EXPLORE }

enum class ExploreStatus {
    INITIAL,
    LOADING,
    SUCCESS,
    EMPTY,
    ERROR,
    OFFLINE,
    PROVIDER_NOT_CONFIGURED,
    OFFLINE_MODE,
}

enum class RemoteDownloadStatus {
    IDLE,
    PREPARING,
    DOWNLOADING,
    READY,
    QUEUED,
    SUCCESS,
    ERROR,
    DOWNLOAD_NOT_ALLOWED,
}

enum class RemotePreviewStatus {
    IDLE,
    LOADING,
    READY,
    ERROR,
}

data class ExploreUiState(
    val status: ExploreStatus = ExploreStatus.INITIAL,
    val providerName: String? = null,
    val results: List<RemoteTrack> = emptyList(),
    val relatedTo: String? = null,
    val message: String? = null,
    val canLoadMore: Boolean = false,
    val loadingMore: Boolean = false,
    val loadMoreError: String? = null,
)

data class RemoteDownloadUiState(
    val status: RemoteDownloadStatus = RemoteDownloadStatus.IDLE,
    val trackId: String? = null,
    val inspected: YtDlpMediaInfo? = null,
    val sourceUrl: String? = null,
    val importedSong: Song? = null,
    val duplicate: Boolean = false,
    val message: String? = null,
)

data class RemotePreviewUiState(
    val status: RemotePreviewStatus = RemotePreviewStatus.IDLE,
    val trackId: String? = null,
    val preview: YtDlpPreviewInfo? = null,
    val message: String? = null,
)

data class SearchUiState(
    val loading: Boolean = true,
    val query: String = "",
    val results: List<Song> = emptyList(),
    val filter: SongFilter = SongFilter(),
    val sort: SongSort = SongSort.TITLE,
    val ascending: Boolean = true,
    val selectedTab: SearchTab = SearchTab.LIBRARY,
    val explore: ExploreUiState = ExploreUiState(),
    val remoteDownload: RemoteDownloadUiState = RemoteDownloadUiState(),
    val preview: RemotePreviewUiState = RemotePreviewUiState(),
    val albums: List<AlbumEntity> = emptyList(),
    val artists: List<String> = emptyList(),
    val savedReferences: List<RemoteReferenceEntity> = emptyList(),
    val savedReferenceKeys: Set<String> = emptySet(),
    val networkAccess: NetworkAccessState = NetworkAccessState(),
    val error: String? = null,
) {
    val isEmpty get() = !loading && results.isEmpty()
}

private data class SearchOptions(
    val filter: SongFilter,
    val sort: SongSort,
    val ascending: Boolean,
)

private data class SearchRequest(
    val query: String,
    val options: SearchOptions,
)

private data class SearchDisplay(
    val query: String,
    val options: SearchOptions,
    val tab: SearchTab,
)

private data class ExploreRequest(
    val query: String,
    val tab: SearchTab,
    val randomSeed: Long,
    val networkBlockReason: NetworkBlockReason?,
)

private data class LocalSearchResult(
    val loading: Boolean = true,
    val songs: List<Song> = emptyList(),
    val error: String? = null,
)

private data class SearchData(
    val local: LocalSearchResult,
    val explore: ExploreUiState,
    val download: RemoteDownloadUiState,
    val preview: RemotePreviewUiState,
    val savedReferences: List<RemoteReferenceEntity>,
)

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
@HiltViewModel
class SearchViewModel @Inject constructor(
    private val repository: MusicRepository,
    private val providerRegistry: AuthorizedProviderRegistry,
    private val downloadCoordinator: DownloadCoordinator,
    private val ytDlpExtractor: YtDlpExtractor,
    private val remoteReferenceRepository: RemoteReferenceRepository,
    private val preferencesStore: PreferencesStore,
    @ApplicationContext private val context: Context,
    private val networkAccessPolicy: NetworkAccessPolicy = UnrestrictedNetworkAccessPolicy,
) : ViewModel() {
    private val ytDlpSourceResolver = YtDlpSourceResolver(ytDlpExtractor)
    private val query = MutableStateFlow("")
    private val filter = MutableStateFlow(SongFilter())
    private val sort = MutableStateFlow(SongSort.TITLE)
    private val ascending = MutableStateFlow(true)
    private val selectedTab = MutableStateFlow(SearchTab.LIBRARY)
    private val remoteDownload = MutableStateFlow(RemoteDownloadUiState())
    private val remotePreview = MutableStateFlow(RemotePreviewUiState())
    private val exploreRefresh = MutableStateFlow(0L)
    private val exploreResults = MutableStateFlow(ExploreUiState())
    private var remoteDownloadJob: Job? = null
    private var remotePreviewJob: Job? = null
    private var explorePageJob: Job? = null
    private var pendingReferenceRemoval: RemoteReferenceEntity? = null
    private var lastExploreRequest: ExploreRequest? = null
    private var exploreProviderId: String? = null
    private var exploreProviderQuery: String? = null
    private var explorePageToken: String? = null

    init {
        viewModelScope.launch {
            runCatching { preferencesStore.current() }.getOrNull()?.let { preferences ->
                sort.value = preferences.searchSort.toSongSort()
                ascending.value = preferences.searchAscending
            }
        }
    }

    private val debouncedQuery = query
        .map(String::trim)
        .debounce(SEARCH_DEBOUNCE_MS)
        .distinctUntilChanged()

    init {
        viewModelScope.launch {
            combine(
                debouncedQuery,
                selectedTab,
                exploreRefresh,
                networkAccessPolicy.state.map { it.remoteBlockReason }.distinctUntilChanged(),
            ) { currentQuery, tab, randomSeed, blockReason ->
                ExploreRequest(currentQuery, tab, randomSeed, blockReason)
            }.collectLatest { request ->
                if (request.tab != SearchTab.EXPLORE || request == lastExploreRequest) return@collectLatest
                explorePageJob?.cancel()
                explorePageJob = null
                lastExploreRequest = request
                if (request.networkBlockReason != null) {
                    cancelRemotePreparation()
                    cancelExplorePaging()
                    exploreResults.value = blockedExploreState(networkAccessPolicy.state.value)
                    return@collectLatest
                }
                val songs = withContext(Dispatchers.IO) { repository.observeSongs().first() }
                providerSearch(request.query, songs, request.randomSeed)
                    .collect { state -> exploreResults.value = state }
            }
        }
    }

    init {
        viewModelScope.launch {
            networkAccessPolicy.state.collectLatest { access ->
                if (access.offlineMode) {
                    cancelRemotePreparation()
                    remoteDownload.value = RemoteDownloadUiState()
                    remotePreview.value = RemotePreviewUiState()
                }
            }
        }
    }

    init {
        viewModelScope.launch {
            repository.observeSongs().collectLatest { songs ->
                val current = exploreResults.value
                if (current.results.isEmpty()) return@collectLatest
                val filtered = current.results.withoutLibrarySongs(songs)
                if (filtered.size != current.results.size) {
                    exploreResults.value = current.copy(
                        status = if (filtered.isEmpty()) ExploreStatus.EMPTY else current.status,
                        results = filtered,
                    )
                }
            }
        }
    }

    private val options = combine(filter, sort, ascending, ::SearchOptions)

    private val localResults: Flow<LocalSearchResult> = combine(
        debouncedQuery,
        options,
        ::SearchRequest,
    ).flatMapLatest { request ->
        repository.search(
            request.query,
            request.options.filter,
            request.options.sort,
            request.options.ascending,
        ).map<List<Song>, LocalSearchResult> { LocalSearchResult(loading = false, songs = it) }
            .onStart { emit(LocalSearchResult(loading = true)) }
            .catch {
                emit(LocalSearchResult(loading = false, error = it.message ?: "No se pudo buscar"))
            }
    }.onStart { emit(LocalSearchResult(loading = true)) }

    private val display = combine(query, options, selectedTab, ::SearchDisplay)
    private val data = combine(
        localResults,
        exploreResults,
        remoteDownload,
        remotePreview,
        remoteReferenceRepository.observeAll(),
        ::SearchData,
    )
    private val libraryMetadata = combine(
        repository.observeAlbums(),
        repository.observeArtists(),
    ) { albums, artists -> albums to artists }

    val uiState: StateFlow<SearchUiState> = combine(
        display,
        data,
        libraryMetadata,
        networkAccessPolicy.state,
    ) { current, result, metadata, networkAccess ->
        SearchUiState(
            loading = result.local.loading,
            query = current.query,
            results = result.local.songs,
            filter = current.options.filter,
            sort = current.options.sort,
            ascending = current.options.ascending,
            selectedTab = current.tab,
            explore = result.explore,
            remoteDownload = result.download,
            preview = result.preview,
            albums = metadata.first,
            artists = metadata.second,
            savedReferences = result.savedReferences,
            savedReferenceKeys = result.savedReferences.mapTo(mutableSetOf()) {
                RemoteReferenceRepository.key(it.providerId, it.remoteTrackId)
            },
            networkAccess = networkAccess,
            error = result.local.error,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SearchUiState())

    fun setQuery(value: String) {
        if (query.value == value) return
        cancelExplorePaging()
        pendingReferenceRemoval = null
        query.value = value
        cancelRemotePreparation()
        remoteDownload.value = RemoteDownloadUiState()
        remotePreview.value = RemotePreviewUiState()
    }

    fun selectTab(tab: SearchTab) {
        cancelExplorePaging()
        cancelRemotePreparation()
        pendingReferenceRemoval = null
        if (tab != SearchTab.EXPLORE) lastExploreRequest = null
        selectedTab.value = tab
        remoteDownload.value = RemoteDownloadUiState()
        remotePreview.value = RemotePreviewUiState()
    }

    fun setFilter(value: SongFilter) {
        filter.value = value
    }

    fun setSort(value: SongSort) {
        sort.value = value
        viewModelScope.launch {
            runCatching { preferencesStore.setSearchSort(value.name) }
        }
    }

    fun toggleAscending() {
        ascending.value = !ascending.value
        viewModelScope.launch {
            runCatching { preferencesStore.setSearchAscending(ascending.value) }
        }
    }

    fun resetFilters() {
        filter.value = SongFilter()
    }

    fun download(track: RemoteTrack) {
        pendingReferenceRemoval = null
        startDownload(track)
    }

    private fun startDownload(track: RemoteTrack) {
        val access = networkAccessPolicy.state.value
        if (!access.downloadAllowed) {
            remoteDownload.value = RemoteDownloadUiState(
                status = RemoteDownloadStatus.ERROR,
                trackId = track.id,
                message = access.downloadBlockReason?.userMessage(),
            )
            return
        }
        remotePreviewJob?.cancel()
        remotePreviewJob = null
        remotePreview.value = RemotePreviewUiState()
        if (!track.allowsDownload) {
            remoteDownload.value = RemoteDownloadUiState(
                status = RemoteDownloadStatus.DOWNLOAD_NOT_ALLOWED,
                trackId = track.id,
                message = "La licencia de esta pista no permite descargarla",
            )
            return
        }
        remoteDownloadJob?.cancel()
        remoteDownloadJob = viewModelScope.launch {
            remoteDownload.value = RemoteDownloadUiState(
                status = RemoteDownloadStatus.PREPARING,
                trackId = track.id,
                message = context.getString(R.string.search_explore_download_preparing),
            )
            try {
                val provider = providerRegistry.provider(track.providerId)
                    ?: throw ProviderNotConfiguredException("El proveedor ya no está disponible")
                val authorized = provider.resolveDownload(track).getOrThrow()
                when (val source = authorized.source) {
                    is AuthorizedDownloadSource.YtDlp -> prepareYtDlpDownload(track, source.sourceUrl)
                }
            } catch (_: TimeoutCancellationException) {
                showDownloadError(
                    track.id,
                    IllegalStateException(context.getString(R.string.search_explore_remote_timeout)),
                )
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                showDownloadError(track.id, error)
            }
        }
    }

    fun enqueueYtDlp(title: String, artist: String, album: String, albumId: Long? = null) {
        val current = remoteDownload.value
        val info = current.inspected ?: return
        val sourceUrl = current.sourceUrl ?: return
        val access = networkAccessPolicy.state.value
        if (!access.downloadAllowed) {
            remoteDownload.value = current.copy(
                status = RemoteDownloadStatus.ERROR,
                message = access.downloadBlockReason?.userMessage(),
            )
            return
        }
        val referenceToRemove = pendingReferenceRemoval?.takeIf {
            it.remoteTrackId == current.trackId
        }
        viewModelScope.launch {
            remoteDownload.value = current.copy(
                status = RemoteDownloadStatus.DOWNLOADING,
                message = context.getString(R.string.search_explore_download_starting),
            )
            runCatching {
                downloadCoordinator.enqueueYtDlp(sourceUrl, info, title, artist, album, albumId)
            }.onSuccess {
                if (referenceToRemove != null && pendingReferenceRemoval == referenceToRemove) {
                    runCatching {
                        remoteReferenceRepository.remove(
                            referenceToRemove.providerId,
                            referenceToRemove.remoteTrackId,
                        )
                    }
                    pendingReferenceRemoval = null
                }
                remoteDownload.value = RemoteDownloadUiState(
                    status = RemoteDownloadStatus.QUEUED,
                    trackId = current.trackId,
                    message = context.getString(R.string.search_explore_download_queued),
                )
            }.onFailure { error ->
                remoteDownload.value = RemoteDownloadUiState(
                    status = RemoteDownloadStatus.ERROR,
                    trackId = current.trackId,
                    message = error.message ?: "No se pudo iniciar la descarga",
                )
            }
        }
    }

    fun preview(track: RemoteTrack) {
        val access = networkAccessPolicy.state.value
        if (!access.previewAllowed) {
            remotePreview.value = RemotePreviewUiState(
                status = RemotePreviewStatus.ERROR,
                trackId = track.id,
                message = access.remoteBlockReason?.userMessage(),
            )
            return
        }
        val sourceUrl = track.externalUrl ?: return
        remoteDownloadJob?.cancel()
        remoteDownloadJob = null
        remoteDownload.value = RemoteDownloadUiState()
        remotePreviewJob?.cancel()
        remotePreviewJob = viewModelScope.launch {
            remotePreview.value = RemotePreviewUiState(
                status = RemotePreviewStatus.LOADING,
                trackId = track.id,
            )
            try {
                val info = withTimeout(REMOTE_PREPARATION_TIMEOUT_MS) {
                    ytDlpSourceResolver.resolve(
                        title = track.title,
                        artist = track.artist.name,
                        sourceUrl = sourceUrl,
                        resolver = ytDlpExtractor::resolvePreview,
                    ).value
                }
                remotePreview.value = RemotePreviewUiState(
                    status = RemotePreviewStatus.READY,
                    trackId = track.id,
                    preview = info.copy(
                        title = info.title.ifBlank { track.title },
                        artist = info.artist.ifBlank { track.artist.name },
                        thumbnailUrl = info.thumbnailUrl ?: track.coverUri,
                    ),
                )
            } catch (_: TimeoutCancellationException) {
                remotePreview.value = RemotePreviewUiState(
                    status = RemotePreviewStatus.ERROR,
                    trackId = track.id,
                    message = context.getString(R.string.search_explore_remote_timeout),
                )
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                remotePreview.value = RemotePreviewUiState(
                    status = RemotePreviewStatus.ERROR,
                    trackId = track.id,
                    message = error.message ?: "No se pudo preparar el adelanto",
                )
            }
        }
    }

    fun refreshExplore() {
        if (selectedTab.value == SearchTab.EXPLORE) {
            if (!networkAccessPolicy.state.value.remoteSearchAllowed) {
                exploreResults.value = blockedExploreState(networkAccessPolicy.state.value)
                return
            }
            cancelExplorePaging()
            cancelRemotePreparation()
            pendingReferenceRemoval = null
            remoteDownload.value = RemoteDownloadUiState()
            remotePreview.value = RemotePreviewUiState()
            exploreResults.value = ExploreUiState(
                status = ExploreStatus.LOADING,
                providerName = providerRegistry.defaultProvider()?.displayName,
            )
            exploreRefresh.value++
        }
    }

    fun loadMoreExplore() {
        if (!networkAccessPolicy.state.value.remoteSearchAllowed) return
        val request = lastExploreRequest ?: return
        if (request.tab != SearchTab.EXPLORE) return
        val pageToken = explorePageToken ?: return
        val providerId = exploreProviderId ?: return
        val providerQuery = exploreProviderQuery ?: return
        val current = exploreResults.value
        if (!current.canLoadMore || current.loadingMore || explorePageJob?.isActive == true) return
        val provider = providerRegistry.provider(providerId) ?: return

        exploreResults.value = current.copy(loadingMore = true, loadMoreError = null)
        explorePageJob = viewModelScope.launch {
            try {
                val songs = withContext(Dispatchers.IO) { repository.observeSongs().first() }
                val page = fetchExplorePage(provider, providerQuery, pageToken).getOrThrow()
                if (lastExploreRequest != request) return@launch
                val currentState = exploreResults.value
                val appended = (
                    currentState.results + page.tracks.withoutLibrarySongs(songs)
                    ).distinctBy(RemoteTrack::id)
                explorePageToken = page.nextPageToken
                exploreResults.value = currentState.copy(
                    status = if (appended.isEmpty()) ExploreStatus.EMPTY else ExploreStatus.SUCCESS,
                    results = appended,
                    canLoadMore = page.nextPageToken != null,
                    loadingMore = false,
                    loadMoreError = null,
                )
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                if (lastExploreRequest == request) {
                    exploreResults.value = exploreResults.value.copy(
                        loadingMore = false,
                        loadMoreError = error.message ?: "No se pudieron cargar más resultados",
                    )
                }
            }
        }
    }

    fun clearRemoteDownloadMessage() {
        pendingReferenceRemoval = null
        remoteDownload.value = RemoteDownloadUiState()
    }

    fun clearPreview() {
        remotePreview.value = RemotePreviewUiState()
    }

    fun download(reference: RemoteReferenceEntity) {
        val provider = providerRegistry.provider(reference.providerId)
        if (provider == null) {
            pendingReferenceRemoval = null
            remoteDownload.value = RemoteDownloadUiState(
                status = RemoteDownloadStatus.ERROR,
                trackId = reference.remoteTrackId,
                message = "El proveedor de esta referencia ya no está disponible",
            )
            return
        }
        pendingReferenceRemoval = reference
        startDownload(reference.toRemoteTrack(provider.displayName, provider.allowsDownload))
    }

    fun removeReference(reference: RemoteReferenceEntity) {
        viewModelScope.launch {
            remoteReferenceRepository.remove(reference.providerId, reference.remoteTrackId)
        }
    }

    fun toggleReference(track: RemoteTrack) {
        if (track.externalUrl == null) return
        viewModelScope.launch {
            val key = RemoteReferenceRepository.key(track.providerId, track.id)
            runCatching {
                if (key in uiState.value.savedReferenceKeys) {
                    remoteReferenceRepository.remove(track)
                } else {
                    remoteReferenceRepository.save(track)
                }
            }.onFailure { error ->
                remoteDownload.value = RemoteDownloadUiState(
                    status = RemoteDownloadStatus.ERROR,
                    trackId = track.id,
                    message = error.message ?: "No se pudo guardar la referencia",
                )
            }
        }
    }

    private suspend fun prepareYtDlpDownload(track: RemoteTrack, sourceUrl: String) {
        val resolved = withTimeout(REMOTE_PREPARATION_TIMEOUT_MS) {
            ytDlpSourceResolver.resolve(
                title = track.title,
                artist = track.artist.name,
                sourceUrl = sourceUrl,
                resolver = ytDlpExtractor::inspect,
            )
        }
        val inspected = resolved.value.let { info ->
            info.copy(
                title = info.title.ifBlank { track.title },
                artist = info.artist.ifBlank { track.artist.name },
                album = "",
                thumbnailUrl = info.thumbnailUrl ?: track.coverUri,
            )
        }
        remoteDownload.value = RemoteDownloadUiState(
            status = RemoteDownloadStatus.READY,
            trackId = track.id,
            inspected = inspected,
            sourceUrl = resolved.sourceUrl,
            message = context.getString(R.string.search_explore_download_review),
        )
    }

    private fun showDownloadError(trackId: String, error: Throwable) {
        remoteDownload.value = RemoteDownloadUiState(
            status = if (error is DownloadNotAllowedException) {
                RemoteDownloadStatus.DOWNLOAD_NOT_ALLOWED
            } else {
                RemoteDownloadStatus.ERROR
            },
            trackId = trackId,
            message = error.message ?: "No se pudo preparar la descarga",
        )
    }

    private fun cancelRemotePreparation() {
        remoteDownloadJob?.cancel()
        remoteDownloadJob = null
        remotePreviewJob?.cancel()
        remotePreviewJob = null
    }

    private fun cancelExplorePaging() {
        explorePageJob?.cancel()
        explorePageJob = null
        explorePageToken = null
        exploreProviderId = null
        exploreProviderQuery = null
    }

    private suspend fun fetchExplorePage(
        provider: AuthorizedMusicProvider,
        query: String,
        pageToken: String?,
    ): Result<RemoteSearchPage> = when {
        provider is PaginatedAuthorizedMusicProvider -> provider.searchPage(query, pageToken)
        pageToken == null -> provider.search(query).map { tracks -> RemoteSearchPage(tracks) }
        else -> Result.success(RemoteSearchPage(emptyList()))
    }

    private fun providerSearch(
        currentQuery: String,
        songs: List<Song>,
        randomSeed: Long,
    ): Flow<ExploreUiState> = flow {
        val access = networkAccessPolicy.current()
        if (!access.remoteSearchAllowed) {
            emit(blockedExploreState(access))
            return@flow
        }
        val provider = providerRegistry.defaultProvider()
            ?: throw ProviderNotConfiguredException("No hay un proveedor autorizado configurado")
        if (!provider.isConfigured) {
            throw ProviderNotConfiguredException(
                provider.configurationMessage ?: "El proveedor no está configurado",
            )
        }
        val relatedSong = songs.relatedSongForExplore(randomSeed)
        val relatedQuery = relatedSong?.exploreQuery()
        val providerQuery = currentQuery.ifBlank { relatedQuery.orEmpty() }
        cancelExplorePaging()
        exploreProviderId = provider.id
        exploreProviderQuery = providerQuery.takeIf(String::isNotBlank)
        if (providerQuery.isBlank()) {
            emit(
                ExploreUiState(
                    status = ExploreStatus.EMPTY,
                    providerName = provider.displayName,
                    message = context.getString(R.string.search_explore_no_library_context),
                ),
            )
            return@flow
        }
        emit(ExploreUiState(status = ExploreStatus.LOADING, providerName = provider.displayName))
        fetchExplorePage(provider, providerQuery, pageToken = null)
            .onSuccess { page ->
                val newTracks = page.tracks.withoutLibrarySongs(songs)
                val displayTracks = if (currentQuery.isBlank()) {
                    newTracks.shuffled(kotlin.random.Random(randomSeed))
                } else {
                    newTracks
                }
                explorePageToken = page.nextPageToken
                emit(
                    ExploreUiState(
                        status = if (displayTracks.isEmpty()) ExploreStatus.EMPTY else ExploreStatus.SUCCESS,
                        providerName = provider.displayName,
                        results = displayTracks,
                        relatedTo = relatedQuery.takeIf { currentQuery.isBlank() },
                        message = if (page.tracks.isEmpty() && currentQuery.isBlank()) {
                            context.getString(R.string.search_explore_no_related_results)
                        } else {
                            null
                        },
                        canLoadMore = page.nextPageToken != null,
                    ),
                )
            }
            .onFailure { throw it }
    }.catch { error ->
        if (error is CancellationException) throw error
        emit(
            ExploreUiState(
                status = when (error) {
                    is ProviderOfflineException -> ExploreStatus.OFFLINE
                    is ProviderNotConfiguredException -> ExploreStatus.PROVIDER_NOT_CONFIGURED
                    else -> ExploreStatus.ERROR
                },
                message = error.message ?: "No se pudo consultar el proveedor",
            ),
        )
    }

    private fun blockedExploreState(access: NetworkAccessState): ExploreUiState = ExploreUiState(
        status = if (access.remoteBlockReason == NetworkBlockReason.OFFLINE_MODE) {
            ExploreStatus.OFFLINE_MODE
        } else {
            ExploreStatus.OFFLINE
        },
        providerName = providerRegistry.defaultProvider()?.displayName,
        message = access.remoteBlockReason?.userMessage(),
    )

    companion object {
        const val SEARCH_DEBOUNCE_MS = 300L
        // URL directa + hasta cinco alternativas públicas. El límite evita
        // una espera infinita sin cortar el fallback demasiado pronto.
        private const val REMOTE_PREPARATION_TIMEOUT_MS = 90_000L
    }
}

private fun List<RemoteTrack>.withoutLibrarySongs(songs: List<Song>): List<RemoteTrack> {
    val librarySongs = songs.filter(Song::isAvailable)
    val libraryUrls = librarySongs.mapNotNullTo(mutableSetOf()) { it.sourceUrl?.trim() }
    val normalizedLibrary = librarySongs.map { song ->
        NormalizedLibrarySong(
            title = song.title.duplicateMatchKey(),
            artist = song.artist.duplicateMatchKey(),
        )
    }

    return filterNot { track ->
        val normalizedTrack = NormalizedRemoteTrack(
            title = track.title.duplicateMatchKey(),
            artists = sequenceOf(track.artist.name, track.album.artist.name)
                .map(String::duplicateMatchKey)
                .filter(String::isNotBlank)
                .toList(),
        )
        normalizedLibrary.any { song -> song.matches(normalizedTrack) } ||
            track.externalUrl?.trim()?.let(libraryUrls::contains) == true
    }
}

private data class NormalizedLibrarySong(
    val title: String,
    val artist: String,
)

private data class NormalizedRemoteTrack(
    val title: String,
    val artists: List<String>,
)

private fun NormalizedLibrarySong.matches(track: NormalizedRemoteTrack): Boolean {
    val localTitle = title
    val remoteTitle = track.title
    if (!titlesMatch(localTitle, remoteTitle)) return false

    val localArtist = artist
    if (localArtist.isBlank()) return true
    if (track.artists.any { namesMatch(localArtist, it) }) return true

    // A user may have shortened the title by removing the artist prefix.
    return localTitle != remoteTitle && titlesOverlap(localTitle, remoteTitle)
}

private fun namesMatch(first: String, second: String): Boolean =
    first == second || first.containsAsWords(second) || second.containsAsWords(first)

private fun titlesMatch(first: String, second: String): Boolean =
    first == second || titlesOverlap(first, second)

private fun titlesOverlap(first: String, second: String): Boolean =
    first.length >= MIN_DUPLICATE_TITLE_LENGTH &&
        second.length >= MIN_DUPLICATE_TITLE_LENGTH &&
        (first.containsAsWords(second) || second.containsAsWords(first))

private fun String.containsAsWords(other: String): Boolean =
    other.isNotBlank() && " $this ".contains(" $other ")

private val duplicateMatchMarks = Regex("\\p{M}+")
private val duplicateMatchSeparators = Regex("[^\\p{L}\\p{N}]+")
private val duplicateMatchWhitespace = Regex("\\s+")
private const val MIN_DUPLICATE_TITLE_LENGTH = 3

private fun String.duplicateMatchKey(): String = Normalizer.normalize(this, Normalizer.Form.NFD)
    .replace(duplicateMatchMarks, "")
    .lowercase(Locale.ROOT)
    .replace(duplicateMatchSeparators, " ")
    .trim()
    .replace(duplicateMatchWhitespace, " ")

private fun List<Song>.relatedSongForExplore(randomSeed: Long): Song? {
    val candidates = asSequence()
        .filter(Song::isAvailable)
        .filter { it.sourceType == SourceType.DOWNLOADED.name }
        .toList()
        .ifEmpty {
            filter(Song::isAvailable)
        }
    return candidates.randomOrNull(kotlin.random.Random(randomSeed))
}

private fun Song.exploreQuery(): String =
    artist.trim().takeIf(String::isNotBlank)
        ?: albumName.trim().takeIf(String::isNotBlank)
        ?: title.trim()

private fun String.toSongSort(): SongSort = runCatching { SongSort.valueOf(this) }
    .getOrDefault(SongSort.TITLE)

private fun RemoteReferenceEntity.toRemoteTrack(
    providerName: String,
    allowsDownload: Boolean,
): RemoteTrack {
    val artistModel = RemoteArtist(
        id = "$providerId-artist-$artist",
        name = artist,
    )
    return RemoteTrack(
        id = remoteTrackId,
        title = title,
        artist = artistModel,
        album = RemoteAlbum(
            id = "$providerId-album-$album",
            name = album,
            artist = artistModel,
        ),
        durationMs = durationMs,
        coverUri = thumbnailUrl,
        providerId = providerId,
        providerName = providerName,
        license = ProviderLicense(
            id = license,
            name = license.ifBlank { "Licencia no informada" },
            url = null,
            allowsDownload = allowsDownload,
            requiresAttribution = attribution != null,
        ),
        attribution = attribution?.let(::ProviderAttribution),
        allowsDownload = allowsDownload,
        externalUrl = externalUrl,
    )
}
