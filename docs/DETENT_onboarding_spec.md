# DETENT — Onboarding Implementation Spec
**Version:** 1.0  **Date:** 2026-03-24  **For:** Claude Code

---

## Overview

Implement a first-run onboarding flow for DETENT. The flow is a hybrid of
guided narration (explaining UI features) and real interactions (user
actually creates their first gear and roll). By the end of onboarding the
user has a lens, camera body, film stock, and a loaded roll ready to log.

The flow is gated by a SharedPreferences boolean `onboardingComplete`
(already defined in AppPreferences). On first launch this is false and the
Welcome Screen is shown instead of Quick Screen. The flow is re-runnable
from Settings → Onboarding → Re-run introduction, which resets the flag.

**Do not make any git commits. Run `./gradlew assembleDebug` after all
changes and confirm clean compile.**

---

## File Structure

Create a new package: `ui/onboarding/`

Files to create:
```
ui/onboarding/
    OnboardingActivity.kt         — or a full-screen composable hosted in MainActivity
    WelcomeScreen.kt
    OnboardingCoachScreen.kt      — reusable coach mark overlay composable
    OnboardingStep.kt             — step enum / state model
    WidgetSetupScreen.kt
    OnboardingViewModel.kt
```

The onboarding flow should be a separate nav graph or a dedicated
full-screen composable stack that MainActivity routes to when
`onboardingComplete == false`. On completion it navigates to Quick Screen
and sets the flag.

---

## App Launch Routing (MainActivity)

Existing launch sequence reads `onboardingComplete` from AppPreferences.
If false → show Welcome Screen.
If true → existing logic (Quick Screen empty state or active roll).

The wireframes already had a TODO for this routing — wire it up now.

---

## Step Inventory (11 Steps)

| # | Type | Screen | User Action Required |
|---|------|--------|---------------------|
| 1 | Welcome | WelcomeScreen | Tap "Show me around" or "Skip setup" |
| 2 | Do | Gear Library → Lenses → New Lens | Create real lens |
| 3 | Do | Gear Library → Bodies → New Body | Create real camera body |
| 4 | Tour | Gear Library → Filters tab | Read + tap "Got it" |
| 5 | Do | Gear Library → Film Stocks → New Stock | Create real film stock |
| 6 | Tour | Gear Library → Kits tab | Read + tap "Got it" |
| 7 | Do | Roll List → FAB → Roll Setup | Create & Load real roll |
| 8 | Tour | Roll Journal | Read + tap "Got it" |
| 9 | Tour | Quick Screen | Read + tap "Got it" |
| 10 | Tour | Rolls → Finished tab | Read + tap "Got it" |
| 11 | Close | Widget Setup Instructions (Settings) | Tap "Done" → Ko-fi prompt → Quick Screen |

---

## Step 1 — Welcome Screen

**New screen. Not a coach mark overlay.**

Layout (centered, vertically distributed):
- App icon (centered, large)
- App name: **DETENT** (large, Space Grotesk, theme primary color)
- Tagline: "a film photography field journal"
- Body copy (muted, centered):

> Welcome to DETENT — a shot logger designed for speed and workflow.
> The goal is to log a frame in under 5 seconds from the Quick Screen,
> or under 3 seconds from the home screen widget.

- Primary button: **"Show me around"** → navigates to Step 2
- Secondary button: **"Skip setup"** → shows inline message below button:
  *"No problem. You can run this introduction any time from
  Settings → Onboarding."* then after 2 seconds navigates to Quick Screen
  and sets `onboardingComplete = true`
- Version number (muted, bottom): e.g. "v1.0.0"

**Note:** The wireframe shows "FRAME//LOG" — the correct name is **DETENT**.

---

## Steps 2–10 — Coach Mark Overlay System

### OnboardingCoachScreen Composable

A full-screen composable that layers over the real destination screen.
The user can interact with the real UI beneath the overlay except where
the overlay intercepts touches (on the coach card and skip link only).

**Visual spec (from wireframe WIREFRAME_onboarding_coach.svg):**

```
Overlay:        #111111 at 82% opacity (fill entire screen)
Cutout ring:    White stroke, 2dp, drawn as circle or rounded rect
                around the target element. Inner area is clear
                (use Canvas with BlendMode.Clear or a Path subtraction)
Arrow:          ↓ or ↑ white, 20sp, pointing from card toward spotlight
Coach card:     #222222 background, #444444 border 0.5dp, cornerRadius 10dp
                Positioned below spotlight when element is in upper half,
                above spotlight when element is in lower half (FAB case:
                card goes above, arrow points down toward FAB)
```

