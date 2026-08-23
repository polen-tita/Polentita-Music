package com.polentita.music.core.designsystem

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.polentita.music.core.storage.ThemeMode

private val PolentitaDark = darkColorScheme(
    primary = Color(0xFF64D8E8),
    onPrimary = Color(0xFF00363D),
    primaryContainer = Color(0xFF004F58),
    onPrimaryContainer = PolentitaContentColors.PrimaryOnDark,
    secondary = Color(0xFFA7DCE2),
    onSecondary = Color(0xFF08363C),
    secondaryContainer = Color(0xFF25464B),
    onSecondaryContainer = PolentitaContentColors.PrimaryOnDark,
    tertiary = Color(0xFFB9C4FF),
    onTertiary = Color(0xFF202A61),
    tertiaryContainer = Color(0xFF394579),
    onTertiaryContainer = PolentitaContentColors.PrimaryOnDark,
    background = Color(0xFF080B0C),
    surface = Color(0xFF101719),
    surfaceVariant = Color(0xFF232D2F),
    onBackground = Color(0xFFE7F1F2),
    onSurface = Color(0xFFE7F1F2),
    onSurfaceVariant = Color(0xFFB8C8CA),
    outline = Color(0xFF849699),
    outlineVariant = Color(0xFF3C4A4D),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF5B211F),
    onErrorContainer = Color(0xFFFFDAD6),
)

private val PolentitaLight = lightColorScheme(
    primary = Color(0xFF006874),
    onPrimary = Color.White,
    primaryContainer = Color(0xFF97F0FF),
    onPrimaryContainer = Color(0xFF001F24),
    secondary = Color(0xFF4A6267),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFCDE7EB),
    onSecondaryContainer = Color(0xFF051F23),
    tertiary = Color(0xFF565E92),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFDEE0FF),
    onTertiaryContainer = Color(0xFF111A4B),
    background = Color(0xFFF7FBFC),
    surface = Color(0xFFF7FBFC),
    surfaceVariant = Color(0xFFDCE7E9),
    onBackground = Color(0xFF161D1E),
    onSurface = Color(0xFF161D1E),
    onSurfaceVariant = Color(0xFF3F484A),
    outline = Color(0xFF6F797B),
    outlineVariant = Color(0xFFC0CBCD),
    error = Color(0xFFBA1A1A),
    onError = Color.White,
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
)

private val PolentitaShapes = Shapes(
    extraSmall = androidx.compose.foundation.shape.RoundedCornerShape(PolentitaRadii.small),
    small = androidx.compose.foundation.shape.RoundedCornerShape(PolentitaRadii.medium),
    medium = androidx.compose.foundation.shape.RoundedCornerShape(PolentitaRadii.large),
    large = androidx.compose.foundation.shape.RoundedCornerShape(PolentitaRadii.hero),
    extraLarge = androidx.compose.foundation.shape.RoundedCornerShape(PolentitaRadii.hero),
)

private val PolentitaTypography = Typography().run {
    copy(
        displaySmall = displaySmall.copy(fontWeight = FontWeight.Black, letterSpacing = (-0.8).sp),
        headlineLarge = headlineLarge.copy(fontWeight = FontWeight.Black, letterSpacing = (-0.6).sp),
        headlineMedium = headlineMedium.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.4).sp),
        headlineSmall = headlineSmall.copy(fontWeight = FontWeight.Bold),
        titleLarge = titleLarge.copy(fontWeight = FontWeight.Bold),
        titleMedium = titleMedium.copy(fontWeight = FontWeight.SemiBold),
        labelLarge = labelLarge.copy(fontWeight = FontWeight.SemiBold, letterSpacing = 0.2.sp),
    )
}

@Composable
fun PolentitaTheme(
    themeMode: ThemeMode,
    dynamicColor: Boolean,
    artworkPalette: ArtworkPalette? = null,
    adaptiveArtworkTheme: Boolean = true,
    content: @Composable () -> Unit,
) {
    val dark = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val context = LocalContext.current
    val baseColors = remember(context, dynamicColor, dark) {
        when {
            dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && dark ->
                dynamicDarkColorScheme(context)
            dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
                dynamicLightColorScheme(context)
            dark -> PolentitaDark
            else -> PolentitaLight
        }
    }
    val colors = remember(baseColors, artworkPalette, adaptiveArtworkTheme, dark) {
        artworkAmbientColorScheme(
            base = baseColors,
            palette = artworkPalette,
            enabled = adaptiveArtworkTheme,
            dark = dark,
        )
    }
    MaterialTheme(
        colorScheme = colors,
        typography = PolentitaTypography,
        shapes = PolentitaShapes,
    ) {
        CompositionLocalProvider(
            LocalContentColor provides colors.onBackground,
            content = content,
        )
    }
}

