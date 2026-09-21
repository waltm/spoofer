package com.spoofer.data.gpx

import com.google.android.gms.maps.model.LatLng
import java.io.Writer
import java.util.Locale

object GpxWriter {
    fun write(
        writer: Writer,
        routeName: String,
        points: List<LatLng>,
    ) {
        writer.appendLine("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
        writer.appendLine(
            """<gpx version="1.1" creator="Spoofer" xmlns="http://www.topografix.com/GPX/1/1">""",
        )
        writer.appendLine("  <trk>")
        writer.appendLine("    <name>${escapeXml(routeName)}</name>")
        writer.appendLine("    <trkseg>")
        for (point in points) {
            writer.appendLine(
                "      <trkpt lat=\"${formatCoord(point.latitude)}\" lon=\"${formatCoord(point.longitude)}\" />",
            )
        }
        writer.appendLine("    </trkseg>")
        writer.appendLine("  </trk>")
        writer.appendLine("</gpx>")
    }

    private fun formatCoord(value: Double): String = String.format(Locale.US, "%.6f", value)

    private fun escapeXml(value: String): String =
        value
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")
}
