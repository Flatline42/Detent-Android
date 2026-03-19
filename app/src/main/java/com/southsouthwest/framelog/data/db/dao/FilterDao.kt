package com.southsouthwest.framelog.data.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.southsouthwest.framelog.data.db.entity.Filter
import kotlinx.coroutines.flow.Flow

@Dao
interface FilterDao {

    /**
     * Search filters by name, make, or filter type. Empty query returns all filters.
     * Used by the gear library list and roll setup filter picker.
     */
    @Query("SELECT * FROM filters WHERE name LIKE '%' || :query || '%' OR make LIKE '%' || :query || '%' OR filterType LIKE '%' || :query || '%' ORDER BY filterType ASC, name ASC")
    fun searchFilters(query: String): Flow<List<Filter>>

    @Query("SELECT * FROM filters WHERE id = :id")
    fun getFilterById(id: Int): Flow<Filter>

    @Insert
    suspend fun insertFilter(filter: Filter)

    @Update
    suspend fun updateFilter(filter: Filter)

    @Delete
    suspend fun deleteFilter(filter: Filter)

    /**
     * Returns all distinct filterType values across all filters.
     * Used to populate the filter type picker — folksonomy pattern,
     * user-defined vocabulary with autocomplete from existing values.
     */
    @Query("SELECT DISTINCT filterType FROM filters ORDER BY filterType ASC")
    fun getDistinctFilterTypes(): Flow<List<String>>
}
