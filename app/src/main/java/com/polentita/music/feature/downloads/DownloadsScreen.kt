package com.polentita.music.feature.downloads

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.PopupProperties
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.res.stringResource
import com.polentita.music.R
import com.polentita.music.core.common.formatBytes
import com.polentita.music.core.common.formatDuration
import com.polentita.music.core.database.DownloadEntity
import com.polentita.music.core.database.AlbumEntity
import com.polentita.music.core.database.DownloadProvider
import com.polentita.music.core.database.DownloadStatus
import com.polentita.music.core.designsystem.Artwork
import com.polentita.music.core.designsystem.EmptyState
import com.polentita.music.core.designsystem.PolentitaAlertDialog
import com.polentita.music.core.designsystem.PolentitaDropdownMenu
import com.polentita.music.core.designsystem.PolentitaMotion
import com.polentita.music.core.designsystem.PolentitaOpacity
import com.polentita.music.core.designsystem.PolentitaRadii
import com.polentita.music.core.designsystem.PolentitaSpacing
import com.polentita.music.core.designsystem.PolentitaStatusPill
import com.polentita.music.core.designsystem.PolentitaStatusTone
import com.polentita.music.core.designsystem.polentitaOutlinedTextFieldColors
import com.polentita.music.domain.model.Song
import com.polentita.music.data.extractor.YtDlpSearchResult
import com.polentita.music.feature.player.PlayerViewModel

