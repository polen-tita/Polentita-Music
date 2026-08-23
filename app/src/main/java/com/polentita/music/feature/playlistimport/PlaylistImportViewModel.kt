package com.polentita.music.feature.playlistimport

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.polentita.music.R
import com.polentita.music.core.database.DownloadEntity
import com.polentita.music.core.database.PlaylistImportEntity
import com.polentita.music.core.network.NetworkAccessPolicy
import com.polentita.music.core.network.NetworkAccessState
import com.polentita.music.data.downloader.DownloadCoordinator
import com.polentita.music.data.playlistimport.PublicPlaylistUnavailableException
import com.polentita.music.data.playlistimport.PlaylistImportCoordinator
import com.polentita.music.data.playlistimport.PlaylistImportProviderRegistry
import com.polentita.music.data.playlistimport.PlaylistImportRepository
import com.polentita.music.data.playlistimport.PlaylistImportSnapshot
import com.polentita.music.domain.playlistimport.PlaylistImportRequest
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class ImportProviderUi(
    val name: String,
    val configured: Boolean,
    val message: String?,
)

data class PlaylistImportUiState(
    val history: List<PlaylistImportEntity> = emptyList(),
    val selected: PlaylistImportSnapshot? = null,
    val downloads: Map<String, DownloadEntity> = emptyMap(),
    val providers: List<ImportProviderUi> = emptyList(),
    val network: NetworkAccessState = NetworkAccessState(),
    val busy: Boolean = false,
    val message: String? = null,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class PlaylistImportViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: PlaylistImportRepository,
    private val providerRegistry: PlaylistImportProviderRegistry,
    private val coordinator: PlaylistImportCoordinator,
    private val downloadCoordinator: DownloadCoordinator,
    networkAccessPolicy: NetworkAccessPolicy,
) : ViewModel() {
    private val selectedImportId = MutableStateFlow<String?>(null)
    private val busy = MutableStateFlow(false)
    private val message = MutableStateFlow<String?>(null)

    private val selected = selectedImportId.flatMapLatest { importId ->
        if (importId == null) flowOf(null) else repository.observe(importId)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val selectedDownloadIds = selected
        .map { snapshot ->
            snapshot?.items
                ?.mapNotNull { it.item.downloadId }
                ?.distinct()
                .orEmpty()
        }
        .distinctUntilChanged()

    private val selectedDownloads = selectedDownloadIds.flatMapLatest { ids ->
        if (ids.isEmpty()) flowOf(emptyList()) else downloadCoordinator.observe(ids)
    }

    val state: StateFlow<PlaylistImportUiState> = combine(
        repository.observeHistory(),
        selected,
        selectedDownloads,
        networkAccessPolicy.state,
        combine(busy, message, ::Pair),
    ) { history, current, downloads, network, transient ->
        PlaylistImportUiState(
            history = history,
            selected = current,
            downloads = downloads.associateBy(DownloadEntity::id),
            providers = providerRegistry.providers.map {
                ImportProviderUi(it.displayName, it.isConfigured, it.configurationMessage)
            },
            network = network,
            busy = transient.first,
            message = transient.second,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PlaylistImportUiState())

    fun openImport(importId: String) {
        selectedImportId.value = importId
        message.value = null
    }

    fun closeImport(): Boolean {
        if (selectedImportId.value == null) return false
        selectedImportId.value = null
        message.value = null
        return true
    }

    fun analyzeLink(value: String) = launchBusy {
        val clean = value.trim()
        require(clean.isNotBlank()) { "Pega un enlace de playlist o álbum" }
        selectedImportId.value = repository.analyze(PlaylistImportRequest.Url(clean))
    }

    fun analyzeFile(uri: Uri) = launchBusy {
        val file = withContext(Dispatchers.IO) {
            val resolver = context.contentResolver
            val displayName = resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { cursor ->
                    if (cursor.moveToFirst()) cursor.getString(0) else null
                }
                ?: uri.lastPathSegment
                ?: "playlist.json"
            val mimeType = resolver.getType(uri)
            val content = resolver.openInputStream(uri)?.bufferedReader()?.use { reader ->
                val buffer = CharArray(8_192)
                val result = StringBuilder()
                while (true) {
                    val read = reader.read(buffer)
                    if (read < 0) break
                    result.append(buffer, 0, read)
                    require(result.length <= MAX_FILE_CHARACTERS) { "El archivo supera el límite de 5 MB" }
                }
                result.toString()
            } ?: error("No se pudo leer el archivo")
            PlaylistImportRequest.FileContent(displayName, mimeType, content)
        }
        selectedImportId.value = repository.analyze(file)
    }

    fun setSelected(itemId: String, selected: Boolean) = launchOperation {
        repository.updateSelection(itemId, selected)
    }

    fun confirmLocalMatch(itemId: String) = launchOperation {
        repository.confirmLocalMatch(itemId)
    }

    fun createPlaylist(name: String, artworkUrl: String?) = launchBusy {
        val importId = selectedImportId.value ?: return@launchBusy
        repository.createLocalPlaylist(importId, name, artworkUrl)
        val hasMissing = repository.getItems(importId).any { item ->
            item.selected && item.state in setOf("MISSING", "PROBABLE_MATCH", "REQUIRES_REVIEW")
        }
        if (hasMissing) {
            coordinator.resolveMissing(importId)
        }
    }

    fun updateCollection(name: String, artworkUrl: String?) = launchOperation {
        val importId = selectedImportId.value ?: return@launchOperation
        repository.updateCollection(importId, name, artworkUrl)
    }

    fun resolveMissing() = launchOperation {
        val importId = selectedImportId.value ?: return@launchOperation
        coordinator.resolveMissing(importId)
    }

    fun selectCandidate(itemId: String, candidateId: String) = launchOperation {
        repository.selectCandidate(itemId, candidateId)
    }

    fun omit(itemId: String) = launchOperation {
        val item = repository.getItem(itemId) ?: return@launchOperation
        if (item.downloadId != null) coordinator.cancelItem(itemId) else repository.omit(itemId)
    }

    fun start() = launchBusy {
        coordinator.start(selectedImportId.value ?: return@launchBusy)
    }

    fun pause() = launchOperation {
        coordinator.pause(selectedImportId.value ?: return@launchOperation)
    }

    fun resume() = launchOperation {
        coordinator.resume(selectedImportId.value ?: return@launchOperation)
    }

    fun cancelImport() = launchOperation {
        coordinator.cancelImport(selectedImportId.value ?: return@launchOperation)
    }

    fun retryErrors() = launchOperation {
        coordinator.retryErrors(selectedImportId.value ?: return@launchOperation)
    }

    fun clearMessage() {
        message.value = null
    }

    private fun launchBusy(block: suspend () -> Unit) {
        viewModelScope.launch {
            busy.value = true
            message.value = null
            runCatching { block() }
                .onFailure { message.value = displayError(it) }
            busy.value = false
        }
    }

    private fun launchOperation(block: suspend () -> Unit) {
        viewModelScope.launch {
            message.value = null
            runCatching { block() }
                .onFailure { message.value = displayError(it) }
        }
    }

    private fun displayError(error: Throwable): String = when (error) {
        is PublicPlaylistUnavailableException -> context.getString(R.string.playlist_import_public_unavailable)
        else -> error.message ?: "No se pudo completar la operación"
    }

    companion object {
        private const val MAX_FILE_CHARACTERS = 5 * 1024 * 1024
    }
}
