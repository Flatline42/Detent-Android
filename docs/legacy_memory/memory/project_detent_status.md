---
name: DETENT implementation status
description: Full implementation status of the DETENT Android app — data, VM, and UI layers
type: project
---

## Fully complete layers (compiling cleanly as of 2026-03-19)

**Database / data layer:**
- All 12 Room entities in `data/db/entity/`
- All enums in `Enums.kt`: CameraBodyFormat, FilmFormat, ShutterIncrements, ApertureIncrements, ColorType, RollStatus
- TypeConverters for all enums (string values, not ordinals)
- All 11 DAOs in `data/db/dao/`
- Relation types in `data/db/relation/`: RollWithDetails, FrameWithDetails, KitWithDetails, + join relations
- Export types in `RollExport.kt`
- AppDatabase: singleton, all DAO accessors, 3 multi-DAO @Transaction methods
- All 4 repositories in `data/repository/`

**Infrastructure:**
- `data/AppPreferences.kt` — SharedPreferences wrapper (activeRollId, onboarding, settings, per-roll currentFrame + MRU filters)
- `ui/util/ExposureValues.kt` — shutter speed + aperture value lists, display formatters, EC values
- `ui/util/ExportFormatter.kt` — CSV, JSON, plain text formatters for RollExport

**ViewModel layer (13 VMs):**
GearLibraryViewModel, LensDetailViewModel, CameraBodyDetailViewModel, FilterDetailViewModel,
FilmStockDetailViewModel, KitDetailViewModel, KitSelectorViewModel, RollListViewModel,
RollSetupViewModel, RollJournalViewModel, FrameDetailViewModel, QuickScreenViewModel, SettingsViewModel

---

## UI layer — Gear Library screens complete (verified compiling as of 2026-03-20)

**Done and compiling clean:**
- `ui/navigation/NavRoutes.kt` — all type-safe @Serializable route definitions (Navigation Compose 2.8.0)
- `ui/navigation/NavGraph.kt` — DETENTNavGraph NavHost; all Gear Library routes wired, Roll/Quick/Settings routes commented as TODO
- `MainActivity.kt` — updated to use DETENTNavGraph + rememberNavController
- `ui/gear/GearLibraryScreen.kt` — full 5-tab implementation (Lenses, Bodies, Filters, Film, Kits); ScrollableTabRow, search + sort dropdown, LazyColumn, gear cards for all types, EmptyState; defines ColorType.label (internal)
- `ui/gear/LensDetailScreen.kt` — full form; defines shared internal composables **MountTypeField** and **EnumDropdown**, plus ApertureIncrements.label
- `ui/gear/CameraBodyDetailScreen.kt` — full form; defines CameraBodyFormat.label and ShutterIncrements.label
- `ui/gear/FilterDetailScreen.kt` — full form; private FilterTypeField autocomplete composable
- `ui/gear/FilmStockDetailScreen.kt` — full form with discontinued Switch; defines FilmFormat.label; reuses ColorType.label from GearLibraryScreen.kt
- `ui/gear/KitDetailScreen.kt` — full form: body picker, lens add/remove/primary-select, filter toggles, IncompatibleLensesRemoved snackbar, Duplicate text button

**Roll screens — in progress:**
- `ui/rolls/RollListScreen.kt` — complete and compiling (2026-03-20)
  - 3-tab layout (Active/Finished/Archived) with counts
  - Active tab splits into "In camera" (loaded) and "Inventory" (unloaded) sections
  - SwipeToDismissBox on unloaded cards → load confirmation dialog
  - Long press context menu (state-aware options: Load Roll, Open Journal, Archive, Unarchive, Delete)
  - 4 AlertDialog confirmations: Load, Delete, Archive, Unarchive
  - `RollListRow` data class (JOIN: Roll + filmStock name + cameraBody name + loggedFrameCount)
  - `dashedBorder` modifier extension for unloaded card treatment
  - `NavGraph.kt` updated: RollList route wired
