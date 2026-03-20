package com.southsouthwest.framelog.data.repository

import com.southsouthwest.framelog.data.db.AppDatabase
import com.southsouthwest.framelog.data.db.entity.Frame
import com.southsouthwest.framelog.data.db.entity.FrameFilter
import com.southsouthwest.framelog.data.db.relation.FrameWithDetails
import kotlinx.coroutines.flow.Flow

/**
 * Repository for Frame reads and the frame-logging write operation.
 *
 * Frames are pre-created at roll setup and populated as the user shoots. The primary
 * write operation is [logFrame], which updates a frame's exposure data and applies
 * filter changes atomically.
 *
 * Frame slots are never deleted — unlogged frames remain in the database with
 * isLogged = false. This preserves the roll's totalExposures count and allows
 * retroactive logging.
 */
class FrameRepository(db: AppDatabase) {

    private val frameDao = db.frameDao()

    // ---------------------------------------------------------------------------
    // Reads
    // ---------------------------------------------------------------------------

    /**
     * Returns all frames for a roll ordered by frameNumber, each with its Lens and
     * active Filters resolved. Used by the Roll Journal screen.
     */
    fun getFramesForRoll(rollId: Int): Flow<List<FrameWithDetails>> =
        frameDao.getFramesForRoll(rollId)

    /**
     * Returns a single frame with its Lens and active Filters.
     * Used by the Frame Detail/Edit screen — navigated to with frameId only,
     * per the navigation pattern of never passing full objects between screens.
     */
    fun getFrameById(frameId: Int): Flow<FrameWithDetails> =
        frameDao.getFrameById(frameId)

    // ---------------------------------------------------------------------------
    // Writes
    // ---------------------------------------------------------------------------

    /**
     * Atomically logs a frame: updates the frame record and applies the filter delta.
     *
     * [filtersToAdd] are filters newly toggled on for this frame. [filterIdsToRemove]
     * are filter IDs toggled off. Both lists may be empty (e.g. updating notes only).
     *
     * The delta approach minimises writes: only changed filters are touched, not the
     * full set. This matters for the widget's quick-log path where latency is critical.
     *
     * All steps succeed or none do — the frame remains in its previous state on failure.
     *
     * @param frame The updated frame. Must have a valid id (not 0).
     * @param filtersToAdd FrameFilter records to insert. frameId must match frame.id.
     * @param filterIdsToRemove Filter IDs to remove from this frame's FrameFilter rows.
     */
    suspend fun logFrame(
        frame: Frame,
        filtersToAdd: List<FrameFilter> = emptyList(),
        filterIdsToRemove: List<Int> = emptyList(),
    ) = frameDao.logFrame(frame, filtersToAdd, filterIdsToRemove)
}
