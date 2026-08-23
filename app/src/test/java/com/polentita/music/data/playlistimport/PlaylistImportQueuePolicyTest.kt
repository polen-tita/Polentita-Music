package com.polentita.music.data.playlistimport

import com.polentita.music.core.database.PlaylistImportItemEntity
import com.polentita.music.core.network.ConnectivityState
import com.polentita.music.core.network.evaluateNetworkAccess
import com.polentita.music.core.storage.AppPreferences
import com.polentita.music.domain.playlistimport.PlaylistImportItemState
import com.polentita.music.domain.playlistimport.PlaylistImportState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaylistImportQueuePolicyTest {
    @Test
    fun `offline mode and wifi restriction prevent queue processing`() {
        val offline = evaluateNetworkAccess(
            AppPreferences(offlineMode = true),
            ConnectivityState(connected = true, wifi = true, metered = false),
        )
        val mobileWithWifiOnly = evaluateNetworkAccess(
            AppPreferences(wifiOnlyDownloads = true),
            ConnectivityState(connected = true, wifi = false, metered = true),
        )
        val unmeteredWifi = evaluateNetworkAccess(
            AppPreferences(wifiOnlyDownloads = true),
            ConnectivityState(connected = true, wifi = true, metered = false),
        )

        assertFalse(PlaylistImportQueuePolicy.canProcess(offline))
        assertFalse(PlaylistImportQueuePolicy.canProcess(mobileWithWifiOnly))
        assertTrue(PlaylistImportQueuePolicy.canProcess(unmeteredWifi))
    }

    @Test
    fun `restart recovers searching and preparing without duplicating a download`() {
        val searching = item(0, PlaylistImportItemState.SEARCHING)
        val preparing = item(1, PlaylistImportItemState.PREPARING)
        val downloading = item(2, PlaylistImportItemState.DOWNLOADING).copy(downloadId = "existing-work")

        assertEquals(
            PlaylistImportItemState.MISSING.name,
            PlaylistImportQueuePolicy.recoverAfterRestart(searching, 10).state,
        )
        assertEquals(
            PlaylistImportItemState.PENDING.name,
            PlaylistImportQueuePolicy.recoverAfterRestart(preparing, 10).state,
        )
        assertEquals(
            downloading,
            PlaylistImportQueuePolicy.recoverAfterRestart(downloading, 10),
        )
    }

    @Test
    fun `queue selects one pending track in original order`() {
        val items = listOf(
            item(3, PlaylistImportItemState.PENDING),
            item(0, PlaylistImportItemState.COMPLETED),
            item(2, PlaylistImportItemState.PENDING),
            item(1, PlaylistImportItemState.PENDING).copy(selected = false),
        )

        assertEquals(2, PlaylistImportQueuePolicy.nextRunnable(items)?.originalPosition)
    }

    @Test
    fun `cancellation preserves completed items and omits only remaining work`() {
        val completed = item(0, PlaylistImportItemState.COMPLETED).copy(localSongId = 4)
        val pending = item(1, PlaylistImportItemState.PENDING)

        val cancelled = PlaylistImportQueuePolicy.cancelRemaining(listOf(completed, pending), 50)

        assertEquals(completed, cancelled[0])
        assertEquals(PlaylistImportItemState.OMITTED.name, cancelled[1].state)
        assertFalse(cancelled[1].selected)
        assertEquals(PlaylistImportState.PARTIAL, PlaylistImportQueuePolicy.completionState(cancelled))
    }

    @Test
    fun `next candidate clears failed download identity before requeueing`() {
        val item = item(1, PlaylistImportItemState.ERROR).copy(
            downloadId = "failed-download",
            errorMessage = "Temporal",
            attemptCount = 2,
        )

        val reset = PlaylistImportQueuePolicy.resetForNextCandidate(item, now = 50)

        assertEquals(PlaylistImportItemState.PENDING.name, reset.state)
        assertEquals(null, reset.downloadId)
        assertEquals(null, reset.errorMessage)
        assertEquals(0, reset.attemptCount)
        assertEquals(50L, reset.updatedAt)
    }

    @Test
    fun `temporary failures retry once before changing candidate`() {
        assertEquals(
            PlaylistImportRecoveryAction.RETRY_CURRENT_CANDIDATE,
            PlaylistImportRecoveryPolicy.decide(
                attemptCount = 1,
                hasNextCandidate = true,
                providerUnavailable = false,
                errorMessage = "La conexión se interrumpió",
            ),
        )
        assertEquals(
            PlaylistImportRecoveryAction.TRY_NEXT_CANDIDATE,
            PlaylistImportRecoveryPolicy.decide(
                attemptCount = 2,
                hasNextCandidate = true,
                providerUnavailable = false,
                errorMessage = "La conexión se interrumpió",
            ),
        )
    }

    @Test
    fun `permanent or configuration failures do not waste retry cycles`() {
        assertEquals(
            PlaylistImportRecoveryAction.TRY_NEXT_CANDIDATE,
            PlaylistImportRecoveryPolicy.decide(
                attemptCount = 1,
                hasNextCandidate = true,
                providerUnavailable = false,
                errorMessage = "Video not available",
            ),
        )
        assertEquals(
            PlaylistImportRecoveryAction.NEEDS_REVIEW,
            PlaylistImportRecoveryPolicy.decide(
                attemptCount = 0,
                hasNextCandidate = true,
                providerUnavailable = true,
                errorMessage = "Proveedor no configurado",
            ),
        )
    }

    @Test
    fun `completion is truthful only when every included item is available`() {
        assertEquals(
            PlaylistImportState.COMPLETED,
            PlaylistImportQueuePolicy.completionState(
                listOf(
                    item(0, PlaylistImportItemState.IN_LIBRARY),
                    item(1, PlaylistImportItemState.COMPLETED),
                ),
            ),
        )
        assertEquals(
            PlaylistImportState.PARTIAL,
            PlaylistImportQueuePolicy.completionState(
                listOf(item(0, PlaylistImportItemState.ERROR)),
            ),
        )
    }

    private fun item(position: Int, state: PlaylistImportItemState) = PlaylistImportItemEntity(
        id = "item-$position",
        importId = "import",
        sourceId = "source-$position",
        title = "Tema $position",
        artists = "Artista",
        originalPosition = position,
        state = state.name,
    )
}
