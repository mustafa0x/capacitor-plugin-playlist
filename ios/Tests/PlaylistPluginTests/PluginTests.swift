import XCTest
@testable import PlaylistPlugin

class PluginTests: XCTestCase {

    func testPlaybackPositionSuppressedWhenWebViewInactive() {
        let player = RmxAudioPlayer()
        player.setWebViewActive(false)
        XCTAssertFalse(player.shouldEmitStatusToBridge(.rmxstatus_PLAYBACK_POSITION))
    }

    func testPlaybackPositionEmittedWhenWebViewActive() {
        let player = RmxAudioPlayer()
        player.setWebViewActive(true)
        XCTAssertTrue(player.shouldEmitStatusToBridge(.rmxstatus_PLAYBACK_POSITION))
    }

    func testPlayingEmittedWhenWebViewInactive() {
        let player = RmxAudioPlayer()
        player.setWebViewActive(false)
        XCTAssertTrue(player.shouldEmitStatusToBridge(.rmxstatus_PLAYING))
    }

    func testSelectingMissingTrackIdThrows() {
        let player = RmxAudioPlayer()
        let track = AudioTrack(url: URL(fileURLWithPath: "/track.mp3"))
        track.trackId = "existing"
        player.avQueuePlayer.queuedAudioTracks = [track]

        XCTAssertThrowsError(try player.selectTrack(id: "missing"))
    }

    func testRemovingMissingTrackIdThrowsWithoutMutation() {
        let player = RmxAudioPlayer()
        let track = AudioTrack(url: URL(fileURLWithPath: "/track.mp3"))
        track.trackId = "existing"
        player.avQueuePlayer.queuedAudioTracks = [track]

        XCTAssertThrowsError(try player.removeItem("missing"))
        XCTAssertEqual(player.avQueuePlayer.queuedAudioTracks.count, 1)
        XCTAssertTrue(player.avQueuePlayer.queuedAudioTracks[0] === track)
    }
}
