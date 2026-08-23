package com.polentita.music.core.designsystem

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlaylistAdd
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Snackbar
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldColors
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import com.polentita.music.R
import coil3.compose.AsyncImage
import com.polentita.music.core.common.formatDuration
import com.polentita.music.domain.model.Song
import com.polentita.music.playback.session.PlaybackUiState

data class SongActions(
    val play: () -> Unit,
    val playNext: (() -> Unit)? = null,
    val queue: (() -> Unit)? = null,
    val addToPlaylist: (() -> Unit)? = null,
    val removeFromPlaylist: (() -> Unit)? = null,
    val favorite: (() -> Unit)? = null,
    val edit: (() -> Unit)? = null,
    val share: (() -> Unit)? = null,
    val details: (() -> Unit)? = null,
    val remove: (() -> Unit)? = null,
)

data class AmbientChromeStyle(
    val backdrop: Color,
    val surface: Color,
    val accent: Color,
    val secondary: Color,
    val strength: Float,
)

enum class PolentitaStatusTone {
    ACCENT,
    SUCCESS,
    WARNING,
    ERROR,
    NEUTRAL,
}

@Composable
fun PolentitaStatusPill(
    text: String,
    tone: PolentitaStatusTone,
    modifier: Modifier = Modifier,
) {
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val accent = when (tone) {
        PolentitaStatusTone.ACCENT -> MaterialTheme.colorScheme.tertiary
        PolentitaStatusTone.SUCCESS -> if (dark) Color(0xFF72E0AE) else Color(0xFF006C48)
        PolentitaStatusTone.WARNING -> if (dark) Color(0xFFFFC66F) else Color(0xFF7A5500)
        PolentitaStatusTone.ERROR -> MaterialTheme.colorScheme.error
        PolentitaStatusTone.NEUTRAL -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(PolentitaRadii.pill),
        color = accent.copy(alpha = 0.12f),
        contentColor = accent,
        border = BorderStroke(1.dp, accent.copy(alpha = 0.26f)),
        tonalElevation = 0.dp,
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
        )
    }
}

@Composable
fun PolentitaMetricCard(
    value: String,
    label: String,
    tone: PolentitaStatusTone,
    modifier: Modifier = Modifier,
) {
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val accent = when (tone) {
        PolentitaStatusTone.ACCENT -> MaterialTheme.colorScheme.tertiary
        PolentitaStatusTone.SUCCESS -> if (dark) Color(0xFF72E0AE) else Color(0xFF006C48)
        PolentitaStatusTone.WARNING -> if (dark) Color(0xFFFFC66F) else Color(0xFF7A5500)
        PolentitaStatusTone.ERROR -> MaterialTheme.colorScheme.error
        PolentitaStatusTone.NEUTRAL -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(PolentitaRadii.medium),
        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.62f),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.20f)),
        tonalElevation = 0.dp,
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            Text(
                value,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Black,
                color = accent,
            )
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
    }
}

@Composable
fun PolentitaSectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    action: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(PolentitaSpacing.small),
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            subtitle?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        action?.invoke()
    }
}

@Composable
fun PolentitaDropdownMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    scrollState: ScrollState = rememberScrollState(),
    properties: PopupProperties = PopupProperties(focusable = true),
    content: @Composable ColumnScope.() -> Unit,
) {
    val windowContext = LocalContext.current
    val windowConfiguration = LocalConfiguration.current
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
        modifier = modifier,
        scrollState = scrollState,
        properties = properties,
        shape = RoundedCornerShape(PolentitaRadii.medium),
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.98f),
        tonalElevation = PolentitaElevation.resting,
        shadowElevation = PolentitaElevation.floating,
        border = BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.48f),
        ),
        content = {
            CompositionLocalProvider(
                LocalContext provides windowContext,
                LocalConfiguration provides windowConfiguration,
            ) {
                content()
            }
        },
    )
}

