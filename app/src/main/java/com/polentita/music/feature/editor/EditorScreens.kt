package com.polentita.music.feature.editor

import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.polentita.music.R
import com.polentita.music.core.common.formatBytes
import com.polentita.music.core.common.formatDuration
import com.polentita.music.core.database.AlbumEntity
import com.polentita.music.core.designsystem.Artwork
import com.polentita.music.core.designsystem.AdaptiveArtworkBackground
import com.polentita.music.core.designsystem.ArtworkDynamicTheme
import com.polentita.music.core.designsystem.EmptyState
import com.polentita.music.core.designsystem.LoadingState
import com.polentita.music.core.designsystem.PolentitaCoverSize
import com.polentita.music.core.designsystem.PolentitaAlertDialog
import com.polentita.music.core.designsystem.PolentitaDropdownMenu
import com.polentita.music.core.designsystem.PolentitaOpacity
import com.polentita.music.core.designsystem.PolentitaRadii
import com.polentita.music.core.designsystem.PolentitaSpacing
import com.polentita.music.core.designsystem.SongActions
import com.polentita.music.core.designsystem.SongRow
import com.polentita.music.core.designsystem.activeSongVisualState
import com.polentita.music.core.designsystem.rememberArtworkPalette
import com.polentita.music.core.designsystem.polentitaOutlinedTextFieldColors
import com.polentita.music.domain.model.Song
import com.polentita.music.domain.artwork.ArtworkChoice
import com.polentita.music.feature.library.LibraryViewModel
import com.polentita.music.feature.library.TextFieldsDialog
import com.polentita.music.feature.library.shareSong
import com.polentita.music.feature.player.PlayerViewModel
import com.polentita.music.playback.queue.PlaybackContextKind
import com.polentita.music.feature.downloads.AlbumSelector
import com.polentita.music.feature.downloads.ArtistSelector
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

