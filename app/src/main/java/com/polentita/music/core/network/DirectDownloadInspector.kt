package com.polentita.music.core.network

import com.polentita.music.core.common.AudioFormats
import com.polentita.music.core.common.FileNameSanitizer
import com.polentita.music.core.common.RemoteUrlValidator
import com.polentita.music.core.common.UrlValidation
import java.net.URI
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response

data class RemoteAudioInfo(
    val finalUrl: String,
    val domain: String,
    val fileName: String,
    val mimeType: String,
    val sizeBytes: Long,
    val acceptsRanges: Boolean,
)

@Singleton
class DirectDownloadInspector @Inject constructor(
    private val client: OkHttpClient,
) {
    suspend fun inspect(rawUrl: String): RemoteAudioInfo = withContext(Dispatchers.IO) {
        val initial = (RemoteUrlValidator.validate(rawUrl) as? UrlValidation.Valid)?.uri
            ?: throw IllegalArgumentException("Ingresa un enlace HTTPS válido")
        var response = executeWithRedirects(initial.toString(), head = true)
        if (response.code == 405 || response.code == 501) {
            response.close()
            response = executeWithRedirects(initial.toString(), head = false, rangeProbe = true)
        }
        response.use {
            if (!it.isSuccessful && it.code != 206) {
                throw IllegalArgumentException("El servidor respondió con ${it.code}")
            }
            val finalUri = URI(it.request.url.toString())
            val dispositionName = contentDispositionName(it.header("Content-Disposition"))
            val pathName = finalUri.path.substringAfterLast('/').ifBlank { "audio" }
            val fileName = FileNameSanitizer.sanitize(dispositionName ?: pathName)
            val mime = it.header("Content-Type")
                ?.substringBefore(';')
                ?.trim()
                ?.lowercase()
                .orEmpty()
            requireCompatibleRemoteAudio(mime, fileName)
            val size = contentRangeTotal(it.header("Content-Range"))
                ?: it.header("Content-Length")?.toLongOrNull()
                ?: -1L
            RemoteAudioInfo(
                finalUrl = it.request.url.toString(),
                domain = finalUri.host,
                fileName = ensureExtension(fileName, mime),
                mimeType = mime.ifBlank { AudioFormats.mimeFor(fileName) },
                sizeBytes = size,
                acceptsRanges = it.code == 206 || it.header("Accept-Ranges").equals("bytes", true),
            )
        }
    }

    fun executeGet(url: String, downloaded: Long = 0): Response {
        val requestBuilder = Request.Builder()
            .url(url)
            .get()
            .header("User-Agent", "PolentitaMusic/1")
        if (downloaded > 0) requestBuilder.header("Range", "bytes=$downloaded-")
        return executeRequestFollowingRedirects(requestBuilder.build())
    }

    private fun executeWithRedirects(url: String, head: Boolean, rangeProbe: Boolean = false): Response {
        val builder = Request.Builder().url(url).header("User-Agent", "PolentitaMusic/1")
        if (head) builder.head() else builder.get()
        if (rangeProbe) builder.header("Range", "bytes=0-0")
        return executeRequestFollowingRedirects(builder.build())
    }

    private fun executeRequestFollowingRedirects(initial: Request): Response {
        var request = initial
        repeat(MAX_REDIRECTS + 1) { redirectCount ->
            val response = client.newCall(request).execute()
            if (response.code !in REDIRECT_CODES) return response
            if (redirectCount == MAX_REDIRECTS) {
                response.close()
                throw IllegalArgumentException("El enlace excede el límite de redirecciones")
            }
            val redirectCode = response.code
            val location = response.header("Location")
                ?: run {
                    response.close()
                    throw IllegalArgumentException("La redirección no tiene destino")
                }
            val next = response.request.url.resolve(location)
            response.close()
            val validation = RemoteUrlValidator.validate(next?.toString().orEmpty())
            if (validation !is UrlValidation.Valid) {
                throw IllegalArgumentException("La redirección no es HTTPS o no es segura")
            }
            request = request.newBuilder()
                .url(validation.uri.toString())
                .apply { if (redirectCode == 303) get() }
                .build()
        }
        error("Redirección inesperada")
    }

    private fun contentDispositionName(value: String?): String? {
        if (value.isNullOrBlank()) return null
        val utf8 = Regex("""filename\*=UTF-8''([^;]+)""", RegexOption.IGNORE_CASE)
            .find(value)?.groupValues?.getOrNull(1)
            ?.let { runCatching { java.net.URLDecoder.decode(it, Charsets.UTF_8.name()) }.getOrNull() }
        if (!utf8.isNullOrBlank()) return utf8
        return Regex("""filename="?([^";]+)"?""", RegexOption.IGNORE_CASE)
            .find(value)?.groupValues?.getOrNull(1)
    }

    private fun contentRangeTotal(value: String?): Long? =
        value?.substringAfterLast('/', "")?.toLongOrNull()

    private fun ensureExtension(name: String, mime: String): String {
        if (name.substringAfterLast('.', "").lowercase() in AudioFormats.extensions) return name
        val extension = when (mime) {
            "audio/mp4" -> "m4a"
            "audio/aac" -> "aac"
            "audio/ogg" -> "ogg"
            "audio/opus" -> "opus"
            "audio/flac", "audio/x-flac" -> "flac"
            "audio/wav", "audio/x-wav", "audio/vnd.wave" -> "wav"
            "audio/webm" -> "webm"
            else -> "mp3"
        }
        return "$name.$extension"
    }

    companion object {
        private const val MAX_REDIRECTS = 5
        private val REDIRECT_CODES = setOf(301, 302, 303, 307, 308)
    }
}

internal fun requireCompatibleRemoteAudio(mimeType: String, fileName: String) {
    if (!AudioFormats.isSupported(mimeType, fileName)) {
        throw IllegalArgumentException("Este enlace no apunta a un archivo de audio compatible")
    }
}
