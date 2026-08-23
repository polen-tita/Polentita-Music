package com.polentita.music.playback.session

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.polentita.music.MainActivity

object PlaybackSessionActivity {
    const val ACTION_OPEN_PLAYER = "com.polentita.music.action.OPEN_PLAYER"

    fun intent(context: Context): Intent =
        Intent(context, MainActivity::class.java)
            .setAction(ACTION_OPEN_PLAYER)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)

    fun pendingIntent(context: Context): PendingIntent =
        PendingIntent.getActivity(
            context,
            REQUEST_CODE,
            intent(context),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    private const val REQUEST_CODE = 901
}
