# 05 roll
## Entity — Roll

## Fields

| Field | Type | Nullable | Notes |
| --- | --- | --- | --- |
| id | integer | no | auto-generated primary key |
| name | string | no | auto-generated from film stock + date, user editable |
| filmStockId | integer | no | foreign key → FilmStock |
| cameraBodyId | integer | no | foreign key → CameraBody |
| pushPull | integer | no | default 0 (box speed). Range -3 to +3. -1 = pull 1 stop, +1 = push 1 stop. |
| ratedISO | integer | no | derived from FilmStock.iso + pushPull math. Custom override available. |
| filmExpiryDate | date | yes | expiry date of the specific physical roll loaded |
| totalExposures | integer | no | locked at creation — see calculation note below |
| isLoaded | boolean | no | default false. True = roll is in a camera and drives widget/quick screen. |
| gpsEnabled | boolean | no | default false — if true, lat/lng captured on each frame log |
| status | enum | no | `active \| finished \| archived` |
| loadedAt | timestamp | no | when the roll was created |
| finishedAt | timestamp | yes | when the roll was marked finished — null until then |
| lastExportedAt | timestamp | yes | when the roll was last exported — null until first export |
| notes | string | yes | free text |

## totalExposures Calculation

Determined at roll creation, immutable after that.

*   **Standard body:** `defaultFrameCount + extraFrames` (extraFrames from app setting, default 2)
*   **Half-frame body:** `(defaultFrameCount + extraFrames) × 2`

Example: 36-exposure roll, +2 extra frames, half-frame body → `(36 + 2) × 2 = 76 slots`

## Push/Pull and ratedISO

*   `pushPull` is the authoritative field. Range -3 to +3, default 0.
*   `ratedISO` is derived: `FilmStock.iso × 2^pushPull`. Example: HP5 (ISO 400) pushed 1 stop → ratedISO 800.
*   Custom ISO override: user can manually set ratedISO to a non-standard value (e.g. 350). In this case pushPull is set to null to indicate a custom rating.
*   Display: pushPull = 0 shows "box speed", pushPull = 1 shows "push 1 · ISO 800", custom shows just the ISO value.

## Roll Status and Loaded State

*   `status` and `isLoaded` are independent fields.
*   A roll can be `status = active` and `isLoaded = false` (purchased, prepared, sitting in inventory).
*   A roll can be `status = active` and `isLoaded = true` (in a camera, actively shooting).
*   Multiple rolls can have `status = active` simultaneously — one per camera body is the typical case.
*   Multiple rolls can have `isLoaded = true` simultaneously — one per camera body.
*   Only one roll is the "active roll" for the widget and quick screen at any time — selected by the user via the Quick Screen header switcher.

## Roll Creation Actions

*   **Create Roll** — sets `status = active`, `isLoaded = false`. Roll appears in inventory but does not drive widget.
*   **Create & Load Roll** — sets `status = active`, `isLoaded = true`. Roll is immediately the active roll for widget and quick screen (if no other loaded roll was already selected).

## Notes

*   All Frame slots are created in bulk at roll creation — `frameNumber` 1 through `totalExposures`. No frames are added or removed after creation.
*   `filmExpiryDate` is for the physical roll, not the stock definition. Multiple rolls of the same stock can have different expiry dates.
*   `gpsEnabled` is set at roll creation. If enabled, the app checks for location permissions and requests them via the standard Android permission flow if not already granted. If denied, `gpsEnabled` is set back to false gracefully.
*   `lastExportedAt` is updated after any successful export. Not part of a transaction — failure is non-critical.

## Relationships

*   One Roll → one FilmStock
*   One Roll → one CameraBody
*   One Roll → many RollLens (one isPrimary)
*   One Roll → many RollFilter
*   One Roll → many Frames


