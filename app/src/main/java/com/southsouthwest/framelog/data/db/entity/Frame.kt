package com.southsouthwest.framelog.data.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A single exposure slot on a roll.
 *
 * All frames are pre-created in bulk when a roll is loaded — one for each of
 * Roll.totalExposures. An unlogged frame has isLogged = false and null exposure fields.
 *
 * [frameNumber] is immutable. Set at roll creation, never changes. This is the canonical
 * ordering key for frames.
 *
 * [isLogged] and [loggedAt] are deliberately decoupled: a frame can be marked as logged
 * retroactively without fabricating a timestamp. isLogged = true with loggedAt = null is
 * valid (retroactive log). isLogged = false with loggedAt = null is an unlogged slot.
 *
 * [aperture] and [shutterSpeed] are stored as canonical strings (e.g. "f/5.6", "1/125").
 * Valid values are enforced by the UI steppers, not the database.
 *
 * Active filters are in the FrameFilter join table, not here. Filters stack.
 */
@Entity(
    tableName = "frames",
    foreignKeys = [
        ForeignKey(
            entity = Roll::class,
            parentColumns = ["id"],
            childColumns = ["rollId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = Lens::class,
            parentColumns = ["id"],
            childColumns = ["lensId"],
        ),
    ],
    indices = [Index("rollId"), Index("lensId")],
)
data class Frame(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val rollId: Int,
    /** Immutable — 1 through Roll.totalExposures, set at roll creation. */
    val frameNumber: Int,
    val isLogged: Boolean = false,
    /** When the frame was logged, as epoch milliseconds. Null if never logged or logged retroactively. */
    val loggedAt: Long? = null,
    /** Canonical string, e.g. "f/5.6". Null if unlogged. */
    val aperture: String? = null,
    /** Canonical string, e.g. "1/125", "1s", "B". Null if unlogged. */
    val shutterSpeed: String? = null,
    /** Must reference a lens in this roll's RollLens table. Enforced at application layer. */
    val lensId: Int? = null,
    /** Exposure compensation in EV stops, e.g. -1.0, +0.5. Null if not applied. */
    val exposureCompensation: Float? = null,
    /** GPS latitude. Captured automatically if Roll.gpsEnabled = true. */
    val lat: Double? = null,
    /** GPS longitude. Captured automatically if Roll.gpsEnabled = true. */
    val lng: Double? = null,
    val notes: String? = null,
)
