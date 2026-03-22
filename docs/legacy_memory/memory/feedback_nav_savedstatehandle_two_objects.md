---
name: NavBackStackEntry.savedStateHandle vs ViewModel savedState are different objects
description: Critical Navigation Compose architectural fact — the navigation-results handle and the ViewModel's SavedStateHandle are not the same object
type: feedback
---

`NavBackStackEntry.savedStateHandle` (used for passing results between screens) is a **different object** from the `SavedStateHandle` injected into a ViewModel via its constructor.

- `NavBackStackEntry.savedStateHandle` — backed by `NavResultSavedStateViewModel`; this is the navigation-results handle. `navController.previousBackStackEntry?.savedStateHandle` writes to this.
- ViewModel's `savedState: SavedStateHandle` — created by `AbstractSavedStateViewModelFactory` for the ViewModel's own state. Receives navigation route arguments at creation time.

Writing to one has NO effect on the other.

**Why:** Discovered when `RollSetupViewModel.observeKitSelection()` used `savedState.getStateFlow("selected_kit_id", -1)` to receive a kit ID written by `KitSelectorScreen` via `navController.previousBackStackEntry?.savedStateHandle`. The ViewModel's collect block never fired because it was watching the wrong object.

**How to apply:** For the "return a result to previous screen" pattern in Navigation Compose:
- The **sending** screen writes to `navController.previousBackStackEntry?.savedStateHandle?.set(key, value)`
- The **receiving** screen observes via `navController.currentBackStackEntry?.savedStateHandle?.getStateFlow(key, default)` **in the composable** (e.g., a `LaunchedEffect`), NOT in the ViewModel
- The ViewModel exposes a regular function (e.g., `loadAndApplyKit(kitId)`) that the composable calls when the value arrives
