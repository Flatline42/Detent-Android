package com.southsouthwest.framelog.data.db.relation

import androidx.room.Embedded
import androidx.room.Relation
import com.southsouthwest.framelog.data.db.entity.Filter
import com.southsouthwest.framelog.data.db.entity.KitFilter

/** A KitFilter join record together with the full Filter details. */
data class KitFilterWithFilter(
    @Embedded val kitFilter: KitFilter,
    @Relation(parentColumn = "filterId", entityColumn = "id")
    val filter: Filter,
)
