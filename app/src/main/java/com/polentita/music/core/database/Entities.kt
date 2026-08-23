package com.polentita.music.core.database

import androidx.room.Entity
import androidx.room.ColumnInfo
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "albums",
    indices = [
        Index(value = ["name", "artist"], unique = true),
        Index(value = ["dateCreated"]),
    ],
)
data class AlbumEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val artist: String = "",
    val year: Int? = null,
    val coverUri: String? = null,
    val dateCreated: Long = System.currentTimeMillis(),
)

@Entity(
    tableName = "songs",
    foreignKeys = [
        ForeignKey(
            entity = AlbumEntity::class,
            parentColumns = ["id"],
            childColumns = ["albumId"],
            onDelete = ForeignKey.SET_NULL,
            onUpdate = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["contentUri"], unique = true),
        Index(value = ["checksum"]),
        Index(value = ["title"]),
        Index(value = ["artist"]),
        Index(value = ["albumId"]),
        Index(value = ["albumName"]),
        Index(value = ["genre"]),
        Index(value = ["year"]),
        Index(value = ["dateAdded"]),
        Index(value = ["playCount"]),
        Index(value = ["isrc"]),
    ],
)
data class SongEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val artist: String = "",
    val albumId: Long? = null,
    val albumName: String = "",
    val genre: String = "",
    val year: Int? = null,
    val trackNumber: Int? = null,
    val discNumber: Int? = null,
    val durationMs: Long = 0,
    val contentUri: String,
    val originalFileName: String,
    val displayFileName: String,
    val mimeType: String,
    val fileSize: Long = 0,
    val coverUri: String? = null,
    val sourceType: String = SourceType.IMPORTED.name,
    val sourceUrl: String? = null,
    val dateAdded: Long = System.currentTimeMillis(),
    val dateModified: Long = System.currentTimeMillis(),
    val lastPlayedAt: Long? = null,
    val playCount: Long = 0,
    val isFavorite: Boolean = false,
    val isAvailable: Boolean = true,
    val checksum: String,
    val isrc: String? = null,
)

@Entity(
    tableName = "playlists",
    indices = [Index(value = ["name"]), Index(value = ["dateModified"])],
)
data class PlaylistEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val description: String = "",
    val coverUri: String? = null,
    val dateCreated: Long = System.currentTimeMillis(),
    val dateModified: Long = System.currentTimeMillis(),
)

@Entity(
    tableName = "playlist_songs",
    primaryKeys = ["playlistId", "songId"],
    foreignKeys = [
        ForeignKey(
            entity = PlaylistEntity::class,
            parentColumns = ["id"],
            childColumns = ["playlistId"],
            onDelete = ForeignKey.CASCADE,
            onUpdate = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = SongEntity::class,
            parentColumns = ["id"],
            childColumns = ["songId"],
            onDelete = ForeignKey.CASCADE,
            onUpdate = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["songId"]),
        Index(value = ["playlistId", "position"], unique = true),
    ],
)
data class PlaylistSongCrossRef(
    val playlistId: Long,
    val songId: Long,
    val position: Int,
    val dateAdded: Long = System.currentTimeMillis(),
)

@Entity(
    tableName = "downloads",
    indices = [Index(value = ["status"]), Index(value = ["createdAt"])],
)
data class DownloadEntity(
    @PrimaryKey val id: String,
    val sourceUrl: String,
    @ColumnInfo(defaultValue = "'direct'") val providerId: String = DownloadProvider.DIRECT,
    val thumbnailUrl: String? = null,
    val destinationUri: String? = null,
    val title: String,
    val artist: String = "",
    val album: String = "",
    val status: String = DownloadStatus.PENDING.name,
    val progress: Int = 0,
    val bytesDownloaded: Long = 0,
    val totalBytes: Long = -1,
    val speedBytesPerSecond: Long = 0,
    val errorMessage: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val completedAt: Long? = null,
    val isrc: String? = null,
)

@Entity(
    tableName = "playback_history",
    foreignKeys = [
        ForeignKey(
            entity = SongEntity::class,
            parentColumns = ["id"],
            childColumns = ["songId"],
            onDelete = ForeignKey.CASCADE,
            onUpdate = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["songId"]), Index(value = ["playedAt"])],
)
data class PlaybackHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val songId: Long,
    val playedAt: Long = System.currentTimeMillis(),
    val completed: Boolean = false,
    val playbackPositionMs: Long = 0,
)

enum class SourceType { IMPORTED, DOWNLOADED, SCANNED }
enum class DownloadStatus { PENDING, DOWNLOADING, VALIDATING, SAVING, PAUSED, COMPLETED, CANCELLED, FAILED }

object DownloadProvider {
    const val DIRECT = "direct"
    const val YT_DLP = "yt-dlp"
}

@Entity(
    tableName = "remote_references",
    indices = [
        Index(value = ["providerId", "remoteTrackId"], unique = true),
        Index(value = ["dateSaved"]),
    ],
)
data class RemoteReferenceEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val providerId: String,
    val remoteTrackId: String,
    val title: String,
    val artist: String,
    val album: String,
    val durationMs: Long,
    val thumbnailUrl: String?,
    val externalUrl: String,
    val license: String,
    val attribution: String?,
    val dateSaved: Long = System.currentTimeMillis(),
)
