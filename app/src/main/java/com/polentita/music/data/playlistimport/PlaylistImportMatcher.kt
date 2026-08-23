package com.polentita.music.data.playlistimport

import com.polentita.music.core.database.SongEntity
import com.polentita.music.domain.playlistimport.CandidateConfidence
import com.polentita.music.domain.playlistimport.ImportCandidate
import com.polentita.music.domain.playlistimport.ImportMatchStatus
import com.polentita.music.domain.playlistimport.ImportedTrack
import com.polentita.music.domain.provider.RemoteTrack
import java.text.Normalizer
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max

data class LocalTrackMatch(
    val status: ImportMatchStatus,
    val songId: Long? = null,
    val score: Double = 0.0,
)

data class CandidateSelection(
    val candidates: List<ImportCandidate>,
    val selected: ImportCandidate?,
    val ambiguous: Boolean,
)

object PlaylistImportMatcher {
    const val DURATION_TOLERANCE_MS = 4_000L

    fun normalizeTitle(value: String): String = normalizeBase(
        value
            .replace(FEAT_SECTION, " ")
            .replace(BRACKETED_NOISE, " ")
            .replace(TRAILING_NOISE, " "),
    )

    fun normalizeArtist(value: String): String = artistParts(value)
        .map(::normalizeBase)
        .filter(String::isNotBlank)
        .sorted()
        .joinToString(" ")

    fun normalizeAlbum(value: String): String = normalizeBase(value.replace(BRACKETED_NOISE, " "))

    fun localMatch(track: ImportedTrack, songs: List<SongEntity>): LocalTrackMatch {
        if (track.title.isBlank()) return LocalTrackMatch(ImportMatchStatus.METADATA_ERROR)
        val available = songs.filter(SongEntity::isAvailable)
        val normalizedIsrc = track.isrc?.normalizedIsrc()
        if (!normalizedIsrc.isNullOrBlank()) {
            available.firstOrNull { it.isrc?.normalizedIsrc() == normalizedIsrc }?.let {
                return LocalTrackMatch(ImportMatchStatus.IN_LIBRARY, it.id, 1.0)
            }
        }
        val title = normalizeTitle(track.title)
        val artist = normalizeArtist(track.artists.joinToString(";"))
        val album = normalizeAlbum(track.album)
        if (artist.isBlank()) return LocalTrackMatch(ImportMatchStatus.REQUIRES_REVIEW)

        available.firstOrNull { song ->
            normalizeTitle(song.title) == title &&
                normalizeArtist(song.artist) == artist &&
                versionMarkers(song.title) == versionMarkers(track.title)
        }?.let { return LocalTrackMatch(ImportMatchStatus.IN_LIBRARY, it.id, 0.98) }

        available.firstOrNull { song ->
            normalizeTitle(song.title) == title &&
                normalizeArtist(song.artist) == artist &&
                album.isNotBlank() && normalizeAlbum(song.albumName) == album &&
                durationCompatible(track.durationMs, song.durationMs) &&
                versionMarkers(song.title) == versionMarkers(track.title)
        }?.let { return LocalTrackMatch(ImportMatchStatus.IN_LIBRARY, it.id, 0.96) }

        val ranked = available.map { song -> song to localScore(track, song) }
            .sortedByDescending { it.second }
        val best = ranked.firstOrNull() ?: return LocalTrackMatch(ImportMatchStatus.MISSING)
        val secondScore = ranked.getOrNull(1)?.second ?: 0.0
        return if (best.second >= 0.78 && best.second - secondScore >= 0.08) {
            LocalTrackMatch(ImportMatchStatus.PROBABLE_MATCH, best.first.id, best.second)
        } else {
            LocalTrackMatch(ImportMatchStatus.MISSING, score = best.second)
        }
    }

    fun selectCandidates(track: ImportedTrack, remoteTracks: List<RemoteTrack>): CandidateSelection {
        val ranked = remoteTracks
            .distinctBy { "${it.providerId}:${it.id}" }
            .map { remote -> remote to remoteScore(track, remote) }
            .filter { it.second >= 0.30 }
            .sortedByDescending { it.second }
            .take(6)
            .map { (remote, score) ->
                ImportCandidate(
                    providerId = remote.providerId,
                    remoteTrackId = remote.id,
                    title = remote.title,
                    artist = remote.artist.name,
                    album = remote.album.name,
                    durationMs = remote.durationMs,
                    artworkUrl = remote.coverUri,
                    externalUrl = remote.externalUrl.orEmpty(),
                    score = score,
                    confidence = when {
                        score >= 0.88 -> CandidateConfidence.HIGH
                        score >= 0.72 -> CandidateConfidence.MEDIUM
                        else -> CandidateConfidence.LOW
                    },
                )
            }
        val best = ranked.firstOrNull()
        val second = ranked.getOrNull(1)
        val ambiguous = best == null || best.score < 0.78 ||
            (second != null && best.score - second.score < 0.08)
        return CandidateSelection(
            candidates = ranked,
            selected = best,
            ambiguous = ambiguous,
        )
    }

