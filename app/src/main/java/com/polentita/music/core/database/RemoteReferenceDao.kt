package com.polentita.music.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface RemoteReferenceDao {
    @Query("SELECT * FROM remote_references ORDER BY dateSaved DESC")
    fun observeAll(): Flow<List<RemoteReferenceEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(reference: RemoteReferenceEntity): Long

    @Query("DELETE FROM remote_references WHERE providerId = :providerId AND remoteTrackId = :remoteTrackId")
    suspend fun delete(providerId: String, remoteTrackId: String)
}
