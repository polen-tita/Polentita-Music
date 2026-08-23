package com.polentita.music.feature.player

import android.content.Intent
import android.provider.Settings
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.VolumeDown
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.Player
import com.polentita.music.R
import com.polentita.music.core.common.formatDuration
import com.polentita.music.core.designsystem.Artwork
import com.polentita.music.core.designsystem.AdaptiveArtworkBackground
import com.polentita.music.core.designsystem.ArtworkDynamicTheme
import com.polentita.music.core.designsystem.EmptyState
import com.polentita.music.core.designsystem.PolentitaMotion
import com.polentita.music.core.designsystem.PolentitaOpacity
import com.polentita.music.core.designsystem.PolentitaAlertDialog
import com.polentita.music.core.designsystem.PolentitaRadii
import com.polentita.music.core.designsystem.PolentitaSpacing
import com.polentita.music.core.designsystem.animationDuration
import com.polentita.music.core.designsystem.rememberAnimationsEnabled
import com.polentita.music.core.designsystem.rememberArtworkPalette
import com.polentita.music.core.designsystem.localizedPlaybackContextLabel
import com.polentita.music.feature.library.TextFieldsDialog
import com.polentita.music.playback.queue.PlaybackQueueOrigin
import androidx.compose.ui.res.stringResource
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