**Coach card layout:**

```
[ step X of 11 ]                    [ skip tour ]
[ Title — Space Grotesk, 11sp bold, white      ]
[ Body copy — 9sp, #DDDDDD, 2-3 lines          ]
[                           [ got it  › ]      ]
```

- "step X of 11" — 8sp, #AAAAAA, top-left of card
- "skip tour" — 9sp, #888888, top-right of card, tappable
- Title — 11sp, bold, #FFFFFF
- Body — 9sp, #DDDDDD
- "got it ›" — white pill button, 9sp bold, #111111 label,
  bottom-right of card, cornerRadius 6dp

**Skip behavior:** Tapping "skip tour" at any step navigates immediately
to Quick Screen and sets `onboardingComplete = true`. No confirmation.

**"Got it ›" behavior:** Advances to next step. On the final coach step,
navigates to Widget Setup screen (Step 11).

### Target Element Identification

Each step specifies which element gets the spotlight. Use
`onGloballyPositioned` to capture the element's bounding box in screen
coordinates, then pass to the overlay composable for cutout rendering.

Use a `LocalOnboardingController` CompositionLocal or a shared
OnboardingViewModel to coordinate between the overlay and the screens
beneath it.

---

## Step 2 — Add Your First Lens

**Type:** Do (user performs real interaction)

**Navigation:** App navigates to Gear Library → Lenses tab, then opens
New Lens screen automatically (same as tapping "+ Add Lens" FAB).

**Coach mark shown on:** Gear Library Lenses tab, spotlight on
"+ Add Lens" pill button (bottom-right of screen).

**Coach card copy:**
- Step: "step 2 of 11"
- Title: "Start with your glass"
- Body: "Your gear library starts with lenses — tap + Add Lens to add
  your first one. Lenses define your mount type, which tells the app
  which camera bodies are compatible."

**"Got it ›":** Navigates to New Lens screen (opens the form).

**On New Lens screen**, show a second coach card (no overlay darkening
needed here — just a tooltip-style card anchored below the Mount type
field):
- Title: "Mount type is important"
- Body: "Type your mount (e.g. 'Canon FD'). This is free-form — you
  define your own vocabulary. Camera bodies will pick from the mount
  types you enter here. Filter size (mm) is optional — leave blank for
  lenses without a standard filter thread."
- "Got it ›": Dismisses tooltip, user fills form and saves normally.

**Advancement:** Step advances automatically when the user saves a lens
(observe navigation back event from New Lens screen). Navigate to Step 3.

---

## Step 3 — Add Your First Camera Body

**Type:** Do

**Navigation:** App navigates to Gear Library → Bodies tab, opens
New Camera Body screen automatically.

**Coach mark shown on:** Bodies tab, spotlight on "+ Add Body" FAB/button.

**Coach card copy:**
- Step: "step 3 of 11"
- Title: "Add a camera body"
- Body: "Select your mount type from the list — it's already populated
  from your lens. Shutter increments (full, half, or third stops) control
  the shutter speed stepper when this body is active."

**Advancement:** Advances when user saves a camera body. Navigate to Step 4.

---

## Step 4 — Filters (Tour)

**Type:** Tour (no creation required)

**Navigation:** App navigates to Gear Library → Filters tab.

**Coach mark shown on:** Filters tab list area (no specific element
spotlight — spotlight the tab itself or the empty state area).

**Coach card copy:**
- Step: "step 4 of 11"
- Title: "Filters are optional"
- Body: "Add filters to your library if you use them. The filter size
  (mm) matches your lens thread — leave it blank and the filter will
  show as compatible with all lenses. EV reduction is also optional,
  for filters like UV that don't meaningfully affect exposure."

**"Got it ›":** Navigate to Step 5.

---

## Step 5 — Add Your First Film Stock

**Type:** Do

**Navigation:** App navigates to Gear Library → Film Stocks tab, opens
New Film Stock screen automatically.

**Coach mark shown on:** Film Stocks tab, spotlight on "+ Add Film Stock"
FAB/button.

