package com.southsouthwest.framelog.ui.widget

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.LocalContext
import androidx.glance.action.Action
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import com.southsouthwest.framelog.MainActivity
import com.southsouthwest.framelog.ui.util.ExposureValues

// ---------------------------------------------------------------------------
// Widget state keys (DataStore<Preferences> via PreferencesGlanceStateDefinition)
// ---------------------------------------------------------------------------

/**
 * Keys for the widget's Glance DataStore state. All widget data is stored here so that
 * stepper actions can update a single value without reloading from Room.
 *
 * FrameLogWidgetUpdater writes all keys on a full refresh. Stepper actions write only
 * APERTURE or SHUTTER. LogFrameAction triggers a full updater refresh after writing to Room.
 */
object WidgetState {
    /** True when a loaded roll is active. When false, the widget shows the empty state. */
    val HAS_ROLL = booleanPreferencesKey("w_has_roll")

    /** Room ID of the active roll. -1 when no roll is selected. */
    val ROLL_ID = intPreferencesKey("w_roll_id")

    /** "FilmStockName — CameraBodyName" header string. */
    val FILM_CAMERA = stringPreferencesKey("w_film_camera")

    /** Current frame pointer (1-based). Set from SharedPreferences at update time. */
    val FRAME_NUMBER = intPreferencesKey("w_frame_number")

    /** Total frame count for the roll (totalExposures). */
    val TOTAL_FRAMES = intPreferencesKey("w_total_frames")

    /** Number of filters associated with this roll (informational display only). */
    val FILTER_COUNT = intPreferencesKey("w_filter_count")

    /** Currently selected aperture value in canonical storage format, e.g. "f/8". */
    val APERTURE = stringPreferencesKey("w_aperture")

    /** Currently selected shutter speed in canonical storage format, e.g. "1/125". */
    val SHUTTER = stringPreferencesKey("w_shutter")

    /**
     * Comma-separated aperture value list for the active lens, ordered widest→narrowest.
     * Stored here so stepper actions can step without hitting the database.
     */
    val APERTURE_LIST = stringPreferencesKey("w_aperture_list")

    /**
     * Comma-separated shutter speed list for the active camera body, ordered slowest→fastest.
     * Stored here so stepper actions can step without hitting the database.
     */
    val SHUTTER_LIST = stringPreferencesKey("w_shutter_list")

    /**
     * Lens ID from the last logged frame (or the roll's primary lens when no frames are logged yet).
     * -1 when no lens is available. Read by LogFrameAction to carry the lens forward on widget logs.
     */
    val LENS_ID = intPreferencesKey("w_lens_id")

    /**
     * Comma-separated filter IDs active on the last logged frame.
     * Empty string when no filters were active. Read by LogFrameAction to carry filters forward.
     */
    val FILTER_IDS = stringPreferencesKey("w_filter_ids")

    /**
     * True when there are no unlogged frames after the highest logged frame number.
     * ActiveRollContent shows "Roll Complete" instead of the log button in this state.
     */
    val IS_ROLL_COMPLETE = booleanPreferencesKey("w_is_roll_complete")
}

// ---------------------------------------------------------------------------
// GlanceAppWidget
// ---------------------------------------------------------------------------

class FrameLogWidget : GlanceAppWidget() {

