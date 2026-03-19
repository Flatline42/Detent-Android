package com.southsouthwest.framelog.data.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

/**
 * Lenses available during a roll. Appears in the lens picker on the quick screen.
 *
 * [isPrimary] = true sets the default lens for Frame 1. After that, the active lens inherits
 * from the previous logged frame. Exactly one isPrimary = true per roll is required.
 *
 * Insertion order determines display order in the picker.
 */
@Entity(
    tableName = "roll_lenses",
    primaryKeys = ["rollId", "lensId"],
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
    indices = [Index("lensId")],
)
data class RollLens(
    val rollId: Int,
    val lensId: Int,
    val isPrimary: Boolean,
)
