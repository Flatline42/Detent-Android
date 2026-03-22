# GEMINI.md

# FRAME//LOG Instructions

## Operation Modes
- **Plan Mode:** Use `/plan` before any implementation. I need to see the logic before files are written.
- **Haptic Check:** Whenever modifying UI, verify against `reference_pixel7_haptics.md`.

## Project Context
- **Room Database:** 12 entities, 11 DAOs. 
- **UI Rule:** Bottom-60% interaction zone for one-handed use.

## Picking up from Claude Code

Context for past decisions can be found in @docs/legacy_history/ this is the claude code history in jsonl and md format

This file provided guidance to Claude Code (claude.ai/code) when working with code in this repository. It has been repurposed for Gemini.

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

# FRAMELOG claude code handoff
## FRAME//LOG — Claude Code Handoff Document

**Version:** 1.0 **Date:** 2026-03-19 **Prepared by:** Claude (Sonnet 4.6) in conversation with Sean

---

## What This Document Is

This is a complete project handoff for Claude Code. It summarizes all design, architecture, and implementation decisions made during an extended design session covering requirements, data model, API/data layer contracts, screen inventory, and wireframes. Read this document in full before writing any code. The companion Trilium project export contains the full detail documents referenced here.

---

## What FRAME//LOG Is

A native Android application for film photographers to log shooting data in the field. It is a **shot log**, not a development log, not a light meter, not a social app. Its single core value proposition is that logging a frame should be achievable **one-handed, without unlocking the phone, in under three seconds**.

The app is designed primarily for the developer's personal use, with potential future public release. It is offline-first, no server, no accounts, no cloud sync in v1.0.

**Working title:** FRAME//LOG (may change before release)

---

## Technology Stack

| Concern | Choice | Notes |
| --- | --- | --- |
| Language | Kotlin | Standard Android |
| UI | Jetpack Compose | Modern Android UI toolkit |
| Database | SQLite via Room | Google's official ORM for Android |
| Navigation | Jetpack Navigation Component | Pass IDs between screens, not objects |
| Widget | Android App Widget API | 2×4 home screen widget |
| GPS | Android Location API | Capture on frame log only, not continuous |
| Export | Android Share Intent | No direct cloud integration |
| Backup | SQLite file export as .framelog | Via share sheet |
| Preferences | SharedPreferences | App config only — not SQLite |
| Haptics | VibrationEffect API | Directional stepper feedback |
| Speech | Android SpeechRecognizer | Note field on Quick Screen |
| Monetization | Ko-fi link or Play in-app purchase | Tip jar only, TBD |

---

## Project Structure — Recommended

```
framelog-android/
├── app/
│   ├── src/main/
│   │   ├── java/com/framelog/
│   │   │   ├── data/
│   │   │   │   ├── db/           — Room database, entities, DAOs
│   │   │   │   ├── repository/   — Repository classes
│   │   │   │   └── model/        — Data classes
│   │   │   ├── ui/
│   │   │   │   ├── widget/       — Home screen widget
│   │   │   │   ├── quickscreen/  — Layer 2 quick screen
│   │   │   │   ├── rolls/        — Roll list, setup, journal
│   │   │   │   ├── frames/       — Frame detail/edit
│   │   │   │   ├── gear/         — Gear library screens
│   │   │   │   ├── settings/     — Settings screen
│   │   │   │   └── onboarding/   — Welcome + coach marks
│   │   │   └── MainActivity.kt
│   │   └── res/
│   └── build.gradle
└── README.md
```

---

## Data Model — Complete Entity List

See individual entity notes in the Trilium export for full field specifications. Summary:

| Entity | Key Fields | Notes |
| --- | --- | --- |
| CameraBody | id, name, make, model, mountType, format, shutterIncrements, notes | format: `35mm` or `half_frame` |
| Lens | id, name, make, focalLengthMm, mountType, maxAperture, minAperture, apertureIncrements, filterSizeMm, notes | filterSizeMm nullable |
| Filter | id, name, make, filterType, evReduction, filterSizeMm, notes | both nullable fields |
| FilmStock | id, name, make, iso, format, defaultFrameCount, colorType, discontinued, notes | iso = box speed only |
| Kit | id, name, cameraBodyId, lastUsedAt, notes | template only |
| KitLens | kitId, lensId, isPrimary | join table |
| KitFilter | kitId, filterId | join table |
| Roll | id, name, filmStockId, cameraBodyId, pushPull, ratedISO, filmExpiryDate, totalExposures, isLoaded, gpsEnabled, status, loadedAt, finishedAt, lastExportedAt, notes | see Roll notes |
| RollLens | rollId, lensId, isPrimary | join table |
| RollFilter | rollId, filterId | join table |
| Frame | id, rollId, frameNumber, isLogged, loggedAt, aperture, shutterSpeed, lensId, exposureCompensation, lat, lng, notes | frameNumber immutable |
| FrameFilter | frameId, filterId | join table |

### Critical Roll Fields

