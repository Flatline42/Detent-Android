package com.southsouthwest.framelog.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.southsouthwest.framelog.data.db.entity.Frame
import com.southsouthwest.framelog.data.db.entity.FrameFilter
import com.southsouthwest.framelog.data.db.relation.FrameWithDetails
import kotlinx.coroutines.flow.Flow

/**
 * DAO for Frame reads/writes and FrameFilter management.
 *
 * FrameFilter operations are included here (rather than a separate DAO) because they are
 * always called together with frame updates inside the logFrame transaction. Keeping them
 * in one abstract class allows the @Transaction logFrame method to call both.
 */
@Dao
abstract class FrameDao {

    // ---------------------------------------------------------------------------
    // Frame reads
    // ---------------------------------------------------------------------------

    /**
     * Returns all frames for a roll ordered by frameNumber, each with its Lens and
     * active Filters. Used by the Roll Journal screen.
     */
    @Transaction
    @Query("SELECT * FROM frames WHERE rollId = :rollId ORDER BY frameNumber ASC")
    abstract fun getFramesForRoll(rollId: Int): Flow<List<FrameWithDetails>>

    /**
     * Returns a single frame with its Lens and active Filters.
     * Used by the Frame Detail/Edit screen — navigated to with frameId only.
     */
    @Transaction
    @Query("SELECT * FROM frames WHERE id = :frameId")
    abstract fun getFrameById(frameId: Int): Flow<FrameWithDetails>

    // ---------------------------------------------------------------------------
    // Frame writes
    // ---------------------------------------------------------------------------

    /**
     * Bulk-inserts all pre-generated frame slots for a new roll.
     * All frames created with isLogged = false and all exposure fields null.
     * Not called in isolation — part of AppDatabase.createRollWithAssociations().
     */
    @Insert
    abstract suspend fun insertFrames(frames: List<Frame>)

    /**
     * Updates all mutable fields on a frame. Called as part of logFrame().
     */
    @Update
    abstract suspend fun updateFrame(frame: Frame)

    // ---------------------------------------------------------------------------
    // FrameFilter writes (always called within logFrame transaction)
    // ---------------------------------------------------------------------------

    @Insert
    abstract suspend fun insertFrameFilter(frameFilter: FrameFilter)

    @Query("DELETE FROM frame_filters WHERE frameId = :frameId AND filterId = :filterId")
    abstract suspend fun deleteFrameFilter(frameId: Int, filterId: Int)

    // ---------------------------------------------------------------------------
    // Log frame transaction
    // ---------------------------------------------------------------------------

    /**
     * Atomically logs a frame: updates the frame record and applies the filter delta.
     *
     * Delta approach: only filter changes are written. [filtersToAdd] are newly toggled-on
     * filters, [filterIdsToRemove] are toggled-off filter IDs. Both lists can be empty.
     *
     * All steps succeed or none do — if any write fails the transaction rolls back
     * and the frame remains in its previous state.
     */
    @Transaction
    open suspend fun logFrame(
        frame: Frame,
        filtersToAdd: List<FrameFilter>,
        filterIdsToRemove: List<Int>,
    ) {
        updateFrame(frame)
        filtersToAdd.forEach { insertFrameFilter(it) }
        filterIdsToRemove.forEach { filterId -> deleteFrameFilter(frame.id, filterId) }
    }
}
