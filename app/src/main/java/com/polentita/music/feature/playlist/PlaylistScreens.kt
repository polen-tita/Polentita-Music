package com.polentita.music.feature.playlist

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlaylistAdd
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.polentita.music.R
import com.polentita.music.core.common.formatDuration
import com.polentita.music.core.database.PlaylistEntity
import com.polentita.music.core.designsystem.Artwork
import com.polentita.music.core.designsystem.AdaptiveArtworkBackground
import com.polentita.music.core.designsystem.ArtworkDynamicTheme
import com.polentita.music.core.designsystem.EmptyState
import com.polentita.music.core.designsystem.LoadingState
import com.polentita.music.core.designsystem.PolentitaOpacity
import com.polentita.music.core.designsystem.PolentitaAlertDialog
import com.polentita.music.core.designsystem.PolentitaRadii
import com.polentita.music.core.designsystem.PolentitaSpacing
import com.polentita.music.core.designsystem.rememberArtworkPalette
import com.polentita.music.core.designsystem.polentitaOutlinedTextFieldColors
import com.polentita.music.core.designsystem.SongActions
import com.polentita.music.core.designsystem.SongRow
import com.polentita.music.core.designsystem.activeSongVisualState
import com.polentita.music.domain.model.Song
import com.polentita.music.feature.library.LibraryViewModel
import com.polentita.music.feature.library.TextFieldsDialog
import com.polentita.music.feature.library.shareSong
import com.polentita.music.feature.player.PlayerViewModel
import com.polentita.music.playback.queue.PlaybackContextKind
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

enum class SmartPlaylistKind(val routeValue: String) {
    RECENT("recent"),
    MOST_PLAYED("most_played"),
}

