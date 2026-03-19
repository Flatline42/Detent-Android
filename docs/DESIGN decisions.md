# DESIGN decisions
## Design Decisions

Functional design decisions made during wireframing. These are not aesthetic choices — they affect information architecture, interaction behavior, or accessibility. Execute during visual design and implementation phases.

---

## Haptic Feedback

**Decision:** Directional haptic feedback on all stepper controls.

*   `+` (increase) — single firm haptic (`VibrationEffect.createOneShot`)
*   `−` (decrease) — double short pulse (`VibrationEffect.createWaveform`)
*   Log Frame button — distinct longer confirmation haptic on successful frame log

**Rationale:** Aperture `+` and shutter `−` buttons are physically adjacent on the widget. In the field, thumb drift is likely. Distinct haptic patterns let the user feel whether they dialed the correct direction without looking at the screen — recreating the tactile detent feedback of a physical aperture ring.

**Applies to:** Widget steppers, Quick Screen steppers. Consistent pattern both places for muscle memory.

**Implementation note:** Android `VibrationEffect` API. Respect system accessibility settings — haptics should be suppressible if the user has vibration disabled.

---

## Shutter Speed Display Format

**Decision:** Shutter speed displayed as plain integers for fractions, suffixed for whole seconds, `B` for bulb.

| Stored value | Display |
| --- | --- |
| `"1/1000"` | `1000` |
| `"1/500"` | `500` |
| `"1/125"` | `125` |
| `"1/30"` | `30` |
| `"1s"` | `1s` |
| `"2s"` | `2s` |
| `"4s"` | `4s` |
| `"B"` | `B` |

**Rationale:** Matches how photographers read camera dials. Fractions of a second don't need the `1/` prefix — context is implied. Full seconds need the `s` suffix to distinguish from fractions.

**Applies to:** Widget display, Quick Screen display, Roll Journal frame cards.

---

## Shutter Speed Color Coding

**Decision:** Whole-second and bulb shutter values displayed in a distinct color.

**Rationale:** Follows established camera convention (red numbers on Canon AE-1 and others). Whole-second exposures represent a meaningfully different shooting mode — long exposure, tripod, etc. Color distinction provides instant visual confirmation of exposure range without reading the value.

**Applies to:** Widget stepper display, Quick Screen stepper display.

**Execute in:** Visual design phase.

**Color blindness:** maybe a toggle in settings to turn on color blindness so color coding used is color blind friendly for accessibility? 

---

## Current Frame Emphasis

**Decision:** The current active frame number is visually emphasized in the Roll Journal View.

**Rationale:** When reviewing the journal mid-roll, the photographer needs to quickly locate where they are. Visual emphasis (weight, color, or indicator) distinguishes the current frame pointer position from logged and unlogged frames.

**Execute in:** Visual design phase.

---

## Consistent Stepper Pattern

**Decision:** All stepper controls across the app use identical layout and behavior — decrement left, value center, increment right. No exceptions.

**Rationale:** Muscle memory. The user learns the pattern once on the widget and it works identically on the Quick Screen and Frame Detail/Edit screen.

**Applies to:** Widget, Quick Screen, Frame Detail/Edit, any future stepper controls.

---

## Color Blind Accessibility Mode

**Decision:** Provide an alternate visual mode for color-coded elements.

**Rationale:** Shutter speed color coding and any other color-dependent UI conventions must have an accessible alternative. Color alone should never be the sole differentiator for functional information.

**Implementation options (decide in visual design phase):** Shape indicator, underline, or text suffix as supplement or replacement for color. Example: whole-second shutter values could show an underline or `s` suffix in addition to color rather than relying on color alone.

**Setting:** Toggle in Settings — "Color blind mode" or "Accessible color mode." Off by default.

**Execute in:** Visual design phase.

---

## Database Backup and Restore

**Decision:** Full database backup and restore available from Settings.

**Rationale:** Per-roll CSV/JSON export is for data extraction. A full backup is for continuity — phone replacement, factory reset, data loss prevention. A journaling app with no backup path puts years of logged rolls at risk.

**Implementation:** Export writes the SQLite database as a `.framelog` file to Downloads or via Android share sheet (Google Drive, email, etc.). Restore reads a `.framelog` file and replaces the current database with confirmation prompt.

