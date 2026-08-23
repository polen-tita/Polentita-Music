package com.polentita.music.playback.session

import android.os.Bundle
import java.util.concurrent.ConcurrentHashMap

internal const val PREVIEW_HTTP_HEADERS_KEY = "com.polentita.music.preview.httpHeaders"
internal const val PREVIEW_MEDIA_ID_PREFIX = "polentita-preview:"
internal const val DEFAULT_PREVIEW_USER_AGENT =
    "com.google.android.apps.youtube.vr.oculus/1.65.10 " +
        "(Linux; U; Android 12L; eureka-user Build/SQ3A.220605.009.A1) gzip"

private val allowedPreviewHeaderNames = setOf(
    "user-agent",
    "referer",
    "origin",
    "accept",
    "accept-language",
)

internal fun previewHttpHeadersExtras(headers: Map<String, String>): Bundle? {
    val safeHeaders = headers
        .filter { (name, value) ->
            name.lowercase() in allowedPreviewHeaderNames &&
                value.isNotBlank() &&
                value.length <= 512
        }
    if (safeHeaders.isEmpty()) return null
    return Bundle().apply {
        putBundle(
            PREVIEW_HTTP_HEADERS_KEY,
            Bundle().apply {
                safeHeaders.forEach { (name, value) -> putString(name, value) }
            },
        )
    }
}

internal fun Bundle.previewHttpHeaders(): Map<String, String> = keySet()
    .mapNotNull { name ->
        val value = getString(name)?.takeIf(String::isNotBlank) ?: return@mapNotNull null
        if (name.lowercase() !in allowedPreviewHeaderNames) return@mapNotNull null
        name to value
    }
    .toMap()

/**
 * MediaItem metadata is serialized when a MediaController talks to the
 * MediaSession. Keep a process-local copy as well so short-lived signed URLs
 * retain their request headers even when a device drops custom Bundle extras.
 */
internal object PreviewMediaHeadersRegistry {
    private val values = ConcurrentHashMap<String, Map<String, String>>()

    fun put(mediaId: String, headers: Map<String, String>) {
        val safeHeaders = headers
            .filter { (name, value) ->
                name.lowercase() in allowedPreviewHeaderNames &&
                    value.isNotBlank() &&
                    value.length <= 512
            }
        if (safeHeaders.isEmpty()) {
            values.remove(mediaId)
        } else {
            values[mediaId] = safeHeaders
        }
    }

    fun get(mediaId: String): Map<String, String> = values[mediaId].orEmpty()

    fun clear() {
        values.clear()
    }
}
