package com.polentita.music.feature.editor

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.polentita.music.core.database.AlbumEntity
import com.polentita.music.domain.model.Song
import com.polentita.music.domain.artwork.ArtworkChoice
import com.polentita.music.domain.artwork.ArtworkEditingRepository
import com.polentita.music.domain.repository.MusicRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SongEditorUiState(
    val loading: Boolean = true,
    val song: Song? = null,
    val albums: List<AlbumEntity> = emptyList(),
    val artists: List<String> = emptyList(),
    val saving: Boolean = false,
    val saved: Boolean = false,
    val error: String? = null,
)

@HiltViewModel
class SongEditorViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: MusicRepository,
    private val artworkEditingRepository: ArtworkEditingRepository,
) : ViewModel() {
    private val songId = savedStateHandle.get<String>("songId")?.toLongOrNull() ?: -1
    private val operation = MutableStateFlow(SaveState())

    val state: StateFlow<SongEditorUiState> = combine(
        repository.observeSong(songId),
        repository.observeAlbums(),
        repository.observeArtists(),
        operation,
    ) { song, albums, artists, save ->
        SongEditorUiState(
            loading = false,
            song = song,
            albums = albums,
            artists = artists,
            saving = save.saving,
            saved = save.saved,
            error = save.error,
        )
    }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SongEditorUiState())

    fun save(updated: Song, artworkChoice: ArtworkChoice) = viewModelScope.launch {
        operation.value = SaveState(saving = true)
        runCatching { artworkEditingRepository.saveSong(updated, artworkChoice) }
            .onSuccess { operation.value = SaveState(saved = true) }
            .onFailure { error ->
                operation.value = SaveState(error = error.message ?: "No se pudieron guardar los cambios")
            }
    }

    fun clearError() {
        operation.value = operation.value.copy(error = null)
    }

    private data class SaveState(
        val saving: Boolean = false,
        val saved: Boolean = false,
        val error: String? = null,
    )
}
