# Tasks: Optimize Android Crawl UX

## 0. Baseline Statistics

- [x] 0.1 Record working-tree scope and diff size.
- [x] 0.2 Record source file sizes after the current optimization.
- [x] 0.3 Confirm initial APK build status.
- [x] 0.4 Migrate Android source to the root Gradle/Compose `app/` module.

## 1. OpenSpec Artifacts

- [x] 1.1 Create change folder under `openspec/changes/optimize-android-crawl-ux/`.
- [x] 1.2 Write proposal with scope, statistics, and success criteria.
- [x] 1.3 Write design notes for current implementation choices.
- [x] 1.4 Write behavior-focused spec delta.
- [x] 1.5 Update Markdown references from the old `android_app/` project to the new Gradle project.

## 2. Static Review

- [x] 2.1 Review XTunnel startup and fallback state transitions.
- [x] 2.2 Review busy-state and button enablement behavior.
- [x] 2.3 Review thumbnail and preview image loading paths.
- [x] 2.4 Review cache write behavior and stale cache handling.
- [x] 2.5 Record fixes and residual risks.

## 3. Targeted Fixes

- [x] 3.1 Apply small fixes found during static review.
- [x] 3.2 Keep implementation scoped to current optimization.

## 4. Build Verification

- [x] 4.1 Run initial Android APK build.
- [x] 4.2 Rebuild after OpenSpec docs and fixes.
- [x] 4.3 Confirm final debug APK path: `app/build/outputs/apk/debug/app-debug.apk`.
- [x] 4.4 Remove the old `android_app/` project and compatibility build script.

## 5. Device Validation

- [x] 5.1 Check whether an ADB device is connected.
- [ ] 5.2 Install the APK if a device is available.
- [ ] 5.3 Validate system VPN/direct crawl path.
- [ ] 5.4 Validate XTunnel startup, fallback, and retry path.
- [ ] 5.5 Validate image preview navigation/retry/original-thread controls.
- [ ] 5.6 Validate copy/share export button state and output.

## Residual Risks

- No ADB device is currently attached, so install and real crawl/image/export validation remain pending.
- Build emits existing JDK/build-tools warnings, but compilation and signing verification pass.
