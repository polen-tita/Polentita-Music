package com.polentita.music.core.designsystem

import android.graphics.Color as AndroidColor
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.core.graphics.ColorUtils
import kotlin.math.abs

@Immutable
data class ArtworkPalette(
    val dominant: Color,
    val vibrant: Color,
    val muted: Color,
    val background: Color,
    val surface: Color,
    val accent: Color,
    val onBackground: Color,
    val onAccent: Color,
)

object ArtworkColorAnalyzer {
    private const val MIN_TEXT_CONTRAST = 4.5

    fun analyze(pixels: IntArray, seed: String): ArtworkPalette {
        val usable = pixels.filter { AndroidColor.alpha(it) >= 160 }
        if (usable.isEmpty()) return fallback(seed)

        val buckets = linkedMapOf<Int, MutableList<Int>>()
        usable.forEach { color ->
            val key =
                ((AndroidColor.red(color) shr 4) shl 8) or
                    ((AndroidColor.green(color) shr 4) shl 4) or
                    (AndroidColor.blue(color) shr 4)
            buckets.getOrPut(key) { mutableListOf() }.add(color)
        }
        val candidates = buckets.values
            .sortedByDescending(List<Int>::size)
            .take(24)
            .map(::averageColor)

        val dominant = candidates.firstOrNull() ?: return fallback(seed)
        val vibrant = candidates.maxByOrNull { colorScore(it, preferredSaturation = 0.78f) }
            ?: dominant
        val muted = candidates.maxByOrNull { colorScore(it, preferredSaturation = 0.34f) }
            ?: dominant
        return buildPalette(dominant, vibrant, muted, seed)
    }

    fun fallback(seed: String): ArtworkPalette {
        val hash = stableHash(seed.ifBlank { "Polentita Music" })
        val hue = ((hash ushr 1) % 360).toFloat()
        val secondaryHue = (hue + 34f + (hash % 76)) % 360f
        val dominant = AndroidColor.HSVToColor(floatArrayOf(hue, 0.58f, 0.62f))
        val vibrant = AndroidColor.HSVToColor(floatArrayOf(secondaryHue, 0.76f, 0.82f))
        val muted = AndroidColor.HSVToColor(floatArrayOf(hue, 0.32f, 0.48f))
        return buildPalette(dominant, vibrant, muted, seed)
    }

    fun contrastRatio(foreground: Color, background: Color): Double =
        ColorUtils.calculateContrast(foreground.toArgb(), background.toArgb())

    fun hasReadableContrast(palette: ArtworkPalette): Boolean =
        contrastRatio(palette.onBackground, palette.background) >= MIN_TEXT_CONTRAST &&
            contrastRatio(palette.onAccent, palette.accent) >= MIN_TEXT_CONTRAST

    fun safeContentColor(background: Color): Color {
        val light = PolentitaContentColors.PrimaryOnDark
        val dark = PolentitaContentColors.DarkOnBright
        val lightContrast = contrastRatio(light, background)
        val darkContrast = contrastRatio(dark, background)
        return if (lightContrast >= MIN_TEXT_CONTRAST || lightContrast >= darkContrast) {
            light
        } else {
            dark
        }
    }

    private fun buildPalette(
        dominantColor: Int,
        vibrantColor: Int,
        mutedColor: Int,
        seed: String,
    ): ArtworkPalette {
        val dominant = Color(dominantColor)
        val vibrantArgb = normalizeAccent(vibrantColor, seed)
        val accent = Color(vibrantArgb)
        val backgroundArgb = ColorUtils.blendARGB(dominantColor, AndroidColor.BLACK, 0.78f)
        val surfaceArgb = ColorUtils.blendARGB(mutedColor, AndroidColor.BLACK, 0.68f)
        val onBackground = PolentitaContentColors.PrimaryOnDark.toArgb()
        val onAccent = safeContentColor(accent).toArgb()
        return ArtworkPalette(
            dominant = dominant,
            vibrant = Color(vibrantArgb),
            muted = Color(mutedColor),
            background = Color(backgroundArgb),
            surface = Color(surfaceArgb),
            accent = accent,
            onBackground = Color(onBackground),
            onAccent = Color(onAccent),
        )
    }

    private fun normalizeAccent(color: Int, seed: String): Int {
        val hsl = FloatArray(3)
        ColorUtils.colorToHSL(color, hsl)
        if (hsl[1] < 0.15f) {
            hsl[0] = ((stableHash(seed) ushr 1) % 360).toFloat()
        }
        hsl[1] = hsl[1].coerceIn(0.52f, 0.86f)
        hsl[2] = hsl[2].coerceIn(0.48f, 0.66f)
        var accent = ColorUtils.HSLToColor(hsl)
        val darkSurface = PolentitaFallbackColors.Background.toArgb()
        while (
            (
                ColorUtils.calculateContrast(accent, darkSurface) < MIN_TEXT_CONTRAST ||
                    bestContentContrast(accent) < MIN_TEXT_CONTRAST
                ) &&
            hsl[2] < 0.88f
        ) {
            hsl[2] = (hsl[2] + 0.04f).coerceAtMost(0.88f)
            accent = ColorUtils.HSLToColor(hsl)
        }
        return accent
    }

    private fun bestContentContrast(background: Int): Double = maxOf(
        ColorUtils.calculateContrast(
            PolentitaContentColors.PrimaryOnDark.toArgb(),
            background,
        ),
        ColorUtils.calculateContrast(
            PolentitaContentColors.DarkOnBright.toArgb(),
            background,
        ),
    )

    private fun colorScore(color: Int, preferredSaturation: Float): Float {
        val hsv = FloatArray(3)
        AndroidColor.colorToHSV(color, hsv)
        val saturationAffinity = 1f - abs(hsv[1] - preferredSaturation)
        val brightnessAffinity = 1f - abs(hsv[2] - 0.66f)
        return saturationAffinity * 0.72f + brightnessAffinity * 0.28f
    }

    private fun averageColor(colors: List<Int>): Int {
        val size = colors.size.coerceAtLeast(1)
        return AndroidColor.rgb(
            colors.sumOf(AndroidColor::red) / size,
            colors.sumOf(AndroidColor::green) / size,
            colors.sumOf(AndroidColor::blue) / size,
        )
    }

    private fun stableHash(value: String): Int {
        var hash = 0x51F15E
        value.forEach { character -> hash = hash * 31 + character.code }
        return hash and Int.MAX_VALUE
    }
}
