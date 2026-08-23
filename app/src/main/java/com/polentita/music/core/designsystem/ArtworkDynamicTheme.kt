package com.polentita.music.core.designsystem

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.lerp

@Composable
fun ArtworkDynamicTheme(
    palette: ArtworkPalette,
    content: @Composable () -> Unit,
) {
    val animationsEnabled = rememberAnimationsEnabled()
    val duration = animationDuration(animationsEnabled, PolentitaMotion.artwork)
    val background by animateColorAsState(
        targetValue = palette.background,
        animationSpec = tween(duration),
        label = "Fondo de portada",
    )
    val surface by animateColorAsState(
        targetValue = palette.surface,
        animationSpec = tween(duration),
        label = "Superficie de portada",
    )
    val accent by animateColorAsState(
        targetValue = palette.accent,
        animationSpec = tween(duration),
        label = "Acento de portada",
    )
    val onAccent = ArtworkColorAnalyzer.safeContentColor(accent)
    val primaryContainer = lerp(surface, accent, 0.16f)
    val surfaceVariant = lerp(surface, PolentitaFallbackColors.SurfaceRaised, 0.34f)
    val surfaceLow = lerp(background, surface, 0.62f)
    val surfaceHigh = lerp(surface, PolentitaContentColors.PrimaryOnDark, 0.08f)
    val secondary = lerp(accent, PolentitaContentColors.PrimaryOnDark, 0.12f)
    MaterialTheme(
        colorScheme = MaterialTheme.colorScheme.copy(
            primary = accent,
            onPrimary = onAccent,
            primaryContainer = primaryContainer,
            onPrimaryContainer = PolentitaContentColors.PrimaryOnDark,
            secondary = secondary,
            onSecondary = ArtworkColorAnalyzer.safeContentColor(secondary),
            secondaryContainer = surfaceVariant,
            onSecondaryContainer = PolentitaContentColors.PrimaryOnDark,
            tertiary = secondary,
            onTertiary = ArtworkColorAnalyzer.safeContentColor(secondary),
            tertiaryContainer = primaryContainer,
            onTertiaryContainer = PolentitaContentColors.PrimaryOnDark,
            background = background,
            onBackground = PolentitaContentColors.PrimaryOnDark,
            surface = surface,
            onSurface = PolentitaContentColors.PrimaryOnDark,
            surfaceVariant = surfaceVariant,
            onSurfaceVariant = PolentitaContentColors.SecondaryOnDark,
            surfaceDim = background,
            surfaceBright = surfaceHigh,
            surfaceContainerLowest = background,
            surfaceContainerLow = surfaceLow,
            surfaceContainer = surface,
            surfaceContainerHigh = surfaceVariant,
            surfaceContainerHighest = surfaceHigh,
            inverseSurface = PolentitaContentColors.PrimaryOnDark,
            inverseOnSurface = background,
            outline = PolentitaContentColors.SecondaryOnDark.copy(alpha = 0.72f),
            outlineVariant = PolentitaContentColors.DisabledOnDark.copy(alpha = 0.58f),
        ),
        typography = MaterialTheme.typography,
        shapes = MaterialTheme.shapes,
    ) {
        CompositionLocalProvider(
            LocalContentColor provides PolentitaContentColors.PrimaryOnDark,
            content = content,
        )
    }
}