    /**
     * Provides the widget's Compose-like UI. Glance calls this whenever the widget needs
     * to render — on add, after any updateAppWidgetState call, or after explicit update().
     *
     * GlanceMaterial3Theme wraps the content to provide Material You dynamic colors via
     * GlanceTheme.colors inside all child composables.
     *
     * State is read via currentState<Preferences>() — the DataStore snapshot written
     * by FrameLogWidgetUpdater and by the stepper ActionCallbacks.
     */
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            // GlanceTheme.colors from glance-appwidget provides system-aware dark/light colors.
            // We don't use the glance-material3 wrapper here to avoid a naming conflict with
            // androidx.glance.GlanceTheme (both packages use the same class name).
            val state = currentState<Preferences>()
            val hasRoll = state[WidgetState.HAS_ROLL] ?: false
            if (hasRoll) {
                ActiveRollContent(state)
            } else {
                NoRollContent()
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Widget composables
// ---------------------------------------------------------------------------

/**
 * Main widget layout shown when a roll is loaded and active.
 *
 * Layout mirrors WIREFRAME_widget_2x4.svg:
 *   Row 1 (header): "FilmStock — Camera" | filter count
 *                   "Frame X / Y"
 *   Divider
 *   Row 2 (steppers): aperture [− / value / +]  ·  shutter [− / value / +]
 *   Divider
 *   Rows 3–4 (log button): full-width tap target
 */
@Composable
private fun ActiveRollContent(prefs: Preferences) {
    val filmCamera = prefs[WidgetState.FILM_CAMERA] ?: ""
    val frameNumber = prefs[WidgetState.FRAME_NUMBER] ?: 1
    val totalFrames = prefs[WidgetState.TOTAL_FRAMES] ?: 0
    val filterCount = prefs[WidgetState.FILTER_COUNT] ?: 0
    val aperture = prefs[WidgetState.APERTURE] ?: "—"
    val shutter = prefs[WidgetState.SHUTTER] ?: "—"
    val shutterDisplay = if (shutter != "—") ExposureValues.shutterDisplayValue(shutter) else "—"
    val isLongExposure = shutter != "—" && ExposureValues.isLongExposure(shutter)
    val isRollComplete = prefs[WidgetState.IS_ROLL_COMPLETE] ?: false
    val rollId = prefs[WidgetState.ROLL_ID] ?: -1

    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(GlanceTheme.colors.surface),
    ) {
        // --- Row 1: Header ---
        Row(
            modifier = GlanceModifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // defaultWeight() is a scope extension on GlanceModifier — no explicit import needed
            Column(modifier = GlanceModifier.defaultWeight()) {
                Text(
                    text = filmCamera,
                    style = TextStyle(
                        fontSize = 11.sp,
                        color = GlanceTheme.colors.onSurfaceVariant,
                    ),
                    maxLines = 1,
                )
                Text(
                    text = "Frame $frameNumber / $totalFrames",
                    style = TextStyle(
                        fontSize = 10.sp,
                        color = GlanceTheme.colors.onSurfaceVariant,
                    ),
                )
            }
            // Filter indicator — one bullet per filter (max 4), then +N overflow label
            if (filterCount > 0) {
                val bullets = "●".repeat(filterCount.coerceAtMost(4))
                val suffix = if (filterCount > 4) " +${filterCount - 4}" else " filters"
                Text(
                    text = "$bullets$suffix",
                    style = TextStyle(
                        fontSize = 10.sp,
                        color = GlanceTheme.colors.onSurfaceVariant,
                    ),
                )
            }
        }

        // --- Divider ---
        HorizontalDivider()

        // --- Row 2: Aperture + Shutter steppers ---
        Row(
            modifier = GlanceModifier
                .fillMaxWidth()
                .padding(horizontal = 6.dp, vertical = 5.dp),
        ) {
            // Aperture stepper (left half)
            Column(
                modifier = GlanceModifier.defaultWeight(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = "aperture",
                    style = TextStyle(
                        fontSize = 9.sp,
                        color = GlanceTheme.colors.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    ),
                )
                Spacer(modifier = GlanceModifier.height(3.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // − = narrower aperture (higher f-number = higher list index)
                    StepperButton(label = "−", action = actionRunCallback<ApertureDownAction>())
                    Spacer(modifier = GlanceModifier.width(4.dp))
                    StepperValue(text = aperture, isLongExposure = false)
                    Spacer(modifier = GlanceModifier.width(4.dp))
                    // + = wider aperture (lower f-number = lower list index = more exposure)
                    StepperButton(label = "+", action = actionRunCallback<ApertureUpAction>())
                }
            }

            Spacer(modifier = GlanceModifier.width(4.dp))

            // Shutter stepper (right half)
            Column(
                modifier = GlanceModifier.defaultWeight(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = "shutter",
                    style = TextStyle(
                        fontSize = 9.sp,
                        color = GlanceTheme.colors.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    ),
                )
                Spacer(modifier = GlanceModifier.height(3.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // − = faster shutter (higher list index = less exposure)
                    StepperButton(label = "−", action = actionRunCallback<ShutterDownAction>())
                    Spacer(modifier = GlanceModifier.width(4.dp))
                    StepperValue(text = shutterDisplay, isLongExposure = isLongExposure)
                    Spacer(modifier = GlanceModifier.width(4.dp))
                    // + = slower shutter (lower list index = more exposure)
                    StepperButton(label = "+", action = actionRunCallback<ShutterUpAction>())
                }
            }
        }

        // --- Divider ---
        HorizontalDivider()

        // --- Rows 3–4: Log Frame or Roll Complete ---
        // defaultWeight() is a ColumnScope extension — must be applied inline here.
        if (isRollComplete) {
            // Taps to open the app at the Roll Journal so the user can finish the roll.
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("framelog://journal/$rollId"))
            Box(
                modifier = GlanceModifier
                    .fillMaxWidth()
                    .defaultWeight()
                    .clickable(actionStartActivity(intent)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "roll complete →",
                    style = TextStyle(
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        color = GlanceTheme.colors.primary,
                        textAlign = TextAlign.Center,
                    ),
                )
            }
        } else {
            Box(
                modifier = GlanceModifier
                    .fillMaxWidth()
                    .defaultWeight()
                    .clickable(actionRunCallback<LogFrameAction>()),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "log frame",
                    style = TextStyle(
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        color = GlanceTheme.colors.onSurface,
                        textAlign = TextAlign.Center,
                    ),
                )
            }
        }
    }
}

/**
 * Empty state shown when no roll is loaded. Tapping the entire widget opens the app
 * at the Quick Screen so the user can load a roll.
 *
 * actionStartActivity takes an explicit Intent — glance-appwidget does not provide an
 * inline reified overload, so we pass the Intent directly using LocalContext.
 */
@Composable
private fun NoRollContent() {
    val context = LocalContext.current
    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(GlanceTheme.colors.surface)
            .clickable(actionStartActivity(Intent(context, MainActivity::class.java))),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "FRAME//LOG",
                style = TextStyle(
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = GlanceTheme.colors.onSurface,
                    textAlign = TextAlign.Center,
                ),
            )
            Spacer(modifier = GlanceModifier.height(4.dp))
            Text(
                text = "No roll loaded — tap to open",
                style = TextStyle(
                    fontSize = 10.sp,
                    color = GlanceTheme.colors.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                ),
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Shared stepper composables
// ---------------------------------------------------------------------------

/**
 * Tappable stepper button (− or +). Uses surfaceVariant background to visually distinguish
 * it from the value display box. 28×28dp gives a usable tap target on a widget.
 *
 * Corner radius (6dp) is applied on API 31+ only — silently ignored on API 26-30.
 */
@Composable
private fun StepperButton(label: String, action: Action) {
    Box(
        modifier = GlanceModifier
            .size(28.dp)
            .background(GlanceTheme.colors.surfaceVariant)
            .cornerRadius(6.dp)
            .clickable(action),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = TextStyle(
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                color = GlanceTheme.colors.onSurfaceVariant,
                textAlign = TextAlign.Center,
            ),
        )
    }
}

/**
 * Read-only value display between the stepper buttons.
 *
 * Long-exposure shutter values (whole seconds and B) render in the error color,
 * matching the camera dial convention (red numbers on film cameras like the Canon AE-1)
 * and consistent with the Quick Screen stepper behavior.
 */
@Composable
private fun StepperValue(text: String, isLongExposure: Boolean) {
    val color = if (isLongExposure) GlanceTheme.colors.error else GlanceTheme.colors.onSurface
    Box(
        modifier = GlanceModifier
            .width(48.dp)
            .height(28.dp)
            .background(GlanceTheme.colors.surface),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = TextStyle(
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                color = color,
                textAlign = TextAlign.Center,
            ),
        )
    }
}

/** Thin horizontal rule between widget layout sections. */
@Composable
private fun HorizontalDivider() {
    Box(
        modifier = GlanceModifier
            .fillMaxWidth()
            .height(1.dp)
            .background(GlanceTheme.colors.surfaceVariant),
    ) {}
}
