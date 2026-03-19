package com.southsouthwest.framelog.data.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A named gear template — one body, one or more lenses, zero or more filters — used to
 * pre-populate roll setup quickly.
 *
 * Kit is a template only. It has no direct relationship to any Roll after roll setup is
 * complete. Changing a kit after a roll is loaded has no effect on that roll.
 *
 * [lastUsedAt] is updated when a roll is created using this kit. Enables "sort by last used"
 * in the Gear Library Kits tab.
 */
@Entity(
    tableName = "kits",
    foreignKeys = [
        ForeignKey(
            entity = CameraBody::class,
            parentColumns = ["id"],
            childColumns = ["cameraBodyId"],
        ),
    ],
    indices = [Index("cameraBodyId")],
)
data class Kit(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val cameraBodyId: Int,
    /** Updated when a roll is created from this kit. Null until first use. */
    val lastUsedAt: Long? = null,
    val notes: String? = null,
)
