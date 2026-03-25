# DETENT — Web Agent Handoff

**Version:** 1.0 **Date:** 2026-03-24 **For:** Claude.ai web interface (no direct codebase access)

This document brings a web agent up to speed so it can help with design decisions, code review, and planning while a developer feeds code and questions through the chat interface.

---

## What DETENT Is

A native Android app for film photographers to log shooting data in the field. It is a **shot log**, not a development log, not a light meter, not a social app.

**Single core value proposition:** Log a frame one-handed, without unlocking the phone, in under three seconds.

Designed primarily for the developer's personal use, with potential Play Store release. Offline-first — no server, no accounts, no cloud sync in v1.0.

**Formerly named:** FRAME//LOG (renamed to DETENT 2026-03-23)

---

## Technology Stack

| Concern | Choice | Notes |
|---|---|---|
| Language | Kotlin | |
| UI | Jetpack Compose + Material 3 | Single-module app |
| Database | Room/SQLite | |
| Navigation | Navigation Compose 2.8.0 | Type-safe `@Serializable` routes |
| Widget | Glance AppWidget 1.1.0 | `SizeMode.Responsive`, 3 size variants |
| GPS | FusedLocationProviderClient | Capture on frame log only, not continuous |
| Export | Android Share Intent | FileProvider URI for binary files |
| Backup | SQLite file copy as `.DETENT` | Via share sheet |
| Preferences | SharedPreferences (AppPreferences wrapper) | App config + active roll state |
| Haptics | VibrationEffect / createWaveform | Pixel 7 confirmed; 4-segment minimum |
| Speech | Android SpeechRecognizer | Note field on Quick Screen and Frame Detail |

**Package:** `com.southsouthwest.framelog` (unchanged from original name)
**Min SDK:** 26 / **Target SDK:** 36 / **Kotlin:** 2.2.10 / **Java:** 11

---

## Project Structure (Actual)

```
app/src/main/java/com/southsouthwest/framelog/
├── data/
│   ├── AppPreferences.kt           — SharedPreferences wrapper
│   ├── db/
│   │   ├── AppDatabase.kt          — Room singleton, 3 multi-DAO @Transaction methods
│   │   ├── Converters.kt           — TypeConverters for all enums
│   │   ├── dao/                    — 11 DAOs
│   │   ├── entity/                 — 12 entities + Enums.kt
│   │   └── relation/               — 7 relation/export types
│   └── repository/                 — 4 repositories
└── ui/
    ├── frames/                     — FrameDetailScreen + ViewModel
    ├── gear/                       — 5 gear screens + 5 ViewModels
    ├── navigation/                 — NavRoutes.kt + NavGraph.kt
    ├── quickscreen/                — QuickScreenScreen + ViewModel
    ├── rolls/                      — RollList, RollJournal, RollSetup, KitSelector + VMs
    ├── settings/                   — SettingsScreen + ViewModel
    ├── theme/                      — Color.kt, Theme.kt, Type.kt
    ├── util/                       — ExposureValues.kt, ExportFormatter.kt
    └── widget/                     — DETENTWidget, Receiver, Updater, Actions (4 files)
```

---

## Data Model

### Entity Summary

| Entity | Key Fields |
|---|---|
| CameraBody | id, name, make, model, mountType, format, shutterIncrements, notes |
| Lens | id, name, make, focalLengthMm, mountType, maxAperture, minAperture, apertureIncrements, filterSizeMm, notes |
| Filter | id, name, make, filterType, evReduction, filterSizeMm, notes |
| FilmStock | id, name, make, iso, format, defaultFrameCount, colorType, discontinued, notes |
| Kit | id, name, cameraBodyId, lastUsedAt, notes |
| KitLens | kitId, lensId, isPrimary |
| KitFilter | kitId, filterId |
| Roll | id, name, filmStockId, cameraBodyId, pushPull, ratedISO, filmExpiryDate, totalExposures, isLoaded, gpsEnabled, status, loadedAt, finishedAt, lastExportedAt, notes |
| RollLens | rollId, lensId, isPrimary |
| RollFilter | rollId, filterId |
| Frame | id, rollId, frameNumber, isLogged, loggedAt, aperture, shutterSpeed, lensId, exposureCompensation, lat, lng, notes |
| FrameFilter | frameId, filterId |

### Critical Field Rules

