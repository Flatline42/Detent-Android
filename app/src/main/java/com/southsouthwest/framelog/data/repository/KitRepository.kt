package com.southsouthwest.framelog.data.repository

import com.southsouthwest.framelog.data.db.AppDatabase
import com.southsouthwest.framelog.data.db.entity.Kit
import com.southsouthwest.framelog.data.db.entity.KitFilter
import com.southsouthwest.framelog.data.db.entity.KitLens
import com.southsouthwest.framelog.data.db.relation.KitWithDetails
import kotlinx.coroutines.flow.Flow

/**
 * Repository for Kit (gear template) management.
 *
 * Kits are templates used to pre-populate roll setup. They have no ongoing relationship
 * to rolls created from them — changing a kit after roll creation has no effect on
 * existing rolls.
 *
 * Kit saves use a wholesale replace strategy for associations: all existing KitLens and
 * KitFilter records are deleted and the current set is reinserted. This is simpler than
 * delta tracking and appropriate since kits are edited deliberately at home, not in the field.
 */
class KitRepository(private val db: AppDatabase) {

    private val kitDao = db.kitDao()

    // ---------------------------------------------------------------------------
    // Reads
    // ---------------------------------------------------------------------------

    /**
     * Search kits by name. Pass empty string to return all kits, sorted by most recently used.
     * Used by the Gear Library Kits tab and the kit selector in roll setup.
     */
    fun searchKits(query: String): Flow<List<Kit>> =
        kitDao.searchKits(query)

    fun getKitById(id: Int): Flow<Kit> =
        kitDao.getKitById(id)

    /**
     * Returns a kit with its camera body, lenses, and filters fully resolved.
     * Used by the Kit Detail/Edit screen and the roll setup pre-populate flow.
     */
    fun getKitWithDetails(id: Int): Flow<KitWithDetails> =
        kitDao.getKitWithDetails(id)

    // ---------------------------------------------------------------------------
    // Writes
    // ---------------------------------------------------------------------------

    /**
     * Atomically saves a kit (create or update) with a wholesale replacement of its
     * lens and filter associations. Returns the kit ID.
     *
     * For a new kit, [kit].id must be 0. For an update, [kit].id must be the existing kit ID.
     *
     * All writes succeed or none do.
     *
     * @param kit The kit to save. id = 0 for new, existing id for update.
     * @param kitLenses The complete current set of lens associations. Replaces all existing.
     * @param kitFilters The complete current set of filter associations. Replaces all existing.
     * @return The kit ID (auto-generated for new kits, same as kit.id for updates).
     */
    suspend fun saveKitWithAssociations(
        kit: Kit,
        kitLenses: List<KitLens>,
        kitFilters: List<KitFilter>,
    ): Long = db.saveKitWithAssociations(kit, kitLenses, kitFilters)

    /**
     * Deletes a kit. KitLens and KitFilter records are cascade-deleted by the database.
     * Rolls created from this kit are unaffected — kits are templates, not live references.
     */
    suspend fun deleteKit(kit: Kit) =
        kitDao.deleteKit(kit)

    /**
     * Updates the lastUsedAt timestamp on a kit. Called when a roll is successfully
     * created using this kit, so the kit rises to the top of the "sort by last used" list.
     *
     * This is a convenience wrapper that copies the kit with the updated timestamp and saves it.
     * Note: this does NOT replace lens/filter associations — call [saveKitWithAssociations]
     * if associations also need updating.
     */
    suspend fun touchLastUsedAt(kit: Kit, timestamp: Long) =
        kitDao.updateKit(kit.copy(lastUsedAt = timestamp))
}