@Composable
fun PlaylistsScreen(
    onPlaylist: (Long) -> Unit,
    onAutomaticPlaylist: (SmartPlaylistKind) -> Unit,
    onImportPlaylist: () -> Unit,
    viewModel: LibraryViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    if (state.loading) {
        LoadingState()
        return
    }
    var creating by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(
                    horizontal = PolentitaSpacing.large,
                    vertical = PolentitaSpacing.medium,
                ),
        ) {
            Column {
                Text(stringResource(R.string.playlists), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black)
                Text(
                    stringResource(R.string.playlists_subtitle),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Row(
                Modifier.fillMaxWidth().padding(top = PolentitaSpacing.small),
                horizontalArrangement = Arrangement.spacedBy(PolentitaSpacing.small),
            ) {
                OutlinedButton(
                    onClick = onImportPlaylist,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(PolentitaRadii.pill),
                    contentPadding = PaddingValues(horizontal = PolentitaSpacing.small, vertical = 8.dp),
                ) {
                    Icon(Icons.Default.PlaylistAdd, null)
                    Text(stringResource(R.string.playlist_import_action), maxLines = 1)
                }
                FilledTonalButton(
                    onClick = { creating = true },
                    shape = RoundedCornerShape(PolentitaRadii.pill),
                    contentPadding = PaddingValues(horizontal = PolentitaSpacing.medium, vertical = 8.dp),
                ) {
                    Icon(Icons.Default.Add, null)
                    Text(stringResource(R.string.new_item))
                }
            }
        }
        LazyColumn(
            contentPadding = PaddingValues(
                start = PolentitaSpacing.small,
                end = PolentitaSpacing.small,
                bottom = 112.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(PolentitaSpacing.xs),
        ) {
            item {
                AutomaticPlaylistRow(
                    title = stringResource(R.string.recent_playlist_title),
                    subtitle = stringResource(R.string.recent_playlist_subtitle),
                    icon = Icons.Default.History,
                    onOpen = { onAutomaticPlaylist(SmartPlaylistKind.RECENT) },
                )
            }
            item {
                AutomaticPlaylistRow(
                    title = stringResource(R.string.most_played),
                    subtitle = stringResource(R.string.most_played_playlist_subtitle),
                    icon = Icons.Default.TrendingUp,
                    onOpen = { onAutomaticPlaylist(SmartPlaylistKind.MOST_PLAYED) },
                )
            }
            if (state.playlists.isEmpty()) {
                item {
                    EmptyState(
                        stringResource(R.string.no_own_playlists),
                        stringResource(R.string.no_own_playlists_message),
                        Modifier.height(320.dp),
                    )
                }
            } else {
                items(state.playlists.size, key = { state.playlists[it].id }) { index ->
                    val playlist = state.playlists[index]
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .pointerInput(playlist.id) {
                                detectTapGestures(onTap = { onPlaylist(playlist.id) })
                            },
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(PolentitaRadii.medium),
                        color = MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.78f),
                        border = BorderStroke(
                            1.dp,
                            MaterialTheme.colorScheme.outlineVariant.copy(alpha = PolentitaOpacity.border),
                        ),
                    ) {
                        Row(
                            Modifier.padding(PolentitaSpacing.small),
                            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                        ) {
                            Artwork(
                                playlist.coverUri,
                                stringResource(R.string.home_playlist_cover_description, playlist.name),
                                Modifier.size(60.dp),
                            )
                            Spacer(Modifier.size(PolentitaSpacing.small))
                            Column(Modifier.weight(1f)) {
                                Text(playlist.name, fontWeight = FontWeight.Medium)
                                Text(
                                    stringResource(
                                        R.string.playlist_summary,
                                        playlist.songCount,
                                        formatDuration(playlist.durationMs),
                                    ),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Icon(
                                Icons.Default.PlayArrow,
                                stringResource(R.string.open_playlist),
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                }
            }
        }
    }
    if (creating) {
        TextFieldsDialog(
            title = stringResource(R.string.playlist_new),
            firstLabel = stringResource(R.string.field_name),
            secondLabel = stringResource(R.string.field_description),
            onDismiss = { creating = false },
            onSave = { name, description ->
                if (name.isNotBlank()) viewModel.createPlaylist(name, description)
                creating = false
            },
        )
    }
}

@Composable
private fun AutomaticPlaylistRow(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onOpen: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(PolentitaRadii.medium),
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
        border = BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.primary.copy(alpha = 0.18f),
        ),
        onClick = onOpen,
    ) {
        Row(
            Modifier.padding(horizontal = PolentitaSpacing.medium, vertical = PolentitaSpacing.small),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
        ) {
            Surface(
                modifier = Modifier.size(44.dp),
                shape = androidx.compose.foundation.shape.RoundedCornerShape(PolentitaRadii.small),
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
                contentColor = MaterialTheme.colorScheme.primary,
            ) {
                Icon(icon, null, Modifier.padding(10.dp))
            }
            Spacer(Modifier.size(PolentitaSpacing.small))
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.Medium)
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(
                Icons.Default.ChevronRight,
                contentDescription = stringResource(R.string.open_playlist),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
fun SmartPlaylistDetailScreen(
    kind: SmartPlaylistKind,
    player: PlayerViewModel,
    onEditSong: (Long) -> Unit,
    onDetails: (Long) -> Unit,
    onBack: () -> Unit,
    viewModel: LibraryViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    if (state.loading) {
        LoadingState()
        return
    }
    val context = LocalContext.current
    val songs = remember(state.songs, kind) {
        when (kind) {
            SmartPlaylistKind.RECENT -> state.songs
                .filter(Song::isAvailable)
                .sortedWith(compareByDescending<Song> { it.dateAdded }.thenBy { it.id })
                .take(100)
            SmartPlaylistKind.MOST_PLAYED -> state.songs
                .filter { it.isAvailable && it.playCount > 0 }
                .sortedWith(
                    compareByDescending<Song> { it.playCount }
                        .thenByDescending { it.lastPlayedAt ?: Long.MIN_VALUE }
                        .thenBy { it.id },
                )
        }
    }
    val title = when (kind) {
        SmartPlaylistKind.RECENT -> stringResource(R.string.recent_playlist_title)
        SmartPlaylistKind.MOST_PLAYED -> stringResource(R.string.most_played)
    }
    val subtitle = when (kind) {
        SmartPlaylistKind.RECENT -> stringResource(R.string.recent_playlist_subtitle)
        SmartPlaylistKind.MOST_PLAYED -> stringResource(R.string.most_played_playlist_subtitle)
    }
    val activePlayback by remember(player) {
        player.state.map { Triple(it.currentSongId, it.isPlaying, it.contextLabel) }.distinctUntilChanged()
    }.collectAsStateWithLifecycle(
        initialValue = Triple(null, false, null),
    )
    val contextActive = activePlayback.third == title && songs.any { it.id == activePlayback.first }
    LaunchedEffect(kind, songs, title) {
        player.updatePlaybackContext(
            songs = songs,
            kind = PlaybackContextKind.SMART_PLAYLIST,
            key = kind.routeValue,
            label = title,
        )
    }

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
                    Icon(Icons.Default.ArrowBack, contentDescription = stringResource(R.string.back))
                }
                Column(Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        Text(
            stringResource(R.string.song_count, songs.size),
            Modifier.padding(
                start = PolentitaSpacing.large,
                top = PolentitaSpacing.medium,
                end = PolentitaSpacing.large,
            ),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = PolentitaSpacing.medium, vertical = PolentitaSpacing.small),
            horizontalArrangement = Arrangement.spacedBy(PolentitaSpacing.small),
        ) {
            Button(
                onClick = {
                    if (contextActive) {
                        player.togglePlayPause()
                    } else {
                        player.playContext(
                            songs = songs,
                            kind = PlaybackContextKind.SMART_PLAYLIST,
                            key = kind.routeValue,
                            label = title,
                        )
                    }
                },
                enabled = songs.isNotEmpty(),
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(PolentitaRadii.pill),
            ) {
                Icon(
                    if (contextActive && activePlayback.second) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = null,
                )
                Text(
                    if (contextActive) stringResource(R.string.continue_playback)
                    else stringResource(R.string.play),
                )
            }
            FilledTonalButton(
                onClick = {
                    player.playContext(
                        songs = songs,
                        shuffle = true,
                        kind = PlaybackContextKind.SMART_PLAYLIST,
                        key = kind.routeValue,
                        label = title,
                    )
                },
                enabled = songs.isNotEmpty(),
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(PolentitaRadii.pill),
            ) {
                Icon(Icons.Default.Shuffle, contentDescription = null)
                Text(stringResource(R.string.home_quick_random))
            }
        }
        if (songs.isEmpty()) {
            val emptyMessage = when (kind) {
                SmartPlaylistKind.RECENT -> R.string.smart_playlist_empty_recent
                SmartPlaylistKind.MOST_PLAYED -> R.string.smart_playlist_empty_most_played
            }
            EmptyState(stringResource(R.string.smart_playlist_empty_title), stringResource(emptyMessage))
        } else {
            LazyColumn(
                contentPadding = PaddingValues(
                    start = PolentitaSpacing.small,
                    end = PolentitaSpacing.small,
                    bottom = 112.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(PolentitaSpacing.xs),
            ) {
                itemsIndexed(songs, key = { _, song -> song.id }) { _, song ->
                    SongRow(
                        song = song,
                        actions = SongActions(
                            play = {
                                player.playFromContext(
                                    song = song,
                                    songs = songs,
                                    kind = PlaybackContextKind.SMART_PLAYLIST,
                                    key = kind.routeValue,
                                    label = title,
                                )
                            },
                            playNext = { player.playNext(song) },
                            queue = { player.addToQueue(song) },
                            favorite = { viewModel.toggleFavorite(song.id) },
                            edit = { onEditSong(song.id) },
                            share = { shareSong(context, song) },
                            details = { onDetails(song.id) },
                        ),
                        activeState = activeSongVisualState(
                            song.id,
                            activePlayback.first,
                            activePlayback.second,
                        ),
                    )
                }
            }
        }
    }
}

@Composable
fun PlaylistDetailScreen(
    player: PlayerViewModel,
    onEditSong: (Long) -> Unit,
    onDetails: (Long) -> Unit,
    onBack: () -> Unit,
    viewModel: PlaylistViewModel = hiltViewModel(),
    libraryViewModel: LibraryViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var adding by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val defaultItemHeight = with(LocalDensity.current) { 72.dp.toPx() }
    val playlistListState = rememberLazyListState()
    val dragScrollScope = rememberCoroutineScope()
    var draggedSongId by remember { mutableStateOf<Long?>(null) }
    var draggedOffset by remember { mutableFloatStateOf(0f) }
    val latestSongs = rememberUpdatedState(state.songs)
    val activePlayback by remember(player) {
        player.state.map { Triple(it.currentSongId, it.isPlaying, it.contextLabel) }.distinctUntilChanged()
    }.collectAsStateWithLifecycle(
        initialValue = Triple(null, false, null),
    )
    if (state.loading) return LoadingState()
    val playlist = state.playlist ?: return EmptyState(
        stringResource(R.string.playlist_not_found),
        stringResource(R.string.playlist_missing),
    )
    LaunchedEffect(adding) {
        if (adding) viewModel.loadAllSongs()
    }
    LaunchedEffect(playlist.id, state.songs) {
        player.updatePlaybackContext(
            songs = state.songs,
            kind = PlaybackContextKind.PLAYLIST,
            key = playlist.id.toString(),
            label = playlist.name,
        )
    }
    val cover = playlist.coverUri ?: state.songs.firstNotNullOfOrNull(Song::coverUri)
    val contextActive = activePlayback.third == playlist.name && state.songs.any { it.id == activePlayback.first }
    val palette = rememberArtworkPalette(cover, playlist.name)
    val compactHeader by remember {
        derivedStateOf {
            playlistListState.firstVisibleItemIndex > 0 ||
                playlistListState.firstVisibleItemScrollOffset > 180
        }
    }

    ArtworkDynamicTheme(palette) {
        AdaptiveArtworkBackground(cover, palette) {
            Box(Modifier.fillMaxSize()) {
                LazyColumn(
                    state = playlistListState,
                    contentPadding = PaddingValues(bottom = 112.dp),
                ) {
                    item(key = "playlist-header") {
                        Column(Modifier.fillMaxWidth()) {
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .height(236.dp),
                            ) {
                                Artwork(
                                    cover,
                                    stringResource(R.string.home_playlist_cover_description, playlist.name),
                                    Modifier.fillMaxSize(),
                                    seed = playlist.name,
                                )
                                Box(
                                    Modifier
                                        .fillMaxSize()
                                        .background(
                                            Brush.verticalGradient(
                                                listOf(
                                                    Color.Black.copy(alpha = 0.08f),
                                                    Color.Transparent,
                                                    MaterialTheme.colorScheme.background.copy(alpha = 0.92f),
                                                ),
                                            ),
                                        ),
                                )
                                IconButton(
                                    onClick = onBack,
                                    modifier = Modifier
                                        .align(Alignment.TopStart)
                                        .padding(PolentitaSpacing.small)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.72f)),
                                ) {
                                    Icon(Icons.Default.ArrowBack, stringResource(R.string.back))
                                }
                            }
                            Column(
                                Modifier.padding(
                                    start = PolentitaSpacing.large,
                                    end = PolentitaSpacing.large,
                                    bottom = PolentitaSpacing.large,
                                ),
                            ) {
                                Text(
                                    playlist.name,
                                    style = MaterialTheme.typography.headlineMedium,
                                    fontWeight = FontWeight.Black,
                                    maxLines = 2,
                                )
                                if (playlist.description.isNotBlank()) {
                                    Text(
                                        playlist.description,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 2,
                                    )
                                }
                                Text(
                                    stringResource(
                                        R.string.playlist_summary,
                                        state.songs.size,
                                        formatDuration(state.durationMs),
                                    ),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Spacer(Modifier.height(PolentitaSpacing.large))
                                Row(
                                    Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(PolentitaSpacing.small),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Button(
                                        onClick = {
                                            if (contextActive) {
                                                player.togglePlayPause()
                                            } else {
                                                player.playContext(
                                                    songs = state.songs,
                                                    kind = PlaybackContextKind.PLAYLIST,
                                                    key = playlist.id.toString(),
                                                    label = playlist.name,
                                                )
                                            }
                                        },
                                        enabled = state.songs.isNotEmpty(),
                                        modifier = Modifier.weight(1f),
                                        shape = RoundedCornerShape(PolentitaRadii.pill),
                                        contentPadding = PaddingValues(horizontal = 12.dp),
                                    ) {
                                        Icon(
                                            if (contextActive && activePlayback.second) Icons.Default.Pause
                                            else Icons.Default.PlayArrow,
                                            null,
                                        )
                                        Text(
                                            if (contextActive) stringResource(R.string.continue_playback)
                                            else stringResource(R.string.play),
                                            maxLines = 1,
                                            softWrap = false,
                                        )
                                    }
                                    FilledTonalIconButton(
                                        onClick = {
                                            player.playContext(
                                                songs = state.songs,
                                                shuffle = true,
                                                kind = PlaybackContextKind.PLAYLIST,
                                                key = playlist.id.toString(),
                                                label = playlist.name,
                                            )
                                        },
                                        enabled = state.songs.isNotEmpty(),
                                    ) { Icon(Icons.Default.Shuffle, stringResource(R.string.random)) }
                                    FilledTonalIconButton(onClick = { adding = true }) {
                                        Icon(Icons.Default.Add, stringResource(R.string.playlist_add_songs))
                                    }
                                    FilledTonalIconButton(onClick = { editing = true }) {
                                        Icon(Icons.Default.Edit, stringResource(R.string.playlist_edit))
                                    }
                                    FilledTonalIconButton(onClick = { deleting = true }) {
                                        Icon(Icons.Default.Delete, stringResource(R.string.playlist_delete))
                                    }
                                }
                            }
                        }
                    }
                    if (state.songs.isEmpty()) {
                        item(key = "playlist-empty") {
                            Box(Modifier.fillMaxWidth().height(280.dp)) {
                                EmptyState(
                                    stringResource(R.string.playlist_empty),
                                    stringResource(R.string.playlist_empty_message),
                                )
                            }
                        }
                    } else {
                        itemsIndexed(state.songs, key = { _, song -> song.id }) { _, song ->
                            val isDragged = draggedSongId == song.id
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .zIndex(if (isDragged) 1f else 0f)
                                    .graphicsLayer {
                                        translationY = if (isDragged) draggedOffset else 0f
                                        shadowElevation = if (isDragged) 16.dp.toPx() else 0f
                                        scaleX = if (isDragged) 1.02f else 1f
                                        scaleY = if (isDragged) 1.02f else 1f
                                    }
                                    .background(
                                        MaterialTheme.colorScheme.surfaceContainerHigh.copy(
                                            alpha = if (isDragged) 0.98f else 0f,
                                        ),
                                    )
                                    .pointerInput(song.id) {
                                        detectDragGesturesAfterLongPress(
                                            onDragStart = {
                                                draggedSongId = song.id
                                                draggedOffset = 0f
                                            },
                                            onDragEnd = {
                                                draggedSongId = null
                                                draggedOffset = 0f
                                            },
                                            onDragCancel = {
                                                draggedSongId = null
                                                draggedOffset = 0f
                                            },
                                            onDrag = { change, dragAmount ->
                                                change.consume()
                                                draggedOffset += dragAmount.y
                                                val songs = latestSongs.value
                                                val currentIndex = songs.indexOfFirst { it.id == song.id }
                                                if (currentIndex < 0) return@detectDragGesturesAfterLongPress
                                                val currentItem = playlistListState.layoutInfo.visibleItemsInfo
                                                    .firstOrNull { it.key == song.id }
                                                val itemHeight = currentItem?.size?.toFloat()
                                                    ?: defaultItemHeight
                                                val halfItemHeight = itemHeight / 2f
                                                when {
                                                    draggedOffset > halfItemHeight &&
                                                        currentIndex < songs.lastIndex -> {
                                                        viewModel.move(song.id, direction = 1)
                                                        draggedOffset -= itemHeight
                                                    }
                                                    draggedOffset < -halfItemHeight && currentIndex > 0 -> {
                                                        viewModel.move(song.id, direction = -1)
                                                        draggedOffset += itemHeight
                                                    }
                                                }
                                                currentItem?.let { item ->
                                                    val viewport = playlistListState.layoutInfo
                                                    when {
                                                        item.offset + item.size + draggedOffset >
                                                            viewport.viewportEndOffset - 72 -> {
                                                            dragScrollScope.launch {
                                                                playlistListState.scrollBy(12f)
                                                            }
                                                        }
                                                        item.offset + draggedOffset <
                                                            viewport.viewportStartOffset + 72 -> {
                                                            dragScrollScope.launch {
                                                                playlistListState.scrollBy(-12f)
                                                            }
                                                        }
                                                    }
                                                }
                                            },
                                        )
                                    },
                            ) {
                                Icon(
                                    Icons.Default.DragHandle,
                                    stringResource(R.string.drag_queue_item),
                                    Modifier.padding(top = 22.dp),
                                )
                                SongRow(
                                    song = song,
                                    actions = SongActions(
                                        play = {
                                            player.playFromContext(
                                                song = song,
                                                songs = state.songs,
                                                kind = PlaybackContextKind.PLAYLIST,
                                                key = playlist.id.toString(),
                                                label = playlist.name,
                                            )
                                        },
                                        playNext = { player.playNext(song) },
                                        queue = { player.addToQueue(song) },
                                        removeFromPlaylist = { viewModel.remove(song.id) },
                                        edit = { onEditSong(song.id) },
                                        share = { shareSong(context, song) },
                                        details = { onDetails(song.id) },
                                        favorite = { libraryViewModel.toggleFavorite(song.id) },
                                    ),
                                    modifier = Modifier.weight(1f),
                                    activeState = activeSongVisualState(
                                        song.id,
                                        activePlayback.first,
                                        activePlayback.second,
                                    ),
                                )
                            }
                        }
                    }
                }
                AnimatedVisibility(
                    visible = compactHeader,
                    enter = fadeIn(),
                    exit = fadeOut(),
                    modifier = Modifier.align(Alignment.TopCenter),
                ) {
                    Surface(
                        color = palette.background.copy(alpha = 0.96f),
                        border = BorderStroke(
                            1.dp,
                            MaterialTheme.colorScheme.outlineVariant.copy(alpha = PolentitaOpacity.border),
                        ),
                        tonalElevation = 0.dp,
                    ) {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = PolentitaSpacing.xs, vertical = PolentitaSpacing.xs),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            IconButton(onClick = onBack) {
                                Icon(Icons.Default.ArrowBack, stringResource(R.string.back))
                            }
                            Artwork(
                                cover,
                                stringResource(R.string.home_playlist_cover_description, playlist.name),
                                Modifier.size(40.dp),
                                seed = playlist.name,
                            )
                            Spacer(Modifier.size(PolentitaSpacing.small))
                            Text(
                                playlist.name,
                                modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.titleMedium,
                                maxLines = 1,
                            )
                            IconButton(
                                onClick = {
                                    player.playContext(
                                        songs = state.songs,
                                        kind = PlaybackContextKind.PLAYLIST,
                                        key = playlist.id.toString(),
                                        label = playlist.name,
                                    )
                                },
                                enabled = state.songs.isNotEmpty(),
                            ) {
                                Icon(Icons.Default.PlayArrow, stringResource(R.string.play_playlist))
                            }
                        }
                    }
                }
            }
        }
    }

    if (adding) {
        val selected = remember { mutableStateListOf<Long>() }
        PolentitaAlertDialog(
            onDismissRequest = { adding = false },
            title = { Text(stringResource(R.string.playlist_add_songs)) },
            text = {
                LazyColumn(Modifier.height(360.dp)) {
                    items(state.allSongs.size, key = { state.allSongs[it].id }) { index ->
                        val song = state.allSongs[index]
                        Row(Modifier.fillMaxWidth()) {
                            Checkbox(
                                checked = song.id in selected,
                                onCheckedChange = { checked ->
                                    if (checked) selected.add(song.id) else selected.remove(song.id)
                                },
                            )
                            Text(song.title, Modifier.padding(top = 12.dp))
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { viewModel.addSongs(selected); adding = false }) { Text(stringResource(R.string.add)) }
            },
            dismissButton = { TextButton(onClick = { adding = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
    if (editing) {
        PlaylistEditDialog(
            playlist = playlist,
            onDismiss = { editing = false },
            onSave = { name, description, cover ->
                viewModel.update(name, description, cover)
                editing = false
            },
        )
    }
    if (deleting) {
        PolentitaAlertDialog(
            onDismissRequest = { deleting = false },
            title = { Text(stringResource(R.string.playlist_delete)) },
            text = { Text(stringResource(R.string.playlist_delete_message)) },
            confirmButton = {
                TextButton(onClick = { viewModel.delete(); deleting = false; onBack() }) {
                    Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { deleting = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

@Composable
private fun PlaylistEditDialog(
    playlist: PlaylistEntity,
    onDismiss: () -> Unit,
    onSave: (String, String, String?) -> Unit,
) {
    var name by remember { mutableStateOf(playlist.name) }
    var description by remember { mutableStateOf(playlist.description) }
    var cover by remember { mutableStateOf(playlist.coverUri) }
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        runCatching {
            context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        cover = uri.toString()
    }
    PolentitaAlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text(
                    stringResource(R.string.playlist_customize),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(stringResource(R.string.playlist_edit), style = MaterialTheme.typography.headlineSmall)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(PolentitaSpacing.medium)) {
                Artwork(
                    cover,
                    stringResource(R.string.playlist_cover_description),
                    Modifier.fillMaxWidth().height(152.dp),
                    seed = playlist.name,
                )
                TextButton(
                    onClick = { launcher.launch(arrayOf("image/*")) },
                    modifier = Modifier.align(Alignment.End),
                ) { Text(stringResource(R.string.artwork_change)) }
                androidx.compose.material3.OutlinedTextField(
                    name,
                    { name = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.field_name)) },
                    shape = RoundedCornerShape(PolentitaRadii.medium),
                    colors = polentitaOutlinedTextFieldColors(),
                )
                androidx.compose.material3.OutlinedTextField(
                    description,
                    { description = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.field_description)) },
                    shape = RoundedCornerShape(PolentitaRadii.medium),
                    colors = polentitaOutlinedTextFieldColors(),
                )
            }
        },
        confirmButton = { Button(onClick = { onSave(name, description, cover) }) { Text(stringResource(R.string.save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}
