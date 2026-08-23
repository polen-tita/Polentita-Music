package com.polentita.music.data.playlistimport

import androidx.room.withTransaction
import com.polentita.music.core.database.PlaylistDao
import com.polentita.music.core.database.PlaylistEntity
import com.polentita.music.core.database.PlaylistImportCandidateEntity
import com.polentita.music.core.database.PlaylistImportDao
import com.polentita.music.core.database.PlaylistImportEntity
import com.polentita.music.core.database.PlaylistImportItemEntity
import com.polentita.music.core.database.PolentitaDatabase
import com.polentita.music.core.database.SongDao
import com.polentita.music.domain.model.PlaylistNames
import com.polentita.music.domain.playlistimport.ImportCandidate
import com.polentita.music.domain.playlistimport.ImportMatchStatus
import com.polentita.music.domain.playlistimport.ImportedCollection
import com.polentita.music.domain.playlistimport.PlaylistImportItemState
import com.polentita.music.domain.playlistimport.PlaylistImportRequest
import com.polentita.music.domain.playlistimport.PlaylistImportState
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

data class PlaylistImportItemSnapshot(
    val item: PlaylistImportItemEntity,
    val candidates: List<PlaylistImportCandidateEntity>,
) {
    val selectedCandidate: PlaylistImportCandidateEntity?
        get() = candidates.firstOrNull(PlaylistImportCandidateEntity::selected)
}

data class PlaylistImportSnapshot(
    val imported: PlaylistImportEntity,
    val items: List<PlaylistImportItemSnapshot>,
) {
    val completedCount: Int
        get() = items.count { it.item.state in COMPLETED_ITEM_STATES }
    val downloadedCount: Int
        get() = items.count { it.item.state == PlaylistImportItemState.COMPLETED.name }
    val alreadyAvailableCount: Int
        get() = items.count { it.item.state == PlaylistImportItemState.IN_LIBRARY.name }
    val omittedCount: Int
        get() = items.count { it.item.state == PlaylistImportItemState.OMITTED.name }
    val errorCount: Int
        get() = items.count { it.item.state == PlaylistImportItemState.ERROR.name }
    val reviewCount: Int
        get() = items.count {
            it.item.state in setOf(
                PlaylistImportItemState.PROBABLE_MATCH.name,
                PlaylistImportItemState.REQUIRES_REVIEW.name,
                PlaylistImportItemState.NOT_AVAILABLE.name,
                PlaylistImportItemState.METADATA_ERROR.name,
            )
        }
    val selectedPendingCount: Int
        get() = items.count {
            it.item.selected && it.item.state in setOf(
                PlaylistImportItemState.MISSING.name,
                PlaylistImportItemState.PENDING.name,
                PlaylistImportItemState.PAUSED.name,
                PlaylistImportItemState.ERROR.name,
            )
        }

    companion object {
        val COMPLETED_ITEM_STATES = setOf(
            PlaylistImportItemState.IN_LIBRARY.name,
            PlaylistImportItemState.COMPLETED.name,
        )
    }
}

