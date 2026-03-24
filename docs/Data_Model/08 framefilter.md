# 08 framefilter
## Entity — FrameFilter

## Fields

| Field | Type | Nullable | Notes |
| --- | --- | --- | --- |
| frameId | integer | no | foreign key → Frame |
| filterId | integer | no | foreign key → Filter |

## Notes

*   Records which filters were **active** at the time of a specific exposure.
*   Filters stack — a frame can have multiple FrameFilter records.
*   `filterId` must reference a filter present in the parent roll's RollFilter table. Enforced at the application layer.
*   Active filters for frame N+1 are pre-populated from frame N's FrameFilter records. Frame 1 defaults to no active filters.
*   Deleting a FrameFilter record (toggling a filter off) is a normal editing operation — no soft delete needed.