@Composable
fun PolentitaAlertDialog(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    dismissButton: (@Composable () -> Unit)? = null,
    title: (@Composable () -> Unit)? = null,
    text: (@Composable () -> Unit)? = null,
) {
    val windowContext = LocalContext.current
    val windowConfiguration = LocalConfiguration.current
    AlertDialog(
        onDismissRequest = onDismissRequest,
        confirmButton = {
            CompositionLocalProvider(
                LocalContext provides windowContext,
                LocalConfiguration provides windowConfiguration,
            ) {
                confirmButton()
            }
        },
        modifier = modifier,
        dismissButton = dismissButton?.let { button ->
            {
                CompositionLocalProvider(
                    LocalContext provides windowContext,
                    LocalConfiguration provides windowConfiguration,
                ) {
                    button()
                }
            }
        },
        title = title?.let { titleContent ->
            {
                CompositionLocalProvider(
                    LocalContext provides windowContext,
                    LocalConfiguration provides windowConfiguration,
                ) {
                    titleContent()
                }
            }
        },
        text = text?.let { textContent ->
            {
                CompositionLocalProvider(
                    LocalContext provides windowContext,
                    LocalConfiguration provides windowConfiguration,
                ) {
                    textContent()
                }
            }
        },
        shape = RoundedCornerShape(PolentitaRadii.large),
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        iconContentColor = MaterialTheme.colorScheme.primary,
        titleContentColor = MaterialTheme.colorScheme.onSurface,
        textContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        tonalElevation = PolentitaElevation.floating,
    )
}

@Composable
fun localizedPlaybackContextLabel(label: String?): String? {
    val library = stringResource(R.string.library)
    val selection = stringResource(R.string.playback_context_selection)
    return when (label) {
        "Biblioteca", "Library", "Bibliothèque", "音乐库" -> library
        "Selección", "Selection", "Seleção", "Sélection", "选择" -> selection
        else -> label?.takeIf(String::isNotBlank)
    }
}

@Composable
fun polentitaOutlinedTextFieldColors(): TextFieldColors = OutlinedTextFieldDefaults.colors(
    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.78f),
    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLowest.copy(alpha = 0.64f),
    disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.42f),
    focusedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.90f),
    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.72f),
    disabledBorderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.32f),
    cursorColor = MaterialTheme.colorScheme.primary,
)

@Composable
fun CompactSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    hint: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(PolentitaRadii.medium)
    val borderColor = if (focused) {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.84f)
    } else {
        MaterialTheme.colorScheme.outline.copy(alpha = 0.56f)
    }
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        enabled = enabled,
        modifier = modifier
            .height(48.dp)
            .clip(shape)
            .background(
                Brush.horizontalGradient(
                    listOf(
                        MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.78f),
                        MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.60f),
                    ),
                ),
            )
            .border(1.dp, borderColor, shape)
            .onFocusChanged { focused = it.isFocused },
        singleLine = true,
        textStyle = MaterialTheme.typography.bodyMedium.copy(
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = if (enabled) 1f else 0.52f),
        ),
        decorationBox = { innerTextField ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 13.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Default.Search,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.width(10.dp))
                Box(Modifier.weight(1f)) {
                    if (value.isBlank()) {
                        Text(
                            hint,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    innerTextField()
                }
            }
        },
    )
}

data class ActiveSongVisualState(
    val isActive: Boolean,
    val isPlaying: Boolean,
    val statusLabel: String,
)

fun activeSongVisualState(
    songId: Long,
    currentSongId: Long?,
    isPlaying: Boolean,
): ActiveSongVisualState {
    val active = songId == currentSongId
    return ActiveSongVisualState(
        isActive = active,
        isPlaying = active && isPlaying,
        statusLabel = when {
            active && isPlaying -> "Reproduciendo"
            active -> "En pausa"
            else -> ""
        },
    )
}

