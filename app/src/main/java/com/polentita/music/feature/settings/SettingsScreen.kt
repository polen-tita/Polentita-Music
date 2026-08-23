package com.polentita.music.feature.settings

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Image as ImageIcon
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.polentita.music.BuildConfig
import com.polentita.music.R
import com.polentita.music.core.common.formatBytes
import com.polentita.music.core.designsystem.PolentitaOpacity
import com.polentita.music.core.designsystem.PolentitaDropdownMenu
import com.polentita.music.core.designsystem.PolentitaRadii
import com.polentita.music.core.designsystem.PolentitaSpacing
import com.polentita.music.core.designsystem.PolentitaStatusPill
import com.polentita.music.core.designsystem.PolentitaStatusTone
import com.polentita.music.core.storage.ThemeMode
import com.polentita.music.core.localization.AppLanguage
import com.polentita.music.core.launcher.LauncherIconChoice
import com.polentita.music.core.launcher.LauncherIconState
import com.polentita.music.feature.library.LibraryViewModel
import com.polentita.music.feature.update.AppUpdateUiState
import coil3.compose.AsyncImage
import java.io.File
import kotlinx.coroutines.delay

private const val POLENTITA_REPOSITORY_URL = "https://github.com/polen-tita/Polentita-Music"
private const val GITHUB_ANDROID_PACKAGE = "com.github.android"

private fun openPolentitaRepository(context: Context) {
    val repositoryUri = Uri.parse(POLENTITA_REPOSITORY_URL)
    val browserIntent = Intent(Intent.ACTION_VIEW, repositoryUri)
    val githubIntent = Intent(Intent.ACTION_VIEW, repositoryUri).apply {
        setPackage(GITHUB_ANDROID_PACKAGE)
    }
    if (githubIntent.resolveActivity(context.packageManager) != null) {
        runCatching { context.startActivity(githubIntent) }
            .onFailure { context.startActivity(browserIntent) }
    } else {
        context.startActivity(browserIntent)
    }
}

