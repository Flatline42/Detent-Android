# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build Commands

```bash
./gradlew assembleDebug          # Build debug APK
./gradlew assembleRelease        # Build release APK
./gradlew test                   # Run unit tests
./gradlew testDebugUnitTest      # Run a single test class: add --tests "com.southsouthwest.framelog.ExampleUnitTest"
./gradlew connectedAndroidTest   # Run instrumented tests (requires device/emulator)
./gradlew lint                   # Run Android Lint
./gradlew clean                  # Clean build artifacts
```

## Architecture

Single-module Android app using **Jetpack Compose** with **Material 3**.

- **Root package**: `com.southsouthwest.framelog`
- **Entry point**: `MainActivity` — sets up Compose content with `FrameLogTheme`
- **Theme**: `ui/theme/` — `Theme.kt` (light/dark + dynamic color on API 31+), `Color.kt`, `Type.kt`

Key config:
- Min SDK 26, Target/Compile SDK 36
- Kotlin 2.2.10 with Compose compiler plugin
- Dependencies managed via version catalog at `gradle/libs.versions.toml`
- Java 11 compatibility
