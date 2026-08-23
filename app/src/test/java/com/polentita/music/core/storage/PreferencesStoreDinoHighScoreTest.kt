package com.polentita.music.core.storage

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class PreferencesStoreDinoHighScoreTest {
    @Test
    fun `el record del dino persiste y nunca disminuye`() = runTest {
        val store = PreferencesStore(ApplicationProvider.getApplicationContext<Context>())
        val current = store.current().dinoHighScore
        val candidate = minOf(Int.MAX_VALUE.toLong(), current.toLong() + 137L).toInt()

        store.recordDinoHighScore(candidate)
        assertEquals(candidate, store.current().dinoHighScore)

        store.recordDinoHighScore((candidate - 1).coerceAtLeast(0))
        assertEquals(candidate, store.current().dinoHighScore)
    }
}
