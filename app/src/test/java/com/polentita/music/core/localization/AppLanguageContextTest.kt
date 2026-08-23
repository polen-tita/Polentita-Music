package com.polentita.music.core.localization

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.polentita.music.R
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AppLanguageContextTest {
    @Test
    fun `el contexto resuelve recursos para cada idioma`() {
        val context = ApplicationProvider.getApplicationContext<Context>()

        assertEquals("Ajustes", context.withAppLanguage(AppLanguage.SPANISH).getString(R.string.settings_title))
        assertEquals("Settings", context.withAppLanguage(AppLanguage.ENGLISH).getString(R.string.settings_title))
        assertEquals("Configurações", context.withAppLanguage(AppLanguage.PORTUGUESE).getString(R.string.settings_title))
        assertEquals("Réglages", context.withAppLanguage(AppLanguage.FRENCH).getString(R.string.settings_title))
        assertEquals("设置", context.withAppLanguage(AppLanguage.CHINESE).getString(R.string.settings_title))
    }
}
