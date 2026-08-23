package com.polentita.music.core.launcher

import android.content.Context
import android.content.Intent
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.drawable.Icon
import android.net.Uri
import com.polentita.music.MainActivity
import com.polentita.music.R
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

enum class LauncherIconChoice { OFFICIAL, CUSTOM }

data class LauncherIconState(
    val customIconPath: String? = null,
    val hasPinnedShortcut: Boolean = false,
    val pinningSupported: Boolean = true,
)

enum class LauncherIconApplyResult {
    PIN_REQUESTED,
    UPDATED,
    RESTORED,
    ALREADY_OFFICIAL,
    UNSUPPORTED,
}

@Singleton
class LauncherIconManager @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val shortcutManager: ShortcutManager?
        get() = context.getSystemService(ShortcutManager::class.java)

    fun currentState(): LauncherIconState {
        val pinned = hasPinnedShortcut()
        val supported = runCatching {
            shortcutManager?.isRequestPinShortcutSupported == true
        }.getOrDefault(false)
        return LauncherIconState(
            customIconPath = customIconFile.takeIf(File::isFile)?.absolutePath,
            hasPinnedShortcut = pinned,
            pinningSupported = supported || pinned,
        )
    }

    suspend fun importCustomIcon(uri: Uri): LauncherIconState = withContext(Dispatchers.IO) {
        val bitmap = decodeImportedBitmap(uri)
        val prepared = prepareSquareLauncherIcon(bitmap)
        val directory = customIconFile.parentFile
        require(directory != null && (directory.isDirectory || directory.mkdirs()))
        val pendingFile = File(directory, "$CUSTOM_ICON_FILE.pending")
        FileOutputStream(pendingFile).use { output ->
            require(prepared.compress(Bitmap.CompressFormat.PNG, 100, output))
        }
        if (!pendingFile.renameTo(customIconFile)) {
            pendingFile.copyTo(customIconFile, overwrite = true)
            pendingFile.delete()
        }
        if (prepared !== bitmap) prepared.recycle()
        bitmap.recycle()
        currentState()
    }

    suspend fun apply(choice: LauncherIconChoice): LauncherIconApplyResult =
        withContext(Dispatchers.IO) {
            when (choice) {
                LauncherIconChoice.OFFICIAL -> restoreOfficialIcon()
                LauncherIconChoice.CUSTOM -> applyCustomIcon()
            }
        }

    private fun applyCustomIcon(): LauncherIconApplyResult {
        val manager = shortcutManager ?: return LauncherIconApplyResult.UNSUPPORTED
        val bitmap = BitmapFactory.decodeFile(customIconFile.absolutePath)
            ?: error("El archivo de icono personalizado ya no está disponible")
        return try {
            val shortcut = shortcutInfo(Icon.createWithAdaptiveBitmap(bitmap))
            if (hasPinnedShortcut(manager)) {
                if (manager.updateShortcuts(listOf(shortcut))) {
                    LauncherIconApplyResult.UPDATED
                } else {
                    LauncherIconApplyResult.UNSUPPORTED
                }
            } else if (
                manager.isRequestPinShortcutSupported &&
                manager.requestPinShortcut(shortcut, null)
            ) {
                LauncherIconApplyResult.PIN_REQUESTED
            } else {
                LauncherIconApplyResult.UNSUPPORTED
            }
        } finally {
            bitmap.recycle()
        }
    }

    private fun restoreOfficialIcon(): LauncherIconApplyResult {
        val manager = shortcutManager
        val result = if (manager != null && hasPinnedShortcut(manager)) {
            val official = shortcutInfo(Icon.createWithResource(context, R.mipmap.ic_launcher))
            if (manager.updateShortcuts(listOf(official))) {
                LauncherIconApplyResult.RESTORED
            } else {
                LauncherIconApplyResult.UNSUPPORTED
            }
        } else {
            LauncherIconApplyResult.ALREADY_OFFICIAL
        }
        if (result != LauncherIconApplyResult.UNSUPPORTED) {
            customIconFile.takeIf(File::exists)?.delete()
        }
        return result
    }

    private fun shortcutInfo(icon: Icon): ShortcutInfo = ShortcutInfo.Builder(context, SHORTCUT_ID)
        .setShortLabel(context.getString(R.string.app_name))
        .setLongLabel(context.getString(R.string.app_name))
        .setIcon(icon)
        .setIntent(
            Intent(context, MainActivity::class.java).apply {
                action = Intent.ACTION_MAIN
                addCategory(Intent.CATEGORY_LAUNCHER)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
        )
        .build()

    private fun hasPinnedShortcut(manager: ShortcutManager? = shortcutManager): Boolean =
        runCatching {
            manager?.pinnedShortcuts?.any { it.id == SHORTCUT_ID } == true
        }.getOrDefault(false)

    private fun decodeImportedBitmap(uri: Uri): Bitmap {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri).use { input ->
            requireNotNull(input)
            BitmapFactory.decodeStream(input, null, bounds)
        }
        require(bounds.outWidth in 1..MAX_SOURCE_DIMENSION)
        require(bounds.outHeight in 1..MAX_SOURCE_DIMENSION)
        val options = BitmapFactory.Options().apply {
            inSampleSize = calculateLauncherIconSampleSize(
                bounds.outWidth,
                bounds.outHeight,
                DECODE_TARGET_SIZE,
            )
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        return context.contentResolver.openInputStream(uri).use { input ->
            requireNotNull(input)
            requireNotNull(BitmapFactory.decodeStream(input, null, options))
        }
    }

    private val customIconFile: File
        get() = File(File(context.filesDir, CUSTOM_ICON_DIRECTORY), CUSTOM_ICON_FILE)

    private companion object {
        const val SHORTCUT_ID = "polentita-custom-launcher"
        const val CUSTOM_ICON_DIRECTORY = "launcher-icon"
        const val CUSTOM_ICON_FILE = "custom-icon.png"
        const val ICON_SIZE = 512
        const val DECODE_TARGET_SIZE = ICON_SIZE * 2
        const val MAX_SOURCE_DIMENSION = 16_384
    }
}

internal fun calculateLauncherIconSampleSize(
    width: Int,
    height: Int,
    targetSize: Int,
): Int {
    require(width > 0 && height > 0 && targetSize > 0)
    var sampleSize = 1
    while (width / (sampleSize * 2) >= targetSize && height / (sampleSize * 2) >= targetSize) {
        sampleSize *= 2
    }
    return sampleSize
}

internal fun launcherIconCropRect(width: Int, height: Int): Rect {
    require(width > 0 && height > 0)
    val edge = minOf(width, height)
    val left = (width - edge) / 2
    val top = (height - edge) / 2
    return Rect(left, top, left + edge, top + edge)
}

internal fun prepareSquareLauncherIcon(source: Bitmap, size: Int = 512): Bitmap {
    require(size > 0)
    val output = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    Canvas(output).drawBitmap(
        source,
        launcherIconCropRect(source.width, source.height),
        Rect(0, 0, size, size),
        Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG),
    )
    return output
}
