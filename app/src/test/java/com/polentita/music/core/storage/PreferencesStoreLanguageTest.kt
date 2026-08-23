package com.polentita.music.core.storage

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.polentita.music.core.localization.AppLanguage
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class PreferencesStoreLanguageTest {
    @Test
    fun `persiste el idioma seleccionado`() = runTest {
        val store = PreferencesStore(ApplicationProvider.getApplicationContext<Context>())
        store.setLanguage(AppLanguage.CHINESE)

        assertEquals(AppLanguage.CHINESE, store.current().language)

        store.setLanguage(AppLanguage.SPANISH)
    }
}
