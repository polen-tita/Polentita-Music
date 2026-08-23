package com.polentita.music.core.network

import com.polentita.music.core.common.RemoteUrlValidator
import com.polentita.music.core.common.UrlValidation
import org.junit.Assert.assertTrue
import org.junit.Test

class DirectDownloadValidationTest {
    @Test
    fun `http url is rejected`() {
        assertTrue(RemoteUrlValidator.validate("http://audio.example/track.mp3") is UrlValidation.Invalid)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `html response is rejected even with audio extension`() {
        requireCompatibleRemoteAudio("text/html", "track.mp3")
    }

    @Test(expected = IllegalArgumentException::class)
    fun `unsupported mime is rejected`() {
        requireCompatibleRemoteAudio("application/vnd.android.package-archive", "track.apk")
    }

    @Test
    fun `supported audio mime is accepted`() {
        requireCompatibleRemoteAudio("audio/mpeg", "track.mp3")
    }
}
