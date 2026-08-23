package com.polentita.music.data.downloader

import com.polentita.music.core.database.DownloadStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DownloadFailurePolicyTest {
    @Test
    fun `automatic failures remain pending while WorkManager can retry`() {
        val decision = DownloadFailurePolicy.decide(
            runAttemptCount = 0,
            blocked = false,
            cancelled = false,
            errorMessage = "startForegroundService() not allowed",
        )

        assertEquals(DownloadStatus.PENDING, decision.status)
        assertTrue(decision.shouldRetry)
        assertNull(decision.errorMessage)
    }

    @Test
    fun `terminal foreground error is converted to a useful message`() {
        val decision = DownloadFailurePolicy.decide(
            runAttemptCount = 2,
            blocked = false,
            cancelled = false,
            errorMessage = "startForegroundService() not allowed due to mAllowStartForeground false",
        )

        assertEquals(DownloadStatus.FAILED, decision.status)
        assertFalse(decision.shouldRetry)
        assertEquals(
            "Android no pudo iniciar el servicio de descarga. Abre la aplicación y reintenta.",
            decision.errorMessage,
        )
    }
}
