package com.southsouthwest.framelog.ui.widget

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
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
import androidx.glance.LocalSize
import androidx.glance.action.Action
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
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
import androidx.glance.text.FontFamily
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
// Size variants
// ---------------------------------------------------------------------------

/**
 * The three supported widget size configurations.
 *
 *   STANDARD — 4×2 cells (250×110dp): default placement. Steppers side-by-side.
 *   COMPACT  — 4×3 cells (250×180dp): taller. Steppers side-by-side, enlarged.
 *   LOOSE    — 5×3 cells (320×180dp): taller + wider. Steppers stacked full-width.
 */
private enum class WidgetSize { STANDARD, COMPACT, LOOSE }

/**
 * Per-size stepper element dimensions. Passed into the stepper composables so they
 * don't need to know which variant is active.
 *
 * [buttonSize] applies to both width and height of the tap-target button boxes.
 * [valueWidth] / [valueHeight] size the value display box (ignored in LOOSE — value
 * uses defaultWeight() to fill available horizontal space instead).
 * [itemSpacer] is the gap between the button and the value box within one stepper row.
 * [betweenStepperSpacer] is the gap between aperture and shutter sections (LOOSE only,
 * ignored in side-by-side variants which use column weights instead).
 */
private data class StepperDimensions(
    val buttonWidth: Dp,  // horizontal tap target; may differ from height in LOOSE
    val buttonSize: Dp,   // height (and width for STANDARD/COMPACT square buttons)
    val valueWidth: Dp,
    val valueHeight: Dp,
    val labelSp: TextUnit,
    val buttonSp: TextUnit,
    val valueSp: TextUnit,
    val itemSpacer: Dp,
    val betweenStepperSpacer: Dp,
    val sectionPaddingVertical: Dp,
)

private fun dimensionsFor(size: WidgetSize) = when (size) {
    WidgetSize.STANDARD -> StepperDimensions(
        buttonWidth = 28.dp, buttonSize = 28.dp, valueWidth = 48.dp, valueHeight = 28.dp,
        labelSp = 9.sp, buttonSp = 16.sp, valueSp = 15.sp,
        itemSpacer = 4.dp, betweenStepperSpacer = 0.dp, sectionPaddingVertical = 5.dp,
    )
    WidgetSize.COMPACT -> StepperDimensions(
        buttonWidth = 36.dp, buttonSize = 36.dp, valueWidth = 56.dp, valueHeight = 36.dp,
        labelSp = 10.sp, buttonSp = 17.sp, valueSp = 17.sp,
        itemSpacer = 5.dp, betweenStepperSpacer = 0.dp, sectionPaddingVertical = 8.dp,
    )
    WidgetSize.LOOSE -> StepperDimensions(
        buttonWidth = 88.dp, buttonSize = 44.dp, valueWidth = 0.dp, valueHeight = 44.dp,
        labelSp = 11.sp, buttonSp = 20.sp, valueSp = 20.sp,
        itemSpacer = 8.dp, betweenStepperSpacer = 10.dp, sectionPaddingVertical = 8.dp,
    )
}

// ---------------------------------------------------------------------------
// GlanceAppWidget
// ---------------------------------------------------------------------------

class FrameLogWidget : GlanceAppWidget() {

    companion object {
        // Declared sizes drive SizeMode.Responsive. Glance generates a RemoteViews entry for each
        // size in the set and the launcher selects the closest fit for the actual widget allocation.
        // Values use the standard Android cell formula: dp = 70n − 30.
        private val SIZE_STANDARD = DpSize(250.dp, 110.dp) // 4 wide × 2 tall
        private val SIZE_COMPACT  = DpSize(250.dp, 180.dp) // 4 wide × 3 tall
        private val SIZE_LOOSE    = DpSize(320.dp, 180.dp) // 5 wide × 3 tall
    }

    /**
     * Responsive mode: Glance renders one RemoteViews per declared size. The launcher
     * picks whichever fits best in the allocated widget area. Inside the composable,
     * LocalSize.current reports the size being rendered for that pass.
     */
    override val sizeMode = SizeMode.Responsive(setOf(SIZE_STANDARD, SIZE_COMPACT, SIZE_LOOSE))

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
            val state = currentState<Preferences>()
            val hasRoll = state[WidgetState.HAS_ROLL] ?: false

            // Determine which layout variant to render for this pass.
            val currentSize = LocalSize.current
            val widgetSize = when {
                currentSize.width >= SIZE_LOOSE.width &&
                        currentSize.height >= SIZE_LOOSE.height -> WidgetSize.LOOSE
                currentSize.height >= SIZE_COMPACT.height -> WidgetSize.COMPACT
                else -> WidgetSize.STANDARD
            }

