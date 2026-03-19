package com.southsouthwest.framelog.data.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.southsouthwest.framelog.data.db.entity.Lens
import kotlinx.coroutines.flow.Flow

@Dao
interface LensDao {

    /**
     * Search lenses by name, make, or focal length. Empty query returns all lenses.
     * Used by the gear library list and roll setup lens picker.
     */
    @Query("SELECT * FROM lenses WHERE name LIKE '%' || :query || '%' OR make LIKE '%' || :query || '%' OR CAST(focalLengthMm AS TEXT) LIKE '%' || :query || '%' ORDER BY make ASC, focalLengthMm ASC")
    fun searchLenses(query: String): Flow<List<Lens>>

    @Query("SELECT * FROM lenses WHERE id = :id")
    fun getLensById(id: Int): Flow<Lens>

    @Insert
    suspend fun insertLens(lens: Lens)

    @Update
    suspend fun updateLens(lens: Lens)

    @Delete
    suspend fun deleteLens(lens: Lens)

    /**
     * Returns all lenses compatible with a specific camera body mount type.
     * Used by roll setup to filter the lens picker to compatible lenses only.
     */
    @Query("SELECT * FROM lenses WHERE mountType = :mountType ORDER BY focalLengthMm ASC")
    fun getLensesByMountType(mountType: String): Flow<List<Lens>>
}
