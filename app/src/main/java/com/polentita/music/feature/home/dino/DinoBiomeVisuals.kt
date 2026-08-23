package com.polentita.music.feature.home.dino

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance

internal data class DinoBiomeSpec(
    val active: Boolean,
    val phase: DinoRunnerAmbientPhase,
    val sky: Color,
    val horizon: Color,
    val accent: Color,
    val secondary: Color,
    val fieldSilhouette: Color,
    val chromeBackdrop: Color,
    val chromeSurface: Color,
    val overlayAlpha: Float,
    val surfaceTintStrength: Float,
    val chromeStrength: Float,
)

internal data class DinoLateEffectSpec(
    val meteorCount: Int = 0,
    val meteorAlpha: Float = 0f,
    val meteorSpeed: Float = 1f,
    val meteorDurationMs: Long = 9_000L,
    val snowflakeCount: Int = 0,
)

internal object DinoBiomeColors {
    val DaySky = Color(0xFF15333B)
    val DayHorizon = Color(0xFF263A38)
    val DayCyan = Color(0xFF67DCE2)
    val DaySand = Color(0xFFE0B96B)

    val SunsetSky = Color(0xFF35232D)
    val SunsetHorizon = Color(0xFF432C35)
    val SunsetCoral = Color(0xFFFF8A5B)
    val DuskViolet = Color(0xFF7554A8)

    val NightSky = Color(0xFF101A2B)
    val NightHorizon = Color(0xFF182331)
    val NightBlue = Color(0xFF09142F)
    val Moonlight = Color(0xFFC7DCFF)

    val AuroraSky = Color(0xFF16343A)
    val AuroraHorizon = Color(0xFF203D45)
    val AuroraCyan = Color(0xFF37E0C1)
    val AuroraViolet = Color(0xFF8B6CFF)

    val EclipseSky = Color(0xFF2E271F)
    val EclipseHorizon = Color(0xFF392F25)
    val EclipseObsidian = Color(0xFF07090F)
    val EclipseCorona = Color(0xFFFFB35C)

    val DeepSpaceSky = Color(0xFF0C1B36)
    val DeepSpaceHorizon = Color(0xFF132443)
    val DeepSpace = Color(0xFF050B1C)
    val CosmicBlue = Color(0xFF4A8CFF)

    val NebulaSky = Color(0xFF342D55)
    val NebulaHorizon = Color(0xFF3B315F)
    val NebulaMagenta = Color(0xFFD16BFF)
    val NebulaCyan = Color(0xFF55E8E8)

    val HyperspaceSky = Color(0xFF213A4B)
    val HyperspaceHorizon = Color(0xFF294355)
    val HyperspaceIce = Color(0xFFC8F7FF)

    val SingularitySky = Color(0xFF41275B)
    val SingularityHorizon = Color(0xFF4A2B63)
    val SingularityViolet = Color(0xFF9B70FF)
    val SingularityRose = Color(0xFFFF6A9A)

    val SupernovaSky = Color(0xFF37443E)
    val SupernovaHorizon = Color(0xFF3C4B44)
    val SupernovaGold = Color(0xFFFFD27A)
    val SupernovaCyan = Color(0xFF70ECF7)
}

