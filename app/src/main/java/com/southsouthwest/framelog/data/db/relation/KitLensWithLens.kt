package com.southsouthwest.framelog.data.db.relation

import androidx.room.Embedded
import androidx.room.Relation
import com.southsouthwest.framelog.data.db.entity.KitLens
import com.southsouthwest.framelog.data.db.entity.Lens

/** A KitLens join record together with the full Lens details. */
data class KitLensWithLens(
    @Embedded val kitLens: KitLens,
    @Relation(parentColumn = "lensId", entityColumn = "id")
    val lens: Lens,
)
