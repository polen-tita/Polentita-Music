package com.polentita.music.data.playlistimport

internal enum class PlaylistImportRecoveryAction {
    RETRY_CURRENT_CANDIDATE,
    TRY_NEXT_CANDIDATE,
    NEEDS_REVIEW,
}

internal object PlaylistImportRecoveryPolicy {
    const val MAX_AUTOMATIC_CYCLES_PER_CANDIDATE = 2

    fun decide(
        attemptCount: Int,
        hasNextCandidate: Boolean,
        providerUnavailable: Boolean,
        errorMessage: String?,
    ): PlaylistImportRecoveryAction = when {
        providerUnavailable -> PlaylistImportRecoveryAction.NEEDS_REVIEW
        attemptCount < MAX_AUTOMATIC_CYCLES_PER_CANDIDATE && !isPermanent(errorMessage) ->
            PlaylistImportRecoveryAction.RETRY_CURRENT_CANDIDATE
        hasNextCandidate -> PlaylistImportRecoveryAction.TRY_NEXT_CANDIDATE
        else -> PlaylistImportRecoveryAction.NEEDS_REVIEW
    }

    private fun isPermanent(message: String?): Boolean {
        val normalized = message.orEmpty().lowercase()
        return PERMANENT_FAILURE_MARKERS.any(normalized::contains)
    }

    private val PERMANENT_FAILURE_MARKERS = listOf(
        "copyright",
        "copyrighted",
        "drm",
        "private video",
        "video privado",
        "not available",
        "no disponible",
        "unsupported url",
        "url no compatible",
        "age-restricted",
        "restricción de edad",
        "sign in",
        "inicia sesión",
        "no video formats",
        "no hay formatos",
    )
}
