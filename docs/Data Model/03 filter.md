# 03 filter
## Entity — Filter

## Fields

| Field | Type | Nullable | Notes |
| --- | --- | --- | --- |
| id | integer | no | auto-generated primary key |
| name | string | no | user display name, e.g. "Hoya HMC Yellow K2" |
| make | string | no | e.g. "Hoya" |
| filterType | string | no | folksonomy — picked from existing user-defined values |
| evReduction | decimal | yes | stops of light reduction — null for filters with no meaningful reduction (e.g. UV, skylight) |
| filterSizeMm | integer | yes | screw-mount thread diameter in mm. Null for slot/square filters or step-up/step-down rings. |
| notes | string | yes | free text, e.g. "Cokin P series slot filter" |

## Input Patterns (Gear Detail Screen)

*   `filterType` uses autocomplete — existing values appear as suggestions, "+ add new" for first entry. Folksonomy.
*   `filterSizeMm` entered as plain number + `mm` suffix. Labeled "optional" in UI. Use notes field for slot filter system details (e.g. "Cokin P series").
*   `evReduction` entered as plain decimal. Labeled "optional."

## Notes

*   `filterType` is user-defined vocabulary. Suggested starting values: `color`, `ND`, `polarizer`, `graduated ND`, `UV`, `infrared` — but not enforced.
*   `evReduction` is nullable because some filters (UV, skylight 1A) do not meaningfully reduce exposure.
*   `filterSizeMm` null means the filter works with any lens regardless of thread size — slot filters, gel filters, etc. These always appear in filter pickers regardless of lens filter size compatibility filtering.
*   Filters stack — a frame can have multiple active filters via the FrameFilter join table.
*   Filter EV sum is displayed on the Quick Screen and Frame Detail filter row — sum of evReduction for all active filters with non-null values. A `~` prefix appears if any active filter has null evReduction.

## Relationships

*   One Filter → many RollFilter
*   One Filter → many FrameFilter
*   One Filter → many KitFilter