@Composable
fun SongEditorScreen(
    onBack: () -> Unit,
    viewModel: SongEditorViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val song = state.song
    var title by remember { mutableStateOf("") }
    var artist by remember { mutableStateOf("") }
    var albumName by remember { mutableStateOf("") }
    var albumId by remember { mutableStateOf<Long?>(null) }
    var genre by remember { mutableStateOf("") }
    var artworkChoice by remember { mutableStateOf<ArtworkChoice>(ArtworkChoice.Unchanged) }
    var showArtworkPicker by remember { mutableStateOf(false) }
    var artworkPickerSession by remember { mutableIntStateOf(0) }

    LaunchedEffect(song?.id) {
        song ?: return@LaunchedEffect
        title = song.title
        artist = song.artist
        albumName = song.albumName
        albumId = song.albumId
        genre = song.genre
        artworkChoice = ArtworkChoice.Unchanged
    }

    LaunchedEffect(state.saved) {
        if (state.saved) onBack()
    }

    if (state.loading) return LoadingState()
    if (song == null) {
        return EmptyState(
            stringResource(R.string.song_not_found),
            stringResource(R.string.song_missing),
        )
    }
    val coverUri = when (val choice = artworkChoice) {
        ArtworkChoice.Unchanged -> song.coverUri
        ArtworkChoice.Remove -> null
        is ArtworkChoice.LocalFile -> choice.contentUri
        is ArtworkChoice.Remote -> choice.candidate.imageUrl
    }
    val palette = rememberArtworkPalette(coverUri, "${song.title}|${song.artist}")
    ArtworkDynamicTheme(palette) {
        AdaptiveArtworkBackground(coverUri, palette) {
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = PolentitaSpacing.xl,
                    top = PolentitaSpacing.small,
                    end = PolentitaSpacing.xl,
                    bottom = 112.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(PolentitaSpacing.medium),
            ) {
                item(key = "editor-toolbar") {
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        androidx.compose.material3.IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back))
                        }
                        Column {
                            Text(
                                stringResource(R.string.editor_library_label),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            Text(
                                stringResource(R.string.editor_edit_song),
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                }
        item {
            Column(
                Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Artwork(
                    coverUri,
                    stringResource(R.string.artwork_selected),
                    Modifier.size(232.dp),
                    seed = "${song.title}|${song.artist}",
                    elevated = true,
                )
                TextButton(
                    onClick = {
                        viewModel.clearError()
                        artworkPickerSession++
                        showArtworkPicker = true
                    },
                ) {
                    Text(stringResource(R.string.artwork_change))
                }
                if (albumId != null) {
                    Text(
                        stringResource(R.string.artwork_save_song_only),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        item {
            OutlinedTextField(
                title,
                { title = it },
                Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.field_title)) },
                shape = RoundedCornerShape(PolentitaRadii.medium),
                colors = polentitaOutlinedTextFieldColors(),
            )
        }
        item {
            ArtistSelector(
                value = artist,
                artists = state.artists,
                onValueChange = {
                    artist = it
                    albumId = null
                },
                onArtistSelected = {
                    artist = it
                    albumId = null
                },
            )
        }
        item {
            AlbumSelector(
                value = albumName,
                albums = state.albums,
                onValueChange = {
                    albumName = it
                    albumId = null
                },
                onAlbumSelected = {
                    albumName = it.name
                    albumId = it.id
                },
            )
        }
        item {
            OutlinedTextField(
                genre,
                { genre = it },
                Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.field_genre)) },
                shape = RoundedCornerShape(PolentitaRadii.medium),
                colors = polentitaOutlinedTextFieldColors(),
            )
        }
        item {
            state.error?.let { error ->
                Text(
                    error,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(PolentitaSpacing.small))
            }
            Button(
                onClick = {
                    viewModel.save(
                        song.copy(
                            title = title.trim().ifBlank { song.title },
                            artist = artist.trim(),
                            albumId = albumId,
                            albumName = albumName.trim(),
                            genre = genre.trim(),
                            coverUri = song.coverUri,
                        ),
                        artworkChoice,
                    )
                },
                enabled = !state.saving,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(PolentitaRadii.pill),
            ) {
                Text(
                    if (state.saving) stringResource(R.string.saving_changes)
                    else stringResource(R.string.save_changes),
                )
            }
        }
            }
        }
    }
    if (showArtworkPicker) {
        ArtworkPickerDialog(
            targetKey = "song-${song.id}-$artworkPickerSession",
            initialQuery = albumName.ifBlank { title },
            artist = artist,
            currentCoverUri = coverUri,
            onDismiss = { showArtworkPicker = false },
            onUse = { choice ->
                viewModel.clearError()
                artworkChoice = choice
                showArtworkPicker = false
            },
        )
    }
}

