package com.polentita.music.core.update

import android.content.Context
import android.net.Uri
import android.os.Build
import androidx.core.content.FileProvider
import com.polentita.music.BuildConfig
import com.polentita.music.core.common.RemoteUrlValidator
import com.polentita.music.core.common.UrlValidation
import com.polentita.music.core.localization.AppLanguage
import com.polentita.music.core.network.NetworkAccessPolicy
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.security.MessageDigest
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.json.JSONObject

data class AppUpdateInfo(
    val versionCode: Int,
    val versionName: String,
    val releaseUrl: String,
    val downloadUrl: String?,
    val sha256: String?,
)

@Singleton
class AppUpdateChecker @Inject constructor(
    private val client: OkHttpClient,
    private val networkAccessPolicy: NetworkAccessPolicy,
) {
    suspend fun check(language: AppLanguage): AppUpdateInfo? {
        if (!networkAccessPolicy.current().remoteSearchAllowed) return null
        return withContext(Dispatchers.IO) {
            try {
                val request = Request.Builder()
                    .url(LATEST_MANIFEST_URL)
                    .header("Accept", "application/json")
                    .build()
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@withContext null
                    val body = response.body ?: return@withContext null
                    if (body.contentLength() > MAX_MANIFEST_BYTES) return@withContext null
                    val json = body.string()
                    if (json.toByteArray(Charsets.UTF_8).size > MAX_MANIFEST_BYTES) {
                        return@withContext null
                    }
                    parseManifest(json, language)
                }
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                null
            }
        }
    }

    private fun parseManifest(raw: String, language: AppLanguage): AppUpdateInfo? {
        val root = JSONObject(raw)
        val versionCode = root.optLong("versionCode", -1L)
            .takeIf { it in 1..Int.MAX_VALUE }
            ?.toInt()
            ?: return null
        if (versionCode <= BuildConfig.VERSION_CODE) return null

        val versionName = root.optString("versionName")
            .trim()
            .takeIf { it.isNotBlank() && it.length <= 32 }
            ?: return null
        val releaseUrl = validateUpdateUrl(
            root.optString("releaseUrl"),
            RELEASE_HOSTS,
        ) ?: return null

        val download = selectDownload(root.optJSONObject("downloads"), language)
        return AppUpdateInfo(
            versionCode = versionCode,
            versionName = versionName,
            releaseUrl = releaseUrl,
            downloadUrl = download?.first,
            sha256 = download?.second,
        )
    }

    private fun selectDownload(
        downloads: JSONObject?,
        language: AppLanguage,
    ): Pair<String, String>? {
        if (downloads == null) return null
        val languageCode = if (language == AppLanguage.SPANISH) "es" else "en"
        val abi = Build.SUPPORTED_ABIS
            .firstOrNull { it == "arm64-v8a" || it == "x86_64" }
            ?: "arm64-v8a"
        val keys = listOf(
            "$languageCode-$abi",
            "$languageCode-arm64-v8a",
            "$languageCode-x86_64",
        ).distinct()
        val selected = keys.asSequence()
            .mapNotNull { downloads.optJSONObject(it) }
            .mapNotNull { entry ->
                val url = validateUpdateUrl(entry.optString("url"), DOWNLOAD_HOSTS)
                    ?.takeIf { it.substringBefore('?').lowercase(Locale.ROOT).endsWith(".apk") }
                val sha256 = entry.optString("sha256")
                    .trim()
                    .lowercase(Locale.ROOT)
                    .takeIf { SHA256_PATTERN.matches(it) }
                if (url == null || sha256 == null) null else url to sha256
            }
            .firstOrNull()
        return selected
    }

    private fun validateUpdateUrl(raw: String, allowedHosts: Set<String>): String? {
        val validation = RemoteUrlValidator.validate(raw)
        val uri = (validation as? UrlValidation.Valid)?.uri ?: return null
        val host = uri.host.lowercase(Locale.ROOT)
        if (host !in allowedHosts) return null
        return uri.toString()
    }

    private companion object {
        const val LATEST_MANIFEST_URL =
            "https://raw.githubusercontent.com/polen-tita/Polentita-Music/main/updates/latest.json"
        const val MAX_MANIFEST_BYTES = 64 * 1024L
        val SHA256_PATTERN = Regex("[0-9a-f]{64}")
        val RELEASE_HOSTS = setOf("github.com", "www.github.com")
        val DOWNLOAD_HOSTS = setOf(
            "raw.githubusercontent.com",
            "github.com",
            "www.github.com",
            "objects.githubusercontent.com",
        )
    }
}

