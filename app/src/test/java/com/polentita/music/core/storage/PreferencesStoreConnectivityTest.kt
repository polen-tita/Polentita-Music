package com.polentita.music.core.storage

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class PreferencesStoreConnectivityTest {
    @Test
    fun `persiste modo offline y wifi only de forma independiente`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val store = PreferencesStore(context)
        store.setOfflineMode(false)
        store.setWifiOnlyDownloads(false)

        store.setOfflineMode(true)
        store.setWifiOnlyDownloads(true)
        val enabled = store.current()

        assertTrue(enabled.offlineMode)
        assertTrue(enabled.wifiOnlyDownloads)

        store.setOfflineMode(false)
        val restored = store.current()
        assertFalse(restored.offlineMode)
        assertTrue(restored.wifiOnlyDownloads)

        store.setWifiOnlyDownloads(false)
    }
}