@Composable
fun TechnicalDetailsScreen(
    songId: Long,
    onBack: () -> Unit,
    viewModel: LibraryViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val song = state.songs.firstOrNull { it.id == songId }
    var rename by remember { mutableStateOf(false) }
    if (state.loading) return LoadingState()
    if (song == null) {
        return EmptyState(
            stringResource(R.string.file_not_found),
            stringResource(R.string.file_missing),
        )
    }
    val palette = rememberArtworkPalette(song.coverUri, "${song.title}|${song.artist}")
    ArtworkDynamicTheme(palette) {
        AdaptiveArtworkBackground(song.coverUri, palette) {
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = PolentitaSpacing.large,
                    top = PolentitaSpacing.small,
                    end = PolentitaSpacing.large,
                    bottom = 112.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(PolentitaSpacing.medium),
            ) {
                item(key = "technical-header") {
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        androidx.compose.material3.IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back))
                        }
                        Artwork(
                            song.coverUri,
                            stringResource(R.string.home_cover_description, song.title),
                            Modifier.size(52.dp),
                            seed = "${song.title}|${song.artist}",
                        )
                        Spacer(Modifier.width(PolentitaSpacing.medium))
                        Column(Modifier.weight(1f)) {
                            Text(
                                stringResource(R.string.technical_details),
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                song.title,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                            )
                        }
                    }
                }
                item(key = "technical-data") {
                    Surface(
                        shape = RoundedCornerShape(PolentitaRadii.large),
                        color = MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.72f),
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            MaterialTheme.colorScheme.outlineVariant.copy(alpha = PolentitaOpacity.border),
                        ),
                    ) {
                        Column(Modifier.padding(PolentitaSpacing.large)) {
                            listOf(
                                stringResource(R.string.technical_file) to song.displayFileName,
                                stringResource(R.string.technical_uri) to song.contentUri,
                                stringResource(R.string.technical_mime) to song.mimeType,
                                stringResource(R.string.technical_size) to formatBytes(song.fileSize),
                                stringResource(R.string.technical_duration) to formatDuration(song.durationMs),
                                stringResource(R.string.technical_checksum) to song.checksum,
                                stringResource(R.string.technical_source) to song.sourceType,
                                stringResource(R.string.technical_availability) to if (song.isAvailable) {
                                    stringResource(R.string.technical_available)
                                } else {
                                    stringResource(R.string.technical_missing)
                                },
                                stringResource(R.string.technical_play_count) to song.playCount.toString(),
                            ).forEachIndexed { index, detail ->
                                Detail(detail.first, detail.second)
                                if (index < 8) {
                                    HorizontalDivider(
                                        Modifier.padding(vertical = PolentitaSpacing.small),
                                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.20f),
                                    )
                                }
                            }
                        }
                    }
                }
                item {
                    Button(
                        onClick = { rename = true },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(PolentitaRadii.pill),
                    ) {
                        Icon(Icons.Default.Edit, null)
                        Text(stringResource(R.string.rename_file))
                    }
                }
            }
        }
    }
    if (rename) {
        TextFieldsDialog(
            title = stringResource(R.string.rename_file),
            firstLabel = stringResource(R.string.field_extension_name),
            secondLabel = null,
            initialFirst = song.displayFileName,
            onDismiss = { rename = false },
            onSave = { name, _ ->
                viewModel.rename(song.id, name)
                rename = false
            },
        )
    }
}