            if (hasRoll) {
                ActiveRollContent(state, widgetSize)
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
 * Layout (all variants):
 *   Header: "FilmStock — Camera" | filter count  +  "Frame X / Y"
 *   Divider
 *   Stepper section (variant-specific — see below)
 *   Divider
 *   Log Frame or Roll Complete (fills remaining height)
 *
 * Stepper section variants:
 *   STANDARD / COMPACT — aperture and shutter side-by-side, each occupying half the width.
 *   LOOSE              — aperture above shutter, each spanning the full width.
 */
@Composable
private fun ActiveRollContent(prefs: Preferences, widgetSize: WidgetSize) {
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
    val dims = dimensionsFor(widgetSize)

    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(GlanceTheme.colors.surface),
    ) {
        // --- Header ---
        Row(
            modifier = GlanceModifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = GlanceModifier.defaultWeight()) {
                Text(
                    text = filmCamera,
                    style = TextStyle(fontSize = 11.sp, color = GlanceTheme.colors.onSurfaceVariant),
                    maxLines = 1,
                )
                Text(
                    text = "Frame $frameNumber / $totalFrames",
                    style = TextStyle(fontSize = 10.sp, color = GlanceTheme.colors.onSurfaceVariant),
                )
            }
            if (filterCount > 0) {
                val bullets = "●".repeat(filterCount.coerceAtMost(4))
                val suffix = if (filterCount > 4) " +${filterCount - 4}" else " filters"
                Text(
                    text = "$bullets$suffix",
                    style = TextStyle(fontSize = 10.sp, color = GlanceTheme.colors.onSurfaceVariant),
                )
            }
        }

        HorizontalDivider()

        // --- Stepper section (layout branches on widgetSize) ---
        if (widgetSize == WidgetSize.LOOSE) {
            StepperSectionStacked(aperture, shutterDisplay, isLongExposure, dims)
        } else {
            StepperSectionSideBySide(aperture, shutterDisplay, isLongExposure, dims)
        }

        HorizontalDivider()

        // --- Log Frame or Roll Complete (fills remaining height) ---
        if (isRollComplete) {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("detent://journal/$rollId"))
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
 * Aperture and shutter steppers displayed side-by-side in two equal-weight columns.
 * Used for STANDARD (2×4) and COMPACT (4×3) variants.
 */
@Composable
private fun StepperSectionSideBySide(
    aperture: String,
    shutterDisplay: String,
    isLongExposure: Boolean,
    dims: StepperDimensions,
) {
    Row(
        modifier = GlanceModifier
            .fillMaxWidth()
            .padding(horizontal = 6.dp, vertical = dims.sectionPaddingVertical),
    ) {
        // Aperture (left column)
        Column(
            modifier = GlanceModifier.defaultWeight(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "aperture",
                style = TextStyle(
                    fontSize = dims.labelSp,
                    color = GlanceTheme.colors.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                ),
            )
            Spacer(modifier = GlanceModifier.height(3.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                // − = narrower (higher f-number = higher list index)
                StepperButton("−", actionRunCallback<ApertureDownAction>(), dims)
                Spacer(modifier = GlanceModifier.width(dims.itemSpacer))
                StepperValue(aperture, isLongExposure = false, dims)
                Spacer(modifier = GlanceModifier.width(dims.itemSpacer))
                // + = wider (lower f-number = lower list index = more exposure)
                StepperButton("+", actionRunCallback<ApertureUpAction>(), dims)
            }
        }

        Spacer(modifier = GlanceModifier.width(4.dp))

        // Shutter (right column)
        Column(
            modifier = GlanceModifier.defaultWeight(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "shutter",
                style = TextStyle(
                    fontSize = dims.labelSp,
                    color = GlanceTheme.colors.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                ),
            )
            Spacer(modifier = GlanceModifier.height(3.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                // − = faster (higher list index = less exposure)
                StepperButton("−", actionRunCallback<ShutterDownAction>(), dims)
                Spacer(modifier = GlanceModifier.width(dims.itemSpacer))
                StepperValue(shutterDisplay, isLongExposure, dims)
                Spacer(modifier = GlanceModifier.width(dims.itemSpacer))
                // + = slower (lower list index = more exposure)
                StepperButton("+", actionRunCallback<ShutterUpAction>(), dims)
            }
        }
    }
}

/**
 * Aperture and shutter steppers stacked vertically, each spanning the full widget width.
 * Used for the LOOSE (5×3) variant where extra horizontal real estate lets the value
 * display fill the space between the buttons.
 */
@Composable
private fun StepperSectionStacked(
    aperture: String,
    shutterDisplay: String,
    isLongExposure: Boolean,
    dims: StepperDimensions,
) {
    Column(
        modifier = GlanceModifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = dims.sectionPaddingVertical),
    ) {
        // Aperture row
        Text(
            text = "aperture",
            modifier = GlanceModifier.fillMaxWidth(),
            style = TextStyle(
                fontSize = dims.labelSp,
                color = GlanceTheme.colors.onSurfaceVariant,
                textAlign = TextAlign.Center,
            ),
        )
        Spacer(modifier = GlanceModifier.height(3.dp))
        Row(
            modifier = GlanceModifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            StepperButton("−", actionRunCallback<ApertureDownAction>(), dims)
            Spacer(modifier = GlanceModifier.width(dims.itemSpacer))
            // defaultWeight() is a RowScope extension — must be inlined here, not extracted.
            Box(
                modifier = GlanceModifier
                    .defaultWeight()
                    .height(dims.valueHeight)
                    .background(GlanceTheme.colors.surface),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = aperture,
                    style = TextStyle(
                        fontSize = dims.valueSp,
                        fontWeight = FontWeight.Medium,
                        color = GlanceTheme.colors.onSurface,
                        textAlign = TextAlign.Center,
                    ),
                )
            }
            Spacer(modifier = GlanceModifier.width(dims.itemSpacer))
            StepperButton("+", actionRunCallback<ApertureUpAction>(), dims)
        }

        Spacer(modifier = GlanceModifier.height(dims.betweenStepperSpacer))

        // Shutter row
        Text(
            text = "shutter",
            modifier = GlanceModifier.fillMaxWidth(),
            style = TextStyle(
                fontSize = dims.labelSp,
                color = GlanceTheme.colors.onSurfaceVariant,
                textAlign = TextAlign.Center,
            ),
        )
        Spacer(modifier = GlanceModifier.height(3.dp))
        Row(
            modifier = GlanceModifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            StepperButton("−", actionRunCallback<ShutterDownAction>(), dims)
            Spacer(modifier = GlanceModifier.width(dims.itemSpacer))
            // defaultWeight() is a RowScope extension — must be inlined here, not extracted.
            Box(
                modifier = GlanceModifier
                    .defaultWeight()
                    .height(dims.valueHeight)
                    .background(GlanceTheme.colors.surface),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = shutterDisplay,
                    style = TextStyle(
                        fontSize = dims.valueSp,
                        fontWeight = FontWeight.Medium,
                        color = if (isLongExposure) GlanceTheme.colors.error
                                else GlanceTheme.colors.onSurface,
                        textAlign = TextAlign.Center,
                    ),
                )
            }
            Spacer(modifier = GlanceModifier.width(dims.itemSpacer))
            StepperButton("+", actionRunCallback<ShutterUpAction>(), dims)
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
                text = "DETENT",
                style = TextStyle(
                    fontSize = 12.sp,
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
 * Tappable stepper button (− or +). Size is driven by [dims] so the same composable
 * scales across all widget size variants.
 *
 * Corner radius (6dp) is applied on API 31+ only — silently ignored on API 26-30.
 */
@Composable
private fun StepperButton(label: String, action: Action, dims: StepperDimensions) {
    Box(
        modifier = GlanceModifier
            .width(dims.buttonWidth)
            .height(dims.buttonSize)
            .background(GlanceTheme.colors.surfaceVariant)
            .cornerRadius(6.dp)
            .clickable(action),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = TextStyle(
                fontSize = dims.buttonSp,
                fontWeight = FontWeight.Medium,
                color = GlanceTheme.colors.onSurfaceVariant,
                textAlign = TextAlign.Center,
            ),
        )
    }
}

/**
 * Fixed-width value display for STANDARD and COMPACT variants (side-by-side layout).
 * Width and height are taken from [dims].
 *
 * Long-exposure shutter values render in the error color, matching the camera dial
 * convention (red numbers) and the Quick Screen stepper behavior.
 */
@Composable
private fun StepperValue(text: String, isLongExposure: Boolean, dims: StepperDimensions) {
    val color = if (isLongExposure) GlanceTheme.colors.error else GlanceTheme.colors.onSurface
    Box(
        modifier = GlanceModifier
            .width(dims.valueWidth)
            .height(dims.valueHeight)
            .background(GlanceTheme.colors.surface),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = TextStyle(
                fontSize = dims.valueSp,
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
