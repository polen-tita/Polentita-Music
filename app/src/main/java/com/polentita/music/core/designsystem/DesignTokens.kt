package com.polentita.music.core.designsystem

import android.animation.ValueAnimator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

object PolentitaSpacing {
    val xxs = 2.dp
    val xs = 4.dp
    val small = 8.dp
    val medium = 12.dp
    val large = 16.dp
    val xl = 20.dp
    val xxl = 24.dp
    val huge = 32.dp
}

object PolentitaCoverSize {
    val mini = 48.dp
    val row = 56.dp
    val shelf = 156.dp
    val albumGrid = 168.dp
    val hero = 288.dp
}

object PolentitaRadii {
    val small = 12.dp
    val medium = 16.dp
    val large = 24.dp
    val hero = 30.dp
    val pill = 100.dp
}

object PolentitaElevation {
    val resting = 0.dp
    val floating = 8.dp
    val artwork = 18.dp
}

object PolentitaOpacity {
    const val secondary = 0.72f
    const val disabled = 0.42f
    const val glass = 0.82f
    const val scrim = 0.66f
    const val subtle = 0.12f
    const val border = 0.28f
    const val surface = 0.94f
}

object PolentitaMotion {
    const val quick = 180
    const val standard = 280
    const val artwork = 420
    const val slow = 520
}

object PolentitaFallbackColors {
    val Background = Color(0xFF070A0C)
    val Surface = Color(0xFF101719)
    val SurfaceRaised = Color(0xFF1A2427)
    val Accent = Color(0xFF64D8E8)
    val QueueSwipe = Color(0xFF075B6B)
    val OnDark = Color(0xFFF7F5EF)
}

object PolentitaContentColors {
    val PrimaryOnDark = Color(0xFFF8F7F3)
    val SecondaryOnDark = Color(0xFFC9C7C1)
    val DisabledOnDark = Color(0xFF8B8984)
    val DarkOnBright = Color(0xFF15120F)
}

@Composable
fun rememberAnimationsEnabled(): Boolean = remember {
    ValueAnimator.areAnimatorsEnabled()
}

fun animationDuration(enabled: Boolean, durationMillis: Int): Int =
    if (enabled) durationMillis else 0
