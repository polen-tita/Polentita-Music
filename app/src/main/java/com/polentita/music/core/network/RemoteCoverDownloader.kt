package com.polentita.music.core.network

import android.graphics.BitmapFactory
import com.polentita.music.core.common.RemoteUrlValidator
import com.polentita.music.core.common.UrlValidation
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response

data class DownloadedCover(
    val file: File,
    val mimeType: String,
)

object CoverImageValidator {
    const val MAX_COVER_BYTES = 8L * 1024 * 1024

    fun validate(file: File, declaredMimeType: String? = null): String {
        require(file.length() in 1..MAX_COVER_BYTES) {
            if (file.length() == 0L) "La portada está vacía" else "La portada supera el límite de 8 MB"
        }
        val declaredMime = declaredMimeType
            ?.substringBefore(';')
            ?.trim()
            ?.lowercase()
            .orEmpty()
        if (declaredMime.isNotBlank() &&
            declaredMime != "application/octet-stream" &&
            declaredMime !in SUPPORTED_MIME_TYPES
        ) {
            throw IllegalArgumentException("La portada no es JPEG, PNG o WebP")
        }
        val detectedMime = detectMime(file)
            ?: throw IllegalArgumentException("La portada no tiene un formato de imagen válido")
        require(hasCompleteContainer(file, detectedMime)) {
            "La portada está dañada o incompleta"
        }
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        require(bounds.outWidth in 1..MAX_COVER_DIMENSION && bounds.outHeight in 1..MAX_COVER_DIMENSION) {
            "La portada está dañada o tiene dimensiones no compatibles"
        }
        var sampleSize = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / sampleSize > VALIDATION_DECODE_SIZE) {
            sampleSize *= 2
        }
        val decoded = BitmapFactory.decodeFile(
            file.absolutePath,
            BitmapFactory.Options().apply { inSampleSize = sampleSize },
        )
        requireNotNull(decoded) { "La portada está dañada o no puede decodificarse" }
            .recycle()
        if (declaredMime in SUPPORTED_MIME_TYPES && normalizeMime(declaredMime) != detectedMime) {
            throw IllegalArgumentException("El tipo de la portada no coincide con su contenido")
        }
        return detectedMime
    }

    private fun detectMime(file: File): String? {
        val header = ByteArray(12)
        val count = file.inputStream().use { it.read(header) }
        if (count >= 3 &&
            header[0] == 0xFF.toByte() &&
            header[1] == 0xD8.toByte() &&
            header[2] == 0xFF.toByte()
        ) {
            return "image/jpeg"
        }
        if (count >= 8 && header.copyOfRange(0, 8).contentEquals(PNG_SIGNATURE)) {
            return "image/png"
        }
        if (count >= 12 &&
            header.copyOfRange(0, 4).decodeToString() == "RIFF" &&
            header.copyOfRange(8, 12).decodeToString() == "WEBP"
        ) {
            return "image/webp"
        }
        return null
    }

    private fun hasCompleteContainer(file: File, mimeType: String): Boolean =
        runCatching {
            RandomAccessFile(file, "r").use { input ->
                when (mimeType) {
                    "image/jpeg" -> {
                        if (input.length() < 4) return@use false
                        input.seek(input.length() - 2)
                        input.readUnsignedByte() == 0xFF && input.readUnsignedByte() == 0xD9
                    }
                    "image/png" -> {
                        if (input.length() < 20) return@use false
                        input.seek(input.length() - 8)
                        val marker = ByteArray(4)
                        input.readFully(marker)
                        marker.decodeToString() == "IEND"
                    }
                    "image/webp" -> {
                        if (input.length() < 12) return@use false
                        input.seek(4)
                        val size = input.readUnsignedByte().toLong() or
                            (input.readUnsignedByte().toLong() shl 8) or
                            (input.readUnsignedByte().toLong() shl 16) or
                            (input.readUnsignedByte().toLong() shl 24)
                        size + 8 == input.length()
                    }
                    else -> false
                }
            }
        }.getOrDefault(false)

    private fun normalizeMime(mimeType: String): String =
        if (mimeType == "image/jpg") "image/jpeg" else mimeType

    private val SUPPORTED_MIME_TYPES = setOf("image/jpeg", "image/jpg", "image/png", "image/webp")
    private const val MAX_COVER_DIMENSION = 20_000
    private const val VALIDATION_DECODE_SIZE = 512
    private val PNG_SIGNATURE = byteArrayOf(
        0x89.toByte(),
        0x50,
        0x4E,
        0x47,
        0x0D,
        0x0A,
        0x1A,
        0x0A,
    )
}