enum class DownloadInputTab { SEARCH, PASTE_LINK }

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun DownloadsScreen(
    onBack: () -> Unit,
    onOpenSong: (Song) -> Unit,
    player: PlayerViewModel,
    viewModel: DownloadsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var inputTab by rememberSaveable { mutableStateOf(DownloadInputTab.SEARCH) }
    var url by rememberSaveable { mutableStateOf("") }
    var artist by rememberSaveable { mutableStateOf("") }
    var album by rememberSaveable { mutableStateOf("") }
    var selectedAlbumId by rememberSaveable { mutableStateOf<Long?>(null) }
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}

    LaunchedEffect(state.songToOpen?.id) {
        state.songToOpen?.let {
            onOpenSong(it)
            viewModel.consumeSongToOpen()
        }
    }

    LaunchedEffect(state.preview.preview?.streamUrl) {
        state.preview.preview?.let { preview ->
            player.playPreview(preview)
            viewModel.clearPreview()
        }
    }

    LaunchedEffect(state.remoteSearchError) {
        if (state.remoteSearchError != null) {
            kotlinx.coroutines.delay(3_000)
            viewModel.clearRemoteSearchError()
        }
    }

    LaunchedEffect(state.preview.status, state.preview.message) {
        if (state.preview.status == DownloadPreviewStatus.ERROR) {
            kotlinx.coroutines.delay(3_000)
            viewModel.clearPreview()
        }
    }

    LaunchedEffect(state.error) {
        if (state.error != null) {
            kotlinx.coroutines.delay(3_000)
            viewModel.clearError()
        }
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
                    .padding(
                        start = PolentitaSpacing.xs,
                        top = PolentitaSpacing.small,
                        end = PolentitaSpacing.large,
                        bottom = PolentitaSpacing.small,
                    ),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back))
                }
                Column {
                    Text(
                        stringResource(R.string.downloads_title),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Black,
                    )
                    Text(
                        stringResource(R.string.downloads_subtitle),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        DownloadInputSelector(
            selected = inputTab,
            onSelected = { inputTab = it },
        )
        if (state.networkAccess.offlineMode) {
            OfflineDownloadsBanner()
        } else if (!state.networkAccess.downloadAllowed && state.networkAccess.connected) {
            Text(
                stringResource(R.string.network_wifi_required),
                modifier = Modifier.padding(horizontal = PolentitaSpacing.medium),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (inputTab == DownloadInputTab.SEARCH) {
            RemoteSearchForm(
                state = state,
                onQueryChange = viewModel::setRemoteSearchQuery,
                onSearch = viewModel::submitRemoteSearch,
                onRetry = viewModel::retryRemoteSearch,
                onClearRemoteSearchError = viewModel::clearRemoteSearchError,
                onClearPreviewError = viewModel::clearPreview,
                onLoadMore = viewModel::loadMoreRemoteResults,
                previewingUrl = state.preview.url.takeIf {
                    state.preview.status == DownloadPreviewStatus.LOADING
                },
                onPreview = viewModel::preview,
                onUse = { result ->
                    url = viewModel.useSearchResult(result)
                    selectedAlbumId = null
                    viewModel.clearInspection()
                    viewModel.inspectWithYtDlp(url)
                },
            )
        } else {
            YtDlpDownloadForm(
                state = state,
                url = url,
                setUrl = { url = it },
                onInspect = viewModel::inspectWithYtDlp,
            )
        }
    }

    state.ytDlpInspected?.let { info ->
        val titleState = rememberTextFieldState(initialText = info.title)
        val titleScrollState = rememberScrollState()
        LaunchedEffect(info.id, state.albums) {
            artist = info.artist
            album = info.album
            selectedAlbumId = state.albums.firstOrNull {
                it.name.equals(info.album, ignoreCase = true)
            }?.id
        }
        PolentitaAlertDialog(
            onDismissRequest = viewModel::clearInspection,
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
                                    R.string.download_provider,
                                    info.extractor.ifBlank { stringResource(R.string.download_unknown_provider) },
                                ),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            Text(
                                stringResource(
                                    R.string.search_explore_duration,
                                    formatDuration(info.durationMs),
                                ),
                                style = MaterialTheme.typography.bodySmall,
                            )
                            Text(
                                stringResource(
                                    R.string.download_output,
                                    if (info.extension.lowercase() in setOf("mp4", "mov", "mkv")) {
                                        stringResource(R.string.download_audio_only_output)
                                    } else {
                                        info.extension.ifBlank {
                                            stringResource(R.string.download_auto_audio_output)
                                        }
                                    },
                                ),
                                style = MaterialTheme.typography.bodySmall,
                            )
                            Text(
                                if (info.sizeBytes >= 0) {
                                    stringResource(
                                        R.string.search_explore_size_approx,
                                        formatBytes(info.sizeBytes),
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
                        artists = state.artists,
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
                        albums = state.albums,
                        onValueChange = {
                            album = it
                            selectedAlbumId = null
                        },
                        onAlbumSelected = {
                            album = it.name
                            selectedAlbumId = it.id
                        },
                    )
                    if (!state.networkAccess.downloadAllowed) {
                        Text(
                            state.networkAccess.downloadBlockReason
                                ?.let { stringResource(it.messageRes) }
                                .orEmpty(),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    enabled = state.networkAccess.downloadAllowed,
                    onClick = {
                        if (Build.VERSION.SDK_INT >= 33) {
                            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                        viewModel.enqueueYtDlp(
                            titleState.text.toString(),
                            artist,
                            album,
                            selectedAlbumId,
                        )
                        url = ""
                        artist = ""
                        album = ""
                        selectedAlbumId = null
                    },
                ) {
                    Icon(Icons.Default.Download, contentDescription = null)
                    Spacer(Modifier.width(PolentitaSpacing.small))
                    Text(stringResource(R.string.download))
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::clearInspection) { Text(stringResource(R.string.cancel)) }
            },
        )
    }
}

@Composable
private fun DownloadInputSelector(
    selected: DownloadInputTab,
    onSelected: (DownloadInputTab) -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = PolentitaSpacing.medium, vertical = PolentitaSpacing.small),
        shape = RoundedCornerShape(PolentitaRadii.medium),
        color = MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.78f),
        border = BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = PolentitaOpacity.border),
        ),
        tonalElevation = 0.dp,
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(44.dp)
                .padding(PolentitaSpacing.xs),
        ) {
            DownloadInputTab.entries.forEach { tab ->
                val isSelected = selected == tab
                val indicatorColor by animateColorAsState(
                    targetValue = if (isSelected) {
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
                    } else {
                        Color.Transparent
                    },
                    animationSpec = tween(PolentitaMotion.quick),
                    label = "Indicador de descarga",
                )
                Tab(
                    selected = isSelected,
                    onClick = { onSelected(tab) },
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(PolentitaRadii.small))
                        .background(indicatorColor),
                    selectedContentColor = MaterialTheme.colorScheme.primary,
                    unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    text = {
                        Text(
                            if (tab == DownloadInputTab.SEARCH) {
                                stringResource(R.string.nav_search)
                            } else {
                                stringResource(R.string.download_paste_link_tab)
                            },
                            maxLines = 1,
                        )
                    },
                )
            }
        }
    }
}