- `frameNumber` — immutable after bulk creation at roll setup. Never changes.
- `isLogged` — independent of `loggedAt`. Allows retroactive correction without fabricating a timestamp.
- `pushPull` — integer -3 to +3. `ratedISO = FilmStock.iso × 2^pushPull`. null = custom override.
- `totalExposures` — immutable after creation. Half-frame cameras: `(defaultFrameCount + extraFrames) × 2`.
- `aperture` — stored as `"f/8"`, displayed as `"f/8"`.
- `shutterSpeed` — stored as `"1/125"`, displayed as `125`. Whole-second stored as `"2s"`, displayed as `2s`. `"B"` as `"B"`.

### Enums

`CameraBodyFormat` (35mm, half_frame), `FilmFormat` (35mm, half_frame), `ShutterIncrements`, `ApertureIncrements`, `ColorType` (color, bw, special), `RollStatus` (active, finished, archived)

---

## Architecture Decisions

### Navigation
- Type-safe `@Serializable` route data classes (Navigation Compose 2.8.0)
- **Always pass IDs between screens, never objects.** Each screen loads its own data.
- Bottom navigation bar with 5 top-level destinations (Gear Library, Roll List, Quick Screen, Journal, Settings)
- Bottom bar hidden on all detail screens
- `popUpTo + launchSingleTop + restoreState` for tab navigation
- Rolls tab uses `restoreState = false` — always lands on RollList, never restores sub-stack to Journal

### Navigation Results (Kit Selection)
`KitSelectorScreen` writes `kitId` to `previousBackStackEntry?.savedStateHandle["selected_kit_id"]` then pops. **Observation must happen in the composable** (via `LaunchedEffect + navController.currentBackStackEntry?.savedStateHandle?.getStateFlow()`), not in the ViewModel. `NavBackStackEntry.savedStateHandle` and `ViewModel.savedStateHandle` are different objects.

### Data Flow
- Repositories expose `Flow<T>` for reactive updates
- ViewModels collect via `collectAsStateWithLifecycle()`
- No DI framework — repositories get AppDatabase passed directly
- `viewModel()` inside `composable<T>` — Navigation Compose handles Application + SavedStateHandle injection

### Atomic Transactions (3 operations)
1. **Log frame:** `updateFrame` + insert/delete FrameFilter delta
2. **Create roll:** `insertRoll` + `insertRollLens(×n)` + `insertRollFilter(×n)` + `insertFrames(bulk)`
3. **Save kit:** `insert/updateKit` + `deleteAllKitLenses` + `insertKitLens(×n)` + `deleteAllKitFilters` + `insertKitFilter(×n)`

### Storage Split
| What | Where |
|---|---|
| All photography data | Room/SQLite |
| Onboarding complete flag | SharedPreferences |
| App settings | SharedPreferences |
| Active roll (widget) | SharedPreferences `activeRollId` |
| Per-roll frame pointer + MRU filters | SharedPreferences |

### Widget
- Glance AppWidget with `SizeMode.Responsive`, 3 size variants (STANDARD 4×2, COMPACT 4×3, LOOSE 5×3)
- Widget frame pointer derived entirely from Room — first unlogged frame after highest logged frame number. Independent of QuickScreen's SharedPreferences pointer.
- Widget haptics **fully removed** — Glance ActionCallback context doesn't have reliable Vibrator/Looper access
- 5 ActionCallbacks: ApertureUp/Down, ShutterUp/Down, LogFrame
- Widget state keys: `HAS_ROLL, ROLL_ID, FILM_CAMERA, FRAME_NUMBER, TOTAL_FRAMES, FILTER_COUNT, APERTURE, SHUTTER, APERTURE_LIST, SHUTTER_LIST, LENS_ID, FILTER_IDS, IS_ROLL_COMPLETE`
- Deep link: `DETENT://journal/{rollId}` for roll-complete → Roll Journal
- `maxResizeHeight="250dp"` (Pixel 7 rows are ~74dp; 3 rows = 192dp, needs headroom above 180dp)

### Backup/Restore
`AppDatabase.closeInstance()` before copying the SQLite file. WAL checkpoint happens automatically on last-connection-close. Same pattern for restore + app restart via `getLaunchIntentForPackage + finishAffinity`.

---

## Display Conventions

### Shutter Speed
| Stored | Displayed | Color |
|---|---|---|
| `"1/1000"` | `1000` | normal |
| `"1/125"` | `125` | normal |
| `"1s"` | `1s` | `colorScheme.error` |
| `"2s"` | `2s` | `colorScheme.error` |
| `"B"` | `B` | `colorScheme.error` |

