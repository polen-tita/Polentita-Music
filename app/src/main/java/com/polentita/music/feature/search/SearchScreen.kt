package com.polentita.music.feature.search

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.polentita.music.R
import com.polentita.music.core.common.formatDuration
import com.polentita.music.core.database.AlbumEntity
import com.polentita.music.core.database.RemoteReferenceEntity
import com.polentita.music.core.designsystem.CompactSearchField
import com.polentita.music.core.designsystem.Artwork
import com.polentita.music.core.designsystem.EmptyState
import com.polentita.music.core.designsystem.ErrorState
import com.polentita.music.core.designsystem.LoadingState
import com.polentita.music.core.designsystem.PolentitaOpacity
import com.polentita.music.core.designsystem.PolentitaAlertDialog
import com.polentita.music.core.designsystem.PolentitaRadii
import com.polentita.music.core.designsystem.PolentitaSpacing
import com.polentita.music.core.designsystem.polentitaOutlinedTextFieldColors
import com.polentita.music.domain.provider.RemoteTrack
import com.polentita.music.data.extractor.YtDlpMediaInfo
import com.polentita.music.data.repository.RemoteReferenceRepository
import com.polentita.music.feature.downloads.AlbumSelector
import com.polentita.music.feature.downloads.ArtistSelector
import com.polentita.music.feature.player.PlayerViewModel
import androidx.compose.foundation.verticalScroll
import kotlinx.coroutines.delay

@Composable
fun SearchScreen(
    player: PlayerViewModel,
    onOpenLibrary: () -> Unit,
    viewModel: SearchViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}

    LaunchedEffect(Unit) {
        viewModel.selectTab(SearchTab.EXPLORE)
    }

    LaunchedEffect(state.preview.preview?.streamUrl) {
        state.preview.preview?.let { preview ->
            player.playPreview(preview)
            viewModel.clearPreview()
        }
    }

    LaunchedEffect(
        state.remoteDownload.status,
        state.remoteDownload.trackId,
        state.remoteDownload.message,
    ) {
        if (state.remoteDownload.status in setOf(
                RemoteDownloadStatus.QUEUED,
                RemoteDownloadStatus.SUCCESS,
                RemoteDownloadStatus.ERROR,
                RemoteDownloadStatus.DOWNLOAD_NOT_ALLOWED,
            )
        ) {
            delay(TRANSIENT_STATUS_MESSAGE_MS)
            viewModel.clearRemoteDownloadMessage()
        }
    }

    LaunchedEffect(state.preview.status, state.preview.trackId, state.preview.message) {
        if (state.preview.status == RemotePreviewStatus.ERROR) {
            delay(TRANSIENT_STATUS_MESSAGE_MS)
            viewModel.clearPreview()
        }
    }

    Column(Modifier.fillMaxSize()) {
        CompactSearchField(
            value = state.query,
            onValueChange = viewModel::setQuery,
            hint = stringResource(R.string.search_hint),
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    start = PolentitaSpacing.medium,
                    top = PolentitaSpacing.small,
                    end = PolentitaSpacing.medium,
                    bottom = PolentitaSpacing.small,
                ),
            enabled = !state.networkAccess.offlineMode,
        )
        ExploreResults(state, viewModel, player, onOpenLibrary)
    }

    state.remoteDownload.inspected?.let { info ->
        RemoteDownloadMetadataDialog(
            info = info,
            artists = state.artists,
            albums = state.albums,
            downloadEnabled = state.networkAccess.downloadAllowed,
            blockedMessage = state.networkAccess.downloadBlockReason
                ?.let { stringResource(it.messageRes) },
            onDismiss = viewModel::clearRemoteDownloadMessage,
            onConfirm = { title, artist, album, albumId ->
                if (Build.VERSION.SDK_INT >= 33) {
                    notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
                viewModel.enqueueYtDlp(title, artist, album, albumId)
            },
        )
    }

}

