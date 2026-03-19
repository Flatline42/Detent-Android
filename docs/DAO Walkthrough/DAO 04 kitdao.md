# DAO 04 kitdao
## DAO — KitDao

## Responsibility

Kit CRUD with transactional lens and filter management. Kit saves use a wholesale replace strategy for associated lenses and filters — simpler than delta updates since kits are edited at home deliberately, not under field time pressure.

## Methods

### Reads

**`searchKits(query: String): List<Kit>`**

*   Returns kits where name contains query string
*   Used by kit list screen and kit selector in roll setup

**`getKitById(id: Int): Kit`**

*   Returns a single kit record
*   Used when only the kit metadata is needed

**`getKitWithDetails(id: Int): KitWithDetails`**

*   Returns a kit with its associated lenses and filters
*   Used by kit detail/edit screen and roll setup pre-populate flow

### Writes

**`insertKit(kit: Kit): Long`**

*   Inserts a new kit record, returns generated ID
*   Called as part of kit creation transaction

**`updateKit(kit: Kit)`**

*   Updates kit record fields only
*   Called as part of kit save transaction

**`deleteKit(kit: Kit)`**

*   Deletes kit record
*   KitLens and KitFilter records cascade delete

## Kit Save Transaction

Saving a kit (create or edit) is a single atomic transaction:

1.  `KitDao.insertKit(kit)` or `KitDao.updateKit(kit)`
2.  `KitLensDao.deleteAllKitLenses(kitId)` — clear existing lens associations
3.  `KitLensDao.insertKitLens(kitLens)` × n — insert current lens set
4.  `KitFilterDao.deleteAllKitFilters(kitId)` — clear existing filter associations
5.  `KitFilterDao.insertKitFilter(kitFilter)` × n — insert current filter set

Wholesale replace chosen over delta — kits are edited deliberately, not in the field. Simpler implementation, no meaningful performance difference at this scale.

## KitWithDetails Structure

```
KitWithDetails
├── Kit
├── CameraBody
├── List<KitLensWithLens>  (each KitLens record + its Lens details, isPrimary first)
└── List<KitFilterWithFilter>  (each KitFilter record + its Filter details)
```

---

## DAO — Join Table DAOs

These DAOs are called only within transactions. They are never called directly from the UI layer.

## RollLensDao

**`insertRollLens(rollLens: RollLens)`**

*   Called within roll creation transaction

## RollFilterDao

**`insertRollFilter(rollFilter: RollFilter)`**

*   Called within roll creation transaction

## KitLensDao

**`insertKitLens(kitLens: KitLens)`**

*   Called within kit save transaction

**`deleteAllKitLenses(kitId: Int)`**

*   Deletes all KitLens records for a kit
*   Called within kit save transaction before reinserting current lens set

## KitFilterDao

**`insertKitFilter(kitFilter: KitFilter)`**

*   Called within kit save transaction

**`deleteAllKitFilters(kitId: Int)`**

*   Deletes all KitFilter records for a kit
*   Called within kit save transaction before reinserting current filter set