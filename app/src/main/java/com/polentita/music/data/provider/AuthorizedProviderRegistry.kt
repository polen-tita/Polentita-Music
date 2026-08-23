package com.polentita.music.data.provider

import com.polentita.music.domain.provider.AuthorizedMusicProvider
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthorizedProviderRegistry @Inject constructor(
    youtubeProvider: YouTubeAuthorizedMusicProvider,
) {
    val providers: List<AuthorizedMusicProvider> = listOf(youtubeProvider)

    fun defaultProvider(): AuthorizedMusicProvider? =
        providers.firstOrNull {
            it.id == YouTubeAuthorizedMusicProvider.PROVIDER_ID && it.isConfigured
        } ?: providers.firstOrNull { it.isConfigured } ?: providers.firstOrNull()

    fun provider(id: String): AuthorizedMusicProvider? = providers.firstOrNull { it.id == id }
}
