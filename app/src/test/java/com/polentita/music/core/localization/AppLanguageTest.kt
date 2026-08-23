package com.polentita.music.core.localization

import org.junit.Assert.assertEquals
import org.junit.Test

class AppLanguageTest {
    @Test
    fun `conoce las cinco variantes y usa español como respaldo`() {
        assertEquals(
            listOf("es-ES", "en-US", "pt-BR", "fr-FR", "zh-CN"),
            AppLanguage.entries.map(AppLanguage::tag),
        )
        assertEquals(AppLanguage.CHINESE, AppLanguage.fromTag("zh-CN"))
        assertEquals(AppLanguage.SPANISH, AppLanguage.fromTag("ja-JP"))
        assertEquals(AppLanguage.SPANISH, AppLanguage.fromTag(null))
    }
}
