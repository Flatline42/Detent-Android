package com.southsouthwest.framelog.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A film stock definition in the gear library.
 *
 * This is a stock definition, not a shooting record. [iso] is box speed only — rated ISO
 * (push/pull adjustments) lives on Roll, not here.
 *
 * [discontinued] allows stocks like Kodak Portra 160NC to persist in historical roll data
 * without cluttering the active film pickers. Discontinued stocks are hidden by default
 * and shown only when the user opts in.
 *
 * [defaultFrameCount] is a starting point at roll creation — editable there for
 * bulk-loaded or short-loaded rolls.
 *
 * [format] is THIRTY_FIVE_MM only in v1.0. MEDIUM_FORMAT is reserved for v1.1.
 */
@Entity(tableName = "film_stocks")
data class FilmStock(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val make: String,
    /** Box speed only. Rated ISO (push/pull) lives on Roll. */
    val iso: Int,
    val format: FilmFormat,
    val defaultFrameCount: Int,
    val colorType: ColorType,
    val discontinued: Boolean = false,
    val notes: String? = null,
)