internal fun dinoBiomeSpec(
    ambient: DinoRunnerAmbientState,
    colorScheme: ColorScheme,
): DinoBiomeSpec {
    val base = colorScheme.background
    val darkSurface = base.luminance() < 0.5f
    if (!ambient.active) {
        return DinoBiomeSpec(
            active = false,
            phase = DinoRunnerAmbientPhase.DAY,
            sky = base,
            horizon = base,
            accent = colorScheme.primary,
            secondary = colorScheme.tertiary,
            fieldSilhouette = colorScheme.surfaceVariant,
            chromeBackdrop = base,
            chromeSurface = colorScheme.surface,
            overlayAlpha = 0f,
            surfaceTintStrength = 0f,
            chromeStrength = 0f,
        )
    }

    val palette = when (ambient.phase) {
        DinoRunnerAmbientPhase.DAY -> PhasePalette(
            DinoBiomeColors.DaySky,
            DinoBiomeColors.DayHorizon,
            DinoBiomeColors.DayCyan,
            DinoBiomeColors.DaySand,
            0.08f,
            0.18f,
        )
        DinoRunnerAmbientPhase.SUNSET -> PhasePalette(
            DinoBiomeColors.SunsetSky,
            DinoBiomeColors.SunsetHorizon,
            DinoBiomeColors.SunsetCoral,
            DinoBiomeColors.DuskViolet,
            0.10f,
            0.28f,
        )
        DinoRunnerAmbientPhase.NIGHT -> PhasePalette(
            DinoBiomeColors.NightSky,
            DinoBiomeColors.NightHorizon,
            DinoBiomeColors.Moonlight,
            DinoBiomeColors.NightBlue,
            0.09f,
            0.38f,
        )
        DinoRunnerAmbientPhase.AURORA -> PhasePalette(
            DinoBiomeColors.AuroraSky,
            DinoBiomeColors.AuroraHorizon,
            DinoBiomeColors.AuroraCyan,
            DinoBiomeColors.AuroraViolet,
            0.12f,
            0.50f,
        )
        DinoRunnerAmbientPhase.ECLIPSE -> PhasePalette(
            DinoBiomeColors.EclipseSky,
            DinoBiomeColors.EclipseHorizon,
            DinoBiomeColors.EclipseCorona,
            DinoBiomeColors.EclipseObsidian,
            0.14f,
            if (ambient.globalChromeActive) 0.82f else 0.62f,
        )
        DinoRunnerAmbientPhase.DEEP_SPACE -> PhasePalette(
            DinoBiomeColors.DeepSpaceSky,
            DinoBiomeColors.DeepSpaceHorizon,
            DinoBiomeColors.CosmicBlue,
            DinoBiomeColors.DeepSpace,
            0.14f,
            0.88f,
        )
        DinoRunnerAmbientPhase.NEBULA -> PhasePalette(
            DinoBiomeColors.NebulaSky,
            DinoBiomeColors.NebulaHorizon,
            DinoBiomeColors.NebulaMagenta,
            DinoBiomeColors.NebulaCyan,
            0.16f,
            0.92f,
        )
        DinoRunnerAmbientPhase.HYPERSPACE -> PhasePalette(
            DinoBiomeColors.HyperspaceSky,
            DinoBiomeColors.HyperspaceHorizon,
            DinoBiomeColors.HyperspaceIce,
            DinoBiomeColors.CosmicBlue,
            0.15f,
            0.95f,
        )
        DinoRunnerAmbientPhase.SINGULARITY -> PhasePalette(
            DinoBiomeColors.SingularitySky,
            DinoBiomeColors.SingularityHorizon,
            DinoBiomeColors.SingularityViolet,
            DinoBiomeColors.SingularityRose,
            0.18f,
            1f,
        )
        DinoRunnerAmbientPhase.SUPERNOVA -> PhasePalette(
            DinoBiomeColors.SupernovaSky,
            DinoBiomeColors.SupernovaHorizon,
            DinoBiomeColors.SupernovaGold,
            DinoBiomeColors.SupernovaCyan,
            0.17f,
            1f,
        )
    }
    val themeBlend = if (darkSurface) 0.92f else 0.72f
    val sky = lerp(base, palette.sky, themeBlend)
    val horizon = lerp(base, palette.horizon, themeBlend)
    val unified = lerp(sky, horizon, 0.52f)
    val surface = lerp(unified, palette.accent, 0.085f)
    return DinoBiomeSpec(
        active = true,
        phase = ambient.phase,
        sky = sky,
        horizon = horizon,
        accent = palette.accent,
        secondary = palette.secondary,
        fieldSilhouette = lerp(unified, palette.secondary, 0.20f),
        chromeBackdrop = unified,
        chromeSurface = surface,
        overlayAlpha = if (darkSurface) 0.992f else 0.965f,
        surfaceTintStrength = palette.surfaceTintStrength,
        chromeStrength = palette.chromeStrength,
    )
}

/**
 * Keeps late-game density explicit and testable without introducing per-particle state.
 * Effects begin only after the epic 5,000-point threshold has actually been crossed.
 */
internal fun dinoLateEffectSpec(ambient: DinoRunnerAmbientState): DinoLateEffectSpec {
    if (!ambient.active || !ambient.globalChromeActive) return DinoLateEffectSpec()

    return when (ambient.phase) {
        DinoRunnerAmbientPhase.ECLIPSE -> DinoLateEffectSpec(
            meteorCount = 12,
            meteorAlpha = 0.90f,
            meteorSpeed = 1f,
            meteorDurationMs = 9_000L,
        )
        DinoRunnerAmbientPhase.DEEP_SPACE -> DinoLateEffectSpec(
            meteorCount = 18,
            meteorAlpha = 1f,
            meteorSpeed = 1.20f,
            meteorDurationMs = 7_800L,
        )
        DinoRunnerAmbientPhase.NEBULA -> DinoLateEffectSpec(
            meteorCount = 14,
            meteorAlpha = 0.75f,
            meteorSpeed = 1f,
            meteorDurationMs = 10_000L,
            snowflakeCount = 42,
        )
        DinoRunnerAmbientPhase.HYPERSPACE -> DinoLateEffectSpec(
            meteorCount = 16,
            meteorAlpha = 0.68f,
            meteorSpeed = 1.30f,
            meteorDurationMs = 7_600L,
        )
        DinoRunnerAmbientPhase.SINGULARITY -> DinoLateEffectSpec(
            meteorCount = 14,
            meteorAlpha = 0.76f,
            meteorSpeed = 0.92f,
            meteorDurationMs = 9_800L,
        )
        DinoRunnerAmbientPhase.SUPERNOVA -> DinoLateEffectSpec(
            meteorCount = 16,
            meteorAlpha = 0.84f,
            meteorSpeed = 1.08f,
            meteorDurationMs = 8_500L,
        )
        else -> DinoLateEffectSpec()
    }
}

private data class PhasePalette(
    val sky: Color,
    val horizon: Color,
    val accent: Color,
    val secondary: Color,
    val surfaceTintStrength: Float,
    val chromeStrength: Float,
)
