package com.polentita.music.feature.playlistimport

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlaylistAdd
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.polentita.music.R
import com.polentita.music.core.common.formatDuration
import com.polentita.music.core.database.PlaylistImportCandidateEntity
import com.polentita.music.core.database.PlaylistImportItemEntity
import com.polentita.music.core.designsystem.Artwork
import com.polentita.music.core.designsystem.EmptyState
import com.polentita.music.core.designsystem.PolentitaAlertDialog
import com.polentita.music.core.designsystem.PolentitaOpacity
import com.polentita.music.core.designsystem.PolentitaRadii
import com.polentita.music.core.designsystem.PolentitaMetricCard
import com.polentita.music.core.designsystem.PolentitaSectionHeader
import com.polentita.music.core.designsystem.PolentitaSpacing
import com.polentita.music.core.designsystem.PolentitaStatusPill
import com.polentita.music.core.designsystem.PolentitaStatusTone
import com.polentita.music.core.designsystem.polentitaOutlinedTextFieldColors
import com.polentita.music.data.playlistimport.PlaylistImportItemSnapshot
import com.polentita.music.data.playlistimport.PlaylistImportSnapshot
import com.polentita.music.domain.playlistimport.PlaylistImportItemState
import com.polentita.music.domain.playlistimport.PlaylistImportState

private enum class ImportItemFilter { ALL, PENDING, COMPLETED, ERROR }

@Composable
fun PlaylistImportScreen(
    onBack: () -> Unit,
    onOpenPlaylist: (Long) -> Unit,
    viewModel: PlaylistImportViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val selected = state.selected
    if (selected == null) {
        ImportLanding(
            state = state,
            onBack = onBack,
            onAnalyzeLink = viewModel::analyzeLink,
            onAnalyzeFile = viewModel::analyzeFile,
            onOpenImport = viewModel::openImport,
            onClearMessage = viewModel::clearMessage,
        )
    } else {
        ImportDetail(
            snapshot = selected,
            state = state,
            onBack = {
                if (!viewModel.closeImport()) onBack()
            },
            onOpenPlaylist = onOpenPlaylist,
            onSetSelected = viewModel::setSelected,
            onConfirmLocalMatch = viewModel::confirmLocalMatch,
            onCreatePlaylist = viewModel::createPlaylist,
            onUpdateCollection = viewModel::updateCollection,
            onResolve = viewModel::resolveMissing,
            onSelectCandidate = viewModel::selectCandidate,
            onOmit = viewModel::omit,
            onStart = viewModel::start,
            onPause = viewModel::pause,
            onResume = viewModel::resume,
            onCancel = viewModel::cancelImport,
            onRetry = viewModel::retryErrors,
            onClearMessage = viewModel::clearMessage,
        )
    }
}

