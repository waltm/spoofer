package com.spoofer.data.gpx

import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import java.io.InputStream

/**
 * Parses GPX 1.0/1.1 files into a flat point list. Reads any mix of `trkpt`, `rtept`
 * and `wpt` elements (in document order) since community-shared route files use all
 * three inconsistently, and falls back to the first top-level `<name>` it finds for
 * the route label.
 */
object GpxParser {
    fun parse(input: InputStream): GpxRoute {
        val parser = Xml.newPullParser()
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
        parser.setInput(input, null)

        var routeName: String? = null
        val points = mutableListOf<GpxPoint>()

        var pointDepth = 0
        var currentLat: Double? = null
        var currentLon: Double? = null
        var currentEle: Double? = null
        var capturingRouteName = false

        var eventType = parser.eventType
        while (eventType != XmlPullParser.END_DOCUMENT) {
            when (eventType) {
                XmlPullParser.START_TAG -> {
                    when (parser.name) {
                        "trkpt", "rtept", "wpt" -> {
                            pointDepth++
                            currentLat = parser.getAttributeValue(null, "lat")?.toDoubleOrNull()
                            currentLon = parser.getAttributeValue(null, "lon")?.toDoubleOrNull()
                            currentEle = null
                        }
                        "ele" -> {
                            if (pointDepth > 0) {
                                currentEle = parser.nextText().trim().toDoubleOrNull()
                            }
                        }
                        "name" -> {
                            capturingRouteName = pointDepth == 0 && routeName == null
                        }
                    }
                }
                XmlPullParser.TEXT -> {
                    if (capturingRouteName) {
                        val text = parser.text?.trim()
                        if (!text.isNullOrEmpty()) routeName = text
                    }
                }
                XmlPullParser.END_TAG -> {
                    when (parser.name) {
                        "trkpt", "rtept", "wpt" -> {
                            val lat = currentLat
                            val lon = currentLon
                            if (lat != null && lon != null) {
                                points.add(GpxPoint(lat, lon, currentEle))
                            }
                            pointDepth--
                        }
                        "name" -> capturingRouteName = false
                    }
                }
            }
            eventType = parser.next()
        }

        require(points.isNotEmpty()) { "No track, route, or waypoints found in this GPX file" }
        return GpxRoute(name = routeName ?: "Imported route", points = points)
    }
}
