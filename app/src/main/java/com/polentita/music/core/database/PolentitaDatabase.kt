package com.polentita.music.core.database

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        SongEntity::class,
        AlbumEntity::class,
        PlaylistEntity::class,
        PlaylistSongCrossRef::class,
        DownloadEntity::class,
        PlaybackHistoryEntity::class,
        RemoteReferenceEntity::class,
        PlaylistImportEntity::class,
        PlaylistImportItemEntity::class,
        PlaylistImportCandidateEntity::class,
    ],
    version = 4,
    exportSchema = true,
)
abstract class PolentitaDatabase : RoomDatabase() {
    abstract fun songDao(): SongDao
    abstract fun albumDao(): AlbumDao
    abstract fun playlistDao(): PlaylistDao
    abstract fun downloadDao(): DownloadDao
    abstract fun historyDao(): PlaybackHistoryDao
    abstract fun backupDao(): BackupDao
    abstract fun remoteReferenceDao(): RemoteReferenceDao
    abstract fun playlistImportDao(): PlaylistImportDao
}
