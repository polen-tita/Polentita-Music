package com.polentita.music.data.playlistimport

import com.polentita.music.domain.playlistimport.ImportedCollection
import com.polentita.music.domain.playlistimport.ImportedCollectionSource
import com.polentita.music.domain.playlistimport.ImportedCollectionType
import com.polentita.music.domain.playlistimport.ImportedSourceReference
import com.polentita.music.domain.playlistimport.ImportedTrack
import com.polentita.music.domain.playlistimport.PlaylistImportProvider
import com.polentita.music.domain.playlistimport.PlaylistImportRequest
import com.polentita.music.data.extractor.YtDlpExtractor
import com.polentita.music.data.extractor.YtDlpPlaylistInfo
import java.net.URI
import java.security.MessageDigest
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import okhttp3.HttpUrl.Companion.toHttpUrl

@Singleton
class FilePlaylistImportProvider @Inject constructor() : PlaylistImportProvider {
    override val source = ImportedCollectionSource.FILE
    override val displayName = "JSON, CSV o TXT"
    override val isConfigured = true
    override val configurationMessage: String? = null

    override fun supports(request: PlaylistImportRequest) = request is PlaylistImportRequest.FileContent

    override suspend fun analyze(request: PlaylistImportRequest): Result<ImportedCollection> =
        withContext(Dispatchers.Default) {
            runCatching {
                val file = request as? PlaylistImportRequest.FileContent
                    ?: error("Selecciona un archivo JSON, CSV o TXT")
                require(file.content.toByteArray().size <= MAX_FILE_BYTES) {
                    "El archivo supera el límite de 5 MB"
                }
                val extension = file.displayName.substringAfterLast('.', "").lowercase(Locale.ROOT)
                val parsed = when {
                    extension == "json" || file.mimeType?.contains("json", true) == true -> parseJson(file)
                    extension == "csv" || file.mimeType?.contains("csv", true) == true -> parseDelimited(file, ',')
                    extension == "txt" || file.mimeType?.startsWith("text/") == true -> parseText(file)
                    file.content.trimStart().startsWith("{") || file.content.trimStart().startsWith("[") -> parseJson(file)
                    else -> error("Formato no compatible. Usa JSON, CSV o TXT estructurado")
                }
                require(parsed.tracks.isNotEmpty()) { "El archivo no contiene canciones" }
                parsed.copy(
                    sourceId = parsed.sourceId.ifBlank { sha256(file.content) },
                    totalTracks = parsed.tracks.size,
                    tracks = parsed.tracks.withIndex()
                        .sortedWith(compareBy<IndexedValue<ImportedTrack>> { it.value.originalPosition }.thenBy { it.index })
                        .mapIndexed { index, indexed ->
                            val track = indexed.value
                        track.copy(
                            sourceId = track.sourceId.ifBlank {
                                sha256("${track.title}|${track.artists.joinToString()}|$index").take(24)
                            },
                            originalPosition = index,
                            )
                        },
                )
            }
        }

    private fun parseJson(file: PlaylistImportRequest.FileContent): ImportedCollection {
        val root = JSONTokener(file.content).nextValue()
        val collectionObject = root as? JSONObject
        val tracksArray = when (root) {
            is JSONArray -> root
            is JSONObject -> root.optJSONArray("tracks")
                ?: root.optJSONArray("items")
                ?: error("El JSON debe incluir un arreglo tracks")
            else -> error("El JSON no tiene una estructura válida")
        }
        val tracks = buildList {
            repeat(tracksArray.length()) { index ->
                val item = tracksArray.optJSONObject(index) ?: return@repeat
                val artists = when (val rawArtists = item.opt("artists")) {
                    is JSONArray -> buildList {
                        repeat(rawArtists.length()) { artistIndex ->
                            rawArtists.optString(artistIndex).trim().takeIf(String::isNotBlank)?.let(::add)
                        }
                    }
                    is String -> splitArtists(rawArtists)
                    else -> splitArtists(item.optString("artist"))
                }
                add(
                    ImportedTrack(
                        sourceId = item.optString("sourceId").ifBlank { item.optString("id") },
                        title = item.optString("title").trim(),
                        artists = artists,
                        album = item.optString("album").trim(),
                        durationMs = parseDuration(item.opt("durationMs") ?: item.opt("duration")),
                        trackNumber = item.optPositiveInt("trackNumber"),
                        discNumber = item.optPositiveInt("discNumber"),
                        isrc = item.optString("isrc").trim().uppercase(Locale.ROOT).takeIf(String::isNotBlank),
                        artworkUrl = item.optString("artworkUrl").trim().takeIf(String::isNotBlank),
                        originalPosition = item.optInt("originalPosition", index).coerceAtLeast(0),
                    ),
                )
            }
        }
        val fallbackName = file.displayName.substringBeforeLast('.').ifBlank { "Playlist importada" }
        return ImportedCollection(
            source = ImportedCollectionSource.FILE,
            sourceId = collectionObject?.optString("sourceId").orEmpty(),
            type = runCatching {
                ImportedCollectionType.valueOf(collectionObject?.optString("type").orEmpty().uppercase())
            }.getOrDefault(ImportedCollectionType.PLAYLIST),
            name = collectionObject?.optString("name")?.trim().orEmpty().ifBlank { fallbackName },
            description = collectionObject?.optString("description")?.trim()?.takeIf(String::isNotBlank),
            artworkUrl = collectionObject?.optString("artworkUrl")?.trim()?.takeIf(String::isNotBlank),
            owner = collectionObject?.optString("owner")?.trim()?.takeIf(String::isNotBlank),
            tracks = tracks,
        )
    }

