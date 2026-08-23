package com.polentita.music.data.repository

import com.polentita.music.core.database.RemoteReferenceDao
import com.polentita.music.domain.provider.ProviderAttribution
import com.polentita.music.domain.provider.ProviderLicense
import com.polentita.music.domain.provider.RemoteAlbum
import com.polentita.music.domain.provider.RemoteArtist
import com.polentita.music.domain.provider.RemoteTrack
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class RemoteReferenceRepositoryTest {
    private val dao = mockk<RemoteReferenceDao>(relaxed = true)
    private val repository = RemoteReferenceRepository(dao)

    @Test
    fun `saved keys retain provider identity and remote id`() = runTest {
        every { dao.observeAll() } returns flowOf(
            listOf(
                mockk {
                    every { providerId } returns "youtube"
                    every { remoteTrackId } returns "video-1"
                },
            ),
        )

        assertEquals(setOf("youtube:video-1"), repository.observeSavedKeys().first())
    }

    @Test
    fun `save maps an external result without changing its license metadata`() = runTest {
        repository.save(track())

        coVerify {
            dao.upsert(
                match {
                    it.providerId == "youtube" &&
                        it.remoteTrackId == "video-1" &&
                        it.externalUrl == "https://www.youtube.com/watch?v=video-1" &&
                        it.license == "Creative Commons en YouTube" &&
                        it.attribution == "Canal académico"
                },
            )
        }
    }

    private fun track(): RemoteTrack {
        val artist = RemoteArtist("channel-1", "Canal académico")
        return RemoteTrack(
            id = "video-1",
            title = "Referencia",
            artist = artist,
            album = RemoteAlbum("youtube", "YouTube", artist),
            durationMs = 10_000,
            coverUri = "https://i.example/cover.jpg",
            providerId = "youtube",
            providerName = "YouTube",
            license = ProviderLicense(
                id = "creativeCommon",
                name = "Creative Commons en YouTube",
                url = "https://www.youtube.com/t/terms",
                allowsDownload = false,
                requiresAttribution = true,
            ),
            attribution = ProviderAttribution("Canal académico"),
            allowsDownload = false,
            externalUrl = "https://www.youtube.com/watch?v=video-1",
        )
    }
}
