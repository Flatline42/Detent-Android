# 06 rolllens rollfilter
## Entity — RollLens

## Fields

| Field | Type | Nullable | Notes |
| --- | --- | --- | --- |
| rollId | integer | no | foreign key → Roll |
| lensId | integer | no | foreign key → Lens |
| isPrimary | boolean | no | exactly one RollLens per roll must be true |

## Notes

*   Defines which lenses are **available** during a roll — appears in the lens picker on the quick screen.
*   `isPrimary` sets the default lens for Frame 1. After that, active lens inherits from the previous logged frame.
*   Insertion order determines display order in the picker — no separate sort field needed.
*   Exactly one `isPrimary = true` per roll is a data integrity constraint.

---

## Entity — RollFilter

## Fields

| Field | Type | Nullable | Notes |
| --- | --- | --- | --- |
| rollId | integer | no | foreign key → Roll |
| filterId | integer | no | foreign key → Filter |

## Notes

*   Defines which filters are **available** during a roll — appears in the filter picker on the quick screen.
*   No `isPrimary` or `inUse` flag — active filters inherit from the previous logged frame via FrameFilter.
*   Frame 1 defaults to no active filters.
*   Insertion order determines display order in the picker.

## Further ideas

*   Kits (favorites). Groups of bodies/lenses/filters assembled into favorites that can import into a roll log on setup. Gear can still be added or removed before logging the roll.  
      
    Notes on this idea-

The data model addition is clean — one new entity:

**Kit** (or **CameraBag**, but Kit is shorter)

*   `id`
*   `name` — e.g. "Street Kit", "Yosemite Bag"
*   `cameraBodyId` — one body per kit
*   `notes` — nullable

Plus two join tables:

*   **KitLens** — `kitId`, `lensId`, `isPrimary`
*   **KitFilter** — `kitId`, `filterId`

Same pattern as RollLens and RollFilter, which means the code is basically already designed.

At roll setup, Layer 3, you get a new first step — "load from kit?" If yes, body, lenses and filters all pre-populate from the kit. You can still add or remove gear before hitting Load Roll. If no, you build manually like before.

It doesn't change anything downstream — the Roll still gets its own RollLens and RollFilter records. The kit is just a template that pre-fills roll setup, not a permanent link. You could load the same kit onto ten different rolls and edit each one independently.


