package com.southsouthwest.framelog.data.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

/**
 * Records which filters were active at the time of a specific exposure.
 *
 * Filters stack — a frame can have multiple FrameFilter records.
 *
 * [filterId] must reference a filter in the parent roll's RollFilter table.
 * This constraint is enforced at the application layer, not by the database.
 *
 * Active filters for frame N+1 are pre-populated from frame N's FrameFilter records.
 * Frame 1 defaults to no active filters.
 */
@Entity(
    tableName = "frame_filters",
    primaryKeys = ["frameId", "filterId"],
    foreignKeys = [
        ForeignKey(
            entity = Frame::class,
            parentColumns = ["id"],
            childColumns = ["frameId"],
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
data class FrameFilter(
    val frameId: Int,
    val filterId: Int,
)
