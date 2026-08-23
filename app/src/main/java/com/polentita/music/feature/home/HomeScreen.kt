package com.polentita.music.feature.home

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.polentita.music.R
import com.polentita.music.core.common.formatDuration
import com.polentita.music.core.designsystem.AdaptiveArtworkBackground
import com.polentita.music.core.designsystem.Artwork
import com.polentita.music.core.designsystem.ArtworkDynamicTheme
import com.polentita.music.core.designsystem.ArtworkPalette
import com.polentita.music.core.designsystem.ErrorState
import com.polentita.music.core.designsystem.LoadingState
import com.polentita.music.core.designsystem.PolentitaMotion
import com.polentita.music.core.designsystem.PolentitaOpacity
import com.polentita.music.core.designsystem.PolentitaRadii
import com.polentita.music.core.designsystem.PolentitaSpacing
import com.polentita.music.core.designsystem.animationDuration
import com.polentita.music.core.designsystem.rememberAnimationsEnabled
import com.polentita.music.core.designsystem.rememberArtworkPalette
import com.polentita.music.domain.model.PlaylistNames
import com.polentita.music.domain.model.Song
import com.polentita.music.feature.home.dino.DinoRunnerAmbientPhase
import com.polentita.music.feature.home.dino.DinoRunnerAmbientState
import com.polentita.music.feature.home.dino.DinoRunnerGame
import com.polentita.music.feature.home.dino.DinoRunnerSession
import com.polentita.music.feature.home.dino.DinoBiomeColors
import com.polentita.music.feature.home.dino.dinoBiomeSpec
import com.polentita.music.feature.home.dino.dinoLateEffectSpec
import com.polentita.music.feature.player.PlayerViewModel
import com.polentita.music.feature.update.AppUpdateUiState
import com.polentita.music.playback.queue.PlaybackContextKind
import com.polentita.music.playback.session.PlaybackUiState
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import java.time.format.TextStyle

private object HomeShelfCardMetrics {
    val continueWidth = 132.dp
    val favoriteWidth = 148.dp
}

private val DINO_BOTTOM_CONTROL_AREA_HEIGHT = 168.dp
private val DINO_AMBIENT_TWO_PI = (Math.PI * 2.0).toFloat()
private const val HOME_TAGLINE_COUNT = 5

private data class FeaturedPlaybackState(
    val isCurrent: Boolean,
    val isPlaying: Boolean,
    val positionMs: Long,
    val durationMs: Long,
)

private data class FeaturedSongContent(
    val id: Long,
    val title: String,
    val artist: String,
    val coverUri: String?,
)

private data class PlaybackProgress(
    val positionMs: Long,
    val durationMs: Long,
)

private fun PlayerViewModel.playHomeSong(
    song: Song,
    songs: List<Song>,
    key: String,
    label: String,
) = playFromContext(
    song = song,
    songs = songs,
    kind = PlaybackContextKind.HOME,
    key = key,
    label = label,
)

@Composable
fun HomeScreen(
    homeTaglineIndex: Int,
    updateState: AppUpdateUiState,
    onPlaylist: (Long) -> Unit,
    onAlbum: (Long) -> Unit,
    onLibrary: () -> Unit,
    onDownloads: () -> Unit,
    playerViewModel: PlayerViewModel,
    dinoAmbient: DinoRunnerAmbientState,
    dinoSession: DinoRunnerSession,
    onDinoAmbientChanged: (DinoRunnerAmbientState) -> Unit,
    onDinoHighScore: (Int) -> Unit,
    onUpdate: () -> Unit,
    onDismissUpdate: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val playbackVisual by playerViewModel.visualState.collectAsStateWithLifecycle()
    val currentSong = playbackVisual.currentSongId?.let { activeId ->
        sequenceOf(state.history, state.recent, state.favorites, state.mostPlayed)
            .flatten()
            .firstOrNull { it.id == activeId }
    }
    val featured = if (playbackVisual.currentSongId != null) {
        currentSong
    } else {
        state.history.firstOrNull() ?: state.recent.firstOrNull()
    }
    val ambientCover = playbackVisual.artworkUri
    val ambientSeed = playbackVisual.title
        .takeIf { playbackVisual.currentSongId != null && it.isNotBlank() }
        ?: "Polentita Music"
    val palette = rememberArtworkPalette(ambientCover, ambientSeed)
    val homeTagline = homeTaglineForEntry(homeTaglineIndex)

    HomeAmbientBackground(
        coverUri = ambientCover,
        palette = palette,
        dinoAmbient = dinoAmbient,
        dinoSession = dinoSession,
        modifier = Modifier.fillMaxSize(),
    ) { ambientPalette ->
        when {
            state.loading -> LoadingState()
            state.error != null -> ErrorState(state.error.orEmpty())
            state.recent.isEmpty() && state.favorites.isEmpty() && state.history.isEmpty() ->
                HomeEmptyState(onLibrary)
            else -> HomeContent(
                state = state,
                tagline = homeTagline,
                updateState = updateState,
                featured = featured,
                palette = ambientPalette,
                playbackVisual = playbackVisual,
                playerViewModel = playerViewModel,
                onPlaylist = onPlaylist,
                onAlbum = onAlbum,
                onLibrary = onLibrary,
                onDownloads = onDownloads,
                dinoAmbient = dinoAmbient,
                dinoSession = dinoSession,
                onDinoAmbientChanged = onDinoAmbientChanged,
                onDinoHighScore = onDinoHighScore,
                onUpdate = onUpdate,
                onDismissUpdate = onDismissUpdate,
            )
        }
    }
}

@Composable
private fun homeTaglineForEntry(entryIndex: Int): String {
    val locale = LocalConfiguration.current.locales[0]
    return when (entryIndex % HOME_TAGLINE_COUNT) {
        0 -> stringResource(R.string.home_tagline_offline)
        1 -> stringResource(R.string.home_tagline_smile)
        2 -> stringResource(
            R.string.home_tagline_today,
            LocalDate.now().dayOfWeek.getDisplayName(TextStyle.FULL, locale),
        )
        3 -> stringResource(R.string.home_tagline_dino)
        else -> stringResource(R.string.home_tagline_aura_farmer)
    }
}

@Composable
private fun HomeAmbientBackground(
    coverUri: String?,
    palette: ArtworkPalette,
    dinoAmbient: DinoRunnerAmbientState,
    dinoSession: DinoRunnerSession,
    modifier: Modifier,
    content: @Composable (ArtworkPalette) -> Unit,
) {
    val darkSurface = MaterialTheme.colorScheme.background.luminance() < 0.5f
    if (darkSurface) {
        ArtworkDynamicTheme(palette) {
            val visuals = rememberDinoAmbientVisuals(dinoAmbient)
            val animationsEnabled = rememberAnimationsEnabled()
            val duration = animationDuration(animationsEnabled, PolentitaMotion.artwork)
            DinoHomeAmbientSurfaceTheme(
                visuals = visuals,
                palette = palette,
            ) { ambientPalette ->
                Box(modifier) {
                    AnimatedVisibility(
                        visible = !dinoAmbient.active,
                        enter = fadeIn(tween(duration)),
                        exit = fadeOut(tween(duration)),
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        AdaptiveArtworkBackground(
                            coverUri = coverUri,
                            palette = palette,
                            modifier = Modifier.fillMaxSize(),
                        ) {}
                    }
                    DinoHomeAmbientOverlay(visuals, dinoSession)
                    content(ambientPalette)
                }
            }
        }
    } else {
        val animationsEnabled = rememberAnimationsEnabled()
        val base = MaterialTheme.colorScheme.background
        val top by androidx.compose.animation.animateColorAsState(
            targetValue = lerp(base, palette.dominant, 0.08f),
            animationSpec = tween(animationDuration(animationsEnabled, PolentitaMotion.artwork)),
            label = "Ambiente claro de Inicio",
        )
        val middle by androidx.compose.animation.animateColorAsState(
            targetValue = lerp(base, palette.muted, 0.05f),
            animationSpec = tween(animationDuration(animationsEnabled, PolentitaMotion.artwork)),
            label = "Superficie clara de Inicio",
        )
        Box(
            modifier.background(
                Brush.verticalGradient(listOf(top, middle, base)),
            ),
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.radialGradient(
                            listOf(
                                palette.dominant.copy(alpha = 0.10f),
                                Color.Transparent,
                            ),
                    ),
                ),
            )
            val visuals = rememberDinoAmbientVisuals(dinoAmbient)
            DinoHomeAmbientSurfaceTheme(
                visuals = visuals,
                palette = palette,
            ) { ambientPalette ->
                DinoHomeAmbientOverlay(visuals, dinoSession)
                content(ambientPalette)
            }
        }
    }
}

private data class DinoAmbientVisuals(
    val state: DinoRunnerAmbientState,
    val top: Color,
    val bottom: Color,
    val accent: Color,
    val secondary: Color,
    val overlayAlpha: Float,
    val surfaceTintStrength: Float,
)

private data class DinoAmbientTargets(
    val top: Color,
    val bottom: Color,
    val accent: Color,
    val secondary: Color,
    val overlayAlpha: Float,
    val surfaceTintStrength: Float,
)

private val DINO_SUNSET_CORAL = DinoBiomeColors.SunsetCoral
private val DINO_DUSK_VIOLET = DinoBiomeColors.DuskViolet
private val DINO_NIGHT_BLUE = DinoBiomeColors.NightBlue
private val DINO_MOONLIGHT = DinoBiomeColors.Moonlight
private val DINO_AURORA_CYAN = DinoBiomeColors.AuroraCyan
private val DINO_AURORA_VIOLET = DinoBiomeColors.AuroraViolet
private val DINO_ECLIPSE_OBSIDIAN = DinoBiomeColors.EclipseObsidian
private val DINO_ECLIPSE_CORONA = DinoBiomeColors.EclipseCorona
private val DINO_DEEP_SPACE = DinoBiomeColors.DeepSpace
private val DINO_COSMIC_BLUE = DinoBiomeColors.CosmicBlue
private val DINO_NEBULA_MAGENTA = DinoBiomeColors.NebulaMagenta
private val DINO_NEBULA_CYAN = DinoBiomeColors.NebulaCyan
private val DINO_HYPERSPACE_ICE = DinoBiomeColors.HyperspaceIce
private val DINO_SINGULARITY_VIOLET = DinoBiomeColors.SingularityViolet
private val DINO_SINGULARITY_ROSE = DinoBiomeColors.SingularityRose
private val DINO_SUPERNOVA_GOLD = DinoBiomeColors.SupernovaGold
private val DINO_SUPERNOVA_CYAN = DinoBiomeColors.SupernovaCyan

