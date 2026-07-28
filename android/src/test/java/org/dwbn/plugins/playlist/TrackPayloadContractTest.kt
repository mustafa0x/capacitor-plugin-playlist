package org.dwbn.plugins.playlist

import org.dwbn.plugins.playlist.data.AudioTrack
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

class TrackPayloadContractTest {

    @Test
    fun missingTrackIdIsGeneratedOnce() {
        val track = AudioTrack(JSONObject().put("assetUrl", "https://example.com/generated.m4a"))

        UUID.fromString(track.trackId)
        assertEquals(track.trackId, track.toDict().getString("trackId"))
    }

    @Test
    fun separateTracksReceiveSeparateGeneratedIds() {
        val payload = JSONObject().put("assetUrl", "https://example.com/generated.m4a")

        assertNotEquals(AudioTrack(payload).trackId, AudioTrack(payload).trackId)
    }

    @Test
    fun playableAssetRequiresANonBlankUrl() {
        assertFalse(AudioTrack(JSONObject()).hasPlayableAsset)
        assertFalse(AudioTrack(JSONObject().put("assetUrl", "   ")).hasPlayableAsset)
        assertTrue(
            AudioTrack(JSONObject().put("assetUrl", "https://example.com/valid.m4a"))
                .hasPlayableAsset
        )
    }

    @Test
    fun explicitTrackFieldsRoundTripWithStrictStreamType() {
        val track = AudioTrack(
            JSONObject()
                .put("trackId", "track-a")
                .put("assetUrl", "https://example.com/a.m4a")
                .put("albumArt", "https://example.com/a.jpg")
                .put("artist", "Artist")
                .put("album", "Album")
                .put("title", "Track A")
                .put("isStream", true)
        )

        assertEquals("track-a", track.trackId)
        assertTrue(track.isStream)
        assertEquals("track-a", track.toDict().getString("trackId"))

        val numericStream = AudioTrack(
            JSONObject()
                .put("trackId", "track-b")
                .put("assetUrl", "https://example.com/b.m4a")
                .put("isStream", 1)
        )
        assertFalse(numericStream.isStream)
    }

    @Test
    fun playlistOptionsPreserveDocumentedDefaultsAndUnits() {
        val defaults = PlaylistItemOptions(JSONObject())
        assertFalse(defaults.startPaused)
        assertFalse(defaults.retainPosition)
        assertEquals(-1, defaults.playFromPosition)

        val configured = PlaylistItemOptions(
            JSONObject()
                .put("startPaused", true)
                .put("retainPosition", true)
                .put("playFromId", "track-a")
                .put("playFromPosition", 2.5)
        )
        assertTrue(configured.startPaused)
        assertTrue(configured.retainPosition)
        assertEquals("track-a", configured.playFromId)
        assertEquals(2_500, configured.playFromPosition)
    }
}
