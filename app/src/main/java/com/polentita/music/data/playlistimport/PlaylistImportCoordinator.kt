package com.polentita.music.data.playlistimport

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.polentita.music.MainActivity
import com.polentita.music.R
import com.polentita.music.core.database.DownloadDao
import com.polentita.music.core.database.DownloadStatus
import com.polentita.music.core.database.PlaylistImportEntity
import com.polentita.music.core.database.PlaylistImportItemEntity
import com.polentita.music.core.database.PlaylistImportCandidateEntity
import com.polentita.music.core.database.SongDao
import com.polentita.music.core.database.SongEntity
import com.polentita.music.core.network.NetworkAccessBlockedException
import com.polentita.music.core.network.NetworkAccessPolicy
import com.polentita.music.core.network.userMessage
import com.polentita.music.data.downloader.DownloadCoordinator
import com.polentita.music.data.extractor.YtDlpExtractor
import com.polentita.music.data.extractor.YtDlpSourceResolver
import com.polentita.music.data.provider.AuthorizedProviderRegistry
import com.polentita.music.domain.playlistimport.ImportedTrack
import com.polentita.music.domain.playlistimport.PlaylistImportItemState
import com.polentita.music.domain.playlistimport.PlaylistImportState
import com.polentita.music.domain.provider.AuthorizedDownloadSource
import com.polentita.music.domain.provider.ProviderLicense
import com.polentita.music.domain.provider.ProviderNotConfiguredException
import com.polentita.music.domain.provider.RemoteAlbum
import com.polentita.music.domain.provider.RemoteArtist
import com.polentita.music.domain.provider.RemoteTrack
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

