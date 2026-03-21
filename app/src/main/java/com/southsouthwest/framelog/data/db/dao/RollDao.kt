package com.southsouthwest.framelog.data.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import com.southsouthwest.framelog.data.db.entity.Roll
import com.southsouthwest.framelog.data.db.relation.FrameExport
import com.southsouthwest.framelog.data.db.relation.FilterExportRow
import com.southsouthwest.framelog.data.db.relation.FrameExportRow
import com.southsouthwest.framelog.data.db.relation.RollExport
import com.southsouthwest.framelog.data.db.relation.RollExportRow
import com.southsouthwest.framelog.data.db.relation.RollListRow
import com.southsouthwest.framelog.data.db.relation.RollWithDetails
import kotlinx.coroutines.flow.Flow

@Dao
abstract class RollDao {

    // ---------------------------------------------------------------------------
    // Reads
    // ---------------------------------------------------------------------------

    /**
     * Returns all rolls currently loaded in a camera (isLoaded = true) with full details.
     * The "active roll" for the widget is selected from this list via SharedPreferences.
     * Called on app launch and to populate the Quick Screen header switcher.
     */
    @Transaction
    @Query("SELECT * FROM rolls WHERE isLoaded = 1 ORDER BY loadedAt DESC")
    abstract fun getActiveRolls(): Flow<List<RollWithDetails>>

    /**
     * Returns a specific roll with all its lenses, filters, and frames.
     * Used by the Quick Screen (with rollId from SharedPreferences) and the Roll Journal.
     */
    @Transaction
    @Query("SELECT * FROM rolls WHERE id = :rollId")
    abstract fun getRollById(rollId: Int): Flow<RollWithDetails?>

    /**
     * Search rolls by name. Optionally filter by status (pass null for all statuses).
     * Used by the Roll List screen tabs.
     */
    @Query("SELECT * FROM rolls WHERE name LIKE '%' || :query || '%' ORDER BY loadedAt DESC")
    abstract fun searchRolls(query: String): Flow<List<Roll>>

    @Query("SELECT * FROM rolls WHERE name LIKE '%' || :query || '%' AND status = :status ORDER BY loadedAt DESC")
    abstract fun searchRollsByStatus(query: String, status: String): Flow<List<Roll>>

    /**
     * Returns rolls for the Roll List screen, enriched with film stock name, camera body name,
     * and a count of logged frames. Uses a correlated subquery for the frame count to avoid
     * loading full RollWithDetails (all frames) just to render a list card.
     */
    @Query("""
        SELECT r.id, r.name, r.filmStockId, r.cameraBodyId, r.pushPull, r.ratedISO,
               r.filmExpiryDate, r.totalExposures, r.isLoaded, r.gpsEnabled, r.status,
               r.loadedAt, r.finishedAt, r.lastExportedAt, r.notes,
               fs.name AS filmStockName, fs.make AS filmStockMake,
               cb.name AS cameraBodyName, cb.make AS cameraBodyMake,
               (SELECT COUNT(*) FROM frames f WHERE f.rollId = r.id AND f.isLogged = 1) AS loggedFrameCount
        FROM rolls r
        JOIN film_stocks fs ON r.filmStockId = fs.id
        JOIN camera_bodies cb ON r.cameraBodyId = cb.id
        WHERE r.name LIKE '%' || :query || '%' AND r.status = :status
        ORDER BY r.loadedAt DESC
    """)
    abstract fun searchRollListRowsByStatus(query: String, status: String): Flow<List<RollListRow>>

    // ---------------------------------------------------------------------------
    // Writes
    // ---------------------------------------------------------------------------

    /**
     * Inserts a new roll and returns the auto-generated row ID.
     * Not called in isolation — use AppDatabase.createRollWithAssociations() to create
     * a roll atomically with its lenses, filters, and pre-generated frame slots.
     */
    @Insert
    abstract suspend fun insertRoll(roll: Roll): Long

    /** Sets status = finished and records the finish timestamp. */
    @Query("UPDATE rolls SET status = 'finished', finishedAt = :finishedAt WHERE id = :rollId")
    abstract suspend fun finishRoll(rollId: Int, finishedAt: Long)

    /** Sets status = archived. Only valid on finished rolls — enforced by the UI, not the DB. */
    @Query("UPDATE rolls SET status = 'archived' WHERE id = :rollId")
    abstract suspend fun archiveRoll(rollId: Int)

