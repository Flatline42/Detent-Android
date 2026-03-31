package com.southsouthwest.framelog.ui.util

import android.content.Context
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntOffset
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.southsouthwest.framelog.ui.theme.AlpineGold
import com.southsouthwest.framelog.ui.theme.DustyMauve
import com.southsouthwest.framelog.ui.theme.GoldenHourAmber
import com.southsouthwest.framelog.ui.theme.GoldenHourBackground
import com.southsouthwest.framelog.ui.theme.WheelAdjLightGray
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

// ---------------------------------------------------------------------------
// Public API
// ---------------------------------------------------------------------------

/**
 * How adjacent items are visually separated in the wheel.
 *
 * DOTS — no explicit separator; intermediate stop positions show "·" (the calling code
 *        provides dot labels via [HorizontalScrollWheel.labelFor] for intermediate items).
 * PIPES — a thin "|" is drawn between each adjacent pair of items.
 */
enum class WheelNotation { DOTS, PIPES }

/**
 * Three-tier color scheme for the wheel: selected center, adjacent (±1), and far (±2+) items.
 * Use [wheelTierColors] to obtain the correct colors for the active theme.
 */
data class WheelTierColors(
    val selected: Color,
    val adjacent: Color,
    val far: Color,
)

/**
 * Returns [WheelTierColors] matched to the active DETENT theme.
 *
 * Alpine (light) — AlpineGold / WheelAdjLightGray
 * Golden Hour (dark) — GoldenHourAmber / DustyMauve
 *
 * All color values reference named constants from Color.kt — no hardcoded hex in this function.
 */
@Composable
fun wheelTierColors(): WheelTierColors {
    // Detect theme by background: Golden Hour uses the dark GoldenHourBackground.
    val isDark = MaterialTheme.colorScheme.background == GoldenHourBackground
    return WheelTierColors(
        selected = if (isDark) GoldenHourAmber else AlpineGold,
        adjacent = if (isDark) DustyMauve else WheelAdjLightGray,
        far = if (isDark) DustyMauve.copy(alpha = 0.7f) else WheelAdjLightGray.copy(alpha = 0.7f),
    )
}

// ---------------------------------------------------------------------------
// Composable
// ---------------------------------------------------------------------------

/**
 * Horizontal scroll wheel — manual-offset implementation.
 *
 * Items are positioned by direct pixel calculation relative to the container center:
 *
 *   itemCenterX(i) = containerWidth/2 + (i − settledIndex) × itemWidth + dragOffset
 *
 * The center highlight pill is always drawn at a fixed position (containerWidth/2).
 * There is no LazyRow, no content-padding math, and no snap-fling behavior.
 * Centering is guaranteed regardless of screen size or density.
 *
 * On drag end, [dragOffset] animates to the nearest item boundary, settledIndex is committed,
 * and [onValueChange] is called with the new index.
 *
 * @param values          Canonical string for each wheel position — passed back via [onValueChange].
 * @param selectedIndex   Which index is currently centered (drives position on external change).
 * @param onValueChange   Called with the new index after the wheel snaps to a resting position.
 * @param labelFor        Returns the display string for each cell. Receives the index and whether
 *                        that cell is the current visual center. Use this to show "·" for
 *                        intermediate aperture/EC positions when they are not centered.
 * @param notation        DOTS — no separators drawn (dots come from [labelFor]);
 *                        PIPES — a "|" separator is drawn on the right edge of each cell.
 * @param tierColors      Selected / adjacent / far colors. Use [wheelTierColors] for theme defaults.
 * @param subLabelFor     Optional: returns a small supplementary string drawn below the selected
 *                        center label. Used by the EC wheel to show a decimal value for intermediate
 *                        stops (e.g. "−0.67" below "−⅔").
 * @param selectedColorOverride  Optional: called for the centered item to override the selected
 *                        color (e.g. whole-second shutter speeds render in colorScheme.error).
 * @param itemWidthDp     Width of each cell. Choose a size that comfortably fits the widest label.
 * @param wheelHeightDp   Height of the wheel track (not including the label row above it).
 */
