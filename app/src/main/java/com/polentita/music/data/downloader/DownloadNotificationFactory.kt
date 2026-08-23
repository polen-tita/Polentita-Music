package com.polentita.music.data.downloader

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.ForegroundInfo
import com.polentita.music.R
import java.util.concurrent.atomic.AtomicBoolean

internal object DownloadNotificationFactory {
    const val CHANNEL_ID = "polentita_downloads"

    private val channelCreated = AtomicBoolean(false)

    fun progress(
        context: Context,
        id: String,
        title: String,
        progress: Int,
        titleRes: Int,
    ): ForegroundInfo {
        val manager = context.getSystemService(NotificationManager::class.java)
        if (channelCreated.compareAndSet(false, true)) {
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    context.getString(R.string.download_channel),
                    NotificationManager.IMPORTANCE_LOW,
                ),
            )
        }
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(titleRes))
            .setContentText(title)
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .setProgress(100, progress.coerceIn(0, 100), progress == 0)
            .build()
        return ForegroundInfo(
            id.hashCode(),
            notification,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            } else {
                0
            },
        )
    }
}
