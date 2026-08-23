package com.polentita.music

import android.content.Context
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.polentita.music.core.database.PlaylistEntity
import com.polentita.music.core.database.PolentitaDatabase
import com.polentita.music.core.database.SongEntity
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PolentitaFlowInstrumentedTest {
    @Test
    fun importLibraryPlayerEditAndPlaylistFlow() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val database = Room.inMemoryDatabaseBuilder(context, PolentitaDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val audio = File(context.cacheDir, "generated-test.wav")
        writeSilentWav(audio)
        val checksum = MessageDigest.getInstance("SHA-256")
            .digest(audio.readBytes())
            .joinToString("") { "%02x".format(it) }

        // 1. Importar una canción de prueba generada, sin contenido comercial.
        val songId = database.songDao().insert(
            SongEntity(
                title = "Audio de prueba",
                artist = "Polentita",
                durationMs = 250,
                contentUri = Uri.fromFile(audio).toString(),
                originalFileName = audio.name,
                displayFileName = audio.name,
                mimeType = "audio/wav",
                fileSize = audio.length(),
                checksum = checksum,
            ),
        )
        // 2. Mostrarla en la biblioteca observable.
        assertEquals("Audio de prueba", database.songDao().observeAll().first().single().title)

        // 3. Abrirla con el mismo motor ExoPlayer usado por el servicio.
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val player = ExoPlayer.Builder(context).build()
            player.setMediaItem(MediaItem.fromUri(Uri.fromFile(audio)))
            player.prepare()
            assertEquals(1, player.mediaItemCount)
            player.release()
        }

        // 4. Editar el título.
        val imported = requireNotNull(database.songDao().getById(songId))
        database.songDao().update(imported.copy(title = "Título editado"))
        assertEquals("Título editado", database.songDao().getById(songId)?.title)

        // 5. Agregarla a una playlist.
        val playlistId = database.playlistDao().insert(PlaylistEntity(name = "Pruebas"))
        database.playlistDao().addSongs(playlistId, listOf(songId))
        assertEquals(1, database.playlistDao().observeSongs(playlistId).first().size)

        // 6. Eliminarla de la playlist sin borrar la canción.
        database.playlistDao().removeSong(playlistId, songId)
        assertTrue(database.playlistDao().observeSongs(playlistId).first().isEmpty())
        assertTrue(database.songDao().getById(songId) != null)
        database.close()
    }

    private fun writeSilentWav(file: File) {
        val sampleRate = 8_000
        val samples = sampleRate / 4
        val dataSize = samples * 2
        FileOutputStream(file).use { out ->
            fun leInt(value: Int) {
                out.write(byteArrayOf(value.toByte(), (value shr 8).toByte(), (value shr 16).toByte(), (value shr 24).toByte()))
            }
            fun leShort(value: Int) {
                out.write(byteArrayOf(value.toByte(), (value shr 8).toByte()))
            }
            out.write("RIFF".toByteArray())
            leInt(36 + dataSize)
            out.write("WAVEfmt ".toByteArray())
            leInt(16)
            leShort(1)
            leShort(1)
            leInt(sampleRate)
            leInt(sampleRate * 2)
            leShort(2)
            leShort(16)
            out.write("data".toByteArray())
            leInt(dataSize)
            out.write(ByteArray(dataSize))
        }
    }
}