@Composable
private fun rememberDinoAmbientVisuals(
    ambient: DinoRunnerAmbientState,
): DinoAmbientVisuals {
    val colorScheme = MaterialTheme.colorScheme
    val targets = dinoAmbientTargets(ambient, colorScheme)
    val animationsEnabled = rememberAnimationsEnabled()
    val duration = animationDuration(animationsEnabled, PolentitaMotion.artwork)
    val animationSpec = tween<Color>(duration)
    val top by androidx.compose.animation.animateColorAsState(
        targetValue = targets.top,
        animationSpec = animationSpec,
        label = "Cielo de Inicio",
    )
    val bottom by androidx.compose.animation.animateColorAsState(
        targetValue = targets.bottom,
        animationSpec = animationSpec,
        label = "Horizonte de Inicio",
    )
    val accent by androidx.compose.animation.animateColorAsState(
        targetValue = targets.accent,
        animationSpec = animationSpec,
        label = "Acento ambiental de Inicio",
    )
    val secondary by androidx.compose.animation.animateColorAsState(
        targetValue = targets.secondary,
        animationSpec = animationSpec,
        label = "Acento ambiental secundario",
    )
    val overlayAlpha by animateFloatAsState(
        targetValue = targets.overlayAlpha,
        animationSpec = tween(duration),
        label = "Intensidad del cielo de Inicio",
    )
    val surfaceTintStrength by animateFloatAsState(
        targetValue = targets.surfaceTintStrength,
        animationSpec = tween(duration),
        label = "Matiz de superficies de Inicio",
    )
    return DinoAmbientVisuals(
        state = ambient,
        top = top,
        bottom = bottom,
        accent = accent,
        secondary = secondary,
        overlayAlpha = overlayAlpha,
        surfaceTintStrength = surfaceTintStrength,
    )
}

private fun dinoAmbientTargets(
    ambient: DinoRunnerAmbientState,
    colorScheme: ColorScheme,
): DinoAmbientTargets {
    val spec = dinoBiomeSpec(ambient, colorScheme)
    return DinoAmbientTargets(
        top = spec.sky,
        bottom = spec.horizon,
        accent = spec.accent,
        secondary = spec.secondary,
        overlayAlpha = spec.overlayAlpha,
        surfaceTintStrength = spec.surfaceTintStrength,
    )
}

@Composable
private fun DinoHomeAmbientSurfaceTheme(
    visuals: DinoAmbientVisuals,
    palette: ArtworkPalette,
    content: @Composable (ArtworkPalette) -> Unit,
) {
    val colorScheme = MaterialTheme.colorScheme
    val strength = visuals.surfaceTintStrength
    val surface = lerp(colorScheme.surface, visuals.accent, strength)
    val surfaceLow = lerp(colorScheme.surfaceContainerLow, visuals.secondary, strength * 0.78f)
    val surfaceHigh = lerp(colorScheme.surfaceContainerHigh, visuals.accent, strength * 0.86f)
    val surfaceHighest = lerp(
        colorScheme.surfaceContainerHighest,
        visuals.secondary,
        strength * 0.72f,
    )
    val background = lerp(colorScheme.background, visuals.top, strength * 0.45f)
    val ambientPalette = palette.copy(
        dominant = lerp(palette.dominant, visuals.accent, strength * 1.30f),
        vibrant = lerp(palette.vibrant, visuals.secondary, strength),
        muted = lerp(palette.muted, visuals.secondary, strength),
        background = lerp(palette.background, visuals.top, strength * 0.80f),
        surface = lerp(palette.surface, visuals.accent, strength),
    )
    MaterialTheme(
        colorScheme = colorScheme.copy(
            background = background,
            surface = surface,
            surfaceContainerLowest = background,
            surfaceContainerLow = surfaceLow,
            surfaceContainer = surface,
            surfaceContainerHigh = surfaceHigh,
            surfaceContainerHighest = surfaceHighest,
            surfaceVariant = surfaceHigh,
        ),
        typography = MaterialTheme.typography,
        shapes = MaterialTheme.shapes,
    ) {
        content(ambientPalette)
    }
}

@Composable
private fun DinoHomeAmbientOverlay(
    visuals: DinoAmbientVisuals,
    dinoSession: DinoRunnerSession,
) {
    val unifiedBackground = lerp(visuals.top, visuals.bottom, 0.48f)
    val elapsedMs = dinoSession.state.elapsedMs
    Box(
        Modifier
            .fillMaxSize()
            .graphicsLayer { alpha = visuals.overlayAlpha }
            .background(unifiedBackground),
    )

    DinoHomeAmbientDecorations(
        ambient = visuals.state,
        top = unifiedBackground,
        colorScheme = MaterialTheme.colorScheme,
        animationsEnabled = rememberAnimationsEnabled(),
        motionTimeMs = elapsedMs,
    )
}

@Composable
private fun DinoHomeAmbientDecorations(
    ambient: DinoRunnerAmbientState,
    top: Color,
    colorScheme: ColorScheme,
    animationsEnabled: Boolean,
    motionTimeMs: Long,
) {
    val duration = animationDuration(animationsEnabled, PolentitaMotion.artwork)
    AnimatedVisibility(
        visible = ambient.active,
        enter = fadeIn(tween(duration)),
        exit = fadeOut(tween(duration)),
    ) {
        Box(Modifier.fillMaxSize()) {
            when (ambient.phase) {
                DinoRunnerAmbientPhase.DAY -> DinoHomeDayDecorations(
                    colorScheme = colorScheme,
                    motionTimeMs = motionTimeMs,
                    animationsEnabled = animationsEnabled,
                )
                DinoRunnerAmbientPhase.SUNSET -> DinoHomeSunsetDecorations(
                    colorScheme = colorScheme,
                    motionTimeMs = motionTimeMs,
                    animationsEnabled = animationsEnabled,
                )
                DinoRunnerAmbientPhase.NIGHT -> DinoHomeNightDecorations(
                    top = top,
                    colorScheme = colorScheme,
                    motionTimeMs = motionTimeMs,
                    animationsEnabled = animationsEnabled,
                )
                DinoRunnerAmbientPhase.AURORA -> DinoHomeAuroraDecorations(
                    colorScheme = colorScheme,
                    animationsEnabled = animationsEnabled,
                    motionTimeMs = motionTimeMs,
                )
                DinoRunnerAmbientPhase.ECLIPSE -> DinoHomeEclipseDecorations(
                    top = top,
                    colorScheme = colorScheme,
                    animationsEnabled = animationsEnabled,
                    motionTimeMs = motionTimeMs,
                )
                DinoRunnerAmbientPhase.DEEP_SPACE -> DinoHomeDeepSpaceDecorations(
                    colorScheme = colorScheme,
                    animationsEnabled = animationsEnabled,
                    motionTimeMs = motionTimeMs,
                )
                DinoRunnerAmbientPhase.NEBULA -> DinoHomeNebulaDecorations(
                    colorScheme = colorScheme,
                    animationsEnabled = animationsEnabled,
                    motionTimeMs = motionTimeMs,
                )
                DinoRunnerAmbientPhase.HYPERSPACE -> DinoHomeHyperspaceDecorations(
                    colorScheme = colorScheme,
                    animationsEnabled = animationsEnabled,
                    motionTimeMs = motionTimeMs,
                )
                DinoRunnerAmbientPhase.SINGULARITY -> DinoHomeSingularityDecorations(
                    colorScheme = colorScheme,
                    animationsEnabled = animationsEnabled,
                    motionTimeMs = motionTimeMs,
                )
                DinoRunnerAmbientPhase.SUPERNOVA -> DinoHomeSupernovaDecorations(
                    colorScheme = colorScheme,
                    animationsEnabled = animationsEnabled,
                    motionTimeMs = motionTimeMs,
                )
            }
            if (ambient.globalChromeActive) {
                DinoHomePersistentMeteorLayer(
                    ambient = ambient,
                    colorScheme = colorScheme,
                    animationsEnabled = animationsEnabled,
                    motionTimeMs = motionTimeMs,
                )
            }
        }
    }
}

private fun ambientMotionProgress(
    motionTimeMs: Long,
    durationMs: Long,
    animationsEnabled: Boolean,
    fallback: Float,
): Float = if (animationsEnabled) {
    (motionTimeMs.mod(durationMs)).toFloat() / durationMs.toFloat()
} else {
    fallback
}

@Composable
private fun DinoHomeDayDecorations(
    colorScheme: ColorScheme,
    motionTimeMs: Long,
    animationsEnabled: Boolean,
) {
    val progress = ambientMotionProgress(motionTimeMs, 16_000L, animationsEnabled, 0.28f)
    Canvas(Modifier.fillMaxSize()) {
        drawHomeStars(
            color = DinoBiomeColors.DaySand,
            progress = progress,
            alphaMultiplier = 0.24f,
        )
        val center = Offset(size.width * 0.79f, size.height * 0.16f)
        val pulse = (kotlin.math.sin(progress * DINO_AMBIENT_TWO_PI) + 1f) * 0.5f
        val radius = (size.minDimension * (0.052f + pulse * 0.003f)).coerceIn(26f, 70f)
        repeat(3) { ring ->
            drawCircle(
                color = DinoBiomeColors.DaySand.copy(alpha = 0.055f - ring * 0.012f),
                radius = radius * (1.55f + ring * 0.66f + pulse * 0.08f),
                center = center,
                style = Stroke(width = (2.2f - ring * 0.35f).coerceAtLeast(1.2f)),
            )
        }
        drawCircle(
            color = colorScheme.tertiary.copy(alpha = 0.22f),
            radius = radius,
            center = center,
        )
        repeat(14) { index ->
            val local = wrapAmbientUnit(progress * (0.42f + index % 3 * 0.16f) + index * 0.071f)
            val x = local * size.width
            val y = size.height * (0.24f + ((index * 23) % 51) / 100f)
            drawLine(
                color = DinoBiomeColors.DaySand.copy(alpha = 0.08f + index % 3 * 0.025f),
                start = Offset(x - size.width * 0.018f, y),
                end = Offset(x, y - size.height * 0.004f),
                strokeWidth = (size.minDimension * 0.0012f).coerceAtLeast(1.2f),
                cap = StrokeCap.Round,
            )
        }
    }
}

