package com.southsouthwest.framelog.data.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.southsouthwest.framelog.data.db.entity.Kit
import com.southsouthwest.framelog.data.db.relation.KitWithDetails
import kotlinx.coroutines.flow.Flow

@Dao
interface KitDao {

    /**
     * Search kits by name. Empty query returns all kits.
     * Used by the Gear Library Kits tab and the Kit Selector in roll setup.
     */
    @Query("SELECT * FROM kits WHERE name LIKE '%' || :query || '%' ORDER BY lastUsedAt DESC, name ASC")
    fun searchKits(query: String): Flow<List<Kit>>

    @Query("SELECT * FROM kits WHERE id = :id")
    fun getKitById(id: Int): Flow<Kit>

    /**
     * Returns a kit with its camera body, lenses, and filters.
     * Used by the Kit Detail/Edit screen and the roll setup pre-populate flow.
     */
    @Transaction
    @Query("SELECT * FROM kits WHERE id = :id")
    fun getKitWithDetails(id: Int): Flow<KitWithDetails>

    /** Inserts a new kit and returns the auto-generated row ID. Part of saveKit transaction. */
    @Insert
    suspend fun insertKit(kit: Kit): Long

    /** Updates kit metadata only (name, cameraBodyId, notes, lastUsedAt). Part of saveKit transaction. */
    @Update
    suspend fun updateKit(kit: Kit)

    /**
     * Deletes a kit. KitLens and KitFilter records are cascade-deleted by the database.
     */
    @Delete
    suspend fun deleteKit(kit: Kit)
}