@Composable
private fun ImportLanding(
    state: PlaylistImportUiState,
    onBack: () -> Unit,
    onAnalyzeLink: (String) -> Unit,
    onAnalyzeFile: (android.net.Uri) -> Unit,
    onOpenImport: (String) -> Unit,
    onClearMessage: () -> Unit,
) {
    var link by remember { mutableStateOf("") }
    var showFileHelp by remember { mutableStateOf(false) }
    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(onAnalyzeFile)
    }
    Column(Modifier.fillMaxSize()) {
        ImportTopBar(stringResource(R.string.playlist_import_title), onBack)
        LazyColumn(
            contentPadding = PaddingValues(
                start = PolentitaSpacing.large,
                end = PolentitaSpacing.large,
                top = PolentitaSpacing.medium,
                bottom = 112.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(PolentitaSpacing.medium),
        ) {
            item(key = "intro") {
                ImportCard {
                    Text(
                        stringResource(R.string.playlist_import_subtitle),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Black,
                    )
                    Text(
                        stringResource(R.string.playlist_import_explanation),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    ImportJourney(currentStep = 0)
                    PolentitaStatusPill(
                        text = stringResource(R.string.playlist_import_analysis_safe),
                        tone = PolentitaStatusTone.SUCCESS,
                    )
                }
            }
            item(key = "url") {
                ImportCard {
                    OutlinedTextField(
                        value = link,
                        onValueChange = { link = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(stringResource(R.string.playlist_import_link_label)) },
                        placeholder = { Text(stringResource(R.string.playlist_import_link_placeholder)) },
                        leadingIcon = { Icon(Icons.Default.Link, null) },
                        singleLine = true,
                        shape = RoundedCornerShape(PolentitaRadii.medium),
                        colors = polentitaOutlinedTextFieldColors(),
                    )
                    Button(
                        onClick = { onAnalyzeLink(link) },
                        enabled = link.isNotBlank() && !state.busy,
                        modifier = Modifier.fillMaxWidth().padding(top = PolentitaSpacing.small),
                    ) {
                        if (state.busy) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                        Text(stringResource(R.string.playlist_import_analyze))
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = PolentitaSpacing.small),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(PolentitaSpacing.small),
                    ) {
                        OutlinedButton(
                            onClick = {
                                filePicker.launch(arrayOf("application/json", "text/csv", "text/plain"))
                            },
                            enabled = !state.busy,
                            modifier = Modifier.weight(1f),
                        ) {
                            Icon(Icons.Default.InsertDriveFile, null)
                            Text(stringResource(R.string.playlist_import_choose_file))
                        }
                        Surface(
                            onClick = { showFileHelp = true },
                            enabled = !state.busy,
                            modifier = Modifier.size(48.dp),
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                            contentColor = MaterialTheme.colorScheme.primary,
                            border = BorderStroke(
                                1.dp,
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.42f),
                            ),
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Default.Info,
                                    contentDescription = stringResource(
                                        R.string.playlist_import_help_button_description,
                                    ),
                                )
                            }
                        }
                    }
                }
            }
            item(key = "providers-title") {
                PolentitaSectionHeader(
                    title = stringResource(R.string.playlist_import_providers),
                    subtitle = stringResource(R.string.playlist_import_providers_hint),
                )
            }
            items(state.providers, key = ImportProviderUi::name) { provider ->
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(PolentitaRadii.medium),
                    color = MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.82f),
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
                            if (provider.configured) Icons.Default.CheckCircle else Icons.Default.ErrorOutline,
                            null,
                            tint = if (provider.configured) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        )
                        Column(Modifier.weight(1f).padding(horizontal = PolentitaSpacing.small)) {
                            Text(provider.name, fontWeight = FontWeight.Medium)
                            provider.message?.let {
                                Text(
                                    it,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        PolentitaStatusPill(
                            text = stringResource(
                                if (provider.configured) R.string.playlist_import_ready
                                else R.string.playlist_import_not_configured,
                            ),
                            tone = if (provider.configured) {
                                PolentitaStatusTone.SUCCESS
                            } else {
                                PolentitaStatusTone.WARNING
                            },
                        )
                    }
                }
            }
            item(key = "history-title") {
                PolentitaSectionHeader(
                    title = stringResource(R.string.playlist_import_history),
                    subtitle = stringResource(R.string.playlist_import_history_hint),
                    modifier = Modifier.padding(top = PolentitaSpacing.small),
                )
            }
            if (state.history.isEmpty()) {
                item(key = "history-empty") {
                    EmptyState(
                        stringResource(R.string.playlist_import_history),
                        stringResource(R.string.playlist_import_history_empty),
                        Modifier.height(180.dp),
                    )
                }
            } else {
                items(state.history, key = { it.id }) { imported ->
                    Surface(
                        onClick = { onOpenImport(imported.id) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(PolentitaRadii.medium),
                        color = MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.82f),
                    ) {
                        Row(
                            Modifier.padding(PolentitaSpacing.small),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Artwork(
                                artworkForDisplay(imported.artworkUrl, state.network.remoteSearchAllowed),
                                stringResource(R.string.home_playlist_cover_description, imported.name),
                                Modifier.size(56.dp),
                                seed = imported.name,
                            )
                            Column(Modifier.weight(1f).padding(horizontal = PolentitaSpacing.small)) {
                                Text(imported.name, fontWeight = FontWeight.Medium, maxLines = 1)
                                Text(
                                    "${imported.source.lowercase().replaceFirstChar(Char::titlecase)} · " +
                                        stringResource(R.string.playlist_import_track_count, imported.totalTracks),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                PolentitaStatusPill(
                                    text = importStateLabel(imported.state),
                                    tone = importTone(imported.state),
                                    modifier = Modifier.padding(top = PolentitaSpacing.xs),
                                )
                            }
                            Icon(Icons.Default.ChevronRight, contentDescription = null)
                        }
                    }
                }
            }
        }
    }
    if (showFileHelp) {
        PlaylistImportHelpDialog(onDismiss = { showFileHelp = false })
    }
    state.message?.let { ErrorDialog(it, onClearMessage) }
}