    private fun parseDelimited(
        file: PlaylistImportRequest.FileContent,
        delimiter: Char,
    ): ImportedCollection {
        val rows = parseCsvRows(file.content, delimiter).filter { row -> row.any(String::isNotBlank) }
        require(rows.isNotEmpty()) { "El archivo está vacío" }
        val headers = rows.first().map { canonicalHeader(it) }
        require("title" in headers) { "El archivo debe incluir una columna title o título" }
        val tracks = rows.drop(1).mapIndexedNotNull { index, values ->
            val row = headers.mapIndexed { column, header -> header to values.getOrElse(column) { "" }.trim() }.toMap()
            val title = row["title"].orEmpty()
            if (title.isBlank() && row.values.all(String::isBlank)) return@mapIndexedNotNull null
            ImportedTrack(
                sourceId = row["sourceid"].orEmpty(),
                title = title,
                artists = splitArtists(row["artists"].orEmpty().ifBlank { row["artist"].orEmpty() }),
                album = row["album"].orEmpty(),
                durationMs = parseDuration(row["durationms"].orEmpty().ifBlank { row["duration"].orEmpty() }),
                trackNumber = row["tracknumber"].toPositiveIntOrNull(),
                discNumber = row["discnumber"].toPositiveIntOrNull(),
                isrc = row["isrc"]?.uppercase(Locale.ROOT)?.takeIf(String::isNotBlank),
                artworkUrl = row["artworkurl"]?.takeIf(String::isNotBlank),
                originalPosition = row["originalposition"].toPositiveIntOrNull()?.minus(1) ?: index,
            )
        }
        return ImportedCollection(
            source = ImportedCollectionSource.FILE,
            sourceId = sha256(file.content),
            name = file.displayName.substringBeforeLast('.').ifBlank { "Playlist importada" },
            tracks = tracks,
        )
    }

    private fun parseText(file: PlaylistImportRequest.FileContent): ImportedCollection {
        var name = file.displayName.substringBeforeLast('.').ifBlank { "Playlist importada" }
        val dataLines = file.content.lineSequence()
            .map(String::trim)
            .filter(String::isNotBlank)
            .filter { line ->
                if (line.startsWith("#")) {
                    val metadata = line.removePrefix("#").trim()
                    if (metadata.startsWith("name:", true)) name = metadata.substringAfter(':').trim().ifBlank { name }
                    false
                } else {
                    true
                }
            }
            .toList()
        require(dataLines.isNotEmpty()) { "El archivo está vacío" }
        val delimiter = if (dataLines.first().contains('\t')) '\t' else '|'
        val firstCells = dataLines.first().split(delimiter).map(::canonicalHeader)
        val hasHeader = "title" in firstCells
        val headers = if (hasHeader) firstCells else listOf("title", "artist", "album", "duration")
        val tracks = dataLines.drop(if (hasHeader) 1 else 0).mapIndexedNotNull { index, line ->
            val values = line.split(delimiter).map(String::trim)
            val row = headers.mapIndexed { column, header -> header to values.getOrElse(column) { "" } }.toMap()
            val title = row["title"].orEmpty()
            if (title.isBlank()) return@mapIndexedNotNull null
            ImportedTrack(
                sourceId = row["sourceid"].orEmpty(),
                title = title,
                artists = splitArtists(row["artists"].orEmpty().ifBlank { row["artist"].orEmpty() }),
                album = row["album"].orEmpty(),
                durationMs = parseDuration(row["durationms"].orEmpty().ifBlank { row["duration"].orEmpty() }),
                trackNumber = row["tracknumber"].toPositiveIntOrNull(),
                discNumber = row["discnumber"].toPositiveIntOrNull(),
                isrc = row["isrc"]?.uppercase(Locale.ROOT)?.takeIf(String::isNotBlank),
                artworkUrl = row["artworkurl"]?.takeIf(String::isNotBlank),
                originalPosition = row["originalposition"].toPositiveIntOrNull()?.minus(1) ?: index,
            )
        }
        return ImportedCollection(
            source = ImportedCollectionSource.FILE,
            sourceId = sha256(file.content),
            name = name,
            tracks = tracks,
        )
    }

