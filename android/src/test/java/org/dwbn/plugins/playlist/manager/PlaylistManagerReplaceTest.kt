package org.dwbn.plugins.playlist.manager

import org.dwbn.plugins.playlist.data.AudioTrack
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Regression coverage for [PlaylistManager.mergeReplacementTrackId], which decides whether a
 * replacement track keeps its own id or inherits the id of the track it is replacing.
 */
class PlaylistManagerReplaceTest {

    private fun track(trackId: String? = null, assetUrl: String = "https://example.com/a.mp3"): AudioTrack {
        val json = JSONObject()
        if (trackId != null) {
            json.put("trackId", trackId)
        }
        json.put("assetUrl", assetUrl)
        json.put("artist", "Artist")
        json.put("album", "Album")
        json.put("title", "Title")
        return AudioTrack(json)
    }

    @Test
    fun replacementWithoutTrackId_inheritsExistingTrackId() {
        val existing = track(trackId = "existing-id")
        val replacement = track(trackId = null, assetUrl = "https://example.com/local.mp3")

        val result = PlaylistManager.mergeReplacementTrackId(existing, replacement)

        assertEquals("existing-id", result.trackId)
        assertEquals("https://example.com/local.mp3", result.mediaUrl)
    }

    @Test
    fun replacementWithExplicitTrackId_isNotOverridden() {
        val existing = track(trackId = "existing-id")
        val replacement = track(trackId = "new-id")

        val result = PlaylistManager.mergeReplacementTrackId(existing, replacement)

        assertEquals("new-id", result.trackId)
    }

    @Test
    fun replacementWithoutTrackId_andNoExistingTrackId_staysNull() {
        val existing = track(trackId = null)
        val replacement = track(trackId = null)

        val result = PlaylistManager.mergeReplacementTrackId(existing, replacement)

        assertNull(result.trackId)
    }
}
