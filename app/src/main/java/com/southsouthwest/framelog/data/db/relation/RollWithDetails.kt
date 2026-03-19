package com.southsouthwest.framelog.data.db.relation

import androidx.room.Embedded
import androidx.room.Relation
import com.southsouthwest.framelog.data.db.entity.Frame
import com.southsouthwest.framelog.data.db.entity.Roll
import com.southsouthwest.framelog.data.db.entity.RollFilter
import com.southsouthwest.framelog.data.db.entity.RollLens

/**
 * A Roll with all its associated lenses, filters, and pre-generated frame slots.
 *
 * Used by the Quick Screen and Roll Journal — one database trip returns everything needed.
 * Queries returning this type must be annotated with @Transaction.
 */
data class RollWithDetails(
    @Embedded val roll: Roll,

    /** Lenses available during this roll, each with full Lens details. */
    @Relation(
        entity = RollLens::class,
        parentColumn = "id",
        entityColumn = "rollId",
    )
    val lenses: List<RollLensWithLens>,

    /** Filters available during this roll, each with full Filter details. */
    @Relation(
        entity = RollFilter::class,
        parentColumn = "id",
        entityColumn = "rollId",
    )
    val filters: List<RollFilterWithFilter>,

    /** All pre-generated frame slots, ordered by frameNumber. */
    @Relation(
        parentColumn = "id",
        entityColumn = "rollId",
    )
    val frames: List<Frame>,
)
