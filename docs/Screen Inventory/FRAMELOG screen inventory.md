# FRAMELOG screen inventory
## Screen Inventory

## Overview

FRAME//LOG contains approximately 26 distinct screens across 5 sections. Most complexity is concentrated in the one-time gear setup layer. Daily field use touches 2-4 screens maximum.

Many screens follow the same list/detail pattern repeated for different entity types — the unique screen template count is closer to 8-10.

See `FRAMELOG_navigation_map.mermaid` for the full navigation diagram.

---

## Onboarding (first run only)

| Screen | Description | Notes |
| --- | --- | --- |
| Welcome Screen | App name, tagline, "show me around" / "skip setup" buttons | Sets onboarding boolean flag |
| Add First Lens | Gear library lens edit screen with coach mark overlay | Reuses gear library screen |
| Add First Camera Body | Gear library body edit screen with coach mark overlay | Reuses gear library screen — mount type picker already populated from lens |
| Add First Film Stock | Gear library stock edit screen with coach mark overlay | Reuses gear library screen |
| Load First Roll | Roll setup screen with coach mark overlay | Reuses roll setup screen |
| Widget Setup Instructions | Step-by-step guide to adding 2×4 widget to home screen | Android cannot add widget programmatically. Also accessible from Settings anytime. |

**Coach mark pattern:** Semi-transparent overlay with highlighted cutout, arrow, step counter, "got it ›" button, and "skip tour" link. User performs real interactions — coach mark does not perform actions on their behalf.

---

## Bottom Navigation — 5 Destinations

| Position | Destination | Layer |
| --- | --- | --- |
| 1 (left) | Gear Library | 4 |
| 2 | Roll List | 3 |
| 3 (center) | Quick Screen | 2 |
| 4 | Active Roll Journal | 3 |
| 5 (right) | Settings | — |

Quick Screen is center position — primary daily destination, thumb-reachable without looking.

---

## Layer 2 — Quick Screen

Single screen. App home. All interactive elements in bottom 60% for one-handed use.

**Header (tappable when multiple loaded rolls exist)**

| Element | Type | Notes |
| --- | --- | --- |
| "active roll · tap to switch" | Muted label | Only shown when multiple loaded rolls exist |
| Roll name | Tappable, underlined with › | Opens Switch Roll bottom sheet |
| Filter count + EV comp | Read-only, right-aligned | Status indicators |
| Frame counter | Read-only | Current / total |

**Switch Roll Bottom Sheet** — appears on header tap. Shows all loaded rolls with radio buttons, frame progress bars, and "last shot" timestamp. No confirmation needed — selection is immediately reversible.

**Interactive fields (top to bottom — frequency order)**

| Element | Type | Notes |
| --- | --- | --- |
| Lens selector | Tap to cycle | Cycles through roll's configured lenses |
| Filter chips (up to 4 MRU) + EV sum + picker | Tap to toggle / + to open picker | MRU = most recently used. EV sum right-aligned, ~ prefix if any filter has null evReduction |
| Exposure compensation | Centered stepper | ±3 EV in 1/3 stop increments, nullable |
| Frame pointer | Centered stepper | Manual navigation between frame slots |
| Aperture | Large centered stepper | Bounded by lens min/max, increments from lens |
| Shutter speed | Large centered stepper | Increments from camera body. Whole seconds in distinct color. |
| Note | Text field + mic button | Optional, speech to text available |
| Log Frame | Primary action button | Full width, anchored to bottom |

All fields pre-populate from last logged frame. Overwriting a previously logged frame triggers confirmation prompt.

---

## Layer 3 — Roll Management

### Roll List Screen

Entry point for all rolls. FAB (+) bottom right to create a new roll.

**Tabs:** Active / Finished / Archived (swipe to navigate). Tab shows count when inside (e.g. "Active (3)").

**Active tab sections:**

*   Loaded rolls (camera icon, solid border)
*   Unloaded rolls (canister icon, dashed border, muted, "← swipe to load →" hint)

**Card interactions:**

*   Tap → Roll Journal View
*   Swipe left or right → Load Roll confirmation sheet (unloaded rolls only)
*   Long press → context-aware quick action menu (options vary by roll state)

**Long press menu by state:**

| Roll State | Available Actions |
| --- | --- |
| Active, unloaded | Load Roll (→ confirmation), Open Journal, Delete (→ danger) |
| Active, loaded | Open Journal, Delete (→ danger) |
| Finished | Open Journal, Archive (→ confirmation), Delete (→ danger) |
| Archived | Open Journal, Unarchive (→ confirmation), Delete (→ danger) |

### Roll Setup Screen

Single scrollable form. Two-handed, unhurried. Accessed via FAB on Roll List and during onboarding.

| Section | Fields |
| --- | --- |
| Film stock | Select from library or create new |
|  | Frame count (defaults from stock, overridable) |
|  | Push/pull actuator (Pull 3 → Box → Push 3, ISO updates live) |
|  | Custom ISO override (collapsed link below actuator) |
|  | Film expiry date (optional) |
| Kit (optional) | Load from kit button — pre-fills body, lenses, filters. All editable after. |
| Camera body | Select from library |
| Lenses | Primary lens (required), additional lenses optional. Filtered by mount type. |
| Filters | Chip pattern, optional |
| Details | GPS capture toggle |
|  | Roll name (auto-generated, editable) |
| Actions | Create Roll (unloaded) / Create & Load Roll (loaded) |

