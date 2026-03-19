package com.southsouthwest.framelog.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.southsouthwest.framelog.data.db.entity.KitLens

/**
 * DAO for the KitLens join table.
 * Only called within the kit save transaction — never directly from the UI layer.
 * See AppDatabase.saveKitWithAssociations().
 */
@Dao
interface KitLensDao {

    @Insert
    suspend fun insertKitLens(kitLens: KitLens)

    /**
     * Deletes all lens associations for a kit.
     * Called before reinserting the current lens set during a kit save (wholesale replace).
     */
    @Query("DELETE FROM kit_lenses WHERE kitId = :kitId")
    suspend fun deleteAllKitLenses(kitId: Int)
}
