# DETENT PRD v0.4
## DETENT — Product Requirements Document

**v0.4 — Working Draft** 2026-03-19

---

## 1\. Overview

DETENT is an Android application for film photographers to log shooting data in the field and manage equipment across sessions. Its core design principle is that the primary interaction — logging a frame — must be achievable one-handed, without unlocking the phone, in under three seconds.

The dominant usage pattern is continuation with small delta: the photographer is shooting a roll, most frames share settings with the previous, and only one or two values change per shot. The app is built around this pattern. All other functionality (equipment management, data export, retroactive editing) is subordinate to it.

---

## 2\. Design Philosophy

### The Journal Metaphor

A roll is a journal. Frame slots are pre-ruled pages. You fill them in as you shoot. You can go back and correct an entry. The journal closes when the roll is finished.

This metaphor should inform all UX copy and interaction design. The app never refers to "records" or "entries" — they are frames. The app never refers to "creating" a frame — you log it.

### Interaction Budget by Layer

Each layer of the app carries an explicit interaction budget. Features must fit within the budget of their layer, not bleed upward.

| Layer | Context | Hands | Time Budget | Primary Action |
| --- | --- | --- | --- | --- |
| Widget | Shooting, camera up | One (thumb) | < 3 seconds | Log frame |
| Quick Screen | Between shots, something changed | One (thumb) | < 15 seconds | Change setting, log frame |
| Roll Setup | Loading a new roll | Two | < 2 minutes | Configure roll from gear |
| Gear Library | At home, one-time setup | Two | Unlimited | Build equipment catalog |

---

## 3\. Scope

### 3.1 In Scope — v1.0

*   Home screen widget (2×4) with aperture stepper, shutter speed stepper, frame confirm
*   Quick screen: active roll state, all per-frame fields, one-tap log, roll switcher for multiple loaded rolls
*   Roll setup: pre-generated frame slots, gear selection from library or kit, push/pull ISO actuator
*   Roll inventory: loaded vs unloaded roll distinction, multiple loaded rolls supported
*   Gear library: cameras, lenses, filters, film stocks, kits
*   Kits: named gear sets (body + lenses + filters) for fast roll setup, copy/duplicate support
*   Auto-capture of GPS coordinates and timestamp on frame log (optional, toggled at roll setup)
*   Export: CSV, JSON, plain text via Android share sheet
*   Roll view with inline frame editing (retroactive correction)
*   Configurable extra frames per roll (e.g. +2 for 36-exposure, ×2 for half-frame bodies)
*   Full database backup and restore (.DETENT file)
*   Tip jar monetization (Ko-fi or Play in-app purchase)

### 3.2 Explicit Non-Goals — v1.0

