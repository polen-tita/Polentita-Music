package com.polentita.music.core.designsystem

import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.graphics.Color
import com.polentita.music.core.storage.AppPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AdaptiveArtworkThemeTest {
    private val base = darkColorScheme(
        primary = Color(0xFFF3B82E),
        background = Color(0xFF080807),
        surface = Color(0xFF11110F),
    )

    @Test
    fun `el tema adaptativo esta activado por defecto`() {
        assertTrue(AppPreferences().adaptiveArtworkTheme)
    }

    @Test
    fun `la portada ambienta fondos y contenedores sin reemplazar la identidad`() {
        val palette = ArtworkColorAnalyzer.fallback("Canción de prueba|Artista")

        val themed = artworkAmbientColorScheme(base, palette, enabled = true, dark = true)

        assertNotEquals(base.background, themed.background)
        assertEquals(base.primary, themed.primary)
        assertNotEquals(base.primaryContainer, themed.primaryContainer)
        assertNotEquals(base.surface, themed.surface)
        assertTrue(themed.background.red <= palette.background.red)
        assertTrue(themed.background.green <= palette.background.green)
        assertTrue(themed.background.blue <= palette.background.blue)
    }

    @Test
    fun `desactivar el tema conserva la paleta oscura normal`() {
        val palette = ArtworkColorAnalyzer.fallback("Otra canción")
        val expected = artworkAmbientColorScheme(base, null, enabled = false, dark = true)

        val actual = artworkAmbientColorScheme(base, palette, enabled = false, dark = true)

        assertEquals(expected.primary, actual.primary)
        assertEquals(expected.primaryContainer, actual.primaryContainer)
        assertEquals(expected.background, actual.background)
        assertEquals(expected.surface, actual.surface)
        assertEquals(expected.surfaceVariant, actual.surfaceVariant)
        assertEquals(expected.onBackground, actual.onBackground)
    }

    @Test
    fun `el contenido global mantiene contraste seguro`() {
        val palettes = listOf(
            ArtworkColorAnalyzer.fallback("Portada muy clara"),
            ArtworkColorAnalyzer.fallback("Portada muy oscura"),
            ArtworkColorAnalyzer.fallback("Portada saturada"),
        )

        palettes.forEach { palette ->
            val themed = artworkAmbientColorScheme(base, palette, enabled = true, dark = true)
            assertTrue(
                ArtworkColorAnalyzer.contrastRatio(
                    themed.onBackground,
                    themed.background,
                ) >= 4.5,
            )
            assertTrue(
                ArtworkColorAnalyzer.contrastRatio(
                    themed.onSurface,
                    themed.surface,
                ) >= 4.5,
            )
            assertTrue(
                ArtworkColorAnalyzer.contrastRatio(
                    themed.onPrimaryContainer,
                    themed.primaryContainer,
                ) >= 4.5,
            )
        }
    }
}