    private fun parseCsvRows(content: String, delimiter: Char): List<List<String>> {
        val rows = mutableListOf<List<String>>()
        val row = mutableListOf<String>()
        val cell = StringBuilder()
        var quoted = false
        var index = 0
        while (index < content.length) {
            val character = content[index]
            when {
                character == '"' && quoted && content.getOrNull(index + 1) == '"' -> {
                    cell.append('"')
                    index++
                }
                character == '"' -> quoted = !quoted
                character == delimiter && !quoted -> {
                    row += cell.toString()
                    cell.clear()
                }
                (character == '\n' || character == '\r') && !quoted -> {
                    if (character == '\r' && content.getOrNull(index + 1) == '\n') index++
                    row += cell.toString()
                    cell.clear()
                    rows += row.toList()
                    row.clear()
                }
                else -> cell.append(character)
            }
            index++
        }
        if (cell.isNotEmpty() || row.isNotEmpty()) {
            row += cell.toString()
            rows += row.toList()
        }
        require(!quoted) { "El CSV contiene comillas sin cerrar" }
        return rows
    }

    private fun parseDuration(raw: Any?): Long = when (raw) {
        is Number -> raw.toLong().coerceAtLeast(0)
        is String -> {
            val clean = raw.trim()
            when {
                clean.isBlank() -> 0
                ':' in clean -> clean.split(':').mapNotNull(String::toLongOrNull)
                    .fold(0L) { total, part -> total * 60 + part } * 1_000
                else -> clean.toLongOrNull()?.coerceAtLeast(0) ?: 0
            }
        }
        else -> 0
    }

    private fun splitArtists(value: String): List<String> = value
        .split(Regex("\\s*(?:;|&|,|\\bfeat\\.?\\b|\\bft\\.?\\b)\\s*", RegexOption.IGNORE_CASE))
        .map(String::trim)
        .filter(String::isNotBlank)
        .distinctBy { it.lowercase(Locale.ROOT) }

    private fun canonicalHeader(value: String): String = value.trim()
        .lowercase(Locale.ROOT)
        .replace("í", "i")
        .replace("ó", "o")
        .replace("_", "")
        .replace(" ", "")
        .let {
            when (it) {
                "titulo" -> "title"
                "artista" -> "artist"
                "artistas" -> "artists"
                "album" -> "album"
                "duracion" -> "duration"
                "pista" -> "tracknumber"
                "disco" -> "discnumber"
                else -> it
            }
        }

    private fun JSONObject.optPositiveInt(name: String): Int? = optInt(name).takeIf { it > 0 }
    private fun String?.toPositiveIntOrNull(): Int? = this?.toIntOrNull()?.takeIf { it > 0 }

    companion object {
        const val MAX_FILE_BYTES = 5 * 1024 * 1024

        internal fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray())
            .joinToString("") { "%02x".format(it) }
    }
}

