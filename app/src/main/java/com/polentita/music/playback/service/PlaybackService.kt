package com.polentita.music.playback.service

import android.app.Service
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Intent
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.drm.DrmSessionManagerProvider
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.ShuffleOrder
import androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.CommandButton
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionResult
import com.polentita.music.R
import com.polentita.music.core.artwork.SquareArtworkBitmapLoader
import com.polentita.music.core.database.PlaybackHistoryDao
import com.polentita.music.core.database.PlaybackHistoryEntity
import com.polentita.music.core.database.SongDao
import com.polentita.music.core.storage.PlaybackSnapshot
import com.polentita.music.core.storage.PreferencesStore
import com.polentita.music.domain.model.toModel
import com.polentita.music.playback.queue.PlaybackContext
import com.polentita.music.playback.queue.PlaybackContextKind
import com.polentita.music.playback.queue.PlaybackQueueOrigin
import com.polentita.music.playback.queue.playbackContextKey
import com.polentita.music.playback.queue.playbackContextKind
import com.polentita.music.playback.queue.playbackContextLabel
import com.polentita.music.playback.queue.playbackContextAnchorId
import com.polentita.music.playback.queue.playbackQueueOrigin
import com.polentita.music.playback.queue.priorityShuffleOrder
import com.polentita.music.playback.queue.sortedForPlayback
import com.polentita.music.playback.queue.toMediaItem
import com.polentita.music.playback.session.PlaybackSessionActivity
import com.polentita.music.playback.session.PreviousPlaybackAction
import com.polentita.music.playback.session.PREVIEW_HTTP_HEADERS_KEY
import com.polentita.music.playback.session.PREVIEW_MEDIA_ID_PREFIX
import com.polentita.music.playback.session.PreviewMediaHeadersRegistry
import com.polentita.music.playback.session.DEFAULT_PREVIEW_USER_AGENT
import com.polentita.music.playback.session.previewHttpHeaders
import com.polentita.music.playback.session.previousPlaybackAction
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

private class PreviewAwareMediaSourceFactory(
    context: android.content.Context,
) : MediaSource.Factory {
    private val defaultFactory = DefaultMediaSourceFactory(context)

    override fun createMediaSource(mediaItem: MediaItem): MediaSource {
        val metadataHeaders = mediaItem.mediaMetadata.extras
            ?.getBundle(PREVIEW_HTTP_HEADERS_KEY)
            ?.previewHttpHeaders()
            .orEmpty()
        val headers = PreviewMediaHeadersRegistry.get(mediaItem.mediaId).ifEmpty { metadataHeaders }
        if (headers.isEmpty()) {
            if (!mediaItem.mediaId.startsWith(PREVIEW_MEDIA_ID_PREFIX)) {
                return defaultFactory.createMediaSource(mediaItem)
            }
            val fallbackHttpFactory = DefaultHttpDataSource.Factory()
                .setUserAgent(DEFAULT_PREVIEW_USER_AGENT)
                .setDefaultRequestProperties(mapOf("Accept" to "*/*"))
            return DefaultMediaSourceFactory(fallbackHttpFactory).createMediaSource(mediaItem)
        }

        // YouTube's signed media URLs are short-lived and commonly require the
        // exact User-Agent/Referer calculated by yt-dlp. The registry is the
        // primary transport and MediaMetadata is the fallback across the
        // MediaController -> service hop.
        val userAgent = headers.entries.firstOrNull { (name, _) ->
            name.equals("User-Agent", ignoreCase = true)
        }?.value
        val requestHeaders = headers.filterKeys { name ->
            !name.equals("User-Agent", ignoreCase = true)
        }
        val httpFactory = DefaultHttpDataSource.Factory()
            .setUserAgent(userAgent ?: DEFAULT_PREVIEW_USER_AGENT)
            .setDefaultRequestProperties(requestHeaders)
        return DefaultMediaSourceFactory(httpFactory).createMediaSource(mediaItem)
    }

    override fun getSupportedTypes(): IntArray = defaultFactory.supportedTypes

    override fun setDrmSessionManagerProvider(
        drmSessionManagerProvider: DrmSessionManagerProvider,
    ): MediaSource.Factory {
        defaultFactory.setDrmSessionManagerProvider(drmSessionManagerProvider)
        return this
    }

    override fun setLoadErrorHandlingPolicy(
        loadErrorHandlingPolicy: LoadErrorHandlingPolicy,
    ): MediaSource.Factory {
        defaultFactory.setLoadErrorHandlingPolicy(loadErrorHandlingPolicy)
        return this
    }
}

