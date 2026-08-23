package com.polentita.music.core.database

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

data class PlaylistSummary(
    val id: Long,
    val name: String,
    val description: String,
    val coverUri: String?,
    val dateCreated: Long,
    val dateModified: Long,
    val songCount: Int,
    val durationMs: Long,
)

@Dao
interface AlbumDao {
    @Query("SELECT * FROM albums ORDER BY name COLLATE NOCASE")
    fun observeAll(): Flow<List<AlbumEntity>>

    @Query("SELECT * FROM albums WHERE id = :id LIMIT 1")
    fun observeById(id: Long): Flow<AlbumEntity?>

    @Query("SELECT * FROM albums WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): AlbumEntity?

    @Query("SELECT * FROM albums WHERE name = :name COLLATE NOCASE AND artist = :artist COLLATE NOCASE LIMIT 1")
    suspend fun findByNameArtist(name: String, artist: String): AlbumEntity?

    @Query("SELECT * FROM albums WHERE name = :name COLLATE NOCASE ORDER BY dateCreated LIMIT 1")
    suspend fun findFirstByName(name: String): AlbumEntity?

    @Insert
    suspend fun insert(album: AlbumEntity): Long

    @Update
    suspend fun updateEntity(album: AlbumEntity)

    @Query("SELECT COUNT(*) FROM albums WHERE coverUri = :coverUri")
    suspend fun countCoverReferences(coverUri: String): Int

    @Query(
        """
        UPDATE songs
        SET albumName = :albumName, dateModified = :now
        WHERE albumId = :albumId
        """,
    )
    suspend fun updateSongAlbumName(
        albumId: Long,
        albumName: String,
        now: Long = System.currentTimeMillis(),
    )

    @Transaction
    suspend fun update(album: AlbumEntity) {
        updateEntity(album)
        updateSongAlbumName(album.id, album.name)
    }

    @Delete
    suspend fun delete(album: AlbumEntity)

    @Query(
        """
        UPDATE songs
        SET albumId = NULL, albumName = '', dateModified = :now
        WHERE albumId = :albumId
        """,
    )
    suspend fun detachSongs(albumId: Long, now: Long = System.currentTimeMillis())

    @Query("DELETE FROM albums WHERE id = :albumId")
    suspend fun deleteById(albumId: Long): Int

    @Transaction
    suspend fun deletePreservingSongs(albumId: Long) {
        detachSongs(albumId)
        deleteById(albumId)
    }

    @Query(
        """
        DELETE FROM albums
        WHERE NOT EXISTS (
            SELECT 1 FROM songs WHERE songs.albumId = albums.id
        )
        """,
    )
    suspend fun deleteEmpty(): Int
}

@Dao
interface PlaylistDao {
    @Query(
        """
        SELECT p.id, p.name, p.description, p.coverUri, p.dateCreated, p.dateModified,
               COUNT(ps.songId) AS songCount, COALESCE(SUM(s.durationMs), 0) AS durationMs
        FROM playlists p
        LEFT JOIN playlist_songs ps ON ps.playlistId = p.id
        LEFT JOIN songs s ON s.id = ps.songId
        GROUP BY p.id
        ORDER BY p.dateModified DESC
        """,
    )
    fun observeSummaries(): Flow<List<PlaylistSummary>>

    @Query("SELECT * FROM playlists WHERE id = :id LIMIT 1")
    fun observeById(id: Long): Flow<PlaylistEntity?>

    @Query("SELECT * FROM playlists WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): PlaylistEntity?

    @Query("SELECT * FROM playlists WHERE name = :name COLLATE NOCASE ORDER BY dateCreated LIMIT 1")
    suspend fun findByName(name: String): PlaylistEntity?

    @Query(
        """
        SELECT s.* FROM songs s
        INNER JOIN playlist_songs ps ON ps.songId = s.id
        WHERE ps.playlistId = :playlistId
        ORDER BY ps.position ASC
        """,
    )
    fun observeSongs(playlistId: Long): Flow<List<SongEntity>>

    @Query("SELECT * FROM playlist_songs WHERE playlistId = :playlistId ORDER BY position")
    suspend fun getCrossRefs(playlistId: Long): List<PlaylistSongCrossRef>

    @Insert
    suspend fun insert(playlist: PlaylistEntity): Long

    @Update
    suspend fun update(playlist: PlaylistEntity)

    @Query("SELECT COUNT(*) FROM playlists WHERE coverUri = :coverUri")
    suspend fun countCoverReferences(coverUri: String): Int

    @Delete
    suspend fun delete(playlist: PlaylistEntity)

    @Query("SELECT COALESCE(MAX(position), -1) + 1 FROM playlist_songs WHERE playlistId = :playlistId")
    suspend fun nextPosition(playlistId: Long): Int

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertCrossRef(ref: PlaylistSongCrossRef): Long

    @Transaction
    suspend fun insertImportedSong(playlistId: Long, songId: Long, originalPosition: Int): Boolean {
        val inserted = insertCrossRef(
            PlaylistSongCrossRef(
                playlistId = playlistId,
                songId = songId,
                position = originalPosition.coerceAtLeast(0) * IMPORT_POSITION_STEP,
            ),
        )
        if (inserted != -1L) touch(playlistId)
        return inserted != -1L
    }

    @Query("DELETE FROM playlist_songs WHERE playlistId = :playlistId AND songId = :songId")
    suspend fun removeSong(playlistId: Long, songId: Long)

    @Query("DELETE FROM playlist_songs WHERE playlistId = :playlistId")
    suspend fun clear(playlistId: Long)

    @Query("UPDATE playlist_songs SET position = :position WHERE playlistId = :playlistId AND songId = :songId")
    suspend fun setPosition(playlistId: Long, songId: Long, position: Int)

    @Query("UPDATE playlists SET dateModified = :now WHERE id = :playlistId")
    suspend fun touch(playlistId: Long, now: Long = System.currentTimeMillis())

    @Transaction
    suspend fun replaceOrder(playlistId: Long, songIds: List<Long>) {
        clear(playlistId)
        songIds.forEachIndexed { index, songId ->
            insertCrossRef(PlaylistSongCrossRef(playlistId, songId, index))
        }
        touch(playlistId)
    }

    @Transaction
    suspend fun addSongs(playlistId: Long, songIds: List<Long>) {
        var position = nextPosition(playlistId)
        songIds.distinct().forEach { songId ->
            val inserted = insertCrossRef(PlaylistSongCrossRef(playlistId, songId, position))
            if (inserted != -1L) position++
        }
        touch(playlistId)
    }

    companion object {
        const val IMPORT_POSITION_STEP = 1_000
    }
}