@Composable
private fun ExploreResults(
    state: SearchUiState,
    viewModel: SearchViewModel,
    player: PlayerViewModel,
    onOpenLibrary: () -> Unit,
) {
    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            contentPadding = PaddingValues(
                start = PolentitaSpacing.medium,
                top = PolentitaSpacing.xs,
                end = PolentitaSpacing.medium,
                bottom = 112.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(PolentitaSpacing.xs),
        ) {
            if (state.networkAccess.offlineMode) {
                item(key = "offline-mode-banner") {
                    OfflineExploreBanner(onOpenLibrary)
                }
            }
            item {
                Row(
                    Modifier.fillMaxWidth().padding(bottom = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        stringResource(R.string.search_explore_recommendations),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    if (state.query.isBlank() && state.networkAccess.remoteSearchAllowed) {
                        TextButton(
                            onClick = {
                                player.stopPreview()
                                viewModel.refreshExplore()
                            },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                        ) {
                            Text(stringResource(R.string.search_explore_another_mix))
                        }
                    }
                }
            }
            if (state.query.isBlank() && state.savedReferences.isNotEmpty()) {
                item {
                    Text(
                        stringResource(R.string.search_explore_saved_references),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 4.dp, bottom = 2.dp),
                    )
                }
                items(
                    state.savedReferences,
                    key = { reference ->
                        RemoteReferenceRepository.key(reference.providerId, reference.remoteTrackId)
                    },
                ) { reference ->
                    SavedReferenceCard(
                        reference = reference,
                        onDownload = {
                            player.stopPreview()
                            viewModel.download(reference)
                        },
                        onRemove = { viewModel.removeReference(reference) },
                        downloadEnabled = state.networkAccess.downloadAllowed,
                        artworkEnabled = state.networkAccess.remoteSearchAllowed,
                    )
                }
            }
            when (state.explore.status) {
                ExploreStatus.INITIAL,
                ExploreStatus.LOADING,
                -> item { ExploreLoadingState(Modifier.fillParentMaxSize()) }
                ExploreStatus.EMPTY -> item {
                    EmptyState(
                        stringResource(R.string.search_external_empty_title),
                        state.explore.message ?: if (state.query.isBlank()) {
                            stringResource(R.string.search_explore_empty_query)
                        } else {
                            stringResource(R.string.search_explore_no_results)
                        },
                        Modifier.fillParentMaxSize(),
                    )
                }
                ExploreStatus.ERROR -> item {
                    ErrorState(
                        state.explore.message ?: stringResource(R.string.search_explore_error),
                        Modifier.fillParentMaxSize(),
                    )
                }
                ExploreStatus.OFFLINE -> item {
                    EmptyState(
                        stringResource(R.string.search_offline_title),
                        stringResource(R.string.search_offline_message),
                        Modifier.fillParentMaxSize(),
                    )
                }
                ExploreStatus.OFFLINE_MODE -> item {
                    Text(
                        stringResource(R.string.network_offline_search_message),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(vertical = PolentitaSpacing.medium),
                    )
                }
                ExploreStatus.PROVIDER_NOT_CONFIGURED -> item {
                    EmptyState(
                        stringResource(R.string.search_provider_not_configured),
                        state.explore.message ?: stringResource(R.string.search_provider_configure_message),
                        Modifier.fillParentMaxSize(),
                    )
                }
                ExploreStatus.SUCCESS -> {
                    state.explore.relatedTo?.let { relatedTo ->
                        item {
                            Text(
                                stringResource(R.string.search_related_to_library, relatedTo),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(bottom = 4.dp),
                            )
                        }
                    }
                    items(state.explore.results, key = RemoteTrack::id) { track ->
                        RemoteTrackCard(
                            track = track,
                            downloading = state.remoteDownload.status in setOf(
                                RemoteDownloadStatus.PREPARING,
                                RemoteDownloadStatus.DOWNLOADING,
                            ) &&
                                state.remoteDownload.trackId == track.id,
                            previewing = state.preview.status == RemotePreviewStatus.LOADING &&
                                state.preview.trackId == track.id,
                            referenceSaved = RemoteReferenceRepository.key(
                                track.providerId,
                                track.id,
                            ) in state.savedReferenceKeys,
                            onDownload = {
                                player.stopPreview()
                                viewModel.download(track)
                            },
                            onPreview = if (track.externalUrl != null) {
                                {
                                    player.stopPreview()
                                    viewModel.preview(track)
                                }
                            } else {
                                null
                            },
                            onToggleReference = { viewModel.toggleReference(track) },
                            previewEnabled = state.networkAccess.previewAllowed,
                            downloadEnabled = state.networkAccess.downloadAllowed,
                        )
                    }
                }
            }
            if (state.explore.canLoadMore) {
                item {
                    ExploreLoadMore(
                        state = state.explore,
                        onLoadMore = viewModel::loadMoreExplore,
                    )
                }
            }
        }
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = 12.dp)
                .padding(bottom = 112.dp),
        ) {
            if (state.remoteDownload.status != RemoteDownloadStatus.IDLE) {
                RemoteDownloadMessage(state.remoteDownload, viewModel, player)
            }
            if (state.preview.status == RemotePreviewStatus.ERROR) {
                RemotePreviewMessage(state.preview, viewModel)
            }
        }
    }
}