@Composable
fun FullPlayerScreen(
    viewModel: PlayerViewModel,
    onQueue: () -> Unit,
    onEditSong: (Long) -> Unit,
    onBack: () -> Unit,
) {
    val state by viewModel.visualState.collectAsStateWithLifecycle()
    val isFavorite by viewModel.isFavorite.collectAsStateWithLifecycle()
    val localizedContextLabel = localizedPlaybackContextLabel(state.contextLabel)
    val favoriteSavedDescription = stringResource(R.string.favorite_saved)
    val favoriteNotSavedDescription = stringResource(R.string.favorite_not_saved)
    val playbackStatusDescription = stringResource(
        if (state.isPlaying) R.string.playback_status_playing else R.string.playback_status_paused,
    )
    val playerVolumeDescription = stringResource(R.string.player_volume)
    if (state.currentSongId == null && !state.isPreview) {
        EmptyState(
            stringResource(R.string.nothing_playing_title),
            stringResource(R.string.nothing_playing_message),
        )
        return
    }
    val palette = rememberArtworkPalette(
        coverUri = state.artworkUri,
        seed = "${state.title}|${state.artist}",
    )
    var horizontalDrag by remember { mutableFloatStateOf(0f) }
    val animationsEnabled = rememberAnimationsEnabled()
    val context = LocalContext.current
    ArtworkDynamicTheme(palette) {
        AdaptiveArtworkBackground(state.artworkUri, palette) {
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(
                        horizontal = PolentitaSpacing.large,
                        vertical = PolentitaSpacing.small,
                    ),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.KeyboardArrowDown, stringResource(R.string.player_minimize))
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            stringResource(R.string.now_playing),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            localizedContextLabel ?: if (state.queue.isNotEmpty()) {
                                stringResource(R.string.songs_in_queue, state.queue.size)
                            } else {
                                stringResource(R.string.app_name)
                            },
                            style = MaterialTheme.typography.labelMedium,
                            maxLines = 1,
                        )
                    }
                    IconButton(
                        onClick = { state.currentSongId?.let(onEditSong) },
                        enabled = state.currentSongId != null,
                    ) {
                        Icon(Icons.Default.Edit, stringResource(R.string.player_edit_info))
                    }
                }
                Spacer(Modifier.height(PolentitaSpacing.medium))
                AnimatedContent(
                    targetState = state.currentSongId to state.artworkUri,
                    transitionSpec = {
                        fadeIn(tween(animationDuration(animationsEnabled, PolentitaMotion.standard))) togetherWith
                            fadeOut(tween(animationDuration(animationsEnabled, PolentitaMotion.quick)))
                    },
                    label = "Cambio de portada",
                ) { (_, artwork) ->
                    Artwork(
                        artwork,
                        stringResource(R.string.artwork_large_description, state.title),
                        Modifier
                            .fillMaxWidth()
                            .aspectRatio(1f)
                            .pointerInput(state.currentSongId) {
                                detectHorizontalDragGestures(
                                    onDragStart = { horizontalDrag = 0f },
                                    onHorizontalDrag = { _, amount -> horizontalDrag += amount },
                                    onDragEnd = {
                                        if (horizontalDrag > 140) viewModel.previous()
                                        if (horizontalDrag < -140) viewModel.next()
                                        horizontalDrag = 0f
                                    },
                                )
                            },
                        seed = "${state.title}|${state.artist}",
                        elevated = true,
                    )
                }
                Spacer(Modifier.height(PolentitaSpacing.large))
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(PolentitaRadii.medium),
                    color = palette.surface.copy(alpha = 0.48f),
                    border = BorderStroke(
                        1.dp,
                        palette.accent.copy(alpha = 0.14f),
                    ),
                    tonalElevation = 0.dp,
                ) {
                    Column(
                        Modifier.padding(
                            horizontal = PolentitaSpacing.medium,
                            vertical = PolentitaSpacing.small,
                        ),
                    ) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            state.title,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            state.artist.ifBlank { stringResource(R.string.unknown_artist) },
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    IconButton(
                        onClick = viewModel::toggleFavorite,
                        enabled = !state.isPreview,
                        modifier = Modifier.semantics {
                            stateDescription = if (isFavorite) {
                                favoriteSavedDescription
                            } else {
                                favoriteNotSavedDescription
                            }
                        },
                    ) {
                        AnimatedContent(
                            targetState = isFavorite,
                            transitionSpec = {
                                (
                                    fadeIn(
                                        tween(
                                            animationDuration(
                                                animationsEnabled,
                                                PolentitaMotion.quick,
                                            ),
                                        ),
                                    ) + scaleIn(
                                        animationSpec = tween(
                                            animationDuration(
                                                animationsEnabled,
                                                PolentitaMotion.quick,
                                            ),
                                        ),
                                        initialScale = 0.78f,
                                    )
                                    ) togetherWith (
                                    fadeOut(
                                        tween(
                                            animationDuration(
                                                animationsEnabled,
                                                PolentitaMotion.quick,
                                            ),
                                        ),
                                    ) + scaleOut(
                                        animationSpec = tween(
                                            animationDuration(
                                                animationsEnabled,
                                                PolentitaMotion.quick,
                                            ),
                                        ),
                                        targetScale = 0.78f,
                                    )
                                    )
                            },
                            label = "Estado de favorita",
                        ) { favorite ->
                            Icon(
                                if (favorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                contentDescription = if (favorite) {
                                    stringResource(R.string.action_remove_favorite)
                                } else {
                                    stringResource(R.string.action_mark_favorite)
                                },
                                tint = if (favorite) {
                                    Color(0xFFFF4D67)
                                } else {
                                    MaterialTheme.colorScheme.onSurface
                                },
                            )
                        }
                    }
                }
                PlayerProgress(viewModel)
                    }
                }
                Spacer(Modifier.height(PolentitaSpacing.small))
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    FilledTonalIconButton(onClick = viewModel::toggleShuffle) {
                        Icon(
                            Icons.Default.Shuffle,
                            stringResource(
                                if (state.shuffle) R.string.shuffle_disable else R.string.shuffle_enable,
                            ),
                            tint = if (state.shuffle) {
                                palette.accent
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            },
                        )
                    }
                    IconButton(onClick = viewModel::previous, Modifier.size(56.dp)) {
                        Icon(
                            Icons.Default.SkipPrevious,
                            stringResource(R.string.previous_song_accessibility),
                            Modifier.size(34.dp),
                        )
                    }
                    FilledIconButton(
                        onClick = viewModel::togglePlayPause,
                        modifier = Modifier.size(72.dp).semantics {
                            stateDescription = playbackStatusDescription
                        },
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = palette.accent,
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                        ),
                    ) {
                        AnimatedContent(
                            targetState = state.isPlaying,
                            label = "Estado de reproducción",
                        ) { playing ->
                            Icon(
                                if (playing) Icons.Default.Pause else Icons.Default.PlayArrow,
                                stringResource(if (playing) R.string.pause else R.string.play),
                                Modifier.size(40.dp),
                            )
                        }
                    }
                    IconButton(onClick = viewModel::next, Modifier.size(56.dp)) {
                        Icon(
                            Icons.Default.SkipNext,
                            stringResource(R.string.player_next_song),
                            Modifier.size(34.dp),
                        )
                    }
                    FilledTonalIconButton(onClick = viewModel::cycleRepeat) {
                        Icon(
                            if (state.repeatMode == Player.REPEAT_MODE_ONE) {
                                Icons.Default.RepeatOne
                            } else {
                                Icons.Default.Repeat
                            },
                            when (state.repeatMode) {
                                Player.REPEAT_MODE_ONE -> stringResource(R.string.repeat_song)
                                Player.REPEAT_MODE_ALL -> stringResource(R.string.repeat_queue)
                                else -> stringResource(R.string.repeat_off)
                            },
                            tint = if (state.repeatMode != Player.REPEAT_MODE_OFF) {
                                palette.accent
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            },
                        )
                    }
                }
                Spacer(Modifier.height(PolentitaSpacing.large))
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(PolentitaSpacing.medium),
                ) {
                    FilledTonalButton(
                        onClick = onQueue,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(PolentitaRadii.pill),
                    ) {
                        Icon(Icons.Default.QueueMusic, null)
                        Spacer(Modifier.width(PolentitaSpacing.small))
                        Text(stringResource(R.string.queue))
                    }
                    FilledTonalButton(
                        onClick = {
                            context.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(PolentitaRadii.pill),
                    ) {
                        Icon(Icons.Default.Devices, null)
                        Spacer(Modifier.width(PolentitaSpacing.small))
                        Text(stringResource(R.string.audio_output))
                    }
                }
                Spacer(Modifier.height(PolentitaSpacing.small))
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(PolentitaRadii.medium),
                    color = MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.62f),
                    border = BorderStroke(
                        1.dp,
                        MaterialTheme.colorScheme.outlineVariant.copy(alpha = PolentitaOpacity.border),
                    ),
                    tonalElevation = 0.dp,
                ) {
                    Row(
                        Modifier.padding(horizontal = PolentitaSpacing.medium),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Default.VolumeDown, null, Modifier.size(20.dp))
                        Slider(
                            value = state.volume,
                            onValueChange = viewModel::setVolume,
                            valueRange = 0f..1f,
                            colors = SliderDefaults.colors(
                                thumbColor = palette.accent,
                                activeTrackColor = palette.accent,
                                inactiveTrackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.14f),
                            ),
                            modifier = Modifier.weight(1f).semantics {
                                contentDescription = playerVolumeDescription
                            },
                        )
                        Icon(Icons.Default.VolumeUp, null, Modifier.size(20.dp))
                    }
                }
                Spacer(Modifier.weight(0.06f))
            }
        }
    }
}