**Setting:** New "Data & Backup" section in Settings containing two actions — Export Backup and Restore from Backup.

**Note:** Restore is destructive — overwrites current data. Must include a clear warning and confirmation step before executing.

**Applies to:** Settings screen, screen inventory, PRD scope.

---

## Filter EV Sum Display

**Decision:** Display the sum of active filter EV reductions alongside the filter row.

**Rationale:** When stacking filters the total light reduction is immediately actionable for exposure decisions. Saves mental arithmetic in the field.

**Behavior:**

*   Only displays when at least one active filter has a non-null `evReduction` value
*   Shows `~` prefix if any active filter has null `evReduction` — indicates sum may be incomplete
*   Displays nothing when no filters are active or all active filters have null reduction

**Format:** `-4.0 EV` right-aligned on the filter row.

**Applies to:** Quick Screen filter row, Frame Detail/Edit filter row.

---

## Filter Chip Row — Most Recently Used Pattern

**Decision:** Filter row displays up to 3 chips showing most recently used filters. A `+` button accesses remaining available filters via picker.

**Behavior:**

*   Chips reflect the 4 most recently toggled filters from the roll's available filters
*   Chips are instant toggle — tap to activate/deactivate without opening picker
*   Tapping `+` opens a picker of remaining available filters on this roll
*   Selecting a filter from the picker promotes it into the chip row, displacing least recently used chip
*   Chip row adapts automatically to shooting behavior — no manual configuration

**Rationale:** Most shooting sessions use 1-2 filters consistently. The chip row surfaces those without extra taps while keeping the UI uncluttered regardless of how many filters are in the bag.

**Applies to:** Quick Screen, Frame Detail/Edit screen.

---

## Filter Chip Row — Maximum 4 Chips

**Update to previous decision:** Target 4 chips rather than 3. Most shooting scenarios involve at most 3-4 filters on a roll. 4 chips covers the majority of use cases without the picker. The `+` button handles anything beyond 4.

**Verify fit in visual design phase.**

---

## Quick Screen Control Order — Frequency Based

**Decision:** Controls ordered by frequency of use, increasing toward the bottom of the screen (thumb zone).

Order top to bottom within the interactive zone:

1.  Lens selector — set and forget, session level
2.  Filter chips + EV sum — occasional changes
3.  Exposure compensation — infrequent
4.  Frame pointer — infrequent
5.  Aperture — frequent
6.  Shutter speed — most frequent, closest to thumb
7.  Note field — optional, pre-log pause point
8.  Log Frame button — anchored to bottom

**Rationale:** Thumb naturally rests lower on the screen. Highest frequency controls belong in the prime thumb zone. Note field intentionally sits outside primary thumb zone since it breaks the one-handed paradigm by design.

---

**New OQ — Roll loaded state**

The current `status` enum (`active | finished | archived`) doesn't distinguish between a roll that's loaded in a camera and one that's purchased/prepared but not yet loaded. With multiple camera bodies this distinction matters.

Proposed solution: add `isLoaded` boolean to Roll entity. A roll can be `status = active` and `isLoaded = false` (in inventory) or `isLoaded = true` (in a camera). Only loaded rolls drive the widget and quick screen.

**Impact:** Data model, Roll entity, Roll Setup screen, Roll List display logic.

---

## GPS Coordinates — Tappable Link

**Decision:** GPS coordinates on Frame Detail screen are displayed as a tappable link.

**Behavior:** Tapping coordinates triggers Android's standard geo intent — opens the user's preferred maps app at that location. No in-app map rendering needed.

**Format:** `37.7749, -119.5183` as a tappable text link.

**Rationale:** Zero implementation cost, uses the user's preferred maps app, works with any mapping app installed on the device.

**Applies to:** Frame Detail / Edit screen.

---

## Kit Card Filter Display

**Decision:** Kit cards in the Kit Selector show up to 3-4 filter chips inline, then "+N more" if additional filters exist.

**Rationale:** Consistent with the Quick Screen chip row pattern. Visual at a glance, doesn't require opening the kit to see what filters are in the bag.

**Applies to:** Kit Selector screen, Gear Library Kits list.

---

## Kit Management — Gear Library Only

**Decision:** Copy, delete, and full edit of kits are only available from the Gear Library Kits tab. The Kit Selector is a selection surface only.