### Kit Selector Screen

Accessed from Roll Setup. Searchable list of kits. Read-only selection surface.

*   Tap kit → pre-populates roll setup, returns to form
*   FAB → create new kit (navigates to Kit Detail, returns on save)
*   "Skip — fill gear manually" button at bottom

### Roll Journal View

Scrollable list of all frame slots for a specific roll.

**Header:** Roll name, back arrow (←), overflow menu (⋯), progress bar, film stock + body + ISO info.

**Frame cards — hybrid pattern:**

*   Logged: filled circle + frame number + solid border + aperture, shutter, lens, filters, timestamp, GPS indicator, note preview
*   Unlogged: empty circle + dashed border + "— unlogged —"
*   Current frame: heavier border + "current" label
*   Extra frames labeled "(extra)"

**Bottom bar — state dependent:**

| Roll State | Bottom Bar |
| --- | --- |
| Active, unloaded | Load Roll (primary) + Export |
| Active, loaded | Finish Roll (primary) + Export |
| Finished | Export only |
| Archived | Export only |

**Overflow menu (⋯):** Archive / Unarchive (state dependent), Delete (→ danger confirmation always).

**Roll status transitions:**

*   Active (unloaded) → Active (loaded): Load Roll — confirmation required
*   Active (loaded) → Finished: Finish Roll — confirmation required, irreversible
*   Finished → Archived: Archive — confirmation required
*   Archived → Finished: Unarchive — confirmation required
*   Any state → Deleted: Delete — danger confirmation, permanent

### Frame Detail / Edit Screen

Same fields as Quick Screen (minus frame pointer), pre-populated. Always editable.

*   `isLogged` toggle — marks frame as logged without fabricating a timestamp (retroactive correction)
*   GPS coordinates displayed as tappable link — opens user's preferred maps app via Android geo intent
*   Note field slightly taller than Quick Screen — unhurried context
*   Save Changes button (no auto-advance)
*   Back arrow triggers save prompt if unsaved changes exist
*   Overflow menu (⋯): no additional actions in v1.0

---

## Layer 4 — Gear Library

Five tabs, identical list/detail pattern. All list screens: search bar + sort dropdown, scrollable cards, FAB.

| Tab | Entity | Sort Options |
| --- | --- | --- |
| Lenses | Lens | A–Z, Last Used, Recently Added, Mount Type |
| Bodies | CameraBody | A–Z, Last Used, Recently Added |
| Filters | Filter | A–Z, Last Used, Recently Added, Filter Size |
| Film Stocks | FilmStock | A–Z, Last Used, Recently Added |
| Kits | Kit | A–Z, Last Used, Recently Added |

**Design principle:** All list screens follow the same pattern — search bar + sort, scrollable results, FAB to add, tap to detail. No exceptions in v1.0.

**Gear Detail / Edit screens:** Full CRUD. Delete and Duplicate in overflow menu (⋯) only. Input patterns: `f/` prefix for aperture values, `mm` suffix for filter size, autocomplete for folksonomy fields (mount type, filter type).

**Kit Detail / Edit:** Radio buttons for primary lens designation. Camera body change automatically removes incompatible lenses with warning to review filters.

---

## Settings

| Section | Screen / Setting | Notes |
| --- | --- | --- |
| Shooting Defaults | Extra frames per roll | Integer, default 2 |
|  | GPS precision | High / battery saver |
|  | Default export format | CSV / JSON / plain text |
| Appearance | App theme | Light / dark / system |
|  | Accessible color mode | Off by default — alternative indicators for color-coded elements |
| Data & Backup | Export backup | Writes .framelog file via share sheet |
|  | Restore from backup | Destructive — requires confirmation |
| Widget | Widget setup instructions | Same screen as onboarding step 6 |
| Onboarding | Re-run introduction | Resets onboarding boolean flag |
| Support | Tip jar | Ko-fi link or Play in-app purchase |
| About | Version | Read-only |
|  | Privacy policy | External link |
|  | Open source licenses | Generated by OSS Licenses plugin |

---

## Export Flow

Triggered from Roll Journal View. Bottom sheet — not a full screen.

| Format | v1.0 |
| --- | --- |
| CSV | Yes |
| JSON | Yes |
| Plain text | Yes |
| Google Sheets | v1.1 |
| ExifTool command file | v1.1 |

Format selection via radio buttons. Export button hands file to Android share sheet — user chooses destination (Drive, email, Files, etc.). No direct cloud integration in v1.0.

---

## Screen Count Summary

| Section | Screens |
| --- | --- |
| Onboarding | 6 (4 reuse existing screens) |
| Quick Screen | 1 + Switch Roll bottom sheet |
| Roll Management | 5 (Roll List, Roll Setup, Kit Selector, Roll Journal, Frame Detail) |
| Gear Library | 10 (5 list + 5 detail/edit) |
| Settings | 1 (with sub-sections) |
| Export | 1 (bottom sheet) |
| **Total unique screens** | **~24 + 2 bottom sheets** |