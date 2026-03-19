# DAO 01 rolldao
## DAO — RollDao

## Responsibility

All database operations involving the Roll entity. Status transitions, export timestamp, and the compound query that drives the Quick Screen and Journal.

## Methods

### Reads

**`getActiveRoll(): RollWithDetails?`**

*   Returns the roll where `status = active`, with its lenses, filters, and all frame slots
*   Returns null if no active roll exists
*   Called on app launch and whenever the Quick Screen or Journal needs to load
*   Nullable return — UI handles the three cases: no rolls, rolls but none active, active roll found

**`getRollById(rollId: Int): RollWithDetails`**

*   Returns a specific roll with its lenses, filters, and frames
*   Used by Roll Journal View — navigated to with rollId only, loads its own data

**`getRollForExport(rollId: Int): RollExport`**

*   Returns a fully denormalized roll — all frames with lens names, filter names, film stock name, camera body name as human-readable strings rather than foreign key IDs
*   Used exclusively by the export flow

**`searchRolls(query: String, status: RollStatus?): List<Roll>`**

*   Returns rolls matching query string, optionally filtered by status tab
*   Used by Roll List screen

### Writes

**`insertRoll(roll: Roll): Long`**

*   Inserts a new roll record, returns generated ID
*   Called as part of the roll creation transaction — not called in isolation

**`finishRoll(rollId: Int, finishedAt: Long)`**

*   Sets `status = finished`, sets `finishedAt` timestamp
*   Only valid on active rolls — UI enforces, not database

**`archiveRoll(rollId: Int)`**

*   Sets `status = archived`
*   Only valid on finished rolls — UI enforces, not database

**`unarchiveRoll(rollId: Int)`**

*   Sets `status = finished`
*   Returns roll to finished state — cannot return to active

**`updateLastExported(rollId: Int, timestamp: Long)`**

*   Sets `lastExportedAt` to current timestamp
*   Called after successful export — not part of a transaction, failure is non-critical

## Roll Creation Transaction

Roll creation is a single atomic transaction combining multiple DAOs:

1.  `RollDao.insertRoll(roll)` — insert roll, capture generated rollId
2.  `RollLensDao.insertRollLens(rollLens)` × n — insert each lens association
3.  `RollFilterDao.insertRollFilter(rollFilter)` × n — insert each filter association
4.  `FrameDao.insertFrames(frames)` — bulk insert all pre-generated frame slots

All four steps succeed or none do.

## RollWithDetails Structure

```
RollWithDetails
├── Roll
├── List<RollLensWithLens>  (each RollLens record + its Lens details)
├── List<RollFilterWithFilter>  (each RollFilter record + its Filter details)
└── List<Frame>  (all pre-generated slots, ordered by frameNumber)
```