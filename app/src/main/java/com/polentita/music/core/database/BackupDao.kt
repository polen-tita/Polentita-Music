package com.polentita.music.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction

data class DatabaseBackup(
    val songs: List<SongEntity>,
    val albums: List<AlbumEntity>,
    val playlists: List<PlaylistEntity>,
    val playlistSongs: List<PlaylistSongCrossRef>,
    val history: List<PlaybackHistoryEntity>,
)

@Dao
abstract class BackupDao {
    @Query("SELECT * FROM songs")
    abstract suspend fun songs(): List<SongEntity>

    @Query("SELECT * FROM albums")
    abstract suspend fun albums(): List<AlbumEntity>

    @Query("SELECT * FROM playlists")
    abstract suspend fun playlists(): List<PlaylistEntity>

    @Query("SELECT * FROM playlist_songs")
    abstract suspend fun playlistSongs(): List<PlaylistSongCrossRef>

    @Query("SELECT * FROM playback_history")
    abstract suspend fun history(): List<PlaybackHistoryEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertSongs(items: List<SongEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertAlbums(items: List<AlbumEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertPlaylists(items: List<PlaylistEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertPlaylistSongs(items: List<PlaylistSongCrossRef>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertHistory(items: List<PlaybackHistoryEntity>)

    @Query("DELETE FROM playback_history")
    abstract suspend fun clearHistory()

    @Query("DELETE FROM playlist_songs")
    abstract suspend fun clearPlaylistSongs()

    @Query("DELETE FROM songs")
    abstract suspend fun clearSongs()

    @Query("DELETE FROM playlists")
    abstract suspend fun clearPlaylists()

    @Query("DELETE FROM albums")
    abstract suspend fun clearAlbums()

    @Transaction
    open suspend fun snapshot(includeHistory: Boolean): DatabaseBackup = DatabaseBackup(
        songs = songs(),
        albums = albums(),
        playlists = playlists(),
        playlistSongs = playlistSongs(),
        history = if (includeHistory) history() else emptyList(),
    )

    @Transaction
    open suspend fun restore(backup: DatabaseBackup) {
        clearHistory()
        clearPlaylistSongs()
        clearSongs()
        clearPlaylists()
        clearAlbums()
        insertAlbums(backup.albums)
        insertSongs(backup.songs)
        insertPlaylists(backup.playlists)
        insertPlaylistSongs(backup.playlistSongs)
        insertHistory(backup.history)
    }
}