### Filter EV Sum
Sum of non-null `evReduction` values. `~` prefix if any active filter has null `evReduction`. Hidden when no filters active.

### Push/Pull
| Value | Display |
|---|---|
| 0 | "box speed" |
| 1 | "push 1 · ISO 800" |
| -1 | "pull 1 · ISO 200" |
| null | ISO value only |

### Roll Name Default
Day of month + abbreviated month + year — e.g., `"23 Mar '26"`.

### Frame 1 Defaults
When no previous logged frame exists on a roll: aperture `f/5.6`, shutter `1/125`.

---

## Haptics

**IMPORTANT:** `createOneShot` silently rejected on Pixel 7. All haptic effects use `createWaveform` with minimum 4 segments.

```kotlin
// Decrement stepper
createWaveform(longArrayOf(0, 30, 50, 30), intArrayOf(0, 80, 0, 80), -1)

// Increment stepper (same pattern — directional differentiation deferred to v1.1)
createWaveform(longArrayOf(0, 30, 50, 30), intArrayOf(0, 80, 0, 80), -1)

// Log Frame confirm (heavier feel)
createWaveform(longArrayOf(0, 120, 80, 120), intArrayOf(0, 80, 0, 80), -1)
```

Use direct `Vibrator`/`VibrationEffect`, not `LocalHapticFeedback` (can be suppressed by accessibility settings).

---

## Themes

Dynamic color disabled by default. Two brand palettes:

- **Alpine (High Noon):** `#000000` background, `#FFD700` Safety Gold text
- **Golden Hour:** `#121212` background, `#FFBF00` Amber text, `#F7882F` Burnt Orange accent
- **Darkroom Safe** (`#000000` bg, pure red text): deferred to v1.1

Theme switching: `appThemeFlow` in `AppPreferences`, collected in `MainActivity` for real-time switching.

---

## Icons

Uses `material-icons-core` only (no extended icons library). Icons confirmed present in core:
`ArrowBack (AutoMirrored.Filled)`, `KeyboardArrowRight (AutoMirrored.Filled)`, `Search`, `Clear`, `Check`, `Add`, `MoreVert`, `KeyboardArrowDown`, `KeyboardArrowUp`, `Home`, `Build`, `List (AutoMirrored.Filled)`, `Create`, `Settings`

Icons **not** in core (substitutions used):
- `ExpandMore`/`ExpandLess` → `KeyboardArrowDown`/`KeyboardArrowUp`
- `LocationOn` → text link
- `Mic` → `OutlinedButton("Mic")`

---

## Screen Inventory

### App Launch Sequence
```
App opens → installSplashScreen()
→ Read SharedPreferences: onboardingComplete?
  → No: Welcome Screen (NOT YET BUILT)
  → Yes: Query Room: any loaded rolls?
    → No: Quick Screen (empty state)
    → Yes: Quick Screen (activeRollId from SharedPreferences)
```

### Screens by Area

**Quick Screen** — main home. Header (roll name, frame X/Y, filter count, total EV), lens cycler, filter chips (MRU, up to 4 shown), EV sum, EC stepper, aperture + shutter LargeSteppers, note field + mic, Log Frame button. Frontier indicator shows when frame pointer is behind current frame. SwitchRollBottomSheet when multiple rolls loaded. 60% rule: spacer pushes all controls into bottom 60% for thumb reach.

**Gear Library** — 5-tab `ScrollableTabRow` (Lenses, Bodies, Filters, Film, Kits). Search + sort. Each tab has a FAB → detail screen.

**Roll List** — 3 tabs (Active, Finished, Archived). Active tab splits loaded ("In camera") from unloaded ("Inventory"). Swipe-to-dismiss on unloaded cards. Long-press context menus.

**Roll Setup** — scrollable form: FilmStock, Kit (optional), CameraBody, Lenses, Filters, Details, push/pull stepper, GPS toggle, expiry date, custom ISO. Two buttons: "Create Roll" (unloaded) and "Create + Load" (loaded + sets active roll).

**Roll Journal** — frame list with search + sort. FrameCards show logged/unlogged/current states. Bottom bar adapts to roll status.

**Frame Detail** — per-frame editing. Logged toggle, lens picker, filter chips, EC stepper, aperture + shutter steppers, notes + mic, GPS tappable link, unsaved-changes back guard.

