package com.polentita.music.core.storage

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.polentita.music.core.localization.AppLanguage
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore("polentita_preferences")

enum class ThemeMode { SYSTEM, LIGHT, DARK }

private const val DEFAULT_SORT = "TITLE"

data class AppPreferences(
    val libraryTreeUri: String? = null,
    val themeMode: ThemeMode = ThemeMode.DARK,
    val dynamicColor: Boolean = true,
    val restoreQueue: Boolean = true,
    val stopPlaybackOnTaskRemoved: Boolean = true,
    val pauseOnDisconnect: Boolean = true,
    val offlineMode: Boolean = false,
    val wifiOnlyDownloads: Boolean = false,
    val deleteFileByDefault: Boolean = false,
    val adaptiveArtworkTheme: Boolean = true,
    val searchSort: String = DEFAULT_SORT,
    val searchAscending: Boolean = true,
    val librarySort: String = DEFAULT_SORT,
    val libraryAscending: Boolean = true,
    val libraryGridMode: Boolean = false,
    val dinoHighScore: Int = 0,
    val language: AppLanguage = AppLanguage.defaultForBuild,
)

data class PlaybackSnapshot(
    val songIds: List<Long>,
    val currentIndex: Int,
    val positionMs: Long,
    val shuffle: Boolean,
    val repeatMode: Int,
    val queueOrigins: List<String> = emptyList(),
    val contextKind: String? = null,
    val contextKey: String? = null,
    val contextLabel: String? = null,
    val contextAnchorId: Long? = null,
)

