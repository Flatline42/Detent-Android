package com.southsouthwest.framelog.ui.util

import com.southsouthwest.framelog.data.db.relation.FrameExport
import com.southsouthwest.framelog.data.db.relation.RollExport
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Formats a [RollExport] snapshot into CSV, JSON, or plain text for sharing.
 *
 * All formatting is pure (no I/O, no Android dependencies) — safe to call from any coroutine.
 * The ViewModel passes the resulting String to the UI for the Android share Intent.
 */
object ExportFormatter {

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)

    // ExifTool requires "YYYY:MM:DD HH:MM:SS" — note colons in the date portion.
    private val exifDateFormat = SimpleDateFormat("yyyy:MM:dd HH:mm:ss", Locale.US)

    // ---------------------------------------------------------------------------
    // CSV
    // ---------------------------------------------------------------------------

    /**
     * Produces a CSV file with a roll header block followed by a frame data table.
     *
     * Header block uses "# key,value" comment rows so CSV parsers can skip them.
     * Frame table has a single header row followed by one row per frame.
     */
    fun toCsv(export: RollExport): String = buildString {
        // Roll header as commented key-value rows
        appendLine("# Roll,${csvEscape(export.rollName)}")
        appendLine("# Film Stock,${csvEscape("${export.filmStockMake} ${export.filmStockName}")}")
        appendLine("# Camera Body,${csvEscape("${export.cameraBodyMake} ${export.cameraBodyName}")}")
        appendLine("# Rated ISO,${export.ratedISO}")
        val pushPullDisplay = when (export.pushPull) {
            null -> "custom"
            0 -> "box speed"
            else -> if (export.pushPull > 0) "push ${export.pushPull}" else "pull ${-export.pushPull}"
        }
        appendLine("# Push/Pull,$pushPullDisplay")
        export.loadedAt.let { appendLine("# Loaded,${dateFormat.format(Date(it))}") }
        export.finishedAt?.let { appendLine("# Finished,${dateFormat.format(Date(it))}") }
        appendLine()

        // Frame table header
        appendLine("Frame,Logged,Timestamp,Aperture,Shutter,Lens,EC,Filters,EV Sum,Lat,Lng,Notes")

        // Frame rows
        for (frame in export.frames) {
            val evSum = filterEvSum(frame)
            val filters = frame.filterNames.joinToString("; ")
            appendLine(
                listOf(
                    frame.frameNumber.toString(),
                    if (frame.isLogged) "yes" else "no",
                    frame.loggedAt?.let { dateFormat.format(Date(it)) } ?: "",
                    frame.aperture ?: "",
                    frame.shutterSpeed?.let { ExposureValues.shutterDisplayValue(it) } ?: "",
                    frame.lensName?.let { "${frame.lensMake} $it ${frame.lensFocalLengthMm}mm" } ?: "",
                    frame.exposureCompensation?.let { ExposureValues.formatExposureCompensation(it) } ?: "",
                    filters,
                    evSum ?: "",
                    frame.lat?.toString() ?: "",
                    frame.lng?.toString() ?: "",
                    frame.notes ?: "",
                ).joinToString(",") { csvEscape(it) }
            )
        }
    }

    // ---------------------------------------------------------------------------
    // JSON
    // ---------------------------------------------------------------------------

    /**
     * Produces a JSON object with roll metadata and a "frames" array.
     * Hand-rolled JSON to avoid adding a serialization dependency on the export types.
     */
    fun toJson(export: RollExport): String = buildString {
        appendLine("{")
        appendLine("  \"roll\": ${jsonEscape(export.rollName)},")
        appendLine("  \"filmStock\": ${jsonEscape("${export.filmStockMake} ${export.filmStockName}")},")
        appendLine("  \"cameraBody\": ${jsonEscape("${export.cameraBodyMake} ${export.cameraBodyName}")},")
        appendLine("  \"ratedISO\": ${export.ratedISO},")
        val ppDisplay = export.pushPull?.toString() ?: "null"
        appendLine("  \"pushPull\": $ppDisplay,")
        appendLine("  \"gpsEnabled\": ${export.gpsEnabled},")
        appendLine("  \"loadedAt\": ${jsonEscape(dateFormat.format(Date(export.loadedAt)))},")
        val finishedAtStr = export.finishedAt?.let { dateFormat.format(Date(it)) }
        appendLine("  \"finishedAt\": ${if (finishedAtStr != null) jsonEscape(finishedAtStr) else "null"},")
        appendLine("  \"notes\": ${if (export.notes != null) jsonEscape(export.notes) else "null"},")
        appendLine("  \"frames\": [")

        export.frames.forEachIndexed { index, frame ->
            val isLast = index == export.frames.lastIndex
            appendLine("    {")
            appendLine("      \"frame\": ${frame.frameNumber},")
            appendLine("      \"logged\": ${frame.isLogged},")
            appendLine("      \"loggedAt\": ${frame.loggedAt?.let { jsonEscape(dateFormat.format(Date(it))) } ?: "null"},")
            appendLine("      \"aperture\": ${frame.aperture?.let { jsonEscape(it) } ?: "null"},")
            appendLine("      \"shutterSpeed\": ${frame.shutterSpeed?.let { jsonEscape(it) } ?: "null"},")
            val lensStr = frame.lensName?.let { "${frame.lensMake} $it ${frame.lensFocalLengthMm}mm" }
            appendLine("      \"lens\": ${if (lensStr != null) jsonEscape(lensStr) else "null"},")
            appendLine("      \"exposureCompensation\": ${frame.exposureCompensation ?: "null"},")
            appendLine("      \"filters\": [${frame.filterNames.joinToString(", ") { jsonEscape(it) }}],")
            appendLine("      \"evSum\": ${filterEvSum(frame)?.let { jsonEscape(it) } ?: "null"},")
            appendLine("      \"lat\": ${frame.lat ?: "null"},")
            appendLine("      \"lng\": ${frame.lng ?: "null"},")
            append("      \"notes\": ${frame.notes?.let { jsonEscape(it) } ?: "null"}")
            appendLine()
            append("    }")
            if (!isLast) append(",")
            appendLine()
        }

        appendLine("  ]")
        append("}")
    }

    // ---------------------------------------------------------------------------
    // Plain text
    // ---------------------------------------------------------------------------

    /**
     * Produces a human-readable contact-sheet style text log.
     * Modeled after a film photography lab printout.
     */
    fun toPlainText(export: RollExport): String = buildString {
        appendLine("DETENT Export")
        appendLine("═".repeat(40))
        appendLine("Roll:       ${export.rollName}")
        appendLine("Film:       ${export.filmStockMake} ${export.filmStockName}")
        appendLine("Camera:     ${export.cameraBodyMake} ${export.cameraBodyName}")
        appendLine("Rated ISO:  ${export.ratedISO}")
        val ppLine = when (export.pushPull) {
            null -> "custom"
            0 -> "box speed"
            else -> if (export.pushPull > 0) "push ${export.pushPull}" else "pull ${-export.pushPull}"
        }
        appendLine("Push/Pull:  $ppLine")
        appendLine("Loaded:     ${dateFormat.format(Date(export.loadedAt))}")
        export.finishedAt?.let { appendLine("Finished:   ${dateFormat.format(Date(it))}") }
        export.notes?.let { appendLine("Notes:      $it") }
        appendLine()

        for (frame in export.frames) {
            val label = if (frame.isLogged) "▣  Frame ${frame.frameNumber}" else "□  Frame ${frame.frameNumber}"
            appendLine(label)
            if (frame.isLogged) {
                frame.loggedAt?.let { appendLine("   Time:      ${dateFormat.format(Date(it))}") }
                frame.aperture?.let { appendLine("   Aperture:  $it") }
                frame.shutterSpeed?.let {
                    appendLine("   Shutter:   ${ExposureValues.shutterDisplayValue(it)}")
                }
                frame.lensName?.let {
                    appendLine("   Lens:      ${frame.lensMake} $it ${frame.lensFocalLengthMm}mm")
                }
                frame.exposureCompensation?.let {
                    appendLine("   EC:        ${ExposureValues.formatExposureCompensation(it)} EV")
                }
                if (frame.filterNames.isNotEmpty()) {
                    val evStr = filterEvSum(frame)?.let { " ($it EV)" } ?: ""
                    appendLine("   Filters:   ${frame.filterNames.joinToString(", ")}$evStr")
                }
                if (frame.lat != null && frame.lng != null) {
                    appendLine("   GPS:       ${frame.lat}, ${frame.lng}")
                }
                frame.notes?.let { appendLine("   Notes:     $it") }
            }
            appendLine()
        }
    }

    // ---------------------------------------------------------------------------
    // ExifTool CSV
    // ---------------------------------------------------------------------------

    /**
     * Produces a CSV formatted for Phil Harvey's ExifTool utility.
     *
     * Column names map directly to ExifTool tag names so ExifTool can batch-write
     * metadata to scanned film negatives with:
     *   exiftool -csv=export_exiftool.csv /path/to/scans/
     *
     * Only logged frames are included. The SourceFile column is left empty —
     * the user fills it in with their scan filenames before running ExifTool.
     *
     * No comment rows — ExifTool cannot parse # comment lines.
     */
    fun toExifToolCsv(export: RollExport): String = buildString {
        appendLine("SourceFile,Make,Model,LensModel,FNumber,ExposureTime,ISO,DateTimeOriginal,GPSLatitude,GPSLongitude,GPSLatitudeRef,GPSLongitudeRef,ImageDescription,UserComment,XMP:Subject")

        for (frame in export.frames) {
            if (!frame.isLogged) continue

            // "f/5.6" → "5.6"
            val fNumber = frame.aperture?.removePrefix("f/") ?: ""

            // Shutter speed as decimal seconds; "B" (bulb) is left empty
            val exposureTime = frame.shutterSpeed?.let { parseShutterSecs(it) } ?: ""

            val dateTimeOriginal = frame.loggedAt?.let { exifDateFormat.format(Date(it)) } ?: ""

            // GPS: store absolute values; hemisphere goes in the Ref columns
            val gpsLat = frame.lat?.let { if (it < 0) -it else it }?.toString() ?: ""
            val gpsLng = frame.lng?.let { if (it < 0) -it else it }?.toString() ?: ""
            val latRef  = frame.lat?.let { if (it >= 0) "N" else "S" } ?: ""
            val lngRef  = frame.lng?.let { if (it >= 0) "E" else "W" } ?: ""

            // XMP:Subject holds active filter names as a comma-separated list.
            // csvEscape will quote the value if it contains commas.
            val subject = frame.filterNames.joinToString(",")

            appendLine(
                listOf(
                    "",                          // SourceFile — user fills in scan filenames
                    export.cameraBodyMake,       // Make
                    export.cameraBodyName,       // Model
                    frame.lensName ?: "",        // LensModel
                    fNumber,                     // FNumber
                    exposureTime,                // ExposureTime (decimal seconds)
                    export.ratedISO.toString(),  // ISO
                    dateTimeOriginal,            // DateTimeOriginal ("YYYY:MM:DD HH:MM:SS")
                    gpsLat,                      // GPSLatitude (absolute value)
                    gpsLng,                      // GPSLongitude (absolute value)
                    latRef,                      // GPSLatitudeRef ("N" or "S")
                    lngRef,                      // GPSLongitudeRef ("E" or "W")
                    frame.notes ?: "",           // ImageDescription
                    export.filmStockName,        // UserComment (same for all frames on the roll)
                    subject,                     // XMP:Subject (filter names)
                ).joinToString(",") { csvEscape(it) }
            )
        }
    }

    // ---------------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------------

    /**
     * Computes the display EV sum string for a frame's active filters.
     * Returns null if no filters are active or all have null evReduction.
     * Prepends "~" if any active filter has null evReduction (incomplete sum).
     */
    fun filterEvSum(frame: FrameExport): String? {
        if (frame.filterNames.isEmpty()) return null
        val reductions = frame.filterEvReductions
        val hasNull = reductions.any { it == null }
        val knownSum = reductions.filterNotNull().sum()
        if (knownSum == 0f && !hasNull) return null
        val prefix = if (hasNull) "~" else ""
        return "$prefix-${"%.1f".format(knownSum)} EV"
    }

    /**
     * Converts a stored shutter speed string to decimal seconds for ExifTool.
     *
     * "1/125" → "0.008", "2s" → "2", "B" → "" (bulb cannot be represented).
     */
    private fun parseShutterSecs(shutterSpeed: String): String {
        if (shutterSpeed == "B") return ""
        // Whole-second values are stored with a trailing "s" (e.g. "1s", "30s")
        if (shutterSpeed.endsWith("s")) {
            val secs = shutterSpeed.dropLast(1).toDoubleOrNull() ?: return ""
            return formatExifDecimal(secs)
        }
        // Fractional values are stored as "numerator/denominator" (e.g. "1/125")
        val parts = shutterSpeed.split("/")
        if (parts.size == 2) {
            val num = parts[0].toDoubleOrNull() ?: return ""
            val den = parts[1].toDoubleOrNull() ?: return ""
            if (den == 0.0) return ""
            return formatExifDecimal(num / den)
        }
        return ""
    }

    /**
     * Formats a Double with up to 6 decimal places, stripping trailing zeros.
     * Avoids scientific notation (e.g. 0.008 instead of 8.0E-3).
     */
    private fun formatExifDecimal(value: Double): String =
        "%.6f".format(value).trimEnd('0').trimEnd('.')

    private fun csvEscape(value: String): String {
        // Wrap in quotes if value contains comma, quote, or newline
        return if (value.contains(',') || value.contains('"') || value.contains('\n')) {
            "\"${value.replace("\"", "\"\"")}\""
        } else {
            value
        }
    }

    private fun jsonEscape(value: String): String {
        val escaped = value
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t")
        return "\"$escaped\""
    }
}
