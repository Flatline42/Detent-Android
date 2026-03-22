# FrameLog Project Memory

## Project Status
- [project_framelog_status.md](project_framelog_status.md) — Full implementation status: data layer, ViewModels, and partially-started UI layer

## Device-specific
- [reference_pixel7_haptics.md](reference_pixel7_haptics.md) — Pixel 7 haptic engine: requires 4-segment createWaveform minimum; createOneShot and short waveforms silently rejected

## Feedback
- [feedback_wait_for_goahead.md](feedback_wait_for_goahead.md) — Wait for explicit go-ahead before starting large implementation tasks
- [feedback_write_memory_after_changes.md](feedback_write_memory_after_changes.md) — Always update memory after completing changes; don't skip the memory write step
- [feedback_nav_savedstatehandle_two_objects.md](feedback_nav_savedstatehandle_two_objects.md) — NavBackStackEntry.savedStateHandle ≠ ViewModel's savedState; observe navigation results in the composable, not the ViewModel