@Composable
fun SettingsScreen(
    onImport: () -> Unit,
    onDownloads: () -> Unit,
    onAbout: () -> Unit,
    updateState: AppUpdateUiState = AppUpdateUiState(),
    onUpdate: () -> Unit = {},
    onOpenUpdatePage: () -> Unit = {},
    settingsViewModel: SettingsViewModel = hiltViewModel(),
    libraryViewModel: LibraryViewModel = hiltViewModel(),
) {
    val state by settingsViewModel.state.collectAsStateWithLifecycle()
    val library by libraryViewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var advancedOptions by rememberSaveable { mutableStateOf(false) }
    var themeMenuExpanded by rememberSaveable { mutableStateOf(false) }
    var languageMenuExpanded by rememberSaveable { mutableStateOf(false) }
    var launcherIconChoice by rememberSaveable {
        mutableStateOf(LauncherIconChoice.OFFICIAL.name)
    }
    val relink = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        runCatching {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
        }
        settingsViewModel.linkLibrary(uri)
    }
    val exportBackup = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip"),
    ) { uri -> uri?.let(settingsViewModel::exportBackup) }
    val importBackup = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri -> uri?.let(settingsViewModel::importBackup) }
    val scanDevice = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) libraryViewModel.scanDeviceMusic()
    }
    val launcherIconPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri -> uri?.let(settingsViewModel::importLauncherIcon) }

    LaunchedEffect(state.launcherIcon.customIconPath) {
        if (state.launcherIcon.customIconPath != null) {
            launcherIconChoice = LauncherIconChoice.CUSTOM.name
        }
    }

    LaunchedEffect(state.message, state.error) {
        if (state.message != null || state.error != null) {
            delay(4_000)
            settingsViewModel.clearMessage()
        }
    }

    LazyColumn(Modifier.fillMaxSize()) {
        item {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(PolentitaSpacing.small),
                verticalAlignment = Alignment.Top,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.settings_title),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Black,
                    )
                    Text(
                        stringResource(R.string.settings_subtitle),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Box {
                    IconButton(
                        onClick = { languageMenuExpanded = true },
                        modifier = Modifier.size(48.dp),
                    ) {
                        Image(
                            painter = painterResource(state.preferences.language.flagRes),
                            contentDescription = stringResource(
                                R.string.settings_language_current,
                                stringResource(state.preferences.language.accessibilityRes),
                            ),
                            modifier = Modifier.size(28.dp),
                        )
                    }
                    PolentitaDropdownMenu(
                        expanded = languageMenuExpanded,
                        onDismissRequest = { languageMenuExpanded = false },
                    ) {
                        AppLanguage.entries.forEach { language ->
                            DropdownMenuItem(
                                text = {
                                    Image(
                                        painter = painterResource(language.flagRes),
                                        contentDescription = stringResource(language.accessibilityRes),
                                        modifier = Modifier.size(28.dp),
                                    )
                                },
                                trailingIcon = if (language == state.preferences.language) {
                                    {
                                        Icon(
                                            androidx.compose.material.icons.Icons.Default.Check,
                                            contentDescription = stringResource(R.string.settings_language_selected),
                                            tint = MaterialTheme.colorScheme.primary,
                                        )
                                    }
                                } else {
                                    null
                                },
                                onClick = {
                                    settingsViewModel.setLanguage(language)
                                    languageMenuExpanded = false
                                },
                            )
                        }
                    }
                }
            }
        }
        item {
            SettingsCard(stringResource(R.string.settings_appearance)) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.settings_theme))
                        Text(
                            stringResource(R.string.settings_theme_description),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Box {
                        TextButton(onClick = { themeMenuExpanded = true }) {
                            Text(themeLabel(state.preferences.themeMode))
                        }
                        PolentitaDropdownMenu(
                            expanded = themeMenuExpanded,
                            onDismissRequest = { themeMenuExpanded = false },
                        ) {
                            ThemeMode.entries.forEach { mode ->
                                DropdownMenuItem(
                                    text = { Text(themeLabel(mode)) },
                                    onClick = {
                                        settingsViewModel.setTheme(mode)
                                        themeMenuExpanded = false
                                    },
                                )
                            }
                        }
                    }
                }
                SettingSwitch(
                    stringResource(R.string.settings_dynamic_color),
                    state.preferences.dynamicColor,
                    settingsViewModel::setDynamic,
                )
            }
        }
        item {
            SettingsCard(stringResource(R.string.settings_library)) {
                Text(
                    if (state.preferences.libraryTreeUri.isNullOrBlank()) {
                        stringResource(R.string.settings_library_not_linked)
                    } else {
                        stringResource(R.string.settings_library_linked)
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    stringResource(
                        R.string.settings_library_summary,
                        library.songs.size,
                        formatBytes(library.songs.sumOf { it.fileSize }),
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
                OutlinedButton(
                    onClick = { relink.launch(null) },
                    modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                ) {
                    Text(stringResource(R.string.settings_change_folder))
                }
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    TextButton(
                        onClick = libraryViewModel::scanLibrary,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(stringResource(R.string.settings_refresh_library))
                    }
                    TextButton(
                        onClick = onImport,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(stringResource(R.string.settings_import_audio))
                    }
                }
            }
        }
        item {
            SettingsCard(stringResource(R.string.settings_playback)) {
                SettingSwitch(
                    stringResource(R.string.settings_pause_on_disconnect),
                    state.preferences.pauseOnDisconnect,
                    settingsViewModel::setPauseOnDisconnect,
                )
            }
        }
        item {
            SettingsCard(stringResource(R.string.settings_connectivity)) {
                SettingSwitch(
                    label = stringResource(R.string.settings_offline_mode),
                    description = stringResource(R.string.settings_offline_mode_description),
                    checked = state.preferences.offlineMode,
                    update = settingsViewModel::setOfflineMode,
                )
                SettingSwitch(
                    label = stringResource(R.string.settings_wifi_only),
                    description = stringResource(R.string.settings_wifi_only_description),
                    checked = state.preferences.wifiOnlyDownloads,
                    update = settingsViewModel::setWifiOnly,
                    enabled = !state.preferences.offlineMode,
                )
                val connectivityLabel = when {
                        state.preferences.offlineMode -> stringResource(R.string.settings_connectivity_offline_active)
                        !state.networkAccess.connected -> stringResource(R.string.settings_connectivity_no_internet)
                        state.networkAccess.downloadAllowed -> stringResource(R.string.settings_connectivity_available)
                        else -> stringResource(R.string.settings_connectivity_wifi_needed)
                    }
                PolentitaStatusPill(
                    text = connectivityLabel,
                    tone = when {
                        state.preferences.offlineMode -> PolentitaStatusTone.NEUTRAL
                        !state.networkAccess.connected -> PolentitaStatusTone.ERROR
                        state.networkAccess.downloadAllowed -> PolentitaStatusTone.SUCCESS
                        else -> PolentitaStatusTone.WARNING
                    },
                    modifier = Modifier.padding(top = PolentitaSpacing.xs),
                )
            }
        }
        item {
            CollaborationCard(
                onOpenRepository = { openPolentitaRepository(context) },
            )
        }
        item {
            SettingsCard(stringResource(R.string.settings_downloads)) {
                Button(
                    onClick = onDownloads,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                ) { Text(stringResource(R.string.settings_download_audio)) }
            }
        }
        if (updateState.info != null && updateState.dismissed) {
            item {
                UpdateAvailableCard(
                    versionName = updateState.info.versionName,
                    downloading = updateState.downloading,
                    downloadFailed = updateState.downloadFailed,
                    onUpdate = onUpdate,
                    onOpenUpdatePage = onOpenUpdatePage,
                )
            }
        }
        item {
            TextButton(
                onClick = { advancedOptions = !advancedOptions },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
            ) {
                Text(
                    stringResource(
                        if (advancedOptions) {
                            R.string.settings_hide_more_options
                        } else {
                            R.string.settings_more_options
                        },
                    ),
                )
            }
        }
        item {
            AnimatedVisibility(
                visible = advancedOptions,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically(),
            ) {
                Column {
                    LauncherIconSettingsCard(
                        state = state.launcherIcon,
                        selected = runCatching {
                            LauncherIconChoice.valueOf(launcherIconChoice)
                        }.getOrDefault(LauncherIconChoice.OFFICIAL),
                        onSelect = { launcherIconChoice = it.name },
                        onImport = { launcherIconPicker.launch(arrayOf("image/*")) },
                        onDone = {
                            settingsViewModel.applyLauncherIcon(
                                runCatching {
                                    LauncherIconChoice.valueOf(launcherIconChoice)
                                }.getOrDefault(LauncherIconChoice.OFFICIAL),
                            )
                        },
                    )
                    SettingsCard(stringResource(R.string.settings_playback)) {
                        SettingSwitch(
                            stringResource(R.string.settings_restore_queue),
                            state.preferences.restoreQueue,
                            settingsViewModel::setRestoreQueue,
                        )
                        SettingSwitch(
                            stringResource(R.string.settings_stop_on_close),
                            state.preferences.stopPlaybackOnTaskRemoved,
                            settingsViewModel::setStopPlaybackOnTaskRemoved,
                        )
                    }
                    SettingsCard(stringResource(R.string.settings_library)) {
                        TextButton(
                            onClick = {
                                scanDevice.launch(
                                    if (Build.VERSION.SDK_INT >= 33) {
                                        Manifest.permission.READ_MEDIA_AUDIO
                                    } else {
                                        Manifest.permission.READ_EXTERNAL_STORAGE
                                    },
                                )
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text(stringResource(R.string.settings_scan_device)) }
                        SettingSwitch(
                            stringResource(R.string.settings_delete_default),
                            state.preferences.deleteFileByDefault,
                            settingsViewModel::setDeleteDefault,
                        )
                        TextButton(
                            onClick = settingsViewModel::clearCoverCache,
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text(stringResource(R.string.settings_clear_covers)) }
                    }
                    SettingsCard(stringResource(R.string.settings_backups)) {
                        Button(
                            onClick = { exportBackup.launch("polentita-backup.zip") },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text(stringResource(R.string.settings_export_backup)) }
                        TextButton(
                            onClick = { importBackup.launch(arrayOf("application/zip", "application/octet-stream")) },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text(stringResource(R.string.settings_import_backup)) }
                    }
                }
            }
        }
        item {
            state.message?.let {
                Text(it, Modifier.padding(16.dp), color = MaterialTheme.colorScheme.primary)
            }
            state.error?.let {
                Text(it, Modifier.padding(16.dp), color = MaterialTheme.colorScheme.error)
            }
        }
        item {
            TextButton(onClick = onAbout, Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.settings_about_version, BuildConfig.VERSION_NAME))
            }
        }
        item { Spacer(Modifier.padding(bottom = 112.dp)) }
    }
}

@Composable
private fun LauncherIconSettingsCard(
    state: LauncherIconState,
    selected: LauncherIconChoice,
    onSelect: (LauncherIconChoice) -> Unit,
    onImport: () -> Unit,
    onDone: () -> Unit,
) {
    SettingsCard(stringResource(R.string.settings_launcher_icon)) {
        Text(
            stringResource(R.string.settings_launcher_icon_description),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        LauncherIconOption(
            selected = selected == LauncherIconChoice.OFFICIAL,
            onClick = { onSelect(LauncherIconChoice.OFFICIAL) },
            preview = {
                Image(
                    painter = painterResource(R.mipmap.ic_launcher_foreground),
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.size(56.dp),
                )
            },
            title = stringResource(R.string.app_name),
            description = stringResource(R.string.settings_launcher_icon_official_description),
        )
        state.customIconPath?.let { path ->
            LauncherIconOption(
                selected = selected == LauncherIconChoice.CUSTOM,
                onClick = { onSelect(LauncherIconChoice.CUSTOM) },
                preview = {
                    AsyncImage(
                        model = File(path),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(56.dp)
                            .clip(androidx.compose.foundation.shape.RoundedCornerShape(16.dp)),
                    )
                },
                title = stringResource(R.string.settings_launcher_icon_custom),
                description = stringResource(R.string.settings_launcher_icon_custom_description),
            )
        }
        OutlinedButton(
            onClick = onImport,
            enabled = state.pinningSupported,
            modifier = Modifier.fillMaxWidth().padding(top = PolentitaSpacing.small),
        ) {
            Icon(
                imageVector = androidx.compose.material.icons.Icons.Default.ImageIcon,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
            )
            Text(
                stringResource(R.string.settings_launcher_icon_import),
                modifier = Modifier.padding(start = PolentitaSpacing.small),
            )
        }
        Button(
            onClick = onDone,
            enabled = state.pinningSupported && (
                selected == LauncherIconChoice.OFFICIAL || state.customIconPath != null
            ),
            modifier = Modifier.fillMaxWidth().padding(top = PolentitaSpacing.small),
        ) {
            Text(stringResource(R.string.settings_launcher_icon_done))
        }
        if (!state.pinningSupported) {
            PolentitaStatusPill(
                text = stringResource(R.string.settings_launcher_icon_unsupported),
                tone = PolentitaStatusTone.WARNING,
                modifier = Modifier.padding(top = PolentitaSpacing.small),
            )
        }
    }
}

@Composable
private fun LauncherIconOption(
    selected: Boolean,
    onClick: () -> Unit,
    preview: @Composable () -> Unit,
    title: String,
    description: String,
) {
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().padding(top = PolentitaSpacing.small),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(PolentitaRadii.medium),
        color = if (selected) {
            MaterialTheme.colorScheme.primary.copy(alpha = 0.11f)
        } else {
            MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.72f)
        },
        border = BorderStroke(
            1.dp,
            if (selected) {
                MaterialTheme.colorScheme.primary.copy(alpha = 0.72f)
            } else {
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.48f)
            },
        ),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(PolentitaSpacing.small),
            horizontalArrangement = Arrangement.spacedBy(PolentitaSpacing.small),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            preview()
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Text(
                    description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (selected) {
                Icon(
                    imageVector = androidx.compose.material.icons.Icons.Default.Check,
                    contentDescription = stringResource(R.string.settings_launcher_icon_selected),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

@Composable
private fun UpdateAvailableCard(
    versionName: String,
    downloading: Boolean,
    downloadFailed: Boolean,
    onUpdate: () -> Unit,
    onOpenUpdatePage: () -> Unit,
) {
    val updateColor = MaterialTheme.colorScheme.error
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = PolentitaSpacing.medium, vertical = PolentitaSpacing.xs),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(PolentitaRadii.medium),
        border = BorderStroke(1.dp, updateColor.copy(alpha = 0.48f)),
        colors = CardDefaults.cardColors(
            containerColor = updateColor.copy(alpha = 0.13f),
        ),
    ) {
        Column(Modifier.padding(PolentitaSpacing.medium)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(PolentitaSpacing.small),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = androidx.compose.material.icons.Icons.Default.SystemUpdate,
                    contentDescription = null,
                    tint = updateColor,
                    modifier = Modifier.size(26.dp),
                )
                Column(Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.settings_update_title),
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        stringResource(R.string.settings_update_description, versionName),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Button(
                onClick = onUpdate,
                enabled = !downloading,
                modifier = Modifier.fillMaxWidth().padding(top = PolentitaSpacing.small),
            ) {
                if (downloading) {
                    androidx.compose.material3.CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                    )
                    Text(
                        stringResource(R.string.settings_update_downloading),
                        modifier = Modifier.padding(start = PolentitaSpacing.small),
                    )
                } else {
                    Text(stringResource(R.string.settings_update_action))
                }
            }
            if (downloadFailed) {
                Text(
                    stringResource(R.string.settings_update_error),
                    style = MaterialTheme.typography.bodySmall,
                    color = updateColor,
                    modifier = Modifier.padding(top = PolentitaSpacing.small),
                )
                TextButton(
                    onClick = onOpenUpdatePage,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.settings_update_open_page))
                }
            }
        }
    }
}