**Settings** — Shooting Defaults, Appearance, Data & Backup, Widget, Onboarding, Support, About.

**Widget** — Glance widget. Header (film camera icon, Frame X/Y, filter count), divider, stepper row (aperture + shutter), divider, Log Frame or "roll complete →" deep link.

**Onboarding** — NOT YET BUILT. Coach mark pattern (overlay on real UI). 5 steps:
1. Gear Library → Lenses → tap +
2. Gear Library → Bodies → tap +
3. Gear Library → Film Stocks → tap +
4. Roll List → FAB → Roll Setup → Create & Load Roll
5. Settings → Widget Setup Instructions

---

## Roll Loading Interaction Matrix

| Surface | Interaction | Result |
|---|---|---|
| Roll List card (unloaded) | Tap | Open Roll Journal |
| Roll List card (unloaded) | Swipe left or right | Load Roll confirmation |
| Roll List card (unloaded) | Long press | Menu: Load Roll, Open Journal, Delete |
| Roll List card (loaded) | Long press | Menu: Open Journal, Delete |
| Roll List card (finished) | Long press | Menu: Open Journal, Archive, Delete |
| Roll List card (archived) | Long press | Menu: Open Journal, Unarchive, Delete |
| Roll Journal (unloaded) | Load Roll button | Load Roll confirmation |
| Roll Journal (any) | Overflow ⋯ | Archive / Unarchive / Delete |

All destructive/state-change actions require confirmation dialogs. Delete is always danger-styled (permanent).

---

## Implementation Status (as of 2026-03-23)

### Complete
- Data layer (all 12 entities, 11 DAOs, 4 repos, all transactions)
- Infrastructure (AppPreferences, ExposureValues, ExportFormatter, SplashScreen)
- 13 ViewModels
- All UI screens: Gear Library, Roll List, Roll Setup, Kit Selector, Roll Journal, Frame Detail, Quick Screen, Settings, Widget
- Navigation (bottom bar, type-safe routes, deep links)
- Theme system (Alpine + Golden Hour, real-time switching)
- Custom OFL fonts (JetBrains Mono, Space Grotesk, Share Tech Mono)

### Not Yet Started (v1.0 remaining)
- **Onboarding screens** (Welcome screen + 5-step coach mark flow)
- Privacy policy URL (placeholder in SettingsScreen — replace before Play Store)
- OSS licenses screen (plugin in place, screen not wired)

### Deferred to v1.1
Horizontal scroll wheels, per-body aperture direction, lock screen notification, Darkroom Safe theme, Google Sheets sync, ExifTool export, medium format, zoom lenses, stepless aperture.

---

## v1.0 Scope — Hard Limits

Do not implement these regardless of how easy they seem:
- Medium format (enum value reserved but not surfaced in UI)
- Zoom lenses
- Stepless aperture / cine lenses
- Google Sheets sync
- ExifTool command file export
- Per-frame ISO override
- In-app map rendering (use geo: intent to open user's maps app)
- Background GPS tracking
- iOS support

---

## Code Quality Notes

Intended for Play Store release. Developer is learning Android development and reviews all generated code before shipping.

- Prefer clarity over cleverness
- Comment non-obvious decisions
- Follow MVVM, repository pattern, unidirectional data flow
- Primary concern: security and correctness around data handling and file I/O
- Be explicit about what each piece of code does and why

---

## Reference Documents in `/docs/`

> **Note on wireframes:** All wireframes in `docs/Wireframes_DETENT/` still reference the old name FRAME//LOG. This is cosmetic only — treat them as DETENT wireframes. Do not rename them.

| Document | Contents |
|---|---|
| `DETENT claude code handoff.md` | Full architecture reference (original handoff) |
| `DETENT PRD v0.4.md` | Full requirements and scope |
| `DESIGN decisions.md` | Functional design decisions from wireframing sessions |
| `Screen_Inventory/DETENT screen inventory.md` | All 26+ screens, every element, all behaviors |
| `Visual Pass Notes.md` | Typography, color palettes, ergonomics, haptics, licensing |
| `Roadmap for v1.1.md` | All deferred features with rationale |
| `Data_Model/` | Individual entity specifications (12 files) |
| `DAO_Walkthrough/` | Complete DAO method specifications (6 files) |
| `legacy_memory/memory/project_detent_status.md` | Session-by-session implementation notes |
