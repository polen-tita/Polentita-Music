package com.polentita.music.feature.home.dino

import androidx.compose.material3.darkColorScheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DinoBiomeVisualsTest {
    private val colors = darkColorScheme()

    @Test
    fun `cada fase activa comparte una superficie y un chrome progresivo`() {
        val strengths = DinoRunnerAmbientPhase.entries.map { phase ->
            val spec = dinoBiomeSpec(
                ambient = DinoRunnerAmbientState(active = true, phase = phase),
                colorScheme = colors,
            )

            assertTrue(spec.active)
            assertEquals(phase, spec.phase)
            assertTrue(spec.overlayAlpha > 0.95f)
            assertTrue(spec.chromeBackdrop != colors.background)
            spec.chromeStrength
        }

        assertTrue(strengths.first() > 0f)
        strengths.zipWithNext().forEach { (current, next) ->
            assertTrue(next >= current)
        }
        assertEquals(1f, strengths.last(), 0.001f)
    }

    @Test
    fun `el eclipse intensifica el chrome al cruzar el modo epico`() {
        val beforeEpic = dinoBiomeSpec(
            ambient = DinoRunnerAmbientState(
                active = true,
                phase = DinoRunnerAmbientPhase.ECLIPSE,
                globalChromeActive = false,
            ),
            colorScheme = colors,
        )
        val epic = dinoBiomeSpec(
            ambient = DinoRunnerAmbientState(
                active = true,
                phase = DinoRunnerAmbientPhase.ECLIPSE,
                globalChromeActive = true,
            ),
            colorScheme = colors,
        )

        assertEquals(0.62f, beforeEpic.chromeStrength, 0.001f)
        assertEquals(0.82f, epic.chromeStrength, 0.001f)
    }

    @Test
    fun `el ambiente inactivo devuelve el tema sin tinte`() {
        val spec = dinoBiomeSpec(DinoRunnerAmbientState(), colors)

        assertFalse(spec.active)
        assertEquals(colors.background, spec.chromeBackdrop)
        assertEquals(colors.surface, spec.chromeSurface)
        assertEquals(0f, spec.overlayAlpha, 0.001f)
        assertEquals(0f, spec.chromeStrength, 0.001f)
    }

    @Test
    fun `los efectos tardios empiezan en 5000 y aumentan en espacio profundo`() {
        val beforeEpic = dinoLateEffectSpec(
            DinoRunnerAmbientState(
                active = true,
                phase = DinoRunnerAmbientPhase.ECLIPSE,
                globalChromeActive = false,
            ),
        )
        val eclipse = dinoLateEffectSpec(
            DinoRunnerAmbientState(
                active = true,
                phase = DinoRunnerAmbientPhase.ECLIPSE,
                globalChromeActive = true,
            ),
        )
        val deepSpace = dinoLateEffectSpec(
            DinoRunnerAmbientState(
                active = true,
                phase = DinoRunnerAmbientPhase.DEEP_SPACE,
                globalChromeActive = true,
            ),
        )

        assertEquals(0, beforeEpic.meteorCount)
        assertEquals(12, eclipse.meteorCount)
        assertEquals(18, deepSpace.meteorCount)
        assertTrue(deepSpace.meteorCount > eclipse.meteorCount)
        assertTrue(deepSpace.meteorSpeed > eclipse.meteorSpeed)
    }

    @Test
    fun `la nebulosa de 9000 agrega nieve y las fases posteriores conservan meteoros`() {
        val nebula = dinoLateEffectSpec(
            DinoRunnerAmbientState(
                active = true,
                phase = DinoRunnerAmbientPhase.NEBULA,
                globalChromeActive = true,
            ),
        )

        assertEquals(42, nebula.snowflakeCount)
        assertTrue(nebula.meteorCount > 0)
        listOf(
            DinoRunnerAmbientPhase.HYPERSPACE,
            DinoRunnerAmbientPhase.SINGULARITY,
            DinoRunnerAmbientPhase.SUPERNOVA,
        ).forEach { phase ->
            val spec = dinoLateEffectSpec(
                DinoRunnerAmbientState(
                    active = true,
                    phase = phase,
                    globalChromeActive = true,
                ),
            )
            assertTrue(spec.meteorCount > 0)
            assertEquals(0, spec.snowflakeCount)
        }
    }

}