**Coach card copy:**
- Step: "step 5 of 11"
- Title: "Add a film stock"
- Body: "Enter the box speed ISO — push and pull adjustments happen at
  the roll level, not here. 'Discontinued' hides the stock from the
  roll setup picker without deleting it, so historical rolls are
  preserved."

**Advancement:** Advances when user saves a film stock. Navigate to Step 6.

---

## Step 6 — Kits (Tour)

**Type:** Tour

**Navigation:** App navigates to Gear Library → Kits tab.

**Coach mark shown on:** Kits tab, spotlight on the empty state area or
the "+ Add Kit" FAB.

**Coach card copy:**
- Step: "step 6 of 11"
- Title: "Kits are gear presets"
- Body: "A kit bundles a camera body, lenses, and filters into a named
  preset — like 'Street Kit' or 'Yosemite Bag'. At roll setup, loading
  a kit pre-fills all your gear in one tap. You can still edit everything
  before creating the roll."

**"Got it ›":** Navigate to Step 7.

---

## Step 7 — Create & Load Your First Roll

**Type:** Do

**Navigation:** App navigates to Roll List screen, spotlight on the
FAB (+) button.

**Coach mark shown on:** Roll List, spotlight on FAB.

**Coach card copy:**
- Step: "step 7 of 11"
- Title: "Load your first roll"
- Body: "Tap + to set up a new roll. Select a film stock, set push/pull
  stops or a custom ISO, and choose your camera body and lenses. Then
  tap 'Create & Load' to load the film and start logging."

**"Got it ›":** Opens Roll Setup screen (same as tapping FAB).

**On Roll Setup screen**, show tooltip coach cards at key fields
(dismiss on tap, user fills form normally):

*Film Stock field:*
- "Select the film you're shooting. Frame count defaults from the stock —
  you can adjust it. Add extra frames in Settings for bulk-loaded or
  short-loaded rolls."

*Push/Pull stepper:*
- "Push or pull in full stops, or set a custom ISO if you're rating the
  film at a non-standard value — useful for expired film."

*Expiry date field:*
- "Optional — useful if you're shooting expired film."

*GPS toggle:*
- "Captures your GPS coordinates when you log each frame. Note: the
  widget cannot capture GPS locations due to Android limitations. Enable
  GPS capture in Settings first if you haven't already."

*Create & Load button:*
- "'Create Roll' saves an unloaded roll to inventory. 'Create & Load'
  loads the film into your camera and makes it active for logging."

**Advancement:** Advances when user taps "Create & Load Roll" and roll
is successfully created. Navigate to Step 8.

---

## Step 8 — Roll Journal (Tour)

**Type:** Tour

**Navigation:** After roll creation the app navigates to Quick Screen
(per the fix already made). For this step, navigate to the Roll Journal
of the newly created roll instead, then continue to Quick Screen after.

**Coach mark shown on:** Roll Journal frame list, spotlight on Frame 1
card (the CURRENT frame).

**Coach card copy:**
- Step: "step 8 of 11"
- Title: "Your roll journal"
- Body: "Every frame slot is created in advance and waiting for you.
  Frame 1 is your current frame. Tap any frame to edit it — useful for
  retroactive corrections. The journal is your permanent record of
  the roll."

**"Got it ›":** Navigate to Step 9 (Quick Screen).

---

## Step 9 — Quick Screen (Tour)

**Type:** Tour

**Navigation:** App navigates to Quick Screen with the newly loaded roll active.

Show a sequence of coach cards walking through the screen top to bottom.
Each "got it ›" advances to the next sub-step. Sub-steps do not have
separate step numbers — they are all "step 9 of 11".

**Sub-step 9a — Header:**
Spotlight: roll name / header area
- Title: "Your active roll"
- Body: "Tap the roll name to switch between loaded rolls if you're
  shooting multiple cameras at once."

**Sub-step 9b — Lens selector:**
Spotlight: lens selector row
- Title: "Cycle your lenses"
- Body: "Tap to cycle through the lenses configured on this roll."

**Sub-step 9c — Filter chips:**
Spotlight: filter chips row
- Title: "Toggle filters"
- Body: "Tap a chip to toggle a filter on or off. The EV sum updates
  automatically. Tap + to access all available filters."

**Sub-step 9d — Aperture + Shutter steppers:**
Spotlight: both large steppers
- Title: "Aperture and shutter speed"
- Body: "Set your exposure values here. Shutter speeds at 1 second or
  slower are highlighted — you'll want to be careful about motion blur."

