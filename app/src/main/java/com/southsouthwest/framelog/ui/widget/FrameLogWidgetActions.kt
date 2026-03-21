package com.southsouthwest.framelog.ui.widget

import android.content.Context
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.datastore.preferences.core.Preferences
import androidx.glance.GlanceId
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.state.getAppWidgetState
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.action.ActionParameters
import androidx.glance.state.PreferencesGlanceStateDefinition
import com.southsouthwest.framelog.data.db.AppDatabase
import com.southsouthwest.framelog.data.db.entity.Frame
import com.southsouthwest.framelog.data.db.entity.FrameFilter
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
// ---------------------------------------------------------------------------
// Haptic helpers — direct Vibrator; VIBRATE permission declared in manifest
// ---------------------------------------------------------------------------

/** Single firm pulse — stepper increment (+). */
private fun vibrateIncrement(context: Context) {
    context.getSystemService(Vibrator::class.java)
        ?.takeIf { it.hasVibrator() }
        ?.vibrate(VibrationEffect.createOneShot(50, 80))
}

/** Double short pulse — stepper decrement (−). */
private fun vibrateDecrement(context: Context) {
    context.getSystemService(Vibrator::class.java)
        ?.takeIf { it.hasVibrator() }
        ?.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 30, 50, 30), intArrayOf(0, 80, 0, 80), -1))
}

/** Longer firm pulse — successful frame log confirmation. */
private fun vibrateConfirm(context: Context) {
    context.getSystemService(Vibrator::class.java)
        ?.takeIf { it.hasVibrator() }
        ?.vibrate(VibrationEffect.createOneShot(120, 200))
}

// ---------------------------------------------------------------------------
// Stepper direction helpers
// ---------------------------------------------------------------------------

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
        vibrateIncrement(context)
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
        vibrateDecrement(context)
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
        vibrateIncrement(context)
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
        vibrateDecrement(context)
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
 *   - Haptic feedback uses direct Vibrator.vibrate() (VIBRATE permission required).
 *     The accessibility-aware LocalHapticFeedback path is unavailable outside Compose.
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
        val lensId: Int? = state[WidgetState.LENS_ID]?.takeIf { it != -1 }
        val targetFilterIds: Set<Int> = parseList(state[WidgetState.FILTER_IDS] ?: "")
            .map { it.toInt() }.toSet()

        val db = AppDatabase.getInstance(context)
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
            lensId = lensId,
            exposureCompensation = frame.exposureCompensation,
            lat = null,
            lng = null,
            notes = frame.notes,
        )

        // Compute filter delta relative to what is already on the frame slot.
        // For a fresh (unlogged) frame this is always emptySet, but we read it defensively
        // in case the user previously logged and then the widget is re-logging the same slot.
        val previousFilterIds = db.frameDao().getFrameById(frame.id).first()
            .filters.map { it.id }.toSet()
        val filtersToAdd = (targetFilterIds - previousFilterIds).map { filterId ->
            FrameFilter(frameId = frame.id, filterId = filterId)
        }
        val filterIdsToRemove = (previousFilterIds - targetFilterIds).toList()

        frameRepository.logFrame(updatedFrame, filtersToAdd, filterIdsToRemove)
        vibrateConfirm(context)

        // Full widget refresh — re-derives the frame pointer from Room (first unlogged frame
        // after the highest logged frame number, or roll complete state).
        FrameLogWidgetUpdater.update(context)
    }
}
