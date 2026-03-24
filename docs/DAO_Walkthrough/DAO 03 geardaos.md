# DAO 03 geardaos
## DAO — Gear DAOs

All gear DAOs follow an identical pattern: search, get by ID, insert, update, delete. Each also has one or two entity-specific queries for folksonomy pickers or compatibility filtering.

---

## CameraBodyDao

**`searchCameraBodies(query: String): List<CameraBody>`**

*   Returns camera bodies where name or make contains query string
*   Used by camera body list screen and roll setup body picker

**`getCameraBodyById(id: Int): CameraBody`**

*   Returns a single camera body record
*   Used by camera body detail/edit screen

**`insertCameraBody(cameraBody: CameraBody)`** **`updateCameraBody(cameraBody: CameraBody)`** **`deleteCameraBody(cameraBody: CameraBody)`**

**`getDistinctMountTypes(): List<String>`**

*   Returns all distinct mountType values from the Lens table
*   Used to populate the mount type picker on camera body entry — Lens is source of truth for mount vocabulary

---

## LensDao

**`searchLenses(query: String): List<Lens>`**

*   Returns lenses where name, make, or focalLengthMm contains query string

**`getLensById(id: Int): Lens`**

**`insertLens(lens: Lens)`** **`updateLens(lens: Lens)`** **`deleteLens(lens: Lens)`**

**`getLensesByMountType(mountType: String): List<Lens>`**

*   Returns all lenses matching a specific mount type
*   Used by roll setup to filter available lenses to those compatible with the selected camera body

---

## FilterDao

**`searchFilters(query: String): List<Filter>`**

*   Returns filters where name, make, or filterType contains query string

**`getFilterById(id: Int): Filter`**

**`insertFilter(filter: Filter)`** **`updateFilter(filter: Filter)`** **`deleteFilter(filter: Filter)`**

**`getDistinctFilterTypes(): List<String>`**

*   Returns all distinct filterType values from the Filter table
*   Used to populate the filter type picker — folksonomy pattern, user-defined vocabulary

---

## FilmStockDao

**`searchFilmStocks(query: String, includeDiscontinued: Boolean = false): List<FilmStock>`**

*   Returns film stocks where name or make contains query string
*   Excludes discontinued stocks by default — user can opt in to show them
*   Used by film stock list screen and roll setup stock picker

**`getFilmStockById(id: Int): FilmStock`**

**`insertFilmStock(filmStock: FilmStock)`** **`updateFilmStock(filmStock: FilmStock)`** **`deleteFilmStock(filmStock: FilmStock)`**


