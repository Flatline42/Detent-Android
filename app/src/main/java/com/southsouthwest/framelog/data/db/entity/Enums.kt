package com.southsouthwest.framelog.data.db.entity

/**
 * Camera body film format.
 * MEDIUM_FORMAT is reserved for v1.1 — must not appear in any v1.0 UI.
 */
enum class CameraBodyFormat(val value: String) {
    THIRTY_FIVE_MM("35mm"),
    HALF_FRAME("half_frame"),
    MEDIUM_FORMAT("medium_format"); // Reserved — do not surface in v1.0 UI

    companion object {
        fun fromValue(value: String) = entries.first { it.value == value }
    }
}

/**
 * Film stock format.
 * MEDIUM_FORMAT is reserved for v1.1 — must not appear in any v1.0 UI.
 */
enum class FilmFormat(val value: String) {
    THIRTY_FIVE_MM("35mm"),
    MEDIUM_FORMAT("medium_format"); // Reserved — do not surface in v1.0 UI

    companion object {
        fun fromValue(value: String) = entries.first { it.value == value }
    }
}

/**
 * Shutter speed step granularity. Physically determined by the camera body shutter mechanism.
 * Controls which shutter speed values appear in the stepper when this body is active on a roll.
 */
enum class ShutterIncrements(val value: String) {
    FULL("full"),
    HALF("half"),
    THIRD("third");

    companion object {
        fun fromValue(value: String) = entries.first { it.value == value }
    }
}

/**
 * Aperture step granularity. Physically determined by the lens aperture ring detents.
 * Controls which aperture values appear in the stepper when this lens is active on a frame.
 */
enum class ApertureIncrements(val value: String) {
    FULL("full"),
    HALF("half"),
    THIRD("third");

    companion object {
        fun fromValue(value: String) = entries.first { it.value == value }
    }
}

/** Film stock type: negative vs slide, color vs B&W. */
enum class ColorType(val value: String) {
    COLOR_NEGATIVE("color_negative"),
    BW_NEGATIVE("bw_negative"),
    SLIDE("slide");

    companion object {
        fun fromValue(value: String) = entries.first { it.value == value }
    }
}

/**
 * Roll lifecycle status. Independent of [Roll.isLoaded] — a roll can be active but not loaded,
 * or loaded but finished.
 */
enum class RollStatus(val value: String) {
    ACTIVE("active"),
    FINISHED("finished"),
    ARCHIVED("archived");

    companion object {
        fun fromValue(value: String) = entries.first { it.value == value }
    }
}
