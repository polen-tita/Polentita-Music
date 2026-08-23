package com.polentita.music.feature.update

import android.net.Uri
import android.os.SystemClock
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.polentita.music.core.storage.PreferencesStore
import com.polentita.music.core.update.AppUpdateChecker
import com.polentita.music.core.update.AppUpdateInfo
import com.polentita.music.core.update.AppUpdateInstaller
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

data class AppUpdateUiState(
    val info: AppUpdateInfo? = null,
    val dismissed: Boolean = false,
    val checking: Boolean = false,
    val downloading: Boolean = false,
    val downloadFailed: Boolean = false,
    val downloadedUri: Uri? = null,
)

@HiltViewModel
class AppUpdateViewModel @Inject constructor(
    private val checker: AppUpdateChecker,
    private val installer: AppUpdateInstaller,
    private val preferencesStore: PreferencesStore,
) : ViewModel() {
    private val mutableState = MutableStateFlow(AppUpdateUiState())
    val state: StateFlow<AppUpdateUiState> = mutableState.asStateFlow()
    private var lastCheckAt = 0L

    fun checkForUpdate() {
        val now = SystemClock.elapsedRealtime()
        if (now - lastCheckAt < CHECK_INTERVAL_MS) return
        lastCheckAt = now
        mutableState.update { it.copy(checking = true) }
        viewModelScope.launch {
            val language = preferencesStore.current().language
            val latest = checker.check(language)
            mutableState.update { current ->
                if (latest == null) {
                    current.copy(checking = false)
                } else {
                    val isSameVersion = current.info?.versionCode == latest.versionCode
                    current.copy(
                        info = latest,
                        dismissed = if (isSameVersion) current.dismissed else false,
                        checking = false,
                        downloadFailed = false,
                    )
                }
            }
        }
    }

    fun dismiss() {
        mutableState.update { it.copy(dismissed = true) }
    }

    fun startDownload() {
        val info = state.value.info ?: return
        if (state.value.downloading) return
        mutableState.update {
            it.copy(
                downloading = true,
                downloadFailed = false,
                downloadedUri = null,
            )
        }
        viewModelScope.launch {
            try {
                val uri = installer.download(info)
                mutableState.update {
                    it.copy(downloading = false, downloadedUri = uri)
                }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                mutableState.update {
                    it.copy(downloading = false, downloadFailed = true)
                }
            }
        }
    }

    fun canRequestInstallation(): Boolean = installer.canRequestInstallation()

    fun consumeDownloadedUri(uri: Uri) {
        mutableState.update {
            if (it.downloadedUri == uri) it.copy(downloadedUri = null) else it
        }
    }

    private companion object {
        const val CHECK_INTERVAL_MS = 30 * 60 * 1000L
    }
}
