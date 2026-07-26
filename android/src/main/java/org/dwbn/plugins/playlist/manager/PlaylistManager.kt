package org.dwbn.plugins.playlist.manager

import android.app.Application
import android.util.Log
import androidx.annotation.FloatRange
import androidx.annotation.IntRange
import com.devbrackets.android.exomedia.listener.OnErrorListener
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

    fun setOnErrorListener(listener: OnErrorListener?) {
        errorListener = WeakReference(listener)
    }

    fun setMediaControlsListener(listener: MediaControlsListener?) {
        mediaControlsListener = WeakReference(listener)
    }

    val isPlaying: Boolean
        get() = playlistHandler?.currentMediaPlayer?.isPlaying == true

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
        get() = currentPosition in 0 until itemCount &&
            (loop || currentPosition + 1 < itemCount)

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
        val replacementItems = items.orEmpty()
        val seekStart = when {
            options.playFromPosition >= 0 -> options.playFromPosition
            options.retainPosition -> currentProgress?.position ?: 0
            else -> 0
        }

        clearItems(emitCurrentItemCleared = replacementItems.isEmpty())
        audioTracks.addAll(replacementItems)
        this.items = audioTracks
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

    fun addItem(item: AudioTrack?) {
        item ?: return
        addAllItems(listOf(item))
    }

    fun addAllItems(its: List<AudioTrack>?) {
        val addedItems = its.orEmpty()
        if (addedItems.isEmpty()) {
            return
        }

        val wasEmpty = audioTracks.isEmpty()
        audioTracks.addAll(addedItems)
        items = audioTracks

        if (wasEmpty) {
            currentPosition = 0
            beginPlayback(1, true)
        }

        playlistHandler?.updateMediaControls()
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

        val selectedPosition = resolvePostRemovalPosition(
            currentPosition,
            indices,
            snapshot.size - indices.size
        )
        val removingCurrent = currentPosition in indices
        val clearsCurrentItem = shouldClearCurrentItemAfterRemoval(removingCurrent, selectedPosition)
        val wasPlaying = removingCurrent && isPlaying

        for (index in indices.sortedDescending()) {
            audioTracks.removeAt(index)
        }
        items = audioTracks

        if (audioTracks.isEmpty()) {
            currentPosition = INVALID_POSITION
            if (clearsCurrentItem) {
                mediaControlsListener.get()?.onCurrentItemChanged(null, INVALID_POSITION)
            }
            playlistHandler?.stop()
            return removedTracks
        }

        currentPosition = selectedPosition ?: INVALID_POSITION
        if (!removingCurrent) {
            playlistHandler?.updateMediaControls()
            return removedTracks
        }

        val handler = playlistHandler
        if (currentPosition == INVALID_POSITION) {
            if (clearsCurrentItem) {
                mediaControlsListener.get()?.onCurrentItemChanged(null, INVALID_POSITION)
            }
            handler?.stop()
        } else if (handler != null) {
            handler.startItemPlayback(0, !wasPlaying)
        } else {
            mediaControlsListener.get()?.onCurrentItemChanged(currentItem, currentPosition)
        }

        return removedTracks
    }

    fun clearItems(emitCurrentItemCleared: Boolean = true) {
        val clearsCurrentItem = shouldEmitCurrentItemCleared(
            hasCurrentItem = currentItem != null,
            leavesNoCurrentItem = emitCurrentItemCleared
        )
        currentPosition = INVALID_POSITION
        if (clearsCurrentItem) {
            mediaControlsListener.get()?.onCurrentItemChanged(null, INVALID_POSITION)
        }
        playlistHandler?.stop()
        audioTracks.clear()
        items = audioTracks
    }

    override fun reset() {
        val clearsCurrentItem = shouldEmitCurrentItemCleared(
            hasCurrentItem = currentItem != null,
            leavesNoCurrentItem = true
        )
        super.reset()
        if (clearsCurrentItem) {
            mediaControlsListener.get()?.onCurrentItemChanged(null, INVALID_POSITION)
        }
    }

    fun getAllItems(): List<AudioTrack> {
        return audioTracks.toList()
    }

    internal fun findTrackPosition(trackId: String): Int =
        audioTracks.indexOfFirst { it.trackId == trackId }

    fun selectPosition(position: Int) {
        val hadCurrentItem = currentItem != null
        currentPosition = position
        if (shouldEmitCurrentItemCleared(hadCurrentItem, currentItem == null)) {
            mediaControlsListener.get()?.onCurrentItemChanged(null, INVALID_POSITION)
        }
    }

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
        playlistHandler?.currentMediaPlayer?.let { mediaPlayer ->
            Log.i(TAG, "setVolume completing with volume = $left")
            mediaPlayer.setVolume(volumeLeft, volumeRight)
        }
    }

    fun getPlaybackSpeed(): Float {
        return playbackSpeed
    }

    fun setPlaybackSpeed(@FloatRange(from = 0.0625, to = 16.0) speed: Float) {
        val validSpeed = speed.coerceIn(0.0625f, 16.0f)
        playbackSpeed = validSpeed
        (playlistHandler?.currentMediaPlayer as? AudioApi)?.let { mediaPlayer ->
            Log.i(TAG, "setPlaybackSpeed completing with speed = $validSpeed")
            mediaPlayer.setPlaybackSpeed(validSpeed)
        }
    }

    fun beginPlayback(@IntRange(from = 0) seekPosition: Long, startPaused: Boolean) {
        currentItem ?: return
        try {
            super.play(seekPosition, startPaused)
        } catch (e: IllegalStateException) {
            // Android 12+: BackgroundServiceStartNotAllowedException when app is backgrounded
            Log.w(TAG, "beginPlayback: cannot start MediaService while backgrounded: ${e.message}")
            mediaControlsListener.get()?.onCurrentItemChanged(currentItem, currentPosition)
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

internal fun resolvePostRemovalPosition(
    currentPosition: Int,
    removedIndices: Set<Int>,
    remainingCount: Int
): Int? {
    val shiftedPosition = currentPosition - removedIndices.count { it < currentPosition }
    return shiftedPosition.takeIf { it in 0 until remainingCount }
}

internal fun shouldClearCurrentItemAfterRemoval(
    removingCurrent: Boolean,
    selectedPosition: Int?
): Boolean = removingCurrent && selectedPosition == null

internal fun shouldEmitCurrentItemCleared(
    hasCurrentItem: Boolean,
    leavesNoCurrentItem: Boolean
): Boolean = hasCurrentItem && leavesNoCurrentItem
