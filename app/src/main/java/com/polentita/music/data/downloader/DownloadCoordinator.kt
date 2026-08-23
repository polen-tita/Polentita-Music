package com.polentita.music.data.downloader

import android.content.Context
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.BackoffPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.polentita.music.core.database.DownloadDao
import com.polentita.music.core.database.AlbumDao
import com.polentita.music.core.database.DownloadEntity
import com.polentita.music.core.database.DownloadProvider
import com.polentita.music.core.database.DownloadStatus
import com.polentita.music.core.network.RemoteAudioInfo
import com.polentita.music.core.network.NetworkAccessBlockedException
import com.polentita.music.core.network.NetworkAccessPolicy
import com.polentita.music.core.network.NetworkBlockReason
import com.polentita.music.data.extractor.YtDlpMediaInfo
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.UUID
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.launch

@Singleton
class DownloadCoordinator @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dao: DownloadDao,
    private val albumDao: AlbumDao,
    private val networkAccessPolicy: NetworkAccessPolicy,
) {
    private val workManager = WorkManager.getInstance(context)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val downloads = dao.observeAll()
    val downloadStatuses = dao.observeStatus().distinctUntilChanged()

    fun observe(ids: List<String>): Flow<List<DownloadEntity>> = dao.observeByIds(ids)

    init {
        scope.launch {
            networkAccessPolicy.state
                .distinctUntilChangedBy { it.downloadBlockReason }
                .collect { access ->
                    if (access.downloadBlockReason == NetworkBlockReason.OFFLINE_MODE) {
                        workManager.cancelAllWorkByTag(DOWNLOAD_TAG)
                    }
                }
        }
    }

    suspend fun enqueue(
        info: RemoteAudioInfo,
        title: String,
        artist: String,
        album: String,
        albumId: Long? = null,
        isrc: String? = null,
    ): String {
        requireDownloadAccess()
        val id = UUID.randomUUID().toString()
        dao.upsert(
            DownloadEntity(
                id = id,
                sourceUrl = info.finalUrl,
                providerId = DownloadProvider.DIRECT,
                title = title.ifBlank { info.fileName },
                artist = artist,
                album = album,
                totalBytes = info.sizeBytes,
                isrc = isrc,
            ),
        )
        enqueueWork(id, DownloadProvider.DIRECT, albumId)
        return id
    }

    suspend fun enqueueYtDlp(
        sourceUrl: String,
        info: YtDlpMediaInfo,
        title: String,
        artist: String,
        album: String,
        albumId: Long? = null,
        isrc: String? = null,
        thumbnailUrl: String? = null,
    ): String {
        requireDownloadAccess()
        val id = UUID.randomUUID().toString()
        dao.upsert(
            DownloadEntity(
                id = id,
                sourceUrl = sourceUrl,
                providerId = DownloadProvider.YT_DLP,
                thumbnailUrl = thumbnailUrl ?: info.thumbnailUrl,
                title = title.ifBlank { info.title },
                artist = artist.ifBlank { info.artist },
                album = album.trim(),
                totalBytes = info.sizeBytes,
                isrc = isrc,
            ),
        )
        enqueueWork(id, DownloadProvider.YT_DLP, albumId)
        return id
    }

    suspend fun retry(id: String) {
        requireDownloadAccess()
        val current = dao.getById(id) ?: return
        if (
            current.status == DownloadStatus.COMPLETED.name ||
            current.status in ACTIVE_DOWNLOAD_STATUSES
        ) return
        dao.upsert(
            current.copy(
                status = DownloadStatus.PENDING.name,
                errorMessage = null,
                completedAt = null,
            ),
        )
        val albumId = current.album.takeIf(String::isNotBlank)
            ?.let { albumDao.findFirstByName(it)?.id }
        enqueueWork(id, current.providerId, albumId)
    }

    suspend fun cancel(id: String) {
        val before = dao.getById(id) ?: return
        if (before.status == DownloadStatus.COMPLETED.name) return
        workManager.cancelUniqueWork(workName(id))
        val current = dao.getById(id) ?: return
        if (current.status == DownloadStatus.COMPLETED.name) return
        dao.upsert(DownloadStateTransitions.cancelled(current))
    }

    suspend fun deleteRecord(id: String) {
        workManager.cancelUniqueWork(workName(id))
        dao.delete(id)
        java.io.File(context.cacheDir, "polentita-download-$id.part").delete()
        java.io.File(context.cacheDir, "yt-dlp/$id").deleteRecursively()
    }

    private suspend fun enqueueWork(id: String, providerId: String, albumId: Long?) {
        val access = requireDownloadAccess()
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(
                if (access.wifiOnlyDownloads) NetworkType.UNMETERED else NetworkType.CONNECTED,
            )
            .build()
        val input = Data.Builder()
            .putString(DirectDownloadWorker.KEY_ID, id)
            .apply {
                albumId?.let { putLong(DirectDownloadWorker.KEY_ALBUM_ID, it) }
            }
            .build()
        val work = if (providerId == DownloadProvider.YT_DLP) {
            OneTimeWorkRequestBuilder<YtDlpDownloadWorker>()
                .setInputData(input)
                .setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10, TimeUnit.SECONDS)
                .addTag(DOWNLOAD_TAG)
                .build()
        } else {
            OneTimeWorkRequestBuilder<DirectDownloadWorker>()
                .setInputData(input)
                .setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10, TimeUnit.SECONDS)
                .addTag(DOWNLOAD_TAG)
                .build()
        }
        // KEEP closes the race where the worker has already requested a retry
        // but its Room status has not been observed yet.
        workManager.enqueueUniqueWork(workName(id), ExistingWorkPolicy.KEEP, work)
    }

    private suspend fun requireDownloadAccess() = networkAccessPolicy.current().also { access ->
        access.downloadBlockReason?.let { throw NetworkAccessBlockedException(it) }
    }

    private fun workName(id: String) = "direct-download-$id"

    companion object {
        const val DOWNLOAD_TAG = "polentita-download"
        private val ACTIVE_DOWNLOAD_STATUSES = setOf(
            DownloadStatus.PENDING.name,
            DownloadStatus.DOWNLOADING.name,
            DownloadStatus.VALIDATING.name,
            DownloadStatus.SAVING.name,
        )
    }
}