@Composable
private fun ImportDetail(
    snapshot: PlaylistImportSnapshot,
    state: PlaylistImportUiState,
    onBack: () -> Unit,
    onOpenPlaylist: (Long) -> Unit,
    onSetSelected: (String, Boolean) -> Unit,
    onConfirmLocalMatch: (String) -> Unit,
    onCreatePlaylist: (String, String?) -> Unit,
    onUpdateCollection: (String, String?) -> Unit,
    onResolve: () -> Unit,
    onSelectCandidate: (String, String) -> Unit,
    onOmit: (String) -> Unit,
    onStart: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onCancel: () -> Unit,
    onRetry: () -> Unit,
    onClearMessage: () -> Unit,
) {
    val imported = snapshot.imported
    var name by remember(imported.id) { mutableStateOf(imported.name) }
    var artwork by remember(imported.id) { mutableStateOf(imported.artworkUrl) }
    var filter by remember { mutableStateOf(ImportItemFilter.ALL) }
    var candidateItem by remember { mutableStateOf<PlaylistImportItemSnapshot?>(null) }
    var confirmStart by remember { mutableStateOf(false) }
    var confirmCancel by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val analyzedTrackCount = snapshot.items.size
    val pendingSourceTrackCount = (imported.totalTracks - analyzedTrackCount).coerceAtLeast(0)
    val coverPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        runCatching {
            context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        artwork = uri.toString()
    }
    val active = imported.state in ACTIVE_IMPORT_STATES
    val terminal = imported.state in TERMINAL_IMPORT_STATES
    val currentItem = snapshot.items.firstOrNull { it.item.state in ACTIVE_ITEM_STATES }
    val visibleItems = remember(snapshot.items, filter) {
        snapshot.items.filter { row ->
            when (filter) {
                ImportItemFilter.ALL -> true
                ImportItemFilter.PENDING -> row.item.state in PENDING_ITEM_STATES
                ImportItemFilter.COMPLETED -> row.item.state in PlaylistImportSnapshot.COMPLETED_ITEM_STATES
                ImportItemFilter.ERROR -> row.item.state in ERROR_ITEM_STATES
            }
        }
    }

    Column(Modifier.fillMaxSize()) {
        ImportTopBar(
            if (active) stringResource(R.string.playlist_import_progress_title)
            else stringResource(R.string.playlist_import_title),
            onBack,
        )
        LazyColumn(
            contentPadding = PaddingValues(
                start = PolentitaSpacing.medium,
                end = PolentitaSpacing.medium,
                bottom = 112.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(PolentitaSpacing.small),
        ) {
            item(key = "journey") {
                ImportJourney(
                    currentStep = when {
                        active || terminal -> 2
                        imported.localPlaylistId != null -> 1
                        else -> 1
                    },
                    modifier = Modifier.padding(top = PolentitaSpacing.small),
                )
            }
            item(key = "collection-header") {
                ImportCard(Modifier.padding(top = PolentitaSpacing.small)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Artwork(
                            artworkForDisplay(artwork, state.network.remoteSearchAllowed),
                            stringResource(R.string.home_playlist_cover_description, name),
                            Modifier.size(104.dp),
                            seed = name,
                        )
                        Column(Modifier.weight(1f).padding(start = PolentitaSpacing.medium)) {
                            Text(
                                imported.source.lowercase().replaceFirstChar(Char::titlecase),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            Text(
                                stringResource(R.string.playlist_import_track_count, imported.totalTracks),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            if (pendingSourceTrackCount > 0) {
                                Text(
                                    stringResource(
                                        R.string.playlist_import_partial_source_hint,
                                        analyzedTrackCount,
                                        imported.totalTracks,
                                        pendingSourceTrackCount,
                                    ),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                            Text(
                                stringResource(
                                    R.string.playlist_import_duration,
                                    formatDuration(snapshot.items.sumOf { it.item.durationMs }),
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            PolentitaStatusPill(
                                text = importStateLabel(imported.state),
                                tone = importTone(imported.state),
                                modifier = Modifier.padding(top = PolentitaSpacing.xs),
                            )
                        }
                    }
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        enabled = !active,
                        modifier = Modifier.fillMaxWidth().padding(top = PolentitaSpacing.medium),
                        label = { Text(stringResource(R.string.playlist_import_name)) },
                        singleLine = true,
                        shape = RoundedCornerShape(PolentitaRadii.medium),
                        colors = polentitaOutlinedTextFieldColors(),
                    )
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                    ) {
                        TextButton(onClick = { coverPicker.launch(arrayOf("image/*")) }, enabled = !active) {
                            Icon(Icons.Default.Edit, null)
                            Text(stringResource(R.string.playlist_import_change_cover))
                        }
                        if (imported.localPlaylistId != null && !active) {
                            TextButton(onClick = { onUpdateCollection(name, artwork) }) {
                                Text(stringResource(R.string.playlist_import_save))
                            }
                        }
                    }
                }
            }
            if (!state.network.remoteSearchAllowed || !state.network.downloadAllowed) {
                item(key = "offline") {
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.22f),
                        shape = RoundedCornerShape(PolentitaRadii.medium),
                    ) {
                        Text(
                            stringResource(R.string.playlist_import_offline),
                            modifier = Modifier.padding(PolentitaSpacing.medium),
                            color = MaterialTheme.colorScheme.onErrorContainer,
                        )
                    }
                }
            }
            if (active || terminal) {
                item(key = "progress") {
                    ImportProgressCard(snapshot, currentItem?.item, state)
                }
                item(key = "filters") {
                    ImportFilters(snapshot, filter, onSelect = { filter = it })
                }
            } else {
                item(key = "review-title") {
                    Text(
                        stringResource(R.string.playlist_import_review_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = PolentitaSpacing.small),
                    )
                }
            }
            items(visibleItems, key = { it.item.id }) { row ->
                ImportTrackCard(
                    row = row,
                    download = row.item.downloadId?.let(state.downloads::get),
                    editableSelection = imported.localPlaylistId == null,
                    onSetSelected = onSetSelected,
                    onConfirmLocalMatch = onConfirmLocalMatch,
                    onChangeCandidate = { candidateItem = row },
                    onOmit = onOmit,
                    remoteArtworkAllowed = state.network.remoteSearchAllowed,
                )
            }
            item(key = "actions") {
                ImportActions(
                    snapshot = snapshot,
                    busy = state.busy,
                    onCreate = { onCreatePlaylist(name, artwork) },
                    onResolve = onResolve,
                    onStart = { confirmStart = true },
                    onPause = onPause,
                    onResume = onResume,
                    onCancel = { confirmCancel = true },
                    onRetry = onRetry,
                    onReviewErrors = { filter = ImportItemFilter.ERROR },
                    onOpenPlaylist = onOpenPlaylist,
                )
            }
        }
    }
    candidateItem?.let { row ->
        CandidateDialog(
            row = row,
            onDismiss = { candidateItem = null },
            onSelect = { candidateId ->
                onSelectCandidate(row.item.id, candidateId)
                candidateItem = null
            },
        )
    }
    if (confirmStart) {
        val ready = snapshot.items.count { it.item.state == PlaylistImportItemState.IN_LIBRARY.name }
        val selectedCandidates = snapshot.items.count { it.selectedCandidate != null && it.item.selected }
        val review = snapshot.items.count { it.item.state in ERROR_ITEM_STATES }
        val omitted = snapshot.items.count { !it.item.selected || it.item.state == PlaylistImportItemState.OMITTED.name }
        PolentitaAlertDialog(
            onDismissRequest = { confirmStart = false },
            title = { Text(stringResource(R.string.playlist_import_confirm_title)) },
            text = {
                Text(
                    stringResource(
                        R.string.playlist_import_confirm_message,
                        ready,
                        selectedCandidates,
                        review,
                        omitted,
                    ),
                )
            },
            confirmButton = {
                Button(onClick = { confirmStart = false; onStart() }) {
                    Text(stringResource(R.string.playlist_import_process))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmStart = false }) {
                    Text(stringResource(R.string.playlist_import_cancel_short))
                }
            },
        )
    }
    if (confirmCancel) {
        PolentitaAlertDialog(
            onDismissRequest = { confirmCancel = false },
            title = { Text(stringResource(R.string.playlist_import_cancel_title)) },
            text = { Text(stringResource(R.string.playlist_import_cancel_message)) },
            confirmButton = {
                TextButton(onClick = { confirmCancel = false; onCancel() }) {
                    Text(stringResource(R.string.playlist_import_cancel), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmCancel = false }) {
                    Text(stringResource(R.string.playlist_import_return))
                }
            },
        )
    }
    state.message?.let { ErrorDialog(it, onClearMessage) }
}

@Composable
private fun ImportProgressCard(
    snapshot: PlaylistImportSnapshot,
    current: PlaylistImportItemEntity?,
    state: PlaylistImportUiState,
) {
    val total = snapshot.items.count { it.item.state != PlaylistImportItemState.DUPLICATE.name }
    val completed = snapshot.completedCount
    ImportCard {
        PolentitaSectionHeader(
            title = stringResource(R.string.playlist_import_progress, completed, total),
            subtitle = if (snapshot.imported.state == PlaylistImportState.PARTIAL.name) {
                stringResource(R.string.playlist_import_progress_partial_hint)
            } else {
                stringResource(R.string.playlist_import_auto_recovery_hint)
            },
        )
        LinearProgressIndicator(
            progress = { if (total == 0) 0f else completed.toFloat() / total },
            modifier = Modifier.fillMaxWidth().padding(vertical = PolentitaSpacing.small),
        )
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(PolentitaSpacing.small),
        ) {
            PolentitaMetricCard(
                value = completed.toString(),
                label = stringResource(R.string.playlist_import_metric_ready),
                tone = PolentitaStatusTone.SUCCESS,
                modifier = Modifier.weight(1f),
            )
            PolentitaMetricCard(
                value = (total - completed - snapshot.errorCount).coerceAtLeast(0).toString(),
                label = stringResource(R.string.playlist_import_metric_pending),
                tone = PolentitaStatusTone.ACCENT,
                modifier = Modifier.weight(1f),
            )
            PolentitaMetricCard(
                value = snapshot.errorCount.toString(),
                label = stringResource(R.string.playlist_import_metric_review),
                tone = if (snapshot.errorCount > 0) {
                    PolentitaStatusTone.WARNING
                } else {
                    PolentitaStatusTone.NEUTRAL
                },
                modifier = Modifier.weight(1f),
            )
        }
        current?.let {
            Text(stringResource(R.string.playlist_import_current, it.title), maxLines = 1)
            it.downloadId?.let(state.downloads::get)?.let { download ->
                if (download.progress > 0) Text("${download.progress}%")
            }
        }
        Text(
            stringResource(R.string.playlist_import_close_queue_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ImportFilters(
    snapshot: PlaylistImportSnapshot,
    selected: ImportItemFilter,
    onSelect: (ImportItemFilter) -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(PolentitaSpacing.small),
    ) {
        ImportItemFilter.entries.forEach { filter ->
            FilterChip(
                selected = selected == filter,
                onClick = { onSelect(filter) },
                label = {
                    val count = when (filter) {
                        ImportItemFilter.ALL -> snapshot.items.size
                        ImportItemFilter.PENDING -> snapshot.items.count { it.item.state in PENDING_ITEM_STATES }
                        ImportItemFilter.COMPLETED -> snapshot.completedCount
                        ImportItemFilter.ERROR -> snapshot.items.count { it.item.state in ERROR_ITEM_STATES }
                    }
                    Text(
                        stringResource(
                            R.string.playlist_import_filter_count,
                            stringResource(
                                when (filter) {
                                    ImportItemFilter.ALL -> R.string.playlist_import_all
                                    ImportItemFilter.PENDING -> R.string.playlist_import_pending
                                    ImportItemFilter.COMPLETED -> R.string.playlist_import_completed
                                    ImportItemFilter.ERROR -> R.string.playlist_import_with_error
                                },
                            ),
                            count,
                        ),
                    )
                },
            )
        }
    }
}

@Composable
private fun ImportTrackCard(
    row: PlaylistImportItemSnapshot,
    download: com.polentita.music.core.database.DownloadEntity?,
    editableSelection: Boolean,
    onSetSelected: (String, Boolean) -> Unit,
    onConfirmLocalMatch: (String) -> Unit,
    onChangeCandidate: () -> Unit,
    onOmit: (String) -> Unit,
    remoteArtworkAllowed: Boolean,
) {
    val item = row.item
    val tone = itemTone(item.state)
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(PolentitaRadii.medium),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.84f),
        ),
        border = BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = PolentitaOpacity.border),
        ),
    ) {
        Column(Modifier.padding(PolentitaSpacing.small)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (editableSelection && item.state !in PlaylistImportRepositoryTerminalStates) {
                    Checkbox(
                        checked = item.selected,
                        onCheckedChange = { onSetSelected(item.id, it) },
                    )
                }
                Artwork(
                    artworkForDisplay(item.artworkUrl, remoteArtworkAllowed),
                    stringResource(R.string.home_cover_description, item.title),
                    Modifier.size(54.dp),
                    seed = "${item.title}|${item.artists}",
                )
                Column(Modifier.weight(1f).padding(start = PolentitaSpacing.small)) {
                    Text(
                        item.title.ifBlank { stringResource(R.string.playlist_import_untitled) },
                        fontWeight = FontWeight.Medium,
                        maxLines = 2,
                    )
                    Text(
                        item.artists.replace("\u001F", ", ")
                            .ifBlank { stringResource(R.string.playlist_import_unknown_artist) },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                    Text(
                        "${item.originalPosition + 1} · ${formatDuration(item.durationMs)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                PolentitaStatusPill(text = itemStateLabel(item.state), tone = tone)
            }
            row.selectedCandidate?.let { candidate ->
                Text(
                    stringResource(R.string.playlist_import_candidate_selected, candidate.title),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = PolentitaSpacing.xs),
                    maxLines = 2,
                )
                Text(
                    "${candidate.artist} · ${formatDuration(candidate.durationMs)} · " +
                        stringResource(R.string.playlist_import_confidence, (candidate.score * 100).toInt()),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (download != null && item.state == PlaylistImportItemState.DOWNLOADING.name) {
                LinearProgressIndicator(
                    progress = { download.progress / 100f },
                    modifier = Modifier.fillMaxWidth().padding(top = PolentitaSpacing.small),
                )
            }
            item.errorMessage
                ?.takeIf { item.state !in ACTIVE_ITEM_STATES && item.state != PlaylistImportItemState.PENDING.name }
                ?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (item.state == PlaylistImportItemState.ERROR.name) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        modifier = Modifier.padding(top = PolentitaSpacing.xs),
                    )
                }
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                if (item.state == PlaylistImportItemState.PROBABLE_MATCH.name && item.localSongId != null) {
                    TextButton(onClick = { onConfirmLocalMatch(item.id) }) {
                        Text(stringResource(R.string.playlist_import_use_local))
                    }
                }
                if (
                    row.candidates.isNotEmpty() &&
                    item.state !in PlaylistImportSnapshot.COMPLETED_ITEM_STATES &&
                    item.state !in ACTIVE_ITEM_STATES &&
                    item.downloadId == null
                ) {
                    TextButton(onClick = onChangeCandidate) {
                        Text(stringResource(R.string.playlist_import_change_match))
                    }
                }
                if (item.state !in PlaylistImportSnapshot.COMPLETED_ITEM_STATES &&
                    item.state != PlaylistImportItemState.DUPLICATE.name &&
                    item.state != PlaylistImportItemState.OMITTED.name
                ) {
                    TextButton(onClick = { onOmit(item.id) }) {
                        Text(stringResource(R.string.playlist_import_skip))
                    }
                }
            }
        }
    }
}

