package com.spoofer.usecase

import com.google.android.gms.maps.model.LatLng
import javax.inject.Inject
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

class StaticSpoofUseCase
    @Inject
    constructor() {
        fun getJitteredLocation(
            target: LatLng,
            jitterEnabled: Boolean = true,
            intensityMeters: Float = 2f,
        ): LatLng {
            if (!jitterEnabled || intensityMeters <= 0f) return target

            val angle = Random.nextDouble(0.0, 2 * PI)
            val distance = Random.nextDouble(0.0, intensityMeters.toDouble())
            val dLat = (distance * cos(angle)) / 111_320.0
            val dLng = (distance * sin(angle)) / (111_320.0 * cos(Math.toRadians(target.latitude)))

            return LatLng(target.latitude + dLat, target.longitude + dLng)
        }
    }
