package com.polentita.music.data.artwork

import com.polentita.music.core.common.RemoteUrlValidator
import com.polentita.music.core.common.UrlValidation
import com.polentita.music.core.network.NetworkAccessBlockedException
import com.polentita.music.core.network.NetworkAccessPolicy
import com.polentita.music.core.network.NetworkBlockReason
import com.polentita.music.data.playlistimport.PublicPlaylistHttpClient
import com.polentita.music.data.playlistimport.TIDAL_PUBLIC_WEB_CLIENT_ID
import com.polentita.music.data.playlistimport.tidalArtworkUrl
import com.polentita.music.data.provider.AuthorizedProviderRegistry
import com.polentita.music.domain.artwork.ArtworkCandidate
import com.polentita.music.domain.artwork.ArtworkSearchRepository
import com.polentita.music.domain.artwork.ArtworkSearchRequest
import com.polentita.music.domain.artwork.ArtworkSearchResult
import com.polentita.music.domain.artwork.ArtworkSource
import java.text.Normalizer
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.json.JSONArray
import org.json.JSONObject

internal const val INTERNET_FETCH_SIZE = 24

internal interface ArtworkCandidateSource {
    val reportedSources: Set<ArtworkSource>
    suspend fun search(request: ArtworkSearchRequest): List<ArtworkCandidate>
}

@Singleton
class DefaultArtworkSearchRepository @Inject constructor(
    private val musicBrainz: MusicBrainzArtworkSource,
    private val tidal: TidalArtworkSource,
    private val youtube: YouTubeArtworkSource,
    private val internet: InternetArtworkSource,
    private val networkAccessPolicy: NetworkAccessPolicy,
) : ArtworkSearchRepository {
    override suspend fun search(request: ArtworkSearchRequest): ArtworkSearchResult = coroutineScope {
        require(request.query.isNotBlank()) { "Escribe un álbum o una canción para buscar" }
        val access = networkAccessPolicy.current()
        if (!access.remoteSearchAllowed) {
            throw NetworkAccessBlockedException(
                access.remoteBlockReason ?: NetworkBlockReason.NO_CONNECTION,
            )
        }
        val sources = if (request.page == 0) {
            listOf<ArtworkCandidateSource>(internet, musicBrainz, tidal, youtube)
        } else {
            listOf<ArtworkCandidateSource>(internet)
        }
        val attempts = sources.map { source ->
            async { source to runCatchingCancellable { source.search(request) } }
        }.map { it.await() }
        val errors = buildMap {
            attempts.forEach { (source, result) ->
                result.exceptionOrNull()?.let { error ->
                    source.reportedSources.forEach { artworkSource ->
                        put(artworkSource, error.message ?: "No se pudo consultar esta fuente")
                    }
                }
            }
        }
        val candidates = attempts
            .flatMap { (_, result) -> result.getOrDefault(emptyList()) }
            .filter { isSafeHttps(it.imageUrl) }
            .groupBy(ArtworkCandidate::source)
            .flatMap { (source, sourceCandidates) ->
                sourceCandidates
                    .distinctBy { it.imageUrl.substringBefore('?') }
                    .sortedWith(
                        compareByDescending<ArtworkCandidate> { it.score }
                            .thenByDescending { minOf(it.width ?: 0, it.height ?: 0) },
                    )
                    .take(
                        if (source == ArtworkSource.INTERNET) {
                            INTERNET_RESULTS_PER_PAGE
                        } else {
                            MAX_RESULTS_PER_SOURCE
                        },
                    )
            }
        val internetResult = attempts
            .firstOrNull { (source, _) -> source === internet }
            ?.second
            ?.getOrNull()
        ArtworkSearchResult(
            candidates = candidates,
            sourceErrors = errors,
            // DuckDuckGo can return a valid, smaller page (especially on mobile or for
            // restrictive queries) even though the offset can still be advanced.
            hasMore = request.page < MAX_INTERNET_PAGES &&
                internetResult?.isNotEmpty() == true,
        )
    }

    private companion object {
        const val MAX_RESULTS_PER_SOURCE = 6
        const val MAX_INTERNET_PAGES = 8
        const val INTERNET_RESULTS_PER_PAGE = 12
    }
}