@Composable
private fun ImportActions(
    snapshot: PlaylistImportSnapshot,
    busy: Boolean,
    onCreate: () -> Unit,
    onResolve: () -> Unit,
    onStart: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onCancel: () -> Unit,
    onRetry: () -> Unit,
    onReviewErrors: () -> Unit,
    onOpenPlaylist: (Long) -> Unit,
) {
    val imported = snapshot.imported
    Column(
        Modifier.fillMaxWidth().padding(top = PolentitaSpacing.medium),
        verticalArrangement = Arrangement.spacedBy(PolentitaSpacing.small),
    ) {
        when {
            imported.localPlaylistId == null -> Button(
                onClick = onCreate,
                enabled = !busy,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Default.PlaylistAdd, null)
                Text(stringResource(R.string.playlist_import_create_review))
            }
            imported.state == PlaylistImportState.RESOLVING.name -> {
                LinearProgressIndicator(Modifier.fillMaxWidth())
                Text(itemStateLabel(PlaylistImportItemState.SEARCHING.name))
            }
            imported.state in setOf(
                PlaylistImportState.ANALYZED.name,
                PlaylistImportState.REVIEW.name,
                PlaylistImportState.READY.name,
                PlaylistImportState.ERROR.name,
            ) -> {
                if (snapshot.errorCount > 0 || snapshot.items.any {
                        it.item.state == PlaylistImportItemState.NOT_AVAILABLE.name
                    }
                ) {
                    OutlinedButton(onClick = onRetry, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.Refresh, null)
                        Text(stringResource(R.string.playlist_import_retry_pending))
                    }
                }
                if (snapshot.items.any { it.item.selected && it.item.state == PlaylistImportItemState.MISSING.name }) {
                    OutlinedButton(onClick = onResolve, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.Refresh, null)
                        Text(stringResource(R.string.playlist_import_resolve))
                    }
                }
                Button(
                    onClick = onStart,
                    enabled = snapshot.items.any {
                        it.item.selected && it.item.state == PlaylistImportItemState.PENDING.name
                    } || snapshot.items.none {
                        it.item.selected && it.item.state in PENDING_ITEM_STATES
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Default.PlayArrow, null)
                    Text(stringResource(R.string.playlist_import_process))
                }
            }
            imported.state == PlaylistImportState.RUNNING.name -> {
                FilledTonalButton(onClick = onPause, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.Pause, null)
                    Text(stringResource(R.string.playlist_import_pause))
                }
                OutlinedButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.Cancel, null)
                    Text(stringResource(R.string.playlist_import_cancel))
                }
            }
            imported.state == PlaylistImportState.PAUSED.name -> {
                Button(onClick = onResume, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.PlayArrow, null)
                    Text(stringResource(R.string.playlist_import_resume))
                }
                OutlinedButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.playlist_import_cancel))
                }
            }
            imported.state in TERMINAL_IMPORT_STATES -> {
                ImportCard {
                    Text(
                        stringResource(R.string.playlist_import_finished_title),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        stringResource(
                            R.string.playlist_import_finished_summary,
                            snapshot.completedCount,
                            snapshot.items.size,
                        ),
                    )
                    Text(
                        stringResource(
                            R.string.playlist_import_finished_detail,
                            snapshot.downloadedCount,
                            snapshot.alreadyAvailableCount,
                            snapshot.items.size - snapshot.completedCount,
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                val playlistId = requireNotNull(imported.localPlaylistId)
                Button(onClick = { onOpenPlaylist(playlistId) }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.playlist_import_open_playlist))
                }
                if (snapshot.reviewCount > 0 || snapshot.errorCount > 0) {
                    OutlinedButton(onClick = onReviewErrors, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.playlist_import_review_errors))
                    }
                }
                if (snapshot.errorCount > 0 || snapshot.items.any {
                        it.item.state == PlaylistImportItemState.NOT_AVAILABLE.name
                    }
                ) {
                    OutlinedButton(onClick = onRetry, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.Refresh, null)
                        Text(stringResource(R.string.playlist_import_retry_pending))
                    }
                }
            }
        }
    }
}