@Singleton
class AppUpdateInstaller @Inject constructor(
    @ApplicationContext private val context: Context,
    private val client: OkHttpClient,
) {
    fun canRequestInstallation(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.O || context.packageManager.canRequestPackageInstalls()

    suspend fun download(info: AppUpdateInfo): Uri = withContext(Dispatchers.IO) {
        val url = info.downloadUrl ?: throw IllegalArgumentException("No hay una descarga disponible")
        val destinationDirectory = File(context.cacheDir, UPDATES_DIRECTORY).apply { mkdirs() }
        val destination = File(destinationDirectory, "polentita-update-${info.versionCode}.apk")
        val temporary = File(destinationDirectory, ".${destination.name}.download")
        try {
            executeFollowingRedirects(url).use { response ->
                if (!response.isSuccessful) {
                    throw IOException("La descarga respondió con ${response.code}")
                }
                val contentLength = response.body?.contentLength() ?: -1L
                if (contentLength > MAX_APK_BYTES) {
                    throw IOException("La actualización supera el tamaño permitido")
                }
                val digest = MessageDigest.getInstance("SHA-256")
                var totalBytes = 0L
                FileOutputStream(temporary, false).use { output ->
                    val body = response.body ?: throw IOException("La descarga está vacía")
                    body.byteStream().use { input ->
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                        while (true) {
                            val count = input.read(buffer)
                            if (count < 0) break
                            totalBytes += count
                            if (totalBytes > MAX_APK_BYTES) {
                                throw IOException("La actualización supera el tamaño permitido")
                            }
                            digest.update(buffer, 0, count)
                            output.write(buffer, 0, count)
                        }
                    }
                    output.fd.sync()
                }
                if (totalBytes == 0L) throw IOException("La descarga está vacía")
                val expected = info.sha256 ?: throw IOException("La descarga no tiene una firma válida")
                val actual = digest.digest().toHexString()
                if (!actual.equals(expected, ignoreCase = true)) {
                    throw IOException("La actualización no superó la verificación de integridad")
                }
            }
            check(temporary.renameTo(destination)) { "No se pudo guardar la actualización" }
            destinationDirectory.listFiles()
                ?.filter { it != destination && it.isFile }
                ?.forEach(File::delete)
            FileProvider.getUriForFile(context, "${context.packageName}.files", destination)
        } catch (error: Throwable) {
            temporary.delete()
            destination.delete()
            throw error
        }
    }

    private fun executeFollowingRedirects(initialUrl: String): Response {
        val initialValidation = RemoteUrlValidator.validate(initialUrl)
        val initialHost = (initialValidation as? UrlValidation.Valid)?.uri?.host
            ?.lowercase(Locale.ROOT)
        if (initialHost !in DOWNLOAD_HOSTS) {
            throw IOException("La descarga de la actualización no es segura")
        }
        var request = Request.Builder().url(initialUrl).get().build()
        repeat(MAX_REDIRECTS + 1) { redirectCount ->
            val response = client.newCall(request).execute()
            if (response.code !in REDIRECT_CODES) return response
            if (redirectCount == MAX_REDIRECTS) {
                response.close()
                throw IOException("La actualización excede el límite de redirecciones")
            }
            val location = response.header("Location")
            val next = location?.let(request.url::resolve)
            response.close()
            val nextUrl = next?.toString().orEmpty()
            val validation = RemoteUrlValidator.validate(nextUrl)
            val host = (validation as? UrlValidation.Valid)?.uri?.host
                ?.lowercase(Locale.ROOT)
            if (host !in DOWNLOAD_HOSTS) {
                throw IOException("La redirección de la actualización no es segura")
            }
            request = Request.Builder().url(nextUrl).get().build()
        }
        error("Redirección inesperada")
    }

    private fun ByteArray.toHexString(): String =
        joinToString("") { "%02x".format(it.toInt() and 0xff) }

    private companion object {
        const val UPDATES_DIRECTORY = "updates"
        const val MAX_APK_BYTES = 150L * 1024L * 1024L
        const val MAX_REDIRECTS = 3
        val REDIRECT_CODES = setOf(301, 302, 303, 307, 308)
        val DOWNLOAD_HOSTS = setOf(
            "raw.githubusercontent.com",
            "github.com",
            "www.github.com",
            "objects.githubusercontent.com",
        )
    }
}
