package com.southsouthwest.framelog.data.repository

import com.southsouthwest.framelog.data.db.AppDatabase
import com.southsouthwest.framelog.data.db.entity.Frame
import com.southsouthwest.framelog.data.db.entity.Roll
import com.southsouthwest.framelog.data.db.entity.RollFilter
import com.southsouthwest.framelog.data.db.entity.RollLens
import com.southsouthwest.framelog.data.db.relation.RollExport
import com.southsouthwest.framelog.data.db.relation.RollWithDetails
import kotlinx.coroutines.flow.Flow

/**
 * Repository for Roll lifecycle management.
 *
 * Roll creation is the most complex write in the app — it atomically creates a roll record,
 * its lens and filter associations, and all pre-generated frame slots in one transaction.
 * [createRollWithAssociations] is the single entry point for this operation.
 *
 * Status transitions follow this state machine (enforced at application layer):
 *   active → finished (finishRoll)
 *   finished → archived (archiveRoll)
 *   archived → finished (unarchiveRoll)
 *   A roll cannot return to active once finished.
 *
 * The "active roll" selection for the widget and Quick Screen is stored in SharedPreferences,
 * not here. This repository has no knowledge of that preference.
 */
class RollRepository(private val db: AppDatabase) {

    private val rollDao = db.rollDao()

    // ---------------------------------------------------------------------------
    // Reads
    // ---------------------------------------------------------------------------

    /**
     * Returns all rolls currently in a camera (isLoaded = true) with full details.
     * The Quick Screen and widget draw from this list via the SharedPreferences active roll ID.
     */
    fun getActiveRolls(): Flow<List<RollWithDetails>> =
        rollDao.getActiveRolls()

    /**
     * Returns a specific roll with all its lenses, filters, and frame slots.
     * Used by the Quick Screen (active roll) and Roll Journal.
     */
    fun getRollById(rollId: Int): Flow<RollWithDetails> =
        rollDao.getRollById(rollId)

    /** Search rolls by name. Used by the Roll List screen. */
    fun searchRolls(query: String): Flow<List<Roll>> =
        rollDao.searchRolls(query)

    /** Search rolls filtered to a specific status. Used by Roll List tabs. */
    fun searchRollsByStatus(query: String, status: String): Flow<List<Roll>> =
        rollDao.searchRollsByStatus(query, status)

    // ---------------------------------------------------------------------------
    // Writes
    // ---------------------------------------------------------------------------

    /**
     * Atomically creates a roll with its lens associations, filter associations, and
     * all pre-generated frame slots. Returns the auto-generated roll ID.
     *
     * [roll] must have id = 0 (auto-generate). The rollId fields in [rollLenses],
     * [rollFilters], and [frames] are replaced with the generated roll ID inside the
     * transaction — callers may pass 0 for those fields.
     *
     * This is the ONLY correct way to create a roll. Do not call RollDao.insertRoll()
     * directly — doing so would create a roll with no frame slots.
     *
     * @param roll The roll to create. id must be 0.
     * @param rollLenses Lenses to associate with the roll. May be empty.
     * @param rollFilters Filters to associate with the roll. May be empty.
     * @param frames Pre-generated frame slots. Count must equal roll.totalExposures.
     * @return The auto-generated roll ID.
     */
    suspend fun createRollWithAssociations(
        roll: Roll,
        rollLenses: List<RollLens>,
        rollFilters: List<RollFilter>,
        frames: List<Frame>,
    ): Long = db.createRollWithAssociations(roll, rollLenses, rollFilters, frames)

    /**
     * Marks the roll as finished and records the current time as the finish timestamp.
     * Sets isLoaded = false is NOT done here — call [updateIsLoaded] separately if needed.
     */
    suspend fun finishRoll(rollId: Int, finishedAt: Long) =
        rollDao.finishRoll(rollId, finishedAt)

    /** Moves a finished roll to archived status. */
    suspend fun archiveRoll(rollId: Int) =
        rollDao.archiveRoll(rollId)

    /** Returns an archived roll to finished status. */
    suspend fun unarchiveRoll(rollId: Int) =
        rollDao.unarchiveRoll(rollId)

    /**
     * Updates the isLoaded flag. Call this when the user loads or unloads a roll from
     * a camera. When setting isLoaded = true, also write the roll ID to SharedPreferences
     * as the active roll if it should be the new active roll.
     */
    suspend fun updateIsLoaded(rollId: Int, isLoaded: Boolean) =
        rollDao.updateIsLoaded(rollId, isLoaded)

    /** Records the timestamp of the last successful export. Non-critical — not transactional. */
    suspend fun updateLastExported(rollId: Int, timestamp: Long) =
        rollDao.updateLastExported(rollId, timestamp)

    /**
     * Permanently deletes a roll and all its associated data.
     * RollLens, RollFilter, Frame, and FrameFilter records are cascade-deleted.
     * This is irreversible — caller must confirm with the user before calling.
     */
    suspend fun deleteRoll(roll: Roll) =
        rollDao.deleteRoll(roll)

    // ---------------------------------------------------------------------------
    // Export
    // ---------------------------------------------------------------------------

    /**
     * Returns a fully denormalized roll snapshot for export. All foreign key IDs are
     * resolved to human-readable strings so the exported file is self-contained.
     *
     * This is a snapshot — subsequent database changes do not affect the returned object.
     */
    suspend fun getRollForExport(rollId: Int): RollExport =
        rollDao.getRollForExport(rollId)
}
