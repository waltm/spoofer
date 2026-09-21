package com.spoofer.util

import com.google.android.gms.maps.model.LatLng
import com.spoofer.data.PlaceSuggestion

private val COORDINATE_REGEX = Regex("""^\s*(-?\d{1,3}(?:\.\d+)?)\s*,\s*(-?\d{1,3}(?:\.\d+)?)\s*$""")

/**
 * Parses text like "37.7749,-122.4194" or "37.7749, -122.4194" — typed or pasted
 * directly as coordinates — into a LatLng. Returns null for anything else (including
 * place names), so it's safe to try against every keystroke before falling back to
 * a geocoding search.
 */
fun parseCoordinates(text: String): LatLng? {
    val match = COORDINATE_REGEX.matchEntire(text) ?: return null
    val lat = match.groupValues[1].toDoubleOrNull() ?: return null
    val lng = match.groupValues[2].toDoubleOrNull() ?: return null
    if (lat !in -90.0..90.0 || lng !in -180.0..180.0) return null
    return LatLng(lat, lng)
}

/** A synthetic [PlaceSuggestion] representing raw coordinates the user typed/pasted directly. */
fun coordinateSuggestion(coordinate: LatLng): PlaceSuggestion {
    val formatted = "%.6f, %.6f".format(coordinate.latitude, coordinate.longitude)
    return PlaceSuggestion(
        label = formatted,
        name = formatted,
        locality = "Exact coordinates",
        latitude = coordinate.latitude,
        longitude = coordinate.longitude,
        type = "coordinate",
    )
}