@Composable
private fun OfflineExploreBanner(onOpenLibrary: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(bottom = PolentitaSpacing.small),
        shape = RoundedCornerShape(PolentitaRadii.medium),
        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.74f),
        border = BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = PolentitaOpacity.border),
        ),
    ) {
        Row(
            Modifier.padding(PolentitaSpacing.medium),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Default.CloudOff,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
            Column(Modifier.weight(1f).padding(horizontal = PolentitaSpacing.small)) {
                Text(stringResource(R.string.network_offline_mode_active), fontWeight = FontWeight.Bold)
                Text(
                    stringResource(R.string.network_offline_search_message),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            TextButton(onClick = onOpenLibrary) { Text(stringResource(R.string.network_open_library)) }
        }
    }
}

@Composable
private fun ExploreLoadingState(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(vertical = PolentitaSpacing.large),
        verticalArrangement = Arrangement.spacedBy(PolentitaSpacing.small),
    ) {
        repeat(3) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(88.dp),
                shape = RoundedCornerShape(PolentitaRadii.medium),
                color = MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.72f),
                border = BorderStroke(
                    1.dp,
                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = PolentitaOpacity.border),
                ),
            ) {
                Row(
                    Modifier.padding(PolentitaSpacing.small),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier
                            .size(64.dp)
                            .clip(RoundedCornerShape(PolentitaRadii.small))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.62f)),
                    )
                    Spacer(Modifier.width(PolentitaSpacing.small))
                    Column(verticalArrangement = Arrangement.spacedBy(PolentitaSpacing.xs)) {
                        Box(
                            Modifier
                                .width(176.dp)
                                .height(14.dp)
                                .clip(RoundedCornerShape(PolentitaRadii.small))
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.62f)),
                        )
                        Box(
                            Modifier
                                .width(112.dp)
                                .height(11.dp)
                                .clip(RoundedCornerShape(PolentitaRadii.small))
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.46f)),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ExploreLoadMore(
    state: ExploreUiState,
    onLoadMore: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        state.loadMoreError?.let { message ->
            Text(
                message,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(bottom = 6.dp),
            )
        }
        OutlinedButton(
            onClick = onLoadMore,
            enabled = !state.loadingMore,
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (state.loadingMore) {
                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                Spacer(Modifier.size(8.dp))
            }
            Text(
                stringResource(
                    if (state.loadingMore) {
                        R.string.search_explore_loading_more
                    } else {
                        R.string.search_explore_load_more
                    },
                ),
            )
        }
    }
}

