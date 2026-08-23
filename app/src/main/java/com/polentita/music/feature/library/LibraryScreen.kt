package com.polentita.music.feature.library

import android.content.ClipData
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.res.stringResource
import com.polentita.music.R
import com.polentita.music.core.designsystem.Artwork
import com.polentita.music.core.designsystem.CompactGridMenuButton
import com.polentita.music.core.designsystem.CompactSearchField
import com.polentita.music.core.designsystem.EmptyState
import com.polentita.music.core.designsystem.ErrorState
import com.polentita.music.core.designsystem.LoadingState
import com.polentita.music.core.designsystem.PolentitaMotion
import com.polentita.music.core.designsystem.PolentitaOpacity
import com.polentita.music.core.designsystem.PolentitaAlertDialog
import com.polentita.music.core.designsystem.PolentitaDropdownMenu
import com.polentita.music.core.designsystem.PolentitaRadii
import com.polentita.music.core.designsystem.PolentitaSpacing
import com.polentita.music.core.designsystem.SongActions
import com.polentita.music.core.designsystem.SongGridCard
import com.polentita.music.core.designsystem.SongRow
import com.polentita.music.core.designsystem.activeSongVisualState
import com.polentita.music.core.designsystem.polentitaOutlinedTextFieldColors
import com.polentita.music.core.database.AlbumEntity
import com.polentita.music.domain.model.Song
import com.polentita.music.domain.model.SongSort
import com.polentita.music.feature.editor.AlbumEditDialog
import com.polentita.music.feature.player.PlayerViewModel
import com.polentita.music.playback.queue.PlaybackContextKind
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun FolderSetupScreen(
    viewModel: LibraryViewModel,
    onLinked: () -> Unit,
) {
    val context = LocalContext.current
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        runCatching {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
        }
        viewModel.linkLibrary(uri, onLinked)
    }
    Box(Modifier.fillMaxSize().padding(28.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Default.Album, null, Modifier.size(88.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(18.dp))
            Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Black)
            Text(
                stringResource(R.string.folder_setup_description),
                Modifier.padding(vertical = 18.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(onClick = { launcher.launch(null) }, enabled = !state.busy) {
                Icon(Icons.Default.Folder, null)
                Spacer(Modifier.size(8.dp))
                Text(stringResource(R.string.library_choose_folder))
            }
            if (state.busy) CircularProgressIndicator(Modifier.padding(16.dp))
            state.error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(12.dp)) }
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun LibraryScreen(
    playerViewModel: PlayerViewModel,
    onImport: () -> Unit,
    onDownload: () -> Unit,
    onAlbum: (Long) -> Unit,
    onArtist: (String) -> Unit,
    onEditSong: (Long) -> Unit,
    onDetails: (Long) -> Unit,
    viewModel: LibraryViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(state.songs) {
        playerViewModel.updateLibraryContext(state.songs)
    }
    var tab by remember { mutableIntStateOf(0) }
    var addPlaylistSong by remember { mutableStateOf<Song?>(null) }
    var removingSong by remember { mutableStateOf<Song?>(null) }
    var createAlbum by remember { mutableStateOf(false) }
    var editingAlbum by remember { mutableStateOf<AlbumEntity?>(null) }
    var deletingAlbum by remember { mutableStateOf<AlbumEntity?>(null) }
    var albumMenuId by remember { mutableStateOf<Long?>(null) }
    var artistMenuName by remember { mutableStateOf<String?>(null) }
    var editingArtist by remember { mutableStateOf<String?>(null) }
    var deletingArtist by remember { mutableStateOf<String?>(null) }
    var confirmCleanAlbums by remember { mutableStateOf(false) }
    var utilityMenuExpanded by remember { mutableStateOf(false) }
    var sortExpanded by remember { mutableStateOf(false) }
    val songListState = rememberLazyListState()
    val songGridState = rememberLazyGridState()
    var pendingScrollPosition by remember { mutableStateOf<LibraryScrollPosition?>(null) }
    val captureSongScrollPosition = {
        pendingScrollPosition = if (state.gridMode) {
            LibraryScrollPosition(
                songGridState.firstVisibleItemIndex,
                songGridState.firstVisibleItemScrollOffset,
            )
        } else {
            LibraryScrollPosition(
                songListState.firstVisibleItemIndex,
                songListState.firstVisibleItemScrollOffset,
            )
        }
    }
    LaunchedEffect(state.sort, state.ascending, pendingScrollPosition) {
        val target = pendingScrollPosition ?: return@LaunchedEffect
        if (state.songs.isNotEmpty()) {
            val safeIndex = target.index.coerceIn(0, state.songs.lastIndex)
            if (state.gridMode) {
                songGridState.requestScrollToItem(safeIndex, target.offset)
            } else {
                songListState.requestScrollToItem(safeIndex, target.offset)
            }
        }
        pendingScrollPosition = null
    }
    val songsByArtist = remember(state.songs) { state.songs.groupBy(Song::artist) }
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current
    val activePlayback by remember(playerViewModel) {
        playerViewModel.state.map { it.currentSongId to it.isPlaying }.distinctUntilChanged()
    }.collectAsStateWithLifecycle(
        initialValue = null to false,
    )

    LaunchedEffect(state.message, state.error) {
        val message = state.message ?: state.error ?: return@LaunchedEffect
        val timeout = launch {
            delay(3_000)
            snackbar.currentSnackbarData?.dismiss()
        }
        try {
            snackbar.showSnackbar(
                message,
                duration = SnackbarDuration.Indefinite,
                withDismissAction = true,
            )
        } finally {
            timeout.cancel()
            snackbar.currentSnackbarData?.dismiss()
            viewModel.clearMessage()
        }
    }

    Box(Modifier.fillMaxSize()) {
        Column {
            Surface(
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.78f),
                border = BorderStroke(
                    1.dp,
                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = PolentitaOpacity.border),
                ),
                tonalElevation = 0.dp,
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = PolentitaSpacing.medium, vertical = PolentitaSpacing.small),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CompactSearchField(
                        value = state.query,
                        onValueChange = viewModel::setQuery,
                        hint = stringResource(R.string.search_library_hint),
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(PolentitaSpacing.xs))
                    IconButton(onClick = onDownload) {
                        Icon(Icons.Default.Download, contentDescription = stringResource(R.string.download))
                    }
                    Box {
                        IconButton(onClick = { utilityMenuExpanded = true }) {
                            Icon(Icons.Default.MoreVert, stringResource(R.string.library_more_options))
                        }
                        PolentitaDropdownMenu(
                            expanded = utilityMenuExpanded,
                            onDismissRequest = { utilityMenuExpanded = false },
                        ) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.import_files)) },
                                leadingIcon = { Icon(Icons.Default.Folder, contentDescription = null) },
                                onClick = {
                                    utilityMenuExpanded = false
                                    onImport()
                                },
                            )
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        if (state.gridMode) {
                                            stringResource(R.string.show_list)
                                        } else {
                                            stringResource(R.string.show_grid)
                                        },
                                    )
                                },
                                leadingIcon = {
                                    Icon(
                                        if (state.gridMode) Icons.Default.ViewList else Icons.Default.GridView,
                                        contentDescription = null,
                                    )
                                },
                                onClick = {
                                    viewModel.toggleGridMode()
                                    utilityMenuExpanded = false
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.rescan_library)) },
                                leadingIcon = { Icon(Icons.Default.Refresh, contentDescription = null) },
                                enabled = !state.busy,
                                onClick = {
                                    utilityMenuExpanded = false
                                    viewModel.scanLibrary()
                                },
                            )
                        }
                    }
                }
            }
            LibrarySummaryRow(
                state = state,
                showSort = tab == 0,
                sortExpanded = sortExpanded,
                onSortExpandedChange = { sortExpanded = it },
                onSort = { sort ->
                    captureSongScrollPosition()
                    viewModel.setSort(sort)
                },
                onToggleAscending = {
                    captureSongScrollPosition()
                    viewModel.toggleAscending()
                },
            )
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = PolentitaSpacing.medium, vertical = PolentitaSpacing.xs),
                shape = RoundedCornerShape(PolentitaRadii.medium),
                color = MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.78f),
                border = BorderStroke(
                    1.dp,
                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = PolentitaOpacity.border),
                ),
            ) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(PolentitaSpacing.xs)
                        .height(40.dp),
                ) {
                    listOf(
                        stringResource(R.string.songs_tab),
                        stringResource(R.string.albums_tab),
                        stringResource(R.string.artists_tab),
                    ).forEachIndexed { index, label ->
                        val selected = tab == index
                        val segmentColor by animateColorAsState(
                            targetValue = if (selected) {
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
                            } else {
                                Color.Transparent
                            },
                            animationSpec = tween(PolentitaMotion.quick),
                            label = "Indicador de pestaña",
                        )
                        Tab(
                            selected = selected,
                            onClick = { tab = index },
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(PolentitaRadii.small))
                                .background(segmentColor),
                            selectedContentColor = MaterialTheme.colorScheme.primary,
                            unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            text = { Text(label, maxLines = 1) },
                        )
                    }
                }
            }
            when {
                state.loading -> LoadingState()
                state.error != null && state.songs.isEmpty() -> ErrorState(state.error.orEmpty())
                tab == 0 -> Column {
                    if (state.songs.isEmpty()) {
                        EmptyState(
                            stringResource(R.string.library_empty_title),
                            stringResource(R.string.library_empty_message),
                        )
                    } else {
                        Crossfade(
                            targetState = state.gridMode,
                            animationSpec = tween(PolentitaMotion.quick),
                            label = "Vista de canciones",
                        ) { showGrid ->
                            if (showGrid) {
                                LazyVerticalGrid(
                                    state = songGridState,
                                    columns = GridCells.Fixed(3),
                                    contentPadding = PaddingValues(
                                        start = PolentitaSpacing.medium,
                                        top = PolentitaSpacing.medium,
                                        end = PolentitaSpacing.medium,
                                        bottom = 112.dp,
                                    ),
                                    horizontalArrangement = Arrangement.spacedBy(PolentitaSpacing.medium),
                                    verticalArrangement = Arrangement.spacedBy(PolentitaSpacing.large),
                                ) {
                                    items(state.songs, key = Song::id) { song ->
                                        SongGridCard(
                                            song = song,
                                            actions = librarySongActions(
                                                song = song,
                                                songs = state.songs,
                                                player = playerViewModel,
                                                viewModel = viewModel,
                                                context = context,
                                                contextLabel = stringResource(R.string.library),
                                                addToPlaylist = { addPlaylistSong = it },
                                                edit = onEditSong,
                                                details = onDetails,
                                                remove = { removingSong = it },
                                            ),
                                            activeState = activeSongVisualState(
                                                song.id,
                                                activePlayback.first,
                                                activePlayback.second,
                                            ),
                                        )
                                    }
                                }
                            } else {
                                LazyColumn(
                                    state = songListState,
                                    contentPadding = PaddingValues(
                                        start = PolentitaSpacing.small,
                                        end = PolentitaSpacing.small,
                                        bottom = 112.dp,
                                    ),
                                    verticalArrangement = Arrangement.spacedBy(PolentitaSpacing.xs),
                                ) {
                                    items(state.songs, key = Song::id) { song ->
                                        SongRow(
                                            song = song,
                                            actions = librarySongActions(
                                                song = song,
                                                songs = state.songs,
                                                player = playerViewModel,
                                                viewModel = viewModel,
                                                context = context,
                                                contextLabel = stringResource(R.string.library),
                                                addToPlaylist = { addPlaylistSong = it },
                                                edit = onEditSong,
                                                details = onDetails,
                                                remove = { removingSong = it },
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
                }
                tab == 1 -> Column {
                    Row(
                        Modifier.fillMaxWidth().padding(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                    ) {
                        OutlinedButton(onClick = { confirmCleanAlbums = true }) {
                            Text(stringResource(R.string.clean_empty_albums))
                        }
                        FilledTonalButton(onClick = { createAlbum = true }) {
                            Icon(Icons.Default.Add, null)
                            Text(stringResource(R.string.create_album))
                        }
                    }
                    if (state.albums.isEmpty()) {
                        EmptyState(
                            stringResource(R.string.no_albums),
                            stringResource(R.string.no_albums_message),
                        )
                    } else {
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(3),
                            contentPadding = PaddingValues(
                                PolentitaSpacing.medium,
                                0.dp,
                                PolentitaSpacing.medium,
                                112.dp,
                            ),
                            horizontalArrangement = Arrangement.spacedBy(PolentitaSpacing.medium),
                            verticalArrangement = Arrangement.spacedBy(PolentitaSpacing.xl),
                        ) {
                            items(state.albums, key = { it.id }) { album ->
                                Box {
                                    Column(
                                        Modifier
                                            .clip(RoundedCornerShape(PolentitaRadii.medium))
                                            .background(
                                                MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.34f),
                                                RoundedCornerShape(PolentitaRadii.medium),
                                            )
                                            .border(
                                                1.dp,
                                                MaterialTheme.colorScheme.outlineVariant.copy(alpha = PolentitaOpacity.border),
                                                RoundedCornerShape(PolentitaRadii.medium),
                                            )
                                            .clickable { onAlbum(album.id) },
                                    ) {
                                        Artwork(
                                            album.coverUri,
                                            stringResource(R.string.home_album_cover_description, album.name),
                                            Modifier.fillMaxWidth().aspectRatio(1f),
                                            seed = "${album.name}|${album.artist}",
                                        )
                                        Spacer(Modifier.height(PolentitaSpacing.small))
                                        Text(
                                            album.name,
                                            modifier = Modifier.padding(horizontal = PolentitaSpacing.xxs),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            fontWeight = FontWeight.Medium,
                                        )
                                        Text(
                                            album.artist,
                                            modifier = Modifier.padding(horizontal = PolentitaSpacing.xxs),
                                            maxLines = 1,
                                            style = MaterialTheme.typography.bodySmall,
                                        )
                                    }
                                    Box(Modifier.align(Alignment.TopEnd)) {
                                        CompactGridMenuButton(
                                            contentDescription = stringResource(R.string.more_options_for, album.name),
                                            onClick = { albumMenuId = album.id },
                                        )
                                        PolentitaDropdownMenu(
                                            expanded = albumMenuId == album.id,
                                            onDismissRequest = { albumMenuId = null },
                                        ) {
                                            DropdownMenuItem(
                                                text = { Text(stringResource(R.string.edit_album)) },
                                                leadingIcon = {
                                                    Icon(Icons.Default.Edit, contentDescription = null)
                                                },
                                                onClick = {
                                                    albumMenuId = null
                                                    viewModel.clearMessage()
                                                    editingAlbum = album
                                                },
                                            )
                                            DropdownMenuItem(
                                                text = { Text(stringResource(R.string.delete)) },
                                                leadingIcon = {
                                                    Icon(Icons.Default.Delete, contentDescription = null)
                                                },
                                                onClick = {
                                                    albumMenuId = null
                                                    deletingAlbum = album
                                                },
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                else -> {
                    if (state.artists.isEmpty()) {
                        EmptyState(
                            stringResource(R.string.no_artists),
                            stringResource(R.string.no_artists_message),
                        )
                    } else {
                        Crossfade(
                            targetState = state.gridMode,
                            animationSpec = tween(PolentitaMotion.quick),
                            label = "Vista de artistas",
                        ) { showGrid ->
                            if (showGrid) {
                                LazyVerticalGrid(
                                    columns = GridCells.Fixed(2),
                                    contentPadding = PaddingValues(
                                        PolentitaSpacing.medium,
                                        PolentitaSpacing.medium,
                                        PolentitaSpacing.medium,
                                        112.dp,
                                    ),
                                    horizontalArrangement = Arrangement.spacedBy(PolentitaSpacing.medium),
                                    verticalArrangement = Arrangement.spacedBy(PolentitaSpacing.xl),
                                ) {
                                    items(state.artists, key = { it }) { artist ->
                                        val artistSongs = songsByArtist[artist].orEmpty()
                                        Box {
                                            Column(
                                                Modifier
                                                    .clip(RoundedCornerShape(PolentitaRadii.medium))
                                                    .background(
                                                        MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.34f),
                                                        RoundedCornerShape(PolentitaRadii.medium),
                                                    )
                                                    .border(
                                                        1.dp,
                                                        MaterialTheme.colorScheme.outlineVariant.copy(alpha = PolentitaOpacity.border),
                                                        RoundedCornerShape(PolentitaRadii.medium),
                                                    )
                                                    .clickable { onArtist(artist) },
                                            ) {
                                                Artwork(
                                                    artistSongs.firstNotNullOfOrNull(Song::coverUri),
                                                    stringResource(R.string.artwork_image_of, artist),
                                                    Modifier.fillMaxWidth().aspectRatio(1f),
                                                    seed = artist,
                                                )
                                                Spacer(Modifier.height(PolentitaSpacing.small))
                                                Text(
                                                    artist.ifBlank { stringResource(R.string.unknown_artist) },
                                                    modifier = Modifier.padding(horizontal = PolentitaSpacing.xxs),
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                    style = MaterialTheme.typography.titleMedium,
                                                )
                                                Text(
                                                    stringResource(R.string.song_count, artistSongs.size),
                                                    style = MaterialTheme.typography.bodySmall,
                                                )
                                            }
                                            ArtistOptionsMenu(
                                                artist = artist,
                                                expanded = artistMenuName == artist,
                                                onExpandedChange = { expanded ->
                                                    artistMenuName = artist.takeIf { expanded }
                                                },
                                                onEdit = {
                                                    artistMenuName = null
                                                    editingArtist = artist
                                                },
                                                onDelete = {
                                                    artistMenuName = null
                                                    deletingArtist = artist
                                                },
                                                modifier = Modifier.align(Alignment.TopEnd),
                                            )
                                        }
                                    }
                                }
                            } else {
                                LazyColumn(
                                    contentPadding = PaddingValues(bottom = 112.dp),
                                    verticalArrangement = Arrangement.spacedBy(PolentitaSpacing.xs),
                                ) {
                                    items(state.artists, key = { it }) { artist ->
                                        val artistSongs = songsByArtist[artist].orEmpty()
                                        Surface(
                                            modifier = Modifier
                                                .padding(horizontal = PolentitaSpacing.small)
                                                .fillMaxWidth()
                                                .clickable { onArtist(artist) },
                                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.32f),
                                            shape = RoundedCornerShape(PolentitaRadii.medium),
                                            border = BorderStroke(
                                                1.dp,
                                                MaterialTheme.colorScheme.outlineVariant.copy(alpha = PolentitaOpacity.border),
                                            ),
                                        ) {
                                            Row(
                                                Modifier.padding(PolentitaSpacing.small),
                                                verticalAlignment = Alignment.CenterVertically,
                                            ) {
                                                Artwork(
                                                    artistSongs.firstNotNullOfOrNull(Song::coverUri),
                                                    stringResource(R.string.artwork_image_of, artist),
                                                    Modifier.size(64.dp),
                                                    seed = artist,
                                                )
                                                Spacer(Modifier.size(PolentitaSpacing.medium))
                                                Column(Modifier.weight(1f)) {
                                                    Text(
                                                        artist.ifBlank { stringResource(R.string.unknown_artist) },
                                                        modifier = Modifier.padding(horizontal = PolentitaSpacing.xxs),
                                                        style = MaterialTheme.typography.titleMedium,
                                                    )
                                                    Text(
                                                        stringResource(R.string.song_count, artistSongs.size),
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    )
                                                }
                                                ArtistOptionsMenu(
                                                    artist = artist,
                                                    expanded = artistMenuName == artist,
                                                    onExpandedChange = { expanded ->
                                                        artistMenuName = artist.takeIf { expanded }
                                                    },
                                                    onEdit = {
                                                        artistMenuName = null
                                                        editingArtist = artist
                                                    },
                                                    onDelete = {
                                                        artistMenuName = null
                                                        deletingArtist = artist
                                                    },
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        SnackbarHost(
            snackbar,
            Modifier.align(Alignment.BottomCenter).padding(bottom = 96.dp),
            snackbar = { data ->
                Snackbar(
                    snackbarData = data,
                    containerColor = MaterialTheme.colorScheme.inverseSurface.copy(alpha = 0.78f),
                    contentColor = MaterialTheme.colorScheme.inverseOnSurface,
                    actionColor = MaterialTheme.colorScheme.inversePrimary,
                    dismissActionContentColor = MaterialTheme.colorScheme.inverseOnSurface,
                )
            },
        )
    }

    addPlaylistSong?.let { song ->
        PolentitaAlertDialog(
            onDismissRequest = { addPlaylistSong = null },
            title = { Text(stringResource(R.string.add_to_playlist_title)) },
            text = {
                if (state.playlists.isEmpty()) {
                    Text(stringResource(R.string.no_playlists_message))
                } else {
                    Column {
                        state.playlists.forEach { playlist ->
                            TextButton(
                                onClick = {
                                    viewModel.addToPlaylist(playlist.id, song.id)
                                    addPlaylistSong = null
                                },
                                modifier = Modifier.fillMaxWidth(),
                            ) { Text(playlist.name) }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { addPlaylistSong = null }) { Text(stringResource(R.string.close)) } },
        )
    }
    removingSong?.let { song ->
        PolentitaAlertDialog(
            onDismissRequest = { removingSong = null },
            title = { Text(stringResource(R.string.remove_song_title, song.title)) },
            text = {
                Text(stringResource(R.string.remove_song_message))
            },
            confirmButton = {
                TextButton(onClick = { viewModel.removeSong(song.id, false); removingSong = null }) {
                    Text(stringResource(R.string.remove_song_from_library))
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.removeSong(song.id, true); removingSong = null }) {
                    Text(stringResource(R.string.delete_file_too), color = MaterialTheme.colorScheme.error)
                }
            },
        )
    }
    if (createAlbum) {
        TextFieldsDialog(
            title = stringResource(R.string.create_album),
            firstLabel = stringResource(R.string.field_name),
            secondLabel = stringResource(R.string.field_artist),
            onDismiss = { createAlbum = false },
            onSave = { name, artist ->
                if (name.isNotBlank()) viewModel.createAlbum(name, artist, null, null)
                createAlbum = false
            },
        )
    }
    editingAlbum?.let { album ->
        AlbumEditDialog(
            album = album,
            displayCoverUri = album.coverUri ?: state.songs
                .firstOrNull { it.albumId == album.id && !it.coverUri.isNullOrBlank() }
                ?.coverUri,
            saving = state.busy,
            errorMessage = state.error,
            onDismiss = { editingAlbum = null },
            onSave = { updated, artworkChoice ->
                viewModel.updateAlbum(updated, artworkChoice) { editingAlbum = null }
            },
        )
    }
    deletingAlbum?.let { album ->
        PolentitaAlertDialog(
            onDismissRequest = { deletingAlbum = null },
            title = { Text(stringResource(R.string.delete_album_title)) },
            text = { Text(stringResource(R.string.delete_album_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteAlbum(album)
                        deletingAlbum = null
                    },
                ) {
                    Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { deletingAlbum = null }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }
    editingArtist?.let { artist ->
        TextFieldsDialog(
            title = stringResource(R.string.edit_artist),
            firstLabel = stringResource(R.string.field_artist),
            secondLabel = null,
            initialFirst = artist,
            onDismiss = { editingArtist = null },
            onSave = { newName, _ ->
                viewModel.renameArtist(artist, newName)
                editingArtist = null
            },
        )
    }
    deletingArtist?.let { artist ->
        PolentitaAlertDialog(
            onDismissRequest = { deletingArtist = null },
            title = { Text(stringResource(R.string.delete_artist_title)) },
            text = { Text(stringResource(R.string.delete_artist_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteArtist(artist)
                        deletingArtist = null
                    },
                ) {
                    Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { deletingArtist = null }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }
    if (confirmCleanAlbums) {
        PolentitaAlertDialog(
            onDismissRequest = { confirmCleanAlbums = false },
            title = { Text(stringResource(R.string.clean_albums_title)) },
            text = { Text(stringResource(R.string.clean_albums_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.cleanEmptyAlbums()
                        confirmCleanAlbums = false
                    },
                ) { Text(stringResource(R.string.clean)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmCleanAlbums = false }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }
}

@Composable
private fun ArtistOptionsMenu(
    artist: String,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier) {
        CompactGridMenuButton(
            contentDescription = stringResource(R.string.artist_options, artist),
            onClick = { onExpandedChange(true) },
        )
        PolentitaDropdownMenu(
            expanded = expanded,
            onDismissRequest = { onExpandedChange(false) },
        ) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.edit_artist)) },
                leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                onClick = {
                    onEdit()
                    onExpandedChange(false)
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.delete)) },
                leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null) },
                onClick = {
                    onDelete()
                    onExpandedChange(false)
                },
            )
        }
    }
}

@Composable
private fun LibrarySummaryRow(
    state: LibraryUiState,
    showSort: Boolean,
    sortExpanded: Boolean,
    onSortExpandedChange: (Boolean) -> Unit,
    onSort: (SongSort) -> Unit,
    onToggleAscending: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(
                start = PolentitaSpacing.large,
                top = 8.dp,
                end = PolentitaSpacing.large,
                bottom = 4.dp,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            stringResource(R.string.song_count, state.songs.size),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
        if (showSort) {
            Spacer(Modifier.weight(1f))
            Box {
                Text(
                    text = stringResource(R.string.sort_order, state.sort.localizedLabel()),
                    modifier = Modifier
                        .clip(RoundedCornerShape(PolentitaRadii.pill))
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.72f))
                        .border(
                            1.dp,
                            MaterialTheme.colorScheme.outlineVariant.copy(alpha = PolentitaOpacity.border),
                            RoundedCornerShape(PolentitaRadii.pill),
                        )
                        .clickable { onSortExpandedChange(true) }
                        .padding(horizontal = PolentitaSpacing.small, vertical = 8.dp),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
                PolentitaDropdownMenu(
                    expanded = sortExpanded,
                    onDismissRequest = { onSortExpandedChange(false) },
                ) {
                    SongSort.entries.forEach { sort ->
                        DropdownMenuItem(
                            text = { Text(sort.localizedLabel()) },
                            onClick = {
                                onSort(sort)
                                onSortExpandedChange(false)
                            },
                        )
                    }
                }
            }
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.72f))
                    .border(
                        1.dp,
                        MaterialTheme.colorScheme.outlineVariant.copy(alpha = PolentitaOpacity.border),
                        CircleShape,
                    )
                    .clickable(onClick = onToggleAscending),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    if (state.ascending) Icons.Default.ArrowUpward else Icons.Default.ArrowDownward,
                    modifier = Modifier.size(22.dp),
                    contentDescription = stringResource(
                        if (state.ascending) R.string.sort_ascending else R.string.sort_descending,
                    ),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun SongSort.localizedLabel() = when (this) {
    SongSort.TITLE -> stringResource(R.string.field_title)
    SongSort.ARTIST -> stringResource(R.string.field_artist)
    SongSort.ALBUM -> stringResource(R.string.field_album)
    SongSort.DATE_ADDED -> stringResource(R.string.sort_date_added)
    SongSort.DATE_MODIFIED -> stringResource(R.string.sort_date_modified)
    SongSort.DURATION -> stringResource(R.string.technical_duration)
    SongSort.PLAY_COUNT -> stringResource(R.string.technical_play_count)
    SongSort.LAST_PLAYED -> stringResource(R.string.sort_last_played)
}

private data class LibraryScrollPosition(
    val index: Int,
    val offset: Int,
)

private fun librarySongActions(
    song: Song,
    songs: List<Song>,
    player: PlayerViewModel,
    viewModel: LibraryViewModel,
    context: android.content.Context,
    contextLabel: String,
    addToPlaylist: (Song) -> Unit,
    edit: (Long) -> Unit,
    details: (Long) -> Unit,
    remove: (Song) -> Unit,
): SongActions = SongActions(
    play = {
        player.playFromContext(
            song = song,
            songs = songs,
            kind = PlaybackContextKind.LIBRARY,
            key = "library",
            label = contextLabel,
        )
    },
    playNext = { player.playNext(song) },
    queue = { player.addToQueue(song) },
    addToPlaylist = { addToPlaylist(song) },
    favorite = { viewModel.toggleFavorite(song.id) },
    edit = { edit(song.id) },
    share = { shareSong(context, song) },
    details = { details(song.id) },
    remove = { remove(song) },
)

@Composable
fun ImportScreen(
    onBack: () -> Unit,
    viewModel: LibraryViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) viewModel.importFiles(uris)
    }
    Column(
        Modifier
            .fillMaxSize()
            .padding(PolentitaSpacing.large),
        verticalArrangement = Arrangement.spacedBy(PolentitaSpacing.medium),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text(stringResource(R.string.about_back)) }
            Spacer(Modifier.width(PolentitaSpacing.small))
            Text(
                stringResource(R.string.import_songs),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
        }
        Surface(
            shape = RoundedCornerShape(PolentitaRadii.large),
            color = MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.72f),
            border = BorderStroke(
                1.dp,
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = PolentitaOpacity.border),
            ),
        ) {
            Column(
                Modifier.padding(PolentitaSpacing.large),
                verticalArrangement = Arrangement.spacedBy(PolentitaSpacing.small),
            ) {
                Text(stringResource(R.string.compatible_files), style = MaterialTheme.typography.titleMedium)
                Text(
                    stringResource(R.string.import_files_description),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Button(
            onClick = { launcher.launch(arrayOf("audio/*")) },
            enabled = !state.busy,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.select_files))
        }
        OutlinedButton(
            onClick = viewModel::scanLibrary,
            enabled = !state.busy,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.rescan_library))
        }
        if (state.busy) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                CircularProgressIndicator(Modifier.padding(PolentitaSpacing.large))
            }
        }
        state.message?.let {
            Text(it, Modifier.padding(PolentitaSpacing.medium), color = MaterialTheme.colorScheme.primary)
        }
        state.error?.let {
            Text(it, Modifier.padding(PolentitaSpacing.medium), color = MaterialTheme.colorScheme.error)
        }
        Spacer(Modifier.weight(1f))
    }
}

@Composable
fun TextFieldsDialog(
    title: String,
    firstLabel: String,
    secondLabel: String?,
    onDismiss: () -> Unit,
    onSave: (String, String) -> Unit,
    initialFirst: String = "",
    initialSecond: String = "",
) {
    var first by remember(initialFirst) { mutableStateOf(initialFirst) }
    var second by remember(initialSecond) { mutableStateOf(initialSecond) }
    PolentitaAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(PolentitaSpacing.medium)) {
                androidx.compose.material3.OutlinedTextField(
                    value = first,
                    onValueChange = { first = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(firstLabel) },
                    singleLine = true,
                    shape = RoundedCornerShape(PolentitaRadii.medium),
                    colors = polentitaOutlinedTextFieldColors(),
                )
                secondLabel?.let { label ->
                    androidx.compose.material3.OutlinedTextField(
                        value = second,
                        onValueChange = { second = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(label) },
                        singleLine = true,
                        shape = RoundedCornerShape(PolentitaRadii.medium),
                        colors = polentitaOutlinedTextFieldColors(),
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = { onSave(first, second) }) { Text(stringResource(R.string.save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

fun shareSong(context: android.content.Context, song: Song) {
    val uri = Uri.parse(song.contentUri)
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = song.mimeType
        putExtra(Intent.EXTRA_STREAM, uri)
        clipData = ClipData.newRawUri(song.displayFileName, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    ContextCompat.startActivity(
        context,
        Intent.createChooser(intent, context.getString(R.string.share_song)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        null,
    )
}