@Composable
private fun Detail(label: String, value: String) {
    Column {
        Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        Text(value.ifBlank { stringResource(R.string.no_data) }, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
fun AlbumDetailScreen(
    albumId: Long,
    player: PlayerViewModel,
    onEditSong: (Long) -> Unit,
    onDetails: (Long) -> Unit,
    onBack: () -> Unit,
    viewModel: LibraryViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val album = state.albums.firstOrNull { it.id == albumId }
    val songs = state.songs.filter { it.albumId == albumId }
    LaunchedEffect(albumId, songs) {
        player.updatePlaybackContext(
            songs = songs,
            kind = PlaybackContextKind.ALBUM,
            key = albumId.toString(),
            label = album?.name.orEmpty(),
        )
    }
    var editing by remember { mutableStateOf(false) }
    var openArtworkPickerInitially by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }
    var addingSongs by remember { mutableStateOf(false) }
    var menuExpanded by remember { mutableStateOf(false) }
    if (state.loading) return LoadingState()
    if (album == null) {
        return EmptyState(
            stringResource(R.string.album_not_found),
            stringResource(R.string.album_missing),
        )
    }
    val cover = album.coverUri ?: songs.firstNotNullOfOrNull(Song::coverUri)
    val palette = rememberArtworkPalette(cover, "${album.name}|${album.artist}")
    val listState = rememberLazyListState()
    val compactHeader by remember {
        derivedStateOf {
            listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 520
        }
    }
    val activePlayback by remember(player) {
        player.state.map { Triple(it.currentSongId, it.isPlaying, it.contextLabel) }.distinctUntilChanged()
    }.collectAsStateWithLifecycle(
        initialValue = Triple(null, false, null),
    )
    val contextActive = activePlayback.third == album.name && songs.any { it.id == activePlayback.first }
    ArtworkDynamicTheme(palette) {
        AdaptiveArtworkBackground(cover, palette) {
            Box(Modifier.fillMaxSize()) {
                LazyColumn(
                    state = listState,
                    contentPadding = PaddingValues(
                        top = 72.dp,
                        bottom = 112.dp,
                    ),
                ) {
                    item(key = "album-header") {
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = PolentitaSpacing.xl),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Artwork(
                                cover,
                                stringResource(R.string.home_album_cover_description, album.name),
                                Modifier
                                    .size(PolentitaCoverSize.hero)
                                    .graphicsLayer {
                                        val progress =
                                            (listState.firstVisibleItemScrollOffset / 700f).coerceIn(0f, 0.14f)
                                        scaleX = 1f - progress
                                        scaleY = 1f - progress
                                    },
                                seed = "${album.name}|${album.artist}",
                                elevated = true,
                            )
                            Spacer(Modifier.height(PolentitaSpacing.xxl))
                            Text(
                                album.name,
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.Black,
                                maxLines = 2,
                            )
                            Text(
                                album.artist.ifBlank { stringResource(R.string.unknown_artist) },
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                            )
                            Spacer(Modifier.height(PolentitaSpacing.small))
                            Text(
                                listOfNotNull(
                                    album.year?.toString(),
                                    stringResource(R.string.song_count, songs.size),
                                    formatDuration(songs.sumOf(Song::durationMs)),
                                ).joinToString(" · "),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.height(PolentitaSpacing.xl))
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(PolentitaSpacing.medium),
                            ) {
                                Button(
                                    onClick = {
                                        if (contextActive) {
                                            player.togglePlayPause()
                                        } else {
                                            player.playContext(
                                                songs = songs,
                                                kind = PlaybackContextKind.ALBUM,
                                                key = albumId.toString(),
                                                label = album.name,
                                            )
                                        }
                                    },
                                    enabled = songs.isNotEmpty(),
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(PolentitaRadii.pill),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = palette.accent,
                                        contentColor = MaterialTheme.colorScheme.onPrimary,
                                    ),
                                ) {
                                    Icon(
                                        if (contextActive && activePlayback.second) Icons.Default.Pause
                                        else Icons.Default.PlayArrow,
                                        null,
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
                                            kind = PlaybackContextKind.ALBUM,
                                            key = albumId.toString(),
                                            label = album.name,
                                        )
                                    },
                                    enabled = songs.isNotEmpty(),
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(PolentitaRadii.pill),
                                ) {
                                    Icon(Icons.Default.Shuffle, null)
                                    Text(stringResource(R.string.random))
                                }
                            }
                            Spacer(Modifier.height(PolentitaSpacing.xl))
                        }
                    }
                    if (songs.isEmpty()) {
                        item {
                            EmptyState(
                                stringResource(R.string.album_empty),
                                stringResource(R.string.album_empty_message),
                                Modifier.height(280.dp),
                            )
                        }
                    } else {
                        itemsIndexed(songs, key = { _, song -> song.id }) { index, song ->
                            SongRow(
                                song = song,
                                actions = SongActions(
                                    play = {
                                        player.playFromContext(
                                            song = song,
                                            songs = songs,
                                            kind = PlaybackContextKind.ALBUM,
                                            key = albumId.toString(),
                                            label = album.name,
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
                                trackNumber = song.trackNumber ?: index + 1,
                                modifier = Modifier.padding(horizontal = PolentitaSpacing.small),
                            )
                        }
                    }
                }
                Row(
                    Modifier
                        .fillMaxWidth()
                        .background(
                            if (compactHeader) palette.background.copy(alpha = 0.94f) else Color.Transparent,
                        )
                        .padding(horizontal = PolentitaSpacing.small, vertical = PolentitaSpacing.xs),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    androidx.compose.material3.IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back))
                    }
                    Text(
                        if (compactHeader) album.name else "",
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                    )
                    Box {
                        androidx.compose.material3.IconButton(onClick = { menuExpanded = true }) {
                            Icon(Icons.Default.MoreVert, stringResource(R.string.album_options))
                        }
                        PolentitaDropdownMenu(
                            expanded = menuExpanded,
                            onDismissRequest = { menuExpanded = false },
                        ) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.edit_album)) },
                                leadingIcon = { Icon(Icons.Default.Edit, null) },
                                onClick = {
                                    menuExpanded = false
                                    viewModel.clearMessage()
                                    openArtworkPickerInitially = false
                                    editing = true
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.artwork_change)) },
                                leadingIcon = { Icon(Icons.Default.Edit, null) },
                                onClick = {
                                    menuExpanded = false
                                    viewModel.clearMessage()
                                    openArtworkPickerInitially = true
                                    editing = true
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.playlist_add_songs)) },
                                leadingIcon = { Icon(Icons.Default.Add, null) },
                                onClick = { menuExpanded = false; addingSongs = true },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.delete)) },
                                leadingIcon = { Icon(Icons.Default.Delete, null) },
                                onClick = { menuExpanded = false; deleting = true },
                            )
                        }
                    }
                }
            }
        }
    }
    if (editing) {
        AlbumEditDialog(
            album = album,
            displayCoverUri = cover,
            saving = state.busy,
            errorMessage = state.error,
            openArtworkPickerInitially = openArtworkPickerInitially,
            onDismiss = {
                editing = false
                openArtworkPickerInitially = false
            },
            onSave = { updated, artworkChoice ->
                viewModel.updateAlbum(updated, artworkChoice) {
                    editing = false
                    openArtworkPickerInitially = false
                }
            },
        )
    }
    if (deleting) {
        PolentitaAlertDialog(
            onDismissRequest = { deleting = false },
            title = { Text(stringResource(R.string.delete_album_title)) },
            text = { Text(stringResource(R.string.delete_album_message)) },
            confirmButton = {
                TextButton(onClick = { viewModel.deleteAlbum(album); deleting = false; onBack() }) {
                    Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { deleting = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
    if (addingSongs) {
        AddSongsToAlbumDialog(
            songs = state.songs.filter { it.albumId != album.id },
            onDismiss = { addingSongs = false },
            onSave = {
                viewModel.assignSongsToAlbum(it, album)
                addingSongs = false
            },
        )
    }
}

@Composable
fun AlbumEditDialog(
    album: AlbumEntity,
    displayCoverUri: String? = album.coverUri,
    saving: Boolean = false,
    errorMessage: String? = null,
    openArtworkPickerInitially: Boolean = false,
    onDismiss: () -> Unit,
    onSave: (AlbumEntity, ArtworkChoice) -> Unit,
) {
    var name by remember { mutableStateOf(album.name) }
    var artist by remember { mutableStateOf(album.artist) }
    var year by remember { mutableStateOf(album.year?.toString().orEmpty()) }
    var artworkChoice by remember { mutableStateOf<ArtworkChoice>(ArtworkChoice.Unchanged) }
    var showArtworkPicker by remember(album.id) { mutableStateOf(openArtworkPickerInitially) }
    var artworkPickerSession by remember(album.id) {
        mutableIntStateOf(if (openArtworkPickerInitially) 1 else 0)
    }
    val cover = when (val choice = artworkChoice) {
        ArtworkChoice.Unchanged -> displayCoverUri
        ArtworkChoice.Remove -> null
        is ArtworkChoice.LocalFile -> choice.contentUri
        is ArtworkChoice.Remote -> choice.candidate.imageUrl
    }
    PolentitaAlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text(
                    stringResource(R.string.metadata_label),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(stringResource(R.string.edit_album), style = MaterialTheme.typography.headlineSmall)
            }
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(PolentitaSpacing.medium),
            ) {
                Artwork(
                    cover,
                    stringResource(R.string.home_album_cover_description, album.name),
                    Modifier.fillMaxWidth().height(152.dp),
                    seed = "${album.name}|${album.artist}",
                )
                TextButton(
                    onClick = {
                        artworkPickerSession++
                        showArtworkPicker = true
                    },
                    modifier = Modifier.align(Alignment.End),
                ) { Text(stringResource(R.string.artwork_change)) }
                Text(
                    stringResource(R.string.artwork_save_album_only),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                errorMessage?.let { error ->
                    Text(
                        error,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                OutlinedTextField(
                    name,
                    { name = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.field_name)) },
                    shape = RoundedCornerShape(PolentitaRadii.medium),
                    colors = polentitaOutlinedTextFieldColors(),
                )
                OutlinedTextField(
                    artist,
                    { artist = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.field_artist)) },
                    shape = RoundedCornerShape(PolentitaRadii.medium),
                    colors = polentitaOutlinedTextFieldColors(),
                )
                OutlinedTextField(
                    year,
                    { year = it.filter(Char::isDigit).take(4) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.field_year)) },
                    shape = RoundedCornerShape(PolentitaRadii.medium),
                    colors = polentitaOutlinedTextFieldColors(),
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(
                        album.copy(
                            name = name.trim().ifBlank { album.name },
                            artist = artist.trim(),
                            year = year.toIntOrNull(),
                            coverUri = album.coverUri,
                        ),
                        artworkChoice,
                    )
                },
                enabled = !saving,
            ) {
                Text(if (saving) stringResource(R.string.saving_changes) else stringResource(R.string.save))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
    if (showArtworkPicker) {
        ArtworkPickerDialog(
            targetKey = "album-${album.id}-$artworkPickerSession",
            initialQuery = name,
            artist = artist,
            currentCoverUri = cover,
            onDismiss = { showArtworkPicker = false },
            onUse = { choice ->
                artworkChoice = choice
                showArtworkPicker = false
            },
        )
    }
}

@Composable
fun ArtistDetailScreen(
    artist: String,
    player: PlayerViewModel,
    onEditSong: (Long) -> Unit,
    onDetails: (Long) -> Unit,
    onBack: () -> Unit,
    viewModel: LibraryViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val songs = remember(state.songs, artist) {
        state.songs.filter { it.artist == artist }
    }
    LaunchedEffect(artist, songs) {
        player.updatePlaybackContext(
            songs = songs,
            kind = PlaybackContextKind.ARTIST,
            key = artist,
            label = artist,
        )
    }
    val albums = remember(state.albums, songs, artist) {
        state.albums.filter { album ->
            album.artist.equals(artist, ignoreCase = true) || songs.any { it.albumId == album.id }
        }
    }
    val covers = remember(albums, songs) {
        (albums.mapNotNull(AlbumEntity::coverUri) + songs.mapNotNull(Song::coverUri)).distinct()
    }
    val palette = rememberArtworkPalette(covers.firstOrNull(), artist)
    val listState = rememberLazyListState()
    val compactHeader by remember {
        derivedStateOf {
            listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 360
        }
    }
    val activePlayback by remember(player) {
        player.state.map { Triple(it.currentSongId, it.isPlaying, it.contextLabel) }.distinctUntilChanged()
    }.collectAsStateWithLifecycle(
        initialValue = Triple(null, false, null),
    )
    val contextActive = activePlayback.third == artist && songs.any { it.id == activePlayback.first }
    val context = LocalContext.current
    ArtworkDynamicTheme(palette) {
        AdaptiveArtworkBackground(covers.firstOrNull(), palette) {
            Box(Modifier.fillMaxSize()) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(top = 68.dp, bottom = 112.dp),
                ) {
                    item(key = "artist-header") {
                        Column(
                            Modifier.fillMaxWidth().padding(horizontal = PolentitaSpacing.xl),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            ArtistMosaic(
                                covers = covers,
                                artist = artist,
                                modifier = Modifier.size(220.dp),
                            )
                            Spacer(Modifier.height(PolentitaSpacing.xl))
                            Text(
                                artist.ifBlank { stringResource(R.string.unknown_artist) },
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.Black,
                                maxLines = 2,
                            )
                            Text(
                                stringResource(R.string.artist_summary, songs.size, albums.size),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.height(PolentitaSpacing.large))
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(PolentitaSpacing.medium),
                            ) {
                                Button(
                                    onClick = {
                                        if (contextActive) {
                                            player.togglePlayPause()
                                        } else {
                                            player.playContext(
                                                songs = songs,
                                                kind = PlaybackContextKind.ARTIST,
                                                key = artist,
                                                label = artist,
                                            )
                                        }
                                    },
                                    enabled = songs.isNotEmpty(),
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(PolentitaRadii.pill),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = palette.accent,
                                        contentColor = MaterialTheme.colorScheme.onPrimary,
                                    ),
                                ) {
                                    Icon(
                                        if (contextActive && activePlayback.second) Icons.Default.Pause
                                        else Icons.Default.PlayArrow,
                                        null,
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
                                            kind = PlaybackContextKind.ARTIST,
                                            key = artist,
                                            label = artist,
                                        )
                                    },
                                    enabled = songs.isNotEmpty(),
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(PolentitaRadii.pill),
                                ) {
                                    Icon(Icons.Default.Shuffle, null)
                                    Text(stringResource(R.string.random))
                                }
                            }
                            Spacer(Modifier.height(PolentitaSpacing.xl))
                        }
                    }
                    if (songs.isEmpty()) {
                        item {
                            Box(Modifier.fillMaxWidth().height(280.dp)) {
                                EmptyState(
                                    stringResource(R.string.smart_playlist_empty_title),
                                    stringResource(R.string.artist_no_songs),
                                )
                            }
                        }
                    } else {
                        item(key = "popular-title") {
                            Text(
                                stringResource(R.string.popular_songs),
                                Modifier.padding(
                                    start = PolentitaSpacing.xl,
                                    top = PolentitaSpacing.small,
                                    end = PolentitaSpacing.xl,
                                    bottom = PolentitaSpacing.small,
                                ),
                                style = MaterialTheme.typography.titleLarge,
                            )
                        }
                        itemsIndexed(
                            songs.sortedByDescending(Song::playCount).take(5),
                            key = { _, song -> "popular-${song.id}" },
                        ) { index, song ->
                            SongRow(
                                song,
                                SongActions(
                                    play = {
                                        player.playFromContext(
                                            song = song,
                                            songs = songs,
                                            kind = PlaybackContextKind.ARTIST,
                                            key = artist,
                                            label = artist,
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
                                trackNumber = index + 1,
                                modifier = Modifier.padding(horizontal = PolentitaSpacing.small),
                            )
                        }
                        if (albums.isNotEmpty()) {
                            item(key = "albums-title") {
                                Text(
                                    stringResource(R.string.albums_tab),
                                    Modifier.padding(
                                        start = PolentitaSpacing.xl,
                                        top = PolentitaSpacing.xl,
                                        end = PolentitaSpacing.xl,
                                        bottom = PolentitaSpacing.small,
                                    ),
                                    style = MaterialTheme.typography.titleLarge,
                                )
                            }
                            item(key = "albums-row") {
                                LazyRow(
                                    contentPadding = PaddingValues(horizontal = PolentitaSpacing.xl),
                                    horizontalArrangement = Arrangement.spacedBy(PolentitaSpacing.medium),
                                ) {
                                    items(albums, key = AlbumEntity::id) { album ->
                                        Surface(
                                            modifier = Modifier.width(154.dp),
                                            shape = RoundedCornerShape(PolentitaRadii.medium),
                                            color = MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.70f),
                                        ) {
                                            Column {
                                                Artwork(
                                                    album.coverUri,
                                                    stringResource(R.string.home_album_cover_description, album.name),
                                                    Modifier.size(154.dp),
                                                    seed = "${album.name}|${album.artist}",
                                                )
                                                Column(Modifier.padding(PolentitaSpacing.small)) {
                                                    Text(album.name, maxLines = 1)
                                                    Text(
                                                        album.year?.toString().orEmpty(),
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        item(key = "all-title") {
                            Text(
                                stringResource(R.string.all_songs),
                                Modifier.padding(
                                    start = PolentitaSpacing.xl,
                                    top = PolentitaSpacing.xl,
                                    end = PolentitaSpacing.xl,
                                    bottom = PolentitaSpacing.small,
                                ),
                                style = MaterialTheme.typography.titleLarge,
                            )
                        }
                        itemsIndexed(songs, key = { _, song -> "all-${song.id}" }) { _, song ->
                            SongRow(
                                song,
                                SongActions(
                                    play = {
                                        player.playFromContext(
                                            song = song,
                                            songs = songs,
                                            kind = PlaybackContextKind.ARTIST,
                                            key = artist,
                                            label = artist,
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
                                modifier = Modifier.padding(horizontal = PolentitaSpacing.small),
                            )
                        }
                    }
                }
                Row(
                    Modifier
                        .fillMaxWidth()
                        .background(
                            if (compactHeader) {
                                palette.background.copy(alpha = 0.96f)
                            } else {
                                Color.Transparent
                            },
                        )
                        .padding(horizontal = PolentitaSpacing.small, vertical = PolentitaSpacing.xs),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    androidx.compose.material3.IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back))
                    }
                    if (compactHeader) {
                        Artwork(
                            covers.firstOrNull(),
                            stringResource(R.string.artwork_image_of, artist),
                            Modifier.size(40.dp),
                            seed = artist,
                        )
                        Spacer(Modifier.width(PolentitaSpacing.small))
                    }
                    Text(
                        if (compactHeader) {
                            artist.ifBlank { stringResource(R.string.unknown_artist) }
                        } else {
                            stringResource(R.string.artist_label)
                        },
                        modifier = Modifier.weight(1f),
                        style = if (compactHeader) {
                            MaterialTheme.typography.titleMedium
                        } else {
                            MaterialTheme.typography.labelMedium
                        },
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

@Composable
private fun ArtistMosaic(
    covers: List<String>,
    artist: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.clip(CircleShape),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        if (covers.isEmpty()) {
            Artwork(
                null,
                stringResource(R.string.artwork_image_of, artist),
                Modifier.fillMaxSize(),
                seed = artist,
            )
        } else {
            Column {
                repeat(2) { row ->
                    Row(Modifier.weight(1f)) {
                        repeat(2) { column ->
                            val index = row * 2 + column
                            Artwork(
                                covers.getOrNull(index) ?: covers[index % covers.size],
                                stringResource(R.string.artwork_mosaic, artist),
                                Modifier.weight(1f).fillMaxSize(),
                                seed = "$artist-$index",
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AddSongsToAlbumDialog(
    songs: List<Song>,
    onDismiss: () -> Unit,
    onSave: (List<Song>) -> Unit,
) {
    var selectedIds by remember { mutableStateOf(emptySet<Long>()) }
    PolentitaAlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text(
                    stringResource(R.string.field_album).uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(stringResource(R.string.playlist_add_songs), style = MaterialTheme.typography.headlineSmall)
            }
        },
        text = {
            if (songs.isEmpty()) {
                Text(stringResource(R.string.album_all_songs))
            } else {
                LazyColumn(Modifier.height(360.dp)) {
                    items(songs, key = Song::id) { song ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(PolentitaRadii.medium))
                                .background(
                                    if (song.id in selectedIds) {
                                        MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)
                                    } else {
                                        Color.Transparent
                                    },
                                )
                                .clickable {
                                    selectedIds = if (song.id in selectedIds) {
                                        selectedIds - song.id
                                    } else {
                                        selectedIds + song.id
                                    }
                                }
                                .padding(vertical = PolentitaSpacing.xs),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(
                                checked = song.id in selectedIds,
                                onCheckedChange = {
                                    selectedIds = if (it) selectedIds + song.id else selectedIds - song.id
                                },
                            )
                            Artwork(
                                song.coverUri,
                                stringResource(R.string.home_cover_description, song.title),
                                Modifier.size(44.dp),
                                seed = "${song.title}|${song.artist}",
                            )
                            Spacer(Modifier.width(PolentitaSpacing.small))
                            Column(Modifier.weight(1f)) {
                                Text(song.title, maxLines = 1, fontWeight = FontWeight.Medium)
                                Text(
                                    song.artist,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                enabled = selectedIds.isNotEmpty(),
                onClick = { onSave(songs.filter { it.id in selectedIds }) },
            ) { Text(stringResource(R.string.add)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}
