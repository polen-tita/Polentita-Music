package com.polentita.music

import android.graphics.drawable.AdaptiveIconDrawable
import android.util.TypedValue
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [31])
class SplashIconResourceTest {
    @Test
    fun `compose splash uses raster icon instead of adaptive icon xml`() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val value = TypedValue()

        context.resources.getValue(splashIconResourceId, value, true)
        val drawable = context.getDrawable(splashIconResourceId)

        assertTrue(value.string?.toString()?.endsWith(".png") == true)
        assertNotNull(drawable)
        assertFalse(drawable is AdaptiveIconDrawable)
    }
}
