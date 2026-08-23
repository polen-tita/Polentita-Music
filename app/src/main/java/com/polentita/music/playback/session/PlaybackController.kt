package com.polentita.music.playback.session

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.polentita.music.R
import com.polentita.music.core.storage.PreferencesStore
import com.polentita.music.domain.model.Song
import com.polentita.music.domain.repository.MusicRepository
import com.polentita.music.playback.queue.PlaybackContext
import com.polentita.music.playback.queue.PlaybackContextKind
import com.polentita.music.playback.queue.PlaybackQueueOrigin
import com.polentita.music.playback.queue.playbackContextKey
import com.polentita.music.playback.queue.playbackContextKind
import com.polentita.music.playback.queue.playbackContextLabel
import com.polentita.music.playback.queue.playbackContextAnchorId
import com.polentita.music.playback.queue.playbackContinuation
import com.polentita.music.playback.queue.playbackQueueOrigin
import com.polentita.music.playback.queue.sortedForPlayback
import com.polentita.music.playback.queue.toMediaItem
import com.polentita.music.playback.queue.withPlaybackQueueMetadata
import com.polentita.music.playback.service.PlaybackService
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class QueueItem(
    val songId: Long,
    val title: String,
    val artist: String,
    val artworkUri: String?,
    val origin: PlaybackQueueOrigin,
)

data class PlaybackUiState(
    val connected: Boolean = false,
    val isPlaying: Boolean = false,
    val currentSongId: Long? = null,
    val currentMediaId: String? = null,
    val isPreview: Boolean = false,
    val title: String = "",
    val artist: String = "",
    val artworkUri: String? = null,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val bufferedPercentage: Int = 0,
    val shuffle: Boolean = false,
    val repeatMode: Int = Player.REPEAT_MODE_OFF,
    val volume: Float = 1f,
    val queue: List<QueueItem> = emptyList(),
    val currentIndex: Int = -1,
    val contextLabel: String? = null,
)

data class RemotePreview(
    val id: String,
    val title: String,
    val artist: String,
    val durationMs: Long,
    val artworkUri: String?,
    val streamUrl: String,
    val mimeType: String? = null,
    val httpHeaders: Map<String, String> = emptyMap(),
)

internal enum class PreviousPlaybackAction {
    RESTART,
    PREVIOUS,
}

internal enum class LibraryNavigationDirection {
    PREVIOUS,
    NEXT,
}

private const val PREVIOUS_RESTART_THRESHOLD_MS = 3_000L
private const val PREVIEW_DURATION_MS = 30_000L

internal fun previousPlaybackAction(
    positionMs: Long,
    canSeekPrevious: Boolean = true,
    hasRestartedCurrentItem: Boolean = false,
): PreviousPlaybackAction =
    if (
        hasRestartedCurrentItem &&
            positionMs <= PREVIOUS_RESTART_THRESHOLD_MS &&
            canSeekPrevious
    ) {
        PreviousPlaybackAction.PREVIOUS
    } else {
        PreviousPlaybackAction.RESTART
    }

internal fun neighboringAvailableSong(
    songs: List<Song>,
    currentSongId: Long?,
    direction: LibraryNavigationDirection,
): Song? {
    val availableSongs = songs.filter(Song::isAvailable)
    val currentIndex = availableSongs.indexOfFirst { it.id == currentSongId }
    if (currentIndex < 0) return null
    val neighborIndex = when (direction) {
        LibraryNavigationDirection.PREVIOUS -> currentIndex - 1
        LibraryNavigationDirection.NEXT -> currentIndex + 1
    }
    return availableSongs.getOrNull(neighborIndex)
}

internal fun libraryNavigationTarget(
    songs: List<Song>,
    currentSongId: Long?,
    direction: LibraryNavigationDirection,
): Song? = neighboringAvailableSong(songs, currentSongId, direction) ?: when (direction) {
    LibraryNavigationDirection.NEXT -> songs.firstOrNull(Song::isAvailable)
    LibraryNavigationDirection.PREVIOUS -> songs.lastOrNull(Song::isAvailable)
}

