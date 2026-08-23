package com.polentita.music.data.downloader

import com.polentita.music.core.database.DownloadStatus

internal data class DownloadFailureDecision(
    val status: DownloadStatus,
    val shouldRetry: Boolean,
    val errorMessage: String?,
)

internal object DownloadFailurePolicy {
    private const val AUTOMATIC_RETRY_LIMIT = 2

    fun decide(
        runAttemptCount: Int,
        blocked: Boolean,
        cancelled: Boolean,
        errorMessage: String?,
    ): DownloadFailureDecision = when {
        blocked -> DownloadFailureDecision(
            status = DownloadStatus.PAUSED,
            shouldRetry = true,
            errorMessage = errorMessage,
        )
        cancelled -> DownloadFailureDecision(
            status = DownloadStatus.CANCELLED,
            shouldRetry = false,
            errorMessage = "La descarga fue cancelada",
        )
        runAttemptCount < AUTOMATIC_RETRY_LIMIT -> DownloadFailureDecision(
            // Mientras WorkManager todavía tenga un reintento pendiente, el
            // coordinador no debe tratar el elemento como un fallo definitivo.
            status = DownloadStatus.PENDING,
            shouldRetry = true,
            errorMessage = null,
        )
        else -> DownloadFailureDecision(
            status = DownloadStatus.FAILED,
            shouldRetry = false,
            errorMessage = userFacingDownloadError(errorMessage),
        )
    }
}

internal fun userFacingDownloadError(message: String?): String {
    val clean = message.orEmpty().trim()
    if (
        clean.contains("startForegroundService", ignoreCase = true) ||
        clean.contains("mAllowStartForeground", ignoreCase = true) ||
        clean.contains("SystemForegroundService", ignoreCase = true)
    ) {
        return "Android no pudo iniciar el servicio de descarga. Abre la aplicación y reintenta."
    }
    return clean.ifBlank { "No se pudo completar la descarga" }
}
