package com.polentita.music.data.extractor

import kotlinx.coroutines.CancellationException

/**
 * Resolves a public YouTube URL through the same authorized fallback used by
 * Search and playlist imports. It only inspects metadata before a download is
 * explicitly enqueued by the caller.
 */
class YtDlpSourceResolver(
    private val extractor: YtDlpExtractor,
) {
    suspend fun <T> resolve(
        title: String,
        artist: String,
        sourceUrl: String,
        resolver: suspend (String) -> T,
    ): ResolvedYtDlp<T> {
        try {
            return ResolvedYtDlp(sourceUrl, resolver(sourceUrl))
        } catch (error: CancellationException) {
            throw error
        } catch (directError: Throwable) {
            var lastError = directError
            for (candidateUrl in fallbackUrls(title, artist, sourceUrl)) {
                try {
                    return ResolvedYtDlp(candidateUrl, resolver(candidateUrl))
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Throwable) {
                    lastError = error
                }
            }
            throw lastError
        }
    }

    private suspend fun fallbackUrls(
        title: String,
        artist: String,
        sourceUrl: String,
    ): List<String> {
        val query = listOf(title, artist)
            .map(String::trim)
            .filter(String::isNotBlank)
            .joinToString(" ")
        if (query.length < 3) return emptyList()

        val results = try {
            extractor.search(
                query,
                page = 0,
                pageSize = FALLBACK_SEARCH_PAGE_SIZE,
            ).items
        } catch (error: CancellationException) {
            throw error
        } catch (_: Throwable) {
            return emptyList()
        }
        return results.asSequence()
            .filter { result ->
                result.webpageUrl.isNotBlank() && result.webpageUrl != sourceUrl
            }
            .map(YtDlpSearchResult::webpageUrl)
            .distinct()
            .take(FALLBACK_RESOLUTION_LIMIT)
            .toList()
    }

    companion object {
        const val FALLBACK_SEARCH_PAGE_SIZE = 10
        const val FALLBACK_RESOLUTION_LIMIT = 5
    }
}

data class ResolvedYtDlp<T>(
    val sourceUrl: String,
    val value: T,
)
