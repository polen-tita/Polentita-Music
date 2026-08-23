package com.polentita.music.feature.editor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.polentita.music.core.network.NetworkAccessPolicy
import com.polentita.music.domain.artwork.ArtworkCandidate
import com.polentita.music.domain.artwork.ArtworkSearchRepository
import com.polentita.music.domain.artwork.ArtworkSearchRequest
import com.polentita.music.domain.artwork.ArtworkSource
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ArtworkPickerUiState(
    val targetKey: String? = null,
    val query: String = "",
    val artist: String = "",
    val candidates: List<ArtworkCandidate> = emptyList(),
    val selectedSource: ArtworkSource? = null,
    val loading: Boolean = false,
    val loadingMore: Boolean = false,
    val page: Int = 0,
    val hasMore: Boolean = false,
    val remoteSearchAllowed: Boolean = false,
    val sourceErrors: Map<ArtworkSource, String> = emptyMap(),
    val error: String? = null,
) {
    val visibleCandidates: List<ArtworkCandidate>
        get() = selectedSource?.let { source -> candidates.filter { it.source == source } } ?: candidates

    val showNoMoreResultsHint: Boolean
        get() = page > 0 && !hasMore
}

@HiltViewModel
class ArtworkPickerViewModel @Inject constructor(
    private val repository: ArtworkSearchRepository,
    networkAccessPolicy: NetworkAccessPolicy,
) : ViewModel() {
    private val mutableState = MutableStateFlow(
        ArtworkPickerUiState(remoteSearchAllowed = networkAccessPolicy.state.value.remoteSearchAllowed),
    )
    val state: StateFlow<ArtworkPickerUiState> = mutableState.asStateFlow()
    private var searchJob: Job? = null

    init {
        viewModelScope.launch {
            networkAccessPolicy.state.collect { access ->
                val previous = mutableState.value
                val shouldSearchWhenRestored = access.remoteSearchAllowed &&
                    !previous.remoteSearchAllowed &&
                    previous.targetKey != null &&
                    previous.query.isNotBlank()
                if (!access.remoteSearchAllowed) searchJob?.cancel()
                mutableState.update {
                    it.copy(
                        remoteSearchAllowed = access.remoteSearchAllowed,
                        loading = if (access.remoteSearchAllowed) it.loading else false,
                        loadingMore = if (access.remoteSearchAllowed) it.loadingMore else false,
                        candidates = if (access.remoteSearchAllowed) it.candidates else emptyList(),
                        page = if (access.remoteSearchAllowed) it.page else 0,
                        hasMore = if (access.remoteSearchAllowed) it.hasMore else false,
                        sourceErrors = if (access.remoteSearchAllowed) it.sourceErrors else emptyMap(),
                        error = if (access.remoteSearchAllowed) it.error else null,
                    )
                }
                if (shouldSearchWhenRestored) search()
            }
        }
    }

    fun open(targetKey: String, initialQuery: String, artist: String) {
        searchJob?.cancel()
        mutableState.value = ArtworkPickerUiState(
            targetKey = targetKey,
            query = initialQuery,
            artist = artist,
            remoteSearchAllowed = mutableState.value.remoteSearchAllowed,
        )
        search()
    }

    fun setQuery(value: String) {
        mutableState.update { it.copy(query = value, error = null) }
    }

    fun setArtist(value: String) {
        mutableState.update { it.copy(artist = value, error = null) }
    }

    fun selectSource(source: ArtworkSource?) {
        mutableState.update { it.copy(selectedSource = source) }
    }

    fun search() {
        if (!mutableState.value.remoteSearchAllowed) {
            mutableState.update { it.copy(loading = false, candidates = emptyList(), error = null) }
            return
        }
        val request = ArtworkSearchRequest(
            query = mutableState.value.query.trim(),
            artist = mutableState.value.artist,
            page = 0,
        )
        if (request.query.isBlank()) {
            mutableState.update { it.copy(error = "Escribe un álbum o una canción para buscar") }
            return
        }
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            mutableState.update {
                it.copy(
                    loading = true,
                    loadingMore = false,
                    page = 0,
                    hasMore = false,
                    candidates = emptyList(),
                    sourceErrors = emptyMap(),
                    error = null,
                )
            }
            try {
                val result = repository.search(request)
                mutableState.update {
                    it.copy(
                        loading = false,
                        loadingMore = false,
                        page = 0,
                        candidates = result.candidates,
                        hasMore = result.hasMore,
                        sourceErrors = result.sourceErrors,
                    )
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                mutableState.update {
                    it.copy(
                        loading = false,
                        error = error.message ?: "No se pudieron buscar portadas",
                    )
                }
            }
        }
    }

    fun loadMore() {
        val current = mutableState.value
        if (!current.remoteSearchAllowed || current.loading || current.loadingMore ||
            !current.hasMore || current.targetKey == null
        ) return
        val nextPage = current.page + 1
        val request = ArtworkSearchRequest(
            query = current.query.trim(),
            artist = current.artist,
            page = nextPage,
        )
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            mutableState.update { it.copy(loadingMore = true, error = null) }
            try {
                val result = repository.search(request)
                mutableState.update { state ->
                    val candidates = (state.candidates + result.candidates).distinctBy { candidate ->
                        candidate.imageUrl.substringBefore('?')
                    }
                    state.copy(
                        loadingMore = false,
                        page = nextPage,
                        candidates = candidates,
                        hasMore = result.hasMore && candidates.size > state.candidates.size,
                        sourceErrors = state.sourceErrors + result.sourceErrors,
                    )
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                mutableState.update {
                    it.copy(
                        loadingMore = false,
                        error = error.message ?: "No se pudieron cargar más portadas",
                    )
                }
            }
        }
    }

    fun close() {
        searchJob?.cancel()
        mutableState.update {
            it.copy(
                targetKey = null,
                loading = false,
                loadingMore = false,
                page = 0,
                hasMore = false,
                candidates = emptyList(),
                sourceErrors = emptyMap(),
                error = null,
            )
        }
    }
}
