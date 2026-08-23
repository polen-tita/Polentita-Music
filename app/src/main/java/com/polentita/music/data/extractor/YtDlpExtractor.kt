package com.polentita.music.data.extractor

import android.content.Context
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform
import com.polentita.music.core.common.RemoteUrlValidator
import com.polentita.music.core.common.UrlValidation
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

data class YtDlpMediaInfo(
    val id: String,
    val title: String,
    val artist: String,
    val album: String,
    val durationMs: Long,
    val thumbnailUrl: String?,
    val webpageUrl: String,
    val extractor: String,
    val extension: String,
    val sizeBytes: Long,
)

data class YtDlpPreviewInfo(
    val id: String,
    val title: String,
    val artist: String,
    val durationMs: Long,
    val thumbnailUrl: String?,
    val webpageUrl: String,
    val streamUrl: String,
    val mimeType: String? = null,
    val httpHeaders: Map<String, String> = emptyMap(),
)

data class YtDlpDownloadedFile(
    val file: File,
    val media: YtDlpMediaInfo,
)

data class YtDlpSearchResult(
    val id: String,
    val title: String,
    val channel: String,
    val durationMs: Long,
    val thumbnailUrl: String?,
    val webpageUrl: String,
    val uploadDate: String?,
)

data class YtDlpSearchPage(
    val items: List<YtDlpSearchResult>,
    val page: Int,
    val hasMore: Boolean,
)

data class YtDlpPlaylistEntry(
    val id: String,
    val title: String,
    val artist: String,
    val album: String,
    val durationMs: Long,
    val thumbnailUrl: String?,
    val webpageUrl: String,
)

data class YtDlpPlaylistInfo(
    val id: String,
    val title: String,
    val description: String?,
    val thumbnailUrl: String?,
    val webpageUrl: String,
    val entries: List<YtDlpPlaylistEntry>,
    val totalTracks: Int,
)

interface YtDlpProgressCallback {
    fun onProgress(status: String, downloadedBytes: Long, totalBytes: Long, speedBytesPerSecond: Long)
    fun isCancelled(): Boolean
}

interface YtDlpExtractor {
    suspend fun inspect(url: String): YtDlpMediaInfo
    suspend fun inspectPlaylist(url: String): YtDlpPlaylistInfo
    suspend fun resolvePreview(url: String): YtDlpPreviewInfo
    suspend fun download(url: String, outputDirectory: File, callback: YtDlpProgressCallback): YtDlpDownloadedFile
    suspend fun search(query: String, page: Int, pageSize: Int = 10): YtDlpSearchPage
}

