package com.polentita.music.data.downloader

import com.polentita.music.core.database.DownloadEntity
import com.polentita.music.core.database.DownloadStatus

internal object DownloadStateTransitions {
    fun cancelled(download: DownloadEntity): DownloadEntity = download.copy(
        status = DownloadStatus.CANCELLED.name,
        speedBytesPerSecond = 0,
        errorMessage = "La descarga fue cancelada",
    )
}
