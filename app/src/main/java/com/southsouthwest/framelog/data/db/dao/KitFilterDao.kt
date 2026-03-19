package com.southsouthwest.framelog.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.southsouthwest.framelog.data.db.entity.KitFilter

/**
 * DAO for the KitFilter join table.
 * Only called within the kit save transaction — never directly from the UI layer.
 * See AppDatabase.saveKitWithAssociations().
 */
@Dao
interface KitFilterDao {

    @Insert
    suspend fun insertKitFilter(kitFilter: KitFilter)

    /**
     * Deletes all filter associations for a kit.
     * Called before reinserting the current filter set during a kit save (wholesale replace).
     */
    @Query("DELETE FROM kit_filters WHERE kitId = :kitId")
    suspend fun deleteAllKitFilters(kitId: Int)
}
