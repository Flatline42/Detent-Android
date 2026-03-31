package com.southsouthwest.framelog.data

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.map

private const val PREFS_NAME = "framelog_prefs"
// ... (rest of the file remains, I will use a more targeted replacement)

// Global keys
private const val KEY_ONBOARDING_COMPLETE = "onboarding_complete"
private const val KEY_ACTIVE_ROLL_ID = "active_roll_id"
private const val KEY_EXTRA_FRAMES_PER_ROLL = "extra_frames_per_roll"
private const val KEY_GPS_CAPTURE_ENABLED = "gps_capture_enabled"
private const val KEY_DEFAULT_EXPORT_FORMAT = "default_export_format"
private const val KEY_APP_THEME = "app_theme"
private const val KEY_ACCESSIBLE_COLOR_MODE = "accessible_color_mode"
private const val KEY_TIP_JAR_PROMPT_SHOWN = "tip_jar_prompt_shown"
private const val KEY_APERTURE_WHEEL_REVERSED = "aperture_wheel_reversed"
private const val KEY_SHUTTER_SOUND_ENABLED = "shutter_sound_enabled"

// Per-roll keys
private fun keyCurrentFrame(rollId: Int) = "current_frame_$rollId"
private fun keyMruFilters(rollId: Int) = "mru_filters_$rollId"

/**
 * Thin wrapper around SharedPreferences for all app configuration.
 *
 * All photography data lives in Room. This class holds:
 *   - The active roll ID (which loaded roll drives the widget and Quick Screen)
 *   - Onboarding and session state
 *   - User preferences (extra frames, GPS precision, export format, theme)
 *   - Per-roll ephemeral state (current frame pointer, MRU filter chip order)
 *
 * Not a singleton — instantiate wherever needed with an Application or activity Context.
 */
