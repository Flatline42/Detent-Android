# DAO 02 framedao
## DAO — FrameDao

## Responsibility

Reading and writing individual frame records. Bulk insert at roll creation. All frame writes are wrapped in a transaction with FrameFilterDao operations.

## Methods

### Reads

**`getFramesForRoll(rollId: Int): List<FrameWithDetails>`**

*   Returns all frames for a roll ordered by frameNumber ascending
*   Each frame includes its associated Lens and active Filters
*   Used by Roll Journal View

**`getFrameById(frameId: Int): FrameWithDetails`**

*   Returns a single frame with its Lens and active Filters
*   Used by Frame Detail / Edit screen — navigated to with frameId, loads its own data

### Writes

**`insertFrames(frames: List<Frame>)`**

*   Bulk inserts all pre-generated frame slots for a new roll
*   All frames created with `isLogged = false`, all exposure fields null
*   Called as part of roll creation transaction — not called in isolation

**`updateFrame(frame: Frame)`**

*   Updates all fields on an existing frame record
*   Called as part of the log frame transaction alongside FrameFilterDao operations

## Log Frame Transaction

Logging a frame is a single atomic transaction:

1.  `FrameDao.updateFrame(frame)` — update frame record with exposure data, isLogged, loggedAt
2.  `FrameFilterDao.insertFrameFilter(frameFilter)` × n — insert newly toggled-on filters
3.  `FrameFilterDao.deleteFrameFilter(frameId, filterId)` × n — remove toggled-off filters

Delta only — only filter changes are written, not a wholesale replace. All three steps succeed or none do.

## FrameWithDetails Structure

```
FrameWithDetails
├── Frame
├── Lens?  (nullable — null if frame is unlogged)
└── List<Filter>  (active filters via FrameFilter join)
```

---

## DAO — FrameFilterDao

## Responsibility

Managing filter associations per frame. Always called within the log frame transaction — never in isolation.

## Methods

**`insertFrameFilter(frameFilter: FrameFilter)`**

*   Inserts one filter association for a frame
*   Called when a filter is toggled on during frame logging

**`deleteFrameFilter(frameId: Int, filterId: Int)`**

*   Removes one filter association from a frame
*   Called when a filter is toggled off during frame logging