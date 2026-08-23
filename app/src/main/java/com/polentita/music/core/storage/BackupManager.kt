package com.polentita.music.core.storage

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import com.polentita.music.core.database.AlbumEntity
import com.polentita.music.core.database.BackupDao
import com.polentita.music.core.database.DatabaseBackup
import com.polentita.music.core.database.PlaybackHistoryEntity
import com.polentita.music.core.database.PlaylistEntity
import com.polentita.music.core.database.PlaylistSongCrossRef
import com.polentita.music.core.database.SongEntity
import com.polentita.music.core.localization.AppLanguage
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

data class BackupResult(val songs: Int, val albums: Int, val playlists: Int)

@Singleton
class BackupManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val backupDao: BackupDao,
    private val preferencesStore: PreferencesStore,
) {
    private val resolver: ContentResolver get() = context.contentResolver

    suspend fun export(destination: Uri, includeHistory: Boolean = true): BackupResult =
        withContext(Dispatchers.IO) {
            val backup = backupDao.snapshot(includeHistory)
            val preferences = preferencesStore.current()
            val root = encode(backup, preferences)
            resolver.openOutputStream(destination, "w").use { raw ->
                requireNotNull(raw) { "No se pudo crear la copia" }
                ZipOutputStream(BufferedOutputStream(raw)).use { zip ->
                    zip.putNextEntry(ZipEntry(ENTRY_NAME))
                    zip.writer(Charsets.UTF_8).use { it.write(root.toString()) }
                }
            }
            BackupResult(backup.songs.size, backup.albums.size, backup.playlists.size)
        }

    suspend fun import(source: Uri): BackupResult = withContext(Dispatchers.IO) {
        val text = resolver.openInputStream(source).use { raw ->
            requireNotNull(raw) { "No se pudo abrir la copia" }
            ZipInputStream(BufferedInputStream(raw)).use { zip ->
                var entry = zip.nextEntry
                while (entry != null && entry.name != ENTRY_NAME) entry = zip.nextEntry
                require(entry?.name == ENTRY_NAME) { "La copia no contiene $ENTRY_NAME" }
                zip.reader(Charsets.UTF_8).readText()
            }
        }
        val root = JSONObject(text)
        require(root.optInt("version", -1) == FORMAT_VERSION) {
            "Versión de copia no compatible"
        }
        val decoded = decode(root)
        backupDao.restore(decoded)
        val pref = root.optJSONObject("preferences")
        if (pref != null) {
            val themeName = pref.optString("theme")
            if (themeName.isNotBlank()) {
                runCatching { ThemeMode.valueOf(themeName) }.getOrNull()?.let { mode ->
                    preferencesStore.setTheme(mode)
                }
            }
            preferencesStore.setDynamicColor(pref.optBoolean("dynamicColor", true))
            preferencesStore.setAdaptiveArtworkTheme(
                pref.optBoolean("adaptiveArtworkTheme", true),
            )
            preferencesStore.setRestoreQueue(pref.optBoolean("restoreQueue", true))
            preferencesStore.setStopPlaybackOnTaskRemoved(
                pref.optBoolean("stopPlaybackOnTaskRemoved", true),
            )
            preferencesStore.setPauseOnDisconnect(pref.optBoolean("pauseOnDisconnect", true))
            preferencesStore.setOfflineMode(pref.optBoolean("offlineMode", false))
            preferencesStore.setWifiOnlyDownloads(pref.optBoolean("wifiOnlyDownloads", false))
            preferencesStore.recordDinoHighScore(pref.optInt("dinoHighScore", 0))
            val languageTag = pref.optString("language")
            if (languageTag.isNotBlank()) {
                val language = AppLanguage.entries.firstOrNull { it.tag == languageTag }
                if (language != null) preferencesStore.setLanguage(language)
            }
        }
        BackupResult(decoded.songs.size, decoded.albums.size, decoded.playlists.size)
    }

    private fun encode(backup: DatabaseBackup, preferences: AppPreferences) = JSONObject().apply {
        put("version", FORMAT_VERSION)
        put("createdAt", System.currentTimeMillis())
        put("audioIncluded", false)
        put("songs", JSONArray().apply { backup.songs.forEach { put(it.json()) } })
        put("albums", JSONArray().apply { backup.albums.forEach { put(it.json()) } })
        put("playlists", JSONArray().apply { backup.playlists.forEach { put(it.json()) } })
        put("playlistSongs", JSONArray().apply { backup.playlistSongs.forEach { put(it.json()) } })
        put("history", JSONArray().apply { backup.history.forEach { put(it.json()) } })
        put(
            "preferences",
            JSONObject().apply {
                put("theme", preferences.themeMode.name)
                put("dynamicColor", preferences.dynamicColor)
                put("adaptiveArtworkTheme", preferences.adaptiveArtworkTheme)
                put("restoreQueue", preferences.restoreQueue)
                put("stopPlaybackOnTaskRemoved", preferences.stopPlaybackOnTaskRemoved)
                put("pauseOnDisconnect", preferences.pauseOnDisconnect)
                put("offlineMode", preferences.offlineMode)
                put("wifiOnlyDownloads", preferences.wifiOnlyDownloads)
                put("dinoHighScore", preferences.dinoHighScore)
                put("language", preferences.language.tag)
            },
        )
    }

    private fun decode(root: JSONObject) = DatabaseBackup(
        songs = root.array("songs").objects().map(::song),
        albums = root.array("albums").objects().map(::album),
        playlists = root.array("playlists").objects().map(::playlist),
        playlistSongs = root.array("playlistSongs").objects().map(::playlistSong),
        history = root.array("history").objects().map(::history),
    )

    private fun SongEntity.json() = JSONObject().apply {
        put("id", id); put("title", title); put("artist", artist); putNullable("albumId", albumId)
        put("albumName", albumName); put("genre", genre); putNullable("year", year)
        putNullable("trackNumber", trackNumber); putNullable("discNumber", discNumber)
        put("durationMs", durationMs); put("contentUri", contentUri)
        put("originalFileName", originalFileName); put("displayFileName", displayFileName)
        put("mimeType", mimeType); put("fileSize", fileSize); putNullable("coverUri", coverUri)
        put("sourceType", sourceType); putNullable("sourceUrl", sourceUrl); put("dateAdded", dateAdded)
        put("dateModified", dateModified); putNullable("lastPlayedAt", lastPlayedAt)
        put("playCount", playCount); put("isFavorite", isFavorite); put("isAvailable", isAvailable)
        put("checksum", checksum); putNullable("isrc", isrc)
    }

    private fun AlbumEntity.json() = JSONObject().apply {
        put("id", id); put("name", name); put("artist", artist); putNullable("year", year)
        putNullable("coverUri", coverUri); put("dateCreated", dateCreated)
    }

    private fun PlaylistEntity.json() = JSONObject().apply {
        put("id", id); put("name", name); put("description", description)
        putNullable("coverUri", coverUri); put("dateCreated", dateCreated); put("dateModified", dateModified)
    }

    private fun PlaylistSongCrossRef.json() = JSONObject().apply {
        put("playlistId", playlistId); put("songId", songId); put("position", position); put("dateAdded", dateAdded)
    }

    private fun PlaybackHistoryEntity.json() = JSONObject().apply {
        put("id", id); put("songId", songId); put("playedAt", playedAt)
        put("completed", completed); put("playbackPositionMs", playbackPositionMs)
    }

    private fun song(o: JSONObject) = SongEntity(
        id = o.getLong("id"), title = o.getString("title"), artist = o.optString("artist"),
        albumId = o.optLongOrNull("albumId"), albumName = o.optString("albumName"),
        genre = o.optString("genre"), year = o.optIntOrNull("year"),
        trackNumber = o.optIntOrNull("trackNumber"), discNumber = o.optIntOrNull("discNumber"),
        durationMs = o.optLong("durationMs"), contentUri = o.getString("contentUri"),
        originalFileName = o.optString("originalFileName"), displayFileName = o.optString("displayFileName"),
        mimeType = o.optString("mimeType"), fileSize = o.optLong("fileSize"),
        coverUri = o.optStringOrNull("coverUri"), sourceType = o.optString("sourceType"),
        sourceUrl = o.optStringOrNull("sourceUrl"), dateAdded = o.optLong("dateAdded"),
        dateModified = o.optLong("dateModified"), lastPlayedAt = o.optLongOrNull("lastPlayedAt"),
        playCount = o.optLong("playCount"), isFavorite = o.optBoolean("isFavorite"),
        isAvailable = o.optBoolean("isAvailable", true), checksum = o.getString("checksum"),
        isrc = o.optStringOrNull("isrc"),
    )

    private fun album(o: JSONObject) = AlbumEntity(
        id = o.getLong("id"), name = o.getString("name"), artist = o.optString("artist"),
        year = o.optIntOrNull("year"), coverUri = o.optStringOrNull("coverUri"),
        dateCreated = o.optLong("dateCreated"),
    )

    private fun playlist(o: JSONObject) = PlaylistEntity(
        id = o.getLong("id"), name = o.getString("name"), description = o.optString("description"),
        coverUri = o.optStringOrNull("coverUri"), dateCreated = o.optLong("dateCreated"),
        dateModified = o.optLong("dateModified"),
    )

    private fun playlistSong(o: JSONObject) = PlaylistSongCrossRef(
        playlistId = o.getLong("playlistId"), songId = o.getLong("songId"),
        position = o.getInt("position"), dateAdded = o.optLong("dateAdded"),
    )

    private fun history(o: JSONObject) = PlaybackHistoryEntity(
        id = o.getLong("id"), songId = o.getLong("songId"), playedAt = o.optLong("playedAt"),
        completed = o.optBoolean("completed"), playbackPositionMs = o.optLong("playbackPositionMs"),
    )

    private fun JSONObject.putNullable(key: String, value: Any?) {
        put(key, value ?: JSONObject.NULL)
    }

    private fun JSONObject.array(key: String) = optJSONArray(key) ?: JSONArray()
    private fun JSONArray.objects() = (0 until length()).map(::getJSONObject)
    private fun JSONObject.optStringOrNull(key: String) =
        if (isNull(key)) null else optString(key).takeIf(String::isNotBlank)
    private fun JSONObject.optLongOrNull(key: String) = if (isNull(key) || !has(key)) null else getLong(key)
    private fun JSONObject.optIntOrNull(key: String) = if (isNull(key) || !has(key)) null else getInt(key)

    companion object {
        const val FORMAT_VERSION = 1
        const val ENTRY_NAME = "polentita-backup.json"
    }
}