@Singleton
class PlaylistImportRepository @Inject constructor(
    private val database: PolentitaDatabase,
    private val dao: PlaylistImportDao,
    private val songDao: SongDao,
    private val playlistDao: PlaylistDao,
    private val providers: PlaylistImportProviderRegistry,
) {
    fun observeHistory(): Flow<List<PlaylistImportEntity>> = dao.observeAll()

    fun observe(importId: String): Flow<PlaylistImportSnapshot?> = combine(
        dao.observeById(importId),
        dao.observeItems(importId),
        dao.observeCandidates(importId),
    ) { imported, items, candidates ->
        imported?.let {
            val byItem = candidates.groupBy(PlaylistImportCandidateEntity::importItemId)
            PlaylistImportSnapshot(
                imported = it,
                items = items.map { item -> PlaylistImportItemSnapshot(item, byItem[item.id].orEmpty()) },
            )
        }
    }

    suspend fun analyze(request: PlaylistImportRequest): String {
        val provider = providers.providerFor(request)
            ?: throw IllegalArgumentException("El enlace o archivo no pertenece a un proveedor compatible")
        check(provider.isConfigured) { provider.configurationMessage ?: "El proveedor no está configurado" }
        val collection = provider.analyze(request).getOrThrow()
        dao.getBySource(collection.source.name, collection.sourceId)?.let { return it.id }
        return persistAnalysis(collection)
    }

    suspend fun updateSelection(itemId: String, selected: Boolean) {
        val item = dao.getItem(itemId) ?: return
        if (item.state in TERMINAL_ITEM_STATES) return
        dao.updateItem(item.copy(selected = selected, updatedAt = System.currentTimeMillis()))
    }

    suspend fun confirmLocalMatch(itemId: String) = database.withTransaction {
        val item = dao.getItem(itemId) ?: return@withTransaction
        val songId = item.localSongId ?: return@withTransaction
        if (songDao.getById(songId)?.isAvailable != true) return@withTransaction
        dao.getById(item.importId)?.localPlaylistId?.let { playlistId ->
            if (playlistDao.getById(playlistId) != null) {
                playlistDao.insertImportedSong(playlistId, songId, item.originalPosition)
            }
        }
        dao.updateItem(
            item.copy(
                state = PlaylistImportItemState.IN_LIBRARY.name,
                errorMessage = null,
                updatedAt = System.currentTimeMillis(),
            ),
        )
    }

    suspend fun updateCollection(importId: String, name: String, artworkUrl: String?) {
        val imported = dao.getById(importId) ?: return
        val cleanName = name.trim()
        require(cleanName.isNotBlank()) { "Escribe un nombre para la playlist" }
        require(!isProtectedPlaylistName(cleanName)) { "Ese nombre está reservado. Elige otro" }
        dao.update(
            imported.copy(
                name = cleanName,
                artworkUrl = artworkUrl,
                updatedAt = System.currentTimeMillis(),
            ),
        )
    }

    suspend fun createLocalPlaylist(importId: String, name: String, artworkUrl: String?): Long =
        database.withTransaction {
            val imported = dao.getById(importId) ?: error("La importación ya no existe")
            imported.localPlaylistId?.let { existingId ->
                playlistDao.getById(existingId)?.let { return@withTransaction existingId }
            }
            val cleanName = name.trim()
            require(cleanName.isNotBlank()) { "Escribe un nombre para la playlist" }
            require(!isProtectedPlaylistName(cleanName)) { "Ese nombre está reservado. Elige otro" }
            val playlistId = playlistDao.insert(
                PlaylistEntity(
                    name = cleanName,
                    description = imported.description,
                    coverUri = artworkUrl,
                ),
            )
            dao.getItems(importId)
                .filter { item ->
                    item.selected &&
                        item.state == PlaylistImportItemState.IN_LIBRARY.name &&
                        item.localSongId != null
                }
                .forEach { item ->
                    playlistDao.insertImportedSong(
                        playlistId = playlistId,
                        songId = requireNotNull(item.localSongId),
                        originalPosition = item.originalPosition,
                    )
                }
            dao.update(
                imported.copy(
                    name = cleanName,
                    artworkUrl = artworkUrl,
                    localPlaylistId = playlistId,
                    state = PlaylistImportState.REVIEW.name,
                    updatedAt = System.currentTimeMillis(),
                    errorMessage = null,
                ),
            )
            playlistId
        }

    suspend fun selectCandidate(itemId: String, candidateId: String) {
        val item = dao.getItem(itemId) ?: return
        require(dao.getCandidates(itemId).any { it.id == candidateId }) { "La coincidencia ya no está disponible" }
        database.withTransaction {
            dao.selectCandidate(itemId, candidateId)
            dao.updateItem(
                item.copy(
                    selected = true,
                    state = PlaylistImportItemState.PENDING.name,
                    errorMessage = null,
                    updatedAt = System.currentTimeMillis(),
                ),
            )
        }
    }

    suspend fun omit(itemId: String) {
        val item = dao.getItem(itemId) ?: return
        if (item.state in PlaylistImportSnapshot.COMPLETED_ITEM_STATES) return
        dao.updateItem(
            item.copy(
                selected = false,
                state = PlaylistImportItemState.OMITTED.name,
                errorMessage = null,
                updatedAt = System.currentTimeMillis(),
            ),
        )
    }

    suspend fun retryErrors(importId: String) {
        val items = dao.getItems(importId).map { item ->
            if (item.state !in setOf(
                    PlaylistImportItemState.ERROR.name,
                    PlaylistImportItemState.NOT_AVAILABLE.name,
                )
            ) return@map item
            val hasCandidate = dao.getSelectedCandidate(item.id) != null
            item.copy(
                selected = true,
                state = if (hasCandidate) {
                    PlaylistImportItemState.PENDING.name
                } else {
                    PlaylistImportItemState.MISSING.name
                },
                errorMessage = null,
                updatedAt = System.currentTimeMillis(),
            )
        }
        dao.updateItems(items)
    }

    suspend fun get(importId: String) = dao.getById(importId)
    suspend fun getItems(importId: String) = dao.getItems(importId)
    suspend fun getItem(itemId: String) = dao.getItem(itemId)
    suspend fun getCandidates(itemId: String) = dao.getCandidates(itemId)
    suspend fun getSelectedCandidate(itemId: String) = dao.getSelectedCandidate(itemId)
    suspend fun activeImports() = dao.getByStates(ACTIVE_IMPORT_STATES)
    suspend fun update(imported: PlaylistImportEntity) = dao.update(imported)
    suspend fun updateItem(item: PlaylistImportItemEntity) = dao.updateItem(item)
    suspend fun updateItems(items: List<PlaylistImportItemEntity>) = dao.updateItems(items)
    suspend fun markCompletionNotified(importId: String) = dao.markCompletionNotified(importId)

    suspend fun replaceCandidates(
        item: PlaylistImportItemEntity,
        candidates: List<ImportCandidate>,
        selected: ImportCandidate?,
    ) = database.withTransaction {
        dao.deleteCandidates(item.id)
        val entities = candidates.mapIndexed { rank, candidate ->
            candidate.toEntity(item.id, rank, selected == candidate)
        }
        if (entities.isNotEmpty()) dao.replaceCandidates(entities)
        dao.updateItem(
            item.copy(
                state = when {
                    candidates.isEmpty() -> PlaylistImportItemState.NOT_AVAILABLE.name
                    selected == null -> PlaylistImportItemState.REQUIRES_REVIEW.name
                    else -> PlaylistImportItemState.PENDING.name
                },
                errorMessage = if (candidates.isEmpty()) "No se encontraron candidatos" else null,
                updatedAt = System.currentTimeMillis(),
            ),
        )
    }

    suspend fun attachSong(
        importId: String,
        itemId: String,
        songId: Long,
        itemState: PlaylistImportItemState = PlaylistImportItemState.COMPLETED,
    ) = database.withTransaction {
        val imported = dao.getById(importId) ?: return@withTransaction
        val item = dao.getItem(itemId) ?: return@withTransaction
        val playlistId = imported.localPlaylistId
        if (playlistId == null || playlistDao.getById(playlistId) == null) {
            dao.updateItem(
                item.copy(
                    state = PlaylistImportItemState.ERROR.name,
                    errorMessage = "La playlist local fue eliminada; el audio descargado se conservó",
                    localSongId = songId,
                    updatedAt = System.currentTimeMillis(),
                ),
            )
            return@withTransaction
        }
        playlistDao.insertImportedSong(playlistId, songId, item.originalPosition)
        dao.updateItem(
            item.copy(
                state = itemState.name,
                localSongId = songId,
                errorMessage = null,
                updatedAt = System.currentTimeMillis(),
            ),
        )
    }

    private suspend fun persistAnalysis(collection: ImportedCollection): String = database.withTransaction {
        dao.getBySource(collection.source.name, collection.sourceId)?.let { return@withTransaction it.id }
        val importId = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        val duplicates = PlaylistImportMatcher.duplicatePositions(collection.tracks)
        val localSongs = songDao.getAvailable()
        val imported = PlaylistImportEntity(
            id = importId,
            source = collection.source.name,
            sourceId = collection.sourceId,
            sourceUrl = collection.sourceUrl,
            type = collection.type.name,
            name = collection.name.trim().ifBlank { "Playlist importada" },
            description = collection.description.orEmpty().trim(),
            artworkUrl = collection.artworkUrl,
            owner = collection.owner,
            totalTracks = collection.totalTracks.coerceAtLeast(collection.tracks.size),
            createdAt = now,
            updatedAt = now,
        )
        check(dao.insert(imported) != -1L) { "La importación ya existe" }
        val items = collection.tracks.sortedBy { it.originalPosition }.map { track ->
            val duplicate = track.originalPosition in duplicates
            val match = if (duplicate) null else PlaylistImportMatcher.localMatch(track, localSongs)
            val state = when {
                duplicate -> PlaylistImportItemState.DUPLICATE
                match?.status == ImportMatchStatus.IN_LIBRARY -> PlaylistImportItemState.IN_LIBRARY
                match?.status == ImportMatchStatus.PROBABLE_MATCH -> PlaylistImportItemState.PROBABLE_MATCH
                match?.status == ImportMatchStatus.REQUIRES_REVIEW -> PlaylistImportItemState.REQUIRES_REVIEW
                match?.status == ImportMatchStatus.METADATA_ERROR -> PlaylistImportItemState.METADATA_ERROR
                else -> PlaylistImportItemState.MISSING
            }
            PlaylistImportItemEntity(
                id = "$importId:${track.originalPosition}",
                importId = importId,
                sourceId = track.sourceId,
                title = track.title.trim(),
                artists = track.artists.joinToString(ARTIST_SEPARATOR),
                album = track.album.trim(),
                durationMs = track.durationMs.coerceAtLeast(0),
                trackNumber = track.trackNumber,
                discNumber = track.discNumber,
                isrc = track.isrc,
                artworkUrl = track.artworkUrl,
                originalPosition = track.originalPosition,
                selected = !duplicate && state != PlaylistImportItemState.METADATA_ERROR,
                state = state.name,
                localSongId = match?.songId,
                errorMessage = when (state) {
                    PlaylistImportItemState.METADATA_ERROR -> "Falta el título de la canción"
                    PlaylistImportItemState.REQUIRES_REVIEW -> "Falta el artista; revisa la coincidencia"
                    else -> null
                },
                updatedAt = now,
            )
        }
        dao.insertItems(items)
        importId
    }

    private fun ImportCandidate.toEntity(itemId: String, rank: Int, selected: Boolean) =
        PlaylistImportCandidateEntity(
            id = "$itemId:$providerId:$remoteTrackId",
            importItemId = itemId,
            providerId = providerId,
            remoteTrackId = remoteTrackId,
            title = title,
            artist = artist,
            album = album,
            durationMs = durationMs,
            artworkUrl = artworkUrl,
            externalUrl = externalUrl,
            score = score,
            confidence = confidence.name,
            rank = rank,
            selected = selected,
        )

    companion object {
        const val ARTIST_SEPARATOR = "\u001F"
        val TERMINAL_ITEM_STATES = setOf(
            PlaylistImportItemState.IN_LIBRARY.name,
            PlaylistImportItemState.COMPLETED.name,
            PlaylistImportItemState.DUPLICATE.name,
            PlaylistImportItemState.OMITTED.name,
        )
        val ACTIVE_IMPORT_STATES = listOf(
            PlaylistImportState.RUNNING.name,
            PlaylistImportState.PAUSED.name,
            PlaylistImportState.RESOLVING.name,
        )

        fun artists(item: PlaylistImportItemEntity): List<String> =
            item.artists.split(ARTIST_SEPARATOR).filter(String::isNotBlank)

        fun isProtectedPlaylistName(name: String): Boolean =
            name.trim().equals(PlaylistNames.TUS_ME_GUSTA, ignoreCase = true)
    }
}
