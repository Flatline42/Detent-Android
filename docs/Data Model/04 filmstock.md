# 04 filmstock
## Entity — FilmStock

## Fields

| Field | Type | Nullable | Notes |
| --- | --- | --- | --- |
| id | integer | no | auto-generated primary key |
| name | string | no | e.g. "HP5 Plus" |
| make | string | no | e.g. "Ilford" |
| iso | integer | no | box speed only |
| format | enum | no | `35mm` only in v1.0 — `medium_format` reserved for v1.1 |
| defaultFrameCount | integer | no | e.g. 36, 24, 12 — editable at roll creation |
| colorType | enum | no | `color_negative \| bw_negative \| slide` |
| discontinued | boolean | no | default false — hides from active pickers without deleting historical data |
| notes | string | yes | free text |

## Notes

*   `iso` is box speed only. Rated ISO (push/pull) lives on Roll, not here. FilmStock is a definition, not a shooting record.
*   `discontinued` allows stocks like Kodak Portra 160NC to persist in historical roll data without cluttering the active pickers.
*   `defaultFrameCount` is a starting point — editable at roll creation for bulk-loaded or short-loaded rolls.
*   `format` enum includes `medium_format` as a reserved value for v1.1. Medium format is explicitly out of scope for v1.0 and should not appear in the UI.
*   Development logging is explicitly out of scope — use Massive Dev Chart. This is a shot log, not a dev log.

## Relationships

*   One FilmStock → many Rolls