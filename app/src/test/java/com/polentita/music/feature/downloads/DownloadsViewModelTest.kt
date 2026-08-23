package com.polentita.music.feature.downloads

import app.cash.turbine.test
import com.polentita.music.MainDispatcherRule
import com.polentita.music.core.database.AlbumEntity
import com.polentita.music.core.network.NetworkAccessPolicy
import com.polentita.music.core.network.NetworkAccessState
import com.polentita.music.core.network.NetworkBlockReason
import com.polentita.music.data.downloader.DownloadCoordinator
import com.polentita.music.data.extractor.YtDlpExtractor
import com.polentita.music.data.extractor.YtDlpPreviewInfo
import com.polentita.music.data.extractor.YtDlpSearchPage
import com.polentita.music.data.extractor.YtDlpSearchResult
import com.polentita.music.domain.repository.MusicRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DownloadsViewModelTest {
    @get:Rule val main = MainDispatcherRule()

    @Test
    fun `youtube search waits for submit debounce paginates and selects result url`() = runTest {
        val coordinator = mockk<DownloadCoordinator>(relaxed = true)
        every { coordinator.downloads } returns flowOf(emptyList())
        val extractor = mockk<YtDlpExtractor>()
        val first = result("uno", "https://www.youtube.com/watch?v=uno")
        val second = result("dos", "https://www.youtube.com/watch?v=dos")
        coEvery { extractor.search("Creepers vs zombies", 0, 10) } returns
            YtDlpSearchPage(listOf(first), page = 0, hasMore = true)
        coEvery { extractor.search("Creepers vs zombies", 1, 10) } returns
            YtDlpSearchPage(listOf(second), page = 1, hasMore = false)
        val repository = mockk<MusicRepository>(relaxed = true)
        every { repository.observeAlbums() } returns flowOf(emptyList())
        every { repository.observeArtists() } returns flowOf(emptyList())
        val viewModel = DownloadsViewModel(
            coordinator = coordinator,
            repository = repository,
            ytDlpExtractor = extractor,
        )

        viewModel.state.test {
            awaitItem()
            viewModel.setRemoteSearchQuery("Creepers vs zombies")
            viewModel.submitRemoteSearch()
            coVerify(exactly = 0) { extractor.search(any(), any(), any()) }

            advanceTimeBy(DownloadsViewModel.SEARCH_DEBOUNCE_MS + 1)
            runCurrent()
            var state = awaitItem()
            while (state.remoteSearchResults.isEmpty()) state = awaitItem()
            assertEquals(listOf(first), state.remoteSearchResults)
            assertEquals(first.webpageUrl, viewModel.useSearchResult(first))

            viewModel.loadMoreRemoteResults()
            runCurrent()
            state = awaitItem()
            while (state.remoteSearchResults.size < 2) state = awaitItem()
            assertEquals(listOf(first, second), state.remoteSearchResults)
            assertFalse(state.remoteSearchHasMore)
            coVerify(exactly = 1) { extractor.search("Creepers vs zombies", 0, 10) }
            coVerify(exactly = 1) { extractor.search("Creepers vs zombies", 1, 10) }
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `youtube search response with ten videos exposes all ten in ui state`() = runTest {
        val coordinator = mockk<DownloadCoordinator>(relaxed = true)
        every { coordinator.downloads } returns flowOf(emptyList())
        val extractor = mockk<YtDlpExtractor>()
        val results = (1..10).map { index ->
            result(
                id = "video-$index",
                url = "https://www.youtube.com/watch?v=video-$index",
            )
        }
        coEvery { extractor.search("diez resultados", 0, 10) } returns
            YtDlpSearchPage(results, page = 0, hasMore = true)
        val repository = mockk<MusicRepository>(relaxed = true)
        every { repository.observeAlbums() } returns flowOf(
            listOf(AlbumEntity(id = 4, name = "Audios de TikTok")),
        )
        every { repository.observeArtists() } returns flowOf(listOf("Artista guardado"))
        val viewModel = DownloadsViewModel(
            coordinator = coordinator,
            repository = repository,
            ytDlpExtractor = extractor,
        )

        viewModel.state.test {
            awaitItem()
            viewModel.setRemoteSearchQuery("diez resultados")
            viewModel.submitRemoteSearch()
            advanceTimeBy(DownloadsViewModel.SEARCH_DEBOUNCE_MS + 1)
            runCurrent()

            var state = awaitItem()
            while (state.remoteSearchResults.size < 10) state = awaitItem()

            assertEquals(10, state.remoteSearchResults.size)
            assertEquals("Audios de TikTok", state.albums.single().name)
            assertEquals(listOf("Artista guardado"), state.artists)
            assertEquals(results.map(YtDlpSearchResult::id), state.remoteSearchResults.map(YtDlpSearchResult::id))
            coVerify(exactly = 1) { extractor.search("diez resultados", 0, 10) }
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `youtube result preview resolves through yt dlp`() = runTest {
        val coordinator = mockk<DownloadCoordinator>(relaxed = true)
        every { coordinator.downloads } returns flowOf(emptyList())
        val extractor = mockk<YtDlpExtractor>(relaxed = true)
        val url = "https://www.youtube.com/watch?v=preview"
        val preview = YtDlpPreviewInfo(
            id = "preview",
            title = "Adelanto",
            artist = "Canal",
            durationMs = 30_000,
            thumbnailUrl = "https://i.example/preview.jpg",
            webpageUrl = url,
            streamUrl = "https://stream.example/preview.webm",
        )
        coEvery { extractor.resolvePreview(url) } returns preview
        val repository = mockk<MusicRepository>(relaxed = true)
        every { repository.observeAlbums() } returns flowOf(emptyList())
        every { repository.observeArtists() } returns flowOf(emptyList())
        val viewModel = DownloadsViewModel(coordinator, repository, extractor)

        viewModel.state.test {
            awaitItem()
            viewModel.preview(url)
            var state = awaitItem()
            while (state.preview.status != DownloadPreviewStatus.READY) state = awaitItem()
            assertEquals(preview, state.preview.preview)
            coVerify { extractor.resolvePreview(url) }
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `modo offline bloquea busqueda inspeccion y adelanto antes del extractor`() = runTest {
        val coordinator = mockk<DownloadCoordinator>(relaxed = true)
        every { coordinator.downloads } returns flowOf(emptyList())
        val extractor = mockk<YtDlpExtractor>(relaxed = true)
        val repository = mockk<MusicRepository>(relaxed = true)
        every { repository.observeAlbums() } returns flowOf(emptyList())
        every { repository.observeArtists() } returns flowOf(emptyList())
        val offline = NetworkAccessState(
            offlineMode = true,
            connected = true,
            wifi = true,
            metered = false,
            remoteBlockReason = NetworkBlockReason.OFFLINE_MODE,
            downloadBlockReason = NetworkBlockReason.OFFLINE_MODE,
        )
        val policy = testPolicy(offline)
        val viewModel = DownloadsViewModel(coordinator, repository, extractor, policy)

        viewModel.state.test {
            awaitItem()
            viewModel.setRemoteSearchQuery("cancion")
            viewModel.submitRemoteSearch()
            viewModel.preview("https://www.youtube.com/watch?v=test")
            viewModel.inspectWithYtDlp("https://www.youtube.com/watch?v=test")
            advanceTimeBy(DownloadsViewModel.SEARCH_DEBOUNCE_MS + 1)
            runCurrent()
            var item = awaitItem()
            while (!item.networkAccess.offlineMode) item = awaitItem()

            coVerify(exactly = 0) { extractor.search(any(), any(), any()) }
            coVerify(exactly = 0) { extractor.resolvePreview(any()) }
            coVerify(exactly = 0) { extractor.inspect(any()) }
            cancelAndIgnoreRemainingEvents()
        }
    }

    private fun result(id: String, url: String) = YtDlpSearchResult(
        id = id,
        title = "Resultado $id",
        channel = "Canal",
        durationMs = 60_000,
        thumbnailUrl = "https://i.example/$id.jpg",
        webpageUrl = url,
        uploadDate = "20260724",
    )
}

private fun testPolicy(initial: NetworkAccessState): NetworkAccessPolicy =
    object : NetworkAccessPolicy {
        private val mutable = MutableStateFlow(initial)
        override val state: StateFlow<NetworkAccessState> = mutable
        override suspend fun current(): NetworkAccessState = mutable.value
    }
