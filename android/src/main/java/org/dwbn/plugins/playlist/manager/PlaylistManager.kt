package org.dwbn.plugins.playlist.manager

import android.app.Application
import android.util.Log
import androidx.annotation.FloatRange
import androidx.annotation.IntRange
import com.devbrackets.android.exomedia.listener.OnErrorListener
import com.devbrackets.android.playlistcore.api.MediaPlayerApi
import com.devbrackets.android.playlistcore.manager.ListPlaylistManager
import org.dwbn.plugins.playlist.PlaylistItemOptions
import org.dwbn.plugins.playlist.TrackRemovalItem
import org.dwbn.plugins.playlist.data.AudioTrack
import org.dwbn.plugins.playlist.playlist.AudioApi
import org.dwbn.plugins.playlist.service.MediaService
import java.lang.ref.WeakReference
import java.util.*

/**
 * A PlaylistManager that extends the [ListPlaylistManager] for use with the
 * [MediaService] which extends [com.devbrackets.android.playlistcore.service.BasePlaylistService].
 */
class PlaylistManager(application: Application) :
    ListPlaylistManager<AudioTrack>(application, MediaService::class.java), OnErrorListener {
    private val audioTracks: MutableList<AudioTrack> = ArrayList()
    private var volumeLeft = 1.0f
    private var volumeRight = 1.0f
    private var playbackSpeed = 1.0f
    var loop = false
    var isShouldStopPlaylist = false
    var currentErrorTrack: AudioTrack? = null

    /** When true, MediaService stays in foreground during native video (Android 17 AudioHardening). */
    var videoHandoffForegroundRetain = false
    var mediaServiceInForeground = false

    // Really need a way to propagate the settings through the app
    var resetStreamOnPause = true
    var options: Options
    private var mediaControlsListener = WeakReference<MediaControlsListener?>(null)
    private var errorListener = WeakReference<OnErrorListener?>(null)
    private var currentMediaPlayer: WeakReference<MediaPlayerApi<AudioTrack>?>? =
        WeakReference(null)

    fun setOnErrorListener(listener: OnErrorListener?) {
        errorListener = WeakReference(listener)
    }

    fun setMediaControlsListener(listener: MediaControlsListener?) {
        mediaControlsListener = WeakReference(listener)
    }

    val isPlaying: Boolean
        get() = playlistHandler != null && playlistHandler!!.currentMediaPlayer != null && playlistHandler!!.currentMediaPlayer!!.isPlaying

    override fun onError(e: Exception?): Boolean {

        if (e != null && errorListener.get() != null) {
            Log.i(TAG, "onError: $e")
            errorListener.get()!!.onError(e)
        }
        return true
    }

    /*
     * isNextAvailable, getCurrentItem, and next() are overridden because there is
     * a glaring bug in playlist core where when an item completes, isNextAvailable and
     * getCurrentItem return wildly contradictory things, resulting in endless repeat
     * of the last item in the playlist.
     */
    override val isNextAvailable: Boolean
        get() {
            if (itemCount <= 1) {
                return false;
            }
            val isAtEnd = currentPosition + 1 >= itemCount
            val isConstrained = currentPosition + 1 in 0 until itemCount
            return if (isAtEnd) {
                loop
            } else isConstrained
        }

    override operator fun next(): AudioTrack? {
        if (isNextAvailable) {
            val isAtEnd = currentPosition + 1 >= itemCount
            currentPosition = if (isAtEnd && loop) {
                0
            } else {
                (currentPosition + 1).coerceAtMost(itemCount)
            }
        } else {
            if (loop) {
                currentPosition = INVALID_POSITION
            } else {
                isShouldStopPlaylist = true
                return null
            }
        }

        return currentItem
    }


    /*
     * List management
     */
    fun setAllItems(items: List<AudioTrack>?, options: PlaylistItemOptions) {
        clearItems()
        addAllItems(items)
        currentPosition = 0
        // If the options said to start from a specific position, do so.
        var seekStart: Long = 0
        if (options.playFromPosition > 0) {
            seekStart = options.playFromPosition
        } else if (options.retainPosition) {
            val progress = currentProgress
            if (progress != null) {
              seekStart = progress.position
            }
        }

        // If the requested id exists, start there; otherwise keep the first track.
        options.playFromId?.let { trackId ->
            val position = findTrackPosition(trackId)
            if (position != INVALID_POSITION) {
                currentPosition = position
            }
        }

        // We assume that if the playlist is fully loaded in one go,
        // that the next thing to happen will be to play. So let's start
        // paused, which will allow the player to pre-buffer until the
        // user says Go.
        beginPlayback(seekStart, options.startPaused)
    }

    fun addItem(item: AudioTrack?) {
        if (item == null) {
            return
        }
        val countBefore = audioTracks.size;
        audioTracks.add(item)
        items = audioTracks
        if (countBefore == 0) {
            currentPosition = 0
            beginPlayback(1, true)
        }
        if (this.playlistHandler != null) {
            this.playlistHandler!!.updateMediaControls()
        }
    }

    fun addAllItems(its: List<AudioTrack>?) {
        val currentItem = currentItem // may be null
        audioTracks.addAll(its.orEmpty())
        items =
            audioTracks // not *strictly* needed since they share the reference, but for good measure..
        currentPosition = audioTracks.indexOf(currentItem)
    }

    fun removeItem(index: Int, itemId: String): AudioTrack? {
        val snapshot = audioTracks.toList()
        val resolvedIndex = resolveRemovalIndex(snapshot, index, itemId)
        if (resolvedIndex == INVALID_POSITION) {
            return null
        }

        return removeResolvedItems(snapshot, linkedSetOf(resolvedIndex)).first()
    }

    fun removeAllItems(its: ArrayList<TrackRemovalItem>): ArrayList<AudioTrack> {
        val snapshot = audioTracks.toList()
        val resolvedIndices = LinkedHashSet<Int>()

        for (item in its) {
            val index = resolveRemovalIndex(snapshot, item.trackIndex, item.trackId)
            if (index != INVALID_POSITION) {
                resolvedIndices.add(index)
            }
        }

        return removeResolvedItems(snapshot, resolvedIndices)
    }

    private fun resolveRemovalIndex(
        snapshot: List<AudioTrack>,
        index: Int,
        itemId: String
    ): Int {
        return when {
            index in snapshot.indices -> index
            itemId.isNotEmpty() -> snapshot.indexOfFirst { it.trackId == itemId }
            else -> INVALID_POSITION
        }
    }

    private fun removeResolvedItems(
        snapshot: List<AudioTrack>,
        indices: Set<Int>
    ): ArrayList<AudioTrack> {
        if (indices.isEmpty()) {
            return arrayListOf()
        }

        val removedTracks = ArrayList<AudioTrack>(indices.size)
        for (index in indices) {
            removedTracks.add(snapshot[index])
        }

        val currentItem = currentItem
        val currentIndex = snapshot.indexOf(currentItem)
        val removingCurrent = currentIndex in indices
        val nextItem =
            if (removingCurrent) {
                snapshot.indices
                    .firstOrNull { it > currentIndex && it !in indices }
                    ?.let(snapshot::get)
            } else {
                null
            }
        val wasPlaying = isPlaying

        for (index in indices.sortedDescending()) {
            audioTracks.removeAt(index)
        }
        items = audioTracks

        if (audioTracks.isEmpty()) {
            currentPosition = INVALID_POSITION
            playlistHandler?.stop()
            return removedTracks
        }

        if (!removingCurrent) {
            currentPosition = audioTracks.indexOf(currentItem)
            playlistHandler?.updateMediaControls()
            return removedTracks
        }

        currentPosition = nextItem?.let(audioTracks::indexOf) ?: INVALID_POSITION
        val handler = playlistHandler
        if (currentPosition == INVALID_POSITION) {
            handler?.stop()
        } else if (handler != null) {
            handler.startItemPlayback(0, !wasPlaying)
        }

        return removedTracks
    }

    fun clearItems() {
        playlistHandler?.stop()
        audioTracks.clear()
        items = audioTracks
        currentPosition = INVALID_POSITION
    }

    fun getAllItems(): List<AudioTrack> {
        return audioTracks.toList()
    }

    internal fun findTrackPosition(trackId: String): Int =
        audioTracks.indexOfFirst { it.trackId == trackId }

    fun getVolumeLeft(): Float {
        return volumeLeft
    }

    fun getVolumeRight(): Float {
        return volumeRight
    }

    fun setVolume(
        @FloatRange(from = 0.0, to = 1.0) left: Float,
        @FloatRange(from = 0.0, to = 1.0) right: Float
    ) {
        volumeLeft = left
        volumeRight = right
        if (currentMediaPlayer != null && currentMediaPlayer!!.get() != null) {
            Log.i("PlaylistManager", "setVolume completing with volume = $left")
            currentMediaPlayer!!.get()!!.setVolume(volumeLeft, volumeRight)
        }
    }

    fun getPlaybackSpeed(): Float {
        return playbackSpeed
    }

    fun setPlaybackSpeed(@FloatRange(from = 0.0625, to = 16.0) speed: Float) {
        val validSpeed = speed.coerceIn(0.0625f, 16.0f)
        playbackSpeed = validSpeed
        playlistHandler?.let { handler ->
            handler.currentMediaPlayer?.let { mediaPlayer ->
                if (mediaPlayer is AudioApi) {
                    Log.i(TAG, "setPlaybackSpeed completing with speed = $validSpeed")
                    mediaPlayer.setPlaybackSpeed(playbackSpeed)
                }
            }
        }
    }

    fun beginPlayback(@IntRange(from = 0) seekPosition: Long, startPaused: Boolean) {
        currentItem ?: return
        try {
            super.play(seekPosition, startPaused)
        } catch (e: IllegalStateException) {
            // Android 12+: BackgroundServiceStartNotAllowedException when app is backgrounded
            Log.w(TAG, "beginPlayback: cannot start MediaService while backgrounded: ${e.message}")
            return
        }
        try {
            setVolume(volumeLeft, volumeRight)
            setPlaybackSpeed(playbackSpeed)
        } catch (e: Exception) {
            Log.w(TAG, "beginPlayback: Error setting volume or playback speed: " + e.message)
        }
    }

    companion object {
        private const val TAG = "PlaylistManager"
    }

    init {
        setParameters(audioTracks, 0)
        options = Options(application.baseContext)
    }
}