@Singleton
class MusicBrainzArtworkSource @Inject constructor(
    private val http: PublicPlaylistHttpClient,
    private val requestGate: MusicBrainzRequestGate,
) : ArtworkCandidateSource {
    override val reportedSources = setOf(ArtworkSource.SPOTIFY)

    override suspend fun search(request: ArtworkSearchRequest): List<ArtworkCandidate> = coroutineScope {
        val groups = searchReleaseGroups(request)
        if (groups.isEmpty()) return@coroutineScope emptyList()
        spotifyCandidates(groups.first(), request)
    }

    private suspend fun searchReleaseGroups(request: ArtworkSearchRequest): List<ReleaseGroup> {
        val withArtist = queryReleaseGroups(request.query, request.artist)
        if (withArtist.isNotEmpty() || request.artist.isBlank()) return withArtist

        // MusicBrainz can credit a soundtrack, collective or featured artist differently from
        // the metadata written in a local file. A title-only retry keeps Spotify relations useful
        // in that case.
        return queryReleaseGroups(request.query, artist = "")
    }

    private suspend fun queryReleaseGroups(query: String, artist: String): List<ReleaseGroup> {
        val searchTerms = buildString {
            append("releasegroup:\"")
            append(query.trim().replace("\"", ""))
            append('"')
            if (artist.isNotBlank()) {
                append(" AND artist:\"")
                append(artist.trim().replace("\"", ""))
                append('"')
            }
        }
        val url = MUSIC_BRAINZ_RELEASE_GROUP_SEARCH.toHttpUrl().newBuilder()
            .addQueryParameter("query", searchTerms)
            .addQueryParameter("fmt", "json")
            .addQueryParameter("limit", "6")
            .build()
        val root = JSONObject(
            requestGate.request { http.get(url.toString(), MUSIC_BRAINZ_HEADERS) },
        )
        return root.optJSONArray("release-groups").objects().mapNotNull { group ->
            val id = group.optString("id").trim()
            val title = group.optString("title").trim()
            if (!MUSIC_BRAINZ_ID.matches(id) || title.isBlank()) return@mapNotNull null
            ReleaseGroup(
                id = id,
                title = title,
                artist = group.artistCredit(),
                externalUrl = "https://musicbrainz.org/release-group/$id",
            )
        }
    }

    private suspend fun spotifyCandidates(
        group: ReleaseGroup,
        request: ArtworkSearchRequest,
    ): List<ArtworkCandidate> {
        val browseUrl = MUSIC_BRAINZ_RELEASE_BROWSE.toHttpUrl().newBuilder()
            .addQueryParameter("release-group", group.id)
            .addQueryParameter("inc", "url-rels")
            .addQueryParameter("fmt", "json")
            .addQueryParameter("limit", "25")
            .build()
        val releases = runCatchingCancellable {
            JSONObject(
                requestGate.request { http.get(browseUrl.toString(), MUSIC_BRAINZ_HEADERS) },
            )
                .optJSONArray("releases")
                .objects()
        }.getOrDefault(emptyList())
        val spotifyUrls = releases
            .flatMap { release -> release.optJSONArray("relations").objects() }
            .mapNotNull { relation -> relation.optJSONObject("url")?.optString("resource") }
            .filter(::isSpotifyAlbumUrl)
            .distinct()
            .take(6)
        return spotifyUrls.mapNotNull { spotifyUrl ->
            runCatchingCancellable {
                val oEmbedUrl = SPOTIFY_OEMBED.toHttpUrl().newBuilder()
                    .addQueryParameter("url", spotifyUrl)
                    .build()
                val metadata = JSONObject(http.get(oEmbedUrl.toString()))
                val imageUrl = metadata.optString("thumbnail_url")
                    .takeIf { it.startsWith("https://", true) }
                    ?: return@runCatchingCancellable null
                ArtworkCandidate(
                    id = "spotify:${spotifyUrl.substringAfterLast('/')}",
                    source = ArtworkSource.SPOTIFY,
                    title = metadata.optString("title").trim().ifBlank { group.title },
                    artist = group.artist,
                    imageUrl = imageUrl,
                    externalUrl = spotifyUrl,
                    width = metadata.optInt("thumbnail_width").takeIf { it > 0 },
                    height = metadata.optInt("thumbnail_height").takeIf { it > 0 },
                    score = artworkScore(
                        request,
                        group.title,
                        group.artist,
                        metadata.optInt("thumbnail_width"),
                        metadata.optInt("thumbnail_height"),
                    ),
                )
            }.getOrNull()
        }
    }

    private data class ReleaseGroup(
        val id: String,
        val title: String,
        val artist: String,
        val externalUrl: String,
    )

    private companion object {
        const val MUSIC_BRAINZ_RELEASE_GROUP_SEARCH = "https://musicbrainz.org/ws/2/release-group/"
        const val MUSIC_BRAINZ_RELEASE_BROWSE = "https://musicbrainz.org/ws/2/release/"
        const val SPOTIFY_OEMBED = "https://open.spotify.com/oembed"
        val MUSIC_BRAINZ_HEADERS = mapOf(
            "Accept" to "application/json",
            "User-Agent" to
                "PolentitaMusic/1.0.0 (https://github.com/polen-tita/Polentita-Music)",
        )
        val MUSIC_BRAINZ_ID = Regex(
            "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$",
        )
    }
}

