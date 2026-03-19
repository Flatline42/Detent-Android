# 00 data model overview
## Data Model — Overview

## Entities

| Entity | Purpose |
| --- | --- |
| CameraBody | Physical camera bodies in gear library |
| Lens | Lenses in gear library |
| Filter | Filters in gear library |
| FilmStock | Film stock definitions in gear library |
| Kit | Named gear sets for fast roll setup |
| KitLens | Lenses included in a kit |
| KitFilter | Filters included in a kit |
| Roll | A loaded or unloaded roll of film — the journal |
| RollLens | Lenses available during a roll |
| RollFilter | Filters available during a roll |
| Frame | Individual exposure slot on a roll |
| FrameFilter | Filters active on a specific frame |

## Relationships

*   One **CameraBody** → many **Rolls**
*   One **CameraBody** → many **Kits**
*   One **Kit** → many **Lenses** via KitLens (one marked isPrimary)
*   One **Kit** → many **Filters** via KitFilter
*   One **Roll** → one **FilmStock**, one **CameraBody**
*   One **Roll** → many **Lenses** via RollLens (one marked isPrimary)
*   One **Roll** → many **Filters** via RollFilter
*   One **Roll** → many **Frames** (created in bulk at roll creation, count = totalExposures)
*   One **Frame** → one **Lens** (from roll's available lenses)
*   One **Frame** → zero or more **Filters** via FrameFilter

## Frame State Inheritance

The widget and quick screen always pre-populate from the last logged frame. No explicit "current state" flags are needed — active lens and active filters are implicitly whatever the previous frame recorded.

*   **Frame 1 lens** — defaults to the RollLens where isPrimary = true
*   **Frame 1 filters** — defaults to none active

## Key Constraints

*   `frameNumber` is immutable — set at roll creation, never changes
*   `totalExposures` is locked at roll creation
*   Mount type vocabulary is user-defined — Lens is the source of truth, CameraBody picks from existing lens mount values
*   Filter type vocabulary is user-defined (folksonomy)
*   ISO is a roll-level concern for analog — box speed lives on FilmStock, pushPull and ratedISO live on Roll, no per-frame ISO in v1.0
*   `pushPull` is an integer in range -3 to +3 (default 0 = box speed). `ratedISO` is derived from FilmStock.iso + pushPull math, with a custom override escape hatch
*   Half-frame is a property of CameraBody (`format = half_frame`) — doubles totalExposures automatically at roll creation
*   Aperture increment granularity is a property of Lens — the aperture ring and its detents are physically part of the lens mechanism
*   Shutter speed increment granularity is a property of CameraBody — the shutter mechanism is physically in the body
*   `isLoaded` distinguishes rolls in active use from inventory rolls. Multiple rolls can have `status = active` but only loaded rolls drive the widget and quick screen
*   The widget and quick screen reflect whichever loaded roll the user has selected as active — controlled via the tappable Quick Screen header
*   Kit is a template only — it pre-populates roll setup but has no direct relationship to Roll after that point
*   `filterSizeMm` on both Lens and Filter is nullable — null indicates no fixed thread size (slot filters, rear gel filters, etc.)

## Deferred to v1.1

*   Medium format support (`medium_format` enum value reserved but not active in v1.0)
*   Zoom lens support (focal length range, per-frame focal length)
*   Stepless aperture / cine lens support
*   Google Sheets live sync
*   ExifTool command file export

## Explicitly Out of Scope

*   Reciprocity failure data — use dedicated calculators
*   Development logging — use Massive Dev Chart
*   Large format / per-frame ISO override