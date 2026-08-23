package com.polentita.music.feature.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.polentita.music.core.database.PlaylistEntity
import com.polentita.music.core.network.NetworkAccessPolicy
import com.polentita.music.core.network.UnrestrictedNetworkAccessPolicy
import com.polentita.music.data.extractor.YtDlpPreviewInfo
import com.polentita.music.domain.model.Song
import com.polentita.music.domain.repository.MusicRepository
import com.polentita.music.playback.queue.PlaybackContext
import com.polentita.music.playback.queue.PlaybackContextKind
import com.polentita.music.playback.queue.PlaybackQueueOrigin
import com.polentita.music.playback.session.PlaybackController
import com.polentita.music.playback.session.PlaybackUiState
import com.polentita.music.playback.session.RemotePreview
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlin.random.Random
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
@OptIn(ExperimentalCoroutinesApi::class)
class PlayerViewModel @Inject constructor(
    private val controller: PlaybackController,
    private val repository: MusicRepository,
    networkAccessPolicy: NetworkAccessPolicy = UnrestrictedNetworkAccessPolicy,
) : ViewModel() {
    init {
        viewModelScope.launch {
            networkAccessPolicy.state
                .map { it.offlineMode }
                .distinctUntilChanged()
                .collect { offline -> if (offline) controller.stopPreview() }
        }
    }
    val state: StateFlow<PlaybackUiState> = controller.state
    val visualState: StateFlow<PlaybackUiState> = state
        .map {
            it.copy(
                positionMs = 0,
                durationMs = 0,
                bufferedPercentage = 0,
            )
        }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.Eagerly, PlaybackUiState())

    val isFavorite: StateFlow<Boolean> = state
        .map { playback -> playback.currentSongId }
        .distinctUntilChanged()
        .flatMapLatest { songId ->
            if (songId == null) {
                flowOf(false)
            } else {
                repository.observeSong(songId).map { it?.isFavorite == true }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    fun play(songs: List<Song>, index: Int = 0, shuffle: Boolean = false) =
        controller.playSongs(songs, index, shuffle)

    fun playSong(song: Song) = controller.playSong(song)

    fun playFromContext(
        song: Song,
        songs: List<Song>,
        kind: PlaybackContextKind,
        key: String,
        label: String,
    ) = controller.playFromContext(
        song = song,
        context = PlaybackContext(kind = kind, key = key, label = label, songs = songs),
    )

    fun playContext(
        songs: List<Song>,
        index: Int = 0,
        shuffle: Boolean = false,
        kind: PlaybackContextKind,
        key: String,
        label: String,
    ) = controller.playContext(
        songs = songs,
        startIndex = index,
        shuffle = shuffle,
        context = PlaybackContext(kind = kind, key = key, label = label, songs = songs),
    )

    fun updateLibraryContext(songs: List<Song>) = controller.updateLibraryContext(songs)

    fun updatePlaybackContext(
        songs: List<Song>,
        kind: PlaybackContextKind,
        key: String,
        label: String,
    ) = controller.updatePlaybackContext(
        PlaybackContext(kind = kind, key = key, label = label, songs = songs),
    )

    fun playPreview(preview: YtDlpPreviewInfo) = controller.playPreview(
        RemotePreview(
            id = preview.id,
            title = preview.title,
            artist = preview.artist,
            durationMs = preview.durationMs,
            artworkUri = preview.thumbnailUrl,
            streamUrl = preview.streamUrl,
            mimeType = preview.mimeType,
            httpHeaders = preview.httpHeaders,
        ),
    )

    fun stopPreview() = controller.stopPreview()

    fun playShuffled(songs: List<Song>) {
        if (songs.isEmpty()) return
        val currentSongId = state.value.currentSongId
        val candidateIndices = songs.indices.filter { songs[it].id != currentSongId }
        val startIndex = if (candidateIndices.isEmpty()) {
            0
        } else {
            candidateIndices[Random.nextInt(candidateIndices.size)]
        }
        controller.playSongs(songs, startIndex, shuffle = true)
    }

    fun playShuffled(
        songs: List<Song>,
        kind: PlaybackContextKind,
        key: String,
        label: String,
    ) {
        if (songs.isEmpty()) return
        val currentSongId = state.value.currentSongId
        val candidateIndices = songs.indices.filter { songs[it].id != currentSongId }
        val startIndex = if (candidateIndices.isEmpty()) {
            0
        } else {
            candidateIndices[Random.nextInt(candidateIndices.size)]
        }
        controller.playContext(
            songs = songs,
            startIndex = startIndex,
            shuffle = true,
            context = PlaybackContext(kind = kind, key = key, label = label, songs = songs),
        )
    }
    fun playNext(song: Song) = controller.playNext(song)
    fun addToQueue(song: Song) = controller.addToQueue(song)
    fun togglePlayPause() = controller.togglePlayPause()
    fun previous() = controller.previous()
    fun next() = controller.next()
    fun seek(positionMs: Long) = controller.seekTo(positionMs)
    fun toggleShuffle() = controller.toggleShuffle()
    fun cycleRepeat() = controller.cycleRepeat()
    fun setVolume(volume: Float) = controller.setVolume(volume)
    fun moveQueueItem(from: Int, to: Int) = controller.moveQueueItem(from, to)
    fun removeQueueItem(index: Int) = controller.removeQueueItem(index)
    fun clearQueue() = controller.clearQueue()

    fun toggleFavorite() {
        val id = state.value.currentSongId ?: return
        viewModelScope.launch { repository.toggleFavorite(id) }
    }

    fun saveQueueAsPlaylist(name: String) {
        val ids = state.value.queue
            .filter { it.origin == PlaybackQueueOrigin.MANUAL }
            .map { it.songId }
            .filter { it >= 0 }
        if (ids.isEmpty()) return
        viewModelScope.launch {
            val playlistId = repository.createPlaylist(PlaylistEntity(name = name))
            repository.addSongsToPlaylist(playlistId, ids)
        }
    }
}
