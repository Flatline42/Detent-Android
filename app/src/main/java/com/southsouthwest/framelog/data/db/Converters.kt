package com.southsouthwest.framelog.data.db

import androidx.room.TypeConverter
import com.southsouthwest.framelog.data.db.entity.ApertureIncrements
import com.southsouthwest.framelog.data.db.entity.CameraBodyFormat
import com.southsouthwest.framelog.data.db.entity.ColorType
import com.southsouthwest.framelog.data.db.entity.FilmFormat
import com.southsouthwest.framelog.data.db.entity.RollStatus
import com.southsouthwest.framelog.data.db.entity.ShutterIncrements

/**
 * Room TypeConverters for all enum types stored as human-readable strings in SQLite.
 * Using string values (e.g. "35mm", "full") rather than ordinals makes the database
 * readable outside the app and safe against enum reordering in future refactors.
 */
class Converters {

    @TypeConverter fun fromCameraBodyFormat(value: CameraBodyFormat): String = value.value
    @TypeConverter fun toCameraBodyFormat(value: String): CameraBodyFormat = CameraBodyFormat.fromValue(value)

    @TypeConverter fun fromFilmFormat(value: FilmFormat): String = value.value
    @TypeConverter fun toFilmFormat(value: String): FilmFormat = FilmFormat.fromValue(value)

    @TypeConverter fun fromShutterIncrements(value: ShutterIncrements): String = value.value
    @TypeConverter fun toShutterIncrements(value: String): ShutterIncrements = ShutterIncrements.fromValue(value)

    @TypeConverter fun fromApertureIncrements(value: ApertureIncrements): String = value.value
    @TypeConverter fun toApertureIncrements(value: String): ApertureIncrements = ApertureIncrements.fromValue(value)

    @TypeConverter fun fromColorType(value: ColorType): String = value.value
    @TypeConverter fun toColorType(value: String): ColorType = ColorType.fromValue(value)

    @TypeConverter fun fromRollStatus(value: RollStatus): String = value.value
    @TypeConverter fun toRollStatus(value: String): RollStatus = RollStatus.fromValue(value)
}
