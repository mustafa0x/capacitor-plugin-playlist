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
        val seekStart = when {
            options.playFromPosition >= 0 -> options.playFromPosition
            options.retainPosition -> currentProgress?.position ?: 0
            else -> 0
        }

        clearItems()
        addAllItems(items)
        currentPosition = 0

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

    fun addItem(item: AudioTrack?, index: Int = -1) {
        if (item == null) {
            return
        }
        val countBefore = audioTracks.size
        val insertIndex = if (index >= 0) {
            index.coerceIn(0, audioTracks.size)
        } else {
            audioTracks.size
        }

        if (insertIndex >= audioTracks.size) {
            audioTracks.add(item)
        } else {
            audioTracks.add(insertIndex, item)
            if (currentPosition >= insertIndex && currentPosition != INVALID_POSITION) {
                currentPosition++
            }
        }
        items = audioTracks

        if (countBefore == 0) {
            currentPosition = 0
            beginPlayback(1, true)
        } else if (this.playlistHandler != null) {
            this.playlistHandler!!.updateMediaControls()
        }
    }

    fun moveItem(from: Int, to: Int): Boolean {
        if (from < 0 || from >= audioTracks.size || to < 0 || to >= audioTracks.size) {
            return false
        }
        if (from == to) {
            return true
        }

        val item = audioTracks.removeAt(from)
        audioTracks.add(to, item)
        items = audioTracks

        currentPosition = adjustCurrentIndexForMove(currentPosition, from, to, INVALID_POSITION)

        if (this.playlistHandler != null) {
            this.playlistHandler!!.updateMediaControls()
        }
        return true
    }

    fun replaceItem(index: Int, itemId: String, replacement: AudioTrack?): AudioTrack? {
        if (replacement == null) {
            return null
        }
        val resolvedIndex = resolveItemPosition(index, itemId)
        if (resolvedIndex < 0 || resolvedIndex >= audioTracks.size) {
            return null
        }

        val existing = audioTracks[resolvedIndex]
        val resolvedReplacement = mergeReplacementTrackId(existing, replacement)

        val isCurrent = existing == currentItem
        val wasPlaying = isPlaying
        val progress = currentProgress
        val seekPosition: Long = if (progress != null) progress.position else 0

        if (isCurrent && playlistHandler != null) {
            playlistHandler!!.pause(true)
        }

        audioTracks[resolvedIndex] = resolvedReplacement
        items = audioTracks

        if (isCurrent) {
            beginPlayback(seekPosition, !wasPlaying)
        } else if (this.playlistHandler != null) {
            this.playlistHandler!!.updateMediaControls()
        }

        return resolvedReplacement
    }


    fun addAllItems(its: List<AudioTrack>?) {
        val currentItem = currentItem // may be null
        audioTracks.addAll(its.orEmpty())
        items =
            audioTracks // not *strictly* needed since they share the reference, but for good measure..
        currentPosition = audioTracks.indexOf(currentItem)
    }

    fun removeItem(index: Int, itemId: String): AudioTrack? {
        val wasPlaying = isPlaying
        if (playlistHandler != null) {
            playlistHandler!!.pause(true)
        }
        var currentPosition = currentPosition
        var foundItem: AudioTrack? = null
        var removingCurrent = false

        // Get the current playback position in milliseconds before removing items
        val progress = currentProgress
        val seekPosition: Long = if (progress != null) progress.position else 0

        // If isPlaying is true, and currentItem is not null,
        // that implies that currentItem is the currently playing item.
        // If removingCurrent gets set to true, we are removing the currently playing item,
        // and we need to restart playback once we do.
        val resolvedIndex = resolveItemPosition(index, itemId)
        if (resolvedIndex >= 0) {
            foundItem = audioTracks[resolvedIndex]
            if (foundItem == currentItem) {
                removingCurrent = true
            }
            audioTracks.removeAt(resolvedIndex)
        }
        items = audioTracks
        currentPosition = if (removingCurrent) currentPosition else audioTracks.indexOf(currentItem)
        // If removing the current item, start from beginning (0), otherwise preserve playback position
        val seekStart = if (removingCurrent) 0 else seekPosition
        beginPlayback(seekStart, !wasPlaying)
        if (this.playlistHandler != null) {
            this.playlistHandler!!.updateMediaControls()
        }
        return foundItem
    }

    fun removeAllItems(its: ArrayList<TrackRemovalItem>): ArrayList<AudioTrack> {
        val removedTracks = ArrayList<AudioTrack>()
        val wasPlaying = isPlaying
        if (playlistHandler != null) {
            playlistHandler!!.pause(true)
        }
        var currentPosition = currentPosition
        val currentItem = currentItem // may be null
        var removingCurrent = false

        // Get the current playback position in milliseconds before removing items
        val progress = currentProgress
        val seekPosition: Long = if (progress != null) progress.position else 0

        for (item in its) {
            val resolvedIndex = resolveItemPosition(item.trackIndex, item.trackId)
            if (resolvedIndex >= 0) {
                val foundItem = audioTracks[resolvedIndex]
                if (foundItem == currentItem) {
                    removingCurrent = true
                }
                removedTracks.add(foundItem)
                audioTracks.removeAt(resolvedIndex)
            }
        }
        items = audioTracks
        currentPosition = if (removingCurrent) currentPosition else audioTracks.indexOf(currentItem)
        // If removing the current item, start from beginning (0), otherwise preserve playback position
        val seekStart = if (removingCurrent) 0 else seekPosition
        beginPlayback(seekStart, !wasPlaying)
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

    private fun resolveItemPosition(trackIndex: Int, trackId: String): Int {
        return when {
            trackIndex in audioTracks.indices -> trackIndex
            trackId.isNotEmpty() -> findTrackPosition(trackId)
            else -> INVALID_POSITION
        }
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

        /**
         * Computes the new position of the "current" item after moving an item from index
         * [from] to index [to] (post-removal insertion semantics, i.e. matching
         * `MutableList.removeAt(from)` followed by `MutableList.add(to, item)`).
         *
         * Pure function so it can be unit-tested without an Android/Application context.
         */
        @JvmStatic
        fun adjustCurrentIndexForMove(currentIndex: Int, from: Int, to: Int, invalidPosition: Int): Int {
            if (currentIndex == invalidPosition) {
                return currentIndex
            }
            if (from == currentIndex) {
                return to
            }
            val mid = if (from < currentIndex) currentIndex - 1 else currentIndex
            return if (mid >= to) mid + 1 else mid
        }

        /**
         * Builds the [AudioTrack] that should replace [existing], backfilling `trackId` from
         * [existing] when [replacement] doesn't specify one (an omitted id signals "keep the
         * existing id" rather than "generate a new one").
         *
         * Pure function so it can be unit-tested without an Android/Application context.
         */
        @JvmStatic
        fun mergeReplacementTrackId(existing: AudioTrack, replacement: AudioTrack): AudioTrack {
            val replacementConfig = replacement.toDict()
            if (replacement.trackId.isNullOrEmpty() && existing.trackId != null) {
                replacementConfig.put("trackId", existing.trackId)
            }
            return AudioTrack(replacementConfig)
        }
    }

    init {
        setParameters(audioTracks, 0)
        options = Options(application.baseContext)
    }

}
