package com.polentita.music.data.artwork

import android.content.Context
import android.net.Uri
import androidx.core.net.toUri
import com.polentita.music.core.common.sha256
import com.polentita.music.core.network.CoverImageValidator
import com.polentita.music.core.network.NetworkAccessBlockedException
import com.polentita.music.core.network.NetworkAccessPolicy
import com.polentita.music.core.network.NetworkBlockReason
import com.polentita.music.core.network.RemoteCoverDownloader
import com.polentita.music.core.storage.LibraryStorage
import com.polentita.music.domain.artwork.ArtworkChoice
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

interface ManagedArtworkStore {
    suspend fun prepare(choice: ArtworkChoice): String?
    suspend fun deleteIfManaged(uri: String): Boolean
}

@Singleton
class DefaultManagedArtworkStore @Inject constructor(
    @ApplicationContext private val context: Context,
    private val downloader: RemoteCoverDownloader,
    private val storage: LibraryStorage,
    private val networkAccessPolicy: NetworkAccessPolicy,
) : ManagedArtworkStore {
    override suspend fun prepare(choice: ArtworkChoice): String? = withContext(Dispatchers.IO) {
        when (choice) {
            ArtworkChoice.Unchanged -> error("La portada sin cambios no necesita prepararse")
            ArtworkChoice.Remove -> null
            is ArtworkChoice.LocalFile -> storeLocal(choice.contentUri.toUri())
            is ArtworkChoice.Remote -> storeRemote(choice.candidate.imageUrl)
        }
    }

    override suspend fun deleteIfManaged(uri: String): Boolean = storage.deleteManagedCover(uri)

    private suspend fun storeLocal(source: Uri): String {
        require(source.scheme.equals("content", ignoreCase = true)) {
            "Elige la portada mediante el selector de archivos"
        }
        val temporary = File.createTempFile("polentita-selected-cover-", ".part", context.cacheDir)
        try {
            context.contentResolver.openInputStream(source).use { input ->
                requireNotNull(input) { "No se pudo abrir la portada seleccionada" }
                temporary.outputStream().use { output ->
                    copyLimited(input, output)
                    output.fd.sync()
                }
            }
            val mimeType = CoverImageValidator.validate(
                temporary,
                context.contentResolver.getType(source),
            )
            return storage.storeDownloadedCover(temporary, temporary.sha256(), mimeType)
        } finally {
            temporary.delete()
        }
    }

    private suspend fun storeRemote(url: String): String {
        val access = networkAccessPolicy.current()
        if (!access.downloadAllowed) {
            throw NetworkAccessBlockedException(
                access.downloadBlockReason ?: NetworkBlockReason.NO_CONNECTION,
            )
        }
        val temporary = File.createTempFile("polentita-remote-cover-", ".part", context.cacheDir)
        try {
            val downloaded = downloader.download(url, temporary)
            return storage.storeDownloadedCover(
                downloaded.file,
                downloaded.file.sha256(),
                downloaded.mimeType,
            )
        } finally {
            temporary.delete()
        }
    }

    private fun copyLimited(input: java.io.InputStream, output: java.io.FileOutputStream) {
        var total = 0L
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            total += count
            require(total <= CoverImageValidator.MAX_COVER_BYTES) {
                "La portada supera el límite de 8 MB"
            }
            output.write(buffer, 0, count)
        }
        require(total > 0) { "La portada está vacía" }
    }
}