*   `pushPull` — integer -3 to +3, default 0. Drives ratedISO calculation.
*   `ratedISO` — derived from `FilmStock.iso × 2^pushPull`. Custom override sets pushPull to null.
*   `isLoaded` — boolean. Distinguishes rolls in a camera (true) from inventory rolls (false).
*   `status` — `active | finished | archived`. Independent of isLoaded.
*   `totalExposures` — immutable after creation. Standard: `defaultFrameCount + extraFrames`. Half-frame: `(defaultFrameCount + extraFrames) × 2`.

### Critical Frame Fields

*   `frameNumber` — immutable. Never changes after bulk creation at roll setup.
*   `isLogged` — boolean, independent of loggedAt. Allows retroactive correction without fabricating a timestamp.
*   `aperture` — stored as canonical string e.g. `"f/8"`. UI steppers enforce valid values.
*   `shutterSpeed` — stored as canonical string e.g. `"1/125"`. Display format differs from storage format (see below).

---

## DAO Layer — Complete Method List

See DAO notes in Trilium export for full specifications. Key patterns:

**All gear DAOs (CameraBodyDao, LensDao, FilterDao, FilmStockDao)** follow identical pattern:

*   `search[Entity](query: String)` — search-as-you-type
*   `get[Entity]ById(id: Int)` — for detail screens
*   `insert/update/delete[Entity]`
*   Entity-specific: `getDistinctMountTypes()`, `getLensesByMountType()`, `getDistinctFilterTypes()`

**RollDao** — key methods:

*   `getActiveRolls()` — all loaded rolls (isLoaded = true)
*   `getRollWithDetails(rollId)` — Roll + RollLens + RollFilter + Frames
*   `getRollForExport(rollId)` — fully denormalized for export
*   `insertRoll()`, `finishRoll()`, `archiveRoll()`, `unarchiveRoll()`, `updateIsLoaded()`, `updateLastExported()`

**FrameDao:**

*   `getFramesForRoll(rollId)` — all frames with lens and filters
*   `getFrameById(frameId)` — single frame with lens and filters
*   `insertFrames(frames: List<Frame>)` — bulk insert at roll creation
*   `updateFrame(frame)` — wrapped in transaction with FrameFilter delta

**KitDao:**

*   `getKitWithDetails(id)` — Kit + lenses + filters
*   Kit save is a wholesale replace transaction (delete all KitLens/KitFilter, reinsert current set)

### Transaction Rules

Three operations must be atomic transactions:

1.  **Log frame:** updateFrame + insert/delete FrameFilter records (delta only)
2.  **Create roll:** insertRoll + insertRollLens(×n) + insertRollFilter(×n) + insertFrames(bulk)
3.  **Save kit:** insert/updateKit + deleteAllKitLenses + insertKitLens(×n) + deleteAllKitFilters + insertKitFilter(×n)

### Navigation Pattern

**Always pass IDs between screens, never objects.** Each screen loads its own data via DAO. This respects Android's navigation size limits and keeps screens self-contained.

---

## Storage Architecture

| Data Type | Storage | Reason |
| --- | --- | --- |
| All photography data | Room/SQLite | Structured, relational |
| Onboarding complete flag | SharedPreferences | Simple boolean |
| App settings/preferences | SharedPreferences | Small config values |
| Active roll selection | SharedPreferences | Which loaded roll is "active" for widget |

The "active roll" for the widget is stored in SharedPreferences as a rollId. When the user switches rolls via the Quick Screen header, this value updates immediately and the widget re-renders.

---

## Screen Architecture

### App Launch Sequence

```
App opens
→ Read SharedPreferences: onboardingComplete?
  → No: Welcome Screen
  → Yes: Query Room: any loaded rolls?
    → No: Quick Screen (empty state)
    → Yes: Quick Screen (load active rollId from SharedPreferences)
```

### Key Screen Behaviors

**Quick Screen header** — tappable when multiple loaded rolls exist. Opens Switch Roll bottom sheet. Selecting a roll writes new rollId to SharedPreferences, widget updates.

**Widget** — reflects SharedPreferences activeRollId. On Log Frame tap: writes Frame record, updates FrameFilter delta, auto-advances frame pointer in SharedPreferences.

**Roll Setup** — two action buttons. "Create Roll" sets isLoaded=false. "Create & Load Roll" sets isLoaded=true and writes rollId to SharedPreferences active roll.

**Frame Detail/Edit** — navigated to with frameId + rollId. No prev/next navigation in v1.0. Back arrow triggers save prompt if unsaved changes.

---

## Display Conventions

### Shutter Speed Display

| Stored | Displayed |
| --- | --- |
| `"1/1000"` | `1000` |
| `"1/125"` | `125` |
| `"1/30"` | `30` |
| `"1s"` | `1s` |
| `"2s"` | `2s` |
| `"B"` | `B` |

Whole-second values and B displayed in accent color (red convention from film cameras). Color blind mode substitutes underline or suffix indicator.

### Aperture Display

