package com.polentita.music.core.database

import androidx.sqlite.db.SupportSQLiteDatabase
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Test

class DatabaseMigrationsTest {
    @Test
    fun `migration one to two preserves downloads and adds direct provider default`() {
        val database = mockk<SupportSQLiteDatabase>(relaxed = true)

        DatabaseMigrations.MIGRATION_1_2.migrate(database)

        assertEquals(1, DatabaseMigrations.MIGRATION_1_2.startVersion)
        assertEquals(2, DatabaseMigrations.MIGRATION_1_2.endVersion)
        verify {
            database.execSQL(
                match {
                    it.contains("ALTER TABLE downloads") &&
                        it.contains("providerId TEXT NOT NULL") &&
                        it.contains("DEFAULT 'direct'")
                },
            )
            database.execSQL(
                match {
                    it.contains("CREATE TABLE IF NOT EXISTS remote_references") &&
                        it.contains("externalUrl TEXT NOT NULL")
                },
            )
        }
    }

    @Test
    fun `migration two to three adds nullable thumbnail to downloads`() {
        val database = mockk<SupportSQLiteDatabase>(relaxed = true)

        DatabaseMigrations.MIGRATION_2_3.migrate(database)

        assertEquals(2, DatabaseMigrations.MIGRATION_2_3.startVersion)
        assertEquals(3, DatabaseMigrations.MIGRATION_2_3.endVersion)
        verify {
            database.execSQL(
                match {
                    it.contains("ALTER TABLE downloads") &&
                        it.contains("thumbnailUrl TEXT")
                },
            )
        }
    }

    @Test
    fun `migration three to four preserves existing tables and adds import queue`() {
        val database = mockk<SupportSQLiteDatabase>(relaxed = true)

        DatabaseMigrations.MIGRATION_3_4.migrate(database)

        assertEquals(3, DatabaseMigrations.MIGRATION_3_4.startVersion)
        assertEquals(4, DatabaseMigrations.MIGRATION_3_4.endVersion)
        verify {
            database.execSQL(match { it.contains("ALTER TABLE songs ADD COLUMN isrc TEXT") })
            database.execSQL(match { it.contains("ALTER TABLE downloads ADD COLUMN isrc TEXT") })
            database.execSQL(
                match {
                    it.contains("CREATE TABLE IF NOT EXISTS playlist_imports") &&
                        it.contains("FOREIGN KEY(localPlaylistId) REFERENCES playlists")
                },
            )
            database.execSQL(
                match {
                    it.contains("CREATE TABLE IF NOT EXISTS playlist_import_items") &&
                        it.contains("FOREIGN KEY(importId) REFERENCES playlist_imports")
                },
            )
            database.execSQL(
                match {
                    it.contains("CREATE TABLE IF NOT EXISTS playlist_import_candidates") &&
                        it.contains("FOREIGN KEY(importItemId) REFERENCES playlist_import_items")
                },
            )
        }
    }
}