- `ui/rolls/RollJournalScreen.kt` — complete and compiling (2026-03-20)
  - TopAppBar (back, roll name, overflow ⋯ with Archive/Unarchive/Delete)
  - Header: ISO · push/pull · loaded status subtitle + LinearProgressIndicator (logged/total)
  - Search OutlinedTextField + sort dropdown (frame 1→N / N→1) via KeyboardArrowDown/Up
  - LazyColumn of FrameCards: logged (solid border, filled circle, aperture/shutter/lens/EC/GPS/notes) and unlogged (dashed border, empty circle, "— unlogged —"); current frame gets tertiary accent border + "CURRENT" label
  - Long-exposure shutter speeds render in `colorScheme.error` (red convention)
  - Bottom bar: unloaded active → Load Roll + Export; loaded active → Finish Roll + Export; finished/archived → Export only
  - ExportFormatBottomSheet (ModalBottomSheet): CSV/JSON/Plain Text RadioButton list + Export button
  - 5 AlertDialog confirmations: Load, Finish Roll (error-colored, irreversible warning), Archive, Unarchive, Delete (error-colored)
  - Share export via `Intent.ACTION_SEND` (content as EXTRA_TEXT)
  - `NavGraph.kt` updated: RollJournal route wired

- `ui/rolls/RollSetupScreen.kt` — complete and compiling (2026-03-20)
  - Scrollable form: FilmStock, Kit (optional), CameraBody, Lenses, Filters, Details, CustomISO, Actions
  - ModalBottomSheet pickers for FilmStock, CameraBody, AddLens (filtered by mount type)
  - DatePickerDialog for film expiry date (with Clear button to nullify)
  - Push/pull stepper (OutlinedButton −/+ with label "box"/"+N"/"-N") + live ISO display
  - Custom ISO collapse/expand link (TextDecoration.Underline clickable) — expands OutlinedTextField
  - FilterChip row (FlowRow @ExperimentalLayoutApi) for all available filters
  - LensRow: RadioButton (primary) + name + Clear remove button
  - GPS Switch with description
  - Two action buttons: "Create Roll" (OutlinedButton) and "Create + Load" (Button, primary)
  - RollCreated → navigate RollJournal; RollCreatedAndLoaded → RollJournal (TODO: QuickScreen)
  - NavigateToKitSelector → navigate(KitSelector)
  - NavGraph: RollSetup route wired
- `ui/rolls/KitSelectorScreen.kt` — complete and compiling (2026-03-20; bug fix 2026-03-20)
  - Simple searchable kit list with kit cards (name, body, primary lens + count, filters)
  - KitSelected event writes kit.kit.id to previousBackStackEntry?.savedStateHandle["selected_kit_id"], then pops back
  - FAB navigates to KitDetail(0) for new kit creation
  - NavGraph: KitSelector route wired
  - **Bug fixed (crash):** original shared-ViewModel approach (getBackStackEntry) crashed; replaced with SavedStateHandle result-passing pattern
  - **Bug fixed (silent failure):** RollSetupViewModel.observeKitSelection() observed the ViewModel's own SavedStateHandle, which is a DIFFERENT object from NavBackStackEntry.savedStateHandle (the navigation-results handle that KitSelectorScreen writes to). Observation moved to RollSetupScreen composable via LaunchedEffect(navController) → navController.currentBackStackEntry?.savedStateHandle?.getStateFlow("selected_kit_id", -1). ViewModel now exposes loadAndApplyKit(kitId: Int) instead of observing internally.

**Icon set note:** Project uses material-icons-core only (no extended). Confirmed safe core icons: ArrowBack (AutoMirrored.Filled), KeyboardArrowRight (AutoMirrored.Filled), Search, Clear, Check, Add, MoreVert, KeyboardArrowDown, KeyboardArrowUp. ExpandMore/ExpandLess, LocationOn, Mic are NOT in core. Substitutions: KeyboardArrowDown/Up for expand/collapse; OutlinedButton("Mic") text button for speech; text link for GPS coordinates.

