package com.polentita.music.core.launcher

import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class LauncherIconManagerTest {
    @Test
    fun sampleSize_keepsDecodeNearTargetWithoutDroppingBelowIt() {
        assertEquals(1, calculateLauncherIconSampleSize(900, 900, 1_024))
        assertEquals(2, calculateLauncherIconSampleSize(4_096, 2_048, 1_024))
        assertEquals(4, calculateLauncherIconSampleSize(8_192, 4_096, 1_024))
    }

    @Test
    fun cropRect_centersWideAndTallImages() {
        assertEquals(
            android.graphics.Rect(500, 0, 1_500, 1_000),
            launcherIconCropRect(2_000, 1_000),
        )
        assertEquals(
            android.graphics.Rect(0, 500, 1_000, 1_500),
            launcherIconCropRect(1_000, 2_000),
        )
        assertEquals(
            android.graphics.Rect(0, 0, 1_000, 1_000),
            launcherIconCropRect(1_000, 1_000),
        )
    }
}
