# Changelog

## 0.9.0

### Breaking Changes

- Low-level `Playlist.removeItem()` and `Playlist.removeItems()` now accept only `id` / `index`.
- If you were calling the raw Capacitor plugin API with `trackId` / `trackIndex`, update those calls to `id` / `index`.
- The higher-level `RmxAudioPlayer` wrapper still accepts `trackId` / `trackIndex` and maps them to the canonical plugin contract.