@OptIn(markerClass = [UnstableApi::class])
@AndroidEntryPoint
class PlaybackService : MediaSessionService() {
    @Inject lateinit var songDao: SongDao
    @Inject lateinit var historyDao: PlaybackHistoryDao
    @Inject lateinit var preferences: PreferencesStore

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var player: ExoPlayer
    private var session: MediaSession? = null
    private var lastRecordedMediaId: String? = null
    private var stopRequested = false
    private var currentSongMetadataJob: Job? = null
    private var persistJob: Job? = null
    private val persistMutex = Mutex()
    private var wrappingLibrary = false
    private var installingShuffleOrder = false
    private var shuffleSeed = System.nanoTime()
    private var previousRestartPending = false

    private val taskRemovalHandler by lazy {
        PlaybackTaskRemovalHandler(
            stopEnabled = { preferences.current().stopPlaybackOnTaskRemoved },
            requestStop = ::requestPlaybackStop,
        )
    }

    private val shutdownCoordinator by lazy {
        PlaybackShutdownCoordinator(
            persistQueueAndPosition = ::persistQueue,
            stopPlayback = {
                if (::player.isInitialized) player.stop()
            },
            clearActiveQueue = {
                if (::player.isInitialized) player.clearMediaItems()
            },
            removeForegroundNotification = {
                stopForeground(Service.STOP_FOREGROUND_REMOVE)
            },
            releaseSession = {
                session?.release()
                session = null
            },
            stopService = ::stopSelf,
        )
    }

    override fun onCreate() {
        super.onCreate()
        createPlaybackChannel()
        val notificationProvider = DefaultMediaNotificationProvider.Builder(this)
            .setChannelId(MEDIA_NOTIFICATION_CHANNEL_ID)
            .setChannelName(R.string.media_playback_channel)
            .build()
            .apply { setSmallIcon(R.drawable.ic_notification) }
        setMediaNotificationProvider(notificationProvider)
        setShowNotificationForIdlePlayer(SHOW_NOTIFICATION_FOR_IDLE_PLAYER_NEVER)

        val pauseOnDisconnect = runBlocking(Dispatchers.IO) {
            preferences.current().pauseOnDisconnect
        }
        player = ExoPlayer.Builder(this)
            .setMediaSourceFactory(PreviewAwareMediaSourceFactory(this))
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                true,
            )
            .setHandleAudioBecomingNoisy(pauseOnDisconnect)
            .build()
        session = MediaSession.Builder(this, player)
            .setSessionActivity(PlaybackSessionActivity.pendingIntent(this))
            .setBitmapLoader(SquareArtworkBitmapLoader(this))
            .setMediaButtonPreferences(mediaButtonPreferences())
            .setCallback(
                object : MediaSession.Callback {
                    @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
                    override fun onPlayerCommandRequest(
                        session: MediaSession,
                        controller: MediaSession.ControllerInfo,
                        playerCommand: Int,
                    ): Int {
                        if (!isPreviousPlaybackCommand(playerCommand)) {
                            return SessionResult.RESULT_SUCCESS
                        }
                        handlePreviousCommand()
                        return SessionResult.RESULT_ERROR_NOT_SUPPORTED
                    }
                },
            )
            .build()
        player.addListener(
            object : Player.Listener {
                override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                    previousRestartPending = false
                    observeCurrentSongMetadata(mediaItem)
                    schedulePersistQueue(immediate = true)
                }

                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    if (!isPlaying) {
                        schedulePersistQueue(immediate = true)
                        return
                    }
                    val mediaItem = player.currentMediaItem ?: return
                    val songId = mediaItem.mediaId.toLongOrNull() ?: return
                    if (mediaItem.mediaId == lastRecordedMediaId) return
                    lastRecordedMediaId = mediaItem.mediaId
                    serviceScope.launch {
                        songDao.recordPlay(songId)
                        historyDao.insert(PlaybackHistoryEntity(songId = songId))
                    }
                }

