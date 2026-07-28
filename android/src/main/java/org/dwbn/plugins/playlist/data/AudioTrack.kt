package org.dwbn.plugins.playlist.data

import com.devbrackets.android.playlistcore.annotation.SupportedMediaType
import com.devbrackets.android.playlistcore.api.PlaylistItem
import com.devbrackets.android.playlistcore.manager.BasePlaylistManager
import org.json.JSONObject
import java.util.UUID
import java.util.concurrent.atomic.AtomicLong

class AudioTrack(private val config: JSONObject) : PlaylistItem {
    companion object {
        private val nextPlaylistId = AtomicLong(1)
    }

    override val id: Long = nextPlaylistId.getAndIncrement()

    var bufferPercentFloat = 0f
        set(value) {
            // MediaProgress can report 100 instead of 1.
            field = Math.min(Math.max(field, value), 1f)
        }

    var bufferPercent = 0
        set(value) {
            field = Math.max(field, value)
        }

    var duration: Long = 0
        set(value) {
            field = Math.max(0, value)
        }

    fun toDict(): JSONObject = JSONObject().apply {
        put("trackId", trackId)
        put("isStream", isStream)
        put("assetUrl", mediaUrl)
        put("albumArt", thumbnailUrl)
        put("artist", artist)
        put("album", album)
        put("title", title)
    }

    val isStream: Boolean
        get() = config.opt("isStream") as? Boolean ?: false

    val trackId: String = stringValue("trackId")?.takeIf { it.isNotEmpty() }
        ?: UUID.randomUUID().toString()

    override val downloaded = false
    override val downloadedMediaUri: String? = null

    @get:SupportedMediaType
    override val mediaType = BasePlaylistManager.AUDIO

    override val mediaUrl: String
        get() = stringValue("assetUrl").orEmpty()

    internal val hasPlayableAsset: Boolean
        get() = mediaUrl.isNotBlank()

    override val thumbnailUrl: String?
        get() = stringValue("albumArt")?.takeIf { it.isNotEmpty() }

    override val artworkUrl: String?
        get() = thumbnailUrl

    override val title: String
        get() = stringValue("title").orEmpty()

    override val album: String
        get() = stringValue("album").orEmpty()

    override val artist: String
        get() = stringValue("artist").orEmpty()

    private fun stringValue(key: String): String? = config.opt(key) as? String
}