@Singleton
class RemoteCoverDownloader @Inject constructor(
    private val client: OkHttpClient,
) {
    suspend fun download(url: String, destination: File): DownloadedCover =
        withContext(Dispatchers.IO) {
            val safeUrl = validUrl(url)
            destination.parentFile?.mkdirs()
            try {
                executeFollowingRedirects(safeUrl).use { response ->
                    if (!response.isSuccessful) {
                        throw IllegalArgumentException("El servidor de portada respondió con ${response.code}")
                    }
                    val declaredMime = response.header("Content-Type")
                        ?.substringBefore(';')
                        ?.trim()
                        ?.lowercase()
                        .orEmpty()
                    if (declaredMime.isNotBlank() &&
                        declaredMime != "application/octet-stream" &&
                        declaredMime !in SUPPORTED_MIME_TYPES
                    ) {
                        throw IllegalArgumentException("La miniatura no es JPEG, PNG o WebP")
                    }
                    val length = response.body?.contentLength() ?: -1
                    if (length > CoverImageValidator.MAX_COVER_BYTES) {
                        throw IllegalArgumentException("La miniatura supera el límite de 8 MB")
                    }
                    var total = 0L
                    FileOutputStream(destination, false).use { output ->
                        val body = requireNotNull(response.body) { "La miniatura no contiene datos" }
                        body.byteStream().use { input ->
                            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                            while (true) {
                                val count = input.read(buffer)
                                if (count < 0) break
                                total += count
                                if (total > CoverImageValidator.MAX_COVER_BYTES) {
                                    throw IllegalArgumentException("La miniatura supera el límite de 8 MB")
                                }
                                output.write(buffer, 0, count)
                            }
                            output.fd.sync()
                        }
                    }
                    require(total > 0) { "La miniatura está vacía" }
                    val detectedMime = runCatching {
                        CoverImageValidator.validate(destination, declaredMime)
                    }.getOrElse { error ->
                        throw IllegalArgumentException(
                            error.message?.replace("portada", "miniatura")
                                ?: "La miniatura no tiene un formato de imagen válido",
                            error,
                        )
                    }
                    DownloadedCover(destination, detectedMime)
                }
            } catch (error: Throwable) {
                destination.delete()
                throw error
            }
        }

    private fun executeFollowingRedirects(initialUrl: String): Response {
        var request = Request.Builder().url(initialUrl).get().build()
        repeat(MAX_REDIRECTS + 1) { redirectCount ->
            val response = client.newCall(request).execute()
            if (response.code !in REDIRECT_CODES) return response
            if (redirectCount == MAX_REDIRECTS) {
                response.close()
                throw IllegalArgumentException("La portada excede el límite de redirecciones")
            }
            val location = response.header("Location")
            val next = location?.let(response.request.url::resolve)
            response.close()
            request = Request.Builder()
                .url(validUrl(next?.toString().orEmpty()))
                .get()
                .build()
        }
        error("Redirección inesperada")
    }

    private fun validUrl(raw: String): String {
        val validation = RemoteUrlValidator.validate(raw)
        return (validation as? UrlValidation.Valid)?.uri?.toString()
            ?: throw IllegalArgumentException("La URL de portada no es HTTPS o no es segura")
    }

    private companion object {
        const val MAX_REDIRECTS = 3
        val REDIRECT_CODES = setOf(301, 302, 303, 307, 308)
        val SUPPORTED_MIME_TYPES = setOf("image/jpeg", "image/jpg", "image/png", "image/webp")
    }
}