@Composable
private fun SavedReferenceCard(
    reference: RemoteReferenceEntity,
    onDownload: () -> Unit,
    onRemove: () -> Unit,
    downloadEnabled: Boolean,
    artworkEnabled: Boolean,
) {
    Card(
        onClick = onDownload,
        enabled = downloadEnabled,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        shape = RoundedCornerShape(PolentitaRadii.medium),
        border = BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = PolentitaOpacity.border),
        ),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.78f),
        ),
    ) {
        Row(Modifier.fillMaxWidth().padding(PolentitaSpacing.small)) {
            Artwork(
                reference.thumbnailUrl.takeIf { artworkEnabled },
                stringResource(R.string.search_explore_reference_cover_description, reference.title),
                Modifier.size(56.dp),
            )
            Column(Modifier.weight(1f).padding(start = PolentitaSpacing.small)) {
                ScrollableRemoteTitle(reference.title)
                Text(
                    reference.artist,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    "${reference.album} · ${formatDuration(reference.durationMs)}",
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            TextButton(
                onClick = onRemove,
                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
            ) {
                Icon(Icons.Default.Bookmark, contentDescription = null)
                Text(stringResource(R.string.search_explore_remove_reference))
            }
        }
    }
}

@Composable
private fun RemoteDownloadMessage(
    download: RemoteDownloadUiState,
    viewModel: SearchViewModel,
    player: PlayerViewModel,
) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(bottom = PolentitaSpacing.small),
        shape = RoundedCornerShape(PolentitaRadii.medium),
        border = BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = PolentitaOpacity.border),
        ),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.78f),
        ),
    ) {
        Column(Modifier.padding(PolentitaSpacing.medium)) {
            Text(
                download.message.orEmpty(),
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                color = if (download.status == RemoteDownloadStatus.ERROR ||
                    download.status == RemoteDownloadStatus.DOWNLOAD_NOT_ALLOWED
                ) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
            )
            if (download.status == RemoteDownloadStatus.PREPARING ||
                download.status == RemoteDownloadStatus.DOWNLOADING
            ) {
                LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 10.dp))
            }
            download.importedSong?.let { song ->
                Button(
                    onClick = {
                        player.playSong(song)
                        viewModel.clearRemoteDownloadMessage()
                    },
                    modifier = Modifier.padding(top = 8.dp),
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null)
                    Text(stringResource(R.string.play))
                }
            }
            if (download.status != RemoteDownloadStatus.PREPARING &&
                download.status != RemoteDownloadStatus.DOWNLOADING &&
                download.importedSong == null
            ) {
                TextButton(onClick = viewModel::clearRemoteDownloadMessage) { Text(stringResource(R.string.close)) }
            }
        }
    }
}

@Composable
private fun RemotePreviewMessage(
    preview: RemotePreviewUiState,
    viewModel: SearchViewModel,
) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(bottom = PolentitaSpacing.small),
        shape = RoundedCornerShape(PolentitaRadii.medium),
        border = BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = PolentitaOpacity.border),
        ),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.78f),
        ),
    ) {
        Column(Modifier.padding(PolentitaSpacing.medium)) {
            Text(
                preview.message.orEmpty(),
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.error,
            )
            TextButton(onClick = viewModel::clearPreview) { Text(stringResource(R.string.close)) }
        }
    }
}

private const val TRANSIENT_STATUS_MESSAGE_MS = 3_000L

