package com.polentita.music.data.downloader

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DownloadProgressReporterTest {
    @Test
    fun `conflates frequent callbacks and flushes the latest value`() = runTest {
        val published = mutableListOf<DownloadProgressUpdate>()
        val dispatcher = StandardTestDispatcher(testScheduler)
        val scope = CoroutineScope(dispatcher)
        val reporter = DownloadProgressReporter(
            scope = scope,
            intervalMillis = 100,
            dispatcher = dispatcher,
            publish = { published += it },
        )

        reporter.start()
        reporter.offer(DownloadProgressUpdate(10, 100, 10))
        reporter.offer(DownloadProgressUpdate(80, 100, 80))
        advanceTimeBy(99)
        runCurrent()
        assertTrue(published.isEmpty())

        advanceTimeBy(1)
        runCurrent()
        assertEquals(listOf(80L), published.map(DownloadProgressUpdate::bytesDownloaded))

        reporter.offer(DownloadProgressUpdate(100, 100, 100))
        reporter.stopAndFlush()
        assertEquals(listOf(80L, 100L), published.map(DownloadProgressUpdate::bytesDownloaded))
        scope.cancel()
    }
}