@Composable
private fun RemoteSearchForm(
    state: DownloadsUiState,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    onRetry: () -> Unit,
    onClearRemoteSearchError: () -> Unit,
    onClearPreviewError: () -> Unit,
    onLoadMore: () -> Unit,
    previewingUrl: String?,
    onPreview: (String) -> Unit,
    onUse: (YtDlpSearchResult) -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = PolentitaSpacing.medium),
    ) {
        Text(
            stringResource(R.string.download_search_title),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(top = PolentitaSpacing.xs),
        )
        Text(
            stringResource(R.string.download_search_description),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = PolentitaSpacing.small),
        )
        OutlinedTextField(
            value = state.remoteSearchQuery,
            onValueChange = onQueryChange,
            enabled = state.networkAccess.remoteSearchAllowed,
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.download_search_label)) },
            placeholder = { Text(stringResource(R.string.download_search_example)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { onSearch() }),
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            trailingIcon = {
                IconButton(
                    onClick = onSearch,
                    enabled = state.networkAccess.remoteSearchAllowed && !state.remoteSearching,
                ) {
                    Icon(Icons.Default.Search, stringResource(R.string.download_search_action))
                }
            },
            shape = RoundedCornerShape(PolentitaRadii.medium),
            colors = polentitaOutlinedTextFieldColors(),
        )
        if (state.remoteSearching) {
            LinearProgressIndicator(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = PolentitaSpacing.small)
                    .height(2.dp),
            )
        }
        state.remoteSearchError?.let { error ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = PolentitaSpacing.medium),
                shape = RoundedCornerShape(PolentitaRadii.medium),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.22f),
                ),
                border = BorderStroke(
                    1.dp,
                    MaterialTheme.colorScheme.error.copy(alpha = 0.28f),
                ),
            ) {
                Column(Modifier.padding(PolentitaSpacing.medium)) {
                    Text(
                        error,
                        color = MaterialTheme.colorScheme.error,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                    TextButton(onClick = onRetry) { Text(stringResource(R.string.retry)) }
                    TextButton(onClick = onClearRemoteSearchError) { Text(stringResource(R.string.close)) }
                }
            }
        }
        state.preview.message?.let { message ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = PolentitaSpacing.small),
                shape = RoundedCornerShape(PolentitaRadii.medium),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.22f),
                ),
                border = BorderStroke(
                    1.dp,
                    MaterialTheme.colorScheme.error.copy(alpha = 0.28f),
                ),
            ) {
                Column(Modifier.padding(PolentitaSpacing.medium)) {
                    Text(
                        message,
                        color = MaterialTheme.colorScheme.error,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                    TextButton(onClick = onClearPreviewError) { Text(stringResource(R.string.close)) }
                }
            }
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = PolentitaSpacing.small, bottom = 112.dp),
            verticalArrangement = Arrangement.spacedBy(PolentitaSpacing.small),
        ) {
            if (!state.remoteSearching &&
                state.remoteSearchResults.isEmpty() &&
                state.remoteSearchQuery.isBlank() &&
                !state.networkAccess.offlineMode
            ) {
                item(key = "initial-download-search") {
                    Box(Modifier.fillMaxWidth().height(210.dp)) {
                        EmptyState(
                            stringResource(R.string.downloads_initial_title),
                            stringResource(R.string.downloads_initial_message),
                        )
                    }
                }
            }
            if (state.remoteSearching && state.remoteSearchResults.isEmpty()) {
                items(2, key = { index -> "download-skeleton-$index" }) {
                    DownloadResultSkeleton()
                }
            }
            if (!state.remoteSearching &&
                state.remoteSearchError == null &&
                state.remoteSearchResults.isEmpty() &&
                state.remoteSearchQuery.trim().length >= DownloadsViewModel.MIN_SEARCH_LENGTH
            ) {
                item(key = "empty-results") {
                    Box(Modifier.fillMaxWidth().height(260.dp)) {
                        EmptyState(
                            stringResource(R.string.downloads_no_results_title),
                            stringResource(R.string.downloads_no_results_message),
                        )
                    }
                }
            }
            items(
                state.remoteSearchResults,
                key = { result -> result.id.ifBlank { result.webpageUrl } },
            ) { result ->
                RemoteSearchResultCard(
                    result = result,
                    previewing = result.webpageUrl == previewingUrl,
                    onPreview = onPreview,
                    onUse = onUse,
                    previewEnabled = state.networkAccess.previewAllowed,
                    downloadEnabled = state.networkAccess.downloadAllowed,
                    artworkEnabled = state.networkAccess.remoteSearchAllowed,
                )
            }
            if (state.remoteSearchHasMore) {
                item {
                    Button(
                        onClick = onLoadMore,
                        enabled = !state.remoteLoadingMore,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = PolentitaSpacing.medium),
                    ) {
                        if (state.remoteLoadingMore) {
                            CircularProgressIndicator(Modifier.size(20.dp))
                        }
                        Text(
                            stringResource(
                                if (state.remoteLoadingMore) {
                                    R.string.search_explore_loading_more
                                } else {
                                    R.string.search_explore_load_more
                                },
                            ),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DownloadResultSkeleton() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(PolentitaRadii.large),
        color = MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.62f),
        border = BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.16f),
        ),
    ) {
        Column(Modifier.padding(PolentitaSpacing.medium)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(84.dp)
                        .clip(RoundedCornerShape(PolentitaRadii.medium))
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.58f)),
                )
                Spacer(Modifier.width(PolentitaSpacing.medium))
                Column(
                    Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(PolentitaSpacing.small),
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth(0.92f)
                            .height(16.dp)
                            .clip(RoundedCornerShape(PolentitaRadii.small))
                            .background(MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.58f)),
                    )
                    Box(
                        Modifier
                            .fillMaxWidth(0.62f)
                            .height(12.dp)
                            .clip(RoundedCornerShape(PolentitaRadii.small))
                            .background(MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.42f)),
                    )
                }
            }
            Row(
                Modifier.padding(top = PolentitaSpacing.medium),
                horizontalArrangement = Arrangement.spacedBy(PolentitaSpacing.small),
            ) {
                repeat(2) {
                    Box(
                        Modifier
                            .weight(1f)
                            .height(44.dp)
                            .clip(RoundedCornerShape(PolentitaRadii.pill))
                            .background(MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.44f)),
                    )
                }
            }
        }
    }
}

