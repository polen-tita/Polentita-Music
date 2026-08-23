package com.polentita.music.feature.editor

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
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
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.polentita.music.R
import com.polentita.music.core.designsystem.Artwork
import com.polentita.music.core.designsystem.PolentitaAlertDialog
import com.polentita.music.core.designsystem.PolentitaOpacity
import com.polentita.music.core.designsystem.PolentitaRadii
import com.polentita.music.core.designsystem.PolentitaSpacing
import com.polentita.music.core.designsystem.polentitaOutlinedTextFieldColors
import com.polentita.music.domain.artwork.ArtworkCandidate
import com.polentita.music.domain.artwork.ArtworkChoice
import com.polentita.music.domain.artwork.ArtworkSource

@Composable
fun ArtworkPickerDialog(
    targetKey: String,
    initialQuery: String,
    artist: String,
    currentCoverUri: String?,
    onDismiss: () -> Unit,
    onUse: (ArtworkChoice) -> Unit,
    viewModel: ArtworkPickerViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val gridState = rememberLazyGridState()
    val headerVisible by remember(gridState) {
        derivedStateOf {
            gridState.firstVisibleItemIndex == 0 && gridState.firstVisibleItemScrollOffset < 24
        }
    }
    var selectedChoice by remember(targetKey) { mutableStateOf<ArtworkChoice?>(null) }
    var confirmRemove by remember { mutableStateOf(false) }
    val hasCoverToRemove = !currentCoverUri.isNullOrBlank() || selectedChoice != null
    val dismissPicker = {
        viewModel.close()
        onDismiss()
    }
    val useChoice: (ArtworkChoice) -> Unit = { choice ->
        viewModel.close()
        onUse(choice)
    }
    val fileLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        selectedChoice = ArtworkChoice.LocalFile(uri.toString())
    }
    val localizedContext = LocalContext.current
    val localizedConfiguration = LocalConfiguration.current

    LaunchedEffect(targetKey, initialQuery, artist) {
        viewModel.open(targetKey, initialQuery, artist)
    }
    LaunchedEffect(state.remoteSearchAllowed) {
        if (!state.remoteSearchAllowed && selectedChoice is ArtworkChoice.Remote) {
            selectedChoice = null
        }
    }
    LaunchedEffect(state.loading) {
        if (state.loading) selectedChoice = null
    }
    LaunchedEffect(targetKey, state.loading) {
        if (state.loading) gridState.scrollToItem(0)
    }

    Dialog(
        onDismissRequest = dismissPicker,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        CompositionLocalProvider(
            LocalContext provides localizedContext,
            LocalConfiguration provides localizedConfiguration,
        ) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.background,
            ) {
                Column(
                    Modifier
                        .fillMaxSize()
                        .safeDrawingPadding(),
                ) {
                AnimatedVisibility(
                    visible = headerVisible,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically(),
                ) {
                    Column {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = PolentitaSpacing.small, vertical = PolentitaSpacing.xs),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = dismissPicker) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back),
                        )
                    }
                    Column(Modifier.weight(1f)) {
                        Text(
                            stringResource(R.string.artwork_picker_title),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Black,
                        )
                        Text(
                            state.artist,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                OutlinedTextField(
                    value = state.query,
                    onValueChange = viewModel::setQuery,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = PolentitaSpacing.medium),
                    singleLine = true,
                    enabled = !state.loading && state.remoteSearchAllowed,
                    label = { Text(stringResource(R.string.artwork_search_label)) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    trailingIcon = {
                        IconButton(
                            onClick = viewModel::search,
                            enabled = !state.loading && state.remoteSearchAllowed,
                        ) {
                            Icon(
                                Icons.Default.Search,
                                contentDescription = stringResource(R.string.artwork_search),
                            )
                        }
                    },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { viewModel.search() }),
                    shape = RoundedCornerShape(PolentitaRadii.medium),
                    colors = polentitaOutlinedTextFieldColors(),
                )
                OutlinedTextField(
                    value = state.artist,
                    onValueChange = viewModel::setArtist,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(
                            start = PolentitaSpacing.medium,
                            top = PolentitaSpacing.xs,
                            end = PolentitaSpacing.medium,
                        ),
                    singleLine = true,
                    enabled = !state.loading && state.remoteSearchAllowed,
                    label = { Text(stringResource(R.string.artwork_artist_label)) },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { viewModel.search() }),
                    shape = RoundedCornerShape(PolentitaRadii.medium),
                    colors = polentitaOutlinedTextFieldColors(),
                )
                LazyRow(
                    contentPadding = PaddingValues(
                        horizontal = PolentitaSpacing.medium,
                        vertical = PolentitaSpacing.small,
                    ),
                    horizontalArrangement = Arrangement.spacedBy(PolentitaSpacing.small),
                ) {
                    item(key = "all") {
                        FilterChip(
                            selected = state.selectedSource == null,
                            onClick = { viewModel.selectSource(null) },
                            label = { Text(stringResource(R.string.artwork_all_sources)) },
                        )
                    }
                    items(ArtworkSource.entries, key = ArtworkSource::name) { source ->
                        FilterChip(
                            selected = state.selectedSource == source,
                            onClick = { viewModel.selectSource(source) },
                            label = { Text(source.displayLabel()) },
                        )
                    }
                }
                if (state.loading) {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                }
                val selectedSourceError = state.selectedSource?.let(state.sourceErrors::get)
                val statusMessage = when {
                    !state.remoteSearchAllowed -> stringResource(R.string.artwork_search_offline)
                    state.error != null -> state.error
                    selectedSourceError != null -> selectedSourceError
                    state.sourceErrors.isNotEmpty() -> stringResource(R.string.artwork_partial_results)
                    else -> null
                }
                statusMessage?.let { message ->
                    Text(
                        message,
                        modifier = Modifier.padding(
                            horizontal = PolentitaSpacing.large,
                            vertical = PolentitaSpacing.xs,
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = if (state.error != null) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }
                    }
                }
                val canLoadMore = state.hasMore &&
                    (state.selectedSource == null || state.selectedSource == ArtworkSource.INTERNET)
                Box(Modifier.weight(1f)) {
                    if (state.targetKey == targetKey &&
                        state.remoteSearchAllowed &&
                        !state.loading &&
                        state.visibleCandidates.isEmpty() &&
                        state.error == null
                    ) {
                        Text(
                            stringResource(R.string.artwork_no_results),
                            modifier = Modifier
                                .align(Alignment.Center)
                                .padding(PolentitaSpacing.xl),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        LazyVerticalGrid(
                            state = gridState,
                            columns = GridCells.Adaptive(148.dp),
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(
                                start = PolentitaSpacing.medium,
                                end = PolentitaSpacing.medium,
                                top = PolentitaSpacing.small,
                                bottom = PolentitaSpacing.medium,
                            ),
                            horizontalArrangement = Arrangement.spacedBy(PolentitaSpacing.small),
                            verticalArrangement = Arrangement.spacedBy(PolentitaSpacing.small),
                        ) {
                            gridItems(state.visibleCandidates, key = ArtworkCandidate::id) { candidate ->
                                ArtworkCandidateCard(
                                    candidate = candidate,
                                    selected = (selectedChoice as? ArtworkChoice.Remote)?.candidate?.id == candidate.id,
                                    onClick = {
                                        selectedChoice = if (
                                            (selectedChoice as? ArtworkChoice.Remote)?.candidate?.id == candidate.id
                                        ) {
                                            null
                                        } else {
                                            ArtworkChoice.Remote(candidate)
                                        }
                                    },
                                )
                            }
                            if (canLoadMore) {
                                item(
                                    key = "artwork-load-more",
                                    span = { GridItemSpan(maxLineSpan) },
                                ) {
                                    OutlinedButton(
                                        onClick = viewModel::loadMore,
                                        enabled = !state.loadingMore,
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(PolentitaRadii.pill),
                                    ) {
                                        Text(
                                            if (state.loadingMore) {
                                                stringResource(R.string.artwork_loading_more)
                                            } else {
                                                stringResource(R.string.artwork_load_more)
                                            },
                                        )
                                    }
                                }
                            }
                            if (state.loadingMore) {
                                item(
                                    key = "artwork-load-more-progress",
                                    span = { GridItemSpan(maxLineSpan) },
                                ) {
                                    LinearProgressIndicator(Modifier.fillMaxWidth())
                                }
                            }
                            if (state.showNoMoreResultsHint &&
                                (state.selectedSource == null || state.selectedSource == ArtworkSource.INTERNET)
                            ) {
                                item(
                                    key = "artwork-no-more-results-hint",
                                    span = { GridItemSpan(maxLineSpan) },
                                ) {
                                    Text(
                                        stringResource(R.string.artwork_no_more_results_hint),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(
                                                horizontal = PolentitaSpacing.medium,
                                                vertical = PolentitaSpacing.small,
                                            ),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        textAlign = TextAlign.Center,
                                    )
                                }
                            }
                        }
                    }
                }
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    border = BorderStroke(
                        1.dp,
                        MaterialTheme.colorScheme.outlineVariant.copy(alpha = PolentitaOpacity.border),
                    ),
                ) {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .padding(PolentitaSpacing.medium),
                        verticalArrangement = Arrangement.spacedBy(PolentitaSpacing.small),
                    ) {
                        selectedChoice?.let { choice ->
                            val previewUri = when (choice) {
                                is ArtworkChoice.LocalFile -> choice.contentUri
                                is ArtworkChoice.Remote -> choice.candidate.previewUrl
                                    ?: choice.candidate.imageUrl
                                else -> null
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Artwork(
                                    previewUri,
                                    stringResource(R.string.artwork_selected),
                                    Modifier.size(56.dp),
                                )
                                Spacer(Modifier.width(PolentitaSpacing.small))
                                Text(
                                    when (choice) {
                                        is ArtworkChoice.Remote -> choice.candidate.source.displayLabel()
                                        is ArtworkChoice.LocalFile -> stringResource(R.string.artwork_choose_file)
                                        else -> stringResource(R.string.artwork_selected)
                                    },
                                    modifier = Modifier.weight(1f),
                                    style = MaterialTheme.typography.labelLarge,
                                )
                                IconButton(
                                    onClick = { selectedChoice = null },
                                    modifier = Modifier.size(40.dp),
                                ) {
                                    Icon(
                                        Icons.Default.Close,
                                        contentDescription = stringResource(R.string.artwork_clear_selection),
                                    )
                                }
                            }
                            Button(
                                onClick = { useChoice(choice) },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(PolentitaRadii.pill),
                            ) {
                                Text(stringResource(R.string.artwork_use_selected))
                            }
                        }
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(PolentitaSpacing.small),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            OutlinedButton(
                                onClick = { fileLauncher.launch(arrayOf("image/jpeg", "image/png", "image/webp")) },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(PolentitaRadii.pill),
                            ) {
                                Icon(Icons.Default.FolderOpen, contentDescription = null)
                                Spacer(Modifier.width(PolentitaSpacing.xs))
                                Text(stringResource(R.string.artwork_choose_file), maxLines = 1)
                            }
                            IconButton(
                                onClick = { confirmRemove = true },
                                enabled = hasCoverToRemove,
                            ) {
                                Icon(
                                    Icons.Default.Delete,
                                    contentDescription = stringResource(R.string.artwork_delete),
                                    tint = if (!hasCoverToRemove) {
                                        MaterialTheme.colorScheme.onSurfaceVariant.copy(
                                            alpha = PolentitaOpacity.disabled,
                                        )
                                    } else {
                                        MaterialTheme.colorScheme.error
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

    if (confirmRemove) {
        PolentitaAlertDialog(
            onDismissRequest = { confirmRemove = false },
            title = { Text(stringResource(R.string.artwork_delete_title)) },
            text = { Text(stringResource(R.string.artwork_delete_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmRemove = false
                        useChoice(ArtworkChoice.Remove)
                    },
                ) {
                    Text(
                        stringResource(R.string.artwork_delete_confirm),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmRemove = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }
}

@Composable
private fun ArtworkCandidateCard(
    candidate: ArtworkCandidate,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(PolentitaRadii.medium),
        color = if (selected) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.54f)
        } else {
            MaterialTheme.colorScheme.surfaceContainerLow
        },
        border = BorderStroke(
            if (selected) 2.dp else 1.dp,
            if (selected) MaterialTheme.colorScheme.primary else {
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = PolentitaOpacity.border)
            },
        ),
    ) {
        Column(Modifier.padding(PolentitaSpacing.small)) {
            Artwork(
                candidate.previewUrl ?: candidate.imageUrl,
                candidate.title,
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f),
                seed = "${candidate.source}:${candidate.id}",
            )
            Spacer(Modifier.height(PolentitaSpacing.small))
            Text(
                candidate.title.ifBlank { candidate.source.displayLabel() },
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                candidate.artist.ifBlank { candidate.source.displayLabel() },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            candidate.width?.let { width ->
                candidate.height?.let { height ->
                    Text(
                        stringResource(R.string.artwork_resolution, width, height),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Text(
                candidate.source.displayLabel(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun ArtworkSource.displayLabel(): String = stringResource(
    when (this) {
        ArtworkSource.INTERNET -> R.string.artwork_source_internet
        ArtworkSource.SPOTIFY -> R.string.artwork_source_spotify
        ArtworkSource.TIDAL -> R.string.artwork_source_tidal
        ArtworkSource.YOUTUBE -> R.string.artwork_source_youtube
    },
)