@Composable
private fun PlayerProgress(viewModel: PlayerViewModel) {
    val positionState by remember(viewModel) {
        viewModel.state.map { it.positionMs to it.durationMs }.distinctUntilChanged()
    }.collectAsStateWithLifecycle(
        initialValue = 0L to 0L,
    )
    val (position, duration) = positionState
    val positionDescription = stringResource(R.string.player_position)
    Slider(
        value = position.toFloat().coerceIn(0f, duration.coerceAtLeast(1).toFloat()),
        onValueChange = { viewModel.seek(it.toLong()) },
        valueRange = 0f..duration.coerceAtLeast(1).toFloat(),
        colors = SliderDefaults.colors(
            thumbColor = MaterialTheme.colorScheme.primary,
            activeTrackColor = MaterialTheme.colorScheme.primary,
            inactiveTrackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.14f),
        ),
        modifier = Modifier.fillMaxWidth().semantics {
            contentDescription = positionDescription
            stateDescription = "${formatDuration(position)} de ${formatDuration(duration)}"
        },
    )
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(formatDuration(position), style = MaterialTheme.typography.labelMedium)
        Text(formatDuration(duration), style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
fun QueueScreen(
    viewModel: PlayerViewModel,
    onBack: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val localizedContextLabel = localizedPlaybackContextLabel(state.contextLabel)
    val manualCount = state.queue.count { it.origin == PlaybackQueueOrigin.MANUAL }
    var saving by remember { mutableStateOf(false) }
    var confirmingClear by remember { mutableStateOf(false) }
    var draggedIndex by remember { mutableStateOf(-1) }
    val threshold = with(LocalDensity.current) { 48.dp.toPx() }
    Column(Modifier.fillMaxSize()) {
        Surface(
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.82f),
            border = BorderStroke(
                1.dp,
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = PolentitaOpacity.border),
            ),
            tonalElevation = 0.dp,
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = PolentitaSpacing.xs, vertical = PolentitaSpacing.small),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Default.KeyboardArrowDown, stringResource(R.string.back))
                }
                Column(Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.queue),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        stringResource(R.string.queue_manual_count, manualCount),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(
                    onClick = { confirmingClear = true },
                    enabled = manualCount > 0,
                ) {
                    Icon(Icons.Default.DeleteSweep, stringResource(R.string.clear_queue))
                }
                FilledTonalButton(
                    onClick = { saving = true },
                    enabled = manualCount > 0,
                    shape = RoundedCornerShape(PolentitaRadii.pill),
                ) {
                    Text(stringResource(R.string.save_queue_short))
                }
            }
        }
        if (state.queue.isEmpty()) {
            EmptyState(
                stringResource(R.string.queue_empty_title),
                stringResource(R.string.queue_empty_description),
            )
        } else {
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    top = PolentitaSpacing.small,
                    bottom = 112.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(PolentitaSpacing.xs),
            ) {
                itemsIndexed(state.queue, key = { index, item -> "${item.songId}-$index" }) { index, item ->
                    val manual = item.origin == PlaybackQueueOrigin.MANUAL
                    val previousOrigin = state.queue.getOrNull(index - 1)?.origin
                    var accumulated by remember { mutableFloatStateOf(0f) }
                    Column {
                        if (index == 0 || previousOrigin != item.origin) {
                            Text(
                                text = when (item.origin) {
                                    PlaybackQueueOrigin.MANUAL -> stringResource(R.string.queue_manual_section)
                                    PlaybackQueueOrigin.CONTEXT -> localizedContextLabel
                                        ?.let { stringResource(R.string.queue_context_section, it) }
                                        ?: stringResource(R.string.queue_automatic_section)
                                    PlaybackQueueOrigin.LIBRARY_FALLBACK ->
                                        stringResource(R.string.queue_library_section)
                                    PlaybackQueueOrigin.CURRENT -> stringResource(R.string.queue_automatic_section)
                                },
                                modifier = Modifier.padding(
                                    start = PolentitaSpacing.large,
                                    top = PolentitaSpacing.small,
                                    bottom = PolentitaSpacing.xs,
                                ),
                                style = MaterialTheme.typography.labelLarge,
                                color = if (manual) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                            )
                        }
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = PolentitaSpacing.small)
                                .then(if (manual) Modifier.pointerInput(item.songId, index) {
                                detectDragGesturesAfterLongPress(
                                    onDragStart = {
                                        accumulated = 0f
                                        draggedIndex = index
                                    },
                                    onDragEnd = {
                                        accumulated = 0f
                                        draggedIndex = -1
                                    },
                                    onDragCancel = {
                                        accumulated = 0f
                                        draggedIndex = -1
                                    },
                                    onDrag = { change, amount ->
                                        change.consume()
                                        accumulated += amount.y
                                        when {
                                            accumulated > threshold &&
                                                draggedIndex < state.queue.lastIndex &&
                                                state.queue[draggedIndex + 1].origin == PlaybackQueueOrigin.MANUAL -> {
                                                val target = draggedIndex + 1
                                                viewModel.moveQueueItem(draggedIndex, target)
                                                draggedIndex = target
                                                accumulated -= threshold
                                            }
                                            accumulated < -threshold && draggedIndex > 0 -> {
                                                val target = draggedIndex - 1
                                                viewModel.moveQueueItem(draggedIndex, target)
                                                draggedIndex = target
                                                accumulated += threshold
                                            }
                                        }
                                    },
                                )
                            } else Modifier),
                            shape = RoundedCornerShape(PolentitaRadii.medium),
                            color = MaterialTheme.colorScheme.surfaceContainerLow.copy(
                                alpha = if (draggedIndex == index) 0.94f else 0.62f,
                            ),
                            border = BorderStroke(
                                1.dp,
                                if (manual) {
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.24f)
                                } else {
                                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.20f)
                                },
                            ),
                        ) {
                        Row(
                            Modifier.padding(PolentitaSpacing.small),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                if (manual) Icons.Default.DragHandle else Icons.Default.QueueMusic,
                                if (manual) stringResource(R.string.drag_queue_item) else null,
                                tint = if (manual) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                            )
                            Spacer(Modifier.size(PolentitaSpacing.small))
                            Artwork(
                                item.artworkUri,
                                stringResource(R.string.home_cover_description, item.title),
                                Modifier.size(52.dp),
                            )
                            Spacer(Modifier.size(PolentitaSpacing.medium))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    item.title,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    fontWeight = FontWeight.Medium,
                                )
                                Text(
                                    item.artist,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                )
                            }
                            if (manual) {
                                IconButton(onClick = { viewModel.removeQueueItem(index) }) {
                                    Icon(Icons.Default.Close, stringResource(R.string.remove_from_queue))
                                }
                            }
                        }
                    }
                    }
                }
            }
        }
    }
    if (confirmingClear) {
        PolentitaAlertDialog(
            onDismissRequest = { confirmingClear = false },
            title = { Text(stringResource(R.string.clear_queue_title)) },
            text = { Text(stringResource(R.string.clear_queue_description)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.clearQueue()
                        confirmingClear = false
                    },
                ) { Text(stringResource(R.string.clear)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmingClear = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }
    if (saving) {
        TextFieldsDialog(
            title = stringResource(R.string.queue_save_title),
            firstLabel = stringResource(R.string.playlist_name),
            secondLabel = null,
            onDismiss = { saving = false },
            onSave = { name, _ ->
                if (name.isNotBlank()) viewModel.saveQueueAsPlaylist(name)
                saving = false
            },
        )
    }
}