@Singleton
class PlaybackController @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: MusicRepository,
    private val preferences: PreferencesStore,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val _state = MutableStateFlow(PlaybackUiState())
    val state: StateFlow<PlaybackUiState> = _state.asStateFlow()
    private var controller: MediaController? = null
    private var libraryNavigationJob: Job? = null
    private var previewJob: Job? = null
    private var previewRestore: PreviewRestore? = null
    private var pendingPreview: RemotePreview? = null
    private var activeContext: PlaybackContext? = null
    private var latestLibrarySongs: List<Song> = emptyList()
    private var cachedQueue: List<QueueItem> = emptyList()
    private var queueDirty = true
    private val connectionGate = MediaControllerConnectionGate()

    private val playerListener = object : Player.Listener {
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            queueDirty = true
            val origin = mediaItem?.playbackQueueOrigin()
            val songId = mediaItem?.mediaId?.toLongOrNull()
            if (songId != null && origin != PlaybackQueueOrigin.MANUAL) {
                val kind = mediaItem.playbackContextKind()
                val key = mediaItem.playbackContextKey().orEmpty()
                val matchingSongs = activeContext
                    ?.takeIf { it.kind == kind && it.key == key }
                    ?.songs
                    .orEmpty()
                activeContext = kind?.let {
                    PlaybackContext(
                        kind = it,
                        key = key,
                        label = mediaItem.playbackContextLabel().orEmpty(),
                        songs = if (it == PlaybackContextKind.LIBRARY) {
                            latestLibrarySongs
                        } else {
                            matchingSongs
                        },
                        anchorSongId = songId,
                    )
                } ?: activeContext?.copy(anchorSongId = songId)
            }
            refresh()
        }

        override fun onEvents(player: Player, events: Player.Events) {
            queueDirty = true
            if (player.playbackState == Player.STATE_ENDED && player.isPreviewItem()) {
                restorePreview()
            }
            refresh()
        }
    }

    private val controllerListener = object : MediaController.Listener {
        override fun onDisconnected(disconnectedController: MediaController) {
            if (controller !== disconnectedController) return
            disconnectedController.removeListener(playerListener)
            controller = null
            previewJob?.cancel()
            previewJob = null
            previewRestore = null
            cachedQueue = emptyList()
            queueDirty = true
            _state.value = PlaybackUiState()
        }
    }

    init {
        ensureConnected()
        scope.launch {
            while (isActive) {
                refresh()
                delay(500)
            }
        }
    }

    fun ensureConnected() {
        val currentController = controller
        if (!connectionGate.tryStart(currentController?.isConnected == true)) return
        if (currentController != null) {
            currentController.removeListener(playerListener)
            currentController.release()
            controller = null
        }
        val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        val future = MediaController.Builder(context, token)
            .setListener(controllerListener)
            .buildAsync()
        future.addListener(
            {
                val connectedController = runCatching { future.get() }.getOrNull()
                connectionGate.complete()
                if (connectedController == null || !connectedController.isConnected) {
                    connectedController?.release()
                    _state.value = PlaybackUiState()
                    return@addListener
                }
                controller = connectedController
                queueDirty = true
                connectedController.addListener(playerListener)
                refresh()
                pendingPreview?.let { preview ->
                    pendingPreview = null
                    playPreview(preview)
                }
            },
            ContextCompat.getMainExecutor(context),
        )
    }

    fun playSongs(songs: List<Song>, startIndex: Int = 0, shuffle: Boolean = false) {
        playContext(
            songs = songs,
            startIndex = startIndex,
            shuffle = shuffle,
            context = PlaybackContext(
                kind = PlaybackContextKind.SINGLE,
                key = "selection",
                label = context.getString(R.string.playback_context_selection),
                songs = songs,
            ),
        )
    }

    fun playContext(
        songs: List<Song>,
        startIndex: Int = 0,
        shuffle: Boolean = false,
        context: PlaybackContext,
    ) {
        discardPreview()
        val availableSongs = songs.filter(Song::isAvailable)
        if (availableSongs.isEmpty()) return
        val safeStartIndex = startIndex.coerceIn(availableSongs.indices)
        activeContext = context.copy(
            songs = availableSongs,
            anchorSongId = availableSongs[safeStartIndex].id,
        )
        controller?.apply {
            val current = availableSongs[safeStartIndex]
            val continuation = playbackContinuation(
                currentSongId = current.id,
                contextSongs = availableSongs,
                manualSongIds = emptySet(),
                librarySongs = latestLibrarySongs,
            )
            setMediaItems(
                buildList {
                    add(current.toMediaItem(PlaybackQueueOrigin.CURRENT, activeContext))
                    addAll(
                        continuation.contextSongs.map {
                            it.toMediaItem(PlaybackQueueOrigin.CONTEXT, activeContext?.anchoredAt(it.id))
                        },
                    )
                    addAll(
                        continuation.librarySongs.map {
                            it.toMediaItem(
                                PlaybackQueueOrigin.LIBRARY_FALLBACK,
                                libraryPlaybackContext(it.id),
                            )
                        },
                    )
                },
                0,
                0,
            )
            shuffleModeEnabled = shuffle
            prepare()
            play()
        }
        ensureLibraryFallback()
    }

    fun playSong(song: Song) {
        playFromContext(
            song = song,
            context = PlaybackContext(
                kind = PlaybackContextKind.SINGLE,
                key = song.id.toString(),
                label = context.getString(R.string.library),
                songs = listOf(song),
            ),
        )
    }

    fun playFromContext(song: Song, context: PlaybackContext) {
        discardPreview()
        val player = controller ?: return
        activeContext = context.copy(anchorSongId = song.id)
        val manualItems = queuePlayerIndices(player.currentMediaItemIndex, player.mediaItemCount)
            .map(player::getMediaItemAt)
            .filter { it.playbackQueueOrigin() == PlaybackQueueOrigin.MANUAL }
            .map { it.withPlaybackQueueMetadata(PlaybackQueueOrigin.MANUAL, activeContext) }
        val continuation = playbackContinuation(
            currentSongId = song.id,
            contextSongs = context.songs,
            manualSongIds = manualItems.mapNotNull { it.mediaId.toLongOrNull() }.toSet(),
            librarySongs = latestLibrarySongs,
        )
        player.setMediaItems(
            buildList {
                add(song.toMediaItem(PlaybackQueueOrigin.CURRENT, activeContext))
                addAll(manualItems)
                addAll(
                    continuation.contextSongs.map {
                        it.toMediaItem(PlaybackQueueOrigin.CONTEXT, context.anchoredAt(it.id))
                    },
                )
                addAll(
                    continuation.librarySongs.map {
                        it.toMediaItem(
                            PlaybackQueueOrigin.LIBRARY_FALLBACK,
                            libraryPlaybackContext(it.id),
                        )
                    },
                )
            },
            0,
            0,
        )
        player.prepare()
        player.play()
        ensureLibraryFallback()
    }

    fun updateLibraryContext(songs: List<Song>) {
        val updatedSongs = songs.filter(Song::isAvailable).distinctBy(Song::id)
        val previousSongs = latestLibrarySongs
        latestLibrarySongs = updatedSongs
        if (previousSongs.map(Song::id) != updatedSongs.map(Song::id)) {
            scope.launch(Dispatchers.IO) {
                preferences.savePlaybackLibraryOrder(updatedSongs.map(Song::id))
            }
        }
        if (previousSongs == updatedSongs) return
        var current = activeContext
        if (current == null && controller?.currentMediaItem?.playbackContextKind() == PlaybackContextKind.LIBRARY) {
            current = PlaybackContext(
                kind = PlaybackContextKind.LIBRARY,
                key = "library",
                label = context.getString(R.string.library),
                songs = latestLibrarySongs,
                anchorSongId = controller?.currentMediaItem?.playbackContextAnchorId(),
            )
            activeContext = current
        }
        if (current?.kind == PlaybackContextKind.LIBRARY) {
            activeContext = current.copy(songs = latestLibrarySongs)
            rebuildAutomaticTail()
        } else if (current != null) {
            rebuildAutomaticTail()
        }
    }

    fun updatePlaybackContext(context: PlaybackContext) {
        val currentItem = controller?.currentMediaItem
        val active = activeContext ?: currentItem?.let { item ->
            val kind = item.playbackContextKind() ?: return@let null
            PlaybackContext(
                kind = kind,
                key = item.playbackContextKey().orEmpty(),
                label = item.playbackContextLabel().orEmpty(),
                songs = emptyList(),
                anchorSongId = item.playbackContextAnchorId(),
            )
        } ?: return
        if (active.kind != context.kind || active.key != context.key) return
        activeContext = context.copy(
            songs = context.songs.filter(Song::isAvailable),
            anchorSongId = active.anchorSongId,
        )
        rebuildAutomaticTail()
    }

    fun playPreview(preview: RemotePreview) {
        val player = controller
        if (player == null || !player.isConnected) {
            pendingPreview = preview
            ensureConnected()
            return
        }
        pendingPreview = null
        if (previewRestore == null && !player.isPreviewItem()) {
            previewRestore = player.capturePreviewRestore()
        }
        previewJob?.cancel()
        val item = preview.toMediaItem()
        player.setMediaItem(item, 0L)
        player.shuffleModeEnabled = false
        player.repeatMode = Player.REPEAT_MODE_OFF
        player.prepare()
        player.play()
        previewJob = scope.launch {
            delay(PREVIEW_DURATION_MS)
            if (controller === player && player.currentMediaItem?.mediaId == item.mediaId) {
                restorePreview()
            }
        }
    }

    fun stopPreview() {
        pendingPreview = null
        if (previewRestore != null || controller?.isPreviewItem() == true) {
            restorePreview()
        }
    }

    fun playNext(song: Song) {
        controller?.let { player ->
            val position = (player.currentMediaItemIndex + 1).coerceAtLeast(0)
            player.addMediaItem(
                position,
                song.toMediaItem(PlaybackQueueOrigin.MANUAL, activeContext),
            )
        }
    }

    fun addToQueue(song: Song) {
        controller?.let { player ->
            val insertionIndex = queuePlayerIndices(player.currentMediaItemIndex, player.mediaItemCount)
                .takeWhile {
                    player.getMediaItemAt(it).playbackQueueOrigin() == PlaybackQueueOrigin.MANUAL
                }
                .lastOrNull()
                ?.plus(1)
                ?: (player.currentMediaItemIndex + 1).coerceAtLeast(0)
            player.addMediaItem(
                insertionIndex,
                song.toMediaItem(PlaybackQueueOrigin.MANUAL, activeContext),
            )
        }
    }

    fun togglePlayPause() {
        controller?.let { if (it.isPlaying) it.pause() else it.play() }
    }

    fun play() = controller?.play()
    fun pause() = controller?.pause()
    fun next() {
        controller?.let { player ->
            if (player.isPreviewItem()) {
                restorePreview()
                return
            }
            if (player.repeatMode != Player.REPEAT_MODE_OFF || player.hasNextMediaItem()) {
                player.seekToNextMediaItem()
            } else {
                playLibraryNeighbor(player, LibraryNavigationDirection.NEXT)
            }
        }
    }
    fun previous() {
        controller?.let { player ->
            if (player.isPreviewItem()) {
                restorePreview()
                return
            }
            // MediaSessionService owns the restart/previous decision so app and
            // notification presses share the same two-press state.
            // Unlike seekToPreviousMediaItem(), this command remains available
            // for the first timeline item, where it is needed to restart at 0.
            player.seekToPrevious()
        }
    }
    fun seekTo(positionMs: Long) = controller?.seekTo(positionMs.coerceAtLeast(0))
    fun setVolume(volume: Float) {
        controller?.volume = volume.coerceIn(0f, 1f)
        refresh()
    }

    fun toggleShuffle() {
        controller?.let { it.shuffleModeEnabled = !it.shuffleModeEnabled }
    }

    fun cycleRepeat() {
        controller?.let {
            it.repeatMode = when (it.repeatMode) {
                Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ONE
                Player.REPEAT_MODE_ONE -> Player.REPEAT_MODE_ALL
                else -> Player.REPEAT_MODE_OFF
            }
        }
    }

    fun moveQueueItem(from: Int, to: Int) {
        val player = controller ?: return
        val currentIndex = player.currentMediaItemIndex
        val fromPlayerIndex = playerIndexForQueueItem(currentIndex, from)
        val toPlayerIndex = playerIndexForQueueItem(currentIndex, to)
        if (fromPlayerIndex == null || toPlayerIndex == null) return
        if (fromPlayerIndex !in 0 until player.mediaItemCount || toPlayerIndex !in 0 until player.mediaItemCount) return
        if (player.getMediaItemAt(fromPlayerIndex).playbackQueueOrigin() != PlaybackQueueOrigin.MANUAL) return
        if (player.getMediaItemAt(toPlayerIndex).playbackQueueOrigin() != PlaybackQueueOrigin.MANUAL) return
        player.moveMediaItem(fromPlayerIndex, toPlayerIndex)
    }

    fun removeQueueItem(index: Int) {
        val player = controller ?: return
        val playerIndex = playerIndexForQueueItem(player.currentMediaItemIndex, index) ?: return
        if (
            playerIndex in 0 until player.mediaItemCount &&
            player.getMediaItemAt(playerIndex).playbackQueueOrigin() == PlaybackQueueOrigin.MANUAL
        ) {
            player.removeMediaItem(playerIndex)
        }
    }

    fun clearQueue() {
        val player = controller ?: return
        val currentIndex = player.currentMediaItemIndex
        if (currentIndex < 0 || currentIndex >= player.mediaItemCount) {
            player.clearMediaItems()
            return
        }
        queuePlayerIndices(currentIndex, player.mediaItemCount)
            .asReversed()
            .filter { player.getMediaItemAt(it).playbackQueueOrigin() == PlaybackQueueOrigin.MANUAL }
            .forEach(player::removeMediaItem)
        if (currentIndex > 0) {
            player.removeMediaItems(0, currentIndex)
        }
    }

    private fun refresh() {
        val player = controller ?: return
        val item = player.currentMediaItem
        val metadata = item?.mediaMetadata
        val mediaId = item?.mediaId
        if (queueDirty) {
            cachedQueue = buildQueue(player)
            queueDirty = false
        }
        _state.value = PlaybackUiState(
            connected = true,
            isPlaying = player.isPlaying,
            currentSongId = item?.mediaId?.toLongOrNull(),
            currentMediaId = mediaId,
            isPreview = mediaId?.startsWith(PREVIEW_MEDIA_ID_PREFIX) == true,
            title = metadata?.title?.toString().orEmpty(),
            artist = metadata?.artist?.toString().orEmpty(),
            artworkUri = metadata?.artworkUri?.toString(),
            positionMs = player.currentPosition.coerceAtLeast(0),
            durationMs = player.duration.takeIf { it > 0 } ?: 0,
            bufferedPercentage = player.bufferedPercentage,
            shuffle = player.shuffleModeEnabled,
            repeatMode = player.repeatMode,
            volume = player.volume,
            queue = cachedQueue,
            currentIndex = player.currentMediaItemIndex,
            contextLabel = item?.playbackContextLabel(),
        )
    }

    private fun playLibraryNeighbor(
        player: MediaController,
        direction: LibraryNavigationDirection,
    ) {
        val currentSongId = player.currentMediaItem?.mediaId?.toLongOrNull() ?: return
        libraryNavigationJob?.cancel()
        libraryNavigationJob = scope.launch {
            val songs = if (latestLibrarySongs.isNotEmpty()) {
                latestLibrarySongs
            } else {
                withContext(Dispatchers.IO) {
                    val settings = preferences.current()
                    repository.observeSongs().first()
                        .sortedForPlayback(settings.librarySort, settings.libraryAscending)
                }
            }
            val neighbor = libraryNavigationTarget(songs, currentSongId, direction) ?: return@launch
            if (player.currentMediaItem?.mediaId?.toLongOrNull() != currentSongId) return@launch
            playFromContext(
                song = neighbor,
                context = PlaybackContext(
                    kind = PlaybackContextKind.LIBRARY,
                    key = "library",
                    label = context.getString(R.string.library),
                    songs = songs,
                ),
            )
        }
    }

    private fun buildQueue(player: Player): List<QueueItem> = buildList {
        val currentIndex = player.currentMediaItemIndex
        queuePlayerIndices(currentIndex, player.mediaItemCount).forEach { index ->
            val item: MediaItem = player.getMediaItemAt(index)
            add(
                QueueItem(
                    songId = item.mediaId.toLongOrNull() ?: -1,
                    title = item.mediaMetadata.title?.toString().orEmpty(),
                    artist = item.mediaMetadata.artist?.toString().orEmpty(),
                    artworkUri = item.mediaMetadata.artworkUri?.toString(),
                    origin = item.playbackQueueOrigin(),
                ),
            )
        }
    }

    private fun discardPreview() {
        pendingPreview = null
        previewJob?.cancel()
        previewJob = null
        previewRestore = null
        PreviewMediaHeadersRegistry.clear()
    }

    private fun ensureLibraryFallback() {
        if (latestLibrarySongs.isNotEmpty()) {
            rebuildAutomaticTail()
            return
        }
        libraryNavigationJob?.cancel()
        libraryNavigationJob = scope.launch {
            val songs = withContext(Dispatchers.IO) {
                val settings = preferences.current()
                repository.observeSongs().first()
                    .sortedForPlayback(settings.librarySort, settings.libraryAscending)
            }
            latestLibrarySongs = songs.filter(Song::isAvailable).distinctBy(Song::id)
            withContext(Dispatchers.IO) {
                preferences.savePlaybackLibraryOrder(latestLibrarySongs.map(Song::id))
            }
            rebuildAutomaticTail()
        }
    }

    private fun rebuildAutomaticTail() {
        val player = controller ?: return
        val currentSongId = player.currentMediaItem?.mediaId?.toLongOrNull() ?: return
        val context = activeContext ?: player.currentMediaItem?.let { item ->
            val kind = item.playbackContextKind() ?: return@let null
            PlaybackContext(
                kind = kind,
                key = item.playbackContextKey().orEmpty(),
                label = item.playbackContextLabel().orEmpty(),
                songs = if (kind == PlaybackContextKind.LIBRARY) latestLibrarySongs else emptyList(),
                anchorSongId = item.playbackContextAnchorId(),
            )
        } ?: return
        val followingIndices = queuePlayerIndices(player.currentMediaItemIndex, player.mediaItemCount)
        val manualItems = followingIndices
            .map(player::getMediaItemAt)
            .filter { it.playbackQueueOrigin() == PlaybackQueueOrigin.MANUAL }
        val currentOrigin = player.currentMediaItem?.playbackQueueOrigin()
        val continuation = playbackContinuation(
            currentSongId = if (currentOrigin == PlaybackQueueOrigin.MANUAL) {
                context.anchorSongId ?: currentSongId
            } else {
                currentSongId
            },
            contextSongs = context.songs,
            manualSongIds = buildSet {
                addAll(manualItems.mapNotNull { it.mediaId.toLongOrNull() })
                if (currentOrigin == PlaybackQueueOrigin.MANUAL) add(currentSongId)
            },
            librarySongs = latestLibrarySongs,
        )
        if (followingIndices.isNotEmpty()) {
            player.removeMediaItems(followingIndices.first(), player.mediaItemCount)
        }
        player.addMediaItems(
            buildList {
                addAll(manualItems)
                addAll(
                    continuation.contextSongs.map {
                        it.toMediaItem(PlaybackQueueOrigin.CONTEXT, context.anchoredAt(it.id))
                    },
                )
                addAll(
                    continuation.librarySongs.map {
                        it.toMediaItem(
                            PlaybackQueueOrigin.LIBRARY_FALLBACK,
                            libraryPlaybackContext(it.id),
                        )
                    },
                )
            },
        )
        refresh()
    }

    private fun libraryPlaybackContext(anchorSongId: Long? = null) = PlaybackContext(
        kind = PlaybackContextKind.LIBRARY,
        key = "library",
        label = context.getString(R.string.library),
        songs = latestLibrarySongs,
        anchorSongId = anchorSongId,
    )

    private fun restorePreview() {
        val player = controller ?: return
        previewJob?.cancel()
        previewJob = null
        val restore = previewRestore
        previewRestore = null
        PreviewMediaHeadersRegistry.clear()
        if (restore == null || restore.items.isEmpty()) {
            player.clearMediaItems()
            player.stop()
            refresh()
            return
        }
        player.setMediaItems(restore.items, restore.currentIndex, restore.positionMs)
        player.shuffleModeEnabled = restore.shuffle
        player.repeatMode = restore.repeatMode
        player.prepare()
        if (restore.wasPlaying) player.play() else player.pause()
        refresh()
    }

    private fun Player.capturePreviewRestore(): PreviewRestore {
        val items = buildList {
            repeat(mediaItemCount) { index -> add(getMediaItemAt(index)) }
        }
        return PreviewRestore(
            items = items,
            currentIndex = currentMediaItemIndex.coerceIn(0, (items.lastIndex).coerceAtLeast(0)),
            positionMs = currentPosition.coerceAtLeast(0),
            shuffle = shuffleModeEnabled,
            repeatMode = repeatMode,
            wasPlaying = isPlaying,
        )
    }

    private fun Player.isPreviewItem(): Boolean =
        currentMediaItem?.mediaId?.startsWith(PREVIEW_MEDIA_ID_PREFIX) == true

    private data class PreviewRestore(
        val items: List<MediaItem>,
        val currentIndex: Int,
        val positionMs: Long,
        val shuffle: Boolean,
        val repeatMode: Int,
        val wasPlaying: Boolean,
    )
}

