package com.polentita.music.data.downloader

import android.content.Context
import com.polentita.music.core.network.RemoteCoverDownloader
import com.polentita.music.core.storage.LibraryStorage
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Singleton
class DownloadedCoverResolver @Inject constructor(
    @ApplicationContext private val context: Context,
    private val downloader: RemoteCoverDownloader,
    private val storage: LibraryStorage,
) {
    suspend fun resolveOrNull(thumbnailUrl: String?, checksum: String): String? =
        withContext(Dispatchers.IO) {
            if (thumbnailUrl.isNullOrBlank()) return@withContext null
            val temporary = File.createTempFile(
                "polentita-cover-${checksum.take(16)}-",
                ".part",
                context.cacheDir,
            )
            try {
                try {
                    val downloaded = downloader.download(thumbnailUrl, temporary)
                    storage.storeDownloadedCover(downloaded.file, checksum, downloaded.mimeType)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Throwable) {
                    null
                }
            } finally {
                temporary.delete()
            }
        }
}
