package com.southsouthwest.framelog.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import com.southsouthwest.framelog.data.db.entity.RollFilter

/**
 * DAO for the RollFilter join table.
 * Only called within the roll creation transaction — never directly from the UI layer.
 * See AppDatabase.createRollWithAssociations().
 */
@Dao
interface RollFilterDao {

    @Insert
    suspend fun insertRollFilter(rollFilter: RollFilter)
}
