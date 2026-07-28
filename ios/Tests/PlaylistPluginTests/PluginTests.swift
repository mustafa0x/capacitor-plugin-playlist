import XCTest
import AVFoundation
@testable import PlaylistPlugin

@MainActor
class PluginTests: XCTestCase {

    private func track(_ id: String) -> AudioTrack {
        AudioTrack.initWithDictionary([
            "trackId": id,
            "assetUrl": "https://example.com/\(id).m4a",
            "artist": "Artist",
            "album": "Album",
            "title": "Track \(id)"
        ])!
    }

    private func writeSilentAudio(to url: URL, duration: TimeInterval) throws {
        let format = AVAudioFormat(standardFormatWithSampleRate: 44_100, channels: 1)!
        let file = try AVAudioFile(forWriting: url, settings: format.settings)
        let buffer = AVAudioPCMBuffer(
            pcmFormat: format,
            frameCapacity: AVAudioFrameCount(format.sampleRate * duration)
        )!
        buffer.frameLength = buffer.frameCapacity
        try file.write(from: buffer)
    }

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

    func testTrackPayloadRequiresAnAssetAndGeneratesMissingId() {
        XCTAssertNil(AudioTrack.initWithDictionary([
            "trackId": "invalid",
            "assetUrl": "   "
        ]))

        let generated = AudioTrack.initWithDictionary([
            "assetUrl": "https://example.com/generated.m4a",
            "artist": "Artist",
            "album": "Album",
            "title": "Generated"
        ])

        XCTAssertNotNil(generated?.trackId)
        XCTAssertFalse(generated?.trackId?.isEmpty ?? true)
    }

    func testPausedPlaylistSelectsRequestedTrackAndClearsCompletely() {
        let player = RmxAudioPlayer()
        player.setPlaylistItems(
            [track("a"), track("b")],
            options: ["startPaused": true, "playFromId": "b"]
        )

        XCTAssertEqual(player.avQueuePlayer.queuedAudioTracks.map(\.trackId), ["a", "b"])
        XCTAssertEqual(player.avQueuePlayer.currentAudioTrack?.trackId, "b")
        XCTAssertEqual(player.avQueuePlayer.rate, 0)

        player.clearAllItems()

        XCTAssertTrue(player.avQueuePlayer.queuedAudioTracks.isEmpty)
        XCTAssertNil(player.avQueuePlayer.currentAudioTrack)
        XCTAssertEqual(player.avQueuePlayer.rate, 0)
    }

    func testBatchRemovalUsesOneStablePlaylistSnapshot() {
        let player = RmxAudioPlayer()
        player.setPlaylistItems(
            [track("a"), track("b"), track("c")],
            options: ["startPaused": true]
        )

        let removed = player.removeItems([
            ["index": 0],
            ["id": "c"]
        ])

        XCTAssertEqual(removed, 2)
        XCTAssertEqual(player.avQueuePlayer.queuedAudioTracks.map(\.trackId), ["b"])
    }

    func testInvalidTrackSelectionsFailWithoutChangingSelection() throws {
        let player = RmxAudioPlayer()
        player.setPlaylistItems([track("a")], options: ["startPaused": true])

        XCTAssertThrowsError(try player.selectTrack(index: -1))
        XCTAssertThrowsError(try player.selectTrack(index: 2))
        XCTAssertThrowsError(try player.selectTrack(id: "missing"))
        XCTAssertEqual(player.avQueuePlayer.currentAudioTrack?.trackId, "a")
    }

    func testInitializeReleaseCanRepeatSafely() {
        let player = RmxAudioPlayer()

        player.initialize()
        player.initialize()
        player.releaseResources()
        player.initialize()
        player.releaseResources()

        XCTAssertTrue(player.avQueuePlayer.queuedAudioTracks.isEmpty)
    }

    func testVideoHandoffWithoutTrackHasDeterministicState() {
        let player = RmxAudioPlayer()
        player.prepareForVideoHandoff()

        XCTAssertEqual(player.getLastKnownPosition(), 0)
        XCTAssertEqual(player.avQueuePlayer.actionAtItemEnd, .none)

        var prewarmResult: Bool?
        player.resumeAfterVideoHandoff(position: 3, prewarm: true) {
            prewarmResult = $0
        }
        XCTAssertEqual(prewarmResult, false)
        XCTAssertEqual(player.getLastKnownPosition(), 3)

        var resumeResult: Bool?
        player.resumeAfterVideoHandoff(position: 0, play: false) {
            resumeResult = $0
        }
        XCTAssertEqual(resumeResult, true)
        XCTAssertEqual(player.avQueuePlayer.actionAtItemEnd, .advance)
    }

    func testPreferredRateSurvivesColdPlaybackStart() async throws {
        let url = FileManager.default.temporaryDirectory
            .appendingPathComponent("playlist-rate-\(UUID().uuidString).wav")
        defer { try? FileManager.default.removeItem(at: url) }

        try writeSilentAudio(to: url, duration: 10)

        let audioTrack = AudioTrack(url: url)
        audioTrack.trackId = "rate"
        audioTrack.assetUrl = url

        let player = RmxAudioPlayer()
        player.setPlaybackRate(2)
        player.setPlaylistItems([audioTrack], options: ["startPaused": false])

        let readyDeadline = Date().addingTimeInterval(5)
        while player.avQueuePlayer.currentTime().seconds < 0.25 && Date() < readyDeadline {
            try await Task.sleep(nanoseconds: 50_000_000)
        }
        XCTAssertGreaterThanOrEqual(player.avQueuePlayer.currentTime().seconds, 0.25)

        let startPosition = player.avQueuePlayer.currentTime().seconds
        let started = Date()
        try await Task.sleep(nanoseconds: 1_000_000_000)
        let measuredRate = (player.avQueuePlayer.currentTime().seconds - startPosition)
            / Date().timeIntervalSince(started)

        XCTAssertGreaterThan(measuredRate, 1.5)
        XCTAssertLessThan(measuredRate, 2.5)
        player.clearAllItems()
    }
}
