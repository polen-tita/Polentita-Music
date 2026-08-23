package com.polentita.music.feature.editor

import com.polentita.music.MainDispatcherRule
import com.polentita.music.core.network.NetworkAccessPolicy
import com.polentita.music.core.network.NetworkAccessState
import com.polentita.music.domain.artwork.ArtworkCandidate
import com.polentita.music.domain.artwork.ArtworkSearchRepository
import com.polentita.music.domain.artwork.ArtworkSearchRequest
import com.polentita.music.domain.artwork.ArtworkSearchResult
import com.polentita.music.domain.artwork.ArtworkSource
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ArtworkPickerViewModelTest {
    @get:Rule val main = MainDispatcherRule()

    @Test
    fun `opening picker performs initial search and keeps partial provider results`() = runTest {
        val cover = candidate("internet", ArtworkSource.INTERNET)
        val repository = RecordingSearchRepository(
            listOf(ArtworkSearchResult(
                candidates = listOf(cover),
                sourceErrors = mapOf(ArtworkSource.TIDAL to "TIDAL no respondió"),
            )),
        )
        val viewModel = ArtworkPickerViewModel(repository, FakeNetworkPolicy(allowedAccess()))

        viewModel.open("album-1", "AM", "Arctic Monkeys")
        runCurrent()

        assertEquals(ArtworkSearchRequest("AM", "Arctic Monkeys"), repository.requests.single())
        assertEquals(listOf(cover), viewModel.state.value.candidates)
        assertEquals("TIDAL no respondió", viewModel.state.value.sourceErrors[ArtworkSource.TIDAL])
        assertFalse(viewModel.state.value.loading)
    }

    @Test
    fun `source filter changes visible candidates without repeating the search`() = runTest {
        val coverArt = candidate("internet", ArtworkSource.INTERNET)
        val tidal = candidate("tidal", ArtworkSource.TIDAL)
        val repository = RecordingSearchRepository(listOf(ArtworkSearchResult(listOf(coverArt, tidal))))
        val viewModel = ArtworkPickerViewModel(repository, FakeNetworkPolicy(allowedAccess()))
        viewModel.open("song-1", "AM", "Arctic Monkeys")
        runCurrent()

        viewModel.selectSource(ArtworkSource.TIDAL)

        assertEquals(listOf(tidal), viewModel.state.value.visibleCandidates)
        assertEquals(1, repository.requests.size)
    }

    @Test
    fun `load more appends internet results and advances the page`() = runTest {
        val first = candidate("first", ArtworkSource.INTERNET)
        val second = candidate("second", ArtworkSource.INTERNET)
        val repository = RecordingSearchRepository(
            listOf(
                ArtworkSearchResult(listOf(first), hasMore = true),
                ArtworkSearchResult(listOf(second), hasMore = false),
            ),
        )
        val viewModel = ArtworkPickerViewModel(repository, FakeNetworkPolicy(allowedAccess()))

        viewModel.open("song-1", "AM", "Arctic Monkeys")
        runCurrent()
        viewModel.loadMore()
        runCurrent()

        assertEquals(listOf(first, second), viewModel.state.value.candidates)
        assertEquals(1, repository.requests.last().page)
        assertFalse(viewModel.state.value.hasMore)
        assertTrue(viewModel.state.value.showNoMoreResultsHint)
    }

    @Test
    fun `network state immediately disables remote search while retaining local actions`() = runTest {
        val policy = FakeNetworkPolicy(allowedAccess())
        val viewModel = ArtworkPickerViewModel(
            RecordingSearchRepository(listOf(ArtworkSearchResult(emptyList()))),
            policy,
        )
        runCurrent()

        policy.mutable.value = NetworkAccessState(remoteSearchAllowed = false)
        runCurrent()

        assertFalse(viewModel.state.value.remoteSearchAllowed)
        assertTrue(viewModel.state.value.candidates.isEmpty())
    }

    @Test
    fun `opening picker offline does not call a remote repository`() = runTest {
        val repository = RecordingSearchRepository(listOf(ArtworkSearchResult(emptyList())))
        val viewModel = ArtworkPickerViewModel(
            repository,
            FakeNetworkPolicy(NetworkAccessState(remoteSearchAllowed = false)),
        )
        runCurrent()

        viewModel.open("album-1", "AM", "Arctic Monkeys")
        runCurrent()

        assertTrue(repository.requests.isEmpty())
        assertFalse(viewModel.state.value.loading)
        assertEquals(null, viewModel.state.value.error)
    }

    private fun candidate(id: String, source: ArtworkSource) = ArtworkCandidate(
        id = id,
        source = source,
        title = "AM",
        artist = "Arctic Monkeys",
        imageUrl = "https://images.example.test/$id.jpg",
    )

    private class RecordingSearchRepository(
        private val results: List<ArtworkSearchResult>,
    ) : ArtworkSearchRepository {
        val requests = mutableListOf<ArtworkSearchRequest>()

        override suspend fun search(request: ArtworkSearchRequest): ArtworkSearchResult {
            requests += request
            return results.getOrElse(request.page) { results.last() }
        }
    }

    private class FakeNetworkPolicy(initial: NetworkAccessState) : NetworkAccessPolicy {
        val mutable = MutableStateFlow(initial)
        override val state: StateFlow<NetworkAccessState> = mutable
        override suspend fun current(): NetworkAccessState = mutable.value
    }

    private fun allowedAccess() = NetworkAccessState(
        connected = true,
        wifi = true,
        metered = false,
        remoteSearchAllowed = true,
        downloadAllowed = true,
        remoteBlockReason = null,
        downloadBlockReason = null,
    )
}
