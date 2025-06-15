package org.dwbn.plugins.playlist.service

import android.app.Notification
import android.app.ForegroundServiceStartNotAllowedException
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.ServiceCompat          // ⬅ safe wrapper
import androidx.core.content.ContextCompat
import com.devbrackets.android.playlistcore.components.playlisthandler.PlaylistHandler
import com.devbrackets.android.playlistcore.service.BasePlaylistService
import org.dwbn.plugins.playlist.App
import org.dwbn.plugins.playlist.data.AudioTrack
import org.dwbn.plugins.playlist.manager.PlaylistManager
import org.dwbn.plugins.playlist.playlist.AudioApi
import org.dwbn.plugins.playlist.playlist.AudioPlaylistHandler
import org.dwbn.plugins.playlist.service.MediaImageProvider.OnImageUpdatedListener

/**
 * A simple service that extends [BasePlaylistService] in order to provide
 * the application specific information required.
 */
class MediaService : BasePlaylistService<AudioTrack, PlaylistManager>() {

    /* ---------- public helper ------------------------------------------------ */

    companion object {
        /**
         * Call this from **every** place you start or resume playback
         * (UI button, notification action, headset media-button, …).
         *
         * It guarantees that the service is in the correct “may-promote”
         * state before any later call to [runAsForeground].
         */
        fun ensureStarted(ctx: Context) {
            val i = Intent(ctx, MediaService::class.java)
            ContextCompat.startForegroundService(ctx, i)
        }
    }

    /* ------------------------------------------------------------------------ */

    override fun onCreate() {
        super.onCreate()
        // Adds the audio player implementation, otherwise there's nothing to play media with
        val newAudio = AudioApi(applicationContext)
        newAudio.addErrorListener(playlistManager)
        playlistManager.mediaPlayers.add(newAudio)
    }

    override fun onDestroy() {
        super.onDestroy()

        // Releases and clears all the MediaPlayersMediaImageProvider
        for (player in playlistManager.mediaPlayers) {
            player.release()
        }
        playlistManager.mediaPlayers.clear()
    }

    private val mainHandler = Handler(Looper.getMainLooper())

    override fun runAsForeground(
        notificationId: Int,
        notification: Notification
    ) {
        if (inForeground) return

        try {
            ServiceCompat.startForeground(
                this,
                notificationId,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
            )
            inForeground = true

        } catch (e: ForegroundServiceStartNotAllowedException) {
            // Happens when the service was *recreated* by the system
            // and nobody called ContextCompat.startForegroundService().
            inForeground = false            // allow a second attempt

            ContextCompat.startForegroundService(this, Intent(this, javaClass))
            mainHandler.postDelayed(
                { runAsForeground(notificationId, notification) },
                300
            )
        }
    }

    override val playlistManager: PlaylistManager
        get() = (applicationContext as App).playlistManager

    override fun newPlaylistHandler(): PlaylistHandler<AudioTrack> {
        val imageProvider = MediaImageProvider(applicationContext, object : OnImageUpdatedListener {
            override fun onImageUpdated() {
                playlistHandler.updateMediaControls()
            }
        }, playlistManager.options)

        return AudioPlaylistHandler.Builder(
            applicationContext,
            javaClass,
            playlistManager,
            imageProvider,
            null
        ).build()
    }
}