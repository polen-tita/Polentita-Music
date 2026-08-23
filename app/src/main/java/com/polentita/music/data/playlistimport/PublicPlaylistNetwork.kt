package com.polentita.music.data.playlistimport

import java.io.IOException
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.CookieJar
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener

internal const val PUBLIC_PLAYLIST_UNAVAILABLE_MESSAGE =
    "Este proveedor no permite leer esta playlist públicamente. " +
        "Impórtala mediante JSON, CSV o TXT"

// Identificador público del cliente web de TIDAL; no representa una cuenta ni una sesión.
internal const val TIDAL_PUBLIC_WEB_CLIENT_ID = "0jTR49MPo79CqbDQ"

internal fun tidalArtworkUrl(value: String?, size: Int = 640): String? {
    require(size in 80..1280) { "Tamaño de portada TIDAL no compatible" }
    val clean = value.orEmpty().trim()
    if (clean.isBlank()) return null
    if (clean.startsWith("https://", true)) return clean
    val parts = clean.replace("-", "")
    if (parts.length != 32) return null
    return "https://resources.tidal.com/images/" +
        "${parts.substring(0, 8)}/${parts.substring(8, 12)}/" +
        "${parts.substring(12, 16)}/${parts.substring(16, 20)}/" +
        "${parts.substring(20)}/${size}x$size.jpg"
}

internal class PublicPlaylistUnavailableException :
    IllegalStateException(PUBLIC_PLAYLIST_UNAVAILABLE_MESSAGE)

internal class PublicPlaylistHttpException(
    val statusCode: Int,
) : IOException("HTTP $statusCode")

@Singleton
class PublicPlaylistHttpClient @Inject constructor(
    private val baseClient: OkHttpClient,
) {
    suspend fun get(
        url: String,
        headers: Map<String, String> = emptyMap(),
    ): String = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(url)
            .get()
            .header("Accept", "text/html,application/json;q=0.9")
            .header("User-Agent", "PolentitaMusic/playlist-import")
            .apply {
                headers.forEach { (name, value) -> header(name, value) }
            }
            .build()
        baseClient.newBuilder()
            .cookieJar(CookieJar.NO_COOKIES)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()
            .newCall(request)
            .execute()
            .use { response ->
                if (!response.isSuccessful) throw PublicPlaylistHttpException(response.code)
                val body = response.body ?: throw IOException("Respuesta pública vacía")
                if (body.contentLength() > MAX_RESPONSE_BYTES) {
                    throw IOException("La respuesta pública supera el límite permitido")
                }
                body.string().also { content ->
                    if (content.toByteArray(Charsets.UTF_8).size > MAX_RESPONSE_BYTES) {
                        throw IOException("La respuesta pública supera el límite permitido")
                    }
                }
            }
    }

    private companion object {
        const val MAX_RESPONSE_BYTES = 10 * 1024 * 1024L
    }
}

internal data class PublicCollectionPageMetadata(
    val name: String? = null,
    val description: String? = null,
    val artworkUrl: String? = null,
    val totalTracks: Int? = null,
)

internal object PublicPlaylistPageParser {
    fun metadata(html: String): PublicCollectionPageMetadata {
        var result = PublicCollectionPageMetadata(
            name = metaValue(html, "og:title") ?: metaValue(html, "twitter:title"),
            description = metaValue(html, "og:description") ?: metaValue(html, "twitter:description"),
            artworkUrl = metaValue(html, "og:image") ?: metaValue(html, "twitter:image"),
            totalTracks = null,
        )
        val jsonLdScripts = scriptContents(html, "application/ld+json")
        jsonLdScripts.forEach { script ->
            runCatching { JSONTokener(script).nextValue() }
                .getOrNull()
                ?.let { root ->
                    walkObjects(root) { objectValue ->
                        if (!isCollectionObject(objectValue)) return@walkObjects
                        result = result.merge(
                            PublicCollectionPageMetadata(
                                name = objectValue.optString("name").trim().takeIf(String::isNotBlank),
                                description = objectValue.optString("description")
                                    .trim()
                                    .takeIf(String::isNotBlank),
                                artworkUrl = imageUrl(objectValue.opt("image")),
                                totalTracks = collectionCount(objectValue),
                            ),
                        )
                    }
                }
        }
        return result.copy(
            totalTracks = result.totalTracks ?: parseCount(result.description),
            artworkUrl = result.artworkUrl?.takeIf(::isHttpsUrl),
        )
    }

    fun initialState(html: String): JSONObject? =
        scriptContentsById(html, "initialState")
            .firstOrNull()
            ?.let { encoded ->
                runCatching {
                    val decoded = java.util.Base64.getDecoder().decode(encoded.trim())
                    JSONTokener(decoded.toString(Charsets.UTF_8)).nextValue() as? JSONObject
                }.getOrNull()
            }