                override fun onTimelineChanged(timeline: androidx.media3.common.Timeline, reason: Int) {
                    if (player.shuffleModeEnabled) installPriorityShuffleOrder()
                    schedulePersistQueue()
                }

                override fun onPositionDiscontinuity(
                    oldPosition: Player.PositionInfo,
                    newPosition: Player.PositionInfo,
                    reason: Int,
                ) {
                    schedulePersistQueue(immediate = true)
                }

                override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
                    if (shuffleModeEnabled) {
                        shuffleSeed = System.nanoTime()
                        installPriorityShuffleOrder()
                    }
                    schedulePersistQueue(immediate = true)
                }

                override fun onRepeatModeChanged(repeatMode: Int) {
                    schedulePersistQueue(immediate = true)
                }

                override fun onPlaybackStateChanged(playbackState: Int) {
                    if (
                        playbackState == Player.STATE_ENDED &&
                        player.repeatMode == Player.REPEAT_MODE_OFF &&
                        player.currentMediaItem?.mediaId?.toLongOrNull() != null
                    ) {
                        continueFromLibraryStart()
                    }
                }
            },
        )
        serviceScope.launch { restoreQueue() }
        serviceScope.launch {
            while (isActive) {
                delay(5_000)
                persistQueue()
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (isStopPlaybackAction(intent?.action)) {
            requestPlaybackStop()
            return START_NOT_STICKY
        }
        return super.onStartCommand(intent, flags, startId)
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    override fun onTaskRemoved(rootIntent: Intent?) {
        serviceScope.launch { taskRemovalHandler.handle() }
    }

    override fun onDestroy() {
        persistJob?.cancel()
        if (::player.isInitialized && !stopRequested) {
            runBlocking {
                runCatching { persistQueue() }
            }
        }
        session?.release()
        session = null
        if (::player.isInitialized) player.release()
        currentSongMetadataJob?.cancel()
        serviceScope.cancel()
        super.onDestroy()
    }

    private suspend fun restoreQueue() {
        if (stopRequested) return
        if (!preferences.current().restoreQueue) return
        val snapshot = preferences.playbackSnapshot() ?: return
        val byId = songDao.getByIds(snapshot.songIds).associateBy { it.id }
        val context = snapshot.contextKind?.let { value ->
            runCatching { PlaybackContextKind.valueOf(value) }.getOrNull()
        }?.let { kind ->
            PlaybackContext(
                kind = kind,
                key = snapshot.contextKey.orEmpty(),
                label = snapshot.contextLabel.orEmpty(),
                songs = emptyList(),
                anchorSongId = snapshot.contextAnchorId,
            )
        }
        val restored = snapshot.songIds.mapIndexedNotNull { originalIndex, id ->
            val song = byId[id]?.takeIf { it.isAvailable }?.toModel() ?: return@mapIndexedNotNull null
            val origin = snapshot.queueOrigins.getOrNull(originalIndex)?.let { value ->
                runCatching { PlaybackQueueOrigin.valueOf(value) }.getOrNull()
            } ?: when {
                originalIndex == snapshot.currentIndex -> PlaybackQueueOrigin.CURRENT
                originalIndex > snapshot.currentIndex -> PlaybackQueueOrigin.MANUAL
                else -> PlaybackQueueOrigin.CONTEXT
            }
            originalIndex to song.toMediaItem(
                origin,
                context?.copy(
                    anchorSongId = if (origin == PlaybackQueueOrigin.CONTEXT) {
                        song.id
                    } else {
                        context.anchorSongId
                    },
                ),
            )
        }
        if (restored.isEmpty() || stopRequested) return
        val restoredIndex = restored.indexOfFirst { it.first == snapshot.currentIndex }
            .takeIf { it >= 0 }
            ?: restored.indexOfFirst { it.first > snapshot.currentIndex }.takeIf { it >= 0 }
            ?: restored.lastIndex
        player.setMediaItems(restored.map(Pair<Int, MediaItem>::second), restoredIndex, snapshot.positionMs)
        if (snapshot.shuffle) installPriorityShuffleOrder(force = true)
        player.shuffleModeEnabled = snapshot.shuffle
        player.repeatMode = snapshot.repeatMode
        player.prepare()
    }

    private fun schedulePersistQueue(immediate: Boolean = false) {
        persistJob?.cancel()
        persistJob = serviceScope.launch {
            if (!immediate) delay(PERSIST_DEBOUNCE_MILLIS)
            persistQueue()
        }
    }

    private suspend fun persistQueue() {
        persistMutex.withLock {
            if (!::player.isInitialized || player.mediaItemCount == 0) return@withLock
            val ids = buildList {
                repeat(player.mediaItemCount) { index ->
                    player.getMediaItemAt(index).mediaId.toLongOrNull()?.let(::add)
                }
            }
            if (ids.isEmpty()) return@withLock
            val items = buildList {
                repeat(player.mediaItemCount) { index -> add(player.getMediaItemAt(index)) }
            }
            val currentItem = player.currentMediaItem
            preferences.savePlaybackSnapshot(
                PlaybackSnapshot(
                    songIds = ids,
                    currentIndex = player.currentMediaItemIndex.coerceAtLeast(0),
                    positionMs = player.currentPosition.coerceAtLeast(0),
                    shuffle = player.shuffleModeEnabled,
                    repeatMode = player.repeatMode,
                    queueOrigins = items.map { it.playbackQueueOrigin().name },
                    contextKind = currentItem?.playbackContextKind()?.name,
                    contextKey = currentItem?.playbackContextKey(),
                    contextLabel = currentItem?.playbackContextLabel(),
                    contextAnchorId = currentItem?.playbackContextAnchorId(),
                ),
            )
        }
    }

    private fun requestPlaybackStop() {
        if (stopRequested) return
        stopRequested = true
        serviceScope.launch { shutdownCoordinator.shutdown() }
    }

    private fun handlePreviousCommand() {
        when (
            previousPlaybackAction(
                positionMs = player.currentPosition,
                canSeekPrevious = player.hasPreviousMediaItem(),
                hasRestartedCurrentItem = previousRestartPending,
            )
        ) {
            PreviousPlaybackAction.RESTART -> {
                previousRestartPending = true
                player.seekTo(0)
            }
            PreviousPlaybackAction.PREVIOUS -> {
                player.seekToPreviousMediaItem()
                previousRestartPending = false
            }
        }
    }

    private fun observeCurrentSongMetadata(mediaItem: MediaItem?) {
        currentSongMetadataJob?.cancel()
        val songId = mediaItem?.mediaId?.toLongOrNull() ?: return
        currentSongMetadataJob = serviceScope.launch {
            songDao.observeById(songId)
                .filterNotNull()
                .collectLatest { entity ->
                    val index = player.currentMediaItemIndex
                    if (index !in 0 until player.mediaItemCount) return@collectLatest
                    val current = player.getMediaItemAt(index)
                    if (current.mediaId != songId.toString()) return@collectLatest
                    val context = current.playbackContextKind()?.let { kind ->
                        PlaybackContext(
                            kind = kind,
                            key = current.playbackContextKey().orEmpty(),
                            label = current.playbackContextLabel().orEmpty(),
                            songs = emptyList(),
                            anchorSongId = current.playbackContextAnchorId(),
                        )
                    }
                    val updated = entity.toModel().toMediaItem(
                        origin = current.playbackQueueOrigin(),
                        context = context,
                    )
                    if (updated.mediaMetadata != current.mediaMetadata) {
                        player.replaceMediaItem(index, updated)
                    }
                }
        }
    }

    private fun continueFromLibraryStart() {
        if (wrappingLibrary || stopRequested) return
        wrappingLibrary = true
        serviceScope.launch {
            try {
                val settings = preferences.current()
                val preferredOrder = preferences.playbackLibraryOrder()
                val songs = withContext(Dispatchers.IO) {
                    if (preferredOrder.isNotEmpty()) {
                        val byId = songDao.getByIds(preferredOrder).associateBy { it.id }
                        preferredOrder.mapNotNull(byId::get)
                            .filter { it.isAvailable }
                            .map { it.toModel() }
                    } else {
                        songDao.getAvailable().map { it.toModel() }
                            .sortedForPlayback(settings.librarySort, settings.libraryAscending)
                    }
                }
                if (songs.isEmpty() || stopRequested || player.playbackState != Player.STATE_ENDED) return@launch
                val context = PlaybackContext(
                    kind = PlaybackContextKind.LIBRARY,
                    key = "library",
                    label = getString(R.string.library),
                    songs = songs,
                    anchorSongId = songs.first().id,
                )
                player.setMediaItems(
                    songs.mapIndexed { index, song ->
                        song.toMediaItem(
                            origin = if (index == 0) {
                                PlaybackQueueOrigin.CURRENT
                            } else {
                                PlaybackQueueOrigin.CONTEXT
                            },
                            context = context.copy(anchorSongId = song.id),
                        )
                    },
                    0,
                    0L,
                )
                player.prepare()
                player.play()
            } finally {
                wrappingLibrary = false
            }
        }
    }

    private fun installPriorityShuffleOrder(force: Boolean = false) {
        if (
            installingShuffleOrder ||
            player.mediaItemCount == 0 ||
            (!force && !player.shuffleModeEnabled)
        ) return
        val currentIndex = player.currentMediaItemIndex
        if (currentIndex !in 0 until player.mediaItemCount) return
        val origins = buildList {
            repeat(player.mediaItemCount) { index ->
                add(player.getMediaItemAt(index).playbackQueueOrigin())
            }
        }
        installingShuffleOrder = true
        try {
            val order = priorityShuffleOrder(currentIndex, origins, shuffleSeed)
            player.setShuffleOrder(ShuffleOrder.DefaultShuffleOrder(order, shuffleSeed))
        } finally {
            installingShuffleOrder = false
        }
    }

    private fun createPlaybackChannel() {
        val channel = NotificationChannel(
            MEDIA_NOTIFICATION_CHANNEL_ID,
            getString(R.string.media_playback_channel),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = getString(R.string.media_playback_channel_description)
            setShowBadge(false)
            lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun mediaButtonPreferences(): List<CommandButton> = listOf(
        CommandButton.Builder(CommandButton.ICON_PREVIOUS)
            .setPlayerCommand(Player.COMMAND_SEEK_TO_PREVIOUS)
            .setDisplayName(getString(R.string.previous))
            .setSlots(CommandButton.SLOT_BACK)
            .build(),
        CommandButton.Builder(CommandButton.ICON_PLAY)
            .setPlayerCommand(Player.COMMAND_PLAY_PAUSE)
            .setDisplayName(getString(R.string.play_pause))
            .setSlots(CommandButton.SLOT_CENTRAL)
            .build(),
        CommandButton.Builder(CommandButton.ICON_NEXT)
            .setPlayerCommand(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)
            .setDisplayName(getString(R.string.next))
            .setSlots(CommandButton.SLOT_FORWARD)
            .build(),
    )

    private companion object {
        const val MEDIA_NOTIFICATION_CHANNEL_ID = "polentita_media_playback"
        const val PERSIST_DEBOUNCE_MILLIS = 250L
    }
}

internal fun isPreviousPlaybackCommand(playerCommand: Int): Boolean =
    playerCommand == Player.COMMAND_SEEK_TO_PREVIOUS ||
        playerCommand == Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM
