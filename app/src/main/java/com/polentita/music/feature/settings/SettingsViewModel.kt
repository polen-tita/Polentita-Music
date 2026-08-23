package com.polentita.music.feature.settings

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.polentita.music.core.storage.AppPreferences
import com.polentita.music.core.storage.BackupManager
import com.polentita.music.core.storage.LibraryStorage
import com.polentita.music.core.storage.PreferencesStore
import com.polentita.music.core.storage.ThemeMode
import com.polentita.music.core.launcher.LauncherIconApplyResult
import com.polentita.music.core.launcher.LauncherIconChoice
import com.polentita.music.core.launcher.LauncherIconManager
import com.polentita.music.core.launcher.LauncherIconState
import com.polentita.music.core.network.NetworkAccessPolicy
import com.polentita.music.core.network.NetworkAccessState
import com.polentita.music.core.localization.AppLanguage
import com.polentita.music.core.localization.withAppLanguage
import com.polentita.music.R
import coil3.imageLoader
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class SettingsUiState(
    val loading: Boolean = true,
    val preferences: AppPreferences = AppPreferences(),
    val message: String? = null,
    val error: String? = null,
    val networkAccess: NetworkAccessState = NetworkAccessState(),
    val launcherIcon: LauncherIconState = LauncherIconState(),
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val preferences: PreferencesStore,
    private val storage: LibraryStorage,
    private val backup: BackupManager,
    private val networkAccessPolicy: NetworkAccessPolicy,
    private val launcherIconManager: LauncherIconManager,
) : ViewModel() {
    private val operation = MutableStateFlow<Pair<String?, String?>>(null to null)
    private val launcherIcon = MutableStateFlow(launcherIconManager.currentState())
    val state: StateFlow<SettingsUiState> = combine(
        preferences.preferences,
        operation,
        networkAccessPolicy.state,
        launcherIcon,
    ) { prefs, op, access, icon ->
        SettingsUiState(
            loading = false,
            preferences = prefs,
            message = op.first,
            error = op.second,
            networkAccess = access,
            launcherIcon = icon,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState())

    fun setTheme(value: ThemeMode) = viewModelScope.launch { preferences.setTheme(value) }
    fun setDynamic(value: Boolean) = viewModelScope.launch { preferences.setDynamicColor(value) }
    fun setLanguage(value: AppLanguage) = viewModelScope.launch { preferences.setLanguage(value) }
    fun setAdaptiveArtworkTheme(value: Boolean) = viewModelScope.launch {
        preferences.setAdaptiveArtworkTheme(value)
    }
    fun setRestoreQueue(value: Boolean) = viewModelScope.launch { preferences.setRestoreQueue(value) }
    fun setStopPlaybackOnTaskRemoved(value: Boolean) = viewModelScope.launch {
        preferences.setStopPlaybackOnTaskRemoved(value)
    }
    fun setPauseOnDisconnect(value: Boolean) = viewModelScope.launch { preferences.setPauseOnDisconnect(value) }
    fun setOfflineMode(value: Boolean) = viewModelScope.launch { preferences.setOfflineMode(value) }
    fun setWifiOnly(value: Boolean) = viewModelScope.launch { preferences.setWifiOnlyDownloads(value) }
    fun setDeleteDefault(value: Boolean) = viewModelScope.launch { preferences.setDeleteFileDefault(value) }

    fun linkLibrary(uri: Uri) = operation {
        storage.linkLibrary(uri)
        it.getString(R.string.library_linked)
    }

    fun exportBackup(uri: Uri) = operation {
        val result = backup.export(uri)
        it.getString(R.string.backup_exported, result.songs, result.playlists)
    }

    fun importBackup(uri: Uri) = operation {
        val result = backup.import(uri)
        it.getString(R.string.backup_restored, result.songs)
    }

    fun clearCoverCache() = operation {
        withContext(Dispatchers.IO) {
            context.imageLoader.memoryCache?.clear()
            context.imageLoader.diskCache?.clear()
        }
        it.getString(R.string.cover_cache_cleared)
    }

    fun importLauncherIcon(uri: Uri) = operation { localizedContext ->
        launcherIcon.value = runCatching { launcherIconManager.importCustomIcon(uri) }
            .getOrElse {
                throw IllegalArgumentException(
                    localizedContext.getString(R.string.settings_launcher_icon_import_error),
                    it,
                )
            }
        localizedContext.getString(R.string.settings_launcher_icon_ready)
    }

    fun applyLauncherIcon(choice: LauncherIconChoice) = operation { localizedContext ->
        val result = launcherIconManager.apply(choice)
        launcherIcon.value = launcherIconManager.currentState()
        when (result) {
            LauncherIconApplyResult.PIN_REQUESTED ->
                localizedContext.getString(R.string.settings_launcher_icon_pin_requested)
            LauncherIconApplyResult.UPDATED ->
                localizedContext.getString(R.string.settings_launcher_icon_updated)
            LauncherIconApplyResult.RESTORED ->
                localizedContext.getString(R.string.settings_launcher_icon_restored)
            LauncherIconApplyResult.ALREADY_OFFICIAL ->
                localizedContext.getString(R.string.settings_launcher_icon_already_official)
            LauncherIconApplyResult.UNSUPPORTED -> throw IllegalStateException(
                localizedContext.getString(R.string.settings_launcher_icon_unsupported),
            )
        }
    }

    fun clearMessage() { operation.value = null to null }

    private fun operation(block: suspend (Context) -> String) {
        viewModelScope.launch {
            val localizedContext = context.withAppLanguage(preferences.current().language)
            runCatching { block(localizedContext) }
                .onSuccess { operation.value = it to null }
                .onFailure {
                    operation.value = null to (
                        it.message ?: localizedContext.getString(R.string.operation_failed)
                    )
                }
        }
    }
}
