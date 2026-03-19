# 01 camerabody
## Entity — CameraBody

## Fields

| Field | Type | Nullable | Notes |
| --- | --- | --- | --- |
| id | integer | no | auto-generated primary key |
| name | string | no | user display name, e.g. "My AE-1" |
| make | string | no | e.g. "Canon" |
| model | string | no | e.g. "AE-1" |
| mountType | string | no | picked from existing Lens mount values — Lens is source of truth |
| format | enum | no | `35mm \| half_frame` — `medium_format` reserved for v1.1 |
| shutterIncrements | enum | no | `full \| half \| third` |
| notes | string | yes | free text, e.g. "meter needs calibration" |

## Notes

*   `mountType` is not a locked enum — the vocabulary is defined by the user's Lens library. CameraBody picks from existing mount type strings already entered on lenses. Lens is always entered first.
*   `format = half_frame` doubles `totalExposures` at roll creation, including the extra frames multiplier. A 36-exposure roll with +2 extra frames becomes 76 slots on a half-frame body.
*   `shutterIncrements` controls shutter speed stepper behavior in the widget and quick screen when this body is active on a roll. Shutter mechanism is physically part of the camera body.
*   Aperture increment granularity has moved to the Lens entity — the aperture ring and its detents are physically part of the lens, not the body.

## Relationships

*   One CameraBody → many Rolls
*   One CameraBody → many Kits