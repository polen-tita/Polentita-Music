package com.polentita.music.core.artwork

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.common.util.BitmapLoader
import androidx.media3.datasource.DataSourceBitmapLoader
import coil3.size.Size
import coil3.transform.Transformation
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import kotlin.math.max
import kotlin.math.min

object SquareArtworkProcessor {
    private const val MIN_SQUARE_RATIO = 0.82f
    private const val MAX_SQUARE_RATIO = 1.22f
    private const val MAX_OUTPUT_SIZE = 1_024

    fun requiresComposition(width: Int, height: Int): Boolean {
        if (width <= 0 || height <= 0) return false
        val ratio = width.toFloat() / height
        return ratio !in MIN_SQUARE_RATIO..MAX_SQUARE_RATIO
    }

    fun transform(source: Bitmap): Bitmap {
        if (!requiresComposition(source.width, source.height)) return source
        val side = max(source.width, source.height).coerceAtMost(MAX_OUTPUT_SIZE)
        val output = Bitmap.createBitmap(side, side, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        val sourceSquare = min(source.width, source.height)
        val cropLeft = (source.width - sourceSquare) / 2
        val cropTop = (source.height - sourceSquare) / 2
        val crop = Bitmap.createBitmap(source, cropLeft, cropTop, sourceSquare, sourceSquare)
        val blurSide = (side / 18).coerceIn(28, 64)
        val softened = Bitmap.createScaledBitmap(crop, blurSide, blurSide, true)
        val destination = Rect(0, 0, side, side)
        canvas.drawBitmap(
            softened,
            null,
            destination,
            Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG),
        )
        canvas.drawColor(Color.argb(104, 0, 0, 0))

        val scale = min(side.toFloat() / source.width, side.toFloat() / source.height)
        val fittedWidth = source.width * scale
        val fittedHeight = source.height * scale
        val fitted = RectF(
            (side - fittedWidth) / 2f,
            (side - fittedHeight) / 2f,
            (side + fittedWidth) / 2f,
            (side + fittedHeight) / 2f,
        )
        canvas.drawBitmap(
            source,
            null,
            fitted,
            Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG),
        )
        if (crop !== source && !crop.isRecycled) crop.recycle()
        if (softened !== crop && !softened.isRecycled) softened.recycle()
        return output
    }
}

object SquareArtworkTransformation : Transformation() {
    override val cacheKey: String = "polentita-square-artwork-v1"

    override suspend fun transform(input: Bitmap, size: Size): Bitmap =
        SquareArtworkProcessor.transform(input)
}

@OptIn(markerClass = [UnstableApi::class])
class SquareArtworkBitmapLoader(context: Context) : BitmapLoader {
    private val delegate = DataSourceBitmapLoader.Builder(context)
        .setMaximumOutputDimension(1_024)
        .build()

    override fun supportsMimeType(mimeType: String): Boolean = delegate.supportsMimeType(mimeType)

    override fun decodeBitmap(data: ByteArray): ListenableFuture<Bitmap> = Futures.transform(
        delegate.decodeBitmap(data),
        SquareArtworkProcessor::transform,
        MoreExecutors.directExecutor(),
    )

    override fun loadBitmap(uri: Uri): ListenableFuture<Bitmap> {
        return Futures.transform(
            delegate.loadBitmap(uri),
            { source -> SquareArtworkProcessor.transform(requireNotNull(source)) },
            MoreExecutors.directExecutor(),
        )
    }
}
