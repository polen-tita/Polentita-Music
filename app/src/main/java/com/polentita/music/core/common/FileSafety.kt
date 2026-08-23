package com.polentita.music.core.common

import android.webkit.MimeTypeMap
import java.net.URI
import java.text.Normalizer
import java.util.Locale

object FileNameSanitizer {
    private const val MAX_LENGTH = 120
    private val unsafe = Regex("""[\\/:*?"<>|\u0000-\u001F]""")
    private val repeatedDots = Regex("""\.{2,}""")
    private val spaces = Regex("""\s+""")

    fun sanitize(input: String, fallback: String = "audio"): String {
        val normalized = Normalizer.normalize(input, Normalizer.Form.NFKC)
            .substringAfterLast('/')
            .substringAfterLast('\\')
            .replace(unsafe, "_")
            .replace(repeatedDots, ".")
            .replace(spaces, " ")
            .trim(' ', '.')
        val candidate = normalized.ifBlank { fallback }
        if (candidate.length <= MAX_LENGTH) return candidate
        val extension = candidate.substringAfterLast('.', "")
        val suffix = extension.takeIf { it.length in 1..10 }?.let { ".$it" }.orEmpty()
        return candidate.take(MAX_LENGTH - suffix.length).trimEnd() + suffix
    }
}

object AudioFormats {
    val mimeTypes = setOf(
        "audio/mpeg",
        "audio/mp4",
        "audio/aac",
        "audio/ogg",
        "application/ogg",
        "audio/opus",
        "audio/x-m4a",
        "audio/flac",
        "audio/x-flac",
        "audio/wav",
        "audio/x-wav",
        "audio/vnd.wave",
        "audio/webm",
    )
    val extensions = setOf("mp3", "m4a", "aac", "ogg", "opus", "flac", "wav", "webm")

    fun isSupported(mimeType: String?, fileName: String): Boolean {
        val cleanMime = mimeType?.substringBefore(';')?.lowercase(Locale.ROOT)
        val extension = fileName.substringAfterLast('.', "").lowercase(Locale.ROOT)
        if (cleanMime in mimeTypes) return true
        if (cleanMime.isNullOrBlank() || cleanMime == "application/octet-stream") {
            return extension in extensions
        }
        return false
    }

    fun mimeFor(fileName: String): String {
        val ext = fileName.substringAfterLast('.', "").lowercase(Locale.ROOT)
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext)
            ?.takeIf { it.startsWith("audio/") }
            ?: when (ext) {
                "opus" -> "audio/opus"
                "flac" -> "audio/flac"
                "webm" -> "audio/webm"
                else -> "audio/mpeg"
            }
    }
}

sealed interface UrlValidation {
    data class Valid(val uri: URI) : UrlValidation
    data class Invalid(val message: String) : UrlValidation
}

object RemoteUrlValidator {
    fun validate(raw: String): UrlValidation {
        val trimmed = raw.trim()
        if (trimmed.length !in 8..8192) return UrlValidation.Invalid("El enlace no tiene una longitud válida")
        val uri = runCatching { URI(trimmed) }.getOrNull()
            ?: return UrlValidation.Invalid("El enlace no es válido")
        if (!uri.scheme.equals("https", ignoreCase = true)) {
            return UrlValidation.Invalid("Solo se permiten enlaces HTTPS")
        }
        if (uri.host.isNullOrBlank() || uri.userInfo != null) {
            return UrlValidation.Invalid("El dominio del enlace no es válido")
        }
        if (isPrivateHost(uri.host)) {
            return UrlValidation.Invalid("No se permiten direcciones locales o privadas")
        }
        if (uri.fragment != null) return UrlValidation.Invalid("El enlace no debe contener fragmentos")
        return UrlValidation.Valid(uri)
    }

    fun redacted(raw: String): String =
        (validate(raw) as? UrlValidation.Valid)?.uri?.let { "${it.scheme}://${it.host}${it.path}" }
            ?: "enlace inválido"

    private fun isPrivateHost(host: String): Boolean {
        val normalized = host.lowercase(Locale.ROOT).removePrefix("[").removeSuffix("]")
        if (normalized == "localhost" || normalized.endsWith(".localhost") ||
            normalized == "::1" || normalized == "0:0:0:0:0:0:0:1" ||
            normalized.startsWith("fe80:")
        ) return true
        val parts = normalized.split('.').mapNotNull(String::toIntOrNull)
        if (parts.size != 4) return false
        return parts[0] == 10 ||
            parts[0] == 127 ||
            parts[0] == 0 ||
            (parts[0] == 169 && parts[1] == 254) ||
            (parts[0] == 192 && parts[1] == 168) ||
            (parts[0] == 172 && parts[1] in 16..31)
    }
}
