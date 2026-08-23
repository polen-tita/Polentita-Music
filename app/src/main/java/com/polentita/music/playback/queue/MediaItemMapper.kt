package com.polentita.music.playback.queue

import android.net.Uri
import android.os.Bundle
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import com.polentita.music.domain.model.Song

const val ARTWORK_REVISION_EXTRA = "com.polentita.music.ARTWORK_REVISION"
const val QUEUE_ORIGIN_EXTRA = "com.polentita.music.QUEUE_ORIGIN"
const val PLAYBACK_CONTEXT_KIND_EXTRA = "com.polentita.music.PLAYBACK_CONTEXT_KIND"
const val PLAYBACK_CONTEXT_KEY_EXTRA = "com.polentita.music.PLAYBACK_CONTEXT_KEY"
const val PLAYBACK_CONTEXT_LABEL_EXTRA = "com.polentita.music.PLAYBACK_CONTEXT_LABEL"
const val PLAYBACK_CONTEXT_ANCHOR_EXTRA = "com.polentita.music.PLAYBACK_CONTEXT_ANCHOR"

fun Song.toMediaItem(
    origin: PlaybackQueueOrigin = PlaybackQueueOrigin.CONTEXT,
    context: PlaybackContext? = null,
): MediaItem = MediaItem.Builder()
    .setMediaId(id.toString())
    .setUri(contentUri)
    .setMediaMetadata(
        MediaMetadata.Builder()
            .setTitle(title)
            .setArtist(artist)
            .setAlbumTitle(albumName)
            .setDurationMs(durationMs)
            .setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC)
            .setArtworkUri(coverUri?.let(Uri::parse))
            .setExtras(
                Bundle().apply {
                    putLong(ARTWORK_REVISION_EXTRA, dateModified)
                    putString(QUEUE_ORIGIN_EXTRA, origin.name)
                    context?.let {
                        putString(PLAYBACK_CONTEXT_KIND_EXTRA, it.kind.name)
                        putString(PLAYBACK_CONTEXT_KEY_EXTRA, it.key)
                        putString(PLAYBACK_CONTEXT_LABEL_EXTRA, it.label)
                        it.anchorSongId?.let { anchor ->
                            putLong(PLAYBACK_CONTEXT_ANCHOR_EXTRA, anchor)
                        }
                    }
                },
            )
            .setIsPlayable(isAvailable)
            .build(),
    )
    .build()

fun MediaItem.playbackQueueOrigin(): PlaybackQueueOrigin = runCatching {
    PlaybackQueueOrigin.valueOf(
        mediaMetadata.extras?.getString(QUEUE_ORIGIN_EXTRA).orEmpty(),
    )
}.getOrDefault(PlaybackQueueOrigin.MANUAL)

fun MediaItem.playbackContextKind(): PlaybackContextKind? = runCatching {
    PlaybackContextKind.valueOf(
        mediaMetadata.extras?.getString(PLAYBACK_CONTEXT_KIND_EXTRA).orEmpty(),
    )
}.getOrNull()

fun MediaItem.playbackContextKey(): String? =
    mediaMetadata.extras?.getString(PLAYBACK_CONTEXT_KEY_EXTRA)?.takeIf(String::isNotBlank)

fun MediaItem.playbackContextLabel(): String? =
    mediaMetadata.extras?.getString(PLAYBACK_CONTEXT_LABEL_EXTRA)?.takeIf(String::isNotBlank)

fun MediaItem.playbackContextAnchorId(): Long? = mediaMetadata.extras
    ?.takeIf { it.containsKey(PLAYBACK_CONTEXT_ANCHOR_EXTRA) }
    ?.getLong(PLAYBACK_CONTEXT_ANCHOR_EXTRA)

fun MediaItem.withPlaybackQueueMetadata(
    origin: PlaybackQueueOrigin,
    context: PlaybackContext?,
): MediaItem {
    val extras = Bundle(mediaMetadata.extras ?: Bundle()).apply {
        putString(QUEUE_ORIGIN_EXTRA, origin.name)
        if (context == null) {
            remove(PLAYBACK_CONTEXT_KIND_EXTRA)
            remove(PLAYBACK_CONTEXT_KEY_EXTRA)
            remove(PLAYBACK_CONTEXT_LABEL_EXTRA)
            remove(PLAYBACK_CONTEXT_ANCHOR_EXTRA)
        } else {
            putString(PLAYBACK_CONTEXT_KIND_EXTRA, context.kind.name)
            putString(PLAYBACK_CONTEXT_KEY_EXTRA, context.key)
            putString(PLAYBACK_CONTEXT_LABEL_EXTRA, context.label)
            context.anchorSongId?.let { putLong(PLAYBACK_CONTEXT_ANCHOR_EXTRA, it) }
                ?: remove(PLAYBACK_CONTEXT_ANCHOR_EXTRA)
        }
    }
    return buildUpon()
        .setMediaMetadata(mediaMetadata.buildUpon().setExtras(extras).build())
        .build()
}
