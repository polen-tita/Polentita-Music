package com.polentita.music.data.downloader

import org.junit.Assert.assertEquals
import org.junit.Test

class DownloadedAudioPreparerTest {
    @Test
    fun `combined TikTok style video is converted to an audio-only file`() {
        assertEquals(
            DownloadedMediaPreparation.EXTRACT_AUDIO_TRACK,
            downloadedMediaPreparation(DownloadedMediaTracks(hasAudio = true, hasVideo = true)),
        )
    }

    @Test
    fun `audio-only download is preserved and silent video is rejected`() {
        assertEquals(
            DownloadedMediaPreparation.USE_AUDIO_FILE,
            downloadedMediaPreparation(DownloadedMediaTracks(hasAudio = true, hasVideo = false)),
        )
        assertEquals(
            DownloadedMediaPreparation.REJECT,
            downloadedMediaPreparation(DownloadedMediaTracks(hasAudio = false, hasVideo = true)),
        )
    }
}
