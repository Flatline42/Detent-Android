package com.southsouthwest.framelog.data.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A roll of film — the primary shooting journal entity.
 *
 * [isLoaded] distinguishes rolls in a camera (true) from inventory rolls (false). Multiple
 * rolls can be loaded simultaneously (one per camera body). The widget and quick screen reflect
 * whichever loaded roll the user has selected as active — that selection is stored in
 * SharedPreferences, not here.
 *
 * [status] and [isLoaded] are independent:
 *   - active + unloaded: purchased, in inventory, not yet in a camera
 *   - active + loaded: in a camera, actively shooting
 *   - finished: roll is done, camera unloaded
 *   - archived: finished roll moved to archive
 *
 * [pushPull] drives [ratedISO]: ratedISO = FilmStock.iso × 2^pushPull.
 * null pushPull indicates a custom ISO override — the user has set ratedISO manually.
 *
 * [totalExposures] is immutable after creation. Calculated at roll creation:
 *   - Standard body: defaultFrameCount + extraFrames
 *   - Half-frame body: (defaultFrameCount + extraFrames) × 2
 * All [Frame] slots are bulk-created at roll creation to match this count.
 */
@Entity(
    tableName = "rolls",
    foreignKeys = [
        ForeignKey(
            entity = FilmStock::class,
            parentColumns = ["id"],
            childColumns = ["filmStockId"],
        ),
        ForeignKey(
            entity = CameraBody::class,
            parentColumns = ["id"],
            childColumns = ["cameraBodyId"],
        ),
    ],
    indices = [Index("filmStockId"), Index("cameraBodyId")],
)
data class Roll(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val filmStockId: Int,
    val cameraBodyId: Int,
    /**
     * Push/pull stops. Range -3 to +3, default 0 (box speed).
     * Null when the user has entered a custom ratedISO override.
     */
    val pushPull: Int? = 0,
    /** Effective shooting ISO. Derived from FilmStock.iso × 2^pushPull, or a custom override. */
    val ratedISO: Int,
    /** Expiry date of this specific physical roll, stored as epoch milliseconds. */
    val filmExpiryDate: Long? = null,
    /** Immutable after creation. See class-level doc for calculation. */
    val totalExposures: Int,
    val isLoaded: Boolean = false,
    /** When true, lat/lng are captured automatically on each frame log. */
    val gpsEnabled: Boolean = false,
    val status: RollStatus = RollStatus.ACTIVE,
    /** When the roll was created, stored as epoch milliseconds. */
    val loadedAt: Long,
    val finishedAt: Long? = null,
    val lastExportedAt: Long? = null,
    val notes: String? = null,
)