@Singleton
class MusicBrainzRequestGate @Inject constructor() {
    private val mutex = Mutex()
    private var lastRequestStartedAtNanos = 0L

    suspend fun <T> request(block: suspend () -> T): T = mutex.withLock {
        val now = System.nanoTime()
        val elapsedMillis = (now - lastRequestStartedAtNanos).coerceAtLeast(0L) / 1_000_000L
        if (lastRequestStartedAtNanos != 0L && elapsedMillis < REQUEST_INTERVAL_MS) {
            delay(REQUEST_INTERVAL_MS - elapsedMillis)
        }
        lastRequestStartedAtNanos = System.nanoTime()
        block()
    }

    private companion object {
        const val REQUEST_INTERVAL_MS = 1_100L
    }
}

@Singleton
class InternetArtworkSource @Inject constructor(
    private val http: PublicPlaylistHttpClient,
) : ArtworkCandidateSource {
    override val reportedSources = setOf(ArtworkSource.INTERNET)

    override suspend fun search(request: ArtworkSearchRequest): List<ArtworkCandidate> {
        val query = buildInternetQuery(request)
        val token = extractVqd(
            http.get(
                DDG_IMAGES_PAGE.toHttpUrl().newBuilder()
                    .addQueryParameter("q", query)
                    .addQueryParameter("iax", "images")
                    .addQueryParameter("ia", "images")
                    .build()
                    .toString(),
                headers = DDG_PAGE_HEADERS,
            ),
        ) ?: error("El buscador de imágenes no respondió correctamente")
        val endpoint = DDG_IMAGES_API.toHttpUrl().newBuilder()
            .addQueryParameter("l", "us-en")
            .addQueryParameter("o", "json")
            .addQueryParameter("q", query)
            .addQueryParameter("vqd", token)
            .addQueryParameter("f", ",,,")
            .addQueryParameter("p", "1")
            .addQueryParameter("s", (request.page * INTERNET_FETCH_SIZE).toString())
            .build()
        val root = JSONObject(http.get(endpoint.toString(), headers = DDG_API_HEADERS))
        return root.optJSONArray("results").objects().mapNotNull { result ->
            internetCandidate(result, request)
        }
    }

    private fun internetCandidate(
        result: JSONObject,
        request: ArtworkSearchRequest,
    ): ArtworkCandidate? {
        val fullImageUrl = result.optString("image").trim()
        val thumbnailUrl = result.optString("thumbnail").trim()
        val imageUrl = sequenceOf(fullImageUrl, thumbnailUrl)
            .mapNotNull { it.takeIf(::isSafeHttps) }
            .firstOrNull()
            ?: return null
        val previewUrl = thumbnailUrl
            .takeIf(::isSafeHttps)
            ?.takeUnless { it == imageUrl }
        val pageUrl = result.optString("url").trim().takeIf(::isSafeHttps)
        val title = result.optString("title")
            .stripMarkup()
            .ifBlank { request.query.trim() }
        val source = result.optString("source")
            .stripMarkup()
            .ifBlank { pageUrl?.toHttpUrlOrNull()?.host.orEmpty() }
        val width = result.optInt("width").takeIf { it > 0 }
            ?: result.optInt("thumbnail_width").takeIf { it > 0 }
        val height = result.optInt("height").takeIf { it > 0 }
            ?: result.optInt("thumbnail_height").takeIf { it > 0 }
        return ArtworkCandidate(
            id = "internet:${imageUrl.substringBefore('?').hashCode()}",
            source = ArtworkSource.INTERNET,
            title = title,
            artist = request.artist.trim().ifBlank { source },
            imageUrl = imageUrl,
            previewUrl = previewUrl,
            externalUrl = pageUrl,
            width = width,
            height = height,
            score = artworkScore(request, title, request.artist, width, height),
        )
    }

    private fun buildInternetQuery(request: ArtworkSearchRequest): String = listOf(
        request.query.trim(),
        request.artist.trim(),
        "album cover",
    ).filter(String::isNotBlank).joinToString(" ")

    private companion object {
        const val DDG_IMAGES_PAGE = "https://duckduckgo.com/"
        const val DDG_IMAGES_API = "https://duckduckgo.com/i.js"
        val DDG_PAGE_HEADERS = mapOf(
            "Accept" to "text/html,application/xhtml+xml",
            "User-Agent" to "PolentitaMusic/artwork-search",
        )
        val DDG_API_HEADERS = mapOf(
            "Accept" to "application/json,text/javascript,*/*;q=0.01",
            "Referer" to "https://duckduckgo.com/",
            "User-Agent" to "PolentitaMusic/artwork-search",
        )
        val VQD_REGEX = Regex(
            "(?i)(?:[\\\"']vqd[\\\"']?\\s*:\\s*|vqd\\s*=\\s*[\\\"']?)([A-Za-z0-9._-]+)",
        )

        fun extractVqd(html: String): String? = VQD_REGEX.find(html)?.groupValues?.get(1)
    }
}

