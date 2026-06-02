# Design: Optimize Android Crawl UX

## Technical Approach

This is now a modern Android Gradle project. Keep the implementation in the root `app/` module, use Kotlin and Jetpack Compose for UI, and build through the Gradle wrapper. The previous framework-only Java project under `android_app/` has been removed.

## Decisions

### Gradle is the only Android build entry point

The app builds through `./gradlew :app:assembleDebug`. The old compatibility script and `android_app/` output path are no longer maintained, so debug APKs come from `app/build/outputs/apk/debug/app-debug.apk`.

### UI is implemented in Compose

The migrated app keeps screen state in `MainViewModel` and renders the main experience from `MagnetCatcherApp`. This keeps crawl state, selection, image preview state, and export actions in the Kotlin/Compose project instead of the removed Java activity.

### XTunnel startup is single-flight

`XTunnelManager` owns startup coordination so repeated taps or automatic warmup do not start multiple native XTunnel instances. The native startup path waits for both native ready status and a reachable local HTTP proxy port before marking the proxy usable.

### Proxy use is readiness-gated

HTTP and image requests check XTunnel readiness before constructing a proxy. If the user selected XTunnel but it is not ready, the app surfaces a fallback message and continues via system VPN/direct where possible.

### Long-running operations own busy state

The view model owns operation locking and status reset. Crawl, check, and manual XTunnel startup share this state so buttons cannot queue duplicate heavy work.

### Status is visible before results

The header now includes a status card with title, detail, meta text, and spinner. Results have a separate summary card so network state and result counts do not overwrite each other.

### Image preview stays local to the dialog

`ImagePreviewDialog` owns retry/action/navigation controls and keeps caller-provided loading asynchronous. `MainActivity` only supplies image requests, navigation callbacks, and the original-thread action.

### Cache writes use a temporary file

Image cache writes now write to a `.tmp` file and promote it only after the write closes. Existing targets are moved to `.bak` during replacement and restored if promotion fails. This reduces the chance that a partially downloaded image is later decoded as a valid cache entry, while preserving the previous cache entry on ordinary rename failure.

## File Impact

- `app/src/main/kotlin/com/example/magnetcatcher/MainActivity.kt`: Android activity entry point.
- `app/src/main/kotlin/com/example/magnetcatcher/ui/MagnetCatcherApp.kt`: Compose UI, result list, preview controls, export actions.
- `app/src/main/kotlin/com/example/magnetcatcher/ui/MainViewModel.kt`: crawl orchestration, busy state, selection, network status.
- `app/src/main/kotlin/com/example/magnetcatcher/data/ImageCache.kt`: disk writes through temporary and backup files.
- `app/src/main/kotlin/com/example/magnetcatcher/xtunnel/XTunnelManager.kt`: embedded XTunnel lifecycle and readiness checks.
- `app/src/main/jni/xtbridge.c`: native bridge for the embedded XTunnel runtime.

## Verification Strategy

- Build with `./gradlew :app:assembleDebug`.
- Review risky paths manually: XTunnel state transitions, proxy fallback, image loading, cache writes, and UI enablement.
- If a device is available, install via `adb install -r app/build/outputs/apk/debug/app-debug.apk` and validate the scenarios in the spec delta.
