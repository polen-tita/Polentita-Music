package com.polentita.music.data.downloader

import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

internal data class DownloadProgressUpdate(
    val bytesDownloaded: Long,
    val totalBytes: Long,
    val speedBytesPerSecond: Long,
)

/**
 * Keeps progress callbacks cheap while the worker performs the actual persistence and
 * notification update at a bounded cadence. This is especially important for yt-dlp, whose
 * callback is invoked while the shared Python bridge is busy.
 */
internal class DownloadProgressReporter(
    private val scope: CoroutineScope,
    private val intervalMillis: Long = DEFAULT_INTERVAL_MILLIS,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val publish: suspend (DownloadProgressUpdate) -> Unit,
) {
    private val latest = AtomicReference<DownloadProgressUpdate?>(null)
    private var job: Job? = null

    fun start() {
        check(job == null) { "El actualizador de progreso ya fue iniciado" }
        job = scope.launch(dispatcher) {
            while (isActive) {
                delay(intervalMillis)
                val update = latest.getAndSet(null)
                if (update != null) publish(update)
            }
        }
    }

    fun offer(update: DownloadProgressUpdate) {
        latest.set(update)
    }

    suspend fun stopAndFlush() {
        job?.cancelAndJoin()
        job = null
        val update = latest.getAndSet(null)
        if (update != null) publish(update)
    }

    companion object {
        const val DEFAULT_INTERVAL_MILLIS = 750L
    }
}