    fun duplicatePositions(tracks: List<ImportedTrack>): Set<Int> {
        val seen = mutableSetOf<String>()
        return buildSet {
            tracks.sortedBy(ImportedTrack::originalPosition).forEach { track ->
                val normalizedIsrc = track.isrc?.normalizedIsrc().orEmpty()
                val normalizedTitle = normalizeTitle(track.title)
                val normalizedArtist = normalizeArtist(track.artists.joinToString(";"))
                val key = if (normalizedIsrc.isNotBlank()) {
                    "isrc:$normalizedIsrc"
                } else if (normalizedTitle.isNotBlank() && normalizedArtist.isNotBlank()) {
                    // La duración puede cambiar entre ediciones, uploads o
                    // cortes del mismo audio. No la uses para crear una
                    // segunda descarga de la misma pista.
                    "metadata:$normalizedTitle|$normalizedArtist|" +
                        versionMarkers(track.title).sorted().joinToString(",")
                } else {
                    // Sin artista no es seguro fusionar dos canciones que
                    // solo comparten título; el sourceId conserva ambas.
                    "source:${track.sourceId.ifBlank { track.originalPosition.toString() }}"
                }
                if (!seen.add(key)) add(track.originalPosition)
            }
        }
    }

    fun durationCompatible(expected: Long, actual: Long): Boolean {
        if (expected <= 0 || actual <= 0) return false
        val tolerance = max(DURATION_TOLERANCE_MS, (expected * 0.03).toLong())
        return abs(expected - actual) <= tolerance
    }

    private fun localScore(track: ImportedTrack, song: SongEntity): Double {
        val titleScore = tokenSimilarity(normalizeTitle(track.title), normalizeTitle(song.title))
        val artistScore = tokenSimilarity(
            normalizeArtist(track.artists.joinToString(";")),
            normalizeArtist(song.artist),
        )
        val albumScore = if (track.album.isBlank() || song.albumName.isBlank()) {
            0.5
        } else {
            tokenSimilarity(normalizeAlbum(track.album), normalizeAlbum(song.albumName))
        }
        val durationScore = when {
            track.durationMs <= 0 || song.durationMs <= 0 -> 0.5
            durationCompatible(track.durationMs, song.durationMs) -> 1.0
            else -> 0.0
        }
        val markerPenalty = if (versionMarkers(track.title) == versionMarkers(song.title)) 0.0 else 0.3
        return (titleScore * 0.48 + artistScore * 0.30 + albumScore * 0.08 + durationScore * 0.14 - markerPenalty)
            .coerceIn(0.0, 1.0)
    }

    private fun remoteScore(track: ImportedTrack, remote: RemoteTrack): Double {
        val titleScore = tokenSimilarity(normalizeTitle(track.title), normalizeTitle(remote.title))
        val artistScore = tokenSimilarity(
            normalizeArtist(track.artists.joinToString(";")),
            normalizeArtist(remote.artist.name),
        )
        val durationScore = when {
            track.durationMs <= 0 || remote.durationMs <= 0 -> 0.5
            durationCompatible(track.durationMs, remote.durationMs) -> 1.0
            else -> (1.0 - abs(track.durationMs - remote.durationMs).toDouble() / 60_000.0).coerceIn(0.0, 1.0)
        }
        val markerPenalty = if (versionMarkers(track.title) == versionMarkers(remote.title)) 0.0 else 0.36
        return (titleScore * 0.58 + artistScore * 0.27 + durationScore * 0.15 - markerPenalty)
            .coerceIn(0.0, 1.0)
    }

    private fun tokenSimilarity(first: String, second: String): Double {
        if (first == second && first.isNotBlank()) return 1.0
        val left = first.split(' ').filter(String::isNotBlank).toSet()
        val right = second.split(' ').filter(String::isNotBlank).toSet()
        if (left.isEmpty() || right.isEmpty()) return 0.0
        return left.intersect(right).size.toDouble() / left.union(right).size
    }

    private fun artistParts(value: String): List<String> = value.split(ARTIST_SEPARATOR)

    private fun normalizeBase(value: String): String = Normalizer.normalize(value, Normalizer.Form.NFD)
        .replace(DIACRITICS, "")
        .lowercase(Locale.ROOT)
        .replace(PUNCTUATION, " ")
        .replace(MULTIPLE_SPACES, " ")
        .trim()

    private fun versionMarkers(value: String): Set<String> {
        val clean = normalizeBase(value)
        return VERSION_MARKERS.filterTo(mutableSetOf()) { marker ->
            Regex("(?:^| )${Regex.escape(marker)}(?: |$)").containsMatchIn(clean)
        }
    }

    private fun String.normalizedIsrc(): String = uppercase(Locale.ROOT).replace(Regex("[^A-Z0-9]"), "")

    private val FEAT_SECTION = Regex("\\s*[\\[(]?\\b(?:feat|ft)\\.?\\s+[^\\])]+[\\])]?$", RegexOption.IGNORE_CASE)
    private val BRACKETED_NOISE = Regex(
        "[\\[(](?:official\\s+(?:music\\s+)?video|official\\s+audio|lyrics?|audio|remaster(?:ed)?(?:\\s+\\d{4})?|\\d{4}\\s+remaster)[\\])]",
        RegexOption.IGNORE_CASE,
    )
    private val TRAILING_NOISE = Regex(
        "\\s*[-–—]\\s*(?:official\\s+(?:music\\s+)?video|official\\s+audio|lyrics?|audio|remaster(?:ed)?(?:\\s+\\d{4})?)\\s*$",
        RegexOption.IGNORE_CASE,
    )
    private val ARTIST_SEPARATOR = Regex("\\s*(?:;|,|&|/|\\band\\b|\\by\\b|\\bfeat\\.?\\b|\\bft\\.?\\b)\\s*", RegexOption.IGNORE_CASE)
    private val DIACRITICS = Regex("\\p{M}+")
    private val PUNCTUATION = Regex("[^a-z0-9]+")
    private val MULTIPLE_SPACES = Regex("\\s+")
    private val VERSION_MARKERS = setOf(
        "live",
        "concert",
        "cover",
        "remix",
        "slowed",
        "nightcore",
        "instrumental",
        "karaoke",
        "acoustic",
        "lyrics",
        "lyric",
    )
}
