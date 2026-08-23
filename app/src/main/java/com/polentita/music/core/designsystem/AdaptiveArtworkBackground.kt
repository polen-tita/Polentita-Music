package com.polentita.music.core.designsystem

import android.os.Build
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.material3.LocalContentColor
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import androidx.compose.ui.platform.LocalContext

@Composable
fun AdaptiveArtworkBackground(
    coverUri: String?,
    palette: ArtworkPalette,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val animationsEnabled = rememberAnimationsEnabled()
    val context = LocalContext.current
    val dominant by animateColorAsState(
        targetValue = palette.dominant,
        animationSpec = tween(animationDuration(animationsEnabled, PolentitaMotion.artwork)),
        label = "Dominante ambiental",
    )
    val background by animateColorAsState(
        targetValue = palette.background,
        animationSpec = tween(animationDuration(animationsEnabled, PolentitaMotion.artwork)),
        label = "Fondo ambiental",
    )
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    0f to dominant.copy(alpha = 0.62f),
                    0.42f to background,
                    1f to PolentitaFallbackColors.Background,
                ),
        ),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        listOf(
                            dominant.copy(alpha = 0.16f),
                            Color.Transparent,
                        ),
                    ),
                ),
        )
        Crossfade(
            targetState = coverUri,
            animationSpec = tween(animationDuration(animationsEnabled, PolentitaMotion.artwork)),
            label = "Transición del fondo de portada",
            modifier = Modifier.fillMaxSize(),
        ) { currentArtwork ->
            if (!currentArtwork.isNullOrBlank()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(currentArtwork)
                            .size(BACKGROUND_THUMBNAIL_PX, BACKGROUND_THUMBNAIL_PX)
                            .memoryCacheKey("adaptive-background:$currentArtwork")
                            .build(),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(320.dp)
                            .scale(1.6f)
                            .then(
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                    Modifier.blur(28.dp)
                                } else {
                                    Modifier
                                },
                            ),
                    )
                }
            }
        }
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = PolentitaOpacity.scrim)),
        )
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to Color.Transparent,
                        0.48f to background.copy(alpha = 0.72f),
                        1f to PolentitaFallbackColors.Background.copy(alpha = 0.98f),
                    ),
                ),
        )
        CompositionLocalProvider(
            LocalContentColor provides PolentitaContentColors.PrimaryOnDark,
            content = content,
        )
    }
}

private const val BACKGROUND_THUMBNAIL_PX = 192
