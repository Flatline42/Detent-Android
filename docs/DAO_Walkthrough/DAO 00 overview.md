# DAO 00 overview
## DAO Layer — Overview

## What is a DAO?

A DAO (Data Access Object) is an interface that defines how the app talks to the database. It translates between the UI's Kotlin objects and SQLite's raw table data. Room generates the implementation at compile time from annotated method definitions — you write the intent, Room handles the mechanics.

The UI never touches SQL directly. It calls a DAO method, gets back a Kotlin object, and works with that.

## Storage Layers

DETENT uses two storage mechanisms:

| Layer | Technology | Used For |
| --- | --- | --- |
| Structured data | Room / SQLite | All photography data — rolls, frames, gear |
| App configuration | SharedPreferences | Onboarding flag, settings, preferences |

Settings screens have no DAO methods — they use SharedPreferences directly.

## DAO List

| DAO | Responsibility |
| --- | --- |
| RollDao | Roll CRUD, status transitions, export timestamp |
| FrameDao | Frame reads, bulk insert at roll creation, frame updates |
| FrameFilterDao | Filter associations per frame |
| CameraBodyDao | Camera body CRUD, mount type folksonomy |
| LensDao | Lens CRUD, mount type filtering |
| FilterDao | Filter CRUD, filter type folksonomy |
| FilmStockDao | Film stock CRUD, search |
| KitDao | Kit CRUD with transactional lens/filter management |
| RollLensDao | Roll-lens associations (called within roll creation transaction) |
| RollFilterDao | Roll-filter associations (called within roll creation transaction) |
| KitLensDao | Kit-lens associations (called within kit save transaction) |
| KitFilterDao | Kit-filter associations (called within kit save transaction) |

## Key Patterns

**Compound queries with relations** — screens that need an entity plus its associated records (e.g. Roll + its lenses + its filters + its frames) use Room's relation system. One database trip returns a fully populated object tree.

**Pass IDs through navigation, not objects** — screens receive only the ID(s) they need via navigation arguments and load their own data via DAO. This keeps screens self-contained and avoids Android's object size limits on navigation.

**Transactions** — any operation that writes to multiple tables must be wrapped in a transaction. Either all writes succeed or none do. Used for: logging a frame (Frame + FrameFilter delta), creating a roll (Roll + RollLens + RollFilter + bulk Frame insert), saving a kit (Kit + KitLens + KitFilter wholesale replace).

**Search-as-you-type** — all gear list screens use a search query rather than fetching all records. Consistent pattern across all five gear entity types.

**Folksonomy queries** — mount types and filter types are user-defined vocabulary. Distinct value queries populate pickers without maintaining global enums.