- `ui/frames/FrameDetailScreen.kt` — complete and compiling (2026-03-20)
  - TopAppBar: back arrow (unsaved-changes-aware), "Frame N" title, logged timestamp + "GPS logged" subtitle
  - Logged toggle: FilterChip (yes/no)
  - Lens picker row: tappable → ModalBottomSheet with LensPickerItem list ("current" trailing label)
  - Filter chips: FlowRow, MRU-ordered, FilterChip per rollFilter; EV sum with `~` prefix if any null evReduction
  - EC stepper: small (42×36dp OutlinedButtons), null treated as 0.0 index, tapping value clears to null, "tap to clear" underline hint
  - Aperture + Shutter: LargeStepper (52dp square OutlinedButtons + 108dp value Box); aperture list widest-first so `+`=lower index; shutter slowest-first so `+`=lower index; long-exposure shutter renders in colorScheme.error
  - Notes field + OutlinedButton("Mic") → RecognizerIntent.ACTION_RECOGNIZE_SPEECH via rememberLauncherForActivityResult
  - GPS: tappable "lat, lng ›" text link → geo: Intent via viewModel.onGpsCoordinatesTapped()
  - BackHandler(enabled = hasUnsavedChanges) → ConfirmDiscard event → AlertDialog
  - Save Changes Button → SaveSuccessful event → popBackStack()
  - NavGraph.kt updated: FrameDetail route wired

- `ui/quickscreen/QuickScreenScreen.kt` — complete and compiling (2026-03-20)
  - Header: roll name (tappable+underlined ›  when multiple rolls loaded), active filter count, Frame X/Y, total EV (EC + filter EV sum with ~ prefix)
  - Lens row: OutlinedCard, tap cycles through roll lenses (disabled if only 1 lens)
  - Filter chips: FlowRow, up to 4 MRU-ordered FilterChips; "+" chip when more than 4 filters available
  - Filter EV sum: right-aligned label above chip row, ~ prefix if any null evReduction
  - EC SmallStepper (42×36dp buttons): null treated as 0.0 index, tapping underlined value clears to null
  - Frame pointer SmallStepper: shows "X / Y"; advancing past last emits RollComplete
  - Aperture LargeStepper (52dp square buttons): + = wider = lower index; − = narrower = higher index
  - Shutter LargeStepper: + = slower = lower index; − = faster; long exposure renders in colorScheme.error
  - Note OutlinedTextField (minLines=1, maxLines=3) + OutlinedButton("Mic") → RecognizerIntent
  - Log Frame Button (64dp height, full width): GPS-aware — requests ACCESS_FINE_LOCATION if roll.gpsEnabled; uses FusedLocationProviderClient.getCurrentLocation(); falls back to null coords on denial
  - DETENTged event → HapticFeedbackType.LongPress; ConfirmOverwrite → AlertDialog; RollComplete → "Open Journal" dialog
  - SwitchRollBottomSheet: list of loaded rolls, RadioButton per roll, frame progress, last-shot formatted time, LinearProgressIndicator
  - FilterPickerBottomSheet: all available roll filters as toggleable FilterChips
  - EmptyState: shown when no rolls loaded — "New Roll" button navigates to RollSetup
  - NavGraph.kt updated: QuickScreen route wired

- `ui/settings/SettingsScreen.kt` — complete and compiling (2026-03-20)
  - TopAppBar: "Settings" title (no back — top-level tab)
  - Sections: Shooting Defaults, Appearance, Data & Backup, Widget, Onboarding, Support, About + footer
  - Extra frames per roll: stepper AlertDialog (0–10, −/+ buttons around large number)
  - GPS precision, default export format, app theme: SingleChoiceDialog<T> with RadioButton list
  - Accessible color mode: Switch trailing in row
  - Export backup: calls ViewModel, shares via FileProvider content:// URI + Intent.ACTION_SEND
  - Restore from backup: GetContent file picker → danger AlertDialog → copyUriToCache() → ViewModel
  - Restart required dialog after restore: restartApp() via getLaunchIntentForPackage + finishAffinity
  - Widget / Onboarding / OSS Licenses: emit events handled with snackbar TODOs (screens not yet built)
  - Tip jar: OutlinedButton("tip jar ›") in SupportRow, opens TIP_JAR_URL in browser
  - Privacy policy: opens PRIVACY_POLICY_URL in browser (URL placeholder — replace before release)
  - Version: read from PackageManager (BuildConfig not enabled; AGP 9 disables it by default)
  - NavGraph.kt updated: Settings route wired
  - AndroidManifest.xml updated: ACCESS_FINE_LOCATION + ACCESS_COARSE_LOCATION permissions; FileProvider provider block
  - res/xml/file_provider_paths.xml created: cache-path "backups" for backup file sharing

