package com.polentita.music.core.database

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface SongDao {
    @Query("SELECT * FROM songs WHERE isAvailable = 1 ORDER BY title COLLATE NOCASE")
    fun observeAvailable(): Flow<List<SongEntity>>

    @Query("SELECT * FROM songs ORDER BY title COLLATE NOCASE")
    fun observeAll(): Flow<List<SongEntity>>

    @Query("SELECT * FROM songs WHERE isAvailable = 1 ORDER BY id")
    suspend fun getAvailable(): List<SongEntity>

    @Query("SELECT * FROM songs WHERE id = :id LIMIT 1")
    fun observeById(id: Long): Flow<SongEntity?>

    @Query("SELECT * FROM songs WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): SongEntity?

    @Query("SELECT * FROM songs WHERE id IN (:ids)")
    suspend fun getByIds(ids: List<Long>): List<SongEntity>

    @Query("SELECT * FROM songs WHERE contentUri = :uri LIMIT 1")
    suspend fun findByUri(uri: String): SongEntity?

    @Query("SELECT * FROM songs WHERE checksum = :checksum LIMIT 1")
    suspend fun findByChecksum(checksum: String): SongEntity?

    @Query(
        """
        SELECT DISTINCT songs.* FROM songs
        WHERE (
            TRIM(:query) = ''
            OR LOWER(COALESCE(title, '')) LIKE '%' || LOWER(TRIM(:query)) || '%'
            OR LOWER(COALESCE(artist, '')) LIKE '%' || LOWER(TRIM(:query)) || '%'
            OR LOWER(COALESCE(albumName, '')) LIKE '%' || LOWER(TRIM(:query)) || '%'
            OR LOWER(COALESCE(genre, '')) LIKE '%' || LOWER(TRIM(:query)) || '%'
            OR LOWER(COALESCE(originalFileName, '')) LIKE '%' || LOWER(TRIM(:query)) || '%'
            OR LOWER(COALESCE(displayFileName, '')) LIKE '%' || LOWER(TRIM(:query)) || '%'
            OR EXISTS (
                SELECT 1 FROM playlist_songs ps
                JOIN playlists p ON p.id = ps.playlistId
                WHERE ps.songId = songs.id
                    AND LOWER(COALESCE(p.name, '')) LIKE '%' || LOWER(TRIM(:query)) || '%'
            )
        )
        AND (:favoriteOnly = 0 OR isFavorite = 1)
        AND (:sourceType IS NULL OR songs.sourceType = :sourceType)
        AND (:coverMode = 0 OR (:coverMode = 1 AND coverUri IS NOT NULL) OR (:coverMode = 2 AND coverUri IS NULL))
        AND (:availableMode = 0 OR (:availableMode = 1 AND isAvailable = 1) OR (:availableMode = 2 AND isAvailable = 0))
        AND (:artist IS NULL OR LOWER(songs.artist) = LOWER(TRIM(:artist)))
        AND (:album IS NULL OR LOWER(songs.albumName) = LOWER(TRIM(:album)))
        AND (:genre IS NULL OR LOWER(songs.genre) = LOWER(TRIM(:genre)))
        AND (:year IS NULL OR songs.year = :year)
        ORDER BY
            CASE WHEN :ascending = 1 AND :sort = 'TITLE' THEN title END COLLATE NOCASE ASC,
            CASE WHEN :ascending = 0 AND :sort = 'TITLE' THEN title END COLLATE NOCASE DESC,
            CASE WHEN :ascending = 1 AND :sort = 'ARTIST' THEN artist END COLLATE NOCASE ASC,
            CASE WHEN :ascending = 0 AND :sort = 'ARTIST' THEN artist END COLLATE NOCASE DESC,
            CASE WHEN :ascending = 1 AND :sort = 'ALBUM' THEN albumName END COLLATE NOCASE ASC,
            CASE WHEN :ascending = 0 AND :sort = 'ALBUM' THEN albumName END COLLATE NOCASE DESC,
            CASE WHEN :ascending = 1 AND :sort = 'DATE_ADDED' THEN dateAdded END ASC,
            CASE WHEN :ascending = 0 AND :sort = 'DATE_ADDED' THEN dateAdded END DESC,
            CASE WHEN :ascending = 1 AND :sort = 'DATE_MODIFIED' THEN dateModified END ASC,
            CASE WHEN :ascending = 0 AND :sort = 'DATE_MODIFIED' THEN dateModified END DESC,
            CASE WHEN :ascending = 1 AND :sort = 'DURATION' THEN durationMs END ASC,
            CASE WHEN :ascending = 0 AND :sort = 'DURATION' THEN durationMs END DESC,
            CASE WHEN :ascending = 1 AND :sort = 'PLAY_COUNT' THEN playCount END ASC,
            CASE WHEN :ascending = 0 AND :sort = 'PLAY_COUNT' THEN playCount END DESC,
            CASE WHEN :ascending = 1 AND :sort = 'LAST_PLAYED' THEN lastPlayedAt END ASC,
            CASE WHEN :ascending = 0 AND :sort = 'LAST_PLAYED' THEN lastPlayedAt END DESC,
            CASE WHEN :ascending = 1 THEN title END COLLATE NOCASE ASC,
            CASE WHEN :ascending = 0 THEN title END COLLATE NOCASE DESC,
            CASE WHEN :ascending = 1 THEN id END ASC,
            CASE WHEN :ascending = 0 THEN id END DESC
        """,
    )
    fun search(
        query: String,
        favoriteOnly: Boolean = false,
        sourceType: String? = null,
        coverMode: Int = 0,
        availableMode: Int = 0,
        artist: String? = null,
        album: String? = null,
        genre: String? = null,
        year: Int? = null,
        sort: String = "TITLE",
        ascending: Boolean = true,
    ): Flow<List<SongEntity>>

    @Query("SELECT * FROM songs WHERE isFavorite = 1 AND isAvailable = 1 ORDER BY dateModified DESC LIMIT :limit")
    fun observeFavorites(limit: Int = 30): Flow<List<SongEntity>>

    @Query("SELECT id FROM songs WHERE isFavorite = 1 ORDER BY dateModified ASC, id ASC")
    suspend fun favoriteIds(): List<Long>

    @Query("SELECT * FROM songs WHERE isAvailable = 1 ORDER BY dateAdded DESC LIMIT :limit")
    fun observeRecentlyAdded(limit: Int = 30): Flow<List<SongEntity>>

    @Query("SELECT * FROM songs WHERE isAvailable = 1 AND playCount > 0 ORDER BY playCount DESC, lastPlayedAt DESC LIMIT :limit")
    fun observeMostPlayed(limit: Int = 30): Flow<List<SongEntity>>

    @Query("SELECT DISTINCT artist FROM songs WHERE artist != '' ORDER BY artist COLLATE NOCASE")
    fun observeArtists(): Flow<List<String>>

    @Query("SELECT * FROM songs WHERE artist = :artist ORDER BY albumName COLLATE NOCASE, trackNumber, title COLLATE NOCASE")
    fun observeByArtist(artist: String): Flow<List<SongEntity>>

    @Query("SELECT * FROM songs WHERE albumId = :albumId ORDER BY discNumber, trackNumber, title COLLATE NOCASE")
    fun observeByAlbum(albumId: Long): Flow<List<SongEntity>>

    @Query("SELECT contentUri FROM songs")
    suspend fun allContentUris(): List<String>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(song: SongEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(songs: List<SongEntity>): List<Long>

    @Update
    suspend fun update(song: SongEntity)

    @Query("SELECT coverUri FROM songs WHERE albumId = :albumId AND coverUri IS NOT NULL")
    suspend fun coverUrisForAlbum(albumId: Long): List<String>

    @Query(
        """
        UPDATE songs
        SET coverUri = :coverUri, dateModified = :now
        WHERE albumId = :albumId
        """,
    )
    suspend fun updateAlbumArtwork(albumId: Long, coverUri: String?, now: Long)

    @Query("SELECT COUNT(*) FROM songs WHERE coverUri = :coverUri")
    suspend fun countCoverReferences(coverUri: String): Int

    @Query("UPDATE songs SET artist = '', dateModified = :now WHERE artist = :artist")
    suspend fun clearArtist(artist: String, now: Long = System.currentTimeMillis()): Int

    @Query("UPDATE songs SET artist = :newArtist, dateModified = :now WHERE artist = :oldArtist")
    suspend fun renameArtist(
        oldArtist: String,
        newArtist: String,
        now: Long = System.currentTimeMillis(),
    ): Int

    @Delete
    suspend fun delete(song: SongEntity)

    @Query("DELETE FROM songs WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("UPDATE songs SET isFavorite = NOT isFavorite, dateModified = :now WHERE id = :id")
    suspend fun toggleFavorite(id: Long, now: Long = System.currentTimeMillis())

    @Query("UPDATE songs SET isAvailable = :available, dateModified = :now WHERE contentUri = :uri")
    suspend fun setAvailable(uri: String, available: Boolean, now: Long = System.currentTimeMillis())

    @Query("UPDATE songs SET isAvailable = 0, dateModified = :now")
    suspend fun markAllUnavailable(now: Long = System.currentTimeMillis())

    @Query(
        """
        UPDATE songs SET
            playCount = playCount + 1,
            lastPlayedAt = :playedAt,
            dateModified = :playedAt
        WHERE id = :songId
        """,
    )
    suspend fun recordPlay(songId: Long, playedAt: Long = System.currentTimeMillis())
}
