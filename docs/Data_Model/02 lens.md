# 02 lens
## Entity — Lens

## Fields

| Field | Type | Nullable | Notes |
| --- | --- | --- | --- |
| id | integer | no | auto-generated primary key |
| name | string | no | user display name, e.g. "FD 50mm f/1.4" |
| make | string | no | e.g. "Canon" |
| focalLengthMm | integer | no | e.g. 50 |
| mountType | string | no | source of truth for mount vocabulary, e.g. "Canon FD" |
| maxAperture | decimal | no | e.g. 1.4 — widest aperture, upper bound for stepper |
| minAperture | decimal | no | e.g. 16 — smallest aperture, lower bound for stepper |
| apertureIncrements | enum | no | `full \| half \| third` — physical detents of this lens's aperture ring |
| filterSizeMm | integer | yes | e.g. 52 — screw-mount filter thread diameter. Null if no standard filter thread. |
| notes | string | yes | free text, e.g. "soft at f/1.4, vignettes wide open" |

## Input Patterns (Gear Detail Screen)

*   `maxAperture` and `minAperture` entered as `f/` prefix + plain number. User types `1.4`, displays as `f/1.4`.
*   `filterSizeMm` entered as plain number + `mm` suffix. Labeled "optional" in UI.
*   `mountType` uses autocomplete — existing values appear as suggestions, "+ add new" for first entry.
*   `apertureIncrements` uses a three-option dropdown (full / half / third).

## Notes

*   `mountType` is entered as free text on the lens. This is the source of truth — camera body mount type picker pulls from existing values here. Keeps vocabulary consistent without maintaining a global enum.
*   `maxAperture` and `minAperture` bound the aperture stepper in the widget and quick screen when this lens is active.
*   `apertureIncrements` reflects the physical aperture ring detents on this specific lens. The aperture ring is part of the lens mechanism, not the camera body. Different lenses on the same body can have different aperture step granularity.
*   `filterSizeMm` enables optional "compatible only" filter filtering at roll setup and kit building. When a lens has a value, the filter picker can show only matching screw-mount filters plus null-size filters (slot/square). Null-size filters are always shown regardless.
*   Shutter speed increments are on CameraBody, not here — the shutter mechanism is physically in the body.
*   Stepless aperture (cine lenses) deferred to v1.1 — would require a continuous value input rather than a discrete stepper.
*   Zoom lenses deferred to v1.1 — `focalLengthMm` is a single value in v1.0. Future migration: rename to `minFocalLengthMm`, add `maxFocalLengthMm` and `lensType` enum.

## Relationships

*   One Lens → many RollLens
*   One Lens → many KitLens
*   One Lens → many Frames (as active lens at time of exposure)


