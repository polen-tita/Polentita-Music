package com.polentita.music.playback.service

internal const val ACTION_STOP_PLAYBACK = "com.polentita.music.action.STOP_PLAYBACK"

internal fun isStopPlaybackAction(action: String?): Boolean = action == ACTION_STOP_PLAYBACK

internal class PlaybackTaskRemovalHandler(
    private val stopEnabled: suspend () -> Boolean,
    private val requestStop: () -> Unit,
) {
    suspend fun handle(): Boolean {
        val shouldStop = runCatching { stopEnabled() }.getOrDefault(true)
        if (shouldStop) requestStop()
        return shouldStop
    }
}

internal class PlaybackShutdownCoordinator(
    private val persistQueueAndPosition: suspend () -> Unit,
    private val stopPlayback: () -> Unit,
    private val clearActiveQueue: () -> Unit,
    private val removeForegroundNotification: () -> Unit,
    private val releaseSession: () -> Unit,
    private val stopService: () -> Unit,
) {
    suspend fun shutdown() {
        runCatching { persistQueueAndPosition() }
        runCatching { stopPlayback() }
        runCatching { clearActiveQueue() }
        runCatching { removeForegroundNotification() }
        runCatching { releaseSession() }
        runCatching { stopService() }
    }
}
