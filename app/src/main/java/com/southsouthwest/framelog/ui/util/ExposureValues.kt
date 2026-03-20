package com.southsouthwest.framelog.ui.util

import com.southsouthwest.framelog.data.db.entity.ApertureIncrements
import com.southsouthwest.framelog.data.db.entity.ShutterIncrements

/**
 * Generates valid aperture and shutter speed value lists for stepper controls.
 *
 * All values use the canonical storage format (same strings stored in the database):
 *   Apertures: "f/1.4", "f/8", "f/11" etc.
 *   Shutter speeds: "B", "30s", "1s", "1/125", "1/1000" etc.
 *
 * Lists are ordered slow→fast (shutter) or wide→narrow (aperture) so that stepping
 * "up" always increases exposure.
 */
object ExposureValues {

    // ---------------------------------------------------------------------------
    // Shutter speed sequences
    // ---------------------------------------------------------------------------

    // Full stops: B + 19 stops from 30s to 1/8000
    private val SHUTTER_FULL = listOf(
        "B",
        "30s", "15s", "8s", "4s", "2s", "1s",
        "1/2", "1/4", "1/8", "1/15", "1/30", "1/60",
        "1/125", "1/250", "1/500", "1/1000", "1/2000", "1/4000", "1/8000",
    )

    // Half stops: same range with intermediate values added
    private val SHUTTER_HALF = listOf(
        "B",
        "30s", "20s", "15s", "10s", "8s", "6s", "4s", "3s", "2s", "1.5s", "1s",
        "1/1.5", "1/2", "1/3", "1/4", "1/6", "1/8", "1/10", "1/15", "1/20",
        "1/30", "1/45", "1/60", "1/90", "1/125", "1/180", "1/250", "1/350",
        "1/500", "1/750", "1/1000", "1/1500", "1/2000", "1/3000", "1/4000",
        "1/6000", "1/8000",
    )

    // Third stops: standard photographic values at 1/3 stop spacing
    private val SHUTTER_THIRD = listOf(
        "B",
        "30s", "25s", "20s", "15s", "13s", "10s", "8s", "6s", "5s", "4s", "3s",
        "2.5s", "2s", "1.6s", "1.3s", "1s",
        "1/1.3", "1/1.6", "1/2", "1/2.5", "1/3", "1/4", "1/5", "1/6",
        "1/8", "1/10", "1/13", "1/15", "1/20", "1/25", "1/30", "1/40", "1/50",
        "1/60", "1/80", "1/100", "1/125", "1/160", "1/200", "1/250", "1/320", "1/400",
        "1/500", "1/640", "1/800", "1/1000", "1/1250", "1/1600", "1/2000", "1/2500",
        "1/3200", "1/4000", "1/5000", "1/6400", "1/8000",
    )

    /**
     * Returns the complete shutter speed list for the given increment type,
     * ordered slowest → fastest (B first, 1/8000 last).
     */
    fun shutterSpeeds(increments: ShutterIncrements): List<String> = when (increments) {
        ShutterIncrements.FULL -> SHUTTER_FULL
        ShutterIncrements.HALF -> SHUTTER_HALF
        ShutterIncrements.THIRD -> SHUTTER_THIRD
    }

    /**
     * Returns the human-readable display string for a stored shutter speed value.
     *
     * Convention from the DESIGN decisions doc:
     *   "1/125" → "125"  (drop the "1/" — implied by context)
     *   "2s"    → "2s"   (keep the s-suffix for whole seconds)
     *   "B"     → "B"
     */
    fun shutterDisplayValue(stored: String): String = when {
        stored == "B" -> "B"
        stored.endsWith("s") -> stored
        stored.startsWith("1/") -> stored.removePrefix("1/")
        else -> stored // e.g. "1/1.5" keeps as-is if doesn't match above
    }

    /**
     * Returns true if this shutter speed value should render in the accent color.
     * Whole-second values and bulb are "long exposure" by convention (red numbers on
     * classic cameras like the Canon AE-1).
     */
    fun isLongExposure(stored: String): Boolean =
        stored == "B" || stored.endsWith("s")

    // ---------------------------------------------------------------------------
    // Aperture sequences
    // ---------------------------------------------------------------------------