**Exception:** Creating a new kit is available from the Kit Selector via FAB — because discovering you need a new kit mid-roll-setup is a valid workflow.

**Copy kit:** Available via overflow menu (⋯) on Kit Detail screen. Creates a duplicate with "Copy of \[name\]" as default name, opens for editing immediately.

**Applies to:** Kit Selector, Gear Library Kit Detail / Edit screen.

---

**Data model updates — add to reconciliation list:**

*   `Filter.filterSizeMm` — nullable integer (mm). Null for slot/square filters or anything without a fixed thread size.
*   `Lens.filterSizeMm` — nullable integer (mm). Null if no standard filter thread.
*   Filter picker at roll setup and kit building gains optional "compatible only" toggle when active lens has a `filterSizeMm` value.

---

**Design decisions to paste in:**

## Gear Library Sort Options

**Decision:** Sort dropdown sits next to search bar on all gear list screens. Default is A–Z.

| Tab | Sort options |
| --- | --- |
| Lenses | A–Z, Last Used, Recently Added, Mount Type |
| Bodies | A–Z, Last Used, Recently Added |
| Filters | A–Z, Last Used, Recently Added, Filter Size |
| Film Stocks | A–Z, Last Used, Recently Added |
| Kits | A–Z, Last Used, Recently Added |

---

## Kit Last Used Date

**Decision:** Kit cards display last used date. Enables sorting by last used and helps identify active vs template kits at a glance.

**Data model:** Add `lastUsedAt` nullable timestamp to Kit entity — updated when a kit is loaded into a roll at roll setup.

---

## Gear Detail / Edit — Input Patterns

**Decision:** Standardized input patterns across all gear detail screens.

**Folksonomy fields** (mount type, filter type) use autocomplete text fields — existing values appear as suggestions while typing, with a "+ add new" option at the bottom for first entries. Same Android autocomplete pattern used consistently across all folksonomy fields.

**Aperture fields** use a fixed `f/` prefix with a plain number entry. User types `1.4`, display reads `f/1.4`. Eliminates ambiguous input.

**Filter size** uses a plain number entry with fixed `mm` suffix. Nullable — labeled "optional" in the UI.

**Aperture increments** uses a simple three-option dropdown (full / half / third). No stepper needed — this is a one-time setup field.

**Delete and Duplicate** live in the overflow menu (⋯) in the screen header. Not surfaced on the list screen or anywhere else.

**Applies to:** All five Gear Detail / Edit screens (Lens, Camera Body, Filter, Film Stock, Kit).

---

## Monetization — Tip Jar

**Decision:** Tip jar model. No ads, no subscription, no feature gating.

**Implementation:**

*   Ko-fi link or Google Play in-app purchase — TBD
*   Mentioned once during onboarding: "If you find this useful, consider buying me a coffee"
*   Triggered once at 5 rolls logged: friendly non-pushy prompt
*   Persistent button in Settings — always accessible, never nagged again after the 5-roll prompt
*   Language is personal and casual, not transactional

**Rationale:** Film photography community is niche and passionate. Respects the user completely. Aligns with the app's personal tool philosophy.

---

## Kit Detail — Primary Lens Selection

**Decision:** Primary lens designated via radio button on each lens row, not by position or first-added order.

**Behavior:** Filled radio = primary. Empty radio with "tap to set primary" hint = available but not primary. Tapping an empty radio immediately promotes that lens, demotes the previous primary. Exactly one primary required at all times.

**Visual treatment:** Primary lens row gets a heavier border to reinforce status.

**Remove:** × button right-aligned on each lens row. Removing the primary lens auto-promotes the next lens in the list, or clears primary if no lenses remain.

---

## Kit Detail — Camera Body Change Cascade

**Decision:** Changing the camera body on a kit automatically removes lenses that don't match the new body's mount type. Filters are not automatically removed but the user is warned to review them.

**Warning copy:** "Switching will remove incompatible lenses. Review your filter selection afterward."

**Rationale:** Lens incompatibility is a hard constraint — a Canon FD lens physically cannot mount on a Nikon F body. Filter incompatibility is advisory — a null/slot filter works with any lens, and a size-mismatched screw-mount filter is just inconvenient, not impossible. Cascading filter removal would be over-engineered.

---