@Composable
private fun DinoHomeSunsetDecorations(
    colorScheme: ColorScheme,
    motionTimeMs: Long,
    animationsEnabled: Boolean,
) {
    val progress = ambientMotionProgress(motionTimeMs, 12_000L, animationsEnabled, 0.25f)
    Canvas(Modifier.fillMaxSize()) {
        drawHomeStars(
            color = colorScheme.tertiary,
            progress = progress,
            alphaMultiplier = 0.52f,
        )
        val center = Offset(size.width * 0.82f, size.height * 0.19f)
        val pulse = (kotlin.math.sin(progress * DINO_AMBIENT_TWO_PI) + 1f) * 0.5f
        val radius = (size.minDimension * (0.060f + pulse * 0.004f)).coerceIn(30f, 78f)
        drawCircle(
            color = colorScheme.tertiary.copy(alpha = 0.12f + pulse * 0.04f),
            radius = radius + 20f + pulse * 6f,
            center = center,
        )
        drawCircle(
            color = colorScheme.tertiary.copy(alpha = 0.28f),
            radius = radius,
            center = center,
        )
    }
}

@Composable
private fun DinoHomeNightDecorations(
    top: Color,
    colorScheme: ColorScheme,
    motionTimeMs: Long,
    animationsEnabled: Boolean,
) {
    val progress = ambientMotionProgress(motionTimeMs, 14_000L, animationsEnabled, 0.32f)
    Canvas(Modifier.fillMaxSize()) {
        drawHomeStars(
            color = colorScheme.onBackground,
            progress = progress,
            alphaMultiplier = 1.18f,
        )
        drawHomeMoon(top, colorScheme.onBackground)
    }
}

@Composable
private fun DinoHomeAuroraDecorations(
    colorScheme: ColorScheme,
    animationsEnabled: Boolean,
    motionTimeMs: Long,
) {
    val drift = ambientMotionProgress(motionTimeMs, 12_000L, animationsEnabled, 0.18f)
    Canvas(Modifier.fillMaxSize()) {
        drawHomeStars(
            color = colorScheme.onBackground,
            progress = drift,
            alphaMultiplier = 1.12f,
        )
        drawHomeAurora(
            progress = drift,
            firstColor = colorScheme.primary,
            secondColor = colorScheme.tertiary,
        )
    }
}

@Composable
private fun DinoHomeEclipseDecorations(
    top: Color,
    colorScheme: ColorScheme,
    animationsEnabled: Boolean,
    motionTimeMs: Long,
) {
    val progress = ambientMotionProgress(motionTimeMs, 9_000L, animationsEnabled, 0.38f)
    Canvas(Modifier.fillMaxSize()) {
        drawHomeStars(
            color = colorScheme.onBackground,
            progress = progress,
            alphaMultiplier = 1.16f,
        )
        drawHomeEclipse(
            top = top,
            eclipseColor = colorScheme.secondary,
            ringColor = colorScheme.primary,
        )
    }
}

@Composable
private fun DinoHomeDeepSpaceDecorations(
    colorScheme: ColorScheme,
    animationsEnabled: Boolean,
    motionTimeMs: Long,
) {
    val progress = ambientMotionProgress(motionTimeMs, 18_000L, animationsEnabled, 0.42f)
    Canvas(Modifier.fillMaxSize()) {
        drawHomeNebula(
            firstColor = DINO_COSMIC_BLUE,
            secondColor = DINO_AURORA_VIOLET,
            progress = progress,
        )
        drawHomeStars(
            color = colorScheme.onBackground,
            progress = progress,
            alphaMultiplier = 1.24f,
        )
        drawHomeConstellations(progress, colorScheme.tertiary)
    }
}

@Composable
private fun DinoHomePersistentMeteorLayer(
    ambient: DinoRunnerAmbientState,
    colorScheme: ColorScheme,
    animationsEnabled: Boolean,
    motionTimeMs: Long,
) {
    val spec = dinoLateEffectSpec(ambient)
    if (spec.meteorCount == 0 && spec.snowflakeCount == 0) return

    val progress = ambientMotionProgress(
        motionTimeMs = motionTimeMs,
        durationMs = spec.meteorDurationMs,
        animationsEnabled = animationsEnabled,
        fallback = 0.38f,
    )
    val (trailColor, headColor) = when (ambient.phase) {
        DinoRunnerAmbientPhase.ECLIPSE -> DINO_ECLIPSE_CORONA to colorScheme.onBackground
        DinoRunnerAmbientPhase.DEEP_SPACE -> colorScheme.tertiary to colorScheme.onBackground
        DinoRunnerAmbientPhase.NEBULA -> DINO_NEBULA_CYAN to colorScheme.onBackground
        DinoRunnerAmbientPhase.HYPERSPACE -> DINO_HYPERSPACE_ICE to colorScheme.onBackground
        DinoRunnerAmbientPhase.SINGULARITY -> DINO_SINGULARITY_ROSE to DINO_SINGULARITY_VIOLET
        DinoRunnerAmbientPhase.SUPERNOVA -> DINO_SUPERNOVA_GOLD to DINO_SUPERNOVA_CYAN
        else -> colorScheme.primary to colorScheme.onBackground
    }

    Canvas(Modifier.fillMaxSize()) {
        if (spec.meteorCount > 0) {
            drawHomeMeteorShower(
                progress = progress,
                trailColor = trailColor,
                headColor = headColor,
                alphaMultiplier = spec.meteorAlpha,
                meteorCount = spec.meteorCount,
                speedMultiplier = spec.meteorSpeed,
            )
        }
        if (spec.snowflakeCount > 0) {
            drawHomeSnowfall(
                progress = progress,
                color = colorScheme.onBackground,
                accent = DINO_NEBULA_CYAN,
                snowflakeCount = spec.snowflakeCount,
            )
        }
    }
}

@Composable
private fun DinoHomeNebulaDecorations(
    colorScheme: ColorScheme,
    animationsEnabled: Boolean,
    motionTimeMs: Long,
) {
    val drift = ambientMotionProgress(motionTimeMs, 20_000L, animationsEnabled, 0.45f)
    Canvas(Modifier.fillMaxSize()) {
        drawHomeNebula(
            firstColor = DINO_NEBULA_MAGENTA,
            secondColor = DINO_NEBULA_CYAN,
            progress = drift,
            alphaMultiplier = 1.55f,
        )
        drawHomeStars(
            color = colorScheme.onBackground,
            progress = drift,
            alphaMultiplier = 1.24f,
        )
        drawHomeAurora(
            progress = drift,
            firstColor = DINO_NEBULA_CYAN.copy(alpha = 0.72f),
            secondColor = DINO_NEBULA_MAGENTA.copy(alpha = 0.64f),
        )
    }
}

@Composable
private fun DinoHomeHyperspaceDecorations(
    colorScheme: ColorScheme,
    animationsEnabled: Boolean,
    motionTimeMs: Long,
) {
    val travel = ambientMotionProgress(motionTimeMs, 9_000L, animationsEnabled, 0.38f)
    Canvas(Modifier.fillMaxSize()) {
        drawHomeNebula(
            firstColor = DINO_DEEP_SPACE,
            secondColor = DINO_COSMIC_BLUE,
            progress = travel,
        )
        drawHomeStars(
            color = colorScheme.onBackground,
            progress = travel,
            alphaMultiplier = 1.28f,
        )
        drawHomeHyperspace(
            progress = travel,
            color = DINO_HYPERSPACE_ICE,
        )
    }
}

@Composable
private fun DinoHomeSingularityDecorations(
    colorScheme: ColorScheme,
    animationsEnabled: Boolean,
    motionTimeMs: Long,
) {
    val orbit = ambientMotionProgress(motionTimeMs, 16_000L, animationsEnabled, 0.24f)
    Canvas(Modifier.fillMaxSize()) {
        drawHomeStars(
            color = colorScheme.onBackground,
            progress = orbit,
            alphaMultiplier = 1.14f,
        )
        drawHomeNebula(
            firstColor = DINO_SINGULARITY_VIOLET,
            secondColor = DINO_SINGULARITY_ROSE,
            progress = orbit,
            alphaMultiplier = 0.82f,
        )
        drawHomeSingularity(
            progress = orbit,
            coreColor = DINO_ECLIPSE_OBSIDIAN,
            firstRing = DINO_SINGULARITY_VIOLET,
            secondRing = DINO_SINGULARITY_ROSE,
        )
    }
}

@Composable
private fun DinoHomeSupernovaDecorations(
    colorScheme: ColorScheme,
    animationsEnabled: Boolean,
    motionTimeMs: Long,
) {
    val pulse = ambientMotionProgress(motionTimeMs, 14_000L, animationsEnabled, 0.58f)
    Canvas(Modifier.fillMaxSize()) {
        drawHomeNebula(
            firstColor = DINO_SUPERNOVA_GOLD,
            secondColor = DINO_SUPERNOVA_CYAN,
            progress = pulse,
            alphaMultiplier = 1.18f,
        )
        drawHomeStars(
            color = colorScheme.onBackground,
            progress = pulse,
            alphaMultiplier = 1.30f,
        )
        drawHomeSupernova(
            progress = pulse,
            coreColor = colorScheme.onBackground,
            gold = DINO_SUPERNOVA_GOLD,
            cyan = DINO_SUPERNOVA_CYAN,
        )
    }
}

private data class HomeAmbientStar(
    val x: Float,
    val y: Float,
    val radiusFraction: Float,
    val alpha: Float,
    val depth: Float,
)

private val HOME_AMBIENT_STARS = List(72) { index ->
    HomeAmbientStar(
        x = ((index * 47 + 13) % 127) / 127f,
        y = 0.03f + ((index * 59 + 11) % 89) / 100f,
        radiusFraction = 0.0009f + (index % 4) * 0.00028f,
        alpha = 0.24f + (index % 5) * 0.075f,
        depth = 0.32f + (index % 7) * 0.13f,
    )
}

private val HOME_CONSTELLATION_EDGES = listOf(
    0 to 4,
    4 to 9,
    9 to 14,
    14 to 19,
    19 to 25,
)

private data class HomeAmbientMeteor(
    val x: Float,
    val y: Float,
    val travel: Float,
    val length: Float,
    val offset: Float,
    val alpha: Float,
)

