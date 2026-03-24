---
name: Pixel 7 device-specific behaviour
description: Haptic engine constraints and launcher cell sizes confirmed through real-device testing on Pixel 7
type: reference
---

## Pixel 7 haptic engine — confirmed behaviour

Tested on Pixel 7, Android 13/14, system haptics enabled.

**What does NOT work:**
- `VibrationEffect.createOneShot(duration, amplitude)` — silently rejected regardless of duration or amplitude
- `VibrationEffect.createWaveform` with 2-segment arrays (e.g. `longArrayOf(0, 80), intArrayOf(0, 80)`) — silently rejected
- `View.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)` — silently rejected on `LocalView.current` (root ComposeView)
- `View.performHapticFeedback(..., FLAG_IGNORE_VIEW_SETTING)` — also silently rejected
- `LocalHapticFeedback.performHapticFeedback()` — unreliable, can be suppressed

**What DOES work:**
- `VibrationEffect.createWaveform` with **4 or more segments** fires reliably.
- The minimum confirmed pattern: `createWaveform(longArrayOf(0, 30, 50, 30), intArrayOf(0, 80, 0, 80), -1)`

**Rule:** Any new haptic effect on Pixel 7 must use a 4-segment (minimum) `createWaveform` array.

**Widget context extra requirement:** In Glance `ActionCallback.onAction()`, use `VibratorManager.defaultVibrator` on API 31+ — the `context` parameter doesn't reliably route `getSystemService(Vibrator::class.java)` on those API levels. See `getVibrator()` helper in `DETENTWidgetActions.kt`.

**Current working patterns in the app:**
```kotlin
// Stepper (increment and decrement) — quick double tap
createWaveform(longArrayOf(0, 30, 50, 30), intArrayOf(0, 80, 0, 80), -1)

// Log Frame confirm — same rhythm, longer/heavier
createWaveform(longArrayOf(0, 120, 80, 120), intArrayOf(0, 80, 0, 80), -1)
```

Directional differentiation between + and − is deferred. May revisit when refactoring steppers.

---

## Pixel 7 home screen launcher — widget cell sizes

- **Column width**: ~70dp (standard formula — 5 columns × (70n−30) = 320dp matches maxResizeWidth exactly)
- **Row height**: ~74dp (slightly above standard — 3 rows × (74n−30) = 192dp, vs standard 180dp)

The 12dp gap between standard formula (180dp) and Pixel 7 actual (192dp) for 3 rows was enough to prevent the resize handle from reaching the 3-row snap point when `maxResizeHeight="180dp"`. Fixed by setting `maxResizeHeight="250dp"` in `frame_log_widget_info.xml`.

The `SizeMode.Responsive` height thresholds (180dp for COMPACT and LOOSE) remain correct: 192dp ≥ 180dp so the right layout activates once 3 rows is reachable on Pixel 7.



