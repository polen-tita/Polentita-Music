package com.polentita.music.core.storage

import android.content.ContentResolver
import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import androidx.documentfile.provider.DocumentFile
import com.polentita.music.core.common.AudioFormats
import com.polentita.music.core.common.FileNameSanitizer
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.FileNotFoundException
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class AudioMetadata(
    val title: String,
    val artist: String,
    val album: String,
    val genre: String,
    val year: Int?,
    val trackNumber: Int?,
    val discNumber: Int?,
    val durationMs: Long,
    val displayName: String,
    val mimeType: String,
    val fileSize: Long,
    val modifiedAt: Long,
    val embeddedCover: ByteArray?,
)

data class StoredDocument(val uri: Uri, val displayName: String, val mimeType: String)

@Singleton
class LibraryStorage @Inject constructor(
    @ApplicationContext private val context: Context,
    private val preferencesStore: PreferencesStore,
) {
    private val resolver: ContentResolver get() = context.contentResolver

    suspend fun linkLibrary(treeUri: Uri) = withContext(Dispatchers.IO) {
        val root = DocumentFile.fromTreeUri(context, treeUri)
            ?.takeIf { it.exists() && it.canWrite() }
            ?: throw SecurityException("No se pudo escribir en la carpeta seleccionada")
        listOf("Music", "Covers", "Imports", "Downloads").forEach { name ->
            root.findFile(name) ?: root.createDirectory(name)
                ?: throw FileNotFoundException("No se pudo crear la carpeta $name")
        }
        root.findFile("Covers")?.let(::ensureNoMedia)
        preferencesStore.setLibraryTreeUri(treeUri.toString())
    }

    suspend fun isLibraryAccessible(): Boolean = withContext(Dispatchers.IO) {
        rootOrNull()?.let { it.exists() && it.canRead() && it.canWrite() } == true
    }

    suspend fun ensureCoverFolderHidden() = withContext(Dispatchers.IO) {
        rootOrNull()?.findFile("Covers")?.let(::ensureNoMedia)
    }

    suspend fun copyIntoLibrary(
        source: Uri,
        subfolder: String,
        preferredName: String? = null,
        preferredMime: String? = null,
    ): StoredDocument = withContext(Dispatchers.IO) {
        val metadata = queryFile(source)
        val safeName = uniqueName(
            folder(subfolder),
            FileNameSanitizer.sanitize(preferredName ?: metadata.displayName),
        )
        val mime = preferredMime?.takeIf { it.startsWith("audio/") }
            ?: metadata.mimeType.takeIf { it.startsWith("audio/") }
            ?: AudioFormats.mimeFor(safeName)
        if (!AudioFormats.isSupported(mime, safeName)) {
            throw IllegalArgumentException("Formato de audio no compatible")
        }
        val destination = folder(subfolder).createFile(mime, "$safeName.part")
            ?: throw FileNotFoundException("No se pudo crear el archivo de destino")
        try {
            resolver.openInputStream(source).use { input ->
                requireNotNull(input) { "No se pudo abrir el archivo" }
                resolver.openOutputStream(destination.uri, "w").use { output ->
                    requireNotNull(output) { "No se pudo escribir el archivo" }
                    input.copyTo(output, DEFAULT_BUFFER_SIZE)
                }
            }
            if (!destination.renameTo(safeName)) {
                throw FileNotFoundException("El proveedor no permitió completar el archivo")
            }
            StoredDocument(destination.uri, safeName, mime)
        } catch (error: Throwable) {
            destination.delete()
            throw error
        }
    }

    suspend fun writeDownloadedFile(
        sourceFile: java.io.File,
        displayName: String,
        mimeType: String,
    ): StoredDocument = withContext(Dispatchers.IO) {
        val safeName = uniqueName(folder("Downloads"), FileNameSanitizer.sanitize(displayName))
        val destination = folder("Downloads").createFile(mimeType, "$safeName.part")
            ?: throw FileNotFoundException("No se pudo crear el archivo de descarga")
        try {
            sourceFile.inputStream().use { input ->
                resolver.openOutputStream(destination.uri, "w").use { output ->
                    requireNotNull(output)
                    input.copyTo(output, DEFAULT_BUFFER_SIZE)
                }
            }
            if (!destination.renameTo(safeName)) {
                throw FileNotFoundException("No se pudo finalizar la descarga")
            }
            StoredDocument(destination.uri, safeName, mimeType)
        } catch (error: Throwable) {
            destination.delete()
            throw error
        }
    }

    suspend fun extractAndStoreCover(audioUri: Uri, checksum: String): String? =
        withContext(Dispatchers.IO) {
            val picture = extractMetadata(audioUri).embeddedCover ?: return@withContext null
            if (picture.size > 8 * 1024 * 1024) return@withContext null
            val coverFolder = coversFolder()
            val name = "$checksum.jpg"
            coverFolder.findFile(name)?.uri?.toString()?.let { return@withContext it }
            val document = coverFolder.createFile("image/jpeg", "$name.part") ?: return@withContext null
            try {
                resolver.openOutputStream(document.uri, "w").use { output ->
                    requireNotNull(output)
                    output.write(picture)
                }
                if (!document.renameTo(name)) {
                    document.delete()
                    null
                } else {
                    document.uri.toString()
                }
            } catch (_: Throwable) {
                document.delete()
                null
            }
        }

    suspend fun findStoredCover(checksum: String): String? = withContext(Dispatchers.IO) {
        val safeChecksum = checksum.lowercase().filter(Char::isLetterOrDigit).take(64)
        if (safeChecksum.isBlank()) return@withContext null
        val coverFolder = coversFolder()
        listOf("jpg", "png", "webp").firstNotNullOfOrNull { extension ->
            coverFolder.findFile("$safeChecksum.$extension")
                ?.takeIf { document ->
                    document.exists() &&
                        document.isFile &&
                        document.type
                            ?.substringBefore(';')
                            ?.lowercase()
                            ?.let(SUPPORTED_COVER_MIME_TYPES::contains) != false
                }
                ?.uri
                ?.toString()
        }
    }

    suspend fun storeDownloadedCover(
        sourceFile: java.io.File,
        checksum: String,
        mimeType: String,
    ): String = withContext(Dispatchers.IO) {
        val extension = when (mimeType.lowercase()) {
            "image/jpeg", "image/jpg" -> "jpg"
            "image/png" -> "png"
            "image/webp" -> "webp"
            else -> throw IllegalArgumentException("Formato de portada no compatible")
        }
        val safeChecksum = checksum.lowercase().filter(Char::isLetterOrDigit).take(64)
        require(safeChecksum.isNotBlank()) { "No se pudo identificar la portada" }
        val coverFolder = coversFolder()
        listOf("jpg", "png", "webp").forEach { existingExtension ->
            coverFolder.findFile("$safeChecksum.$existingExtension")?.uri?.let {
                return@withContext it.toString()
            }
        }
        val name = "$safeChecksum.$extension"
        val destination = coverFolder.createFile(mimeType, "$name.part")
            ?: throw FileNotFoundException("No se pudo crear la portada")
        try {
            sourceFile.inputStream().use { input ->
                resolver.openOutputStream(destination.uri, "w").use { output ->
                    requireNotNull(output) { "No se pudo escribir la portada" }
                    input.copyTo(output, DEFAULT_BUFFER_SIZE)
                }
            }
            if (!destination.renameTo(name)) {
                throw FileNotFoundException("No se pudo finalizar la portada")
            }
            destination.uri.toString()
        } catch (error: Throwable) {
            destination.delete()
            throw error
        }
    }

    /** Deletes only an exact document inside Polentita's managed Covers folder. */
    suspend fun deleteManagedCover(uri: String): Boolean = withContext(Dispatchers.IO) {
        val target = coversFolder().listFiles().firstOrNull { document ->
            document.isFile && document.uri.toString() == uri
        } ?: return@withContext false
        target.delete()
    }

    suspend fun extractMetadata(uri: Uri): AudioMetadata = withContext(Dispatchers.IO) {
        val file = queryFile(uri)
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, uri)
            val title = retriever.value(MediaMetadataRetriever.METADATA_KEY_TITLE)
                .ifBlank { file.displayName.substringBeforeLast('.') }
            AudioMetadata(
                title = title,
                artist = retriever.value(MediaMetadataRetriever.METADATA_KEY_ARTIST),
                album = retriever.value(MediaMetadataRetriever.METADATA_KEY_ALBUM),
                genre = retriever.value(MediaMetadataRetriever.METADATA_KEY_GENRE),
                year = retriever.value(MediaMetadataRetriever.METADATA_KEY_YEAR).toIntOrNull(),
                trackNumber = retriever.value(MediaMetadataRetriever.METADATA_KEY_CD_TRACK_NUMBER)
                    .substringBefore('/').toIntOrNull(),
                discNumber = retriever.value(MediaMetadataRetriever.METADATA_KEY_DISC_NUMBER)
                    .substringBefore('/').toIntOrNull(),
                durationMs = retriever.value(MediaMetadataRetriever.METADATA_KEY_DURATION).toLongOrNull() ?: 0,
                displayName = file.displayName,
                mimeType = file.mimeType,
                fileSize = file.size,
                modifiedAt = file.modifiedAt,
                embeddedCover = runCatching { retriever.embeddedPicture }.getOrNull(),
            )
        } catch (error: Throwable) {
            throw IllegalArgumentException("No se pudo leer el audio: ${error.message}", error)
        } finally {
            retriever.release()
        }
    }

    suspend fun checksum(uri: Uri): String = withContext(Dispatchers.IO) {
        val digest = MessageDigest.getInstance("SHA-256")
        resolver.openInputStream(uri).use { input ->
            requireNotNull(input) { "No se pudo abrir el archivo" }
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        digest.digest().joinToString("") { "%02x".format(it) }
    }

    suspend fun delete(uri: Uri): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            if (DocumentsContract.isDocumentUri(context, uri)) {
                DocumentsContract.deleteDocument(resolver, uri)
            } else {
                DocumentFile.fromSingleUri(context, uri)?.delete() == true
            }
        }.getOrDefault(false)
    }

    suspend fun rename(uri: Uri, newName: String): Uri? = withContext(Dispatchers.IO) {
        val safe = FileNameSanitizer.sanitize(newName)
        runCatching { DocumentsContract.renameDocument(resolver, uri, safe) }.getOrNull()
    }

    suspend fun audioDocuments(): List<DocumentFile> = withContext(Dispatchers.IO) {
        val root = rootOrNull() ?: return@withContext emptyList()
        root.findFile("Covers")?.let(::ensureNoMedia)
        listOf("Music", "Imports", "Downloads")
            .mapNotNull(root::findFile)
            .flatMap(::walkAudio)
    }

    fun canWrite(uri: Uri): Boolean =
        DocumentFile.fromSingleUri(context, uri)?.canWrite() == true

    private fun walkAudio(document: DocumentFile): List<DocumentFile> {
        if (document.isFile) {
            return if (AudioFormats.isSupported(document.type, document.name.orEmpty())) listOf(document) else emptyList()
        }
        if (!document.isDirectory) return emptyList()
        return document.listFiles().flatMap(::walkAudio)
    }

    private suspend fun rootOrNull(): DocumentFile? {
        val raw = preferencesStore.current().libraryTreeUri ?: return null
        return DocumentFile.fromTreeUri(context, Uri.parse(raw))
    }

    private suspend fun folder(name: String): DocumentFile {
        val tree = preferencesStore.current().libraryTreeUri
            ?: throw IllegalStateException("Primero selecciona una carpeta de biblioteca")
        val root = DocumentFile.fromTreeUri(context, Uri.parse(tree))
            ?: throw FileNotFoundException("La carpeta necesita volver a vincularse")
        return root.findFile(name) ?: root.createDirectory(name)
            ?: throw FileNotFoundException("No se pudo acceder a $name")
    }

    private suspend fun coversFolder(): DocumentFile = folder("Covers").also(::ensureNoMedia)

    private fun ensureNoMedia(folder: DocumentFile) {
        if (folder.findFile(NO_MEDIA_FILE) == null) {
            folder.createFile("application/octet-stream", NO_MEDIA_FILE)
        }
    }

    private fun uniqueName(folder: DocumentFile, requested: String): String {
        if (folder.findFile(requested) == null) return requested
        val base = requested.substringBeforeLast('.', requested)
        val extension = requested.substringAfterLast('.', "").let { if (it.isBlank()) "" else ".$it" }
        var suffix = 2
        while (folder.findFile("$base ($suffix)$extension") != null) suffix++
        return "$base ($suffix)$extension"
    }

    private data class QueriedFile(
        val displayName: String,
        val mimeType: String,
        val size: Long,
        val modifiedAt: Long,
    )

    private fun queryFile(uri: Uri): QueriedFile {
        var name = uri.lastPathSegment?.substringAfterLast('/') ?: "audio"
        var size = 0L
        resolver.query(
            uri,
            arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE, DocumentsContract.Document.COLUMN_LAST_MODIFIED),
            null,
            null,
            null,
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME).takeIf { it >= 0 }?.let {
                    name = cursor.getString(it) ?: name
                }
                cursor.getColumnIndex(OpenableColumns.SIZE).takeIf { it >= 0 }?.let {
                    if (!cursor.isNull(it)) size = cursor.getLong(it)
                }
                val modified = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_LAST_MODIFIED)
                    .takeIf { it >= 0 && !cursor.isNull(it) }
                    ?.let(cursor::getLong)
                    ?: 0L
                return QueriedFile(name, resolver.getType(uri).orEmpty(), size, modified)
            }
        }
        return QueriedFile(name, resolver.getType(uri).orEmpty(), size, 0)
    }

    private fun MediaMetadataRetriever.value(key: Int): String =
        extractMetadata(key)?.trim().orEmpty()

    private companion object {
        const val NO_MEDIA_FILE = ".nomedia"
        val SUPPORTED_COVER_MIME_TYPES = setOf(
            "image/jpeg",
            "image/jpg",
            "image/png",
            "image/webp",
        )
    }
}
