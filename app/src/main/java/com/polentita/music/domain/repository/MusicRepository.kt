package com.polentita.music.domain.repository

import android.net.Uri
import com.polentita.music.core.database.AlbumEntity
import com.polentita.music.core.database.PlaylistEntity
import com.polentita.music.core.database.PlaylistSummary
import com.polentita.music.domain.model.Song
import com.polentita.music.domain.model.SongFilter
import com.polentita.music.domain.model.SongSort
import kotlinx.coroutines.flow.Flow

data class ImportSummary(
    val imported: Int,
    val duplicates: Int,
    val failed: Int,
    val errors: List<String> = emptyList(),
)

data class ScanSummary(
    val added: Int,
    val restored: Int,
    val missing: Int,
    val failed: Int,
)

interface MusicRepository {
    fun observeSongs(): Flow<List<Song>>
    fun observeSong(id: Long): Flow<Song?>
    fun search(query: String, filter: SongFilter, sort: SongSort, ascending: Boolean): Flow<List<Song>>
    fun observeFavorites(): Flow<List<Song>>
    fun observeRecentlyAdded(): Flow<List<Song>>
    fun observeMostPlayed(): Flow<List<Song>>
    fun observeRecentHistory(): Flow<List<Song>>
    fun observeArtists(): Flow<List<String>>
    fun observeArtistSongs(artist: String): Flow<List<Song>>
    fun observeAlbums(): Flow<List<AlbumEntity>>
    fun observeAlbum(id: Long): Flow<AlbumEntity?>
    fun observeAlbumSongs(id: Long): Flow<List<Song>>
    fun observePlaylists(): Flow<List<PlaylistSummary>>
    fun observePlaylist(id: Long): Flow<PlaylistEntity?>
    fun observePlaylistSongs(id: Long): Flow<List<Song>>

    suspend fun getSong(id: Long): Song?
    suspend fun getSongs(ids: List<Long>): List<Song>
    suspend fun findSongByUri(uri: String): Song?
    suspend fun findSongByChecksum(checksum: String): Song?
    suspend fun findAvailableSongByMetadata(
        title: String,
        artist: String,
        isrc: String? = null,
    ): Song?
    suspend fun importFiles(uris: List<Uri>): ImportSummary
    suspend fun registerDownloadedFile(
        uri: Uri,
        title: String,
        artist: String,
        album: String,
        sourceUrl: String,
        downloadedCoverUri: String? = null,
        preferredAlbumId: Long? = null,
        useMetadataAlbumWhenBlank: Boolean = true,
        isrc: String? = null,
    ): Long
    suspend fun scanLibrary(): ScanSummary
    suspend fun updateSong(song: Song)
    suspend fun clearArtist(artist: String): Int
    suspend fun renameArtist(artist: String, newName: String): Int
    suspend fun toggleFavorite(songId: Long)
    suspend fun syncFavoritesPlaylist()
    suspend fun removeSong(songId: Long, deleteFile: Boolean): Boolean
    suspend fun renameSongFile(songId: Long, newName: String): Boolean
    suspend fun createAlbum(album: AlbumEntity): Long
    suspend fun updateAlbum(album: AlbumEntity)
    suspend fun deleteAlbum(album: AlbumEntity)
    suspend fun cleanEmptyAlbums(): Int
    suspend fun createPlaylist(playlist: PlaylistEntity): Long
    suspend fun updatePlaylist(playlist: PlaylistEntity)
    suspend fun deletePlaylist(playlist: PlaylistEntity)
    suspend fun addSongsToPlaylist(playlistId: Long, songIds: List<Long>)
    suspend fun removeSongFromPlaylist(playlistId: Long, songId: Long)
    suspend fun reorderPlaylist(playlistId: Long, songIds: List<Long>)
}
