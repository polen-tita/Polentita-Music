package com.polentita.music.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.polentita.music.core.database.PlaylistSummary
import com.polentita.music.domain.model.Song
import com.polentita.music.domain.repository.MusicRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class HomeUiState(
    val loading: Boolean = true,
    val recent: List<Song> = emptyList(),
    val favorites: List<Song> = emptyList(),
    val mostPlayed: List<Song> = emptyList(),
    val history: List<Song> = emptyList(),
    val playlists: List<PlaylistSummary> = emptyList(),
    val error: String? = null,
)

@HiltViewModel
class HomeViewModel @Inject constructor(repository: MusicRepository) : ViewModel() {
    val uiState: StateFlow<HomeUiState> = combine(
        repository.observeRecentlyAdded(),
        repository.observeFavorites(),
        repository.observeMostPlayed(),
        repository.observeRecentHistory(),
        repository.observePlaylists(),
    ) { recent, favorites, mostPlayed, history, playlists ->
        HomeUiState(
            loading = false,
            recent = recent,
            favorites = favorites,
            mostPlayed = mostPlayed,
            history = history,
            playlists = playlists,
        )
    }.catch { emit(HomeUiState(loading = false, error = it.message ?: "No se pudo cargar Inicio")) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())
}