@Composable
private fun RemoteSearchResultCard(
    result: YtDlpSearchResult,
    previewing: Boolean,
    onPreview: (String) -> Unit,
    onUse: (YtDlpSearchResult) -> Unit,
    previewEnabled: Boolean,
    downloadEnabled: Boolean,
    artworkEnabled: Boolean,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(PolentitaRadii.large),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.84f),
        ),
        border = BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = PolentitaOpacity.border),
        ),
    ) {
        Column(Modifier.padding(PolentitaSpacing.small)) {
            Row {
                Artwork(
                    result.thumbnailUrl.takeIf { artworkEnabled },
                    stringResource(R.string.artwork_thumbnail_description, result.title),
                    Modifier.size(84.dp),
                    seed = "${result.title}|${result.channel}",
                )
                Spacer(Modifier.width(PolentitaSpacing.medium))
                Column(Modifier.weight(1f)) {
                    Text(
                        result.title,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        result.channel,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        "${formatDuration(result.durationMs)} · ${formatUploadDate(result.uploadDate)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(top = PolentitaSpacing.medium),
                horizontalArrangement = Arrangement.spacedBy(PolentitaSpacing.small),
            ) {
                OutlinedButton(
                    onClick = { onPreview(result.webpageUrl) },
                    enabled = previewEnabled && !previewing,
                    modifier = Modifier.weight(1f),
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
                Button(
                    onClick = { onUse(result) },
                    enabled = downloadEnabled,
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Default.Download, contentDescription = null)
                    Text(stringResource(R.string.download))
                }
            }
        }
    }
}

@Composable
private fun formatUploadDate(raw: String?): String =
    raw?.takeIf { it.length == 8 && it.all(Char::isDigit) }?.let {
        "${it.substring(6, 8)}/${it.substring(4, 6)}/${it.substring(0, 4)}"
    } ?: stringResource(R.string.date_unavailable)

@Composable
private fun YtDlpDownloadForm(
    state: DownloadsUiState,
    url: String,
    setUrl: (String) -> Unit,
    onInspect: (String) -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = PolentitaSpacing.large, vertical = PolentitaSpacing.small),
        verticalArrangement = Arrangement.spacedBy(PolentitaSpacing.medium),
    ) {
        Text(
            stringResource(R.string.download_audio_title),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
        )
        Surface(
            shape = RoundedCornerShape(PolentitaRadii.medium),
            color = MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.72f),
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
                    Icons.Default.Link,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.width(PolentitaSpacing.medium))
                Text(
                    stringResource(R.string.download_paste_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        OutlinedTextField(
            value = url,
            onValueChange = setUrl,
            enabled = !state.networkAccess.offlineMode,
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.download_paste_link_label)) },
            leadingIcon = { Icon(Icons.Default.Link, contentDescription = null) },
            singleLine = true,
            shape = RoundedCornerShape(PolentitaRadii.medium),
            colors = polentitaOutlinedTextFieldColors(),
        )
        Button(
            onClick = { onInspect(url) },
            enabled = url.isNotBlank() &&
                !state.inspectingWithYtDlp &&
                state.networkAccess.downloadAllowed,
            modifier = Modifier.align(Alignment.End),
        ) {
            if (state.inspectingWithYtDlp) {
                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
            } else {
                Icon(Icons.Default.Download, contentDescription = null)
            }
            Spacer(Modifier.width(PolentitaSpacing.small))
            Text(
                stringResource(
                    if (state.inspectingWithYtDlp) {
                        R.string.search_explore_download_preparing
                    } else {
                        R.string.download
                    },
                ),
            )
        }
        state.error?.let {
            Surface(
                shape = RoundedCornerShape(PolentitaRadii.medium),
                color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.22f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.28f)),
            ) {
                Text(
                    it,
                    Modifier.padding(PolentitaSpacing.medium),
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

@Composable
private fun OfflineDownloadsBanner() {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = PolentitaSpacing.medium, vertical = PolentitaSpacing.xs),
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
            Icon(Icons.Default.CloudOff, null, tint = MaterialTheme.colorScheme.primary)
            Column(Modifier.padding(start = PolentitaSpacing.small)) {
                Text(stringResource(R.string.network_offline_mode_active), fontWeight = FontWeight.Bold)
                Text(
                    stringResource(R.string.downloads_offline_message),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
internal fun ArtistSelector(
    value: String,
    artists: List<String>,
    onValueChange: (String) -> Unit,
    onArtistSelected: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val menuScrollState = rememberScrollState()
    val suggestions = remember(value, artists) {
        val query = value.trim()
        artists.asSequence()
            .filter { query.isBlank() || it.trim().startsWith(query, ignoreCase = true) }
            .distinctBy(String::lowercase)
            .toList()
    }
    Box(Modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = value,
            onValueChange = {
                onValueChange(it)
                expanded = artists.isNotEmpty()
            },
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.field_artist)) },
            supportingText = {
                Text(
                    stringResource(
                        if (artists.isEmpty()) {
                            R.string.download_no_artists_hint
                        } else {
                            R.string.download_existing_artists_hint
                        },
                    ),
                )
            },
            trailingIcon = {
                IconButton(
                    onClick = { expanded = !expanded },
                    enabled = artists.isNotEmpty(),
                ) {
                    Icon(
                        Icons.Default.ArrowDropDown,
                        stringResource(R.string.download_show_artists),
                    )
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(PolentitaRadii.medium),
            colors = polentitaOutlinedTextFieldColors(),
        )
        PolentitaDropdownMenu(
            expanded = expanded && suggestions.isNotEmpty(),
            onDismissRequest = { expanded = false },
            modifier = Modifier
                .widthIn(min = 280.dp, max = 360.dp)
                .heightIn(max = 280.dp),
            scrollState = menuScrollState,
            properties = PopupProperties(focusable = false),
        ) {
            suggestions.forEach { existingArtist ->
                DropdownMenuItem(
                    text = {
                        Text(
                            existingArtist,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                    onClick = {
                        onArtistSelected(existingArtist)
                        expanded = false
                    },
                )
            }
        }
    }
}

@Composable
internal fun AlbumSelector(
    value: String,
    albums: List<AlbumEntity>,
    onValueChange: (String) -> Unit,
    onAlbumSelected: (AlbumEntity) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val menuScrollState = rememberScrollState()
    val suggestions = remember(value, albums) {
        val query = value.trim()
        albums.asSequence()
            .filter { query.isBlank() || it.name.trim().startsWith(query, ignoreCase = true) }
            .distinctBy { "${it.name.lowercase()}|${it.artist.lowercase()}" }
            .toList()
    }
    Box(Modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = value,
            onValueChange = {
                onValueChange(it)
                expanded = albums.isNotEmpty()
            },
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.field_album)) },
            supportingText = {
                Text(
                    if (albums.isEmpty()) {
                        stringResource(R.string.download_no_albums_hint)
                    } else {
                        stringResource(R.string.download_existing_albums_hint)
                    },
                )
            },
            trailingIcon = {
                IconButton(
                    onClick = { expanded = !expanded },
                    enabled = albums.isNotEmpty(),
                ) {
                    Icon(
                        Icons.Default.ArrowDropDown,
                        stringResource(R.string.download_show_albums),
                    )
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(PolentitaRadii.medium),
            colors = polentitaOutlinedTextFieldColors(),
        )
        PolentitaDropdownMenu(
            expanded = expanded && suggestions.isNotEmpty(),
            onDismissRequest = { expanded = false },
            modifier = Modifier
                .widthIn(min = 280.dp, max = 360.dp)
                .heightIn(max = 280.dp),
            scrollState = menuScrollState,
            properties = PopupProperties(focusable = false),
        ) {
            suggestions.forEach { existingAlbum ->
                DropdownMenuItem(
                    text = {
                        Column {
                            Text(existingAlbum.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            if (existingAlbum.artist.isNotBlank()) {
                                Text(
                                    existingAlbum.artist,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    },
                    onClick = {
                        onAlbumSelected(existingAlbum)
                        expanded = false
                    },
                )
            }
        }
    }
}

@Composable
private fun DownloadHistory(
    state: DownloadsUiState,
    onRetry: (String) -> Unit,
    onCancel: (String) -> Unit,
    onDelete: (String) -> Unit,
    onOpen: (String) -> Unit,
) {
    if (state.downloads.isEmpty()) {
        EmptyState(
            stringResource(R.string.download_history_empty_title),
            stringResource(R.string.download_history_empty_message),
        )
        return
    }
    LazyColumn(
        contentPadding = PaddingValues(
            start = PolentitaSpacing.medium,
            top = PolentitaSpacing.small,
            end = PolentitaSpacing.medium,
            bottom = 112.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(PolentitaSpacing.small),
    ) {
        state.error?.let { error ->
            item { Text(error, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(8.dp)) }
        }
        items(state.downloads, key = DownloadEntity::id) { download ->
            DownloadHistoryCard(download, onRetry, onCancel, onDelete, onOpen)
        }
    }
}

@Composable
private fun DownloadHistoryCard(
    download: DownloadEntity,
    onRetry: (String) -> Unit,
    onCancel: (String) -> Unit,
    onDelete: (String) -> Unit,
    onOpen: (String) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(PolentitaRadii.large),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.84f),
        ),
        border = BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = PolentitaOpacity.border),
        ),
    ) {
        Column(Modifier.padding(PolentitaSpacing.medium)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(PolentitaSpacing.small),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(download.title, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                PolentitaStatusPill(
                    text = localizedDownloadStatus(download.status),
                    tone = downloadStatusTone(download.status),
                )
            }
            Text(
                if (download.providerId == DownloadProvider.YT_DLP) {
                    stringResource(R.string.download_source_ytdlp)
                } else {
                    stringResource(R.string.download_source_direct)
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                "${formatBytes(download.bytesDownloaded)} / " +
                    if (download.totalBytes >= 0) {
                        formatBytes(download.totalBytes)
                    } else {
                        stringResource(R.string.download_unknown_size)
                    },
                style = MaterialTheme.typography.bodySmall,
            )
            if (download.status == DownloadStatus.DOWNLOADING.name ||
                download.status == DownloadStatus.PENDING.name ||
                download.status == DownloadStatus.VALIDATING.name ||
                download.status == DownloadStatus.SAVING.name
            ) {
                LinearProgressIndicator(
                    progress = { download.progress / 100f },
                    modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                )
                Text("${formatBytes(download.speedBytesPerSecond)}/s")
            }
            download.errorMessage?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                if (download.status == DownloadStatus.COMPLETED.name && download.destinationUri != null) {
                    IconButton(onClick = { onOpen(download.id) }) {
                        Icon(Icons.Default.PlayArrow, stringResource(R.string.download_open_song))
                    }
                }
                if (download.status == DownloadStatus.FAILED.name ||
                    download.status == DownloadStatus.CANCELLED.name
                ) {
                    IconButton(onClick = { onRetry(download.id) }) {
                        Icon(Icons.Default.Refresh, stringResource(R.string.download_retry))
                    }
                }
                if (download.status == DownloadStatus.DOWNLOADING.name ||
                    download.status == DownloadStatus.PENDING.name ||
                    download.status == DownloadStatus.VALIDATING.name ||
                    download.status == DownloadStatus.SAVING.name
                ) {
                    IconButton(onClick = { onCancel(download.id) }) {
                        Icon(Icons.Default.Cancel, stringResource(R.string.download_cancel))
                    }
                }
                IconButton(onClick = { onDelete(download.id) }) {
                    Icon(Icons.Default.Delete, stringResource(R.string.download_remove_history))
                }
            }
        }
    }
}

@Composable
private fun localizedDownloadStatus(status: String): String = when (status) {
    DownloadStatus.COMPLETED.name -> stringResource(R.string.download_status_completed)
    DownloadStatus.FAILED.name -> stringResource(R.string.download_status_failed)
    DownloadStatus.CANCELLED.name -> stringResource(R.string.download_status_cancelled)
    DownloadStatus.PAUSED.name -> stringResource(R.string.download_status_paused)
    DownloadStatus.PENDING.name -> stringResource(R.string.download_status_pending)
    DownloadStatus.DOWNLOADING.name -> stringResource(R.string.download_status_downloading)
    DownloadStatus.VALIDATING.name -> stringResource(R.string.download_status_validating)
    DownloadStatus.SAVING.name -> stringResource(R.string.download_status_saving)
    else -> status.lowercase().replaceFirstChar(Char::titlecase)
}

private fun downloadStatusTone(status: String): PolentitaStatusTone = when (status) {
    DownloadStatus.COMPLETED.name -> PolentitaStatusTone.SUCCESS
    DownloadStatus.FAILED.name,
    DownloadStatus.CANCELLED.name,
    -> PolentitaStatusTone.ERROR
    DownloadStatus.PAUSED.name -> PolentitaStatusTone.WARNING
    DownloadStatus.PENDING.name,
    DownloadStatus.DOWNLOADING.name,
    DownloadStatus.VALIDATING.name,
    DownloadStatus.SAVING.name,
    -> PolentitaStatusTone.ACCENT
    else -> PolentitaStatusTone.NEUTRAL
}
