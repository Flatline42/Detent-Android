package com.southsouthwest.framelog.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A filter in the gear library.
 *
 * [filterType] is user-defined vocabulary (folksonomy). Suggested starting values: color, ND,
 * polarizer, graduated ND, UV, infrared — but not enforced. UI uses autocomplete with existing
 * values plus "add new" option.
 *
 * [evReduction] is nullable because some filters (UV, skylight 1A) have no meaningful exposure
 * reduction. When displaying the filter EV sum, only non-null values are summed. A "~" prefix
 * is shown if any active filter has null evReduction.
 *
 * [filterSizeMm] null means the filter works with any lens (slot filters, gel filters, etc.).
 * Null-size filters always appear in filter pickers regardless of lens thread size.
 *
 * Filters stack — a frame can have multiple active filters via the FrameFilter join table.
 */
@Entity(tableName = "filters")
data class Filter(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val make: String,
    val filterType: String,
    /** Stops of light reduction. Null for filters with no meaningful exposure effect. */
    val evReduction: Float? = null,
    val filterSizeMm: Int? = null,
    val notes: String? = null,
)
