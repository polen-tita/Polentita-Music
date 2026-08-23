package com.polentita.music.core.database

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import java.io.File
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class DatabaseMigration3To4RoomTest {
    private lateinit var context: Context
    private val databaseName = "migration-3-4-test.db"

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase(databaseName)
    }

    @After
    fun tearDown() {
        context.deleteDatabase(databaseName)
    }

    @Test
    fun `real room migration preserves existing user data and creates import tables`() {
        createVersionThreeDatabase()

        val room = Room.databaseBuilder(context, PolentitaDatabase::class.java, databaseName)
            .addMigrations(DatabaseMigrations.MIGRATION_3_4)
            .allowMainThreadQueries()
            .build()
        val db = room.openHelper.writableDatabase

        assertEquals("Canción preservada", db.singleString("SELECT title FROM songs WHERE id = 11"))
        assertEquals("Playlist preservada", db.singleString("SELECT name FROM playlists WHERE id = 21"))
        assertEquals("PENDING", db.singleString("SELECT status FROM downloads WHERE id = 'download-1'"))
        assertNotNull(db.query("SELECT * FROM playlist_imports").use { it.columnNames })
        assertNotNull(db.query("SELECT * FROM playlist_import_items").use { it.columnNames })
        assertNotNull(db.query("SELECT * FROM playlist_import_candidates").use { it.columnNames })
        room.close()
    }

    private fun createVersionThreeDatabase() {
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(databaseName)
                .callback(
                    object : SupportSQLiteOpenHelper.Callback(3) {
                        override fun onCreate(db: SupportSQLiteDatabase) {
                            val schema = JSONObject(schemaFile().readText()).getJSONObject("database")
                            val entities = schema.getJSONArray("entities")
                            repeat(entities.length()) { index ->
                                val entity = entities.getJSONObject(index)
                                val table = entity.getString("tableName")
                                db.execSQL(entity.getString("createSql").replace("\${TABLE_NAME}", table))
                                val indices = entity.getJSONArray("indices")
                                repeat(indices.length()) { indexPosition ->
                                    db.execSQL(
                                        indices.getJSONObject(indexPosition)
                                            .getString("createSql")
                                            .replace("\${TABLE_NAME}", table),
                                    )
                                }
                            }
                            db.execSQL(
                                """
                                INSERT INTO albums (id, name, artist, year, coverUri, dateCreated)
                                VALUES (1, 'Álbum', 'Autora', NULL, NULL, 1)
                                """.trimIndent(),
                            )
                            db.execSQL(
                                """
                                INSERT INTO songs (
                                    id, title, artist, albumId, albumName, genre, year, trackNumber,
                                    discNumber, durationMs, contentUri, originalFileName, displayFileName,
                                    mimeType, fileSize, coverUri, sourceType, sourceUrl, dateAdded,
                                    dateModified, lastPlayedAt, playCount, isFavorite, isAvailable, checksum
                                ) VALUES (
                                    11, 'Canción preservada', 'Autora', 1, 'Álbum', '', NULL, NULL,
                                    NULL, 180000, 'content://song/11', 'song.mp3', 'song.mp3',
                                    'audio/mpeg', 10, NULL, 'IMPORTED', NULL, 1, 1, NULL, 0, 0, 1, 'sum-11'
                                )
                                """.trimIndent(),
                            )
                            db.execSQL(
                                """
                                INSERT INTO playlists (id, name, description, coverUri, dateCreated, dateModified)
                                VALUES (21, 'Playlist preservada', '', NULL, 1, 1)
                                """.trimIndent(),
                            )
                            db.execSQL(
                                """
                                INSERT INTO playlist_songs (playlistId, songId, position, dateAdded)
                                VALUES (21, 11, 0, 1)
                                """.trimIndent(),
                            )
                            db.execSQL(
                                """
                                INSERT INTO downloads (
                                    id, sourceUrl, providerId, thumbnailUrl, destinationUri, title, artist,
                                    album, status, progress, bytesDownloaded, totalBytes, speedBytesPerSecond,
                                    errorMessage, createdAt, completedAt
                                ) VALUES (
                                    'download-1', 'https://example.com/audio', 'direct', NULL, NULL,
                                    'Pendiente', '', '', 'PENDING', 0, 0, -1, 0, NULL, 1, NULL
                                )
                                """.trimIndent(),
                            )
                        }

                        override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
                    },
                )
                .build(),
        )
        helper.writableDatabase
        helper.close()
    }

    private fun schemaFile(): File {
        val relative = "schemas/com.polentita.music.core.database.PolentitaDatabase/3.json"
        return listOf(File(relative), File("app/$relative"))
            .firstOrNull(File::isFile)
            ?: error("No se encontró el esquema Room 3")
    }

    private fun SupportSQLiteDatabase.singleString(query: String): String? =
        this.query(query).use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
}
