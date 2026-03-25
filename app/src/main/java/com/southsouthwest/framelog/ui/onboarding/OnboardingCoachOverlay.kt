package com.southsouthwest.framelog.ui.onboarding

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Full-screen coach overlay rendered on top of the existing app UI during onboarding.
 *
 * ## Spotlight positioning
 * Uses runtime [spotlightBounds] (window-space coordinates captured via onGloballyPositioned
 * on the target element) for the cutout. Falls back to fraction-based positioning from the
 * [OnboardingStep] enum if bounds are not yet available.
 *
 * To convert window-space bounds to canvas-local coordinates, the overlay Box captures its
 * own window position via onGloballyPositioned and subtracts it from the element bounds.
 *
 * ## Tab row cutout
 * On Gear Library steps (ADD_LENS, ADD_BODY, FILTERS_TOUR, ADD_FILM_STOCK, KITS_TOUR)
 * a second BlendMode.Clear rectangle reveals the tab row so tabs remain tappable and visible.
 *
 * ## Arrow direction
 * Arrow points FROM the coach card TOWARD the spotlight:
 * - Spotlight in top half → card at bottom → arrow points UP (↑)
 * - Spotlight in bottom half → card at top → arrow points DOWN (↓)
 *
 * @param step The current [OnboardingStep]. Must have [OnboardingStep.showsOverlay] = true.
 * @param spotlightBounds Window-space bounds of the target element, or null if not yet captured.
 * @param tabRowBounds Window-space bounds of the GearLibrary tab row, or null.
 * @param onGotIt Called when the user taps the "got it ›" button.
 * @param onSkip Called when the user taps "skip tour".
 */
