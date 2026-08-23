package com.polentita.music.data.artwork

import androidx.room.withTransaction
import com.polentita.music.core.database.AlbumEntity
import com.polentita.music.core.database.PolentitaDatabase
import com.polentita.music.domain.artwork.ArtworkChoice
import com.polentita.music.domain.artwork.ArtworkEditingRepository
import com.polentita.music.domain.model.Song
import com.polentita.music.domain.model.toEntity
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DefaultArtworkEditingRepository @Inject constructor(
    private val database: PolentitaDatabase,
    private val artworkStore: ManagedArtworkStore,
) : ArtworkEditingRepository {
    override suspend fun saveSong(song: Song, artworkChoice: ArtworkChoice) {
        if (artworkChoice == ArtworkChoice.Unchanged) {
            database.songDao().update(song.copy(dateModified = System.currentTimeMillis()).toEntity())
            return
        }
        val preparedCover = artworkStore.prepare(artworkChoice)
        val previousCovers = linkedSetOf<String>()
        try {
            database.withTransaction {
                val songDao = database.songDao()
                val storedSong = requireNotNull(songDao.getById(song.id)) {
                    "La canción ya no existe"
                }
                storedSong.coverUri?.let(previousCovers::add)

                val now = System.currentTimeMillis()
                songDao.update(
                    song.copy(
                        coverUri = preparedCover,
                        dateModified = now,
                    ).toEntity(),
                )
            }
        } catch (error: Throwable) {
            if (preparedCover != null) deleteWhenUnreferenced(preparedCover)
            throw error
        }
        previousCovers.forEach { oldCover ->
            if (oldCover != preparedCover) deleteWhenUnreferenced(oldCover)
        }
    }

    override suspend fun saveAlbum(album: AlbumEntity, artworkChoice: ArtworkChoice) {
        if (artworkChoice == ArtworkChoice.Unchanged) {
            database.albumDao().update(album)
            return
        }
        val preparedCover = artworkStore.prepare(artworkChoice)
        val previousCovers = linkedSetOf<String>()
        try {
            database.withTransaction {
                val albumDao = database.albumDao()
                val storedAlbum = requireNotNull(albumDao.getById(album.id)) {
                    "El álbum ya no existe"
                }
                storedAlbum.coverUri?.let(previousCovers::add)

                val now = System.currentTimeMillis()
                albumDao.updateEntity(album.copy(coverUri = preparedCover))
                albumDao.updateSongAlbumName(album.id, album.name, now)
            }
        } catch (error: Throwable) {
            if (preparedCover != null) deleteWhenUnreferenced(preparedCover)
            throw error
        }
        previousCovers.forEach { oldCover ->
            if (oldCover != preparedCover) deleteWhenUnreferenced(oldCover)
        }
    }

    private suspend fun deleteWhenUnreferenced(uri: String) {
        val referenced = runCatching {
            database.withTransaction {
                database.songDao().countCoverReferences(uri) +
                    database.albumDao().countCoverReferences(uri) +
                    database.playlistDao().countCoverReferences(uri) > 0
            }
        }.getOrElse { return }
        if (!referenced) runCatching { artworkStore.deleteIfManaged(uri) }
    }
}