internal fun artworkAmbientColorScheme(
    base: ColorScheme,
    palette: ArtworkPalette?,
    enabled: Boolean,
    dark: Boolean,
): ColorScheme {
    val accessibleBase = if (dark) base.withSafeDarkContent() else base
    if (!enabled || palette == null) return accessibleBase
    return if (dark) {
        accessibleBase.withDarkArtworkAmbient(palette)
    } else {
        accessibleBase.withLightArtworkAmbient(palette)
    }
}

private fun ColorScheme.withDarkArtworkAmbient(palette: ArtworkPalette): ColorScheme {
    val background = lerp(palette.background, Color.Black, 0.18f)
    val surface = lerp(palette.surface, Color.Black, 0.16f)
    val tintedSurface = lerp(palette.muted, Color.Black, 0.74f)
    val surfaceVariant = lerp(surface, tintedSurface, 0.54f)
    val accent = palette.accent
    val primaryContainer = lerp(surface, accent, 0.18f)
    val secondaryContainer = lerp(surface, palette.muted, 0.16f)
    val tertiaryContainer = lerp(surface, palette.vibrant, 0.13f)
    val surfaceLow = lerp(background, surface, 0.58f)
    val surfaceHigh = lerp(surfaceVariant, PolentitaContentColors.PrimaryOnDark, 0.07f)
    return copy(
        primaryContainer = primaryContainer,
        onPrimaryContainer = ArtworkColorAnalyzer.safeContentColor(primaryContainer),
        secondary = lerp(accent, PolentitaContentColors.PrimaryOnDark, 0.14f),
        onSecondary = ArtworkColorAnalyzer.safeContentColor(
            lerp(accent, PolentitaContentColors.PrimaryOnDark, 0.14f),
        ),
        secondaryContainer = secondaryContainer,
        onSecondaryContainer = ArtworkColorAnalyzer.safeContentColor(secondaryContainer),
        tertiary = lerp(palette.vibrant, PolentitaContentColors.PrimaryOnDark, 0.18f),
        onTertiary = ArtworkColorAnalyzer.safeContentColor(
            lerp(palette.vibrant, PolentitaContentColors.PrimaryOnDark, 0.18f),
        ),
        tertiaryContainer = tertiaryContainer,
        onTertiaryContainer = ArtworkColorAnalyzer.safeContentColor(tertiaryContainer),
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
        surfaceTint = accent,
        outline = lerp(outline, accent, 0.12f),
        outlineVariant = lerp(outlineVariant, accent, 0.10f),
    ).withSafeDarkContent()
}

private fun ColorScheme.withLightArtworkAmbient(palette: ArtworkPalette): ColorScheme {
    val accent = palette.accent
    val backgroundTint = lerp(background, accent, 0.035f)
    val surfaceTinted = lerp(surface, accent, 0.045f)
    val primaryContainerTinted = lerp(primaryContainer, accent, 0.16f)
    val secondaryContainerTinted = lerp(secondaryContainer, palette.muted, 0.10f)
    val tertiaryContainerTinted = lerp(tertiaryContainer, palette.vibrant, 0.08f)
    return copy(
        primaryContainer = primaryContainerTinted,
        onPrimaryContainer = ArtworkColorAnalyzer.safeContentColor(primaryContainerTinted),
        secondaryContainer = secondaryContainerTinted,
        onSecondaryContainer = ArtworkColorAnalyzer.safeContentColor(secondaryContainerTinted),
        tertiaryContainer = tertiaryContainerTinted,
        onTertiaryContainer = ArtworkColorAnalyzer.safeContentColor(tertiaryContainerTinted),
        background = backgroundTint,
        surface = surfaceTinted,
        surfaceContainerLow = lerp(surfaceContainerLow, accent, 0.04f),
        surfaceContainer = lerp(surfaceContainer, accent, 0.05f),
        surfaceContainerHigh = lerp(surfaceContainerHigh, accent, 0.06f),
        surfaceContainerHighest = lerp(surfaceContainerHighest, accent, 0.07f),
        surfaceTint = accent,
    )
}

private fun ColorScheme.withSafeDarkContent(): ColorScheme = copy(
    onBackground = PolentitaContentColors.PrimaryOnDark,
    onSurface = PolentitaContentColors.PrimaryOnDark,
    onSurfaceVariant = PolentitaContentColors.SecondaryOnDark,
    onPrimary = ArtworkColorAnalyzer.safeContentColor(primary),
    onPrimaryContainer = ArtworkColorAnalyzer.safeContentColor(primaryContainer),
    onSecondary = ArtworkColorAnalyzer.safeContentColor(secondary),
    onSecondaryContainer = ArtworkColorAnalyzer.safeContentColor(secondaryContainer),
    onTertiary = ArtworkColorAnalyzer.safeContentColor(tertiary),
    onTertiaryContainer = ArtworkColorAnalyzer.safeContentColor(tertiaryContainer),
    onError = ArtworkColorAnalyzer.safeContentColor(error),
    onErrorContainer = ArtworkColorAnalyzer.safeContentColor(errorContainer),
)