@Singleton
class PlaylistImportCoordinator @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: PlaylistImportRepository,
    private val downloadDao: DownloadDao,
    private val songDao: SongDao,
    private val providerRegistry: AuthorizedProviderRegistry,
    private val downloadCoordinator: DownloadCoordinator,
    private val ytDlpExtractor: YtDlpExtractor,
    private val networkAccessPolicy: NetworkAccessPolicy,
) {
    private val processJob = SupervisorJob()
    private val scope = CoroutineScope(processJob + Dispatchers.IO)
    private val queueMutex = Mutex()
    private val resolutionJobs = ConcurrentHashMap<String, Job>()
    private val started = AtomicBoolean(false)
    private val ytDlpSourceResolver = YtDlpSourceResolver(ytDlpExtractor)

    fun restoreIfNeeded() {
        scope.launch {
            if (repository.activeImports().isNotEmpty()) ensureStarted()
        }
    }

    fun ensureStarted() {
        if (!started.compareAndSet(false, true)) return
        scope.launch {
            recoverInterruptedWork()
            combine(
                repository.observeHistory(),
                downloadCoordinator.downloadStatuses,
                networkAccessPolicy.state,
            ) { _, _, _ -> Unit }.collect {
                runCatching {
                    queueMutex.withLock {
                        reconcileActiveImports()
                        pumpActiveImports()
                    }
                }
            }
        }
    }

    fun shutdownForProcessTermination() {
        processJob.cancel()
    }

    @Synchronized
    fun resolveMissing(importId: String) {
        ensureStarted()
        if (resolutionJobs[importId]?.isActive == true) return
        resolutionJobs[importId] = scope.launch {
            try {
                resolveMissingNow(importId)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                repository.get(importId)?.let { imported ->
                    repository.update(
                        imported.copy(
                            state = PlaylistImportState.ERROR.name,
                            errorMessage = error.message ?: "No se pudieron resolver las canciones faltantes",
                            updatedAt = System.currentTimeMillis(),
                        ),
                    )
                }
            } finally {
                resolutionJobs.remove(importId)
            }
        }
    }

    suspend fun resolveMissingNow(importId: String) {
        val access = networkAccessPolicy.current()
        access.remoteBlockReason?.let { throw NetworkAccessBlockedException(it) }
        val provider = providerRegistry.defaultProvider()
            ?: throw ProviderNotConfiguredException("No hay un proveedor de búsqueda configurado")
        if (!provider.isConfigured) {
            throw ProviderNotConfiguredException(provider.configurationMessage.orEmpty())
        }
        val imported = repository.get(importId) ?: return
        repository.update(
            imported.copy(
                state = PlaylistImportState.RESOLVING.name,
                errorMessage = null,
                updatedAt = System.currentTimeMillis(),
            ),
        )
        var globalError: Throwable? = null
        val pending = repository.getItems(importId).filter { item ->
            item.selected && item.title.isNotBlank() && item.state in RESOLVABLE_ITEM_STATES
        }
        for (original in pending) {
            val current = repository.getItem(original.id) ?: continue
            repository.updateItem(
                current.copy(
                    state = PlaylistImportItemState.SEARCHING.name,
                    errorMessage = null,
                    updatedAt = System.currentTimeMillis(),
                ),
            )
            try {
                networkAccessPolicy.current().remoteBlockReason?.let { throw NetworkAccessBlockedException(it) }
                val query = buildQuery(current)
                val results = provider.search(query).getOrThrow()
                val selection = PlaylistImportMatcher.selectCandidates(current.toImportedTrack(), results)
                repository.replaceCandidates(current, selection.candidates, selection.selected)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                val latest = repository.getItem(current.id) ?: current
                repository.updateItem(
                    latest.copy(
                        state = PlaylistImportItemState.ERROR.name,
                        errorMessage = error.message ?: "No se pudo buscar una coincidencia",
                        updatedAt = System.currentTimeMillis(),
                    ),
                )
                if (error is ProviderNotConfiguredException) {
                    globalError = error
                    break
                }
            }
        }
        val latest = repository.get(importId) ?: return
        repository.update(
            latest.copy(
                state = if (globalError == null) PlaylistImportState.REVIEW.name else PlaylistImportState.ERROR.name,
                errorMessage = globalError?.message,
                updatedAt = System.currentTimeMillis(),
            ),
        )
    }

    suspend fun start(importId: String) {
        ensureStarted()
        queueMutex.withLock {
            val imported = repository.get(importId) ?: return
            require(imported.localPlaylistId != null) { "Crea la playlist local antes de continuar" }
            val now = System.currentTimeMillis()
            val items = repository.getItems(importId).map { item ->
                if (!item.selected && item.state !in PlaylistImportRepository.TERMINAL_ITEM_STATES) {
                    item.copy(
                        state = PlaylistImportItemState.OMITTED.name,
                        errorMessage = null,
                        updatedAt = now,
                    )
                } else {
                    item
                }
            }
            repository.updateItems(items)
            repository.update(
                imported.copy(
                    state = PlaylistImportState.RUNNING.name,
                    isPaused = false,
                    errorMessage = null,
                    updatedAt = now,
                ),
            )
            pump(imported.id)
        }
    }

    suspend fun pause(importId: String) {
        ensureStarted()
        queueMutex.withLock {
            val imported = repository.get(importId) ?: return
            reconcile(imported)
            val refreshed = repository.get(importId) ?: return
            repository.update(
                refreshed.copy(
                    state = PlaylistImportState.PAUSED.name,
                    isPaused = true,
                    updatedAt = System.currentTimeMillis(),
                ),
            )
            repository.getItems(importId).firstOrNull { it.state in ACTIVE_ITEM_STATES }?.let { item ->
                repository.updateItem(
                    item.copy(
                        state = PlaylistImportItemState.PAUSED.name,
                        updatedAt = System.currentTimeMillis(),
                    ),
                )
                item.downloadId?.let { downloadCoordinator.cancel(it) }
            }
        }
    }

    suspend fun resume(importId: String) {
        ensureStarted()
        queueMutex.withLock {
            val imported = repository.get(importId) ?: return
            networkAccessPolicy.current().downloadBlockReason?.let { throw NetworkAccessBlockedException(it) }
            reconcile(imported)
            val refreshed = repository.get(importId) ?: return
            repository.update(
                refreshed.copy(
                    state = PlaylistImportState.RUNNING.name,
                    isPaused = false,
                    errorMessage = null,
                    updatedAt = System.currentTimeMillis(),
                ),
            )
            val paused = repository.getItems(importId).firstOrNull {
                it.selected && it.state == PlaylistImportItemState.PAUSED.name
            }
            if (paused != null) {
                if (paused.downloadId != null) {
                    downloadCoordinator.retry(paused.downloadId)
                    repository.updateItem(
                        paused.copy(
                            state = PlaylistImportItemState.DOWNLOADING.name,
                            updatedAt = System.currentTimeMillis(),
                        ),
                    )
                } else {
                    repository.updateItem(
                        paused.copy(
                            state = PlaylistImportItemState.PENDING.name,
                            updatedAt = System.currentTimeMillis(),
                        ),
                    )
                }
            }
            pump(importId)
        }
    }

    suspend fun cancelImport(importId: String) {
        ensureStarted()
        queueMutex.withLock {
            val imported = repository.get(importId) ?: return
            reconcile(imported)
            val refreshed = repository.get(importId) ?: return
            val now = System.currentTimeMillis()
            val items = repository.getItems(importId)
            items.firstOrNull { it.state in ACTIVE_ITEM_STATES }?.downloadId?.let {
                downloadCoordinator.cancel(it)
            }
            repository.updateItems(PlaylistImportQueuePolicy.cancelRemaining(items, now))
            repository.update(
                refreshed.copy(
                    state = PlaylistImportState.CANCELLED.name,
                    isPaused = false,
                    updatedAt = now,
                ),
            )
        }
    }

    suspend fun cancelItem(itemId: String) {
        ensureStarted()
        queueMutex.withLock {
            val item = repository.getItem(itemId) ?: return
            repository.get(item.importId)?.let { reconcile(it) }
            val refreshed = repository.getItem(itemId) ?: return
            if (refreshed.state in PlaylistImportSnapshot.COMPLETED_ITEM_STATES) return
            refreshed.downloadId?.let { downloadCoordinator.cancel(it) }
            repository.omit(itemId)
            pump(refreshed.importId)
        }
    }

    suspend fun retryErrors(importId: String) {
        ensureStarted()
        repository.retryErrors(importId)
        val missing = repository.getItems(importId).any {
            it.selected && it.state == PlaylistImportItemState.MISSING.name
        }
        if (missing) {
            resolveMissing(importId)
        } else {
            start(importId)
        }
    }

    private suspend fun recoverInterruptedWork() {
        repository.activeImports().forEach { imported ->
            val now = System.currentTimeMillis()
            val items = repository.getItems(imported.id).map {
                PlaylistImportQueuePolicy.recoverAfterRestart(it, now)
            }
            repository.updateItems(items)
            if (imported.state == PlaylistImportState.RESOLVING.name) {
                repository.update(
                    imported.copy(
                        state = PlaylistImportState.REVIEW.name,
                        errorMessage = "El análisis remoto se interrumpió; revisa los elementos pendientes",
                        updatedAt = now,
                    ),
                )
            }
        }
    }

    private suspend fun reconcileActiveImports() {
        repository.activeImports().forEach { imported -> reconcile(imported) }
    }

    private suspend fun reconcile(imported: PlaylistImportEntity) {
        for (item in repository.getItems(imported.id).filter { it.downloadId != null }) {
            if (item.state in PlaylistImportRepository.TERMINAL_ITEM_STATES) continue
            val download = downloadDao.getById(requireNotNull(item.downloadId))
            if (download == null) {
                if (!recoverFromLibrary(imported, item) && !recoverMissingDownload(item)) {
                    updateStateIfChanged(
                        item,
                        PlaylistImportItemState.ERROR,
                        "El registro de descarga ya no existe; puedes reintentar sin duplicar archivos",
                    )
                }
                continue
            }
            when (download.status) {
                DownloadStatus.PENDING.name,
                DownloadStatus.DOWNLOADING.name,
                -> updateStateIfChanged(item, PlaylistImportItemState.DOWNLOADING, error = null)
                DownloadStatus.VALIDATING.name -> updateStateIfChanged(
                    item,
                    PlaylistImportItemState.VALIDATING,
                    error = null,
                )
                DownloadStatus.SAVING.name -> updateStateIfChanged(
                    item,
                    PlaylistImportItemState.SAVING,
                    error = null,
                )
                DownloadStatus.PAUSED.name -> {
                    updateStateIfChanged(item, PlaylistImportItemState.PAUSED, download.errorMessage)
                    if (!imported.isPaused && networkAccessPolicy.state.value.downloadAllowed) {
                        runCatching { downloadCoordinator.retry(download.id) }
                    }
                }
                DownloadStatus.COMPLETED.name -> {
                    val song = download.destinationUri?.let { songDao.findByUri(it) }
                        ?: item.localSongId?.let { songDao.getById(it) }
                    if (song?.isAvailable == true) {
                        repository.attachSong(
                            imported.id,
                            item.id,
                            song.id,
                            if (song.dateAdded < download.createdAt) {
                                PlaylistImportItemState.IN_LIBRARY
                            } else {
                                PlaylistImportItemState.COMPLETED
                            },
                        )
                    } else {
                        updateStateIfChanged(
                            item,
                            PlaylistImportItemState.ERROR,
                            "La descarga terminó, pero no se pudo recuperar su registro local",
                        )
                    }
                }
                DownloadStatus.FAILED.name -> if (!recoverFailedDownload(imported, item, download.errorMessage)) {
                    updateStateIfChanged(
                        item,
                        PlaylistImportItemState.ERROR,
                        download.errorMessage ?: "La descarga falló",
                    )
                }
                DownloadStatus.CANCELLED.name -> if (
                    imported.isPaused || !networkAccessPolicy.state.value.downloadAllowed
                ) {
                    updateStateIfChanged(
                        item,
                        PlaylistImportItemState.PAUSED,
                        networkAccessPolicy.state.value.downloadBlockReason?.userMessage()
                            ?: "La descarga está pausada",
                    )
                } else if (
                    imported.state != PlaylistImportState.CANCELLED.name &&
                    !recoverFailedDownload(imported, item, "La descarga se interrumpió")
                ) {
                    updateStateIfChanged(
                        item,
                        PlaylistImportItemState.ERROR,
                        "La descarga se interrumpió y no quedan coincidencias alternativas",
                    )
                }
            }
        }
    }

    private suspend fun pumpActiveImports() {
        if (hasActiveImportDownload()) return
        repository.activeImports()
            .firstOrNull { it.state == PlaylistImportState.RUNNING.name && !it.isPaused }
            ?.let { pump(it.id) }
    }

    private suspend fun recoverFromLibrary(
        imported: PlaylistImportEntity,
        item: PlaylistImportItemEntity,
    ): Boolean {
        val songId = findLocalSongId(item, repository.getSelectedCandidate(item.id)) ?: return false
        repository.attachSong(imported.id, item.id, songId)
        return true
    }

    private suspend fun pump(importId: String) {
        while (true) {
            val imported = repository.get(importId) ?: return
            if (imported.state != PlaylistImportState.RUNNING.name || imported.isPaused) return
            if (hasActiveImportDownload(importId)) return
            val items = repository.getItems(importId)
            val recoverablePause = items.firstOrNull {
                it.selected && it.state == PlaylistImportItemState.PAUSED.name && it.downloadId == null
            }
            if (recoverablePause != null && networkAccessPolicy.state.value.downloadAllowed) {
                repository.updateItem(
                    recoverablePause.copy(
                        state = PlaylistImportItemState.PENDING.name,
                        errorMessage = null,
                        updatedAt = System.currentTimeMillis(),
                    ),
                )
                continue
            }
            if (items.any { it.state in ACTIVE_ITEM_STATES }) return
            val next = PlaylistImportQueuePolicy.nextRunnable(items)
            if (next == null) {
                finishIfIdle(imported, items)
                return
            }
            processItem(imported, next)
        }
    }

    private suspend fun processItem(imported: PlaylistImportEntity, original: PlaylistImportItemEntity) {
        val candidate = repository.getSelectedCandidate(original.id)
        val localSongId = findLocalSongId(original, candidate)
        if (localSongId != null) {
            repository.attachSong(
                imported.id,
                original.id,
                localSongId,
                PlaylistImportItemState.IN_LIBRARY,
            )
            return
        }
        if (candidate == null) {
            updateStateIfChanged(
                original,
                PlaylistImportItemState.REQUIRES_REVIEW,
                "Elige una coincidencia antes de procesar esta canción",
            )
            return
        }
        if (resumeExistingDownload(original)) return
        repository.updateItem(
            original.copy(
                state = PlaylistImportItemState.PREPARING.name,
                downloadId = original.downloadId?.takeIf { downloadDao.getById(it) != null },
                attemptCount = original.attemptCount + 1,
                errorMessage = null,
                updatedAt = System.currentTimeMillis(),
            ),
        )
        try {
            networkAccessPolicy.current().downloadBlockReason?.let { throw NetworkAccessBlockedException(it) }
            val provider = providerRegistry.provider(candidate.providerId)
                ?: throw ProviderNotConfiguredException("El proveedor de la coincidencia ya no está disponible")
            val remote = candidate.toRemoteTrack(provider.displayName)
            val authorized = provider.resolveDownload(remote).getOrThrow()
            val source = authorized.source as? AuthorizedDownloadSource.YtDlp
                ?: error("La coincidencia no usa el flujo autorizado compatible")
            val resolved = ytDlpSourceResolver.resolve(
                title = original.title,
                artist = PlaylistImportRepository.artists(original).joinToString(", "),
                sourceUrl = source.sourceUrl,
                resolver = ytDlpExtractor::inspect,
            )
            val downloadId = downloadCoordinator.enqueueYtDlp(
                sourceUrl = resolved.sourceUrl,
                info = resolved.value,
                title = original.title,
                artist = PlaylistImportRepository.artists(original).joinToString(", "),
                // Una playlist no es un álbum. Los metadatos del proveedor
                // pueden traer un álbum distinto por canción y crear una
                // colección de álbumes de un solo tema.
                album = "",
                isrc = original.isrc,
                thumbnailUrl = original.artworkUrl,
            )
            val current = repository.getItem(original.id) ?: return
            repository.updateItem(
                current.copy(
                    state = PlaylistImportItemState.DOWNLOADING.name,
                    downloadId = downloadId,
                    errorMessage = null,
                    updatedAt = System.currentTimeMillis(),
                ),
            )
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (blocked: NetworkAccessBlockedException) {
            val current = repository.getItem(original.id) ?: return
            repository.updateItem(
                current.copy(
                    state = PlaylistImportItemState.PAUSED.name,
                    errorMessage = blocked.message,
                    updatedAt = System.currentTimeMillis(),
                ),
            )
        } catch (error: Throwable) {
            val current = repository.getItem(original.id) ?: return
            if (recoverPreparationFailure(current, error)) return
            repository.updateItem(
                current.copy(
                    state = PlaylistImportItemState.ERROR.name,
                    errorMessage = error.message ?: "No se pudo preparar la descarga",
                    updatedAt = System.currentTimeMillis(),
                ),
            )
        }
    }

    private suspend fun advanceToNextCandidate(item: PlaylistImportItemEntity): Boolean {
        val selected = repository.getSelectedCandidate(item.id)
        val next = repository.getCandidates(item.id).firstOrNull { candidate ->
            candidate.rank > (selected?.rank ?: -1)
        } ?: return false
        repository.selectCandidate(item.id, next.id)
        repository.getItem(item.id)?.let {
            repository.updateItem(
                PlaylistImportQueuePolicy.resetForNextCandidate(it, System.currentTimeMillis()),
            )
        }
        return true
    }

    private suspend fun recoverMissingDownload(item: PlaylistImportItemEntity): Boolean {
        val hasNext = hasNextCandidate(item)
        return when (
            PlaylistImportRecoveryPolicy.decide(
                attemptCount = item.attemptCount,
                hasNextCandidate = hasNext,
                providerUnavailable = false,
                errorMessage = null,
            )
        ) {
            PlaylistImportRecoveryAction.RETRY_CURRENT_CANDIDATE -> {
                repository.updateItem(
                    item.copy(
                        state = PlaylistImportItemState.PENDING.name,
                        downloadId = null,
                        errorMessage = "Recuperando la descarga automáticamente",
                        updatedAt = System.currentTimeMillis(),
                    ),
                )
                true
            }
            PlaylistImportRecoveryAction.TRY_NEXT_CANDIDATE -> advanceToNextCandidate(item)
            PlaylistImportRecoveryAction.NEEDS_REVIEW -> false
        }
    }

    private suspend fun recoverFailedDownload(
        imported: PlaylistImportEntity,
        item: PlaylistImportItemEntity,
        errorMessage: String?,
    ): Boolean {
        if (recoverFromLibrary(imported, item)) return true
        return when (
            PlaylistImportRecoveryPolicy.decide(
                attemptCount = item.attemptCount,
                hasNextCandidate = hasNextCandidate(item),
                providerUnavailable = false,
                errorMessage = errorMessage,
            )
        ) {
            PlaylistImportRecoveryAction.RETRY_CURRENT_CANDIDATE -> {
                val downloadId = item.downloadId ?: return recoverMissingDownload(item)
                try {
                    downloadCoordinator.retry(downloadId)
                    repository.updateItem(
                        item.copy(
                            state = PlaylistImportItemState.DOWNLOADING.name,
                            attemptCount = item.attemptCount + 1,
                            errorMessage = null,
                            updatedAt = System.currentTimeMillis(),
                        ),
                    )
                    true
                } catch (blocked: NetworkAccessBlockedException) {
                    repository.updateItem(
                        item.copy(
                            state = PlaylistImportItemState.PAUSED.name,
                            errorMessage = blocked.message,
                            updatedAt = System.currentTimeMillis(),
                        ),
                    )
                    true
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Throwable) {
                    advanceToNextCandidate(item)
                }
            }
            PlaylistImportRecoveryAction.TRY_NEXT_CANDIDATE -> advanceToNextCandidate(item)
            PlaylistImportRecoveryAction.NEEDS_REVIEW -> false
        }
    }

    private suspend fun recoverPreparationFailure(
        item: PlaylistImportItemEntity,
        error: Throwable,
    ): Boolean = when (
        PlaylistImportRecoveryPolicy.decide(
            attemptCount = item.attemptCount,
            hasNextCandidate = hasNextCandidate(item),
            providerUnavailable = error is ProviderNotConfiguredException,
            errorMessage = error.message,
        )
    ) {
        PlaylistImportRecoveryAction.RETRY_CURRENT_CANDIDATE -> {
            repository.updateItem(
                item.copy(
                    state = PlaylistImportItemState.PENDING.name,
                    downloadId = null,
                    errorMessage = "Reintentando automáticamente",
                    updatedAt = System.currentTimeMillis(),
                ),
            )
            true
        }
        PlaylistImportRecoveryAction.TRY_NEXT_CANDIDATE -> advanceToNextCandidate(item)
        PlaylistImportRecoveryAction.NEEDS_REVIEW -> false
    }

    private suspend fun hasNextCandidate(item: PlaylistImportItemEntity): Boolean {
        val selectedRank = repository.getSelectedCandidate(item.id)?.rank ?: -1
        return repository.getCandidates(item.id).any { it.rank > selectedRank }
    }

    private suspend fun resumeExistingDownload(item: PlaylistImportItemEntity): Boolean {
        val downloadId = item.downloadId ?: return false
        val download = downloadDao.getById(downloadId) ?: return false
        when (download.status) {
            DownloadStatus.PENDING.name,
            DownloadStatus.DOWNLOADING.name,
            DownloadStatus.VALIDATING.name,
            DownloadStatus.SAVING.name,
            -> {
                updateStateIfChanged(
                    item,
                    downloadItemState(download.status),
                    error = null,
                )
                return true
            }
            DownloadStatus.PAUSED.name,
            DownloadStatus.FAILED.name,
            DownloadStatus.CANCELLED.name,
            -> {
                downloadCoordinator.retry(download.id)
                repository.updateItem(
                    item.copy(
                        state = PlaylistImportItemState.DOWNLOADING.name,
                        errorMessage = null,
                        updatedAt = System.currentTimeMillis(),
                    ),
                )
                return true
            }
            DownloadStatus.COMPLETED.name -> {
                val songId = download.destinationUri
                    ?.let { songDao.findByUri(it)?.takeIf(SongEntity::isAvailable)?.id }
                    ?: item.localSongId?.takeIf { songDao.getById(it)?.isAvailable == true }
                if (songId != null) {
                    repository.attachSong(
                        importId = item.importId,
                        itemId = item.id,
                        songId = songId,
                        itemState = PlaylistImportItemState.COMPLETED,
                    )
                    return true
                }
            }
        }
        return false
    }

    private suspend fun findLocalSongId(
        item: PlaylistImportItemEntity,
        candidate: PlaylistImportCandidateEntity?,
    ): Long? {
        val songs = songDao.getAvailable()
        val tracks = buildList {
            add(item.toImportedTrack())
            candidate?.let { add(it.toImportedTrack(item)) }
        }
        return tracks.asSequence()
            .map { PlaylistImportMatcher.localMatch(it, songs) }
            .firstOrNull {
                it.status == com.polentita.music.domain.playlistimport.ImportMatchStatus.IN_LIBRARY
            }
            ?.songId
    }

    private suspend fun hasActiveImportDownload(currentImportId: String? = null): Boolean =
        repository.activeImports().any { imported ->
            (currentImportId == null || imported.id != currentImportId) &&
                repository.getItems(imported.id).any { it.state in HEAVY_ITEM_STATES }
        }

    private suspend fun finishIfIdle(
        imported: PlaylistImportEntity,
        items: List<PlaylistImportItemEntity>,
    ) {
        if (items.any { it.state in ACTIVE_ITEM_STATES || it.state == PlaylistImportItemState.PENDING.name }) return
        val finalState = PlaylistImportQueuePolicy.completionState(items)
        val now = System.currentTimeMillis()
        val final = imported.copy(
            state = finalState.name,
            isPaused = false,
            completedAt = now,
            updatedAt = now,
            errorMessage = null,
        )
        repository.update(final)
        if (!imported.completionNotified) {
            showCompletionNotification(final, items)
            repository.markCompletionNotified(imported.id)
        }
    }

    private suspend fun updateStateIfChanged(
        item: PlaylistImportItemEntity,
        state: PlaylistImportItemState,
        error: String? = item.errorMessage,
    ) {
        if (item.state == state.name && item.errorMessage == error) return
        repository.updateItem(
            item.copy(
                state = state.name,
                errorMessage = error,
                updatedAt = System.currentTimeMillis(),
            ),
        )
    }

    private fun showCompletionNotification(
        imported: PlaylistImportEntity,
        items: List<PlaylistImportItemEntity>,
    ) {
        runCatching {
            val manager = context.getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(
                NotificationChannel(
                    NOTIFICATION_CHANNEL,
                    context.getString(R.string.playlist_import_notification_channel),
                    NotificationManager.IMPORTANCE_DEFAULT,
                ),
            )
            val available = items.count { it.state in PlaylistImportSnapshot.COMPLETED_ITEM_STATES }
            val intent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val pendingIntent = PendingIntent.getActivity(
                context,
                imported.id.hashCode(),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            manager.notify(
                imported.id.hashCode(),
                NotificationCompat.Builder(context, NOTIFICATION_CHANNEL)
                    .setSmallIcon(R.drawable.ic_notification)
                    .setContentTitle(context.getString(R.string.playlist_import_finished_title))
                    .setContentText("$available de ${items.size} canciones disponibles · ${imported.name}")
                    .setContentIntent(pendingIntent)
                    .setAutoCancel(true)
                    .build(),
            )
        }
    }

    private fun buildQuery(item: PlaylistImportItemEntity): String = buildString {
        append(item.title.trim())
        val artists = PlaylistImportRepository.artists(item).joinToString(" ")
        if (artists.isNotBlank()) append(" ").append(artists)
    }

    private fun PlaylistImportItemEntity.toImportedTrack() = ImportedTrack(
        sourceId = sourceId,
        title = title,
        artists = PlaylistImportRepository.artists(this),
        album = album,
        durationMs = durationMs,
        trackNumber = trackNumber,
        discNumber = discNumber,
        isrc = isrc,
        artworkUrl = artworkUrl,
        originalPosition = originalPosition,
    )

    private fun PlaylistImportCandidateEntity.toImportedTrack(
        item: PlaylistImportItemEntity,
    ) = ImportedTrack(
        sourceId = remoteTrackId,
        title = title,
        artists = artist.trim().takeIf(String::isNotBlank)?.let(::listOf).orEmpty(),
        album = album,
        durationMs = durationMs,
        isrc = item.isrc,
        artworkUrl = artworkUrl,
        originalPosition = item.originalPosition,
    )

    private fun downloadItemState(status: String): PlaylistImportItemState = when (status) {
        DownloadStatus.VALIDATING.name -> PlaylistImportItemState.VALIDATING
        DownloadStatus.SAVING.name -> PlaylistImportItemState.SAVING
        else -> PlaylistImportItemState.DOWNLOADING
    }

    private fun com.polentita.music.core.database.PlaylistImportCandidateEntity.toRemoteTrack(
        providerName: String,
    ): RemoteTrack {
        val remoteArtist = RemoteArtist("$providerId:$artist", artist)
        return RemoteTrack(
            id = remoteTrackId,
            title = title,
            artist = remoteArtist,
            album = RemoteAlbum("$providerId:$album", album, remoteArtist),
            durationMs = durationMs,
            coverUri = artworkUrl,
            providerId = providerId,
            providerName = providerName,
            license = ProviderLicense(
                id = providerId,
                name = "Metadatos provistos por $providerName",
                url = externalUrl,
                allowsDownload = true,
                requiresAttribution = true,
            ),
            attribution = null,
            allowsDownload = true,
            externalUrl = externalUrl,
        )
    }

    companion object {
        private const val NOTIFICATION_CHANNEL = "polentita_playlist_imports"
        private val RESOLVABLE_ITEM_STATES = setOf(
            PlaylistImportItemState.MISSING.name,
            PlaylistImportItemState.REQUIRES_REVIEW.name,
            PlaylistImportItemState.ERROR.name,
        )
        private val ACTIVE_ITEM_STATES = setOf(
            PlaylistImportItemState.SEARCHING.name,
            PlaylistImportItemState.PREPARING.name,
            PlaylistImportItemState.DOWNLOADING.name,
            PlaylistImportItemState.VALIDATING.name,
            PlaylistImportItemState.SAVING.name,
            PlaylistImportItemState.PAUSED.name,
        )
        private val HEAVY_ITEM_STATES = setOf(
            PlaylistImportItemState.PREPARING.name,
            PlaylistImportItemState.DOWNLOADING.name,
            PlaylistImportItemState.VALIDATING.name,
            PlaylistImportItemState.SAVING.name,
        )
    }
}
