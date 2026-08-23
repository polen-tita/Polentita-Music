package com.polentita.music.core.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FileSafetyTest {
    @Test
    fun `sanitizer removes traversal and reserved characters`() {
        val result = FileNameSanitizer.sanitize("../../mi:canción?.mp3")
        assertEquals("mi_canción_.mp3", result)
        assertFalse(result.contains(".."))
        assertFalse(result.contains('/'))
    }

    @Test
    fun `sanitizer supplies safe fallback`() {
        assertEquals("audio", FileNameSanitizer.sanitize("..."))
    }

    @Test
    fun `validator accepts only well formed https without credentials`() {
        assertTrue(RemoteUrlValidator.validate("https://audio.example/song.mp3") is UrlValidation.Valid)
        assertTrue(RemoteUrlValidator.validate("http://audio.example/song.mp3") is UrlValidation.Invalid)
        assertTrue(RemoteUrlValidator.validate("file:///tmp/song.mp3") is UrlValidation.Invalid)
        assertTrue(RemoteUrlValidator.validate("https://user:secret@example.com/song.mp3") is UrlValidation.Invalid)
        assertTrue(RemoteUrlValidator.validate("javascript:alert(1)") is UrlValidation.Invalid)
        assertTrue(RemoteUrlValidator.validate("https://127.0.0.1/song.mp3") is UrlValidation.Invalid)
        assertTrue(RemoteUrlValidator.validate("https://192.168.1.2/song.mp3") is UrlValidation.Invalid)
    }

    @Test
    fun `audio format recognizes required formats`() {
        listOf("mp3", "m4a", "aac", "ogg", "opus", "flac", "wav").forEach {
            assertTrue(AudioFormats.isSupported(null, "track.$it"))
        }
        assertFalse(AudioFormats.isSupported("text/html", "index.html"))
        assertFalse(AudioFormats.isSupported("text/html", "song.mp3"))
        assertFalse(AudioFormats.isSupported("application/vnd.android.package-archive", "song.mp3"))
    }
}