private val HOME_AMBIENT_METEORS = listOf(
    HomeAmbientMeteor(-0.42f, -0.28f, 1.84f, 0.070f, 0.04f, 0.82f),
    HomeAmbientMeteor(-0.18f, -0.34f, 1.78f, 0.086f, 0.17f, 0.70f),
    HomeAmbientMeteor(0.08f, -0.25f, 1.68f, 0.064f, 0.29f, 0.76f),
    HomeAmbientMeteor(-0.56f, -0.12f, 1.92f, 0.096f, 0.41f, 0.62f),
    HomeAmbientMeteor(0.22f, -0.40f, 1.72f, 0.058f, 0.53f, 0.72f),
    HomeAmbientMeteor(-0.30f, -0.22f, 1.82f, 0.078f, 0.64f, 0.66f),
    HomeAmbientMeteor(-0.04f, -0.31f, 1.76f, 0.066f, 0.74f, 0.78f),
    HomeAmbientMeteor(-0.48f, -0.18f, 1.88f, 0.088f, 0.85f, 0.58f),
    HomeAmbientMeteor(0.16f, -0.37f, 1.70f, 0.060f, 0.93f, 0.68f),
    HomeAmbientMeteor(-0.62f, -0.42f, 1.96f, 0.074f, 0.09f, 0.66f),
    HomeAmbientMeteor(0.30f, -0.30f, 1.64f, 0.056f, 0.22f, 0.74f),
    HomeAmbientMeteor(-0.24f, -0.46f, 1.86f, 0.090f, 0.35f, 0.60f),
    HomeAmbientMeteor(0.02f, -0.16f, 1.74f, 0.068f, 0.47f, 0.80f),
    HomeAmbientMeteor(-0.52f, -0.36f, 1.90f, 0.082f, 0.58f, 0.64f),
    HomeAmbientMeteor(0.26f, -0.22f, 1.66f, 0.062f, 0.69f, 0.76f),
    HomeAmbientMeteor(-0.12f, -0.39f, 1.80f, 0.084f, 0.79f, 0.70f),
    HomeAmbientMeteor(-0.38f, -0.14f, 1.86f, 0.072f, 0.88f, 0.68f),
    HomeAmbientMeteor(0.12f, -0.48f, 1.72f, 0.058f, 0.97f, 0.78f),
)

private data class HomeAmbientSnowflake(
    val x: Float,
    val y: Float,
    val radiusFraction: Float,
    val speed: Float,
    val drift: Float,
    val offset: Float,
    val alpha: Float,
)

private val HOME_AMBIENT_SNOWFLAKES = List(42) { index ->
    HomeAmbientSnowflake(
        x = ((index * 37 + 11) % 101) / 101f,
        y = ((index * 53 + 7) % 97) / 97f,
        radiusFraction = 0.0018f + (index % 4) * 0.00055f,
        speed = 0.42f + (index % 5) * 0.11f,
        drift = 0.012f + (index % 4) * 0.006f,
        offset = ((index * 29) % 83) / 83f,
        alpha = 0.34f + (index % 5) * 0.09f,
    )
}

private fun wrapAmbientUnit(value: Float): Float = ((value % 1f) + 1f) % 1f

