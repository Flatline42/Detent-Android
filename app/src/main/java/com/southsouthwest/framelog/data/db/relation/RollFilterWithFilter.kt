package com.southsouthwest.framelog.data.db.relation

import androidx.room.Embedded
import androidx.room.Relation
import com.southsouthwest.framelog.data.db.entity.Filter
import com.southsouthwest.framelog.data.db.entity.RollFilter

/** A RollFilter join record together with the full Filter details. */
data class RollFilterWithFilter(
    @Embedded val rollFilter: RollFilter,
    @Relation(parentColumn = "filterId", entityColumn = "id")
    val filter: Filter,
)