@Singleton
class PreferencesStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private object Keys {
        val treeUri = stringPreferencesKey("library_tree_uri")
        val theme = stringPreferencesKey("theme_mode")
        val dynamic = booleanPreferencesKey("dynamic_color")
        val adaptiveArtworkTheme = booleanPreferencesKey("adaptive_artwork_theme")
        val restoreQueue = booleanPreferencesKey("restore_queue")
        val stopPlaybackOnTaskRemoved = booleanPreferencesKey("stop_playback_on_task_removed")
        val pauseOnDisconnect = booleanPreferencesKey("pause_on_disconnect")
        val offlineMode = booleanPreferencesKey("offline_mode")
        val wifiOnly = booleanPreferencesKey("wifi_only_downloads")
        val deleteDefault = booleanPreferencesKey("delete_file_default")
        val searchSort = stringPreferencesKey("search_sort")
        val searchAscending = booleanPreferencesKey("search_sort_ascending")
        val librarySort = stringPreferencesKey("library_sort")
        val libraryAscending = booleanPreferencesKey("library_sort_ascending")
        val libraryGridMode = booleanPreferencesKey("library_grid_mode")
        val dinoHighScore = intPreferencesKey("dino_high_score")
        val language = stringPreferencesKey("language")
        val queueIds = stringPreferencesKey("queue_ids")
        val queueIndex = stringPreferencesKey("queue_index")
        val queuePosition = longPreferencesKey("queue_position")
        val queueShuffle = booleanPreferencesKey("queue_shuffle")
        val queueRepeat = stringPreferencesKey("queue_repeat")
        val queueFormatVersion = intPreferencesKey("queue_format_version")
        val queueOrigins = stringPreferencesKey("queue_origins")
        val queueContextKind = stringPreferencesKey("queue_context_kind")
        val queueContextKey = stringPreferencesKey("queue_context_key")
        val queueContextLabel = stringPreferencesKey("queue_context_label")
        val queueContextAnchor = longPreferencesKey("queue_context_anchor")
        val playbackLibraryIds = stringPreferencesKey("playback_library_ids")
    }

    val preferences: Flow<AppPreferences> = context.dataStore.data.map { values ->
        AppPreferences(
            libraryTreeUri = values[Keys.treeUri],
            themeMode = runCatching { ThemeMode.valueOf(values[Keys.theme] ?: ThemeMode.DARK.name) }
                .getOrDefault(ThemeMode.DARK),
            dynamicColor = values[Keys.dynamic] ?: true,
            restoreQueue = values[Keys.restoreQueue] ?: true,
            stopPlaybackOnTaskRemoved = values[Keys.stopPlaybackOnTaskRemoved] ?: true,
            pauseOnDisconnect = values[Keys.pauseOnDisconnect] ?: true,
            offlineMode = values[Keys.offlineMode] ?: false,
            wifiOnlyDownloads = values[Keys.wifiOnly] ?: false,
            deleteFileByDefault = values[Keys.deleteDefault] ?: false,
            adaptiveArtworkTheme = values[Keys.adaptiveArtworkTheme] ?: true,
            searchSort = values[Keys.searchSort] ?: DEFAULT_SORT,
            searchAscending = values[Keys.searchAscending] ?: true,
            librarySort = values[Keys.librarySort] ?: DEFAULT_SORT,
            libraryAscending = values[Keys.libraryAscending] ?: true,
            libraryGridMode = values[Keys.libraryGridMode] ?: false,
            dinoHighScore = (values[Keys.dinoHighScore] ?: 0).coerceAtLeast(0),
            language = AppLanguage.fromTag(
                values[Keys.language],
                fallback = AppLanguage.defaultForBuild,
            ),
        )
    }

    suspend fun current(): AppPreferences = preferences.first()

    suspend fun setLibraryTreeUri(uri: String?) = context.dataStore.edit {
        if (uri == null) it.remove(Keys.treeUri) else it[Keys.treeUri] = uri
    }

    suspend fun setTheme(mode: ThemeMode) = context.dataStore.edit { it[Keys.theme] = mode.name }
    suspend fun setDynamicColor(enabled: Boolean) = context.dataStore.edit { it[Keys.dynamic] = enabled }
    suspend fun setAdaptiveArtworkTheme(enabled: Boolean) = context.dataStore.edit {
        it[Keys.adaptiveArtworkTheme] = enabled
    }
    suspend fun setRestoreQueue(enabled: Boolean) = context.dataStore.edit { it[Keys.restoreQueue] = enabled }
    suspend fun setStopPlaybackOnTaskRemoved(enabled: Boolean) = context.dataStore.edit {
        it[Keys.stopPlaybackOnTaskRemoved] = enabled
    }
    suspend fun setPauseOnDisconnect(enabled: Boolean) = context.dataStore.edit { it[Keys.pauseOnDisconnect] = enabled }
    suspend fun setOfflineMode(enabled: Boolean) = context.dataStore.edit { it[Keys.offlineMode] = enabled }
    suspend fun setWifiOnlyDownloads(enabled: Boolean) = context.dataStore.edit { it[Keys.wifiOnly] = enabled }
    suspend fun setDeleteFileDefault(enabled: Boolean) = context.dataStore.edit { it[Keys.deleteDefault] = enabled }
    suspend fun setSearchSort(sort: String) = context.dataStore.edit { it[Keys.searchSort] = sort }
    suspend fun setSearchAscending(ascending: Boolean) = context.dataStore.edit {
        it[Keys.searchAscending] = ascending
    }
    suspend fun setLibrarySort(sort: String) = context.dataStore.edit { it[Keys.librarySort] = sort }
    suspend fun setLibraryAscending(ascending: Boolean) = context.dataStore.edit {
        it[Keys.libraryAscending] = ascending
    }
    suspend fun setLibraryGridMode(enabled: Boolean) = context.dataStore.edit {
        it[Keys.libraryGridMode] = enabled
    }
    suspend fun recordDinoHighScore(score: Int) = context.dataStore.edit { values ->
        val candidate = score.coerceAtLeast(0)
        val current = values[Keys.dinoHighScore] ?: 0
        if (candidate > current) values[Keys.dinoHighScore] = candidate
    }
    suspend fun setLanguage(language: AppLanguage) = context.dataStore.edit {
        it[Keys.language] = language.tag
    }

    suspend fun savePlaybackSnapshot(snapshot: PlaybackSnapshot) = context.dataStore.edit {
        it[Keys.queueFormatVersion] = QUEUE_FORMAT_VERSION
        it[Keys.queueIds] = snapshot.songIds.joinToString(",")
        it[Keys.queueIndex] = snapshot.currentIndex.toString()
        it[Keys.queuePosition] = snapshot.positionMs
        it[Keys.queueShuffle] = snapshot.shuffle
        it[Keys.queueRepeat] = snapshot.repeatMode.toString()
        it[Keys.queueOrigins] = snapshot.queueOrigins.joinToString(",")
        snapshot.contextKind?.let { value -> it[Keys.queueContextKind] = value }
            ?: it.remove(Keys.queueContextKind)
        snapshot.contextKey?.let { value -> it[Keys.queueContextKey] = value }
            ?: it.remove(Keys.queueContextKey)
        snapshot.contextLabel?.let { value -> it[Keys.queueContextLabel] = value }
            ?: it.remove(Keys.queueContextLabel)
        snapshot.contextAnchorId?.let { value -> it[Keys.queueContextAnchor] = value }
            ?: it.remove(Keys.queueContextAnchor)
    }

    suspend fun playbackSnapshot(): PlaybackSnapshot? {
        val values = context.dataStore.data.first()
        val formatVersion = values[Keys.queueFormatVersion] ?: return null
        if (formatVersion !in SUPPORTED_QUEUE_FORMATS) return null
        val ids = values[Keys.queueIds]?.split(',')
            ?.mapNotNull(String::toLongOrNull)
            ?.takeIf { it.isNotEmpty() }
            ?: return null
        return PlaybackSnapshot(
            songIds = ids,
            currentIndex = values[Keys.queueIndex]?.toIntOrNull()?.coerceIn(ids.indices) ?: 0,
            positionMs = values[Keys.queuePosition] ?: 0,
            shuffle = values[Keys.queueShuffle] ?: false,
            repeatMode = values[Keys.queueRepeat]?.toIntOrNull() ?: 0,
            queueOrigins = if (formatVersion >= QUEUE_FORMAT_VERSION) {
                values[Keys.queueOrigins]?.split(',')?.filter(String::isNotBlank).orEmpty()
            } else {
                emptyList()
            },
            contextKind = values[Keys.queueContextKind],
            contextKey = values[Keys.queueContextKey],
            contextLabel = values[Keys.queueContextLabel],
            contextAnchorId = values[Keys.queueContextAnchor],
        )
    }

    suspend fun savePlaybackLibraryOrder(songIds: List<Long>) = context.dataStore.edit {
        if (songIds.isEmpty()) {
            it.remove(Keys.playbackLibraryIds)
        } else {
            it[Keys.playbackLibraryIds] = songIds.distinct().joinToString(",")
        }
    }

    suspend fun playbackLibraryOrder(): List<Long> = context.dataStore.data.first()
        .get(Keys.playbackLibraryIds)
        ?.split(',')
        ?.mapNotNull(String::toLongOrNull)
        .orEmpty()

    private companion object {
        const val QUEUE_FORMAT_VERSION = 3
        val SUPPORTED_QUEUE_FORMATS = setOf(2, QUEUE_FORMAT_VERSION)
    }
}
