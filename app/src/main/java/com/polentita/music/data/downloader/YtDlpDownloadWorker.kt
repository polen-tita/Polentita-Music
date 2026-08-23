package com.polentita.music.data.downloader

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.polentita.music.R
import com.polentita.music.core.common.AudioFormats
import com.polentita.music.core.common.FileNameSanitizer
import com.polentita.music.core.common.RemoteUrlValidator
import com.polentita.music.core.common.sha256
import com.polentita.music.core.network.NetworkAccessBlockedException
import com.polentita.music.core.network.NetworkAccessPolicy
import com.polentita.music.core.database.DownloadDao
import com.polentita.music.core.database.DownloadStatus
import com.polentita.music.core.storage.LibraryStorage
import com.polentita.music.data.extractor.YtDlpExtractor
import com.polentita.music.data.extractor.YtDlpProgressCallback
import com.polentita.music.domain.repository.MusicRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.withContext

@HiltWorker
class YtDlpDownloadWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val downloadDao: DownloadDao,
    private val extractor: YtDlpExtractor,
    private val storage: LibraryStorage,
    private val musicRepository: MusicRepository,
    private val coverResolver: DownloadedCoverResolver,
    private val audioPreparer: DownloadedAudioPreparer,
    private val networkAccessPolicy: NetworkAccessPolicy,
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val id = inputData.getString(DirectDownloadWorker.KEY_ID) ?: return@withContext Result.failure()
        val preferredAlbumId = inputData.getLong(DirectDownloadWorker.KEY_ALBUM_ID, -1L)
            .takeIf { it > 0 }
        val download = downloadDao.getById(id) ?: return@withContext Result.failure()
        val outputDirectory = File(applicationContext.cacheDir, "yt-dlp/$id")
        var policyBlocked: NetworkAccessBlockedException? = null
        try {
            ensureDownloadAllowed()
            setForeground(
                DownloadNotificationFactory.progress(
                    applicationContext,
                    id,
                    download.title,
                    download.progress,
                    R.string.extracting_audio,
                ),
            )
            downloadDao.updateProgress(
                id = id,
                status = DownloadStatus.DOWNLOADING.name,
                progress = download.progress,
                bytes = download.bytesDownloaded,
                total = download.totalBytes,
                speed = 0,
            )
            val result = coroutineScope {
                val progressReporter = DownloadProgressReporter(this) { update ->
                    updateProgress(
                        id = id,
                        title = download.title,
                        bytes = update.bytesDownloaded,
                        total = update.totalBytes,
                        speed = update.speedBytesPerSecond,
                    )
                }
                progressReporter.start()
                try {
                    extractor.download(
                        url = download.sourceUrl,
                        outputDirectory = outputDirectory,
                        callback = object : YtDlpProgressCallback {
                            override fun isCancelled(): Boolean {
                                if (isStopped) return true
                                val reason = networkAccessPolicy.state.value.downloadBlockReason
                                if (reason != null) {
                                    policyBlocked = NetworkAccessBlockedException(reason)
                                    return true
                                }
                                return false
                            }

                            override fun onProgress(
                                status: String,
                                downloadedBytes: Long,
                                totalBytes: Long,
                                speedBytesPerSecond: Long,
                            ) {
                                progressReporter.offer(
                                    DownloadProgressUpdate(
                                        bytesDownloaded = downloadedBytes,
                                        totalBytes = totalBytes,
                                        speedBytesPerSecond = speedBytesPerSecond,
                                    ),
                                )
                            }
                        },
                    )
                } finally {
                    progressReporter.stopAndFlush()
                }
            }
            val audioFile = audioPreparer.prepare(result.file, outputDirectory) { isStopped }
            downloadDao.updateProgress(
                id = id,
                status = DownloadStatus.VALIDATING.name,
                progress = 99,
                bytes = download.bytesDownloaded,
                total = download.totalBytes,
                speed = 0,
            )
            val checksum = audioFile.sha256()
            musicRepository.findSongByChecksum(checksum)
                ?.takeIf { song -> song.isAvailable }
                ?.let { existingSong ->
                    val finalBytes = audioFile.length()
                    downloadDao.updateProgress(
                        id = id,
                        status = DownloadStatus.COMPLETED.name,
                        progress = 100,
                        bytes = finalBytes,
                        total = finalBytes,
                        speed = 0,
                        destinationUri = existingSong.contentUri,
                        completedAt = System.currentTimeMillis(),
                    )
                    downloadDao.setSafeSourceUrl(id, RemoteUrlValidator.redacted(download.sourceUrl))
                    downloadDao.clearThumbnailUrl(id)
                    outputDirectory.deleteRecursively()
                    return@withContext Result.success()
                }
            musicRepository.findAvailableSongByMetadata(
                title = download.title.ifBlank { result.media.title },
                artist = download.artist.ifBlank { result.media.artist },
                isrc = download.isrc,
            )?.takeIf { song -> song.isAvailable }
                ?.let { existingSong ->
                    val finalBytes = audioFile.length()
                    downloadDao.updateProgress(
                        id = id,
                        status = DownloadStatus.COMPLETED.name,
                        progress = 100,
                        bytes = finalBytes,
                        total = finalBytes,
                        speed = 0,
                        destinationUri = existingSong.contentUri,
                        completedAt = System.currentTimeMillis(),
                    )
                    downloadDao.setSafeSourceUrl(id, RemoteUrlValidator.redacted(download.sourceUrl))
                    outputDirectory.deleteRecursively()
                    return@withContext Result.success()
                }
            val extension = audioFile.extension.lowercase()
            require(extension in AudioFormats.extensions) {
                "yt-dlp produjo una extensión de audio no compatible"
            }
            val mime = AudioFormats.mimeFor(audioFile.name)
            require(AudioFormats.isSupported(mime, audioFile.name)) {
                "yt-dlp produjo un formato de audio no compatible"
            }
            val sanitizedTitle = FileNameSanitizer.sanitize(
                download.title.ifBlank { result.media.title },
            )
            val safeTitle = sanitizedTitle.substringBeforeLast('.', sanitizedTitle)
            ensureDownloadAllowed()
            downloadDao.updateProgress(
                id = id,
                status = DownloadStatus.SAVING.name,
                progress = 99,
                bytes = audioFile.length(),
                total = audioFile.length(),
                speed = 0,
            )
            val coverUri = withTimeoutOrNull(COVER_RESOLUTION_TIMEOUT_MILLIS) {
                coverResolver.resolveOrNull(download.thumbnailUrl, checksum)
            }
            val stored = storage.writeDownloadedFile(
                sourceFile = audioFile,
                displayName = "$safeTitle.$extension",
                mimeType = mime,
            )
            musicRepository.registerDownloadedFile(
                uri = stored.uri,
                title = download.title.ifBlank { result.media.title },
                artist = download.artist.ifBlank { result.media.artist },
                album = download.album,
                sourceUrl = download.sourceUrl,
                downloadedCoverUri = coverUri,
                preferredAlbumId = preferredAlbumId,
                useMetadataAlbumWhenBlank = false,
                isrc = download.isrc,
            )
            val finalBytes = audioFile.length()
            downloadDao.updateProgress(
                id = id,
                status = DownloadStatus.COMPLETED.name,
                progress = 100,
                bytes = finalBytes,
                total = finalBytes,
                speed = 0,
                destinationUri = stored.uri.toString(),
                completedAt = System.currentTimeMillis(),
            )
            downloadDao.setSafeSourceUrl(id, RemoteUrlValidator.redacted(download.sourceUrl))
            downloadDao.clearThumbnailUrl(id)
            outputDirectory.deleteRecursively()
            Result.success()
        } catch (error: Throwable) {
            val blocked = policyBlocked ?: (error as? NetworkAccessBlockedException)
            val cancelled = isStopped || error.stackTraceToString().contains(CANCEL_MARKER)
            val decision = DownloadFailurePolicy.decide(
                runAttemptCount = runAttemptCount,
                blocked = blocked != null,
                cancelled = cancelled,
                errorMessage = blocked?.message ?: error.message,
            )
            val partialBytes = outputDirectory.walkTopDown()
                .filter(File::isFile)
                .sumOf(File::length)
            downloadDao.updateProgress(
                id = id,
                status = decision.status.name,
                progress = download.progress,
                bytes = partialBytes,
                total = download.totalBytes,
                speed = 0,
                error = decision.errorMessage,
            )
            if (decision.shouldRetry) Result.retry() else Result.failure(
                workDataOf(
                    DirectDownloadWorker.KEY_ERROR to requireNotNull(decision.errorMessage),
                ),
            )
        }
    }

    private suspend fun ensureDownloadAllowed() {
        networkAccessPolicy.current().downloadBlockReason?.let {
            throw NetworkAccessBlockedException(it)
        }
    }

    private suspend fun updateProgress(
        id: String,
        title: String,
        bytes: Long,
        total: Long,
        speed: Long,
    ) {
        val progress = if (total > 0) ((bytes * 100 / total).coerceIn(0, 99)).toInt() else 0
        downloadDao.updateProgress(
            id = id,
            status = DownloadStatus.DOWNLOADING.name,
            progress = progress,
            bytes = bytes,
            total = total,
            speed = speed,
        )
        setProgress(workDataOf("progress" to progress, "bytes" to bytes, "total" to total))
        setForeground(
            DownloadNotificationFactory.progress(
                applicationContext,
                id,
                title,
                progress,
                R.string.extracting_audio,
            ),
        )
    }

    companion object {
        private const val CANCEL_MARKER = "__POLENTITA_CANCELLED__"
        private const val COVER_RESOLUTION_TIMEOUT_MILLIS = 15_000L
    }
}