Stored as `"f/8"`, displayed as `"f/8"`. Input in gear detail: `f/` fixed prefix + plain number entry.

### Filter EV Sum

Displayed on Quick Screen and Frame Detail filter row. Sum of non-null evReduction values for active filters. `~` prefix if any active filter has null evReduction. Hidden when no filters active.

### Push/Pull Display

| pushPull value | Display |
| --- | --- |
| 0 | "box speed" |
| 1 | "push 1 · ISO 800" |
| \-1 | "pull 1 · ISO 200" |
| null (custom) | ISO value only |

---

## Haptics

All stepper controls use directional haptic feedback via direct `Vibrator`/`VibrationEffect` (not `LocalHapticFeedback`, which can be suppressed by accessibility settings):

*   `+` (increment) — single firm pulse: `createWaveform(longArrayOf(0, 80), intArrayOf(0, 255), -1)`
*   `−` (decrement) — double short pulse: `createWaveform(longArrayOf(0, 30, 50, 30), intArrayOf(0, 80, 0, 80), -1)`
*   Log Frame confirmation — longer firm pulse: `createWaveform(longArrayOf(0, 200), intArrayOf(0, 255), -1)`

**Important:** Use `createWaveform` for all effects — `createOneShot` is silently suppressed on some devices (confirmed on Pixel 7). Widget ActionCallbacks must use `VibratorManager.defaultVibrator` on API 31+ (Glance context doesn't reliably route `getSystemService(Vibrator)` on those API levels); fall back to `getSystemService(Vibrator)` on API 26–30.

---

## Roll Loading Interaction Matrix

| Surface | Interaction | Result |
| --- | --- | --- |
| Roll List card (unloaded) | Tap | Open Roll Journal |
| Roll List card (unloaded) | Swipe left or right | Load Roll confirmation sheet |
| Roll List card (unloaded) | Long press | Context menu: Load Roll, Open Journal, Delete |
| Roll List card (loaded) | Long press | Context menu: Open Journal, Delete |
| Roll List card (finished) | Long press | Context menu: Open Journal, Archive, Delete |
| Roll List card (archived) | Long press | Context menu: Open Journal, Unarchive, Delete |
| Roll Journal (unloaded) | Load Roll button | Load Roll confirmation sheet |
| Roll Journal (any) | Overflow ⋯ | Archive / Unarchive / Delete |

All destructive and state-change actions require confirmation sheets. Delete is always a danger confirmation (permanent, cannot be undone).

---

## Onboarding

Coach mark pattern — overlay on real UI, user performs real interactions. Five steps:

1.  Gear Library → Lenses → tap +
2.  Gear Library → Bodies → tap +
3.  Gear Library → Film Stocks → tap +
4.  Roll List → tap FAB → Roll Setup → Create & Load Roll
5.  Settings → Widget Setup Instructions

SharedPreferences boolean `onboardingComplete` gates the flow. Re-runnable from Settings.

Tip jar prompt triggers once at 5 rolls logged. Never again after that. Button always available in Settings.

---

## v1.0 Scope Boundaries — Hard Limits

Do not implement these in v1.0 regardless of how easy they seem:

*   Medium format (reserve `medium_format` enum value but do not surface in UI)
*   Zoom lenses (focalLengthMm is single value only)
*   Stepless aperture / cine lenses
*   Google Sheets sync
*   ExifTool command file export
*   Per-frame ISO override
*   In-app map rendering (use geo intent to open user's maps app)
*   Background GPS tracking
*   iOS support

---

## Legal Requirements (before Play Store submission)

*   **Privacy policy** required — generates at app-privacy-policy-generator.nisrulz.com. Host at GitHub Pages. Link from Settings and Play Store listing.
*   **Open source licenses** — use Google Play Services OSS Licenses Gradle plugin. Auto-generates from dependencies.

---

## Reference Documents in Trilium Export

All detail is in these companion documents. Read them as needed during implementation:

| Document | Contents |
| --- | --- |
| FRAMELOG PRD v0.4 | Full requirements, scope, layer specs |
| 00\_data\_model\_overview | Entity list, relationships, key constraints |
| 01\_camerabody through 10\_kitlens\_kitfilter | Individual entity schemas with all fields |
| DAO\_00\_overview through DAO\_04\_kitdao | Complete DAO method specifications |
| FRAMELOG\_screen\_inventory | Every screen, every element, all behaviors |
| FRAMELOG\_navigation\_map.mermaid | Full navigation diagram |
| DESIGN\_decisions | All functional design decisions from wireframing |
| WIREFRAME\_\*.svg | Visual reference for all screens |

---

## A Note on Code Quality

This app is intended for potential public release on the Play Store. Code should be written as if it will be read and audited by the developer, who is learning Android development and will review all generated code before shipping. Prefer clarity over cleverness. Comment non-obvious decisions. Follow Android architecture guidelines (MVVM, repository pattern, unidirectional data flow).

The developer's primary concern about AI-generated code is security and correctness — particularly around data handling and file I/O. Be explicit about what each piece of code does and why.