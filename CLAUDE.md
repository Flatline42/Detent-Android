# CLAUDE.md

This file provides guidance to Claude Code when working with code in this repository.

## Debug Files

Debug files for analysis (logcat output, crash reports, stack traces, etc.) are placed in `docs/debug/` within this project. When the user references a debug file, look there first.

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

## Project Identity

**App name:** DETENT (formerly FRAME//LOG, renamed 2026-03-23)
**Package:** `com.southsouthwest.framelog` (unchanged)
**Entry point:** `MainActivity` — sets up Compose content with `DetentTheme`
**Theme:** `ui/theme/` — `Theme.kt`, `Color.kt`, `Type.kt`
**Deep link scheme:** `DETENT://journal/{rollId}`
**File export extension:** `.DETENT`

Key config:
- Min SDK 26, Target/Compile SDK 36
- Kotlin 2.2.10 with Compose compiler plugin
- Dependencies managed via version catalog at `gradle/libs.versions.toml`
- Java 11 compatibility
- No DI framework — repositories get AppDatabase passed directly

## What DETENT Is

A native Android app for film photographers to log shooting data in the field. Shot log only — not a development log, not a light meter, not a social app. Single core value: log a frame **one-handed, without unlocking the phone, in under three seconds**.

Offline-first. No server, no accounts, no cloud sync in v1.0.

## Architecture

Single-module Android app using **Jetpack Compose** with **Material 3**.

### Project Structure

```
app/src/main/java/com/southsouthwest/framelog/
├── data/
│   ├── AppPreferences.kt           — SharedPreferences wrapper
│   ├── db/
│   │   ├── AppDatabase.kt          — Room singleton, 3 multi-DAO @Transaction methods
│   │   ├── Converters.kt
│   │   ├── dao/                    — 11 DAOs
│   │   ├── entity/                 — 12 entities + Enums.kt
│   │   └── relation/               — 7 relation/export types
│   └── repository/                 — 4 repositories
└── ui/
    ├── frames/
    ├── gear/
    ├── navigation/                 — NavRoutes.kt, NavGraph.kt
    ├── quickscreen/
    ├── rolls/
    ├── settings/
    ├── theme/
    ├── util/                       — ExposureValues.kt, ExportFormatter.kt
    └── widget/                     — DETENTWidget, Receiver, Updater, Actions
```

### Key Patterns

**Navigation:** Type-safe `@Serializable` routes (Navigation Compose 2.8.0). Always pass IDs between screens, never objects. Each screen loads its own data.

**Navigation results:** Kit selection result is written to `previousBackStackEntry?.savedStateHandle`. Observe in the **composable** via `LaunchedEffect + navController.currentBackStackEntry?.savedStateHandle?.getStateFlow()` — not in the ViewModel. `NavBackStackEntry.savedStateHandle` ≠ `ViewModel.savedStateHandle`.

**Data flow:** Repositories expose `Flow<T>`. ViewModels collect via `collectAsStateWithLifecycle()`. ViewModels created with `viewModel()` inside `composable<T>` — Navigation Compose injects Application + SavedStateHandle automatically.

**Atomic transactions (3 operations):**
1. Log frame: `updateFrame` + insert/delete FrameFilter delta
2. Create roll: `insertRoll` + `insertRollLens(×n)` + `insertRollFilter(×n)` + `insertFrames(bulk)`
3. Save kit: `insert/updateKit` + delete all KitLens/KitFilter + reinsert current set

**Backup/restore:** `AppDatabase.closeInstance()` before file copy — WAL checkpoint on last-connection-close. Same pattern for restore + restart via `getLaunchIntentForPackage + finishAffinity`.

**Widget frame pointer:** Derived from Room (first unlogged frame after highest logged frame). Independent of QuickScreen's SharedPreferences pointer. Widget haptics removed — Glance ActionCallback context doesn't have reliable Vibrator access.

**CSV export:** Writes to `cacheDir`, shared via FileProvider URI + `EXTRA_STREAM + FLAG_GRANT_READ_URI_PERMISSION`. JSON and plain text use `EXTRA_TEXT`.

## Implementation Status

### Complete (as of 2026-03-23)
- Data layer: all 12 entities, 11 DAOs, 4 repositories, all transactions
- Infrastructure: AppPreferences, ExposureValues, ExportFormatter, SplashScreen, theme switching
- 13 ViewModels
- All UI: Gear Library, Roll List, Roll Setup, Kit Selector, Roll Journal, Frame Detail, Quick Screen, Settings
- Glance widget with 3 responsive size variants (STANDARD 4×2, COMPACT 4×3, LOOSE 5×3)
- Navigation with bottom bar, type-safe routes, deep links
- Themes: Alpine (High Noon) and Golden Hour palettes; custom OFL fonts

### Not Yet Started (v1.0 remaining)
- **Onboarding** (Welcome screen + 5-step coach mark flow) — waiting for explicit go-ahead
- Privacy policy URL (placeholder in SettingsScreen)
- OSS licenses screen wiring (plugin in place)

## Display Conventions

**Shutter speed:** Stored `"1/125"`, displayed `125`. Stored `"2s"`, displayed `2s`. `"B"` as `"B"`. Whole-second values and B rendered in `colorScheme.error`.

**Aperture:** Stored and displayed as `"f/8"`.

**Push/Pull:** 0 → "box speed"; ±N → "push/pull N · ISO N"; null → ISO value only.

**Filter EV sum:** `~` prefix if any active filter has null `evReduction`. Hidden when no active filters.

**Roll name default:** Day of month + abbreviated month + year, e.g., `"23 Mar '26"`.

## Haptics

`createOneShot` silently rejected on Pixel 7. Use `createWaveform` with minimum 4 segments for all effects:

```kotlin
// Stepper decrement
createWaveform(longArrayOf(0, 30, 50, 30), intArrayOf(0, 80, 0, 80), -1)
// Stepper increment (same pattern)
createWaveform(longArrayOf(0, 30, 50, 30), intArrayOf(0, 80, 0, 80), -1)
// Log Frame confirm
createWaveform(longArrayOf(0, 120, 80, 120), intArrayOf(0, 80, 0, 80), -1)
```

Use direct `Vibrator`/`VibrationEffect`, not `LocalHapticFeedback`.

## Icons

`material-icons-core` only (no extended). `LocationOn`, `Mic`, `ExpandMore`/`ExpandLess` are not in core — use text buttons and `KeyboardArrowDown`/`KeyboardArrowUp` substitutions.

## v1.0 Scope — Hard Limits

Do not implement these regardless of how easy they seem:
- Medium format (enum value reserved, not surfaced in UI)
- Zoom lenses
- Stepless aperture / cine lenses
- Google Sheets sync
- ExifTool export
- Per-frame ISO override
- In-app map rendering (use `geo:` intent)
- Background GPS tracking
- iOS support

## Reference Documents

> **Note on wireframes:** All wireframes in `docs/Wireframes_DETENT/` still reference the old name FRAME//LOG. This is cosmetic only — treat them as DETENT wireframes. Do not rename them.

All detail specs are in `docs/`:

| Document | Contents |
|---|---|
| `docs/web_agent_handoff.md` | Full project summary for any AI assistant |
| `docs/DETENT claude code handoff.md` | Full architecture reference |
| `docs/DETENT PRD v0.4.md` | Full requirements and scope |
| `docs/DESIGN decisions.md` | Functional design decisions |
| `docs/Screen_Inventory/DETENT screen inventory.md` | All screens, all elements, all behaviors |
| `docs/Visual Pass Notes.md` | Typography, color palettes, ergonomics |
| `docs/Data_Model/` | Individual entity specifications |
| `docs/DAO_Walkthrough/` | Complete DAO method specifications |
| `docs/legacy_memory/memory/project_detent_status.md` | Session-by-session implementation notes |

## Code Quality

Intended for Play Store release. Developer reviews all generated code before shipping and is learning Android development.

- Prefer clarity over cleverness
- Comment non-obvious decisions
- Follow MVVM, repository pattern, unidirectional data flow
- Primary concern: security and correctness around data handling and file I/O
- Be explicit about what each piece of code does and why
