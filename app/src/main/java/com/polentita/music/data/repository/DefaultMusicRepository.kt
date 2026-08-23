package com.polentita.music.data.repository

import android.net.Uri
import com.polentita.music.core.common.AudioFormats
import com.polentita.music.core.common.FileNameSanitizer
import com.polentita.music.core.common.RemoteUrlValidator
import com.polentita.music.core.database.AlbumDao
import com.polentita.music.core.database.AlbumEntity
import com.polentita.music.core.database.PlaybackHistoryDao
import com.polentita.music.core.database.PlaylistDao
import com.polentita.music.core.database.PlaylistEntity
import com.polentita.music.core.database.PlaylistSummary
import com.polentita.music.core.database.SongDao
import com.polentita.music.core.database.SongEntity
import com.polentita.music.core.database.SourceType
import com.polentita.music.core.storage.LibraryStorage
import com.polentita.music.domain.model.Song
import com.polentita.music.domain.model.SongFilter
import com.polentita.music.domain.model.SongSort
import com.polentita.music.domain.model.PlaylistNames
import com.polentita.music.domain.model.toEntity
import com.polentita.music.domain.model.toModel
import com.polentita.music.domain.playlistimport.ImportMatchStatus
import com.polentita.music.domain.playlistimport.ImportedTrack
import com.polentita.music.domain.repository.ImportSummary
import com.polentita.music.domain.repository.MusicRepository
import com.polentita.music.domain.repository.ScanSummary
import com.polentita.music.data.playlistimport.PlaylistImportMatcher
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

