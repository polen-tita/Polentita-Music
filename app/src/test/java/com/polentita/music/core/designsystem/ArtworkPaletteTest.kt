package com.polentita.music.core.designsystem

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ArtworkPaletteTest {
    @Test
    fun `selecciona colores desde los pixeles de una portada`() {
        val pixels = IntArray(144) { index ->
            when {
                index < 80 -> 0xFFB72E45.toInt()
                index < 120 -> 0xFFDC8B28.toInt()
                else -> 0xFF243C64.toInt()
            }
        }

        val palette = ArtworkColorAnalyzer.analyze(pixels, "portada-de-prueba")

        assertNotEquals(ArtworkColorAnalyzer.fallback("portada-de-prueba"), palette)
        assertTrue(palette.dominant.red > palette.dominant.blue)
        assertTrue(ArtworkColorAnalyzer.hasReadableContrast(palette))
    }

    @Test
    fun `usa fallback determinista cuando no hay portada`() {
        val first = ArtworkColorAnalyzer.analyze(intArrayOf(), "Sin portada|Artista")
        val second = ArtworkColorAnalyzer.fallback("Sin portada|Artista")

        assertEquals(first, second)
        assertTrue(ArtworkColorAnalyzer.hasReadableContrast(first))
    }

    @Test
    fun `garantiza contraste minimo en fondos y acentos`() {
        listOf(
            "álbum claro",
            "álbum oscuro",
            "álbum monocromo",
            "otro artista",
        ).forEach { seed ->
            val palette = ArtworkColorAnalyzer.fallback(seed)

            assertTrue(
                ArtworkColorAnalyzer.contrastRatio(
                    palette.onBackground,
                    palette.background,
                ) >= 4.5,
            )
            assertTrue(
                ArtworkColorAnalyzer.contrastRatio(
                    palette.onAccent,
                    palette.accent,
                ) >= 4.5,
            )
        }
    }

    @Test
    fun `mantiene colores estables para la misma cancion`() {
        val pixels = IntArray(64) { index ->
            if (index % 2 == 0) 0xFF156C77.toInt() else 0xFF512E70.toInt()
        }

        val first = ArtworkColorAnalyzer.analyze(pixels, "Canción estable|Artista")
        val second = ArtworkColorAnalyzer.analyze(pixels.copyOf(), "Canción estable|Artista")

        assertEquals(first, second)
    }

    @Test
    fun `expone el estado visual de la cancion activa sin depender solo del color`() {
        val playing = activeSongVisualState(songId = 7, currentSongId = 7, isPlaying = true)
        val paused = activeSongVisualState(songId = 7, currentSongId = 7, isPlaying = false)
        val inactive = activeSongVisualState(songId = 8, currentSongId = 7, isPlaying = true)

        assertTrue(playing.isActive)
        assertTrue(playing.isPlaying)
        assertEquals("Reproduciendo", playing.statusLabel)
        assertEquals("En pausa", paused.statusLabel)
        assertFalse(inactive.isActive)
        assertEquals("", inactive.statusLabel)
    }

    @Test
    fun `semillas diferentes producen fallbacks diferenciables`() {
        val first = ArtworkColorAnalyzer.fallback("Primera canción")
        val second = ArtworkColorAnalyzer.fallback("Segunda canción")

        assertNotEquals(first.dominant, second.dominant)
        assertNotEquals(first.background, Color.Transparent)
    }

    @Test
    fun `contenido seguro mantiene contraste sobre superficies claras y oscuras`() {
        val darkBackground = Color(0xFF08090A)
        val brightAccent = Color(0xFFF48FB1)

        assertTrue(
            ArtworkColorAnalyzer.contrastRatio(
                ArtworkColorAnalyzer.safeContentColor(darkBackground),
                darkBackground,
            ) >= 4.5,
        )
        assertTrue(
            ArtworkColorAnalyzer.contrastRatio(
                ArtworkColorAnalyzer.safeContentColor(brightAccent),
                brightAccent,
            ) >= 4.5,
        )
    }
}
