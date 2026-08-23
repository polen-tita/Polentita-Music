package com.polentita.music.playback.session

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import com.polentita.music.MainActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class PlaybackSessionActivityTest {
    @Test
    fun `media session pending intent is immutable and targets single activity`() {
        val context = ApplicationProvider.getApplicationContext<Context>()

        val pendingIntent = PlaybackSessionActivity.pendingIntent(context)
        val savedIntent = shadowOf(pendingIntent).savedIntent

        assertTrue(pendingIntent.isImmutable)
        assertNotNull(savedIntent)
        assertEquals(MainActivity::class.java.name, savedIntent.component?.className)
        assertEquals(PlaybackSessionActivity.ACTION_OPEN_PLAYER, savedIntent.action)
        assertTrue(savedIntent.flags and Intent.FLAG_ACTIVITY_CLEAR_TOP != 0)
        assertTrue(savedIntent.flags and Intent.FLAG_ACTIVITY_SINGLE_TOP != 0)
        assertEquals(
            PendingIntent.getActivity(
                context,
                901,
                PlaybackSessionActivity.intent(context),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            ),
            pendingIntent,
        )
    }
}
