package com.polentita.music.core.database

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SongSearchDaoTest {
    private lateinit var database: PolentitaDatabase
    private lateinit var dao: SongDao
    private var metallicaId = 0L
    private var tributeId = 0L

    @Before
    fun setup() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, PolentitaDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = database.songDao()
        metallicaId = dao.insert(
            song(
                title = "Nothing Else Matters",
                artist = "Metallica",
                checksum = "metallica",
            ).copy(
                albumName = "Black Album",
                genre = "Metal",
                originalFileName = "track_05_master.flac",
                displayFileName = "nothing-else.flac",
                isFavorite = true,
                sourceType = SourceType.IMPORTED.name,
            ),
        )
        tributeId = dao.insert(
            song(
                title = "Metallica Tribute",
                artist = "Local Band",
                checksum = "tribute",
            ).copy(
                albumName = "Covers",
                genre = "Rock",
                sourceType = SourceType.DOWNLOADED.name,
            ),
        )
        dao.insert(
            song("Silent Sky", "Other Artist", "other").copy(
                albumName = "Clouds",
                isAvailable = false,
            ),
        )
    }

    @After
    fun close() = database.close()

    @Test
    fun `exact title match`() = runTest {
        assertEquals(listOf(metallicaId), ids("Nothing Else Matters"))
    }

    @Test
    fun `partial match finds title and artist`() = runTest {
        assertEquals(setOf(metallicaId, tributeId), ids("lica").toSet())
    }

    @Test
    fun `search ignores upper and lower case`() = runTest {
        assertEquals(setOf(metallicaId, tributeId), ids("mEtAlLiCa").toSet())
    }

    @Test
    fun `searches by artist`() = runTest {
        assertEquals(listOf(tributeId), ids("Local Band"))
    }

    @Test
    fun `searches by album`() = runTest {
        assertEquals(listOf(metallicaId), ids("Black Album"))
    }

    @Test
    fun `searches original file name`() = runTest {
        assertEquals(listOf(metallicaId), ids("05_master"))
    }

    @Test
    fun `nonexistent text returns empty list`() = runTest {
        assertTrue(ids("no-existe-xyz").isEmpty())
    }

    @Test
    fun `empty query returns all songs`() = runTest {
        assertEquals(3, ids("").size)
    }

    @Test
    fun `query ignores leading and trailing spaces`() = runTest {
        assertEquals(setOf(metallicaId, tributeId), ids("   lica   ").toSet())
    }

    @Test
    fun `query combines with source availability and favorite filters`() = runTest {
        val result = dao.search(
            query = "metal",
            favoriteOnly = true,
            sourceType = SourceType.IMPORTED.name,
            availableMode = 1,
        ).first()

        assertEquals(listOf(metallicaId), result.map { it.id })
    }

    private suspend fun ids(query: String) = dao.search(query = query).first().map { it.id }

    private fun song(title: String, artist: String, checksum: String) = SongEntity(
        title = title,
        artist = artist,
        contentUri = "content://search/$checksum",
        originalFileName = "$title.wav",
        displayFileName = "$title.wav",
        mimeType = "audio/wav",
        checksum = checksum,
    )
}
