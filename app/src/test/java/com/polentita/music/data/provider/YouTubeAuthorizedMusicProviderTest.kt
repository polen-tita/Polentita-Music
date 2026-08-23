package com.polentita.music.data.provider

import com.polentita.music.data.extractor.YtDlpExtractor
import com.polentita.music.data.extractor.YtDlpSearchPage
import com.polentita.music.data.extractor.YtDlpSearchResult
import com.polentita.music.domain.provider.AuthorizedDownloadSource
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class YouTubeAuthorizedMusicProviderTest {
    private val extractor = mockk<YtDlpExtractor>()

    @Test
    fun `maps yt dlp search metadata and prepares authorized download`() = runTest {
        coEvery {
            extractor.search("metal", 0, YouTubeAuthorizedMusicProvider.RESULTS_PER_PAGE)
        } returns YtDlpSearchPage(
            items = listOf(
                result(
                    id = "video-1",
                    title = "Metal & Música",
                    channel = "Canal Académico",
                    durationMs = 93_000,
                    thumbnailUrl = "https://img.example/high.jpg",
                ),
            ),
            page = 0,
            hasMore = false,
        )
        val provider = YouTubeAuthorizedMusicProvider(extractor)

        val track = provider.search("metal").getOrThrow().single()

        assertEquals("Metal & Música", track.title)
        assertEquals("Canal Académico", track.artist.name)
        assertEquals(93_000, track.durationMs)
        assertEquals("https://img.example/high.jpg", track.coverUri)
        assertEquals("https://www.youtube.com/watch?v=video-1", track.externalUrl)
        assertTrue(track.allowsDownload)
        assertEquals("Licencia no informada", track.license.name)

        val download = provider.resolveDownload(track).getOrThrow()
        assertTrue(download.source is AuthorizedDownloadSource.YtDlp)
        assertEquals(track.externalUrl, (download.source as AuthorizedDownloadSource.YtDlp).sourceUrl)
    }

    @Test
    fun `is always configured without an api key`() = runTest {
        val provider = YouTubeAuthorizedMusicProvider(extractor)

        assertTrue(provider.isConfigured)
        assertEquals(null, provider.configurationMessage)
    }

    @Test
    fun `returns the next numeric page token for more yt dlp results`() = runTest {
        coEvery {
            extractor.search("metal", 2, YouTubeAuthorizedMusicProvider.RESULTS_PER_PAGE)
        } returns YtDlpSearchPage(
            items = listOf(result(id = "video-3", title = "Otra canción")),
            page = 2,
            hasMore = true,
        )
        val provider = YouTubeAuthorizedMusicProvider(extractor)

        val page = provider.searchPage("metal", "2").getOrThrow()

        assertEquals("3", page.nextPageToken)
        assertEquals(listOf("video-3"), page.tracks.map { it.id })
        coVerify(exactly = 1) {
            extractor.search("metal", 2, YouTubeAuthorizedMusicProvider.RESULTS_PER_PAGE)
        }
    }

    @Test
    fun `does not invoke yt dlp for a blank query`() = runTest {
        val provider = YouTubeAuthorizedMusicProvider(extractor)

        assertTrue(provider.search("   ").getOrThrow().isEmpty())

        coVerify(exactly = 0) { extractor.search(any(), any(), any()) }
    }

    private fun result(
        id: String,
        title: String,
        channel: String = "Canal",
        durationMs: Long = 120_000,
        thumbnailUrl: String? = null,
    ) = YtDlpSearchResult(
        id = id,
        title = title,
        channel = channel,
        durationMs = durationMs,
        thumbnailUrl = thumbnailUrl,
        webpageUrl = "https://www.youtube.com/watch?v=$id",
        uploadDate = null,
    )
}