- `ui/widget/DETENTWidget.kt` — GlanceAppWidget + composable UI (complete, 2026-03-20)
  - `WidgetState` object: all DataStore<Preferences> key definitions
  - `DETENTWidget.GlanceAppWidget` with `provideGlance` + `provideContent`
  - `ActiveRollContent`: header (filmCamera, Frame X/Y, filter bullet count), divider, stepper row (aperture + shutter), divider, log frame OR roll complete bottom section
  - Roll complete state: "roll complete →" in `primary` color; taps via `Intent(ACTION_VIEW, "DETENT://journal/$rollId")` deep link to Roll Journal
  - Log frame / roll complete branches are inline within the Column (not extracted composables) — `defaultWeight()` is a ColumnScope extension and cannot be used outside the Column lambda
  - `NoRollContent`: empty state, taps to open MainActivity via explicit Intent (actionStartActivity doesn't have reified overload in glance-appwidget 1.1.0)
  - `StepperButton` (28×28dp surfaceVariant background, cornerRadius 6dp), `StepperValue` (48×28dp, long-exposure in error color), `HorizontalDivider` (1dp surfaceVariant)
  - Uses `GlanceTheme.colors` from `glance-appwidget` (not glance-material3) — avoids naming conflict between `androidx.glance.GlanceTheme` and `androidx.glance.material3.GlanceTheme`
- `ui/widget/DETENTWidgetReceiver.kt` — GlanceAppWidgetReceiver (complete, 2026-03-20)
  - `onUpdate` and `onEnabled` call `DETENTWidgetUpdater.update()` via MainScope coroutine
- `ui/widget/DETENTWidgetUpdater.kt` — data loader + Glance state writer (complete, 2026-03-20)
  - Reads activeRollId from SharedPreferences, loads roll+filmStock+cameraBody from Room
  - Writes all WidgetState keys via `updateAppWidgetState(context, glanceId) { prefs -> }` (2-arg form, provides MutablePreferences)
  - Frame pointer derived entirely from Room — independent of Quick Screen's SharedPreferences pointer
  - Frame pointer logic: `highestLoggedFrame` = last logged frame on the roll; `targetFrame` = first unlogged frame with frameNumber > highestLoggedFrame (or frame 1 when nothing logged); `isRollComplete` = targetFrame == null
  - All exposure pre-population (aperture, shutter, LENS_ID, FILTER_IDS) sourced from `highestLoggedFrame`
  - FILTER_IDS requires second DAO call (`getFrameById`) — RollWithDetails.frames contains bare Frame entities without FrameFilter join data
- `ui/widget/DETENTWidgetActions.kt` — 5 ActionCallbacks (complete, 2026-03-20; haptics removed 2026-03-21)
  - `ApertureUpAction` / `ApertureDownAction` — step aperture list (widest-first, + = lower index = wider)
  - `ShutterUpAction` / `ShutterDownAction` — step shutter list (slowest-first, + = lower index = slower)
  - `LogFrameAction` — writes frame to Room with carried-forward lens + filters (no GPS, no overwrite dialog); calls `DETENTWidgetUpdater.update()` which re-derives frame pointer from Room; no SharedPreferences writes
  - `getAppWidgetState` requires 3-arg form: `getAppWidgetState(context, PreferencesGlanceStateDefinition, glanceId): Preferences`
- `AndroidManifest.xml` updated: `DETENTWidgetReceiver` receiver; deep link intent filter (`DETENT://journal`) for Roll Complete → Roll Journal navigation
- `NavGraph.kt` updated: `composable<RollJournal>` has `deepLinks = listOf(navDeepLink { uriPattern = "DETENT://journal/{rollId}" })`
- `res/xml/frame_log_widget_info.xml`: 4×2 target cells, 250×110dp min, resizeMode=horizontal|vertical, maxResizeWidth=320dp, maxResizeHeight=250dp (raised from 180dp for Pixel 7 74dp-row compatibility)
- `libs.versions.toml` + `app/build.gradle.kts` updated: glance 1.1.0 (glance-appwidget + glance-material3)
- `QuickScreenViewModel.kt` updated: calls `DETENTWidgetUpdater.update()` after frame log and after roll switch
- `RollListViewModel.kt` updated: calls updater in `onLoadRollConfirmed()` and `onDeleteConfirmed()`
- `RollJournalViewModel.kt` updated: calls updater in `onLoadRollConfirmed()`, `onFinishRollConfirmed()`, and `onDeleteConfirmed()`
- `RollSetupViewModel.kt` updated: calls updater directly (suspend, not launch) in `createRoll(loadAfterCreate=true)` path

**Widget state keys (WidgetState object):** HAS_ROLL, ROLL_ID, FILM_CAMERA, FRAME_NUMBER, TOTAL_FRAMES, FILTER_COUNT, APERTURE, SHUTTER, APERTURE_LIST, SHUTTER_LIST, LENS_ID (Int, -1 sentinel), FILTER_IDS (comma-separated String), IS_ROLL_COMPLETE (Boolean)

**Full widget update trigger map:**
| Trigger | Location |
|---|---|
| Widget add / launcher request | DETENTWidgetReceiver.onUpdate/onEnabled |
| Roll loaded from Roll List | RollListViewModel.onLoadRollConfirmed() |
| Roll deleted from Roll List (was active) | RollListViewModel.onDeleteConfirmed() |
| Roll loaded from Roll Journal | RollJournalViewModel.onLoadRollConfirmed() |
| Roll finished (was active) | RollJournalViewModel.onFinishRollConfirmed() |
| Roll deleted from Roll Journal (was active) | RollJournalViewModel.onDeleteConfirmed() |
| New roll created and loaded | RollSetupViewModel.createRoll(loadAfterCreate=true) |
| Roll switched from Quick Screen | QuickScreenViewModel.onRollSwitched() |
| Frame logged from Quick Screen | QuickScreenViewModel.writeFrame() |
| Frame logged from widget | LogFrameAction → DETENTWidgetUpdater.update() |
| Aperture/shutter stepped | Stepper actions — GlanceState only, no DB or updater |

**Not yet started (needs user go-ahead first):**
- Onboarding screens (Welcome, coach marks)

---

---

## Changes applied (2026-03-21) — second session

- **Quick Screen frame pointer frontier indicator** — `QuickScreenUiState` gains `frontierFrameNumber: Int?` (null = roll complete). `computeFrontierFrameNumber()` in the ViewModel derives it from the roll's frame list: first unlogged frame after the highest logged frame number, or frame 1 when nothing is logged. Set in `populateDraftForRoll` on every Room emission; optimistically updated in `writeFrame` to avoid a flash. UI: when `currentFrameNumber != frontierFrameNumber`, a compact row below the frame pointer stepper shows "not at current frame" (muted) and a "← return to N" `TextButton` that snaps the pointer back. `isAtFrontier` also true when `frontierFrameNumber == null` (roll complete). Smart-cast handles the non-null guarantee inside the `if (!isAtFrontier)` block.

- **Roll Setup expiry date — single row** — replaced the two-element `PickerRow` (FieldLabel above + "Optional" value) with a single inline `Row`: "Set expiry date (optional)" when no date set, "Expiry date: yyyy-MM-dd" when set. Saves one row of vertical space and removes redundant label. `RollSetupScreen.kt`.

- **Widget: three size variants** — `DETENTWidget.kt` now uses `SizeMode.Responsive` with three declared sizes. `WidgetSize` enum (STANDARD / COMPACT / LOOSE) and `StepperDimensions` data class parameterise all per-size values. `ActiveRollContent` branches the stepper section: `StepperSectionSideBySide` for STANDARD/COMPACT, `StepperSectionStacked` for LOOSE. `defaultWeight()` inlined directly inside Row lambdas (Glance scope extension constraint). `frame_log_widget_info.xml` updated: `resizeMode="horizontal|vertical"`, `maxResizeWidth="320dp"`, `maxResizeHeight="250dp"`.
  - STANDARD (4×2): button 28×28dp, value 48×28dp, label 9sp, value 15sp — side-by-side
  - COMPACT (4×3): button 36×36dp, value 56×36dp, label 10sp, value 17sp — side-by-side
  - LOOSE (5×3): button 88×44dp (doubled width), value fills defaultWeight, label 11sp, value 20sp — stacked full-width, labels centered

- **Widget: maxResizeHeight fix for Pixel 7** — `maxResizeHeight` was set to 180dp (standard 70dp-cell formula for 3 rows). Pixel 7 rows are ~74dp tall: 3 rows = 192dp, putting the 3-row snap point 12dp above the cap. Fixed by raising `maxResizeHeight` to 250dp. Column width on Pixel 7 follows the standard 70dp formula so `maxResizeWidth="320dp"` (= 5 columns) is unchanged. `SizeMode.Responsive` height thresholds (180dp) remain correct — 192dp ≥ 180dp activates the right layout once 3 rows is reachable.

## Bug fixes applied (2026-03-21) — first use pass

- **Quick Screen control order** — reordered scrollable column: Frame pointer → Lens → Filters → EV Comp → Aperture → Shutter → Note → Log Frame. HorizontalDivider between Frame pointer and Lens section.
- **Frame 1 defaults** — when no previous logged frame exists on a roll, `QuickScreenViewModel.populateDraftForRoll()` defaults aperture to `f/5.6` and shutter speed to `1/125`.
- **Rolls bottom nav from Roll Journal** — removed `restoreState = true` from Rolls tab click handler in `NavGraph.kt`. Previously the saved sub-stack `[RollList → RollJournal]` was being restored, keeping the user on the journal. Now always lands on RollList.
- **IME padding on all 7 form screens** — added `imePadding()` before `verticalScroll()` on: LensDetailScreen, CameraBodyDetailScreen, FilterDetailScreen, FilmStockDetailScreen, KitDetailScreen, RollSetupScreen, FrameDetailScreen.
- **GPS setting simplified** — `AppPreferences` `GpsPrecision` enum removed, replaced with `gpsCaptureEnabled: Boolean` (default false). `SettingsScreen` GPS precision dialog removed, replaced with inline Switch. `SettingsViewModel.gpsPrecision` → `gpsCaptureEnabled`.
- **Per-roll GPS defaults from global setting** — `RollSetupUiState.gpsEnabled` initialised from `appPreferences.gpsCaptureEnabled`. `onGpsEnabledChanged()` emits `GpsDisabledInSettings` event if user tries to enable while global GPS is off. `RollSetupScreen` shows AlertDialog explaining to enable GPS in Settings first.
- **Custom ISO moved in RollSetupScreen** — "set custom ISO instead" link + field moved from Details section to directly below PushPullRow in Film Stock section.
- **Roll Journal sort dropdown N placeholder** — `Frame: 1 → N` / `Frame: N → 1` replaced with `Frame: 1 → $totalExposures` / `Frame: $totalExposures → 1` using the already-computed `totalExposures` value.
- **Haptics — extended debugging (2026-03-21)** — Long debugging arc. Root causes and final working state:
  - VIBRATE permission added to `AndroidManifest.xml`.
  - `LocalHapticFeedback`, `createOneShot`, and `View.performHapticFeedback` all fail silently on Pixel 7 in various ways. `View.performHapticFeedback` produced zero haptics on any button.
  - `createWaveform` with 2-segment arrays (e.g. `longArrayOf(0, 80)`) also silently rejected on Pixel 7.
  - **Root cause confirmed**: Pixel 7 haptic engine requires a minimum of 4 waveform segments. The double-pulse pattern `createWaveform(longArrayOf(0, 30, 50, 30), intArrayOf(0, 80, 0, 80), -1)` is the minimum confirmed to fire.
  - Widget haptics (`DETENTWidgetActions.kt`): use `VibratorManager.defaultVibrator` on API 31+ (Glance context doesn't route `getSystemService(Vibrator)` reliably); fall back to direct `Vibrator` on API 26–30.
  - **Final haptic patterns** (all in `QuickScreenScreen.kt` private helpers):
    - `vibrateDecrement`: `longArrayOf(0, 30, 50, 30)` / `intArrayOf(0, 80, 0, 80)` — quick double tap
    - `vibrateIncrement`: identical to decrement (directional differentiation deferred; may revisit)
    - `vibrateConfirm` (Log Frame): `longArrayOf(0, 120, 80, 120)` / `intArrayOf(0, 80, 0, 80)` — doubled duration vs steppers for a distinct, noticeably heavier feel
  - **Widget haptics fully removed** — all vibrate calls, `getVibrator` helper, and VibrationEffect/Vibrator/VibratorManager imports removed from `DETENTWidgetActions.kt`. Single deferral comment left: "Widget haptics deferred — Glance ActionCallback context does not have reliable Vibrator/Looper access. Revisit in v1.1."
- **Backup export data loss** — WAL checkpoint PRAGMA approach was fundamentally unreliable: Room's connection pool keeps readers alive, preventing `TRUNCATE` from completing. Calling `execSQL` on a PRAGMA that returns data also throws. Final fix: `AppDatabase.closeInstance()` before copying — SQLite performs automatic full WAL checkpoint on last-connection-close. Same pattern already used by restore. `SettingsViewModel.createBackupFile()`.
- **New lens default aperture increments** — `LensDetailUiState.apertureIncrements` default changed from `THIRD` to `HALF`.
- CSV export as file attachment — `RollJournalScreen` `ShareExportContent` handler: when mimeType is `text/csv`, writes to `cacheDir/<filename>.csv`, gets FileProvider URI, shares via `EXTRA_STREAM` + `FLAG_GRANT_READ_URI_PERMISSION`. JSON and plain text keep existing `EXTRA_TEXT` path.

## Changes applied (2026-03-21) — Visual Pass

- **Typography & Fonts:** Added OFL fonts. `Type.kt` updated to define `SpaceGrotesk`, `ShareTechMono`, and `JetBrainsMono` and mapped them directly to Material 3 Typography tokens (e.g., `displayMedium` for ShareTechMono, `headlineSmall` for JetBrainsMono).
- **Quick Screen Layout (60% Rule):** Added a `Spacer(Modifier.weight(1f))` at the top of the scrollable column in `QuickScreenScreen.kt` to enforce the ergonomic mapping, pushing interactive elements into the bottom "thumb zone". Top Plate UI updated to use `displayMedium` and `displaySmall` tokens.

## Bug fixes applied (2026-03-20)

- **Widget frame pointer now independent of SharedPreferences** — Widget always targets first unlogged frame after the highest logged frame number (derived from Room, not SharedPrefs). No overwrite possible from widget. Roll complete state shown when no unlogged frames remain after highest logged; "roll complete →" button deep-links to Roll Journal via `DETENT://journal/{rollId}`. If no frames logged yet, defaults to frame 1. Skipped frames (e.g. frame 6 skipped via Quick Screen, frames 7+ logged) are correctly bypassed — widget advances past them.

- **Widget logged frames with null lens and empty filters** — `LogFrameAction` used `frame.lensId` (always null on fresh/unlogged frame slots) and `emptyList()` for filters. Fixed: `DETENTWidgetUpdater` now stores `LENS_ID` and `FILTER_IDS` in GlanceState from the last logged frame. `LogFrameAction` reads these, uses `lensId` from state, and computes the filter delta (reads existing FrameFilter rows defensively to handle the silent-overwrite case, then diffs against `targetFilterIds`).

- **Widget didn't update when rolls were loaded/finished/deleted outside Quick Screen** — `RollListViewModel`, `RollJournalViewModel`, and `RollSetupViewModel` had no widget update calls. Fixed: all three now call `DETENTWidgetUpdater.update(getApplication())` at every roll state transition that could change what the widget displays.

- **NavGraph.kt window insets regression** — `consumeWindowInsets(innerPadding)` was consuming both top and bottom, hiding FABs behind the nav bar and causing status bar overlap. Fixed: apply only `padding(bottom = innerPadding.calculateBottomPadding())` + `consumeWindowInsets(WindowInsets(bottom = ...))`, leaving top insets for inner Scaffolds' TopAppBars.

- **Crash: deleting an active/loaded roll** — `RollDao.getRollById` returned `Flow<RollWithDetails>` (non-nullable); when the roll was deleted, Room emitted `null`, causing NPE at `collect`. Fixed: return type changed to `Flow<RollWithDetails?>` in both DAO and RollRepository. Added null guard + `NavigateBack` event in `RollJournalViewModel`. Added `?: return` guard in `FrameDetailViewModel.populateFromFrame`.

- **Crash: "Load from kit" in RollSetupScreen** — shared ViewModel via `getBackStackEntry(RollSetup::class)` is unreliable in Navigation Compose. Replaced entirely: `KitSelectorScreen` now writes kitId to `previousBackStackEntry?.savedStateHandle`; `RollSetupViewModel` observes that key via `getStateFlow` (see KitSelectorScreen entry above).

## Bottom Navigation — wired (2026-03-20)

- Root `Scaffold` in `NavGraph.kt` hosts `DETENTNavigationBar` (private composable)
- `Modifier.consumeWindowInsets(innerPadding)` on the `NavHost` Box — prevents double-padding with per-screen Scaffolds
- Start destination changed from `GearLibrary` → `QuickScreen`
- Bottom bar shown only on the 5 top-level routes (GearLibrary, RollList, QuickScreen, RollJournal, Settings); hidden on all detail screens
- Tab 4 (Journal): reads `AppPreferences.activeRollId` at tap time; falls back to RollList if no roll loaded
- All tab navigations use `popUpTo<QuickScreen> + launchSingleTop + restoreState`; Quick tab itself pops inclusive
- Icons: Build (Gear), AutoMirrored.Filled.List (Rolls), Home (Quick), Create (Journal), Settings (Settings)

## Architecture notes

- No DI framework — repositories get AppDatabase passed directly
- Navigation: type-safe routes via @Serializable data classes; SavedStateHandle auto-populated with route params by Navigation 2.8.0
- ViewModel creation: `viewModel()` inside `composable<T>` — Navigation Compose handles Application + SavedStateHandle injection automatically
- Start destination is GearLibrary temporarily; will move to QuickScreen with onboarding/launch logic later
- MediumFormat excluded from all v1.0 UI (CameraBodyFormat, FilmFormat)
- `collectAsStateWithLifecycle()` used in screen composables (requires lifecycle-runtime-compose; available via lifecycle 2.8.0 BOM)

**Why:** App is offline-first, no server. data/model/ dir not created — entities serve as models directly.
**How to apply:** Wait for explicit go-ahead from user before starting large implementation blocks. Repository layer is the correct access point for ViewModels.
-   * * W i d g e t   U I   P o l i s h   ( C l e a n ) : * *   I n c r e a s e d   w i d g e t   n u m e r i c   d i s p l a y   b y   2 s p   f o r   a l l   l a y o u t s .   S e t   \ F o n t F a m i l y . M o n o s p a c e \   u n i v e r s a l l y   a c r o s s   \ F r a m e L o g W i d g e t . k t \   t o   b r i n g   t h e   L C D   l o o k   t o   t h e   e n t i r e   w i d g e t .   S e t   \ F o n t W e i g h t . B o l d \   o n   t h e   t o p   b a r   i n f o   ( \  i l m C a m e r a \ ,   F r a m e   C o u n t ,   F i l t e r   C o u n t )   a n d   t h e   \ S t e p p e r V a l u e \ / \ S t e p p e r S e c t i o n S t a c k e d \   r e a d o u t s .   C o n s t r a i n t   a n a l y s i s   v e r i f i e d   n o   v e r t i c a l   c l i p p i n g .   H a p t i c s   i n t e n t i o n a l l y   o m i t t e d   p e r   u s e r   r e v e r t .  
 



