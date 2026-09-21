package com.spoofer.usecase

import com.google.android.gms.maps.model.LatLng
import com.spoofer.data.DirectionsRepository
import com.spoofer.data.ElevationRepository
import com.spoofer.data.RouteInfo
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

@Singleton
class SpeedSimulationUseCase
    @Inject
    constructor(
        private val directionsRepo: DirectionsRepository,
        private val elevationRepository: ElevationRepository,
    ) {
        private var polylinePoints: List<LatLng> = emptyList()
        private var elevationPoints: List<Double>? = null
        private var segmentSpeedsMps: List<Double>? = null
        private var currentSegmentIndex = 0
        private var progressAlongSegment = 0.0
        var totalDistanceTraveled = 0.0
            private set

        /** OSRM's assigned speed (m/s) for the road segment currently being traversed, when known. */
        val currentSegmentSpeedLimitMps: Double?
            get() = segmentSpeedsMps?.getOrNull(currentSegmentIndex)

        val totalDistance: Double get() =
            if (polylinePoints.size < 2) {
                0.0
            } else {
                var d = 0.0
                for (i in 0 until polylinePoints.lastIndex) {
                    d += distanceBetween(polylinePoints[i], polylinePoints[i + 1])
                }
                d
            }

        val remainingDistance: Double get() = totalDistance - totalDistanceTraveled

        suspend fun initialize(
            origin: LatLng,
            destination: LatLng,
            fetchElevation: Boolean = false,
        ): RouteInfo = initialize(listOf(origin, destination), fetchElevation)

        /**
         * Fetches and sets a route through 2+ waypoints in order — origin, any stops,
         * destination (or origin again, for a round trip) — as a single OSRM request.
         */
        suspend fun initialize(
            waypoints: List<LatLng>,
            fetchElevation: Boolean = false,
        ): RouteInfo {
            require(waypoints.size >= 2) { "A route needs at least an origin and a destination" }
            val route = directionsRepo.getRoute(waypoints)
            polylinePoints = route.polyline
            currentSegmentIndex = 0
            progressAlongSegment = 0.0
            totalDistanceTraveled = 0.0
            segmentSpeedsMps = route.segmentSpeedsMps
            elevationPoints =
                if (fetchElevation) {
                    elevationRepository.getElevations(route.polyline)?.takeIf { it.size == route.polyline.size }
                } else {
                    null
                }
            return route
        }

        /**
         * Sets the route directly from an already-known point sequence (e.g. an
         * imported GPX file), skipping the OSRM directions lookup entirely. There's
         * no routing-provider ETA for a user-supplied path, so duration is estimated
         * at a brisk walking pace.
         *
         * [elevations] carries per-point elevation when the source already has it (an
         * imported GPX's `<ele>` tags); gaps are filled by interpolating between the
         * nearest known values. When it's entirely absent and [fetchElevationIfMissing]
         * is set, elevation is looked up from a remote provider instead.
         */
        suspend fun initializeWithPolyline(
            polyline: List<LatLng>,
            elevations: List<Double?>? = null,
            fetchElevationIfMissing: Boolean = false,
        ): RouteInfo {
            require(polyline.size >= 2) { "A route needs at least two points" }
            polylinePoints = polyline
            currentSegmentIndex = 0
            progressAlongSegment = 0.0
            totalDistanceTraveled = 0.0
            // A user-supplied point sequence carries no road-class data, unlike an
            // OSRM-fetched route — clear any speed limits left over from a prior session.
            segmentSpeedsMps = null

            elevationPoints =
                when {
                    elevations != null && elevations.any { it != null } -> fillElevationGaps(elevations)
                    fetchElevationIfMissing -> elevationRepository.getElevations(polyline)
                    else -> null
                }?.takeIf { it.size == polyline.size }

            val distanceMeters = totalDistance
            val estimatedDurationSeconds = (distanceMeters / ESTIMATED_WALK_SPEED_MPS).toInt()
            return RouteInfo(
                polyline = polyline,
                durationSeconds = estimatedDurationSeconds,
                distanceMeters = distanceMeters.toInt(),
            )
        }

        fun tick(speedMps: Float): MovementResult? {
            if (currentSegmentIndex >= polylinePoints.lastIndex) return null

            var remainingMeters = speedMps.toDouble()

            while (remainingMeters > 0 && currentSegmentIndex < polylinePoints.lastIndex) {
                val segStart = polylinePoints[currentSegmentIndex]
                val segEnd = polylinePoints[currentSegmentIndex + 1]
                val segLength = distanceBetween(segStart, segEnd)
                val remainingInSeg = segLength - progressAlongSegment

                if (remainingMeters < remainingInSeg) {
                    progressAlongSegment += remainingMeters
                    totalDistanceTraveled += remainingMeters
                    val fraction = progressAlongSegment / segLength
                    val position = interpolate(segStart, segEnd, fraction)
                    val bearing = calculateBearing(segStart, segEnd)
                    val altitude = interpolateElevation(currentSegmentIndex, fraction)
                    return MovementResult(position, bearing, totalDistanceTraveled, arrived = false, altitude = altitude)
                } else {
                    remainingMeters -= remainingInSeg
                    totalDistanceTraveled += remainingInSeg
                    currentSegmentIndex++
                    progressAlongSegment = 0.0
                }
            }

            val finalAltitude = elevationPoints?.lastOrNull() ?: 0.0
            return MovementResult(polylinePoints.last(), 0f, totalDistanceTraveled, arrived = true, altitude = finalAltitude)
        }

        private fun interpolateElevation(
            segmentIndex: Int,
            fraction: Double,
        ): Double {
            val elevations = elevationPoints ?: return 0.0
            if (segmentIndex >= elevations.lastIndex) return elevations.lastOrNull() ?: 0.0
            val startElevation = elevations[segmentIndex]
            val endElevation = elevations[segmentIndex + 1]
            return startElevation + (endElevation - startElevation) * fraction
        }

        /** Linearly interpolates missing values between the nearest known elevations, and flat-extrapolates at the ends. Returns null if none are known. */
        private fun fillElevationGaps(elevations: List<Double?>): List<Double>? {
            val knownIndices = elevations.indices.filter { elevations[it] != null }
            if (knownIndices.isEmpty()) return null

            return elevations.indices.map { i ->
                val known = elevations[i]
                if (known != null) return@map known

                val before = knownIndices.lastOrNull { it < i }
                val after = knownIndices.firstOrNull { it > i }
                when {
                    before != null && after != null -> {
                        val t = (i - before).toDouble() / (after - before)
                        elevations[before]!! + (elevations[after]!! - elevations[before]!!) * t
                    }
                    before != null -> elevations[before]!!
                    after != null -> elevations[after]!!
                    else -> 0.0
                }
            }
        }

        companion object {
            private const val EARTH_RADIUS = 6_371_000.0
            private const val ESTIMATED_WALK_SPEED_MPS = 1.4

            fun distanceBetween(
                a: LatLng,
                b: LatLng,
            ): Double {
                val dLat = Math.toRadians(b.latitude - a.latitude)
                val dLng = Math.toRadians(b.longitude - a.longitude)
                val sinLat = sin(dLat / 2)
                val sinLng = sin(dLng / 2)
                val aVal =
                    sinLat * sinLat + cos(Math.toRadians(a.latitude)) *
                        cos(Math.toRadians(b.latitude)) * sinLng * sinLng
                return EARTH_RADIUS * 2 * atan2(sqrt(aVal), sqrt(1 - aVal))
            }

            fun calculateBearing(
                from: LatLng,
                to: LatLng,
            ): Float {
                val fromLat = Math.toRadians(from.latitude)
                val toLat = Math.toRadians(to.latitude)
                val dLng = Math.toRadians(to.longitude - from.longitude)
                val x = sin(dLng) * cos(toLat)
                val y = cos(fromLat) * sin(toLat) - sin(fromLat) * cos(toLat) * cos(dLng)
                return Math.toDegrees(atan2(x, y)).toFloat()
            }

            private fun interpolate(
                a: LatLng,
                b: LatLng,
                fraction: Double,
            ): LatLng {
                return LatLng(
                    a.latitude + (b.latitude - a.latitude) * fraction,
                    a.longitude + (b.longitude - a.longitude) * fraction,
                )
            }
        }
    }

data class MovementResult(
    val position: LatLng,
    val bearing: Float,
    val totalDistance: Double,
    val arrived: Boolean,
    val altitude: Double = 0.0,
)