@Composable
private fun CandidateDialog(
    row: PlaylistImportItemSnapshot,
    onDismiss: () -> Unit,
    onSelect: (String) -> Unit,
) {
    PolentitaAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.playlist_import_change_match)) },
        text = {
            Column {
                Text(
                    stringResource(R.string.playlist_import_candidate_original, row.item.title),
                    style = MaterialTheme.typography.bodyMedium,
                )
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().height(340.dp),
                    contentPadding = PaddingValues(vertical = PolentitaSpacing.small),
                    verticalArrangement = Arrangement.spacedBy(PolentitaSpacing.small),
                ) {
                    items(row.candidates, key = PlaylistImportCandidateEntity::id) { candidate ->
                        Surface(
                            onClick = { onSelect(candidate.id) },
                            shape = RoundedCornerShape(PolentitaRadii.medium),
                            color = if (candidate.selected) {
                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.38f)
                            } else {
                                MaterialTheme.colorScheme.surfaceContainerHigh
                            },
                        ) {
                            Column(Modifier.padding(PolentitaSpacing.small)) {
                                Text(candidate.title, fontWeight = FontWeight.Medium, maxLines = 2)
                                Text(
                                    "${candidate.artist} · ${formatDuration(candidate.durationMs)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Text(
                                    stringResource(
                                        R.string.playlist_import_confidence,
                                        (candidate.score * 100).toInt(),
                                    ),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.playlist_import_close)) }
        },
    )
}

