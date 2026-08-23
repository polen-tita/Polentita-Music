package com.polentita.music.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

data class DownloadStatusRow(
    val id: String,
    val status: String,
    val destinationUri: String?,
    val errorMessage: String?,
)

@Dao
interface DownloadDao {
    @Query("SELECT * FROM downloads ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<DownloadEntity>>

    @Query("SELECT * FROM downloads WHERE id IN (:ids) ORDER BY createdAt DESC")
    fun observeByIds(ids: List<String>): Flow<List<DownloadEntity>>

    @Query("SELECT id, status, destinationUri, errorMessage FROM downloads ORDER BY id")
    fun observeStatus(): Flow<List<DownloadStatusRow>>

    @Query("SELECT * FROM downloads WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): DownloadEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(download: DownloadEntity)

    @Update
    suspend fun update(download: DownloadEntity)

    @Query(
        """
        UPDATE downloads SET status = :status, progress = :progress,
            bytesDownloaded = :bytes, totalBytes = :total, speedBytesPerSecond = :speed,
            errorMessage = :error, destinationUri = COALESCE(:destinationUri, destinationUri),
            completedAt = :completedAt
        WHERE id = :id
        """,
    )
    suspend fun updateProgress(
        id: String,
        status: String,
        progress: Int,
        bytes: Long,
        total: Long,
        speed: Long,
        error: String? = null,
        destinationUri: String? = null,
        completedAt: Long? = null,
    )

    @Query("DELETE FROM downloads WHERE id = :id")
    suspend fun delete(id: String)

    @Query("UPDATE downloads SET sourceUrl = :safeUrl WHERE id = :id")
    suspend fun setSafeSourceUrl(id: String, safeUrl: String)

    @Query("UPDATE downloads SET thumbnailUrl = NULL WHERE id = :id")
    suspend fun clearThumbnailUrl(id: String)
}

@Dao
interface PlaybackHistoryDao {
    @Query(
        """
        SELECT s.* FROM songs s
        INNER JOIN (
            SELECT songId, MAX(playedAt) AS lastAt
            FROM playback_history
            GROUP BY songId
        ) h ON h.songId = s.id
        WHERE s.isAvailable = 1
        ORDER BY h.lastAt DESC
        LIMIT :limit
        """,
    )
    fun observeRecentSongs(limit: Int = 30): Flow<List<SongEntity>>

    @Query("SELECT * FROM playback_history ORDER BY playedAt DESC LIMIT :limit")
    fun observeRecent(limit: Int = 100): Flow<List<PlaybackHistoryEntity>>

    @Insert
    suspend fun insert(history: PlaybackHistoryEntity): Long

    @Query("DELETE FROM playback_history")
    suspend fun clear()
}