    // Full stops: f/1 through f/64
    private val APERTURE_FULL = listOf(
        1.0f, 1.4f, 2.0f, 2.8f, 4.0f, 5.6f, 8.0f, 11.0f, 16.0f, 22.0f, 32.0f, 45.0f, 64.0f,
    )

    // Half stops: intermediate values added between full stops
    private val APERTURE_HALF = listOf(
        1.0f, 1.2f, 1.4f, 1.7f, 2.0f, 2.4f, 2.8f, 3.3f, 4.0f, 4.8f, 5.6f, 6.7f,
        8.0f, 9.5f, 11.0f, 13.0f, 16.0f, 19.0f, 22.0f, 27.0f, 32.0f,
    )

    // Third stops: standard photographic values at 1/3 stop spacing
    private val APERTURE_THIRD = listOf(
        1.0f, 1.1f, 1.2f, 1.4f, 1.6f, 1.8f, 2.0f, 2.2f, 2.5f, 2.8f, 3.2f, 3.5f,
        4.0f, 4.5f, 5.0f, 5.6f, 6.3f, 7.1f, 8.0f, 9.0f, 10.0f, 11.0f, 13.0f, 14.0f,
        16.0f, 18.0f, 20.0f, 22.0f, 25.0f, 29.0f, 32.0f,
    )

    /**
     * Returns valid aperture strings for a lens, bounded by [maxAperture] (widest) and
     * [minAperture] (narrowest), using the lens's [increments] type.
     *
     * Ordered widest → narrowest (f/1.4 first, f/22 last) — "up" step = wider = more exposure.
     *
     * A small epsilon (0.05) is used in the bounds check to handle floating-point edge cases
     * where lens.maxAperture = 1.4f might not exactly match the list value 1.4f.
     */
    fun apertures(
        maxAperture: Float,
        minAperture: Float,
        increments: ApertureIncrements,
    ): List<String> {
        val all = when (increments) {
            ApertureIncrements.FULL -> APERTURE_FULL
            ApertureIncrements.HALF -> APERTURE_HALF
            ApertureIncrements.THIRD -> APERTURE_THIRD
        }
        return all
            .filter { it >= maxAperture - 0.05f && it <= minAperture + 0.05f }
            .map { formatAperture(it) }
    }

    /**
     * Formats a Float aperture value to the canonical "f/X" storage string.
     * Integer values omit the decimal point: 8.0f → "f/8", 1.4f → "f/1.4".
     */
    fun formatAperture(value: Float): String =
        if (value == value.toLong().toFloat()) "f/${value.toLong()}" else "f/$value"

    /**
     * Parses a canonical "f/X" string back to a Float for bounds comparison.
     * Returns null if the string is malformed.
     */
    fun parseAperture(stored: String): Float? =
        stored.removePrefix("f/").toFloatOrNull()

    // ---------------------------------------------------------------------------
    // Exposure compensation
    // ---------------------------------------------------------------------------

    /**
     * Valid exposure compensation values in 1/3 stop increments from -3.0 to +3.0 EV.
     * Null is not in this list — the ViewModel handles null separately as "no EC applied".
     */
    val exposureCompensationValues: List<Float> = buildList {
        // Generate -3.0, -2.67, -2.33 … 0 … 2.33, 2.67, 3.0
        // Use integer arithmetic to avoid float accumulation error.
        for (thirds in -9..9) {
            add(thirds / 3.0f)
        }
    }

    /**
     * Formats an EC float for display, e.g. 0.0 → "0", 0.333 → "+⅓", -1.0 → "-1".
     * Uses vulgar fractions for third-stop values for a clean camera-dial aesthetic.
     */
    fun formatExposureCompensation(value: Float): String {
        val thirds = (value * 3).toInt()
        val wholePart = thirds / 3
        val remainder = thirds % 3
        val sign = if (value > 0f) "+" else if (value < 0f) "-" else ""
        val absWhole = Math.abs(wholePart)
        val absFrac = Math.abs(remainder)
        return when {
            thirds == 0 -> "0"
            absFrac == 0 -> "$sign$absWhole"
            absWhole == 0 -> "$sign${fraction(absFrac)}"
            else -> "$sign$absWhole${fraction(absFrac)}"
        }
    }

    private fun fraction(thirds: Int): String = when (thirds) {
        1 -> "⅓"
        2 -> "⅔"
        else -> ""
    }
}
