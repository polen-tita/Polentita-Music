package com.polentita.music.feature.library

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.polentita.music.core.database.AlbumEntity
import com.polentita.music.core.database.PlaylistEntity
import com.polentita.music.core.database.PlaylistSummary
import com.polentita.music.core.storage.DeviceMusicScanner
import com.polentita.music.core.storage.LibraryStorage
import com.polentita.music.core.storage.PreferencesStore
import com.polentita.music.domain.model.Song
import com.polentita.music.domain.artwork.ArtworkChoice
import com.polentita.music.domain.artwork.ArtworkEditingRepository
import com.polentita.music.domain.model.SongFilter
import com.polentita.music.domain.model.SongSort
import com.polentita.music.domain.repository.ImportSummary
import com.polentita.music.domain.repository.MusicRepository
import com.polentita.music.domain.repository.ScanSummary
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class LibraryUiState(
    val loading: Boolean = true,
    val query: String = "",
    val songs: List<Song> = emptyList(),
    val sort: SongSort = SongSort.TITLE,
    val ascending: Boolean = true,
    val gridMode: Boolean = false,
    val albums: List<AlbumEntity> = emptyList(),
    val artists: List<String> = emptyList(),
    val playlists: List<PlaylistSummary> = emptyList(),
    val busy: Boolean = false,
    val message: String? = null,
    val error: String? = null,
) {
    val isEmpty: Boolean get() = !loading && songs.isEmpty()
}

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val repository: MusicRepository,
    private val storage: LibraryStorage,
    private val deviceMusicScanner: DeviceMusicScanner,
    private val preferencesStore: PreferencesStore,
    private val artworkEditingRepository: ArtworkEditingRepository,
) : ViewModel() {
    private val operation = MutableStateFlow(OperationState())
    private val sort = MutableStateFlow(SongSort.TITLE)
    private val ascending = MutableStateFlow(true)
    private val gridMode = MutableStateFlow(false)
    private val query = MutableStateFlow("")

    init {
        viewModelScope.launch {
            runCatching { preferencesStore.current() }.getOrNull()?.let { preferences ->
                sort.value = preferences.librarySort.toLibrarySongSort()
                ascending.value = preferences.libraryAscending
                gridMode.value = preferences.libraryGridMode
            }
        }
    }

    private val debouncedQuery = query
        .map(String::trim)
        .debounce { currentQuery ->
            if (currentQuery.isBlank()) 0L else SEARCH_DEBOUNCE_MS
        }
        .distinctUntilChanged()

    private val sortedSongs = combine(
        debouncedQuery,
        sort,
        ascending,
    ) { currentQuery, currentSort, isAscending ->
        Triple(currentQuery, currentSort, isAscending)
    }.flatMapLatest { (currentQuery, currentSort, isAscending) ->
        repository.search(
            query = currentQuery,
            filter = SongFilter(),
            sort = currentSort,
            ascending = isAscending,
        )
    }

    private val contentState = combine(
        sortedSongs,
        repository.observeAlbums(),
        repository.observeArtists(),
        repository.observePlaylists(),
        operation,
    ) { songs, albums, artists, playlists, op ->
        LibraryUiState(
            loading = false,
            songs = songs,
            sort = sort.value,
            ascending = ascending.value,
            albums = albums,
            artists = artists,
            playlists = playlists,
            busy = op.busy,
            message = op.message,
            error = op.error,
        )
    }

    val uiState: StateFlow<LibraryUiState> = combine(contentState, gridMode, query) { state, showGrid, currentQuery ->
        val normalizedQuery = currentQuery.trim()
        state.copy(
            gridMode = showGrid,
            query = currentQuery,
            albums = state.albums.filter { album ->
                normalizedQuery.isBlank() ||
                    album.name.contains(normalizedQuery, ignoreCase = true) ||
                    album.artist.contains(normalizedQuery, ignoreCase = true)
            },
            artists = state.artists.filter { artist ->
                normalizedQuery.isBlank() || artist.contains(normalizedQuery, ignoreCase = true)
            },
        )
    }.catch { emit(LibraryUiState(loading = false, error = it.message ?: "No se pudo cargar la biblioteca")) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LibraryUiState())

    fun setQuery(value: String) {
        query.value = value
    }

    fun linkLibrary(uri: Uri, onLinked: () -> Unit = {}) = launchOperation {
        storage.linkLibrary(uri)
        onLinked()
        "Biblioteca vinculada"
    }

    fun importFiles(uris: List<Uri>) = launchOperation {
        val summary = repository.importFiles(uris)
        summary.message()
    }

    fun scanLibrary() = launchOperation {
        val summary = repository.scanLibrary()
        summary.message()
    }

    fun scanDeviceMusic() = launchOperation {
        val uris = deviceMusicScanner.audioUris()
        if (uris.isEmpty()) return@launchOperation "No se encontró música en el dispositivo"
        repository.importFiles(uris).message()
    }

    fun toggleFavorite(songId: Long) = launchOperation { repository.toggleFavorite(songId); null }
    fun updateSong(song: Song) = launchOperation { repository.updateSong(song); "Cambios guardados" }

    fun setSort(value: SongSort) {
        sort.value = value
        viewModelScope.launch {
            runCatching { preferencesStore.setLibrarySort(value.name) }
        }
    }

    fun toggleAscending() {
        ascending.value = !ascending.value
        viewModelScope.launch {
            runCatching { preferencesStore.setLibraryAscending(ascending.value) }
        }
    }

    fun toggleGridMode() {
        gridMode.value = !gridMode.value
        viewModelScope.launch {
            runCatching { preferencesStore.setLibraryGridMode(gridMode.value) }
        }
    }

    fun removeSong(songId: Long, deleteFile: Boolean) = launchOperation {
        if (repository.removeSong(songId, deleteFile)) {
            if (deleteFile) "Canción y archivo eliminados" else "Canción quitada de Polentita Music"
        } else {
            throw IllegalStateException("No se pudo eliminar el archivo; no se modificó la biblioteca")
        }
    }
    fun rename(songId: Long, name: String) = launchOperation {
        if (!repository.renameSongFile(songId, name)) error("El proveedor no permitió renombrar el archivo")
        "Archivo renombrado"
    }

    fun createAlbum(name: String, artist: String, year: Int?, coverUri: String?) = launchOperation {
        repository.createAlbum(AlbumEntity(name = name.trim(), artist = artist.trim(), year = year, coverUri = coverUri))
        "Álbum creado"
    }

    fun createPlaylist(name: String, description: String = "", coverUri: String? = null) = launchOperation {
        repository.createPlaylist(
            PlaylistEntity(name = name.trim(), description = description.trim(), coverUri = coverUri),
        )
        "Playlist creada"
    }

    fun addToPlaylist(playlistId: Long, songId: Long) = launchOperation {
        repository.addSongsToPlaylist(playlistId, listOf(songId))
        "Canción agregada a la playlist"
    }

    fun updateAlbum(
        album: AlbumEntity,
        artworkChoice: ArtworkChoice = ArtworkChoice.Unchanged,
        onSaved: () -> Unit = {},
    ) = launchOperation(onSuccess = onSaved) {
        artworkEditingRepository.saveAlbum(album, artworkChoice)
        "Álbum actualizado"
    }

    fun assignSongsToAlbum(songs: List<Song>, album: AlbumEntity) = launchOperation {
        songs.forEach { song ->
            repository.updateSong(
                song.copy(
                    albumId = album.id,
                    albumName = album.name,
                    dateModified = System.currentTimeMillis(),
                ),
            )
        }
        if (songs.size == 1) "Canción agregada al álbum" else "${songs.size} canciones agregadas al álbum"
    }

    fun deleteAlbum(album: AlbumEntity) = launchOperation {
        repository.deleteAlbum(album)
        "Álbum eliminado; sus canciones se conservaron"
    }

    fun deleteArtist(artist: String) = launchOperation {
        repository.clearArtist(artist)
        "Artista eliminado; sus canciones se conservaron sin artista"
    }

    fun renameArtist(artist: String, newName: String) = launchOperation {
        val trimmedName = newName.trim()
        require(trimmedName.isNotBlank()) { "El nombre del artista no puede estar vacío" }
        repository.renameArtist(artist, trimmedName)
        "Artista actualizado"
    }

    fun cleanEmptyAlbums() = launchOperation {
        val deleted = repository.cleanEmptyAlbums()
        if (deleted == 0) "No había álbumes vacíos" else {
            "$deleted ${if (deleted == 1) "álbum vacío eliminado" else "álbumes vacíos eliminados"}"
        }
    }

    fun updatePlaylist(playlist: PlaylistEntity) = launchOperation {
        repository.updatePlaylist(playlist)
        "Playlist actualizada"
    }

    fun deletePlaylist(playlist: PlaylistEntity) = launchOperation {
        repository.deletePlaylist(playlist)
        "Playlist eliminada"
    }

    fun clearMessage() {
        operation.value = OperationState()
    }

    private fun launchOperation(
        onSuccess: () -> Unit = {},
        block: suspend () -> String?,
    ) {
        viewModelScope.launch {
            operation.value = OperationState(busy = true)
            runCatching { block() }
                .onSuccess {
                    operation.value = OperationState(message = it)
                    onSuccess()
                }
                .onFailure {
                    operation.value = OperationState(error = it.message ?: "La operación no pudo completarse")
                }
        }
    }

    private fun ImportSummary.message() =
        "Importadas: $imported · Duplicadas: $duplicates · Fallidas: $failed" +
            errors.firstOrNull()?.let { "\n$it" }.orEmpty()

    private fun ScanSummary.message() =
        "Agregadas: $added · Recuperadas: $restored · Faltantes: $missing · Fallidas: $failed"

    private data class OperationState(
        val busy: Boolean = false,
        val message: String? = null,
        val error: String? = null,
    )

    companion object {
        const val SEARCH_DEBOUNCE_MS = 300L
    }
}

private fun String.toLibrarySongSort(): SongSort = runCatching { SongSort.valueOf(this) }
    .getOrDefault(SongSort.TITLE)
