# Contract coverage

The durable suite checks observable behavior, not merely successful method calls.

| Contract | Shared / Web | iOS | Android |
| --- | --- | --- | --- |
| Track validation and generated IDs | `contract.test.ts` | `PluginTests` | `TrackPayloadContractTest` |
| Wrapper loading / playing / stopped state | `contract.test.ts` | Status events feed the shared wrapper | Status events feed the shared wrapper |
| Paused loading, selection, position, and clear | `web.test.ts` | `PluginTests` | Device coverage pending |
| Stable batch and terminal removal | `web.test.ts` | `PluginTests` | `PlaylistRemovalPolicyTest` |
| Rate before load and zero-rate retention | `web.test.ts` | Measured local-audio XCTest | Device coverage pending |
| Natural completion and loop | `web.test.ts` | Measured local-audio XCTest | Host-app coverage required |
| Release and repeated initialization | `web.test.ts` | `PluginTests` | Device coverage pending |
| Video handoff state | `web.test.ts` | `PluginTests` | Device coverage pending |
| Background bridge event policy | N/A | `PluginTests` | `StatusBridgePolicyTest` |
| Merged media service manifest | N/A | N/A | `PlaylistDeviceContractTest` |

`npm run verify:web`, `npm run verify:ios`, and `npm run verify:android` own these tests. The iOS verification command builds for a generic device and then runs XCTest on an available iPhone simulator.

`npm run verify:android:device` additionally runs Android instrumentation when an emulator or device is attached.

Real-device or emulator coverage is still required for audible Android playback, audio focus, background playback, remote controls, interruptions, live streams, and native natural completion.
