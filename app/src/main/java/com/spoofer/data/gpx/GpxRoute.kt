package com.spoofer.data.gpx

data class GpxPoint(
    val latitude: Double,
    val longitude: Double,
    val elevation: Double? = null,
)

data class GpxRoute(
    val name: String,
    val points: List<GpxPoint>,
)