    private fun metaValue(html: String, wanted: String): String? {
        val tags = Regex("<meta\\b[^>]*>", RegexOption.IGNORE_CASE)
        val attributes = Regex(
            "([a-zA-Z_:][-a-zA-Z0-9_:]*)\\s*=\\s*(\\\"[^\\\"]*\\\"|'[^']*')",
        )
        return tags.findAll(html).mapNotNull { match ->
            val values = attributes.findAll(match.value).associate { attribute ->
                attribute.groupValues[1].lowercase(Locale.ROOT) to
                    decodeHtmlAttribute(attribute.groupValues[2].drop(1).dropLast(1))
            }
            val key = values["property"] ?: values["name"]
            if (key.equals(wanted, ignoreCase = true)) values["content"] else null
        }.firstOrNull { it.isNotBlank() }
    }

    private fun scriptContents(html: String, type: String): List<String> {
        val scripts = Regex(
            "<script\\b[^>]*>.*?</script>",
            setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
        )
        val attributes = Regex(
            "([a-zA-Z_:][-a-zA-Z0-9_:]*)\\s*=\\s*(\\\"[^\\\"]*\\\"|'[^']*')",
        )
        return scripts.findAll(html).mapNotNull { match ->
            val openingTag = match.value.substringBefore('>')
            val values = attributes.findAll(openingTag).associate { attribute ->
                attribute.groupValues[1].lowercase(Locale.ROOT) to
                    attribute.groupValues[2].drop(1).dropLast(1)
            }
            if (values["type"].equals(type, ignoreCase = true)) {
                match.value.substringAfter('>').substringBeforeLast("</script>")
            } else {
                null
            }
        }.toList()
    }

    private fun scriptContentsById(html: String, id: String): List<String> {
        val scripts = Regex(
            "<script\\b[^>]*>.*?</script>",
            setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
        )
        val attributes = Regex(
            "([a-zA-Z_:][-a-zA-Z0-9_:]*)\\s*=\\s*(\\\"[^\\\"]*\\\"|'[^']*')",
        )
        return scripts.findAll(html).mapNotNull { match ->
            val openingTag = match.value.substringBefore('>')
            val values = attributes.findAll(openingTag).associate { attribute ->
                attribute.groupValues[1].lowercase(Locale.ROOT) to
                    attribute.groupValues[2].drop(1).dropLast(1)
            }
            if (values["id"].equals(id, ignoreCase = true)) {
                match.value.substringAfter('>').substringBeforeLast("</script>")
            } else {
                null
            }
        }.toList()
    }

    private fun walkObjects(value: Any?, visit: (JSONObject) -> Unit) {
        when (value) {
            is JSONObject -> {
                visit(value)
                val keys = value.keys()
                while (keys.hasNext()) walkObjects(value.opt(keys.next()), visit)
            }
            is JSONArray -> repeat(value.length()) { index -> walkObjects(value.opt(index), visit) }
        }
    }

    private fun isCollectionObject(value: JSONObject): Boolean {
        val type = value.optString("@type").lowercase(Locale.ROOT)
        return type.contains("playlist") || type.contains("album") || type == "musicgroup"
    }

    private fun collectionCount(value: JSONObject): Int? = listOf(
        "numTracks",
        "numberOfTracks",
        "trackCount",
        "numberOfItems",
        "numItems",
    ).firstNotNullOfOrNull { key -> value.optInt(key).takeIf { it > 0 } }

    private fun parseCount(description: String?): Int? = description
        ?.let { Regex("(?i)(\\d+)\\s+(?:items|canciones|tracks)").find(it)?.groupValues?.get(1) }
        ?.toIntOrNull()
        ?.takeIf { it > 0 }

    private fun imageUrl(value: Any?): String? = when (value) {
        is String -> value.takeIf(::isHttpsUrl)
        is JSONArray -> (0 until value.length()).asSequence()
            .mapNotNull { imageUrl(value.opt(it)) }
            .firstOrNull()
        is JSONObject -> imageUrl(value.opt("url"))
        else -> null
    }

    private fun isHttpsUrl(value: String): Boolean =
        value.startsWith("https://", ignoreCase = true)

    private fun decodeHtmlAttribute(value: String): String = value
        .replace(HTML_NUMERIC_ENTITY) { match ->
            val raw = match.groupValues[1]
            val radix = if (raw.startsWith("x", ignoreCase = true)) 16 else 10
            val digits = raw.removePrefix("x").removePrefix("X")
            digits.toIntOrNull(radix)
                ?.takeIf { it in 0..Character.MAX_CODE_POINT }
                ?.let(Character::toChars)
                ?.concatToString()
                ?: match.value
        }
        .replace("&quot;", "\"", ignoreCase = true)
        .replace("&apos;", "'", ignoreCase = true)
        .replace("&amp;", "&", ignoreCase = true)
        .replace("&lt;", "<", ignoreCase = true)
        .replace("&gt;", ">", ignoreCase = true)
        .trim()

    private val HTML_NUMERIC_ENTITY = Regex("&#(x[0-9a-f]+|[0-9]+);", RegexOption.IGNORE_CASE)

    private fun PublicCollectionPageMetadata.merge(
        other: PublicCollectionPageMetadata,
    ) = PublicCollectionPageMetadata(
        name = name ?: other.name,
        description = description ?: other.description,
        artworkUrl = artworkUrl ?: other.artworkUrl,
        totalTracks = totalTracks ?: other.totalTracks,
    )
}