@Singleton
class ChaquopyYtDlpExtractor @Inject constructor(
    @ApplicationContext private val context: Context,
) : YtDlpExtractor {
    override suspend fun search(query: String, page: Int, pageSize: Int): YtDlpSearchPage =
        withContext(Dispatchers.IO) {
            val cleanQuery = query.trim()
            require(cleanQuery.length >= 3) { "La búsqueda requiere al menos 3 caracteres" }
            require(page >= 0) { "La página no es válida" }
            val json = JSONObject(
                safeCall("search_youtube", cleanQuery, page, pageSize.coerceIn(1, 20)).toString(),
            )
            val items = json.optJSONArray("items")
            val results = buildList {
                if (items != null) {
                    repeat(items.length()) { index ->
                        val item = items.optJSONObject(index) ?: return@repeat
                        val webpageUrl = item.optString("webpageUrl")
                        if (RemoteUrlValidator.validate(webpageUrl) !is UrlValidation.Valid) {
                            return@repeat
                        }
                        add(
                            YtDlpSearchResult(
                                id = item.optString("id"),
                                title = item.optString("title").ifBlank { "Sin título" },
                                channel = item.optString("channel").ifBlank { "Canal desconocido" },
                                durationMs = item.optLong("durationMs"),
                                thumbnailUrl = item.optString("thumbnail").takeIf(String::isNotBlank),
                                webpageUrl = webpageUrl,
                                uploadDate = item.optString("uploadDate").takeIf(String::isNotBlank),
                            ),
                        )
                    }
                }
            }
            YtDlpSearchPage(
                items = results.distinctBy { result ->
                    result.id.ifBlank { result.webpageUrl }
                },
                page = json.optInt("page", page),
                hasMore = json.optBoolean("hasMore"),
            )
        }

    override suspend fun inspect(url: String): YtDlpMediaInfo = withContext(Dispatchers.IO) {
        val safeUrl = validate(url)
        parse(safeCall("inspect_media", safeUrl).toString()).withoutDownloadPath()
    }

    override suspend fun inspectPlaylist(url: String): YtDlpPlaylistInfo = withContext(Dispatchers.IO) {
        val safeUrl = validate(url)
        val json = JSONObject(safeCall("inspect_playlist", safeUrl).toString())
        val entries = json.optJSONArray("entries") ?: error("YouTube no devolvió canciones")
        val parsed = buildList {
            repeat(entries.length()) { index ->
                val item = entries.optJSONObject(index) ?: return@repeat
                val rawId = item.optString("id").trim()
                val fallbackUrl = rawId
                    .takeIf(String::isNotBlank)
                    ?.takeUnless { it.startsWith("https://", ignoreCase = true) }
                    ?.let { "https://www.youtube.com/watch?v=$it" }
                val webpageUrl = listOfNotNull(fallbackUrl, item.optString("webpageUrl"))
                    .firstOrNull { candidate ->
                        RemoteUrlValidator.validate(candidate) is UrlValidation.Valid
                    }
                    ?: return@repeat
                val id = rawId.ifBlank { webpageUrl }
                val title = item.optString("title").trim()
                if (id.isBlank() || title.isBlank()) return@repeat
                add(
                    YtDlpPlaylistEntry(
                        id = id,
                        title = title,
                        artist = item.optString("artist").trim(),
                        album = item.optString("album").trim(),
                        durationMs = item.optLong("durationMs").coerceAtLeast(0),
                        thumbnailUrl = item.optString("thumbnail").takeIf(String::isNotBlank),
                        webpageUrl = webpageUrl,
                    ),
                )
            }
        }.distinctBy { entry -> entry.id.ifBlank { entry.webpageUrl } }
        YtDlpPlaylistInfo(
            id = json.optString("id"),
            title = json.optString("title").trim(),
            description = json.optString("description").trim().takeIf(String::isNotBlank),
            thumbnailUrl = json.optString("thumbnail").takeIf(String::isNotBlank),
            webpageUrl = json.optString("webpageUrl").ifBlank { safeUrl },
            entries = parsed,
            totalTracks = json.optInt("totalTracks", parsed.size).coerceAtLeast(0),
        )
    }

    override suspend fun resolvePreview(url: String): YtDlpPreviewInfo = withContext(Dispatchers.IO) {
        val safeUrl = validate(url)
        val json = JSONObject(safeCall("preview_audio", safeUrl).toString())
        val streamUrl = json.optString("streamUrl")
        require(RemoteUrlValidator.validate(streamUrl) is UrlValidation.Valid) {
            "yt-dlp devolvió una pista de adelanto no válida"
        }
        YtDlpPreviewInfo(
            id = json.optString("id"),
            title = json.optString("title").ifBlank { "Audio" },
            artist = json.optString("artist"),
            durationMs = json.optLong("durationMs"),
            thumbnailUrl = json.optString("thumbnail").takeIf(String::isNotBlank),
            webpageUrl = json.optString("webpageUrl").ifBlank { safeUrl },
            streamUrl = streamUrl,
            mimeType = json.optString("mimeType").takeIf(String::isNotBlank),
            httpHeaders = parsePreviewHeaders(json.optJSONObject("httpHeaders")),
        )
    }

    override suspend fun download(
        url: String,
        outputDirectory: File,
        callback: YtDlpProgressCallback,
    ): YtDlpDownloadedFile = withContext(Dispatchers.IO) {
        val safeUrl = validate(url)
        require(outputDirectory.mkdirs() || outputDirectory.isDirectory) {
            "No se pudo preparar el directorio temporal"
        }
        val media = parse(
            safeCall(
                "download_audio",
                safeUrl,
                outputDirectory.canonicalPath,
                callback,
            ).toString(),
        )
        val file = File(requireNotNull(media.downloadPath) { "yt-dlp no informó el archivo descargado" })
        val root = outputDirectory.canonicalFile
        val canonical = file.canonicalFile
        require(canonical.path.startsWith(root.path + File.separator)) {
            "yt-dlp devolvió una ruta fuera del directorio temporal"
        }
        require(canonical.isFile && canonical.length() > 0) {
            "yt-dlp no produjo un archivo de audio"
        }
        YtDlpDownloadedFile(canonical, media.withoutDownloadPath())
    }

    private fun safeCall(function: String, vararg args: Any) =
        try {
            synchronized(PYTHON_LOCK) {
                if (!Python.isStarted()) Python.start(AndroidPlatform(context))
                Python.getInstance().getModule("yt_dlp_bridge").callAttr(function, *args)
            }
        } catch (error: Throwable) {
            throw IllegalStateException(sanitizeYtDlpError(error.message), error)
        }

    private fun validate(rawUrl: String): String {
        val validated = RemoteUrlValidator.validate(rawUrl)
        return (validated as? UrlValidation.Valid)?.uri?.toString()
            ?: throw IllegalArgumentException(
                (validated as? UrlValidation.Invalid)?.message ?: "La URL no es válida",
            )
    }

    private fun parse(raw: String): ParsedMedia {
        val json = JSONObject(raw)
        return ParsedMedia(
            id = json.optString("id"),
            title = json.optString("title").ifBlank { "Audio" },
            artist = json.optString("artist"),
            album = json.optString("album"),
            durationMs = json.optLong("durationMs"),
            thumbnailUrl = json.optString("thumbnail").takeIf(String::isNotBlank),
            webpageUrl = json.optString("webpageUrl"),
            extractor = json.optString("extractor"),
            extension = json.optString("extension").lowercase(),
            sizeBytes = json.optLong("sizeBytes", -1),
            downloadPath = json.optString("path").takeIf(String::isNotBlank),
        )
    }

    private fun parsePreviewHeaders(json: JSONObject?): Map<String, String> {
        if (json == null) return emptyMap()
        val allowed = setOf("user-agent", "referer", "origin", "accept", "accept-language")
        return buildMap {
            val keys = json.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                if (key.lowercase() !in allowed) continue
                val value = json.optString(key).trim()
                if (value.isNotBlank() && value.length <= MAX_PREVIEW_HEADER_VALUE_LENGTH) {
                    put(key, value)
                }
            }
        }
    }

    private data class ParsedMedia(
        val id: String,
        val title: String,
        val artist: String,
        val album: String,
        val durationMs: Long,
        val thumbnailUrl: String?,
        val webpageUrl: String,
        val extractor: String,
        val extension: String,
        val sizeBytes: Long,
        val downloadPath: String?,
    ) {
        fun withoutDownloadPath() = YtDlpMediaInfo(
            id,
            title,
            artist,
            album,
            durationMs,
            thumbnailUrl,
            webpageUrl,
            extractor,
            extension,
            sizeBytes,
        )
    }

    companion object {
        private val PYTHON_LOCK = Any()
        private const val MAX_PREVIEW_HEADER_VALUE_LENGTH = 512
    }
}