    /** Returns a roll to finished status. A roll cannot return to active via unarchive. */
    @Query("UPDATE rolls SET status = 'finished' WHERE id = :rollId")
    abstract suspend fun unarchiveRoll(rollId: Int)

    /** Updates the isLoaded flag. Called when the user loads or unloads a roll from a camera. */
    @Query("UPDATE rolls SET isLoaded = :isLoaded WHERE id = :rollId")
    abstract suspend fun updateIsLoaded(rollId: Int, isLoaded: Boolean)

    /**
     * Records the timestamp of the last successful export.
     * Not part of a transaction — export timestamp failure is non-critical.
     */
    @Query("UPDATE rolls SET lastExportedAt = :timestamp WHERE id = :rollId")
    abstract suspend fun updateLastExported(rollId: Int, timestamp: Long)

    /**
     * Permanently deletes a roll. Cascades to RollLens, RollFilter, Frame, and FrameFilter
     * records via the database foreign key constraints. This is irreversible.
     * Callers must confirm with the user before invoking.
     */
    @Delete
    abstract suspend fun deleteRoll(roll: Roll)

    // ---------------------------------------------------------------------------
    // Export
    // ---------------------------------------------------------------------------

    /**
     * Returns a fully denormalized RollExport with all frames, lens names, filter names,
     * film stock name, and camera body name resolved to human-readable strings.
     *
     * Implemented as a @Transaction to ensure the snapshot is consistent — no partial
     * writes can occur between the individual queries that assemble this object.
     */
    @Transaction
    open suspend fun getRollForExport(rollId: Int): RollExport {
        val header = queryRollExportHeader(rollId)
        val frameRows = queryFrameExportRows(rollId)
        val frames = frameRows.map { frameRow ->
            val filterRows = queryFilterExportRows(frameRow.id)
            FrameExport(
                frameNumber = frameRow.frameNumber,
                isLogged = frameRow.isLogged,
                loggedAt = frameRow.loggedAt,
                aperture = frameRow.aperture,
                shutterSpeed = frameRow.shutterSpeed,
                lensName = frameRow.lensName,
                lensMake = frameRow.lensMake,
                lensFocalLengthMm = frameRow.lensFocalLengthMm,
                exposureCompensation = frameRow.exposureCompensation,
                filterNames = filterRows.map { it.name },
                filterEvReductions = filterRows.map { it.evReduction },
                lat = frameRow.lat,
                lng = frameRow.lng,
                notes = frameRow.notes,
            )
        }
        return RollExport(
            rollName = header.name,
            filmStockName = header.filmStockName,
            filmStockMake = header.filmStockMake,
            cameraBodyName = header.cameraBodyName,
            cameraBodyMake = header.cameraBodyMake,
            ratedISO = header.ratedISO,
            pushPull = header.pushPull,
            gpsEnabled = header.gpsEnabled,
            loadedAt = header.loadedAt,
            finishedAt = header.finishedAt,
            notes = header.notes,
            frames = frames,
        )
    }

    @Query("""
        SELECT r.name, r.ratedISO, r.pushPull, r.gpsEnabled, r.loadedAt, r.finishedAt, r.notes,
               fs.name AS filmStockName, fs.make AS filmStockMake,
               cb.name AS cameraBodyName, cb.make AS cameraBodyMake
        FROM rolls r
        JOIN film_stocks fs ON r.filmStockId = fs.id
        JOIN camera_bodies cb ON r.cameraBodyId = cb.id
        WHERE r.id = :rollId
    """)
    protected abstract suspend fun queryRollExportHeader(rollId: Int): RollExportRow

    @Query("""
        SELECT f.id, f.frameNumber, f.isLogged, f.loggedAt, f.aperture, f.shutterSpeed,
               f.exposureCompensation, f.lat, f.lng, f.notes,
               l.name AS lensName, l.make AS lensMake, l.focalLengthMm AS lensFocalLengthMm
        FROM frames f
        LEFT JOIN lenses l ON f.lensId = l.id
        WHERE f.rollId = :rollId
        ORDER BY f.frameNumber ASC
    """)
    protected abstract suspend fun queryFrameExportRows(rollId: Int): List<FrameExportRow>

    @Query("""
        SELECT fi.name, fi.evReduction
        FROM frame_filters ff
        JOIN filters fi ON ff.filterId = fi.id
        WHERE ff.frameId = :frameId
    """)
    protected abstract suspend fun queryFilterExportRows(frameId: Int): List<FilterExportRow>
}
