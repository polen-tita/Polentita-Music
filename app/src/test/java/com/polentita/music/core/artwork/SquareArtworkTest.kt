package com.polentita.music.core.artwork

import android.graphics.Bitmap
import android.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.LEGACY)
class SquareArtworkTest {
    @Test
    fun `portada cuadrada conserva el bitmap original`() {
        val source = Bitmap.createBitmap(320, 320, Bitmap.Config.ARGB_8888)

        assertFalse(SquareArtworkProcessor.requiresComposition(source.width, source.height))
        assertSame(source, SquareArtworkProcessor.transform(source))
    }

    @Test
    fun `portada horizontal se compone en un bitmap cuadrado sin alterar la fuente`() {
        val source = Bitmap.createBitmap(640, 360, Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.MAGENTA)
        }

        val square = SquareArtworkProcessor.transform(source)

        assertTrue(SquareArtworkProcessor.requiresComposition(source.width, source.height))
        assertEquals(square.width, square.height)
        assertEquals(640, source.width)
        assertEquals(360, source.height)
    }

    @Test
    fun `portada vertical tambien usa composicion cuadrada`() {
        val source = Bitmap.createBitmap(300, 600, Bitmap.Config.ARGB_8888)
        val square = SquareArtworkProcessor.transform(source)

        assertEquals(square.width, square.height)
    }
}
