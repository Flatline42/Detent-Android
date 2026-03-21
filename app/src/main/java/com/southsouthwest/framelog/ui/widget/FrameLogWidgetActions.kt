package com.southsouthwest.framelog.ui.widget

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.glance.GlanceId
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.state.getAppWidgetState
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.action.ActionParameters
import androidx.glance.state.PreferencesGlanceStateDefinition
import com.southsouthwest.framelog.data.AppPreferences
import com.southsouthwest.framelog.data.db.AppDatabase
import com.southsouthwest.framelog.data.db.entity.Frame
import com.southsouthwest.framelog.data.repository.FrameRepository
import kotlinx.coroutines.flow.first

// ---------------------------------------------------------------------------
// Stepper direction helpers
// ---------------------------------------------------------------------------

/**
 * Steps to the next value in [list] relative to [current], clamped at the boundaries.
 *
 * Both aperture and shutter lists are ordered "more exposure → less exposure":
 *   Aperture: widest first (f/1.4 … f/22)
 *   Shutter:  slowest first (B … 1/8000)
 *
 * "Up" (more exposure) = lower index  — wider aperture or slower shutter.
 * "Down" (less exposure) = higher index — narrower aperture or faster shutter.
 *
 * If [current] isn't in the list (e.g. stale state after roll change), snap to the midpoint.
 */
private fun stepUp(list: List<String>, current: String): String {
    if (list.isEmpty()) return current
    val idx = list.indexOf(current)
    val safeIdx = if (idx == -1) list.size / 2 else idx
    return list[(safeIdx - 1).coerceAtLeast(0)]
}

private fun stepDown(list: List<String>, current: String): String {
    if (list.isEmpty()) return current
    val idx = list.indexOf(current)
    val safeIdx = if (idx == -1) list.size / 2 else idx
    return list[(safeIdx + 1).coerceAtMost(list.size - 1)]
}

private fun parseList(raw: String): List<String> =
    raw.split(",").filter { it.isNotBlank() }

// ---------------------------------------------------------------------------
// Aperture stepper actions
// ---------------------------------------------------------------------------

/**
 * Increments aperture — moves to a wider aperture (lower f-number = lower index in list).
 * Reads current aperture and list from GlanceState, writes the new value, re-renders.
 */
class ApertureUpAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters,
    ) {
        updateAppWidgetState(context, glanceId) { prefs ->
            val list = parseList(prefs[WidgetState.APERTURE_LIST] ?: "")
            val current = prefs[WidgetState.APERTURE] ?: ""
            prefs[WidgetState.APERTURE] = stepUp(list, current)
        }
        FrameLogWidget().update(context, glanceId)
    }
}

/**
 * Decrements aperture — moves to a narrower aperture (higher f-number = higher index in list).
 */
class ApertureDownAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters,
    ) {
        updateAppWidgetState(context, glanceId) { prefs ->
            val list = parseList(prefs[WidgetState.APERTURE_LIST] ?: "")
            val current = prefs[WidgetState.APERTURE] ?: ""
            prefs[WidgetState.APERTURE] = stepDown(list, current)
        }
        FrameLogWidget().update(context, glanceId)
    }
}

// ---------------------------------------------------------------------------
// Shutter speed stepper actions
// ---------------------------------------------------------------------------

/**
 * Increments shutter — moves to a slower speed (more exposure = lower index in list).
 * Long-exposure values (B, whole seconds) render in the accent color automatically.
 */
class ShutterUpAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters,
    ) {
        updateAppWidgetState(context, glanceId) { prefs ->
            val list = parseList(prefs[WidgetState.SHUTTER_LIST] ?: "")
            val current = prefs[WidgetState.SHUTTER] ?: ""
            prefs[WidgetState.SHUTTER] = stepUp(list, current)
        }
        FrameLogWidget().update(context, glanceId)
    }
}

/**
 * Decrements shutter — moves to a faster speed (less exposure = higher index in list).
 */
class ShutterDownAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters,
    ) {
        updateAppWidgetState(context, glanceId) { prefs ->
            val list = parseList(prefs[WidgetState.SHUTTER_LIST] ?: "")
            val current = prefs[WidgetState.SHUTTER] ?: ""
            prefs[WidgetState.SHUTTER] = stepDown(list, current)
        }
        FrameLogWidget().update(context, glanceId)
    }
}

// ---------------------------------------------------------------------------
// Log Frame action
// ---------------------------------------------------------------------------

/**
 * Logs the current frame to Room using the aperture and shutter shown on the widget.
 *
 * Widget limitations vs Quick Screen:
 *   - No confirmation dialog for overwrite — silently overwrites a previously logged frame.
 *     (Widgets cannot show dialogs. The user can correct via the Frame Detail screen.)
 *   - No GPS capture — widgets cannot request location permission.
 *   - No filter changes — the widget does not manage active filter state. FrameFilter rows
 *     for the target frame are left unchanged (empty for a fresh slot).
 *   - No haptic feedback — widgets run outside the in-app process and cannot reliably
 *     trigger vibration via the accessibility-aware path used by the Quick Screen.
 *
 * After a successful write, the frame pointer advances to the next unlogged frame in
 * SharedPreferences, and FrameLogWidgetUpdater.update() fully refreshes the widget.
 */
class LogFrameAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters,
    ) {
        // Read the current widget state from GlanceState (DataStore).
        // getAppWidgetState requires an explicit GlanceStateDefinition — no 2-arg convenience exists.
        val state: Preferences = getAppWidgetState(context, PreferencesGlanceStateDefinition, glanceId)
        val rollId = state[WidgetState.ROLL_ID] ?: return
        val frameNumber = state[WidgetState.FRAME_NUMBER] ?: return
        val aperture = state[WidgetState.APERTURE]
        val shutter = state[WidgetState.SHUTTER]

        val db = AppDatabase.getInstance(context)
        val appPrefs = AppPreferences(context)
        val frameRepository = FrameRepository(db)

        // Load the roll to find the target frame entity by frameNumber
        val rollWithDetails = db.rollDao().getRollById(rollId).first() ?: return
        val frame = rollWithDetails.frames.firstOrNull { it.frameNumber == frameNumber } ?: return

        // Build the updated frame.
        // GPS is null — the widget cannot request location permission.
        // lensId, exposureCompensation, and notes are retained from the existing frame slot
        // (set during the previous Quick Screen session or left at their defaults).
        val updatedFrame = Frame(
            id = frame.id,
            rollId = frame.rollId,
            frameNumber = frame.frameNumber,
            isLogged = true,
            loggedAt = System.currentTimeMillis(),
            aperture = aperture,
            shutterSpeed = shutter,
            lensId = frame.lensId,
            exposureCompensation = frame.exposureCompensation,
            lat = null,
            lng = null,
            notes = frame.notes,
        )

        // Atomic write: update frame, no filter changes (empty lists)
        frameRepository.logFrame(updatedFrame, emptyList(), emptyList())

        // Advance the frame pointer to the next unlogged frame after the one just logged.
        // We use the pre-write rollWithDetails.frames because frames before frameNumber that
        // were already unlogged remain unlogged — we only want frames AFTER the current one.
        val nextUnlogged = rollWithDetails.frames
            .filter { it.frameNumber > frameNumber && !it.isLogged }
            .minByOrNull { it.frameNumber }

        if (nextUnlogged != null) {
            appPrefs.setCurrentFrameNumber(rollId, nextUnlogged.frameNumber)
        }

        // Full widget refresh — re-reads from Room to pick up the updated frame state
        // and the new frame pointer position.
        FrameLogWidgetUpdater.update(context)
    }
}