@Composable
private fun CollaborationCard(onOpenRepository: () -> Unit) {
    val gold = if (MaterialTheme.colorScheme.background.luminance() < 0.5f) {
        Color(0xFFFFC857)
    } else {
        Color(0xFF9A6200)
    }

    SettingsCard(stringResource(R.string.settings_collaborate)) {
        Surface(
            onClick = onOpenRepository,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = PolentitaSpacing.small),
            shape = androidx.compose.foundation.shape.RoundedCornerShape(PolentitaRadii.medium),
            color = gold.copy(alpha = 0.13f),
            contentColor = MaterialTheme.colorScheme.onSurface,
            border = BorderStroke(1.dp, gold.copy(alpha = 0.48f)),
            tonalElevation = 0.dp,
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = PolentitaSpacing.small, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(PolentitaSpacing.small),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = androidx.compose.material.icons.Icons.Default.Star,
                    contentDescription = null,
                    tint = gold,
                    modifier = Modifier.size(28.dp),
                )
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f),
                    tonalElevation = 0.dp,
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_github),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(7.dp).size(24.dp),
                    )
                }
                Image(
                    painter = painterResource(R.mipmap.ic_launcher_foreground),
                    contentDescription = null,
                    contentScale = androidx.compose.ui.layout.ContentScale.Fit,
                    modifier = Modifier.size(40.dp),
                )
                Column(Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.settings_collaborate_title),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        stringResource(R.string.settings_collaborate_description),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Icon(
                    imageVector = androidx.compose.material.icons.Icons.Default.OpenInNew,
                    contentDescription = stringResource(R.string.settings_collaborate_button_description),
                    tint = gold,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

@Composable
private fun SettingsCard(
    title: String,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = PolentitaSpacing.medium, vertical = PolentitaSpacing.xs),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(PolentitaRadii.medium),
        border = BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = PolentitaOpacity.border),
        ),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.68f),
        ),
    ) {
        Column(Modifier.padding(PolentitaSpacing.medium)) {
            Text(
                title,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.padding(top = PolentitaSpacing.xs))
            content()
        }
    }
}

