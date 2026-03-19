package com.southsouthwest.framelog.data.db.relation

/**
 * Fully denormalized roll data used exclusively by the export flow.
 *
 * All foreign key IDs are resolved to human-readable strings so the exported file
 * is self-contained and readable without the database.
 */
data class RollExport(
    val rollName: String,
    val filmStockName: String,
    val filmStockMake: String,
    val cameraBodyName: String,
    val cameraBodyMake: String,
    val ratedISO: Int,
    val pushPull: Int?,
    val gpsEnabled: Boolean,
    val loadedAt: Long,
    val finishedAt: Long?,
    val notes: String?,
    val frames: List<FrameExport>,
)

/** A single frame's data for export, with all foreign keys resolved to strings. */
data class FrameExport(
    val frameNumber: Int,
    val isLogged: Boolean,
    val loggedAt: Long?,
    val aperture: String?,
    val shutterSpeed: String?,
    val lensName: String?,
    val lensMake: String?,
    val lensFocalLengthMm: Int?,
    val exposureCompensation: Float?,
    /** Names of all filters active on this frame. Empty if no filters were used. */
    val filterNames: List<String>,
    /** EV reduction values for each active filter, in the same order as filterNames. */
    val filterEvReductions: List<Float?>,
    val lat: Double?,
    val lng: Double?,
    val notes: String?,
)

// ---------------------------------------------------------------------------
// Internal intermediate types used only by RollDao.getRollForExport().
// Not part of the public API.
// ---------------------------------------------------------------------------

data class RollExportRow(
    val name: String,
    val filmStockName: String,
    val filmStockMake: String,
    val cameraBodyName: String,
    val cameraBodyMake: String,
    val ratedISO: Int,
    val pushPull: Int?,
    val gpsEnabled: Boolean,
    val loadedAt: Long,
    val finishedAt: Long?,
    val notes: String?,
)

data class FrameExportRow(
    val id: Int,
    val frameNumber: Int,
    val isLogged: Boolean,
    val loggedAt: Long?,
    val aperture: String?,
    val shutterSpeed: String?,
    val lensName: String?,
    val lensMake: String?,
    val lensFocalLengthMm: Int?,
    val exposureCompensation: Float?,
    val lat: Double?,
    val lng: Double?,
    val notes: String?,
)

data class FilterExportRow(
    val name: String,
    val evReduction: Float?,
)
