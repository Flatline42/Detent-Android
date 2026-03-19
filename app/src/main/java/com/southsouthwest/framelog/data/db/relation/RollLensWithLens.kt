package com.southsouthwest.framelog.data.db.relation

import androidx.room.Embedded
import androidx.room.Relation
import com.southsouthwest.framelog.data.db.entity.Lens
import com.southsouthwest.framelog.data.db.entity.RollLens

/** A RollLens join record together with the full Lens details. */
data class RollLensWithLens(
    @Embedded val rollLens: RollLens,
    @Relation(parentColumn = "lensId", entityColumn = "id")
    val lens: Lens,
)
