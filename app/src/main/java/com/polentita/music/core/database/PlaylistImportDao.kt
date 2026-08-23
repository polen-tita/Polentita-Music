package com.polentita.music.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface PlaylistImportDao {
    @Query("SELECT * FROM playlist_imports ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<PlaylistImportEntity>>

    @Query("SELECT * FROM playlist_imports WHERE id = :id LIMIT 1")
    fun observeById(id: String): Flow<PlaylistImportEntity?>

    @Query("SELECT * FROM playlist_import_items WHERE importId = :importId ORDER BY originalPosition")
    fun observeItems(importId: String): Flow<List<PlaylistImportItemEntity>>

    @Query(
        """
        SELECT c.* FROM playlist_import_candidates c
        INNER JOIN playlist_import_items i ON i.id = c.importItemId
        WHERE i.importId = :importId
        ORDER BY i.originalPosition, c.rank
        """,
    )
    fun observeCandidates(importId: String): Flow<List<PlaylistImportCandidateEntity>>

    @Query("SELECT * FROM playlist_imports WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): PlaylistImportEntity?

    @Query("SELECT * FROM playlist_imports WHERE source = :source AND sourceId = :sourceId LIMIT 1")
    suspend fun getBySource(source: String, sourceId: String): PlaylistImportEntity?

    @Query("SELECT * FROM playlist_imports WHERE state IN (:states) ORDER BY createdAt")
    suspend fun getByStates(states: List<String>): List<PlaylistImportEntity>

    @Query("SELECT * FROM playlist_import_items WHERE importId = :importId ORDER BY originalPosition")
    suspend fun getItems(importId: String): List<PlaylistImportItemEntity>

    @Query("SELECT * FROM playlist_import_items WHERE id = :id LIMIT 1")
    suspend fun getItem(id: String): PlaylistImportItemEntity?

    @Query("SELECT * FROM playlist_import_candidates WHERE importItemId = :itemId ORDER BY rank")
    suspend fun getCandidates(itemId: String): List<PlaylistImportCandidateEntity>

    @Query("SELECT * FROM playlist_import_candidates WHERE importItemId = :itemId AND selected = 1 LIMIT 1")
    suspend fun getSelectedCandidate(itemId: String): PlaylistImportCandidateEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(imported: PlaylistImportEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertItems(items: List<PlaylistImportItemEntity>): List<Long>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun replaceCandidates(candidates: List<PlaylistImportCandidateEntity>)

    @Update
    suspend fun update(imported: PlaylistImportEntity)

    @Update
    suspend fun updateItem(item: PlaylistImportItemEntity)

    @Update
    suspend fun updateItems(items: List<PlaylistImportItemEntity>)

    @Query("DELETE FROM playlist_import_candidates WHERE importItemId = :itemId")
    suspend fun deleteCandidates(itemId: String)

    @Query("UPDATE playlist_import_candidates SET selected = CASE WHEN id = :candidateId THEN 1 ELSE 0 END WHERE importItemId = :itemId")
    suspend fun selectCandidate(itemId: String, candidateId: String)

    @Query("UPDATE playlist_imports SET completionNotified = 1 WHERE id = :importId")
    suspend fun markCompletionNotified(importId: String)
}
