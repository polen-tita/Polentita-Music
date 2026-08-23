package com.polentita.music.playback.service

import com.polentita.music.core.storage.AppPreferences
import com.polentita.music.core.storage.PlaybackSnapshot
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackShutdownCoordinatorTest {
    @Test
    fun `task removed requests shutdown when setting is enabled by default`() = runTest {
        var shutdownRequested = false
        val handler = PlaybackTaskRemovalHandler(
            stopEnabled = { AppPreferences().stopPlaybackOnTaskRemoved },
            requestStop = { shutdownRequested = true },
        )

        val handled = handler.handle()

        assertTrue(handled)
        assertTrue(shutdownRequested)
    }

    @Test
    fun `task removed preserves playback when setting is disabled`() = runTest {
        var shutdownRequested = false
        val handler = PlaybackTaskRemovalHandler(
            stopEnabled = { AppPreferences(stopPlaybackOnTaskRemoved = false).stopPlaybackOnTaskRemoved },
            requestStop = { shutdownRequested = true },
        )

        val handled = handler.handle()

        assertFalse(handled)
        assertFalse(shutdownRequested)
    }

    @Test
    fun `stop action is explicit and rejects unrelated service actions`() {
        assertTrue(isStopPlaybackAction(ACTION_STOP_PLAYBACK))
        assertFalse(isStopPlaybackAction(null))
        assertFalse(isStopPlaybackAction("androidx.media3.session.MediaSessionService"))
    }

    @Test
    fun `shutdown persists queue and position before stopping and clearing player`() = runTest {
        val events = mutableListOf<String>()
        var savedSnapshot: PlaybackSnapshot? = null
        val expectedSnapshot = PlaybackSnapshot(
            songIds = listOf(11, 22, 33),
            currentIndex = 1,
            positionMs = 42_500,
            shuffle = true,
            repeatMode = 0,
        )
        val coordinator = coordinator(
            persist = {
                savedSnapshot = expectedSnapshot
                events += "persist"
            },
            stop = {
                assertNotNull(savedSnapshot)
                events += "stop"
            },
            clear = {
                assertNotNull(savedSnapshot)
                events += "clear"
            },
            events = events,
        )

        coordinator.shutdown()

        assertEquals(expectedSnapshot, savedSnapshot)
        assertEquals(
            listOf("persist", "stop", "clear", "foreground", "session", "service"),
            events,
        )
    }

    @Test
    fun `playback shutdown leaves active download untouched`() = runTest {
        val activeDownload = true
        val events = mutableListOf<String>()
        val coordinator = coordinator(
            persist = { events += "persist" },
            stop = { events += "stop" },
            clear = { events += "clear" },
            events = events,
        )

        coordinator.shutdown()

        assertTrue(activeDownload)
        assertEquals(
            listOf("persist", "stop", "clear", "foreground", "session", "service"),
            events,
        )
    }

    private fun coordinator(
        persist: suspend () -> Unit,
        stop: () -> Unit,
        clear: () -> Unit,
        events: MutableList<String>,
    ) = PlaybackShutdownCoordinator(
        persistQueueAndPosition = persist,
        stopPlayback = stop,
        clearActiveQueue = clear,
        removeForegroundNotification = { events += "foreground" },
        releaseSession = { events += "session" },
        stopService = { events += "service" },
    )
}
