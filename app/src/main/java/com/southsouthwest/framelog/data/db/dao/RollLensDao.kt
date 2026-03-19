package com.southsouthwest.framelog.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import com.southsouthwest.framelog.data.db.entity.RollLens

/**
 * DAO for the RollLens join table.
 * Only called within the roll creation transaction — never directly from the UI layer.
 * See AppDatabase.createRollWithAssociations().
 */
@Dao
interface RollLensDao {

    @Insert
    suspend fun insertRollLens(rollLens: RollLens)
}
