# Roadmap for v1.1
## DETENT — v1.1 Roadmap

## UI / Interaction

### Horizontal Scroll Wheel for Aperture and Shutter Speed

Replace +/− steppers with a horizontal scroll wheel (HorizontalPager with snap). Swipe left/right maps to physical dial rotation. Adjacent values peek left and right of center value. Direction linkable to camera body convention (see below).

### Aperture Stepper Direction per Camera Body

Add `apertureDirection` enum to CameraBody entity (`clockwise_open` | `counterclockwise_open`). QuickScreen and widget read direction from active roll's camera body and flip stepper/swipe direction accordingly. Eliminates need for a global setting. Requires Room migration.

### Persistent Lock Screen Notification

Persistent notification showing active roll name and current frame number with a "Log Frame" action button. Visible on lock screen without unlocking. Replaces the impossible-on-Android lock screen widget concept. Toggle in Settings or Quick Screen. Uses NotificationCompat with action buttons. Direction linkable to camera body convention.

---

## Features

### Darkroom Safe / Night Vision Color Mode

Pure red (#FF0000) on true black (#000000) color theme. Preserves rhodopsin adapted vision. Won't fog paper in a darkroom environment. Toggle in Settings under Appearance. Deferred from v1.0 — niche but passionate use case.

### Google Sheets Live Sync

Direct export to a Google Sheets spreadsheet via Sheets API. Deferred from v1.0.

### ExifTool Command File Export

Generate a .bat / shell script for batch EXIF injection into scanned negatives. Deferred from v1.0.

### Medium Format Support

`medium_format` enum value reserved in CameraBody. UI excluded in v1.0. Same slot model as 35mm — just different frame counts.

### Zoom Lens Support

`focalLengthMm` is single value in v1.0. Migration: rename to `minFocalLengthMm`, add `maxFocalLengthMm` and `lensType` enum. Per-frame focal length logging.

### Stepless Aperture / Cine Lens Support

Continuous value input rather than discrete stepper. Required for cine lenses with no click stops.

---

## Data Model Changes Required

| Feature | Change |
| --- | --- |
| Aperture direction | Add `apertureDirection` enum to CameraBody |
| Zoom lenses | Rename `focalLengthMm`, add `maxFocalLengthMm`, add `lensType` |
| All above | Room database migration required |

---

Before public release — implement Room migration strategy. All schema changes require Migration objects. Test upgrade path from v1.0 schema.

