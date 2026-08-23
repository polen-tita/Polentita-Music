package com.polentita.music.data.extractor

import com.polentita.music.core.common.RemoteUrlValidator
import com.polentita.music.core.common.UrlValidation
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class YtDlpSecurityTest {
    @Test
    fun `extractor error removes complete remote urls and limits output`() {
        val error = "ERROR: no se pudo abrir https://example.com/watch?v=token-secreto\nfalló el extractor"

        val sanitized = sanitizeYtDlpError(error)

        assertFalse(sanitized.contains("token-secreto"))
        assertFalse(sanitized.contains("https://"))
        assertTrue(sanitized.length <= 240)
    }

    @Test
    fun `empty extractor error has a safe spanish fallback`() {
        assertTrue(sanitizeYtDlpError(null).contains("yt-dlp"))
    }

    @Test
    fun `tiktok short HTTPS link is accepted for yt-dlp inspection`() {
        assertTrue(
            RemoteUrlValidator.validate("https://vt.tiktok.com/ZSXvXSPRc/") is UrlValidation.Valid,
        )
    }

    @Test
    fun `youtube bot challenge becomes a short safe recovery message`() {
        val raw = "DownloadError: ERROR: [youtube] abc: Sign in to confirm you're not a bot. " +
            "Use --cookies-from-browser or --cookies. See https://example.invalid/token"

        val sanitized = sanitizeYtDlpError(raw)

        assertTrue(sanitized.contains("verificación anti-bot"))
        assertFalse(sanitized.contains("--cookies"))
        assertFalse(sanitized.contains("https://"))
    }

    @Test
    fun `youtube player block becomes a short safe recovery message`() {
        val sanitized = sanitizeYtDlpError(
            "ERROR: [youtube] All player responses are invalid. Your IP is likely being blocked by Youtube",
        )

        assertTrue(sanitized.contains("rechazó temporalmente"))
    }

    @Test
    fun `youtube reload error becomes a short safe recovery message`() {
        val sanitized = sanitizeYtDlpError(
            "DownloadError: ERROR: [youtube] abc: The page needs to be reloaded",
        )

        assertTrue(sanitized.contains("verificación anti-bot"))
    }

    @Test
    fun `youtube drm error becomes a short user-facing message`() {
        val sanitized = sanitizeYtDlpError(
            "DownloadError: ERROR: [youtube] abc: This video is DRM protected",
        )

        assertTrue(sanitized.contains("formato de audio descargable"))
        assertFalse(sanitized.contains("DRM"))
    }

    @Test
    fun `youtube administrator restriction becomes a safe user-facing message`() {
        val sanitized = sanitizeYtDlpError(
            "DownloadError: ERROR: [youtube] abc: Video unavailable. This video is restricted. " +
                "Please check the Google Workspace administrator and/or the network administrator restrictions.",
        )

        assertTrue(sanitized.contains("restringió este contenido"))
        assertFalse(sanitized.contains("Workspace"))
    }
}