*   EXIF injection into image files (export data only; ExifTool pipeline is user's responsibility)
*   Cloud sync or multi-device support
*   Photo attachment or scan management
*   Social or sharing features
*   iOS support
*   Google Sheets live sync (deferred to v1.1)
*   Medium format support (deferred to v1.1)
*   Zoom lens support (deferred to v1.1)
*   Stepless aperture / cine lens support (deferred to v1.1)
*   Reciprocity failure calculations (deferred — use dedicated calculators)
*   Development logging (out of scope — use Massive Dev Chart)

> **Note:** ExifTool-compatible command file export (.bat / shell script) is a strong v1.1 candidate based on stated workflow pain.

---

## 4\. Layer Specifications

### 4.1 Layer 1 — Home Screen Widget

A single Android App Widget placed on the home screen. Primary field interaction surface. 2×4 size only — wireframing confirmed 1×4 is insufficient for the required information density.

**Fields displayed (2×4)**

*   Roll name + frame counter (read-only, left)
*   Active filter count + EV compensation (read-only, right)
*   Aperture stepper — decrement / value / increment
*   Shutter speed stepper — decrement / value / increment
*   Log Frame button (full width, bottom)

**Behavior**

*   Defaults to last logged frame's aperture and shutter speed
*   Aperture steps bounded by active lens min/max aperture, increments from Lens.apertureIncrements
*   Shutter speed increments from CameraBody.shutterIncrements
*   Whole-second shutter values displayed in distinct color (color blind mode alternative available)
*   Log Frame captures: aperture, shutter speed, GPS (if roll.gpsEnabled), timestamp — auto-advances frame pointer
*   Widget reflects whichever roll is currently selected as active on the Quick Screen
*   If no active roll, widget displays "No active roll" with a deep link to Roll Setup

> **Note:** Widget cannot capture lens or filter changes — those require Quick Screen (Layer 2).

### 4.2 Layer 2 — Quick Screen

The app's home screen when launched. Designed for one-handed use, all interactive elements in the bottom 60% of the screen.

**Header (tappable)**

*   "active roll · tap to switch" label
*   Roll name with underline and › indicator — tapping opens Switch Roll bottom sheet
*   Frame counter and status indicators (filter count, EV comp) on right

**Switch Roll bottom sheet** — appears when header tapped with multiple loaded rolls. Shows all loaded rolls with radio buttons, frame progress bars, and last shot timestamp. Selecting a roll updates Quick Screen and widget immediately.

**Fields (top to bottom — ordered by frequency of use)**

*   Lens selector — tap to cycle through roll's configured lenses
*   Filter chips (up to 4 MRU) + EV sum + picker button
*   Exposure compensation stepper — ±3 EV in 1/3 stop increments, nullable
*   Frame pointer stepper — manual navigation between slots
*   Aperture stepper — large, centered
*   Shutter speed stepper — large, centered, closest to thumb
*   Note field — optional free text, speech to text button
*   Log Frame — primary action button, full width, anchored to bottom

**Behavior**

*   All fields persist from last logged frame as defaults
*   Logging a frame auto-advances frame pointer to next empty slot
*   Overwriting a previously logged frame triggers confirmation prompt

### 4.3 Layer 3 — Roll Management

**Roll Setup**

*   Film stock selection (first) — name drives roll identity and auto-generated name
*   Frame count — defaults from stock, manually overridable
*   Push/pull actuator — steps through Pull 3 → Pull 2 → Pull 1 → Box → Push 1 → Push 2 → Push 3. ISO updates live. Custom ISO override available as collapsed link below actuator.
*   Film expiry date — optional
*   Kit selector — optional, pre-populates camera body, lenses, filters. All fields editable after.
*   Camera body selection
*   Lens selection — primary required, additional optional. Filtered by mount type.
*   Filter selection — chip pattern, optional
*   GPS capture toggle
*   Roll name — auto-generated from film stock + date, editable
*   Two action buttons: **Create Roll** (unloaded, inventory) and **Create & Load Roll** (active, drives widget)

**Roll List**

*   Tabs: Active / Finished / Archived
*   Active tab groups loaded and unloaded rolls with visual section dividers
*   Unloaded roll cards: dashed border, muted icon, "← swipe to load →" hint
*   Card interactions: tap → journal, swipe either direction → Load Roll confirmation, long press → context menu
*   Long press menu is context-aware by roll state

**Roll Journal View**

*   Bottom bar state-dependent: Unloaded shows Load Roll + Export; Loaded shows Finish Roll + Export
*   Delete available in overflow menu (⋯) only — requires danger confirmation
*   Frame cards: hybrid A+C pattern (filled/empty circle + solid/dashed border)
*   Current frame gets heavier border + "current" label

**Roll Status Transitions**

*   Active (unloaded) → Active (loaded) via Load Roll
*   Active (loaded) → Finished via Finish Roll (irreversible)
*   Finished → Archived
*   Archived → Finished (unarchive)
*   Delete available at any state via overflow menu

### 4.4 Layer 4 — Gear Library

Full CRUD for all equipment and kits. Five tabs: Lenses, Bodies, Filters, Film Stocks, Kits.

All list screens: search bar + sort dropdown (A–Z default), scrollable cards, FAB to add new.

**Sort options by tab:** Lenses adds Mount Type sort; Filters adds Filter Size sort; all tabs support A–Z, Last Used, Recently Added.

**Equipment Types**

*   Camera Body — name, make, model, mount type, format (35mm / half-frame), shutter increments
*   Lens — name, make, focal length (mm), max/min aperture (`f/` prefix entry), aperture increments, filter size (mm, optional)
*   Filter — name, make, type (folksonomy), EV reduction (nullable), filter size (mm, optional — "slot" for square filters)
*   Film Stock — name, make, ISO (box speed), format, default frame count, color type, discontinued flag
*   Kit — name, camera body, lenses (radio button primary), filters (chip pattern)

Kit management (copy, delete) available from Gear Library only. Kit Selector is read-only selection surface.

---

## 5\. Data Model — Entities & Relationships

_Field types and constraints are formalized in the individual entity schema notes._

| Entity | Key Fields | Notes |
| --- | --- | --- |
| CameraBody | id, name, make, model, mountType, format, shutterIncrements, notes | format drives half-frame slot doubling |
| Lens | id, name, make, focalLengthMm, mountType, maxAperture, minAperture, apertureIncrements, filterSizeMm, notes | mountType is source of truth; filterSizeMm nullable |
| Filter | id, name, make, filterType, evReduction, filterSizeMm, notes | filterType folksonomy; filterSizeMm nullable |
| FilmStock | id, name, make, iso, format, defaultFrameCount, colorType, discontinued, notes | iso is box speed only |
| Kit | id, name, cameraBodyId, lastUsedAt, notes | lastUsedAt updated on roll setup from kit |
| KitLens | kitId, lensId, isPrimary | join table |
| KitFilter | kitId, filterId | join table |
| Roll | id, name, filmStockId, cameraBodyId, pushPull, ratedISO, filmExpiryDate, totalExposures, isLoaded, gpsEnabled, status, loadedAt, finishedAt, lastExportedAt, notes | pushPull range -3 to +3; ratedISO derived |
| RollLens | rollId, lensId, isPrimary | join table |
| RollFilter | rollId, filterId | join table |
| Frame | id, rollId, frameNumber, isLogged, loggedAt, aperture, shutterSpeed, lensId, exposureCompensation, lat, lng, notes | frameNumber immutable |
| FrameFilter | frameId, filterId | join table |

---

## 6\. Export

Export triggered from Roll Journal View. Delivered via Android share sheet — no direct integration with cloud services. `Roll.lastExportedAt` updated on successful export.

| Format | Use Case | v1.0 |
| --- | --- | --- |
| CSV | Spreadsheet import, general data work | Yes |
| JSON | Programmatic use, custom tooling | Yes |
| Plain text | Human-readable log, printing | Yes |
| Google Sheets | Direct sync | v1.1 |
| ExifTool command file | Batch EXIF injection | v1.1 |

CSV and JSON column order follows frame number ascending. Coordinates as decimal degrees. Timestamps as ISO 8601.

---

## 7\. Settings

Stored in SharedPreferences — not SQLite. App configuration only.

**Shooting Defaults**

*   Default extra frames per roll (integer, default 2)
*   GPS precision (high / battery saver)
*   Default export format

**Appearance**

*   App theme (light / dark / system)
*   Accessible color mode (off by default — alternative indicators for color-coded UI elements)

**Data & Backup**

*   Export backup (.DETENT file via share sheet)
*   Restore from backup (destructive — requires confirmation)

**Widget**

*   Widget setup instructions

**Onboarding**

*   Re-run introduction

**Support**

*   Tip jar (Ko-fi link or Play in-app purchase)

**About**

*   Version
*   Privacy policy
*   Open source licenses

---

## 8\. Open Questions

| # | Question | Impact | Status |
| --- | --- | --- | --- |
| OQ-1 | App name — DETENT is working title only | Branding, Play Store | Open — revisit before public release |

---

## 9\. Resolved Decisions

| # | Decision | Resolution |
| --- | --- | --- |
| OQ-2 (half-frame) | Half-frame configured per camera body or per roll? | Per camera body — cameras are inherently half-frame or they're not. Roll slot count doubles automatically at load. |
| OQ-2 (aperture increments) | Aperture increment granularity on CameraBody or Lens? | On Lens — the aperture ring and its detents are physically part of the lens mechanism. Shutter increments remain on CameraBody. |
| OQ-3 | Filter EV auto-adjust exposure on quick screen? | No — this is a shot log, not a light meter. EV reduction is informational only. |
| OQ-4 | Medium format frame counts — same slot model? | Deferred to v1.1. |
| OQ-5 | GPS — continuous track or capture on log only? | Capture on log only. Toggle at roll setup. Standard Android permission flow. |
| OQ-6 | Kits / favorite gear sets | Confirmed v1.0. Kit entity with KitLens and KitFilter join tables. |
| OQ-7 | Multiple loaded rolls — how does widget know which to show? | User selects active roll via tappable Quick Screen header. Widget reflects selection. Fallback: single loaded roll is automatic. |
| OQ-8 | Roll loaded state — how distinguished from unloaded? | isLoaded boolean on Roll entity. Create Roll = unloaded. Create & Load Roll = loaded. |
| OQ-9 | Push/pull — separate field or derived from ratedISO? | Explicit pushPull integer field (-3 to +3) on Roll. ratedISO derived. Custom ISO override available. |

---

_Data model schema notes, screen inventory, DAO notes, and design decisions document are companion documents to this PRD._

