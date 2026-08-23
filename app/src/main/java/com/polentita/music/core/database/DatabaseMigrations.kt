package com.polentita.music.core.database

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

object DatabaseMigrations {
    val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "ALTER TABLE downloads ADD COLUMN providerId TEXT NOT NULL DEFAULT '${DownloadProvider.DIRECT}'",
            )
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS remote_references (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    providerId TEXT NOT NULL,
                    remoteTrackId TEXT NOT NULL,
                    title TEXT NOT NULL,
                    artist TEXT NOT NULL,
                    album TEXT NOT NULL,
                    durationMs INTEGER NOT NULL,
                    thumbnailUrl TEXT,
                    externalUrl TEXT NOT NULL,
                    license TEXT NOT NULL,
                    attribution TEXT,
                    dateSaved INTEGER NOT NULL
                )
                """.trimIndent(),
            )
            db.execSQL(
                "CREATE UNIQUE INDEX IF NOT EXISTS index_remote_references_providerId_remoteTrackId " +
                    "ON remote_references (providerId, remoteTrackId)",
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS index_remote_references_dateSaved ON remote_references (dateSaved)",
            )
        }
    }

    val MIGRATION_2_3 = object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE downloads ADD COLUMN thumbnailUrl TEXT")
        }
    }

    val MIGRATION_3_4 = object : Migration(3, 4) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE songs ADD COLUMN isrc TEXT")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_songs_isrc ON songs (isrc)")
            db.execSQL("ALTER TABLE downloads ADD COLUMN isrc TEXT")
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS playlist_imports (
                    id TEXT PRIMARY KEY NOT NULL,
                    source TEXT NOT NULL,
                    sourceId TEXT NOT NULL,
                    sourceUrl TEXT,
                    type TEXT NOT NULL,
                    name TEXT NOT NULL,
                    description TEXT NOT NULL,
                    artworkUrl TEXT,
                    owner TEXT,
                    totalTracks INTEGER NOT NULL,
                    localPlaylistId INTEGER,
                    state TEXT NOT NULL,
                    isPaused INTEGER NOT NULL,
                    createdAt INTEGER NOT NULL,
                    updatedAt INTEGER NOT NULL,
                    completedAt INTEGER,
                    errorMessage TEXT,
                    completionNotified INTEGER NOT NULL,
                    FOREIGN KEY(localPlaylistId) REFERENCES playlists(id) ON UPDATE CASCADE ON DELETE SET NULL
                )
                """.trimIndent(),
            )
            db.execSQL(
                "CREATE UNIQUE INDEX IF NOT EXISTS index_playlist_imports_source_sourceId " +
                    "ON playlist_imports (source, sourceId)",
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS index_playlist_imports_localPlaylistId ON playlist_imports (localPlaylistId)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_playlist_imports_state ON playlist_imports (state)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_playlist_imports_updatedAt ON playlist_imports (updatedAt)")
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS playlist_import_items (
                    id TEXT PRIMARY KEY NOT NULL,
                    importId TEXT NOT NULL,
                    sourceId TEXT NOT NULL,
                    title TEXT NOT NULL,
                    artists TEXT NOT NULL,
                    album TEXT NOT NULL,
                    durationMs INTEGER NOT NULL,
                    trackNumber INTEGER,
                    discNumber INTEGER,
                    isrc TEXT,
                    artworkUrl TEXT,
                    originalPosition INTEGER NOT NULL,
                    selected INTEGER NOT NULL,
                    state TEXT NOT NULL,
                    localSongId INTEGER,
                    downloadId TEXT,
                    errorMessage TEXT,
                    attemptCount INTEGER NOT NULL,
                    updatedAt INTEGER NOT NULL,
                    FOREIGN KEY(importId) REFERENCES playlist_imports(id) ON UPDATE CASCADE ON DELETE CASCADE,
                    FOREIGN KEY(localSongId) REFERENCES songs(id) ON UPDATE CASCADE ON DELETE SET NULL
                )
                """.trimIndent(),
            )
            db.execSQL(
                "CREATE UNIQUE INDEX IF NOT EXISTS index_playlist_import_items_importId_originalPosition " +
                    "ON playlist_import_items (importId, originalPosition)",
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS index_playlist_import_items_importId_state " +
                    "ON playlist_import_items (importId, state)",
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS index_playlist_import_items_localSongId ON playlist_import_items (localSongId)")
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_playlist_import_items_downloadId ON playlist_import_items (downloadId)")
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS playlist_import_candidates (
                    id TEXT PRIMARY KEY NOT NULL,
                    importItemId TEXT NOT NULL,
                    providerId TEXT NOT NULL,
                    remoteTrackId TEXT NOT NULL,
                    title TEXT NOT NULL,
                    artist TEXT NOT NULL,
                    album TEXT NOT NULL,
                    durationMs INTEGER NOT NULL,
                    artworkUrl TEXT,
                    externalUrl TEXT NOT NULL,
                    score REAL NOT NULL,
                    confidence TEXT NOT NULL,
                    rank INTEGER NOT NULL,
                    selected INTEGER NOT NULL,
                    FOREIGN KEY(importItemId) REFERENCES playlist_import_items(id) ON UPDATE CASCADE ON DELETE CASCADE
                )
                """.trimIndent(),
            )
            db.execSQL(
                "CREATE UNIQUE INDEX IF NOT EXISTS index_playlist_import_candidates_importItemId_rank " +
                    "ON playlist_import_candidates (importItemId, rank)",
            )
            db.execSQL(
                "CREATE UNIQUE INDEX IF NOT EXISTS index_playlist_import_candidates_importItemId_providerId_remoteTrackId " +
                    "ON playlist_import_candidates (importItemId, providerId, remoteTrackId)",
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS index_playlist_import_candidates_selected ON playlist_import_candidates (selected)")
        }
    }
}