@Composable
private fun ImportJourney(
    currentStep: Int,
    modifier: Modifier = Modifier,
) {
    val labels = listOf(
        stringResource(R.string.playlist_import_step_analyze),
        stringResource(R.string.playlist_import_step_review),
        stringResource(R.string.playlist_import_step_import),
    )
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(PolentitaSpacing.small),
    ) {
        labels.forEachIndexed { index, label ->
            PolentitaStatusPill(
                text = "${index + 1}  $label",
                tone = when {
                    index < currentStep -> PolentitaStatusTone.SUCCESS
                    index == currentStep -> PolentitaStatusTone.ACCENT
                    else -> PolentitaStatusTone.NEUTRAL
                },
            )
        }
    }
}

@Composable
private fun ImportTopBar(title: String, onBack: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
        border = BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = PolentitaOpacity.border),
        ),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = PolentitaSpacing.xs, vertical = PolentitaSpacing.small),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, stringResource(R.string.back)) }
            Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
        }
    }
}

@Composable
private fun ImportCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(PolentitaRadii.large),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.84f),
        ),
        border = BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = PolentitaOpacity.border),
        ),
    ) {
        Column(
            Modifier.padding(PolentitaSpacing.medium),
            verticalArrangement = Arrangement.spacedBy(PolentitaSpacing.xs),
            content = content,
        )
    }
}

