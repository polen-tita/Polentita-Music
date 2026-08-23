package com.polentita.music.core.designsystem

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import java.util.LinkedHashMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext

class ArtworkPaletteExtractor private constructor(
    private val context: Context,
) {
    private val cache = object : LinkedHashMap<String, ArtworkPalette>(48, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, ArtworkPalette>?): Boolean =
            size > MAX_CACHE_ENTRIES
    }
    private val workScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val inFlight = mutableMapOf<String, Deferred<ArtworkPalette>>()

    suspend fun extract(coverUri: String?, seed: String): ArtworkPalette {
        if (coverUri.isNullOrBlank()) return ArtworkColorAnalyzer.fallback(seed)
        val cacheKey = coverUri
        synchronized(cache) { cache[cacheKey] }?.let { return it }

        val deferred = synchronized(inFlight) {
            inFlight[cacheKey] ?: workScope.async {
                compute(cacheKey, seed)
            }.also { pending ->
                inFlight[cacheKey] = pending
                pending.invokeOnCompletion {
                    synchronized(inFlight) {
                        if (inFlight[cacheKey] === pending) inFlight.remove(cacheKey)
                    }
                }
            }
        }
        return deferred.await()
    }

    private suspend fun compute(cacheKey: String, seed: String): ArtworkPalette {
        val palette = withContext(Dispatchers.IO) {
            decodeSampledBitmap(cacheKey)
        }?.let { bitmap ->
            try {
                withContext(Dispatchers.Default) {
                    val pixels = IntArray(bitmap.width * bitmap.height)
                    bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
                    ArtworkColorAnalyzer.analyze(pixels, seed)
                }
            } finally {
                bitmap.recycle()
            }
        } ?: ArtworkColorAnalyzer.fallback(seed)

        synchronized(cache) { cache[cacheKey] = palette }
        return palette
    }

    private fun decodeSampledBitmap(value: String): Bitmap? {
        val uri = runCatching { Uri.parse(value) }.getOrNull() ?: return null
        if (uri.scheme !in SUPPORTED_SCHEMES) return null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        runCatching {
            context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, bounds)
            }
        }.getOrNull()
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        val options = BitmapFactory.Options().apply {
            inSampleSize = calculateSampleSize(bounds.outWidth, bounds.outHeight)
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        return runCatching {
            context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, options)
            }
        }.getOrNull()
    }

    private fun calculateSampleSize(width: Int, height: Int): Int {
        var sampleSize = 1
        while (width / sampleSize > TARGET_SIZE * 2 || height / sampleSize > TARGET_SIZE * 2) {
            sampleSize *= 2
        }
        return sampleSize
    }

    companion object {
        private const val TARGET_SIZE = 128
        private const val MAX_CACHE_ENTRIES = 48
        private val SUPPORTED_SCHEMES = setOf("content", "android.resource", "file")

        @Volatile
        private var instance: ArtworkPaletteExtractor? = null

        fun shared(context: Context): ArtworkPaletteExtractor =
            instance ?: synchronized(this) {
                instance ?: ArtworkPaletteExtractor(context.applicationContext).also { instance = it }
            }
    }
}

@Composable
fun rememberArtworkPalette(
    coverUri: String?,
    seed: String,
): ArtworkPalette {
    val context = LocalContext.current
    val extractor = remember(context.applicationContext) {
        ArtworkPaletteExtractor.shared(context.applicationContext)
    }
    val fallback = remember(seed) { ArtworkColorAnalyzer.fallback(seed) }
    val palette by produceState(
        initialValue = fallback,
        key1 = coverUri,
        key2 = seed,
    ) {
        value = extractor.extract(coverUri, seed)
    }
    return palette
}
