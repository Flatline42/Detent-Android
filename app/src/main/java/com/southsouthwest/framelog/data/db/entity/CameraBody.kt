package com.southsouthwest.framelog.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A physical camera body in the gear library.
 *
 * [mountType] is NOT a locked enum — the vocabulary is defined by the user's Lens library.
 * Camera body mount type must be picked from existing Lens.mountType values; Lens is always
 * entered first and is the source of truth for mount type strings.
 *
 * [shutterIncrements] controls the shutter speed stepper when this body is active on a roll.
 * The shutter mechanism is physically part of the body, not the lens.
 *
 * [format] = HALF_FRAME doubles totalExposures at roll creation (including extra frames).
 */
@Entity(tableName = "camera_bodies")
data class CameraBody(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val make: String,
    val model: String,
    val mountType: String,
    val format: CameraBodyFormat,
    val shutterIncrements: ShutterIncrements,
    val notes: String? = null,
)
