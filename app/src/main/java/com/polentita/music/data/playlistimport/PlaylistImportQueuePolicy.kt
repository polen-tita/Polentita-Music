package com.polentita.music.data.playlistimport

import com.polentita.music.core.database.PlaylistImportItemEntity
import com.polentita.music.core.network.NetworkAccessState
import com.polentita.music.domain.playlistimport.PlaylistImportItemState
import com.polentita.music.domain.playlistimport.PlaylistImportState

object PlaylistImportQueuePolicy {
    fun canProcess(network: NetworkAccessState): Boolean = network.downloadAllowed

    fun recoverAfterRestart(item: PlaylistImportItemEntity, now: Long): PlaylistImportItemEntity = when {
        item.state == PlaylistImportItemState.SEARCHING.name -> item.copy(
            state = PlaylistImportItemState.MISSING.name,
            errorMessage = "La búsqueda se interrumpió; puedes reintentarlo",
            updatedAt = now,
        )
        item.state == PlaylistImportItemState.PREPARING.name && item.downloadId == null -> item.copy(
            state = PlaylistImportItemState.PENDING.name,
            updatedAt = now,
        )
        else -> item
    }

    fun cancelRemaining(
        items: List<PlaylistImportItemEntity>,
        now: Long,
    ): List<PlaylistImportItemEntity> = items.map { item ->
        if (item.state in PlaylistImportSnapshot.COMPLETED_ITEM_STATES) item else item.copy(
            selected = false,
            state = PlaylistImportItemState.OMITTED.name,
            errorMessage = "Importación cancelada",
            updatedAt = now,
        )
    }

    fun resetForNextCandidate(
        item: PlaylistImportItemEntity,
        now: Long,
    ): PlaylistImportItemEntity = item.copy(
        state = PlaylistImportItemState.PENDING.name,
        downloadId = null,
        errorMessage = null,
        attemptCount = 0,
        updatedAt = now,
    )

    fun completionState(items: List<PlaylistImportItemEntity>): PlaylistImportState =
        if (items.all { it.state in PlaylistImportSnapshot.COMPLETED_ITEM_STATES }) {
            PlaylistImportState.COMPLETED
        } else {
            PlaylistImportState.PARTIAL
        }

    fun nextRunnable(items: List<PlaylistImportItemEntity>): PlaylistImportItemEntity? =
        items.sortedBy(PlaylistImportItemEntity::originalPosition).firstOrNull {
            it.selected && it.state == PlaylistImportItemState.PENDING.name
        }
}
