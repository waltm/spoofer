package com.spoofer.location.mode

import android.content.Context
import com.spoofer.location.JsonPatchedClient
import com.spoofer.location.MockLocationProvider
import com.spoofer.location.PatchedClient
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LocationModeSwitcher
@Inject
constructor(
    @ApplicationContext private val context: Context,
    private val mockLocationProvider: MockLocationProvider,
    private val jsonPatchedClient: JsonPatchedClient,
    private val patchedClient: PatchedClient,
) {
    private var currentMode: LocationMode? = null

    fun setMode(mode: LocationMode) {
        currentMode = mode
    }

    fun isDebugMode(): Boolean = currentMode is LocationMode.DebugMode

    fun isPatchedMode(): Boolean = currentMode is LocationMode.PatchedMode

    fun setMockLocation(
        latitude: Double,
        longitude: Double,
        accuracy: Float = 10f,
        altitude: Double = 0.0,
        bearing: Float = 0f,
        speed: Float = 0f,
    ) {
        currentMode?.let { mode ->
            when (mode) {
                is LocationMode.DebugMode -> {
                    mockLocationProvider.setMockLocation(
                        latitude, longitude, accuracy, altitude, bearing, speed,
                    )
                }
                is LocationMode.PatchedMode -> {
                    patchedClient.sendPosition(latitude, longitude)
                }
                LocationMode.Both -> {
                    mockLocationProvider.setMockLocation(
                        latitude, longitude, accuracy, altitude, bearing, speed,
                    )
                    patchedClient.sendPosition(latitude, longitude)
                }
            }
        }
    }

    fun setSpeed(speedKmh: Double) {
        // mhn's protocol has no separate speed message — it rides along
        // with SendPosition, which we don't currently carry. Nothing to do.
    }

    fun setAltitude(meters: Double) {
        // Same: no separate altitude message in mhn's protocol.
    }

    fun setBearing(degrees: Double) {
        // Same: no separate bearing message in mhn's protocol.
    }

    fun currentModeString(): String {
        return currentMode?.javaClass?.simpleName ?: "none"
    }

    fun reset() {
        currentMode = null
    }
}