@Composable
private fun PlaylistImportHelpDialog(onDismiss: () -> Unit) {
    val configuration = LocalConfiguration.current
    val localizedContext = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val exportifyUrl = stringResource(R.string.playlist_import_help_exportify_url)
    val tuneMyMusicUrl = stringResource(R.string.playlist_import_help_tunemymusic_url)
    val maxBodyHeight = (configuration.screenHeightDp * 0.68f).dp

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        CompositionLocalProvider(
            LocalContext provides localizedContext,
            LocalConfiguration provides configuration,
        ) {
            Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = PolentitaSpacing.large)
                .widthIn(max = 460.dp)
                .heightIn(max = (configuration.screenHeightDp * 0.86f).dp),
            shape = RoundedCornerShape(PolentitaRadii.large),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            contentColor = MaterialTheme.colorScheme.onSurface,
            border = BorderStroke(
                1.dp,
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = PolentitaOpacity.border),
            ),
            tonalElevation = 8.dp,
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(PolentitaSpacing.medium),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Top,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            stringResource(R.string.playlist_import_help_title),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Black,
                        )
                        Text(
                            stringResource(R.string.playlist_import_help_subtitle),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(48.dp),
                    ) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = stringResource(
                                R.string.playlist_import_help_close_description,
                            ),
                        )
                    }
                }
                Spacer(Modifier.height(PolentitaSpacing.small))
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = maxBodyHeight),
                    contentPadding = PaddingValues(bottom = PolentitaSpacing.small),
                    verticalArrangement = Arrangement.spacedBy(PolentitaSpacing.small),
                ) {
                    item(key = "help-intro") {
                        Text(
                            stringResource(R.string.playlist_import_help_intro),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                    item(key = "help-limits-title") {
                        Text(
                            stringResource(R.string.playlist_import_help_limits_title),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(top = PolentitaSpacing.small),
                        )
                    }
                    item(key = "help-spotify") {
                        PlaylistImportHelpLimit(
                            title = stringResource(R.string.playlist_import_help_spotify_title),
                            text = stringResource(R.string.playlist_import_help_spotify_limit),
                        )
                    }
                    item(key = "help-youtube") {
                        PlaylistImportHelpLimit(
                            title = stringResource(R.string.playlist_import_help_youtube_title),
                            text = stringResource(R.string.playlist_import_help_youtube_limit),
                        )
                    }
                    item(key = "help-tidal") {
                        PlaylistImportHelpLimit(
                            title = stringResource(R.string.playlist_import_help_tidal_title),
                            text = stringResource(R.string.playlist_import_help_tidal_limit),
                        )
                    }
                    item(key = "help-guide-title") {
                        Text(
                            stringResource(R.string.playlist_import_help_guide_title),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(top = PolentitaSpacing.small),
                        )
                    }
                    item(key = "help-step-exportify") {
                        Text(
                            stringResource(R.string.playlist_import_help_exportify_steps),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                    item(key = "help-exportify-image") {
                        PlaylistImportGuideImage(
                            imageRes = R.drawable.playlist_import_exportify,
                            description = stringResource(
                                R.string.playlist_import_help_exportify_image_description,
                            ),
                            sourceLabel = stringResource(
                                R.string.playlist_import_help_exportify_source,
                            ),
                            onOpen = { uriHandler.openUri(exportifyUrl) },
                            openLabel = stringResource(
                                R.string.playlist_import_help_open_exportify,
                            ),
                        )
                    }
                    item(key = "help-step-tunemymusic") {
                        Text(
                            stringResource(R.string.playlist_import_help_tunemymusic_steps),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                    item(key = "help-tunemymusic-image") {
                        PlaylistImportGuideImage(
                            imageRes = R.drawable.playlist_import_tunemymusic,
                            description = stringResource(
                                R.string.playlist_import_help_tunemymusic_image_description,
                            ),
                            sourceLabel = stringResource(
                                R.string.playlist_import_help_tunemymusic_source,
                            ),
                            onOpen = { uriHandler.openUri(tuneMyMusicUrl) },
                            openLabel = stringResource(
                                R.string.playlist_import_help_open_tunemymusic,
                            ),
                        )
                    }
                    item(key = "help-step-import") {
                        Text(
                            stringResource(R.string.playlist_import_help_import_steps),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                    item(key = "help-security") {
                        Surface(
                            shape = RoundedCornerShape(PolentitaRadii.medium),
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.10f),
                            border = BorderStroke(
                                1.dp,
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.24f),
                            ),
                        ) {
                            Text(
                                stringResource(R.string.playlist_import_help_security),
                                modifier = Modifier.padding(PolentitaSpacing.medium),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    item(key = "help-links") {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(PolentitaSpacing.small),
                        ) {
                            TextButton(
                                onClick = { uriHandler.openUri(exportifyUrl) },
                                modifier = Modifier.weight(1f),
                            ) {
                                Text(stringResource(R.string.playlist_import_help_open_exportify))
                            }
                            TextButton(
                                onClick = { uriHandler.openUri(tuneMyMusicUrl) },
                                modifier = Modifier.weight(1f),
                            ) {
                                Text(stringResource(R.string.playlist_import_help_open_tunemymusic))
                            }
                        }
                    }
                }
            }
            }
        }
    }
}

@Composable
private fun PlaylistImportHelpLimit(
    title: String,
    text: String,
) {
    Surface(
        shape = RoundedCornerShape(PolentitaRadii.medium),
        color = MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.82f),
        border = BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = PolentitaOpacity.border),
        ),
    ) {
        Column(Modifier.padding(PolentitaSpacing.medium)) {
            Text(title, fontWeight = FontWeight.Bold)
            Text(
                text,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun PlaylistImportGuideImage(
    imageRes: Int,
    description: String,
    sourceLabel: String,
    onOpen: () -> Unit,
    openLabel: String,
) {
    Column(Modifier.fillMaxWidth()) {
        Image(
            painter = painterResource(imageRes),
            contentDescription = description,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(PolentitaRadii.medium)),
            contentScale = ContentScale.Fit,
        )
        Text(
            sourceLabel,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            stringResource(R.string.playlist_import_help_image_note),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        TextButton(
            onClick = onOpen,
            modifier = Modifier.align(Alignment.End),
            contentPadding = PaddingValues(horizontal = PolentitaSpacing.small),
        ) {
            Text(openLabel)
        }
    }
}

@Composable
private fun ErrorDialog(message: String, onDismiss: () -> Unit) {
    PolentitaAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.playlist_import_cannot_continue)) },
        text = { Text(message) },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.playlist_import_understood)) }
        },
    )
}

@Composable
private fun itemStateLabel(state: String): String = stringResource(
    when (state) {
        PlaylistImportItemState.IN_LIBRARY.name -> R.string.playlist_import_state_in_library
        PlaylistImportItemState.PROBABLE_MATCH.name -> R.string.playlist_import_state_probable
        PlaylistImportItemState.MISSING.name -> R.string.playlist_import_state_missing
        PlaylistImportItemState.SEARCHING.name -> R.string.playlist_import_state_searching
        PlaylistImportItemState.REQUIRES_REVIEW.name -> R.string.playlist_import_state_review
        PlaylistImportItemState.NOT_AVAILABLE.name -> R.string.playlist_import_state_unavailable
        PlaylistImportItemState.DUPLICATE.name -> R.string.playlist_import_state_duplicate
        PlaylistImportItemState.METADATA_ERROR.name -> R.string.playlist_import_state_metadata_error
        PlaylistImportItemState.PENDING.name -> R.string.playlist_import_state_pending
        PlaylistImportItemState.PREPARING.name -> R.string.playlist_import_state_preparing
        PlaylistImportItemState.DOWNLOADING.name -> R.string.playlist_import_state_downloading
        PlaylistImportItemState.VALIDATING.name -> R.string.playlist_import_state_validating
        PlaylistImportItemState.SAVING.name -> R.string.playlist_import_state_saving
        PlaylistImportItemState.COMPLETED.name -> R.string.playlist_import_state_completed
        PlaylistImportItemState.OMITTED.name -> R.string.playlist_import_state_omitted
        PlaylistImportItemState.PAUSED.name -> R.string.playlist_import_state_paused
        else -> R.string.playlist_import_state_error
    },
)

