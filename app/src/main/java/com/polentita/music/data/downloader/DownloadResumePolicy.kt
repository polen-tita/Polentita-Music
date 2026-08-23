package com.polentita.music.data.downloader

data class ResumeDecision(val append: Boolean, val startingBytes: Long)

object DownloadResumePolicy {
    fun decide(existingBytes: Long, responseCode: Int): ResumeDecision =
        if (existingBytes > 0 && responseCode == 206) {
            ResumeDecision(append = true, startingBytes = existingBytes)
        } else {
            ResumeDecision(append = false, startingBytes = 0)
        }
}
