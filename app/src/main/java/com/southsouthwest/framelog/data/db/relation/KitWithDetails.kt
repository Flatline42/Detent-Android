package com.southsouthwest.framelog.data.db.relation

import androidx.room.Embedded
import androidx.room.Relation
import com.southsouthwest.framelog.data.db.entity.CameraBody
import com.southsouthwest.framelog.data.db.entity.Kit
import com.southsouthwest.framelog.data.db.entity.KitFilter
import com.southsouthwest.framelog.data.db.entity.KitLens

/**
 * A Kit with its camera body, lenses, and filters.
 *
 * Used by the Kit Detail/Edit screen and the roll setup pre-populate flow.
 * Queries returning this type must be annotated with @Transaction.
 */
data class KitWithDetails(
    @Embedded val kit: Kit,

    @Relation(parentColumn = "cameraBodyId", entityColumn = "id")
    val cameraBody: CameraBody,

    /** Lenses in this kit, each with full Lens details. Primary lens first. */
    @Relation(
        entity = KitLens::class,
        parentColumn = "id",
        entityColumn = "kitId",
    )
    val lenses: List<KitLensWithLens>,

    /** Filters in this kit, each with full Filter details. */
    @Relation(
        entity = KitFilter::class,
        parentColumn = "id",
        entityColumn = "kitId",
    )
    val filters: List<KitFilterWithFilter>,
)
