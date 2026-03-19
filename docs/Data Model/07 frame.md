# 07 frame
## Entity — Frame

## Fields

| Field | Type | Nullable | Notes |
| --- | --- | --- | --- |
| id | integer | no | auto-generated primary key |
| rollId | integer | no | foreign key → Roll |
| frameNumber | integer | no | immutable — 1 through Roll.totalExposures, set at roll creation |
| isLogged | boolean | no | default false — explicit flag, decoupled from loggedAt |
| loggedAt | timestamp | yes | when the frame was logged — null if unlogged |
| aperture | string | yes | canonical string e.g. "f/5.6" — null if unlogged |
| shutterSpeed | string | yes | canonical string e.g. "1/125" — null if unlogged |
| lensId | integer | yes | foreign key → Lens, must be in roll's RollLens — null if unlogged |
| exposureCompensation | decimal | yes | e.g. -1.0, +0.5 — in EV stops, null if not applied |
| lat | decimal | yes | GPS latitude, captured automatically on log |
| lng | decimal | yes | GPS longitude, captured automatically on log |
| notes | string | yes | free text — speech to text input planned for UX layer |

## Notes

*   All frames are pre-created at roll load. An unlogged frame has `isLogged = false` and null values for all exposure fields.
*   `isLogged` and `loggedAt` are decoupled deliberately. This allows retroactive correction of a missed frame — you can mark a frame as logged and fill in the data without fabricating a timestamp.
*   `frameNumber` is immutable and is the canonical ordering key. Never changes after creation.
*   `aperture` valid values are constrained by the active Lens (`minAperture` to `maxAperture`) and step increments from CameraBody (`apertureIncrements`). Enforced by the UI stepper, not the database.
*   `shutterSpeed` valid values are constrained by CameraBody (`shutterIncrements`). Enforced by the UI stepper.
*   Active filters are stored in the FrameFilter join table, not here. Filters stack.

## Relationships

*   One Frame → one Roll
*   One Frame → one Lens (nullable)
*   One Frame → many Filters via FrameFilter