@Singleton
class DefaultMusicRepository @Inject constructor(
    private val songDao: SongDao,
    private val albumDao: AlbumDao,
    private val playlistDao: PlaylistDao,
    private val historyDao: PlaybackHistoryDao,
    private val storage: LibraryStorage,
) : MusicRepository {
    private val favoritesMutex = Mutex()

    override fun observeSongs() = songDao.observeAll().models()
    override fun observeSong(id: Long) = songDao.observeById(id).map { it?.toModel() }
    override fun observeFavorites() = songDao.observeFavorites().models()
    override fun observeRecentlyAdded() = songDao.observeRecentlyAdded().models()
    override fun observeMostPlayed() = songDao.observeMostPlayed().models()
    override fun observeRecentHistory() = historyDao.observeRecentSongs().models()
    override fun observeArtists() = songDao.observeArtists()
    override fun observeArtistSongs(artist: String) = songDao.observeByArtist(artist).models()
    override fun observeAlbums() = albumDao.observeAll()
    override fun observeAlbum(id: Long) = albumDao.observeById(id)
    override fun observeAlbumSongs(id: Long) = songDao.observeByAlbum(id).models()
    override fun observePlaylists(): Flow<List<PlaylistSummary>> = playlistDao.observeSummaries()
    override fun observePlaylist(id: Long) = playlistDao.observeById(id)
    override fun observePlaylistSongs(id: Long) = playlistDao.observeSongs(id).models()

    override fun search(
        query: String,
        filter: SongFilter,
        sort: SongSort,
        ascending: Boolean,
    ): Flow<List<Song>> = songDao.search(
        query = query.trim(),
        favoriteOnly = filter.favoriteOnly,
        sourceType = filter.sourceType,
        coverMode = filter.coverMode,
        availableMode = filter.availableMode,
        artist = filter.artist,
        album = filter.album,
        genre = filter.genre,
        year = filter.year,
        sort = sort.name,
        ascending = ascending,
    ).models()

    override suspend fun getSong(id: Long) = songDao.getById(id)?.toModel()
    override suspend fun getSongs(ids: List<Long>): List<Song> {
        val byId = songDao.getByIds(ids).associateBy { it.id }
        return ids.mapNotNull { byId[it]?.toModel() }
    }
    override suspend fun findSongByUri(uri: String) = songDao.findByUri(uri)?.toModel()
    override suspend fun findSongByChecksum(checksum: String) = songDao.findByChecksum(checksum)?.toModel()
    override suspend fun findAvailableSongByMetadata(
        title: String,
        artist: String,
        isrc: String?,
    ): Song? {
        val track = ImportedTrack(
            sourceId = "metadata-match",
            title = title,
            artists = artist.split(",", ";").map(String::trim).filter(String::isNotBlank),
            isrc = isrc,
            originalPosition = 0,
        )
        val match = PlaylistImportMatcher.localMatch(track, songDao.getAvailable())
        return if (match.status == ImportMatchStatus.IN_LIBRARY && match.songId != null) {
            songDao.getById(match.songId)?.toModel()
        } else {
            null
        }
    }

    override suspend fun importFiles(uris: List<Uri>): ImportSummary {
        var imported = 0
        var duplicates = 0
        var failed = 0
        val errors = mutableListOf<String>()
        uris.distinct().forEach { source ->
            runCatching {
                val sourceMetadata = storage.extractMetadata(source)
                require(AudioFormats.isSupported(sourceMetadata.mimeType, sourceMetadata.displayName)) {
                    "Formato no compatible"
                }
                val checksum = storage.checksum(source)
                if (songDao.findByChecksum(checksum) != null) {
                    duplicates++
                    return@runCatching
                }
                val stored = storage.copyIntoLibrary(source, "Imports", sourceMetadata.displayName, sourceMetadata.mimeType)
                val metadata = storage.extractMetadata(stored.uri)
                val cover = storage.extractAndStoreCover(stored.uri, checksum)
                val albumId = resolveAlbumId(metadata.album, metadata.artist, metadata.year, cover)
                songDao.insert(
                    metadata.toSongEntity(
                        uri = stored.uri,
                        checksum = checksum,
                        coverUri = cover,
                        sourceType = SourceType.IMPORTED,
                        albumId = albumId,
                    ),
                )
                imported++
            }.onFailure {
                failed++
                errors += "${source.lastPathSegment ?: "Archivo"}: ${it.message ?: "error desconocido"}"
            }
        }
        return ImportSummary(imported, duplicates, failed, errors.take(8))
    }

    override suspend fun registerDownloadedFile(
        uri: Uri,
        title: String,
        artist: String,
        album: String,
        sourceUrl: String,
        downloadedCoverUri: String?,
        preferredAlbumId: Long?,
        useMetadataAlbumWhenBlank: Boolean,
        isrc: String?,
    ): Long {
        val checksum = storage.checksum(uri)
        songDao.findByChecksum(checksum)?.let { return it.id }
        val metadata = storage.extractMetadata(uri)
        val cover = downloadedCoverUri
            ?: runCatching { storage.extractAndStoreCover(uri, checksum) }.getOrNull()
        val finalArtist = artist.ifBlank { metadata.artist }
        val preferredAlbum = if (preferredAlbumId != null) {
            albumDao.getById(preferredAlbumId)
        } else {
            null
        }
        val finalAlbum = preferredAlbum?.name
            ?: album.cleanAlbum()
            ?: if (useMetadataAlbumWhenBlank) metadata.album.cleanAlbum().orEmpty() else ""
        if (preferredAlbum != null && preferredAlbum.coverUri == null && cover != null) {
            albumDao.update(preferredAlbum.copy(coverUri = cover))
        }
        val albumId = preferredAlbum?.id
            ?: resolveAlbumId(finalAlbum, finalArtist, metadata.year, cover)
        return songDao.insert(
            metadata.toSongEntity(
                uri = uri,
                checksum = checksum,
                coverUri = cover,
                sourceType = SourceType.DOWNLOADED,
                albumId = albumId,
                titleOverride = title,
                artistOverride = artist,
                albumOverride = finalAlbum,
                sourceUrl = sourceUrl,
                isrc = isrc,
            ),
        )
    }

    override suspend fun scanLibrary(): ScanSummary {
        val documents = storage.audioDocuments()
        val before = songDao.allContentUris().toSet()
        songDao.markAllUnavailable()
        var added = 0
        var restored = 0
        var failed = 0
        documents.forEach { document ->
            val uriText = document.uri.toString()
            runCatching {
                val existing = songDao.findByUri(uriText)
                if (existing != null) {
                    val recoveredCover = existing.coverUri
                        ?: storage.findStoredCover(existing.checksum)
                    if (recoveredCover != existing.coverUri) {
                        songDao.update(
                            existing.copy(
                                coverUri = recoveredCover,
                                isAvailable = true,
                                dateModified = System.currentTimeMillis(),
                            ),
                        )
                    } else {
                        songDao.setAvailable(uriText, true)
                    }
                    if (!existing.isAvailable) restored++
                } else {
                    val checksum = storage.checksum(document.uri)
                    val duplicate = songDao.findByChecksum(checksum)
                    if (duplicate != null) {
                        val recoveredCover = duplicate.coverUri
                            ?: storage.findStoredCover(checksum)
                        if (!duplicate.isAvailable || recoveredCover != duplicate.coverUri) {
                            songDao.update(
                                duplicate.copy(
                                    contentUri = uriText,
                                    coverUri = recoveredCover,
                                    isAvailable = true,
                                    dateModified = System.currentTimeMillis(),
                                ),
                            )
                            restored++
                        }
                    } else {
                        val metadata = storage.extractMetadata(document.uri)
                        val cover = storage.findStoredCover(checksum)
                            ?: storage.extractAndStoreCover(document.uri, checksum)
                        val albumId = resolveAlbumId(metadata.album, metadata.artist, metadata.year, cover)
                        songDao.insert(
                            metadata.toSongEntity(
                                uri = document.uri,
                                checksum = checksum,
                                coverUri = cover,
                                sourceType = SourceType.SCANNED,
                                albumId = albumId,
                            ),
                        )
                        added++
                    }
                }
            }.onFailure { failed++ }
        }
        val afterAvailable = documents.map { it.uri.toString() }.toSet()
        return ScanSummary(added, restored, (before - afterAvailable).size, failed)
    }

    override suspend fun updateSong(song: Song) {
        songDao.update(song.copy(dateModified = System.currentTimeMillis()).toEntity())
    }

    override suspend fun clearArtist(artist: String): Int = songDao.clearArtist(artist)

    override suspend fun renameArtist(artist: String, newName: String): Int =
        songDao.renameArtist(artist, newName.trim())

    override suspend fun toggleFavorite(songId: Long) {
        favoritesMutex.withLock {
            if (songDao.getById(songId) == null) return
            songDao.toggleFavorite(songId)
            syncFavoritesPlaylistLocked()
        }
    }

    override suspend fun syncFavoritesPlaylist() {
        favoritesMutex.withLock { syncFavoritesPlaylistLocked() }
    }

    override suspend fun removeSong(songId: Long, deleteFile: Boolean): Boolean {
        val song = songDao.getById(songId) ?: return false
        if (deleteFile && !storage.delete(Uri.parse(song.contentUri))) return false
        songDao.deleteById(songId)
        if (deleteFile) {
            song.coverUri
                ?.takeIf(String::isNotBlank)
                ?.let { coverUri -> deleteCoverWhenUnreferenced(coverUri) }
        }
        return true
    }

    override suspend fun renameSongFile(songId: Long, newName: String): Boolean {
        val song = songDao.getById(songId) ?: return false
        val safeName = FileNameSanitizer.sanitize(newName)
        val renamed = storage.rename(Uri.parse(song.contentUri), safeName) ?: return false
        songDao.update(
            song.copy(
                contentUri = renamed.toString(),
                displayFileName = safeName,
                dateModified = System.currentTimeMillis(),
            ),
        )
        return true
    }

    override suspend fun createAlbum(album: AlbumEntity) = albumDao.insert(album)
    override suspend fun updateAlbum(album: AlbumEntity) = albumDao.update(album)
    override suspend fun deleteAlbum(album: AlbumEntity) = albumDao.deletePreservingSongs(album.id)
    override suspend fun cleanEmptyAlbums(): Int = albumDao.deleteEmpty()
    override suspend fun createPlaylist(playlist: PlaylistEntity) = playlistDao.insert(playlist)
    override suspend fun updatePlaylist(playlist: PlaylistEntity) = playlistDao.update(
        playlist.copy(dateModified = System.currentTimeMillis()),
    )
    override suspend fun deletePlaylist(playlist: PlaylistEntity) = playlistDao.delete(playlist)
    override suspend fun addSongsToPlaylist(playlistId: Long, songIds: List<Long>) =
        playlistDao.addSongs(playlistId, songIds)
    override suspend fun removeSongFromPlaylist(playlistId: Long, songId: Long) =
        playlistDao.removeSong(playlistId, songId)
    override suspend fun reorderPlaylist(playlistId: Long, songIds: List<Long>) =
        playlistDao.replaceOrder(playlistId, songIds)

    private suspend fun syncFavoritesPlaylistLocked() {
        val favoriteIds = songDao.favoriteIds()
        val playlist = playlistDao.findByName(PlaylistNames.TUS_ME_GUSTA)
        if (playlist == null && favoriteIds.isEmpty()) return

        val playlistId = playlist?.id ?: playlistDao.insert(
            PlaylistEntity(name = PlaylistNames.TUS_ME_GUSTA),
        )
        val existingIds = playlistDao.getCrossRefs(playlistId).map { it.songId }
        val staleIds = existingIds - favoriteIds.toSet()
        val missingIds = favoriteIds.filterNot(existingIds::contains)
        staleIds.forEach { playlistDao.removeSong(playlistId, it) }
        if (missingIds.isNotEmpty()) {
            playlistDao.addSongs(playlistId, missingIds)
        } else if (staleIds.isNotEmpty()) {
            playlistDao.touch(playlistId)
        }
    }

    private fun Flow<List<SongEntity>>.models() = map { items -> items.map(SongEntity::toModel) }

    private suspend fun deleteCoverWhenUnreferenced(uri: String) {
        val isReferenced = runCatching {
            songDao.countCoverReferences(uri) +
                albumDao.countCoverReferences(uri) +
                playlistDao.countCoverReferences(uri) > 0
        }.getOrElse { return }
        if (!isReferenced) runCatching { storage.deleteManagedCover(uri) }
    }

    private fun com.polentita.music.core.storage.AudioMetadata.toSongEntity(
        uri: Uri,
        checksum: String,
        coverUri: String?,
        sourceType: SourceType,
        albumId: Long? = null,
        titleOverride: String = "",
        artistOverride: String = "",
        albumOverride: String? = null,
        sourceUrl: String? = null,
        isrc: String? = null,
    ) = SongEntity(
        title = titleOverride.ifBlank { title },
        artist = artistOverride.ifBlank { artist },
        albumId = albumId,
        albumName = albumOverride ?: album,
        genre = genre,
        year = year,
        trackNumber = trackNumber,
        discNumber = discNumber,
        durationMs = durationMs,
        contentUri = uri.toString(),
        originalFileName = displayName,
        displayFileName = displayName,
        mimeType = mimeType,
        fileSize = fileSize,
        coverUri = coverUri,
        sourceType = sourceType.name,
        sourceUrl = sourceUrl?.let(RemoteUrlValidator::redacted),
        dateModified = modifiedAt.takeIf { it > 0 } ?: System.currentTimeMillis(),
        checksum = checksum,
        isrc = isrc,
    )

    private suspend fun resolveAlbumId(
        name: String,
        artist: String,
        year: Int?,
        coverUri: String?,
    ): Long? {
        if (name.isBlank()) return null
        val cleanName = name.trim()
        val cleanArtist = artist.trim()
        val existing = albumDao.findByNameArtist(cleanName, cleanArtist)
        if (existing != null) {
            if (existing.coverUri == null && coverUri != null) {
                albumDao.update(existing.copy(coverUri = coverUri))
            }
            return existing.id
        }
        return albumDao.insert(
            AlbumEntity(
                name = cleanName,
                artist = cleanArtist,
                year = year,
                coverUri = coverUri,
            ),
        )
    }

    private fun String.cleanAlbum(): String? = trim().takeUnless {
        it.isBlank() ||
            it.equals("YouTube", ignoreCase = true) ||
            it.equals("YouTube Music", ignoreCase = true)
    }
}