@Composable
private fun RemoteTrackCard(
    track: RemoteTrack,
    downloading: Boolean,
    previewing: Boolean,
    referenceSaved: Boolean,
    onDownload: () -> Unit,
    onPreview: (() -> Unit)?,
    onToggleReference: () -> Unit,
    previewEnabled: Boolean,
    downloadEnabled: Boolean,
) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        shape = RoundedCornerShape(PolentitaRadii.medium),
        border = BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = PolentitaOpacity.border),
        ),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.82f),
        ),
    ) {
        Column(Modifier.padding(PolentitaSpacing.small)) {
            Row {
                Artwork(
                    track.coverUri,
                    stringResource(R.string.home_cover_description, track.title),
                    Modifier.size(60.dp),
                )
                Spacer(Modifier.size(8.dp))
                Column(Modifier.weight(1f)) {
                    ScrollableRemoteTitle(track.title)
                    Text(track.artist.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        listOfNotNull(
                            track.album.name.takeIf {
                                it.isNotBlank() && !it.equals(track.providerName, ignoreCase = true)
                            },
                            formatDuration(track.durationMs),
                        ).joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Spacer(Modifier.height(PolentitaSpacing.xs))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(PolentitaSpacing.small),
            ) {
                onPreview?.let { preview ->
                    OutlinedButton(
                        onClick = preview,
                        enabled = previewEnabled && !previewing,
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 5.dp),
                    ) {
                        if (previewing) {
                            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Default.PlayArrow, contentDescription = null)
                        }
                        Text(
                            stringResource(
                                if (previewing) {
                                    R.string.search_explore_preview_loading
                                } else {
                                    R.string.search_explore_preview_short
                                },
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                Button(
                    onClick = onDownload,
                    enabled = downloadEnabled && !downloading,
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 5.dp),
                ) {
                    Icon(Icons.Default.Download, contentDescription = null)
                    Text(
                        when {
                            downloading -> stringResource(R.string.search_explore_download_preparing)
                            track.allowsDownload -> stringResource(R.string.download)
                            else -> stringResource(R.string.download_not_allowed)
                        },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (track.externalUrl != null) {
                TextButton(
                    onClick = onToggleReference,
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                ) {
                    Icon(
                        if (referenceSaved) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                        contentDescription = null,
                    )
                    Text(
                        stringResource(
                            if (referenceSaved) {
                                R.string.search_explore_remove_reference
                            } else {
                                R.string.search_explore_save_reference
                            },
                        ),
                    )
                }
            }
        }
    }
}

@Composable
private fun ScrollableRemoteTitle(title: String) {
    Text(
        title,
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        fontWeight = FontWeight.Bold,
        maxLines = 1,
        softWrap = false,
        overflow = TextOverflow.Clip,
    )
}

@Composable
private fun RemoteDownloadMetadataDialog(
    info: YtDlpMediaInfo,
    artists: List<String>,
    albums: List<AlbumEntity>,
    downloadEnabled: Boolean,
    blockedMessage: String?,
    onDismiss: () -> Unit,
    onConfirm: (title: String, artist: String, album: String, albumId: Long?) -> Unit,
) {
    val titleState = rememberTextFieldState(initialText = info.title)
    val titleScrollState = rememberScrollState()
    var artist by remember(info.id) { mutableStateOf(info.artist) }
    var album by remember(info.id) { mutableStateOf("") }
    var selectedAlbumId by remember(info.id) { mutableStateOf<Long?>(null) }

    PolentitaAlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text(
                    stringResource(R.string.confirm_metadata),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(stringResource(R.string.download_audio_title), style = MaterialTheme.typography.headlineSmall)
            }
        },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(PolentitaSpacing.medium),
            ) {
                Artwork(
                    info.thumbnailUrl,
                    stringResource(R.string.artwork_thumbnail_description, info.title),
                    Modifier.size(196.dp).align(Alignment.CenterHorizontally),
                    seed = "${info.title}|${info.artist}",
                    elevated = true,
                )
                Surface(
                    shape = RoundedCornerShape(PolentitaRadii.medium),
                    color = MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.82f),
                    border = BorderStroke(
                        1.dp,
                        MaterialTheme.colorScheme.outlineVariant.copy(alpha = PolentitaOpacity.border),
                    ),
                ) {
                    Column(
                        Modifier.padding(PolentitaSpacing.medium),
                        verticalArrangement = Arrangement.spacedBy(PolentitaSpacing.xs),
                    ) {
                        Text(
                            stringResource(
                                R.string.search_explore_provider_detected,
                                info.extractor.ifBlank { "yt-dlp" },
                            ),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        stringResource(
                            R.string.search_explore_duration,
                            formatDuration(info.durationMs),
                        ).let { duration ->
                            Text(duration, style = MaterialTheme.typography.bodySmall)
                        }
                        Text(
                            if (info.sizeBytes >= 0) {
                                stringResource(
                                    R.string.search_explore_size_approx,
                                    com.polentita.music.core.common.formatBytes(info.sizeBytes),
                                )
                            } else {
                                stringResource(R.string.search_explore_size_unknown)
                            },
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
                OutlinedTextField(
                    state = titleState,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.field_title)) },
                    lineLimits = TextFieldLineLimits.SingleLine,
                    scrollState = titleScrollState,
                    shape = RoundedCornerShape(PolentitaRadii.medium),
                    colors = polentitaOutlinedTextFieldColors(),
                )
                ArtistSelector(
                    value = artist,
                    artists = artists,
                    onValueChange = {
                        artist = it
                        selectedAlbumId = null
                    },
                    onArtistSelected = {
                        artist = it
                        selectedAlbumId = null
                    },
                )
                AlbumSelector(
                    value = album,
                    albums = albums,
                    onValueChange = {
                        album = it
                        selectedAlbumId = null
                    },
                    onAlbumSelected = {
                        album = it.name
                        selectedAlbumId = it.id
                    },
                )
                if (!downloadEnabled && blockedMessage != null) {
                    Text(
                        blockedMessage,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        confirmButton = {
            Button(
                enabled = downloadEnabled,
                onClick = {
                    onConfirm(titleState.text.toString().trim(), artist.trim(), album.trim(), selectedAlbumId)
                },
            ) {
                Icon(Icons.Default.Download, contentDescription = null)
                Spacer(Modifier.width(PolentitaSpacing.small))
                Text(stringResource(R.string.download))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}
