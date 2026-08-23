package com.polentita.music.data.extractor

import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class YtDlpSourceResolverTest {
    @Test
    fun `uses the first alternative when direct metadata inspection fails`() = runTest {
        val extractor = mockk<YtDlpExtractor>()
        val directUrl = "https://www.youtube.com/watch?v=direct"
        val alternativeUrl = "https://www.youtube.com/watch?v=alternative"
        val media = media(alternativeUrl)
        coEvery { extractor.inspect(directUrl) } throws IllegalStateException("restricted")
        coEvery {
            extractor.search("Tema Artista", page = 0, pageSize = 10)
        } returns YtDlpSearchPage(
            items = listOf(
                result(directUrl),
                result(alternativeUrl),
            ),
            page = 0,
            hasMore = false,
        )
        coEvery { extractor.inspect(alternativeUrl) } returns media

        val resolved = YtDlpSourceResolver(extractor).resolve(
            title = "Tema",
            artist = "Artista",
            sourceUrl = directUrl,
            resolver = extractor::inspect,
        )

        assertEquals(alternativeUrl, resolved.sourceUrl)
        assertEquals(media, resolved.value)
    }

    @Test
    fun `still tries alternatives after a temporary YouTube block`() = runTest {
        val extractor = mockk<YtDlpExtractor>()
        val directUrl = "https://www.youtube.com/watch?v=direct"
        val alternativeUrl = "https://www.youtube.com/watch?v=alternative"
        val media = media(alternativeUrl)
        coEvery { extractor.inspect(directUrl) } throws
            IllegalStateException("YouTube rechazó temporalmente la solicitud desde esta red")
        coEvery {
            extractor.search("Tema Artista", page = 0, pageSize = 10)
        } returns YtDlpSearchPage(
            items = listOf(result(directUrl), result(alternativeUrl)),
            page = 0,
            hasMore = false,
        )
        coEvery { extractor.inspect(alternativeUrl) } returns media

        val resolved = YtDlpSourceResolver(extractor).resolve(
            title = "Tema",
            artist = "Artista",
            sourceUrl = directUrl,
            resolver = extractor::inspect,
        )

        assertEquals(alternativeUrl, resolved.sourceUrl)
        assertEquals(media, resolved.value)
    }

    private fun result(url: String) = YtDlpSearchResult(
        id = url.substringAfter("v="),
        title = "Tema",
        channel = "Artista",
        durationMs = 180_000,
        thumbnailUrl = null,
        webpageUrl = url,
        uploadDate = null,
    )

    private fun media(url: String) = YtDlpMediaInfo(
        id = url.substringAfter("v="),
        title = "Tema",
        artist = "Artista",
        album = "",
        durationMs = 180_000,
        thumbnailUrl = "https://i.ytimg.com/vi/alternative/hqdefault.jpg",
        webpageUrl = url,
        extractor = "youtube",
        extension = "webm",
        sizeBytes = 1_000,
    )
}
