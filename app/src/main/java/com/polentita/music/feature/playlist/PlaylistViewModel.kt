package com.polentita.music.feature.playlist

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.polentita.music.core.database.PlaylistEntity
import com.polentita.music.domain.model.Song
import com.polentita.music.domain.repository.MusicRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class PlaylistUiState(
    val loading: Boolean = true,
    val playlist: PlaylistEntity? = null,
    val songs: List<Song> = emptyList(),
    val allSongs: List<Song> = emptyList(),
    val error: String? = null,
) {
    val durationMs get() = songs.sumOf(Song::durationMs)
}

@HiltViewModel
class PlaylistViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: MusicRepository,
) : ViewModel() {
    val playlistId: Long = savedStateHandle.get<String>("playlistId")?.toLongOrNull() ?: -1
    private val reorderMutex = Mutex()
    private var pendingOrder: List<Long>? = null
    private val allSongs = MutableStateFlow<List<Song>>(emptyList())

    val state: StateFlow<PlaylistUiState> = combine(
        repository.observePlaylist(playlistId),
        repository.observePlaylistSongs(playlistId),
        allSongs,
    ) { playlist, songs, all ->
        PlaylistUiState(loading = false, playlist = playlist, songs = songs, allSongs = all)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PlaylistUiState())

    fun loadAllSongs() = viewModelScope.launch {
        allSongs.value = repository.observeSongs().first()
    }

    fun addSongs(ids: List<Long>) = viewModelScope.launch {
        repository.addSongsToPlaylist(playlistId, ids)
    }

    fun remove(songId: Long) = viewModelScope.launch {
        repository.removeSongFromPlaylist(playlistId, songId)
    }

    fun move(songId: Long, direction: Int) {
        val currentOrder = pendingOrder ?: state.value.songs.map(Song::id)
        val from = currentOrder.indexOf(songId)
        val to = from + direction
        if (from !in currentOrder.indices || to !in currentOrder.indices) return
        val reordered = currentOrder.toMutableList().apply {
            add(to, removeAt(from))
        }
        pendingOrder = reordered
        viewModelScope.launch {
            reorderMutex.withLock {
                try {
                    repository.reorderPlaylist(playlistId, reordered)
                } finally {
                    if (pendingOrder == reordered) pendingOrder = null
                }
            }
        }
    }

    fun update(name: String, description: String, coverUri: String?) {
        val current = state.value.playlist ?: return
        viewModelScope.launch {
            repository.updatePlaylist(
                current.copy(name = name.trim(), description = description.trim(), coverUri = coverUri),
            )
        }
    }

    fun delete() {
        state.value.playlist?.let { viewModelScope.launch { repository.deletePlaylist(it) } }
    }
}
