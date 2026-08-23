package com.polentita.music.data.downloader

import com.polentita.music.core.database.DownloadEntity
import com.polentita.music.core.database.DownloadStatus
import org.junit.Assert.assertEquals
import org.junit.Test

class DownloadStateTransitionsTest {
    @Test
    fun `cancellation preserves partial bytes and marks terminal state`() {
        val original = DownloadEntity(
            id = "download-1",
            sourceUrl = "https://audio.example/test.wav",
            title = "Prueba",
            status = DownloadStatus.DOWNLOADING.name,
            bytesDownloaded = 4_096,
            totalBytes = 8_192,
            speedBytesPerSecond = 2_048,
        )

        val cancelled = DownloadStateTransitions.cancelled(original)

        assertEquals(DownloadStatus.CANCELLED.name, cancelled.status)
        assertEquals(4_096, cancelled.bytesDownloaded)
        assertEquals(0, cancelled.speedBytesPerSecond)
        assertEquals("La descarga fue cancelada", cancelled.errorMessage)
    }
}
