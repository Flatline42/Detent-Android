package com.southsouthwest.framelog.data.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

/**
 * Filters available during a roll. Appears in the filter picker on the quick screen.
 *
 * No isPrimary or inUse flag — active filters inherit from the previous logged frame via
 * FrameFilter. Frame 1 defaults to no active filters.
 *
 * Insertion order determines display order in the picker.
 */
@Entity(
    tableName = "roll_filters",
    primaryKeys = ["rollId", "filterId"],
    foreignKeys = [
        ForeignKey(
            entity = Roll::class,
            parentColumns = ["id"],
            childColumns = ["rollId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = Filter::class,
            parentColumns = ["id"],
            childColumns = ["filterId"],
        ),
    ],
    indices = [Index("filterId")],
)
data class RollFilter(
    val rollId: Int,
    val filterId: Int,
)