@Composable
private fun importStateLabel(state: String): String = when (state) {
    PlaylistImportState.RESOLVING.name -> stringResource(R.string.playlist_import_state_searching)
    PlaylistImportState.RUNNING.name -> stringResource(R.string.playlist_import_progress_title)
    PlaylistImportState.PAUSED.name -> stringResource(R.string.playlist_import_state_paused)
    PlaylistImportState.COMPLETED.name -> stringResource(R.string.playlist_import_state_completed)
    PlaylistImportState.PARTIAL.name -> stringResource(R.string.playlist_import_partially_finished)
    PlaylistImportState.CANCELLED.name -> stringResource(R.string.playlist_import_cancelled)
    PlaylistImportState.ERROR.name -> stringResource(R.string.playlist_import_state_error)
    else -> stringResource(R.string.playlist_import_ready_to_review)
}

@Composable
private fun itemTone(state: String): PolentitaStatusTone = when (state) {
    PlaylistImportItemState.ERROR.name,
    PlaylistImportItemState.METADATA_ERROR.name,
    PlaylistImportItemState.NOT_AVAILABLE.name,
    -> PolentitaStatusTone.ERROR
    PlaylistImportItemState.IN_LIBRARY.name,
    PlaylistImportItemState.COMPLETED.name,
    -> PolentitaStatusTone.SUCCESS
    PlaylistImportItemState.PREPARING.name,
    PlaylistImportItemState.DOWNLOADING.name,
    PlaylistImportItemState.VALIDATING.name,
    PlaylistImportItemState.SAVING.name,
    PlaylistImportItemState.SEARCHING.name,
    -> PolentitaStatusTone.ACCENT
    PlaylistImportItemState.REQUIRES_REVIEW.name,
    PlaylistImportItemState.PROBABLE_MATCH.name,
    PlaylistImportItemState.PAUSED.name,
    -> PolentitaStatusTone.WARNING
    else -> PolentitaStatusTone.NEUTRAL
}

private fun importTone(state: String): PolentitaStatusTone = when (state) {
    PlaylistImportState.COMPLETED.name -> PolentitaStatusTone.SUCCESS
    PlaylistImportState.PARTIAL.name,
    PlaylistImportState.PAUSED.name,
    -> PolentitaStatusTone.WARNING
    PlaylistImportState.ERROR.name,
    PlaylistImportState.CANCELLED.name,
    -> PolentitaStatusTone.ERROR
    PlaylistImportState.RUNNING.name,
    PlaylistImportState.RESOLVING.name,
    -> PolentitaStatusTone.ACCENT
    else -> PolentitaStatusTone.NEUTRAL
}

private val ACTIVE_IMPORT_STATES = setOf(
    PlaylistImportState.RUNNING.name,
    PlaylistImportState.PAUSED.name,
    PlaylistImportState.RESOLVING.name,
)
private val TERMINAL_IMPORT_STATES = setOf(
    PlaylistImportState.COMPLETED.name,
    PlaylistImportState.PARTIAL.name,
    PlaylistImportState.CANCELLED.name,
)
private val ACTIVE_ITEM_STATES = setOf(
    PlaylistImportItemState.SEARCHING.name,
    PlaylistImportItemState.PREPARING.name,
    PlaylistImportItemState.DOWNLOADING.name,
    PlaylistImportItemState.VALIDATING.name,
    PlaylistImportItemState.SAVING.name,
)
private val PENDING_ITEM_STATES = setOf(
    PlaylistImportItemState.MISSING.name,
    PlaylistImportItemState.SEARCHING.name,
    PlaylistImportItemState.REQUIRES_REVIEW.name,
    PlaylistImportItemState.PENDING.name,
    PlaylistImportItemState.PREPARING.name,
    PlaylistImportItemState.DOWNLOADING.name,
    PlaylistImportItemState.VALIDATING.name,
    PlaylistImportItemState.SAVING.name,
    PlaylistImportItemState.PAUSED.name,
)
private val ERROR_ITEM_STATES = setOf(
    PlaylistImportItemState.PROBABLE_MATCH.name,
    PlaylistImportItemState.REQUIRES_REVIEW.name,
    PlaylistImportItemState.NOT_AVAILABLE.name,
    PlaylistImportItemState.METADATA_ERROR.name,
    PlaylistImportItemState.ERROR.name,
)
private val PlaylistImportRepositoryTerminalStates = setOf(
    PlaylistImportItemState.IN_LIBRARY.name,
    PlaylistImportItemState.COMPLETED.name,
    PlaylistImportItemState.DUPLICATE.name,
    PlaylistImportItemState.OMITTED.name,
)

private fun artworkForDisplay(value: String?, remoteAllowed: Boolean): String? = value?.takeIf {
    remoteAllowed || !it.startsWith("http://", ignoreCase = true) &&
        !it.startsWith("https://", ignoreCase = true)
}
