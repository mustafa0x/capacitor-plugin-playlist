package org.dwbn.plugins.playlist.notification

import android.app.PendingIntent
import android.app.PendingIntent.FLAG_IMMUTABLE
import android.app.PendingIntent.FLAG_UPDATE_CURRENT
import android.content.Context
import android.content.Intent
import com.devbrackets.android.playlistcore.components.notification.DefaultPlaylistNotificationProvider

class PlaylistNotificationProvider(context: Context) : DefaultPlaylistNotificationProvider(context) {
    override val clickPendingIntent: PendingIntent?
        get() {
            val intent = context.packageManager
                .getLaunchIntentForPackage(context.packageName)
                ?: return null

            intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            return PendingIntent.getActivity(
                context,
                0,
                intent,
                FLAG_UPDATE_CURRENT or FLAG_IMMUTABLE
            )
        }
}