@Composable
fun OnboardingCoachOverlay(
    step: OnboardingStep,
    spotlightBounds: Rect?,
    tabRowBounds: Rect?,
    onGotIt: () -> Unit,
    onSkip: () -> Unit,
) {
    // Track this Box's own position in window coordinates so we can convert
    // window-space element bounds into canvas-local draw coordinates.
    var overlayWindowOffset by remember { mutableStateOf(Offset.Zero) }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .onGloballyPositioned { coords ->
                overlayWindowOffset = coords.positionInWindow()
            }
            // Consume all taps to prevent interaction with the dimmed UI beneath.
            .pointerInput(Unit) {
                detectTapGestures { /* absorb */ }
            },
    ) {
        val totalHeightPx = with(LocalDensity.current) { maxHeight.toPx() }

        // Convert window-space bounds to canvas-local coordinates by subtracting this overlay's
        // own window offset. This accounts for status bar, navigation bar, scaffold padding, etc.
        val localSpotlight: Rect? = spotlightBounds?.let { b ->
            Rect(
                left = b.left - overlayWindowOffset.x,
                top = b.top - overlayWindowOffset.y,
                right = b.right - overlayWindowOffset.x,
                bottom = b.bottom - overlayWindowOffset.y,
            )
        }
        val localTabRow: Rect? = tabRowBounds?.let { b ->
            Rect(
                left = b.left - overlayWindowOffset.x,
                top = b.top - overlayWindowOffset.y,
                right = b.right - overlayWindowOffset.x,
                bottom = b.bottom - overlayWindowOffset.y,
            )
        }

        // Determine if spotlight is in the bottom half of the screen for card placement.
        // Spotlight in top half → card at bottom. Spotlight in bottom half → card at top.
        val isSpotlightInBottomHalf = if (localSpotlight != null && totalHeightPx > 0f) {
            localSpotlight.center.y >= totalHeightPx * 0.55f
        } else {
            step.spotlightCenterYFraction >= 0.55f
        }

        // Arrow points FROM the card TOWARD the spotlight.
        // Card at bottom (spotlight in top half) → arrow points UP ↑
        // Card at top (spotlight in bottom half) → arrow points DOWN ↓
        val arrowChar = if (isSpotlightInBottomHalf) "\u2191" else "\u2193"

        // ── Dark scrim + spotlight cutout ─────────────────────────────────────
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    // Offscreen compositing required for BlendMode.Clear to work correctly.
                    compositingStrategy = CompositingStrategy.Offscreen
                },
        ) {
            // 1. Dark scrim.
            drawRect(
                color = Color(0xFF111111),
                alpha = 0.82f,
            )

            // 2. Compute spotlight rect — use runtime bounds if available, else fractions.
            val (topLeft, spotSize) = if (localSpotlight != null) {
                Offset(localSpotlight.left, localSpotlight.top) to
                    Size(localSpotlight.width, localSpotlight.height)
            } else {
                val spotW = step.spotlightWidthFraction * size.width
                val spotH = step.spotlightHeightFraction * size.height
                val spotCx = step.spotlightCenterXFraction * size.width
                val spotCy = step.spotlightCenterYFraction * size.height
                Offset(spotCx - spotW / 2f, spotCy - spotH / 2f) to Size(spotW, spotH)
            }

            // 3. Punch spotlight cutout (oval shaped).
            drawOval(
                color = Color.Black,
                topLeft = topLeft,
                size = spotSize,
                blendMode = BlendMode.Clear,
            )

            // 4. White stroke ring around the cutout for visual definition.
            drawOval(
                color = Color.White,
                topLeft = topLeft,
                size = spotSize,
                style = Stroke(width = 2.dp.toPx()),
            )

            // 5. On Gear Library steps, also clear the tab row so tabs remain visible.
            //    This is a rectangular clear (not oval) covering the full tab row width.
            if (localTabRow != null && isGearLibraryStep(step)) {
                drawRect(
                    color = Color.Black,
                    topLeft = Offset(localTabRow.left, localTabRow.top),
                    size = Size(localTabRow.width, localTabRow.height),
                    blendMode = BlendMode.Clear,
                )
            }
        }

        // ── Coach card + directional arrow ────────────────────────────────────
        if (isSpotlightInBottomHalf) {
            // Spotlight is in the bottom half — card goes at the top of the screen.
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
                    .padding(top = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                CoachCard(
                    step = step,
                    onGotIt = onGotIt,
                    onSkip = onSkip,
                )
                Text(
                    text = arrowChar,
                    fontSize = 20.sp,
                    color = Color.White,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        } else {
            // Spotlight is in the top half — card goes at the bottom of the screen.
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = arrowChar,
                    fontSize = 20.sp,
                    color = Color.White,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
                CoachCard(
                    step = step,
                    onGotIt = onGotIt,
                    onSkip = onSkip,
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

/** Steps that display over GearLibraryScreen — on these steps the tab row cutout is applied. */
private fun isGearLibraryStep(step: OnboardingStep): Boolean = step in setOf(
    OnboardingStep.ADD_LENS,
    OnboardingStep.ADD_BODY,
    OnboardingStep.FILTERS_TOUR,
    OnboardingStep.ADD_FILM_STOCK,
    OnboardingStep.KITS_TOUR,
)

// ---------------------------------------------------------------------------
// Coach card
// ---------------------------------------------------------------------------

/**
 * The dark-themed info card shown inside the onboarding overlay.
 *
 * Contains:
 *   - Top row: step counter (e.g. "step 2 of 11") + "skip tour" TextButton
 *   - Step title (bold, white)
 *   - Step body text (light grey)
 *   - Bottom row: right-aligned "got it ›" Button
 */
@Composable
private fun CoachCard(
    step: OnboardingStep,
    onGotIt: () -> Unit,
    onSkip: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF222222)),
        border = BorderStroke(0.5.dp, Color(0xFF444444)),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {

            // Top meta row: step counter + skip button.
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "step ${step.stepNumber} of 11",
                    fontSize = 8.sp,
                    color = Color(0xFFAAAAAA),
                    modifier = Modifier.weight(1f),
                )
                TextButton(
                    onClick = onSkip,
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        horizontal = 4.dp,
                        vertical = 0.dp,
                    ),
                ) {
                    Text(
                        text = "skip tour",
                        fontSize = 9.sp,
                        color = Color(0xFF888888),
                    )
                }
            }

            // Step title.
            Text(
                text = step.title,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
            )

            Spacer(modifier = Modifier.height(6.dp))

            // Step body.
            Text(
                text = step.body,
                fontSize = 9.sp,
                color = Color(0xFFDDDDDD),
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Bottom row: "got it ›" button right-aligned.
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.End,
            ) {
                Button(
                    onClick = onGotIt,
                    shape = RoundedCornerShape(6.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.White,
                        contentColor = Color(0xFF111111),
                    ),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        horizontal = 12.dp,
                        vertical = 4.dp,
                    ),
                ) {
                    Text(
                        text = "got it  \u203a",
                        fontSize = 9.sp,
                    )
                }
            }
        }
    }
}
