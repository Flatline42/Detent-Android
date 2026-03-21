package com.southsouthwest.framelog.ui.widget

import android.content.Context
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.updateAppWidgetState
import com.southsouthwest.framelog.data.AppPreferences
import com.southsouthwest.framelog.data.db.AppDatabase
import com.southsouthwest.framelog.ui.util.ExposureValues
import kotlinx.coroutines.flow.first

/**
 * Reads the current active roll from Room and SharedPreferences, writes all widget state
 * keys to Glance DataStore, and triggers a re-render of every FrameLogWidget instance.
 *
 * Called from:
 *   1. FrameLogWidgetReceiver.onUpdate / onEnabled — on widget add or launcher request.
 *   2. QuickScreenViewModel.writeFrame() — after a successful frame log from the app.
 *   3. QuickScreenViewModel.onRollSwitched() — after the user switches active rolls.
 *   4. LogFrameAction — after a successful frame log from the widget itself.
 *
 * Aperture/shutter are pre-populated from the most recently logged frame at or before
 * the current frame pointer — the same logic as Quick Screen pre-population. Stepper
 * adjustments made on the widget live only in GlanceState; a subsequent full update
 * (e.g. after logging) re-derives the value from the newly logged frame.
 */
object FrameLogWidgetUpdater {

    suspend fun update(context: Context) {
        val db = AppDatabase.getInstance(context)
        val appPrefs = AppPreferences(context)
        val rollId = appPrefs.activeRollId

        val manager = GlanceAppWidgetManager(context)
        val glanceIds = manager.getGlanceIds(FrameLogWidget::class.java)

        if (glanceIds.isEmpty()) return

        // No active roll — clear all widget instances to the empty state
        if (rollId == -1) {
            glanceIds.forEach { glanceId ->
                updateAppWidgetState(context, glanceId) { prefs ->
                    prefs[WidgetState.HAS_ROLL] = false
                }
                FrameLogWidget().update(context, glanceId)
            }
            return
        }

        // Load the active roll with all its associations
        val rollWithDetails = db.rollDao().getRollById(rollId).first()
        if (rollWithDetails == null) {
            // Roll ID in prefs points to a deleted roll — clear and reset
            appPrefs.activeRollId = -1
            glanceIds.forEach { glanceId ->
                updateAppWidgetState(context, glanceId) { prefs ->
                    prefs[WidgetState.HAS_ROLL] = false
                }
                FrameLogWidget().update(context, glanceId)
            }
            return
        }

        // Load film stock and camera body names for the header string
        val filmStock = db.filmStockDao().getFilmStockById(rollWithDetails.roll.filmStockId).first()
        val cameraBody = db.cameraBodyDao().getCameraBodyById(rollWithDetails.roll.cameraBodyId).first()

        // Current frame pointer — clamped to a valid frame range
        val totalFrames = rollWithDetails.frames.size
        val frameNumber = appPrefs.getCurrentFrameNumber(rollId)
            .coerceIn(1, if (totalFrames > 0) totalFrames else 1)

        // Build stepper value lists from the camera body and primary lens
        val shutterList = ExposureValues.shutterSpeeds(cameraBody.shutterIncrements)

        val primaryLens = rollWithDetails.lenses.firstOrNull { it.rollLens.isPrimary }?.lens
        val apertureList = primaryLens?.let {
            ExposureValues.apertures(it.maxAperture, it.minAperture, it.apertureIncrements)
        } ?: emptyList()

        // Pre-populate aperture/shutter from the most recently logged frame at or before
        // the current pointer. Falls back to the midpoint of the list when no frame exists yet.
        val lastLogged = rollWithDetails.frames
            .filter { it.isLogged && it.frameNumber <= frameNumber }
            .maxByOrNull { it.frameNumber }

        val currentAperture = lastLogged?.aperture
            ?: apertureList.getOrNull(apertureList.size / 2)
            ?: ""

        val currentShutter = lastLogged?.shutterSpeed
            ?: shutterList.getOrNull(shutterList.size / 2)
            ?: ""

        val filmCamera = "${filmStock.name} — ${cameraBody.name}"
        val filterCount = rollWithDetails.filters.size

        // Write state and re-render every widget instance
        glanceIds.forEach { glanceId ->
            updateAppWidgetState(context, glanceId) { prefs ->
                prefs[WidgetState.HAS_ROLL] = true
                prefs[WidgetState.ROLL_ID] = rollId
                prefs[WidgetState.FILM_CAMERA] = filmCamera
                prefs[WidgetState.FRAME_NUMBER] = frameNumber
                prefs[WidgetState.TOTAL_FRAMES] = totalFrames
                prefs[WidgetState.FILTER_COUNT] = filterCount
                prefs[WidgetState.APERTURE] = currentAperture
                prefs[WidgetState.SHUTTER] = currentShutter
                prefs[WidgetState.APERTURE_LIST] = apertureList.joinToString(",")
                prefs[WidgetState.SHUTTER_LIST] = shutterList.joinToString(",")
            }
            FrameLogWidget().update(context, glanceId)
        }
    }
}
