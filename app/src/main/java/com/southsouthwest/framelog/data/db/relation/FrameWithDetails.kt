package com.southsouthwest.framelog.data.db.relation

import androidx.room.Embedded
import androidx.room.Junction
import androidx.room.Relation
import com.southsouthwest.framelog.data.db.entity.Filter
import com.southsouthwest.framelog.data.db.entity.Frame
import com.southsouthwest.framelog.data.db.entity.FrameFilter
import com.southsouthwest.framelog.data.db.entity.Lens

/**
 * A Frame with its active Lens and all active Filters at the time of exposure.
 *
 * [lens] is null when the frame is unlogged (Frame.lensId is null).
 * [filters] is empty when no filters were active.
 *
 * Queries returning this type must be annotated with @Transaction.
 */
data class FrameWithDetails(
    @Embedded val frame: Frame,

    /** The lens used for this exposure. Null if the frame is unlogged. */
    @Relation(parentColumn = "lensId", entityColumn = "id")
    val lens: Lens? = null,

    /** Filters active at the time of this exposure, via the FrameFilter join table. */
    @Relation(
        parentColumn = "id",
        entityColumn = "id",
        associateBy = Junction(
            value = FrameFilter::class,
            parentColumn = "frameId",
            entityColumn = "filterId",
        ),
    )
    val filters: List<Filter> = emptyList(),
)
