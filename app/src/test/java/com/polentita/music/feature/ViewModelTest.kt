package com.polentita.music.feature

import android.content.Context
import app.cash.turbine.test
import androidx.lifecycle.SavedStateHandle
import com.polentita.music.MainDispatcherRule
import com.polentita.music.R
import com.polentita.music.core.database.AlbumEntity
import com.polentita.music.core.database.PlaylistSummary
import com.polentita.music.core.database.RemoteReferenceEntity
import com.polentita.music.core.database.SourceType
import com.polentita.music.core.network.NetworkAccessPolicy
import com.polentita.music.core.network.NetworkAccessState
import com.polentita.music.core.network.NetworkBlockReason
import com.polentita.music.core.storage.DeviceMusicScanner
import com.polentita.music.core.storage.LibraryStorage
import com.polentita.music.core.storage.AppPreferences
import com.polentita.music.core.storage.PreferencesStore
import com.polentita.music.data.downloader.DownloadCoordinator
import com.polentita.music.data.extractor.YtDlpExtractor
import com.polentita.music.data.extractor.YtDlpMediaInfo
import com.polentita.music.data.extractor.YtDlpPreviewInfo
import com.polentita.music.data.provider.AuthorizedProviderRegistry
import com.polentita.music.data.repository.RemoteReferenceRepository
import com.polentita.music.domain.model.Song
import com.polentita.music.domain.artwork.ArtworkEditingRepository
import com.polentita.music.domain.artwork.ArtworkChoice
import com.polentita.music.domain.model.SongFilter
import com.polentita.music.domain.model.SongSort
import com.polentita.music.domain.provider.AuthorizedDownload
import com.polentita.music.domain.provider.AuthorizedDownloadSource
import com.polentita.music.domain.provider.AuthorizedMusicProvider
import com.polentita.music.domain.provider.PaginatedAuthorizedMusicProvider
import com.polentita.music.domain.provider.RemoteAlbum
import com.polentita.music.domain.provider.RemoteArtist
import com.polentita.music.domain.provider.RemoteSearchPage
import com.polentita.music.domain.provider.RemoteTrack
import com.polentita.music.domain.provider.ProviderLicense
import com.polentita.music.domain.repository.MusicRepository
import com.polentita.music.feature.library.LibraryViewModel
import com.polentita.music.feature.editor.SongEditorViewModel
import com.polentita.music.feature.player.PlayerViewModel
import com.polentita.music.feature.search.ExploreStatus
import com.polentita.music.feature.search.SearchViewModel
import com.polentita.music.feature.search.SearchTab
import com.polentita.music.playback.session.PlaybackController
import com.polentita.music.playback.session.PlaybackUiState
import io.mockk.coVerify
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ViewModelTest {
    @get:Rule val main = MainDispatcherRule()

    @Test
    fun `library view model exposes repository songs and toggles favorite`() = runTest {
        val repository = repositoryMock()
        val songs = listOf(song(1, "Local"))
        every { repository.observeSongs() } returns flowOf(songs)
        every { repository.search("", SongFilter(), SongSort.TITLE, true) } returns flowOf(songs)
        val preferences = mockk<PreferencesStore>(relaxed = true) {
            coEvery { current() } returns AppPreferences()
        }
        val viewModel = LibraryViewModel(
            repository,
            mockk<LibraryStorage>(relaxed = true),
            mockk<DeviceMusicScanner>(relaxed = true),
            preferences,
            mockk<ArtworkEditingRepository>(relaxed = true),
        )

        viewModel.uiState.test {
            var item = awaitItem()
            while (item.loading) item = awaitItem()
            assertEquals(songs, item.songs)
            viewModel.toggleFavorite(1)
            coVerify { repository.toggleFavorite(1) }
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `library view model restores and persists grid mode`() = runTest {
        val repository = repositoryMock()
        val preferences = mockk<PreferencesStore>(relaxed = true) {
            coEvery { current() } returns AppPreferences(libraryGridMode = true)
        }
        val viewModel = LibraryViewModel(
            repository,
            mockk<LibraryStorage>(relaxed = true),
            mockk<DeviceMusicScanner>(relaxed = true),
            preferences,
            mockk<ArtworkEditingRepository>(relaxed = true),
        )

        viewModel.uiState.test {
            var item = awaitItem()
            while (item.loading || !item.gridMode) item = awaitItem()
            viewModel.toggleGridMode()
            runCurrent()
            item = awaitItem()
            while (item.gridMode) item = awaitItem()
            coVerify { preferences.setLibraryGridMode(false) }
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `song editor exposes existing artists and albums for selectors`() = runTest {
        val repository = repositoryMock()
        val editedSong = song(7, "Shadows")
        val album = AlbumEntity(id = 4, name = "Dark Beach EP", artist = "Pastel Ghost")
        every { repository.observeSong(7) } returns flowOf(editedSong)
        every { repository.observeAlbums() } returns flowOf(listOf(album))
        every { repository.observeArtists() } returns flowOf(listOf("Pastel Ghost", "Arcane S2"))

        val viewModel = SongEditorViewModel(
            SavedStateHandle(mapOf("songId" to "7")),
            repository,
            mockk<ArtworkEditingRepository>(relaxed = true),
        )

        viewModel.state.test {
            var item = awaitItem()
            while (item.loading) item = awaitItem()
            assertEquals(listOf("Pastel Ghost", "Arcane S2"), item.artists)
            assertEquals(listOf(album), item.albums)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `song editor waits for transactional artwork save before reporting success`() = runTest {
        val repository = repositoryMock()
        val editedSong = song(7, "Shadows")
        every { repository.observeSong(7) } returns flowOf(editedSong)
        val artworkRepository = mockk<ArtworkEditingRepository>(relaxed = true)
        val viewModel = SongEditorViewModel(
            SavedStateHandle(mapOf("songId" to "7")),
            repository,
            artworkRepository,
        )

        viewModel.state.test {
            var item = awaitItem()
            while (item.loading) item = awaitItem()
            viewModel.save(editedSong.copy(title = "Shadows editada"), ArtworkChoice.Remove)
            runCurrent()
            item = awaitItem()
            while (!item.saved) item = awaitItem()
            assertFalse(item.saving)
            coVerify {
                artworkRepository.saveSong(
                    match { it.title == "Shadows editada" },
                    ArtworkChoice.Remove,
                )
            }
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `library closes album editor only after artwork save succeeds`() = runTest {
        val repository = repositoryMock()
        val preferences = mockk<PreferencesStore>(relaxed = true) {
            coEvery { current() } returns AppPreferences()
        }
        val artworkRepository = mockk<ArtworkEditingRepository>(relaxed = true)
        val viewModel = LibraryViewModel(
            repository,
            mockk<LibraryStorage>(relaxed = true),
            mockk<DeviceMusicScanner>(relaxed = true),
            preferences,
            artworkRepository,
        )
        val album = AlbumEntity(id = 8, name = "AM", artist = "Arctic Monkeys")
        var closed = false

        viewModel.updateAlbum(album, ArtworkChoice.Remove) { closed = true }
        runCurrent()

        coVerify { artworkRepository.saveAlbum(album, ArtworkChoice.Remove) }
        assertTrue(closed)
    }

    @Test
    fun `search view model keeps text immediate then debounces trimmed repository query`() = runTest {
        val repository = repositoryMock()
        every { repository.search(any(), any(), any(), any()) } returns flowOf(listOf(song(2, "Resultado")))
        val preferences = mockk<PreferencesStore>(relaxed = true) {
            coEvery { current() } returns AppPreferences()
        }
        val viewModel = SearchViewModel(
            repository,
            mockk<AuthorizedProviderRegistry>(relaxed = true),
            mockk<DownloadCoordinator>(relaxed = true),
            mockk<YtDlpExtractor>(relaxed = true),
            mockk<RemoteReferenceRepository> {
                every { observeAll() } returns flowOf(emptyList())
            },
            preferences,
            searchContext(),
        )

        viewModel.uiState.test {
            awaitItem()
            viewModel.setQuery("  res  ")
            var item = awaitItem()
            while (item.query != "  res  ") item = awaitItem()
            assertEquals("  res  ", item.query)

            advanceTimeBy(SearchViewModel.SEARCH_DEBOUNCE_MS + 1)
            runCurrent()
            item = awaitItem()
            while (item.loading) item = awaitItem()
            assertEquals("Resultado", item.results.single().title)
            verify { repository.search("res", SongFilter(), SongSort.TITLE, true) }
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `search explore no consulta proveedor cuando modo offline esta activo`() = runTest {
        val repository = repositoryMock()
        every { repository.search(any(), any(), any(), any()) } returns flowOf(emptyList())
        val provider = mockk<AuthorizedMusicProvider>(relaxed = true)
        val registry = mockk<AuthorizedProviderRegistry>(relaxed = true) {
            every { defaultProvider() } returns provider
        }
        val offline = NetworkAccessState(
            offlineMode = true,
            connected = true,
            wifi = true,
            metered = false,
            remoteBlockReason = NetworkBlockReason.OFFLINE_MODE,
            downloadBlockReason = NetworkBlockReason.OFFLINE_MODE,
        )
        val policy = object : NetworkAccessPolicy {
            private val mutable = MutableStateFlow(offline)
            override val state: StateFlow<NetworkAccessState> = mutable
            override suspend fun current(): NetworkAccessState = mutable.value
        }
        val viewModel = SearchViewModel(
            repository,
            registry,
            mockk<DownloadCoordinator>(relaxed = true),
            mockk<YtDlpExtractor>(relaxed = true),
            mockk<RemoteReferenceRepository> {
                every { observeAll() } returns flowOf(emptyList())
            },
            mockk<PreferencesStore>(relaxed = true) {
                coEvery { current() } returns AppPreferences(offlineMode = true)
            },
            searchContext(),
            policy,
        )

        viewModel.uiState.test {
            awaitItem()
            viewModel.selectTab(com.polentita.music.feature.search.SearchTab.EXPLORE)
            advanceTimeBy(SearchViewModel.SEARCH_DEBOUNCE_MS + 1)
            runCurrent()
            var item = awaitItem()
            while (item.explore.status != ExploreStatus.OFFLINE_MODE) item = awaitItem()

            coVerify(exactly = 0) { provider.search(any()) }
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `search view model restores and persists sort preferences`() = runTest {
        val repository = repositoryMock()
        every { repository.search(any(), any(), any(), any()) } returns flowOf(emptyList())
        val preferences = mockk<PreferencesStore>(relaxed = true) {
            coEvery { current() } returns AppPreferences(
                searchSort = SongSort.DATE_ADDED.name,
                searchAscending = false,
            )
        }
        val viewModel = SearchViewModel(
            repository,
            mockk<AuthorizedProviderRegistry>(relaxed = true),
            mockk<DownloadCoordinator>(relaxed = true),
            mockk<YtDlpExtractor>(relaxed = true),
            mockk<RemoteReferenceRepository> {
                every { observeAll() } returns flowOf(emptyList())
            },
            preferences,
            searchContext(),
        )

        viewModel.uiState.test {
            var item = awaitItem()
            while (item.sort != SongSort.DATE_ADDED || item.ascending) item = awaitItem()
            viewModel.setSort(SongSort.ARTIST)
            viewModel.toggleAscending()
            runCurrent()
            item = awaitItem()
            while (item.sort != SongSort.ARTIST || !item.ascending) item = awaitItem()
            coVerify { preferences.setSearchSort(SongSort.ARTIST.name) }
            coVerify { preferences.setSearchAscending(true) }
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `explore download inspects youtube result then queues it through yt dlp`() = runTest {
        val repository = repositoryMock()
        every { repository.search(any(), any(), any(), any()) } returns flowOf(emptyList())
        val sourceUrl = "https://www.youtube.com/watch?v=video-1"
        val track = RemoteTrack(
            id = "video-1",
            title = "Canción remota",
            artist = RemoteArtist("channel-1", "Artista remoto"),
            album = RemoteAlbum("youtube", "YouTube", RemoteArtist("channel-1", "Artista remoto")),
            durationMs = 90_000,
            coverUri = null,
            providerId = "youtube",
            providerName = "YouTube",
            license = ProviderLicense(
                id = "youtube",
                name = "Licencia estándar",
                url = null,
                allowsDownload = false,
                requiresAttribution = true,
            ),
            attribution = null,
            allowsDownload = true,
            externalUrl = sourceUrl,
        )
        val provider = mockk<AuthorizedMusicProvider>(relaxed = true) {
            every { id } returns "youtube"
            every { displayName } returns "YouTube"
            every { isConfigured } returns true
            coEvery { resolveDownload(track) } returns Result.success(
                AuthorizedDownload(
                    track = track,
                    fileName = "Canción remota.webm",
                    mimeType = "audio/webm",
                    source = AuthorizedDownloadSource.YtDlp(sourceUrl),
                ),
            )
        }
        val registry = mockk<AuthorizedProviderRegistry> {
            every { defaultProvider() } returns provider
            every { providers } returns listOf(provider)
            every { provider("youtube") } returns provider
        }
        val coordinator = mockk<DownloadCoordinator>(relaxed = true)
        val extractor = mockk<YtDlpExtractor>(relaxed = true)
        val info = YtDlpMediaInfo(
            id = "video-1",
            title = "Canción remota",
            artist = "Artista remoto",
            album = "YouTube",
            durationMs = 90_000,
            thumbnailUrl = null,
            webpageUrl = sourceUrl,
            extractor = "YouTube",
            extension = "webm",
            sizeBytes = -1,
        )
        coEvery { extractor.inspect(sourceUrl) } returns info
        val viewModel = SearchViewModel(
            repository,
            registry,
            coordinator,
            extractor,
            mockk<RemoteReferenceRepository> {
                every { observeAll() } returns flowOf(emptyList())
            },
            mockk<PreferencesStore>(relaxed = true) {
                coEvery { current() } returns AppPreferences()
            },
            searchContext(),
        )

        viewModel.uiState.test {
            awaitItem()
            viewModel.download(track)
            runCurrent()
            var item = awaitItem()
            while (item.remoteDownload.status != com.polentita.music.feature.search.RemoteDownloadStatus.READY) {
                item = awaitItem()
            }
            assertEquals(info.copy(album = ""), item.remoteDownload.inspected)
            coVerify { extractor.inspect(sourceUrl) }

            viewModel.enqueueYtDlp("Título editado", "Artista editado", "Álbum editado")
            runCurrent()
            coVerify {
                coordinator.enqueueYtDlp(
                    sourceUrl,
                    info.copy(album = ""),
                    "Título editado",
                    "Artista editado",
                    "Álbum editado",
                    null,
                )
            }
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `explore searches using the most recently added library song`() = runTest {
        val repository = repositoryMock()
        every { repository.search(any(), any(), any(), any()) } returns flowOf(emptyList())
        every { repository.observeSongs() } returns flowOf(
            listOf(
                song(1, "Shadows").copy(
                    artist = "PASTEL GHOST",
                    sourceType = SourceType.DOWNLOADED.name,
                    dateAdded = 20,
                ),
            ),
        )
        val provider = mockk<AuthorizedMusicProvider>(relaxed = true) {
            every { id } returns "related-provider"
            every { displayName } returns "Proveedor relacionado"
            every { isConfigured } returns true
            coEvery { search(any()) } returns Result.success(emptyList())
        }
        val registry = mockk<AuthorizedProviderRegistry> {
            every { defaultProvider() } returns provider
            every { providers } returns listOf(provider)
            every { provider("related-provider") } returns provider
        }
        val viewModel = SearchViewModel(
            repository,
            registry,
            mockk<DownloadCoordinator>(relaxed = true),
            mockk<YtDlpExtractor>(relaxed = true),
            mockk<RemoteReferenceRepository> {
                every { observeAll() } returns flowOf(emptyList())
            },
            mockk<PreferencesStore>(relaxed = true) {
                coEvery { current() } returns AppPreferences()
            },
            searchContext(),
        )

        viewModel.uiState.test {
            awaitItem()
            viewModel.selectTab(SearchTab.EXPLORE)
            advanceTimeBy(SearchViewModel.SEARCH_DEBOUNCE_MS + 1)
            runCurrent()

            var item = awaitItem()
            while (item.selectedTab != SearchTab.EXPLORE ||
                item.explore.status == ExploreStatus.INITIAL ||
                item.explore.status == ExploreStatus.LOADING
            ) {
                item = awaitItem()
            }
            assertEquals(ExploreStatus.EMPTY, item.explore.status)
            assertEquals("No encontramos resultados relacionados con tu biblioteca.", item.explore.message)
            coVerify { provider.search("PASTEL GHOST") }
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `explore does not fill the screen without library context`() = runTest {
        val repository = repositoryMock()
        every { repository.search(any(), any(), any(), any()) } returns flowOf(emptyList())
        val provider = mockk<AuthorizedMusicProvider>(relaxed = true) {
            every { id } returns "youtube"
            every { displayName } returns "YouTube"
            every { isConfigured } returns true
        }
        val registry = mockk<AuthorizedProviderRegistry> {
            every { defaultProvider() } returns provider
            every { providers } returns listOf(provider)
            every { provider("youtube") } returns provider
        }
        val viewModel = SearchViewModel(
            repository,
            registry,
            mockk<DownloadCoordinator>(relaxed = true),
            mockk<YtDlpExtractor>(relaxed = true),
            mockk<RemoteReferenceRepository> {
                every { observeAll() } returns flowOf(emptyList())
            },
            mockk<PreferencesStore>(relaxed = true) {
                coEvery { current() } returns AppPreferences()
            },
            searchContext(),
        )

        viewModel.uiState.test {
            awaitItem()
            viewModel.selectTab(SearchTab.EXPLORE)
            advanceTimeBy(SearchViewModel.SEARCH_DEBOUNCE_MS + 1)
            runCurrent()

            var item = awaitItem()
            while (item.selectedTab != SearchTab.EXPLORE ||
                item.explore.status == ExploreStatus.INITIAL ||
                item.explore.status == ExploreStatus.LOADING
            ) {
                item = awaitItem()
            }
            assertEquals(ExploreStatus.EMPTY, item.explore.status)
            assertEquals(
                "Descarga o importa una canción para recibir recomendaciones relacionadas.",
                item.explore.message,
            )
            coVerify(exactly = 0) { provider.search(any()) }
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `explore removes a newly downloaded result when library changes`() = runTest {
        val repository = repositoryMock()
        val librarySongs = MutableStateFlow(
            listOf(
                song(1, "Uno").copy(
                    artist = "Artista uno",
                    sourceType = SourceType.DOWNLOADED.name,
                ),
            ),
        )
        every { repository.observeSongs() } returns librarySongs
        every { repository.search(any(), any(), any(), any()) } returns flowOf(emptyList())
        val track = remoteTrack()
        val provider = mockk<AuthorizedMusicProvider>(relaxed = true) {
            every { id } returns "related-provider"
            every { displayName } returns "Proveedor relacionado"
            every { isConfigured } returns true
            coEvery { search(any()) } returns Result.success(listOf(track))
        }
        val registry = mockk<AuthorizedProviderRegistry> {
            every { defaultProvider() } returns provider
            every { providers } returns listOf(provider)
            every { provider("related-provider") } returns provider
        }
        val viewModel = SearchViewModel(
            repository,
            registry,
            mockk<DownloadCoordinator>(relaxed = true),
            mockk<YtDlpExtractor>(relaxed = true),
            mockk<RemoteReferenceRepository> {
                every { observeAll() } returns flowOf(emptyList())
            },
            mockk<PreferencesStore>(relaxed = true) {
                coEvery { current() } returns AppPreferences()
            },
            searchContext(),
        )

        viewModel.uiState.test {
            awaitItem()
            viewModel.selectTab(SearchTab.EXPLORE)
            advanceTimeBy(SearchViewModel.SEARCH_DEBOUNCE_MS + 1)
            runCurrent()

            var item = awaitItem()
            while (item.explore.status != ExploreStatus.SUCCESS) item = awaitItem()
            assertEquals(listOf(track), item.explore.results)
            coVerify(exactly = 1) { provider.search("Artista uno") }

            librarySongs.value = listOf(
                song(2, "Canción remota").copy(
                    artist = "Artista remoto",
                    sourceType = SourceType.DOWNLOADED.name,
                ),
            )
            runCurrent()
            item = awaitItem()
            while (item.explore.status != ExploreStatus.EMPTY) item = awaitItem()
            assertEquals(emptyList<RemoteTrack>(), item.explore.results)
            coVerify(exactly = 1) { provider.search(any()) }

            viewModel.refreshExplore()
            runCurrent()
            item = awaitItem()
            while (item.explore.status != ExploreStatus.EMPTY) item = awaitItem()
            coVerify(exactly = 2) { provider.search(any()) }
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `explore hides available library songs from provider results`() = runTest {
        val repository = repositoryMock()
        val downloaded = song(1, "Shadows").copy(
            artist = "Pastel Ghost",
            sourceType = SourceType.DOWNLOADED.name,
            sourceUrl = "https://www.youtube.com/watch",
        )
        val imported = song(2, "No Puedo").copy(
            artist = "Paulo Londra",
            sourceType = SourceType.IMPORTED.name,
        )
        every { repository.observeSongs() } returns flowOf(listOf(downloaded, imported))
        every { repository.search(any(), any(), any(), any()) } returns flowOf(emptyList())
        val duplicate = remoteTrack().copy(
            id = "video-duplicate",
            title = "PASTEL GHOST ~ SHADOWS",
            artist = RemoteArtist("channel-1", "PASTEL GHOST"),
        )
        val importedDuplicate = remoteTrack().copy(
            id = "video-imported-duplicate",
            title = "No Puedo (Video Oficial)",
            artist = RemoteArtist("channel-2", "Paulo Londra"),
        )
        val newTrack = remoteTrack().copy(
            id = "video-new",
            title = "Otra canción",
            artist = RemoteArtist("channel-3", "Otro artista"),
        )
        val provider = mockk<AuthorizedMusicProvider>(relaxed = true) {
            every { id } returns "youtube"
            every { displayName } returns "YouTube"
            every { isConfigured } returns true
            coEvery { search(any()) } returns Result.success(
                listOf(duplicate, importedDuplicate, newTrack),
            )
        }
        val registry = mockk<AuthorizedProviderRegistry> {
            every { defaultProvider() } returns provider
            every { providers } returns listOf(provider)
            every { provider("youtube") } returns provider
        }
        val viewModel = SearchViewModel(
            repository,
            registry,
            mockk<DownloadCoordinator>(relaxed = true),
            mockk<YtDlpExtractor>(relaxed = true),
            mockk<RemoteReferenceRepository> {
                every { observeAll() } returns flowOf(emptyList())
            },
            mockk<PreferencesStore>(relaxed = true) {
                coEvery { current() } returns AppPreferences()
            },
            searchContext(),
        )

        viewModel.uiState.test {
            awaitItem()
            viewModel.selectTab(SearchTab.EXPLORE)
            advanceTimeBy(SearchViewModel.SEARCH_DEBOUNCE_MS + 1)
            runCurrent()

            var item = awaitItem()
            while (item.explore.status != ExploreStatus.SUCCESS) item = awaitItem()
            assertEquals(listOf(newTrack), item.explore.results)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `explore appends the next provider page without replacing current results`() = runTest {
        val repository = repositoryMock()
        every { repository.search(any(), any(), any(), any()) } returns flowOf(emptyList())
        val first = remoteTrack().copy(id = "video-first", title = "Primera canción")
        val second = remoteTrack().copy(id = "video-second", title = "Segunda canción")
        val provider = PagedTestProvider(
            pages = listOf(
                RemoteSearchPage(listOf(first), nextPageToken = "page-2"),
                RemoteSearchPage(listOf(second)),
            ),
        )
        val registry = mockk<AuthorizedProviderRegistry> {
            every { defaultProvider() } returns provider
            every { providers } returns listOf(provider)
            every { provider("youtube") } returns provider
        }
        val viewModel = SearchViewModel(
            repository,
            registry,
            mockk<DownloadCoordinator>(relaxed = true),
            mockk<YtDlpExtractor>(relaxed = true),
            mockk<RemoteReferenceRepository> {
                every { observeAll() } returns flowOf(emptyList())
            },
            mockk<PreferencesStore>(relaxed = true) {
                coEvery { current() } returns AppPreferences()
            },
            searchContext(),
        )

        viewModel.uiState.test {
            awaitItem()
            viewModel.setQuery("canciones")
            viewModel.selectTab(SearchTab.EXPLORE)
            advanceTimeBy(SearchViewModel.SEARCH_DEBOUNCE_MS + 1)
            runCurrent()

            var item = awaitItem()
            while (item.explore.status != ExploreStatus.SUCCESS) item = awaitItem()
            assertEquals(listOf(first), item.explore.results)
            assertTrue(item.explore.canLoadMore)

            viewModel.loadMoreExplore()
            runCurrent()
            item = awaitItem()
            while (item.explore.results.size < 2) item = awaitItem()
            assertEquals(listOf(first, second), item.explore.results)
            assertFalse(item.explore.canLoadMore)
            assertEquals(listOf(null, "page-2"), provider.requestedTokens)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `explore preview resolves through yt dlp`() = runTest {
        val repository = repositoryMock()
        every { repository.search(any(), any(), any(), any()) } returns flowOf(emptyList())
        val track = remoteTrack()
        val preview = YtDlpPreviewInfo(
            id = track.id,
            title = track.title,
            artist = track.artist.name,
            durationMs = track.durationMs,
            thumbnailUrl = track.coverUri,
            webpageUrl = requireNotNull(track.externalUrl),
            streamUrl = "https://stream.example/audio.webm",
        )
        val extractor = mockk<YtDlpExtractor>(relaxed = true)
        coEvery { extractor.resolvePreview(requireNotNull(track.externalUrl)) } returns preview
        val viewModel = SearchViewModel(
            repository,
            mockk<AuthorizedProviderRegistry>(relaxed = true),
            mockk<DownloadCoordinator>(relaxed = true),
            extractor,
            mockk<RemoteReferenceRepository> {
                every { observeAll() } returns flowOf(emptyList())
            },
            mockk<PreferencesStore>(relaxed = true) {
                coEvery { current() } returns AppPreferences()
            },
            searchContext(),
        )

        viewModel.uiState.test {
            awaitItem()
            viewModel.preview(track)
            var item = awaitItem()
            while (item.preview.status != com.polentita.music.feature.search.RemotePreviewStatus.READY) {
                item = awaitItem()
            }
            assertEquals(preview, item.preview.preview)
            coVerify { extractor.resolvePreview(requireNotNull(track.externalUrl)) }
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `saved remote reference is exposed and persisted`() = runTest {
        val repository = repositoryMock()
        every { repository.search(any(), any(), any(), any()) } returns flowOf(emptyList())
        val saved = MutableStateFlow<List<RemoteReferenceEntity>>(emptyList())
        val referenceRepository = mockk<RemoteReferenceRepository>(relaxed = true) {
            every { observeAll() } returns saved
        }
        val track = remoteTrack()
        val viewModel = SearchViewModel(
            repository,
            mockk<AuthorizedProviderRegistry>(relaxed = true),
            mockk<DownloadCoordinator>(relaxed = true),
            mockk<YtDlpExtractor>(relaxed = true),
            referenceRepository,
            mockk<PreferencesStore>(relaxed = true) {
                coEvery { current() } returns AppPreferences()
            },
            searchContext(),
        )

        viewModel.uiState.test {
            awaitItem()
            viewModel.toggleReference(track)
            runCurrent()
            coVerify { referenceRepository.save(track) }

            saved.value = listOf(
                RemoteReferenceEntity(
                    providerId = track.providerId,
                    remoteTrackId = track.id,
                    title = track.title,
                    artist = track.artist.name,
                    album = track.album.name,
                    durationMs = track.durationMs,
                    thumbnailUrl = track.coverUri,
                    externalUrl = requireNotNull(track.externalUrl),
                    license = track.license.name,
                    attribution = track.attribution?.text,
                ),
            )
            runCurrent()
            var item = awaitItem()
            while (item.savedReferences.isEmpty()) item = awaitItem()
            assertEquals(track.id, item.savedReferences.single().remoteTrackId)
            assertTrue(RemoteReferenceRepository.key(track.providerId, track.id) in item.savedReferenceKeys)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `saved remote reference opens the editable flow and is removed once queued`() = runTest {
        val repository = repositoryMock()
        every { repository.search(any(), any(), any(), any()) } returns flowOf(emptyList())
        val reference = RemoteReferenceEntity(
            providerId = "youtube",
            remoteTrackId = "video-1",
            title = "Referencia guardada",
            artist = "Artista remoto",
            album = "YouTube",
            durationMs = 90_000,
            thumbnailUrl = "https://i.example/video-1.jpg",
            externalUrl = "https://www.youtube.com/watch?v=video-1",
            license = "Licencia estándar",
            attribution = "Artista remoto",
        )
        val provider = mockk<AuthorizedMusicProvider>(relaxed = true) {
            every { id } returns "youtube"
            every { displayName } returns "YouTube"
            every { allowsDownload } returns true
            every { isConfigured } returns true
            coEvery { resolveDownload(any()) } returns Result.success(
                AuthorizedDownload(
                    track = remoteTrack(),
                    fileName = "Referencia guardada.webm",
                    mimeType = "audio/webm",
                    source = AuthorizedDownloadSource.YtDlp(reference.externalUrl),
                ),
            )
        }
        val registry = mockk<AuthorizedProviderRegistry> {
            every { defaultProvider() } returns provider
            every { providers } returns listOf(provider)
            every { provider("youtube") } returns provider
        }
        val info = YtDlpMediaInfo(
            id = reference.remoteTrackId,
            title = reference.title,
            artist = reference.artist,
            album = reference.album,
            durationMs = reference.durationMs,
            thumbnailUrl = reference.thumbnailUrl,
            webpageUrl = reference.externalUrl,
            extractor = "YouTube",
            extension = "webm",
            sizeBytes = -1,
        )
        val extractor = mockk<YtDlpExtractor>(relaxed = true)
        coEvery { extractor.inspect(reference.externalUrl) } returns info
        val coordinator = mockk<DownloadCoordinator>(relaxed = true)
        val referenceRepository = mockk<RemoteReferenceRepository>(relaxed = true) {
            every { observeAll() } returns flowOf(listOf(reference))
        }
        val viewModel = SearchViewModel(
            repository,
            registry,
            coordinator,
            extractor,
            referenceRepository,
            mockk<PreferencesStore>(relaxed = true) {
                coEvery { current() } returns AppPreferences()
            },
            searchContext(),
        )

        viewModel.uiState.test {
            awaitItem()
            viewModel.download(reference)
            runCurrent()
            var item = awaitItem()
            while (item.remoteDownload.status != com.polentita.music.feature.search.RemoteDownloadStatus.READY) {
                item = awaitItem()
            }
            assertEquals(info.copy(album = ""), item.remoteDownload.inspected)
            coVerify { provider.resolveDownload(match { it.id == reference.remoteTrackId }) }
            coVerify { extractor.inspect(reference.externalUrl) }
            viewModel.enqueueYtDlp("Título editado", "Artista editado", "", null)
            runCurrent()
            coVerify {
                coordinator.enqueueYtDlp(
                    reference.externalUrl,
                    info.copy(album = ""),
                    "Título editado",
                    "Artista editado",
                    "",
                    null,
                )
            }
            coVerify { referenceRepository.remove(reference.providerId, reference.remoteTrackId) }
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `library view model restores and applies sort direction`() = runTest {
        val repository = repositoryMock()
        val songs = listOf(
            song(1, "Beta").copy(dateAdded = 10),
            song(2, "Alpha").copy(dateAdded = 20),
        )
        every { repository.observeSongs() } returns flowOf(songs)
        every { repository.search("", SongFilter(), SongSort.DATE_ADDED, false) } returns flowOf(songs.asReversed())
        every { repository.search("", SongFilter(), SongSort.TITLE, true) } returns flowOf(songs.asReversed())
        val preferences = mockk<PreferencesStore>(relaxed = true) {
            coEvery { current() } returns AppPreferences(
                librarySort = SongSort.DATE_ADDED.name,
                libraryAscending = false,
            )
        }
        val viewModel = LibraryViewModel(
            repository,
            mockk<LibraryStorage>(relaxed = true),
            mockk<DeviceMusicScanner>(relaxed = true),
            preferences,
            mockk<ArtworkEditingRepository>(relaxed = true),
        )

        viewModel.uiState.test {
            var item = awaitItem()
            while (item.sort != SongSort.DATE_ADDED || item.ascending) item = awaitItem()
            assertEquals(listOf(2L, 1L), item.songs.map(Song::id))
            viewModel.setSort(SongSort.TITLE)
            viewModel.toggleAscending()
            runCurrent()
            item = awaitItem()
            while (item.sort != SongSort.TITLE || !item.ascending) item = awaitItem()
            assertEquals(listOf(2L, 1L), item.songs.map(Song::id))
            coVerify { preferences.setLibrarySort(SongSort.TITLE.name) }
            coVerify { preferences.setLibraryAscending(true) }
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `library search updates visible query immediately and debounces songs`() = runTest {
        val repository = repositoryMock()
        val result = song(2, "Shadows").copy(artist = "Pastel Ghost")
        val matchingAlbum = AlbumEntity(id = 1, name = "Dark Beach", artist = "Pastel Ghost")
        val otherAlbum = AlbumEntity(id = 2, name = "Other", artist = "Arcane S2")
        every { repository.observeAlbums() } returns flowOf(listOf(matchingAlbum, otherAlbum))
        every { repository.observeArtists() } returns flowOf(listOf("Pastel Ghost", "Arcane S2"))
        every {
            repository.search("pastel", SongFilter(), SongSort.TITLE, true)
        } returns flowOf(listOf(result))
        val preferences = mockk<PreferencesStore>(relaxed = true) {
            coEvery { current() } returns AppPreferences()
        }
        val viewModel = LibraryViewModel(
            repository,
            mockk<LibraryStorage>(relaxed = true),
            mockk<DeviceMusicScanner>(relaxed = true),
            preferences,
            mockk<ArtworkEditingRepository>(relaxed = true),
        )

        viewModel.uiState.test {
            var item = awaitItem()
            while (item.loading) item = awaitItem()
            viewModel.setQuery("  pastel  ")
            item = awaitItem()
            while (item.query != "  pastel  ") item = awaitItem()
            assertEquals(listOf(matchingAlbum), item.albums)
            assertEquals(listOf("Pastel Ghost"), item.artists)

            advanceTimeBy(LibraryViewModel.SEARCH_DEBOUNCE_MS + 1)
            runCurrent()
            item = awaitItem()
            while (item.songs != listOf(result)) item = awaitItem()
            verify { repository.search("pastel", SongFilter(), SongSort.TITLE, true) }
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `player view model delegates playback and exposes state`() {
        val controller = mockk<PlaybackController>(relaxed = true)
        every { controller.state } returns MutableStateFlow(PlaybackUiState())
        val repository = repositoryMock()
        val viewModel = PlayerViewModel(controller, repository)
        val songs = listOf(song(1, "Uno"))

        viewModel.play(songs)

        verify { controller.playSongs(songs, 0, false) }
    }

    @Test
    fun `player view model plays an individual song and clears the queue`() {
        val controller = mockk<PlaybackController>(relaxed = true)
        every { controller.state } returns MutableStateFlow(PlaybackUiState())
        val viewModel = PlayerViewModel(controller, repositoryMock())
        val song = song(1, "Uno")

        viewModel.playSong(song)
        viewModel.clearQueue()

        verify { controller.playSong(song) }
        verify { controller.clearQueue() }
    }

    @Test
    fun `player visual state ignores progress-only updates`() = runTest {
        val playback = MutableStateFlow(
            PlaybackUiState(
                currentSongId = 1,
                title = "Uno",
                positionMs = 15_000,
                durationMs = 120_000,
                bufferedPercentage = 30,
            ),
        )
        val controller = mockk<PlaybackController>(relaxed = true)
        every { controller.state } returns playback
        val viewModel = PlayerViewModel(controller, repositoryMock())
        runCurrent()

        viewModel.visualState.test {
            val initial = awaitItem()
            assertEquals(0, initial.positionMs)
            assertEquals(0, initial.durationMs)

            playback.value = playback.value.copy(
                positionMs = 16_000,
                durationMs = 120_500,
                bufferedPercentage = 31,
            )
            runCurrent()
            expectNoEvents()

            playback.value = playback.value.copy(isPlaying = true)
            runCurrent()
            assertTrue(awaitItem().isPlaying)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `player shuffled playback starts away from first song when album has alternatives`() {
        val controller = mockk<PlaybackController>(relaxed = true)
        every { controller.state } returns MutableStateFlow(PlaybackUiState(currentSongId = 1))
        val viewModel = PlayerViewModel(controller, repositoryMock())
        val songs = listOf(song(1, "Uno"), song(2, "Dos"), song(3, "Tres"))

        viewModel.playShuffled(songs)

        verify { controller.playSongs(songs, match { it in 1 until songs.size }, true) }
    }

    @Test
    fun `player exposes favorite changes for current song`() = runTest {
        val currentSong = song(1, "Uno")
        val songFlow = MutableStateFlow<Song?>(currentSong)
        val controller = mockk<PlaybackController>(relaxed = true)
        every { controller.state } returns MutableStateFlow(
            PlaybackUiState(currentSongId = currentSong.id),
        )
        val repository = repositoryMock()
        every { repository.observeSong(currentSong.id) } returns songFlow
        val viewModel = PlayerViewModel(controller, repository)

        viewModel.isFavorite.test {
            assertFalse(awaitItem())
            songFlow.value = currentSong.copy(isFavorite = true)
            assertTrue(awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    private fun repositoryMock(): MusicRepository = mockk(relaxed = true) {
        every { observeSongs() } returns flowOf(emptyList())
        every { search(any(), any(), any(), any()) } returns flowOf(emptyList())
        every { observeAlbums() } returns flowOf<List<AlbumEntity>>(emptyList())
        every { observeArtists() } returns flowOf(emptyList())
        every { observePlaylists() } returns flowOf<List<PlaylistSummary>>(emptyList())
    }

    private fun searchContext(): Context = mockk(relaxed = true) {
        every { getString(R.string.search_explore_no_library_context) } returns
            "Descarga o importa una canción para recibir recomendaciones relacionadas."
        every { getString(R.string.search_explore_no_related_results) } returns
            "No encontramos resultados relacionados con tu biblioteca."
    }

    private fun song(id: Long, title: String) = Song(
        id = id,
        title = title,
        artist = "",
        albumId = null,
        albumName = "",
        genre = "",
        year = null,
        trackNumber = null,
        discNumber = null,
        durationMs = 1_000,
        contentUri = "content://test/$id",
        originalFileName = "$title.wav",
        displayFileName = "$title.wav",
        mimeType = "audio/wav",
        fileSize = 44,
        coverUri = null,
        sourceType = "IMPORTED",
        sourceUrl = null,
        dateAdded = 0,
        dateModified = 0,
        lastPlayedAt = null,
        playCount = 0,
        isFavorite = false,
        isAvailable = true,
        checksum = id.toString(),
    )

    private fun remoteTrack() = RemoteTrack(
        id = "video-1",
        title = "Canción remota",
        artist = RemoteArtist("channel-1", "Artista remoto"),
        album = RemoteAlbum("youtube", "YouTube", RemoteArtist("channel-1", "Artista remoto")),
        durationMs = 90_000,
        coverUri = "https://i.example/video-1.jpg",
        providerId = "youtube",
        providerName = "YouTube",
        license = ProviderLicense(
            id = "youtube",
            name = "Licencia estándar",
            url = "https://www.youtube.com/t/terms",
            allowsDownload = false,
            requiresAttribution = true,
        ),
        attribution = null,
        allowsDownload = true,
        externalUrl = "https://www.youtube.com/watch?v=video-1",
    )
}

private class PagedTestProvider(
    private val pages: List<RemoteSearchPage>,
) : AuthorizedMusicProvider, PaginatedAuthorizedMusicProvider {
    val requestedTokens = mutableListOf<String?>()

    override val id: String = "youtube"
    override val displayName: String = "YouTube"
    override val allowsDownload: Boolean = true
    override val isConfigured: Boolean = true

    override suspend fun search(query: String): Result<List<RemoteTrack>> =
        Result.failure(UnsupportedOperationException("La prueba usa searchPage"))

    override suspend fun searchPage(query: String, pageToken: String?): Result<RemoteSearchPage> {
        requestedTokens += pageToken
        return Result.success(pages[requestedTokens.lastIndex])
    }

    override suspend fun resolveDownload(track: RemoteTrack): Result<AuthorizedDownload> =
        Result.failure(UnsupportedOperationException("No se prueba la descarga"))
}
