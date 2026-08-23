package com.polentita.music.playback.session

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [31])
class PreviewMediaHeadersTest {
    @Test
    fun `preview extras keep safe request headers and reject credentials`() {
        val extras = previewHttpHeadersExtras(
            mapOf(
                "User-Agent" to "preview-agent",
                "Referer" to "https://www.youtube.com/",
                "Accept-Language" to "en-US",
                "Cookie" to "SID=private",
                "Authorization" to "Bearer private",
            ),
        )

        val nested = requireNotNull(extras).getBundle(PREVIEW_HTTP_HEADERS_KEY)
        val headers = requireNotNull(nested).previewHttpHeaders()

        assertEquals(
            mapOf(
                "User-Agent" to "preview-agent",
                "Referer" to "https://www.youtube.com/",
                "Accept-Language" to "en-US",
            ),
            headers,
        )
        assertFalse(nested.containsKey("Cookie"))
        assertFalse(nested.containsKey("Authorization"))
    }

    @Test
    fun `empty or oversized headers do not create extras`() {
        assertNull(previewHttpHeadersExtras(mapOf("Cookie" to "private")))
        assertNull(previewHttpHeadersExtras(mapOf("User-Agent" to "x".repeat(513))))
        assertTrue(previewHttpHeadersExtras(mapOf("Accept" to "*/*")) != null)
    }
}
