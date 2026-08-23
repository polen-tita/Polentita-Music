package com.polentita.music.playback.queue

import com.polentita.music.domain.model.Song
import kotlin.random.Random

enum class PlaybackContextKind {
    LIBRARY,
    HOME,
    ALBUM,
    ARTIST,
    PLAYLIST,
    SMART_PLAYLIST,
    SINGLE,
}

enum class PlaybackQueueOrigin {
    CURRENT,
    MANUAL,
    CONTEXT,
    LIBRARY_FALLBACK,
}

data class PlaybackContext(
    val kind: PlaybackContextKind,
    val key: String,
    val label: String,
    val songs: List<Song>,
    val anchorSongId: Long? = null,
)

internal data class PlaybackContinuation(
    val contextSongs: List<Song>,
    val librarySongs: List<Song>,
)

/**
 * Builds only the automatic tail. Manual items live in Media3 and are inserted
 * before this result by PlaybackController.
 */
internal fun playbackContinuation(
    currentSongId: Long,
    contextSongs: List<Song>,
    manualSongIds: Set<Long>,
    librarySongs: List<Song>,
): PlaybackContinuation {
    val playableContext = contextSongs.filter(Song::isAvailable)
    val currentIndex = playableContext.indexOfFirst { it.id == currentSongId }
    val remainingContext = (if (currentIndex >= 0) {
        playableContext.drop(currentIndex + 1)
    } else {
        playableContext
    }).distinctBy(Song::id)
        .filterNot { it.id == currentSongId || it.id in manualSongIds }

    val occupied = buildSet {
        add(currentSongId)
        addAll(manualSongIds)
        addAll(playableContext.map(Song::id))
    }
    val fallback = librarySongs.asSequence()
        .filter(Song::isAvailable)
        .distinctBy(Song::id)
        .filterNot { it.id in occupied }
        .toList()
    return PlaybackContinuation(remainingContext, fallback)
}

fun List<Song>.sortedForPlayback(sort: String, ascending: Boolean): List<Song> {
    val comparator = when (sort) {
        "ARTIST" -> compareBy<Song> { it.artist.lowercase() }
        "ALBUM" -> compareBy<Song> { it.albumName.lowercase() }
        "DATE_ADDED" -> compareBy<Song>(Song::dateAdded)
        "DATE_MODIFIED" -> compareBy<Song>(Song::dateModified)
        "DURATION" -> compareBy<Song>(Song::durationMs)
        "PLAY_COUNT" -> compareBy<Song>(Song::playCount)
        "LAST_PLAYED" -> compareBy<Song> { it.lastPlayedAt ?: Long.MIN_VALUE }
        else -> compareBy<Song> { it.title.lowercase() }
    }.thenBy { it.title.lowercase() }
        .thenBy(Song::id)
    return sortedWith(if (ascending) comparator else comparator.reversed())
}

internal fun priorityShuffleOrder(
    currentIndex: Int,
    origins: List<PlaybackQueueOrigin>,
    seed: Long,
): IntArray {
    if (currentIndex !in origins.indices) return origins.indices.toList().toIntArray()
    val random = Random(seed)
    val previous = (0 until currentIndex).shuffled(random)
    val future = (currentIndex + 1 until origins.size).toList()
    val manual = future.filter { origins[it] == PlaybackQueueOrigin.MANUAL }.shuffled(random)
    val contextual = future.filter {
        origins[it] == PlaybackQueueOrigin.CONTEXT || origins[it] == PlaybackQueueOrigin.CURRENT
    }.shuffled(random)
    val library = future.filter { origins[it] == PlaybackQueueOrigin.LIBRARY_FALLBACK }.shuffled(random)
    return (previous + currentIndex + manual + contextual + library).toIntArray()
}
