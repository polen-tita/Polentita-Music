package com.polentita.music.data.downloader

import android.content.Context
import android.media.MediaMetadataRetriever
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.polentita.music.R
import com.polentita.music.core.common.AudioFormats
import com.polentita.music.core.common.FileNameSanitizer
import com.polentita.music.core.common.RemoteUrlValidator
import com.polentita.music.core.common.sha256
import com.polentita.music.core.database.DownloadDao
import com.polentita.music.core.database.DownloadStatus
import com.polentita.music.core.network.DirectDownloadInspector
import com.polentita.music.core.network.NetworkAccessBlockedException
import com.polentita.music.core.network.NetworkAccessPolicy
import com.polentita.music.core.storage.LibraryStorage
import com.polentita.music.domain.repository.MusicRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.io.File
import java.io.FileOutputStream
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

@HiltWorker
class DirectDownloadWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val downloadDao: DownloadDao,
    private val inspector: DirectDownloadInspector,
    private val storage: LibraryStorage,
    private val musicRepository: MusicRepository,
    private val networkAccessPolicy: NetworkAccessPolicy,
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val id = inputData.getString(KEY_ID) ?: return@withContext Result.failure()
        val preferredAlbumId = inputData.getLong(KEY_ALBUM_ID, -1L).takeIf { it > 0 }
        val download = downloadDao.getById(id) ?: return@withContext Result.failure()
        val temp = File(applicationContext.cacheDir, "polentita-download-$id.part")
        try {
            ensureDownloadAllowed()
            setForeground(
                DownloadNotificationFactory.progress(
                    applicationContext,
                    id,
                    download.title,
                    download.progress,
                    R.string.downloading,
                ),
            )
            coroutineScope {
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
                    val existing = temp.takeIf(File::exists)?.length() ?: 0L
                    val response = inspector.executeGet(download.sourceUrl, existing)
                    response.use {
                        if (!it.isSuccessful && it.code != 206) {
                            throw IllegalArgumentException("El servidor respondió con ${it.code}")
                        }
                        val mime = it.header("Content-Type")?.substringBefore(';')?.lowercase().orEmpty()
                        if (!AudioFormats.isSupported(mime, download.title)) {
                            throw IllegalArgumentException("El servidor ya no ofrece un audio compatible")
                        }
                        val resume = DownloadResumePolicy.decide(existing, it.code)
                        val append = resume.append
                        val startingBytes = resume.startingBytes
                        val contentLength = it.body?.contentLength() ?: -1L
                        val total = contentRangeTotal(it.header("Content-Range"))
                            ?: contentLength.takeIf { length -> length >= 0 }?.plus(startingBytes)
                            ?: -1L
                        var downloaded = startingBytes
                        var lastBytes = downloaded
                        var lastUpdate = System.currentTimeMillis()
                        FileOutputStream(temp, append).use { output ->
                            val body = requireNotNull(it.body) { "La respuesta no contiene datos" }
                            body.byteStream().use { input ->
                                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                                while (true) {
                                    coroutineContext.ensureActive()
                                    networkAccessPolicy.state.value.downloadBlockReason?.let {
                                        throw NetworkAccessBlockedException(it)
                                    }
                                    val count = input.read(buffer)
                                    if (count < 0) break
                                    output.write(buffer, 0, count)
                                    downloaded += count
                                    val now = System.currentTimeMillis()
                                    if (now - lastUpdate >= 500) {
                                        val speed = (downloaded - lastBytes) * 1000L /
                                            (now - lastUpdate).coerceAtLeast(1)
                                        progressReporter.offer(
                                            DownloadProgressUpdate(downloaded, total, speed),
                                        )
                                        lastBytes = downloaded
                                        lastUpdate = now
                                    }
                                }
                                output.fd.sync()
                            }
                        }
                        validatePlayable(temp)
                        musicRepository.findSongByChecksum(temp.sha256())
                            ?.takeIf { song -> song.isAvailable }
                            ?.let { existingSong ->
                                downloadDao.updateProgress(
                                    id = id,
                                    status = DownloadStatus.COMPLETED.name,
                                    progress = 100,
                                    bytes = temp.length(),
                                    total = temp.length(),
                                    speed = 0,
                                    destinationUri = existingSong.contentUri,
                                    completedAt = System.currentTimeMillis(),
                                )
                                downloadDao.setSafeSourceUrl(id, RemoteUrlValidator.redacted(download.sourceUrl))
                                temp.delete()
                                return@use Result.success()
                            }
                        val baseName = FileNameSanitizer.sanitize(download.title)
                        val name = if (baseName.substringAfterLast('.', "").lowercase() in AudioFormats.extensions) {
                            baseName
                        } else {
                            "$baseName.${extensionFor(mime)}"
                        }
                        val stored = storage.writeDownloadedFile(temp, name, mime)
                        musicRepository.registerDownloadedFile(
                            uri = stored.uri,
                            title = download.title.substringBeforeLast('.'),
                            artist = download.artist,
                            album = download.album,
                            sourceUrl = download.sourceUrl,
                            preferredAlbumId = preferredAlbumId,
                            isrc = download.isrc,
                        )
                        downloadDao.updateProgress(
                            id = id,
                            status = DownloadStatus.COMPLETED.name,
                            progress = 100,
                            bytes = downloaded,
                            total = if (total >= 0) total else downloaded,
                            speed = 0,
                            destinationUri = stored.uri.toString(),
                            completedAt = System.currentTimeMillis(),
                        )
                        downloadDao.setSafeSourceUrl(id, RemoteUrlValidator.redacted(download.sourceUrl))
                        temp.delete()
                        Result.success()
                    }
                } finally {
                    progressReporter.stopAndFlush()
                }
            }
        } catch (blocked: NetworkAccessBlockedException) {
            downloadDao.updateProgress(
                id = id,
                status = DownloadStatus.PAUSED.name,
                progress = download.progress,
                bytes = temp.takeIf(File::exists)?.length() ?: 0,
                total = download.totalBytes,
                speed = 0,
                error = blocked.message,
            )
            Result.retry()
        } catch (cancelled: CancellationException) {
            downloadDao.updateProgress(
                id = id,
                status = DownloadStatus.CANCELLED.name,
                progress = download.progress,
                bytes = temp.takeIf(File::exists)?.length() ?: 0,
                total = download.totalBytes,
                speed = 0,
                error = "La descarga fue cancelada",
            )
            throw cancelled
        } catch (error: Throwable) {
            val decision = DownloadFailurePolicy.decide(
                runAttemptCount = runAttemptCount,
                blocked = false,
                cancelled = false,
                errorMessage = error.message,
            )
            downloadDao.updateProgress(
                id = id,
                status = decision.status.name,
                progress = download.progress,
                bytes = temp.takeIf(File::exists)?.length() ?: 0,
                total = download.totalBytes,
                speed = 0,
                error = decision.errorMessage,
            )
            if (decision.shouldRetry) Result.retry() else Result.failure(
                workDataOf(KEY_ERROR to requireNotNull(decision.errorMessage)),
            )
        }
    }

    private suspend fun ensureDownloadAllowed() {
        networkAccessPolicy.current().downloadBlockReason?.let {
            throw NetworkAccessBlockedException(it)
        }
    }

    private suspend fun updateProgress(id: String, title: String, bytes: Long, total: Long, speed: Long) {
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
                R.string.downloading,
            ),
        )
    }

    private fun validatePlayable(file: File) {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(file.absolutePath)
            val hasAudio =
                retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_AUDIO) == "yes" ||
                    retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                        ?.toLongOrNull() != null
            val hasVideo =
                retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_VIDEO) == "yes"
            require(
                hasAudio && !hasVideo,
            ) { "El archivo descargado no contiene audio reproducible" }
        } finally {
            retriever.release()
        }
    }

    private fun contentRangeTotal(value: String?): Long? =
        value?.substringAfterLast('/', "")?.toLongOrNull()

    private fun extensionFor(mime: String) = when (mime) {
        "audio/mp4" -> "m4a"
        "audio/aac" -> "aac"
        "audio/ogg" -> "ogg"
        "audio/opus" -> "opus"
        "audio/flac", "audio/x-flac" -> "flac"
        "audio/wav", "audio/x-wav", "audio/vnd.wave" -> "wav"
        "audio/webm" -> "webm"
        else -> "mp3"
    }

    companion object {
        const val KEY_ID = "download_id"
        const val KEY_ALBUM_ID = "download_album_id"
        const val KEY_ERROR = "download_error"
    }
}