@Composable
fun Artwork(
    uri: String?,
    description: String,
    modifier: Modifier = Modifier,
    seed: String = description,
    elevated: Boolean = false,
) {
    val fallback = remember(seed) { ArtworkColorAnalyzer.fallback(seed) }
    Box(
        modifier = modifier
            .then(
                if (elevated) {
                    Modifier.shadow(
                        elevation = PolentitaElevation.floating,
                        shape = RoundedCornerShape(PolentitaRadii.medium),
                        clip = false,
                    )
                } else {
                    Modifier
                },
            )
            .clip(RoundedCornerShape(PolentitaRadii.medium))
            .background(
                Brush.linearGradient(
                    listOf(fallback.dominant, fallback.background),
                ),
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (uri.isNullOrBlank()) {
            Icon(
                Icons.Default.MusicNote,
                contentDescription = description,
                modifier = Modifier.size(36.dp),
                tint = PolentitaContentColors.PrimaryOnDark,
            )
        } else {
            AsyncImage(
                model = uri,
                contentDescription = description,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
fun SongRow(
    song: Song,
    actions: SongActions,
    modifier: Modifier = Modifier,
    activeState: ActiveSongVisualState = ActiveSongVisualState(false, false, ""),
    trackNumber: Int? = null,
) {
    var expanded by remember { mutableStateOf(false) }
    val rowShape = RoundedCornerShape(PolentitaRadii.medium)
    val animationsEnabled = rememberAnimationsEnabled()
    val unknownArtist = stringResource(R.string.unknown_artist)
    val localizedStatusLabel = when {
        activeState.isPlaying -> stringResource(R.string.playback_status_playing)
        activeState.isActive -> stringResource(R.string.playback_status_paused)
        else -> ""
    }
    val activeTint by animateColorAsState(
        targetValue = if (activeState.isActive) {
            MaterialTheme.colorScheme.primary.copy(alpha = PolentitaOpacity.subtle)
        } else {
            Color.Transparent
        },
        animationSpec = tween(animationDuration(animationsEnabled, PolentitaMotion.quick)),
        label = "Color de canción activa",
    )
    val activeBorder by animateColorAsState(
        targetValue = if (activeState.isActive) {
            MaterialTheme.colorScheme.primary.copy(alpha = 0.22f)
        } else {
            Color.Transparent
        },
        animationSpec = tween(animationDuration(animationsEnabled, PolentitaMotion.quick)),
        label = "Borde de canción activa",
    )
    SwipeToPlayNext(
        song = song,
        playNext = actions.playNext,
        modifier = modifier,
    ) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                Brush.horizontalGradient(
                    listOf(
                        activeTint,
                        Color.Transparent,
                    ),
                ),
                rowShape,
            )
            .border(
                width = 1.dp,
                color = activeBorder,
                shape = rowShape,
            )
            .clickable(
                onClickLabel = stringResource(R.string.play_song_accessibility, song.title),
                onClick = actions.play,
            )
            .padding(horizontal = PolentitaSpacing.medium, vertical = PolentitaSpacing.small)
            .semantics {
                contentDescription = buildString {
                    append("${song.title}, ${song.artist.ifBlank { unknownArtist }}")
                    if (activeState.isActive) append(", $localizedStatusLabel")
                }
            },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (trackNumber != null) {
            Box(Modifier.width(36.dp), contentAlignment = Alignment.Center) {
                AnimatedContent(
                    targetState = activeState,
                    label = "Estado de pista",
                ) { visualState ->
                    if (visualState.isActive) {
                        Icon(
                            Icons.Default.PlayArrow,
                            contentDescription = localizedStatusLabel,
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    } else {
                        Text(trackNumber.toString(), style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        } else {
            Artwork(
                song.coverUri,
                stringResource(R.string.home_cover_description, song.title),
                Modifier.size(PolentitaCoverSize.row),
                seed = "${song.title}|${song.artist}",
            )
            Spacer(Modifier.width(PolentitaSpacing.medium))
        }
        Column(
            Modifier
                .weight(1f)
                .padding(horizontal = PolentitaSpacing.xxs),
        ) {
            Text(
                song.title,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontWeight = FontWeight.Medium,
                color = if (activeState.isActive) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
            )
            Text(
                listOf(song.artist.ifBlank { unknownArtist }, formatDuration(song.durationMs))
                    .joinToString(" · "),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodySmall,
                color = if (song.isAvailable) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.error
                },
            )
        }
        if (song.isFavorite) {
            AnimatedContent(
                targetState = song.isFavorite,
                label = "Estado de favorita",
            ) { favorite ->
                Icon(
                    if (favorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                    contentDescription = stringResource(
                        if (favorite) R.string.favorite_marked else R.string.favorite_unmarked,
                    ),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }
        Box {
            IconButton(onClick = { expanded = true }) {
                Icon(
                    Icons.Default.MoreVert,
                    contentDescription = stringResource(R.string.more_options_for, song.title),
                )
            }
            PolentitaDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                MenuAction(stringResource(R.string.play), Icons.Default.PlayArrow) { actions.play(); expanded = false }
                actions.playNext?.let { action ->
                    MenuAction(stringResource(R.string.action_play_next), Icons.Default.QueueMusic) {
                        action(); expanded = false
                    }
                }
                actions.queue?.let { action ->
                    MenuAction(stringResource(R.string.action_add_queue), Icons.Default.Add) { action(); expanded = false }
                }
                actions.addToPlaylist?.let { action ->
                    MenuAction(stringResource(R.string.action_add_playlist), Icons.Default.PlaylistAdd) {
                        action(); expanded = false
                    }
                }
                actions.removeFromPlaylist?.let { remove ->
                    MenuAction(stringResource(R.string.action_remove_playlist), Icons.Default.Delete) { remove(); expanded = false }
                }
                actions.favorite?.let { action ->
                    MenuAction(
                        stringResource(
                            if (song.isFavorite) R.string.action_remove_favorite
                            else R.string.action_mark_favorite,
                        ),
                        if (song.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                    ) { action(); expanded = false }
                }
                actions.edit?.let { action ->
                    MenuAction(stringResource(R.string.action_edit_info), Icons.Default.Edit) {
                        action(); expanded = false
                    }
                    MenuAction(stringResource(R.string.action_change_album), Icons.Default.Album) { action(); expanded = false }
                }
                actions.share?.let { action ->
                    MenuAction(stringResource(R.string.action_share_file), Icons.Default.Share) { action(); expanded = false }
                }
                actions.details?.let { action ->
                    MenuAction(stringResource(R.string.action_technical_details), Icons.Default.Info) { action(); expanded = false }
                }
                actions.remove?.let { action ->
                    MenuAction(stringResource(R.string.action_remove_or_delete), Icons.Default.Delete) { action(); expanded = false }
                }
            }
        }
    }
    }
}

@Composable
private fun SwipeToPlayNext(
    song: Song,
    playNext: (() -> Unit)?,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    if (playNext == null) {
        Box(modifier) { content() }
        return
    }

    val density = LocalDensity.current
    val maxOffset = with(density) { 148.dp.toPx() }
    val triggerOffset = with(density) { 112.dp.toPx() }
    var offset by remember(song.id) { mutableFloatStateOf(0f) }
    var showFeedback by remember(song.id) { mutableStateOf(false) }

    LaunchedEffect(showFeedback) {
        if (showFeedback) {
            delay(2_600)
            showFeedback = false
        }
    }

    Box(modifier.fillMaxWidth()) {
        Box(
            Modifier
                .fillMaxHeight()
                .width(with(density) { offset.toDp() })
                .background(PolentitaFallbackColors.QueueSwipe),
            contentAlignment = Alignment.CenterStart,
        ) {
            Icon(
                Icons.Default.QueueMusic,
                contentDescription = null,
                modifier = Modifier.padding(start = PolentitaSpacing.large),
                tint = PolentitaContentColors.PrimaryOnDark,
            )
        }
        Box(
            Modifier
                .fillMaxWidth()
                .offset { IntOffset(offset.roundToInt(), 0) }
                .pointerInput(song.id) {
                    detectDragGesturesAfterLongPress(
                        onDragStart = {},
                        onDragCancel = { offset = 0f },
                        onDragEnd = {
                            if (offset >= triggerOffset) {
                                playNext()
                                showFeedback = true
                            }
                            offset = 0f
                        },
                        onDrag = { change, amount ->
                            if (abs(amount.x) >= abs(amount.y)) {
                                change.consume()
                                offset = (offset + amount.x).coerceIn(0f, maxOffset)
                            }
                        },
                    )
                },
        ) {
            content()
        }
        if (showFeedback) {
            val windowContext = LocalContext.current
            val windowConfiguration = LocalConfiguration.current
            Popup(
                alignment = Alignment.BottomCenter,
                offset = IntOffset(0, -with(density) { 96.dp.roundToPx() }),
                properties = PopupProperties(focusable = false),
            ) {
                CompositionLocalProvider(
                    LocalContext provides windowContext,
                    LocalConfiguration provides windowConfiguration,
                ) {
                    Snackbar(
                        containerColor = PolentitaFallbackColors.QueueSwipe,
                        contentColor = PolentitaContentColors.PrimaryOnDark,
                        shape = RoundedCornerShape(PolentitaRadii.medium),
                    ) {
                        Text(stringResource(R.string.play_next_swipe_feedback))
                    }
                }
            }
        }
    }
}

@Composable
fun CompactGridMenuButton(
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    IconButton(
        onClick = onClick,
        modifier = modifier,
    ) {
        Box(
            modifier = Modifier.size(48.dp),
            contentAlignment = Alignment.TopEnd,
        ) {
            Box(
                modifier = Modifier
                    .padding(top = 2.dp, end = 2.dp)
                    .size(28.dp)
                    .clip(RoundedCornerShape(PolentitaRadii.pill))
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.72f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Default.MoreVert,
                    contentDescription = contentDescription,
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

@Composable
fun SongGridCard(
    song: Song,
    actions: SongActions,
    activeState: ActiveSongVisualState,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    val cardShape = RoundedCornerShape(PolentitaRadii.medium)
    val animationsEnabled = rememberAnimationsEnabled()
    val unknownArtist = stringResource(R.string.unknown_artist)
    val localizedStatusLabel = when {
        activeState.isPlaying -> stringResource(R.string.playback_status_playing)
        activeState.isActive -> stringResource(R.string.playback_status_paused)
        else -> ""
    }
    val container by animateColorAsState(
        targetValue = MaterialTheme.colorScheme.surfaceContainerLow.copy(
            alpha = if (activeState.isActive) 0.76f else 0.38f,
        ),
        animationSpec = tween(animationDuration(animationsEnabled, PolentitaMotion.quick)),
        label = "Superficie de tarjeta activa",
    )
    val border by animateColorAsState(
        targetValue = if (activeState.isActive) {
            MaterialTheme.colorScheme.primary.copy(alpha = 0.30f)
        } else {
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.18f)
        },
        animationSpec = tween(animationDuration(animationsEnabled, PolentitaMotion.quick)),
        label = "Borde de tarjeta activa",
    )
    SwipeToPlayNext(
        song = song,
        playNext = actions.playNext,
        modifier = modifier,
    ) {
    Column(
        modifier = Modifier
            .clip(cardShape)
            .background(
                container,
                cardShape,
            )
            .border(
                width = 1.dp,
                color = border,
                shape = cardShape,
            )
            .clickable(
                onClickLabel = stringResource(R.string.play_song_accessibility, song.title),
                onClick = actions.play,
            )
            .semantics {
                contentDescription = buildString {
                    append("${song.title}, ${song.artist.ifBlank { unknownArtist }}")
                    if (activeState.isActive) append(", $localizedStatusLabel")
                }
            },
    ) {
        Box {
            Artwork(
                song.coverUri,
                stringResource(R.string.home_cover_description, song.title),
                Modifier.fillMaxWidth().aspectRatio(1f),
                seed = "${song.title}|${song.artist}",
            )
            AnimatedContent(
                targetState = activeState.isActive,
                modifier = Modifier.align(Alignment.BottomStart),
                label = "Estado de tarjeta activa",
            ) { active ->
                if (active) {
                    Box(
                        Modifier
                            .padding(PolentitaSpacing.small)
                            .clip(RoundedCornerShape(PolentitaRadii.pill))
                            .background(MaterialTheme.colorScheme.primary)
                            .padding(
                                horizontal = PolentitaSpacing.small,
                                vertical = PolentitaSpacing.xs,
                            ),
                    ) {
                        Text(
                            localizedStatusLabel,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimary,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Clip,
                        )
                    }
                }
            }
            Box(Modifier.align(Alignment.TopEnd)) {
                CompactGridMenuButton(
                    contentDescription = stringResource(R.string.more_options_for, song.title),
                    onClick = { expanded = true },
                )
                PolentitaDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    MenuAction(stringResource(R.string.play), Icons.Default.PlayArrow) {
                        actions.play()
                        expanded = false
                    }
                    actions.playNext?.let { action ->
                        MenuAction(stringResource(R.string.action_play_next), Icons.Default.QueueMusic) {
                            action()
                            expanded = false
                        }
                    }
                    actions.queue?.let { action ->
                        MenuAction(stringResource(R.string.action_add_queue), Icons.Default.Add) {
                            action()
                            expanded = false
                        }
                    }
                    actions.addToPlaylist?.let { action ->
                        MenuAction(stringResource(R.string.action_add_playlist), Icons.Default.PlaylistAdd) {
                            action()
                            expanded = false
                        }
                    }
                    actions.favorite?.let { action ->
                        MenuAction(
                            stringResource(
                                if (song.isFavorite) R.string.action_remove_favorite
                                else R.string.action_mark_favorite,
                            ),
                            if (song.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        ) {
                            action()
                            expanded = false
                        }
                    }
                    actions.edit?.let { action ->
                        MenuAction(stringResource(R.string.action_edit_info), Icons.Default.Edit) {
                            action()
                            expanded = false
                        }
                    }
                    actions.details?.let { action ->
                        MenuAction(stringResource(R.string.action_technical_details), Icons.Default.Info) {
                            action()
                            expanded = false
                        }
                    }
                    actions.remove?.let { action ->
                        MenuAction(stringResource(R.string.action_remove_or_delete), Icons.Default.Delete) {
                            action()
                            expanded = false
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(PolentitaSpacing.small))
        Text(
            song.title,
            modifier = Modifier.padding(horizontal = PolentitaSpacing.xxs),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            fontWeight = FontWeight.SemiBold,
            color = if (activeState.isActive) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurface
            },
        )
        Text(
            song.artist.ifBlank { unknownArtist },
            modifier = Modifier.padding(horizontal = PolentitaSpacing.xxs),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    }
}

@Composable
private fun MenuAction(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    action: () -> Unit,
) {
    DropdownMenuItem(
        text = { Text(label) },
        leadingIcon = { Icon(icon, contentDescription = null) },
        onClick = action,
    )
}

@Composable
fun EmptyState(
    title: String,
    message: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxSize().padding(PolentitaSpacing.huge),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            Modifier
                .size(72.dp)
                .clip(RoundedCornerShape(PolentitaRadii.large))
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
                .border(
                    1.dp,
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.20f),
                    RoundedCornerShape(PolentitaRadii.large),
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Default.MusicNote, null, Modifier.size(34.dp), tint = MaterialTheme.colorScheme.primary)
        }
        Spacer(Modifier.height(PolentitaSpacing.large))
        Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(PolentitaSpacing.small))
        Text(
            message,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
fun LoadingState(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(
            color = MaterialTheme.colorScheme.primary,
            strokeWidth = 3.dp,
        )
    }
}

@Composable
fun ErrorState(message: String, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize().padding(PolentitaSpacing.huge), contentAlignment = Alignment.Center) {
        Text(
            message,
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
fun MiniPlayer(
    state: PlaybackUiState,
    onOpen: () -> Unit,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    onNext: (() -> Unit)? = null,
    progressContent: (@Composable () -> Unit)? = null,
    ambientStyle: AmbientChromeStyle? = null,
) {
    if (state.currentSongId == null && !state.isPreview) return
    val palette = rememberArtworkPalette(
        coverUri = state.artworkUri,
        seed = "${state.title}|${state.artist}",
    )
    val ambientStrength = ambientStyle?.strength?.coerceIn(0f, 1f) ?: 0f
    val ambientAccent = ambientStyle?.accent ?: palette.accent
    val ambientSecondary = ambientStyle?.secondary ?: palette.vibrant
    val ambientBackdrop = ambientStyle?.backdrop ?: Color(0xFF030510)
    val ambientSurface = ambientStyle?.surface ?: Color(0xFF090D1D)
    val styledBackground = lerp(
        lerp(palette.background, ambientBackdrop, 0.46f * ambientStrength),
        ambientSecondary,
        0.08f * ambientStrength,
    )
    val styledSurface = lerp(
        lerp(palette.surface, ambientSurface, 0.52f * ambientStrength),
        ambientAccent,
        0.10f * ambientStrength,
    )
    val effectivePalette = palette.copy(
        dominant = lerp(palette.dominant, ambientSecondary, 0.30f * ambientStrength),
        vibrant = lerp(palette.vibrant, ambientAccent, 0.56f * ambientStrength),
        muted = lerp(palette.muted, ambientSecondary, 0.24f * ambientStrength),
        background = styledBackground,
        surface = styledSurface,
        accent = lerp(palette.accent, ambientAccent, 0.76f * ambientStrength),
        onBackground = ArtworkColorAnalyzer.safeContentColor(styledBackground),
        onAccent = ArtworkColorAnalyzer.safeContentColor(
            lerp(palette.accent, ambientAccent, 0.76f * ambientStrength),
        ),
    )
    val localizedContextLabel = localizedPlaybackContextLabel(state.contextLabel)
    val animationsEnabled = rememberAnimationsEnabled()
    ArtworkDynamicTheme(effectivePalette) {
        Card(
            modifier = modifier
                .fillMaxWidth()
                .padding(horizontal = PolentitaSpacing.small, vertical = 2.dp)
                .border(
                    1.dp,
                    effectivePalette.accent.copy(alpha = 0.24f + 0.18f * ambientStrength),
                    RoundedCornerShape(PolentitaRadii.large),
                ),
            shape = RoundedCornerShape(PolentitaRadii.large),
            colors = CardDefaults.cardColors(
                containerColor = effectivePalette.surface.copy(alpha = PolentitaOpacity.glass),
                contentColor = contentColorFor(MaterialTheme.colorScheme.surface),
            ),
            elevation = CardDefaults.cardElevation(PolentitaElevation.floating),
            onClick = onOpen,
        ) {
                Column {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .background(
                                Brush.horizontalGradient(
                                    listOf(
                                        effectivePalette.dominant.copy(alpha = 0.2f + 0.08f * ambientStrength),
                                        Color.Transparent,
                                    ),
                                ),
                            )
                            .padding(
                                horizontal = PolentitaSpacing.small,
                                vertical = 0.dp,
                            ),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        AnimatedContent(
                            targetState = state.artworkUri,
                            transitionSpec = {
                                (
                                    fadeIn(tween(animationDuration(animationsEnabled, 280))) +
                                        scaleIn(
                                            initialScale = 0.96f,
                                            animationSpec = tween(animationDuration(animationsEnabled, 280)),
                                        )
                                    ) togetherWith (
                                    fadeOut(tween(animationDuration(animationsEnabled, 220))) +
                                        scaleOut(
                                            targetScale = 0.96f,
                                            animationSpec = tween(animationDuration(animationsEnabled, 220)),
                                        )
                                    )
                            },
                            label = "Portada del mini reproductor",
                        ) { artwork ->
                            Artwork(
                                artwork,
                                stringResource(R.string.artwork_current_cover),
                                Modifier.size(PolentitaCoverSize.mini),
                                seed = "${state.title}|${state.artist}",
                            )
                        }
                        Spacer(Modifier.width(PolentitaSpacing.small))
                        AnimatedContent(
                            targetState = state.title to state.artist,
                            transitionSpec = {
                                fadeIn(tween(animationDuration(animationsEnabled, 240))) togetherWith
                                    fadeOut(tween(animationDuration(animationsEnabled, 180)))
                            },
                            modifier = Modifier.weight(1f),
                            label = "Texto del mini reproductor",
                        ) { (title, artist) ->
                            Column {
                                Text(
                                    title,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                Text(
                                if (state.isPreview) {
                                    "${stringResource(R.string.search_explore_preview_short)} · ${artist.ifBlank { stringResource(R.string.unknown_artist) }}"
                                } else {
                                    listOfNotNull(
                                        artist.ifBlank { stringResource(R.string.unknown_artist) },
                                        localizedContextLabel,
                                    ).joinToString(" · ")
                                },
                                    style = MaterialTheme.typography.bodySmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        IconButton(onClick = onToggle) {
                            AnimatedContent(
                                targetState = state.isPlaying,
                                label = "Reproducir o pausar",
                            ) { playing ->
                                Icon(
                                    if (playing) Icons.Default.Pause else Icons.Default.PlayArrow,
                                    contentDescription = stringResource(
                                        if (playing) R.string.pause else R.string.play,
                                    ),
                                )
                            }
                        }
                        onNext?.let { next ->
                            IconButton(onClick = next) {
                                Icon(
                                    Icons.Default.SkipNext,
                                    contentDescription = stringResource(R.string.player_next_song),
                                )
                            }
                        }
                    }
                    if (progressContent != null) {
                        progressContent()
                    } else if (state.durationMs > 0) {
                        LinearProgressIndicator(
                            progress = {
                                state.positionMs.toFloat() / state.durationMs.coerceAtLeast(1)
                            },
                            color = effectivePalette.accent,
                            trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
                            modifier = Modifier.fillMaxWidth().height(2.dp),
                        )
                    }
                }
        }
    }
}