## Legal & Compliance — Required for Play Store Publication

**Privacy Policy**

Google Play requires a privacy policy for any app that collects personal data. FRAME//LOG collects GPS coordinates (optional, user-controlled) which qualifies.

**Options:**

*   Use a generator like [privacypolicygenerator.info](https://privacypolicygenerator.info) or [app-privacy-policy-generator.nisrulz.com](https://app-privacy-policy-generator.nisrulz.com) — the latter is specifically designed for Android apps
*   Host the policy at a public URL (GitHub Pages works fine for this)
*   Link from the Settings screen and the Play Store listing

**Key points to cover:** what data is collected (GPS, none transmitted), how it's stored (local only), no third party sharing, user control (GPS is opt-in per roll)

**Open Source Licenses**

Android apps using third-party libraries must acknowledge their licenses. Room, Kotlin, and any other dependencies will have their own license requirements — most are Apache 2.0 which is permissive and straightforward.

**Implementation:** Android has a built-in mechanism for this — the Google Play Services OSS Licenses plugin auto-generates the licenses screen from your Gradle dependencies. Essentially free to implement once the plugin is added.

**Action items at implementation phase:**

*   Run OSS plugin, verify generated license list
*   Generate privacy policy using a template generator
*   Host policy at a public URL
*   Add both URLs to Play Store listing

---

## Onboarding — Coach Mark Pattern

**Decision:** Onboarding uses overlay coach marks on the real app UI rather than a separate tutorial flow or slideshow.

**Behavior:**

*   Semi-transparent dark overlay with a highlighted cutout around the target element
*   Arrow pointing to the target
*   Coach card with step counter, explanation, "got it ›" button, and "skip tour" link
*   "Got it ›" advances the coach mark but does NOT perform the action — user must tap the real element themselves to build muscle memory
*   "Skip tour" available at every step, dismisses onboarding entirely

**Step sequence:**

1.  Gear Library → Lenses tab → tap + to add first lens
2.  Gear Library → Bodies tab → tap + to add first camera body
3.  Gear Library → Stocks tab → tap + to add first film stock
4.  Roll List → tap + FAB to set up first roll
5.  Settings → Widget Setup Instructions

**Rationale:** User performs real interactions on real UI. Ends onboarding with actual data in the app and genuine muscle memory, not just passive familiarity.

---

## Roll Loading — Interaction Matrix

**Roll List card interactions:**

*   **Tap** — open Roll Journal
*   **Swipe left or right** — Load Roll confirmation sheet (unloaded rolls only)
*   **Long press** — context-aware quick action menu:
    *   Unloaded roll: Load Roll (→ confirmation), Open Journal, Delete (→ danger confirmation)
    *   Loaded roll: Open Journal, Delete (→ danger confirmation)
    *   Finished roll: Open Journal, Archive (→ confirmation), Delete (→ danger confirmation)
    *   Archived roll: Open Journal, Unarchive (→ confirmation), Delete (→ danger confirmation)

**Unloaded roll card visual treatment:** Dashed border, muted icon, italic "← swipe to load →" hint text.

**Roll Journal bottom bar states:**

*   Unloaded: Load Roll (primary) + Export
*   Loaded/active: Finish Roll (primary) + Export
*   Finished: Export only (Archive in overflow ⋯)
*   Archived: Export only (Unarchive in overflow ⋯)

**Delete roll:** Available in overflow menu (⋯) on Roll Journal header and in long press menu on Roll List. Requires danger confirmation. Never shown as a primary button.

---

## Active Roll Selection — Quick Screen + Widget

**Decision:** The Quick Screen header displays the currently active roll and is tappable to switch between loaded rolls.

**Behavior:**

*   Header shows "active roll · tap to switch" label above roll name
*   Roll name styled with underline and `›` to indicate tappability
*   Tapping opens a bottom sheet showing all loaded rolls as radio button options
*   Each row shows: roll name, camera body, frame progress bar, last shot timestamp
*   Selecting a roll immediately updates the Quick Screen and widget — no confirmation needed
*   "Last shot" timestamp is the key differentiator for identifying which camera is in hand

**Widget behavior:** Always reflects whichever roll is currently selected as active on the Quick Screen. Updates immediately on selection change.

**Fallback:** If only one roll is loaded it is automatically active — no selection needed.

---