@Composable
private fun themeLabel(mode: ThemeMode): String = when (mode) {
    ThemeMode.SYSTEM -> stringResource(R.string.settings_theme_system)
    ThemeMode.LIGHT -> stringResource(R.string.settings_theme_light)
    ThemeMode.DARK -> stringResource(R.string.settings_theme_dark)
}

@Composable
private fun SettingSwitch(
    label: String,
    checked: Boolean,
    update: (Boolean) -> Unit,
    description: String? = null,
    enabled: Boolean = true,
) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = PolentitaSpacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            Modifier.weight(1f).padding(end = PolentitaSpacing.medium),
        ) {
            Text(
                label,
                color = if (enabled) MaterialTheme.colorScheme.onSurface else {
                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.46f)
                },
            )
            description?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(
                        alpha = if (enabled) 1f else 0.48f,
                    ),
                )
            }
        }
        Switch(
            checked = checked,
            onCheckedChange = update,
            enabled = enabled,
        )
    }
}

@Composable
fun AboutScreen(onBack: () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(PolentitaSpacing.large),
        verticalArrangement = Arrangement.spacedBy(PolentitaSpacing.medium),
    ) {
        TextButton(onClick = onBack) { Text(stringResource(R.string.about_back)) }
        Column {
            Text(
                stringResource(R.string.app_name),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Black,
            )
            Text(
                stringResource(R.string.about_version, BuildConfig.VERSION_NAME),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Surface(
            shape = androidx.compose.foundation.shape.RoundedCornerShape(PolentitaRadii.large),
            color = MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.72f),
            border = BorderStroke(
                1.dp,
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = PolentitaOpacity.border),
            ),
        ) {
            Text(
                stringResource(R.string.about_description),
                Modifier.padding(PolentitaSpacing.large),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Surface(
            shape = androidx.compose.foundation.shape.RoundedCornerShape(PolentitaRadii.medium),
            color = MaterialTheme.colorScheme.surfaceContainerLowest.copy(alpha = 0.54f),
            border = BorderStroke(
                1.dp,
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.20f),
            ),
        ) {
            Column(
                Modifier.padding(PolentitaSpacing.large),
                verticalArrangement = Arrangement.spacedBy(PolentitaSpacing.small),
            ) {
                Text(stringResource(R.string.about_code), style = MaterialTheme.typography.titleMedium)
                Text(
                    stringResource(R.string.about_license),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    stringResource(R.string.about_dependencies),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.weight(1f))
    }
}
