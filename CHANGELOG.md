# Changelog

## 0.9.10

### Fixed

- iOS now accepts `albumArt` as a local file path, a remote `http(s)` URL, or a relative URL resolved against the Capacitor host.
- Relative iOS artwork URLs are mapped through the Capacitor bridge so bundled web assets can be used for now playing metadata artwork.

## 0.9.9

### Added

- `RmxAudioPlayer.playbackSnapshot` to expose the wrapper's current playback clock state as a low-level primitive.
- `estimatePlaybackPosition(snapshot, now?)` to derive a live playback position from a `PlaybackSnapshot`.

### Changed

- `RmxAudioPlayer.currentPosition` is now derived from `playbackSnapshot`, so consumers can either keep using the convenience getter or estimate position themselves from the exposed snapshot data.

## 0.9.0

### Breaking Changes

- Low-level `Playlist.removeItem()` and `Playlist.removeItems()` now accept only `id` / `index`.
- If you were calling the raw Capacitor plugin API with `trackId` / `trackIndex`, update those calls to `id` / `index`.
- The higher-level `RmxAudioPlayer` wrapper still accepts `trackId` / `trackIndex` and maps them to the canonical plugin contract.