internal fun sanitizeYtDlpError(message: String?): String {
    val original = message.orEmpty()
    if (
        original.contains("sign in to confirm", ignoreCase = true) ||
        original.contains("not a bot", ignoreCase = true) ||
        original.contains("LOGIN_REQUIRED", ignoreCase = true) ||
        original.contains("failed to extract any player response", ignoreCase = true) ||
        original.contains("all player responses are invalid", ignoreCase = true) ||
        original.contains("your ip is likely being blocked", ignoreCase = true) ||
        original.contains("the page needs to be reloaded", ignoreCase = true)
    ) {
        return "YouTube rechazó temporalmente la solicitud desde esta red durante una " +
            "verificación anti-bot. Intenta nuevamente más tarde o usa otro contenido permitido."
    }
    if (original.contains("requested format is not available", ignoreCase = true)) {
        return "YouTube no ofreció una pista de audio compatible para este contenido."
    }
    if (
        original.contains("video unavailable", ignoreCase = true) &&
        (original.contains("restricted", ignoreCase = true) ||
            original.contains("administrator", ignoreCase = true))
    ) {
        return "YouTube restringió este contenido para el reproductor actual. Prueba otro video permitido."
    }
    if (original.contains("drm protected", ignoreCase = true)) {
        return "YouTube no ofreció un formato de audio descargable para este contenido."
    }
    val clean = original
        .replace(Regex("""https?://\S+""", RegexOption.IGNORE_CASE), "enlace remoto")
        .lineSequence()
        .lastOrNull { it.isNotBlank() }
        ?.trim()
        .orEmpty()
        .take(240)
    return clean.ifBlank { "yt-dlp no pudo procesar este enlace" }
}