class AppPreferences(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    // ---------------------------------------------------------------------------
    // Onboarding
    // ---------------------------------------------------------------------------

    /** True after the user completes or skips onboarding. Gates the coach mark flow. */
    var onboardingComplete: Boolean
        get() = prefs.getBoolean(KEY_ONBOARDING_COMPLETE, false)
        set(value) = prefs.edit { putBoolean(KEY_ONBOARDING_COMPLETE, value) }

    // ---------------------------------------------------------------------------
    // Active roll selection
    // ---------------------------------------------------------------------------

    /**
     * The roll ID currently selected for the widget and Quick Screen.
     * -1 means no roll is selected (no loaded rolls exist).
     *
     * Updated by:
     *   - RollSetup when "Create & Load Roll" is tapped (if no other loaded roll exists)
     *   - Quick Screen switch-roll sheet when user selects a different loaded roll
     *   - Automatically when the active roll is finished or unloaded (falls back to next loaded roll)
     */
    var activeRollId: Int
        get() = prefs.getInt(KEY_ACTIVE_ROLL_ID, -1)
        set(value) = prefs.edit { putInt(KEY_ACTIVE_ROLL_ID, value) }

    // ---------------------------------------------------------------------------
    // Shooting defaults
    // ---------------------------------------------------------------------------

    /**
     * Extra frames appended beyond the film stock's defaultFrameCount at roll creation.
     * The extra frames account for leader frames and the slight over-capacity of most film.
     * Default 2. Editable in Settings.
     */
    var extraFramesPerRoll: Int
        get() = prefs.getInt(KEY_EXTRA_FRAMES_PER_ROLL, 2)
        set(value) = prefs.edit { putInt(KEY_EXTRA_FRAMES_PER_ROLL, value) }

    /** Whether GPS location capture is enabled globally. When true, rolls can use GPS capture. */
    var gpsCaptureEnabled: Boolean
        get() = prefs.getBoolean(KEY_GPS_CAPTURE_ENABLED, false)
        set(value) = prefs.edit { putBoolean(KEY_GPS_CAPTURE_ENABLED, value) }

    var defaultExportFormat: ExportFormat
        get() = ExportFormat.fromKey(
            prefs.getString(KEY_DEFAULT_EXPORT_FORMAT, ExportFormat.CSV.key)!!
        )
        set(value) = prefs.edit { putString(KEY_DEFAULT_EXPORT_FORMAT, value.key) }

    // ---------------------------------------------------------------------------
    // Appearance
    // ---------------------------------------------------------------------------

    var appTheme: AppTheme
        get() = AppTheme.fromKey(prefs.getString(KEY_APP_THEME, AppTheme.SYSTEM.key)!!)
        set(value) = prefs.edit { putString(KEY_APP_THEME, value.key) }

    val appThemeFlow: Flow<AppTheme> = callbackFlow {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == KEY_APP_THEME) {
                trySend(appTheme)
            }
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        // Emit initial value
        trySend(appTheme)
        awaitClose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }

    /**
     * Controls the scroll direction for the aperture wheel.
     *
     * false (default) — "Large aperture → Small": swipe right opens the aperture (lower f-number).
     * true            — "Small aperture → Large": swipe right stops down (higher f-number).
     *
     * This matches the convention of different camera brands where the aperture ring
     * may rotate in opposite directions to open the aperture.
     */
    var apertureWheelReversed: Boolean
        get() = prefs.getBoolean(KEY_APERTURE_WHEEL_REVERSED, false)
        set(value) = prefs.edit { putBoolean(KEY_APERTURE_WHEEL_REVERSED, value) }

    /**
     * Whether to play a shutter click sound after a frame is successfully logged.
     * Defaults to on. Silenced automatically when the device ringer is muted or in vibrate mode.
     */
    var shutterSoundEnabled: Boolean
        get() = prefs.getBoolean(KEY_SHUTTER_SOUND_ENABLED, true)
        set(value) = prefs.edit { putBoolean(KEY_SHUTTER_SOUND_ENABLED, value) }

    /**
     * When true, color-coded elements (e.g. long-exposure shutter speeds) use a supplementary
     * indicator (underline or suffix) in addition to or instead of color alone.
     */
    var accessibleColorMode: Boolean
        get() = prefs.getBoolean(KEY_ACCESSIBLE_COLOR_MODE, false)
        set(value) = prefs.edit { putBoolean(KEY_ACCESSIBLE_COLOR_MODE, value) }

    // ---------------------------------------------------------------------------
    // Tip jar
    // ---------------------------------------------------------------------------

    /**
     * True once the one-time 5-rolls-logged prompt has been shown.
     * The tip jar button in Settings remains visible regardless of this flag.
     */
    var tipJarPromptShown: Boolean
        get() = prefs.getBoolean(KEY_TIP_JAR_PROMPT_SHOWN, false)
        set(value) = prefs.edit { putBoolean(KEY_TIP_JAR_PROMPT_SHOWN, value) }

    // ---------------------------------------------------------------------------
    // Per-roll state
    // ---------------------------------------------------------------------------

    /**
     * The current frame number for a roll (1-based). This is the frame the Quick Screen
     * and widget are pointing at for the next log operation.
     *
     * Initialized to 1 when a roll is first loaded. Updated after each successful frame log
     * (advances to the next unlogged frame) and by the frame pointer stepper.
     */
    fun getCurrentFrameNumber(rollId: Int): Int =
        prefs.getInt(keyCurrentFrame(rollId), 1)

    fun setCurrentFrameNumber(rollId: Int, frameNumber: Int) =
        prefs.edit { putInt(keyCurrentFrame(rollId), frameNumber) }

    /**
     * Ordered list of up to 4 most recently toggled filter IDs for a roll.
     * Drives the filter chip row on the Quick Screen and Frame Detail screen.
     * Order: most recently used first (index 0).
     */
    fun getMruFilterIds(rollId: Int): List<Int> {
        val raw = prefs.getString(keyMruFilters(rollId), "") ?: ""
        if (raw.isBlank()) return emptyList()
        return raw.split(",").mapNotNull { it.toIntOrNull() }
    }

    fun setMruFilterIds(rollId: Int, filterIds: List<Int>) {
        prefs.edit { putString(keyMruFilters(rollId), filterIds.take(4).joinToString(",")) }
    }

    /**
     * Promotes [filterId] to the front of the MRU list for [rollId], keeping at most 4 entries.
     * Called whenever the user toggles a filter on the Quick Screen or Frame Detail screen.
     */
    fun promoteFilterInMru(rollId: Int, filterId: Int) {
        val current = getMruFilterIds(rollId).toMutableList()
        current.remove(filterId)
        current.add(0, filterId)
        setMruFilterIds(rollId, current)
    }

    /**
     * Removes all per-roll keys for the given roll. Called when a roll is deleted.
     */
    fun clearRollPreferences(rollId: Int) {
        prefs.edit {
            remove(keyCurrentFrame(rollId))
            remove(keyMruFilters(rollId))
        }
    }
}

// ---------------------------------------------------------------------------
// Preference enums
// ---------------------------------------------------------------------------

enum class ExportFormat(val key: String) {
    CSV("csv"),
    JSON("json"),
    PLAIN_TEXT("plain_text");

    companion object {
        fun fromKey(key: String) = entries.firstOrNull { it.key == key } ?: CSV
    }
}

enum class AppTheme(val key: String) {
    LIGHT("light"),
    DARK("dark"),
    SYSTEM("system");

    companion object {
        fun fromKey(key: String) = entries.firstOrNull { it.key == key } ?: SYSTEM
    }
}
