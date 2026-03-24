# 10 kitlens kitfilter
## Entity — KitLens

## Fields

| Field | Type | Nullable | Notes |
| --- | --- | --- | --- |
| kitId | integer | no | foreign key → Kit |
| lensId | integer | no | foreign key → Lens |
| isPrimary | boolean | no | exactly one KitLens per kit must be true |

## Notes

*   Defines which lenses are included in a kit and which is the default starting lens.
*   `isPrimary` sets the lens that populates as the primary/default lens when roll setup is pre-filled from this kit.
*   Exactly one `isPrimary = true` per kit is a data integrity constraint.
*   Insertion order determines display order in the lens picker at roll setup.
*   Same pattern as RollLens — kit is a pre-roll template, roll is the live record.

---

## Entity — KitFilter

## Fields

| Field | Type | Nullable | Notes |
| --- | --- | --- | --- |
| kitId | integer | no | foreign key → Kit |
| filterId | integer | no | foreign key → Filter |

## Notes

*   Defines which filters are included in a kit — i.e. what's in the bag for a typical outing with this kit.
*   No isPrimary or inUse flag — same reasoning as RollFilter. Active filters inherit from the previous logged frame.
*   Insertion order determines display order in the filter picker at roll setup.
*   Same pattern as RollFilter — kit is a pre-roll template, roll is the live record.