private fun homeAmbientStarPosition(
    star: HomeAmbientStar,
    index: Int,
    progress: Float,
): Offset {
    val phase = progress * DINO_AMBIENT_TWO_PI
    val xFrequency = (1 + index % 3).toFloat()
    val yFrequency = (1 + index % 2).toFloat()
    val driftX = kotlin.math.sin(phase * xFrequency + index * 0.71f) *
        0.014f * star.depth
    val driftY = kotlin.math.cos(phase * yFrequency + index * 0.43f) *
        0.009f * star.depth
    return Offset(
        x = wrapAmbientUnit(star.x + driftX),
        y = (star.y + driftY).coerceIn(0.02f, 0.96f),
    )
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawHomeStars(
    color: Color,
    progress: Float = 0f,
    alphaMultiplier: Float = 1f,
) {
    HOME_AMBIENT_STARS.forEachIndexed { index, star ->
        val position = homeAmbientStarPosition(star, index, progress)
        val pulseWave = (
            kotlin.math.sin(
                progress * DINO_AMBIENT_TWO_PI * (1 + index % 3) + index * 0.83f,
            ) + 1f
            ) * 0.5f
        val pulse = 0.72f + 0.28f * pulseWave
        drawCircle(
            color = color.copy(
                alpha = (star.alpha * alphaMultiplier * pulse).coerceIn(0f, 1f),
            ),
            radius = (size.minDimension * star.radiusFraction).coerceIn(1.5f, 4.5f),
            center = Offset(position.x * size.width, position.y * size.height),
        )
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawHomeMoon(
    top: Color,
    celestialColor: Color,
) {
    val center = Offset(size.width * 0.83f, size.height * 0.17f)
    val radius = (size.minDimension * 0.055f).coerceIn(28f, 72f)
    drawCircle(
        color = celestialColor.copy(alpha = 0.12f),
        radius = radius + 8f,
        center = center,
    )
    drawCircle(
        color = celestialColor.copy(alpha = 0.42f),
        radius = radius,
        center = center,
    )
    drawCircle(
        color = top.copy(alpha = 0.92f),
        radius = radius * 0.86f,
        center = Offset(center.x + 10f, center.y - 8f),
    )
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawHomeAurora(
    progress: Float,
    firstColor: Color,
    secondColor: Color,
) {
    val travel = kotlin.math.sin(progress * DINO_AMBIENT_TWO_PI) * size.width * 0.12f
    val firstBand = Path().apply {
        moveTo(-size.width * 0.16f + travel, size.height * 0.48f)
        cubicTo(
            size.width * 0.22f + travel,
            size.height * 0.30f,
            size.width * 0.45f + travel,
            size.height * 0.58f,
            size.width * 0.76f + travel,
            size.height * 0.38f,
        )
        cubicTo(
            size.width * 0.92f + travel,
            size.height * 0.28f,
            size.width * 1.04f + travel,
            size.height * 0.46f,
            size.width * 1.16f + travel,
            size.height * 0.32f,
        )
    }
    val secondBand = Path().apply {
        moveTo(-size.width * 0.18f - travel, size.height * 0.62f)
        cubicTo(
            size.width * 0.18f - travel,
            size.height * 0.46f,
            size.width * 0.46f - travel,
            size.height * 0.70f,
            size.width * 0.82f - travel,
            size.height * 0.52f,
        )
        cubicTo(
            size.width * 0.96f - travel,
            size.height * 0.44f,
            size.width * 1.06f - travel,
            size.height * 0.58f,
            size.width * 1.18f - travel,
            size.height * 0.48f,
        )
    }
    drawPath(
        path = firstBand,
        color = firstColor.copy(alpha = 0.19f),
        style = Stroke(
            width = (size.minDimension * 0.025f).coerceAtLeast(12f),
            cap = StrokeCap.Round,
        ),
    )
    drawPath(
        path = secondBand,
        color = secondColor.copy(alpha = 0.16f),
        style = Stroke(
            width = (size.minDimension * 0.020f).coerceAtLeast(10f),
            cap = StrokeCap.Round,
        ),
    )
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawHomeEclipse(
    top: Color,
    eclipseColor: Color,
    ringColor: Color,
) {
    val center = Offset(size.width * 0.83f, size.height * 0.17f)
    val radius = (size.minDimension * 0.055f).coerceIn(28f, 72f)
    drawCircle(
        color = ringColor.copy(alpha = 0.12f),
        radius = radius + 12f,
        center = center,
    )
    drawCircle(
        color = ringColor.copy(alpha = 0.46f),
        radius = radius + 4f,
        center = center,
        style = Stroke(width = 3f),
    )
    drawCircle(
        color = eclipseColor.copy(alpha = 0.78f),
        radius = radius,
        center = center,
    )
    drawCircle(
        color = top.copy(alpha = 0.96f),
        radius = radius * 0.87f,
        center = Offset(center.x + 10f, center.y - 8f),
    )
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawHomeMeteorShower(
    progress: Float,
    trailColor: Color,
    headColor: Color,
    alphaMultiplier: Float = 1f,
    meteorCount: Int = HOME_AMBIENT_METEORS.size,
    speedMultiplier: Float = 1f,
) {
    HOME_AMBIENT_METEORS.take(meteorCount).forEachIndexed { index, meteor ->
        val cycles = (1 + index % 3).toFloat()
        val meteorProgress = wrapAmbientUnit(progress * cycles * speedMultiplier + meteor.offset)
        val x = (meteor.x + meteorProgress * meteor.travel) * size.width
        val y = (meteor.y + meteorProgress * meteor.travel * 0.72f) * size.height
        val visibility = kotlin.math.sin(meteorProgress * Math.PI).toFloat()
            .coerceAtLeast(0f)
        val alpha = meteor.alpha * alphaMultiplier * visibility
        drawLine(
            color = trailColor.copy(alpha = alpha),
            start = Offset(x, y),
            end = Offset(
                x - meteor.length * size.width,
                y - meteor.length * size.height * 0.48f,
            ),
            strokeWidth = (size.minDimension * 0.0022f).coerceAtLeast(2.2f),
            cap = StrokeCap.Round,
        )
        drawCircle(
            color = headColor.copy(alpha = (alpha + 0.12f).coerceAtMost(1f)),
            radius = (size.minDimension * 0.0035f).coerceIn(2.5f, 5f),
            center = Offset(x, y),
        )
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawHomeSnowfall(
    progress: Float,
    color: Color,
    accent: Color,
    snowflakeCount: Int,
) {
    HOME_AMBIENT_SNOWFLAKES.take(snowflakeCount).forEachIndexed { index, snowflake ->
        val localProgress = wrapAmbientUnit(
            snowflake.y + progress * snowflake.speed + snowflake.offset,
        )
        val sway = kotlin.math.sin(
            (progress * snowflake.speed + snowflake.offset) * DINO_AMBIENT_TWO_PI,
        ) * snowflake.drift
        val center = Offset(
            x = wrapAmbientUnit(snowflake.x + sway) * size.width,
            y = (-0.04f + localProgress * 1.08f) * size.height,
        )
        val radius = (size.minDimension * snowflake.radiusFraction).coerceIn(1.8f, 5.8f)
        val flakeColor = if (index % 5 == 0) accent else color
        val alpha = snowflake.alpha * (0.72f + 0.28f * kotlin.math.sin(localProgress * Math.PI).toFloat())

        if (index % 4 == 0) {
            repeat(3) { arm ->
                val angle = Math.toRadians((arm * 60f).toDouble())
                val direction = Offset(
                    kotlin.math.cos(angle).toFloat(),
                    kotlin.math.sin(angle).toFloat(),
                )
                drawLine(
                    color = flakeColor.copy(alpha = alpha),
                    start = center - direction * radius,
                    end = center + direction * radius,
                    strokeWidth = (radius * 0.34f).coerceAtLeast(1f),
                    cap = StrokeCap.Round,
                )
            }
        } else {
            drawCircle(
                color = flakeColor.copy(alpha = alpha),
                radius = radius * 0.42f,
                center = center,
            )
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawHomeConstellations(
    progress: Float,
    lineColor: Color,
) {
    HOME_CONSTELLATION_EDGES.forEach { (startIndex, endIndex) ->
        val start = homeAmbientStarPosition(
            HOME_AMBIENT_STARS[startIndex],
            startIndex,
            progress,
        )
        val end = homeAmbientStarPosition(
            HOME_AMBIENT_STARS[endIndex],
            endIndex,
            progress,
        )
        drawLine(
            color = lineColor.copy(alpha = 0.24f),
            start = Offset(start.x * size.width, start.y * size.height),
            end = Offset(end.x * size.width, end.y * size.height),
            strokeWidth = (size.minDimension * 0.0008f).coerceAtLeast(1.2f),
        )
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawHomeNebula(
    firstColor: Color,
    secondColor: Color,
    progress: Float = 0f,
    alphaMultiplier: Float = 1f,
) {
    val phase = progress * DINO_AMBIENT_TWO_PI
    val drift = kotlin.math.sin(phase) * size.width * 0.055f
    val breathe = (kotlin.math.cos(phase) + 1f) * 0.5f
    drawCircle(
        color = firstColor.copy(alpha = 0.095f * alphaMultiplier),
        radius = size.minDimension * (0.16f + breathe * 0.018f),
        center = Offset(
            size.width * 0.18f + drift,
            size.height * (0.20f + kotlin.math.cos(phase) * 0.025f),
        ),
    )
    drawCircle(
        color = secondColor.copy(alpha = 0.082f * alphaMultiplier),
        radius = size.minDimension * (0.14f + (1f - breathe) * 0.016f),
        center = Offset(
            size.width * 0.72f - drift,
            size.height * (0.30f + kotlin.math.sin(phase) * 0.022f),
        ),
    )
    drawCircle(
        color = firstColor.copy(alpha = 0.055f * alphaMultiplier),
        radius = size.minDimension * (0.105f + breathe * 0.012f),
        center = Offset(
            size.width * (0.48f + kotlin.math.cos(phase) * 0.025f),
            size.height * (0.52f + kotlin.math.sin(phase) * 0.025f),
        ),
    )
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawHomeHyperspace(
    progress: Float,
    color: Color,
) {
    HOME_AMBIENT_STARS.forEachIndexed { index, star ->
        if (star.y > 0.58f || (star.x in 0.27f..0.73f && index % 3 != 0)) return@forEachIndexed
        val cycles = (1 + index % 3).toFloat()
        val localProgress = wrapAmbientUnit(progress * cycles + star.x + index * 0.037f)
        val travel = 0.026f + star.depth * 0.034f + localProgress * 0.018f
        val x = wrapAmbientUnit(star.x + localProgress * 0.24f)
        val y = wrapAmbientUnit(star.y + localProgress * 0.62f)
        val head = Offset(x * size.width, y * size.height)
        drawLine(
            color = color.copy(alpha = (star.alpha * 0.82f).coerceAtMost(0.52f)),
            start = head,
            end = Offset(head.x - travel * size.width, head.y - travel * size.height * 0.22f),
            strokeWidth = (size.minDimension * star.radiusFraction).coerceIn(1.2f, 3.4f),
            cap = StrokeCap.Round,
        )
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawHomeSingularity(
    progress: Float,
    coreColor: Color,
    firstRing: Color,
    secondRing: Color,
) {
    val center = Offset(size.width * 0.63f, size.height * 0.21f)
    val radius = (size.minDimension * 0.078f).coerceIn(38f, 96f)
    val phase = progress * DINO_AMBIENT_TWO_PI
    val pulse = (kotlin.math.sin(phase * 2f) + 1f) * 0.5f

    repeat(5) { index ->
        val orbitRadius = radius * (1.90f + index * 0.72f + pulse * 0.08f)
        val reverse = if (index % 2 == 0) 1f else -1f
        drawArc(
            color = if (index % 2 == 0) {
                firstRing.copy(alpha = 0.18f - index * 0.018f)
            } else {
                secondRing.copy(alpha = 0.16f - index * 0.016f)
            },
            startAngle = progress * 360f * reverse + index * 47f,
            sweepAngle = 188f + index * 17f,
            useCenter = false,
            topLeft = Offset(
                center.x - orbitRadius,
                center.y - orbitRadius * (0.40f + index * 0.025f),
            ),
            size = androidx.compose.ui.geometry.Size(
                orbitRadius * 2f,
                orbitRadius * (0.80f + index * 0.05f),
            ),
            style = Stroke(
                width = (size.minDimension * (0.0032f - index * 0.00028f))
                    .coerceAtLeast(1.4f),
                cap = StrokeCap.Round,
            ),
        )
    }

    repeat(20) { index ->
        val reverse = if (index % 2 == 0) 1f else -1f
        val angularSpeed = 0.52f + (index % 4) * 0.15f
        val angle = phase * reverse * angularSpeed + index * 2.399f
        val previousAngle = angle - reverse * 0.12f
        val orbitRadius = radius * (1.38f + (index % 7) * 0.34f)
        val verticalScale = 0.38f + (index % 3) * 0.07f
        val point = Offset(
            center.x + kotlin.math.cos(angle) * orbitRadius,
            center.y + kotlin.math.sin(angle) * orbitRadius * verticalScale,
        )
        val trail = Offset(
            center.x + kotlin.math.cos(previousAngle) * orbitRadius,
            center.y + kotlin.math.sin(previousAngle) * orbitRadius * verticalScale,
        )
        val particleColor = if (index % 2 == 0) firstRing else secondRing
        drawLine(
            color = particleColor.copy(alpha = 0.22f + (index % 3) * 0.045f),
            start = trail,
            end = point,
            strokeWidth = (size.minDimension * 0.0022f).coerceAtLeast(1.5f),
            cap = StrokeCap.Round,
        )
        drawCircle(
            color = particleColor.copy(alpha = 0.58f),
            radius = (size.minDimension * (0.0022f + (index % 3) * 0.00045f))
                .coerceIn(1.6f, 4.2f),
            center = point,
        )
    }

    drawCircle(
        color = firstRing.copy(alpha = 0.10f + pulse * 0.05f),
        radius = radius * (1.72f + pulse * 0.10f),
        center = center,
    )
    drawArc(
        color = firstRing.copy(alpha = 0.42f),
        startAngle = progress * 360f,
        sweepAngle = 226f,
        useCenter = false,
        topLeft = Offset(center.x - radius * 1.42f, center.y - radius * 0.68f),
        size = androidx.compose.ui.geometry.Size(radius * 2.84f, radius * 1.36f),
        style = Stroke(width = (size.minDimension * 0.004f).coerceAtLeast(2.4f)),
    )
    drawArc(
        color = secondRing.copy(alpha = 0.32f),
        startAngle = 180f - progress * 360f,
        sweepAngle = 188f,
        useCenter = false,
        topLeft = Offset(center.x - radius * 1.62f, center.y - radius * 0.84f),
        size = androidx.compose.ui.geometry.Size(radius * 3.24f, radius * 1.68f),
        style = Stroke(width = (size.minDimension * 0.0028f).coerceAtLeast(1.8f)),
    )
    drawCircle(
        color = coreColor.copy(alpha = 0.96f),
        radius = radius,
        center = center,
    )
    drawCircle(
        color = firstRing.copy(alpha = 0.52f),
        radius = radius * 1.05f,
        center = center,
        style = Stroke(width = 2.2f),
    )
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawHomeSupernova(
    progress: Float,
    coreColor: Color,
    gold: Color,
    cyan: Color,
) {
    val center = Offset(size.width * 0.20f, size.height * 0.17f)
    val phase = progress * DINO_AMBIENT_TWO_PI
    val wave = (kotlin.math.sin(phase) + 1f) * 0.5f
    val radius = (size.minDimension * (0.036f + wave * 0.010f)).coerceIn(18f, 58f)
    val maximumWaveRadius = size.maxDimension * 0.88f

    repeat(3) { index ->
        val waveProgress = wrapAmbientUnit(progress + index / 3f)
        val waveAlpha = kotlin.math.sin(waveProgress * Math.PI).toFloat()
            .coerceAtLeast(0f)
        val waveRadius = radius * 1.25f + maximumWaveRadius * waveProgress
        drawCircle(
            color = if (index % 2 == 0) {
                gold.copy(alpha = waveAlpha * 0.20f)
            } else {
                cyan.copy(alpha = waveAlpha * 0.18f)
            },
            radius = waveRadius,
            center = center,
            style = Stroke(
                width = (size.minDimension * (0.0052f - index * 0.0008f))
                    .coerceAtLeast(2f),
            ),
        )
    }

    repeat(32) { index ->
        val cycles = (1 + index % 2).toFloat()
        val particleProgress = wrapAmbientUnit(progress * cycles + index / 32f)
        val particleAlpha = kotlin.math.sin(particleProgress * Math.PI).toFloat()
            .coerceAtLeast(0f)
        val angle = index * 2.399f + kotlin.math.sin(phase + index * 0.37f) * 0.08f
        val direction = Offset(kotlin.math.cos(angle), kotlin.math.sin(angle))
        val distance = radius * 1.12f + maximumWaveRadius * 0.78f * particleProgress
        val head = center + direction * distance
        val trailLength = size.minDimension * (0.018f + (index % 4) * 0.006f)
        val particleColor = if (index % 2 == 0) gold else cyan
        drawLine(
            color = particleColor.copy(alpha = particleAlpha * 0.30f),
            start = head - direction * trailLength,
            end = head,
            strokeWidth = (size.minDimension * 0.0022f).coerceAtLeast(1.5f),
            cap = StrokeCap.Round,
        )
        drawCircle(
            color = particleColor.copy(alpha = particleAlpha * 0.54f),
            radius = (size.minDimension * (0.0023f + (index % 3) * 0.0005f))
                .coerceIn(1.7f, 4.4f),
            center = head,
        )
    }

    drawCircle(
        color = gold.copy(alpha = 0.10f + wave * 0.04f),
        radius = radius * (3.2f + wave * 0.34f),
        center = center,
    )
    drawCircle(
        color = cyan.copy(alpha = 0.10f),
        radius = radius * 2.05f,
        center = center,
    )
    drawCircle(
        color = gold.copy(alpha = 0.48f),
        radius = radius,
        center = center,
    )
    drawCircle(
        color = coreColor.copy(alpha = 0.78f),
        radius = radius * 0.34f,
        center = center,
    )
    repeat(8) { index ->
        val angle = Math.toRadians((index * 45f + progress * 360f).toDouble())
        val direction = Offset(kotlin.math.cos(angle).toFloat(), kotlin.math.sin(angle).toFloat())
        val rayPulse = 0.72f + 0.28f * (
            kotlin.math.sin(phase * 2f + index * 0.82f) + 1f
            ) * 0.5f
        drawLine(
            color = if (index % 2 == 0) {
                gold.copy(alpha = 0.26f)
            } else {
                cyan.copy(alpha = 0.22f)
            },
            start = center + direction * radius * 1.18f,
            end = center + direction * radius * (2.10f + rayPulse * 0.72f),
            strokeWidth = (size.minDimension * 0.0018f).coerceAtLeast(1.6f),
            cap = StrokeCap.Round,
        )
    }
}

@Composable
private fun HomeContent(
    state: HomeUiState,
    tagline: String,
    updateState: AppUpdateUiState,
    featured: Song?,
    palette: ArtworkPalette,
    playbackVisual: PlaybackUiState,
    playerViewModel: PlayerViewModel,
    onPlaylist: (Long) -> Unit,
    onAlbum: (Long) -> Unit,
    onLibrary: () -> Unit,
    onDownloads: () -> Unit,
    dinoAmbient: DinoRunnerAmbientState,
    dinoSession: DinoRunnerSession,
    onDinoAmbientChanged: (DinoRunnerAmbientState) -> Unit,
    onDinoHighScore: (Int) -> Unit,
    onUpdate: () -> Unit,
    onDismissUpdate: () -> Unit,
) {
    val homeContextLabel = stringResource(R.string.nav_home)
    val listState = rememberLazyListState()
    val bottomControlAreaPx = with(LocalDensity.current) {
        DINO_BOTTOM_CONTROL_AREA_HEIGHT.roundToPx()
    }
    val dinoGameplayVisible by remember(listState, bottomControlAreaPx) {
        derivedStateOf {
            val layoutInfo = listState.layoutInfo
            val gameItem = layoutInfo.visibleItemsInfo
                .firstOrNull { it.key == "home-dino-runner" }
                ?: return@derivedStateOf false
            val sceneHeight = (gameItem.size - bottomControlAreaPx).coerceAtLeast(1)
            val visibleStart = maxOf(gameItem.offset, layoutInfo.viewportStartOffset)
            val visibleEnd = minOf(
                gameItem.offset + sceneHeight,
                layoutInfo.viewportEndOffset,
            )
            val visibleHeight = (visibleEnd - visibleStart).coerceAtLeast(0)
            visibleHeight >= sceneHeight * 0.80f
        }
    }
    val scrollFraction by remember(listState) {
        derivedStateOf {
            (
                (listState.firstVisibleItemIndex * HEADER_SCROLL_DISTANCE_DP) +
                    listState.firstVisibleItemScrollOffset
                ) / HEADER_SCROLL_DISTANCE_DP
        }
    }
    val clampedScrollFraction = scrollFraction.coerceIn(0f, 1f)
    val recentAlbums = remember(state.recent) {
        state.recent
            .filter { it.albumId != null && it.albumName.isNotBlank() }
            .distinctBy(Song::albumId)
    }
    val continueSongs = remember(state.history, featured?.id) {
        state.history.filterNot { it.id == featured?.id }
    }
    val playableSongs = remember(state.history, state.recent, state.favorites, state.mostPlayed) {
        (state.history + state.recent + state.favorites + state.mostPlayed).distinctBy(Song::id)
    }
    val favoritesPlaylistId = remember(state.playlists) {
        state.playlists.firstOrNull { playlist ->
            playlist.name.equals(PlaylistNames.TUS_ME_GUSTA, ignoreCase = true)
        }?.id
    }
    val epicFocus = dinoAmbient.active

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                top = 0.dp,
                bottom = 0.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(PolentitaSpacing.medium),
        ) {
            item(key = "home-header") {
                HomeFocusLayer(epicFocus) {
                    CollapsingHomeHeader(
                        scrollFraction = clampedScrollFraction,
                        tagline = tagline,
                    )
                }
            }
            featured?.let { song ->
                item(key = "home-featured-${song.id}") {
                    HomeFocusLayer(epicFocus) {
                        FeaturedNowPlayingCard(
                            song = song,
                            palette = palette,
                            playerViewModel = playerViewModel,
                            onPlay = {
                                playerViewModel.playHomeSong(
                                    song = song,
                                    songs = playableSongs,
                                    key = "home-mix",
                                    label = homeContextLabel,
                                )
                            },
                        )
                    }
                }
            }
            item(key = "home-quick-actions") {
                HomeFocusLayer(epicFocus) {
                    HomeQuickActions(
                        playableSongs = playableSongs,
                        favoriteSongs = state.favorites,
                        favoritesPlaylistId = favoritesPlaylistId,
                        palette = palette,
                        playerViewModel = playerViewModel,
                        onLibrary = onLibrary,
                        onDownloads = onDownloads,
                        onFavoritesPlaylist = {
                            favoritesPlaylistId?.let(onPlaylist)
                        },
                    )
                }
            }
            if (continueSongs.isNotEmpty()) {
                item(key = "home-continue") {
                    HomeFocusLayer(epicFocus) {
                        ContinueListeningSection(continueSongs, playerViewModel)
                    }
                }
            }
            if (state.recent.isNotEmpty()) {
                item(key = "home-recent") {
                    HomeFocusLayer(epicFocus) {
                        RecentTracksSection(state.recent, playerViewModel)
                    }
                }
            }
            if (state.favorites.isNotEmpty()) {
                item(key = "home-favorites") {
                    HomeFocusLayer(epicFocus) {
                        FavoriteTracksSection(state.favorites, playerViewModel, palette)
                    }
                }
            }
            if (recentAlbums.isNotEmpty()) {
                item(key = "home-albums") {
                    HomeFocusLayer(epicFocus) {
                        RecentAlbumsSection(recentAlbums, onAlbum)
                    }
                }
            }
            if (state.playlists.isNotEmpty()) {
                item(key = "home-playlists") {
                    HomeFocusLayer(epicFocus) {
                        PlaylistsSection(state.playlists, onPlaylist)
                    }
                }
            }
            if (state.mostPlayed.isNotEmpty()) {
                item(key = "home-most-played") {
                    HomeFocusLayer(epicFocus) {
                        MostPlayedRanking(state.mostPlayed, playerViewModel, palette)
                    }
                }
            }
            item(key = "home-dino-spacing") {
                Spacer(Modifier.height(96.dp))
            }
            item(key = "home-dino-runner") {
                DinoRunnerGame(
                    session = dinoSession,
                    gameplayVisible = dinoGameplayVisible,
                    bottomControlAreaHeight = DINO_BOTTOM_CONTROL_AREA_HEIGHT,
                    onAmbientChanged = onDinoAmbientChanged,
                    onNewHighScore = onDinoHighScore,
                )
            }
        }
        if (updateState.info != null && !updateState.dismissed) {
            HomeUpdateBubble(
                downloading = updateState.downloading,
                onUpdate = onUpdate,
                onDismiss = onDismissUpdate,
            )
        }
    }
}

@Composable
private fun HomeUpdateBubble(
    downloading: Boolean,
    onUpdate: () -> Unit,
    onDismiss: () -> Unit,
) {
    Box(Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 12.dp, end = PolentitaSpacing.medium)
                .zIndex(1f),
        ) {
            Surface(
                onClick = onUpdate,
                enabled = !downloading,
                modifier = Modifier.size(48.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.error.copy(alpha = 0.16f),
                contentColor = MaterialTheme.colorScheme.error,
                border = BorderStroke(
                    1.dp,
                    MaterialTheme.colorScheme.error.copy(alpha = 0.55f),
                ),
                tonalElevation = 0.dp,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    if (downloading) {
                        androidx.compose.material3.CircularProgressIndicator(
                            modifier = Modifier.size(19.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.error,
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.SystemUpdate,
                            contentDescription = stringResource(R.string.update_available_accessibility),
                            modifier = Modifier.size(23.dp),
                        )
                    }
                }
            }
            Surface(
                onClick = onDismiss,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .size(22.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceContainerHighest,
                contentColor = MaterialTheme.colorScheme.onSurface,
                tonalElevation = 2.dp,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = stringResource(R.string.update_dismiss),
                        modifier = Modifier.size(14.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun HomeFocusLayer(
    focused: Boolean,
    content: @Composable () -> Unit,
) {
    val animationsEnabled = rememberAnimationsEnabled()
    val duration = animationDuration(animationsEnabled, PolentitaMotion.artwork)
    val blurRadius by animateDpAsState(
        targetValue = if (focused) 12.dp else 0.dp,
        animationSpec = tween(duration),
        label = "Desenfoque de foco del Dino",
    )
    val contentAlpha by animateFloatAsState(
        targetValue = if (focused) 0.10f else 1f,
        animationSpec = tween(duration),
        label = "Atenuación de foco del Dino",
    )
    val contentScale by animateFloatAsState(
        targetValue = if (focused) 0.975f else 1f,
        animationSpec = tween(duration),
        label = "Profundidad de foco del Dino",
    )
    Box(
        Modifier
            .fillMaxWidth()
            .blur(blurRadius, edgeTreatment = BlurredEdgeTreatment.Unbounded)
            .graphicsLayer {
                alpha = contentAlpha
                scaleX = contentScale
                scaleY = contentScale
            },
    ) {
        content()
    }
}

@Composable
private fun CollapsingHomeHeader(
    scrollFraction: Float,
    tagline: String,
) {
    val contentAlpha = (1f - scrollFraction * 1.25f).coerceIn(0f, 1f)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(112.dp)
            .padding(horizontal = PolentitaSpacing.xl, vertical = PolentitaSpacing.small)
            .graphicsLayer {
                alpha = contentAlpha
                translationY = -scrollFraction * 18f
            },
        verticalArrangement = Arrangement.Bottom,
    ) {
        Text(
            stringResource(R.string.home_hifi_label),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            stringResource(R.string.home_title),
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Black,
        )
        Text(
            tagline,
            modifier = Modifier.graphicsLayer {
                alpha = (contentAlpha * 0.88f).coerceIn(0f, 1f)
            },
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun HomeQuickActions(
    playableSongs: List<Song>,
    favoriteSongs: List<Song>,
    favoritesPlaylistId: Long?,
    palette: ArtworkPalette,
    playerViewModel: PlayerViewModel,
    onLibrary: () -> Unit,
    onDownloads: () -> Unit,
    onFavoritesPlaylist: () -> Unit,
) {
    val homeContextLabel = stringResource(R.string.nav_home)
    LazyRow(
        contentPadding = PaddingValues(horizontal = PolentitaSpacing.medium),
        horizontalArrangement = Arrangement.spacedBy(PolentitaSpacing.small),
    ) {
        item(key = "quick-shuffle") {
            QuickActionPill(
                icon = Icons.Default.Shuffle,
                label = stringResource(R.string.home_quick_random),
                enabled = playableSongs.isNotEmpty(),
                primary = true,
                palette = palette,
                onClick = {
                    playerViewModel.playShuffled(
                        songs = playableSongs,
                        kind = PlaybackContextKind.HOME,
                        key = "home-mix",
                        label = homeContextLabel,
                    )
                },
            )
        }
        item(key = "quick-favorites") {
            QuickActionPill(
                icon = Icons.Default.Favorite,
                label = stringResource(R.string.favorites),
                enabled = favoriteSongs.isNotEmpty() && favoritesPlaylistId != null,
                palette = palette,
                onClick = onFavoritesPlaylist,
            )
        }
        item(key = "quick-downloads") {
            QuickActionPill(
                icon = Icons.Default.Download,
                label = stringResource(R.string.home_quick_downloads),
                palette = palette,
                onClick = onDownloads,
            )
        }
        item(key = "quick-library") {
            QuickActionPill(
                icon = Icons.Default.LibraryMusic,
                label = stringResource(R.string.library),
                palette = palette,
                onClick = onLibrary,
            )
        }
    }
}

@Composable
private fun QuickActionPill(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    primary: Boolean = false,
    palette: ArtworkPalette? = null,
) {
    val surface = when {
        primary && palette != null -> palette.accent.copy(alpha = 0.86f)
        primary -> MaterialTheme.colorScheme.primary
        palette != null -> palette.surface.copy(alpha = 0.92f)
        else -> MaterialTheme.colorScheme.surfaceContainerHigh
    }
    val content = if (primary && palette != null) {
        palette.onAccent
    } else if (primary) {
        MaterialTheme.colorScheme.onPrimary
    } else {
        MaterialTheme.colorScheme.onSurface
    }
    Pressable(
        modifier = Modifier.height(48.dp),
        enabled = enabled,
        onClick = onClick,
    ) {
        Surface(
            color = surface,
            contentColor = content,
            shape = RoundedCornerShape(PolentitaRadii.pill),
            border = BorderStroke(
                1.dp,
                if (primary) {
                    palette?.onAccent?.copy(alpha = 0.14f)
                        ?: MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.14f)
                } else {
                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = PolentitaOpacity.border)
                },
            ),
            tonalElevation = if (primary) 2.dp else 0.dp,
        ) {
            Row(
                modifier = Modifier.padding(horizontal = PolentitaSpacing.large),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(PolentitaSpacing.small),
            ) {
                Icon(icon, contentDescription = label, modifier = Modifier.size(19.dp))
                Text(label, style = MaterialTheme.typography.labelLarge, maxLines = 1)
            }
        }
    }
}

@Composable
private fun ContinueListeningSection(
    songs: List<Song>,
    playerViewModel: PlayerViewModel,
) {
    Column {
        HomeSectionHeader(stringResource(R.string.continue_listening))
        Spacer(Modifier.height(PolentitaSpacing.xs))
        LazyRow(
            contentPadding = PaddingValues(horizontal = PolentitaSpacing.large),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(songs, key = Song::id) { song ->
                ContinueListeningCard(song, songs, playerViewModel)
            }
        }
    }
}

@Composable
private fun ContinueListeningCard(
    song: Song,
    songs: List<Song>,
    playerViewModel: PlayerViewModel,
) {
    val contextLabel = stringResource(R.string.continue_listening)
    val progress by remember(playerViewModel, song.id) {
        playerViewModel.state.map { playback ->
            if (playback.currentSongId == song.id && playback.durationMs > 0) {
                PlaybackProgress(playback.positionMs, playback.durationMs)
            } else {
                null
            }
        }.distinctUntilChanged()
    }.collectAsStateWithLifecycle(initialValue = null)
    Pressable(
        modifier = Modifier.width(HomeShelfCardMetrics.continueWidth),
        onClick = {
            playerViewModel.playHomeSong(
                song,
                songs,
                "continue",
                contextLabel,
            )
        },
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(PolentitaRadii.large),
            border = BorderStroke(
                1.dp,
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = PolentitaOpacity.border),
            ),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.94f),
            ),
        ) {
            Column {
                Box {
                    Artwork(
                        song.coverUri,
                        stringResource(R.string.home_cover_description, song.title),
                        Modifier
                            .fillMaxWidth()
                            .aspectRatio(1f),
                        seed = "${song.title}|${song.artist}",
                    )
                    progress?.let { current ->
                        LinearProgressIndicator(
                            progress = { (current.positionMs.toFloat() / current.durationMs).coerceIn(0f, 1f) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(3.dp)
                                .align(Alignment.BottomCenter),
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = Color.Transparent,
                        )
                    }
                }
                Column(Modifier.padding(PolentitaSpacing.small)) {
                    Text(
                        song.title,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        song.artist.ifBlank { stringResource(R.string.unknown_artist) },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun RecentTracksSection(
    songs: List<Song>,
    playerViewModel: PlayerViewModel,
) {
    val contextLabel = stringResource(R.string.recently_added)
    Column {
        HomeSectionHeader(stringResource(R.string.recently_added))
        Spacer(Modifier.height(PolentitaSpacing.xs))
        LazyRow(
            contentPadding = PaddingValues(horizontal = PolentitaSpacing.large),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(songs, key = Song::id) { song ->
                Pressable(
                    modifier = Modifier.width(188.dp),
                    onClick = {
                        playerViewModel.playHomeSong(
                            song,
                            songs,
                            "recent",
                            contextLabel,
                        )
                    },
                ) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(PolentitaRadii.medium),
                        border = BorderStroke(
                            1.dp,
                            MaterialTheme.colorScheme.outlineVariant.copy(alpha = PolentitaOpacity.border),
                        ),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.92f),
                        ),
                    ) {
                        Row(
                            modifier = Modifier.padding(PolentitaSpacing.small),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Artwork(
                                song.coverUri,
                                stringResource(R.string.home_cover_description, song.title),
                                Modifier.size(56.dp),
                                seed = "${song.title}|${song.artist}",
                            )
                            Spacer(Modifier.width(PolentitaSpacing.small))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    song.title,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                Text(
                                    song.artist.ifBlank { stringResource(R.string.unknown_artist) },
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FavoriteTracksSection(
    songs: List<Song>,
    playerViewModel: PlayerViewModel,
    palette: ArtworkPalette,
) {
    val contextLabel = stringResource(R.string.favorites)
    Column {
        HomeSectionHeader(stringResource(R.string.favorites))
        Spacer(Modifier.height(PolentitaSpacing.xs))
        LazyRow(
            contentPadding = PaddingValues(horizontal = PolentitaSpacing.large),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(songs, key = Song::id) { song ->
                Pressable(
                    modifier = Modifier.width(HomeShelfCardMetrics.favoriteWidth),
                    onClick = {
                        playerViewModel.playHomeSong(
                            song,
                            songs,
                            "favorites",
                            contextLabel,
                        )
                    },
                ) {
                    Card(
                        modifier = Modifier.fillMaxSize(),
                        shape = RoundedCornerShape(PolentitaRadii.large),
                        border = BorderStroke(
                            1.dp,
                            MaterialTheme.colorScheme.outlineVariant.copy(alpha = PolentitaOpacity.border),
                        ),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.94f),
                        ),
                    ) {
                        Column(Modifier.padding(PolentitaSpacing.small)) {
                            Box {
                                Artwork(
                                    song.coverUri,
                                    stringResource(R.string.home_cover_description, song.title),
                                    Modifier
                                        .fillMaxWidth()
                                        .aspectRatio(1f),
                                    seed = "${song.title}|${song.artist}",
                                )
                                Surface(
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .padding(PolentitaSpacing.small)
                                        .size(28.dp),
                                    shape = CircleShape,
                                    color = palette.accent.copy(alpha = 0.88f),
                                    contentColor = palette.onAccent,
                                ) {
                                    Icon(
                                        Icons.Default.Favorite,
                                        contentDescription = stringResource(R.string.favorites),
                                        modifier = Modifier.padding(6.dp),
                                    )
                                }
                            }
                            Column(Modifier.padding(PolentitaSpacing.small)) {
                                Text(
                                    song.title,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                Text(
                                    song.artist.ifBlank { stringResource(R.string.unknown_artist) },
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RecentAlbumsSection(
    albums: List<Song>,
    onAlbum: (Long) -> Unit,
) {
    Column {
        HomeSectionHeader(stringResource(R.string.recent_albums))
        Spacer(Modifier.height(PolentitaSpacing.xs))
        LazyRow(
            contentPadding = PaddingValues(horizontal = PolentitaSpacing.large),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(albums, key = { it.albumId ?: it.id }) { album ->
                Pressable(
                    modifier = Modifier.width(132.dp),
                    onClick = { album.albumId?.let(onAlbum) },
                ) {
                    Column {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = PolentitaSpacing.small, top = PolentitaSpacing.small),
                        ) {
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(1f)
                                    .padding(start = PolentitaSpacing.small, top = PolentitaSpacing.small)
                                    .clip(RoundedCornerShape(PolentitaRadii.medium))
                                    .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                            )
                            Artwork(
                                album.coverUri,
                                stringResource(R.string.home_album_cover_description, album.albumName),
                                Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(1f)
                                    .clip(RoundedCornerShape(PolentitaRadii.medium)),
                                seed = "${album.albumName}|${album.artist}",
                            )
                        }
                        Spacer(Modifier.height(PolentitaSpacing.small))
                        Text(
                            album.albumName,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            album.artist.ifBlank { stringResource(R.string.unknown_artist) },
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PlaylistsSection(
    playlists: List<com.polentita.music.core.database.PlaylistSummary>,
    onPlaylist: (Long) -> Unit,
) {
    Column {
        HomeSectionHeader(stringResource(R.string.playlists))
        Spacer(Modifier.height(PolentitaSpacing.xs))
        LazyRow(
            contentPadding = PaddingValues(horizontal = PolentitaSpacing.large),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(playlists, key = { it.id }) { playlist ->
                Pressable(
                    modifier = Modifier.width(132.dp),
                    onClick = { onPlaylist(playlist.id) },
                ) {
                    Column {
                        Artwork(
                            playlist.coverUri,
                            stringResource(R.string.home_playlist_cover_description, playlist.name),
                            Modifier
                                .fillMaxWidth()
                                .aspectRatio(1f),
                            seed = playlist.name,
                        )
                        Spacer(Modifier.height(PolentitaSpacing.small))
                        Text(
                            playlist.name,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            stringResource(R.string.song_count, playlist.songCount),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MostPlayedRanking(
    songs: List<Song>,
    playerViewModel: PlayerViewModel,
    palette: ArtworkPalette,
) {
    val rankedSongs = songs.take(5)
    val contextLabel = stringResource(R.string.most_played)
    Column {
        HomeSectionHeader(stringResource(R.string.most_played))
        Spacer(Modifier.height(PolentitaSpacing.small))
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = PolentitaSpacing.large)
                .clip(RoundedCornerShape(PolentitaRadii.large))
                .background(MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.86f))
                .border(
                    1.dp,
                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = PolentitaOpacity.border),
                    RoundedCornerShape(PolentitaRadii.large),
                )
            .padding(vertical = PolentitaSpacing.xs),
        ) {
            rankedSongs.forEachIndexed { index, song ->
                Pressable(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        playerViewModel.playHomeSong(
                            song,
                            rankedSongs,
                            "most-played",
                            contextLabel,
                        )
                    },
                ) {
                    Row(
                        modifier = Modifier.padding(
                            horizontal = PolentitaSpacing.medium,
                            vertical = PolentitaSpacing.xs,
                        ),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "${index + 1}",
                            modifier = Modifier.width(34.dp),
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Black,
                            color = if (index < 3) {
                                palette.accent.copy(alpha = 1f - index * 0.14f)
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        )
                        Artwork(
                            song.coverUri,
                            stringResource(R.string.home_cover_description, song.title),
                            Modifier.size(48.dp),
                            seed = "${song.title}|${song.artist}",
                        )
                        Spacer(Modifier.width(PolentitaSpacing.small))
                        Column(Modifier.weight(1f)) {
                            Text(
                                song.title,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                song.artist.ifBlank { stringResource(R.string.unknown_artist) },
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            if (song.playCount > 0) {
                                Text(
                                    stringResource(R.string.home_play_count, song.playCount),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        Icon(
                            Icons.Default.PlayArrow,
                            contentDescription = stringResource(R.string.play),
                            tint = palette.accent,
                        )
                    }
                }
                if (index < rankedSongs.lastIndex) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 58.dp)
                            .height(1.dp)
                            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)),
                    )
                }
            }
        }
    }
}

@Composable
private fun FeaturedNowPlayingCard(
    song: Song,
    palette: ArtworkPalette,
    playerViewModel: PlayerViewModel,
    onPlay: () -> Unit,
) {
    val animationsEnabled = rememberAnimationsEnabled()
    val playback by remember(playerViewModel, song.id) {
        playerViewModel.state.map { state ->
            FeaturedPlaybackState(
                isCurrent = state.currentSongId == song.id,
                isPlaying = state.isPlaying,
                positionMs = state.positionMs,
                durationMs = state.durationMs,
            )
        }.distinctUntilChanged()
    }.collectAsStateWithLifecycle(
        initialValue = FeaturedPlaybackState(false, false, 0, 0),
    )
    val isFavorite by playerViewModel.isFavorite.collectAsStateWithLifecycle()
    val targetSong = FeaturedSongContent(song.id, song.title, song.artist, song.coverUri)
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = PolentitaSpacing.large),
        shape = RoundedCornerShape(PolentitaRadii.hero),
        border = BorderStroke(1.dp, palette.accent.copy(alpha = 0.22f)),
        colors = CardDefaults.cardColors(
            containerColor = palette.surface.copy(alpha = 0.94f),
            contentColor = contentColorFor(MaterialTheme.colorScheme.surface),
        ),
        elevation = CardDefaults.cardElevation(8.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.horizontalGradient(
                        listOf(
                            palette.dominant.copy(alpha = 0.58f),
                            palette.background.copy(alpha = 0.96f),
                        ),
                    ),
                )
                .padding(PolentitaSpacing.medium),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AnimatedContent(
                    targetState = targetSong,
                    transitionSpec = {
                        (
                            fadeIn(tween(animationDuration(animationsEnabled, 300))) +
                                scaleIn(
                                    initialScale = 0.96f,
                                    animationSpec = tween(animationDuration(animationsEnabled, 300)),
                                )
                            ) togetherWith (
                            fadeOut(tween(animationDuration(animationsEnabled, 240))) +
                                scaleOut(
                                    targetScale = 0.96f,
                                    animationSpec = tween(animationDuration(animationsEnabled, 240)),
                                )
                            )
                    },
                    label = "Portada destacada",
                ) { current ->
                    Artwork(
                        current.coverUri,
                        stringResource(R.string.home_cover_description, current.title),
                        Modifier.size(116.dp),
                        seed = "${current.title}|${current.artist}",
                        elevated = true,
                    )
                }
                Spacer(Modifier.width(PolentitaSpacing.medium))
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            stringResource(
                                when {
                                    playback.isCurrent && playback.isPlaying -> R.string.now_playing
                                    playback.isCurrent -> R.string.playback_paused
                                    else -> R.string.resume_playback
                                },
                            ),
                            style = MaterialTheme.typography.labelSmall,
                            color = palette.accent,
                            maxLines = 1,
                        )
                        Spacer(Modifier.weight(1f))
                        Box(
                            modifier = Modifier.size(48.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (playback.isCurrent) {
                                IconButton(onClick = playerViewModel::toggleFavorite) {
                                    Icon(
                                        if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                        contentDescription = stringResource(R.string.favorites),
                                        tint = palette.accent,
                                    )
                                }
                            }
                        }
                    }
                    Column {
                        Text(
                            song.title,
                            style = MaterialTheme.typography.titleLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            fontWeight = FontWeight.Black,
                        )
                        Text(
                            song.artist.ifBlank { stringResource(R.string.unknown_artist) },
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(Modifier.height(PolentitaSpacing.small))
                    Column(Modifier.heightIn(min = 32.dp)) {
                        if (playback.isCurrent && playback.durationMs > 0) {
                            LinearProgressIndicator(
                                progress = {
                                    (playback.positionMs.toFloat() / playback.durationMs.coerceAtLeast(1))
                                        .coerceIn(0f, 1f)
                                },
                                color = palette.accent,
                                trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.16f),
                                modifier = Modifier.fillMaxWidth().height(5.dp),
                            )
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text(formatDuration(playback.positionMs), style = MaterialTheme.typography.labelSmall)
                                Text(formatDuration(playback.durationMs), style = MaterialTheme.typography.labelSmall)
                            }
                            Spacer(Modifier.height(PolentitaSpacing.small))
                        }
                    }
                    Button(
                        onClick = {
                            if (playback.isCurrent) playerViewModel.togglePlayPause() else onPlay()
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(PolentitaRadii.pill),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = palette.accent,
                            contentColor = palette.onAccent,
                        ),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                    ) {
                        AnimatedContent(
                            targetState = playback.isCurrent && playback.isPlaying,
                            label = "Control destacado",
                        ) { playing ->
                            Icon(
                                if (playing) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = null,
                            )
                        }
                        Spacer(Modifier.width(PolentitaSpacing.small))
                        Text(
                            stringResource(
                                when {
                                    playback.isCurrent && playback.isPlaying -> R.string.pause
                                    playback.isCurrent -> R.string.continue_playback
                                    else -> R.string.play
                                },
                            ),
                            maxLines = 1,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HomeSectionHeader(
    title: String,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = PolentitaSpacing.large),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            title,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )
        if (actionLabel != null && onAction != null) {
            Text(
                actionLabel,
                modifier = Modifier.clickable(onClick = onAction),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun HomeEmptyState(onLibrary: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(PolentitaSpacing.huge),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Surface(
            modifier = Modifier.size(88.dp),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f),
            contentColor = MaterialTheme.colorScheme.primary,
        ) {
            Icon(Icons.Default.MusicNote, null, Modifier.padding(25.dp))
        }
        Spacer(Modifier.height(PolentitaSpacing.large))
        Text(
            stringResource(R.string.home_empty_title),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Black,
        )
        Spacer(Modifier.height(PolentitaSpacing.small))
        Text(
            stringResource(R.string.home_empty_message),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(PolentitaSpacing.large))
        Button(onClick = onLibrary, shape = RoundedCornerShape(PolentitaRadii.pill)) {
            Icon(Icons.Default.LibraryMusic, contentDescription = null)
            Spacer(Modifier.width(PolentitaSpacing.small))
            Text(stringResource(R.string.home_empty_action))
        }
    }
}

@Composable
private fun Pressable(
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit,
    content: @Composable () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val animationsEnabled = rememberAnimationsEnabled()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.97f else 1f,
        animationSpec = if (animationsEnabled) {
            spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessMedium,
            )
        } else {
            tween(0)
        },
        label = "Escala de presión",
    )
    Box(
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                alpha = if (enabled) 1f else PolentitaOpacity.disabled
            }
            .clickable(
                enabled = enabled,
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            ),
    ) {
        content()
    }
}

private const val HEADER_SCROLL_DISTANCE_DP = 180f
