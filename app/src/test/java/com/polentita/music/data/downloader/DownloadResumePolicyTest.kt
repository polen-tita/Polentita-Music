package com.polentita.music.data.downloader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DownloadResumePolicyTest {
    @Test
    fun `interrupted download resumes only on partial content`() {
        val resumed = DownloadResumePolicy.decide(existingBytes = 4096, responseCode = 206)
        assertTrue(resumed.append)
        assertEquals(4096, resumed.startingBytes)
    }

    @Test
    fun `server without range restarts safely`() {
        val restarted = DownloadResumePolicy.decide(existingBytes = 4096, responseCode = 200)
        assertFalse(restarted.append)
        assertEquals(0, restarted.startingBytes)
    }
}
