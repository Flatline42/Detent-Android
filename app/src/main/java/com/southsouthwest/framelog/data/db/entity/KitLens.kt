package com.southsouthwest.framelog.data.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

/**
 * Lenses included in a kit template.
 *
 * [isPrimary] = true sets which lens pre-populates as the primary lens when roll setup is
 * filled from this kit. Exactly one isPrimary = true per kit is required.
 *
 * Same pattern as RollLens — kit is the pre-roll template, roll is the live record.
 * Insertion order determines display order at roll setup.
 */
@Entity(
    tableName = "kit_lenses",
    primaryKeys = ["kitId", "lensId"],
    foreignKeys = [
        ForeignKey(
            entity = Kit::class,
            parentColumns = ["id"],
            childColumns = ["kitId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = Lens::class,
            parentColumns = ["id"],
            childColumns = ["lensId"],
        ),
    ],
    indices = [Index("lensId")],
)
data class KitLens(
    val kitId: Int,
    val lensId: Int,
    val isPrimary: Boolean,
)
