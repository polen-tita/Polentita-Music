package com.polentita.music.feature.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.polentita.music.core.storage.AppPreferences
import com.polentita.music.core.storage.LibraryStorage
import com.polentita.music.core.storage.PreferencesStore
import com.polentita.music.domain.repository.MusicRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class AppUiState(
    val loading: Boolean = true,
    val preferences: AppPreferences = AppPreferences(),
)

@HiltViewModel
class AppViewModel @Inject constructor(
    private val preferencesStore: PreferencesStore,
    private val libraryStorage: LibraryStorage,
    private val repository: MusicRepository,
) : ViewModel() {
    init {
        viewModelScope.launch {
            runCatching { libraryStorage.ensureCoverFolderHidden() }
            runCatching { repository.syncFavoritesPlaylist() }
        }
    }

    val state: StateFlow<AppUiState> = preferencesStore.preferences
        .map { AppUiState(loading = false, preferences = it) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, AppUiState())

    fun recordDinoHighScore(score: Int) {
        viewModelScope.launch {
            runCatching { preferencesStore.recordDinoHighScore(score) }
        }
    }
}
