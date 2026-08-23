package com.polentita.music.core.database

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.polentita.music.domain.playlistimport.PlaylistImportItemState
import com.polentita.music.domain.playlistimport.PlaylistImportState

@Entity(
    tableName = "playlist_imports",
    foreignKeys = [
        ForeignKey(
            entity = PlaylistEntity::class,
            parentColumns = ["id"],
            childColumns = ["localPlaylistId"],
            onDelete = ForeignKey.SET_NULL,
            onUpdate = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["source", "sourceId"], unique = true),
        Index(value = ["localPlaylistId"]),
        Index(value = ["state"]),
        Index(value = ["updatedAt"]),
    ],
)
data class PlaylistImportEntity(
    @PrimaryKey val id: String,
    val source: String,
    val sourceId: String,
    val sourceUrl: String? = null,
    val type: String,
    val name: String,
    val description: String = "",
    val artworkUrl: String? = null,
    val owner: String? = null,
    val totalTracks: Int,
    val localPlaylistId: Long? = null,
    val state: String = PlaylistImportState.ANALYZED.name,
    val isPaused: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val completedAt: Long? = null,
    val errorMessage: String? = null,
    val completionNotified: Boolean = false,
)

@Entity(
    tableName = "playlist_import_items",
    foreignKeys = [
        ForeignKey(
            entity = PlaylistImportEntity::class,
            parentColumns = ["id"],
            childColumns = ["importId"],
            onDelete = ForeignKey.CASCADE,
            onUpdate = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = SongEntity::class,
            parentColumns = ["id"],
            childColumns = ["localSongId"],
            onDelete = ForeignKey.SET_NULL,
            onUpdate = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["importId", "originalPosition"], unique = true),
        Index(value = ["importId", "state"]),
        Index(value = ["localSongId"]),
        Index(value = ["downloadId"], unique = true),
    ],
)
data class PlaylistImportItemEntity(
    @PrimaryKey val id: String,
    val importId: String,
    val sourceId: String,
    val title: String,
    val artists: String,
    val album: String = "",
    val durationMs: Long = 0,
    val trackNumber: Int? = null,
    val discNumber: Int? = null,
    val isrc: String? = null,
    val artworkUrl: String? = null,
    val originalPosition: Int,
    val selected: Boolean = true,
    val state: String = PlaylistImportItemState.MISSING.name,
    val localSongId: Long? = null,
    val downloadId: String? = null,
    val errorMessage: String? = null,
    val attemptCount: Int = 0,
    val updatedAt: Long = System.currentTimeMillis(),
)

@Entity(
    tableName = "playlist_import_candidates",
    foreignKeys = [
        ForeignKey(
            entity = PlaylistImportItemEntity::class,
            parentColumns = ["id"],
            childColumns = ["importItemId"],
            onDelete = ForeignKey.CASCADE,
            onUpdate = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["importItemId", "rank"], unique = true),
        Index(value = ["importItemId", "providerId", "remoteTrackId"], unique = true),
        Index(value = ["selected"]),
    ],
)
data class PlaylistImportCandidateEntity(
    @PrimaryKey val id: String,
    val importItemId: String,
    val providerId: String,
    val remoteTrackId: String,
    val title: String,
    val artist: String,
    val album: String = "",
    val durationMs: Long = 0,
    val artworkUrl: String? = null,
    val externalUrl: String,
    val score: Double,
    val confidence: String,
    val rank: Int,
    val selected: Boolean = false,
)
