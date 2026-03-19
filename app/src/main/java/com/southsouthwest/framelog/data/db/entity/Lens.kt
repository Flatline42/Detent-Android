package com.southsouthwest.framelog.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A lens in the gear library.
 *
 * [mountType] is the source of truth for the mount type vocabulary. Camera bodies pick their
 * mount type from existing Lens.mountType values — lenses are always entered first.
 *
 * [maxAperture] and [minAperture] bound the aperture stepper (e.g. f/1.4 to f/16).
 * [apertureIncrements] reflects the physical detents of this lens's aperture ring.
 *
 * [filterSizeMm] null means no standard filter thread (slot filters, rear gel, etc.).
 * Null-size filters always appear in filter pickers regardless of lens compatibility filtering.
 *
 * Note: focalLengthMm is a single integer (prime lenses only in v1.0). Zoom lens support
 * (focal length range) is deferred to v1.1.
 */
@Entity(tableName = "lenses")
data class Lens(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val make: String,
    val focalLengthMm: Int,
    val mountType: String,
    /** Widest aperture, e.g. 1.4 for f/1.4. Upper bound for the aperture stepper. */
    val maxAperture: Float,
    /** Smallest aperture, e.g. 16.0 for f/16. Lower bound for the aperture stepper. */
    val minAperture: Float,
    val apertureIncrements: ApertureIncrements,
    val filterSizeMm: Int? = null,
    val notes: String? = null,
)