@Singleton
class SpotifyPlaylistImportProvider @Inject constructor(
    private val http: PublicPlaylistHttpClient,
) : PlaylistImportProvider {
    override val source = ImportedCollectionSource.SPOTIFY
    override val displayName = "Spotify"
    override val isConfigured = true
    override val configurationMessage: String? = null

    override fun supports(request: PlaylistImportRequest) =
        (request as? PlaylistImportRequest.Url)?.value?.let(::extractSourceReference) != null

    override suspend fun analyze(request: PlaylistImportRequest): Result<ImportedCollection> =
        withContext(Dispatchers.IO) {
            runCatching {
                val url = (request as? PlaylistImportRequest.Url)?.value
                    ?: error("Pega un enlace público de Spotify")
                val reference = extractSourceReference(url)
                    ?: error("El enlace de Spotify no corresponde a una playlist o álbum")
                val firstPage = loadPage(reference, offset = null)
                val tracks = firstPage.tracks.toMutableList()
                val visitedOffsets = mutableSetOf<Int>()
                var nextOffset = firstPage.nextOffset
                while (nextOffset != null && tracks.size < firstPage.totalTracks) {
                    if (!visitedOffsets.add(nextOffset)) break
                    val page = try {
                        loadPage(reference, nextOffset)
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (_: PublicPlaylistUnavailableException) {
                        // Spotify can expose the first public window but deny later windows.
                        // Keep the verifiable tracks instead of rejecting the whole import.
                        break
                    }
                    val tracksBeforePage = tracks.asSequence()
                        .map(ImportedTrack::sourceId)
                        .toSet()
                    appendPage(tracks, page.tracks, nextOffset)
                    val tracksAfterPage = tracks.asSequence()
                        .map(ImportedTrack::sourceId)
                        .toSet()
                    nextOffset = page.nextOffset
                    if (page.tracks.isEmpty()) break
                    if (tracksAfterPage.size <= tracksBeforePage.size) {
                        // Spotify's public HTML sometimes returns the first window again for
                        // every offset. Keep the first verified window without duplicating it.
                        break
                    }
                }
                val orderedTracks = tracks.distinctBy(ImportedTrack::sourceId)
                if (orderedTracks.isEmpty()) throw PublicPlaylistUnavailableException()
                ImportedCollection(
                    source = source,
                    sourceId = reference.sourceId,
                    sourceUrl = reference.canonicalUrl,
                    type = reference.type,
                    name = firstPage.name.ifBlank { "Colección de Spotify" },
                    description = firstPage.description,
                    artworkUrl = firstPage.artworkUrl,
                    owner = firstPage.owner,
                    tracks = orderedTracks.mapIndexed { index, track ->
                        track.copy(originalPosition = index)
                    },
                    totalTracks = firstPage.totalTracks.coerceAtLeast(orderedTracks.size),
                )
            }
        }

    companion object {
        private const val SPOTIFY_PAGE_SIZE = 100

        fun extractSourceReference(raw: String): ImportedSourceReference? = linkReference(
            raw = raw,
            source = ImportedCollectionSource.SPOTIFY,
            hosts = setOf("open.spotify.com"),
            acceptedTypes = setOf("playlist", "album"),
        )
    }

    private suspend fun loadPage(
        reference: ImportedSourceReference,
        offset: Int?,
    ): SpotifyPage {
        val pageUrl = reference.canonicalUrl.toHttpUrl().newBuilder().apply {
            offset?.let {
                addQueryParameter("offset", it.toString())
                addQueryParameter("limit", SPOTIFY_PAGE_SIZE.toString())
            }
        }.build().toString()
        val html = try {
            http.get(pageUrl)
        } catch (_: Throwable) {
            throw PublicPlaylistUnavailableException()
        }
        val state = PublicPlaylistPageParser.initialState(html)
            ?: throw PublicPlaylistUnavailableException()
        val entity = findEntity(state, reference)
            ?: throw PublicPlaylistUnavailableException()
        return parsePage(entity, reference, html)
    }

    private fun findEntity(
        state: JSONObject,
        reference: ImportedSourceReference,
    ): JSONObject? {
        val items = state.optJSONObject("entities")?.optJSONObject("items") ?: return null
        val expectedKey = "spotify:${reference.type.name.lowercase(Locale.ROOT)}:${reference.sourceId}"
        items.optJSONObject(expectedKey)?.let { return it }
        val keys = items.keys()
        while (keys.hasNext()) {
            val value = items.optJSONObject(keys.next()) ?: continue
            if (value.optString("id") == reference.sourceId) return value
        }
        return null
    }

    private fun parsePage(
        entity: JSONObject,
        reference: ImportedSourceReference,
        html: String,
    ): SpotifyPage {
        val page = entity.optJSONObject("content")
            ?: entity.optJSONObject("tracksV2")
            ?: throw PublicPlaylistUnavailableException()
        val totalTracks = page.optInt("totalCount").takeIf { it > 0 }
            ?: throw PublicPlaylistUnavailableException()
        val fallbackMetadata = PublicPlaylistPageParser.metadata(html)
        val tracksJson = page.optJSONArray("items") ?: throw PublicPlaylistUnavailableException()
        val collectionArtwork = firstArtwork(entity.opt("images"))
            ?: firstArtwork(entity.opt("coverArt"))
            ?: fallbackMetadata.artworkUrl
        val collectionName = entity.optString("name").trim()
            .ifBlank { fallbackMetadata.name.orEmpty() }
        val description = entity.optString("description")
            .trim()
            .takeIf(String::isNotBlank)
            ?: fallbackMetadata.description
        val owner = entity.optJSONObject("ownerV2")
            ?.optJSONObject("data")
            ?.optString("name")
            ?.trim()
            ?.takeIf(String::isNotBlank)
        val albumName = if (reference.type == ImportedCollectionType.ALBUM) collectionName else ""
        val tracks = buildList {
            repeat(tracksJson.length()) { index ->
                val wrapper = tracksJson.optJSONObject(index) ?: return@repeat
                val track = wrapper.optJSONObject("itemV2")?.optJSONObject("data")
                    ?: wrapper.optJSONObject("track")
                    ?: wrapper.optJSONObject("item")
                    ?: return@repeat
                val uri = track.optString("uri")
                val sourceId = uri.substringAfterLast(':')
                    .ifBlank { track.optString("id") }
                val title = track.optString("name").trim()
                if (sourceId.isBlank() || title.isBlank()) return@repeat
                val artists = parseSpotifyArtists(track.optJSONObject("artists"))
                val trackAlbum = track.optJSONObject("albumOfTrack")
                    ?.optString("name")
                    ?.trim()
                    .orEmpty()
                    .ifBlank { albumName }
                val artwork = firstArtwork(track.optJSONObject("albumOfTrack")?.opt("coverArt"))
                    ?: collectionArtwork
                add(
                    ImportedTrack(
                        sourceId = sourceId,
                        title = title,
                        artists = artists,
                        album = trackAlbum,
                        durationMs = track.optJSONObject("duration")?.optLong("totalMilliseconds")
                            ?.coerceAtLeast(0)
                            ?: 0,
                        trackNumber = track.optInt("trackNumber").takeIf { it > 0 },
                        discNumber = track.optInt("discNumber").takeIf { it > 0 },
                        isrc = null,
                        artworkUrl = artwork,
                        originalPosition = index,
                    ),
                )
            }
        }
        val nextOffset = page.optJSONObject("pagingInfo")
            ?.optInt("nextOffset", -1)
            ?.takeIf { it >= 0 }
        return SpotifyPage(
            name = collectionName,
            description = description,
            artworkUrl = collectionArtwork,
            owner = owner,
            totalTracks = totalTracks,
            tracks = tracks,
            nextOffset = nextOffset,
        )
    }

    private fun parseSpotifyArtists(artistsObject: JSONObject?): List<String> {
        val artists = artistsObject?.optJSONArray("items") ?: return emptyList()
        return buildList {
            repeat(artists.length()) { index ->
                val artist = artists.optJSONObject(index) ?: return@repeat
                val name = artist.optJSONObject("profile")?.optString("name")
                    ?.trim()
                    .orEmpty()
                    .ifBlank { artist.optString("name").trim() }
                if (name.isNotBlank() && name !in this) add(name)
            }
        }
    }

    private fun firstArtwork(value: Any?): String? = when (value) {
        is JSONObject -> firstArtwork(value.opt("sources")) ?: value.optString("url")
            .takeIf { it.startsWith("https://", true) }
        is JSONArray -> (0 until value.length()).asSequence()
            .mapNotNull { firstArtwork(value.opt(it)) }
            .firstOrNull()
        else -> null
    }

    private fun appendPage(
        tracks: MutableList<ImportedTrack>,
        pageTracks: List<ImportedTrack>,
        requestedOffset: Int,
    ) {
        if (pageTracks.isEmpty()) return
        val isFullSnapshot = pageTracks.size >= tracks.size &&
            pageTracks.take(tracks.size).sameSpotifySequence(tracks)
        if (isFullSnapshot) {
            tracks += pageTracks.drop(tracks.size)
            return
        }
        if (requestedOffset >= tracks.size) {
            tracks += pageTracks
            return
        }
        val expected = tracks.getOrNull(requestedOffset)
        if (expected != null && pageTracks.first().sameSpotifyTrack(expected)) {
            tracks += pageTracks.drop(1)
        } else {
            tracks += pageTracks
        }
    }

    private fun List<ImportedTrack>.sameSpotifySequence(other: List<ImportedTrack>): Boolean =
        size == other.size && zip(other).all { (left, right) -> left.sameSpotifyTrack(right) }

    private fun ImportedTrack.sameSpotifyTrack(other: ImportedTrack): Boolean =
        sourceId == other.sourceId && title == other.title && artists == other.artists

    private data class SpotifyPage(
        val name: String,
        val description: String?,
        val artworkUrl: String?,
        val owner: String?,
        val totalTracks: Int,
        val tracks: List<ImportedTrack>,
        val nextOffset: Int?,
    )
}

@Singleton
class TidalPlaylistImportProvider @Inject constructor(
    private val http: PublicPlaylistHttpClient,
) : PlaylistImportProvider {
    override val source = ImportedCollectionSource.TIDAL
    override val displayName = "TIDAL"
    override val isConfigured = true
    override val configurationMessage: String? = null

    override fun supports(request: PlaylistImportRequest) =
        (request as? PlaylistImportRequest.Url)?.value?.let(::extractSourceReference) != null

    override suspend fun analyze(request: PlaylistImportRequest): Result<ImportedCollection> =
        withContext(Dispatchers.IO) {
            runCatching {
                val url = (request as? PlaylistImportRequest.Url)?.value
                    ?: error("Pega un enlace público de TIDAL")
                val reference = extractSourceReference(url)
                    ?: error("El enlace de TIDAL no corresponde a una playlist o álbum")
                val html = try {
                    http.get(reference.canonicalUrl)
                } catch (_: Throwable) {
                    throw PublicPlaylistUnavailableException()
                }
                val pageMetadata = PublicPlaylistPageParser.metadata(html)
                val details = getJson(
                    "https://api.tidal.com/v1/${reference.type.apiPath}/${reference.sourceId}" +
                        "?countryCode=$COUNTRY_CODE",
                )
                val expectedTracks = details.optInt("numberOfTracks").takeIf { it > 0 }
                    ?: pageMetadata.totalTracks
                    ?: 0
                val items = fetchItems(reference, expectedTracks)
                if (items.isEmpty() || (expectedTracks > 0 && items.size != expectedTracks)) {
                    throw PublicPlaylistUnavailableException()
                }
                val name = details.optString("title").trim()
                    .ifBlank { pageMetadata.name.orEmpty() }
                    .ifBlank { "Colección de TIDAL" }
                ImportedCollection(
                    source = source,
                    sourceId = reference.sourceId,
                    sourceUrl = reference.canonicalUrl,
                    type = reference.type,
                    name = name,
                    description = details.optString("description").trim().takeIf(String::isNotBlank)
                        ?: pageMetadata.description,
                    artworkUrl = pageMetadata.artworkUrl
                        ?: tidalArtworkUrl(details.optString("squareImage"))
                        ?: tidalArtworkUrl(details.optString("image")),
                    owner = details.optJSONObject("creator")?.optString("name")
                        ?.takeIf(String::isNotBlank),
                    tracks = items,
                )
            }
        }

    companion object {
        private const val COUNTRY_CODE = "AR"
        private const val PAGE_SIZE = 100

        fun extractSourceReference(raw: String): ImportedSourceReference? = linkReference(
            raw = raw,
            source = ImportedCollectionSource.TIDAL,
            hosts = setOf("tidal.com", "www.tidal.com", "listen.tidal.com"),
            acceptedTypes = setOf("playlist", "album"),
        )
    }

    private suspend fun getJson(url: String): JSONObject = try {
        JSONObject(
            http.get(
                url,
                headers = mapOf("x-tidal-token" to TIDAL_PUBLIC_WEB_CLIENT_ID),
            ),
        )
    } catch (_: Throwable) {
        throw PublicPlaylistUnavailableException()
    }

    private suspend fun fetchItems(
        reference: ImportedSourceReference,
        expectedTracks: Int,
    ): List<ImportedTrack> {
        val tracks = mutableListOf<ImportedTrack>()
        var offset = 0
        val seenPages = mutableSetOf<Int>()
        while ((expectedTracks == 0 || tracks.size < expectedTracks) && seenPages.add(offset)) {
            val json = getJson(
                "https://api.tidal.com/v1/${reference.type.apiPath}/${reference.sourceId}/items" +
                    "?countryCode=$COUNTRY_CODE&limit=$PAGE_SIZE&offset=$offset",
            )
            val items = json.optJSONArray("items") ?: throw PublicPlaylistUnavailableException()
            repeat(items.length()) { index ->
                val wrapper = items.optJSONObject(index) ?: return@repeat
                if (wrapper.optString("type").equals("track", true)) {
                    parseTrack(wrapper.optJSONObject("item"), offset + index)?.let(tracks::add)
                }
            }
            val total = json.optInt("totalNumberOfItems").takeIf { it > 0 } ?: expectedTracks
            if (items.length() == 0 || (total > 0 && tracks.size >= total)) break
            offset += items.length()
            if (total == 0 && items.length() < PAGE_SIZE) break
        }
        return tracks
    }

    private fun parseTrack(item: JSONObject?, position: Int): ImportedTrack? {
        if (item == null) return null
        val sourceId = item.optString("id").ifBlank { item.optString("itemUuid") }
        val title = item.optString("title").trim()
        if (sourceId.isBlank() || title.isBlank()) return null
        val artists = buildList {
            val artistArray = item.optJSONArray("artists")
            if (artistArray != null) {
                repeat(artistArray.length()) { index ->
                    val artist = artistArray.optJSONObject(index)?.optString("name")?.trim().orEmpty()
                    if (artist.isNotBlank() && artist !in this) add(artist)
                }
            }
            if (isEmpty()) {
                item.optJSONObject("artist")?.optString("name")?.trim()
                    ?.takeIf(String::isNotBlank)
                    ?.let(::add)
            }
        }
        val album = item.optJSONObject("album")
        return ImportedTrack(
            sourceId = sourceId,
            title = title,
            artists = artists,
            album = album?.optString("title")?.trim().orEmpty(),
            durationMs = item.optLong("duration").coerceAtLeast(0) * 1_000,
            trackNumber = item.optInt("trackNumber").takeIf { it > 0 },
            discNumber = item.optInt("volumeNumber").takeIf { it > 0 },
            isrc = item.optString("isrc").trim().uppercase(Locale.ROOT).takeIf(String::isNotBlank),
            artworkUrl = tidalArtworkUrl(album?.optString("cover")),
            originalPosition = position,
        )
    }

    private val ImportedCollectionType.apiPath: String
        get() = if (this == ImportedCollectionType.ALBUM) "albums" else "playlists"

}

@Singleton
class YouTubePlaylistImportProvider @Inject constructor(
    private val extractor: YtDlpExtractor,
) : PlaylistImportProvider {
    override val source = ImportedCollectionSource.YOUTUBE
    override val displayName = "YouTube / YouTube Music"
    override val isConfigured = true
    override val configurationMessage: String? = null

    override fun supports(request: PlaylistImportRequest) =
        (request as? PlaylistImportRequest.Url)?.value?.let(::extractSourceReference) != null

    override suspend fun analyze(request: PlaylistImportRequest): Result<ImportedCollection> =
        withContext(Dispatchers.IO) {
            runCatching {
                val url = (request as? PlaylistImportRequest.Url)?.value
                    ?: error("Pega un enlace público de YouTube")
                val reference = extractSourceReference(url)
                    ?: error("El enlace de YouTube no corresponde a una playlist")
                val playlist = inspectPublicPlaylist(url, reference)
                val tracks = playlist.entries.mapIndexed { index, entry ->
                    ImportedTrack(
                        sourceId = entry.id.ifBlank { entry.webpageUrl },
                        title = entry.title,
                        artists = entry.artist.trim().takeIf(String::isNotBlank)?.let(::listOf).orEmpty(),
                        album = entry.album,
                        durationMs = entry.durationMs,
                        artworkUrl = entry.thumbnailUrl,
                        originalPosition = index,
                    )
                }.filter { it.sourceId.isNotBlank() && it.title.isNotBlank() }
                if (tracks.isEmpty()) throw PublicPlaylistUnavailableException()
                ImportedCollection(
                    source = source,
                    sourceId = reference.sourceId,
                    sourceUrl = reference.canonicalUrl,
                    type = ImportedCollectionType.PLAYLIST,
                    name = playlist.title.ifBlank { "Playlist de YouTube" },
                    description = playlist.description,
                    artworkUrl = playlist.thumbnailUrl,
                    tracks = tracks,
                    totalTracks = playlist.totalTracks.coerceAtLeast(tracks.size),
                )
            }
        }

    private suspend fun inspectPublicPlaylist(
        rawUrl: String,
        reference: ImportedSourceReference,
    ): YtDlpPlaylistInfo {
        val candidates = buildList {
            add(reference.canonicalUrl)
            val host = parseHttps(rawUrl)?.host?.lowercase(Locale.ROOT)
            if (host == "music.youtube.com") {
                add("https://music.youtube.com/playlist?list=${reference.sourceId}")
            }
        }.distinct()

        var lastFailure: Throwable? = null
        for (candidate in candidates) {
            try {
                val playlist = extractor.inspectPlaylist(candidate)
                if (playlist.entries.isNotEmpty()) return playlist
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Throwable) {
                lastFailure = failure
                // Try the other public YouTube host before reporting the failure.
            }
        }
        throw lastFailure ?: PublicPlaylistUnavailableException()
    }

    companion object {
        fun extractSourceReference(raw: String): ImportedSourceReference? {
            val uri = parseHttps(raw) ?: return null
            val host = uri.host.lowercase(Locale.ROOT)
            if (host !in setOf(
                    "youtube.com",
                    "www.youtube.com",
                    "music.youtube.com",
                    "m.youtube.com",
                    "youtu.be",
                )
            ) {
                return null
            }
            val listId = uri.rawQuery.orEmpty().split('&')
                .mapNotNull { part -> part.split('=', limit = 2).takeIf { it.size == 2 } }
                .firstOrNull { it[0] == "list" }
                ?.get(1)
                ?.takeIf { it.matches(Regex("[A-Za-z0-9_-]{10,80}")) }
                ?: return null
            return ImportedSourceReference(
                source = ImportedCollectionSource.YOUTUBE,
                type = ImportedCollectionType.PLAYLIST,
                sourceId = listId,
                canonicalUrl = "https://www.youtube.com/playlist?list=$listId",
            )
        }
    }
}

@Singleton
class PlaylistImportProviderRegistry @Inject constructor(
    file: FilePlaylistImportProvider,
    spotify: SpotifyPlaylistImportProvider,
    tidal: TidalPlaylistImportProvider,
    youtube: YouTubePlaylistImportProvider,
) {
    val providers: List<PlaylistImportProvider> = listOf(file, spotify, tidal, youtube)

    fun providerFor(request: PlaylistImportRequest): PlaylistImportProvider? =
        providers.firstOrNull { it.supports(request) }
}

private fun linkReference(
    raw: String,
    source: ImportedCollectionSource,
    hosts: Set<String>,
    acceptedTypes: Set<String>,
): ImportedSourceReference? {
    val uri = parseHttps(raw) ?: return null
    if (uri.host.lowercase(Locale.ROOT) !in hosts) return null
    val parts = uri.path.orEmpty().split('/').filter(String::isNotBlank)
    val typeIndex = parts.indexOfFirst { it.lowercase(Locale.ROOT) in acceptedTypes }
    if (typeIndex < 0 || typeIndex + 1 >= parts.size) return null
    val type = parts[typeIndex].lowercase(Locale.ROOT)
    val id = parts[typeIndex + 1].takeIf { it.matches(Regex("[A-Za-z0-9_-]{6,128}")) } ?: return null
    return ImportedSourceReference(
        source = source,
        type = if (type == "album") ImportedCollectionType.ALBUM else ImportedCollectionType.PLAYLIST,
        sourceId = id,
        canonicalUrl = "https://${uri.host.lowercase(Locale.ROOT)}/$type/$id",
    )
}

private fun parseHttps(raw: String): URI? = runCatching { URI(raw.trim()) }.getOrNull()
    ?.takeIf { it.scheme.equals("https", true) && !it.host.isNullOrBlank() }