**Sub-step 9e — Frame pointer:**
Spotlight: frame pointer stepper
- Title: "Frame pointer"
- Body: "Shows your current frame. If you've logged frames out of order,
  tap the frontier indicator to jump back to the current frame."

**Sub-step 9f — Log Frame button:**
Spotlight: Log Frame button
- Title: "Log the frame"
- Body: "Tap to log the current frame. The next frame inherits all the
  same settings — change only what changed on your camera between shots.
  That's the whole workflow."

**"Got it ›" on 9f:** Navigate to Step 10.

---

## Step 10 — Finished Rolls & Export (Tour)

**Type:** Tour

**Navigation:** App navigates to Roll List → Finished tab.

**Coach mark shown on:** Roll List, spotlight on Finished tab.

**Coach card copy:**
- Step: "step 10 of 11"
- Title: "Finished and archived rolls"
- Body: "When you finish a roll, it moves here. Tap any roll to open its
  journal, edit frames, and export your data as plain text, CSV, or JSON
  via the share sheet. Archive rolls you're done with to keep things tidy."

**"Got it ›":** Navigate to Step 11.

---

## Step 11 — Widget Setup + Closing

**Type:** Close

**Navigation:** App navigates to Settings → Widget Setup Instructions
screen (the existing screen, same as Settings → Widget row).

This is the existing WidgetSetupScreen — no coach mark overlay needed.
At the bottom of the Widget Setup screen, add an onboarding-specific
footer section that only appears when arriving from onboarding
(pass a flag via nav argument `fromOnboarding: Boolean`):

**Onboarding footer (below existing "Done" button):**

Divider, then:

> **One more thing**
>
> The 2×4 widget gives you aperture, shutter speed, and one-tap frame
> logging without unlocking your phone — under 3 seconds per frame.
> Note that the widget cannot capture GPS locations due to Android
> limitations.
>
> You can revisit this introduction any time from Settings → Onboarding.
> For more detail, visit the project readme:
> [TODO: insert GitHub Pages / readme URL]

Then a Ko-fi prompt card (styled distinctly — e.g. slight border,
slightly different background):

> **If DETENT is useful to you**
> consider buying me a roll of film ☕
> [ Tip jar → ]   ← links to TODO: Ko-fi URL

**"Done" button behavior when `fromOnboarding = true`:**
- Sets `onboardingComplete = true` in AppPreferences
- Navigates to Quick Screen
- Clears back stack so back button doesn't return to onboarding

---

## OnboardingViewModel

Manages step state across the flow.

```kotlin
// Key responsibilities:
// - currentStep: StateFlow<OnboardingStep>
// - fun advance()          — go to next step
// - fun skip()             — set onboardingComplete, navigate to QuickScreen
// - fun complete()         — set onboardingComplete, navigate to QuickScreen
```

`OnboardingStep` sealed class or enum with values for each of the 11
steps and sub-steps.

---

## AppPreferences Changes

`onboardingComplete` key should already exist. Confirm it does and that:
- Default value is `false`
- `setOnboardingComplete(true)` is called on skip, complete, and
  "Done" from Widget Setup when `fromOnboarding = true`

---

## Settings Wiring (Already Exists)

Settings → Onboarding → "Re-run introduction" should:
1. Set `onboardingComplete = false`
2. Navigate to Welcome Screen

Confirm this is wired. If not, wire it now.

---

## Navigation Notes

- The onboarding flow navigates into real app screens (Gear Library,
  Roll Setup, etc.) — these are the same composables already built.
  The coach mark overlay is layered on top, not a separate copy of
  the screens.
- Use a dedicated onboarding nav graph or a global overlay composable
  in MainActivity that reads current onboarding step from
  OnboardingViewModel and renders the appropriate coach mark.
- The overlay approach (global composable in MainActivity) is preferred
  — it avoids duplicating navigation logic and works with the existing
  NavGraph cleanly.

---

## What NOT to Do

- Do not modify any existing screen's core logic for onboarding
- Do not add onboarding state to existing ViewModels
- Do not make any git commits
- Do not implement the widget itself (already built)
- Do not add GPS permission requests here — that already exists at
  roll setup
- Do not surface medium format, zoom lenses, or any v1.1 features

---

## Compile Check

After all changes:
```
./gradlew assembleDebug
```
Report clean compile before considering this done.