@Composable
fun HorizontalScrollWheel(
    values: List<String>,
    selectedIndex: Int,
    onValueChange: (Int) -> Unit,
    labelFor: (index: Int, isVisualCenter: Boolean) -> String,
    notation: WheelNotation,
    tierColors: WheelTierColors,
    modifier: Modifier = Modifier,
    subLabelFor: ((index: Int) -> String?)? = null,
    selectedColorOverride: ((index: Int) -> Color?)? = null,
    itemWidthDp: Dp = 70.dp,
    wheelHeightDp: Dp = 58.dp,
) {
    if (values.isEmpty()) return

    val context = LocalContext.current
    val density = LocalDensity.current
    val itemWidthPx = with(density) { itemWidthDp.toPx() }
    val scope = rememberCoroutineScope()

    // settledIndex: committed center position, updated after each snap or external change.
    // dragOffset: live pixel offset from the committed center during interaction.
    //   Positive = dragged right → lower-index items move toward center.
    //   Negative = dragged left  → higher-index items move toward center.
    var settledIndex by remember { mutableIntStateOf(selectedIndex.coerceIn(values.indices)) }
    val dragOffsetAnim = remember { Animatable(0f) }

    // Reading dragOffsetAnim.value here makes this composition re-run on every animation frame,
    // which is intentional — item positions must update every frame during drag and snap.
    val currentDragOffset = dragOffsetAnim.value

    // The item currently under the center highlight.
    // Derived directly from the drag offset — no layout info needed.
    val visualCenterIndex = (settledIndex - (currentDragOffset / itemWidthPx).roundToInt())
        .coerceIn(values.indices)

    // Haptic tracking — written from the gesture handler (UI thread), read in next composition.
    val prevHapticIndex = remember { mutableIntStateOf(settledIndex) }

    // External change: animate to the new selectedIndex (e.g. lens change resets aperture).
    LaunchedEffect(selectedIndex) {
        val target = selectedIndex.coerceIn(values.indices)
        if (target != settledIndex) {
            // Animate dragOffset to the pixel distance required to show target at center.
            val targetDrag = (settledIndex - target) * itemWidthPx
            dragOffsetAnim.animateTo(
                targetValue = targetDrag,
                animationSpec = spring(stiffness = Spring.StiffnessMedium),
            )
            settledIndex = target
            prevHapticIndex.value = target
            dragOffsetAnim.snapTo(0f)
        }
    }

    // Container width — measured after the first layout pass.
    var containerWidthPx by remember { mutableIntStateOf(0) }

    Box(
        modifier = modifier
            .height(wheelHeightDp)
            .clipToBounds()
            .onSizeChanged { containerWidthPx = it.width }
            .pointerInput(values.size, itemWidthPx) {
                detectHorizontalDragGestures(
                    onHorizontalDrag = { change, dragAmount ->
                        change.consume()
                        // Clamp so we can't drag past index 0 or values.size-1.
                        val maxRight = settledIndex * itemWidthPx
                        val maxLeft = -(values.size - 1 - settledIndex) * itemWidthPx
                        val clamped = (dragOffsetAnim.value + dragAmount).coerceIn(maxLeft, maxRight)
                        scope.launch { dragOffsetAnim.snapTo(clamped) }
                        // Fire haptic each time a detent boundary is crossed.
                        val newCenter = (settledIndex - (clamped / itemWidthPx).roundToInt())
                            .coerceIn(values.indices)
                        if (newCenter != prevHapticIndex.value) {
                            prevHapticIndex.value = newCenter
                            vibrateWheelDetent(context)
                        }
                    },
                    onDragEnd = {
                        scope.launch {
                            val snapped = (settledIndex - (dragOffsetAnim.value / itemWidthPx).roundToInt())
                                .coerceIn(values.indices)
                            // Animate dragOffset to the exact pixel position that puts snapped at center.
                            val snapTarget = (settledIndex - snapped) * itemWidthPx
                            dragOffsetAnim.animateTo(
                                targetValue = snapTarget,
                                animationSpec = spring(
                                    dampingRatio = Spring.DampingRatioNoBouncy,
                                    stiffness = Spring.StiffnessMedium,
                                ),
                            )
                            // Commit: reset offset to 0 and update settledIndex.
                            settledIndex = snapped
                            prevHapticIndex.value = snapped
                            dragOffsetAnim.snapTo(0f)
                            onValueChange(snapped)
                        }
                    },
                    onDragCancel = {
                        scope.launch { dragOffsetAnim.animateTo(0f) }
                    },
                )
            },
    ) {
        // Center highlight pill — always fixed at the container center.
        // Uses align(Alignment.Center) which is correct regardless of containerWidthPx.
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .width(itemWidthDp)
                .fillMaxHeight()
                .background(
                    color = tierColors.selected.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(8.dp),
                ),
        )

        // Items are only positioned once the container has been measured.
        // On the first frame (containerWidthPx == 0) we skip rendering to avoid a
        // single flash of incorrectly-positioned content.
        if (containerWidthPx > 0) {
            val containerCenterX = containerWidthPx / 2f

            // Render a band of items around both settledIndex and visualCenterIndex so that
            // items remain visible during animations that span many positions.
            val renderRadius = (containerWidthPx / itemWidthPx / 2).toInt() + 3
            val lo = min(settledIndex, visualCenterIndex)
            val hi = max(settledIndex, visualCenterIndex)
            val firstIdx = (lo - renderRadius).coerceAtLeast(0)
            val lastIdx = (hi + renderRadius).coerceAtMost(values.size - 1)

            for (index in firstIdx..lastIdx) {
                // key(index) ensures Compose correctly re-uses composable instances when the
                // rendered range shifts, preventing stale content on adjacent slots.
                key(index) {
                    val itemCenterX = containerCenterX + (index - settledIndex) * itemWidthPx + currentDragOffset
                    val itemStartDp = with(density) { (itemCenterX - itemWidthPx / 2f).toDp() }

                    val distance = abs(index - visualCenterIndex)
                    val isCenter = distance == 0

                    val textColor: Color = when (distance) {
                        0 -> selectedColorOverride?.invoke(index) ?: tierColors.selected
                        1 -> tierColors.adjacent
                        else -> tierColors.far
                    }
                    val fontSize = when (distance) {
                        0 -> 22.sp
                        1 -> 10.sp
                        else -> 9.sp
                    }
                    val fontWeight = when (distance) {
                        0 -> FontWeight.Bold
                        1 -> FontWeight.Medium
                        else -> FontWeight.Normal
                    }

                    val label = labelFor(index, isCenter)
                    val subLabel = if (isCenter) subLabelFor?.invoke(index) else null

                    // offset() positions the item at the calculated x from the Box origin.
                    // Items outside the Box bounds are clipped by clipToBounds() above.
                    Box(
                        modifier = Modifier
                            .offset(x = itemStartDp)
                            .width(itemWidthDp)
                            .fillMaxHeight(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = label,
                                color = textColor,
                                fontSize = fontSize,
                                fontWeight = fontWeight,
                                textAlign = TextAlign.Center,
                                maxLines = 1,
                            )
                            if (subLabel != null) {
                                Text(
                                    text = subLabel,
                                    color = tierColors.adjacent,
                                    fontSize = 8.sp,
                                    fontWeight = FontWeight.Normal,
                                    textAlign = TextAlign.Center,
                                    maxLines = 1,
                                )
                            }
                        }

                        // PIPES notation: draw a thin "|" at the right edge of each cell
                        // (except the last), creating a visual separator between values.
                        if (notation == WheelNotation.PIPES && index < values.size - 1) {
                            Text(
                                text = "|",
                                color = if (distance <= 1) tierColors.adjacent else tierColors.far,
                                fontSize = 10.sp,
                                modifier = Modifier.align(Alignment.CenterEnd),
                            )
                        }
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Haptic helper — fires on every detent during drag.
// Uses the same 4-segment createWaveform required by Pixel 7.
// ---------------------------------------------------------------------------

private fun vibrateWheelDetent(context: Context) {
    context.getSystemService(Vibrator::class.java)
        ?.takeIf { it.hasVibrator() }
        ?.vibrate(
            VibrationEffect.createWaveform(
                longArrayOf(0, 30, 50, 30),
                intArrayOf(0, 80, 0, 80),
                -1,
            )
        )
}