@Singleton
class TidalArtworkSource @Inject constructor(
    private val http: PublicPlaylistHttpClient,
) : ArtworkCandidateSource {
    override val reportedSources = setOf(ArtworkSource.TIDAL)

    override suspend fun search(request: ArtworkSearchRequest): List<ArtworkCandidate> {
        val url = TIDAL_ALBUM_SEARCH.toHttpUrl().newBuilder()
            .addQueryParameter("query", request.fullQuery)
            .addQueryParameter("countryCode", "AR")
            .addQueryParameter("limit", "6")
            .addQueryParameter("offset", "0")
            .build()
        val root = JSONObject(
            http.get(
                url.toString(),
                headers = mapOf("x-tidal-token" to TIDAL_PUBLIC_WEB_CLIENT_ID),
            ),
        )
        val items = root.optJSONArray("items")
            ?: root.optJSONObject("albums")?.optJSONArray("items")
        return items.objects().mapNotNull { album ->
            val id = album.optString("id").ifBlank { album.optString("uuid") }
            val title = album.optString("title").trim()
            val artist = album.optJSONObject("artist")?.optString("name")?.trim().orEmpty()
            val artworkValue = album.optString("cover").ifBlank { album.optString("squareImage") }
            val imageUrl = tidalArtworkUrl(
                artworkValue,
                size = 1280,
            ) ?: return@mapNotNull null
            val knownSize = 1280.takeUnless { artworkValue.startsWith("https://", true) }
            ArtworkCandidate(
                id = "tidal:${id.ifBlank { imageUrl.hashCode().toString() }}",
                source = ArtworkSource.TIDAL,
                title = title,
                artist = artist,
                imageUrl = imageUrl,
                externalUrl = id.takeIf(String::isNotBlank)?.let { "https://tidal.com/album/$it" },
                width = knownSize,
                height = knownSize,
                score = artworkScore(request, title, artist, knownSize, knownSize),
            )
        }
    }

    private companion object {
        const val TIDAL_ALBUM_SEARCH = "https://api.tidal.com/v1/search/albums"
    }
}

