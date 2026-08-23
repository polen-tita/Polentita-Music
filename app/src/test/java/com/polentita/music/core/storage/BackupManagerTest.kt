package com.polentita.music.core.storage

import android.content.Context
import android.net.Uri
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.polentita.music.core.database.PlaylistEntity
import com.polentita.music.core.database.PlaylistSongCrossRef
import com.polentita.music.core.database.PolentitaDatabase
import com.polentita.music.core.database.SongEntity
import com.polentita.music.core.localization.AppLanguage
import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class BackupManagerTest {
    private lateinit var context: Context
    private lateinit var database: PolentitaDatabase
    private lateinit var manager: BackupManager
    private lateinit var preferences: PreferencesStore

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, PolentitaDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        preferences = PreferencesStore(context)
        manager = BackupManager(context, database.backupDao(), preferences)
    }

    @After
    fun close() = database.close()

    @Test
    fun `versioned zip backup round trips songs playlists and order`() = runTest {
        val songId = database.songDao().insert(
            SongEntity(
                title = "Backup",
                contentUri = "content://library/backup",
                originalFileName = "backup.wav",
                displayFileName = "backup.wav",
                mimeType = "audio/wav",
                checksum = "backup",
                isFavorite = true,
            ),
        )
        val playlistId = database.playlistDao().insert(PlaylistEntity(name = "Guardada"))
        database.playlistDao().insertCrossRef(PlaylistSongCrossRef(playlistId, songId, 0))
        val file = File(context.cacheDir, "backup-test.zip")
        val uri = Uri.fromFile(file)

        preferences.setLanguage(AppLanguage.CHINESE)
        val previousDinoHighScore = preferences.current().dinoHighScore
        val exportedDinoHighScore = minOf(
            Int.MAX_VALUE.toLong() - 1L,
            previousDinoHighScore.toLong() + 321L,
        ).toInt()
        val localDinoHighScore = (exportedDinoHighScore.toLong() + 1L).toInt()
        preferences.recordDinoHighScore(exportedDinoHighScore)
        manager.export(uri)
        preferences.recordDinoHighScore(localDinoHighScore)
        database.backupDao().restore(
            com.polentita.music.core.database.DatabaseBackup(
                emptyList(),
                emptyList(),
                emptyList(),
                emptyList(),
                emptyList(),
            ),
        )
        val result = manager.import(uri)

        assertEquals(1, result.songs)
        assertEquals("Backup", database.songDao().getById(songId)?.title)
        assertEquals(listOf(songId), database.playlistDao().observeSongs(playlistId).first().map { it.id })
        assertEquals(AppLanguage.CHINESE, PreferencesStore(context).current().language)
        assertEquals(localDinoHighScore, PreferencesStore(context).current().dinoHighScore)
        preferences.setLanguage(AppLanguage.SPANISH)
    }
}
