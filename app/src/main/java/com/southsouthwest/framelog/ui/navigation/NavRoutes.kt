package com.southsouthwest.framelog.ui.navigation

import kotlinx.serialization.Serializable

// ---------------------------------------------------------------------------
// Type-safe navigation route definitions (Navigation Compose 2.8.0).
// Each object/class corresponds to one back-stack entry.
// Int args default to 0 to represent "new item" (no existing DB row).
// ---------------------------------------------------------------------------

/** App entry point — Quick Screen (frame logging widget target). */
@Serializable
object QuickScreen

/** Gear Library with 5 tabs: Lenses, Bodies, Filters, Film Stocks, Kits. */
@Serializable
object GearLibrary

/** Lens create ([id] = 0) or edit ([id] = existing lens ID). */
@Serializable
data class LensDetail(val id: Int = 0)

/** Camera body create ([id] = 0) or edit ([id] = existing body ID). */
@Serializable
data class CameraBodyDetail(val id: Int = 0)

/** Filter create ([id] = 0) or edit ([id] = existing filter ID). */
@Serializable
data class FilterDetail(val id: Int = 0)

/** Film stock create ([id] = 0) or edit ([id] = existing stock ID). */
@Serializable
data class FilmStockDetail(val id: Int = 0)

/** Kit create ([id] = 0) or edit ([id] = existing kit ID). */
@Serializable
data class KitDetail(val id: Int = 0)

/** Roll list with Active / Finished / Archived tabs. */
@Serializable
object RollList

/** Roll setup / creation form. */
@Serializable
object RollSetup

/**
 * Kit selector — shown from Roll Setup when the user taps "Load from Kit".
 * Returns to Roll Setup via the back stack when a kit is selected.
 */
@Serializable
object KitSelector

/** Roll journal — frame list for a specific roll. */
@Serializable
data class RollJournal(val rollId: Int)

/** Frame detail / edit screen. */
@Serializable
data class FrameDetail(val frameId: Int, val rollId: Int)

/** App settings. */
@Serializable
object Settings
