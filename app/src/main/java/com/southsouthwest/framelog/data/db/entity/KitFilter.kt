package com.southsouthwest.framelog.data.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

/**
 * Filters included in a kit template.
 *
 * No isPrimary or inUse flag — same reasoning as RollFilter. Active filters inherit from the
 * previous logged frame via FrameFilter.
 *
 * Same pattern as RollFilter — kit is the pre-roll template, roll is the live record.
 * Insertion order determines display order at roll setup.
 */
@Entity(
    tableName = "kit_filters",
    primaryKeys = ["kitId", "filterId"],
    foreignKeys = [
        ForeignKey(
            entity = Kit::class,
            parentColumns = ["id"],
            childColumns = ["kitId"],
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
data class KitFilter(
    val kitId: Int,
    val filterId: Int,
)
