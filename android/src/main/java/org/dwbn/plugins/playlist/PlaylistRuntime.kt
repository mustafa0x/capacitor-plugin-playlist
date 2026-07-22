package org.dwbn.plugins.playlist

import android.app.Application
import android.content.Context
import org.dwbn.plugins.playlist.manager.PlaylistManager

object PlaylistRuntime {
    private var playlistManager: PlaylistManager? = null

    @JvmStatic
    fun getPlaylistManager(context: Context): PlaylistManager = synchronized(this) {
        playlistManager ?: PlaylistManager(context.applicationContext as Application).also {
            playlistManager = it
        }
    }
}