private fun RemotePreview.toMediaItem(): MediaItem {
    val mediaId = "$PREVIEW_MEDIA_ID_PREFIX${id.ifBlank { streamUrl.hashCode() }}"
    PreviewMediaHeadersRegistry.put(mediaId, httpHeaders)
    val metadata = MediaMetadata.Builder()
        .setTitle(title)
        .setArtist(artist)
        .setDurationMs(durationMs)
        .setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC)
        .setArtworkUri(artworkUri?.let(Uri::parse))
        .setIsPlayable(true)
        .setExtras(previewHttpHeadersExtras(httpHeaders))
        .build()
    return MediaItem.Builder()
        .setMediaId(mediaId)
        .setUri(streamUrl)
        .setMimeType(mimeType?.takeIf(String::isNotBlank))
        .setMediaMetadata(metadata)
        .build()
}

internal fun playerIndexForQueueItem(currentPlayerIndex: Int, queueIndex: Int): Int? {
    if (currentPlayerIndex < 0 || queueIndex < 0) return null
    return currentPlayerIndex + queueIndex + 1
}

internal fun queuePlayerIndices(currentPlayerIndex: Int, mediaItemCount: Int): List<Int> {
    if (currentPlayerIndex !in 0 until mediaItemCount) return emptyList()
    return (currentPlayerIndex + 1 until mediaItemCount).toList()
}

internal fun mediaItemsForNewSong(
    newItem: MediaItem,
    currentItems: List<MediaItem>,
    currentIndex: Int,
): List<MediaItem> = listOf(newItem) + queuePlayerIndices(currentIndex, currentItems.size).map(currentItems::get)

internal class MediaControllerConnectionGate {
    private var connecting = false

    fun tryStart(alreadyConnected: Boolean): Boolean {
        if (alreadyConnected || connecting) return false
        connecting = true
        return true
    }

    fun complete() {
        connecting = false
    }
}

private fun PlaybackContext.anchoredAt(songId: Long): PlaybackContext = copy(anchorSongId = songId)
