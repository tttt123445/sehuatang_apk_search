# Proposal: Optimize Android Crawl UX

## Intent

把当前 Android APK 的优化和迁移工作沉淀成可追踪的 OpenSpec change。项目已经从旧的 `android_app/` Java 工程迁移到仓库根目录的新版 Gradle/Compose 工程，后续构建、验证和文档都以 `app/` 模块为准。

## Current Statistics

- Project layout: root Gradle project with a single `app/` Android module.
- UI/runtime: Kotlin, Jetpack Compose, Material 3, AndroidX, coroutine-based crawl flow.
- Native bridge: XTunnel JNI code lives under `app/src/main/jni/`.
- Build command: `./gradlew :app:assembleDebug`.
- Debug APK output: `app/build/outputs/apk/debug/app-debug.apk`.
- Legacy project: the old `android_app/` Java project and compatibility build script have been removed.
- OpenSpec CLI: not installed in this workspace shell, so this change is captured as repo-local Markdown artifacts.

## Scope

In scope:

- Make XTunnel startup safer and clearer: single-flight startup, local proxy health check, cooldown after failure, and direct/VPN fallback.
- Improve user feedback: network status card, busy state, disabled duplicate actions, and result summary.
- Improve image workflows: thumbnail state, preview navigation, retry, and original-thread action.
- Improve image cache write safety for streamed downloads.
- Keep the migrated Gradle/Compose project as the only Android project in the repository.
- Continue with code review, targeted fixes, rebuild, and device validation.

Out of scope:

- Reintroducing the removed Go backend or web dashboard.
- Restoring the removed `android_app/` project or compatibility build script.
- Changing forum parsing strategy beyond what is needed for this optimization.
- Publishing a release artifact.

## Success Criteria

- The APK builds from a clean command using `./gradlew :app:assembleDebug`.
- The debug APK is produced at `app/build/outputs/apk/debug/app-debug.apk`.
- Markdown docs no longer instruct users to build or install from `android_app/`.
- When XTunnel is enabled, app requests only use the proxy after native status is ready and the local port is reachable.
- XTunnel failure does not block the user indefinitely; crawl/check/image preview can fall back to system VPN/direct with visible status.
- User actions that start long operations are locked while busy, and export buttons reflect whether selected magnets exist.
- Image preview supports retry, original-thread opening, and previous/next navigation.
- Cached image writes do not leave partially written target files on ordinary interrupted downloads.
- Remaining verification gaps are explicitly tracked in `tasks.md`.