@Singleton
class YouTubeArtworkSource @Inject constructor(
    private val registry: AuthorizedProviderRegistry,
) : ArtworkCandidateSource {
    override val reportedSources = setOf(ArtworkSource.YOUTUBE)

    override suspend fun search(request: ArtworkSearchRequest): List<ArtworkCandidate> {
        val provider = registry.defaultProvider() ?: error("YouTube no está disponible")
        return provider.search("${request.fullQuery} album cover").getOrThrow()
            .mapNotNull { track ->
                val imageUrl = track.coverUri?.takeIf { it.startsWith("https://", true) }
                    ?: return@mapNotNull null
                val title = track.album.name.ifBlank { track.title }
                ArtworkCandidate(
                    id = "youtube:${track.id.ifBlank { imageUrl.hashCode().toString() }}",
                    source = ArtworkSource.YOUTUBE,
                    title = title,
                    artist = track.artist.name,
                    imageUrl = imageUrl,
                    externalUrl = track.externalUrl,
                    score = artworkScore(request, title, track.artist.name, null, null),
                )
            }
    }
}

internal fun artworkScore(
    request: ArtworkSearchRequest,
    title: String,
    artist: String,
    width: Int?,
    height: Int?,
): Double {
    val wantedTitle = normalizeArtworkText(request.query)
    val candidateTitle = normalizeArtworkText(title)
    val wantedArtist = normalizeArtworkText(request.artist)
    val candidateArtist = normalizeArtworkText(artist)
    var score = when {
        wantedTitle.isNotBlank() && candidateTitle == wantedTitle -> 0.68
        wantedTitle.isNotBlank() && (candidateTitle.contains(wantedTitle) || wantedTitle.contains(candidateTitle)) -> 0.50
        else -> 0.24
    }
    if (wantedArtist.isNotBlank()) {
        score += when {
            candidateArtist == wantedArtist -> 0.22
            candidateArtist.contains(wantedArtist) || wantedArtist.contains(candidateArtist) -> 0.14
            else -> 0.0
        }
    }
    if (width != null && height != null && width > 0 && height > 0) {
        val squareRatio = minOf(width, height).toDouble() / maxOf(width, height)
        score += 0.05 * squareRatio
        score += when {
            minOf(width, height) >= 1000 -> 0.05
            minOf(width, height) >= 600 -> 0.035
            minOf(width, height) >= 300 -> 0.02
            else -> 0.0
        }
    }
    return score.coerceIn(0.0, 1.0)
}

internal fun normalizeArtworkText(value: String): String = Normalizer
    .normalize(value.lowercase(Locale.ROOT), Normalizer.Form.NFD)
    .replace("\\p{Mn}+".toRegex(), "")
    .replace("[^a-z0-9]+".toRegex(), " ")
    .trim()

private fun String.stripMarkup(): String = replace("<[^>]*>".toRegex(), "")
    .replace("&amp;", "&", ignoreCase = true)
    .replace("&quot;", "\"", ignoreCase = true)
    .replace("&#39;", "'", ignoreCase = true)
    .trim()

private fun JSONArray?.objects(): List<JSONObject> = if (this == null) {
    emptyList()
} else {
    (0 until length()).mapNotNull(::optJSONObject)
}

private fun JSONObject.artistCredit(): String = optJSONArray("artist-credit").objects()
    .mapNotNull { credit ->
        credit.optJSONObject("artist")?.optString("name")
            ?.trim()
            ?.takeIf(String::isNotBlank)
            ?: credit.optString("name").trim().takeIf(String::isNotBlank)
    }
    .distinct()
    .joinToString(", ")

private fun isSpotifyAlbumUrl(value: String): Boolean = runCatching {
    val url = value.toHttpUrl()
    url.isHttps && url.host == "open.spotify.com" && url.pathSegments.firstOrNull() == "album"
}.getOrDefault(false)

private fun isSafeHttps(value: String): Boolean =
    RemoteUrlValidator.validate(value) is UrlValidation.Valid

private inline fun <T> runCatchingCancellable(block: () -> T): Result<T> = try {
    Result.success(block())
} catch (cancelled: CancellationException) {
    throw cancelled
} catch (error: Throwable) {
    Result.failure(error)
}
