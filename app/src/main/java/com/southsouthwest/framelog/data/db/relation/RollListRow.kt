package com.southsouthwest.framelog.data.db.relation

import androidx.room.Embedded
import com.southsouthwest.framelog.data.db.entity.Roll

/**
 * A denormalized roll row for the Roll List screen.
 *
 * Joins [Roll] with its [FilmStock] and [CameraBody] to surface display names, and includes
 * a correlated subquery for the count of logged frames. This avoids loading full
 * [RollWithDetails] (which includes all frames) just to render a list card.
 *
 * Used exclusively by [RollDao.searchRollListRowsByStatus].
 */
data class RollListRow(
    @Embedded val roll: Roll,
    val filmStockName: String,
    val filmStockMake: String,
    val cameraBodyName: String,
    val cameraBodyMake: String,
    /** Count of frames where isLogged = true. Derived via subquery — not stored in DB. */
    val loggedFrameCount: Int,
)
