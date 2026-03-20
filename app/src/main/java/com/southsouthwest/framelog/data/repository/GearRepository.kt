package com.southsouthwest.framelog.data.repository

import com.southsouthwest.framelog.data.db.AppDatabase
import com.southsouthwest.framelog.data.db.entity.CameraBody
import com.southsouthwest.framelog.data.db.entity.FilmStock
import com.southsouthwest.framelog.data.db.entity.Filter
import com.southsouthwest.framelog.data.db.entity.Lens
import kotlinx.coroutines.flow.Flow

/**
 * Repository for all gear-library entities: camera bodies, lenses, filters, and film stocks.
 *
 * Provides a single access point for the Gear Library screens and the pickers in Roll Setup.
 * All read methods return Flow so the UI updates automatically when the underlying data changes.
 *
 * Write methods are suspend functions; callers should invoke them from a coroutine scope
 * (typically viewModelScope).
 */
class GearRepository(db: AppDatabase) {

    private val cameraBodyDao = db.cameraBodyDao()
    private val lensDao = db.lensDao()
    private val filterDao = db.filterDao()
    private val filmStockDao = db.filmStockDao()

    // ---------------------------------------------------------------------------
    // Camera bodies
    // ---------------------------------------------------------------------------

    /** Search camera bodies by name or make. Pass empty string to return all. */
    fun searchCameraBodies(query: String): Flow<List<CameraBody>> =
        cameraBodyDao.searchCameraBodies(query)

    fun getCameraBodyById(id: Int): Flow<CameraBody> =
        cameraBodyDao.getCameraBodyById(id)

    suspend fun insertCameraBody(cameraBody: CameraBody) =
        cameraBodyDao.insertCameraBody(cameraBody)

    suspend fun updateCameraBody(cameraBody: CameraBody) =
        cameraBodyDao.updateCameraBody(cameraBody)

    suspend fun deleteCameraBody(cameraBody: CameraBody) =
        cameraBodyDao.deleteCameraBody(cameraBody)

    /**
     * Returns all distinct mount type strings from the Lens table.
     * Lens is the source of truth for mount type vocabulary — camera bodies pick from this list.
     */
    fun getDistinctMountTypes(): Flow<List<String>> =
        cameraBodyDao.getDistinctMountTypes()

    // ---------------------------------------------------------------------------
    // Lenses
    // ---------------------------------------------------------------------------

    /** Search lenses by name, make, or focal length. Pass empty string to return all. */
    fun searchLenses(query: String): Flow<List<Lens>> =
        lensDao.searchLenses(query)

    fun getLensById(id: Int): Flow<Lens> =
        lensDao.getLensById(id)

    /** Returns only lenses whose mount type matches the given camera body. Used in roll setup. */
    fun getLensesByMountType(mountType: String): Flow<List<Lens>> =
        lensDao.getLensesByMountType(mountType)

    suspend fun insertLens(lens: Lens) =
        lensDao.insertLens(lens)

    suspend fun updateLens(lens: Lens) =
        lensDao.updateLens(lens)

    suspend fun deleteLens(lens: Lens) =
        lensDao.deleteLens(lens)

    // ---------------------------------------------------------------------------
    // Filters
    // ---------------------------------------------------------------------------

    /** Search filters by name, make, or filter type. Pass empty string to return all. */
    fun searchFilters(query: String): Flow<List<Filter>> =
        filterDao.searchFilters(query)

    fun getFilterById(id: Int): Flow<Filter> =
        filterDao.getFilterById(id)

    /**
     * Returns all distinct filterType strings across all filters.
     * Drives the autocomplete in the filter detail screen — folksonomy pattern.
     */
    fun getDistinctFilterTypes(): Flow<List<String>> =
        filterDao.getDistinctFilterTypes()

    suspend fun insertFilter(filter: Filter) =
        filterDao.insertFilter(filter)

    suspend fun updateFilter(filter: Filter) =
        filterDao.updateFilter(filter)

    suspend fun deleteFilter(filter: Filter) =
        filterDao.deleteFilter(filter)

    // ---------------------------------------------------------------------------
    // Film stocks
    // ---------------------------------------------------------------------------

    /**
     * Search film stocks by name or make.
     * [includeDiscontinued] defaults to false — discontinued stocks are hidden in pickers
     * but preserved in the database so historical roll data remains readable.
     */
    fun searchFilmStocks(query: String, includeDiscontinued: Boolean = false): Flow<List<FilmStock>> =
        filmStockDao.searchFilmStocks(query, includeDiscontinued)

    fun getFilmStockById(id: Int): Flow<FilmStock> =
        filmStockDao.getFilmStockById(id)

    suspend fun insertFilmStock(filmStock: FilmStock) =
        filmStockDao.insertFilmStock(filmStock)

    suspend fun updateFilmStock(filmStock: FilmStock) =
        filmStockDao.updateFilmStock(filmStock)

    suspend fun deleteFilmStock(filmStock: FilmStock) =
        filmStockDao.deleteFilmStock(filmStock)
}
