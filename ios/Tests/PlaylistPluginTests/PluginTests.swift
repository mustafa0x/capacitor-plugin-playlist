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

    func testPlaybackRateCanBeSetBeforeLoadingWithoutStartingPlayback() {
        let player = RmxAudioPlayer()

        player.setPlaybackRate(2)

        XCTAssertEqual(player.avQueuePlayer.rate, 0)
        if #available(iOS 16.0, *) {
            XCTAssertEqual(player.avQueuePlayer.defaultRate, 2)
        }
    }

    func testZeroPlaybackRateKeepsPreferredRate() {
        let player = RmxAudioPlayer()

        player.setPlaybackRate(2)
        player.setPlaybackRate(0)

        XCTAssertEqual(player.avQueuePlayer.rate, 0)
        if #available(iOS 16.0, *) {
            XCTAssertEqual(player.avQueuePlayer.defaultRate, 2)
        }
    }
}
