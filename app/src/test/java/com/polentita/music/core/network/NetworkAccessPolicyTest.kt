package com.polentita.music.core.network

import com.polentita.music.core.storage.AppPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NetworkAccessPolicyTest {
    @Test
    fun `wifi no medida permite todas las operaciones`() {
        val state = evaluateNetworkAccess(
            AppPreferences(wifiOnlyDownloads = true),
            ConnectivityState(connected = true, wifi = true, metered = false),
        )

        assertTrue(state.remoteSearchAllowed)
        assertTrue(state.previewAllowed)
        assertTrue(state.downloadAllowed)
        assertNull(state.downloadBlockReason)
    }

    @Test
    fun `datos moviles mantienen busqueda y adelanto pero bloquean descarga wifi only`() {
        val state = evaluateNetworkAccess(
            AppPreferences(wifiOnlyDownloads = true),
            ConnectivityState(connected = true, wifi = false, metered = true),
        )

        assertTrue(state.remoteSearchAllowed)
        assertTrue(state.previewAllowed)
        assertFalse(state.downloadAllowed)
        assertEquals(NetworkBlockReason.WIFI_REQUIRED, state.downloadBlockReason)
    }

    @Test
    fun `modo offline prevalece incluso con wifi`() {
        val state = evaluateNetworkAccess(
            AppPreferences(offlineMode = true, wifiOnlyDownloads = false),
            ConnectivityState(connected = true, wifi = true, metered = false),
        )

        assertFalse(state.remoteSearchAllowed)
        assertFalse(state.previewAllowed)
        assertFalse(state.downloadAllowed)
        assertEquals(NetworkBlockReason.OFFLINE_MODE, state.remoteBlockReason)
        assertEquals(NetworkBlockReason.OFFLINE_MODE, state.downloadBlockReason)
    }

    @Test
    fun `falta fisica de internet se distingue del modo offline`() {
        val state = evaluateNetworkAccess(
            AppPreferences(offlineMode = false),
            ConnectivityState(),
        )

        assertFalse(state.offlineMode)
        assertEquals(NetworkBlockReason.NO_CONNECTION, state.remoteBlockReason)
    }
}
